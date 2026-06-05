///  Copyright © 2023 Polar. All rights reserved.

import XCTest
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif
@testable import iOSCommunications

final class SkinTemperatureDataTest: XCTestCase {

    func testUncompressedSkinTemperatureData() throws {
        let previousTimeStamp: UInt64 = 120

        let temperatureDataFrameHeader = Data([
            0x07, 0x40, 0xAE, 0x21, 0xAE, 0x31, 0xB2, 0xEE, 0x0A, 0x00
        ])

        let temperatureDataFrameContent = Data([
            0xF6, 0x28, 0xC0, 0x41
        ])

        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: temperatureDataFrameHeader + temperatureDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })

        let temperatureData = try SkinTemperatureData.parseDataFromDataFrame(frame: dataFrame)
        
        XCTAssertEqual(1,temperatureData.samples.count)
        XCTAssertEqual(24.0200005, temperatureData.samples[0].skinTemperature)
        XCTAssertEqual(787762911281000000, temperatureData.samples[0].timeStamp)
    }
    
    func testCompressedSkinTemperatureData() throws {
        let previousTimeStamp: UInt64 = 120
        
        
        let skinTemperatureDataFrameHeader = Data([
            0x07, 0x40, 0xAE, 0x21, 0xAE, 0x31, 0xB2, 0xEE, 0x0A, 0x80
        ])
        
        let skinTemperatureDataFrameContent = Data([
            0xEC, 0x51, 0xDC, 0x41, 0x03, 0x02, 0x00
        ])

        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: skinTemperatureDataFrameHeader + skinTemperatureDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })

        let temperatureData = try SkinTemperatureData.parseDataFromDataFrame(frame: dataFrame)
        
        XCTAssertEqual(3,temperatureData.samples.count)
        XCTAssertEqual(27.54, temperatureData.samples[0].skinTemperature)
        XCTAssertEqual(27.54, temperatureData.samples[1].skinTemperature)
        XCTAssertEqual(27.54, temperatureData.samples[2].skinTemperature)
    }

    func testSkinTemperatureRawType0ParserUsesSharedKmpWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        let dataFrameHex = "0740ae21ae31b2ee0a00f628c041"
        let sharedRows = try XCTUnwrap(PolarIosSharedBridge.shared.skinTemperatureRawType0Samples(dataFrameHex: dataFrameHex, previousTimeStamp: 787762910281000000, factor: 1.0, sampleRate: 4))
        XCTAssertFalse(sharedRows.isEmpty)

        let dataFrame = try PmdDataFrame(
            data: Data(hexString: dataFrameHex),
            { _, _ in 787762910281000000 },
            { _ in 1.0 },
            { _ in 4 })
        let skinTemperatureData = try SkinTemperatureData.parseDataFromDataFrame(frame: dataFrame)
        let sharedSamples = try sharedRows.split(separator: "|").map { row -> (UInt64, Float) in
            let fields = row.split(separator: ",")
            return (
                try XCTUnwrap(UInt64(fields[0])),
                try XCTUnwrap(Float(fields[1]))
            )
        }

        XCTAssertEqual(sharedSamples.count, skinTemperatureData.samples.count)
        for (index, sharedSample) in sharedSamples.enumerated() {
            XCTAssertEqual(sharedSample.0, skinTemperatureData.samples[index].timeStamp)
            XCTAssertEqual(sharedSample.1, skinTemperatureData.samples[index].skinTemperature, accuracy: 0.00001)
            XCTAssertFalse(skinTemperatureData.samples[index].isTimestampEstimated)
        }
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testSkinTemperatureGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadSkinTemperatureGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected skin-temperature golden vectors")

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
                XCTAssertThrowsError(try SkinTemperatureData.parseDataFromDataFrame(frame: dataFrame), id) { error in
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

            let skinTemperatureData = try SkinTemperatureData.parseDataFromDataFrame(frame: dataFrame)

            let samples = try XCTUnwrap(expected["samples"] as? [[String: Any]], id)
            XCTAssertEqual(samples.count, skinTemperatureData.samples.count, id)
            let platformExpectations = vector["platformExpectations"] as? [String: Any]
            let iosExpectations = platformExpectations?["ios"] as? [String: Any]
            let iosSamples = iosExpectations?["samples"] as? [[String: Any]]
            for (index, expectedSample) in samples.enumerated() {
                let actualSample = skinTemperatureData.samples[index]
                XCTAssertEqual(try XCTUnwrap(expectedSample["timeStamp"] as? NSNumber, id).uint64Value, actualSample.timeStamp, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["skinTemperature"] as? NSNumber, id).floatValue, actualSample.skinTemperature, accuracy: 0.00001, id)
                if let expectedEstimated = iosSamples?[index]["isTimestampEstimated"] as? Bool {
                    XCTAssertEqual(expectedEstimated, actualSample.isTimestampEstimated, id)
                }
            }
        }
    }

    func testSkinTemperatureGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadSkinTemperatureGoldenVectors() {
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

    func testSkinTemperatureReadinessManifestIsPinnedBeforeParserMigration() throws {
        let manifest = try loadSkinTemperatureReadinessManifest()
        let id = try XCTUnwrap(manifest["id"] as? String)
        let input = try XCTUnwrap(manifest["input"] as? [String: Any], id)
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any], id)
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any], id)
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], id)
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], id)
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], id)

        XCTAssertEqual("skin-temperature-readiness", id)
        XCTAssertEqual("skinTemperatureReadiness", input["kind"] as? String, id)
        XCTAssertEqual(SKIN_TEMPERATURE_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, id)
        XCTAssertEqual(SKIN_TEMPERATURE_READINESS_FAMILIES, requiredFamilies, id)
        XCTAssertEqual(SKIN_TEMPERATURE_READINESS_FAMILIES, coveredFamilies, id)
        XCTAssertEqual(["com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.SkinTemperatureDataTest"], consumerTests["android"] as? [String], id)
        XCTAssertEqual(["SkinTemperatureDataTest"], consumerTests["ios"] as? [String], id)
        XCTAssertEqual(["com.polar.sharedtest.SkinTemperatureParserCommonPolicyTest"], consumerTests["commonPrototype"] as? [String], id)
    }

    private func loadSkinTemperatureGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("skin-temperature-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                guard let input = vector["input"] as? [String: Any] else {
                    return true
                }
                return input["kind"] as? String != "skinTemperatureReadiness"
            }
    }

    private func loadSkinTemperatureReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors/skin-temperature-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private let SKIN_TEMPERATURE_READINESS_POLICY_VECTOR_PATHS = [
        "protocol/sensors/skin-temperature-raw-type0-estimated-sample-rate.json",
        "protocol/sensors/skin-temperature-raw-type0-single-sample.json",
        "protocol/sensors/skin-temperature-raw-type0-truncated-sample-android-error.json",
        "protocol/sensors/skin-temperature-raw-type0-truncated-sample-ios-empty.json",
        "protocol/sensors/skin-temperature-raw-type1-unsupported.json"
    ]

    private let SKIN_TEMPERATURE_READINESS_FAMILIES = [
        "raw-type0-ieee754-skin-temperature-parsing",
        "sample-rate-timestamp-estimation-policy",
        "unsupported-raw-frame-policy",
        "truncated-raw-sample-policy",
        "ios-empty-malformed-payload-deferral",
        "platform-skin-temperature-vector-reference-gate",
        "compile-verification-gate"
    ]
}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "SkinTemperatureDataTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "SkinTemperatureDataTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }
}
