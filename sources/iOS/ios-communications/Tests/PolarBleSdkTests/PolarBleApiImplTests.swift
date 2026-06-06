// Copyright © 2026 Polar Electro Oy. All rights reserved.

import XCTest
import Combine
import CoreBluetooth
import Foundation

@testable import PolarBleSdk

private let REST_FACADE_RUNTIME_READINESS_COMMON_DECISION = "REST facade runtime migration may proceed only after rest-facade-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, model JSON mapping vectors remain linked, empty-response and malformed-response parse/decode failures plus response-error transport policies stay covered, public facade error mapping is pinned for service-list and service-description response errors, and the shared tests are compile-verified."
private let FILE_FACADE_RUNTIME_READINESS_COMMON_DECISION = "File facade runtime migration may proceed only after file-facade-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, directory-list traversal vectors remain linked, runtime-error-policy.json keeps malformed-directory, response-error, transport-error, empty read payload, delete request failure, write progress before completion, read/write/delete response-error, and write-stream failure behavior covered, public facade error mapping is pinned, and the shared tests are compile-verified."
private let COMMAND_RUNTIME_READINESS_COMMON_DECISION = "Command runtime migration may proceed only after reset-sync-h10-command-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, H10 query failure propagation, every reset-style notification failure propagation, and public facade error mapping are pinned, sync-start and sync-stop platform splits are preserved or explicitly reconciled, and the shared tests are compile-verified."
private let STORED_DATA_CLEANUP_READINESS_COMMON_DECISION = "Stored-data cleanup migration may proceed only after cleanup-workflow-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, cleanup list-failure and empty-parent remove-path splits are preserved in adapters or reconciled explicitly, public facade error mapping is pinned, and the shared tests are compile-verified."
private let DISK_TIME_RUNTIME_READINESS_COMMON_DECISION = "Disk/time facade runtime migration may proceed only after disk-time-query-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, filesystem capability gates remain platform-owned, public facade error mapping is pinned for disk-space and local-time query failures, V2 two-query time setting and H10 single-query behavior are preserved or explicitly reconciled, and the shared tests are compile-verified."
private let COMMAND_RUNTIME_POLICY_COMMON_DECISION = "Promote reset/H10 command planning before sync error handling; H10 query failures and reset notification failures are shared transport-error propagation, while sync failure terminals remain platform compatibility gates."
private let STORED_DATA_CLEANUP_POLICY_COMMON_DECISION = "Promote cleanup traversal and filtering before platform-specific public error/path adapters; do not normalize Android/iOS cleanup failure behavior implicitly."
private let DISK_TIME_RUNTIME_POLICY_COMMON_DECISION = "Promote disk/time query planning only after facade tests keep current H10 capability behavior and V2 two-query time-setting semantics pinned."
private let USER_DEVICE_SETTINGS_RUNTIME_READINESS_COMMON_DECISION = "User-device-settings runtime migration may proceed only after settings-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, protobuf field preservation and public facade error mapping are pinned, read-failure no-write and write-failure-after-payload behavior for telemetry, location, USB, automatic-training-detection, and automatic-OHR writes remain covered, daylight-saving payload shape is preserved, and the shared tests are compile-verified."
private let USER_DEVICE_SETTINGS_RUNTIME_POLICY_COMMON_DECISION = "Promote user-device-settings runtime only after read/write sequencing, no-write read failures, write-failure payload preservation, and platform protobuf serializer differences remain covered by executable facade and model vectors."
private let REST_FACADE_RUNTIME_POLICY_COMMON_DECISION = "Promote REST facade request planning only after service-list and description success cases, service-list and service-description request failures, response-error platform mapping, empty-success and malformed-success parse/decode failures, model JSON mapping vectors, and lower-level empty-response/response-error transport policy remain explicitly covered."
private let FILE_FACADE_RUNTIME_POLICY_COMMON_DECISION = "Promote low-level file facade planning only after read/write/delete public APIs reference this vector, directory traversal remains covered by list-files vectors, and runtime-error-policy.json keeps malformed directory, response-error, transport-error, empty read payload, delete request failure, write progress success, and write-stream failure behavior pinned."
private let OFFLINE_TRIGGER_RUNTIME_POLICY_COMMON_DECISION = "Shared offline trigger runtime code should model set-mode, status-read, per-feature setting writes, optional secret attachment, and get/set transport failures as typed steps before mapping them back to Android and iOS public errors."
private let COMMAND_RUNTIME_POLICY_OPERATION_IDS = ["h10-start-recording", "h10-start-recording-query-failure", "h10-stop-recording", "h10-stop-recording-query-failure", "h10-recording-status", "h10-recording-status-query-failure", "factory-reset", "factory-reset-notification-failure", "factory-reset-preserve-pairing", "factory-reset-preserve-pairing-notification-failure", "restart", "restart-notification-failure", "warehouse-sleep", "warehouse-sleep-notification-failure", "turn-device-off", "turn-device-off-notification-failure", "sync-start-success", "sync-start-query-failure", "sync-stop-success", "sync-stop-notification-failure"]
private let DISK_TIME_RUNTIME_POLICY_OPERATION_IDS = ["get-disk-space", "get-local-time", "get-local-time-with-zone", "set-local-time-v2", "set-local-time-h10", "set-local-time-failure", "get-local-time-failure", "get-local-time-with-zone-failure", "get-disk-space-failure"]
private let STORED_DATA_CLEANUP_POLICY_SCENARIO_IDS = ["telemetry-root-trc-bin-filter", "sdlogs-extension-filter", "activity-prune-empty-parents", "automatic-sample-embedded-day-filter", "sdlogs-list-failure-platform-policy", "telemetry-list-failure-platform-policy"]
private let USER_DEVICE_SETTINGS_RUNTIME_POLICY_OPERATION_IDS = ["get-user-device-settings", "get-user-device-settings-read-failure", "set-telemetry-enabled", "set-telemetry-read-failure", "set-telemetry-write-failure", "set-user-device-location", "set-user-device-location-write-failure", "set-usb-connection-mode", "set-usb-connection-mode-write-failure", "set-automatic-training-detection", "set-automatic-training-detection-write-failure", "set-automatic-ohr-measurement", "set-automatic-ohr-measurement-write-failure", "set-daylight-saving-time"]
private let REST_FACADE_RUNTIME_POLICY_OPERATION_IDS = ["list-rest-api-services-success", "get-rest-api-description-success", "list-rest-api-services-request-failure", "get-rest-api-description-request-failure", "list-rest-api-services-response-error", "get-rest-api-description-response-error", "list-rest-api-services-empty-success", "list-rest-api-services-malformed-success", "get-rest-api-description-empty-success", "get-rest-api-description-malformed-success"]
private let FILE_FACADE_RUNTIME_POLICY_OPERATION_IDS = ["read-low-level-file-success", "read-low-level-file-empty-success", "read-low-level-file-request-failure", "read-low-level-file-response-error", "write-low-level-file-success", "write-low-level-file-progress-success", "write-low-level-file-stream-failure", "write-low-level-file-response-error", "delete-low-level-file-success", "delete-low-level-file-request-failure", "delete-low-level-file-response-error"]
private let OFFLINE_TRIGGER_RUNTIME_POLICY_SCENARIO_IDS = ["set-trigger-success-with-secret", "set-trigger-mode-error", "set-trigger-status-read-error", "set-trigger-setting-error", "get-trigger-success", "get-trigger-transport-error"]

/// Unit tests for `PolarBleApiImpl`.
final class PolarBleApiImplTests: XCTestCase {

    // MARK: - Properties

    private let deviceId = "ABCDEF01"
    private var v2MockClient: MockBlePsFtpClient!
    private var v2MockSession: MockBleDeviceSession!
    private var v2Api: PolarBleApiImplWithMockSession!
    private var h10MockClient: MockBlePsFtpClient!
    private var h10MockSession: MockH10BleDeviceSession!
    private var h10Api: PolarBleApiImplWithMockH10Session!
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Set-up / Tear-down

    override func setUpWithError() throws {
        // Ensure device-capability JSON is loaded so fileSystemType()/isRecordingSupported()
        // return correct values for all device types used in these tests ("360", "h10", …).
        BlePolarDeviceCapabilitiesUtility.resetAndInitializeForTesting(
            deviceFileSystemTypes: ["h10": .h10FileSystem],
            defaultFileSystemType: .polarFileSystemV2,
            defaultRecordingSupported: false
        )
        let gatt = MockPolarGattServiceTransmitter()
        v2MockClient = MockBlePsFtpClient(gattServiceTransmitter: gatt)
        v2MockSession = MockBleDeviceSession(mockFtpClient: v2MockClient)
        v2Api = PolarBleApiImplWithMockSession(mockDeviceSession: v2MockSession)
        let h10Gatt = MockPolarGattServiceTransmitter()
        h10MockClient = MockBlePsFtpClient(gattServiceTransmitter: h10Gatt)
        h10MockSession = MockH10BleDeviceSession(mockFtpClient: h10MockClient)
        h10Api = PolarBleApiImplWithMockH10Session(mockDeviceSession: h10MockSession)
    }

    override func tearDownWithError() throws {
        v2MockClient = nil; v2MockSession = nil; v2Api = nil
        h10MockClient = nil; h10MockSession = nil; h10Api = nil
        cancellables.removeAll()
    }

    // MARK: - Helpers

