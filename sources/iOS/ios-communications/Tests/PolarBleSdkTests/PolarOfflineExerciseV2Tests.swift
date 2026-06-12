// Copyright © 2026 Polar Electro Oy. All rights reserved.

import XCTest
import Foundation
@testable import PolarBleSdk

private let EXERCISE_SESSION_READINESS_COMMON_DECISION = "Exercise-session migration may proceed only after this readiness manifest is executable from shared commonTest, Android and iOS exercise-session tests continue to pin sport-profile ID mapping, unknown sport-profile fallback, offline exercise command planning, offline exercise file read/remove paths, device-info path planning, protobuf construction boundaries, status-result platform boundaries, public error mapping boundaries, platform vector references, and compile verification before broader exercise execution moves."
private let EXERCISE_SESSION_READINESS_FAMILIES = ["sport-profile-id-mapping", "unknown-sport-profile-fallback", "offline-exercise-start-command-planning", "offline-exercise-stop-command-planning", "offline-exercise-status-command-planning", "offline-exercise-file-read-remove-paths", "offline-exercise-device-info-path", "protobuf-construction-platform-boundary", "status-result-platform-boundary", "public-error-mapping-boundary", "platform-exercise-session-vector-reference-gate", "compile-verification-gate"]

final class PolarOfflineExerciseV2Tests: XCTestCase {

    private var identifier = "E123456F"
    private var mockClient: MockBlePsFtpClient!
    private var mockGatt: MockPolarGattServiceTransmitter!

    override func setUpWithError() throws {
        mockGatt = MockPolarGattServiceTransmitter()
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: mockGatt)
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func test_startExercise_response_success() async throws {
        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with {
            $0.result = .resultSuccess
            $0.dmDirectoryPath = "/U/0/20260225/"
        }
        mockClient.requestReturnValueClosure = { _ in try startResult.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.result, .resultSuccess)
        XCTAssertEqual(proto.dmDirectoryPath, "/U/0/20260225/")
    }

    func test_startExercise_response_exerciseOngoing() async throws {
        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with { $0.result = .resultExeOngoing }
        mockClient.requestReturnValueClosure = { _ in try startResult.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.result, .resultExeOngoing)
    }

    func test_startExercise_response_lowBattery() async throws {
        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with { $0.result = .resultLowBattery }
        mockClient.requestReturnValueClosure = { _ in try startResult.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.result, .resultLowBattery)
    }

    func test_startExercise_response_sdkMode() async throws {
        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with { $0.result = .resultSdkMode }
        mockClient.requestReturnValueClosure = { _ in try startResult.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.result, .resultSdkMode)
    }

    func test_startExercise_response_unknownSport() async throws {
        // Arrange
        let startResult = Protocol_PbPftpStartDmExerciseResult.with { $0.result = .resultUnknownSport }
        mockClient.requestReturnValueClosure = { _ in try startResult.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.result, .resultUnknownSport)
    }

    func test_startExercise_request_creation() throws {
        // Arrange
        var sportId = PbSportIdentifier()
        sportId.value = UInt64(PolarExerciseSession.SportProfile.otherOutdoor.rawValue)
        var params = Protocol_PbPFtpStartDmExerciseParams()
        params.sportIdentifier = sportId

        // Act
        let request = try params.serializedData()

        // Assert
        XCTAssertFalse(request.isEmpty)
        let decoded = try Protocol_PbPFtpStartDmExerciseParams(serializedBytes: request)
        XCTAssertEqual(decoded.sportIdentifier.value, UInt64(16))
    }

    func test_sportProfileFromId_preservesPublicIdsAndUnknownFallback() {
        XCTAssertEqual(.unknown, PolarExerciseSession.SportProfile.from(id: 0))
        XCTAssertEqual(.running, PolarExerciseSession.SportProfile.from(id: 1))
        XCTAssertEqual(.cycling, PolarExerciseSession.SportProfile.from(id: 2))
        XCTAssertEqual(.otherOutdoor, PolarExerciseSession.SportProfile.from(id: 16))
        XCTAssertEqual(.unknown, PolarExerciseSession.SportProfile.from(id: 3))
        XCTAssertEqual(.unknown, PolarExerciseSession.SportProfile.from(id: Int.max))
    }

    func test_stopExercise_request_creation() throws {
        // Arrange
        var params = Protocol_PbPFtpStopExerciseParams()
        params.save = true

        // Act
        let request = try params.serializedData()

        // Assert
        XCTAssertFalse(request.isEmpty)
        let decoded = try Protocol_PbPFtpStopExerciseParams(serializedBytes: request)
        XCTAssertTrue(decoded.save)
    }

