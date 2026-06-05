//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

final class GyrDataTest: XCTestCase {
    
    func testProcessCompressedDataType0() throws {
        // Arrange
        // HEX: 05 FF FF FF FF FF FF FF 7F 80
        // index                                                   data:
        // 0        type                                           05 (GYRO)
        // 1..9     timestamp                                      FF FF FF FF FF FF FF 7F
        let timeStamp:UInt64 = 9223372036854775807
        // 10       frame type                                     80 (compressed, type 0)
        
        let gyroDataFrameHeader = Data([
            0x05,
            0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x7F,
            0x80,
        ])
        let previousTimeStamp:UInt64 = 100
        
        // HEX: EA FF 08 00 0D 00 03 01 DF 00
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  EA FF (0xFFEA = -22)
        // 2..3     Sample 0 - channel 1 (ref. sample)  08 00 (0x0008 = 8)
        // 4..5     Sample 0 - channel 2 (ref. sample)  0D 00 (0x000D = 13)
        // 6        Delta size                          03 (3 bit)
        // 7        Sample amount                       01 (1 samples)
        // 8..      Delta data                          DF (binary: 11 011 111) 00 (binary: 0000000 0)
        // Delta channel 0                              111b
        // Delta channel 1                              011b
        // Delta channel 2                              011b
        let expectedSamplesSize = 1 + 1 // reference sample + delta samples
        
        let sample0channel0:Float = -22.0
        let sample0channel1:Float = 8.0
        let sample0channel2:Float = 13.0
        
        let sample1channel0:Float = sample0channel0 - 0x1
        let sample1channel1:Float = sample0channel1 + 0x3
        let sample1channel2:Float = sample0channel2 + 0x3
        
        let gyroDataFrameContent = Data([
            0xEA, 0xFF,
            0x08, 0x00, 0x0D, 0x00,
            0x03, 0x01, 0xDF, 0x00
        ])
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: gyroDataFrameHeader + gyroDataFrameContent,
            { _,_ in previousTimeStamp }  ,
            { _ in factor },
            { _ in 0 })
        
        // Act
        let gyroData = try GyrData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(expectedSamplesSize, gyroData.samples.count)
        XCTAssertEqual(sample0channel0, gyroData.samples[0].x)
        XCTAssertEqual(sample0channel1, gyroData.samples[0].y)
        XCTAssertEqual(sample0channel2, gyroData.samples[0].z)
        
        XCTAssertEqual(sample1channel0, gyroData.samples[1].x)
        XCTAssertEqual(sample1channel1, gyroData.samples[1].y)
        XCTAssertEqual(sample1channel2, gyroData.samples[1].z)
        
