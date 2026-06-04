//  Copyright © 2024 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk

class PolarFirmwareUpdateUtilsTest: XCTestCase {

    var mockClient: MockBlePsFtpClient!

    override func setUpWithError() throws {
        mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
    }

    override func tearDownWithError() throws {
        mockClient = nil
    }

    func testReadDeviceFirmwareInfo_shouldReturnFirmwareInfo() async throws {
        // Arrange
        let expectedDeviceId = "123456"
        let expectedFirmwareVersion = "1.2.0"
        let expectedModelName = "Model"
        let expectedHardwareCode = "00.112233"
        
        let proto = Data_PbDeviceInfo.with {
            $0.deviceVersion = .with {
                $0.major = 1
                $0.minor = 2
                $0.patch = 0
            }
            $0.modelName = expectedModelName
            $0.hardwareCode = expectedHardwareCode
        }
        
        mockClient.requestReturnValue = .success(try proto.serializedData())

        // Act
        let firmwareInfo = await PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(client: mockClient, deviceId: expectedDeviceId)

        // Assert
        XCTAssertEqual(firmwareInfo?.deviceFwVersion, expectedFirmwareVersion)
        XCTAssertEqual(firmwareInfo?.deviceModelName, expectedModelName)
        XCTAssertEqual(firmwareInfo?.deviceHardwareCode, expectedHardwareCode)
        XCTAssertEqual(mockClient.requestCalls.count, 1)
    }
    
