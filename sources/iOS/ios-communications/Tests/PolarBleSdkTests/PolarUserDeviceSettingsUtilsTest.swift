// Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk

final class PolarUserDeviceSettingsUtilsTest: XCTestCase {

    // MARK: - Properties

    var mockFtpClient: MockBlePsFtpClient!

    override func setUpWithError() throws {
        mockFtpClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockFtpClient = nil
    }

    // MARK: - Path constants

    func testDeviceSettingsFilePathConstant() {
        XCTAssertEqual("/U/0/S/UDEVSET.BPB", DEVICE_SETTINGS_FILE_PATH)
    }

    func testSensorSettingsFilePathConstant() {
        XCTAssertEqual("/UDEVSET.BPB", SENSOR_SETTINGS_FILE_PATH)
    }

    func testDeviceLocationStringHelpersPreservePublicMapping() {
        XCTAssertEqual("UNDEFINED", PolarUserDeviceSettings.getStringValue(deviceLocationIndex: 0))
        XCTAssertEqual("WRIST_LEFT", PolarUserDeviceSettings.getStringValue(deviceLocationIndex: 2))
        XCTAssertEqual("BIKE_MOUNT", PolarUserDeviceSettings.getStringValue(deviceLocationIndex: 13))
        XCTAssertEqual(.WRIST_RIGHT, PolarUserDeviceSettings.getDeviceLocation(deviceLocation: "WRIST_RIGHT"))
        XCTAssertEqual(.UNDEFINED, PolarUserDeviceSettings.getDeviceLocation(deviceLocation: "UNKNOWN_LOCATION"))
        XCTAssertEqual(PolarUserDeviceSettings.DeviceLocation.allCases.map { $0.rawValue }, PolarUserDeviceSettings.getAllAsString())
    }

    func testUserDeviceSettingsToProtoWritesAutomaticTrainingDetectionOn() {
        let model = PolarUserDeviceSettings()
        model.deviceLocation = .WRIST_LEFT
        model.automaticTrainingDetectionMode = .ON
        model.automaticTrainingDetectionSensitivity = 77
        model.minimumTrainingDurationSeconds = 300

        let proto = PolarUserDeviceSettings.toProto(userDeviceSettings: model)

        XCTAssertTrue(proto.hasAutomaticMeasurementSettings)
        XCTAssertTrue(proto.automaticMeasurementSettings.hasAutomaticTrainingDetectionSettings)
        XCTAssertEqual(.on, proto.automaticMeasurementSettings.automaticTrainingDetectionSettings.state)
        XCTAssertEqual(77, proto.automaticMeasurementSettings.automaticTrainingDetectionSettings.sensitivity)
        XCTAssertEqual(300, proto.automaticMeasurementSettings.automaticTrainingDetectionSettings.minimumTrainingDurationSeconds)
    }

    func testAutomaticMeasurementStateMappingPreservesAutosFilesPolicy() {
        XCTAssertEqual(.alwaysOn, PolarUserDeviceSettings.automaticMeasurementState(enabled: true))
        XCTAssertEqual(.off, PolarUserDeviceSettings.automaticMeasurementState(enabled: false))
    }

    // MARK: - Request encoding

