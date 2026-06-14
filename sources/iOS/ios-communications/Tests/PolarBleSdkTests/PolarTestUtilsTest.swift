//  Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

class PolarTestUtilsTests: XCTestCase {

    var mockClient: MockBlePsFtpClient!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    // MARK: - Success: full proto

    func testReadSpo2TestFromDayDirectory_Success_AllFields() async throws {
        // Arrange
        let date = makeDate(year: 2026, month: 4, day: 13)
        let proto = buildSpo2TestProto()
        mockClient.requestReturnValues = try makeDirThenFileResults(proto: proto)

        // Act
        let result = try await awaitFirst(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: date))

        // Assert – all non-zero/non-empty fields should be mapped
        XCTAssertEqual(result.bloodOxygenPercent, 97)
        XCTAssertEqual(result.testStatus, .passed)
        XCTAssertEqual(result.spo2Class, .normal)
        XCTAssertEqual(result.averageHeartRateBpm, 65)
        XCTAssertEqual(result.recordingDevice, "Polar Vantage V3")
        XCTAssertEqual(result.spo2ValueDeviationFromBaseline, .usual)
        XCTAssertEqual(result.spo2HrvDeviationFromBaseline, .noBaseline)
        XCTAssertNil(result.triggerType)  // triggerType is not in the proto
        XCTAssertEqual(result.timeZoneOffsetMinutes, 120)
        XCTAssertEqual(result.spo2QualityAveragePercent ?? 0, Float(90.0), accuracy: 0.001)
        XCTAssertEqual(result.heartRateVariabilityMs ?? 0, 45.5, accuracy: 0.001)
        XCTAssertEqual(result.altitudeMeters ?? 0, 100.0, accuracy: 0.001)
    }

    // MARK: - Success: date from testTime proto field (UInt64 ms since epoch)

    func testReadSpo2TestFromDayDirectory_Success_UsesTestTimeWhenPresent() async throws {
        // Arrange – testTime = ms since epoch for 2026-04-13 14:25:07 UTC
        // 1744554307000 ms = 2026-04-13T14:25:07Z
        let fallbackDate = makeDate(year: 2026, month: 4, day: 13)
        var proto = Data_PbSpo2TestResult()
        proto.bloodOxygenPercent = 95
        proto.testTime = 1744554307000  // 2026-04-13 14:25:07 UTC
        mockClient.requestReturnValues = try makeDirThenFileResults(proto: proto)

        // Act
        let result = try await awaitFirst(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: fallbackDate))

        // Assert – result date should not equal fallback (testTime was decoded)
        XCTAssertNotEqual(result.date, fallbackDate)
        // The date should be in the right ballpark (2026-04-13)
        let components = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: result.date)
        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 4)
        XCTAssertEqual(components.day, 13)
    }

    // MARK: - Success: fallback date used when testTime is zero

    func testReadSpo2TestFromDayDirectory_Success_FallsBackToDateWhenTestTimeAbsent() async throws {
        // Arrange – testTime left at default 0
        let fallbackDate = makeDate(year: 2026, month: 4, day: 13)
        var proto = Data_PbSpo2TestResult()
        proto.bloodOxygenPercent = 95
        // testTime == 0 → treated as absent
        mockClient.requestReturnValues = try makeDirThenFileResults(proto: proto)

        // Act
        let result = try await awaitFirst(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: fallbackDate))

        // Assert – when folder name is "142507" (from makeDirThenFileSingles default), date should be parsed from folder
        // but since we used fallbackDate as dayDate the year/month/day should match
        let components = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: result.date)
        XCTAssertEqual(components.year, 2026)
        XCTAssertEqual(components.month, 4)
        XCTAssertEqual(components.day, 13)
    }

    // MARK: - Zero values map to nil

    func testReadSpo2TestFromDayDirectory_Success_ZeroValuesMapToNil() async throws {
        // Arrange – empty proto: all numeric fields are zero, strings empty
        let proto = Data_PbSpo2TestResult()
        mockClient.requestReturnValues = try makeDirThenFileResults(proto: proto)

        // Act
        let result = try await awaitFirst(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date()))

        // Assert – optional fields should be nil when proto value is zero / empty
        XCTAssertNil(result.recordingDevice)
        XCTAssertNil(result.timeZoneOffsetMinutes)
        XCTAssertNil(result.bloodOxygenPercent)
        XCTAssertNil(result.spo2QualityAveragePercent)
        XCTAssertNil(result.averageHeartRateBpm)
        XCTAssertNil(result.heartRateVariabilityMs)
        XCTAssertNil(result.altitudeMeters)
    }

    // MARK: - Correct paths are requested

    func testSpo2TestReadHeadersUseSharedFileFacadePlanning() throws {
        let date = makeDate(year: 2026, month: 4, day: 13)

        let directoryOperation = PolarTestUtils.spo2TestDirectoryReadOperation(date: date)
        XCTAssertEqual(directoryOperation.command, .get)
        XCTAssertEqual(directoryOperation.path, "/U/0/20260413/SPO2TEST/")

        let fileOperation = PolarTestUtils.spo2TestFileReadOperation(directoryPath: "/U/0/20260413/SPO2TEST/", subDirectoryName: "142507/")
        XCTAssertEqual(fileOperation.command, .get)
        XCTAssertEqual(fileOperation.path, "/U/0/20260413/SPO2TEST/142507/SPO2TRES.BPB")
    }

    func testReadSpo2TestFromDayDirectory_CorrectPathsRequested() async throws {
        // Arrange
        let date = makeDate(year: 2026, month: 4, day: 13)
        mockClient.requestReturnValues = try makeDirThenFileResults(proto: buildSpo2TestProto(), subDirName: "142507")

        _ = try await awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: date))

        // Assert – first call lists the SPO2TEST directory, second fetches the file
        XCTAssertEqual(mockClient.requestCalls.count, 2)

        let firstOp = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[0])
        XCTAssertEqual(firstOp.path, "/U/0/20260413/SPO2TEST/")

        let secondOp = try Protocol_PbPFtpOperation(serializedBytes: mockClient.requestCalls[1])
        XCTAssertEqual(secondOp.path, "/U/0/20260413/SPO2TEST/142507/SPO2TRES.BPB")
    }

    // MARK: - Empty directory → completes empty

    func testReadSpo2TestFromDayDirectory_EmptyDirectory_CompletesEmpty() async throws {
        // Arrange – SPO2TEST/ directory exists but has no entries
        var emptyDir = Protocol_PbPFtpDirectory()
        emptyDir.entries = []
        mockClient.requestReturnValues = [.success(try emptyDir.serializedData())]

        // Act & Assert
        let results = try await awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date()))
        XCTAssertTrue(results.isEmpty)
    }

    // MARK: - Directory has only file entries (no subdirectory) → completes empty

    func testReadSpo2TestFromDayDirectory_DirectoryHasNoSubdirs_CompletesEmpty() async throws {
        // Arrange – entry without trailing slash is a file, not a subdir
        var dir = Protocol_PbPFtpDirectory()
        var fileEntry = Protocol_PbPFtpEntry()
        fileEntry.name = "SPO2TRES.BPB"   // no trailing slash
        fileEntry.size = 0
        dir.entries = [fileEntry]
        mockClient.requestReturnValues = [.success(try dir.serializedData())]

        // Act & Assert
        let results = try await awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date()))
        XCTAssertTrue(results.isEmpty)
    }

    // MARK: - Directory listing fails → completes empty

    func testReadSpo2TestFromDayDirectory_DirectoryListingFails_CompletesEmpty() async throws {
        // Arrange – simulate device error 103 (file/directory not found)
        mockClient.requestReturnValues = [
            .failure(NSError(domain: "BLE", code: 103, userInfo: nil))
        ]

        let results = try await awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date()))
        XCTAssertTrue(results.isEmpty)
    }

    // MARK: - File fetch fails after directory found → completes empty

    func testReadSpo2TestFromDayDirectory_FileFetchFails_CompletesEmpty() async throws {
        // Arrange – directory listing succeeds, but file request errors
        var dir = Protocol_PbPFtpDirectory()
        var entry = Protocol_PbPFtpEntry()
        entry.name = "142507/"; entry.size = 0
        dir.entries = [entry]

        mockClient.requestReturnValues = [
            .success(try dir.serializedData()),
            .failure(NSError(domain: "BLE", code: 103, userInfo: nil))
        ]

        // Act & Assert
        let results = try await awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date()))
        XCTAssertTrue(results.isEmpty)
    }

    // MARK: - Malformed file data → completes empty

    func testReadSpo2TestFromDayDirectory_MalformedFileData_CompletesEmpty() async throws {
        // Arrange – directory listing succeeds but the proto bytes are garbage
        var dir = Protocol_PbPFtpDirectory()
        var entry = Protocol_PbPFtpEntry()
        entry.name = "142507/"; entry.size = 0
        dir.entries = [entry]

        mockClient.requestReturnValues = [
            .success(try dir.serializedData()),
            .success(Data([0xFF, 0xFE, 0xFD]))
        ]

        // Act & Assert
        let results = try await awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date()))
        XCTAssertTrue(results.isEmpty)
    }

    // MARK: - Multiple subdirectories → all are returned

    func testReadSpo2TestFromDayDirectory_MultipleSubdirs_ReturnsAll() async throws {
        // Arrange – two time subdirectories; both should be read and returned
        var dir = Protocol_PbPFtpDirectory()
        var entry1 = Protocol_PbPFtpEntry(); entry1.name = "093635/"; entry1.size = 0
        var entry2 = Protocol_PbPFtpEntry(); entry2.name = "093751/"; entry2.size = 0
        dir.entries = [entry1, entry2]

        var proto1 = buildSpo2TestProto(); proto1.bloodOxygenPercent = 97
        var proto2 = buildSpo2TestProto(); proto2.bloodOxygenPercent = 95

        mockClient.requestReturnValues = [
            .success(try dir.serializedData()),
            .success(try proto1.serializedData()),
            .success(try proto2.serializedData())
        ]

        let date = makeDate(year: 2026, month: 4, day: 13)
        let results = try await awaitAll(
            PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: date))

        // Both subdirectories should produce a result
        XCTAssertEqual(results.count, 2)
        let oxygenValues = Set(results.compactMap { $0.bloodOxygenPercent })
        XCTAssertEqual(oxygenValues, Set([97, 95]))

        // Both file paths should have been requested
        XCTAssertEqual(mockClient.requestCalls.count, 3)
        let paths = try mockClient.requestCalls.dropFirst().map {
            try Protocol_PbPFtpOperation(serializedBytes: $0).path
        }
        XCTAssertTrue(paths.contains("/U/0/20260413/SPO2TEST/093635/SPO2TRES.BPB"))
        XCTAssertTrue(paths.contains("/U/0/20260413/SPO2TEST/093751/SPO2TRES.BPB"))
    }

    // MARK: - Enum mapping: Spo2Class

    func testSpo2ClassMapping() {
        XCTAssertEqual(PolarSpo2TestData.Spo2Class(rawValue: 0), .unknown)
        XCTAssertEqual(PolarSpo2TestData.Spo2Class(rawValue: 1), .veryLow)
        XCTAssertEqual(PolarSpo2TestData.Spo2Class(rawValue: 2), .low)
        XCTAssertEqual(PolarSpo2TestData.Spo2Class(rawValue: 3), .normal)
        XCTAssertNil(PolarSpo2TestData.Spo2Class(rawValue: 99))
    }

    // MARK: - Enum mapping: Spo2TestStatus

    func testSpo2TestStatusMapping() {
        XCTAssertEqual(PolarSpo2TestData.Spo2TestStatus(rawValue: 0), .passed)
        XCTAssertEqual(PolarSpo2TestData.Spo2TestStatus(rawValue: 1), .inconclusiveTooLowQualityInSamples)
        XCTAssertEqual(PolarSpo2TestData.Spo2TestStatus(rawValue: 2), .inconclusiveTooLowOverallQuality)
        XCTAssertEqual(PolarSpo2TestData.Spo2TestStatus(rawValue: 3), .inconclusiveTooManyMissingSamples)
        XCTAssertNil(PolarSpo2TestData.Spo2TestStatus(rawValue: 99))
    }

    // MARK: - Enum mapping: DeviationFromBaseline

    func testDeviationFromBaselineMapping() {
        XCTAssertEqual(PolarSpo2TestData.DeviationFromBaseline(rawValue: 0), .noBaseline)
        XCTAssertEqual(PolarSpo2TestData.DeviationFromBaseline(rawValue: 1), .belowUsual)
        XCTAssertEqual(PolarSpo2TestData.DeviationFromBaseline(rawValue: 2), .usual)
        XCTAssertEqual(PolarSpo2TestData.DeviationFromBaseline(rawValue: 3), .aboveUsual)
        XCTAssertNil(PolarSpo2TestData.DeviationFromBaseline(rawValue: 99))
    }

    // MARK: - Enum mapping: Spo2TestTriggerType

    func testTriggerTypeMapping() {
        XCTAssertEqual(PolarSpo2TestData.Spo2TestTriggerType(rawValue: 0), .manual)
        XCTAssertEqual(PolarSpo2TestData.Spo2TestTriggerType(rawValue: 1), .automatic)
        XCTAssertNil(PolarSpo2TestData.Spo2TestTriggerType(rawValue: 99))
    }

    func testSpo2PublicModelEnumMappingDelegatesKnownValuesToSharedBridgeWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual(PolarIosSharedBridge.shared.spo2TestStatus(value: 0), "passed")
        XCTAssertEqual(PolarSpo2TestData.Spo2TestStatus.fromSharedOrRaw(value: 0), .passed)
        XCTAssertEqual(PolarIosSharedBridge.shared.spo2Class(value: 3), "normal")
        XCTAssertEqual(PolarSpo2TestData.Spo2Class.fromSharedOrRaw(value: 3), .normal)
        XCTAssertEqual(PolarIosSharedBridge.shared.spo2DeviationFromBaseline(value: 3), "aboveUsual")
        XCTAssertEqual(PolarSpo2TestData.DeviationFromBaseline.fromSharedOrRaw(value: 3), .aboveUsual)
        XCTAssertEqual(PolarIosSharedBridge.shared.spo2TriggerType(value: 1), "automatic")
        XCTAssertEqual(PolarSpo2TestData.Spo2TestTriggerType.fromSharedOrRaw(value: 1), .automatic)
        XCTAssertNil(PolarSpo2TestData.Spo2Class.fromSharedOrRaw(value: 99))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked for this target")
        #endif
    }

    func testSpo2ProjectionUsesSharedIosPublicShapePolicyWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        let fields = PolarIosSharedBridge.shared.spo2ProjectionFields(
            date: "2026-04-14",
            timeDirName: "063751",
            recordingDevice: "0004BF3D",
            timeZoneOffsetMinutes: 180,
            testStatus: 0,
            bloodOxygenPercent: "96",
            spo2Class: "3",
            spo2ValueDeviationFromBaseline: "2",
            spo2QualityAveragePercent: "99.0",
            averageHeartRateBpm: "66",
            heartRateVariabilityMs: "79.97114",
            spo2HrvDeviationFromBaseline: "3",
            altitudeMeters: "18.13582",
            triggerType: "1"
        ).split(separator: "\u{1F}", omittingEmptySubsequences: false).map(String.init)

        XCTAssertEqual(fields.count, 11)
        XCTAssertEqual(fields[0], "0004BF3D")
        XCTAssertEqual(fields[1], "passed")
        XCTAssertEqual(fields[3], "normal")
        XCTAssertEqual(fields[4], "usual")
        XCTAssertEqual(fields[8], "aboveUsual")
        XCTAssertEqual(fields[10], "automatic")
        #else
        throw XCTSkip("PolarBleSdkShared is not linked for this target")
        #endif
    }

    // MARK: - Round-trip: each Spo2TestStatus from proto

    func testAllSpo2TestStatuses_RoundTripFromProto() async throws {
        let statuses: [(Data_PbSpo2TestStatus, PolarSpo2TestData.Spo2TestStatus)] = [
            (.spo2TestPassed, .passed),
            (.spo2TestInconclusiveTooLowQualityInSamples, .inconclusiveTooLowQualityInSamples),
            (.spo2TestInconclusiveTooLowOverallQuality, .inconclusiveTooLowOverallQuality),
            (.spo2TestInconclusiveTooManyMissingSamples, .inconclusiveTooManyMissingSamples)
        ]
        for (protoStatus, expected) in statuses {
            var proto = Data_PbSpo2TestResult()
            proto.testStatus = protoStatus
            proto.bloodOxygenPercent = 95
            mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
            mockClient.requestReturnValues = try makeDirThenFileResults(proto: proto)

            let result = try await awaitFirst(
                PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date()))
            XCTAssertEqual(result.testStatus, expected, "testStatus mismatch for \(protoStatus)")
        }
    }

    // MARK: - Round-trip: each Spo2Class from proto

    func testAllSpo2Classes_RoundTripFromProto() async throws {
        let classes: [(Data_PbSpo2Class, PolarSpo2TestData.Spo2Class)] = [
            (.spo2ClassUnknown, .unknown),
            (.spo2ClassVeryLow, .veryLow),
            (.spo2ClassLow, .low),
            (.spo2ClassNormal, .normal)
        ]
        for (protoClass, expected) in classes {
            var proto = Data_PbSpo2TestResult()
            proto.spo2Class = protoClass
            proto.bloodOxygenPercent = 95
            mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
            mockClient.requestReturnValues = try makeDirThenFileResults(proto: proto)

            let result = try await awaitFirst(
                PolarTestUtils.readSpo2TestFromDayDirectory(client: mockClient, date: Date()))
            XCTAssertEqual(result.spo2Class, expected, "spo2Class mismatch for \(protoClass)")
        }
    }

    // MARK: - Golden vectors

    func testSpo2GoldenVectors_MapProtoFieldsToPublicModel() throws {
        for vector in try loadSpo2GoldenVectors() {
            let id = try XCTUnwrap(vector["id"] as? String)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let protoFields = try XCTUnwrap(input["proto"] as? [String: Any], id)
            let expected = try expectedForIOS(vector, id: id)
            let proto = try buildProto(from: protoFields, id: id)
            let date = try date(from: XCTUnwrap(input["date"] as? String, id))
            let timeDirName = try XCTUnwrap(input["timeDirName"] as? String, id)

            let result = PolarTestUtils.fromProto(proto: proto, date: date, timeDirName: timeDirName)

            try assertSpo2Result(result, expected: expected, id: id)
        }
    }

    func testSpo2GoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadSpo2GoldenVectors() {
            let id = try XCTUnwrap(vector["id"] as? String)
            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            XCTAssertNotNil(input["date"], id)
            XCTAssertNotNil(input["timeDirName"], id)
            XCTAssertNotNil(input["proto"], id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertEqual(platforms["android"] as? Bool, true, id)
            XCTAssertEqual(platforms["ios"] as? Bool, true, id)
            XCTAssertEqual(platforms["common"] as? Bool, true, id)
        }
    }

    func testSpo2ReadinessManifestPinsModelOwnership() throws {
        let readiness = try loadSpo2ReadinessManifest()
        let input = try XCTUnwrap(readiness["input"] as? [String: Any])
        let expected = try XCTUnwrap(readiness["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(readiness["consumerTests"] as? [String: Any])
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        XCTAssertEqual(readiness["id"] as? String, "spo2-readiness")
        XCTAssertEqual(input["kind"] as? String, "spo2Readiness")
        XCTAssertEqual(policyVectorPaths, [
            "sdk/spo2-test/full-passed-normal.json",
            "sdk/spo2-test/ios-trigger-automatic.json",
            "sdk/spo2-test/omitted-optionals.json",
            "sdk/spo2-test/unknown-spo2-class-platform-difference.json"
        ])
        let expectedFamilies = [
            "full-passed-normal-field-mapping",
            "optional-protobuf-presence-preservation",
            "empty-recording-device-normalization",
            "time-directory-name-parsing",
            "nullable-trigger-type-policy",
            "no-generated-trigger-field-platform-reference",
            "unknown-spo2-class-boundary",
            "platform-spo2-vector-reference-gate",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "SPo2 model shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS SPo2 tests continue to reference the same vectors, optional protobuf presence and empty recording-device normalization remain covered, time-directory parsing remains shared and compile-verified, nullable triggerType policy remains explicit for generated protos that do not expose the field, unknown SPo2 class behavior is handled at a typed boundary before public model exposure, and the shared tests are compile-verified.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.sdk.api.model.utils.PolarTestUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["PolarTestUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.Spo2CommonPolicyTest"])
    }

    // MARK: - Helpers

    private func buildSpo2TestProto() -> Data_PbSpo2TestResult {
        var proto = Data_PbSpo2TestResult()
        proto.recordingDevice = "Polar Vantage V3"
        proto.testStatus = .spo2TestPassed
        proto.bloodOxygenPercent = 97
        proto.spo2Class = .spo2ClassNormal
        proto.spo2ValueDeviationFromBaseline = .deviationUsual
        proto.spo2HrvDeviationFromBaseline = .deviationNoBaseline
        proto.spo2QualityAveragePercent = 90.0
        proto.averageHeartRateBpm = 65
        proto.heartRateVariabilityMs = 45.5
        proto.altitudeMeters = 100.0
        proto.timeZoneOffset = 120
        proto.testTime = 1744574400000  // 2026-04-14 00:00:00 UTC
        return proto
    }

    /// Build [directoryResult, fileResult] to feed requestReturnValues.
    private func makeDirThenFileResults(
        proto: Data_PbSpo2TestResult,
        subDirName: String = "142507"
    ) throws -> [Result<Data, Error>] {
        var dir = Protocol_PbPFtpDirectory()
        var entry = Protocol_PbPFtpEntry()
        entry.name = "\(subDirName)/"
        entry.size = 0
        dir.entries = [entry]
        return [
            .success(try dir.serializedData()),
            .success(try proto.serializedData())
        ]
    }

    /// Collect all values emitted by an AsyncThrowingStream.
    @discardableResult
    private func awaitAll<T>(_ stream: AsyncThrowingStream<T, Error>) async throws -> [T] {
        var results: [T] = []
        for try await value in stream {
            results.append(value)
        }
        return results
    }

    /// Return the first value from an AsyncThrowingStream, or throw if the stream completes empty.
    @discardableResult
    private func awaitFirst<T>(_ stream: AsyncThrowingStream<T, Error>) async throws -> T {
        for try await value in stream {
            return value
        }
        throw XCTestError(.timeoutWhileWaiting)
    }

    /// Create a Date at midnight for a given year/month/day using the Gregorian calendar.
    private func makeDate(year: Int, month: Int, day: Int) -> Date {
        var c = DateComponents()
        c.year = year; c.month = month; c.day = day
        c.hour = 0; c.minute = 0; c.second = 0
        return Calendar(identifier: .gregorian).date(from: c)!
    }

    private func buildProto(from fields: [String: Any], id: String) throws -> Data_PbSpo2TestResult {
        var proto = Data_PbSpo2TestResult()
        if let value = fields["recordingDevice"] as? String { proto.recordingDevice = value }
        if let value = fields["timeZoneOffsetMinutes"] as? NSNumber { proto.timeZoneOffset = value.int32Value }
        if let value = fields["testStatus"] as? NSNumber {
            proto.testStatus = try XCTUnwrap(Data_PbSpo2TestStatus(rawValue: value.intValue), id)
        }
        if let value = fields["bloodOxygenPercent"] as? NSNumber { proto.bloodOxygenPercent = value.int32Value }
        if let value = fields["spo2Class"] as? NSNumber {
            proto.spo2Class = try XCTUnwrap(Data_PbSpo2Class(rawValue: value.intValue), id)
        }
        if let value = fields["spo2ValueDeviationFromBaseline"] as? NSNumber {
            proto.spo2ValueDeviationFromBaseline = try XCTUnwrap(Data_PbDeviationFromBaseline(rawValue: value.intValue), id)
        }
        if let value = fields["spo2QualityAveragePercent"] as? NSNumber { proto.spo2QualityAveragePercent = value.floatValue }
        if let value = fields["averageHeartRateBpm"] as? NSNumber { proto.averageHeartRateBpm = value.uint32Value }
        if let value = fields["heartRateVariabilityMs"] as? NSNumber { proto.heartRateVariabilityMs = value.floatValue }
        if let value = fields["spo2HrvDeviationFromBaseline"] as? NSNumber {
            proto.spo2HrvDeviationFromBaseline = try XCTUnwrap(Data_PbDeviationFromBaseline(rawValue: value.intValue), id)
        }
        if let value = fields["altitudeMeters"] as? NSNumber { proto.altitudeMeters = value.floatValue }
        return proto
    }

    private func assertSpo2Result(_ actual: PolarSpo2TestData, expected: [String: Any], id: String) throws {
        try assertOptionalString(actual.recordingDevice, expected, "recordingDevice", id: id)
        if let expectedDate = expected["dateIsoUtc"] as? String {
            XCTAssertEqual(isoUtc(from: actual.date), expectedDate, id)
        }
        try assertOptionalInt(actual.timeZoneOffsetMinutes, expected, "timeZoneOffsetMinutes", id: id)
        try assertOptionalString(actual.testStatus.map { String(describing: $0) }, expected, "testStatus", id: id)
        try assertOptionalInt(actual.bloodOxygenPercent, expected, "bloodOxygenPercent", id: id)
        try assertOptionalString(actual.spo2Class.map { String(describing: $0) }, expected, "spo2Class", id: id)
        try assertOptionalString(actual.spo2ValueDeviationFromBaseline.map { String(describing: $0) }, expected, "spo2ValueDeviationFromBaseline", id: id)
        try assertOptionalFloat(actual.spo2QualityAveragePercent, expected, "spo2QualityAveragePercent", id: id)
        try assertOptionalUInt(actual.averageHeartRateBpm, expected, "averageHeartRateBpm", id: id)
        try assertOptionalFloat(actual.heartRateVariabilityMs, expected, "heartRateVariabilityMs", id: id)
        try assertOptionalString(actual.spo2HrvDeviationFromBaseline.map { String(describing: $0) }, expected, "spo2HrvDeviationFromBaseline", id: id)
        try assertOptionalFloat(actual.altitudeMeters, expected, "altitudeMeters", id: id)
        try assertOptionalString(actual.triggerType.map { String(describing: $0) }, expected, "triggerType", id: id)
    }

    private func assertOptionalString(_ actual: String?, _ expected: [String: Any], _ key: String, id: String) throws {
        guard expected.keys.contains(key) else { return }
        if expected[key] is NSNull {
            XCTAssertNil(actual, "\(id) \(key)")
        } else {
            XCTAssertEqual(actual, try XCTUnwrap(expected[key] as? String, "\(id) \(key)"), "\(id) \(key)")
        }
    }

    private func assertOptionalInt(_ actual: Int?, _ expected: [String: Any], _ key: String, id: String) throws {
        guard expected.keys.contains(key) else { return }
        if expected[key] is NSNull {
            XCTAssertNil(actual, "\(id) \(key)")
        } else {
            XCTAssertEqual(actual, try XCTUnwrap(expected[key] as? NSNumber, "\(id) \(key)").intValue, "\(id) \(key)")
        }
    }

    private func assertOptionalUInt(_ actual: UInt?, _ expected: [String: Any], _ key: String, id: String) throws {
        guard expected.keys.contains(key) else { return }
        if expected[key] is NSNull {
            XCTAssertNil(actual, "\(id) \(key)")
        } else {
            XCTAssertEqual(actual, UInt(try XCTUnwrap(expected[key] as? NSNumber, "\(id) \(key)").uintValue), "\(id) \(key)")
        }
    }

    private func assertOptionalFloat(_ actual: Float?, _ expected: [String: Any], _ key: String, id: String) throws {
        guard expected.keys.contains(key) else { return }
        if expected[key] is NSNull {
            XCTAssertNil(actual, "\(id) \(key)")
        } else {
            XCTAssertEqual(try XCTUnwrap(actual, "\(id) \(key)"), try XCTUnwrap(expected[key] as? NSNumber, "\(id) \(key)").floatValue, accuracy: 0.00001, "\(id) \(key)")
        }
    }

    private func loadSpo2GoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/spo2-test")
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
                return input?["kind"] as? String != "spo2Readiness"
            }
    }

    private func loadSpo2ReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/spo2-test/spo2-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private func expectedForIOS(_ vector: [String: Any], id: String) throws -> [String: Any] {
        let platforms = try XCTUnwrap(vector["platformExpectations"] as? [String: Any], id)
        return try XCTUnwrap(platforms["ios"] as? [String: Any], id)
    }

    private func date(from value: String) throws -> Date {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return try XCTUnwrap(formatter.date(from: value), value)
    }

    private func isoUtc(from date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.string(from: date)
    }
}