    func testIsAvailableFirmwareVersionHigher_shouldReturnTrue_whenCurrentVersionIsSmallerThanAvailableVersion() {
        XCTAssertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "1.0.0",
                availableVersion: "2.0.0"
            )
        )
        XCTAssertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "2.0.1"
            )
        )
        XCTAssertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "2.1.0"
            )
        )
        XCTAssertTrue(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "3.0.0"
            )
        )
    }
    
    func testIsAvailableFirmwareVersionHigher_shouldReturnFalse_whenCurrentVersionIsSameOrHigherThanAvailableVersion() {
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "1.0.0"
            )
        )
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.1",
                availableVersion: "2.0.0"
            )
        )
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.1.0",
                availableVersion: "2.0.0"
            )
        )
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "3.0.0",
                availableVersion: "2.0.0"
            )
        )
        XCTAssertFalse(
            PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(
                currentVersion: "2.0.0",
                availableVersion: "2.0.0"
            )
        )
    }
    
    func testFwFileComparatorSortsFilesCorrectly() {
           // Arrange
           let btFile = "BTUPDAT.BIN"
           let sysFile = "SYSUPDAT.IMG"
           let touchFile = "TCHUPDAT.BIN"
           var files = [btFile, sysFile, touchFile]
           
           // Act
           files.sort { PolarFirmwareUpdateUtils.FwFileComparator.compare($0, $1) == .orderedAscending }
           
           // Assert
           XCTAssertEqual(files[0], btFile, "First file should be BTUPDAT.BIN")
           XCTAssertEqual(files[1], touchFile, "Second file should be TCHUPDAT.BIN")
           XCTAssertEqual(files[2], sysFile, "Last file should be SYSUPDAT.IMG")
       }
       
       func testFwFileComparatorKeepsAlreadySortedFiles() {
           // Arrange
           let f1 = "BTUPDAT.BIN"
           let f2 = "TCHUPDAT.BIN"
           let f3 = "SYSUPDAT.IMG"
           var files = [f1, f2, f3]
           
           // Act
           files.sort { PolarFirmwareUpdateUtils.FwFileComparator.compare($0, $1) == .orderedAscending }
           
           // Assert
           XCTAssertEqual(files[0], f1, "Files should maintain initial order if already sorted")
           XCTAssertEqual(files[1], f2, "Files should maintain initial order if already sorted")
           XCTAssertEqual(files[2], f3, "Files should maintain initial order if already sorted")
       }

    func testFirmwareDeviceInfoGoldenVectorsMapProtoToModel() async throws {
        for vector in try loadFirmwareUpdateGoldenVectors().filter({ inputKind($0) == "deviceInfo" }) {
            let id = try XCTUnwrap(vector["id"] as? String)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let protoFields = try XCTUnwrap(input["proto"] as? [String: Any], id)
            let version = try XCTUnwrap(protoFields["version"] as? [String: Any], id)
            var proto = Data_PbDeviceInfo()
            proto.deviceVersion = PbVersion.with {
                $0.major = UInt32(try! int32(version, "major", id: id))
                $0.minor = UInt32(try! int32(version, "minor", id: id))
                $0.patch = UInt32(try! int32(version, "patch", id: id))
            }
            proto.modelName = try XCTUnwrap(protoFields["modelName"] as? String, id)
            proto.hardwareCode = try XCTUnwrap(protoFields["hardwareCode"] as? String, id)
            mockClient = MockBlePsFtpClient(gattServiceTransmitter: MockPolarGattServiceTransmitter())
            mockClient.requestReturnValue = .success(try proto.serializedData())

            let firmwareInfo = await PolarFirmwareUpdateUtils.readDeviceFirmwareInfo(client: mockClient, deviceId: "123456")
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)

            XCTAssertEqual(firmwareInfo?.deviceFwVersion, try XCTUnwrap(expected["deviceFwVersion"] as? String, id), id)
            XCTAssertEqual(firmwareInfo?.deviceModelName, try XCTUnwrap(expected["deviceModelName"] as? String, id), id)
            XCTAssertEqual(firmwareInfo?.deviceHardwareCode, try XCTUnwrap(expected["deviceHardwareCode"] as? String, id), id)
        }
    }

    func testFirmwareVersionComparisonGoldenVectorsMatchCurrentPolicy() throws {
        for testCase in try firmwareVectorCases(kind: "versionComparison") {
            let currentVersion = try XCTUnwrap(testCase["currentVersion"] as? String)
            let availableVersion = try XCTUnwrap(testCase["availableVersion"] as? String)
            XCTAssertEqual(
                PolarFirmwareUpdateUtils.isAvailableFirmwareVersionHigher(currentVersion: currentVersion, availableVersion: availableVersion),
                try XCTUnwrap(testCase["expectedHigher"] as? Bool),
                "\(currentVersion) -> \(availableVersion)"
            )
        }
    }

    func testFirmwareVersionComparisonInvalidGoldenVectorsDocumentCurrentPolicy() throws {
        for testCase in try firmwareVectorCases(kind: "versionComparisonError") {
            let expected = try XCTUnwrap(testCase["expectedError"] as? [String: Any])
            XCTAssertEqual(try XCTUnwrap(expected["ios"] as? String), "fatal")
        }
    }

    func testFirmwareFileOrderingGoldenVectorsKeepSystemUpdateLast() throws {
        for testCase in try firmwareVectorCases(kind: "fileOrdering") {
            var files = try XCTUnwrap(testCase["input"] as? [String])

            files.sort { PolarFirmwareUpdateUtils.FwFileComparator.compare($0, $1) == .orderedAscending }

            XCTAssertEqual(files, try XCTUnwrap(testCase["expected"] as? [String]))
        }
    }

    func testFirmwareGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadFirmwareUpdateGoldenVectors() {
            let id = try XCTUnwrap(vector["id"] as? String)
            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            XCTAssertNotNil(input["kind"], id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertEqual(platforms["android"] as? Bool, true, id)
            XCTAssertEqual(platforms["ios"] as? Bool, true, id)
            XCTAssertEqual(platforms["common"] as? Bool, true, id)
        }
    }

    func testWorkflowRuntimePolicyVectorIsPinnedBeforeWorkflowMigration() throws {
        let vector = try XCTUnwrap(try loadFirmwareUpdateGoldenVectors().first { ($0["id"] as? String) == "workflow-runtime-policy" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let commonPrototype = try XCTUnwrap(expected["commonWorkflowPrototype"] as? [String: Any])
        let scenarios = try XCTUnwrap(input["scenarios"] as? [[String: Any]])
        let scenarioIds = scenarios.compactMap { $0["id"] as? String }
        let commonPrototypeCases = try XCTUnwrap(commonPrototype["cases"] as? [[String: Any]])
        let commonPrototypeCaseIds = commonPrototypeCases.compactMap { $0["id"] as? String }
        let execution = try XCTUnwrap(vector["execution"] as? [String: Any])
        let platformExpectations = try XCTUnwrap(vector["platformExpectations"] as? [String: Any])
        let commonDecision = try XCTUnwrap(platformExpectations["commonDecision"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])

        XCTAssertEqual(input["kind"] as? String, "firmwareWorkflowRuntimePolicy", "workflow-runtime-policy")
        XCTAssertEqual(FIRMWARE_WORKFLOW_SCENARIOS, scenarioIds, "workflow-runtime-policy")
        XCTAssertEqual(expected["policy"] as? String, "firmware-update-workflow-runtime-matrix", "workflow-runtime-policy")
        XCTAssertEqual(expected["migrationRequirement"] as? String, FIRMWARE_WORKFLOW_MIGRATION_REQUIREMENT, "workflow-runtime-policy")
        XCTAssertEqual(commonPrototype["status"] as? String, "executable shared commonTest plus Android-hosted prototype", "workflow-runtime-policy")
        XCTAssertEqual(FIRMWARE_WORKFLOW_SCENARIOS, commonPrototypeCaseIds, "workflow-runtime-policy")
        XCTAssertEqual(execution["common"] as? String, "shared-common-test", "workflow-runtime-policy")
        XCTAssertEqual(commonDecision["workflowPolicy"] as? String, FIRMWARE_WORKFLOW_COMMON_DECISION, "workflow-runtime-policy")
        XCTAssertNotNil(vector["execution"], "workflow-runtime-policy")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "workflow-runtime-policy"), ["com.polar.sdk.api.model.utils.PolarFirmwareUpdateUtilsTest", "com.polar.sdk.api.model.utils.FirmwareUpdateCommonFakeWorkflowTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "workflow-runtime-policy"), ["PolarFirmwareUpdateUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "workflow-runtime-policy"), ["com.polar.sdk.api.model.utils.FirmwareUpdateCommonFakeWorkflowTest", "com.polar.sharedtest.FirmwareWorkflowRuntimePolicyCommonTest"])
    }

    func testWorkflowRuntimeReadinessManifestIsPinnedBeforeWorkflowMigration() throws {
        let vector = try XCTUnwrap(try loadFirmwareUpdateGoldenVectors().first { ($0["id"] as? String) == "workflow-runtime-readiness" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])

        XCTAssertEqual(input["kind"] as? String, "firmwareWorkflowRuntimeReadiness")
        XCTAssertEqual(input["policyVectorPath"] as? String, "sdk/firmware-update/workflow-runtime-policy.json")
        XCTAssertEqual(FIRMWARE_WORKFLOW_READINESS_FAMILIES, requiredFamilies)
        XCTAssertEqual(FIRMWARE_WORKFLOW_READINESS_FAMILIES, coveredFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, FIRMWARE_WORKFLOW_READINESS_COMMON_DECISION)
        XCTAssertEqual((expected["commonRuntimePrototype"] as? [String: Any])?["status"] as? String, "executable shared commonTest runtime planning guard")
        XCTAssertEqual((expected["commonRuntimePrototype"] as? [String: Any])?["reason"] as? String, "Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.sdk.api.model.utils.PolarFirmwareUpdateUtilsTest"], "workflow-runtime-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["PolarFirmwareUpdateUtilsTest"], "workflow-runtime-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.FirmwareWorkflowRuntimePolicyCommonTest"], "workflow-runtime-readiness")
    }

    func testFirmwareUtilityReadinessManifestIsPinnedBeforeUtilityMigration() throws {
        let vector = try XCTUnwrap(try loadFirmwareUpdateGoldenVectors().first { ($0["id"] as? String) == "firmware-utility-readiness" })
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])

        XCTAssertEqual(input["kind"] as? String, "firmwareUtilityReadiness")
        XCTAssertEqual(expected["migrationReadiness"] as? String, "compileVerifiedPreMigrationCharacterization")
        XCTAssertEqual(FIRMWARE_UTILITY_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, "firmware-utility-readiness")
        XCTAssertEqual(FIRMWARE_UTILITY_READINESS_FAMILIES, requiredFamilies, "firmware-utility-readiness")
        XCTAssertEqual(FIRMWARE_UTILITY_READINESS_FAMILIES, coveredFamilies, "firmware-utility-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.sdk.api.model.utils.PolarFirmwareUpdateUtilsTest"], "firmware-utility-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["PolarFirmwareUpdateUtilsTest"], "firmware-utility-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.FirmwareUpdateUtilityCommonPolicyTest"], "firmware-utility-readiness")
    }

    private func firmwareVectorCases(kind: String) throws -> [[String: Any]] {
        return try loadFirmwareUpdateGoldenVectors()
            .filter { inputKind($0) == kind }
            .flatMap { vector -> [[String: Any]] in
                let input = try XCTUnwrap(vector["input"] as? [String: Any], kind)
                return try XCTUnwrap(input["cases"] as? [[String: Any]], kind)
            }
    }

    private func inputKind(_ vector: [String: Any]) -> String? {
        return (vector["input"] as? [String: Any])?["kind"] as? String
    }

    private func loadFirmwareUpdateGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/firmware-update")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
    }


    private func int32(_ object: [String: Any], _ key: String, id: String) throws -> Int32 {
        return try XCTUnwrap(object[key] as? NSNumber, "\(id) \(key)").int32Value
    }
}

