import Foundation
import CoreBluetooth
import Combine

open class BleGattClientBase: Hashable, @unchecked Sendable {
    open func hash(into hasher: inout Hasher) {
        hasher.combine(ObjectIdentifier(self).hashValue)
    }

    static func address<T: AnyObject>(o: T) -> Int {
        return unsafeBitCast(o, to: Int.self)
    }

    var baseSerialDispatchQueue: DispatchQueue!
    weak var gattServiceTransmitter: BleAttributeTransportProtocol?
    var mtuSize = 20

    private struct notificationChr {
        let uuid: CBUUID
        var state: AtomicInteger = AtomicInteger.init(initialValue: -1)
        let disableOnDisconnect: Bool
    }

    private var notificationCharacteristics = [notificationChr]()
    private var characteristics = Set<CBUUID>()
    private let availableCharacteristics = AtomicList<CBUUID>()
    private var characteristicsRead = Set<CBUUID>()
    private let availableReadableCharacteristics = AtomicList<CBUUID>()
    private let serviceUuid: CBUUID
    private let serviceDiscovered = AtomicBoolean(initialValue: false)
    private var cancellables = Set<AnyCancellable>()

    let ATT_NOTIFY_OR_INDICATE_STATE_UNKNOWN = -1
    let ATT_NOTIFY_OR_INDICATE_ON = 0
    let ATT_NOTIFY_OR_INDICATE_OFF = 1
    let ATT_NOTIFY_OR_INDICATE_ERROR = 2

    private var notificationWaitObservers = AtomicList<NotificationWaitObserver>()

    /// Wraps a service-wait promise so it can be identified by object identity
    /// and safely resolved exactly once.
    private final class ServiceWaitObserver {
        private let promise: (Result<Void, Error>) -> Void
        private let lock = NSLock()
        private var resolved = false

        init(_ promise: @escaping (Result<Void, Error>) -> Void) {
            self.promise = promise
        }

        func resolve(with result: Result<Void, Error>) {
            lock.lock(); defer { lock.unlock() }
            guard !resolved else { return }
            resolved = true
            promise(result)
        }
    }

    private var serviceWaitObservers = AtomicList<ServiceWaitObserver>()

    private class NotificationWaitObserver {
        private let promise: (Result<Void, Error>) -> Void
        let uuid: CBUUID
        private let lock = NSLock()
        private var cancelled = false
        private var resolved = false

        init(_ promise: @escaping (Result<Void, Error>) -> Void, uuid: CBUUID) {
            self.promise = promise
            self.uuid = uuid
        }

        /// Mark this observer as cancelled so that a concurrent delivery in `notifyDescriptorWritten` is safely ignored.
        func markCancelled() {
            lock.lock(); defer { lock.unlock() }
            cancelled = true
        }

        func isActive() -> Bool {
            lock.lock(); defer { lock.unlock() }
            return !cancelled
        }

        func resolve(with result: Result<Void, Error>) {
            lock.lock()
            guard !cancelled && !resolved else {
                lock.unlock()
                return
            }
            resolved = true
            lock.unlock()
            promise(result)
        }
    }

    private final class NotificationWaitCancellationToken: @unchecked Sendable {
        private let lock = NSLock()
        private var observer: NotificationWaitObserver?

        func set(_ observer: NotificationWaitObserver) {
            lock.lock(); defer { lock.unlock() }
            self.observer = observer
        }

        func cancel() -> NotificationWaitObserver? {
            lock.lock(); defer { lock.unlock() }
            observer?.markCancelled()
            let cancelledObserver = observer
            observer = nil
            return cancelledObserver
        }
    }

    public init(serviceUuid: CBUUID, gattServiceTransmitter: BleAttributeTransportProtocol) {
        self.gattServiceTransmitter = gattServiceTransmitter
        self.serviceUuid = serviceUuid
        self.baseSerialDispatchQueue = DispatchQueue(
            label: "BaseDispatchQueue" + serviceUuid.uuidString + "obj\(NSString(format: "%p", BleGattClientBase.address(o: self)))",
            attributes: []
        )
    }

    func addCharacteristicRead(_ chr: CBUUID) {
        characteristics.insert(chr)
        characteristicsRead.insert(chr)
    }

    func addCharacteristic(_ chr: CBUUID) {
        characteristics.insert(chr)
    }

