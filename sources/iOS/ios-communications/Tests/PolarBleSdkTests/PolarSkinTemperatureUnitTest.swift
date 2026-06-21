//
//  Copyright © 2025 Polar. All rights reserved.
//

import XCTest
@testable import PolarBleSdk

class PolarSkinTemperatureUtilsTests: XCTestCase {
    
    var mockClient: MockBlePsFtpClient!
    private static var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return calendar
    }

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testSkinTemperatureReadHeaderUsesSharedFileFacadePlanning() throws {
        let date = try XCTUnwrap(Self.utcCalendar.date(from: DateComponents(year: 2026, month: 1, day: 2)))

        let operation = PolarSkinTemperatureUtils.skinTemperatureReadOperation(date: date)
        XCTAssertEqual(operation.command, .get)
        XCTAssertEqual(operation.path, "/U/0/20260102/SKINTEMP/TEMPCONT.BPB")
    }
    
    func testReadSkinTemperatureDataFromDayDirectory_SuccessfulResponse() async throws {
        // Arrange
        let date = Date()
        var proto = Data_TemperatureMeasurementPeriod()
        proto.measurementType = .tmSkinTemperature
        proto.sensorLocation = .slProximal
        proto.temperatureMeasurementSamples.append(contentsOf: Self.buildSkinTemperatureSamplesProto())
        mockClient.requestReturnValue = .success(try proto.serializedData())

        let expectedResult = PolarSkinTemperatureData.PolarSkinTemperatureResult(
            date: date,
            sensorLocation: .SL_PROXIMAL,
            measurementType: .TM_SKIN_TEMPERATURE,
            skinTemperatureList: Self.buildSkinTemperatureExpectedData()
        )

        // Act
        let testResult = await PolarSkinTemperatureUtils.readSkinTemperatureData(client: mockClient, date: date)

        // Assert
        XCTAssertNotNil(testResult)
        XCTAssertEqual(testResult?.measurementType, expectedResult.measurementType)
        XCTAssertEqual(testResult?.sensorLocation, expectedResult.sensorLocation)
        XCTAssertEqual(testResult?.skinTemperatureList?.first?.recordingTimeDeltaMs, expectedResult.skinTemperatureList?.first?.recordingTimeDeltaMs)
        XCTAssertEqual(testResult?.skinTemperatureList?.first?.temperature, expectedResult.skinTemperatureList?.first?.temperature)
    }
    
    func testReadSkinTemperatureDataFromDayDirectory_FileNotFound() async {
        // Arrange
        mockClient.requestReturnValue = .failure(NSError(domain: "File not found", code: 103, userInfo: nil))

        // Act – errors are swallowed; method returns nil
        let result = await PolarSkinTemperatureUtils.readSkinTemperatureData(client: mockClient, date: Date())

        // Assert
        XCTAssertNil(result, "Expected nil when file is not found")
    }

    func testSkinTemperatureEnumMappingUsesProductionPolicy() async throws {
        var proto = Data_TemperatureMeasurementPeriod()
        proto.measurementType = .tmCoreTemperature
        proto.sensorLocation = .slDistal
        mockClient.requestReturnValue = .success(try proto.serializedData())

        let result = await PolarSkinTemperatureUtils.readSkinTemperatureData(client: mockClient, date: Date())

        XCTAssertEqual(result?.measurementType, .TM_CORE_TEMPERATURE)
        XCTAssertEqual(result?.sensorLocation, .SL_DISTAL)
    }

    func testSkinTemperatureGoldenVectorsMapProtoToPublicModel() async throws {
        for vector in try loadSkinTemperatureGoldenVectors() {
            let id = try XCTUnwrap(vector["id"] as? String)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let proto = try buildProto(from: XCTUnwrap(input["proto"] as? [String: Any], id), id: id)
            mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
            mockClient.requestReturnValue = .success(try proto.serializedData())

            let result = await PolarSkinTemperatureUtils.readSkinTemperatureData(client: mockClient, date: Date(timeIntervalSince1970: 0))

            try assertSkinTemperatureResult(result, expected: XCTUnwrap((vector["platformExpectations"] as? [String: Any])?["ios"] as? [String: Any], id), id: id)
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
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            XCTAssertNotNil(input["proto"], id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertEqual(platforms["android"] as? Bool, true, id)
            XCTAssertEqual(platforms["ios"] as? Bool, true, id)
            XCTAssertEqual(platforms["common"] as? Bool, true, id)
        }
    }

    func testSkinTemperatureDomainReadinessManifestPinsModelOwnership() throws {
        let readiness = try loadSkinTemperatureDomainReadinessManifest()
        let input = try XCTUnwrap(readiness["input"] as? [String: Any])
        let expected = try XCTUnwrap(readiness["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(readiness["consumerTests"] as? [String: Any])
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        XCTAssertEqual(readiness["id"] as? String, "skin-temperature-domain-readiness")
        XCTAssertEqual(input["kind"] as? String, "skinTemperatureDomainReadiness")
        XCTAssertEqual(policyVectorPaths, [
            "sdk/skin-temperature/core-proximal-empty-samples.json",
            "sdk/skin-temperature/distal-skin-two-samples.json",
            "sdk/skin-temperature/unknown-enums-platform-policy.json"
        ])
        let expectedFamilies = [
            "source-device-id-ownership",
            "empty-sample-list-preservation",
            "sample-delta-preservation",
            "sample-temperature-preservation",
            "measurement-type-mapping",
            "sensor-location-mapping",
            "unknown-measurement-type-boundary",
            "unknown-sensor-location-boundary",
            "platform-skin-temperature-vector-reference-gate",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "Skin-temperature domain shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS skin-temperature tests continue to reference the same vectors, sourceDeviceId ownership remains explicit, empty sample lists and sample values are preserved, measurement and sensor-location mappings are covered, unknown enum behavior is handled at a typed boundary before public model exposure, and the shared tests are compile-verified.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.sdk.api.model.utils.PolarSkinTemperatureUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["PolarSkinTemperatureUnitTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.SkinTemperatureDomainCommonPolicyTest"])
    }

    // MARK: - Helpers

    private static func buildSkinTemperatureSamplesProto() -> [Data_TemperatureMeasurementSample] {
        var skinTempSample = Data_TemperatureMeasurementSample()
        skinTempSample.recordingTimeDeltaMilliseconds = 0
        skinTempSample.temperatureCelsius = 37.0
        return [skinTempSample]
    }
    
    private static func buildSkinTemperatureExpectedData() -> [PolarSkinTemperatureData.PolarSkinTemperatureDataSample] {
        return [PolarSkinTemperatureData.PolarSkinTemperatureDataSample(recordingTimeDeltaMs: 0, temperature: 37.0)]
    }

    private func buildProto(from fields: [String: Any], id: String) throws -> Data_TemperatureMeasurementPeriod {
        var proto = Data_TemperatureMeasurementPeriod()
        proto.sourceDeviceID = try XCTUnwrap(fields["sourceDeviceId"] as? String, id)
        let measurementType = try number(fields, "measurementType", id: id)
        let sensorLocation = try number(fields, "sensorLocation", id: id)
        proto.measurementType = TemperatureMeasurementType(rawValue: measurementType) ?? .UNRECOGNIZED(measurementType)
        proto.sensorLocation = SensorLocation(rawValue: sensorLocation) ?? .UNRECOGNIZED(sensorLocation)
        proto.temperatureMeasurementSamples = try XCTUnwrap(fields["samples"] as? [[String: Any]], id).map { sample in
            var protoSample = Data_TemperatureMeasurementSample()
            protoSample.recordingTimeDeltaMilliseconds = UInt64(try! number(sample, "recordingTimeDeltaMs", id: id))
            protoSample.temperatureCelsius = try! float(sample, "temperature", id: id)
            return protoSample
        }
        return proto
    }

    private func assertSkinTemperatureResult(_ actual: PolarSkinTemperatureData.PolarSkinTemperatureResult?, expected: [String: Any], id: String) throws {
        let result = try XCTUnwrap(actual, id)
        try assertOptionalString(result.sensorLocation.rawValue, expected, "sensorLocation", id: id)
        try assertOptionalString(result.measurementType.rawValue, expected, "measurementType", id: id)
        let expectedSamples = try XCTUnwrap(expected["samples"] as? [[String: Any]], id)
        XCTAssertEqual(result.skinTemperatureList?.count, expectedSamples.count, id)
        for (index, expectedSample) in expectedSamples.enumerated() {
            let actualSample = try XCTUnwrap(result.skinTemperatureList?[index], "\(id) sample \(index)")
            XCTAssertEqual(actualSample.recordingTimeDeltaMs, UInt64(try number(expectedSample, "recordingTimeDeltaMs", id: id)), "\(id) sample \(index)")
            XCTAssertEqual(actualSample.temperature, try float(expectedSample, "temperature", id: id), accuracy: 0.00001, "\(id) sample \(index)")
        }
    }

    private func assertOptionalString(_ actual: String?, _ expected: [String: Any], _ key: String, id: String) throws {
        guard expected.keys.contains(key) else { return }
        if expected[key] is NSNull {
            XCTAssertNil(actual, "\(id) \(key)")
        } else {
            XCTAssertEqual(actual, try XCTUnwrap(expected[key] as? String, "\(id) \(key)"), "\(id) \(key)")
        }
    }

    private func loadSkinTemperatureGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/skin-temperature")
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
                return input?["kind"] as? String != "skinTemperatureDomainReadiness"
            }
    }

    private func loadSkinTemperatureDomainReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/skin-temperature/skin-temperature-domain-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private func number(_ object: [String: Any], _ key: String, id: String) throws -> Int {
        return try XCTUnwrap(object[key] as? NSNumber, "\(id) \(key)").intValue
    }

    private func float(_ object: [String: Any], _ key: String, id: String) throws -> Float {
        return try XCTUnwrap(object[key] as? NSNumber, "\(id) \(key)").floatValue
    }
}
