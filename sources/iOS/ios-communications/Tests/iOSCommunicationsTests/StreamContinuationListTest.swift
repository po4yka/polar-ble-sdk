// Copyright © 2026 Polar. All rights reserved.

import XCTest
import Foundation
import CoreBluetooth
@testable import iOSCommunications

final class StreamContinuationListTest: XCTestCase {

    func testMakeStream_checkConnectionFalse_streamIsCreated() async throws {
        try assertStreamRuntimePolicyVectorContains(fileName: "unchecked-subscription-policy.json", vectorId: "unchecked-subscription-policy")

        // Arrange
        let list = StreamContinuationList<Int>()

        // Act
        let stream = list.makeStream(transport: nil, checkConnection: false)

        // Assert – stream exists and is not immediately finished
        list.finish(throwing: CancellationError())
        var count = 0
        do {
            for try await _ in stream { count += 1 }
        } catch { /* expected CancellationError */ }
        XCTAssertEqual(count, 0)
    }

    func testMakeStream_checkConnectionTrue_transportNotConnected_finishesWithGattDisconnected() async throws {
        try assertStreamRuntimePolicyVectorContains(fileName: "initial-disconnected-policy.json", vectorId: "initial-disconnected-policy")

        // Arrange
        let list = StreamContinuationList<Int>()
        let disconnectedTransport = MockDisconnectedTransport()

        // Act
        let stream = list.makeStream(transport: disconnectedTransport, checkConnection: true)

        // Assert
        do {
            for try await _ in stream { XCTFail("Should not yield values") }
            XCTFail("Should have thrown")
        } catch let error as BleGattException {
            if case .gattDisconnected = error { /* expected */ } else {
                XCTFail("Expected gattDisconnected, got \(error)")
            }
        }
    }

    func testMakeStream_checkConnectionTrue_transportConnected_streamIsCreated() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        let connectedTransport = MockConnectedTransport()

        // Act
        let stream = list.makeStream(transport: connectedTransport, checkConnection: true)

