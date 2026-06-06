//  Copyright © 2026 Polar. All rights reserved.

import XCTest

@testable import PolarBleSdk

private enum D2HNotificationTestError: Error {
    case lateFailure
    case serviceMissing
}

private let D2H_STREAM_RUNTIME_POLICY_SCENARIO_IDS = [
    "late-error-after-emitted-notification",
    "consumer-cancels-after-first-notification",
    "unknown-notification-between-known-values-is-filtered",
    "failed-subscribe-does-not-register-observer"
]

class PolarDeviceToHostNotificationsApiTests: XCTestCase {
    
    var mockClient: MockBlePsFtpClient!
    var mockSession: MockBleDeviceSession!
    var api: PolarBleApiImplWithMockSession!
    let deviceId = "123456"
    
    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
        mockSession = MockBleDeviceSession(mockFtpClient: mockClient)
        api = PolarBleApiImplWithMockSession(mockDeviceSession: mockSession)
    }
    
    override func tearDownWithError() throws {
        mockClient = nil
        mockSession = nil
        api = nil
    }

    func testD2HNotificationMappingUsesSharedPlannerWhenLinked() async throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("STOP_GPS_MEASUREMENT", PolarRuntimePlanner.d2hNotificationTypeName(notificationId: Protocol_PbPFtpDevToHostNotification.stopGpsMeasurement.rawValue))
        #endif
        mockClient.receiveNotificationCalls.append(contentsOf: [
            (Protocol_PbPFtpDevToHostNotification.stopGpsMeasurement.rawValue, [Data()], false)
        ])

        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first

        XCTAssertEqual(result?.notificationType, .stopGpsMeasurement)
        XCTAssertEqual(result?.parameters, Data())
    }

    func testD2HRuntimePlannerMapsNotificationPlanWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        var syncRequiredNotificationParameter = Protocol_PbPFtpSyncRequiredParams()
        var syncTrigger = Protocol_PbPFtpSyncTrigger()
        syncTrigger.source = .timed
        syncRequiredNotificationParameter.syncTriggers = [syncTrigger]
        let parametersHex = try syncRequiredNotificationParameter.serializedData().map { String(format: "%02x", $0) }.joined()
        let plan = PolarD2hRuntimePlanner.notificationPlan(notificationId: Protocol_PbPFtpDevToHostNotification.syncRequired.rawValue, parametersHex: parametersHex)
        XCTAssertEqual("SYNC_REQUIRED", plan?.notificationType)
        XCTAssertEqual("PbPFtpSyncRequiredParams", plan?.parsedProtoName)
        XCTAssertEqual("STOP_GPS_MEASUREMENT", PolarD2hRuntimePlanner.notificationTypeName(notificationId: Protocol_PbPFtpDevToHostNotification.stopGpsMeasurement.rawValue))
        XCTAssertNil(PolarD2hRuntimePlanner.notificationPlan(notificationId: 999, parametersHex: ""))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testD2HNotificationRawValueMappingUsesSharedPlannerWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("STOP_GPS_MEASUREMENT", PolarD2hRuntimePlanner.notificationTypeName(notificationId: Protocol_PbPFtpDevToHostNotification.stopGpsMeasurement.rawValue))
        #endif
        XCTAssertEqual(.filesystemModified, PolarDeviceToHostNotification(rawValue: 0))
        XCTAssertEqual(.syncRequired, PolarDeviceToHostNotification(rawValue: Protocol_PbPFtpDevToHostNotification.syncRequired.rawValue))
        XCTAssertEqual(.stopGpsMeasurement, PolarDeviceToHostNotification(rawValue: Protocol_PbPFtpDevToHostNotification.stopGpsMeasurement.rawValue))
        XCTAssertEqual(.exerciseStatus, PolarDeviceToHostNotification(rawValue: 19))
        XCTAssertNil(PolarDeviceToHostNotification(rawValue: 6))
        XCTAssertNil(PolarDeviceToHostNotification(rawValue: 999))
    }
    
    func testReceivesSyncRequiredNotification() async throws {
        // Arrange
        let syncRequiredNotificationId = Protocol_PbPFtpDevToHostNotification.syncRequired.rawValue
        var syncRequiredNotificationParameter = Protocol_PbPFtpSyncRequiredParams()
        var syncTrigger = Protocol_PbPFtpSyncTrigger()
        syncTrigger.source = .timed
        syncRequiredNotificationParameter.syncTriggers = [syncTrigger]
        let syncRequiredNotificationParamsData = try syncRequiredNotificationParameter.serializedData()
        
        let keepAliveNotificationId = Protocol_PbPFtpDevToHostNotification.keepBackgroundAlive.rawValue
        
        mockClient.receiveNotificationCalls.append(contentsOf: [
            (syncRequiredNotificationId, [syncRequiredNotificationParamsData], false),
            (keepAliveNotificationId, [Data()], false)
        ])
        
        // Act
        let results = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId))
        
        // Assert
        XCTAssertEqual(results.count, 2)
        
        XCTAssertEqual(results[0].notificationType, PolarDeviceToHostNotification.syncRequired)
        XCTAssertEqual(results[0].parameters, syncRequiredNotificationParamsData)
        XCTAssertNotNil(results[0].parsedParameters)
        XCTAssertTrue(results[0].parsedParameters is Protocol_PbPFtpSyncRequiredParams)
        let parsedParams = results[0].parsedParameters as! Protocol_PbPFtpSyncRequiredParams
        XCTAssertEqual(parsedParams, syncRequiredNotificationParameter)
        
        XCTAssertEqual(results[1].notificationType, PolarDeviceToHostNotification.keepBackgroundAlive)
        XCTAssertEqual(results[1].parameters.count, 0)
    }

    func testD2HParameterParserUsesSharedProtoNameWhenLinked() throws {
        var syncRequiredNotificationParameter = Protocol_PbPFtpSyncRequiredParams()
        var syncTrigger = Protocol_PbPFtpSyncTrigger()
        syncTrigger.source = .timed
        syncRequiredNotificationParameter.syncTriggers = [syncTrigger]
        let serializedData = try syncRequiredNotificationParameter.serializedData()
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("PbPFtpSyncRequiredParams", PolarRuntimePlanner.d2hParsedProtoName(notificationType: "SYNC_REQUIRED", parametersHex: serializedData.map { String(format: "%02x", $0) }.joined()))
        #endif

        let parsed = BlePsFtpClient.parseD2HNotificationParameters(.stopGpsMeasurement, data: serializedData, sharedParsedProtoName: "PbPFtpSyncRequiredParams")

        let parsedParams = try XCTUnwrap(parsed as? Protocol_PbPFtpSyncRequiredParams)
        XCTAssertEqual(parsedParams, syncRequiredNotificationParameter)
    }
    
    func testReceivesFilesystemModifiedNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.filesystemModified.rawValue
        var fileSystemModifiedParams = Protocol_PbPFtpFilesystemModifiedParams()
        fileSystemModifiedParams.action = .created
        fileSystemModifiedParams.path = "/U/0/"
        let serializedData = try fileSystemModifiedParams.serializedData()
        
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).last
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.filesystemModified)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPFtpFilesystemModifiedParams)
        let parsedParams = result!.parsedParameters as! Protocol_PbPFtpFilesystemModifiedParams
        XCTAssertEqual(parsedParams, fileSystemModifiedParams)
    }
    
    func testReceivesInactivityAlertNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.inactivityAlert.rawValue
        var inactivityAlertParams = Protocol_PbPFtpInactivityAlert()
        inactivityAlertParams.countdown = 5
        let serializedData = try inactivityAlertParams.serializedData()
        
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.inactivityAlert)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPFtpInactivityAlert)
        let parsedParams = result!.parsedParameters as! Protocol_PbPFtpInactivityAlert
        XCTAssertEqual(parsedParams.countdown, 5)
    }
    
    func testReceivesTrainingSessionStatusNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.trainingSessionStatus.rawValue
        var trainingSessionStatus = Protocol_PbPFtpTrainingSessionStatus()
        trainingSessionStatus.inprogress = true
        let serializedData = try trainingSessionStatus.serializedData()
        
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.trainingSessionStatus)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPFtpTrainingSessionStatus)
        let parsedParams = result!.parsedParameters as! Protocol_PbPFtpTrainingSessionStatus
        XCTAssertTrue(parsedParams.inprogress)
    }
    
    func testReceivesAutosyncStatusNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.autosyncStatus.rawValue
        var autoSyncStatus = Protocol_PbPFtpAutoSyncStatusParams()
        autoSyncStatus.succeeded = true
        autoSyncStatus.description_p = "Sync completed successfully"
        let serializedData = try autoSyncStatus.serializedData()
        
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.autosyncStatus)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPFtpAutoSyncStatusParams)
        let parsedParams = result!.parsedParameters as! Protocol_PbPFtpAutoSyncStatusParams
        XCTAssertTrue(parsedParams.succeeded)
        XCTAssertEqual(parsedParams.description_p, "Sync completed successfully")
    }
    
    func testReceivesNotificationWithoutParameters() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.stopGpsMeasurement.rawValue
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [Data()], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.stopGpsMeasurement)
        XCTAssertEqual(result!.parameters.count, 0)
        XCTAssertNil(result!.parsedParameters)
    }
    
    func testFiltersUnknownNotificationTypes() async throws {
        try assertD2HStreamRuntimePolicyVectorContains("unknown-notification-between-known-values-is-filtered")
        // Arrange
        mockClient.receiveNotificationCalls.append(contentsOf: [
            (999, [Data()], false),
            (Protocol_PbPFtpDevToHostNotification.idling.rawValue, [Data()], false)
        ])
        
        // Act
        let results = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId))
        
        // Assert
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].notificationType, PolarDeviceToHostNotification.idling)
    }
    
    func testHandlesInvalidProtobufDataGracefully() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.syncRequired.rawValue
        let invalidData = "invalid protobuf data".data(using: .utf8)!
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [invalidData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.syncRequired)
        XCTAssertEqual(result!.parameters, invalidData)
        XCTAssertNil(result!.parsedParameters)
    }
    
    func testReceivesMediaControlRequestNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.mediaControlRequestDh.rawValue
        var mediaControlRequest = Protocol_PbPftpDHMediaControlRequest()
        mediaControlRequest.request = .getMediaData
        let serializedData = try mediaControlRequest.serializedData()
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.mediaControlRequestDh)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPftpDHMediaControlRequest)
        let parsedParams = result!.parsedParameters as! Protocol_PbPftpDHMediaControlRequest
        XCTAssertEqual(parsedParams.request, .getMediaData)
    }
    
    func testReceivesMediaControlCommandNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.mediaControlCommandDh.rawValue
        var mediaControlCommand = Protocol_PbPftpDHMediaControlCommand()
        mediaControlCommand.command = .play
        let serializedData = try mediaControlCommand.serializedData()
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.mediaControlCommandDh)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPftpDHMediaControlCommand)
        let parsedParams = result!.parsedParameters as! Protocol_PbPftpDHMediaControlCommand
        XCTAssertEqual(parsedParams.command, .play)
    }
    
    func testReceivesStartGpsMeasurementNotification() async throws {
        // Arrange
        let notificationId = Protocol_PbPFtpDevToHostNotification.startGpsMeasurement.rawValue
        var startGpsMeasurement = Protocol_PbPftpStartGPSMeasurement()
        startGpsMeasurement.minimumInterval = 1000
        startGpsMeasurement.accuracy = 2
        startGpsMeasurement.latitude = 60.1695
        startGpsMeasurement.longitude = 24.9354
        let serializedData = try startGpsMeasurement.serializedData()
        mockClient.receiveNotificationCalls.append(contentsOf: [(notificationId, [serializedData], false)])
        
        // Act
        let result = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId)).first
        
        // Assert
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.notificationType, PolarDeviceToHostNotification.startGpsMeasurement)
        XCTAssertEqual(result!.parameters, serializedData)
        XCTAssertNotNil(result!.parsedParameters)
        XCTAssertTrue(result!.parsedParameters is Protocol_PbPftpStartGPSMeasurement)
        let parsedParams = result!.parsedParameters as! Protocol_PbPftpStartGPSMeasurement
        XCTAssertEqual(parsedParams.minimumInterval, 1000)
        XCTAssertEqual(parsedParams.accuracy, 2)
        XCTAssertEqual(parsedParams.latitude, 60.1695, accuracy: 0.0001)
        XCTAssertEqual(parsedParams.longitude, 24.9354, accuracy: 0.0001)
    }

    func testPropagatesLateNotificationStreamErrorAfterEmittedValues() async throws {
        try assertD2HStreamRuntimePolicyVectorContains("late-error-after-emitted-notification")
        mockClient.receiveNotificationCalls.append(contentsOf: [(Protocol_PbPFtpDevToHostNotification.stopGpsMeasurement.rawValue, [Data()], false)])
        mockClient.receiveNotificationError = D2HNotificationTestError.lateFailure

        var results: [PolarD2HNotificationData] = []
        do {
            for try await value in api.observeDeviceToHostNotifications(identifier: deviceId) {
                results.append(value)
            }
            XCTFail("Expected late D2H failure")
        } catch D2HNotificationTestError.lateFailure {
            XCTAssertEqual(results.count, 1)
            XCTAssertEqual(results[0].notificationType, .stopGpsMeasurement)
            XCTAssertEqual(results[0].parameters.count, 0)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testCancelsUpstreamNotificationStreamWhenConsumerCancels() async throws {
        try assertD2HStreamRuntimePolicyVectorContains("consumer-cancels-after-first-notification")
        mockClient.receiveNotificationCalls.append(contentsOf: [(Protocol_PbPFtpDevToHostNotification.stopGpsMeasurement.rawValue, [Data()], false)])
        mockClient.receiveNotificationWaitsUntilCancelled = true
        let firstValue = expectation(description: "first D2H notification")
        let upstreamCancelled = expectation(description: "upstream D2H stream cancelled")
        mockClient.receiveNotificationCancellationHandler = {
            upstreamCancelled.fulfill()
        }

        var results: [PolarD2HNotificationData] = []
        let task = Task {
            do {
                for try await value in api.observeDeviceToHostNotifications(identifier: deviceId) {
                    results.append(value)
                    firstValue.fulfill()
                }
            } catch {}
        }

        await fulfillment(of: [firstValue], timeout: 1.0)
        task.cancel()
        await fulfillment(of: [upstreamCancelled], timeout: 1.0)
        await task.value

        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].notificationType, .stopGpsMeasurement)
        XCTAssertEqual(results[0].parameters.count, 0)
    }

    func testFailedD2HSubscribePropagatesErrorWithoutEmittedValues() async throws {
        try assertD2HStreamRuntimePolicyVectorContains("failed-subscribe-does-not-register-observer")
        mockClient.receiveNotificationError = D2HNotificationTestError.serviceMissing

        var results: [PolarD2HNotificationData] = []
        do {
            for try await value in api.observeDeviceToHostNotifications(identifier: deviceId) {
                results.append(value)
            }
            XCTFail("Expected failed D2H subscription")
        } catch D2HNotificationTestError.serviceMissing {
            XCTAssertTrue(results.isEmpty, "Failed D2H subscribe must not emit stale values")
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testD2HNotificationGoldenVectorsMatchIOSBehavior() async throws {
        let vectors = try loadD2HNotificationGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected D2H notification golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
            if let notifications = input["notifications"] as? [[String: Any]] {
                mockClient.receiveNotificationCalls = try notifications.map { notification in
                    let notificationId = try XCTUnwrap(notification["notificationId"] as? NSNumber, id).intValue
                    let parameters = try Data(hexString: try XCTUnwrap(notification["parametersHex"] as? String, id))
                    return (notificationId, [parameters], false)
                }
            } else {
                let parameters = try Data(hexString: try XCTUnwrap(input["parametersHex"] as? String, id))
                let notificationId = try XCTUnwrap(input["notificationId"] as? NSNumber, id).intValue
                mockClient.receiveNotificationCalls = [(notificationId, [parameters], false)]
            }

            let results = try await collectStream(api.observeDeviceToHostNotifications(identifier: deviceId))

            if let emittedCount = expected["emittedCount"] as? NSNumber {
                XCTAssertEqual(results.count, emittedCount.intValue, id)
                continue
            }

            let expectedEvents = expected["events"] as? [[String: Any]] ?? [expected]
            XCTAssertEqual(results.count, expectedEvents.count, id)
            for (index, expectedEvent) in expectedEvents.enumerated() {
                let result = results[index]
                XCTAssertEqual(result.notificationType, try expectedNotificationType(expectedEvent, id: id), id)
                if let parametersHex = expectedEvent["parametersHex"] as? String {
                    XCTAssertEqual(result.parameters, try Data(hexString: parametersHex), id)
                } else if input["notifications"] == nil {
                    XCTAssertEqual(result.parameters, try Data(hexString: try XCTUnwrap(input["parametersHex"] as? String, id)), id)
                }
                try assertParsedParameters(result.parsedParameters, matches: expectedEvent, id: id)
            }
        }
    }

    func testD2HNotificationGoldenVectorsFollowNeutralKmpVectorShape() throws {
        for vector in try loadD2HNotificationGoldenVectors() {
            let id = vector["id"] as? String ?? "unknown-vector"

            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertNotNil(platforms["android"], id)
            XCTAssertNotNil(platforms["ios"], id)
            XCTAssertNotNil(platforms["common"], id)
        }
    }

    // MARK: - Helpers

    private func collectStream<T>(_ stream: AsyncThrowingStream<T, Error>) async throws -> [T] {
        var results: [T] = []
        for try await value in stream { results.append(value) }
        return results
    }

    private func loadD2HNotificationGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/d2h-notifications")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                let input = vector["input"] as? [String: Any]
                return input?["kind"] as? String != "d2hStreamRuntimePolicy"
            }
            .filter { vector in
                let input = vector["input"] as? [String: Any]
                return input?["kind"] as? String != "d2hStreamRuntimeReadiness"
            }
            .filter { vector in
                let input = vector["input"] as? [String: Any]
                return input?["kind"] as? String != "d2hNotificationMappingReadiness"
            }
    }

    private func assertD2HStreamRuntimePolicyVectorContains(_ scenarioId: String) throws {
        let file = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/d2h-notifications/stream-runtime-policy.json")
        let data = try Data(contentsOf: file)
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
        XCTAssertEqual(vector["id"] as? String, "stream-runtime-policy")
        let execution = try XCTUnwrap(vector["execution"] as? [String: Any], "stream-runtime-policy.json")
        XCTAssertEqual(execution["kind"] as? String, "fake-stream-runtime-policy")
        let input = try XCTUnwrap(vector["input"] as? [String: Any], "stream-runtime-policy.json")
        let scenarios = try XCTUnwrap(input["scenarios"] as? [[String: Any]], "stream-runtime-policy.json")
        let scenarioIds = scenarios.compactMap { $0["id"] as? String }
        XCTAssertEqual(scenarioIds, D2H_STREAM_RUNTIME_POLICY_SCENARIO_IDS, "stream-runtime-policy.json")
        XCTAssertTrue(scenarioIds.contains(scenarioId), "stream-runtime-policy.json must include \(scenarioId)")
    }

    func testD2HStreamReadinessManifestIsPinnedBeforeStreamRuntimeMigration() throws {
        let file = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/d2h-notifications/stream-runtime-readiness.json")
        let data = try Data(contentsOf: file)
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
        XCTAssertEqual(vector["id"] as? String, "d2h-stream-runtime-readiness")
        let input = try XCTUnwrap(vector["input"] as? [String: Any], "stream-runtime-readiness.json")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "stream-runtime-readiness.json")
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any], "stream-runtime-readiness.json")
        let policyFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/d2h-notifications/stream-runtime-policy.json")
        let policyData = try Data(contentsOf: policyFile)
        let policyVector = try XCTUnwrap(JSONSerialization.jsonObject(with: policyData) as? [String: Any], policyFile.path)
        let policyInput = try XCTUnwrap(policyVector["input"] as? [String: Any], "stream-runtime-policy.json")
        let policyScenarios = try XCTUnwrap(policyInput["scenarios"] as? [[String: Any]], "stream-runtime-policy.json")
        XCTAssertEqual(policyScenarios.compactMap { $0["id"] as? String }, D2H_STREAM_RUNTIME_POLICY_SCENARIO_IDS, "stream-runtime-policy.json")
        XCTAssertEqual(input["kind"] as? String, "d2hStreamRuntimeReadiness")
        XCTAssertEqual(input["policyVectorPath"] as? String, "sdk/d2h-notifications/stream-runtime-policy.json")
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], "stream-runtime-readiness.json")
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], "stream-runtime-readiness.json")
        let expectedFamilies = [
            "mapped-value-before-late-error",
            "late-upstream-error-propagation",
            "consumer-cancellation-upstream-cancel",
            "suppress-notifications-after-cancel",
            "unknown-notification-filtering",
            "known-values-continue-after-unknown",
            "failed-subscribe-no-observer",
            "active-observer-cleanup-gate",
            "facade-error-mapping-gate",
            "platform-facade-vector-reference-gate",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "D2H stream runtime migration may proceed only after stream-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, mapped values emitted before late upstream errors are preserved, consumer cancellation cancels upstream work and suppresses later notifications, unknown notifications are filtered without stopping later known values, failed subscribe paths register no observers, public facade error mapping remains pinned, and the shared tests are compile-verified.")
        let prototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], "stream-runtime-readiness.json")
        XCTAssertEqual(prototype["status"] as? String, "executable shared commonTest runtime planning guard")
        XCTAssertEqual(prototype["reason"] as? String, "Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.sdk.api.model.utils.PolarD2HNotificationsUtilsTest"], "stream-runtime-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["PolarDeviceToHostNotificationsApiTests"], "stream-runtime-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.D2hStreamRuntimePolicyCommonTest"], "stream-runtime-readiness")
    }

    func testD2HMappingReadinessManifestIsPinnedBeforeMappingMigration() throws {
        let file = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/d2h-notifications/mapping-readiness.json")
        let data = try Data(contentsOf: file)
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
        XCTAssertEqual(vector["id"] as? String, "d2h-notification-mapping-readiness")
        let input = try XCTUnwrap(vector["input"] as? [String: Any], "mapping-readiness.json")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "mapping-readiness.json")
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any], "mapping-readiness.json")
        XCTAssertEqual(input["kind"] as? String, "d2hNotificationMappingReadiness")
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], "mapping-readiness.json")
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], "mapping-readiness.json")
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], "mapping-readiness.json")
        XCTAssertEqual(policyVectorPaths, [
            "sdk/d2h-notifications/filesystem-created.json",
            "sdk/d2h-notifications/sync-required-timed.json",
            "sdk/d2h-notifications/autosync-success.json",
            "sdk/d2h-notifications/start-gps-measurement.json",
            "sdk/d2h-notifications/stop-gps-empty.json",
            "sdk/d2h-notifications/sync-required-invalid-payload.json",
            "sdk/d2h-notifications/unknown-id-filtered.json",
            "sdk/d2h-notifications/repeated-sync-required-and-stop-gps.json"
        ])
        let expectedFamilies = [
            "known-notification-id-mapping",
            "unknown-notification-id-filtering",
            "raw-parameter-preservation",
            "filesystem-created-typed-field-decoding",
            "sync-required-trigger-decoding",
            "autosync-status-decoding",
            "start-gps-measurement-field-decoding",
            "stop-gps-empty-parameter-policy",
            "invalid-payload-null-parse-policy",
            "repeated-notification-ordering",
            "platform-mapping-vector-reference-gate",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "D2H notification mapping migration may proceed only after every mapping vector named by this readiness manifest is executable from shared commonTest, Android and iOS D2H mapping tests continue to reference the same vectors, known IDs, unknown-ID filtering, raw parameter preservation, typed fields for filesystem, sync-required, autosync, and start-GPS notifications, stop-GPS empty parameters, invalid-payload null parsing, repeated-notification ordering, and the shared tests are compile-verified.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.sdk.api.model.utils.PolarD2HNotificationsUtilsTest"], "mapping-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["PolarDeviceToHostNotificationsApiTests"], "mapping-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.D2hNotificationCommonPolicyTest"], "mapping-readiness")
    }


    private func expectedNotificationType(_ expected: [String: Any], id: String) throws -> PolarDeviceToHostNotification {
        switch try XCTUnwrap(expected["notificationType"] as? String, id) {
        case "SYNC_REQUIRED": return .syncRequired
        case "FILESYSTEM_MODIFIED": return .filesystemModified
        case "AUTOSYNC_STATUS": return .autosyncStatus
        case "START_GPS_MEASUREMENT": return .startGpsMeasurement
        case "STOP_GPS_MEASUREMENT": return .stopGpsMeasurement
        default:
            XCTFail("Unsupported notification type in \(id): \(String(describing: expected["notificationType"]))")
            return .filesystemModified
        }
    }

    private func assertParsedParameters(_ actual: Any?, matches expected: [String: Any], id: String) throws {
        guard !(expected["parsedProto"] is NSNull) else {
            XCTAssertNil(actual, id)
            return
        }

        switch try XCTUnwrap(expected["parsedProto"] as? String, id) {
        case "PbPFtpSyncRequiredParams":
            let parsed = try XCTUnwrap(actual as? Protocol_PbPFtpSyncRequiredParams, id)
            let expectedTriggers = try XCTUnwrap(expected["syncTriggers"] as? [[String: Any]], id)
            XCTAssertEqual(parsed.syncTriggers.count, expectedTriggers.count, id)
            for (index, trigger) in expectedTriggers.enumerated() {
                switch try XCTUnwrap(trigger["source"] as? String, id) {
                case "TIMED": XCTAssertEqual(parsed.syncTriggers[index].source, .timed, id)
                default: XCTFail("Unsupported sync trigger source in \(id): \(String(describing: trigger["source"]))")
                }
            }
        case "PbPFtpFilesystemModifiedParams":
            let parsed = try XCTUnwrap(actual as? Protocol_PbPFtpFilesystemModifiedParams, id)
            switch try XCTUnwrap(expected["action"] as? String, id) {
            case "CREATED": XCTAssertEqual(parsed.action, .created, id)
            default: XCTFail("Unsupported filesystem action in \(id): \(String(describing: expected["action"]))")
            }
            XCTAssertEqual(parsed.path, try XCTUnwrap(expected["path"] as? String, id), id)
        case "PbPFtpAutoSyncStatusParams":
            let parsed = try XCTUnwrap(actual as? Protocol_PbPFtpAutoSyncStatusParams, id)
            XCTAssertEqual(parsed.succeeded, try XCTUnwrap(expected["succeeded"] as? Bool, id), id)
            XCTAssertEqual(parsed.description_p, try XCTUnwrap(expected["description"] as? String, id), id)
        case "PbPftpStartGPSMeasurement":
            let parsed = try XCTUnwrap(actual as? Protocol_PbPftpStartGPSMeasurement, id)
            XCTAssertEqual(parsed.minimumInterval, try XCTUnwrap(expected["minimumInterval"] as? NSNumber, id).uint32Value, id)
            XCTAssertEqual(parsed.accuracy, try XCTUnwrap(expected["accuracy"] as? NSNumber, id).uint32Value, id)
            XCTAssertEqual(parsed.latitude, try XCTUnwrap(expected["latitude"] as? NSNumber, id).doubleValue, accuracy: 0.0001, id)
            XCTAssertEqual(parsed.longitude, try XCTUnwrap(expected["longitude"] as? NSNumber, id).doubleValue, accuracy: 0.0001, id)
        default:
            XCTFail("Unsupported parsed proto in \(id): \(String(describing: expected["parsedProto"]))")
        }
    }
}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "D2HNotificationGoldenVector", code: 1, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        self.init()
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "D2HNotificationGoldenVector", code: 2, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            append(byte)
            index = nextIndex
        }
    }
}