private let FIRMWARE_UTILITY_READINESS_POLICY_VECTOR_PATHS = [
    "sdk/firmware-update/device-info-basic.json",
    "sdk/firmware-update/device-info-zero-version.json",
    "sdk/firmware-update/version-comparison.json",
    "sdk/firmware-update/version-comparison-invalid.json",
    "sdk/firmware-update/file-ordering.json"
]

private let FIRMWARE_UTILITY_READINESS_FAMILIES = [
    "device-info-protobuf-mapping",
    "zero-version-preservation",
    "dotted-integer-version-comparison",
    "invalid-version-typed-parse-failure",
    "system-update-file-ordering-last",
    "platform-firmware-utility-vector-references",
    "compile-verification-gate"
]

private let FIRMWARE_WORKFLOW_READINESS_FAMILIES = [
    "fake-network-availability",
    "download-failure",
    "fake-filesystem-zip-extraction",
    "empty-or-invalid-package",
    "ble-write-progress",
    "system-update-written-last",
    "reboot-response-success",
    "terminal-device-error",
    "cleanup-gate",
    "cancellation-gate",
    "cancellation-cleanup-after-package-fetch",
    "retryable-server-failure-gate",
    "facade-error-mapping-gate",
    "compile-verification-gate"
]

private let FIRMWARE_WORKFLOW_SCENARIOS = [
    "check-update-not-available",
    "check-update-available",
    "download-failure",
    "retryable-server-failure",
    "empty-or-invalid-zip",
    "cancel-after-package-fetch-cleans-up-before-ble-write",
    "write-package-success-with-system-update-last",
    "system-update-reboot-response-is-success",
    "battery-too-low-response-is-terminal-failure"
]

private let FIRMWARE_WORKFLOW_MIGRATION_REQUIREMENT = "Before moving firmware update orchestration into common KMP code, implement injectable fake network, fake filesystem or zip extraction, and fake BLE write dependencies that can reproduce update availability, download failures, invalid packages, sorted package writes, reboot success, and terminal device errors."

private let FIRMWARE_WORKFLOW_COMMON_DECISION = "separate device-info parsing, server availability, retryable server failures, package download, zip extraction, file ordering, BLE write progress, reboot success, and terminal device errors into typed common workflow states before KMP migration"

private let FIRMWARE_WORKFLOW_READINESS_COMMON_DECISION = "Firmware workflow migration may proceed only after workflow-runtime-policy.json and this readiness manifest are executable from shared commonTest, fake network/filesystem/BLE writer dependencies are injectable, progress, retryable fake-network server failure classification, terminal device errors, and cancellation cleanup before BLE writes are pinned, retry scheduling has explicit platform facade coverage, public facade error mapping is pinned, and the shared tests are compile-verified."