    /// Characteristic notification/indication is automatically enabled on connection establishment.
    func automaticEnableNotificationsOnConnect(chr: CBUUID, disableOnDisconnect: Bool = false) {
        BleLogger.trace("Automatically enable characteristic notification \(chr.uuidString) on connect. Disable on disconnect: \(disableOnDisconnect)")
        addCharacteristicNotification(chr, disableOnDisconnect: disableOnDisconnect)
    }

    /// Enable characteristic notification/indication.
    func enableCharacteristicNotification(chr: CBUUID, disableOnDisconnect: Bool = false) -> AnyPublisher<Never, Error> {
        return gattAsyncVoidPublisher { [weak self] in
            guard let self = self else { return }
            try await self.enableCharacteristicNotificationAsync(chr: chr, disableOnDisconnect: disableOnDisconnect)
        }
    }

    /// Disable characteristic notification/indication.
    func disableCharacteristicNotification(chr: CBUUID) -> AnyPublisher<Never, Error> {
        return gattAsyncVoidPublisher { [weak self] in
            guard let self = self else { return }
            try await self.disableCharacteristicNotificationAsync(chr: chr)
        }
    }

    func containsCharacteristic(_ chr: CBUUID) -> Bool {
        return characteristics.contains(chr)
    }

    func containsNotifyCharacteristic(_ chr: CBUUID) -> Bool {
        return notificationCharacteristics.contains { $0.uuid.isEqual(chr) }
    }

    func containsReadCharacteristic(_ chr: CBUUID) -> Bool {
        return characteristicsRead.contains(chr)
    }

    func getNotificationCharacteristicState(_ chr: CBUUID) -> AtomicInteger? {
        return notificationCharacteristics.first { $0.uuid.isEqual(chr) }?.state ?? nil
    }

    public func disconnected() {
        serviceDiscovered.set(false)
        mtuSize = 20
        availableCharacteristics.removeAll()
        availableReadableCharacteristics.removeAll()
        notificationCharacteristics.forEach { pair in
            pair.state.set(ATT_NOTIFY_OR_INDICATE_STATE_UNKNOWN)
        }
        notificationWaitObservers.list().forEach { $0.resolve(with: .failure(BleGattException.gattDisconnected)) }
        notificationWaitObservers.removeAll()
        serviceWaitObservers.list().forEach { $0.resolve(with: .failure(BleGattException.gattDisconnected)) }
        serviceWaitObservers.removeAll()
        cancellables.removeAll()
    }