        // Assert – stream is live (not immediately finished); close it cleanly
        list.finish(throwing: CancellationError())
        do {
            for try await _ in stream {}
        } catch is CancellationError { /* expected */ }
    }

    func testMakeStream_checkConnectionTrue_transportNil_finishesWithGattDisconnected() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()

        // Act
        let stream = list.makeStream(transport: nil, checkConnection: true)

        // Assert
        do {
            for try await _ in stream { XCTFail("Should not yield values") }
            XCTFail("Should have thrown")
        } catch let error as BleGattException {
            if case .gattDisconnected = error { /* expected */ } else {
                XCTFail("Expected gattDisconnected, got \(error)")
            }
        }
    }

    func testYield_singleStream_receivesValue() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        let stream = list.makeStream(transport: nil, checkConnection: false)

        // Act
        list.yield(42)
        list.finish(throwing: CancellationError())

        // Assert
        var received: [Int] = []
        do {
            for try await value in stream { received.append(value) }
        } catch is CancellationError {}
        XCTAssertEqual(received, [42])
    }

    func testYield_multipleStreams_allReceiveValue() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        let stream1 = list.makeStream(transport: nil, checkConnection: false)
        let stream2 = list.makeStream(transport: nil, checkConnection: false)

        // Act
        list.yield(7)
        list.finish(throwing: CancellationError())

        // Assert
        async let r1: [Int] = {
            var vals: [Int] = []
            do { for try await v in stream1 { vals.append(v) } } catch is CancellationError {}
            return vals
        }()
        async let r2: [Int] = {
            var vals: [Int] = []
            do { for try await v in stream2 { vals.append(v) } } catch is CancellationError {}
            return vals
        }()
        let (received1, received2) = try await (r1, r2)
        XCTAssertEqual(received1, [7])
        XCTAssertEqual(received2, [7])
    }

    func testYield_multipleValues_receivedInOrder() async throws {
        try assertStreamRuntimePolicyVectorContains(fileName: "ordered-emissions-policy.json", vectorId: "ordered-emissions-policy")

        // Arrange
        let list = StreamContinuationList<Int>()
        let stream = list.makeStream(transport: nil, checkConnection: false)

        // Act
        list.yield(1)
        list.yield(2)
        list.yield(3)
        list.finish(throwing: CancellationError())

        // Assert
        var received: [Int] = []
        do {
            for try await value in stream { received.append(value) }
        } catch is CancellationError {}
        XCTAssertEqual(received, [1, 2, 3])
    }

    func testFinish_propagatesErrorToAllStreams() async throws {
        try assertStreamRuntimePolicyVectorContains(fileName: "terminal-error-policy.json", vectorId: "terminal-error-policy")

        // Arrange
        let list = StreamContinuationList<Int>()
        let stream1 = list.makeStream(transport: nil, checkConnection: false)
        let stream2 = list.makeStream(transport: nil, checkConnection: false)

        // Act
        list.finish(throwing: BleGattException.gattDisconnected)

        // Assert
        do {
            for try await _ in stream1 { XCTFail("stream1 should not yield values") }
            XCTFail("stream1 should have thrown")
        } catch let error as BleGattException {
            if case .gattDisconnected = error {} else {
                XCTFail("stream1 expected gattDisconnected, got \(error)")
            }
        }
        do {
            for try await _ in stream2 { XCTFail("stream2 should not yield values") }
            XCTFail("stream2 should have thrown")
        } catch let error as BleGattException {
            if case .gattDisconnected = error {} else {
                XCTFail("stream2 expected gattDisconnected, got \(error)")
            }
        }
    }

    func testFinish_removesAllEntries() async throws {
        try assertStreamRuntimePolicyVectorContains(fileName: "duplicate-completion-policy.json", vectorId: "duplicate-completion-policy")

        // Arrange
        let list = StreamContinuationList<Int>()
        _ = list.makeStream(transport: nil, checkConnection: false)
        _ = list.makeStream(transport: nil, checkConnection: false)

        // Act
        list.finish(throwing: CancellationError())
        list.finish(throwing: CancellationError())

        // Assert – after finish the list is empty
        XCTAssertTrue(list.isEmpty)
    }

    func testYield_afterFinish_doesNotEmitValue() async throws {
        try assertStreamRuntimePolicyVectorContains(fileName: "late-emission-after-completion-policy.json", vectorId: "late-emission-after-completion-policy")

        let list = StreamContinuationList<Int>()
        let stream = list.makeStream(transport: nil, checkConnection: false)

        list.finish(throwing: CancellationError())
        list.yield(99)

        var received: [Int] = []
        do {
            for try await value in stream { received.append(value) }
        } catch is CancellationError {}
        XCTAssertEqual(received, [])
        XCTAssertTrue(list.isEmpty)
    }

    func testConsumerCancellation_removesEntry() async throws {
        try assertStreamRuntimePolicyVectorContains(fileName: "consumer-cancellation-policy.json", vectorId: "consumer-cancellation-policy")

        let list = StreamContinuationList<Int>()
        let stream = list.makeStream(transport: nil, checkConnection: false)
        let task = Task {
            do {
                for try await _ in stream {}
            } catch is CancellationError {}
        }

        await Task.yield()
        XCTAssertFalse(list.isEmpty)

        task.cancel()
        _ = await task.result
        try await Task.sleep(nanoseconds: 10_000_000)
        XCTAssertTrue(list.isEmpty)
    }

    func testConsumerCancellation_suppressesLateEvents() async throws {
        try assertStreamRuntimePolicyVectorContains(fileName: "consumer-cancellation-late-events-policy.json", vectorId: "consumer-cancellation-late-events-policy")

        let list = StreamContinuationList<Int>()
        let stream = list.makeStream(transport: nil, checkConnection: false)
        var received: [Int] = []
        let task = Task {
            do {
                for try await value in stream {
                    received.append(value)
                }
            } catch is CancellationError {}
        }

        list.yield(1)
        await Task.yield()
        task.cancel()
        _ = await task.result
        try await Task.sleep(nanoseconds: 10_000_000)
        list.yield(2)
        list.finish(throwing: BleGattException.gattDisconnected)
        try await Task.sleep(nanoseconds: 10_000_000)

        XCTAssertEqual(received, [1])
        XCTAssertTrue(list.isEmpty)
    }

    func testDisconnectAfterSubscription_finishesWithGattDisconnectedAndRemovesEntry() async throws {
        try assertStreamRuntimePolicyVectorContains(fileName: "disconnect-after-subscription-policy.json", vectorId: "disconnect-after-subscription-policy")

        let list = StreamContinuationList<Int>()
        let stream = list.makeStream(transport: nil, checkConnection: false)

        list.finish(throwing: BleGattException.gattDisconnected)

        do {
            for try await _ in stream { XCTFail("Should not yield values") }
            XCTFail("Should have thrown")
        } catch let error as BleGattException {
            if case .gattDisconnected = error { /* expected */ } else {
                XCTFail("Expected gattDisconnected, got \(error)")
            }
        }
        try await Task.sleep(nanoseconds: 10_000_000)
        XCTAssertTrue(list.isEmpty)
    }

    private func assertStreamRuntimePolicyVectorContains(fileName: String, vectorId: String) throws {
        let vector = try loadStreamRuntimeVector(fileName: fileName)
        let input = try XCTUnwrap(vector["input"] as? [String: Any], vectorId)
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], vectorId)
        let execution = try XCTUnwrap(vector["execution"] as? [String: Any], vectorId)
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any], vectorId)
        let contract = try XCTUnwrap(STREAM_RUNTIME_POLICY_CONTRACTS[vectorId], vectorId)

        XCTAssertEqual(vector["id"] as? String, vectorId)
        XCTAssertEqual(input["kind"] as? String, contract.inputKind)
        XCTAssertEqual(execution["kind"] as? String, "fake-stream-runtime-policy")
        XCTAssertEqual(execution["transport"] as? String, contract.executionTransport)
        XCTAssertEqual(execution["wallClockSafe"] as? Bool, true)
        XCTAssertEqual(expected["commonDecision"] as? String, contract.commonDecision)
        XCTAssertEqual(consumerTests["android"] as? [String], ["com.polar.androidcommunications.common.ble.ChannelUtilsTests"])
        XCTAssertEqual(consumerTests["ios"] as? [String], ["StreamContinuationListTest"])
        XCTAssertEqual(consumerTests["commonPrototype"] as? [String], ["com.polar.sharedtest.StreamRuntimePolicyCommonTest"])
    }

    func testStreamRuntimeReadinessManifestIsPinnedBeforeStreamRuntimeMigration() throws {
        let file = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/stream-runtime/stream-runtime-readiness.json")
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: file)) as? [String: Any])
        let input = try XCTUnwrap(vector["input"] as? [String: Any], "stream-runtime-readiness")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "stream-runtime-readiness")
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any], "stream-runtime-readiness")
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], "stream-runtime-readiness")
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], "stream-runtime-readiness")
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], "stream-runtime-readiness")

        XCTAssertEqual(vector["id"] as? String, "stream-runtime-readiness")
        XCTAssertEqual(input["kind"] as? String, "streamRuntimeReadiness")
        XCTAssertEqual(STREAM_RUNTIME_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, "stream-runtime-readiness")
        XCTAssertEqual(STREAM_RUNTIME_READINESS_FAMILIES, requiredFamilies, "stream-runtime-readiness")
        XCTAssertEqual(STREAM_RUNTIME_READINESS_FAMILIES, coveredFamilies, "stream-runtime-readiness")
        let commonDecision = try XCTUnwrap(expected["commonDecision"] as? String, "stream-runtime-readiness")
        XCTAssertEqual(commonDecision, STREAM_RUNTIME_READINESS_COMMON_DECISION, "stream-runtime-readiness")
        let commonRuntimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], "stream-runtime-readiness")
        XCTAssertEqual(commonRuntimePrototype["status"] as? String, "executable shared commonTest runtime planning guard", "stream-runtime-readiness")
        XCTAssertEqual(commonRuntimePrototype["reason"] as? String, "Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", "stream-runtime-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "stream-runtime-readiness"), ["com.polar.androidcommunications.common.ble.ChannelUtilsTests"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "stream-runtime-readiness"), ["StreamContinuationListTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "stream-runtime-readiness"), ["com.polar.sharedtest.StreamRuntimePolicyCommonTest"])
    }

    private func loadStreamRuntimeVector(fileName: String) throws -> [String: Any] {
        let file = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/stream-runtime/\(fileName)")
        return try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: file)) as? [String: Any], fileName)
    }

    func testServiceReadinessPolicyVectorIsPinnedBeforeSharedRuntimeDelegation() throws {
        let vector = try loadFakeTransportVector(fileName: "service-readiness-policy.json")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "service-readiness-policy")

        try assertFakeTransportPolicyVector(vector, id: "service-readiness-policy", kind: "serviceReadinessPolicy", commonDecision: SERVICE_READINESS_POLICY_COMMON_DECISION)
        XCTAssertEqual(expected["readyOutcome"] as? String, "complete")
        XCTAssertEqual(expected["readyChecks"] as? [String], ["pmd", "pmd", "pmd"])
        XCTAssertEqual(expected["missingOutcome"] as? String, "timeout")
        XCTAssertEqual(expected["missingTimeoutLabel"] as? String, "service-readiness")
        XCTAssertEqual(expected["missingChecks"] as? [String], ["psftp", "psftp"])
    }

    func testScriptedCommandOutcomesPolicyVectorIsPinnedBeforeSharedRuntimeDelegation() throws {
        let vector = try loadFakeTransportVector(fileName: "scripted-command-outcomes-policy.json")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "scripted-command-outcomes-policy")

        try assertFakeTransportPolicyVector(vector, id: "scripted-command-outcomes-policy", kind: "scriptedCommandOutcomesPolicy", commonDecision: SCRIPTED_COMMAND_OUTCOMES_POLICY_COMMON_DECISION)
        XCTAssertEqual(expected["outcomes"] as? [String], ["bytes:0102", "response-error:103:missing", "transport-error:link lost", "complete"])
        XCTAssertEqual(expected["commands"] as? [String], ["READ:/U/0/DEVICE.BPB", "WRITE:/U/0/SETTINGS.BPB:0a0b", "SUBSCRIBE:d2h", "UNSUBSCRIBE:d2h"])
    }

    func testDelayedResponsePolicyVectorIsPinnedBeforeSharedRuntimeDelegation() throws {
        let vector = try loadFakeTransportVector(fileName: "delayed-response-policy.json")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "delayed-response-policy")

        try assertFakeTransportPolicyVector(vector, id: "delayed-response-policy", kind: "delayedResponsePolicy", commonDecision: DELAYED_RESPONSE_POLICY_COMMON_DECISION, commonRuntimePrototypeStatus: "executable shared commonTest delayed-response contract")
        XCTAssertEqual(expected["pollOutcomes"] as? [String], ["timeout:delayed-response", "bytes:0708"])
        XCTAssertEqual(expected["polls"] as? [String], ["/U/0/DELAYED.BPB@0", "/U/0/DELAYED.BPB@150"])
        XCTAssertEqual(expected["finalTimeMillis"] as? Int, 150)
    }

    func testReconnectAfterFailurePolicyVectorIsPinnedBeforeSharedRuntimeDelegation() throws {
        let vector = try loadFakeTransportVector(fileName: "reconnect-after-failure-policy.json")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "reconnect-after-failure-policy")

        try assertFakeTransportPolicyVector(vector, id: "reconnect-after-failure-policy", kind: "reconnectAfterFailurePolicy", commonDecision: RECONNECT_AFTER_FAILURE_POLICY_COMMON_DECISION, commonRuntimePrototypeStatus: "executable shared commonTest reconnect-after-failure contract")
        XCTAssertEqual(expected["outcomes"] as? [String], ["transport-error:disconnected", "complete", "bytes:0506"])
        XCTAssertEqual(expected["commands"] as? [String], ["READ:/U/0/DEVICE.BPB", "RECONNECT:transport", "READ:/U/0/DEVICE.BPB"])
        XCTAssertEqual(expected["connectedAfterReconnect"] as? Bool, true)
    }

    func testRetryDelayPolicyVectorIsPinnedBeforeSharedRuntimeDelegation() throws {
        let vector = try loadFakeTransportVector(fileName: "retry-delay-policy.json")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "retry-delay-policy")

        try assertFakeTransportPolicyVector(vector, id: "retry-delay-policy", kind: "retryDelayPolicy", commonDecision: RETRY_DELAY_POLICY_COMMON_DECISION, commonRuntimePrototypeStatus: "executable shared commonTest retry-delay scheduler contract")
        XCTAssertEqual(expected["retryCount"] as? Int, 3)
        XCTAssertEqual(expected["retryTimesMillis"] as? [Int], [100, 300, 700])
        XCTAssertEqual(expected["finalTimeMillis"] as? Int, 700)
    }

    func testUnscriptedOperationTimeoutPolicyVectorIsPinnedBeforeSharedRuntimeDelegation() throws {
        let vector = try loadFakeTransportVector(fileName: "unscripted-operation-timeout-policy.json")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "unscripted-operation-timeout-policy")

        try assertFakeTransportPolicyVector(vector, id: "unscripted-operation-timeout-policy", kind: "unscriptedOperationTimeoutPolicy", commonDecision: UNSCRIPTED_OPERATION_TIMEOUT_POLICY_COMMON_DECISION)
        XCTAssertEqual(expected["outcome"] as? String, "timeout:unscripted-operation")
        XCTAssertEqual(expected["commands"] as? [String], ["READ:/missing"])
    }

    func testVirtualClockTimeoutPolicyVectorIsPinnedBeforeSharedRuntimeDelegation() throws {
        let vector = try loadFakeTransportVector(fileName: "virtual-clock-timeout-policy.json")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "virtual-clock-timeout-policy")

        try assertFakeTransportPolicyVector(vector, id: "virtual-clock-timeout-policy", kind: "virtualClockTimeoutPolicy", commonDecision: VIRTUAL_CLOCK_TIMEOUT_POLICY_COMMON_DECISION)
        XCTAssertEqual(expected["timedOutBeforeFinalAdvance"] as? Bool, false)
        XCTAssertEqual(expected["timedOutAfterFinalAdvance"] as? Bool, true)
        XCTAssertEqual(expected["finalTimeMillis"] as? Int, 500)
    }

    func testFakeTransportReadinessManifestIsPinnedBeforeSharedRuntimeDelegation() throws {
        let file = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/fake-transport/fake-transport-readiness.json")
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: file)) as? [String: Any])
        let input = try XCTUnwrap(vector["input"] as? [String: Any], "fake-transport-readiness")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "fake-transport-readiness")
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any], "fake-transport-readiness")
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], "fake-transport-readiness")
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], "fake-transport-readiness")
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], "fake-transport-readiness")

        XCTAssertEqual(vector["id"] as? String, "fake-transport-readiness")
        XCTAssertEqual(input["kind"] as? String, "fakeTransportReadiness")
        XCTAssertEqual(FAKE_TRANSPORT_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, "fake-transport-readiness")
        XCTAssertEqual(FAKE_TRANSPORT_READINESS_FAMILIES, requiredFamilies, "fake-transport-readiness")
        XCTAssertEqual(FAKE_TRANSPORT_READINESS_FAMILIES, coveredFamilies, "fake-transport-readiness")
        let commonDecision = try XCTUnwrap(expected["commonDecision"] as? String, "fake-transport-readiness")
        XCTAssertEqual(commonDecision, FAKE_TRANSPORT_READINESS_COMMON_DECISION, "fake-transport-readiness")
        let commonRuntimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], "fake-transport-readiness")
        XCTAssertEqual(commonRuntimePrototype["status"] as? String, "executable shared commonTest fake-transport readiness guard", "fake-transport-readiness")
        XCTAssertEqual(commonRuntimePrototype["wallClockSafe"] as? Bool, true, "fake-transport-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "fake-transport-readiness"), ["com.polar.testutils.FakeTransportContractTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "fake-transport-readiness"), ["StreamContinuationListTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "fake-transport-readiness"), ["com.polar.sharedtest.FakeTransportContractCommonTest"])
    }

    private func loadFakeTransportVector(fileName: String) throws -> [String: Any] {
        let file = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/fake-transport/\(fileName)")
        return try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: file)) as? [String: Any], fileName)
    }

    private func assertFakeTransportPolicyVector(_ vector: [String: Any], id: String, kind: String, commonDecision: String, commonRuntimePrototypeStatus: String = "executable shared commonTest fake-transport contract", file: StaticString = #filePath, line: UInt = #line) throws {
        let input = try XCTUnwrap(vector["input"] as? [String: Any], id, file: file, line: line)
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id, file: file, line: line)
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any], id, file: file, line: line)
        let execution = try XCTUnwrap(vector["execution"] as? [String: Any], id, file: file, line: line)
        let commonRuntimePrototype = try XCTUnwrap(execution["commonRuntimePrototype"] as? [String: Any], id, file: file, line: line)

        XCTAssertEqual(vector["id"] as? String, id, file: file, line: line)
        XCTAssertEqual(input["kind"] as? String, kind, file: file, line: line)
        XCTAssertEqual(expected["commonDecision"] as? String, commonDecision, file: file, line: line)
        XCTAssertEqual(consumerTests["android"] as? [String], ["com.polar.testutils.FakeTransportContractTest"], file: file, line: line)
        XCTAssertEqual(consumerTests["ios"] as? [String], ["StreamContinuationListTest"], file: file, line: line)
        XCTAssertEqual(consumerTests["commonPrototype"] as? [String], ["com.polar.sharedtest.FakeTransportContractCommonTest"], file: file, line: line)
        XCTAssertEqual(commonRuntimePrototype["status"] as? String, commonRuntimePrototypeStatus, file: file, line: line)
        XCTAssertEqual(commonRuntimePrototype["wallClockSafe"] as? Bool, true, file: file, line: line)
    }


    func testIsEmpty_noStreams_returnsTrue() {
        let list = StreamContinuationList<Int>()
        XCTAssertTrue(list.isEmpty)
    }

    func testIsEmpty_withActiveStream_returnsFalse() {
        let list = StreamContinuationList<Int>()
        let stream = list.makeStream(transport: nil, checkConnection: false)
        XCTAssertFalse(list.isEmpty)
        // Keep stream alive until after the assertion
        _ = stream
    }

    func testIsEmpty_afterStreamTerminates_returnsTrue() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        let stream = list.makeStream(transport: nil, checkConnection: false)

        // Act – finish the stream and drain it so onTermination fires
        list.finish(throwing: CancellationError())
        do { for try await _ in stream {} } catch is CancellationError {}

        // Assert – onTermination should have removed the entry
        // Give the onTermination callback a moment to execute
        try await Task.sleep(nanoseconds: 10_000_000)
        XCTAssertTrue(list.isEmpty)
    }

    func testYield_concurrentAccess_doesNotCrash() async throws {
        // Arrange
        let list = StreamContinuationList<Int>()
        let streamCount = 10
        var streams: [AsyncThrowingStream<Int, Error>] = []
        for _ in 0..<streamCount {
            streams.append(list.makeStream(transport: nil, checkConnection: false))
        }

        // Act – yield from multiple concurrent tasks
        await withTaskGroup(of: Void.self) { group in
            for i in 0..<50 {
                group.addTask { list.yield(i) }
            }
        }
        list.finish(throwing: CancellationError())

        // Assert – all streams complete without crashing
        await withTaskGroup(of: Void.self) { group in
            for stream in streams {
                group.addTask {
                    do { for try await _ in stream {} } catch {}
                }
            }
        }
    }
}