    func testGetUserDeviceSettings_sendsRequestWithCorrectPath() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto())

        _ = try await PolarUserDeviceSettingsUtils
            .getUserDeviceSettings(client: mockFtpClient, deviceSettingsPath: DEVICE_SETTINGS_FILE_PATH)

        XCTAssertEqual(1, mockFtpClient.requestCalls.count)
        let sentOperation = try Protocol_PbPFtpOperation(serializedBytes: mockFtpClient.requestCalls[0])
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, sentOperation.path)
        XCTAssertEqual(.get, sentOperation.command)
    }

    func testGetUserDeviceSettings_sendsGetCommand() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto())

        _ = try await PolarUserDeviceSettingsUtils
            .getUserDeviceSettings(client: mockFtpClient, deviceSettingsPath: SENSOR_SETTINGS_FILE_PATH)

        let sentOperation = try Protocol_PbPFtpOperation(serializedBytes: mockFtpClient.requestCalls[0])
        XCTAssertEqual(.get, sentOperation.command)
        XCTAssertEqual(SENSOR_SETTINGS_FILE_PATH, sentOperation.path)
    }

    // MARK: - Successful response: deviceLocation

    func testGetUserDeviceSettings_wristLeft_parsedCorrectly() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto(makeProto(location: .deviceLocationWristLeft)))
        let result = try await awaitResult()
        XCTAssertEqual(PolarUserDeviceSettings.DeviceLocation.WRIST_LEFT, result?.deviceLocation)
    }

    func testGetUserDeviceSettings_wristRight_parsedCorrectly() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto(makeProto(location: .deviceLocationWristRight)))
        let result = try await awaitResult()
        XCTAssertEqual(PolarUserDeviceSettings.DeviceLocation.WRIST_RIGHT, result?.deviceLocation)
    }

    func testGetUserDeviceSettings_chest_parsedCorrectly() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto(makeProto(location: .deviceLocationChest)))
        let result = try await awaitResult()
        XCTAssertEqual(PolarUserDeviceSettings.DeviceLocation.CHEST, result?.deviceLocation)
    }

    // MARK: - Successful response: USB connection mode

    func testGetUserDeviceSettings_usbModeOn_parsedCorrectly() async throws {
        var proto = makeProto(location: .deviceLocationWristLeft)
        var usbSettings = Data_PbUsbConnectionSettings()
        usbSettings.mode = .on
        proto.usbConnectionSettings = usbSettings
        mockFtpClient.requestReturnValue = .success(encodedProto(proto))
        let result = try await awaitResult()
        XCTAssertEqual(.ON, result?.usbConnectionMode)
    }

    func testGetUserDeviceSettings_usbModeOff_parsedCorrectly() async throws {
        var proto = makeProto(location: .deviceLocationWristLeft)
        var usbSettings = Data_PbUsbConnectionSettings()
        usbSettings.mode = .off
        proto.usbConnectionSettings = usbSettings
        mockFtpClient.requestReturnValue = .success(encodedProto(proto))
        let result = try await awaitResult()
        XCTAssertEqual(.OFF, result?.usbConnectionMode)
    }

    func testGetUserDeviceSettings_noUsbSettings_usbModeIsNil() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto(makeProto(location: .deviceLocationWristLeft)))
        let result = try await awaitResult()
        XCTAssertNil(result?.usbConnectionMode)
    }

    // MARK: - Successful response: telemetry

    func testGetUserDeviceSettings_telemetryEnabled_parsedCorrectly() async throws {
        var proto = makeProto(location: .deviceLocationWristLeft)
        var telemetry = Data_PbUserDeviceTelemetrySettings()
        telemetry.telemetryEnabled = true
        proto.telemetrySettings = telemetry
        mockFtpClient.requestReturnValue = .success(encodedProto(proto))
        let result = try await awaitResult()
        XCTAssertEqual(true, result?.telemetryEnabled)
    }

    func testGetUserDeviceSettings_telemetryDisabled_parsedCorrectly() async throws {
        var proto = makeProto(location: .deviceLocationWristLeft)
        var telemetry = Data_PbUserDeviceTelemetrySettings()
        telemetry.telemetryEnabled = false
        proto.telemetrySettings = telemetry
        mockFtpClient.requestReturnValue = .success(encodedProto(proto))
        let result = try await awaitResult()
        XCTAssertEqual(false, result?.telemetryEnabled)
    }

    func testGetUserDeviceSettings_noTelemetrySettings_telemetryIsNil() async throws {
        mockFtpClient.requestReturnValue = .success(encodedProto(makeProto(location: .deviceLocationWristLeft)))
        let result = try await awaitResult()
        XCTAssertNil(result?.telemetryEnabled)
    }

    // MARK: - Successful response: automatic training detection

    func testGetUserDeviceSettings_trainingDetectionOn_parsedCorrectly() async throws {
        var proto = makeProto(location: .deviceLocationWristLeft)
        var atdSettings = Data_PbAutomaticTrainingDetectionSettings()
        atdSettings.state = .on
        atdSettings.sensitivity = 75
        atdSettings.minimumTrainingDurationSeconds = 300
        proto.automaticMeasurementSettings.automaticTrainingDetectionSettings = atdSettings
        mockFtpClient.requestReturnValue = .success(encodedProto(proto))
        let result = try await awaitResult()
        XCTAssertEqual(.ON, result?.automaticTrainingDetectionMode)
        XCTAssertEqual(75, result?.automaticTrainingDetectionSensitivity)
        XCTAssertEqual(300, result?.minimumTrainingDurationSeconds)
    }

    // MARK: - Error handling

    func testGetUserDeviceSettings_clientError_propagatesError() async throws {
        mockFtpClient.requestReturnValue = .failure(PolarErrors.serviceNotFound)
        do {
            _ = try await PolarUserDeviceSettingsUtils
                .getUserDeviceSettings(client: mockFtpClient, deviceSettingsPath: DEVICE_SETTINGS_FILE_PATH)
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertNotNil(error)
        }
    }

    func testGetUserDeviceSettings_invalidProtoData_propagatesError() async throws {
        mockFtpClient.requestReturnValue = .success(Data([0xFF, 0xFF, 0xFF, 0xFF]))
        do {
            _ = try await PolarUserDeviceSettingsUtils
                .getUserDeviceSettings(client: mockFtpClient, deviceSettingsPath: DEVICE_SETTINGS_FILE_PATH)
            XCTFail("Expected error to be thrown")
        } catch {
            XCTAssertNotNil(error)
        }
    }

    func testUserDeviceSettingsGoldenVectorsMatchIOSBehavior() throws {
        let vectors = try loadUserDeviceSettingsGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected user device settings golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            guard try XCTUnwrap(platforms["ios"] as? Bool, id) else {
                continue
            }
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try expectedForIOS(vector, id: id)

            if let protoInput = input["proto"] as? [String: Any], let expectedModel = expected["model"] as? [String: Any] {
                let proto = try userDeviceSettingsProto(from: protoInput, id: id)
                let model = PolarUserDeviceSettings.fromProto(pbUserDeviceSettings: proto)
                try assertUserDeviceSettingsModel(model, matches: expectedModel, id: id)
            }

            if let modelInput = input["model"] as? [String: Any], let expectedProto = expected["proto"] as? [String: Any] {
                let model = try userDeviceSettingsModel(from: modelInput, id: id)
                let proto = PolarUserDeviceSettings.toProto(userDeviceSettings: model)
                try assertUserDeviceSettingsProto(proto, matches: expectedProto, id: id)
            }
        }
    }

    func testUserDeviceSettingsGoldenVectorsFollowNeutralKmpVectorShape() throws {
        for vector in try loadUserDeviceSettingsGoldenVectors() {
            let id = vector["id"] as? String ?? "unknown-vector"

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

    func testUserDeviceSettingsModelReadinessManifestIsPinnedBeforeModelMigration() throws {
        let manifest = try loadUserDeviceSettingsModelReadinessManifest()
        let id = try XCTUnwrap(manifest["id"] as? String)
        let input = try XCTUnwrap(manifest["input"] as? [String: Any], id)
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any], id)
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any], id)
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], id)
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], id)
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], id)

        XCTAssertEqual("user-device-settings-model-readiness", id)
        XCTAssertEqual("userDeviceSettingsModelReadiness", try XCTUnwrap(input["kind"] as? String, id))
        XCTAssertEqual("compileVerifiedPreMigrationCharacterization", try XCTUnwrap(expected["migrationReadiness"] as? String, id))
        XCTAssertEqual(USER_DEVICE_SETTINGS_MODEL_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, id)
        XCTAssertEqual(USER_DEVICE_SETTINGS_MODEL_READINESS_FAMILIES, requiredFamilies, id)
        XCTAssertEqual(USER_DEVICE_SETTINGS_MODEL_READINESS_FAMILIES, coveredFamilies, id)
        XCTAssertEqual(["com.polar.sdk.api.model.PolarUserDeviceSettingsTest"], try XCTUnwrap(consumerTests["android"] as? [String], id), id)
        XCTAssertEqual(["PolarUserDeviceSettingsUtilsTest"], try XCTUnwrap(consumerTests["ios"] as? [String], id), id)
        XCTAssertEqual(["com.polar.sharedtest.UserDeviceSettingsCommonPolicyTest"], try XCTUnwrap(consumerTests["commonPrototype"] as? [String], id), id)
    }
}