        XCTAssertEqual(timeStamp, gyroData.timeStamp)
        XCTAssertEqual(timeStamp, gyroData.samples.last?.timeStamp)
    }

    func testGyrCompressedType0ParserUsesSharedKmpWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        let dataFrameHex = "05ffffffffffffff7f80eaff08000d000301df00"
        let sharedRows = try XCTUnwrap(PolarIosSharedBridge.shared.gyrCompressedType0Samples(dataFrameHex: dataFrameHex, previousTimeStamp: 100, factor: 1.0, sampleRate: 0))
        XCTAssertFalse(sharedRows.isEmpty)

        let dataFrame = try PmdDataFrame(
            data: Data(hexString: dataFrameHex),
            { _, _ in 100 },
            { _ in 1.0 },
            { _ in 0 })
        let gyrData = try GyrData.parseDataFromDataFrame(frame: dataFrame)
        let sharedSamples = try sharedRows.split(separator: "|").map { row -> (UInt64, Float, Float, Float) in
            let fields = row.split(separator: ",")
            return (
                try XCTUnwrap(UInt64(fields[0])),
                try XCTUnwrap(Float(fields[1])),
                try XCTUnwrap(Float(fields[2])),
                try XCTUnwrap(Float(fields[3]))
            )
        }

        XCTAssertEqual(sharedSamples.count, gyrData.samples.count)
        for (index, sharedSample) in sharedSamples.enumerated() {
            XCTAssertEqual(sharedSample.0, gyrData.samples[index].timeStamp)
            XCTAssertEqual(sharedSample.1, gyrData.samples[index].x)
            XCTAssertEqual(sharedSample.2, gyrData.samples[index].y)
            XCTAssertEqual(sharedSample.3, gyrData.samples[index].z)
        }
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testGyrGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadGyrGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected GYR golden vectors")

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
                XCTAssertThrowsError(try GyrData.parseDataFromDataFrame(frame: dataFrame), id) { error in
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

            let gyrData = try GyrData.parseDataFromDataFrame(frame: dataFrame)

            XCTAssertEqual(try XCTUnwrap(expected["timeStamp"] as? NSNumber, id).uint64Value, gyrData.timeStamp, id)
            let samples = try XCTUnwrap(expected["samples"] as? [[String: Any]], id)
            XCTAssertEqual(samples.count, gyrData.samples.count, id)
            for (index, expectedSample) in samples.enumerated() {
                XCTAssertEqual(try XCTUnwrap(expectedSample["timeStamp"] as? NSNumber, id).uint64Value, gyrData.samples[index].timeStamp, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["x"] as? NSNumber, id).floatValue, gyrData.samples[index].x, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["y"] as? NSNumber, id).floatValue, gyrData.samples[index].y, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["z"] as? NSNumber, id).floatValue, gyrData.samples[index].z, id)
            }
        }
    }

    func testGyrGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadGyrGoldenVectors() {
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

    func testGyrReadinessManifestIsPinnedBeforeParserMigration() throws {
        let manifest = try loadGyrReadinessManifest()
        let id = try XCTUnwrap(manifest["id"] as? String)
        let input = try XCTUnwrap(manifest["input"] as? [String: Any], id)
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any], id)
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], id)
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], id)
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], id)

        XCTAssertEqual("gyr-readiness", id)
        XCTAssertEqual("gyrReadiness", input["kind"] as? String, id)
        XCTAssertEqual(GYR_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, id)
        XCTAssertEqual(GYR_READINESS_FAMILIES, requiredFamilies, id)
        XCTAssertEqual(GYR_READINESS_FAMILIES, coveredFamilies, id)
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any], id)
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], id), ["com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.GyrDataTest"], id)
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], id), ["GyrDataTest"], id)
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], id), ["com.polar.sharedtest.GyrParserCommonPolicyTest"], id)
    }

    private func loadGyrGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("gyr-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                guard let input = vector["input"] as? [String: Any] else {
                    return true
                }
                return input["kind"] as? String != "gyrReadiness"
            }
    }

    private func loadGyrReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors/gyr-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private let GYR_READINESS_POLICY_VECTOR_PATHS = [
        "protocol/sensors/gyr-compressed-type0-factor-half.json",
        "protocol/sensors/gyr-compressed-type0-truncated-delta-header-android-error.json",
        "protocol/sensors/gyr-compressed-type0-truncated-delta-header-ios-reference-only.json",
        "protocol/sensors/gyr-compressed-type0-truncated-delta-payload-android-error.json",
        "protocol/sensors/gyr-compressed-type0-truncated-delta-payload-ios-reference-only.json",
        "protocol/sensors/gyr-compressed-type0-two-samples.json",
        "protocol/sensors/gyr-compressed-type1-android-only.json",
        "protocol/sensors/gyr-compressed-type2-unsupported.json",
        "protocol/sensors/gyr-raw-type0-unsupported.json"
    ]

    private let GYR_READINESS_FAMILIES = [
        "compressed-type0-reference-delta-decoding",
        "compressed-type0-factor-scaling",
        "compressed-type0-timestamp-interpolation",
        "unsupported-raw-frame-policy",
        "unsupported-compressed-frame-policy",
        "android-compressed-type1-ownership",
        "truncated-compressed-delta-header-policy",
        "truncated-compressed-delta-payload-policy",
        "platform-gyr-vector-reference-gate",
        "compile-verification-gate"
    ]
}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "GyrDataTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "GyrDataTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }
}