private let STREAM_RUNTIME_READINESS_POLICY_VECTOR_PATHS = [
    "sdk/stream-runtime/ordered-emissions-policy.json",
    "sdk/stream-runtime/terminal-error-policy.json",
    "sdk/stream-runtime/initial-disconnected-policy.json",
    "sdk/stream-runtime/unchecked-subscription-policy.json",
    "sdk/stream-runtime/consumer-cancellation-policy.json",
    "sdk/stream-runtime/consumer-cancellation-late-events-policy.json",
    "sdk/stream-runtime/disconnect-after-subscription-policy.json",
    "sdk/stream-runtime/duplicate-completion-policy.json",
    "sdk/stream-runtime/late-emission-after-completion-policy.json"
]

private let STREAM_RUNTIME_READINESS_FAMILIES = [
    "ordered-emission-before-completion",
    "terminal-error-propagation",
    "terminal-error-observer-cleanup",
    "checked-disconnected-fails-before-observer",
    "unchecked-subscription-skips-connection-check",
    "consumer-cancellation-observer-cleanup",
    "consumer-cancellation-upstream-cancel",
    "consumer-cancellation-idempotence",
    "post-cancellation-late-event-suppression",
    "disconnect-after-subscription-terminal",
    "disconnect-after-subscription-observer-cleanup",
    "disconnect-after-subscription-upstream-cancel",
    "duplicate-completion-idempotence",
    "post-completion-emission-suppression",
    "active-observer-count-gate",
    "platform-stream-vector-reference-gate",
    "compile-verification-gate"
]

