import Foundation

/// A thread-safe container that manages multiple AsyncThrowingStream continuations.
final class StreamContinuationList<T>: @unchecked Sendable {
    private let lock = NSLock()
    private var entries: [(id: UUID, cont: AsyncThrowingStream<T, Error>.Continuation)] = []

    /// Creates a new stream. If `checkConnection` is true and the transport is not connected,
    /// the stream immediately finishes with a gattDisconnected error.
    /// - Parameter initialValues: values yielded only to this new stream before it joins the shared list,
    ///   useful for replaying already-known state to a late subscriber.
    func makeStream(
        transport: BleAttributeTransportProtocol?,
        checkConnection: Bool,
        initialValues: [T] = []
    ) -> AsyncThrowingStream<T, Error> {
        let connected = transport?.isConnected() ?? false
        let plannedTerminal = planRuntimeStreamSubscription(target: "stream", startConnected: connected, checkConnection: checkConnection)
        if plannedTerminal == "gattDisconnected" || (plannedTerminal == nil && checkConnection && !connected) {
            return AsyncThrowingStream { $0.finish(throwing: BleGattException.gattDisconnected) }
        }
        let id = UUID()
        var capturedCont: AsyncThrowingStream<T, Error>.Continuation!
        let stream = AsyncThrowingStream<T, Error> { cont in
            capturedCont = cont
        }
        // Replay initial values to only this new stream before it enters the shared list.
        initialValues.forEach { capturedCont.yield($0) }
        lock.lock()
        entries.append((id: id, cont: capturedCont))
        lock.unlock()
        capturedCont.onTermination = { [weak self] _ in
            guard planRuntimeStreamConsumerCancellation(target: "stream") else { return }
            guard let self else { return }
            self.lock.lock()
            self.entries.removeAll { $0.id == id }
            self.lock.unlock()
        }
        return stream
    }

    /// Yield a value to all active streams.
    func yield(_ value: T) {
        if isEmpty && planRuntimeStreamPostCompletionEmission(target: "stream", value: "value") == 0 { return }
        lock.lock()
        let current = entries
        lock.unlock()
        current.forEach { $0.cont.yield(value) }
    }

    /// Finish all active streams successfully, then remove them.
    func finish() {
        let completionEvents = planRuntimeStreamDuplicateCompletion(target: "stream")
        lock.lock()
        let current = entries
        entries.removeAll()
        lock.unlock()
        guard completionEvents > 0 || !current.isEmpty else { return }
        current.forEach { $0.cont.finish() }
    }

    /// Finish all active streams with the given error, then remove them.
    func finish(throwing error: Error) {
        let errorName = String(describing: type(of: error))
        guard planRuntimeStreamDisconnect(target: "stream", error: errorName) else { return }
        lock.lock()
        let current = entries
        entries.removeAll()
        lock.unlock()
        current.forEach { $0.cont.finish(throwing: error) }
    }

    /// Whether there are any active stream consumers.
    var isEmpty: Bool {
        lock.lock()
        defer { lock.unlock() }
        return entries.isEmpty
    }
}

// MARK: -

/// Connects a single upstream `AsyncThrowingStream` to N independent downstream consumers.
///
/// **Lifecycle:**
/// - Upstream starts lazily — the first `makeStream` call creates the upstream Task.
/// - Each consumer gets its own independently-cancellable `AsyncThrowingStream`.
/// - When the **last** consumer cancels, the upstream Task is also cancelled (ref-counted).
/// - When the upstream finishes or errors, all consumers are completed accordingly.
/// - `finish(throwing:)` terminates all consumers immediately (e.g. on BLE disconnect).
final class MulticastAsyncStream<T>: @unchecked Sendable {

    private let lock = NSLock()
    private let makeUpstream: () -> AsyncThrowingStream<T, Error>
    private var consumers: [(id: UUID, cont: AsyncThrowingStream<T, Error>.Continuation)] = []
    private var upstreamTask: Task<Void, Never>?

    init(upstream: @escaping () -> AsyncThrowingStream<T, Error>) {
        self.makeUpstream = upstream
    }