    public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        assert(false, "processServiceData not overridden by parent class")
    }

    /// Call when notify descriptor is written on Gatt service.
    public func notifyDescriptorWritten(_ chr: CBUUID, enabled: Bool, err: Int) {
        BleLogger.trace("GATT Base notifyDescriptorWritten for chr: \(chr) enabled: \(enabled) err \(err)")

        if let notifChr = notificationCharacteristics.first(where: { $0.uuid.isEqual(chr) }) {
            if err == 0 && enabled {
                notifChr.state.set(ATT_NOTIFY_OR_INDICATE_ON)
            } else if err == 0 && !enabled {
                notifChr.state.set(ATT_NOTIFY_OR_INDICATE_OFF)
            } else {
                notifChr.state.set(ATT_NOTIFY_OR_INDICATE_ERROR)
            }
        }

        let list = notificationWaitObservers.list().filter { $0.uuid.isEqual(chr) }
        for object in list {
            guard object.isActive() else { continue }
            if err == 0 {
                object.resolve(with: .success(()))
            } else {
                object.resolve(with: .failure(BleGattException.gattCharacteristicNotifyError(
                    errorCode: err,
                    errorDescription: "notify description write failed"
                )))
            }
        }
        notificationWaitObservers.remove { observer in
            list.contains { $0 === observer }
        }
    }

    public func serviceDataWritten(_ chr: CBUUID, err: Int) {
        // implement if needed
    }

    public func processCharacteristicDiscovered(_ characteristic: CBUUID, properties: UInt) {
        if !availableCharacteristics.list().contains(characteristic) {
            availableCharacteristics.append(characteristic)
        }
        if !availableReadableCharacteristics.list().contains(characteristic) &&
            (properties & CBCharacteristicProperties.read.rawValue) != 0 {
            availableReadableCharacteristics.append(characteristic)
        }
    }

    public func clientReady(_ checkConnection: Bool) -> AnyPublisher<Never, Error> {
        return Empty<Never, Error>().eraseToAnyPublisher()
    }

    public func setServiceDiscovered(_ value: Bool) {
        serviceDiscovered.set(value)
        if value {
            let observers = serviceWaitObservers.list()
            serviceWaitObservers.removeAll()
            observers.forEach { $0.resolve(with: .success(())) }
        }
    }

    public func isServiceDiscovered() -> Bool {
        return serviceDiscovered.get()
    }

    public func serviceBelongsToClient(_ uuid: CBUUID) -> Bool {
        return serviceUuid.isEqual(uuid)
    }

    public func setMtu(_ mtuSize: Int) {
        self.mtuSize = mtuSize
    }

    func hasAllAvailableCharacteristics(_ list: [CBUUID: AnyObject]) -> Bool {
        return Set(availableCharacteristics.list()).isSubset(of: Set(list.keys))
    }

    func hasAllAvailableReadableCharacteristics(_ list: [CBUUID: AnyObject]) -> Bool {
        return Set(availableReadableCharacteristics.list()).isSubset(of: Set(list.keys))
    }

    public func isCharacteristicNotificationEnabled(_ uuid: CBUUID) -> Bool {
        if let integer = getNotificationCharacteristicState(uuid) {
            return integer.get() == 0
        }
        return false
    }

    public func waitDiscovered(checkConnection: Bool) -> AnyPublisher<Never, Error> {
        return Deferred {
            Future<Void, Error> { [weak self] promise in
                guard let self = self else { promise(.failure(BleGattException.gattDisconnected)); return }
                if !checkConnection || self.gattServiceTransmitter?.isConnected() ?? false {
                    if self.serviceDiscovered.get() == true {
                        promise(.success(()))
                    } else {
                        let observer = ServiceWaitObserver(promise)
                        self.serviceWaitObservers.append(observer)
                    }
                } else {
                    promise(.failure(BleGattException.gattDisconnected))
                }
            }
            .handleEvents(receiveCancel: { [weak self] in
                // Cannot identify individual observer by closure identity — mark all as
                // resolved to avoid a double-resume if the service is discovered after
                // cancellation, then remove them all.  This is safe because ServiceWaitObserver
                // resolves exactly once; the promise is already satisfied by the cancel path.
                self?.serviceWaitObservers.remove { _ in true }
            })
        }
        .ignoreOutput()
        .eraseToAnyPublisher()
    }

    /// Waits for a notification to be enabled.
    public func waitNotificationEnabled(_ chr: CBUUID, checkConnection: Bool) -> AnyPublisher<Never, Error> {
        return waitNotification(chr, checkConnection, toBeEnabled: true)
    }

    /// Tear down the client before connection is closed.
    public func tearDown() -> AnyPublisher<Never, Error> {
        if let notification = notificationCharacteristics.first(where: {
            $0.state.get() == ATT_NOTIFY_OR_INDICATE_ON && $0.disableOnDisconnect
        }) {
            return disableCharacteristicNotification(chr: notification.uuid)
        } else {
            return Empty<Never, Error>().eraseToAnyPublisher()
        }
    }

    private func enableCharacteristicNotificationAsync(chr: CBUUID, disableOnDisconnect: Bool = false) async throws {
        if !containsNotifyCharacteristic(chr) {
            BleLogger.trace("GATT Base request notification enable for chr: \(chr)")
            addCharacteristicNotification(chr, disableOnDisconnect: disableOnDisconnect)
            try await writeCharacteristicNotificationAsync(chr: chr, enable: true)
        }
    }

    private func disableCharacteristicNotificationAsync(chr: CBUUID) async throws {
        if containsNotifyCharacteristic(chr) {
            BleLogger.trace("GATT Base request notification disable for chr: \(chr)")
            try await writeCharacteristicNotificationAsync(chr: chr, enable: false)
        }
    }

    private func writeCharacteristicNotificationAsync(chr: CBUUID, enable: Bool) async throws {
        if let notifChr = notificationCharacteristics.first(where: { $0.uuid.isEqual(chr) }) {
            notifChr.state.set(ATT_NOTIFY_OR_INDICATE_STATE_UNKNOWN)
        }
        try gattServiceTransmitter?.setCharacteristicNotify(
            self, serviceUuid: serviceUuid, characteristicUuid: chr, notify: enable)
        try await waitNotificationAsync(chr, true, toBeEnabled: enable)
    }

    private func addCharacteristicNotification(_ chr: CBUUID, disableOnDisconnect: Bool = false) {
        characteristics.insert(chr)
        if !containsNotifyCharacteristic(chr) {
            let chrNotification = notificationChr(
                uuid: chr,
                state: AtomicInteger.init(initialValue: ATT_NOTIFY_OR_INDICATE_STATE_UNKNOWN),
                disableOnDisconnect: disableOnDisconnect
            )
            notificationCharacteristics.append(chrNotification)
        }
    }

    func removeCharacteristicNotification(_ chr: CBUUID) {
        BleLogger.trace("Remove notification characteristic for \(chr.uuidString)")
        notificationCharacteristics.removeAll { $0.uuid.isEqual(chr) }
        characteristics.remove(chr)
    }

    private func waitNotification(_ chr: CBUUID, _ checkConnection: Bool, toBeEnabled: Bool) -> AnyPublisher<Never, Error> {
        return Deferred {
            Future<Void, Error> { [weak self] promise in
                self?.startWaitNotification(chr, checkConnection, toBeEnabled: toBeEnabled, promise: promise)
                    ?? promise(.failure(BleGattException.gattDisconnected))
            }
            .handleEvents(receiveCancel: { [weak self] in
                self?.notificationWaitObservers.remove {
                    if $0.uuid.isEqual(chr) { $0.markCancelled(); return true }
                    return false
                }
            })
        }
        .ignoreOutput()
        .eraseToAnyPublisher()
    }

    private func waitNotificationAsync(_ chr: CBUUID, _ checkConnection: Bool, toBeEnabled: Bool) async throws {
        let token = NotificationWaitCancellationToken()
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
                startWaitNotification(chr, checkConnection, toBeEnabled: toBeEnabled) { result in
                    continuation.resume(with: result)
                } onObserver: { observer in
                    token.set(observer)
                }
            }
        } onCancel: { [weak self] in
            guard let observer = token.cancel() else { return }
            self?.notificationWaitObservers.remove { $0 === observer }
        }
    }

    private func startWaitNotification(
        _ chr: CBUUID,
        _ checkConnection: Bool,
        toBeEnabled: Bool,
        promise: @escaping (Result<Void, Error>) -> Void,
        onObserver: ((NotificationWaitObserver) -> Void)? = nil
    ) {
        let integer = getNotificationCharacteristicState(chr)
        guard integer != nil else {
            promise(.failure(BleGattException.gattCharacteristicNotFound))
            return
        }
        guard !checkConnection || gattServiceTransmitter?.isConnected() ?? false else {
            promise(.failure(BleGattException.gattDisconnected))
            return
        }
        if integer?.get() != ATT_NOTIFY_OR_INDICATE_STATE_UNKNOWN {
            if (toBeEnabled && integer?.get() == ATT_NOTIFY_OR_INDICATE_ON)
                || (!toBeEnabled && integer?.get() == ATT_NOTIFY_OR_INDICATE_OFF) {
                promise(.success(()))
            } else if toBeEnabled && integer?.get() == ATT_NOTIFY_OR_INDICATE_OFF {
                promise(.failure(BleGattException.gattCharacteristicNotifyNotEnabled))
            } else if !toBeEnabled && integer?.get() == ATT_NOTIFY_OR_INDICATE_ON {
                promise(.failure(BleGattException.gattCharacteristicNotifyNotDisabled))
            } else {
                promise(.failure(BleGattException.gattCharacteristicNotifyError(
                    errorCode: -1,
                    errorDescription: "notify description failed. Waiting for enable: \(toBeEnabled). Error code is not known report as -1"
                )))
            }
        } else {
            let subscriber = NotificationWaitObserver(promise, uuid: chr)
            notificationWaitObservers.append(subscriber)
            onObserver?(subscriber)
        }
    }
}

private func gattAsyncVoidPublisher(_ operation: @escaping () async throws -> Void) -> AnyPublisher<Never, Error> {
    Deferred {
        var task: Task<Void, Never>?
        return Future<Void, Error> { promise in
            task = Task {
                do {
                    try await operation()
                    promise(.success(()))
                } catch is CancellationError {
                    promise(.success(()))
                } catch {
                    promise(.failure(error))
                }
            }
        }
            .handleEvents(receiveCancel: { task?.cancel() })
            .ignoreOutput()
            .eraseToAnyPublisher()
    }
    .eraseToAnyPublisher()
}

public func == (lhs: BleGattClientBase, rhs: BleGattClientBase) -> Bool {
    return lhs === rhs
}