    private func assertSinglePolicyReadinessManifest(path: String, id: String, kind: String, policyPath: String, families: [String], commonDecision: String? = nil, androidConsumers: [String]? = nil, iosConsumers: [String]? = nil, commonPrototypeConsumers: [String]? = nil, file: StaticString = #filePath, line: UInt = #line) throws {
        let vectorURL = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/\(path)")
        let manifest = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: vectorURL)) as? [String: Any], file: file, line: line)
        let input = try XCTUnwrap(manifest["input"] as? [String: Any], file: file, line: line)
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any], file: file, line: line)
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], file: file, line: line)
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], file: file, line: line)
        XCTAssertEqual(id, manifest["id"] as? String, file: file, line: line)
        XCTAssertEqual(kind, input["kind"] as? String, file: file, line: line)
        XCTAssertEqual(policyPath, input["policyVectorPath"] as? String, file: file, line: line)
        XCTAssertEqual(families, requiredFamilies, file: file, line: line)
        XCTAssertEqual(families, coveredFamilies, file: file, line: line)
        let actualCommonDecision = try XCTUnwrap(expected["commonDecision"] as? String, file: file, line: line)
        if let commonDecision {
            XCTAssertEqual(commonDecision, actualCommonDecision, file: file, line: line)
            let commonRuntimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], file: file, line: line)
            XCTAssertEqual(commonRuntimePrototype["status"] as? String, "executable shared commonTest runtime planning guard", file: file, line: line)
            XCTAssertEqual(commonRuntimePrototype["reason"] as? String, "Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", file: file, line: line)
        } else {
            XCTAssertTrue(actualCommonDecision.contains("compile-verified"), file: file, line: line)
        }
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any], file: file, line: line)
        if let androidConsumers {
            XCTAssertEqual(androidConsumers, try XCTUnwrap(consumerTests["android"] as? [String], file: file, line: line), file: file, line: line)
        }
        if let iosConsumers {
            XCTAssertEqual(iosConsumers, try XCTUnwrap(consumerTests["ios"] as? [String], file: file, line: line), file: file, line: line)
        }
        if let commonPrototypeConsumers {
            XCTAssertEqual(commonPrototypeConsumers, try XCTUnwrap(consumerTests["commonPrototype"] as? [String], file: file, line: line), file: file, line: line)
        }
    }

    private func assertCommandRuntimePolicyVectorContains(_ vectorTerm: String, file: StaticString = #filePath, line: UInt = #line) throws {
        let vectorURL = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/command-runtime/reset-sync-h10-command-policy.json")
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: vectorURL)) as? [String: Any], file: file, line: line)
        let input = try XCTUnwrap(vector["input"] as? [String: Any], file: file, line: line)
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], file: file, line: line)
        let operations = try XCTUnwrap(input["operations"] as? [[String: Any]], file: file, line: line)
        let commonRuntimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], file: file, line: line)
        let commonRuntimeCases = try XCTUnwrap(commonRuntimePrototype["cases"] as? [[String: Any]], file: file, line: line)
        let operationIds = try operations.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        let commonRuntimeCaseIds = try commonRuntimeCases.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        XCTAssertEqual(vector["id"] as? String, "reset-sync-h10-command-policy", file: file, line: line)
        XCTAssertEqual(vector["case"] as? String, "reset_sync_h10_command_policy", file: file, line: line)
        XCTAssertEqual(COMMAND_RUNTIME_POLICY_OPERATION_IDS, operationIds, file: file, line: line)
        XCTAssertEqual(COMMAND_RUNTIME_POLICY_OPERATION_IDS, commonRuntimeCaseIds, file: file, line: line)
        XCTAssertEqual(vectorTerm, operationIds.first { $0 == vectorTerm }, file: file, line: line)
        let execution = try XCTUnwrap(vector["execution"] as? [String: Any], file: file, line: line)
        XCTAssertEqual(execution["kind"] as? String, "fake-command-runtime-policy", file: file, line: line)
        XCTAssertEqual(execution["transport"] as? String, "public-facade-command-capture", file: file, line: line)
        XCTAssertEqual(vector["commonDecision"] as? String, COMMAND_RUNTIME_POLICY_COMMON_DECISION, file: file, line: line)
    }

    private func loadCapabilityLookupVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/device-capabilities")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent != "capability-lookup-readiness.json" && $0.lastPathComponent != "capability-resource-override-ownership.json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
    }

    private func loadCapabilityLookupReadinessManifest() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/device-capabilities/capability-lookup-readiness.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }

    private func loadCapabilityResourceOverrideVector() throws -> [String: Any] {
        let vectorFile = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/device-capabilities/capability-resource-override-ownership.json")
        let data = try Data(contentsOf: vectorFile)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], vectorFile.path)
    }

    private func resetCapabilities(from config: [String: Any], id: String) throws {
        let defaults = try XCTUnwrap(config["defaults"] as? [String: Any], id)
        let devices = try XCTUnwrap(config["devices"] as? [String: Any], id)
        var deviceFileSystemTypes: [String: BlePolarDeviceCapabilitiesUtility.FileSystemType] = [:]
        var deviceRecordingSupported: [String: Bool] = [:]
        var deviceFirmwareUpdateSupported: [String: Bool] = [:]
        var deviceActivityDataSupported: [String: Bool] = [:]
        var deviceIsSensor: [String: Bool] = [:]
        for (deviceType, value) in devices {
            let device = try XCTUnwrap(value as? [String: Any], "\(id) \(deviceType)")
            deviceFileSystemTypes[deviceType] = fileSystemType(from: device["fileSystemType"] as? String)
            deviceRecordingSupported[deviceType.lowercased()] = device["recordingSupported"] as? Bool
            deviceFirmwareUpdateSupported[deviceType.lowercased()] = device["firmwareUpdateSupported"] as? Bool
            deviceActivityDataSupported[deviceType.lowercased()] = device["activityDataSupported"] as? Bool
            deviceIsSensor[deviceType.lowercased()] = device["isDeviceSensor"] as? Bool
        }
        BlePolarDeviceCapabilitiesUtility.resetAndInitializeForTesting(
            deviceFileSystemTypes: deviceFileSystemTypes,
            deviceRecordingSupported: deviceRecordingSupported,
            deviceFirmwareUpdateSupported: deviceFirmwareUpdateSupported,
            deviceActivityDataSupported: deviceActivityDataSupported,
            deviceIsSensor: deviceIsSensor,
            defaultFileSystemType: fileSystemType(from: defaults["fileSystemType"] as? String),
            defaultRecordingSupported: (defaults["recordingSupported"] as? Bool) ?? false,
            defaultFirmwareUpdateSupported: (defaults["firmwareUpdateSupported"] as? Bool) ?? false,
            defaultActivityDataSupported: (defaults["activityDataSupported"] as? Bool) ?? false,
            defaultIsSensor: (defaults["isDeviceSensor"] as? Bool) ?? false
        )
    }

    private func resetCapabilitiesByMerging(bundledConfig: [String: Any], userConfig: [String: Any], id: String) throws {
        let bundledData = try JSONSerialization.data(withJSONObject: bundledConfig)
        let userData = try JSONSerialization.data(withJSONObject: userConfig)
        XCTAssertTrue(
            BlePolarDeviceCapabilitiesUtility.resetAndInitializeForTesting(
                bundledConfigData: bundledData,
                userConfigData: userData
            ),
            id
        )
    }

    private func fileSystemType(from value: String?) -> BlePolarDeviceCapabilitiesUtility.FileSystemType {
        switch value {
        case "H10_FILE_SYSTEM": return .h10FileSystem
        case "POLAR_FILE_SYSTEM_V2": return .polarFileSystemV2
        default: return .unknownFileSystem
        }
    }

    private func fileSystemTypeName(_ value: BlePolarDeviceCapabilitiesUtility.FileSystemType) -> String {
        switch value {
        case .h10FileSystem: return "H10_FILE_SYSTEM"
        case .polarFileSystemV2: return "POLAR_FILE_SYSTEM_V2"
        case .unknownFileSystem: return "UNKNOWN_FILE_SYSTEM"
        }
    }

    func testCapabilityGoldenVectorsMatchIOSBehavior() throws {
        for vector in try loadCapabilityLookupVectors() {
            let id = try XCTUnwrap(vector["id"] as? String)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expectedResults = try XCTUnwrap((try XCTUnwrap(vector["expected"] as? [String: Any], id))["results"] as? [[String: Any]], id)

            switch input["kind"] as? String {
            case "deviceCapabilityLookup":
                let config = try XCTUnwrap(input["config"] as? [String: Any], id)
                try resetCapabilities(from: config, id: id)
            case "deviceCapabilityConfigMerge":
                let bundledConfig = try XCTUnwrap(input["bundledConfig"] as? [String: Any], id)
                let userConfig = try XCTUnwrap(input["userConfig"] as? [String: Any], id)
                try resetCapabilitiesByMerging(bundledConfig: bundledConfig, userConfig: userConfig, id: id)
                let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
                XCTAssertEqual("user-device-fields-win-missing-user-fields-fall-back-to-bundled-user-only-devices-survive-bundled-defaults-win", expected["mergePolicy"] as? String, id)
            default:
                XCTFail("Unexpected capability vector kind \(String(describing: input["kind"]))")
            }

            for expected in expectedResults {
                let deviceType = try XCTUnwrap(expected["deviceType"] as? String, id)
                XCTAssertEqual(expected["fileSystemType"] as? String, fileSystemTypeName(BlePolarDeviceCapabilitiesUtility.fileSystemType(deviceType)), "\(id) filesystem \(deviceType)")
                XCTAssertEqual(expected["recordingSupported"] as? Bool, BlePolarDeviceCapabilitiesUtility.isRecordingSupported(deviceType), "\(id) recording \(deviceType)")
                if let firmwareUpdateSupported = expected["firmwareUpdateSupported"] as? Bool {
                    XCTAssertEqual(firmwareUpdateSupported, BlePolarDeviceCapabilitiesUtility.isFirmwareUpdateSupported(deviceType), "\(id) firmware \(deviceType)")
                }
                if let activityDataSupported = expected["activityDataSupported"] as? Bool {
                    XCTAssertEqual(activityDataSupported, BlePolarDeviceCapabilitiesUtility.isActivityDataSupported(deviceType), "\(id) activity \(deviceType)")
                }
                if let isDeviceSensor = expected["isDeviceSensor"] as? Bool {
                    XCTAssertEqual(isDeviceSensor, BlePolarDeviceCapabilitiesUtility.isDeviceSensor(deviceType), "\(id) sensor \(deviceType)")
                }
            }
        }
    }

    func testCapabilityLookupReadinessManifestIsPinnedBeforeCapabilityMigration() throws {
        let manifest = try loadCapabilityLookupReadinessManifest()
        let input = try XCTUnwrap(manifest["input"] as? [String: Any])
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any])
        let families = [
            "filesystem-type-mapping",
            "unknown-filesystem-default",
            "missing-device-defaults",
            "case-insensitive-device-type",
            "recording-support-defaults",
            "firmware-update-defaults",
            "activity-data-defaults",
            "sensor-device-defaults",
            "version-mismatch-user-config-merge",
            "resource-override-platform-ownership",
            "platform-capability-vector-references",
            "compile-verification-gate"
        ]

        XCTAssertEqual("capability-lookup-readiness", manifest["id"] as? String)
        XCTAssertEqual("deviceCapabilityLookupReadiness", input["kind"] as? String)
        XCTAssertEqual(["sdk/device-capabilities/capability-boolean-flags.json", "sdk/device-capabilities/capability-config-merge.json", "sdk/device-capabilities/capability-lookup-basic.json", "sdk/device-capabilities/capability-lookup-default-h10.json", "sdk/device-capabilities/capability-resource-override-ownership.json"], input["policyVectorPaths"] as? [String])
        XCTAssertEqual("coveredByPreMigrationCharacterization", expected["migrationReadiness"] as? String)
        XCTAssertEqual(families, input["requiredBehaviorFamilies"] as? [String])
        XCTAssertEqual(families, expected["coveredBehaviorFamilies"] as? [String])
        XCTAssertEqual(["com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtilityTest"], consumerTests["android"] as? [String])
        XCTAssertEqual(["PolarBleApiImplTests"], consumerTests["ios"] as? [String])
        XCTAssertEqual(["com.polar.sharedtest.DeviceCapabilitiesCommonPolicyTest"], consumerTests["commonPrototype"] as? [String])
        let resourceOwnership = try loadCapabilityResourceOverrideVector()
        let resourceInput = try XCTUnwrap(resourceOwnership["input"] as? [String: Any])
        let resourceExpected = try XCTUnwrap(resourceOwnership["expected"] as? [String: Any])
        XCTAssertEqual("deviceCapabilityResourceOverrideOwnership", resourceInput["kind"] as? String)
        XCTAssertEqual("platformOwnedResourceBoundary", resourceExpected["migrationReadiness"] as? String)
        XCTAssertEqual("Shared KMP capability code may own parsing, lookup, defaults, boolean fields, and version-mismatch config merge, but must receive an already selected config and must not choose Android AssetManager, external Documents/PolarConfig files, iOS Bundle.main, SDK bundles, or sandbox files.", resourceExpected["commonDecision"] as? String)
    }

    func testCommandRuntimeReadinessManifestIsPinnedBeforeRuntimeMigration() throws {
        try assertSinglePolicyReadinessManifest(
            path: "sdk/command-runtime/reset-sync-h10-command-readiness.json",
            id: "reset-sync-h10-command-readiness",
            kind: "resetSyncH10CommandReadiness",
            policyPath: "sdk/command-runtime/reset-sync-h10-command-policy.json",
            families: [
                "h10-recording-start-query",
                "h10-recording-start-query-failure",
                "h10-recording-stop-query",
                "h10-recording-stop-query-failure",
                "h10-recording-status-query",
                "h10-recording-status-query-failure",
                "factory-reset-flags",
                "factory-reset-notification-failure",
                "preserve-pairing-reset-flags",
                "preserve-pairing-reset-notification-failure",
                "restart-reset-flags",
                "restart-reset-notification-failure",
                "warehouse-sleep-reset-flags",
                "warehouse-sleep-reset-notification-failure",
                "turn-device-off-reset-flags",
                "turn-device-off-reset-notification-failure",
                "sync-start-notification-sequence",
                "sync-start-query-failure-platform-split",
                "sync-stop-complete-terminate-sequence",
                "sync-stop-notification-failure-platform-split",
                "facade-error-mapping-gate",
                "platform-facade-vector-reference-gate",
                "compile-verification-gate"
            ],
            commonDecision: COMMAND_RUNTIME_READINESS_COMMON_DECISION,
            androidConsumers: ["com.polar.sdk.impl.BDBleApiImplTest"],
            iosConsumers: ["PolarBleApiImplTests"],
            commonPrototypeConsumers: ["com.polar.sharedtest.CommandRuntimePolicyCommonTest"]
        )
    }

    private func assertStoredDataCleanupWorkflowVectorContains(_ vectorTerm: String, file: StaticString = #filePath, line: UInt = #line) throws {
        let vectorURL = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/stored-data-cleanup/cleanup-workflow-policy.json")
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: vectorURL)) as? [String: Any], file: file, line: line)
        let input = try XCTUnwrap(vector["input"] as? [String: Any], file: file, line: line)
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], file: file, line: line)
        let scenarios = try XCTUnwrap(input["scenarios"] as? [[String: Any]], file: file, line: line)
        let commonRuntimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], file: file, line: line)
        let commonRuntimeCases = try XCTUnwrap(commonRuntimePrototype["cases"] as? [[String: Any]], file: file, line: line)
        let scenarioIds = try scenarios.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        let commonRuntimeCaseIds = try commonRuntimeCases.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        XCTAssertEqual(vector["id"] as? String, "stored-data-cleanup-workflow-policy", file: file, line: line)
        XCTAssertEqual(vector["case"] as? String, "cleanup_workflow_policy", file: file, line: line)
        XCTAssertEqual(STORED_DATA_CLEANUP_POLICY_SCENARIO_IDS, scenarioIds, file: file, line: line)
        XCTAssertEqual(STORED_DATA_CLEANUP_POLICY_SCENARIO_IDS, commonRuntimeCaseIds, file: file, line: line)
        XCTAssertEqual(vectorTerm, scenarioIds.first { $0 == vectorTerm }, file: file, line: line)
        let execution = try XCTUnwrap(vector["execution"] as? [String: Any], file: file, line: line)
        XCTAssertEqual(execution["kind"] as? String, "fake-cleanup-runtime-policy", file: file, line: line)
        XCTAssertEqual(execution["transport"] as? String, "directory-list-and-remove-command-capture", file: file, line: line)
        XCTAssertEqual(vector["commonDecision"] as? String, STORED_DATA_CLEANUP_POLICY_COMMON_DECISION, file: file, line: line)
    }

    func testStoredDataCleanupReadinessManifestIsPinnedBeforeCleanupMigration() throws {
        try assertSinglePolicyReadinessManifest(
            path: "sdk/stored-data-cleanup/cleanup-workflow-readiness.json",
            id: "stored-data-cleanup-workflow-readiness",
            kind: "storedDataCleanupWorkflowReadiness",
            policyPath: "sdk/stored-data-cleanup/cleanup-workflow-policy.json",
            families: [
                "telemetry-trc-filter",
                "sdlogs-extension-filter",
                "activity-prune-empty-parents",
                "automatic-sample-embedded-day-filter",
                "list-failure-platform-split",
                "empty-parent-path-platform-split",
                "facade-error-mapping-gate",
                "platform-facade-vector-reference-gate",
                "compile-verification-gate"
            ],
            commonDecision: STORED_DATA_CLEANUP_READINESS_COMMON_DECISION,
            androidConsumers: ["com.polar.sdk.impl.BDBleApiImplTest"],
            iosConsumers: ["PolarBleApiImplTests"],
            commonPrototypeConsumers: ["com.polar.sharedtest.StoredDataCleanupRuntimePolicyCommonTest"]
        )
    }

    private func assertDiskTimeRuntimePolicyVectorContains(_ vectorTerm: String, file: StaticString = #filePath, line: UInt = #line) throws {
        let vectorURL = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/disk-time-runtime/disk-time-query-policy.json")
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: vectorURL)) as? [String: Any], file: file, line: line)
        let input = try XCTUnwrap(vector["input"] as? [String: Any], file: file, line: line)
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], file: file, line: line)
        let operations = try XCTUnwrap(input["operations"] as? [[String: Any]], file: file, line: line)
        let commonRuntimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], file: file, line: line)
        let commonRuntimeCases = try XCTUnwrap(commonRuntimePrototype["cases"] as? [[String: Any]], file: file, line: line)
        let operationIds = try operations.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        let commonRuntimeCaseIds = try commonRuntimeCases.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        XCTAssertEqual(vector["id"] as? String, "disk-time-query-policy", file: file, line: line)
        XCTAssertEqual(vector["case"] as? String, "disk_time_query_policy", file: file, line: line)
        XCTAssertEqual(DISK_TIME_RUNTIME_POLICY_OPERATION_IDS, operationIds, file: file, line: line)
        XCTAssertEqual(DISK_TIME_RUNTIME_POLICY_OPERATION_IDS, commonRuntimeCaseIds, file: file, line: line)
        XCTAssertEqual(vectorTerm, operationIds.first { $0 == vectorTerm }, file: file, line: line)
        let execution = try XCTUnwrap(vector["execution"] as? [String: Any], file: file, line: line)
        XCTAssertEqual(execution["kind"] as? String, "fake-disk-time-query-runtime-policy", file: file, line: line)
        XCTAssertEqual(execution["transport"] as? String, "public-facade-query-capture", file: file, line: line)
        XCTAssertEqual(vector["commonDecision"] as? String, DISK_TIME_RUNTIME_POLICY_COMMON_DECISION, file: file, line: line)
    }

    func testDiskTimeReadinessManifestIsPinnedBeforeRuntimeMigration() throws {
        try assertSinglePolicyReadinessManifest(
            path: "sdk/disk-time-runtime/disk-time-query-readiness.json",
            id: "disk-time-query-readiness",
            kind: "diskTimeQueryReadiness",
            policyPath: "sdk/disk-time-runtime/disk-time-query-policy.json",
            families: [
                "disk-space-query",
                "local-time-query",
                "local-time-with-zone-query",
                "v2-system-and-local-time-sequence",
                "h10-single-local-time-query",
                "set-local-time-transport-error",
                "local-time-transport-error",
                "local-time-with-zone-transport-error",
                "disk-space-transport-error",
                "filesystem-capability-gate",
                "facade-error-mapping-gate",
                "platform-facade-vector-reference-gate",
                "compile-verification-gate"
            ],
            commonDecision: DISK_TIME_RUNTIME_READINESS_COMMON_DECISION,
            androidConsumers: ["com.polar.sdk.impl.BDBleApiImplTest"],
            iosConsumers: ["PolarBleApiImplTests"],
            commonPrototypeConsumers: ["com.polar.sharedtest.DiskTimeRuntimePolicyCommonTest"]
        )
    }

    private func assertUserDeviceSettingsRuntimePolicyVectorContains(_ vectorTerm: String, file: StaticString = #filePath, line: UInt = #line) throws {
        let vectorURL = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/user-device-settings-runtime/settings-runtime-policy.json")
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: vectorURL)) as? [String: Any], file: file, line: line)
        let input = try XCTUnwrap(vector["input"] as? [String: Any], file: file, line: line)
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], file: file, line: line)
        let operations = try XCTUnwrap(input["operations"] as? [[String: Any]], file: file, line: line)
        let commonRuntimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], file: file, line: line)
        let commonRuntimeCases = try XCTUnwrap(commonRuntimePrototype["cases"] as? [[String: Any]], file: file, line: line)
        let operationIds = try operations.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        let commonRuntimeCaseIds = try commonRuntimeCases.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        let commonRuntimeCasesById = Dictionary(uniqueKeysWithValues: commonRuntimeCases.compactMap { testCase -> (String, [String: Any])? in
            guard let id = testCase["id"] as? String else { return nil }
            return (id, testCase)
        })
        XCTAssertEqual(vector["id"] as? String, "user-device-settings-runtime-policy", file: file, line: line)
        XCTAssertEqual(vector["case"] as? String, "user_device_settings_runtime_policy", file: file, line: line)
        XCTAssertEqual(input["settingsPath"] as? String, "/U/0/S/UDEVSET.BPB", file: file, line: line)
        XCTAssertEqual(USER_DEVICE_SETTINGS_RUNTIME_POLICY_OPERATION_IDS, operationIds, file: file, line: line)
        XCTAssertEqual(USER_DEVICE_SETTINGS_RUNTIME_POLICY_OPERATION_IDS, commonRuntimeCaseIds, file: file, line: line)
        XCTAssertEqual(vectorTerm, operationIds.first { $0 == vectorTerm }, file: file, line: line)
        let selectedCase = try XCTUnwrap(commonRuntimeCasesById[vectorTerm], file: file, line: line)
        XCTAssertFalse(try XCTUnwrap(selectedCase["commands"] as? [String], file: file, line: line).isEmpty, file: file, line: line)
        XCTAssertNotNil(selectedCase["terminal"] as? String, file: file, line: line)
        let execution = try XCTUnwrap(vector["execution"] as? [String: Any], file: file, line: line)
        XCTAssertEqual(execution["kind"] as? String, "fake-user-device-settings-runtime-policy", file: file, line: line)
        XCTAssertEqual(execution["transport"] as? String, "public-facade-psftp-read-write-capture", file: file, line: line)
        XCTAssertEqual(vector["commonDecision"] as? String, USER_DEVICE_SETTINGS_RUNTIME_POLICY_COMMON_DECISION, file: file, line: line)
    }

    func testUserDeviceSettingsReadinessManifestIsPinnedBeforeRuntimeMigration() throws {
        try assertSinglePolicyReadinessManifest(
            path: "sdk/user-device-settings-runtime/settings-runtime-readiness.json",
            id: "user-device-settings-runtime-readiness",
            kind: "userDeviceSettingsRuntimeReadiness",
            policyPath: "sdk/user-device-settings-runtime/settings-runtime-policy.json",
            families: [
                "settings-path-gate",
                "settings-read-success",
                "settings-read-failure-no-write",
                "telemetry-read-then-write",
                "telemetry-write-failure-after-payload",
                "device-location-read-then-write",
                "device-location-write-failure-after-payload",
                "usb-connection-mode-read-then-write",
                "usb-connection-mode-write-failure-after-payload",
                "automatic-training-detection-read-then-write",
                "automatic-training-detection-write-failure-after-payload",
                "automatic-ohr-measurement-read-then-write",
                "automatic-ohr-measurement-write-failure-after-payload",
                "daylight-saving-payload-shape",
                "protobuf-field-preservation-gate",
                "facade-error-mapping-gate",
                "platform-facade-vector-reference-gate",
                "compile-verification-gate"
            ],
            commonDecision: USER_DEVICE_SETTINGS_RUNTIME_READINESS_COMMON_DECISION,
            androidConsumers: ["com.polar.sdk.impl.BDBleApiImplTest"],
            iosConsumers: ["PolarBleApiImplTests"],
            commonPrototypeConsumers: ["com.polar.sharedtest.UserDeviceSettingsRuntimePolicyCommonTest"]
        )
    }

    private func assertRestFacadeRuntimePolicyVectorContains(_ vectorTerm: String, file: StaticString = #filePath, line: UInt = #line) throws {
        let vectorURL = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/rest-service/rest-facade-runtime-policy.json")
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: vectorURL)) as? [String: Any], file: file, line: line)
        let input = try XCTUnwrap(vector["input"] as? [String: Any], file: file, line: line)
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], file: file, line: line)
        let operations = try XCTUnwrap(input["operations"] as? [[String: Any]], file: file, line: line)
        let commonRuntimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], file: file, line: line)
        let commonRuntimeCases = try XCTUnwrap(commonRuntimePrototype["cases"] as? [[String: Any]], file: file, line: line)
        let operationIds = try operations.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        let commonRuntimeCaseIds = try commonRuntimeCases.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        let commonRuntimeCasesById = Dictionary(uniqueKeysWithValues: commonRuntimeCases.compactMap { testCase -> (String, [String: Any])? in
            guard let id = testCase["id"] as? String else { return nil }
            return (id, testCase)
        })
        XCTAssertEqual(vector["id"] as? String, "rest-facade-runtime-policy", file: file, line: line)
        XCTAssertEqual(vector["case"] as? String, "rest_facade_runtime_policy", file: file, line: line)
        XCTAssertEqual(input["kind"] as? String, "restFacadeRuntimePolicy", file: file, line: line)
        XCTAssertEqual(REST_FACADE_RUNTIME_POLICY_OPERATION_IDS, operationIds, file: file, line: line)
        XCTAssertEqual(REST_FACADE_RUNTIME_POLICY_OPERATION_IDS, commonRuntimeCaseIds, file: file, line: line)
        XCTAssertEqual(vectorTerm, operationIds.first { $0 == vectorTerm }, file: file, line: line)
        let selectedCase = try XCTUnwrap(commonRuntimeCasesById[vectorTerm], file: file, line: line)
        XCTAssertFalse(try XCTUnwrap(selectedCase["commands"] as? [String], file: file, line: line).isEmpty, file: file, line: line)
        XCTAssertNotNil(selectedCase["terminal"] as? String, file: file, line: line)
        let operationsById = Dictionary(uniqueKeysWithValues: operations.compactMap { operation -> (String, [String: Any])? in
            guard let id = operation["id"] as? String else { return nil }
            return (id, operation)
        })
        XCTAssertEqual(operationsById["list-rest-api-services-success"]?["command"] as? String, "GET", file: file, line: line)
        XCTAssertEqual(operationsById["list-rest-api-services-success"]?["path"] as? String, "/REST/SERVICE.API", file: file, line: line)
        XCTAssertEqual(operationsById["list-rest-api-services-success"]?["payloadShape"] as? String, "service-list-json", file: file, line: line)
        XCTAssertEqual(operationsById["get-rest-api-description-success"]?["path"] as? String, "/REST/SLEEP.API", file: file, line: line)
        XCTAssertEqual(operationsById["get-rest-api-description-success"]?["payloadShape"] as? String, "service-description-json", file: file, line: line)
        let responseErrorTransport = try XCTUnwrap(operationsById["list-rest-api-services-response-error"]?["transport"] as? [String: Any], file: file, line: line)
        XCTAssertEqual(responseErrorTransport["mode"] as? String, "responseError", file: file, line: line)
        XCTAssertEqual(responseErrorTransport["status"] as? Int, 103, file: file, line: line)
        XCTAssertEqual(responseErrorTransport["message"] as? String, "NO_SUCH_FILE_OR_DIRECTORY", file: file, line: line)
        let responseErrorTerminals = try XCTUnwrap(operationsById["list-rest-api-services-response-error"]?["expectedPlatformTerminal"] as? [String: Any], file: file, line: line)
        XCTAssertEqual(responseErrorTerminals["android"] as? String, "pftp-response-error-name", file: file, line: line)
        XCTAssertEqual(responseErrorTerminals["ios"] as? String, "pftp-response-error-code", file: file, line: line)
        XCTAssertEqual((operationsById["list-rest-api-services-empty-success"]?["transport"] as? [String: Any])?["mode"] as? String, "successEmpty", file: file, line: line)
        XCTAssertEqual((operationsById["get-rest-api-description-malformed-success"]?["transport"] as? [String: Any])?["mode"] as? String, "successMalformedJson", file: file, line: line)
        let execution = try XCTUnwrap(vector["execution"] as? [String: Any], file: file, line: line)
        XCTAssertEqual(execution["kind"] as? String, "fake-rest-facade-runtime-policy", file: file, line: line)
        XCTAssertEqual(execution["transport"] as? String, "public-facade-psftp-request-capture", file: file, line: line)
        XCTAssertEqual(vector["commonDecision"] as? String, REST_FACADE_RUNTIME_POLICY_COMMON_DECISION, file: file, line: line)
    }

    func testRestFacadeReadinessManifestIsPinnedBeforeRuntimeMigration() throws {
        try assertSinglePolicyReadinessManifest(
            path: "sdk/rest-service/rest-facade-runtime-readiness.json",
            id: "rest-facade-runtime-readiness",
            kind: "restFacadeRuntimeReadiness",
            policyPath: "sdk/rest-service/rest-facade-runtime-policy.json",
            families: [
                "service-list-request-path",
                "service-list-json-success",
                "service-list-path-field-mapping",
                "service-description-request-path",
                "service-description-json-success",
                "service-description-action-field-mapping",
                "service-description-event-detail-trigger-mapping",
                "service-list-request-failure",
                "service-description-request-failure",
                "service-list-response-error-platform-mapping",
                "service-description-response-error-platform-mapping",
                "service-list-empty-success-parse-failure",
                "service-description-empty-success-parse-failure",
                "service-list-malformed-success-parse-failure",
                "service-description-malformed-success-parse-failure",
                "model-json-mapping-vector-reference-gate",
                "empty-response-transport-policy-gate",
                "response-error-transport-policy-gate",
                "facade-error-mapping-gate",
                "platform-facade-vector-reference-gate",
                "compile-verification-gate"
            ],
            commonDecision: REST_FACADE_RUNTIME_READINESS_COMMON_DECISION,
            androidConsumers: ["com.polar.sdk.impl.BDBleApiImplTest"],
            iosConsumers: ["PolarBleApiImplTests"],
            commonPrototypeConsumers: ["com.polar.sharedtest.RestFacadeRuntimePolicyCommonTest"]
        )
    }

    private func assertFileFacadeRuntimePolicyVectorContains(_ vectorTerm: String, file: StaticString = #filePath, line: UInt = #line) throws {
        let vectorURL = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/file-utils/file-facade-runtime-policy.json")
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: vectorURL)) as? [String: Any], file: file, line: line)
        let input = try XCTUnwrap(vector["input"] as? [String: Any], file: file, line: line)
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], file: file, line: line)
        let operations = try XCTUnwrap(input["operations"] as? [[String: Any]], file: file, line: line)
        let commonRuntimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], file: file, line: line)
        let commonRuntimeCases = try XCTUnwrap(commonRuntimePrototype["cases"] as? [[String: Any]], file: file, line: line)
        let operationIds = try operations.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        let commonRuntimeCaseIds = try commonRuntimeCases.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        let commonRuntimeCasesById = Dictionary(uniqueKeysWithValues: commonRuntimeCases.compactMap { testCase -> (String, [String: Any])? in
            guard let id = testCase["id"] as? String else { return nil }
            return (id, testCase)
        })
        XCTAssertEqual(vector["id"] as? String, "file-facade-runtime-policy", file: file, line: line)
        XCTAssertEqual(vector["case"] as? String, "file_facade_runtime_policy", file: file, line: line)
        XCTAssertEqual(input["kind"] as? String, "fileFacadeRuntimePolicy", file: file, line: line)
        XCTAssertEqual(FILE_FACADE_RUNTIME_POLICY_OPERATION_IDS, operationIds, file: file, line: line)
        XCTAssertEqual(FILE_FACADE_RUNTIME_POLICY_OPERATION_IDS, commonRuntimeCaseIds, file: file, line: line)
        XCTAssertEqual(vectorTerm, operationIds.first { $0 == vectorTerm }, file: file, line: line)
        let selectedCase = try XCTUnwrap(commonRuntimeCasesById[vectorTerm], file: file, line: line)
        XCTAssertFalse(try XCTUnwrap(selectedCase["commands"] as? [String], file: file, line: line).isEmpty, file: file, line: line)
        XCTAssertNotNil(selectedCase["terminal"] as? String, file: file, line: line)
        let operationsById = Dictionary(uniqueKeysWithValues: operations.compactMap { operation -> (String, [String: Any])? in
            guard let id = operation["id"] as? String else { return nil }
            return (id, operation)
        })
        XCTAssertEqual(operationsById["read-low-level-file-success"]?["command"] as? String, "GET", file: file, line: line)
        XCTAssertEqual(operationsById["read-low-level-file-success"]?["path"] as? String, "/U/0/CUSTOM.BIN", file: file, line: line)
        XCTAssertEqual(operationsById["read-low-level-file-success"]?["responseHex"] as? String, "010203", file: file, line: line)
        XCTAssertEqual(operationsById["read-low-level-file-empty-success"]?["path"] as? String, "/U/0/EMPTY.BIN", file: file, line: line)
        XCTAssertEqual(operationsById["read-low-level-file-empty-success"]?["responseHex"] as? String, "", file: file, line: line)
        XCTAssertEqual(operationsById["write-low-level-file-success"]?["command"] as? String, "PUT", file: file, line: line)
        XCTAssertEqual(operationsById["write-low-level-file-success"]?["payloadHex"] as? String, "0a0b", file: file, line: line)
        let progress = try XCTUnwrap(operationsById["write-low-level-file-progress-success"]?["progress"] as? [NSNumber], file: file, line: line)
        XCTAssertEqual(progress.map(\.intValue), [0, 2], file: file, line: line)
        XCTAssertEqual((operationsById["write-low-level-file-stream-failure"]?["transport"] as? [String: Any])?["mode"] as? String, "writeStreamError", file: file, line: line)
        let writeResponseErrorTransport = try XCTUnwrap(operationsById["write-low-level-file-response-error"]?["transport"] as? [String: Any], file: file, line: line)
        XCTAssertEqual(writeResponseErrorTransport["mode"] as? String, "pftpResponseError", file: file, line: line)
        XCTAssertEqual(writeResponseErrorTransport["status"] as? Int, 103, file: file, line: line)
        let writeResponseErrorTerminals = try XCTUnwrap(operationsById["write-low-level-file-response-error"]?["expectedPlatformTerminal"] as? [String: Any], file: file, line: line)
        XCTAssertEqual(writeResponseErrorTerminals["android"] as? String, "pftp-response-error-object", file: file, line: line)
        XCTAssertEqual(writeResponseErrorTerminals["ios"] as? String, "pftp-response-error-code", file: file, line: line)
        XCTAssertEqual(operationsById["delete-low-level-file-success"]?["command"] as? String, "REMOVE", file: file, line: line)
        XCTAssertEqual((operationsById["delete-low-level-file-request-failure"]?["transport"] as? [String: Any])?["mode"] as? String, "transportError", file: file, line: line)
        XCTAssertEqual((operationsById["delete-low-level-file-response-error"]?["transport"] as? [String: Any])?["mode"] as? String, "pftpResponseError", file: file, line: line)
        let execution = try XCTUnwrap(vector["execution"] as? [String: Any], file: file, line: line)
        XCTAssertEqual(execution["kind"] as? String, "fake-file-facade-runtime-policy", file: file, line: line)
        XCTAssertEqual(execution["transport"] as? String, "public-facade-psftp-command-capture", file: file, line: line)
        XCTAssertEqual(vector["commonDecision"] as? String, FILE_FACADE_RUNTIME_POLICY_COMMON_DECISION, file: file, line: line)
    }

    private func assertOfflineTriggerRuntimePolicyVectorContains(_ vectorTerm: String, file: StaticString = #filePath, line: UInt = #line) throws {
        let vectorURL = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/offline-recording/trigger-runtime-policy.json")
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: vectorURL)) as? [String: Any], file: file, line: line)
        let input = try XCTUnwrap(vector["input"] as? [String: Any], file: file, line: line)
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], file: file, line: line)
        let scenarios = try XCTUnwrap(input["scenarios"] as? [[String: Any]], file: file, line: line)
        let commonRuntimePrototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any], file: file, line: line)
        let commonRuntimeCases = try XCTUnwrap(commonRuntimePrototype["cases"] as? [[String: Any]], file: file, line: line)
        let scenarioIds = try scenarios.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        let commonRuntimeCaseIds = try commonRuntimeCases.map { try XCTUnwrap($0["id"] as? String, file: file, line: line) }
        let cleanupEvidence = try XCTUnwrap(expected["platformCleanupEvidence"] as? [String: Any], file: file, line: line)
        let cleanupEvidenceIds = [
            (cleanupEvidence["android"] as? [String: Any])?["id"] as? String,
            (cleanupEvidence["ios"] as? [String: Any])?["id"] as? String
        ].compactMap { $0 }
        XCTAssertEqual(vector["id"] as? String, "trigger-runtime-policy", file: file, line: line)
        XCTAssertEqual(vector["case"] as? String, "trigger_runtime_policy", file: file, line: line)
        XCTAssertEqual(input["kind"] as? String, "offlineTriggerRuntimePolicy", file: file, line: line)
        XCTAssertEqual(OFFLINE_TRIGGER_RUNTIME_POLICY_SCENARIO_IDS, scenarioIds, file: file, line: line)
        XCTAssertEqual(OFFLINE_TRIGGER_RUNTIME_POLICY_SCENARIO_IDS, commonRuntimeCaseIds, file: file, line: line)
        XCTAssertTrue((scenarioIds + cleanupEvidenceIds).contains(vectorTerm), file: file, line: line)
        let scenariosById = Dictionary(uniqueKeysWithValues: scenarios.compactMap { scenario -> (String, [String: Any])? in
            guard let id = scenario["id"] as? String else { return nil }
            return (id, scenario)
        })
        let desiredTrigger = try XCTUnwrap(input["desiredTrigger"] as? [String: Any], file: file, line: line)
        let secret = try XCTUnwrap(desiredTrigger["secret"] as? [String: Any], file: file, line: line)
        XCTAssertEqual(desiredTrigger["mode"] as? String, "TRIGGER_SYSTEM_START", file: file, line: line)
        XCTAssertEqual(secret["present"] as? Bool, true, file: file, line: line)
        XCTAssertEqual((scenariosById["set-trigger-mode-error"]?["transport"] as? [String: Any])?["setMode"] as? String, "controlPointError", file: file, line: line)
        XCTAssertEqual((scenariosById["set-trigger-status-read-error"]?["transport"] as? [String: Any])?["getStatus"] as? String, "transportError", file: file, line: line)
        XCTAssertEqual((scenariosById["set-trigger-setting-error"]?["transport"] as? [String: Any])?["setSettings"] as? String, "controlPointError", file: file, line: line)
        XCTAssertEqual((scenariosById["get-trigger-transport-error"]?["transport"] as? [String: Any])?["getStatus"] as? String, "transportError", file: file, line: line)
        XCTAssertEqual((cleanupEvidence["android"] as? [String: Any])?["id"] as? String, "android-stale-wrong-command-response-discard", file: file, line: line)
        XCTAssertEqual((cleanupEvidence["ios"] as? [String: Any])?["id"] as? String, "ios-pre-command-response-queue-clear", file: file, line: line)
        XCTAssertEqual(expected["commonDecision"] as? String, OFFLINE_TRIGGER_RUNTIME_POLICY_COMMON_DECISION, file: file, line: line)
        XCTAssertEqual((vector["execution"] as? [String: Any])?["status"] as? String, "shared-common-test", file: file, line: line)
    }

    func testOfflineTriggerRuntimeReadinessManifestIsPinnedBeforeRuntimeMigration() throws {
        try assertSinglePolicyReadinessManifest(
            path: "sdk/offline-recording/trigger-runtime-readiness.json",
            id: "trigger-runtime-readiness",
            kind: "offlineTriggerRuntimeReadiness",
            policyPath: "sdk/offline-recording/trigger-runtime-policy.json",
            families: [
                "typed-set-mode",
                "status-read",
                "settings-write",
                "optional-secret-attachment",
                "get-transport-error",
                "set-mode-control-point-error",
                "status-read-transport-error",
                "settings-control-point-error",
                "enabled-feature-projection",
                "excluded-feature-projection",
                "platform-packet-split",
                "facade-error-mapping-deferred",
                "compile-verification-gate"
            ]
        )
    }

    func testFileFacadeReadinessManifestIsPinnedBeforeRuntimeMigration() throws {
        try assertSinglePolicyReadinessManifest(
            path: "sdk/file-utils/file-facade-runtime-readiness.json",
            id: "file-facade-runtime-readiness",
            kind: "fileFacadeRuntimeReadiness",
            policyPath: "sdk/file-utils/file-facade-runtime-policy.json",
            families: [
                "low-level-file-path-gate",
                "read-file-get-success",
                "read-file-empty-success",
                "read-file-request-failure",
                "read-file-response-error",
                "write-file-put-success",
                "write-file-payload-capture",
                "write-file-progress-before-completion",
                "write-file-stream-failure-after-payload",
                "write-file-response-error-after-payload",
                "delete-file-remove-success",
                "delete-file-request-failure",
                "delete-file-response-error",
                "directory-list-shallow-vector-reference-gate",
                "directory-list-recursive-vector-reference-gate",
                "read-write-delete-model-vector-reference-gate",
                "runtime-error-policy-reference-gate",
                "malformed-directory-policy-gate",
                "response-error-policy-gate",
                "facade-error-mapping-gate",
                "platform-facade-vector-reference-gate",
                "compile-verification-gate"
            ],
            commonDecision: FILE_FACADE_RUNTIME_READINESS_COMMON_DECISION,
            androidConsumers: ["com.polar.sdk.impl.BDBleApiImplTest", "com.polar.sdk.api.model.utils.PolarFileUtilsTest"],
            iosConsumers: ["PolarBleApiImplTests", "PolarFileUtilsTest"],
            commonPrototypeConsumers: ["com.polar.sharedtest.FileFacadeRuntimePolicyCommonTest"]
        )
    }

    private func makePmdApi(responseForPacket: @escaping (Data) -> Data) -> (api: PolarBleApiImplWithMockSession, gatt: MockPolarGattServiceTransmitter) {
        let gatt = MockPolarGattServiceTransmitter()
        let pmdClient = BlePmdClient(gattServiceTransmitter: gatt)
        gatt.transmitMessageHandler = { _, serviceUuid, characteristicUuid, packet, _ in
            guard serviceUuid == BlePmdClient.PMD_SERVICE && characteristicUuid == BlePmdClient.PMD_CP else { return }
            pmdClient.processServiceData(BlePmdClient.PMD_CP, data: responseForPacket(packet), err: 0)
        }
        let session = MockBleDeviceSession(mockFtpClient: v2MockClient, mockPmdClient: pmdClient)
        return (PolarBleApiImplWithMockSession(mockDeviceSession: session), gatt)
    }

    private func pmdResponse(opCode: UInt8, measurementType: UInt8 = 0, errorCode: UInt8 = 0, parameters: Data = Data()) -> Data {
        if parameters.isEmpty {
            return Data([0xF0, opCode, measurementType, errorCode])
        }
        return Data([0xF0, opCode, measurementType, errorCode, 0x00]) + parameters
    }

    private func offlineTriggerStatusData() -> Data {
        return Data([
            0x01,
            0x01, PmdMeasurementType.acc.rawValue, 0x00,
            0x01, PmdMeasurementType.gyro.rawValue, 0x00,
            0x01, PmdMeasurementType.offline_hr.rawValue, 0x00
        ])
    }


    // MARK: - Combine-based helpers (for AnyPublisher-returning APIs)

    @discardableResult
    private func awaitSingle<T>(_ publisher: AnyPublisher<T, Error>, timeout: TimeInterval = 2) throws -> T {
        var result: T?; var receivedError: Error?
        let exp = XCTestExpectation(description: "awaitSingle")
        publisher.first()
            .sink(receiveCompletion: { if case .failure(let e) = $0 { receivedError = e }; exp.fulfill() },
                  receiveValue: { result = $0 })
            .store(in: &cancellables)
        wait(for: [exp], timeout: timeout)
        if let e = receivedError { throw e }
        return try XCTUnwrap(result)
    }

    private func awaitCompletion(_ publisher: AnyPublisher<Never, Error>, timeout: TimeInterval = 2) throws {
        var receivedError: Error?
        let exp = XCTestExpectation(description: "awaitCompletion")
        publisher.sink(receiveCompletion: { if case .failure(let e) = $0 { receivedError = e }; exp.fulfill() },
                       receiveValue: { _ in })
            .store(in: &cancellables)
        wait(for: [exp], timeout: timeout)
        if let e = receivedError { throw e }
    }

    private func awaitError<T>(_ publisher: AnyPublisher<T, Error>, timeout: TimeInterval = 2) -> Error? {
        var receivedError: Error?
        let exp = XCTestExpectation(description: "awaitError")
        publisher.sink(receiveCompletion: { if case .failure(let e) = $0 { receivedError = e }; exp.fulfill() },
                       receiveValue: { _ in })
            .store(in: &cancellables)
        wait(for: [exp], timeout: timeout)
        return receivedError
    }

    /// Collects all emitted values until the publisher completes or fails.
    private func collectAll<T>(_ publisher: AnyPublisher<T, Error>, timeout: TimeInterval = 5) throws -> [T] {
        var results: [T] = []; var receivedError: Error?
        let exp = XCTestExpectation(description: "collectAll")
        publisher.collect()
            .sink(receiveCompletion: { if case .failure(let e) = $0 { receivedError = e }; exp.fulfill() },
                  receiveValue: { results = $0 })
            .store(in: &cancellables)
        wait(for: [exp], timeout: timeout)
        if let e = receivedError { throw e }
        return results
    }

    // MARK: - Async/await helpers (for async throws APIs)

    @discardableResult
    private func awaitSingleAsync<T>(_ operation: @escaping () async throws -> T, timeout: TimeInterval = 2) throws -> T {
        var result: Result<T, Error>?
        let exp = XCTestExpectation(description: "awaitSingleAsync")
        Task {
            do { result = .success(try await operation()) }
            catch { result = .failure(error) }
            exp.fulfill()
        }
        wait(for: [exp], timeout: timeout)
        return try XCTUnwrap(result).get()
    }

    private func awaitVoidAsync(_ operation: @escaping () async throws -> Void, timeout: TimeInterval = 2) throws {
        try awaitSingleAsync(operation, timeout: timeout)
    }

    private func awaitErrorAsync<T>(_ operation: @escaping () async throws -> T, timeout: TimeInterval = 2) -> Error? {
        var receivedError: Error?
        let exp = XCTestExpectation(description: "awaitErrorAsync")
        Task {
            do { _ = try await operation() }
            catch { receivedError = error }
            exp.fulfill()
        }
        wait(for: [exp], timeout: timeout)
        return receivedError
    }

    /// Collects all values from an AsyncThrowingStream until it finishes or throws.
    private func collectAllAsync<T>(_ stream: AsyncThrowingStream<T, Error>, timeout: TimeInterval = 5) throws -> [T] {
        var results: [T] = []
        try awaitSingleAsync({
            var r: [T] = []
            for try await value in stream { r.append(value) }
            return r
        }, timeout: timeout).forEach { results.append($0) }
        return results
    }

    private func data(from stream: InputStream) throws -> Data {
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 1024)
        while stream.hasBytesAvailable {
            let count = stream.read(&buffer, maxLength: buffer.count)
            if count < 0 { throw stream.streamError ?? NSError(domain: "PolarBleApiImplTests", code: 1) }
            if count == 0 { break }
            data.append(buffer, count: count)
        }
        return data
    }

    // MARK: - getLocalTime

    func test_getLocalTime_polarFileSystemV2_success() throws {
        try assertDiskTimeRuntimePolicyVectorContains("get-local-time")
        var proto = Protocol_PbPFtpSetLocalTimeParams()
        proto.date.year = 2024; proto.date.month = 6; proto.date.day = 15
        proto.time.hour = 10; proto.time.minute = 30; proto.time.seconds = 0; proto.tzOffset = 120
        v2MockClient.queryReturnValue = .success(try proto.serializedData())
        let date = try awaitSingleAsync { [self] in try await v2Api.getLocalTime(deviceId) }
        XCTAssertEqual(v2MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.getLocalTime.rawValue)
        let c = Calendar(identifier: .gregorian).dateComponents([.year,.month,.day], from: date)
        XCTAssertEqual(c.year, 2024); XCTAssertEqual(c.month, 6); XCTAssertEqual(c.day, 15)
    }

    func test_getLocalTime_h10FileSystem_returnsOperationNotSupported() {
        let error = awaitErrorAsync { [self] in try await h10Api.getLocalTime(deviceId) }
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
    }

    func test_getLocalTime_queryError_propagatesError() throws {
        try assertDiskTimeRuntimePolicyVectorContains("get-local-time-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7004, userInfo: [NSLocalizedDescriptionKey: "local time query failed"])
        v2MockClient.queryReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await v2Api.getLocalTime(deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.queryCalls.count, 1)
        XCTAssertEqual(v2MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.getLocalTime.rawValue)
        XCTAssertNil(v2MockClient.queryCalls.first?.parameters)
    }

    // MARK: - getLocalTimeWithZone

    func test_getLocalTimeWithZone_polarFileSystemV2_success() throws {
        try assertDiskTimeRuntimePolicyVectorContains("get-local-time-with-zone")
        var proto = Protocol_PbPFtpSetLocalTimeParams()
        proto.date.year = 2025; proto.date.month = 1; proto.date.day = 1
        proto.time.hour = 12; proto.time.minute = 0; proto.time.seconds = 0; proto.tzOffset = 60
        v2MockClient.queryReturnValue = .success(try proto.serializedData())
        let (_, tz) = try awaitSingleAsync { [self] in try await v2Api.getLocalTimeWithZone(deviceId) }
        XCTAssertEqual(tz.secondsFromGMT(), 3600)
    }

    func test_getLocalTimeWithZone_h10_returnsOperationNotSupported() {
        let error = awaitErrorAsync { [self] in try await h10Api.getLocalTimeWithZone(deviceId) }
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
    }

    func test_getLocalTimeWithZone_queryError_propagatesError() throws {
        try assertDiskTimeRuntimePolicyVectorContains("get-local-time-with-zone-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7005, userInfo: [NSLocalizedDescriptionKey: "local time with zone query failed"])
        v2MockClient.queryReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await v2Api.getLocalTimeWithZone(deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.queryCalls.count, 1)
        XCTAssertEqual(v2MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.getLocalTime.rawValue)
        XCTAssertNil(v2MockClient.queryCalls.first?.parameters)
    }

    // MARK: - setLocalTime

    func test_setLocalTime_h10FileSystem_sendsOneQuery() throws {
        try assertDiskTimeRuntimePolicyVectorContains("set-local-time-h10")
        h10MockClient.queryReturnValue = .success(Data())
        try awaitVoidAsync { [self] in try await h10Api.setLocalTime(deviceId, time: Date(), zone: TimeZone(secondsFromGMT: 0)!) }
        XCTAssertEqual(h10MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.setLocalTime.rawValue)
    }

    func test_setLocalTime_h10FileSystem_queryError_propagatesError() throws {
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7006, userInfo: [NSLocalizedDescriptionKey: "set local time failed"])
        h10MockClient.queryReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await h10Api.setLocalTime(deviceId, time: Date(), zone: TimeZone(secondsFromGMT: 0)!) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(h10MockClient.queryCalls.count, 1)
        XCTAssertEqual(h10MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.setLocalTime.rawValue)
        XCTAssertNotNil(h10MockClient.queryCalls.first?.parameters)
    }

    func test_setLocalTime_polarFileSystemV2_sendsTwoQueries() throws {
        try assertDiskTimeRuntimePolicyVectorContains("set-local-time-v2")
        for _ in 0..<2 { v2MockClient.queryReturnValues.append(.success(Data())) }
        try awaitVoidAsync { [self] in try await v2Api.setLocalTime(deviceId, time: Date(), zone: TimeZone(secondsFromGMT: 3600)!) }
        XCTAssertEqual(v2MockClient.queryCalls.count, 2)
        let ids = v2MockClient.queryCalls.map { $0.id }
        XCTAssertTrue(ids.contains(Protocol_PbPFtpQuery.setLocalTime.rawValue))
        XCTAssertTrue(ids.contains(Protocol_PbPFtpQuery.setSystemTime.rawValue))
    }

    // MARK: - setTelemetryEnabled

    func test_setTelemetryEnabled_readsCurrentSettingsAndWritesTelemetryUpdate() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("set-telemetry-enabled")
        var currentSettings = Data_PbUserDeviceSettings()
        currentSettings.generalSettings.deviceLocation = .deviceLocationWristLeft
        currentSettings.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date(timeIntervalSince1970: 0))
        v2MockClient.requestReturnValue = .success(try currentSettings.serializedData())
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }

        try awaitVoidAsync { [self] in try await v2Api.setTelemetryEnabled(deviceId, enabled: true) }

        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, requestOperation.path)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, writeOperation.path)
        let writtenSettings = try Data_PbUserDeviceSettings(serializedBytes: try data(from: v2MockClient.writeCalls[0].data))
        XCTAssertEqual(.deviceLocationWristLeft, writtenSettings.generalSettings.deviceLocation)
        XCTAssertTrue(writtenSettings.hasTelemetrySettings)
        XCTAssertTrue(writtenSettings.telemetrySettings.hasTelemetryEnabled)
        XCTAssertTrue(writtenSettings.telemetrySettings.telemetryEnabled)
    }

    func test_setTelemetryEnabled_propagatesCurrentSettingsReadFailureWithoutWrite() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("set-telemetry-read-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7001, userInfo: [NSLocalizedDescriptionKey: "current settings read failed"])
        v2MockClient.requestReturnValue = .failure(transportError)
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }

        let error = awaitErrorAsync { [self] in try await v2Api.setTelemetryEnabled(deviceId, enabled: true) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        XCTAssertTrue(v2MockClient.writeCalls.isEmpty)
    }

    func test_setTelemetryEnabled_propagatesSettingsWriteFailureAfterPayloadIsPrepared() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("set-telemetry-write-failure")
        var currentSettings = Data_PbUserDeviceSettings()
        currentSettings.generalSettings.deviceLocation = .deviceLocationWristLeft
        currentSettings.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date(timeIntervalSince1970: 0))
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7002, userInfo: [NSLocalizedDescriptionKey: "settings write failed"])
        v2MockClient.requestReturnValue = .success(try currentSettings.serializedData())
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: transportError)
        }

        let error = awaitErrorAsync { [self] in try await v2Api.setTelemetryEnabled(deviceId, enabled: true) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, writeOperation.path)
        let writtenSettings = try Data_PbUserDeviceSettings(serializedBytes: try data(from: v2MockClient.writeCalls[0].data))
        XCTAssertTrue(writtenSettings.telemetrySettings.telemetryEnabled)
    }

    // MARK: - setUserDeviceLocation

    func test_setUserDeviceLocation_readsCurrentSettingsAndWritesLocationUpdate() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("set-user-device-location")
        var currentSettings = Data_PbUserDeviceSettings()
        currentSettings.generalSettings.deviceLocation = .deviceLocationWristLeft
        var telemetrySettings = Data_PbUserDeviceTelemetrySettings()
        telemetrySettings.telemetryEnabled = true
        currentSettings.telemetrySettings = telemetrySettings
        currentSettings.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date(timeIntervalSince1970: 0))
        v2MockClient.requestReturnValue = .success(try currentSettings.serializedData())
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }

        try awaitVoidAsync { [self] in try await v2Api.setUserDeviceLocation(deviceId, location: PbDeviceLocation.deviceLocationWristRight.rawValue) }

        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, requestOperation.path)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, writeOperation.path)
        let writtenSettings = try Data_PbUserDeviceSettings(serializedBytes: try data(from: v2MockClient.writeCalls[0].data))
        XCTAssertEqual(sharedDeviceLocation(PbDeviceLocation.deviceLocationWristRight.rawValue), writtenSettings.generalSettings.deviceLocation)
        XCTAssertTrue(writtenSettings.hasTelemetrySettings)
        XCTAssertTrue(writtenSettings.telemetrySettings.hasTelemetryEnabled)
        XCTAssertTrue(writtenSettings.telemetrySettings.telemetryEnabled)
    }

    func test_setUserDeviceLocation_propagatesWriteFailureAfterPayloadIsPrepared() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("set-user-device-location-write-failure")
        var currentSettings = Data_PbUserDeviceSettings()
        currentSettings.generalSettings.deviceLocation = .deviceLocationWristRight
        var telemetrySettings = Data_PbUserDeviceTelemetrySettings()
        telemetrySettings.telemetryEnabled = true
        currentSettings.telemetrySettings = telemetrySettings
        currentSettings.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date(timeIntervalSince1970: 0))
        v2MockClient.requestReturnValue = .success(try currentSettings.serializedData())
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7026, userInfo: [NSLocalizedDescriptionKey: "location settings write failed"])
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: transportError)
        }

        let error = awaitErrorAsync { [self] in try await v2Api.setUserDeviceLocation(deviceId, location: PbDeviceLocation.deviceLocationWristLeft.rawValue) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, writeOperation.path)
        let writtenSettings = try Data_PbUserDeviceSettings(serializedBytes: try data(from: v2MockClient.writeCalls[0].data))
        XCTAssertEqual(sharedDeviceLocation(PbDeviceLocation.deviceLocationWristLeft.rawValue), writtenSettings.generalSettings.deviceLocation)
        XCTAssertTrue(writtenSettings.telemetrySettings.telemetryEnabled)
    }

    // MARK: - setUsbConnectionMode

    func test_setUsbConnectionMode_readsCurrentSettingsAndWritesUsbModeUpdate() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("set-usb-connection-mode")
        var currentSettings = Data_PbUserDeviceSettings()
        currentSettings.generalSettings.deviceLocation = .deviceLocationWristLeft
        var telemetrySettings = Data_PbUserDeviceTelemetrySettings()
        telemetrySettings.telemetryEnabled = true
        currentSettings.telemetrySettings = telemetrySettings
        currentSettings.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date(timeIntervalSince1970: 0))
        v2MockClient.requestReturnValue = .success(try currentSettings.serializedData())
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }

        try awaitVoidAsync { [self] in try await v2Api.setUsbConnectionMode(deviceId, enabled: true) }

        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, requestOperation.path)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, writeOperation.path)
        let writtenSettings = try Data_PbUserDeviceSettings(serializedBytes: try data(from: v2MockClient.writeCalls[0].data))
        XCTAssertTrue(writtenSettings.hasUsbConnectionSettings)
        XCTAssertEqual(PolarUserDeviceSettings.UsbConnectionMode.ON.toProto(), writtenSettings.usbConnectionSettings.mode)
        XCTAssertEqual(.deviceLocationWristLeft, writtenSettings.generalSettings.deviceLocation)
        XCTAssertTrue(writtenSettings.telemetrySettings.telemetryEnabled)
    }

    func test_setUsbConnectionMode_propagatesWriteFailureAfterPayloadIsPrepared() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("set-usb-connection-mode-write-failure")
        var currentSettings = Data_PbUserDeviceSettings()
        currentSettings.generalSettings.deviceLocation = .deviceLocationWristLeft
        currentSettings.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date(timeIntervalSince1970: 0))
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7015, userInfo: [NSLocalizedDescriptionKey: "USB settings write failed"])
        v2MockClient.requestReturnValue = .success(try currentSettings.serializedData())
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: transportError)
        }

        let error = awaitErrorAsync { [self] in try await v2Api.setUsbConnectionMode(deviceId, enabled: false) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, writeOperation.path)
        let writtenSettings = try Data_PbUserDeviceSettings(serializedBytes: try data(from: v2MockClient.writeCalls[0].data))
        XCTAssertEqual(PolarUserDeviceSettings.UsbConnectionMode.OFF.toProto(), writtenSettings.usbConnectionSettings.mode)
    }

    // MARK: - setAutomaticTrainingDetectionSettings

    func test_setAutomaticTrainingDetectionSettings_readsCurrentSettingsAndWritesAutomaticMeasurementUpdate() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("set-automatic-training-detection")
        var currentSettings = Data_PbUserDeviceSettings()
        currentSettings.generalSettings.deviceLocation = .deviceLocationWristLeft
        var telemetrySettings = Data_PbUserDeviceTelemetrySettings()
        telemetrySettings.telemetryEnabled = true
        currentSettings.telemetrySettings = telemetrySettings
        currentSettings.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date(timeIntervalSince1970: 0))
        v2MockClient.requestReturnValue = .success(try currentSettings.serializedData())
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }

        try awaitVoidAsync { [self] in try await v2Api.setAutomaticTrainingDetectionSettings(deviceId, mode: true, sensitivity: 77, minimumDuration: 300) }

        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, requestOperation.path)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, writeOperation.path)
        let writtenSettings = try Data_PbUserDeviceSettings(serializedBytes: try data(from: v2MockClient.writeCalls[0].data))
        XCTAssertTrue(writtenSettings.hasAutomaticMeasurementSettings)
        let atdSettings = writtenSettings.automaticMeasurementSettings.automaticTrainingDetectionSettings
        XCTAssertEqual(PolarUserDeviceSettings.AutomaticTrainingDetectionMode.ON.toProto(), atdSettings.state)
        XCTAssertEqual(77, atdSettings.sensitivity)
        XCTAssertEqual(300, atdSettings.minimumTrainingDurationSeconds)
        XCTAssertTrue(writtenSettings.telemetrySettings.telemetryEnabled)
    }

    func test_setAutomaticTrainingDetectionSettings_propagatesWriteFailureAfterPayloadIsPrepared() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("set-automatic-training-detection-write-failure")
        var currentSettings = Data_PbUserDeviceSettings()
        currentSettings.generalSettings.deviceLocation = .deviceLocationWristLeft
        currentSettings.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date(timeIntervalSince1970: 0))
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7016, userInfo: [NSLocalizedDescriptionKey: "automatic training detection write failed"])
        v2MockClient.requestReturnValue = .success(try currentSettings.serializedData())
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: transportError)
        }

        let error = awaitErrorAsync { [self] in try await v2Api.setAutomaticTrainingDetectionSettings(deviceId, mode: false, sensitivity: 11, minimumDuration: 120) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, writeOperation.path)
        let writtenSettings = try Data_PbUserDeviceSettings(serializedBytes: try data(from: v2MockClient.writeCalls[0].data))
        let atdSettings = writtenSettings.automaticMeasurementSettings.automaticTrainingDetectionSettings
        XCTAssertEqual(PolarUserDeviceSettings.AutomaticTrainingDetectionMode.OFF.toProto(), atdSettings.state)
        XCTAssertEqual(11, atdSettings.sensitivity)
        XCTAssertEqual(120, atdSettings.minimumTrainingDurationSeconds)
    }

    // MARK: - setAutomaticOHRMeasurementEnabled

    func test_setAutomaticOHRMeasurementEnabled_readsCurrentSettingsAndWritesAlwaysOnState() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("set-automatic-ohr-measurement")
        var currentSettings = Data_PbUserDeviceSettings()
        currentSettings.generalSettings.deviceLocation = .deviceLocationWristLeft
        var currentAtdSettings = Data_PbAutomaticTrainingDetectionSettings()
        currentAtdSettings.state = .on
        currentAtdSettings.sensitivity = 44
        currentAtdSettings.minimumTrainingDurationSeconds = 180
        currentSettings.automaticMeasurementSettings.automaticTrainingDetectionSettings = currentAtdSettings
        currentSettings.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date(timeIntervalSince1970: 0))
        v2MockClient.requestReturnValue = .success(try currentSettings.serializedData())
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }

        try awaitVoidAsync { [self] in try await v2Api.setAutomaticOHRMeasurementEnabled(deviceId, enabled: true) }

        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, requestOperation.path)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, writeOperation.path)
        let writtenSettings = try Data_PbUserDeviceSettings(serializedBytes: try data(from: v2MockClient.writeCalls[0].data))
        XCTAssertEqual(.alwaysOn, writtenSettings.automaticMeasurementSettings.automaticOhrMeasurement.state)
        XCTAssertEqual(currentAtdSettings, writtenSettings.automaticMeasurementSettings.automaticTrainingDetectionSettings)
    }

    func test_setAutomaticOHRMeasurementEnabled_propagatesWriteFailureAfterOffPayloadIsPrepared() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("set-automatic-ohr-measurement-write-failure")
        var currentSettings = Data_PbUserDeviceSettings()
        currentSettings.generalSettings.deviceLocation = .deviceLocationWristLeft
        currentSettings.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date(timeIntervalSince1970: 0))
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7021, userInfo: [NSLocalizedDescriptionKey: "automatic OHR write failed"])
        v2MockClient.requestReturnValue = .success(try currentSettings.serializedData())
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: transportError)
        }

        let error = awaitErrorAsync { [self] in try await v2Api.setAutomaticOHRMeasurementEnabled(deviceId, enabled: false) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, writeOperation.path)
        let writtenSettings = try Data_PbUserDeviceSettings(serializedBytes: try data(from: v2MockClient.writeCalls[0].data))
        XCTAssertEqual(.off, writtenSettings.automaticMeasurementSettings.automaticOhrMeasurement.state)
    }

    // MARK: - getPolarUserDeviceSettings

    func test_getPolarUserDeviceSettings_readsCurrentSettingsFromFakeTransport() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("get-user-device-settings")
        var currentSettings = Data_PbUserDeviceSettings()
        currentSettings.generalSettings.deviceLocation = .deviceLocationWristRight
        var telemetrySettings = Data_PbUserDeviceTelemetrySettings()
        telemetrySettings.telemetryEnabled = true
        currentSettings.telemetrySettings = telemetrySettings
        currentSettings.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date(timeIntervalSince1970: 0))
        v2MockClient.requestReturnValue = .success(try currentSettings.serializedData())

        let result = try awaitSingleAsync { [self] in try await v2Api.getPolarUserDeviceSettings(identifier: deviceId) }

        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, requestOperation.path)
        XCTAssertEqual(.WRIST_RIGHT, result.deviceLocation)
        XCTAssertEqual(true, result.telemetryEnabled)
    }

    func test_getPolarUserDeviceSettings_propagatesFakeTransportReadFailure() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("get-user-device-settings-read-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7003, userInfo: [NSLocalizedDescriptionKey: "user settings read failed"])
        v2MockClient.requestReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await v2Api.getPolarUserDeviceSettings(identifier: deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, requestOperation.path)
    }

    func test_setDaylightSavingTime_readsCurrentSettingsAndWritesNextDstTransition() throws {
        try assertUserDeviceSettingsRuntimePolicyVectorContains("set-daylight-saving-time")
        let originalTimeZone = getenv("TZ").map { String(cString: $0) }
        setenv("TZ", "Europe/Helsinki", 1)
        tzset()
        NSTimeZone.resetSystemTimeZone()
        defer {
            if let originalTimeZone { setenv("TZ", originalTimeZone, 1) } else { unsetenv("TZ") }
            tzset()
            NSTimeZone.resetSystemTimeZone()
        }
        var currentSettings = Data_PbUserDeviceSettings()
        currentSettings.generalSettings.deviceLocation = .deviceLocationWristLeft
        currentSettings.lastModified = PolarTimeUtils.dateToPbSystemDateTime(date: Date(timeIntervalSince1970: 0))
        v2MockClient.requestReturnValue = .success(try currentSettings.serializedData())

        try awaitVoidAsync { [self] in try await v2Api.setDaylightSavingTime(deviceId) }

        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, requestOperation.path)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, writeOperation.path)
        let writtenSettings = try Data_PbUserDeviceSettings(serializedBytes: try data(from: v2MockClient.writeCalls[0].data))
        XCTAssertTrue(writtenSettings.hasDaylightSaving)
        XCTAssertTrue(writtenSettings.daylightSaving.hasNextDaylightSavingTime)
        XCTAssertTrue(writtenSettings.daylightSaving.hasOffset)
        XCTAssertNotEqual(0, writtenSettings.daylightSaving.offset)
    }

    func test_getBatteryLevel_returnsCachedBatteryClientLevel() throws {
        let gatt = MockPolarGattServiceTransmitter()
        let batteryClient = BleBasClient(gattServiceTransmitter: gatt)
        batteryClient.processServiceData(CBUUID(string: "2A19"), data: Data([87]), err: 0)
        let session = MockBleDeviceSession(mockFtpClient: v2MockClient, mockBatteryClient: batteryClient)
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: session)

        let result = try api.getBatteryLevel(identifier: deviceId)

        XCTAssertEqual(87, result)
    }

    func test_getChargerState_returnsCachedBatteryClientChargeState() throws {
        let gatt = MockPolarGattServiceTransmitter()
        let batteryClient = BleBasClient(gattServiceTransmitter: gatt)
        batteryClient.processServiceData(BleBasClient.BATTERY_STATUS_CHARACTERISTIC, data: Data([0x00, 0b10100011]), err: 0)
        let session = MockBleDeviceSession(mockFtpClient: v2MockClient, mockBatteryClient: batteryClient)
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: session)

        let result = try api.getChargerState(identifier: deviceId)

        XCTAssertEqual(.charging, result)
    }

    func test_getRSSIValue_returnsSessionRSSI() throws {
        v2MockSession.rssi = -57

        let result = try v2Api.getRSSIValue(deviceId)

        XCTAssertEqual(-57, result)
    }

    func test_checkIfDeviceDisconnectedDueRemovedPairing_returnsSessionPairingFlag() throws {
        v2MockSession.error = NSError(domain: CBErrorDomain, code: CBError.Code.peerRemovedPairingInformation.rawValue)

        let result = try v2Api.checkIfDeviceDisconnectedDueRemovedPairing(deviceId)

        XCTAssertTrue(result)
    }

    // MARK: - getDiskSpace

    func test_getDiskSpace_success() throws {
        try assertDiskTimeRuntimePolicyVectorContains("get-disk-space")
        var proto = Protocol_PbPFtpDiskSpaceResult()
        proto.fragmentSize = 512; proto.totalFragments = 200; proto.freeFragments = 100
        v2MockClient.queryReturnValue = .success(try proto.serializedData())
        let d = try awaitSingleAsync { [self] in try await v2Api.getDiskSpace(deviceId) }
        XCTAssertEqual(d.totalSpace, 512 * 200); XCTAssertEqual(d.freeSpace, 512 * 100)
        XCTAssertEqual(v2MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.getDiskSpace.rawValue)
    }

    func test_getDiskSpace_queryError_propagatesError() {
        v2MockClient.queryReturnValue = .failure(NSError(domain: "test", code: 42))
        XCTAssertNotNil(awaitErrorAsync { [self] in try await v2Api.getDiskSpace(deviceId) })
    }

    // MARK: - setLedConfig

    func test_setLedConfig_writesLedConfigPayload() throws {
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }

        try awaitVoidAsync { [self] in try await v2Api.setLedConfig(deviceId, ledConfig: LedConfig(sdkModeLedEnabled: true, ppiModeLedEnabled: false)) }

        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(LedConfig.LED_CONFIG_FILENAME, writeOperation.path)
        XCTAssertEqual(Data([LedConfig.LED_ANIMATION_ENABLE_BYTE, LedConfig.LED_ANIMATION_DISABLE_BYTE]), try data(from: v2MockClient.writeCalls[0].data))
    }

    func test_setLedConfig_headersUseSharedFileFacadePlanning() {
        let writeOperation = PolarBleApiImpl.ledConfigWriteOperation()

        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(LedConfig.LED_CONFIG_FILENAME, writeOperation.path)
    }

    func test_setLedConfig_writeError_propagatesErrorAfterPayloadIsPrepared() throws {
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7010, userInfo: [NSLocalizedDescriptionKey: "led config write failed"])
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: transportError)
        }

        let error = awaitErrorAsync { [self] in try await v2Api.setLedConfig(deviceId, ledConfig: LedConfig(sdkModeLedEnabled: false, ppiModeLedEnabled: true)) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(LedConfig.LED_CONFIG_FILENAME, writeOperation.path)
        XCTAssertEqual(Data([LedConfig.LED_ANIMATION_DISABLE_BYTE, LedConfig.LED_ANIMATION_ENABLE_BYTE]), try data(from: v2MockClient.writeCalls[0].data))
    }

    // MARK: - SD log configuration

    func test_getSDLogConfiguration_readsConfigAndWrapsSessionNotifications() throws {
        let plannedStartNotifications = try XCTUnwrap(PolarRuntimePlanner.commandSyncStartNotifications(id: "sync-start-success"))
        let plannedStopNotifications = try XCTUnwrap(PolarRuntimePlanner.commandSyncStopNotifications(id: "sync-stop-success"))
        var proto = Data_PbSensorDataLog()
        proto.ohrLogEnabled = true
        proto.ppiLogEnabled = false
        proto.magnetometerLogFrequency = .magLog10Hz
        v2MockClient.requestReturnValue = .success(try proto.serializedData())

        let result = try awaitSingleAsync { [self] in try await v2Api.getSDLogConfiguration(deviceId) }

        XCTAssertEqual(true, result.ohrLogEnabled)
        XCTAssertEqual(false, result.ppiLogEnabled)
        XCTAssertEqual(Data_PbSensorDataLog.PbMagnetometerLogFrequency.magLog10Hz.rawValue, result.magnetometerFrequency)
        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 2)
        XCTAssertEqual(plannedStartNotifications.first, v2MockClient.sendNotificationCalls[0].notification)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        XCTAssertNil(v2MockClient.sendNotificationCalls[0].parameters)
        XCTAssertEqual(plannedStopNotifications.last, v2MockClient.sendNotificationCalls[1].notification)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue, v2MockClient.sendNotificationCalls[1].notification)
        XCTAssertNil(v2MockClient.sendNotificationCalls[1].parameters)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(SERVICE_DATALOG_CONFIG_FILEPATH, requestOperation.path)
    }

    func test_setSDLogConfiguration_writesConfigPayload() throws {
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }
        let config = SDLogConfig(
            ppiLogEnabled: false,
            accelerationLogEnabled: nil,
            caloriesLogEnabled: nil,
            gpsLogEnabled: nil,
            gpsNmeaLogEnabled: nil,
            magnetometerLogEnabled: nil,
            tapLogEnabled: nil,
            barometerLogEnabled: nil,
            gyroscopeLogEnabled: nil,
            sleepLogEnabled: nil,
            slopeLogEnabled: nil,
            ambientLightLogEnabled: nil,
            tlrLogEnabled: nil,
            ondemandLogEnabled: nil,
            capsenseLogEnabled: nil,
            fusionLogEnabled: nil,
            metLogEnabled: nil,
            ohrLogEnabled: true,
            verticalAccLogEnabled: nil,
            amdLogEnabled: nil,
            skinTemperatureLogEnabled: nil,
            compassLogEnabled: nil,
            speed3DLogEnabled: nil,
            logTrigger: nil,
            magnetometerFrequency: Data_PbSensorDataLog.PbMagnetometerLogFrequency.magLog10Hz.rawValue
        )

        try awaitVoidAsync { [self] in try await v2Api.setSDLogConfiguration(deviceId, logConfiguration: config) }

        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(SERVICE_DATALOG_CONFIG_FILEPATH, writeOperation.path)
        let writtenConfig = try Data_PbSensorDataLog(serializedBytes: try data(from: v2MockClient.writeCalls[0].data))
        XCTAssertTrue(writtenConfig.ohrLogEnabled)
        XCTAssertFalse(writtenConfig.ppiLogEnabled)
        XCTAssertEqual(.magLog10Hz, writtenConfig.magnetometerLogFrequency)
    }

    func test_sdLogConfigEnumMappingPreservesProtoValues() throws {
        let config = SDLogConfig(
            ppiLogEnabled: nil,
            accelerationLogEnabled: nil,
            caloriesLogEnabled: nil,
            gpsLogEnabled: nil,
            gpsNmeaLogEnabled: nil,
            magnetometerLogEnabled: nil,
            tapLogEnabled: nil,
            barometerLogEnabled: nil,
            gyroscopeLogEnabled: nil,
            sleepLogEnabled: nil,
            slopeLogEnabled: nil,
            ambientLightLogEnabled: nil,
            tlrLogEnabled: nil,
            ondemandLogEnabled: nil,
            capsenseLogEnabled: nil,
            fusionLogEnabled: nil,
            metLogEnabled: nil,
            ohrLogEnabled: nil,
            verticalAccLogEnabled: nil,
            amdLogEnabled: nil,
            skinTemperatureLogEnabled: nil,
            compassLogEnabled: nil,
            speed3DLogEnabled: nil,
            logTrigger: Data_PbSensorDataLog.PbLogTrigger.logTriggerExercise.rawValue,
            magnetometerFrequency: Data_PbSensorDataLog.PbMagnetometerLogFrequency.magLog100Hz.rawValue
        )

        let proto = SDLogConfig.toProto(sdLogConfig: config)

        XCTAssertEqual(.logTriggerExercise, proto.logTrigger)
        XCTAssertEqual(2, proto.logTrigger.rawValue)
        XCTAssertEqual(.magLog100Hz, proto.magnetometerLogFrequency)
        XCTAssertEqual(3, proto.magnetometerLogFrequency.rawValue)
    }

    func testSdLogConfigFileHeadersUseSharedFileFacadePlanning() {
        let readOperation = PolarBleApiImpl.sdLogConfigReadOperation()
        XCTAssertEqual(readOperation.command, .get)
        XCTAssertEqual(readOperation.path, SERVICE_DATALOG_CONFIG_FILEPATH)

        let writeOperation = PolarBleApiImpl.sdLogConfigWriteOperation()
        XCTAssertEqual(writeOperation.command, .put)
        XCTAssertEqual(writeOperation.path, SERVICE_DATALOG_CONFIG_FILEPATH)
    }

    func test_setSDLogConfiguration_writeError_propagatesErrorAfterPayloadIsPrepared() throws {
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7022, userInfo: [NSLocalizedDescriptionKey: "sd log write failed"])
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: transportError)
        }
        let config = SDLogConfig(
            ppiLogEnabled: nil,
            accelerationLogEnabled: nil,
            caloriesLogEnabled: nil,
            gpsLogEnabled: nil,
            gpsNmeaLogEnabled: nil,
            magnetometerLogEnabled: nil,
            tapLogEnabled: nil,
            barometerLogEnabled: nil,
            gyroscopeLogEnabled: nil,
            sleepLogEnabled: nil,
            slopeLogEnabled: nil,
            ambientLightLogEnabled: nil,
            tlrLogEnabled: nil,
            ondemandLogEnabled: nil,
            capsenseLogEnabled: nil,
            fusionLogEnabled: nil,
            metLogEnabled: nil,
            ohrLogEnabled: false,
            verticalAccLogEnabled: nil,
            amdLogEnabled: nil,
            skinTemperatureLogEnabled: nil,
            compassLogEnabled: nil,
            speed3DLogEnabled: nil,
            logTrigger: nil,
            magnetometerFrequency: nil
        )

        let error = awaitErrorAsync { [self] in try await v2Api.setSDLogConfiguration(deviceId, logConfiguration: config) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(SERVICE_DATALOG_CONFIG_FILEPATH, writeOperation.path)
        let writtenConfig = try Data_PbSensorDataLog(serializedBytes: try data(from: v2MockClient.writeCalls[0].data))
        XCTAssertFalse(writtenConfig.ohrLogEnabled)
    }

    // MARK: - getUserPhysicalConfiguration

    func test_getUserPhysicalConfiguration_readsPhysicalConfigurationFromFakeTransport() throws {
        var components = DateComponents()
        components.year = 1990
        components.month = 1
        components.day = 2
        let birthDate = try XCTUnwrap(Calendar(identifier: .gregorian).date(from: components))
        let config = PolarFirstTimeUseConfig(
            gender: .male,
            birthDate: birthDate,
            height: 180.5,
            weight: 75.5,
            maxHeartRate: 190,
            vo2Max: 50,
            restingHeartRate: 55,
            trainingBackground: .frequent,
            deviceTime: "2026-05-31T12:00:00Z",
            typicalDay: .mostlyStanding,
            sleepGoalMinutes: 480
        )
        v2MockClient.requestReturnValue = .success(try XCTUnwrap(config.toProto()).serializedData())

        let result = try awaitSingleAsync { [self] in try await v2Api.getUserPhysicalConfiguration(deviceId) }

        let physicalConfiguration = try XCTUnwrap(result)
        XCTAssertEqual(.male, physicalConfiguration.gender)
        let birthComponents = Calendar(identifier: .gregorian).dateComponents([.year, .month, .day], from: physicalConfiguration.birthDate)
        XCTAssertEqual(1990, birthComponents.year)
        XCTAssertEqual(1, birthComponents.month)
        XCTAssertEqual(2, birthComponents.day)
        XCTAssertEqual(180.5, physicalConfiguration.height)
        XCTAssertEqual(75.5, physicalConfiguration.weight)
        XCTAssertEqual(190, physicalConfiguration.maxHeartRate)
        XCTAssertEqual(50, physicalConfiguration.vo2Max)
        XCTAssertEqual(55, physicalConfiguration.restingHeartRate)
        XCTAssertEqual(PolarFirstTimeUseConfig.TrainingBackground.frequent.rawValue, physicalConfiguration.trainingBackground)
        XCTAssertEqual(.mostlyStanding, physicalConfiguration.typicalDay)
        XCTAssertEqual(480, physicalConfiguration.sleepGoalMinutes)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(PolarFirstTimeUseConfig.FTU_CONFIG_FILEPATH, requestOperation.path)
    }

    func test_firstTimeUsePhysicalConfigEnumMappingPreservesProtoValues() throws {
        let birthDate = try XCTUnwrap(Calendar(identifier: .gregorian).date(from: DateComponents(year: 1990, month: 1, day: 2)))
        let config = PolarFirstTimeUseConfig(
            gender: .female,
            birthDate: birthDate,
            height: 170.0,
            weight: 65.0,
            maxHeartRate: 185,
            vo2Max: 45,
            restingHeartRate: 52,
            trainingBackground: .semiPro,
            deviceTime: "2026-05-31T12:00:00Z",
            typicalDay: .mostlyMoving,
            sleepGoalMinutes: 480
        )

        let proto = try XCTUnwrap(config.toProto())

        XCTAssertEqual(Data_PbUserGender.Gender.female, proto.gender.value)
        XCTAssertEqual(2, proto.gender.value.rawValue)
        XCTAssertEqual(Data_PbUserTrainingBackground.TrainingBackground.semiPro, proto.trainingBackground.value)
        XCTAssertEqual(50, proto.trainingBackground.value.rawValue)
        XCTAssertEqual(Data_PbUserTypicalDay.TypicalDay.mostlyMoving, proto.typicalDay.value)
        XCTAssertEqual(3, proto.typicalDay.value.rawValue)
        let physicalConfiguration = proto.toPolarPhysicalConfiguration()
        XCTAssertEqual(.female, physicalConfiguration.gender)
        XCTAssertEqual(PolarFirstTimeUseConfig.TrainingBackground.semiPro.rawValue, physicalConfiguration.trainingBackground)
        XCTAssertEqual(.mostlyMoving, physicalConfiguration.typicalDay)
    }

    func test_getUserPhysicalConfiguration_returnsNilWhenPhysicalDataFileIsMissing() throws {
        v2MockClient.requestReturnValueClosure = { _ in throw BlePsFtpException.responseError(errorCode: Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue) }

        let result = try awaitSingleAsync { [self] in try await v2Api.getUserPhysicalConfiguration(deviceId) }

        XCTAssertNil(result)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(PolarFirstTimeUseConfig.FTU_CONFIG_FILEPATH, requestOperation.path)
    }

    func test_doFirstTimeUse_writesUserIdAndPhysicalConfigBetweenSyncNotifications() throws {
        let birthDate = try XCTUnwrap(Calendar(identifier: .gregorian).date(from: DateComponents(year: 1992, month: 3, day: 4)))
        let config = PolarFirstTimeUseConfig(
            gender: .female,
            birthDate: birthDate,
            height: 171.5,
            weight: 66.5,
            maxHeartRate: 188,
            vo2Max: 47,
            restingHeartRate: 53,
            trainingBackground: .regular,
            deviceTime: "2026-05-31T12:34:56Z",
            typicalDay: .mostlySitting,
            sleepGoalMinutes: 450
        )
        v2MockClient.queryReturnValue = .success(Data())

        try awaitVoidAsync { [self] in try await v2Api.doFirstTimeUse(deviceId, ftuConfig: config) }

        XCTAssertEqual([Protocol_PbPFtpQuery.requestSynchronization.rawValue, Protocol_PbPFtpQuery.setSystemTime.rawValue, Protocol_PbPFtpQuery.setLocalTime.rawValue], v2MockClient.queryCalls.map { $0.id })
        XCTAssertEqual([Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue, Protocol_PbPFtpHostToDevNotification.startSync.rawValue, Protocol_PbPFtpHostToDevNotification.stopSync.rawValue, Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue], v2MockClient.sendNotificationCalls.map { $0.notification })
        XCTAssertEqual(v2MockClient.writeCalls.count, 2)
        let writeOperations = try v2MockClient.writeCalls.map { try Protocol_PbPFtpOperation(serializedBytes: $0.header as Data) }
        XCTAssertEqual([UserIdentifierType.USER_IDENTIFIER_FILENAME, PolarFirstTimeUseConfig.FTU_CONFIG_FILEPATH], writeOperations.map { $0.path })
        XCTAssertEqual([.put, .put], writeOperations.map { $0.command })
        XCTAssertTrue(try Data_PbUserIdentifier(serializedBytes: try data(from: v2MockClient.writeCalls[0].data)).hasMasterIdentifier)
        XCTAssertEqual(try XCTUnwrap(config.toProto()).serializedData(), try data(from: v2MockClient.writeCalls[1].data))
        let stopSyncParams = try Protocol_PbPFtpStopSyncParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls[2].parameters) as Data)
        XCTAssertTrue(stopSyncParams.completed)
    }

    func testFirstTimeUseFileHeadersUseSharedFileFacadePlanning() {
        let userIdReadOperation = PolarBleApiImpl.firstTimeUseUserIdReadOperation()
        XCTAssertEqual(userIdReadOperation.command, .get)
        XCTAssertEqual(userIdReadOperation.path, UserIdentifierType.USER_IDENTIFIER_FILENAME)

        let userIdWriteOperation = PolarBleApiImpl.firstTimeUseUserIdWriteOperation()
        XCTAssertEqual(userIdWriteOperation.command, .put)
        XCTAssertEqual(userIdWriteOperation.path, UserIdentifierType.USER_IDENTIFIER_FILENAME)

        let physicalConfigReadOperation = PolarBleApiImpl.firstTimeUsePhysicalConfigReadOperation()
        XCTAssertEqual(physicalConfigReadOperation.command, .get)
        XCTAssertEqual(physicalConfigReadOperation.path, PolarFirstTimeUseConfig.FTU_CONFIG_FILEPATH)

        let physicalConfigWriteOperation = PolarBleApiImpl.firstTimeUsePhysicalConfigWriteOperation()
        XCTAssertEqual(physicalConfigWriteOperation.command, .put)
        XCTAssertEqual(physicalConfigWriteOperation.path, PolarFirstTimeUseConfig.FTU_CONFIG_FILEPATH)
    }

    func test_doFirstTimeUse_userIdWriteFailurePropagatesWithoutTerminateNotifications() throws {
        let birthDate = try XCTUnwrap(Calendar(identifier: .gregorian).date(from: DateComponents(year: 1991, month: 2, day: 3)))
        let config = PolarFirstTimeUseConfig(
            gender: .male,
            birthDate: birthDate,
            height: 181.0,
            weight: 76.0,
            maxHeartRate: 190,
            vo2Max: 48,
            restingHeartRate: 54,
            trainingBackground: .frequent,
            deviceTime: "2026-05-31T12:34:56Z",
            typicalDay: .mostlyStanding,
            sleepGoalMinutes: 480
        )
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7023, userInfo: [NSLocalizedDescriptionKey: "FTU user id write failed"])
        v2MockClient.queryReturnValue = .success(Data())
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: transportError)
        }

        let error = awaitErrorAsync { [self] in try await v2Api.doFirstTimeUse(deviceId, ftuConfig: config) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual([Protocol_PbPFtpQuery.requestSynchronization.rawValue, Protocol_PbPFtpQuery.setSystemTime.rawValue, Protocol_PbPFtpQuery.setLocalTime.rawValue], v2MockClient.queryCalls.map { $0.id })
        XCTAssertEqual([Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue, Protocol_PbPFtpHostToDevNotification.startSync.rawValue], v2MockClient.sendNotificationCalls.map { $0.notification })
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(UserIdentifierType.USER_IDENTIFIER_FILENAME, writeOperation.path)
        XCTAssertTrue(try Data_PbUserIdentifier(serializedBytes: try data(from: v2MockClient.writeCalls[0].data)).hasMasterIdentifier)
    }

    // MARK: - putNotification

    func test_putNotification_writesRestNotificationPayload() throws {
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }

        try awaitVoidAsync { [self] in try await v2Api.putNotification(identifier: deviceId, notification: "{\"enabled\":true}", path: "/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording") }

        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual("/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording", writeOperation.path)
        XCTAssertEqual(Data("{\"enabled\":true}".utf8), try data(from: v2MockClient.writeCalls[0].data))
    }

    func test_stopSleepRecording_usesSharedSleepRestPaths() throws {
        v2MockClient.requestReturnValue = .success(Data())
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }

        try awaitVoidAsync { [self] in try await v2Api.stopSleepRecording(identifier: deviceId) }

        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(PolarRuntimePlanner.sleepRestApiPath(), requestOperation.path)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual(PolarRuntimePlanner.stopSleepRecordingPath(), writeOperation.path)
        XCTAssertEqual(Data("{}".utf8), try data(from: v2MockClient.writeCalls[0].data))
    }

    func test_putNotification_writeError_propagatesErrorAfterPayloadIsPrepared() throws {
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7012, userInfo: [NSLocalizedDescriptionKey: "REST notification write failed"])
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: transportError)
        }

        let error = awaitErrorAsync { [self] in try await v2Api.putNotification(identifier: deviceId, notification: "{}", path: "/REST/TEST.API?cmd=post") }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual("/REST/TEST.API?cmd=post", writeOperation.path)
        XCTAssertEqual(Data("{}".utf8), try data(from: v2MockClient.writeCalls[0].data))
    }

    // MARK: - Low-level file APIs

    func test_readFile_readsLowLevelFilePathFromFakeTransport() throws {
        try assertFileFacadeRuntimePolicyVectorContains("read-low-level-file-success")
        v2MockClient.requestReturnValue = .success(Data([0x01, 0x02, 0x03]))

        let result = try awaitSingleAsync { [self] in try await v2Api.readFile(identifier: deviceId, filePath: "/U/0/CUSTOM.BIN") }

        XCTAssertEqual(Data([0x01, 0x02, 0x03]), try XCTUnwrap(result))
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/U/0/CUSTOM.BIN", requestOperation.path)
    }

    func test_readFile_emptyLowLevelFilePayloadSucceeds() throws {
        try assertFileFacadeRuntimePolicyVectorContains("read-low-level-file-empty-success")
        v2MockClient.requestReturnValue = .success(Data())

        let result = try awaitSingleAsync { [self] in try await v2Api.readFile(identifier: deviceId, filePath: "/U/0/EMPTY.BIN") }

        XCTAssertEqual(Data(), try XCTUnwrap(result))
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/U/0/EMPTY.BIN", requestOperation.path)
    }

    func test_readFile_requestError_propagatesError() throws {
        try assertFileFacadeRuntimePolicyVectorContains("read-low-level-file-request-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7017, userInfo: [NSLocalizedDescriptionKey: "low level read failed"])
        v2MockClient.requestReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await v2Api.readFile(identifier: deviceId, filePath: "/U/0/CUSTOM.BIN") }

        XCTAssertNotNil(error)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/U/0/CUSTOM.BIN", requestOperation.path)
    }

    func test_readFile_responseError_wrapsAsDeviceErrorWithPftpDetails() throws {
        try assertFileFacadeRuntimePolicyVectorContains("read-low-level-file-response-error")
        v2MockClient.requestReturnValue = .failure(BlePsFtpException.responseError(errorCode: Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue))

        let error = awaitErrorAsync { [self] in try await v2Api.readFile(identifier: deviceId, filePath: "/U/0/CUSTOM.BIN") }

        guard case .deviceError(description: let description) = try XCTUnwrap(error as? PolarErrors) else {
            return XCTFail("Expected PolarErrors.deviceError, got \(String(describing: error))")
        }
        XCTAssertTrue(description.contains("/U/0/CUSTOM.BIN"), description)
        XCTAssertTrue(description.contains("responseError"), description)
        XCTAssertTrue(description.contains(String(Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue)), description)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/U/0/CUSTOM.BIN", requestOperation.path)
    }

    func test_writeFile_writesLowLevelFilePayload() throws {
        try assertFileFacadeRuntimePolicyVectorContains("write-low-level-file-success")
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.finish()
        }

        try awaitVoidAsync { [self] in try await v2Api.writeFile(identifier: deviceId, filePath: "/U/0/CUSTOM.BIN", fileData: Data([0x0A, 0x0B])) }

        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual("/U/0/CUSTOM.BIN", writeOperation.path)
        XCTAssertEqual(Data([0x0A, 0x0B]), try data(from: v2MockClient.writeCalls[0].data))
    }

    func test_writeFile_consumesLowLevelWriteProgressBeforeSuccess() throws {
        try assertFileFacadeRuntimePolicyVectorContains("write-low-level-file-progress-success")
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.yield(0)
            continuation.yield(2)
            continuation.finish()
        }

        try awaitVoidAsync { [self] in try await v2Api.writeFile(identifier: deviceId, filePath: "/U/0/PROGRESS.BIN", fileData: Data([0x10, 0x11])) }

        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual("/U/0/PROGRESS.BIN", writeOperation.path)
        XCTAssertEqual(Data([0x10, 0x11]), try data(from: v2MockClient.writeCalls[0].data))
    }

    func test_writeFile_writeError_propagatesErrorAfterPayloadIsPrepared() throws {
        try assertFileFacadeRuntimePolicyVectorContains("write-low-level-file-stream-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7018, userInfo: [NSLocalizedDescriptionKey: "low level write failed"])
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: transportError)
        }

        let error = awaitErrorAsync { [self] in try await v2Api.writeFile(identifier: deviceId, filePath: "/U/0/CUSTOM.BIN", fileData: Data([0x0C, 0x0D])) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual("/U/0/CUSTOM.BIN", writeOperation.path)
        XCTAssertEqual(Data([0x0C, 0x0D]), try data(from: v2MockClient.writeCalls[0].data))
    }

    func test_writeFile_responseError_preservesPftpErrorAfterPayloadIsPrepared() throws {
        try assertFileFacadeRuntimePolicyVectorContains("write-low-level-file-response-error")
        v2MockClient.writeReturnValue = AsyncThrowingStream { continuation in
            continuation.finish(throwing: BlePsFtpException.responseError(errorCode: Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue))
        }

        let error = awaitErrorAsync { [self] in try await v2Api.writeFile(identifier: deviceId, filePath: "/U/0/CUSTOM.BIN", fileData: Data([0x0E, 0x0F])) }

        guard case .responseError(errorCode: let errorCode) = try XCTUnwrap(error as? BlePsFtpException) else {
            return XCTFail("Expected BlePsFtpException.responseError(noSuchFileOrDirectory), got \(String(describing: error))")
        }
        XCTAssertEqual(errorCode, Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue)
        XCTAssertEqual(v2MockClient.writeCalls.count, 1)
        let writeOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.writeCalls[0].header as Data)
        XCTAssertEqual(.put, writeOperation.command)
        XCTAssertEqual("/U/0/CUSTOM.BIN", writeOperation.path)
        XCTAssertEqual(Data([0x0E, 0x0F]), try data(from: v2MockClient.writeCalls[0].data))
    }

    func test_deleteFileOrDirectory_sendsLowLevelRemoveRequest() throws {
        try assertFileFacadeRuntimePolicyVectorContains("delete-low-level-file-success")
        v2MockClient.requestReturnValue = .success(Data())

        try awaitVoidAsync { [self] in try await v2Api.deleteFileOrDirectory(identifier: deviceId, filePath: "/U/0/CUSTOM.BIN") }

        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.remove, requestOperation.command)
        XCTAssertEqual("/U/0/CUSTOM.BIN", requestOperation.path)
    }

    func test_deleteFileOrDirectory_requestError_propagatesError() throws {
        try assertFileFacadeRuntimePolicyVectorContains("delete-low-level-file-request-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7024, userInfo: [NSLocalizedDescriptionKey: "low level delete failed"])
        v2MockClient.requestReturnValue = .failure(transportError)

        var error: Error?
        do {
            try awaitVoidAsync { [self] in try await v2Api.deleteFileOrDirectory(identifier: deviceId, filePath: "/U/0/CUSTOM.BIN") }
        } catch let caught {
            error = caught
        }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.remove, requestOperation.command)
        XCTAssertEqual("/U/0/CUSTOM.BIN", requestOperation.path)
    }

    func test_deleteFileOrDirectory_propagatesLowLevelResponseError() throws {
        try assertFileFacadeRuntimePolicyVectorContains("delete-low-level-file-response-error")
        v2MockClient.requestReturnValue = .failure(BlePsFtpException.responseError(errorCode: Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue))

        let error = awaitErrorAsync { [self] in try await v2Api.deleteFileOrDirectory(identifier: deviceId, filePath: "/U/0/CUSTOM.BIN") }

        guard case .responseError(errorCode: let errorCode) = try XCTUnwrap(error as? BlePsFtpException) else {
            return XCTFail("Expected BlePsFtpException.responseError(noSuchFileOrDirectory), got \(String(describing: error))")
        }
        XCTAssertEqual(errorCode, Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.remove, requestOperation.command)
        XCTAssertEqual("/U/0/CUSTOM.BIN", requestOperation.path)
    }

    func test_deleteDeviceDateFolders_removesEmptyFoldersInDateRange() throws {
        let calendar = Calendar.current
        let fromDate = try localDateInput(year: 2026, month: 5, day: 30, calendar: calendar)
        let toDate = try localDateInput(year: 2026, month: 5, day: 31, calendar: calendar)
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/U/0/": { try self.makeDirectoryProtoData(entries: [("20260530/", 0), ("20260531/", 0), ("20260601/", 0)]) },
            "/U/0/20260530/": { try self.makeDirectoryProtoData(entries: []) },
            "/U/0/20260531/": { try self.makeDirectoryProtoData(entries: []) }
        ])

        try awaitVoidAsync { [self] in try await v2Api.deleteDeviceDateFolders(deviceId, fromDate: fromDate, toDate: toDate) }

        let operations = try v2MockClient.requestCalls.map { try Protocol_PbPFtpOperation(serializedBytes: $0) }
        XCTAssertEqual([.get, .get, .remove, .get, .remove], operations.map { $0.command })
        XCTAssertEqual(["/U/0/", "/U/0/20260530/", "/U/0/20260530/", "/U/0/20260531/", "/U/0/20260531/"], operations.map { $0.path })
    }

    private func localDateInput(year: Int, month: Int, day: Int, calendar: Calendar) throws -> Date {
        let midnight = try XCTUnwrap(calendar.date(from: DateComponents(year: year, month: month, day: day)))
        return try XCTUnwrap(calendar.date(byAdding: .second, value: -TimeZone.current.secondsFromGMT(for: Date()), to: midnight))
    }

    func test_deleteDeviceDateFolders_missingDatesPropagateInvalidDateError() {
        let error = awaitErrorAsync { [self] in try await v2Api.deleteDeviceDateFolders(deviceId, fromDate: nil, toDate: nil) }

        XCTAssertNotNil(error)
        XCTAssertTrue(v2MockClient.requestCalls.isEmpty)
    }

    func test_deleteTelemetryData_removesTelemetryBinFilesFromFakeTransport() throws {
        try assertStoredDataCleanupWorkflowVectorContains("telemetry-root-trc-bin-filter")
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/": { try self.makeDirectoryProtoData(entries: [("TRC10.BIN", 4), ("ABC10.BIN", 4), ("TRC10.TXT", 4)]) },
            "/TRC10.BIN": { Data() }
        ])

        try awaitVoidAsync { [self] in try await v2Api.deleteTelemetryData(deviceId) }

        let operations = try v2MockClient.requestCalls.map { try Protocol_PbPFtpOperation(serializedBytes: $0) }
        XCTAssertEqual([.get, .remove], operations.map { $0.command })
        XCTAssertEqual(["/", "/TRC10.BIN"], operations.map { $0.path })
    }

    func test_storedDataCleanupFilterHelpersUseSharedKmpWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual(true, PolarRuntimePlanner.storedDataEntryMatchesFilter(entry: "TRC10.BIN", includePrefixes: ["TRC"], includeSuffixes: [".BIN"]))
        XCTAssertEqual(false, PolarRuntimePlanner.storedDataEntryMatchesFilter(entry: "ABC10.BIN", includePrefixes: ["TRC"], includeSuffixes: [".BIN"]))
        XCTAssertEqual(false, PolarRuntimePlanner.storedDataEntryMatchesFilter(entry: "TRC10.TXT", includePrefixes: ["TRC"], includeSuffixes: [".BIN"]))
        XCTAssertEqual(true, PolarRuntimePlanner.storedDataEntryMatchesFilter(entry: "A.SLG", includeSuffixes: [".SLG", ".TXT"]))
        XCTAssertEqual(false, PolarRuntimePlanner.storedDataEntryMatchesFilter(entry: "C.BPB", includeSuffixes: [".SLG", ".TXT"]))
        XCTAssertEqual(true, PolarRuntimePlanner.shouldPruneStoredDataEmptyParents(dataType: PolarStoredDataType.StoredDataType.ACTIVITY.rawValue))
        XCTAssertEqual(false, PolarRuntimePlanner.shouldPruneStoredDataEmptyParents(dataType: PolarStoredDataType.StoredDataType.AUTO_SAMPLE.rawValue))
        XCTAssertEqual(false, PolarRuntimePlanner.shouldPruneStoredDataEmptyParents(dataType: PolarStoredDataType.StoredDataType.SDLOGS.rawValue))
        XCTAssertEqual("/U/0/", PolarRuntimePlanner.storedDataCleanupRootPath(dataType: PolarStoredDataType.StoredDataType.ACTIVITY.rawValue, defaultRoot: "/U/0/"))
        XCTAssertEqual("/U/0/AUTOS", PolarRuntimePlanner.storedDataCleanupRootPath(dataType: PolarStoredDataType.StoredDataType.AUTO_SAMPLE.rawValue, defaultRoot: "/U/0/"))
        XCTAssertEqual("/SDLOGS", PolarRuntimePlanner.storedDataCleanupRootPath(dataType: PolarStoredDataType.StoredDataType.SDLOGS.rawValue, defaultRoot: "/U/0/"))
        XCTAssertEqual(true, PolarRuntimePlanner.storedDataDateIsOnOrBefore(day: "20260530", cutoffDate: "20260531"))
        XCTAssertEqual(true, PolarRuntimePlanner.storedDataDateIsOnOrBefore(day: "20260531", cutoffDate: "20260531"))
        XCTAssertEqual(false, PolarRuntimePlanner.storedDataDateIsOnOrBefore(day: "20260601", cutoffDate: "20260531"))
        XCTAssertEqual(["/U/0/20260530/ACT/", "/U/0/20260530/"], PolarRuntimePlanner.storedDataEmptyParentDirectories(filePath: "/U/0/20260530/ACT/ACTIVITY.BPB", trailingSlash: true))
        XCTAssertEqual(["/TRC10.BIN"], PolarRuntimePlanner.storedDataCleanupRemovePaths(kind: "filterDirectoryEntries", rootPath: "/", entries: ["TRC10.BIN", "ABC10.BIN", "TRC10.TXT"], includePrefixes: ["TRC"], includeSuffixes: [".BIN"]))
        XCTAssertEqual(["/SDLOGS/A.SLG", "/SDLOGS/B.TXT"], PolarRuntimePlanner.storedDataCleanupRemovePaths(kind: "filterDirectoryEntries", rootPath: "/SDLOGS", entries: ["A.SLG", "B.TXT", "C.BPB"], includeSuffixes: [".SLG", ".TXT"]))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func test_runtimePlannerSurfacesSharedTerminalDecisionsWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("success", PolarRuntimePlanner.commandQuery(id: "h10-recording-status", query: "REQUEST_RECORDING_STATUS"))
        XCTAssertEqual(Protocol_PbPFtpQuery.requestRecordingStatus.rawValue, PolarRuntimePlanner.commandQueryValue(id: "h10-recording-status", query: "REQUEST_RECORDING_STATUS"))
        XCTAssertEqual(Protocol_PbPFtpQuery.startExercise.rawValue, PolarRuntimePlanner.commandQueryValue(id: "live-exercise-start", query: "START_EXERCISE", parameters: ["sportProfileId=\(PolarExerciseSession.SportProfile.running.rawValue)"]))
        XCTAssertEqual(Protocol_PbPFtpQuery.pauseExercise.rawValue, PolarRuntimePlanner.commandQueryValue(id: "live-exercise-pause", query: "PAUSE_EXERCISE"))
        XCTAssertEqual(Protocol_PbPFtpQuery.resumeExercise.rawValue, PolarRuntimePlanner.commandQueryValue(id: "live-exercise-resume", query: "RESUME_EXERCISE"))
        XCTAssertEqual(Protocol_PbPFtpQuery.stopExercise.rawValue, PolarRuntimePlanner.commandQueryValue(id: "live-exercise-stop", query: "STOP_EXERCISE", parameters: ["save=true"]))
        XCTAssertEqual(Protocol_PbPFtpQuery.getExerciseStatus.rawValue, PolarRuntimePlanner.commandQueryValue(id: "live-exercise-status", query: "GET_EXERCISE_STATUS"))
        XCTAssertEqual(Protocol_PbPFtpQuery.prepareFirmwareUpdate.rawValue, PolarRuntimePlanner.commandQueryValue(id: "firmware-prepare-update", query: "PREPARE_FIRMWARE_UPDATE", parameters: ["file=SYSUPDAT.IMG"]))
        XCTAssertEqual("success", PolarRuntimePlanner.commandReset(id: "restart", sleep: false, factoryDefaults: false, otaFirmwareUpdate: false))
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.reset.rawValue, PolarRuntimePlanner.commandResetNotification(id: "restart", sleep: false, factoryDefaults: false, otaFirmwareUpdate: false))
        XCTAssertEqual("success", PolarRuntimePlanner.commandSyncStart(id: "sync-start-success"))
        XCTAssertEqual(Protocol_PbPFtpQuery.requestSynchronization.rawValue, PolarRuntimePlanner.commandSyncStartQueryValue(id: "sync-start-success"))
        XCTAssertEqual([
            Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue,
            Protocol_PbPFtpHostToDevNotification.startSync.rawValue
        ], PolarRuntimePlanner.commandSyncStartNotifications(id: "sync-start-success"))
        XCTAssertEqual("success", PolarRuntimePlanner.commandSyncStop(id: "sync-stop-success"))
        XCTAssertEqual([
            Protocol_PbPFtpHostToDevNotification.stopSync.rawValue,
            Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue
        ], PolarRuntimePlanner.commandSyncStopNotifications(id: "sync-stop-success"))
        XCTAssertEqual("EXERCISE_STATUS", PolarRuntimePlanner.d2hNotificationTypeName(notificationId: Protocol_PbPFtpDevToHostNotification.exerciseStatus.rawValue))
        XCTAssertEqual("PbPftpDHExerciseStatus", PolarRuntimePlanner.d2hNotificationPlan(notificationId: Protocol_PbPFtpDevToHostNotification.exerciseStatus.rawValue, parametersHex: "0a020802")?.parsedProtoName)
        XCTAssertEqual("success", PolarRuntimePlanner.diskTimeQuery(id: "get-disk-space", query: "GET_DISK_SPACE"))
        XCTAssertEqual(Protocol_PbPFtpQuery.getDiskSpace.rawValue, PolarRuntimePlanner.diskTimeQueryValue(id: "get-disk-space", query: "GET_DISK_SPACE"))
        XCTAssertEqual("success", PolarRuntimePlanner.setLocalTimeV2(systemTimeHour: 12, localTimeHour: 14))
        XCTAssertEqual([
            Protocol_PbPFtpQuery.setSystemTime.rawValue,
            Protocol_PbPFtpQuery.setLocalTime.rawValue
        ], PolarRuntimePlanner.setLocalTimeV2QueryValues(systemTimeHour: 12, localTimeHour: 14))
        XCTAssertEqual("success", PolarRuntimePlanner.setLocalTimeH10(localTimeHour: 14))
        XCTAssertEqual([Protocol_PbPFtpQuery.setLocalTime.rawValue], PolarRuntimePlanner.setLocalTimeH10QueryValues(localTimeHour: 14))
        XCTAssertEqual("success", PolarRuntimePlanner.restFacadeGet(id: "list-rest-api-services-success", path: "/REST/SERVICE.API", payloadShape: "service-list-json"))
        XCTAssertEqual(.get, PolarRuntimePlanner.restFacadeGetOperation(id: "list-rest-api-services-success", path: "/REST/SERVICE.API", payloadShape: "service-list-json")?.command)
        XCTAssertEqual("/REST/SERVICE.API", PolarRuntimePlanner.restFacadeGetOperation(id: "list-rest-api-services-success", path: "/REST/SERVICE.API", payloadShape: "service-list-json")?.path)
        XCTAssertEqual("/REST/SLEEP.API", PolarRuntimePlanner.sleepRestApiPath())
        XCTAssertEqual("/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]", PolarRuntimePlanner.sleepRecordingStateSubscribePath())
        XCTAssertEqual("/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording", PolarRuntimePlanner.stopSleepRecordingPath())
        XCTAssertEqual("success", PolarRuntimePlanner.fileFacade(id: "write-low-level-file-success", command: "PUT", path: "/U/0/CUSTOM.BIN", payloadHex: "0102"))
        XCTAssertEqual(.put, PolarRuntimePlanner.fileFacadeOperation(id: "write-low-level-file-success", command: "PUT", path: "/U/0/CUSTOM.BIN", payloadHex: "0102")?.command)
        XCTAssertEqual("/U/0/CUSTOM.BIN", PolarRuntimePlanner.fileFacadeOperation(id: "write-low-level-file-success", command: "PUT", path: "/U/0/CUSTOM.BIN", payloadHex: "0102")?.path)
        XCTAssertEqual(.get, PolarRuntimePlanner.fileFacadeOperation(id: "read-low-level-file-success", command: "GET", path: "/U/0/CUSTOM.BIN")?.command)
        XCTAssertEqual(.remove, PolarRuntimePlanner.fileFacadeOperation(id: "delete-low-level-file-success", command: "REMOVE", path: "/U/0/CUSTOM.BIN")?.command)
        XCTAssertEqual("transport-error", PolarRuntimePlanner.fileRuntimeError(operation: "readFile", path: "/U/0/CUSTOM.BIN", error: NSError(domain: "PolarBleApiImplTests", code: 1)))
        XCTAssertEqual("success", PolarRuntimePlanner.userDeviceSettings(id: "set-user-device-settings", kind: "write", path: "/U/0/S/UDEVSET.BPB", payloadFields: ["protobufPayload=platform-built"]))
        XCTAssertEqual([.get, .put], PolarRuntimePlanner.userDeviceSettingsOperations(id: "set-telemetry-enabled", kind: "readThenWrite", path: "/U/0/S/UDEVSET.BPB", payloadFields: ["telemetryEnabled=true"])?.map { $0.command })
        XCTAssertEqual(["/U/0/S/UDEVSET.BPB", "/U/0/S/UDEVSET.BPB"], PolarRuntimePlanner.userDeviceSettingsOperations(id: "set-telemetry-enabled", kind: "readThenWrite", path: "/U/0/S/UDEVSET.BPB", payloadFields: ["telemetryEnabled=true"])?.map { $0.path })
        XCTAssertEqual("WRIST_RIGHT", PolarRuntimePlanner.userDeviceSettingsDeviceLocationName(value: PbDeviceLocation.deviceLocationWristRight.rawValue))
        XCTAssertEqual("ON", PolarRuntimePlanner.userDeviceSettingsUsbConnectionModeName(enabled: true))
        XCTAssertEqual("OFF", PolarRuntimePlanner.userDeviceSettingsAutomaticTrainingDetectionModeName(enabled: false))
        XCTAssertEqual("success", PolarRuntimePlanner.storedDataCleanup(kind: "filterDirectoryEntries", rootPath: "/"))
        XCTAssertEqual("platform-path-split", PolarRuntimePlanner.storedDataCleanup(kind: "activityPrune", rootPath: "/U/0"))
        XCTAssertEqual("success", PolarRuntimePlanner.storedDataCleanup(kind: "automaticSamplePrune", rootPath: "/U/0/AUTOS", cutoffDate: "20260531"))
        XCTAssertEqual("success", PolarRuntimePlanner.offlineTriggerSet(currentTypes: ["acc"], desiredTypes: ["acc"], secretPresent: true))
        XCTAssertEqual("success", PolarRuntimePlanner.offlineTriggerGet(currentTypes: ["acc"]))
        XCTAssertEqual("success", PolarRuntimePlanner.firmwareWorkflow(id: "write-package-success-with-system-update-last", statuses: ["preparingDeviceForFwUpdate", "completed"], firmwareFiles: ["BTUPDAT.BIN", "SYSUPDAT.IMG"]))
        XCTAssertEqual("success", PolarRuntimePlanner.backupRestore(path: "/U/0/BACKUP.TXT", payloadHex: "0102"))
        XCTAssertEqual(.put, PolarRuntimePlanner.backupRestoreOperation(path: "/U/0/BACKUP.TXT", payloadHex: "0102")?.command)
        XCTAssertEqual("/U/0/BACKUP.TXT", PolarRuntimePlanner.backupRestoreOperation(path: "/U/0/BACKUP.TXT", payloadHex: "0102")?.path)
        XCTAssertEqual("/U/0/S/UDEVSET.BPB", PolarFirmwareBackupRuntimePlanner.backupTraversalRootPath("/U/*/S/UDEVSET.BPB"))
        XCTAssertEqual("success", PolarRuntimePlanner.psFtpWriteAck(payloadSize: 2))
        XCTAssertEqual("gattDisconnected", PolarRuntimePlanner.streamSubscription(target: "stream", startConnected: false, checkConnection: true))
        XCTAssertEqual("stream", PolarRuntimePlanner.streamConsumerCancellation(target: "stream"))
        XCTAssertEqual("linkLost", PolarRuntimePlanner.streamDisconnect(target: "stream", error: "linkLost"))
        XCTAssertEqual(1, PolarRuntimePlanner.streamDuplicateCompletion(target: "stream"))
        XCTAssertEqual(0, PolarRuntimePlanner.streamPostCompletionEmission(target: "stream", value: "value"))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func test_fileFacadeRuntimePlannerMapsSharedOperationCommandsWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("success", PolarFileFacadeRuntimePlanner.fileFacade(id: "write-low-level-file-success", command: "PUT", path: "/U/0/CUSTOM.BIN", payloadHex: "0102"))
        let putOperation = PolarFileFacadeRuntimePlanner.fileFacadeOperation(id: "write-low-level-file-success", command: "PUT", path: "/U/0/CUSTOM.BIN", payloadHex: "0102")
        XCTAssertEqual(.put, putOperation?.command)
        XCTAssertEqual("/U/0/CUSTOM.BIN", putOperation?.path)
        XCTAssertEqual(.get, PolarFileFacadeRuntimePlanner.fileFacadeOperation(id: "read-low-level-file-success", command: "GET", path: "/U/0/CUSTOM.BIN")?.command)
        XCTAssertEqual(.remove, PolarFileFacadeRuntimePlanner.fileFacadeOperation(id: "delete-low-level-file-success", command: "REMOVE", path: "/U/0/CUSTOM.BIN")?.command)
        XCTAssertNil(PolarFileFacadeRuntimePlanner.fileFacadeOperation(id: "unsupported-file-operation", command: "UNSUPPORTED", path: "/U/0/CUSTOM.BIN"))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func test_fileRuntimePlannerSurfacesSharedErrorAndWritePolicyWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        let error = NSError(domain: "PolarBleApiImplTests", code: 1)
        XCTAssertEqual("transport-error", PolarFileRuntimePlanner.runtimeError(operation: "readFile", path: "/U/0/CUSTOM.BIN", error: error))
        XCTAssertEqual([0, 1, 2], PolarFileRuntimePlanner.psFtpWriteProgress(payloadSize: 2))
        XCTAssertEqual("success", PolarFileRuntimePlanner.psFtpWriteAck(payloadSize: 2))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func test_commandRuntimePlannerMapsSharedQueriesAndNotificationsWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("success", PolarCommandRuntimePlanner.query(id: "h10-recording-status", query: "REQUEST_RECORDING_STATUS"))
        XCTAssertEqual(Protocol_PbPFtpQuery.requestRecordingStatus.rawValue, PolarCommandRuntimePlanner.queryValue(id: "h10-recording-status", query: "REQUEST_RECORDING_STATUS"))
        XCTAssertEqual("success", PolarCommandRuntimePlanner.reset(id: "restart", sleep: false, factoryDefaults: false, otaFirmwareUpdate: false))
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.reset.rawValue, PolarCommandRuntimePlanner.resetNotification(id: "restart", sleep: false, factoryDefaults: false, otaFirmwareUpdate: false))
        let resetFields = PolarCommandRuntimePlanner.resetFields(id: "warehouse-sleep", sleep: true, factoryDefaults: true, otaFirmwareUpdate: false)
        XCTAssertTrue(resetFields.sleep)
        XCTAssertTrue(resetFields.factoryDefaults)
        XCTAssertFalse(resetFields.otaFirmwareUpdate)
        let h10Fields = PolarCommandRuntimePlanner.h10StartRecordingFields(id: "h10-start-recording", sampleDataIdentifier: "myExercise", sampleType: "SAMPLE_TYPE_HEART_RATE", recordingIntervalSeconds: 1)
        XCTAssertEqual("myExercise", h10Fields.sampleDataIdentifier)
        XCTAssertEqual("SAMPLE_TYPE_HEART_RATE", h10Fields.sampleType)
        XCTAssertEqual(1, h10Fields.recordingIntervalSeconds)
        XCTAssertTrue(PolarCommandRuntimePlanner.syncStopNotificationCompleted(id: "sync-stop-success"))
        XCTAssertEqual([
            Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue,
            Protocol_PbPFtpHostToDevNotification.startSync.rawValue
        ], PolarCommandRuntimePlanner.syncStartNotifications(id: "sync-start-success"))
        XCTAssertEqual([
            Protocol_PbPFtpHostToDevNotification.stopSync.rawValue,
            Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue
        ], PolarCommandRuntimePlanner.syncStopNotifications(id: "sync-stop-success"))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func test_diskTimeRuntimePlannerMapsSharedQueriesWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("success", PolarDiskTimeRuntimePlanner.query(id: "get-disk-space", query: "GET_DISK_SPACE"))
        XCTAssertEqual(Protocol_PbPFtpQuery.getDiskSpace.rawValue, PolarDiskTimeRuntimePlanner.queryValue(id: "get-disk-space", query: "GET_DISK_SPACE"))
        XCTAssertEqual("success", PolarDiskTimeRuntimePlanner.setLocalTimeV2(systemTimeHour: 12, localTimeHour: 14))
        XCTAssertEqual([
            Protocol_PbPFtpQuery.setSystemTime.rawValue,
            Protocol_PbPFtpQuery.setLocalTime.rawValue
        ], PolarDiskTimeRuntimePlanner.setLocalTimeV2QueryValues(systemTimeHour: 12, localTimeHour: 14))
        XCTAssertEqual("success", PolarDiskTimeRuntimePlanner.setLocalTimeH10(localTimeHour: 14))
        XCTAssertEqual([Protocol_PbPFtpQuery.setLocalTime.rawValue], PolarDiskTimeRuntimePlanner.setLocalTimeH10QueryValues(localTimeHour: 14))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func test_restFacadeRuntimePlannerMapsSharedGetOperationWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("success", PolarRestFacadeRuntimePlanner.get(id: "list-rest-api-services-success", path: "/REST/SERVICE.API", payloadShape: "service-list-json"))
        let operation = PolarRestFacadeRuntimePlanner.getOperation(id: "list-rest-api-services-success", path: "/REST/SERVICE.API", payloadShape: "service-list-json")
        XCTAssertEqual(.get, operation?.command)
        XCTAssertEqual("/REST/SERVICE.API", operation?.path)
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func test_userDeviceSettingsRuntimePlannerMapsSharedOperationsWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("success", PolarUserDeviceSettingsRuntimePlanner.plan(id: "set-user-device-settings", kind: "write", path: "/U/0/S/UDEVSET.BPB", payloadFields: ["protobufPayload=platform-built"]))
        let operations = PolarUserDeviceSettingsRuntimePlanner.operations(id: "set-telemetry-enabled", kind: "readThenWrite", path: "/U/0/S/UDEVSET.BPB", payloadFields: ["telemetryEnabled=true"])
        XCTAssertEqual([.get, .put], operations?.map { $0.command })
        XCTAssertEqual(["/U/0/S/UDEVSET.BPB", "/U/0/S/UDEVSET.BPB"], operations?.map { $0.path })
        XCTAssertEqual(DEVICE_SETTINGS_FILE_PATH, PolarUserDeviceSettingsRuntimePlanner.settingsPath(fileSystemType: "polarFileSystemV2", unknownSettingsPath: nil))
        XCTAssertEqual(SENSOR_SETTINGS_FILE_PATH, PolarUserDeviceSettingsRuntimePlanner.settingsPath(fileSystemType: "h10FileSystem", unknownSettingsPath: nil))
        XCTAssertNil(PolarUserDeviceSettingsRuntimePlanner.settingsPath(fileSystemType: "unknownFileSystem", unknownSettingsPath: nil))
        XCTAssertEqual(SENSOR_SETTINGS_FILE_PATH, PolarUserDeviceSettingsRuntimePlanner.settingsPath(fileSystemType: "unknownFileSystem", unknownSettingsPath: SENSOR_SETTINGS_FILE_PATH))
        XCTAssertEqual("WRIST_RIGHT", PolarUserDeviceSettingsRuntimePlanner.deviceLocationName(value: PbDeviceLocation.deviceLocationWristRight.rawValue))
        XCTAssertEqual("ON", PolarUserDeviceSettingsRuntimePlanner.usbConnectionModeName(enabled: true))
        XCTAssertEqual("OFF", PolarUserDeviceSettingsRuntimePlanner.automaticTrainingDetectionModeName(enabled: false))
        XCTAssertEqual(["protobufPayload=platform-built"], PolarUserDeviceSettingsRuntimePlanner.protobufPayloadFields())
        XCTAssertEqual(["telemetryEnabled=true"], PolarUserDeviceSettingsRuntimePlanner.telemetryPayloadFields(enabled: true))
        XCTAssertEqual(["deviceLocation=WRIST_RIGHT"], PolarUserDeviceSettingsRuntimePlanner.deviceLocationPayloadFields(value: PbDeviceLocation.deviceLocationWristRight.rawValue))
        XCTAssertEqual(["usbConnectionMode=ON"], PolarUserDeviceSettingsRuntimePlanner.usbConnectionModePayloadFields(enabled: true))
        XCTAssertEqual(["automaticTrainingDetectionMode=ON", "automaticTrainingDetectionSensitivity=77", "minimumTrainingDurationSeconds=300"], PolarUserDeviceSettingsRuntimePlanner.automaticTrainingDetectionPayloadFields(enabled: true, sensitivity: 77, minimumDurationSeconds: 300))
        XCTAssertEqual(["automaticOhrMeasurement=ALWAYS_ON"], PolarUserDeviceSettingsRuntimePlanner.automaticOhrPayloadFields(enabled: true))
        XCTAssertEqual(["daylightSaving.nextDaylightSavingTime=present", "daylightSaving.offset=nonzero"], PolarUserDeviceSettingsRuntimePlanner.daylightSavingPayloadFields())
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func test_firmwareBackupRuntimePlannerMapsSharedWorkflowAndOperationsWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual(["TCHUPDAT.BIN", "APPUPDAT.BIN", "BTUPDAT.BIN", "SYSUPDAT.IMG"], PolarFirmwareBackupRuntimePlanner.orderFirmwareFiles(["TCHUPDAT.BIN", "SYSUPDAT.IMG", "APPUPDAT.BIN", "BTUPDAT.BIN"]))
        XCTAssertEqual("success", PolarFirmwareBackupRuntimePlanner.firmwareWorkflow(id: "write-package-success-with-system-update-last", statuses: ["preparingDeviceForFwUpdate", "completed"], firmwareFiles: ["BTUPDAT.BIN", "SYSUPDAT.IMG"]))
        XCTAssertEqual("success", PolarFirmwareBackupRuntimePlanner.backupRestore(path: "/U/0/BACKUP.TXT", payloadHex: "0102"))
        let operation = PolarFirmwareBackupRuntimePlanner.backupRestoreOperation(path: "/U/0/BACKUP.TXT", payloadHex: "0102")
        XCTAssertEqual(.put, operation?.command)
        XCTAssertEqual("/U/0/BACKUP.TXT", operation?.path)
        XCTAssertEqual(["/U/0/S/PHYSDATA.BPB", "/U/0/S/UDEVSET.BPB", "/U/0/S/PREFS.BPB", "/U/0/USERID.BPB"], PolarFirmwareBackupRuntimePlanner.defaultBackupPaths())
        XCTAssertEqual("/U/0/S/UDEVSET.BPB", PolarFirmwareBackupRuntimePlanner.backupTraversalRootPath("/U/*/S/UDEVSET.BPB"))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func test_storedDataOfflineRuntimePlannerMapsSharedDecisionsWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("success", PolarStoredDataOfflineRuntimePlanner.storedDataCleanup(kind: "filterDirectoryEntries", rootPath: "/"))
        XCTAssertEqual(true, PolarStoredDataOfflineRuntimePlanner.storedDataEntryMatchesFilter(entry: "TRC10.BIN", includePrefixes: ["TRC"], includeSuffixes: [".BIN"]))
        XCTAssertEqual(false, PolarStoredDataOfflineRuntimePlanner.storedDataEntryMatchesFilter(entry: "TRC10.TXT", includePrefixes: ["TRC"], includeSuffixes: [".BIN"]))
        XCTAssertEqual(true, PolarStoredDataOfflineRuntimePlanner.shouldPruneStoredDataEmptyParents(dataType: PolarStoredDataType.StoredDataType.ACTIVITY.rawValue))
        XCTAssertEqual(true, PolarStoredDataOfflineRuntimePlanner.storedDataDateIsOnOrBefore(day: "20260531", cutoffDate: "20260531"))
        XCTAssertEqual(false, PolarStoredDataOfflineRuntimePlanner.storedDataDateIsOnOrBefore(day: "20260601", cutoffDate: "20260531"))
        XCTAssertEqual(["/U/0/20260530/ACT/", "/U/0/20260530/"], PolarStoredDataOfflineRuntimePlanner.storedDataEmptyParentDirectories(filePath: "/U/0/20260530/ACT/ACTIVITY.BPB", trailingSlash: true))
        XCTAssertEqual("platform-path-split", PolarStoredDataOfflineRuntimePlanner.storedDataCleanup(kind: "activityPrune", rootPath: "/U/0"))
        XCTAssertEqual("success", PolarStoredDataOfflineRuntimePlanner.storedDataCleanup(kind: "automaticSamplePrune", rootPath: "/U/0/AUTOS", cutoffDate: "20260531"))
        XCTAssertEqual("success", PolarStoredDataOfflineRuntimePlanner.offlineTriggerSet(currentTypes: ["acc"], desiredTypes: ["acc"], secretPresent: true))
        XCTAssertEqual("success", PolarStoredDataOfflineRuntimePlanner.offlineTriggerGet(currentTypes: ["acc"]))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func test_streamRuntimePlannerSurfacesSharedEdgeDecisionsWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("gattDisconnected", PolarStreamRuntimePlanner.subscription(target: "stream", startConnected: false, checkConnection: true))
        XCTAssertEqual("stream", PolarStreamRuntimePlanner.consumerCancellation(target: "stream"))
        XCTAssertEqual("linkLost", PolarStreamRuntimePlanner.disconnect(target: "stream", error: "linkLost"))
        XCTAssertEqual(1, PolarStreamRuntimePlanner.duplicateCompletion(target: "stream"))
        XCTAssertEqual(0, PolarStreamRuntimePlanner.postCompletionEmission(target: "stream", value: "value"))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    func test_deleteTelemetryData_listFailurePropagatesError() throws {
        try assertStoredDataCleanupWorkflowVectorContains("telemetry-list-failure-platform-policy")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7021, userInfo: [NSLocalizedDescriptionKey: "telemetry list failed"])
        v2MockClient.requestReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await v2Api.deleteTelemetryData(deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
    }

    func test_deleteStoredDeviceData_removesSDLogFilesFromFakeTransport() throws {
        try assertStoredDataCleanupWorkflowVectorContains("sdlogs-extension-filter")
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/SDLOGS/": { try self.makeDirectoryProtoData(entries: [("A.SLG", 4), ("B.TXT", 5), ("C.BPB", 6)]) },
            "/SDLOGS/A.SLG": { Data() },
            "/SDLOGS/B.TXT": { Data() }
        ])

        try awaitVoidAsync { [self] in try await v2Api.deleteStoredDeviceData(deviceId, dataType: .SDLOGS, until: Date()) }

        let operations = try v2MockClient.requestCalls.map { try Protocol_PbPFtpOperation(serializedBytes: $0) }
        XCTAssertEqual([.get, .remove, .remove], operations.map { $0.command })
        XCTAssertEqual(["/SDLOGS/", "/SDLOGS/A.SLG", "/SDLOGS/B.TXT"], operations.map { $0.path })
    }

    func test_deleteStoredDeviceData_sdLogListFailurePropagatesError() throws {
        try assertStoredDataCleanupWorkflowVectorContains("sdlogs-list-failure-platform-policy")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7022, userInfo: [NSLocalizedDescriptionKey: "sd log list failed"])
        v2MockClient.requestReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await v2Api.deleteStoredDeviceData(deviceId, dataType: .SDLOGS, until: Date()) }

        XCTAssertNotNil(error)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
    }

    func test_deleteStoredDeviceData_removesActivityFilesBeforeCutoffAndPrunesEmptyParents() throws {
        try assertStoredDataCleanupWorkflowVectorContains("activity-prune-empty-parents")
        let until = try XCTUnwrap(Calendar.current.date(from: DateComponents(year: 2026, month: 5, day: 31)))
        var getCountsByPath: [String: Int] = [:]
        v2MockClient.requestReturnValueClosure = { headerData in
            let operation = try Protocol_PbPFtpOperation(serializedBytes: headerData)
            let getCount = getCountsByPath[operation.path, default: 0]
            if operation.command == .get { getCountsByPath[operation.path] = getCount + 1 }
            if operation.command == .remove { return Data() }
            switch operation.path {
            case "/U/0/":
                return try self.makeDirectoryProtoData(entries: [("20260530/", 0)])
            case "/U/0/20260530/":
                if getCount == 0 { return try self.makeDirectoryProtoData(entries: [("ACT/", 0)]) }
                return try self.makeDirectoryProtoData(entries: [])
            case "/U/0/20260530/ACT/":
                if getCount == 0 { return try self.makeDirectoryProtoData(entries: [("ACTIVITY.BPB", 8), ("HIST.BPB", 8)]) }
                return try self.makeDirectoryProtoData(entries: [])
            default:
                throw NSError(domain: "test.unrouted", code: 0, userInfo: [NSLocalizedDescriptionKey: "Unrouted: \(operation.path)"])
            }
        }

        try awaitVoidAsync { [self] in try await v2Api.deleteStoredDeviceData(deviceId, dataType: .ACTIVITY, until: until) }

        let operations = try v2MockClient.requestCalls.map { try Protocol_PbPFtpOperation(serializedBytes: $0) }
        XCTAssertEqual([.get, .get, .get, .remove, .get, .remove, .get, .remove], operations.map { $0.command })
        XCTAssertEqual(["/U/0/", "/U/0/20260530/", "/U/0/20260530/ACT/", "/U/0/20260530/ACT/ACTIVITY.BPB", "/U/0/20260530/ACT/", "/U/0/20260530/ACT/", "/U/0/20260530/", "/U/0/20260530/"], operations.map { $0.path })
    }

    func test_deleteStoredDeviceData_removesAutomaticSampleFilesByEmbeddedSampleDate() throws {
        try assertStoredDataCleanupWorkflowVectorContains("automatic-sample-embedded-day-filter")
        var utcCalendar = Calendar(identifier: .gregorian)
        utcCalendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let until = try XCTUnwrap(utcCalendar.date(from: DateComponents(timeZone: TimeZone(secondsFromGMT: 0), year: 2026, month: 5, day: 31, hour: 12)))
        v2MockClient.requestReturnValueClosure = { headerData in
            let operation = try Protocol_PbPFtpOperation(serializedBytes: headerData)
            if operation.command == .remove { return Data() }
            switch operation.path {
            case "/U/0/AUTOS/":
                return try self.makeDirectoryProtoData(entries: [("20260530/", 0), ("20260601/", 0)])
            case "/U/0/AUTOS/20260530/":
                return try self.makeDirectoryProtoData(entries: [("AUTOS001.BPB", 8)])
            case "/U/0/AUTOS/20260601/":
                return try self.makeDirectoryProtoData(entries: [("AUTOS002.BPB", 8)])
            case "/U/0/AUTOS/20260530/AUTOS001.BPB":
                return try Data_PbAutomaticSampleSessions.with {
                    $0.day = PbDate.with { $0.year = 2026; $0.month = 5; $0.day = 30 }
                }.serializedData()
            case "/U/0/AUTOS/20260601/AUTOS002.BPB":
                return try Data_PbAutomaticSampleSessions.with {
                    $0.day = PbDate.with { $0.year = 2026; $0.month = 6; $0.day = 1 }
                }.serializedData()
            default:
                throw NSError(domain: "test.unrouted", code: 0, userInfo: [NSLocalizedDescriptionKey: "Unrouted: \(operation.path)"])
            }
        }

        try awaitVoidAsync { [self] in try await v2Api.deleteStoredDeviceData(deviceId, dataType: .AUTO_SAMPLE, until: until) }

        let operations = try v2MockClient.requestCalls.map { try Protocol_PbPFtpOperation(serializedBytes: $0) }
        XCTAssertEqual([.get, .get, .get, .get, .remove, .get], operations.map { $0.command })
        XCTAssertEqual(["/U/0/AUTOS/", "/U/0/AUTOS/20260530/", "/U/0/AUTOS/20260601/", "/U/0/AUTOS/20260530/AUTOS001.BPB", "/U/0/AUTOS/20260530/AUTOS001.BPB", "/U/0/AUTOS/20260601/AUTOS002.BPB"], operations.map { $0.path })
    }

    func test_getFileList_listsLowLevelDirectoryWithoutRecursion() throws {
        let directory = Protocol_PbPFtpDirectory.with {
            $0.entries = [
                Protocol_PbPFtpEntry.with { $0.name = "A.BIN"; $0.size = 2 },
                Protocol_PbPFtpEntry.with { $0.name = "DIR/"; $0.size = 0 }
            ]
        }
        v2MockClient.requestReturnValue = .success(try directory.serializedData())

        let result = try awaitSingleAsync { [self] in try await v2Api.getFileList(identifier: deviceId, directoryPath: "U/0", recurseDeep: false) }

        XCTAssertEqual(["/U/0/A.BIN", "/U/0/DIR/"], result)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/U/0/", requestOperation.path)
    }

    // MARK: - isFtuDone

    func test_isFtuDone_readsUserIdAndReturnsTrueWhenMasterIdentifierIsPresent() throws {
        var userIdentifier = Data_PbUserIdentifier()
        userIdentifier.masterIdentifier = UInt64.max
        v2MockClient.requestReturnValue = .success(try userIdentifier.serializedData())

        let result = try awaitSingleAsync { [self] in try await v2Api.isFtuDone(deviceId) }

        XCTAssertTrue(result)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(UserIdentifierType.USER_IDENTIFIER_FILENAME, requestOperation.path)
    }

    func test_isFtuDone_readsUserIdAndReturnsFalseWhenMasterIdentifierIsAbsent() throws {
        v2MockClient.requestReturnValue = .success(try Data_PbUserIdentifier().serializedData())

        let result = try awaitSingleAsync { [self] in try await v2Api.isFtuDone(deviceId) }

        XCTAssertFalse(result)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(UserIdentifierType.USER_IDENTIFIER_FILENAME, requestOperation.path)
    }

    func test_isFtuDone_requestError_propagatesError() throws {
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7013, userInfo: [NSLocalizedDescriptionKey: "user id read failed"])
        v2MockClient.requestReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await v2Api.isFtuDone(deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual(UserIdentifierType.USER_IDENTIFIER_FILENAME, requestOperation.path)
    }

    // MARK: - REST service requests

    func test_listRestApiServices_requestsServiceApiAndDecodesServicePaths() throws {
        try assertRestFacadeRuntimePolicyVectorContains("list-rest-api-services-success")
        v2MockClient.requestReturnValue = .success(Data(#"{"services":{"sleep":"/REST/SLEEP.API","training":"/REST/TRAINING.API"}}"#.utf8))

        let result = try awaitSingleAsync { [self] in try await v2Api.listRestApiServices(identifier: deviceId) }

        XCTAssertEqual(Set(result.serviceNames), Set(["sleep", "training"]))
        XCTAssertEqual(result.pathsForServices?["sleep"], "/REST/SLEEP.API")
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/REST/SERVICE.API", requestOperation.path)
    }

    func test_getRestApiDescription_requestsPathAndDecodesDescription() throws {
        try assertRestFacadeRuntimePolicyVectorContains("get-rest-api-description-success")
        v2MockClient.requestReturnValue = .success(Data(#"{"events":["sleep"],"endpoints":["stop"],"cmd":{"post":"/REST/SLEEP.API?cmd=post"},"sleep":{"details":["state"],"triggers":["change"]}}"#.utf8))

        let result = try awaitSingleAsync { [self] in try await v2Api.getRestApiDescription(identifier: deviceId, path: "/REST/SLEEP.API") }

        XCTAssertEqual(result.events, ["sleep"])
        XCTAssertEqual(result.actionPaths, ["/REST/SLEEP.API?cmd=post"])
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/REST/SLEEP.API", requestOperation.path)
    }

    func test_getRestApiDescription_requestError_propagatesError() throws {
        try assertRestFacadeRuntimePolicyVectorContains("get-rest-api-description-request-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7025, userInfo: [NSLocalizedDescriptionKey: "service description read failed"])
        v2MockClient.requestReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await v2Api.getRestApiDescription(identifier: deviceId, path: "/REST/SLEEP.API") }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/REST/SLEEP.API", requestOperation.path)
    }

    func test_getRestApiDescription_responseError_preservesPftpErrorCode() throws {
        try assertRestFacadeRuntimePolicyVectorContains("get-rest-api-description-response-error")
        v2MockClient.requestReturnValue = .failure(BlePsFtpException.responseError(errorCode: Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue))

        let error = awaitErrorAsync { [self] in try await v2Api.getRestApiDescription(identifier: deviceId, path: "/REST/SLEEP.API") }

        guard case .responseError(errorCode: let errorCode) = try XCTUnwrap(error as? BlePsFtpException) else {
            return XCTFail("Expected BlePsFtpException.responseError(noSuchFileOrDirectory), got \(String(describing: error))")
        }
        XCTAssertEqual(errorCode, Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/REST/SLEEP.API", requestOperation.path)
    }

    func test_getRestApiDescription_emptySuccessfulResponse_failsDecoding() throws {
        try assertRestFacadeRuntimePolicyVectorContains("get-rest-api-description-empty-success")
        v2MockClient.requestReturnValue = .success(Data())

        let error = awaitErrorAsync { [self] in try await v2Api.getRestApiDescription(identifier: deviceId, path: "/REST/SLEEP.API") }

        XCTAssertNotNil(error)
        if case DecodingError.dataCorrupted = try XCTUnwrap(error) {
            // expected
        } else {
            XCTFail("Expected DecodingError.dataCorrupted, got \(String(describing: error))")
        }
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/REST/SLEEP.API", requestOperation.path)
    }

    func test_getRestApiDescription_malformedSuccessfulResponse_failsDecoding() throws {
        try assertRestFacadeRuntimePolicyVectorContains("get-rest-api-description-malformed-success")
        v2MockClient.requestReturnValue = .success(Data("{".utf8))

        let error = awaitErrorAsync { [self] in try await v2Api.getRestApiDescription(identifier: deviceId, path: "/REST/SLEEP.API") }

        XCTAssertNotNil(error)
        if case DecodingError.dataCorrupted = try XCTUnwrap(error) {
            // expected
        } else {
            XCTFail("Expected DecodingError.dataCorrupted, got \(String(describing: error))")
        }
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/REST/SLEEP.API", requestOperation.path)
    }

    func test_listRestApiServices_requestError_propagatesError() throws {
        try assertRestFacadeRuntimePolicyVectorContains("list-rest-api-services-request-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7014, userInfo: [NSLocalizedDescriptionKey: "service api read failed"])
        v2MockClient.requestReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await v2Api.listRestApiServices(identifier: deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/REST/SERVICE.API", requestOperation.path)
    }

    func test_listRestApiServices_responseError_preservesPftpErrorCode() throws {
        try assertRestFacadeRuntimePolicyVectorContains("list-rest-api-services-response-error")
        v2MockClient.requestReturnValue = .failure(BlePsFtpException.responseError(errorCode: Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue))

        let error = awaitErrorAsync { [self] in try await v2Api.listRestApiServices(identifier: deviceId) }

        guard case .responseError(errorCode: let errorCode) = try XCTUnwrap(error as? BlePsFtpException) else {
            return XCTFail("Expected BlePsFtpException.responseError(noSuchFileOrDirectory), got \(String(describing: error))")
        }
        XCTAssertEqual(errorCode, Protocol_PbPFtpError.noSuchFileOrDirectory.rawValue)
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/REST/SERVICE.API", requestOperation.path)
    }

    func test_listRestApiServices_emptySuccessfulResponse_failsDecoding() throws {
        try assertRestFacadeRuntimePolicyVectorContains("list-rest-api-services-empty-success")
        v2MockClient.requestReturnValue = .success(Data())

        let error = awaitErrorAsync { [self] in try await v2Api.listRestApiServices(identifier: deviceId) }

        XCTAssertNotNil(error)
        if case DecodingError.dataCorrupted = try XCTUnwrap(error) {
            // expected
        } else {
            XCTFail("Expected DecodingError.dataCorrupted, got \(String(describing: error))")
        }
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/REST/SERVICE.API", requestOperation.path)
    }

    func test_listRestApiServices_malformedSuccessfulResponse_failsDecoding() throws {
        try assertRestFacadeRuntimePolicyVectorContains("list-rest-api-services-malformed-success")
        v2MockClient.requestReturnValue = .success(Data("{".utf8))

        let error = awaitErrorAsync { [self] in try await v2Api.listRestApiServices(identifier: deviceId) }

        XCTAssertNotNil(error)
        if case DecodingError.dataCorrupted = try XCTUnwrap(error) {
            // expected
        } else {
            XCTFail("Expected DecodingError.dataCorrupted, got \(String(describing: error))")
        }
        XCTAssertEqual(v2MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: v2MockClient.requestCalls[0])
        XCTAssertEqual(.get, requestOperation.command)
        XCTAssertEqual("/REST/SERVICE.API", requestOperation.path)
    }

    // MARK: - reset notifications

    func test_doFactoryReset_sendsResetNotification() throws {
        try assertCommandRuntimePolicyVectorContains("factory-reset")
        try awaitVoidAsync { [self] in try await v2Api.doFactoryReset(deviceId) }

        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 1)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.reset.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        let params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls[0].parameters) as Data)
        XCTAssertFalse(params.sleep)
        XCTAssertTrue(params.doFactoryDefaults)
        XCTAssertFalse(params.otaFwupdate)
    }

    func test_doFactoryResetWithPreservePairing_sendsOtaFirmwareUpdateFlag() throws {
        try assertCommandRuntimePolicyVectorContains("factory-reset-preserve-pairing")
        try awaitVoidAsync { [self] in try await v2Api.doFactoryReset(deviceId, preservePairingInformation: true) }

        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 1)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.reset.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        let params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls[0].parameters) as Data)
        XCTAssertFalse(params.sleep)
        XCTAssertTrue(params.doFactoryDefaults)
        XCTAssertTrue(params.otaFwupdate)
    }

    func test_doRestart_sendsResetNotificationWithoutFactoryDefaults() throws {
        try assertCommandRuntimePolicyVectorContains("restart")
        try awaitVoidAsync { [self] in try await v2Api.doRestart(deviceId) }

        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 1)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.reset.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        let params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls[0].parameters) as Data)
        XCTAssertFalse(params.sleep)
        XCTAssertFalse(params.doFactoryDefaults)
        XCTAssertFalse(params.otaFwupdate)
    }

    func test_setWarehouseSleep_sendsResetNotificationWithSleepAndFactoryDefaults() throws {
        try assertCommandRuntimePolicyVectorContains("warehouse-sleep")
        try awaitVoidAsync { [self] in try await v2Api.setWarehouseSleep(deviceId) }

        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 1)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.reset.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        let params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls[0].parameters) as Data)
        XCTAssertTrue(params.sleep)
        XCTAssertTrue(params.doFactoryDefaults)
        XCTAssertFalse(params.otaFwupdate)
    }

    func test_turnDeviceOff_sendsResetNotificationWithSleepOnly() throws {
        try assertCommandRuntimePolicyVectorContains("turn-device-off")
        try awaitVoidAsync { [self] in try await v2Api.turnDeviceOff(deviceId) }

        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 1)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.reset.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        let params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls[0].parameters) as Data)
        XCTAssertTrue(params.sleep)
        XCTAssertFalse(params.doFactoryDefaults)
        XCTAssertFalse(params.otaFwupdate)
    }

    func test_doFactoryReset_notificationError_propagatesError() throws {
        try assertCommandRuntimePolicyVectorContains("factory-reset-notification-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7011, userInfo: [NSLocalizedDescriptionKey: "reset notification failed"])
        v2MockClient.sendNotificationError = transportError

        let error = awaitErrorAsync { [self] in try await v2Api.doFactoryReset(deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 1)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.reset.rawValue, v2MockClient.sendNotificationCalls[0].notification)
    }

    func test_doFactoryResetWithPreservePairing_notificationError_propagatesError() throws {
        try assertCommandRuntimePolicyVectorContains("factory-reset-preserve-pairing-notification-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7021, userInfo: [NSLocalizedDescriptionKey: "preserve pairing reset notification failed"])
        v2MockClient.sendNotificationError = transportError

        let error = awaitErrorAsync { [self] in try await v2Api.doFactoryReset(deviceId, preservePairingInformation: true) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 1)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.reset.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        let params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls[0].parameters) as Data)
        XCTAssertTrue(params.otaFwupdate)
    }

    func test_doRestart_notificationError_propagatesError() throws {
        try assertCommandRuntimePolicyVectorContains("restart-notification-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7022, userInfo: [NSLocalizedDescriptionKey: "restart notification failed"])
        v2MockClient.sendNotificationError = transportError

        let error = awaitErrorAsync { [self] in try await v2Api.doRestart(deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 1)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.reset.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        let params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls[0].parameters) as Data)
        XCTAssertFalse(params.doFactoryDefaults)
    }

    func test_setWarehouseSleep_notificationError_propagatesError() throws {
        try assertCommandRuntimePolicyVectorContains("warehouse-sleep-notification-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7023, userInfo: [NSLocalizedDescriptionKey: "warehouse sleep notification failed"])
        v2MockClient.sendNotificationError = transportError

        let error = awaitErrorAsync { [self] in try await v2Api.setWarehouseSleep(deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 1)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.reset.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        let params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls[0].parameters) as Data)
        XCTAssertTrue(params.sleep)
        XCTAssertTrue(params.doFactoryDefaults)
    }

    func test_turnDeviceOff_notificationError_propagatesError() throws {
        try assertCommandRuntimePolicyVectorContains("turn-device-off-notification-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7024, userInfo: [NSLocalizedDescriptionKey: "turn device off notification failed"])
        v2MockClient.sendNotificationError = transportError

        let error = awaitErrorAsync { [self] in try await v2Api.turnDeviceOff(deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 1)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.reset.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        let params = try Protocol_PbPFtpFactoryResetParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls[0].parameters) as Data)
        XCTAssertTrue(params.sleep)
        XCTAssertFalse(params.doFactoryDefaults)
    }

    // MARK: - sync notifications

    func test_sendInitializationAndStartSyncNotifications_requestsSyncThenSendsInitializeAndStartSync() throws {
        try assertCommandRuntimePolicyVectorContains("sync-start-success")
        v2MockClient.queryReturnValue = .success(Data())

        try awaitVoidAsync { [self] in try await v2Api.sendInitializationAndStartSyncNotifications(identifier: deviceId) }

        XCTAssertEqual(v2MockClient.queryCalls.count, 1)
        XCTAssertEqual(Protocol_PbPFtpQuery.requestSynchronization.rawValue, v2MockClient.queryCalls[0].id)
        XCTAssertNil(v2MockClient.queryCalls[0].parameters)
        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 2)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        XCTAssertNil(v2MockClient.sendNotificationCalls[0].parameters)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.startSync.rawValue, v2MockClient.sendNotificationCalls[1].notification)
        XCTAssertNil(v2MockClient.sendNotificationCalls[1].parameters)
    }

    func test_sendInitializationAndStartSyncNotifications_queryError_propagatesErrorWithoutNotifications() throws {
        try assertCommandRuntimePolicyVectorContains("sync-start-query-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7019, userInfo: [NSLocalizedDescriptionKey: "sync request failed"])
        v2MockClient.queryReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await v2Api.sendInitializationAndStartSyncNotifications(identifier: deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.queryCalls.count, 1)
        XCTAssertEqual(Protocol_PbPFtpQuery.requestSynchronization.rawValue, v2MockClient.queryCalls[0].id)
        XCTAssertTrue(v2MockClient.sendNotificationCalls.isEmpty)
    }

    func test_sendTerminateAndStopSyncNotifications_sendsCompletedStopSyncThenTerminateSession() throws {
        try assertCommandRuntimePolicyVectorContains("sync-stop-success")
        try awaitVoidAsync { [self] in try await v2Api.sendTerminateAndStopSyncNotifications(identifier: deviceId) }

        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 2)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.stopSync.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        let stopSyncParams = try Protocol_PbPFtpStopSyncParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls[0].parameters) as Data)
        XCTAssertTrue(stopSyncParams.completed)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue, v2MockClient.sendNotificationCalls[1].notification)
        XCTAssertNil(v2MockClient.sendNotificationCalls[1].parameters)
    }

    func test_sendTerminateAndStopSyncNotifications_notificationError_propagatesError() throws {
        try assertCommandRuntimePolicyVectorContains("sync-stop-notification-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7020, userInfo: [NSLocalizedDescriptionKey: "stop sync failed"])
        v2MockClient.sendNotificationError = transportError

        let error = awaitErrorAsync { [self] in try await v2Api.sendTerminateAndStopSyncNotifications(identifier: deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(v2MockClient.sendNotificationCalls.count, 1)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.stopSync.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        let stopSyncParams = try? Protocol_PbPFtpStopSyncParams(serializedBytes: v2MockClient.sendNotificationCalls[0].parameters as? Data ?? Data())
        XCTAssertTrue(stopSyncParams?.completed == true)
    }

    func test_sendTerminateSessionNotification_usesSharedSyncStopTerminateNotification() throws {
        try assertCommandRuntimePolicyVectorContains("sync-stop-success")
        let plannedNotifications = try XCTUnwrap(PolarRuntimePlanner.commandSyncStopNotifications(id: "sync-stop-success"))

        try awaitVoidAsync { [self] in try await v2Api.sendTerminateSessionNotification(identifier: deviceId) }

        XCTAssertEqual(1, v2MockClient.sendNotificationCalls.count)
        XCTAssertEqual(plannedNotifications.last, v2MockClient.sendNotificationCalls[0].notification)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        XCTAssertNil(v2MockClient.sendNotificationCalls[0].parameters)
    }

    func test_sendStopSyncNotification_usesSharedSyncStopNotificationWithCompletedParams() throws {
        try assertCommandRuntimePolicyVectorContains("sync-stop-success")
        let plannedNotifications = try XCTUnwrap(PolarRuntimePlanner.commandSyncStopNotifications(id: "sync-stop-success"))

        try awaitVoidAsync { [self] in try await v2Api.sendStopSyncNotification(identifier: deviceId) }

        XCTAssertEqual(1, v2MockClient.sendNotificationCalls.count)
        XCTAssertEqual(plannedNotifications.first, v2MockClient.sendNotificationCalls[0].notification)
        XCTAssertEqual(Protocol_PbPFtpHostToDevNotification.stopSync.rawValue, v2MockClient.sendNotificationCalls[0].notification)
        let stopSyncParams = try Protocol_PbPFtpStopSyncParams(serializedBytes: try XCTUnwrap(v2MockClient.sendNotificationCalls[0].parameters) as Data)
        XCTAssertTrue(stopSyncParams.completed)
    }

    // MARK: - multi-BLE connection mode

    func test_setMultiBLEConnectionMode_sendsConfigureCommandWithEnableValue() throws {
        let gatt = MockPolarGattServiceTransmitter()
        let pfcClient = BlePfcClient(gattServiceTransmitter: gatt)
        pfcClient.notifyDescriptorWritten(BlePfcClient.PFC_CP, enabled: true, err: 0)
        pfcClient.processServiceData(BlePfcClient.PFC_CP, data: Data([0xF0, UInt8(BlePfcClient.PfcMessage.pfcConfigureMultiConnection.rawValue), 0x01]), err: 0)
        let session = MockBleDeviceSession(mockFtpClient: v2MockClient, mockPfcClient: pfcClient)
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: session)

        try awaitVoidAsync { try await api.setMultiBLEConnectionMode(identifier: self.deviceId, enable: true) }

        XCTAssertEqual(gatt.transmittedMessages.count, 1)
        XCTAssertEqual(BlePfcClient.PFC_SERVICE, gatt.transmittedMessages[0].serviceUuid)
        XCTAssertEqual(BlePfcClient.PFC_CP, gatt.transmittedMessages[0].characteristicUuid)
        XCTAssertEqual(Data([UInt8(BlePfcClient.PfcMessage.pfcConfigureMultiConnection.rawValue), 0x01]), gatt.transmittedMessages[0].packet)
        XCTAssertTrue(gatt.transmittedMessages[0].withResponse)
    }

    func test_setMultiBLEConnectionMode_nonSuccessResponseReturnsOperationNotSupported() {
        let gatt = MockPolarGattServiceTransmitter()
        let pfcClient = BlePfcClient(gattServiceTransmitter: gatt)
        pfcClient.notifyDescriptorWritten(BlePfcClient.PFC_CP, enabled: true, err: 0)
        pfcClient.processServiceData(BlePfcClient.PFC_CP, data: Data([0xF0, UInt8(BlePfcClient.PfcMessage.pfcConfigureMultiConnection.rawValue), 0x02]), err: 0)
        let session = MockBleDeviceSession(mockFtpClient: v2MockClient, mockPfcClient: pfcClient)
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: session)

        let error = awaitErrorAsync { try await api.setMultiBLEConnectionMode(identifier: self.deviceId, enable: false) }

        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
        XCTAssertEqual(Data([UInt8(BlePfcClient.PfcMessage.pfcConfigureMultiConnection.rawValue), 0x00]), gatt.transmittedMessages.first?.packet)
    }

    func test_getMultiBLEConnectionMode_sendsRequestCommandAndMapsEnabledPayload() throws {
        let gatt = MockPolarGattServiceTransmitter()
        let pfcClient = BlePfcClient(gattServiceTransmitter: gatt)
        pfcClient.notifyDescriptorWritten(BlePfcClient.PFC_CP, enabled: true, err: 0)
        pfcClient.processServiceData(BlePfcClient.PFC_CP, data: Data([0xF0, UInt8(BlePfcClient.PfcMessage.pfcRequestMultiConnectionSetting.rawValue), 0x01, 0x01]), err: 0)
        let session = MockBleDeviceSession(mockFtpClient: v2MockClient, mockPfcClient: pfcClient)
        let api = PolarBleApiImplWithMockSession(mockDeviceSession: session)

        let result = try awaitSingleAsync { try await api.getMultiBLEConnectionMode(identifier: self.deviceId) }

        XCTAssertTrue(result)
        XCTAssertEqual(gatt.transmittedMessages.count, 1)
        XCTAssertEqual(Data([UInt8(BlePfcClient.PfcMessage.pfcRequestMultiConnectionSetting.rawValue)]), gatt.transmittedMessages[0].packet)
    }

    // MARK: - offline trigger facade

    func test_setOfflineRecordingTrigger_mapsPublicTriggerAndSecretToPmdControlPointSequence() throws {
        try assertOfflineTriggerRuntimePolicyVectorContains("set-trigger-success-with-secret")
        let (api, gatt) = makePmdApi { [self] packet in
            switch packet.first {
            case 0x08:
                return pmdResponse(opCode: 0x08)
            case 0x07:
                return pmdResponse(opCode: 0x07, parameters: offlineTriggerStatusData())
            case 0x09:
                return pmdResponse(opCode: 0x09, measurementType: packet[2])
            default:
                return pmdResponse(opCode: packet.first ?? 0x00, errorCode: 0x01)
            }
        }
        let trigger = PolarOfflineRecordingTrigger(
            triggerMode: .triggerSystemStart,
            triggerFeatures: [
                .acc: try PolarSensorSetting([.sampleRate: 52, .resolution: 16]),
                .hr: nil
            ]
        )
        let secretBytes = Data((0..<16).map { UInt8($0) })
        let secret = try PolarRecordingSecret(key: secretBytes)

        try awaitVoidAsync { try await api.setOfflineRecordingTrigger(self.deviceId, trigger: trigger, secret: secret) }

        let packets = gatt.transmittedMessages.map { $0.packet }
        XCTAssertEqual(Data([0x08, 0x01]), packets.first)
        XCTAssertEqual(Data([0x07]), packets.dropFirst().first)
        let settingPackets = Array(packets.dropFirst(2))
        XCTAssertEqual(3, settingPackets.count)
        let accPacket = try XCTUnwrap(settingPackets.first { $0.starts(with: Data([0x09, 0x01, PmdMeasurementType.acc.rawValue])) })
        let gyroPacket = try XCTUnwrap(settingPackets.first { $0 == Data([0x09, 0x00, PmdMeasurementType.gyro.rawValue]) })
        let hrPacket = try XCTUnwrap(settingPackets.first { $0.starts(with: Data([0x09, 0x01, PmdMeasurementType.offline_hr.rawValue])) })
        XCTAssertEqual(Data([0x09, 0x00, PmdMeasurementType.gyro.rawValue]), gyroPacket)
        XCTAssertNotNil(accPacket.range(of: Data([0x00, 0x01, 0x34, 0x00])))
        XCTAssertNotNil(accPacket.range(of: Data([0x01, 0x01, 0x10, 0x00])))
        let secretSetting = Data([0x06, 0x01, 0x02]) + secretBytes
        XCTAssertNotNil(accPacket.range(of: secretSetting))
        XCTAssertNotNil(hrPacket.range(of: secretSetting))
    }

    func test_setOfflineRecordingTrigger_setModeErrorPropagatesAndStopsBeforeStatusRead() throws {
        try assertOfflineTriggerRuntimePolicyVectorContains("set-trigger-mode-error")
        let (api, gatt) = makePmdApi { [self] packet in
            return pmdResponse(opCode: packet.first ?? 0x00, errorCode: packet.first == 0x08 ? 0x05 : 0x00)
        }
        let trigger = PolarOfflineRecordingTrigger(triggerMode: .triggerSystemStart, triggerFeatures: [.hr: nil])

        let error = awaitErrorAsync { try await api.setOfflineRecordingTrigger(self.deviceId, trigger: trigger, secret: nil) }

        XCTAssertNotNil(error)
        guard case BlePmdError.controlPointRequestFailed(let errorCode, _) = error! else {
            return XCTFail("Expected controlPointRequestFailed, got \(String(describing: error))")
        }
        XCTAssertEqual(0x05, errorCode)
        XCTAssertEqual([Data([0x08, 0x01])], gatt.transmittedMessages.map { $0.packet })
    }

    func test_getOfflineRecordingTriggerSetup_mapsPmdStatusToPublicTriggerAndPropagatesErrors() throws {
        try assertOfflineTriggerRuntimePolicyVectorContains("get-trigger-success")
        try assertOfflineTriggerRuntimePolicyVectorContains("get-trigger-transport-error")
        let statusData = Data([
            0x01,
            0x01, PmdMeasurementType.acc.rawValue, 0x04, 0x00, 0x01, 0x34, 0x00,
            0x00, PmdMeasurementType.gyro.rawValue,
            0x01, PmdMeasurementType.offline_hr.rawValue, 0x00
        ])
        let (successApi, successGatt) = makePmdApi { [self] packet in
            pmdResponse(opCode: packet.first ?? 0x00, parameters: statusData)
        }

        let result = try awaitSingleAsync { try await successApi.getOfflineRecordingTriggerSetup(self.deviceId) }

        XCTAssertEqual(.triggerSystemStart, result.triggerMode)
        XCTAssertEqual(Set([UInt32(52)]), result.triggerFeatures[.acc]??.settings[.sampleRate])
        XCTAssertTrue(result.triggerFeatures.keys.contains(.hr))
        XCTAssertFalse(result.triggerFeatures.keys.contains(.gyro))
        XCTAssertEqual([Data([0x07])], successGatt.transmittedMessages.map { $0.packet })

        let (errorApi, errorGatt) = makePmdApi { [self] packet in
            pmdResponse(opCode: packet.first ?? 0x00, errorCode: 0x03)
        }
        let error = awaitErrorAsync { try await errorApi.getOfflineRecordingTriggerSetup(self.deviceId) }

        XCTAssertNotNil(error)
        let errorDescription = String(describing: error!)
        XCTAssertTrue(errorDescription.contains("gattAttributeError"), errorDescription)
        XCTAssertTrue(errorDescription.contains("Not supported"), errorDescription)
        XCTAssertEqual([Data([0x07])], errorGatt.transmittedMessages.map { $0.packet })
    }

    // MARK: - startRecording

    func test_startRecording_emptyExerciseId_returnsInvalidArgument() {
        let error = awaitErrorAsync { [self] in try await h10Api.startRecording(deviceId, exerciseId: "", interval: .interval_1s, sampleType: .hr) }
        XCTAssertNotNil(error)
        if case PolarErrors.invalidArgument = error! { } else { XCTFail("Expected invalidArgument") }
    }

    func test_startRecording_exerciseIdTooLong_returnsInvalidArgument() {
        let error = awaitErrorAsync { [self] in try await h10Api.startRecording(deviceId, exerciseId: String(repeating: "a", count: 65), interval: .interval_1s, sampleType: .hr) }
        XCTAssertNotNil(error)
        if case PolarErrors.invalidArgument = error! { } else { XCTFail("Expected invalidArgument") }
    }

    func test_startRecording_h10Device_sendsRequestStartRecordingQuery() throws {
        try assertCommandRuntimePolicyVectorContains("h10-start-recording")
        h10MockClient.queryReturnValue = .success(Data())
        try awaitVoidAsync { [self] in try await h10Api.startRecording(deviceId, exerciseId: "myExercise", interval: .interval_1s, sampleType: .hr) }
        XCTAssertEqual(h10MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.requestStartRecording.rawValue)
        let paramsData = try XCTUnwrap(h10MockClient.queryCalls.first?.parameters)
        let params = try Protocol_PbPFtpRequestStartRecordingParams(serializedBytes: paramsData as Data)
        XCTAssertEqual("myExercise", params.sampleDataIdentifier)
        XCTAssertEqual(.sampleTypeHeartRate, params.sampleType)
        XCTAssertEqual(1, params.recordingInterval.seconds)
    }

    func test_startRecording_h10Device_queryError_propagatesError() throws {
        try assertCommandRuntimePolicyVectorContains("h10-start-recording-query-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7007, userInfo: [NSLocalizedDescriptionKey: "start recording failed"])
        h10MockClient.queryReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await h10Api.startRecording(deviceId, exerciseId: "myExercise", interval: .interval_1s, sampleType: .hr) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(h10MockClient.queryCalls.count, 1)
        XCTAssertEqual(h10MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.requestStartRecording.rawValue)
        XCTAssertNotNil(h10MockClient.queryCalls.first?.parameters)
    }

    func test_startRecording_nonRecordingDevice_returnsOperationNotSupported() {
        let error = awaitErrorAsync { [self] in try await v2Api.startRecording(deviceId, exerciseId: "myExercise", interval: .interval_1s, sampleType: .hr) }
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
    }

    // MARK: - stopRecording

    func test_stopRecording_h10Device_sendsRequestStopRecordingQuery() throws {
        try assertCommandRuntimePolicyVectorContains("h10-stop-recording")
        h10MockClient.queryReturnValue = .success(Data())
        try awaitVoidAsync { [self] in try await h10Api.stopRecording(deviceId) }
        XCTAssertEqual(h10MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.requestStopRecording.rawValue)
        XCTAssertNil(h10MockClient.queryCalls.first?.parameters)
    }

    func test_stopRecording_h10Device_queryError_propagatesError() throws {
        try assertCommandRuntimePolicyVectorContains("h10-stop-recording-query-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7008, userInfo: [NSLocalizedDescriptionKey: "stop recording failed"])
        h10MockClient.queryReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await h10Api.stopRecording(deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(h10MockClient.queryCalls.count, 1)
        XCTAssertEqual(h10MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.requestStopRecording.rawValue)
        XCTAssertNil(h10MockClient.queryCalls.first?.parameters)
    }

    func test_stopRecording_nonRecordingDevice_returnsOperationNotSupported() {
        let error = awaitErrorAsync { [self] in try await v2Api.stopRecording(deviceId) }
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
    }

    // MARK: - requestRecordingStatus

    func test_requestRecordingStatus_h10Device_returnsDecodedStatus() throws {
        try assertCommandRuntimePolicyVectorContains("h10-recording-status")
        var proto = Protocol_PbRequestRecordingStatusResult()
        proto.recordingOn = true; proto.sampleDataIdentifier = "exercise123"
        h10MockClient.queryReturnValue = .success(try proto.serializedData())
        let status = try awaitSingleAsync { [self] in try await h10Api.requestRecordingStatus(deviceId) }
        XCTAssertTrue(status.ongoing); XCTAssertEqual(status.entryId, "exercise123")
        XCTAssertEqual(h10MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.requestRecordingStatus.rawValue)
        XCTAssertNil(h10MockClient.queryCalls.first?.parameters)
    }

    func test_requestRecordingStatus_h10Device_queryError_propagatesError() throws {
        try assertCommandRuntimePolicyVectorContains("h10-recording-status-query-failure")
        let transportError = NSError(domain: "PolarBleApiImplTests", code: 7009, userInfo: [NSLocalizedDescriptionKey: "recording status failed"])
        h10MockClient.queryReturnValue = .failure(transportError)

        let error = awaitErrorAsync { [self] in try await h10Api.requestRecordingStatus(deviceId) }

        XCTAssertNotNil(error)
        XCTAssertEqual((error as NSError?)?.domain, transportError.domain)
        XCTAssertEqual((error as NSError?)?.code, transportError.code)
        XCTAssertEqual(h10MockClient.queryCalls.count, 1)
        XCTAssertEqual(h10MockClient.queryCalls.first?.id, Protocol_PbPFtpQuery.requestRecordingStatus.rawValue)
        XCTAssertNil(h10MockClient.queryCalls.first?.parameters)
    }

    func test_requestRecordingStatus_nonRecordingDevice_returnsOperationNotSupported() {
        let error = awaitErrorAsync { [self] in try await v2Api.requestRecordingStatus(deviceId) }
        XCTAssertNotNil(error)
        if case PolarErrors.operationNotSupported = error! { } else { XCTFail("Expected operationNotSupported") }
    }

    // MARK: - searchForDevice helpers

    private var searchApi: MockSearchBleApiImpl!

    private func makeSession(name: String, deviceType: String = "360", deviceIdUntouched: String = "12345678",
                             rssi: Int32 = -70, connectable: Bool = true) -> MockSearchBleDeviceSession {
        let adv = MockSearchAdvertisementContent()
        adv.mockName = name; adv.mockPolarDeviceType = deviceType
        adv.mockPolarDeviceIdUntouched = deviceIdUntouched
        adv.mockMedianRssi = rssi; adv.mockIsConnectable = connectable
        return MockSearchBleDeviceSession(advertisementContent: adv)
    }

    // MARK: - searchForDevice tests

    func test_searchForDevice_noPrefix_emitsAllSessions() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "two"); exp.expectedFulfillmentCount = 2
        searchApi.searchForDevice()
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 12345678"))
        searchApi.searchSubject.send(makeSession(name: "Garmin Device ABCD"))
        wait(for: [exp], timeout: 2); XCTAssertEqual(received.count, 2)
    }

    func test_searchForDevice_defaultPolarPrefix_filtersNonPolarDevices() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "one"); exp.expectedFulfillmentCount = 1
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: "Polar")
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 AAAABBBB"))
        searchApi.searchSubject.send(makeSession(name: "Garmin Device CCCC"))
        wait(for: [exp], timeout: 2)
        XCTAssertEqual(received.count, 1); XCTAssertEqual(received.first?.name, "Polar H10 AAAABBBB")
    }

    func test_searchForDevice_nonMatchingPrefix_emitsNothing() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: "Garmin")
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0) }).store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 12345678"))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(received.count, 0)
    }

    func test_searchForDevice_nilPrefix_emitsAllSessions() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "two"); exp.expectedFulfillmentCount = 2
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 12345678"))
        searchApi.searchSubject.send(makeSession(name: "Garmin Device ABCD"))
        wait(for: [exp], timeout: 2); XCTAssertEqual(received.count, 2)
    }

    func test_searchForDevice_distinct_deduplicatesSession() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "one")
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        let session = makeSession(name: "Polar H10 AAAABBBB")
        searchApi.searchSubject.send(session); searchApi.searchSubject.send(session)
        wait(for: [exp], timeout: 2); XCTAssertEqual(received.count, 1)
    }

    func test_searchForDevice_mapsPolarDeviceInfoCorrectly() throws {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "received")
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 AABBCCDD", deviceType: "h10",
                                                  deviceIdUntouched: "AABBCCDD", rssi: -55, connectable: true))
        wait(for: [exp], timeout: 2)
        let info = try XCTUnwrap(received.first)
        XCTAssertEqual(info.deviceId, "AABBCCDD"); XCTAssertEqual(info.rssi, -55)
        XCTAssertEqual(info.name, "Polar H10 AABBCCDD"); XCTAssertTrue(info.connectable)
    }

    func test_searchForDevice_hasSAGRFCFileSystem_trueForPolarFileSystemV2() throws {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "received")
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar 360 AABBCCDD", deviceType: "360"))
        wait(for: [exp], timeout: 2)
        XCTAssertTrue(try XCTUnwrap(received.first).hasSAGRFCFileSystem)
    }

    func test_searchForDevice_hasSAGRFCFileSystem_falseForH10FileSystem() throws {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "received")
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 AABBCCDD", deviceType: "h10"))
        wait(for: [exp], timeout: 2)
        XCTAssertFalse(try XCTUnwrap(received.first).hasSAGRFCFileSystem)
    }

    func test_searchForDevice_errorFromSource_propagatesError() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var receivedError: Error?
        let exp = XCTestExpectation(description: "error")
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { if case .failure(let e) = $0 { receivedError = e }; exp.fulfill() },
                  receiveValue: { _ in }).store(in: &cancellables)
        searchApi.searchSubject.send(completion: .failure(NSError(domain: "test", code: 99)))
        wait(for: [exp], timeout: 2); XCTAssertNotNil(receivedError)
    }

    func test_searchForDevice_completesWhenSubjectCompletes() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        searchApi.searchSubject.send(completion: .finished)
        wait(for: [exp], timeout: 2)
    }

    func test_searchForDevice_multipleMatchingSessions_emitsAll() {
        searchApi = MockSearchBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarDeviceInfo] = []
        let exp = XCTestExpectation(description: "three"); exp.expectedFulfillmentCount = 3
        searchApi.searchForDevice(withRequiredDeviceNamePrefix: "Polar")
            .sink(receiveCompletion: { _ in }, receiveValue: { received.append($0); exp.fulfill() })
            .store(in: &cancellables)
        searchApi.searchSubject.send(makeSession(name: "Polar H10 AAAA0001", deviceIdUntouched: "AAAA0001"))
        searchApi.searchSubject.send(makeSession(name: "Polar H9 AAAA0002",  deviceIdUntouched: "AAAA0002"))
        searchApi.searchSubject.send(makeSession(name: "Polar Sense AAAA0003", deviceIdUntouched: "AAAA0003"))
        wait(for: [exp], timeout: 2); XCTAssertEqual(received.count, 3)
    }

    // MARK: - startAutoConnectToDevice helpers

    private var autoConnectApi: MockAutoConnectBleApiImpl!

    private func makeAutoSession(rssi: Int32 = -70, connectable: Bool = true,
                                 deviceType: String = "h10", containsService: Bool = true) -> MockSearchBleDeviceSession {
        let adv = MockSearchAdvertisementContent()
        adv.mockMedianRssi = rssi; adv.mockIsConnectable = connectable
        adv.mockPolarDeviceType = deviceType; adv.mockContainsService = containsService
        return MockSearchBleDeviceSession(advertisementContent: adv)
    }

    // MARK: - startAutoConnectToDevice tests

    func test_startAutoConnectToDevice_matchingSession_completesAndOpensSession() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70))
        wait(for: [exp], timeout: 2); XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
    }

    func test_startAutoConnectToDevice_prefixesAfterFirstMatch() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70))
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -60))
        wait(for: [exp], timeout: 2); XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
    }

    func test_startAutoConnectToDevice_rssiTooLow_notOpened() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        autoConnectApi.startAutoConnectToDevice(-60, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(autoConnectApi.openedSessions.count, 0)
    }

    func test_startAutoConnectToDevice_rssiExactlyAtThreshold_isMatched() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-70, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70))
        wait(for: [exp], timeout: 2); XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
    }

    func test_startAutoConnectToDevice_notConnectable_notOpened() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, connectable: false))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(autoConnectApi.openedSessions.count, 0)
    }

    func test_startAutoConnectToDevice_polarDeviceTypeFilter_matchesCorrectType() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: "h10")
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, deviceType: "360"))
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, deviceType: "h10"))
        wait(for: [exp], timeout: 2)
        XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
        XCTAssertEqual(autoConnectApi.openedSessions.first?.advertisementContent.polarDeviceType, "h10")
    }

    func test_startAutoConnectToDevice_polarDeviceTypeNil_acceptsAnyType() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, deviceType: "360"))
        wait(for: [exp], timeout: 2); XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
    }

    func test_startAutoConnectToDevice_serviceFilter_matchesSessionContainingService() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: CBUUID(string: "180D"), polarDeviceType: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, containsService: true))
        wait(for: [exp], timeout: 2); XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
    }

    func test_startAutoConnectToDevice_serviceFilter_skipsSessionNotContainingService() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        autoConnectApi.startAutoConnectToDevice(-80, service: CBUUID(string: "180D"), polarDeviceType: nil)
            .sink(receiveCompletion: { _ in }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, containsService: false))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(autoConnectApi.openedSessions.count, 0)
    }

    func test_startAutoConnectToDevice_serviceNil_acceptsSessionWithAnyService() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -70, containsService: false))
        wait(for: [exp], timeout: 2); XCTAssertEqual(autoConnectApi.openedSessions.count, 1)
    }

    func test_startAutoConnectToDevice_errorFromSource_propagatesError() {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        var receivedError: Error?; let exp = XCTestExpectation(description: "error")
        autoConnectApi.startAutoConnectToDevice(-80, service: nil, polarDeviceType: nil)
            .sink(receiveCompletion: { if case .failure(let e) = $0 { receivedError = e }; exp.fulfill() },
                  receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(completion: .failure(NSError(domain: "test", code: 7)))
        wait(for: [exp], timeout: 2); XCTAssertNotNil(receivedError)
    }

    func test_startAutoConnectToDevice_allFiltersPass_opensCorrectSession() throws {
        autoConnectApi = MockAutoConnectBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        autoConnectApi.startAutoConnectToDevice(-80, service: CBUUID(string: "FEEE"), polarDeviceType: "360")
            .sink(receiveCompletion: { _ in exp.fulfill() }, receiveValue: { _ in }).store(in: &cancellables)
        autoConnectApi.searchSubject.send(makeAutoSession(rssi: -60, connectable: true, deviceType: "360", containsService: true))
        wait(for: [exp], timeout: 2)
        let opened = try XCTUnwrap(autoConnectApi.openedSessions.first)
        XCTAssertEqual(opened.advertisementContent.polarDeviceType, "360")
        XCTAssertEqual(opened.advertisementContent.medianRssi, -60)
    }

    // MARK: - disconnectFromDevice helpers

    private var disconnectApi: MockDisconnectBleApiImpl!

    private func makeDisconnectSession(state: BleDeviceSession.DeviceSessionState) -> MockSearchBleDeviceSession {
        let session = MockSearchBleDeviceSession(advertisementContent: MockSearchAdvertisementContent())
        session.state = state; return session
    }

    // MARK: - disconnectFromDevice tests

    func test_disconnectFromDevice_sessionOpen_callsCloseSession() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        let session = makeDisconnectSession(state: .sessionOpen)
        disconnectApi.disconnectServiceUtils.stubSession = session
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertEqual(disconnectApi.closeSessionDirectCalls.count, 1)
        XCTAssertTrue(disconnectApi.closeSessionDirectCalls.first === session)
    }

    func test_disconnectFromDevice_sessionOpening_callsCloseSession() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = makeDisconnectSession(state: .sessionOpening)
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertEqual(disconnectApi.closeSessionDirectCalls.count, 1)
    }

    func test_disconnectFromDevice_sessionOpenPark_callsCloseSession() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = makeDisconnectSession(state: .sessionOpenPark)
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertEqual(disconnectApi.closeSessionDirectCalls.count, 1)
    }

    func test_disconnectFromDevice_sessionClosed_doesNotCallCloseSession() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = makeDisconnectSession(state: .sessionClosed)
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertEqual(disconnectApi.closeSessionDirectCalls.count, 0)
    }

    func test_disconnectFromDevice_sessionClosing_doesNotCallCloseSession() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = makeDisconnectSession(state: .sessionClosing)
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertEqual(disconnectApi.closeSessionDirectCalls.count, 0)
    }

    func test_disconnectFromDevice_sessionNotFound_noCloseAndNoThrow() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = nil
        XCTAssertNoThrow(try disconnectApi.disconnectFromDevice(deviceId))
        XCTAssertEqual(disconnectApi.closeSessionDirectCalls.count, 0)
    }

    func test_disconnectFromDevice_fetchSessionThrows_propagatesError() {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.shouldThrow = true
        XCTAssertThrowsError(try disconnectApi.disconnectFromDevice(deviceId)) { error in
            if case PolarErrors.invalidArgument = error { } else { XCTFail("Expected invalidArgument") }
        }
    }

    func test_disconnectFromDevice_removesConnectSubscription() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = nil
        var cancelled = false
        disconnectApi.connectSubscriptions[deviceId] = AnyCancellable { cancelled = true }
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertNil(disconnectApi.connectSubscriptions[deviceId]); XCTAssertTrue(cancelled)
    }

    func test_disconnectFromDevice_doesNotRemoveOtherSubscriptions() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = nil
        let other = "BBBBBBBB"
        disconnectApi.connectSubscriptions[other] = AnyCancellable { }
        disconnectApi.connectSubscriptions[deviceId] = AnyCancellable { }
        try disconnectApi.disconnectFromDevice(deviceId)
        XCTAssertNil(disconnectApi.connectSubscriptions[deviceId])
        XCTAssertNotNil(disconnectApi.connectSubscriptions[other])
    }

    func test_disconnectFromDevice_noSubscriptionForDevice_doesNotCrash() throws {
        disconnectApi = MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession)
        disconnectApi.disconnectServiceUtils.stubSession = nil
        XCTAssertNoThrow(try disconnectApi.disconnectFromDevice(deviceId))
    }

    // MARK: - startListenForPolarHrBroadcasts helpers

    private var hrBroadcastApi: MockHrBroadcastBleApiImpl!

    private func makeHrSession(deviceIdUntouched: String = "AABBCCDD", deviceType: String = "h10",
                               hr: UInt8 = 72, batteryOk: Bool = false, rssi: Int32 = -65,
                               connectable: Bool = true, advFrameCounter: UInt8 = 1) -> MockSearchBleDeviceSession {
        let adv = MockSearchAdvertisementContent()
        adv.mockPolarDeviceIdUntouched = deviceIdUntouched; adv.mockPolarDeviceType = deviceType
        adv.mockIsConnectable = connectable; adv.mockName = "Polar H10 \(deviceIdUntouched)"
        adv.rssiFilter.processRssiValueUpdated(rssi)
        let byte0: UInt8 = (batteryOk ? 0x01 : 0x00) | ((advFrameCounter & 0x07) << 2)
        adv.polarHrAdvertisementData.processPolarManufacturerData(Data([byte0, 0x00, 0x00, hr]))
        return MockSearchBleDeviceSession(advertisementContent: adv)
    }

    // MARK: - startListenForPolarHrBroadcasts tests

    func test_startListenForPolarHrBroadcasts_nilIdentifiers_acceptsAllSessions() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []
        let exp = XCTestExpectation(description: "two"); exp.expectedFulfillmentCount = 2
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "AAAA0001"))
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "BBBB0002"))
        wait(for: [exp], timeout: 2); XCTAssertEqual(received.count, 2)
    }

    func test_startListenForPolarHrBroadcasts_identifierFilter_passesMatchingDevice() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []
        let exp = XCTestExpectation(description: "one")
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(["AAAA0001"]) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "AAAA0001"))
        wait(for: [exp], timeout: 2)
        XCTAssertEqual(received.count, 1); XCTAssertEqual(received.first?.deviceInfo.deviceId, "AAAA0001")
    }

    func test_startListenForPolarHrBroadcasts_identifierFilter_blocksNonMatchingDevice() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(["AAAA0001"]) { received.append(v) } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "BBBB0002"))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(received.count, 0)
    }

    func test_startListenForPolarHrBroadcasts_hrDataNotPresent_sessionFiltered() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v) } }
        let adv = MockSearchAdvertisementContent()
        hrBroadcastApi.searchSubject.send(MockSearchBleDeviceSession(advertisementContent: adv))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(received.count, 0)
    }

    func test_startListenForPolarHrBroadcasts_hrDataNotUpdated_sessionFiltered() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v) } }
        let adv = MockSearchAdvertisementContent()
        let data = Data([0x04, 0x00, 0x00, 72])
        adv.polarHrAdvertisementData.processPolarManufacturerData(data)
        adv.polarHrAdvertisementData.processPolarManufacturerData(data)
        hrBroadcastApi.searchSubject.send(MockSearchBleDeviceSession(advertisementContent: adv))
        let w = XCTestExpectation(description: "w")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { w.fulfill() }
        wait(for: [w], timeout: 1); XCTAssertEqual(received.count, 0)
    }

    func test_startListenForPolarHrBroadcasts_mapsHrValueCorrectly() throws {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []; let exp = XCTestExpectation(description: "received")
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(hr: 95))
        wait(for: [exp], timeout: 2); XCTAssertEqual(received.first?.hr, 95)
    }

    func test_startListenForPolarHrBroadcasts_mapsBatteryStatusCorrectly() throws {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []; let exp = XCTestExpectation(description: "received")
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(batteryOk: true))
        wait(for: [exp], timeout: 2); XCTAssertTrue(try XCTUnwrap(received.first).batteryStatus)
    }

    func test_startListenForPolarHrBroadcasts_mapsDeviceInfoCorrectly() throws {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []; let exp = XCTestExpectation(description: "received")
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "AABBCCDD", hr: 70, rssi: -55))
        wait(for: [exp], timeout: 2)
        let info = try XCTUnwrap(received.first)
        XCTAssertEqual(info.deviceInfo.deviceId, "AABBCCDD"); XCTAssertEqual(info.deviceInfo.rssi, -55)
        XCTAssertTrue(info.deviceInfo.connectable); XCTAssertEqual(info.hr, 70)
    }

    func test_startListenForPolarHrBroadcasts_hasSAGRFCFileSystem_trueForPolarFileSystemV2() throws {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []; let exp = XCTestExpectation(description: "received")
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceType: "360"))
        wait(for: [exp], timeout: 2); XCTAssertTrue(try XCTUnwrap(received.first).deviceInfo.hasSAGRFCFileSystem)
    }

    func test_startListenForPolarHrBroadcasts_hasSAGRFCFileSystem_falseForH10() throws {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []; let exp = XCTestExpectation(description: "received")
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceType: "h10"))
        wait(for: [exp], timeout: 2); XCTAssertFalse(try XCTUnwrap(received.first).deviceInfo.hasSAGRFCFileSystem)
    }

    func test_startListenForPolarHrBroadcasts_multipleIdentifiers_passesAllMatching() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var received: [PolarHrBroadcastData] = []
        let exp = XCTestExpectation(description: "two"); exp.expectedFulfillmentCount = 2
        Task { for try await v in hrBroadcastApi.startListenForPolarHrBroadcasts(["AAAA0001", "BBBB0002"]) { received.append(v); exp.fulfill() } }
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "AAAA0001", advFrameCounter: 1))
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "BBBB0002", advFrameCounter: 1))
        hrBroadcastApi.searchSubject.send(makeHrSession(deviceIdUntouched: "CCCC0003", advFrameCounter: 1))
        wait(for: [exp], timeout: 2)
        XCTAssertEqual(received.count, 2)
        let ids = received.map { $0.deviceInfo.deviceId }
        XCTAssertTrue(ids.contains("AAAA0001")); XCTAssertTrue(ids.contains("BBBB0002"))
    }

    func test_startListenForPolarHrBroadcasts_errorFromSource_propagatesError() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        var receivedError: Error?; let exp = XCTestExpectation(description: "error")
        Task {
            do { for try await _ in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) {} }
            catch { receivedError = error; exp.fulfill() }
        }
        hrBroadcastApi.searchSubject.send(completion: .failure(NSError(domain: "test", code: 5)))
        wait(for: [exp], timeout: 2); XCTAssertNotNil(receivedError)
    }

    func test_startListenForPolarHrBroadcasts_completesWhenSourceCompletes() {
        hrBroadcastApi = MockHrBroadcastBleApiImpl(mockDeviceSession: v2MockSession)
        let exp = XCTestExpectation(description: "completed")
        Task {
            for try await _ in hrBroadcastApi.startListenForPolarHrBroadcasts(nil) {}
            exp.fulfill()
        }
        hrBroadcastApi.searchSubject.send(completion: .finished)
        wait(for: [exp], timeout: 2)
    }

    // MARK: - PMD helpers (shared by requestStreamSettings / requestFullStreamSettings /
    //         requestOfflineRecordingSettings / requestFullOfflineRecordingSettings /
    //         getAvailableOfflineRecordingDataTypes / getOfflineRecordingStatus)

    private var pmdApi: MockPmdBleApiImpl!
    private var mockPmdSession: MockPmdBleDeviceSession!
    private var mockPmdClient: MockBlePmdClient!

    private func makeSuccessPmdSetting() throws -> PmdSetting {
        let data = Data([0x00, 0x01, 0x82, 0x00, 0x01, 0x01, 0x10, 0x00])
        return try PmdSetting(data)
    }

    private func setUpPmdApi() {
        let gatt = MockPolarGattServiceTransmitter()
        mockPmdClient = MockBlePmdClient(gattServiceTransmitter: gatt)
        mockPmdSession = MockPmdBleDeviceSession(mockPmdClient: mockPmdClient)
        pmdApi = MockPmdBleApiImpl(mockPmdSession: mockPmdSession)
    }

    // MARK: - requestStreamSettings tests

    func test_requestStreamSettings_ppi_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestStreamSettings(deviceId, feature: .ppi) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestStreamSettings_hr_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestStreamSettings(deviceId, feature: .hr) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestStreamSettings_ecg_queriesEcgType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .ecg)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .online)
    }

    func test_requestStreamSettings_acc_queriesAccType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .acc) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .acc)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .online)
    }

    func test_requestStreamSettings_ppg_queriesPpgType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .ppg) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .ppg)
    }

    func test_requestStreamSettings_magnetometer_queriesMgnType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .magnetometer) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .mgn)
    }

    func test_requestStreamSettings_gyro_queriesGyroType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .gyro) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .gyro)
    }

    func test_requestStreamSettings_temperature_queriesTemperatureType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .temperature) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .temperature)
    }

    func test_requestStreamSettings_skinTemperature_queriesSkinTemperatureType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .skinTemperature) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .skinTemperature)
    }

    func test_requestStreamSettings_pressure_queriesPressureType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .pressure) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .pressure)
    }

    func test_requestStreamSettings_mapsSettingsCorrectly() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        let result = try awaitSingleAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(result.settings[.sampleRate], [130]); XCTAssertEqual(result.settings[.resolution], [16])
    }

    func test_requestStreamSettings_queryError_wrappedAsDeviceError() {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .failure(NSError(domain: "pmd", code: 42))
        let e = awaitErrorAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .ecg) }
        XCTAssertNotNil(e); if case PolarErrors.deviceError = e! { } else { XCTFail("Expected deviceError") }
    }

    func test_requestStreamSettings_sessionNotReady_propagatesError() {
        setUpPmdApi(); (pmdApi.serviceClientUtils as! MockPmdServiceClientUtils).stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.requestStreamSettings(deviceId, feature: .ecg) })
    }

    // MARK: - requestFullStreamSettings tests

    func test_requestFullStreamSettings_ppi_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullStreamSettings(deviceId, feature: .ppi) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullStreamSettings_hr_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullStreamSettings(deviceId, feature: .hr) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullStreamSettings_temperature_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullStreamSettings(deviceId, feature: .temperature) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullStreamSettings_pressure_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullStreamSettings(deviceId, feature: .pressure) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullStreamSettings_skinTemperature_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullStreamSettings(deviceId, feature: .skinTemperature) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullStreamSettings_ecg_queriesEcgTypeOnline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .ecg)
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.recordingType, .online)
    }

    func test_requestFullStreamSettings_acc_queriesAccTypeOnline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .acc) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .acc)
    }

    func test_requestFullStreamSettings_ppg_queriesPpgTypeOnline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .ppg) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .ppg)
    }

    func test_requestFullStreamSettings_magnetometer_queriesMgnTypeOnline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .magnetometer) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .mgn)
    }

    func test_requestFullStreamSettings_gyro_queriesGyroTypeOnline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .gyro) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .gyro)
    }

    func test_requestFullStreamSettings_mapsSettingsCorrectly() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        let result = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(result.settings[.sampleRate], [130]); XCTAssertEqual(result.settings[.resolution], [16])
    }

    func test_requestFullStreamSettings_doesNotCallQuerySettings() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.count, 0)
    }

    func test_requestFullStreamSettings_queryError_wrappedAsDeviceError() {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .failure(NSError(domain: "pmd.full", code: 99))
        let e = awaitErrorAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .ecg) }
        XCTAssertNotNil(e); if case PolarErrors.deviceError = e! { } else { XCTFail("Expected deviceError") }
    }

    func test_requestFullStreamSettings_sessionNotReady_propagatesError() {
        setUpPmdApi(); (pmdApi.serviceClientUtils as! MockPmdServiceClientUtils).stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.requestFullStreamSettings(deviceId, feature: .ecg) })
    }

    // MARK: - requestOfflineRecordingSettings tests

    func test_requestOfflineRecordingSettings_ppi_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestOfflineRecordingSettings(deviceId, feature: .ppi) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestOfflineRecordingSettings_hr_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestOfflineRecordingSettings(deviceId, feature: .hr) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestOfflineRecordingSettings_ecg_queriesEcgTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .ecg)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_acc_queriesAccTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .acc) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .acc)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_ppg_queriesPpgTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .ppg) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .ppg)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_magnetometer_queriesMgnTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .magnetometer) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .mgn)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_gyro_queriesGyroTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .gyro) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .gyro)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_temperature_queriesTemperatureTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .temperature) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .temperature)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_skinTemperature_queriesSkinTemperatureTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .skinTemperature) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .skinTemperature)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_pressure_queriesPressureTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .pressure) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.type, .pressure)
        XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline)
    }

    func test_requestOfflineRecordingSettings_mapsSettingsCorrectly() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        let result = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(result.settings[.sampleRate], [130]); XCTAssertEqual(result.settings[.resolution], [16])
    }

    func test_requestOfflineRecordingSettings_usesOfflineNotOnlineRecordingType() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        for feature: PolarDeviceDataType in [.ecg, .acc, .ppg, .magnetometer, .gyro, .temperature, .skinTemperature, .pressure] {
            mockPmdClient.querySettingsCalls.removeAll()
            _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: feature) }
            XCTAssertEqual(mockPmdClient.querySettingsCalls.first?.recordingType, .offline, "\(feature) should use .offline")
        }
    }

    func test_requestOfflineRecordingSettings_doesNotCallQueryFullSettings() throws {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.count, 0)
    }

    func test_requestOfflineRecordingSettings_queryError_wrappedAsDeviceError() {
        setUpPmdApi(); mockPmdClient.querySettingsReturnValue = .failure(NSError(domain: "pmd.offline", code: 7))
        let e = awaitErrorAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertNotNil(e); if case PolarErrors.deviceError = e! { } else { XCTFail("Expected deviceError") }
    }

    func test_requestOfflineRecordingSettings_sessionNotReady_propagatesError() {
        setUpPmdApi(); (pmdApi.serviceClientUtils as! MockPmdServiceClientUtils).stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.requestOfflineRecordingSettings(deviceId, feature: .ecg) })
    }

    // MARK: - requestFullOfflineRecordingSettings tests

    func test_requestFullOfflineRecordingSettings_ppi_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullOfflineRecordingSettings(deviceId, feature: .ppi) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullOfflineRecordingSettings_hr_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullOfflineRecordingSettings(deviceId, feature: .hr) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullOfflineRecordingSettings_pressure_returnsOperationNotSupported() {
        let e = awaitErrorAsync { [self] in try await v2Api.requestFullOfflineRecordingSettings(deviceId, feature: .pressure) }
        XCTAssertNotNil(e); if case PolarErrors.operationNotSupported = e! { } else { XCTFail() }
    }

    func test_requestFullOfflineRecordingSettings_ecg_queriesEcgTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .ecg)
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.recordingType, .offline)
    }

    func test_requestFullOfflineRecordingSettings_acc_queriesAccTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .acc) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .acc)
    }

    func test_requestFullOfflineRecordingSettings_ppg_queriesPpgTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .ppg) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .ppg)
    }

    func test_requestFullOfflineRecordingSettings_magnetometer_queriesMgnTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .magnetometer) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .mgn)
    }

    func test_requestFullOfflineRecordingSettings_gyro_queriesGyroTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .gyro) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .gyro)
    }

    func test_requestFullOfflineRecordingSettings_temperature_queriesTemperatureTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .temperature) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .temperature)
    }

    func test_requestFullOfflineRecordingSettings_skinTemperature_queriesSkinTemperatureTypeOffline() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .skinTemperature) }
        XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.type, .skinTemperature)
    }

    func test_requestFullOfflineRecordingSettings_mapsSettingsCorrectly() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        let result = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(result.settings[.sampleRate], [130]); XCTAssertEqual(result.settings[.resolution], [16])
    }

    func test_requestFullOfflineRecordingSettings_usesOfflineNotOnlineRecordingType() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        for feature: PolarDeviceDataType in [.ecg, .acc, .ppg, .magnetometer, .gyro, .temperature, .skinTemperature] {
            mockPmdClient.queryFullSettingsCalls.removeAll()
            _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: feature) }
            XCTAssertEqual(mockPmdClient.queryFullSettingsCalls.first?.recordingType, .offline, "\(feature) should use .offline")
        }
    }

    func test_requestFullOfflineRecordingSettings_doesNotCallQuerySettings() throws {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .success(try makeSuccessPmdSetting())
        _ = try awaitSingleAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertEqual(mockPmdClient.querySettingsCalls.count, 0)
    }

    func test_requestFullOfflineRecordingSettings_queryError_wrappedAsDeviceError() {
        setUpPmdApi(); mockPmdClient.queryFullSettingsReturnValue = .failure(NSError(domain: "pmd.fullOffline", code: 3))
        let e = awaitErrorAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .ecg) }
        XCTAssertNotNil(e); if case PolarErrors.deviceError = e! { } else { XCTFail("Expected deviceError") }
    }

    func test_requestFullOfflineRecordingSettings_sessionNotReady_propagatesError() {
        setUpPmdApi(); (pmdApi.serviceClientUtils as! MockPmdServiceClientUtils).stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.requestFullOfflineRecordingSettings(deviceId, feature: .ecg) })
    }

    // MARK: - getAvailableOfflineRecordingDataTypes tests

    func test_getAvailableOfflineRecordingDataTypes_emptyFeatureSet_returnsEmptySet() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set<PmdMeasurementType>())
        XCTAssertTrue(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }.isEmpty)
    }

    func test_getAvailableOfflineRecordingDataTypes_ecg_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.ecg]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.ecg])
    }

    func test_getAvailableOfflineRecordingDataTypes_acc_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.acc]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.acc])
    }

    func test_getAvailableOfflineRecordingDataTypes_ppg_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.ppg]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.ppg])
    }

    func test_getAvailableOfflineRecordingDataTypes_ppi_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.ppi]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.ppi])
    }

    func test_getAvailableOfflineRecordingDataTypes_gyro_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.gyro]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.gyro])
    }

    func test_getAvailableOfflineRecordingDataTypes_mgn_mappedToMagnetometer() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.mgn]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.magnetometer])
    }

    func test_getAvailableOfflineRecordingDataTypes_offlineHr_mappedToHr() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.offline_hr]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.hr])
    }

    func test_getAvailableOfflineRecordingDataTypes_temperature_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.temperature]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.temperature])
    }

    func test_getAvailableOfflineRecordingDataTypes_skinTemperature_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set([PmdMeasurementType.skinTemperature]))
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }, [.skinTemperature])
    }

    func test_getAvailableOfflineRecordingDataTypes_multipleTypes_allMapped() throws {
        setUpPmdApi()
        let pmdTypes: Set<PmdMeasurementType> = [.ecg, .acc, .ppg, .gyro, .mgn, .offline_hr, .temperature, .skinTemperature]
        mockPmdClient.readFeatureReturnValue = .success(pmdTypes)
        let result = try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }
        XCTAssertEqual(result, [.ecg, .acc, .ppg, .gyro, .magnetometer, .hr, .temperature, .skinTemperature])
    }

    func test_getAvailableOfflineRecordingDataTypes_passesCheckConnectionTrue() throws {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .success(Set<PmdMeasurementType>())
        _ = try awaitSingleAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) }
        XCTAssertEqual(mockPmdClient.readFeatureCalls.first, true)
    }

    func test_getAvailableOfflineRecordingDataTypes_readFeatureError_propagatesError() {
        setUpPmdApi()
        mockPmdClient.readFeatureReturnValue = .failure(NSError(domain: "pmd.readFeature", code: 5))
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) })
    }

    func test_getAvailableOfflineRecordingDataTypes_sessionNotReady_propagatesError() {
        setUpPmdApi(); (pmdApi.serviceClientUtils as! MockPmdServiceClientUtils).stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getAvailableOfflineRecordingDataTypes(deviceId) })
    }

    // MARK: - getOfflineRecordingStatus tests

    func test_getOfflineRecordingStatus_emptyStatus_returnsEmptyDictionary() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([])
        XCTAssertTrue(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }.isEmpty)
    }

    func test_getOfflineRecordingStatus_offlineMeasurementActive_returnsTrueForFeature() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.ecg, PmdActiveMeasurement.offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.ecg], true)
    }

    func test_getOfflineRecordingStatus_onlineOfflineMeasurementActive_returnsTrueForFeature() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.acc, PmdActiveMeasurement.online_offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.acc], true)
    }

    func test_getOfflineRecordingStatus_noMeasurementActive_returnsFalseForFeature() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.ppg, PmdActiveMeasurement.no_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.ppg], false)
    }

    func test_getOfflineRecordingStatus_onlineMeasurementActive_returnsFalseForFeature() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.gyro, PmdActiveMeasurement.online_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.gyro], false)
    }

    func test_getOfflineRecordingStatus_mgn_mappedToMagnetometer() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.mgn, PmdActiveMeasurement.offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.magnetometer], true)
    }

    func test_getOfflineRecordingStatus_offlineHr_mappedToHr() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.offline_hr, PmdActiveMeasurement.offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.hr], true)
    }

    func test_getOfflineRecordingStatus_temperature_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.temperature, PmdActiveMeasurement.offline_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.temperature], true)
    }

    func test_getOfflineRecordingStatus_pressure_mappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.pressure, PmdActiveMeasurement.no_measurement_active)])
        XCTAssertEqual(try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }[.pressure], false)
    }

    func test_getOfflineRecordingStatus_multipleFeatures_allMappedCorrectly() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([
            (PmdMeasurementType.ecg,  PmdActiveMeasurement.offline_measurement_active),
            (PmdMeasurementType.acc,  PmdActiveMeasurement.no_measurement_active),
            (PmdMeasurementType.ppg,  PmdActiveMeasurement.online_offline_measurement_active),
            (PmdMeasurementType.gyro, PmdActiveMeasurement.online_measurement_active)
        ])
        let result = try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }
        XCTAssertEqual(result[.ecg], true); XCTAssertEqual(result[.acc], false)
        XCTAssertEqual(result[.ppg], true); XCTAssertEqual(result[.gyro], false)
    }

    func test_getOfflineRecordingStatus_unmappablePmdType_propagatesError() {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([(PmdMeasurementType.unknown_type, PmdActiveMeasurement.offline_measurement_active)])
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) })
    }

    func test_getOfflineRecordingStatus_readMeasurementStatusError_propagatesError() {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .failure(NSError(domain: "pmd.status", code: 8))
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) })
    }

    func test_getOfflineRecordingStatus_sessionNotReady_propagatesError() {
        setUpPmdApi(); (pmdApi.serviceClientUtils as! MockPmdServiceClientUtils).stubError = PolarErrors.deviceNotConnected
        XCTAssertNotNil(awaitErrorAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) })
    }

    func test_getOfflineRecordingStatus_callsReadMeasurementStatus() throws {
        setUpPmdApi()
        mockPmdClient.readMeasurementStatusReturnValue = .success([])
        _ = try awaitSingleAsync { [self] in try await pmdApi.getOfflineRecordingStatus(deviceId) }
        XCTAssertEqual(mockPmdClient.readMeasurementStatusCalls, 1)
    }

    // MARK: - listOfflineRecordings helpers

    private func makePmdFilesTxtData(entries: [(size: Int, path: String)]) -> Data {
        (entries.map { "\($0.size) \($0.path)" }.joined(separator: "\n")).data(using: .utf8)!
    }

    private func makeDirectoryProtoData(entries: [(name: String, size: UInt64)]) throws -> Data {
        var dir = Protocol_PbPFtpDirectory()
        dir.entries = entries.map { e in
            var entry = Protocol_PbPFtpEntry(); entry.name = e.name; entry.size = e.size; return entry
        }
        return try dir.serializedData()
    }

    private func makeRequestClosure(_ responses: [String: () throws -> Data]) -> (Data) async throws -> Data {
        return { headerData in
            guard let op = try? Protocol_PbPFtpOperation(serializedBytes: headerData) else {
                throw NSError(domain: "test.proto", code: 0)
            }
            if let builder = responses[op.path] {
                return try builder()
            }
            throw NSError(domain: "test.unrouted", code: 0,
                          userInfo: [NSLocalizedDescriptionKey: "Unrouted: \(op.path)"])
        }
    }

    func testOfflineRecordingFileHeadersUseSharedFileFacadePlanning() {
        let pmdFilesOperation = PolarBleApiImpl.offlineRecordingPmdFilesReadOperation()
        XCTAssertEqual(pmdFilesOperation.command, .get)
        XCTAssertEqual(pmdFilesOperation.path, "/PMDFILES.TXT")

        let fileOperation = PolarBleApiImpl.offlineRecordingFileReadOperation(path: "/U/0/20240615/R/103000/ACC.REC")
        XCTAssertEqual(fileOperation.command, .get)
        XCTAssertEqual(fileOperation.path, "/U/0/20240615/R/103000/ACC.REC")

        let directoryOperation = PolarBleApiImpl.offlineRecordingDirectoryReadOperation(path: "/U/0/20240615/R/103000/")
        XCTAssertEqual(directoryOperation.command, .get)
        XCTAssertEqual(directoryOperation.path, "/U/0/20240615/R/103000/")
    }

    func testGenericDirectoryHeadersUseSharedFileFacadePlanning() {
        let directoryOperation = PolarBleApiImpl.genericDirectoryReadOperation(path: "/U/0/")
        XCTAssertEqual(directoryOperation.command, .get)
        XCTAssertEqual(directoryOperation.path, "/U/0/")
    }

    func testFirmwareWriteHeadersUseSharedFileFacadePlanning() {
        let writeOperation = PolarBleApiImpl.firmwareFileWriteOperation(path: "SYSUPDAT.IMG")
        XCTAssertEqual(writeOperation.command, .put)
        XCTAssertEqual(writeOperation.path, "SYSUPDAT.IMG")
    }

    // MARK: - listOfflineRecordings tests

    func test_listOfflineRecordings_sessionNotReady_propagatesError() {
        XCTAssertNotNil(awaitErrorAsync { [self] in
            for try await _ in MockDisconnectBleApiImpl(mockDeviceSession: v2MockSession).listOfflineRecordings(self.deviceId) {}
        })
    }

    func test_listOfflineRecordings_v2Path_singleEntry_emitsEntry() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 1024, path: "/U/0/20240615/R/103000/ACC.REC")]) }
        ])
        let entries = try collectAllAsync(v2Api.listOfflineRecordings(deviceId))
        XCTAssertEqual(entries.count, 1); XCTAssertEqual(entries.first?.type, .acc)
    }

    func test_listOfflineRecordings_v2Path_multipleEntries_allEmitted() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [
                (size: 1024, path: "/U/0/20240615/R/103000/ACC.REC"),
                (size: 2048, path: "/U/0/20240615/R/103000/GYRO.REC")
            ]) }
        ])
        let entries = try collectAllAsync(v2Api.listOfflineRecordings(deviceId))
        XCTAssertEqual(entries.count, 2)
        let types = Set(entries.map { $0.type })
        XCTAssertTrue(types.contains(.acc)); XCTAssertTrue(types.contains(.gyro))
    }

    func test_listOfflineRecordings_v2Path_zeroSizeEntry_ignored() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 0, path: "/U/0/20240615/R/103000/ACC.REC")]) }
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    func test_listOfflineRecordings_v2Path_invalidPathTooFewComponents_ignored() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 1024, path: "/U/0/20240615/ACC.REC")]) }
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    func test_listOfflineRecordings_v2Path_unknownFileType_ignored() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 1024, path: "/U/0/20240615/R/103000/UNKNOWN.REC")]) }
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    func test_listOfflineRecordings_v2Path_entryTypesMappedCorrectly() throws {
        let cases: [(file: String, expected: PolarDeviceDataType)] = [
            ("ACC.REC", .acc), ("GYRO.REC", .gyro), ("MAGNETOMETER.REC", .magnetometer),
            ("PPG.REC", .ppg), ("PPI.REC", .ppi), ("HR.REC", .hr),
            ("TEMP.REC", .temperature), ("SKINTEMP.REC", .skinTemperature)
        ]
        for (fileName, expectedType) in cases {
            v2MockClient.requestReturnValueClosure = nil; cancellables.removeAll()
            v2MockClient.requestReturnValueClosure = makeRequestClosure([
                "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 512, path: "/U/0/20240615/R/103000/\(fileName)")]) }
            ])
            let entries = try collectAllAsync(v2Api.listOfflineRecordings(deviceId))
            XCTAssertEqual(entries.count, 1, "\(fileName) should produce one entry")
            XCTAssertEqual(entries.first?.type, expectedType, "\(fileName) → \(expectedType)")
        }
    }

    func test_listOfflineRecordings_v2Path_dateAndSizeParsedCorrectly() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { self.makePmdFilesTxtData(entries: [(size: 4096, path: "/U/0/20240615/R/103000/ACC.REC")]) }
        ])
        let entry = try XCTUnwrap(collectAllAsync(v2Api.listOfflineRecordings(deviceId)).first)
        XCTAssertEqual(entry.size, 4096)
        let comps = Calendar(identifier: .gregorian).dateComponents(in: TimeZone(secondsFromGMT: 0)!, from: entry.date)
        XCTAssertEqual(comps.year, 2024); XCTAssertEqual(comps.month, 6); XCTAssertEqual(comps.day, 15)
    }

    func test_listOfflineRecordings_v2Path_emptyPmdFileTxt_fallsBackToV1WithNoEntries() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT": { Data() },
            "/U/0/":         { try self.makeDirectoryProtoData(entries: []) }
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    func test_listOfflineRecordings_v1Path_validDirectoryStructure_emitsEntry() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT":             { Data() },
            "/U/0/":                     { try self.makeDirectoryProtoData(entries: [("20240615/", 0)]) },
            "/U/0/20240615/":            { try self.makeDirectoryProtoData(entries: [("R/", 0)]) },
            "/U/0/20240615/R/":          { try self.makeDirectoryProtoData(entries: [("103000/", 0)]) },
            "/U/0/20240615/R/103000/":   { try self.makeDirectoryProtoData(entries: [("ACC.REC", 2048)]) }
        ])
        let entries = try collectAllAsync(v2Api.listOfflineRecordings(deviceId))
        XCTAssertEqual(entries.count, 1); XCTAssertEqual(entries.first?.type, .acc)
        XCTAssertEqual(entries.first?.size, 2048)
    }

    func test_listOfflineRecordings_v1Path_zeroSizeFile_ignored() throws {
        v2MockClient.requestReturnValueClosure = makeRequestClosure([
            "/PMDFILES.TXT":             { Data() },
            "/U/0/":                     { try self.makeDirectoryProtoData(entries: [("20240615/", 0)]) },
            "/U/0/20240615/":            { try self.makeDirectoryProtoData(entries: [("R/", 0)]) },
            "/U/0/20240615/R/":          { try self.makeDirectoryProtoData(entries: [("103000/", 0)]) },
            "/U/0/20240615/R/103000/":   { try self.makeDirectoryProtoData(entries: [("ACC.REC", 0)]) }
        ])
        XCTAssertTrue(try collectAllAsync(v2Api.listOfflineRecordings(deviceId)).isEmpty)
    }

    // MARK: - removeExercise

    func test_removeExercise_polarFileSystemV2_returnsPolarBleSdkInternalException() {
        let error = awaitErrorAsync { [self] in try await v2Api.removeExercise(self.deviceId, entry: PolarExerciseEntry(path: "/some/path", date: Date(), entryId: "id1")) }
        XCTAssertNotNil(error)
        if case PolarErrors.polarBleSdkInternalException = error! { } else { XCTFail("Expected polarBleSdkInternalException") }
    }

    func test_removeExercise_h10_sendsRemoveRequest() throws {
        // Arrange: request returns empty data (success)
        h10MockClient.requestReturnValue = .success(Data())

        // Act
        let entry = PolarExerciseEntry(path: "/EXERCISE/E0000001.BPB", date: Date(), entryId: "id1")
        try awaitVoidAsync { [self] in try await h10Api.removeExercise(self.deviceId, entry: entry) }

        // Assert
        XCTAssertEqual(h10MockClient.requestCalls.count, 1)
        let requestOperation = try Protocol_PbPFtpOperation(serializedBytes: h10MockClient.requestCalls[0])
        XCTAssertEqual(.remove, requestOperation.command)
        XCTAssertEqual(entry.path, requestOperation.path)
    }

    func testH10ExerciseFileHeadersUseSharedFileFacadePlanning() {
        let path = "/EXERCISE/E0000001.BPB"

        let fetchOperation = PolarBleApiImpl.h10ExerciseFetchOperation(path: path)
        XCTAssertEqual(fetchOperation.command, .get)
        XCTAssertEqual(fetchOperation.path, path)

        let removeOperation = PolarBleApiImpl.h10ExerciseRemoveOperation(path: path)
        XCTAssertEqual(removeOperation.command, .remove)
        XCTAssertEqual(removeOperation.path, path)
    }

    private func sharedDeviceLocation(_ value: Int) -> PbDeviceLocation {
        if let sharedName = PolarRuntimePlanner.userDeviceSettingsDeviceLocationName(value: value) {
            return PbDeviceLocation(rawValue: PolarUserDeviceSettings.getDeviceLocation(deviceLocation: sharedName).toInt())!
        }
        return PbDeviceLocation(rawValue: value)!
    }
}