private let STREAM_RUNTIME_POLICY_CONTRACTS = [
    "ordered-emissions-policy": StreamRuntimePolicyContract(inputKind: "genericStreamEmissionPolicy", executionTransport: "generic-stream-emission", commonDecision: "Stream values emitted before terminal completion must be delivered in source order."),
    "terminal-error-policy": StreamRuntimePolicyContract(inputKind: "genericStreamTerminalErrorPolicy", executionTransport: "generic-stream-terminal-error", commonDecision: "Terminal stream errors must propagate to consumers and clear observers without reporting normal completion."),
    "initial-disconnected-policy": StreamRuntimePolicyContract(inputKind: "genericStreamConnectionGuardPolicy", executionTransport: "generic-stream-connection-guard", commonDecision: "A checked stream subscription that starts disconnected must fail before observer registration or upstream work starts."),
    "unchecked-subscription-policy": StreamRuntimePolicyContract(inputKind: "genericStreamConnectionGuardPolicy", executionTransport: "generic-stream-connection-guard", commonDecision: "An unchecked stream subscription must register the observer without querying transport connection state."),
    "consumer-cancellation-policy": StreamRuntimePolicyContract(inputKind: "genericStreamCancellationPolicy", executionTransport: "generic-stream-cancellation", commonDecision: "Consumer cancellation must remove the observer, cancel upstream work once, and remain idempotent."),
    "consumer-cancellation-late-events-policy": StreamRuntimePolicyContract(inputKind: "genericStreamCancellationPolicy", executionTransport: "generic-stream-cancellation", commonDecision: "After consumer cancellation, late stream values, terminal errors, and completion signals must not surface or mutate terminal counters."),
    "disconnect-after-subscription-policy": StreamRuntimePolicyContract(inputKind: "genericStreamDisconnectPolicy", executionTransport: "generic-stream-disconnect", commonDecision: "A stream that disconnects after observer registration must terminate consumers, clear observers, and cancel upstream work without leaking an active subscription."),
    "duplicate-completion-policy": StreamRuntimePolicyContract(inputKind: "genericStreamCompletionPolicy", executionTransport: "generic-stream-completion", commonDecision: "Complete or finish signals after the first terminal completion must be idempotent and must not re-register observers."),
    "late-emission-after-completion-policy": StreamRuntimePolicyContract(inputKind: "genericStreamCompletionPolicy", executionTransport: "generic-stream-completion", commonDecision: "Values emitted after terminal completion must not surface to consumers and must not re-register observers.")
]

