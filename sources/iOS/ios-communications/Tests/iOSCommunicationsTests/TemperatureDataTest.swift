///  Copyright © 2023 Polar. All rights reserved.

import XCTest
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif
@testable import iOSCommunications

final class TemperatureDataTest: XCTestCase {

    func testUncompressedTemperatureData() throws {
        let previousTimeStamp: UInt64 = 120
        
        let temperatureDataFrameHeader = Data([
            0x0C, 0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00, 0x00
        ])

        let temperatureDataFrameContent = Data([
            0xF6, 0x28, 0xC0, 0x41
        ])
        
        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: temperatureDataFrameHeader + temperatureDataFrameContent,
            { _,_ in previousTimeStamp }  ,
            { _ in factor },
            { _ in 0 })

        let temperatureData = try TemperatureData.parseDataFromDataFrame(frame: dataFrame)
        
        XCTAssertEqual(1,temperatureData.samples.count)
        XCTAssertEqual(24.0200005, temperatureData.samples[0].temperature)
        XCTAssertEqual(2000000000, temperatureData.samples[0].timeStamp)
    }
    
    func testCompressedTemperatureData() throws {
        let previousTimeStamp: UInt64 = 120
        
        
        let temperatureDataFrameHeader = Data([
            0x0C, 0x00, 0x94, 0x35, 0x77, 0x00, 0x00, 0x00, 0x00, 0x80
        ])

        let temperatureDataFrameContent = Data([
            0xEC, 0x51, 0xDC, 0x41, 0x03, 0x02, 0x00
        ])

        let factor:Float = 1.0
        let dataFrame = try PmdDataFrame(
            data: temperatureDataFrameHeader + temperatureDataFrameContent,
            { _,_ in previousTimeStamp },
            { _ in factor },
            { _ in 0 })

        let temperatureData = try TemperatureData.parseDataFromDataFrame(frame: dataFrame)
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual(3, temperatureData.samples.count)
        XCTAssertEqual(666666747, temperatureData.samples[0].timeStamp)
        XCTAssertEqual(27.54, temperatureData.samples[0].temperature, accuracy: 0.00001)
        XCTAssertEqual(1333333373, temperatureData.samples[1].timeStamp)
        XCTAssertEqual(27.54, temperatureData.samples[1].temperature, accuracy: 0.00001)
        XCTAssertEqual(2000000000, temperatureData.samples[2].timeStamp)
        XCTAssertEqual(27.54, temperatureData.samples[2].temperature, accuracy: 0.00001)
        #else
        XCTAssertEqual(3,temperatureData.samples.count)
        XCTAssertEqual(27.54, temperatureData.samples[0].temperature)
        XCTAssertEqual(27.54, temperatureData.samples[1].temperature)
        XCTAssertEqual(27.54, temperatureData.samples[2].temperature)
        #endif
    }

    func testTemperatureRawType0ParserUsesSharedKmpWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        let dataFrameHex = "0c009435770000000000f628c041"
        let sharedRows = try XCTUnwrap(TemperatureDataRuntimePlanner.rawType0Samples(dataFrameHex: dataFrameHex, previousTimeStamp: 120, factor: 1.0, sampleRate: 0))
        XCTAssertFalse(sharedRows.isEmpty)

        let dataFrame = try PmdDataFrame(
            data: Data(hexString: dataFrameHex),
            { _, _ in 120 },
            { _ in 1.0 },
            { _ in 0 })
        let temperatureData = try TemperatureData.parseDataFromDataFrame(frame: dataFrame)
        let sharedSamples = try sharedRows.split(separator: "|").map { row -> (UInt64, Float) in
            let fields = row.split(separator: ",")
            return (
                try XCTUnwrap(UInt64(fields[0])),
                try XCTUnwrap(Float(fields[1]))
            )
        }

        XCTAssertEqual(sharedSamples.count, temperatureData.samples.count)
        for (index, sharedSample) in sharedSamples.enumerated() {
            XCTAssertEqual(sharedSample.0, temperatureData.samples[index].timeStamp)
            XCTAssertEqual(sharedSample.1, temperatureData.samples[index].temperature, accuracy: 0.00001)
        }
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testPressureRawType0ParserUsesSharedKmpWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        let dataFrameHex = "0b009435770000000000ae277b44"
        let sharedRows = try XCTUnwrap(PressureDataRuntimePlanner.rawType0Samples(dataFrameHex: dataFrameHex, previousTimeStamp: 100, factor: 1.0, sampleRate: 0))
        XCTAssertFalse(sharedRows.isEmpty)

        let dataFrame = try PmdDataFrame(
            data: Data(hexString: dataFrameHex),
            { _, _ in 100 },
            { _ in 1.0 },
            { _ in 0 })
        let pressureData = try PressureData.parseDataFromDataFrame(frame: dataFrame)
        let sharedSamples = try sharedRows.split(separator: "|").map { row -> (UInt64, Float) in
            let fields = row.split(separator: ",")
            return (
                try XCTUnwrap(UInt64(fields[0])),
                try XCTUnwrap(Float(fields[1]))
            )
        }

        XCTAssertEqual(sharedSamples.count, pressureData.samples.count)
        for (index, sharedSample) in sharedSamples.enumerated() {
            XCTAssertEqual(sharedSample.0, pressureData.samples[index].timeStamp)
            XCTAssertEqual(sharedSample.1, pressureData.samples[index].pressure, accuracy: 0.00001)
        }
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func testTemperatureGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadTemperatureGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected temperature golden vectors")

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
                XCTAssertThrowsError(try TemperatureData.parseDataFromDataFrame(frame: dataFrame), id) { error in
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

            let temperatureData = try TemperatureData.parseDataFromDataFrame(frame: dataFrame)

            let samples = try XCTUnwrap(expected["samples"] as? [[String: Any]], id)
            XCTAssertEqual(samples.count, temperatureData.samples.count, id)
            for pair in zip(samples, temperatureData.samples) {
                let (expectedSample, actualSample) = pair
                XCTAssertEqual(try XCTUnwrap(expectedSample["timeStamp"] as? NSNumber, id).uint64Value, actualSample.timeStamp, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["temperature"] as? NSNumber, id).floatValue, actualSample.temperature, accuracy: 0.00001, id)
            }
        }
    }

    func testTemperatureGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadTemperatureGoldenVectors() {
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

    func testPressureGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadPressureGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected pressure golden vectors")

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
                XCTAssertThrowsError(try PressureData.parseDataFromDataFrame(frame: dataFrame), id) { error in
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

            let pressureData = try PressureData.parseDataFromDataFrame(frame: dataFrame)

            let samples = try XCTUnwrap(expected["samples"] as? [[String: Any]], id)
            XCTAssertEqual(samples.count, pressureData.samples.count, id)
            for pair in zip(samples, pressureData.samples) {
                let (expectedSample, actualSample) = pair
                XCTAssertEqual(try XCTUnwrap(expectedSample["timeStamp"] as? NSNumber, id).uint64Value, actualSample.timeStamp, id)
                XCTAssertEqual(try XCTUnwrap(expectedSample["pressure"] as? NSNumber, id).floatValue, actualSample.pressure, accuracy: 0.00001, id)
            }
        }
    }

    func testPressureGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadPressureGoldenVectors() {
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

    func testPressureTemperatureReadinessManifestPinsScalarParserOwnership() throws {
        let manifest = try loadPressureTemperatureReadinessManifest()
        let id = try XCTUnwrap(manifest["id"] as? String)
        let input = try XCTUnwrap(manifest["input"] as? [String: Any], id)
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any], id)
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any], id)
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], id)
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], id)
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], id)

        XCTAssertEqual("pressure-temperature-readiness", id)
        XCTAssertEqual("pressureTemperatureReadiness", input["kind"] as? String, id)
        XCTAssertEqual(PRESSURE_TEMPERATURE_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, id)
        XCTAssertEqual(PRESSURE_TEMPERATURE_READINESS_FAMILIES, requiredFamilies, id)
        XCTAssertEqual(PRESSURE_TEMPERATURE_READINESS_FAMILIES, coveredFamilies, id)
        XCTAssertEqual(["com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PressureDataTest", "com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.TemperatureDataTest"], consumerTests["android"] as? [String], id)
        XCTAssertEqual(["TemperatureDataTest"], consumerTests["ios"] as? [String], id)
        XCTAssertEqual(["com.polar.sharedtest.PressureTemperatureParserCommonPolicyTest"], consumerTests["commonPrototype"] as? [String], id)
    }

    private func loadPressureGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("pressure-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                guard let input = vector["input"] as? [String: Any] else {
                    return true
                }
                return input["kind"] as? String != "pressureTemperatureReadiness"
            }
    }


    private func loadTemperatureGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("temperature-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
    }

    private func loadPressureTemperatureReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/sensors/pressure-temperature-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }

    private let PRESSURE_TEMPERATURE_READINESS_POLICY_VECTOR_PATHS = [
        "protocol/sensors/pressure-compressed-type0-android-factor-half.json",
        "protocol/sensors/pressure-raw-type0-negative-single-sample.json",
        "protocol/sensors/pressure-raw-type0-single-sample.json",
        "protocol/sensors/pressure-raw-type0-truncated-sample-android-error.json",
        "protocol/sensors/pressure-raw-type1-unsupported.json",
        "protocol/sensors/temperature-compressed-type0-flat-deltas-android-two-samples.json",
        "protocol/sensors/temperature-compressed-type0-flat-deltas.json",
        "protocol/sensors/temperature-raw-type0-ieee754-boundaries.json",
        "protocol/sensors/temperature-raw-type0-negative-single-sample.json",
        "protocol/sensors/temperature-raw-type0-single-sample.json",
        "protocol/sensors/temperature-raw-type0-truncated-sample-android-error.json",
        "protocol/sensors/temperature-raw-type1-unsupported.json"
    ]

    private let PRESSURE_TEMPERATURE_READINESS_FAMILIES = [
        "pressure-raw-type0-ieee754-parsing",
        "temperature-raw-type0-ieee754-parsing",
        "negative-and-boundary-float-values",
        "raw-type0-timestamp-interpolation",
        "unsupported-raw-frame-policy",
        "unsupported-compressed-frame-policy",
        "truncated-raw-sample-policy",
        "compressed-pressure-shared-type0-parser",
        "compressed-temperature-shared-type0-parser",
        "platform-pressure-temperature-vector-reference-gate",
        "compile-verification-gate"
    ]

}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "TemperatureDataTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "TemperatureDataTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }
}