    /// Returns a new consumer stream fed by the shared upstream.
    ///
    /// - Parameters:
    ///   - transport: used for the connection check; pass `nil` to skip.
    ///   - checkConnection: if `true` and transport is not connected, the returned stream
    ///     immediately finishes with `BleGattException.gattDisconnected`.
    func makeStream(
        transport: BleAttributeTransportProtocol?,
        checkConnection: Bool
    ) -> AsyncThrowingStream<T, Error> {
        let connected = transport?.isConnected() ?? false
        let plannedTerminal = planRuntimeStreamSubscription(target: "stream", startConnected: connected, checkConnection: checkConnection)
        if plannedTerminal == "gattDisconnected" || (plannedTerminal == nil && checkConnection && !connected) {
            return AsyncThrowingStream { $0.finish(throwing: BleGattException.gattDisconnected) }
        }
        let id = UUID()
        var capturedCont: AsyncThrowingStream<T, Error>.Continuation!
        let stream = AsyncThrowingStream<T, Error> { capturedCont = $0 }
        lock.lock()
        consumers.append((id: id, cont: capturedCont))
        if upstreamTask == nil { upstreamTask = startUpstreamTask() }
        lock.unlock()
        capturedCont.onTermination = { [weak self] _ in self?.remove(id: id) }
        return stream
    }

    /// Immediately terminates all active consumers with `error` and cancels the upstream.
    func finish(throwing error: Error) {
        let errorName = String(describing: type(of: error))
        guard planRuntimeStreamDisconnect(target: "stream", error: errorName) else { return }
        lock.lock()
        let current = consumers; consumers.removeAll()
        let task = upstreamTask; upstreamTask = nil
        lock.unlock()
        task?.cancel()
        current.forEach { $0.cont.finish(throwing: error) }
    }

    // MARK: - Private

    private func remove(id: UUID) {
        guard planRuntimeStreamConsumerCancellation(target: "stream") else { return }
        lock.lock()
        consumers.removeAll { $0.id == id }
        let shouldCancel = consumers.isEmpty
        let task = shouldCancel ? upstreamTask : nil
        if shouldCancel { upstreamTask = nil }
        lock.unlock()
        task?.cancel()
    }

    private func startUpstreamTask() -> Task<Void, Never> {
        Task { [weak self] in
            guard let self else { return }
            do {
                for try await value in self.makeUpstream() {
                    let current = self.lock.withLock { self.consumers }
                    if current.isEmpty && planRuntimeStreamPostCompletionEmission(target: "stream", value: "value") == 0 { continue }
                    current.forEach { $0.cont.yield(value) }
                }
                let current = self.lock.withLock {
                    let current = self.consumers
                    self.consumers.removeAll()
                    self.upstreamTask = nil
                    return current
                }
                let completionEvents = planRuntimeStreamDuplicateCompletion(target: "stream")
                guard completionEvents > 0 || !current.isEmpty else { return }
                current.forEach { $0.cont.finish() }
            } catch {
                guard !(error is CancellationError) else { return }
                let current = self.lock.withLock {
                    let current = self.consumers
                    self.consumers.removeAll()
                    self.upstreamTask = nil
                    return current
                }
                let errorName = String(describing: type(of: error))
                guard planRuntimeStreamDisconnect(target: "stream", error: errorName) else { return }
                current.forEach { $0.cont.finish(throwing: error) }
            }
        }
    }
}

private func planRuntimeStreamSubscription(target: String, startConnected: Bool, checkConnection: Bool) -> String? {
    let planned = PolarRuntimePlanner.streamSubscription(target: target, startConnected: startConnected, checkConnection: checkConnection)
    return planned == "platform-owned" ? nil : planned
}

private func planRuntimeStreamConsumerCancellation(target: String) -> Bool {
    let planned = PolarRuntimePlanner.streamConsumerCancellation(target: target)
    if planned == "platform-owned" { return true }
    return planned.split(separator: ",").contains(Substring(target))
}

private func planRuntimeStreamDisconnect(target: String, error: String) -> Bool {
    let planned = PolarRuntimePlanner.streamDisconnect(target: target, error: error)
    return planned == "platform-owned" || planned == error
}

private func planRuntimeStreamDuplicateCompletion(target: String) -> Int {
    return PolarRuntimePlanner.streamDuplicateCompletion(target: target)
}

private func planRuntimeStreamPostCompletionEmission(target: String, value: String) -> Int {
    return PolarRuntimePlanner.streamPostCompletionEmission(target: target, value: value)
}