    func test_stopExercise_response_success() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in Data() }

        // Act – request completes without error, no meaningful return value
        let data = try await mockClient.request(Data())

        // Assert
        XCTAssertEqual(data.length, 0)
    }

    func test_exerciseStatus_running() async throws {
        // Arrange
        let status = Protocol_PbPftpGetExerciseStatusResult.with {
            $0.exerciseType = .exerciseTypeDataMerge
            $0.exerciseState = .exerciseStateRunning
        }
        mockClient.requestReturnValueClosure = { _ in try status.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedBytes: Data(data))

        // Assert
        XCTAssertTrue(proto.exerciseType == .exerciseTypeDataMerge && proto.exerciseState == .exerciseStateRunning)
    }

    func test_exerciseStatus_paused() async throws {
        // Arrange
        let status = Protocol_PbPftpGetExerciseStatusResult.with {
            $0.exerciseType = .exerciseTypeDataMerge
            $0.exerciseState = .exerciseStatePaused
        }
        mockClient.requestReturnValueClosure = { _ in try status.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedBytes: Data(data))

        // Assert
        XCTAssertFalse(proto.exerciseType == .exerciseTypeDataMerge && proto.exerciseState == .exerciseStateRunning)
    }

    func test_exerciseStatus_notDataMerge() async throws {
        // Arrange
        let status = Protocol_PbPftpGetExerciseStatusResult.with {
            $0.exerciseType = .exerciseTypeNormal
            $0.exerciseState = .exerciseStateRunning
        }
        mockClient.requestReturnValueClosure = { _ in try status.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedBytes: Data(data))

        // Assert
        XCTAssertFalse(proto.exerciseType == .exerciseTypeDataMerge && proto.exerciseState == .exerciseStateRunning)
    }

    func test_fetchExercise_request_creation() throws {
        // Arrange
        var operation = Protocol_PbPFtpOperation()
        operation.command = .get
        operation.path = "/U/0/20260225/SAMPLES.BPB"

        // Act
        let request = try operation.serializedData()

        // Assert
        XCTAssertFalse(request.isEmpty)
        let decoded = try Protocol_PbPFtpOperation(serializedBytes: request)
        XCTAssertEqual(decoded.command, .get)
        XCTAssertEqual(decoded.path, "/U/0/20260225/SAMPLES.BPB")
    }

    func test_fetchExercise_hrSamples() async throws {
        // Arrange
        let samples = Data_PbExerciseSamples.with {
            $0.recordingInterval = PbDuration.with { $0.seconds = 1 }
            $0.heartRateSamples = [60, 62, 65, 70]
        }
        mockClient.requestReturnValueClosure = { _ in try samples.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Data_PbExerciseSamples(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.recordingInterval.seconds, 1)
        XCTAssertEqual(proto.heartRateSamples, [60, 62, 65, 70])
    }

    func test_fetchExercise_emptySamples() async throws {
        // Arrange
        let samples = Data_PbExerciseSamples.with {
            $0.recordingInterval = PbDuration.with { $0.seconds = 1 }
            $0.heartRateSamples = []
        }
        mockClient.requestReturnValueClosure = { _ in try samples.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Data_PbExerciseSamples(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.recordingInterval.seconds, 1)
        XCTAssertEqual(proto.heartRateSamples, [])
    }

    func test_fetchExercise_withMultipleSamples() async throws {
        // Arrange
        let samples = Data_PbExerciseSamples.with {
            $0.recordingInterval = PbDuration.with { $0.seconds = 5 }
            $0.heartRateSamples = [120, 125, 130, 128, 132, 135]
        }
        mockClient.requestReturnValueClosure = { _ in try samples.serializedData() }

        // Act
        let data = try await mockClient.request(Data())
        let proto = try Data_PbExerciseSamples(serializedBytes: Data(data))

        // Assert
        XCTAssertEqual(proto.recordingInterval.seconds, 5)
        XCTAssertEqual(proto.heartRateSamples.count, 6)
        XCTAssertEqual(proto.heartRateSamples, [120, 125, 130, 128, 132, 135])
    }

    func test_removeExercise_request_creation() throws {
        // Arrange
        var operation = Protocol_PbPFtpOperation()
        operation.command = .remove
        operation.path = "/U/0/20260225/SAMPLES.BPB"

        // Act
        let request = try operation.serializedData()

        // Assert
        XCTAssertFalse(request.isEmpty)
        let decoded = try Protocol_PbPFtpOperation(serializedBytes: request)
        XCTAssertEqual(decoded.command, .remove)
        XCTAssertEqual(decoded.path, "/U/0/20260225/SAMPLES.BPB")
    }

    func test_removeExercise_response_success() async throws {
        // Arrange
        mockClient.requestReturnValueClosure = { _ in Data() }

        // Act – should complete without throwing
        let data = try await mockClient.request(Data())

        // Assert
        XCTAssertEqual(data.length, 0)
    }

    func testOfflineExerciseFileHeadersUseSharedFileFacadePlanning() {
        let path = "/U/0/20260225/E/123456/SAMPLES.BPB"

        let fetchOperation = PolarBleApiImpl.offlineExerciseFetchOperation(path: path)
        XCTAssertEqual(fetchOperation.command, .get)
        XCTAssertEqual(fetchOperation.path, path)

        let removeOperation = PolarBleApiImpl.offlineExerciseRemoveOperation(path: path)
        XCTAssertEqual(removeOperation.command, .remove)
        XCTAssertEqual(removeOperation.path, path)

        let deviceInfoOperation = PolarBleApiImpl.offlineExerciseDeviceInfoReadOperation()
        XCTAssertEqual(deviceInfoOperation.command, .get)
        XCTAssertEqual(deviceInfoOperation.path, "/DEVICE.BPB")
    }

    func testOfflineExerciseCommandQueriesUseSharedCommandPlanningWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual(Protocol_PbPFtpQuery.startDmExercise.rawValue, PolarRuntimePlanner.commandQueryValue(id: "offline-exercise-v2-start", query: "START_DM_EXERCISE", parameters: ["sportProfileId=\(PolarExerciseSession.SportProfile.running.rawValue)"]))
        XCTAssertEqual(Protocol_PbPFtpQuery.stopExercise.rawValue, PolarRuntimePlanner.commandQueryValue(id: "offline-exercise-v2-stop", query: "STOP_EXERCISE", parameters: ["save=true"]))
        XCTAssertEqual(Protocol_PbPFtpQuery.getExerciseStatus.rawValue, PolarRuntimePlanner.commandQueryValue(id: "offline-exercise-v2-status", query: "GET_EXERCISE_STATUS"))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testExerciseSessionReadinessManifestIsPinnedBeforeOfflineExerciseMigration() throws {
        let vectorURL = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/exercise-session/exercise-session-readiness.json")
        let manifest = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: vectorURL)) as? [String: Any])
        let input = try XCTUnwrap(manifest["input"] as? [String: Any])
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])

        XCTAssertEqual(manifest["id"] as? String, "exercise-session-readiness")
        XCTAssertEqual(input["kind"] as? String, "exerciseSessionReadiness")
        XCTAssertEqual(requiredFamilies, EXERCISE_SESSION_READINESS_FAMILIES)
        XCTAssertEqual(coveredFamilies, EXERCISE_SESSION_READINESS_FAMILIES)
        XCTAssertEqual(expected["commonDecision"] as? String, EXERCISE_SESSION_READINESS_COMMON_DECISION)
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.sdk.api.model.PolarExerciseSessionTest", "com.polar.sdk.impl.PolarOfflineExerciseV2ApiImplTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["PolarOfflineExerciseV2Tests"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.ExerciseSessionModelsCommonPolicyTest"])
    }

    func test_exerciseEntry_path_structure() throws {
        // Arrange
        let entryPath = "/U/0/20260225/SAMPLES.BPB"
        let entryDate = Date()
        let entryId = "SAMPLES.BPB"

        // Act
        let entry: (path: String, date: Date, entryId: String) = (entryPath, entryDate, entryId)

        // Assert
        XCTAssertEqual(entry.path, entryPath)
        XCTAssertEqual(entry.entryId, entryId)
        XCTAssertTrue(entry.path.hasSuffix("SAMPLES.BPB"))
    }

    func test_request_error_handling() async throws {
        // Arrange
        let expectedError = NSError(domain: "TestError", code: 500, userInfo: nil)
        mockClient.requestReturnValueClosure = { _ in throw expectedError }

        // Act / Assert
        do {
            _ = try await mockClient.request(Data())
            XCTFail("Should have thrown error")
        } catch {
            let nsError = error as NSError
            XCTAssertEqual(nsError.code, 500)
            XCTAssertEqual(nsError.domain, "TestError")
        }
    }
}
