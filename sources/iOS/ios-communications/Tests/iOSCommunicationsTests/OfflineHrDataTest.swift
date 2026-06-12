//  Copyright © 2023 Polar. All rights reserved.

import XCTest
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif
@testable import iOSCommunications

final class OfflineHrDataTest: XCTestCase {
    
    func testUncompressedOfflineHrDataFrameType0() throws {
        // Arrange
        // HEX: 0E 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0E (Offline hr)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     00 (raw, type 0)
        let offlineHrDataFrameHeader = Data([
            0x0E,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x00,
        ])
        let previousTimeStamp: UInt64 = 0
        
        // index                                                   data:
        // 0             sample0                                   00
        let expectedSample0: UInt8 = 0
        // 1             sample0                                   FF
        let expectedSample1: UInt8 = 255
        // last index    sampleN                                   7F
        let expectedSampleLast: UInt8 = 127
        let expectedSampleSize = 9
        let offlineHrDataFrameContent = Data([
            0x00, 0xFF, 0x32, 0x32, 0x33, 0x33, 0x34, 0x35, 0x7F,
        ])
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: offlineHrDataFrameHeader + offlineHrDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })
        
        
        // Act
        let offlineHrData = try OfflineHrData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(expectedSampleSize, offlineHrData.samples.count)
        XCTAssertEqual(expectedSample0, offlineHrData.samples.first?.hr)
        XCTAssertEqual(expectedSample1, offlineHrData.samples[1].hr)
        XCTAssertEqual(expectedSampleLast, offlineHrData.samples.last?.hr)
    }

    func testCompressedOfflineHrDataFrameType0ThrowsError() throws {
        // Arrange
        // HEX: 0E 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0E (Offline hr)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     80 (compressed, type 0)
        let offlineHrDataFrameHeader = Data([
            0x0E, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
            0x0, 0x0, 0x80,
        ])
        let previousTimeStamp: UInt64 = 0
        let factor:Float = 1.0
        
        // Act
        let dataFrame = try PmdDataFrame(
            data: offlineHrDataFrameHeader,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })

        // Assert
        XCTAssertThrowsError(try OfflineHrData.parseDataFromDataFrame(frame: dataFrame), "Raw FrameType: TYPE_0 is not supported by PPG data parser")
        XCTAssertThrowsError(try OfflineHrData.parseDataFromDataFrame(frame: dataFrame)) { error in
            guard case BleGattException.gattDataError = error else {
                return XCTFail()
            }
        }
    }

    func testUncompressedOfflineHrDataFrameType1() throws {
        // Arrange
        // HEX: 0E 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0E (Offline hr)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     01 (raw, type 1)
        let offlineHrDataFrameHeader = Data([
            0x0E, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
            0x0, 0x0, 0x1,
        ])
        let previousTimeStamp: UInt64 = 0

        // index                                                   data:
        // 0             sample0                                   48
        let expectedHR1: UInt8 = 72
        // 3             sample0                                   51
        let expectedHR2: UInt8 = 81
        // 1             sample0                                   56
        let expectedPPGQuality1: UInt8 = 86
        // 4             sample0                                   40
        let expectedPPGQuality2: UInt8 = 64
        // 2             sample0                                   47
        let expectedCorrectedHR1: UInt8 = 71
        // 5             sample0                                   52
        let expectedCorrectedHR2: UInt8 = 82
        let expectedSampleSize = 2
        
        let offlineHrDataFrameContent = Data([
            0x48, 0x56, 0x47, 0x51, 0x40, 0x52,
        ])

        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: offlineHrDataFrameHeader + offlineHrDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })

        // Act
        let offlineHrData = try OfflineHrData.parseDataFromDataFrame(frame: dataFrame)

        // Assert
        XCTAssertEqual(expectedSampleSize, offlineHrData.samples.count)
        XCTAssertEqual(expectedHR1, offlineHrData.samples.first?.hr)
        XCTAssertEqual(expectedHR2, offlineHrData.samples.last?.hr)
        XCTAssertEqual(expectedPPGQuality1, offlineHrData.samples.first?.ppgQuality)
        XCTAssertEqual(expectedPPGQuality2, offlineHrData.samples.last?.ppgQuality)
        XCTAssertEqual(expectedCorrectedHR1, offlineHrData.samples.first?.correctedHr)
        XCTAssertEqual(expectedCorrectedHR2, offlineHrData.samples.last?.correctedHr)
    }

    func testOfflineHrRawParserUsesSharedKmpWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        let dataFrameHex = "0e000000000000000001485647514052"
        let sharedRows = try XCTUnwrap(OfflineHrDataRuntimePlanner.rawSamples(dataFrameHex: dataFrameHex, previousTimeStamp: 0, factor: 1.0, sampleRate: 0))
        XCTAssertFalse(sharedRows.isEmpty)

        let dataFrame = try PmdDataFrame(
            data: Data(hexString: dataFrameHex),
            { _, _ in 0 },
            { _ in 1.0 },
            { _ in 0 })
        let offlineHrData = try OfflineHrData.parseDataFromDataFrame(frame: dataFrame)
        let sharedSamples = try sharedRows.split(separator: "|").map { row -> (UInt8, UInt8, UInt8) in
            let fields = row.split(separator: ",")
            return (
                try XCTUnwrap(UInt8(fields[0])),
                try XCTUnwrap(UInt8(fields[1])),
                try XCTUnwrap(UInt8(fields[2]))
            )
        }

        XCTAssertEqual(sharedSamples.count, offlineHrData.samples.count)
        for (index, sharedSample) in sharedSamples.enumerated() {
            XCTAssertEqual(sharedSample.0, offlineHrData.samples[index].hr)
            XCTAssertEqual(sharedSample.1, offlineHrData.samples[index].ppgQuality)
            XCTAssertEqual(sharedSample.2, offlineHrData.samples[index].correctedHr)
        }
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testCompressedOfflineHrDataFrameType1ThrowsError() throws {
        // Arrange
        // HEX: 0E 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0E (Offline hr)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     81 (compressed, type 1)
        let offlineHrDataFrameHeader = Data([
            0x0E, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0,
            0x0, 0x0, 0x81,
        ])
        let previousTimeStamp: UInt64 = 0

        let factor:Float = 1.0
        
        // Act
        let dataFrame = try PmdDataFrame(
            data: offlineHrDataFrameHeader,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })

        // Assert
        XCTAssertThrowsError(try PpgData.parseDataFromDataFrame(frame: dataFrame), "Raw FrameType: TYPE_1 is not supported by PPG data parser")
        XCTAssertThrowsError(try PpgData.parseDataFromDataFrame(frame: dataFrame)) { error in
            guard case BleGattException.gattDataError = error else {
                return XCTFail()
            }
        }
    }

    func testOfflineHrGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadOfflineHrGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected offline HR golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            if let platforms = vector["platforms"] as? [String: Any],
               let supported = platforms["ios"] as? Bool,
               !supported {
                continue
            }
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
            let dataFrame = try PmdDataFrame(
                data: Data(hexString: try XCTUnwrap(input["dataFrameHex"] as? String, id)),
                { _, _ in UInt64(truncating: input["previousTimeStamp"] as? NSNumber ?? 0) },
                { _ in Float(truncating: input["factor"] as? NSNumber ?? 1.0) },
                { _ in UInt(truncating: input["sampleRate"] as? NSNumber ?? 0) })

            if let parseError = expected["parseError"] as? String {
                XCTAssertThrowsError(try OfflineHrData.parseDataFromDataFrame(frame: dataFrame), id) { error in
                    switch parseError {
                    case "unsupportedFrame":
                        guard case BleGattException.gattDataError = error else {
                            return XCTFail("Expected gattDataError for \(id), got \(error)")
                        }
                    case "unsupportedCompressedFrame":
                        guard case BleGattException.gattDataError = error else {
                            return XCTFail("Expected gattDataError for \(id), got \(error)")
                        }
                    case "malformedFrame":
                        guard case BleGattException.gattDataError = error else {
                            return XCTFail("Expected gattDataError for \(id), got \(error)")
                        }
                    default:
                        XCTFail("Unsupported parse error expectation in \(id): \(parseError)")
                    }
                }
                continue
            }

            let offlineHrData = try OfflineHrData.parseDataFromDataFrame(frame: dataFrame)

            let samples = try XCTUnwrap(expected["samples"] as? [[String: Any]], id)
            XCTAssertEqual(samples.count, offlineHrData.samples.count, id)
            for (index, expectedSample) in samples.enumerated() {
                let actualSample = offlineHrData.samples[index]
                XCTAssertEqual(try XCTUnwrap(expectedSample["hr"] as? NSNumber, id).uint8Value, actualSample.hr, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["ppgQuality"] as? NSNumber, id).uint8Value, actualSample.ppgQuality, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["correctedHr"] as? NSNumber, id).uint8Value, actualSample.correctedHr, id)
            }
        }
    }

    func testOfflineHrGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadOfflineHrGoldenVectors() {
            let id = try XCTUnwrap(vector["id"] as? String)
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

    func testOfflineHrReadinessManifestIsPinnedBeforeParserMigration() throws {
        let manifest = try loadOfflineHrReadinessManifest()
        let id = try XCTUnwrap(manifest["id"] as? String)
        let input = try XCTUnwrap(manifest["input"] as? [String: Any], id)
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any], id)
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any], id)
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], id)
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], id)
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], id)

        XCTAssertEqual("offline-hr-readiness", id)
        XCTAssertEqual("offlineHrReadiness", input["kind"] as? String, id)
        XCTAssertEqual(OFFLINE_HR_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, id)
        XCTAssertEqual(OFFLINE_HR_READINESS_FAMILIES, requiredFamilies, id)
        XCTAssertEqual(OFFLINE_HR_READINESS_FAMILIES, coveredFamilies, id)
        XCTAssertEqual(["com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.OfflineHrDataTest"], consumerTests["android"] as? [String], id)
        XCTAssertEqual(["OfflineHrDataTest"], consumerTests["ios"] as? [String], id)
        XCTAssertEqual(["com.polar.sharedtest.OfflineHrParserCommonPolicyTest"], consumerTests["commonPrototype"] as? [String], id)
    }

    private func loadOfflineHrGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("offline-hr-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                guard let input = vector["input"] as? [String: Any] else {
                    return true
                }
                return input["kind"] as? String != "offlineHrReadiness"
            }
    }

    private func loadOfflineHrReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors/offline-hr-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private let OFFLINE_HR_READINESS_POLICY_VECTOR_PATHS = [
        "protocol/sensors/offline-hr-compressed-type0-unsupported.json",
        "protocol/sensors/offline-hr-raw-type0-empty.json",
        "protocol/sensors/offline-hr-raw-type0-hr-only-boundaries.json",
        "protocol/sensors/offline-hr-raw-type1-truncated-tuple-android-error.json",
        "protocol/sensors/offline-hr-raw-type1-two-samples.json",
        "protocol/sensors/offline-hr-raw-type2-unsupported.json"
    ]

    private let OFFLINE_HR_READINESS_FAMILIES = [
        "raw-type0-hr-only-samples",
        "raw-type0-empty-recording",
        "raw-type1-hr-ppg-quality-corrected-hr-triples",
        "unsupported-compressed-frame-policy",
        "unsupported-raw-frame-policy",
        "truncated-raw-type1-tuple-policy",
        "platform-offline-hr-vector-reference-gate",
        "compile-verification-gate"
    ]
}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "OfflineHrDataTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "OfflineHrDataTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }
}
