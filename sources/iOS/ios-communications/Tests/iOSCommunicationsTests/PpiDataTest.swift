//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class PpiDataTest: XCTestCase {
    func testProcessPpiRawDataType0() throws {
        // Arrange
        // HEX: 03 00 94 35 77 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           03 (PPI)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     00 (raw, type 0)
        let ppiDataFrameHeader = Data([
            0x01,
            0x00, 0x20, 0x4A, 0xA9, 0xD1, 0x01, 0x00, 0x00, // 2*10^12
            0x00,
        ])

        let previousTimeStamp:UInt64 = 100
        // HEX:  80 80 80 80 80 FF 00 01 00 01 00 00
        // index    type                                            data:
        // 0        HR                                              0x80 (128)
        let heartRate = 128
        // 1..2     PP                                              0x80 0x80 (32896)
        let intervalInMs:UInt16 = 32896
        // 3..4     PP Error Estimate                               0x80 0x80 (32896)
        let errorEstimate:UInt16 = 32896
        // 5        PP flags                                        0xFF
        let blockerBit:Int = 0x01
        let skinContactStatus:Int = 0x01
        let skinContactSupported:Int = 0x01
        
        // 6        HR                                              0x00 (0)
        let heartRate2 = 0
        // 7..8     PP                                              0x01 0x00 (1)
        let intervalInMs2:UInt16 = 1
        // 9..10     PP Error Estimate                              0x01 0x00 (1)
        let errorEstimate2:UInt16 = 1
        // 11        PP flags                                       0x00
        let blockerBit2:Int = 0x00
        let skinContactStatus2:Int = 0x00
        let skinContactSupported2:Int = 0x00
        
        let ppiDataFrameContent = Data([
            0x80, 0x80, 0x80, 0x80,
            0x80, 0xFF, 0x00, 0x01,
            0x00, 0x01, 0x00, 0x00
        ])
        //let timeStamp: Long = Long.MAX_VALUE
        
        let dataFrame = try PmdDataFrame(
            data: ppiDataFrameHeader + ppiDataFrameContent,
            { _,_ in previousTimeStamp }  ,
            { _ in 1.0 },
            { _ in 0 })
        
        // Act
        let ppiData = try PpiData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(heartRate, ppiData.samples[0].hr)
        XCTAssertEqual(intervalInMs, ppiData.samples[0].ppInMs)
        XCTAssertEqual(errorEstimate, ppiData.samples[0].ppErrorEstimate)
        XCTAssertEqual(blockerBit, ppiData.samples[0].blockerBit)
        XCTAssertEqual(skinContactStatus, ppiData.samples[0].skinContactStatus)
        XCTAssertEqual(skinContactSupported, ppiData.samples[0].skinContactSupported)
        XCTAssertEqual(UInt64(UInt64(2e12) - UInt64(intervalInMs2)*UInt64(1e6)), ppiData.samples[0].timeStamp)
        
        XCTAssertEqual(heartRate2, ppiData.samples[1].hr)
        XCTAssertEqual(intervalInMs2, ppiData.samples[1].ppInMs)
        XCTAssertEqual(errorEstimate2, ppiData.samples[1].ppErrorEstimate)
        XCTAssertEqual(blockerBit2, ppiData.samples[1].blockerBit)
        XCTAssertEqual(skinContactStatus2, ppiData.samples[1].skinContactStatus)
        XCTAssertEqual(skinContactSupported2, ppiData.samples[1].skinContactSupported)
        XCTAssertEqual(UInt64(UInt64(2e12)), ppiData.samples[1].timeStamp)
        
        XCTAssertEqual(2, ppiData.samples.count)
    }

    func testPpiGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadPpiGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected PPI golden vectors")

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
                XCTAssertThrowsError(try PpiData.parseDataFromDataFrame(frame: dataFrame), id) { error in
                    switch parseError {
                    case "unsupportedFrame":
                        guard case BleGattException.gattDataError = error else {
                            return XCTFail("Expected gattDataError for \(id), got \(error)")
                        }
                    default:
                        XCTFail("Unsupported parse error expectation in \(id): \(parseError)")
                    }
                }
                XCTAssertEqual(try XCTUnwrap(expected["timeStamp"] as? NSNumber, id).uint64Value, dataFrame.timeStamp, id)
                continue
            }

            let ppiData = try PpiData.parseDataFromDataFrame(frame: dataFrame)

            let samples = try XCTUnwrap(expected["samples"] as? [[String: Any]], id)
            XCTAssertEqual(samples.count, ppiData.samples.count, id)
            for (index, expectedSample) in samples.enumerated() {
                let actualSample = ppiData.samples[index]
                XCTAssertEqual(try XCTUnwrap(expectedSample["timeStamp"] as? NSNumber, id).uint64Value, actualSample.timeStamp, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["hr"] as? NSNumber, id).intValue, actualSample.hr, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["ppInMs"] as? NSNumber, id).uint16Value, actualSample.ppInMs, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["ppErrorEstimate"] as? NSNumber, id).uint16Value, actualSample.ppErrorEstimate, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["blockerBit"] as? NSNumber, id).intValue, actualSample.blockerBit, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["skinContactStatus"] as? NSNumber, id).intValue, actualSample.skinContactStatus, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["skinContactSupported"] as? NSNumber, id).intValue, actualSample.skinContactSupported, id)
            }
        }
    }

    func testPpiGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadPpiGoldenVectors() {
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

    func testPpiReadinessManifestIsPinnedBeforeParserMigration() throws {
        let manifest = try loadPpiReadinessManifest()
        let id = try XCTUnwrap(manifest["id"] as? String)
        let input = try XCTUnwrap(manifest["input"] as? [String: Any], id)
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any], id)
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any], id)
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], id)
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], id)
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], id)

        XCTAssertEqual("ppi-readiness", id)
        XCTAssertEqual("ppiReadiness", input["kind"] as? String, id)
        XCTAssertEqual(PPI_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, id)
        XCTAssertEqual(PPI_READINESS_FAMILIES, requiredFamilies, id)
        XCTAssertEqual(PPI_READINESS_FAMILIES, coveredFamilies, id)
        XCTAssertEqual(["com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpiDataTest"], consumerTests["android"] as? [String], id)
        XCTAssertEqual(["PpiDataTest"], consumerTests["ios"] as? [String], id)
        XCTAssertEqual(["com.polar.sharedtest.PpiParserCommonPolicyTest"], consumerTests["commonPrototype"] as? [String], id)
    }

    private func loadPpiGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("ppi-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                guard let input = vector["input"] as? [String: Any] else {
                    return true
                }
                return input["kind"] as? String != "ppiReadiness"
            }
    }

    private func loadPpiReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors/ppi-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private let PPI_READINESS_POLICY_VECTOR_PATHS = [
        "protocol/sensors/ppi-compressed-type0-unsupported.json",
        "protocol/sensors/ppi-raw-type0-truncated-sample-android-error.json",
        "protocol/sensors/ppi-raw-type0-two-samples.json",
        "protocol/sensors/ppi-raw-type0-zero-timestamp-boundary.json",
        "protocol/sensors/ppi-raw-type1-unsupported.json"
    ]

    private let PPI_READINESS_FAMILIES = [
        "raw-type0-hr-rr-error-status-parsing",
        "raw-type0-zero-timestamp-policy",
        "raw-type0-timestamp-backfill",
        "unsupported-compressed-frame-policy",
        "unsupported-raw-frame-policy",
        "truncated-raw-sample-policy",
        "platform-ppi-vector-reference-gate",
        "compile-verification-gate"
    ]
}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "PpiDataTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "PpiDataTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }
}