private let STREAM_RUNTIME_READINESS_COMMON_DECISION = "Generic stream runtime migration may proceed only after every stream runtime policy vector listed in this readiness manifest is executable from shared commonTest, Android ChannelUtils tests and iOS StreamContinuationList tests continue to reference the same vectors, ordered emissions, terminal errors, connection guards, consumer cancellation, post-cancellation late-event suppression, disconnect-after-subscription termination, duplicate completion, post-completion emission suppression, active observer cleanup, and upstream cancellation remain pinned, and the shared tests are compile-verified."

private struct StreamRuntimePolicyContract {
    let inputKind: String
    let executionTransport: String
    let commonDecision: String
}

private let FAKE_TRANSPORT_READINESS_POLICY_VECTOR_PATHS = [
    "sdk/fake-transport/scripted-command-outcomes-policy.json",
    "sdk/fake-transport/delayed-response-policy.json",
    "sdk/fake-transport/reconnect-after-failure-policy.json",
    "sdk/fake-transport/retry-delay-policy.json",
    "sdk/fake-transport/service-readiness-policy.json",
    "sdk/fake-transport/unscripted-operation-timeout-policy.json",
    "sdk/fake-transport/virtual-clock-timeout-policy.json"
]

private let FAKE_TRANSPORT_READINESS_FAMILIES = [
    "scripted-command-ordering",
    "write-payload-capture",
    "scripted-success-error-complete-outcomes",
    "delayed-response-polling",
    "delayed-response-release",
    "reconnect-after-failure",
    "reconnect-command-recording",
    "retry-delay-scheduling",
    "retry-count-observation",
    "unscripted-operation-timeout",
    "unscripted-command-recording",
    "service-readiness-delayed-success",
    "service-readiness-timeout",
    "readiness-attempt-ordering",
    "virtual-clock-timeout-boundary",
    "wall-clock-free-timeout-tests",
    "platform-facade-compatibility-gate",
    "compile-verification-gate"
]