private let USER_DEVICE_SETTINGS_MODEL_READINESS_POLICY_VECTOR_PATHS = [
    "sdk/user-device-settings/from-proto-full-settings.json",
    "sdk/user-device-settings/from-proto-omitted-optional-settings.json",
    "sdk/user-device-settings/to-proto-telemetry-platform-difference.json",
    "sdk/user-device-settings/to-proto-writable-settings.json"
]

private let USER_DEVICE_SETTINGS_MODEL_READINESS_FAMILIES = [
    "protobuf-presence-preservation",
    "nullable-omitted-optional-settings",
    "writable-settings-serialization",
    "encoder-owned-trusted-last-modified",
    "explicit-telemetry-write-policy",
    "platform-default-divergence",
    "platform-user-device-settings-vector-references",
    "compile-verification-gate"
]

// MARK: - Helpers

extension PolarUserDeviceSettingsUtilsTest {

    private func makeValidProto() -> Data_PbUserDeviceSettings {
        var proto = Data_PbUserDeviceSettings()
        proto.generalSettings = Data_PbUserDeviceGeneralSettings()
        proto.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date())
        proto.automaticMeasurementSettings.automaticTrainingDetectionSettings.sensitivity = 50
        proto.automaticMeasurementSettings.automaticTrainingDetectionSettings.minimumTrainingDurationSeconds = 600
        return proto
    }

    private func makeProto(location: PbDeviceLocation) -> Data_PbUserDeviceSettings {
        var proto = makeValidProto()
        proto.generalSettings.deviceLocation = location
        return proto
    }

    private func encodedProto(_ proto: Data_PbUserDeviceSettings? = nil) -> Data {
        return try! (proto ?? makeValidProto()).serializedData()
    }

    private func awaitResult(
        path: String = DEVICE_SETTINGS_FILE_PATH
    ) async throws -> PolarUserDeviceSettings.PolarUserDeviceSettingsResult? {
        return try await PolarUserDeviceSettingsUtils
            .getUserDeviceSettings(client: mockFtpClient, deviceSettingsPath: path)
    }

    private func loadUserDeviceSettingsGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/user-device-settings")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                guard let input = vector["input"] as? [String: Any] else {
                    return true
                }
                return input["kind"] as? String != "userDeviceSettingsModelReadiness"
            }
    }

    private func loadUserDeviceSettingsModelReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/user-device-settings/settings-model-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }


    private func expectedForIOS(_ vector: [String: Any], id: String) throws -> [String: Any] {
        if let platforms = vector["platformExpectations"] as? [String: Any] {
            return try XCTUnwrap(platforms["ios"] as? [String: Any], id)
        }
        return try XCTUnwrap(vector["expected"] as? [String: Any], id)
    }

    private func userDeviceSettingsProto(from object: [String: Any], id: String) throws -> Data_PbUserDeviceSettings {
        var proto = (object["minimal"] as? Bool) == true ? Data_PbUserDeviceSettings() : makeValidProto()
        if (object["minimal"] as? Bool) == true {
            proto.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date(timeIntervalSince1970: 0))
        }
        if let location = object["deviceLocation"] as? NSNumber {
            proto.generalSettings.deviceLocation = try XCTUnwrap(PbDeviceLocation(rawValue: location.intValue), id)
        }
        if let usbMode = object["usbConnectionMode"] as? String {
            var usbSettings = Data_PbUsbConnectionSettings()
            usbSettings.mode = try usbConnectionMode(from: usbMode, id: id)
            proto.usbConnectionSettings = usbSettings
        }
        if object["automaticTrainingDetectionMode"] != nil || object["automaticTrainingDetectionSensitivity"] != nil || object["minimumTrainingDurationSeconds"] != nil || object["autosFilesEnabled"] != nil {
            if let mode = object["automaticTrainingDetectionMode"] as? String {
                proto.automaticMeasurementSettings.automaticTrainingDetectionSettings.state = try automaticTrainingDetectionState(from: mode, id: id)
            }
            if let sensitivity = object["automaticTrainingDetectionSensitivity"] as? NSNumber {
                proto.automaticMeasurementSettings.automaticTrainingDetectionSettings.sensitivity = sensitivity.uint32Value
            }
            if let minimumDuration = object["minimumTrainingDurationSeconds"] as? NSNumber {
                proto.automaticMeasurementSettings.automaticTrainingDetectionSettings.minimumTrainingDurationSeconds = minimumDuration.uint32Value
            }
            if let autosFilesEnabled = object["autosFilesEnabled"] as? Bool {
                var ohr = Data_PbAutomaticMeasurementSettings()
                ohr.state = PolarUserDeviceSettings.automaticMeasurementState(enabled: autosFilesEnabled)
                proto.automaticMeasurementSettings.automaticOhrMeasurement = ohr
            }
        }
        if let telemetryEnabled = object["telemetryEnabled"] as? Bool {
            var telemetry = Data_PbUserDeviceTelemetrySettings()
            telemetry.telemetryEnabled = telemetryEnabled
            proto.telemetrySettings = telemetry
        }
        return proto
    }

    private func userDeviceSettingsModel(from object: [String: Any], id: String) throws -> PolarUserDeviceSettings {
        let model = PolarUserDeviceSettings()
        if let location = object["deviceLocation"] as? NSNumber {
            model.deviceLocation = PolarUserDeviceSettings.DeviceLocation.allCases[location.intValue]
        }
        if let usbConnectionMode = object["usbConnectionMode"] as? Bool {
            model.usbConnectionMode = usbConnectionMode ? .ON : .OFF
        }
        if let automaticTrainingDetectionMode = object["automaticTrainingDetectionMode"] as? Bool {
            model.automaticTrainingDetectionMode = automaticTrainingDetectionMode ? .ON : .OFF
        }
        if let sensitivity = object["automaticTrainingDetectionSensitivity"] as? NSNumber {
            model.automaticTrainingDetectionSensitivity = sensitivity.uint32Value
        }
        if let minimumDuration = object["minimumTrainingDurationSeconds"] as? NSNumber {
            model.minimumTrainingDurationSeconds = minimumDuration.uint32Value
        }
        if let telemetryEnabled = object["telemetryEnabled"] as? Bool {
            model.telemetryEnabled = telemetryEnabled
        }
        if let autosFilesEnabled = object["autosFilesEnabled"] as? Bool {
            model.autosFilesEnabled = autosFilesEnabled
        }
        return model
    }

    private func assertUserDeviceSettingsModel(_ actual: PolarUserDeviceSettings.PolarUserDeviceSettingsResult, matches expected: [String: Any], id: String) throws {
        if let deviceLocation = expected["deviceLocation"] as? NSNumber {
            XCTAssertEqual(actual.deviceLocation.toInt(), deviceLocation.intValue, id)
        }
        try assertOptional(actual.usbConnectionMode?.rawValue == "ON", matches: expected["usbConnectionMode"], id: id, field: "usbConnectionMode")
        try assertOptional(actual.automaticTrainingDetectionMode?.rawValue == "ON", matches: expected["automaticTrainingDetectionMode"], id: id, field: "automaticTrainingDetectionMode")
        try assertOptionalNumber(actual.automaticTrainingDetectionSensitivity, matches: expected["automaticTrainingDetectionSensitivity"], id: id, field: "automaticTrainingDetectionSensitivity")
        try assertOptionalNumber(actual.minimumTrainingDurationSeconds, matches: expected["minimumTrainingDurationSeconds"], id: id, field: "minimumTrainingDurationSeconds")
        try assertOptional(actual.telemetryEnabled, matches: expected["telemetryEnabled"], id: id, field: "telemetryEnabled")
        try assertOptional(actual.autosFilesEnabled, matches: expected["autosFilesEnabled"], id: id, field: "autosFilesEnabled")
    }

    private func assertUserDeviceSettingsProto(_ actual: Data_PbUserDeviceSettings, matches expected: [String: Any], id: String) throws {
        if let deviceLocation = expected["deviceLocation"] as? NSNumber {
            XCTAssertTrue(actual.hasGeneralSettings, id)
            XCTAssertEqual(actual.generalSettings.deviceLocation.rawValue, deviceLocation.intValue, id)
        }
        if let hasLastModified = expected["hasLastModified"] as? Bool {
            XCTAssertEqual(actual.hasLastModified, hasLastModified, id)
        }
        if let lastModifiedTrusted = expected["lastModifiedTrusted"] as? Bool {
            XCTAssertTrue(actual.hasLastModified, id)
            XCTAssertEqual(actual.lastModified.trusted, lastModifiedTrusted, id)
        }
        if let usbConnectionModeName = expected["usbConnectionMode"] as? String {
            XCTAssertTrue(actual.hasUsbConnectionSettings, id)
            XCTAssertEqual(actual.usbConnectionSettings.mode, try usbConnectionMode(from: usbConnectionModeName, id: id), id)
        }
        if let automaticTrainingDetectionMode = expected["automaticTrainingDetectionMode"] as? String {
            XCTAssertTrue(actual.hasAutomaticMeasurementSettings, id)
            XCTAssertEqual(actual.automaticMeasurementSettings.automaticTrainingDetectionSettings.state, try automaticTrainingDetectionState(from: automaticTrainingDetectionMode, id: id), id)
        }
        if let sensitivity = expected["automaticTrainingDetectionSensitivity"] as? NSNumber {
            XCTAssertEqual(actual.automaticMeasurementSettings.automaticTrainingDetectionSettings.sensitivity, sensitivity.uint32Value, id)
        }
        if let minimumDuration = expected["minimumTrainingDurationSeconds"] as? NSNumber {
            XCTAssertEqual(actual.automaticMeasurementSettings.automaticTrainingDetectionSettings.minimumTrainingDurationSeconds, minimumDuration.uint32Value, id)
        }
        if let autosFilesEnabled = expected["autosFilesEnabled"] as? Bool {
            XCTAssertTrue(actual.automaticMeasurementSettings.hasAutomaticOhrMeasurement, id)
            XCTAssertEqual(actual.automaticMeasurementSettings.automaticOhrMeasurement.state, PolarUserDeviceSettings.automaticMeasurementState(enabled: autosFilesEnabled), id)
        }
        if let hasTelemetryEnabled = expected["hasTelemetryEnabled"] as? Bool {
            XCTAssertEqual(actual.hasTelemetrySettings && actual.telemetrySettings.hasTelemetryEnabled, hasTelemetryEnabled, id)
        }
        if let telemetryEnabled = expected["telemetryEnabled"] as? Bool {
            XCTAssertEqual(actual.telemetrySettings.telemetryEnabled, telemetryEnabled, id)
        }
    }

    private func assertOptional(_ actual: Bool?, matches expected: Any?, id: String, field: String) throws {
        guard let expected else {
            return
        }
        if expected is NSNull {
            XCTAssertNil(actual, "\(id) \(field)")
        } else {
            XCTAssertEqual(actual, try XCTUnwrap(expected as? Bool, "\(id) \(field)"), "\(id) \(field)")
        }
    }

    private func assertOptionalNumber(_ actual: UInt32?, matches expected: Any?, id: String, field: String) throws {
        guard let expected else {
            return
        }
        if expected is NSNull {
            XCTAssertNil(actual, "\(id) \(field)")
        } else {
            XCTAssertEqual(actual, try XCTUnwrap(expected as? NSNumber, "\(id) \(field)").uint32Value, "\(id) \(field)")
        }
    }

    private func usbConnectionMode(from value: String, id: String) throws -> Data_PbUsbConnectionSettings.PbUsbConnectionMode {
        switch value {
        case "ON": return .on
        case "OFF": return .off
        default:
            XCTFail("Unsupported USB connection mode in \(id): \(value)")
            return .unknown
        }
    }

    private func automaticTrainingDetectionState(from value: String, id: String) throws -> Data_PbAutomaticTrainingDetectionSettings.PbAutomaticTrainingDetectionState {
        switch value {
        case "ON": return .on
        case "OFF": return .off
        default:
            XCTFail("Unsupported automatic training detection mode in \(id): \(value)")
            return .off
        }
    }
}
