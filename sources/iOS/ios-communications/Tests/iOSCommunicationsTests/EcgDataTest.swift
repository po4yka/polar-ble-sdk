//  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

final class EcgDataTest: XCTestCase {
    
    func testProcessRawEcgDataType0() throws {
        // Arrange
        // HEX: 00 00 94 35 77 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           00 (Ecg)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        let timeStamp:UInt64 = 2000000000
        // 10       frame type                                     00 (raw, type 0)
        let ecgDataFrameHeader = Data([
            0x00,
            0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00,
            0x00
        ])
        let previousTimeStamp:UInt64 = 100
        
        // HEX: 02 08 FF 02 80 00
        // index    type                                            data:
        // 0..2     uVolts                                          02 80 FF (-32766)
        let ecgValue1:Int32 = -32766
        // 3..4     uVolts                                          02 80 00 (32770)
        let ecgValue2:Int32 = 32770
        let ecgDataFrameContent = Data([0x02, 0x80, 0xFF, 0x02, 0x80, 0x00])
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data:  ecgDataFrameHeader + ecgDataFrameContent,
            { _,_ in previousTimeStamp }  ,
            { _ in factor },
            { _ in 0 })
        
        // Act
        let ecgData = try EcgData.parseDataFromDataFrame(frame: dataFrame)
        
        // Assert
        XCTAssertEqual(ecgValue1, ecgData.samples[0].microVolts)
        XCTAssertEqual(ecgValue2, ecgData.samples[1].microVolts)
        
        XCTAssertEqual(2, ecgData.samples.count)
        
        XCTAssertEqual(timeStamp, ecgData.timeStamp)
        XCTAssertEqual(timeStamp, ecgData.samples[1].timeStamp)
    }

    func testEcgRawType0ParserUsesSharedKmpWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        let dataFrameHex = "000094357700000000000280ff028000"
        let sharedRows = try XCTUnwrap(EcgDataRuntimePlanner.type0Samples(dataFrameHex: dataFrameHex, previousTimeStamp: 100, factor: 1.0, sampleRate: 0))
        XCTAssertEqual("1000000050,-32766|2000000000,32770", sharedRows)

        let frame = try PmdDataFrame(
            data: Data(hexString: dataFrameHex),
            { _, _ in 100 },
            { _ in 1.0 },
            { _ in 0 })
        let ecgData = try EcgData.parseDataFromDataFrame(frame: frame)

        XCTAssertEqual(2, ecgData.samples.count)
        XCTAssertEqual(1_000_000_050, ecgData.samples[0].timeStamp)
        XCTAssertEqual(-32766, ecgData.samples[0].microVolts)
        XCTAssertEqual(2_000_000_000, ecgData.samples[1].timeStamp)
        XCTAssertEqual(32770, ecgData.samples[1].microVolts)
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testEcgGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadEcgGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected ECG golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            if let platforms = vector["platforms"] as? [String: Any], let supported = platforms["ios"] as? Bool, !supported {
                continue
            }
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
            let dataFrame = try PmdDataFrame(
                data: Data(hexString: try XCTUnwrap(input["dataFrameHex"] as? String, id)),
                { _, _ in UInt64(truncating: input["previousTimeStamp"] as? NSNumber ?? 0) },
                { _ in Float(truncating: input["factor"] as? NSNumber ?? 1.0) },
                { _ in UInt(truncating: input["sampleRate"] as? NSNumber ?? 0) })
            let ecgData = try EcgData.parseDataFromDataFrame(frame: dataFrame)

            XCTAssertEqual(try XCTUnwrap(expected["timeStamp"] as? NSNumber, id).uint64Value, ecgData.timeStamp, id)
            let samples = try XCTUnwrap(expected["samples"] as? [[String: Any]], id)
            XCTAssertEqual(samples.count, ecgData.samples.count, id)
            for (index, expectedSample) in samples.enumerated() {
                XCTAssertEqual(try XCTUnwrap(expectedSample["timeStamp"] as? NSNumber, id).uint64Value, ecgData.samples[index].timeStamp, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["microVolts"] as? NSNumber, id).int32Value, ecgData.samples[index].microVolts, id)
            }
        }
    }

    func testEcgGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadEcgGoldenVectors() {
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

    func testEcgReadinessManifestPinsParserOwnership() throws {
        let manifest = try loadEcgReadinessManifest()
        let id = try XCTUnwrap(manifest["id"] as? String)
        let input = try XCTUnwrap(manifest["input"] as? [String: Any], id)
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any], id)
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any], id)
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], id)
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], id)
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], id)

        XCTAssertEqual("ecg-readiness", id)
        XCTAssertEqual("ecgReadiness", input["kind"] as? String, id)
        XCTAssertEqual(ECG_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, id)
        XCTAssertEqual(ECG_READINESS_FAMILIES, requiredFamilies, id)
        XCTAssertEqual(ECG_READINESS_FAMILIES, coveredFamilies, id)
        XCTAssertEqual(["com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.EcgDataTest"], consumerTests["android"] as? [String], id)
        XCTAssertEqual(["EcgDataTest"], consumerTests["ios"] as? [String], id)
        XCTAssertEqual(["com.polar.sharedtest.EcgParserCommonPolicyTest"], consumerTests["commonPrototype"] as? [String], id)
    }

    private func loadEcgGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("ecg-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                guard let input = vector["input"] as? [String: Any] else {
                    return true
                }
                return input["kind"] as? String != "ecgReadiness"
            }
    }

    private func loadEcgReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors/ecg-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private let ECG_READINESS_POLICY_VECTOR_PATHS = [
        "protocol/sensors/ecg-raw-type0-signed-24bit-boundaries.json",
        "protocol/sensors/ecg-raw-type0-truncated-sample-android-error.json",
        "protocol/sensors/ecg-raw-type0-two-samples.json",
        "protocol/sensors/ecg-raw-type1-android-status-bits.json",
        "protocol/sensors/ecg-raw-type2-android-tags.json",
        "protocol/sensors/ecg-raw-type3-android-frame-samples.json"
    ]

    private let ECG_READINESS_FAMILIES = [
        "raw-type0-signed-24bit-parsing",
        "raw-type0-boundary-values",
        "raw-type0-timestamp-interpolation",
        "raw-type0-malformed-short-sample-policy",
        "android-raw-type1-status-bit-ownership",
        "android-raw-type2-tag-ownership",
        "android-raw-type3-frame-sample-ownership",
        "platform-ecg-vector-reference-gate",
        "compile-verification-gate"
    ]
}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "EcgDataTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "EcgDataTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }
}
