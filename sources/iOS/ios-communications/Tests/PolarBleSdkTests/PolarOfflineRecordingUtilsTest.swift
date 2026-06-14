//  Copyright © 2025 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk

final class PolarOfflineRecordingUtilsTest: XCTestCase {

    private var mockClient: MockBlePsFtpClient!
    
    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testListOfflineRecordingsV1_mergesSplitRecFiles() async throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [
            ("/U/0/20250730/R/101010/ACC0.REC", 500_120),
            ("/U/0/20250730/R/101010/ACC1.REC", 500_103),
            ("/U/0/20250730/R/101010/ACC2.REC", 102_325),
            ("/U/0/20250730/R/101010/HR0.REC", 500_000),
            ("/U/0/20250730/R/101010/HR1.REC", 500_050),
            ("/U/0/20250730/R/101010/PPG0.REC", 300)
        ]
        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool) async throws -> [(String, UInt)] = { _, _, _ in
            sampleEntries
        }

        // Act
        let emitted = try await collectStream(PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient,
            fetchRecursively: fetchRecursively
        ))

        // Assert
        let accEntries = emitted.filter { $0.path.contains("ACC") }
        let hrEntries  = emitted.filter { $0.path.contains("HR") }
        let ppgEntries = emitted.filter { $0.path.contains("PPG") }

        XCTAssertEqual(accEntries.count, 1)
        XCTAssertEqual(accEntries.first?.size, 500_120 + 500_103 + 102_325)
        XCTAssertTrue(accEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(hrEntries.count, 1)
        XCTAssertEqual(hrEntries.first?.size, 500_000 + 500_050)
        XCTAssertTrue(hrEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(ppgEntries.count, 1)
        XCTAssertEqual(ppgEntries.first?.size, 300)
        XCTAssertTrue(ppgEntries.first?.path.hasSuffix(".REC") ?? false)

        emitted.forEach { XCTAssertNotNil($0.date) }
    }

    func testListOfflineRecordingsV1_returns_empty_when_regex_fails() async throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [("/U/0/2025073/R/101010/PPG0.REC", 300)]
        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool) async throws -> [(String, UInt)] = { _, _, _ in
            sampleEntries
        }

        // Act
        let emitted = try await collectStream(PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient, fetchRecursively: fetchRecursively))

        // Assert
        XCTAssertEqual(emitted.filter { $0.path.contains("PPG") }.count, 0)
    }

    func testListOfflineRecordingsV1_returns_empty_when_date_parsing_fails() async throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [("/U/0/99999999/R/101010/PPG0.REC", 300)]
        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool) async throws -> [(String, UInt)] = { _, _, _ in
            sampleEntries
        }

        // Act
        let emitted = try await collectStream(PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient, fetchRecursively: fetchRecursively))

        // Assert
        XCTAssertEqual(emitted.filter { $0.path.contains("PPG") }.count, 0)
    }

    func testListOfflineRecordingsV1_returns_empty_when_time_parsing_fails() async throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [("/U/0/20250730/R/999999/PPG0.REC", 300)]
        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool) async throws -> [(String, UInt)] = { _, _, _ in
            sampleEntries
        }

        // Act
        let emitted = try await collectStream(PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient, fetchRecursively: fetchRecursively))

        // Assert
        XCTAssertEqual(emitted.filter { $0.path.contains("PPG") }.count, 0)
    }

    func testListOfflineRecordingsV1_returns_empty_when_meas_type_parsing_fails() async throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [("/U/0/20250730/R/101010/ZZZ9.REC", 300)]
        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool) async throws -> [(String, UInt)] = { _, _, _ in
            sampleEntries
        }

        // Act
        let emitted = try await collectStream(PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient, fetchRecursively: fetchRecursively))

        // Assert
        XCTAssertEqual(emitted.filter { $0.path.contains("PPG") }.count, 0)
    }

    func testListOfflineRecordingsV1_returns_empty_when_empty_file() async throws {
        // Arrange
        let sampleEntries: [(String, UInt)] = [
            ("/U/0/20250730/R/101010/ACC0.REC", 500_120),
            ("/U/0/20250730/R/101010/ACC1.REC", 500_103),
            ("/U/0/20250730/R/101010/ACC2.REC", 0),
            ("/U/0/20250730/R/101010/HR0.REC", 500_000),
            ("/U/0/20250730/R/101010/HR1.REC", 0),
            ("/U/0/20250730/R/101010/PPG0.REC", 0)
        ]
        let fetchRecursively: (BlePsFtpClient, String, @escaping (String) -> Bool) async throws -> [(String, UInt)] = { _, _, _ in
            sampleEntries
        }

        // Act
        let emitted = try await collectStream(PolarOfflineRecordingUtils.listOfflineRecordingsV1(
            client: mockClient, fetchRecursively: fetchRecursively))

        // Assert
        let accEntries = emitted.filter { $0.path.contains("ACC") }
        let hrEntries  = emitted.filter { $0.path.contains("HR") }
        let ppgEntries = emitted.filter { $0.path.contains("PPG") }

        XCTAssertEqual(accEntries.count, 1)
        XCTAssertEqual(accEntries.first?.size, 500_120 + 500_103)
        XCTAssertTrue(accEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(hrEntries.count, 1)
        XCTAssertEqual(hrEntries.first?.size, 500_000)
        XCTAssertTrue(hrEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(ppgEntries.count, 0)

        emitted.forEach { XCTAssertNotNil($0.date) }
    }

    func testListOfflineRecordingsV2_mergesSplitRecFiles() throws {
        // Arrange
        let pmdTxt = """
        500120 /U/0/20250730/R/101010/ACC0.REC
        500103 /U/0/20250730/R/101010/ACC1.REC
        102325 /U/0/20250730/R/101010/ACC2.REC
        500000 /U/0/20250730/R/101010/HR0.REC
        500050 /U/0/20250730/R/101010/HR1.REC
        300 /U/0/20250730/R/101010/PPG0.REC
        """.data(using: .utf8)!

        // Act
        let emitted = try PolarOfflineRecordingUtils.listOfflineRecordingsV2(fileData: pmdTxt)

        // Assert
        let accEntries = emitted.filter { $0.path.contains("ACC") }
        let hrEntries  = emitted.filter { $0.path.contains("HR") }
        let ppgEntries = emitted.filter { $0.path.contains("PPG") }

        XCTAssertEqual(accEntries.count, 1)
        XCTAssertEqual(accEntries.first?.size, 500_120 + 500_103 + 102_325)
        XCTAssertTrue(accEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(hrEntries.count, 1)
        XCTAssertEqual(hrEntries.first?.size, 500_000 + 500_050)
        XCTAssertTrue(hrEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(ppgEntries.count, 1)
        XCTAssertEqual(ppgEntries.first?.size, 300)
        XCTAssertTrue(ppgEntries.first?.path.hasSuffix(".REC") ?? false)

        emitted.forEach { XCTAssertNotNil($0.date) }
    }

    func testListOfflineRecordingsV2_returns_empty_when_empty_file() throws {
        // Arrange
        let pmdTxt = """
        500120 /U/0/20250730/R/101010/ACC0.REC
        500103 /U/0/20250730/R/101010/ACC1.REC
        0 /U/0/20250730/R/101010/ACC2.REC
        500000 /U/0/20250730/R/101010/HR0.REC
        0 /U/0/20250730/R/101010/HR1.REC
        0 /U/0/20250730/R/101010/PPG0.REC
        """.data(using: .utf8)!

        // Act
        let emitted = try PolarOfflineRecordingUtils.listOfflineRecordingsV2(fileData: pmdTxt)

        // Assert
        let accEntries = emitted.filter { $0.path.contains("ACC") }
        let hrEntries  = emitted.filter { $0.path.contains("HR") }
        let ppgEntries = emitted.filter { $0.path.contains("PPG") }

        XCTAssertEqual(accEntries.count, 1)
        XCTAssertEqual(accEntries.first?.size, 500_120 + 500_103)
        XCTAssertTrue(accEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(hrEntries.count, 1)
        XCTAssertEqual(hrEntries.first?.size, 500_000)
        XCTAssertTrue(hrEntries.first?.path.hasSuffix(".REC") ?? false)

        XCTAssertEqual(ppgEntries.count, 0)

        emitted.forEach { XCTAssertNotNil($0.date) }
    }

    func testOfflineRecordingPmdFilesGoldenVectorsMatchIOSBehavior() throws {
        let vector = try loadOfflineRecordingVector("pmdfiles-v2-grouping.json")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let pmdFilesTxt = try XCTUnwrap(input["pmdFilesTxt"] as? String)
        let emitted = try PolarOfflineRecordingUtils.listOfflineRecordingsV2(fileData: try XCTUnwrap(pmdFilesTxt.data(using: .utf8)))

        try assertOfflineRecordingEntries(emitted, expected: try XCTUnwrap(vector["expected"] as? [String: Any]), platformPathKey: "iosPath")
    }

    func testOfflineRecordingPmdFilesGoldenVectorsFollowNeutralKmpVectorShape() throws {
        try assertNeutralKmpVectorShape(try loadOfflineRecordingVector("pmdfiles-v2-grouping.json"), id: "pmdfiles-v2-grouping.json")
    }

    func testOfflineRecordingMetadataReadinessManifestPinsSharedOwnership() throws {
        let readiness = try loadOfflineRecordingVector("metadata-readiness.json")
        let input = try XCTUnwrap(readiness["input"] as? [String: Any])
        let expected = try XCTUnwrap(readiness["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(readiness["consumerTests"] as? [String: Any])
        let platforms = try XCTUnwrap(readiness["platforms"] as? [String: Any])
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        XCTAssertEqual(readiness["id"] as? String, "offline-recording-metadata-readiness")
        XCTAssertEqual(input["kind"] as? String, "offlineRecordingMetadataReadiness")
        XCTAssertEqual(policyVectorPaths, [
            "sdk/offline-recording/filename-mapping.json",
            "sdk/offline-recording/pmdfiles-v2-grouping.json",
            "sdk/offline-recording/trigger-mapping.json"
        ])
        let expectedFamilies = [
            "filename-to-measurement-type-mapping",
            "split-file-index-stripping",
            "invalid-filename-boundary",
            "pmdfiles-grouping",
            "zero-size-recording-filtering",
            "invalid-entry-filtering",
            "representative-path-platform-policy",
            "trigger-model-projection",
            "disabled-trigger-filtering",
            "platform-offline-recording-vector-reference-gate",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "Offline recording metadata shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS metadata tests continue to reference the same vectors, filename classification, split-file normalization, invalid filename handling, PMDFILES grouping, zero-size and invalid-entry filtering, representative path policy, trigger model projection, disabled-trigger filtering, and compile verification remain explicit before production metadata mapping moves.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), [
            "com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingUtilityTest",
            "com.polar.sdk.impl.utils.PolarDataUtilsTest",
            "com.polar.sdk.api.model.utils.PolarOfflineRecordingUtilsTest"
        ])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), [
            "OfflineRecordingUtilsTest",
            "PolarDataUtilsTest",
            "PolarOfflineRecordingUtilsTest"
        ])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.OfflineRecordingMetadataCommonPolicyTest"])
        XCTAssertEqual(platforms["android"] as? Bool, true)
        XCTAssertEqual(platforms["ios"] as? Bool, true)
        XCTAssertEqual(platforms["common"] as? Bool, true)
    }

    // MARK: - Helpers

    private func collectStream<T>(_ stream: AsyncThrowingStream<T, Error>) async throws -> [T] {
        var results: [T] = []
        for try await value in stream { results.append(value) }
        return results
    }

    private func assertOfflineRecordingEntries(_ actual: [PolarOfflineRecordingEntry], expected: [String: Any], platformPathKey: String) throws {
        let expectedEntries = try XCTUnwrap(expected["entries"] as? [[String: Any]])
        XCTAssertEqual(actual.count, expectedEntries.count, "entry count")
        for expectedEntry in expectedEntries {
            let expectedType = try polarDeviceDataType(named: try XCTUnwrap(expectedEntry["type"] as? String))
            let actualEntry = try XCTUnwrap(actual.first { $0.type == expectedType }, "\(expectedType)")
            XCTAssertEqual(actualEntry.path, try XCTUnwrap(expectedEntry[platformPathKey] as? String))
            XCTAssertEqual(actualEntry.size, UInt(try number(expectedEntry, "size")))
            try assertDate(actualEntry.date, expected: try XCTUnwrap(expectedEntry["dateTime"] as? String))
        }
    }

    private func polarDeviceDataType(named name: String) throws -> PolarDeviceDataType {
        switch name {
        case "ACC": return .acc
        case "GYRO": return .gyro
        case "MAGNETOMETER": return .magnetometer
        case "PPG": return .ppg
        case "PPI": return .ppi
        case "HR": return .hr
        case "TEMPERATURE": return .temperature
        case "SKIN_TEMPERATURE": return .skinTemperature
        default: throw NSError(domain: "PolarOfflineRecordingUtilsTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Unknown device data type \(name)"])
        }
    }

    private func assertDate(_ actual: Date, expected: String) throws {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        XCTAssertEqual(formatter.string(from: actual), expected)
    }

    private func number(_ object: [String: Any], _ key: String) throws -> Int {
        return try XCTUnwrap(object[key] as? NSNumber, key).intValue
    }

    private func assertNeutralKmpVectorShape(_ vector: [String: Any], id: String) throws {
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

    private func loadOfflineRecordingVector(_ fileName: String) throws -> [String: Any] {
        let file = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/offline-recording")
            .appendingPathComponent(fileName)
        let data = try Data(contentsOf: file)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
    }

}
