//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class MagDataTest: XCTestCase {
    
    func testProcessMagnetometerCompressedDataType0() throws {
        // Arrange
        // HEX: 06 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           06 (MAG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        let timeStamp:UInt64 = 2000000000
        // 10       frame type                                     80 (compressed, type 0)
        
        let magDataFrameHeader = Data([
            0x06,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x80,
        ])
        let previousTimeStamp:UInt64 = 100
        
        // HEX: E2 E6 FA 15 49 0A 06 01 7F 20 FC
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  E2 E6 (0xE6E2 = -6430)
        // 1..2     Sample 0 - channel 1 (ref. sample)  FA 15 (0x15FA = 5626)
        // 3..4     Sample 0 - channel 2 (ref. sample)  49 0A (0x0A49 = 2633)
        // 5        Delta size                          06 (6 bit)
        // 6        Sample amount                       01 (1 samples)
        // 7..      Delta data                          7F (binary: 01 111111) 20 (binary: 0010 0000) FC (binary: 111111 00)
        // Delta channel 0                              111111b
        // Delta channel 1                              000001b
        // Delta channel 2                              000010b
        let expectedSamplesSize = 1 + 1 // reference sample + delta samples
        let magDataFrameContent = Data([
            0xE2, 0xE6, 0xFA, 0x15, 0x49, 0x0A,
            0x06, 0x01, 0x7F, 0x20, 0xFC
        ])
        
        let sample0channel0: Float = -6430.0
        let sample0channel1: Float = 5626.0
        let sample0channel2: Float = 2633.0
        
        let sample1channel0 = sample0channel0 - 0x01
        let sample1channel1 = sample0channel1 + 0x01
        let sample1channel2 = sample0channel2 + 0x02
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: magDataFrameHeader + magDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })
        
        // Act
        let magData = try MagData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(expectedSamplesSize, magData.samples.count)
        
        XCTAssertEqual(sample0channel0, magData.samples[0].x)
        XCTAssertEqual(sample0channel1, magData.samples[0].y)
        XCTAssertEqual(sample0channel2, magData.samples[0].z)
        
        XCTAssertEqual(sample1channel0, magData.samples[1].x)
        XCTAssertEqual(sample1channel1, magData.samples[1].y)
        XCTAssertEqual(sample1channel2, magData.samples[1].z)
        
        XCTAssertEqual(timeStamp, magData.timeStamp)
        XCTAssertEqual(timeStamp, magData.samples[1].timeStamp)
    }
    
    func testProcessMagnetometerCompressedDataType1() throws {
        // Arrange
        // HEX: 06 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           06 (MAG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        let timeStamp: UInt64 = 2000000000
        // 10       frame type                                     81 (compressed, type 1)
        let magDataFrameHeader = Data([
            0x06,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x81
        ])
        let previousTimeStamp: UInt64 = 100
        
        // HEX: 37 FF 51 FD 6C F6 00 00 03 01 F8 02
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  37 FF (0xFF37 = -201)
        // 2..3     Sample 0 - channel 1 (ref. sample)  51 FD (0xFD51 = -687)
        // 4..5     Sample 0 - channel 2 (ref. sample)  6C F6 (0xF66C = -2452)
        // 6..7     Status (ref. sample)                00 00 (0x0000 = 0)
        // 8        Delta size                          03 (3 bit)
        // 9        Sample amount                       01 (1 samples)
        // 10..     Delta data                          F8 (binary: 11 111 000) 02 (binary: 0000 0010)
        // Delta channel 0                              000b
        // Delta channel 1                              111b
        // Delta channel 2                              011b
        // Delta status                                 001b
        let expectedSamplesSize = 1 + 1 // reference sample + delta samples
        let magDataFrameContent = Data([
            0x37, 0xFF, 0x51, 0xFD, 0x6C, 0xF6,
            0x00, 0x00, 0x03, 0x01, 0xF8, 0x02
        ])
        
        let sample0channel0: Float = -201.0 / 1000
        let sample0channel1: Float = -687.0 / 1000
        let sample0channel2: Float = -2452.0 / 1000
        let sample0status = MagData.CalibrationStatus.getById(id: 0x00)
        
        let sample1channel0: Float = (-201.0 + 0x00) / 1000
        let sample1channel1: Float = (-687.0 - 0x01) / 1000
        let sample1channel2: Float = (-2452.0 + 0x3) / 1000
        let sample1status = MagData.CalibrationStatus.getById(id: 0x00 + 0x01)
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: magDataFrameHeader + magDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })
        
        // Act
        let magData = try MagData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(expectedSamplesSize, magData.samples.count)
        
        XCTAssertEqual(sample0channel0, magData.samples[0].x)
        XCTAssertEqual(sample0channel1, magData.samples[0].y)
        XCTAssertEqual(sample0channel2, magData.samples[0].z)
        XCTAssertEqual(sample0status, magData.samples[0].calibrationStatus)
        
        XCTAssertEqual(sample1channel0, magData.samples[1].x)
        XCTAssertEqual(sample1channel1, magData.samples[1].y)
        XCTAssertEqual(sample1channel2, magData.samples[1].z)
        XCTAssertEqual(sample1status, magData.samples[1].calibrationStatus)
        
        XCTAssertEqual(timeStamp, magData.timeStamp)
        XCTAssertEqual(timeStamp, magData.samples[1].timeStamp)
    }
    
    func testProcessMagnetometerCompressedDataType1WithFactor() throws {
        // Arrange
        // HEX: 06 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           06 (MAG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        let timeStamp: UInt64 = 2000000000
        // 10       frame type                                     81 (compressed, type 1)
        let magDataFrameHeader = Data([
            0x06,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x81
        ])
        
        let previousTimeStamp: UInt64 = 100
        // HEX: 37 FF 51 FD 6C F6 00 00 03 01 F8 02
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  37 FF (0xFF37 = -201)
        // 2..3     Sample 0 - channel 1 (ref. sample)  51 FD (0xFD51 = -687)
        // 4..5     Sample 0 - channel 2 (ref. sample)  6C F6 (0xF66C = -2452)
        // 6..7     Status (ref. sample)                00 00 (0x0000 = 0)
        // 8        Delta size                          03 (3 bit)
        // 9        Sample amount                       01 (1 samples)
        // 10..     Delta data                          F8 (binary: 11 111 000) 02 (binary: 0000 0010)
        // Delta channel 0                              000b
        // Delta channel 1                              111b
        // Delta channel 2                              011b
        // Delta status                                 001b
        let expectedSamplesSize = 1 + 1 // reference sample + delta samples
        let magDataFrameContent = Data([
            0x37, 0xFF, 0x51, 0xFD, 0x6C, 0xF6,
            0x00, 0x00, 0x03, 0x01, 0xF8, 0x02])
        
        let sample0channel0: Float = -201.0 / 1000
        let sample0channel1: Float = -687.0 / 1000
        let sample0channel2: Float = -2452.0 / 1000
        let sample0status = MagData.CalibrationStatus.getById(id: 0x00)
        
        let sample1channel0: Float = (-201.0 + 0x00) / 1000
        let sample1channel1: Float = (-687.0 - 0x01) / 1000
        let sample1channel2: Float = (-2452.0 + 0x3) / 1000
        let sample1status = MagData.CalibrationStatus.getById(id: 0x00 + 0x01)
        
        let factor:Float = 1.1
        let dataFrame = try PmdDataFrame(
            data: magDataFrameHeader + magDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })
        
        // Act
        let magData = try MagData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(expectedSamplesSize, magData.samples.count)
        
        XCTAssertEqual(factor * sample0channel0, magData.samples[0].x, accuracy: 0.00001)
        XCTAssertEqual(factor * sample0channel1, magData.samples[0].y, accuracy: 0.00001)
        XCTAssertEqual(factor * sample0channel2, magData.samples[0].z, accuracy: 0.00001)
        XCTAssertEqual(sample0status, magData.samples[0].calibrationStatus)
        
        XCTAssertEqual(factor * sample1channel0, magData.samples[1].x, accuracy: 0.00001)
        XCTAssertEqual(factor * sample1channel1, magData.samples[1].y, accuracy: 0.00001)
        XCTAssertEqual(factor * sample1channel2, magData.samples[1].z, accuracy: 0.00001)
        XCTAssertEqual(sample1status, magData.samples[1].calibrationStatus)
        
        XCTAssertEqual(timeStamp, magData.timeStamp)
        XCTAssertEqual(timeStamp, magData.samples[1].timeStamp)
        
    }

    func testMagGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadMagGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected MAG golden vectors")

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
                XCTAssertThrowsError(try MagData.parseDataFromDataFrame(frame: dataFrame), id) { error in
                    switch parseError {
                    case "unsupportedFrame":
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
                XCTAssertEqual(try XCTUnwrap(expected["timeStamp"] as? NSNumber, id).uint64Value, dataFrame.timeStamp, id)
                continue
            }

            let magData = try MagData.parseDataFromDataFrame(frame: dataFrame)

            XCTAssertEqual(try XCTUnwrap(expected["timeStamp"] as? NSNumber, id).uint64Value, magData.timeStamp, id)
            let samples = try XCTUnwrap(expected["samples"] as? [[String: Any]], id)
            XCTAssertEqual(samples.count, magData.samples.count, id)
            for (index, expectedSample) in samples.enumerated() {
                XCTAssertEqual(try XCTUnwrap(expectedSample["timeStamp"] as? NSNumber, id).uint64Value, magData.samples[index].timeStamp, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["x"] as? NSNumber, id).floatValue, magData.samples[index].x, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["y"] as? NSNumber, id).floatValue, magData.samples[index].y, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["z"] as? NSNumber, id).floatValue, magData.samples[index].z, id)
                XCTAssertEqual(MagData.CalibrationStatus(vectorName: try XCTUnwrap(expectedSample["calibrationStatus"] as? String, id)), magData.samples[index].calibrationStatus, id)
            }
        }
    }

    func testMagGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadMagGoldenVectors() {
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

    func testMagReadinessManifestIsPinnedBeforeParserMigration() throws {
        let manifest = try loadMagReadinessManifest()
        let id = try XCTUnwrap(manifest["id"] as? String)
        let input = try XCTUnwrap(manifest["input"] as? [String: Any], id)
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any], id)
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], id)
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], id)
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], id)

        XCTAssertEqual("mag-readiness", id)
        XCTAssertEqual("magReadiness", input["kind"] as? String, id)
        XCTAssertEqual(MAG_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, id)
        XCTAssertEqual(MAG_READINESS_FAMILIES, requiredFamilies, id)
        XCTAssertEqual(MAG_READINESS_FAMILIES, coveredFamilies, id)
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any], id)
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], id), ["com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.MagDataTest"], id)
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], id), ["MagDataTest"], id)
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], id), ["com.polar.sharedtest.MagParserCommonPolicyTest"], id)
    }

    private func loadMagGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("mag-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                guard let input = vector["input"] as? [String: Any] else {
                    return true
                }
                return input["kind"] as? String != "magReadiness"
            }
    }

    private func loadMagReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors/mag-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private let MAG_READINESS_POLICY_VECTOR_PATHS = [
        "protocol/sensors/mag-compressed-type0-factor-half.json",
        "protocol/sensors/mag-compressed-type0-truncated-delta-header-android-error.json",
        "protocol/sensors/mag-compressed-type0-truncated-delta-header-ios-reference-only.json",
        "protocol/sensors/mag-compressed-type0-truncated-delta-payload-android-error.json",
        "protocol/sensors/mag-compressed-type0-truncated-delta-payload-ios-reference-only.json",
        "protocol/sensors/mag-compressed-type0-two-samples.json",
        "protocol/sensors/mag-compressed-type1-calibration-status.json",
        "protocol/sensors/mag-compressed-type2-unsupported.json",
        "protocol/sensors/mag-raw-type0-unsupported.json"
    ]

    private let MAG_READINESS_FAMILIES = [
        "compressed-type0-reference-delta-decoding",
        "compressed-type0-factor-scaling",
        "compressed-type0-timestamp-interpolation",
        "compressed-type1-calibration-status-mapping",
        "compressed-type1-milligauss-to-gauss-conversion",
        "unsupported-raw-frame-policy",
        "unsupported-compressed-frame-policy",
        "truncated-compressed-delta-header-policy",
        "truncated-compressed-delta-payload-policy",
        "platform-mag-vector-reference-gate",
        "compile-verification-gate"
    ]
}

private extension MagData.CalibrationStatus {
    init(vectorName: String) {
        switch vectorName {
        case "NOT_AVAILABLE":
            self = .notAvailable
        case "UNKNOWN":
            self = .unknown
        case "POOR":
            self = .poor
        case "OK":
            self = .ok
        case "GOOD":
            self = .good
        default:
            self = .notAvailable
        }
    }
}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "MagDataTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "MagDataTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }
}