private let SERVICE_READINESS_POLICY_COMMON_DECISION = "Service-readiness waits in shared runtime must be bounded, observable, deterministic, and free from wall-clock sleeps before feature readiness is delegated to KMP."
private let SCRIPTED_COMMAND_OUTCOMES_POLICY_COMMON_DECISION = "Shared fake transport must capture request order, write payload bytes, stream subscription targets, and scripted success/error/complete outcomes deterministically before runtime command planning delegates to KMP."
private let DELAYED_RESPONSE_POLICY_COMMON_DECISION = "Shared runtime tests must model delayed transport responses with a virtual clock, observable poll attempts, and a distinct pending label before delayed read, write, or notification orchestration delegates to KMP."
private let RECONNECT_AFTER_FAILURE_POLICY_COMMON_DECISION = "Shared fake transport must make reconnect-after-failure explicit, observable, and deterministic before retry or reconnect-aware runtime planning delegates to KMP."
private let RETRY_DELAY_POLICY_COMMON_DECISION = "Shared runtime retry tests must schedule retry delays on a virtual clock, assert retry count and elapsed retry times, and avoid wall-clock sleeps before retry policy delegates to KMP."
private let UNSCRIPTED_OPERATION_TIMEOUT_POLICY_COMMON_DECISION = "Shared fake transport must report unscripted operations as deterministic timeouts while still recording the attempted command before runtime code delegates to KMP."
private let VIRTUAL_CLOCK_TIMEOUT_POLICY_COMMON_DECISION = "Shared runtime timeout tests must advance virtual time deterministically and must not wait for wall-clock protocol constants."
private let FAKE_TRANSPORT_READINESS_COMMON_DECISION = "Fake-transport base runtime migration may proceed only after scripted command outcomes, delayed-response polling, reconnect-after-failure controls, retry-delay scheduling, unscripted-operation timeouts, service readiness, and virtual-clock timeout policy vectors remain executable from shared commonTest, Android and iOS guard the same vector paths, platform facade compatibility stays explicit, and the shared tests are compile-verified."

private class MockConnectedTransport: BleAttributeTransportProtocol {
    func isConnected() -> Bool { return true }
    func transmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, packet: Data, withResponse: Bool) throws {}
    func characteristicWith(uuid: CBUUID) throws -> CBCharacteristic? { return nil }
    func characteristicNameWith(uuid: CBUUID) -> String? { return nil }
    func readValue(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID) throws {}
    func setCharacteristicNotify(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, notify: Bool) throws {}
    func attributeOperationStarted() {}
    func attributeOperationFinished() {}
}

private class MockDisconnectedTransport: BleAttributeTransportProtocol {
    func isConnected() -> Bool { return false }
    func transmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, packet: Data, withResponse: Bool) throws {}
    func characteristicWith(uuid: CBUUID) throws -> CBCharacteristic? { return nil }
    func characteristicNameWith(uuid: CBUUID) -> String? { return nil }
    func readValue(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID) throws {}
    func setCharacteristicNotify(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, notify: Bool) throws {}
    func attributeOperationStarted() {}
    func attributeOperationFinished() {}
}
