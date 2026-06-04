//  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

private let OFFLINE_TRIGGER_RUNTIME_POLICY_COMMON_DECISION = "Shared offline trigger runtime code should model set-mode, status-read, per-feature setting writes, optional secret attachment, and get/set transport failures as typed steps before mapping them back to Android and iOS public errors."
private let OFFLINE_TRIGGER_RUNTIME_POLICY_SCENARIO_IDS = ["set-trigger-success-with-secret", "set-trigger-mode-error", "set-trigger-status-read-error", "set-trigger-setting-error", "get-trigger-success", "get-trigger-transport-error"]

class BlePmdClientTest: XCTestCase {

    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!
    var blePmdClient: BlePmdClient!

    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockPolarGattServiceTransmitter()
        blePmdClient = BlePmdClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }

    override func tearDownWithError() throws {
        mockGattServiceTransmitterImpl = nil
        blePmdClient = nil
    }

    func testProcessControlPointResponseWhenStatusIsSuccess() throws {
        // Arrange
        // HEX: F0 01 00 00 00 00 00 00 70 FF
        // index    type                                data
        // 0:      Response code                        F0
        // 1...:   Data                                 01 00 00 00 00 00 00 70 FF
        let controlPointResponse = Data([
            0xF0,
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x70, 0xFF
        ])
        let successErrCode = 0x00

        // Act
        blePmdClient.processServiceData(BlePmdClient.PMD_CP, data: controlPointResponse, err: successErrCode)

        // Assert
        let data = try blePmdClient.pmdCpResponseQueue.pop()
        XCTAssertEqual(controlPointResponse, data)
    }

    func testProcessMeasurementStopControlPointCommand() async throws {
        // Arrange
        // HEX: 01 01 02
        // index    type                                data
        // 0:      Online Measurement Stopped           01
        // 1...:   Measurement types                    01 (PPG), 02 (ACC)
        let controlPointResponse = Data([0x01, 0x01, 0x02])
        let successErrCode = 0x00

        let ppgStream = blePmdClient.observePpg()
        let accStream = blePmdClient.observeAcc()
        let ppiStream = blePmdClient.observePpi()

        // Start tasks that consume the streams — they will unblock when streams close
        let ppgTask = Task<Error?, Never> {
            do { for try await _ in ppgStream {} } catch { return error }
            return nil
        }
        let accTask = Task<Error?, Never> {
            do { for try await _ in accStream {} } catch { return error }
            return nil
        }

        // Give tasks a moment to start iterating before the stop command arrives
        try await Task.sleep(nanoseconds: 20_000_000) // 20ms

        // Act
        blePmdClient.processServiceData(BlePmdClient.PMD_CP, data: controlPointResponse, err: successErrCode)

        // Await the stream close errors
        let ppgError = await ppgTask.value
        let accError = await accTask.value

        // Assert PPG stream closed with bleOnlineStreamClosed
        XCTAssertNotNil(ppgError, "PPG stream should have closed with an error")
        guard case BlePmdError.bleOnlineStreamClosed = ppgError! else {
            return XCTFail("Expected bleOnlineStreamClosed for PPG, got \(ppgError!)")
        }

        // Assert ACC stream closed with bleOnlineStreamClosed
        XCTAssertNotNil(accError, "ACC stream should have closed with an error")
        guard case BlePmdError.bleOnlineStreamClosed = accError! else {
            return XCTFail("Expected bleOnlineStreamClosed for ACC, got \(accError!)")
        }

        // Assert PPI stream was NOT closed (type 0x03 was not in the stop command)
        // Race the stream against a short timeout — timeout should win (stream still open)
        let ppiClosedWithError = await withTaskGroup(of: Bool.self) { group in
            group.addTask {
                do { for try await _ in ppiStream {} } catch { return true }
                return false
            }
            group.addTask {
                try? await Task.sleep(nanoseconds: 50_000_000) // 50ms timeout
                return false
            }
            let result = await group.next() ?? false
            group.cancelAll()
            return result
        }
        XCTAssertFalse(ppiClosedWithError, "PPI stream should NOT have been closed by the stop command")
    }

    func testSetOfflineRecordingTriggerSendsIOSSettingAndSecretPacketsWithoutLengthPrefix() async throws {
        try assertOfflineTriggerRuntimePolicyVectorContains("set-trigger-success-with-secret")
        mockGattServiceTransmitterImpl.transmitMessageHandler = { [self] _, serviceUuid, characteristicUuid, packet, _ in
            guard serviceUuid == BlePmdClient.PMD_SERVICE && characteristicUuid == BlePmdClient.PMD_CP else { return }
            switch packet.first {
            case 0x08:
                blePmdClient.processServiceData(BlePmdClient.PMD_CP, data: pmdResponse(opCode: 0x08), err: 0)
            case 0x07:
                blePmdClient.processServiceData(BlePmdClient.PMD_CP, data: pmdResponse(opCode: 0x07, parameters: offlineTriggerStatusData()), err: 0)
            case 0x09:
                blePmdClient.processServiceData(BlePmdClient.PMD_CP, data: pmdResponse(opCode: 0x09, measurementType: packet[2]), err: 0)
            default:
                blePmdClient.processServiceData(BlePmdClient.PMD_CP, data: pmdResponse(opCode: packet.first ?? 0x00, errorCode: 0x01), err: 0)
            }
        }
        let trigger = PmdOfflineTrigger(
            triggerMode: .systemStart,
            triggers: [
                .acc: (.enabled, PmdSetting([.sampleRate: 52, .resolution: 16])),
                .offline_hr: (.enabled, nil)
            ]
        )
        let secretBytes = Data((0..<16).map { UInt8($0) })
        let secret = try PmdSecret(strategy: .aes128, key: secretBytes)

        try await blePmdClient.setOfflineRecordingTrigger(offlineRecordingTrigger: trigger, secret: secret)

        let packets = mockGattServiceTransmitterImpl.transmittedMessages.map { $0.packet }
        XCTAssertEqual(Data([0x08, 0x01]), packets.first)
        XCTAssertEqual(Data([0x07]), packets.dropFirst().first)
        let settingPackets = Array(packets.dropFirst(2))
        XCTAssertEqual(3, settingPackets.count)
        let accPacket = try XCTUnwrap(settingPackets.first { $0.starts(with: Data([0x09, 0x01, PmdMeasurementType.acc.rawValue])) })
        let gyroPacket = try XCTUnwrap(settingPackets.first { $0 == Data([0x09, 0x00, PmdMeasurementType.gyro.rawValue]) })
        let hrPacket = try XCTUnwrap(settingPackets.first { $0.starts(with: Data([0x09, 0x01, PmdMeasurementType.offline_hr.rawValue])) })
        XCTAssertEqual(Data([0x09, 0x00, PmdMeasurementType.gyro.rawValue]), gyroPacket)
        let secretSetting = Data([0x06, 0x01, 0x02]) + secretBytes
        XCTAssertNotEqual(0x1B, accPacket[3])
        XCTAssertNotNil(accPacket.range(of: Data([0x00, 0x01, 0x34, 0x00])))
        XCTAssertNotNil(accPacket.range(of: Data([0x01, 0x01, 0x10, 0x00])))
        XCTAssertNotNil(accPacket.range(of: secretSetting))
        XCTAssertEqual(Data([0x09, 0x01, PmdMeasurementType.offline_hr.rawValue]) + secretSetting, hrPacket)
    }

    func testGetOfflineRecordingTriggerStatusClearsStaleControlPointResponsesBeforeTransmit() async throws {
        try assertOfflineTriggerRuntimePolicyVectorContains("ios-pre-command-response-queue-clear")
        blePmdClient.pmdCpResponseQueue.push(pmdResponse(opCode: PmdControlPointCommandClientToService.GET_MEASUREMENT_STATUS))
        mockGattServiceTransmitterImpl.transmitMessageHandler = { [self] _, serviceUuid, characteristicUuid, packet, _ in
            guard serviceUuid == BlePmdClient.PMD_SERVICE && characteristicUuid == BlePmdClient.PMD_CP else { return }
            XCTAssertEqual(0, blePmdClient.pmdCpResponseQueue.size())
            XCTAssertEqual(Data([PmdControlPointCommandClientToService.GET_OFFLINE_RECORDING_TRIGGER_STATUS]), packet)
            blePmdClient.processServiceData(
                BlePmdClient.PMD_CP,
                data: pmdResponse(
                    opCode: PmdControlPointCommandClientToService.GET_OFFLINE_RECORDING_TRIGGER_STATUS,
                    parameters: Data([0x01, 0x01, PmdMeasurementType.acc.rawValue, 0x00])
                ),
                err: 0
            )
        }

        let trigger = try await blePmdClient.getOfflineRecordingTriggerStatus()

        XCTAssertEqual(PmdOfflineRecTriggerMode.systemStart, trigger.triggerMode)
        XCTAssertNotNil(trigger.triggers[PmdMeasurementType.acc])
        XCTAssertEqual(0, blePmdClient.pmdCpResponseQueue.size())
    }

    func testPmdControlPointResponseGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadControlPointGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected PMD control-point golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            if let platforms = vector["platforms"] as? [String: Any], platforms["ios"] as? Bool == false {
                continue
            }
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
            let response = PmdControlPointResponse(try Data(hexString: try XCTUnwrap(input["hex"] as? String, id)))

            XCTAssertEqual(try XCTUnwrap(expected["responseCode"] as? NSNumber, id).uint8Value, response.response, id)
            XCTAssertEqual(try XCTUnwrap(expected["opCodeValue"] as? NSNumber, id).uint8Value, response.opCode, id)
            XCTAssertEqual(try XCTUnwrap(expected["measurementType"] as? NSNumber, id).uint8Value, response.type.rawValue, id)
            XCTAssertEqual(try XCTUnwrap(expected["statusValue"] as? NSNumber, id).intValue, response.errorCode.rawValue, id)
            XCTAssertEqual(try XCTUnwrap(expected["more"] as? Bool, id), response.more, id)
            XCTAssertEqual(try XCTUnwrap(expected["parametersHex"] as? String, id), response.parameters.asData.hexString, id)
        }
    }

    func testPmdControlPointResponseGoldenVectorsFollowNeutralKmpVectorShape() throws {
        for vector in try loadControlPointGoldenVectors() {
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

    func testPmdControlPointReadinessManifestIsPinnedBeforeResponseParserMigration() throws {
        let manifest = try loadPmdControlPointReadinessManifest()
        let input = try XCTUnwrap(manifest["input"] as? [String: Any])
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        let policyPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])

        XCTAssertEqual("pmd-control-point-readiness", manifest["id"] as? String)
        XCTAssertEqual("pmdControlPointReadiness", input["kind"] as? String)
        XCTAssertEqual(pmdControlPointReadinessPolicyVectorPaths, policyPaths)
        XCTAssertEqual(pmdControlPointReadinessBehaviorFamilies, requiredFamilies)
        XCTAssertEqual(pmdControlPointReadinessBehaviorFamilies, coveredFamilies)
        XCTAssertEqual(pmdControlPointReadinessCommonDecision, try XCTUnwrap(expected["commonDecision"] as? String))
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any])
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePmdClientControlPointResponseTest", "com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdActiveMeasurementTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["BlePmdClientTest", "PmdActiveMeasurementTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.PmdControlPointCommonPolicyTest"])
    }

    private func loadControlPointGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/pmd")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("control-point-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                (vector["input"] as? [String: Any])?["kind"] as? String != "pmdControlPointReadiness"
            }
    }

    private func loadPmdControlPointReadinessManifest() throws -> [String: Any] {
        let manifestUrl = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/pmd/control-point-readiness.json")
        let data = try Data(contentsOf: manifestUrl)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], manifestUrl.path)
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

}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "BlePmdClientTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "BlePmdClientTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }

    var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }
}

private extension NSMutableData {
    var asData: Data {
        self as Data
    }
}

private let pmdControlPointReadinessPolicyVectorPaths = [
    "protocol/pmd/active-measurement-no-active-ecg.json",
    "protocol/pmd/active-measurement-offline-acc.json",
    "protocol/pmd/active-measurement-online-offline-gyro.json",
    "protocol/pmd/active-measurement-online-offline-unknown.json",
    "protocol/pmd/active-measurement-online-ppg.json",
    "protocol/pmd/control-point-error-already-in-state-sdk-mode.json",
    "protocol/pmd/control-point-error-device-in-charger-ecg.json",
    "protocol/pmd/control-point-error-disk-full-offline-recording.json",
    "protocol/pmd/control-point-error-invalid-length-acc.json",
    "protocol/pmd/control-point-error-invalid-measurement-type-unknown.json",
    "protocol/pmd/control-point-error-invalid-mtu-acc.json",
    "protocol/pmd/control-point-error-invalid-number-of-channels-ppg.json",
    "protocol/pmd/control-point-error-invalid-op-code.json",
    "protocol/pmd/control-point-error-invalid-parameter-pressure.json",
    "protocol/pmd/control-point-error-invalid-range-gyro.json",
    "protocol/pmd/control-point-error-invalid-resolution-mag.json",
    "protocol/pmd/control-point-error-invalid-sample-rate-ppg.json",
    "protocol/pmd/control-point-error-invalid-state-stop-temperature.json",
    "protocol/pmd/control-point-error-not-supported-sdk-mode-settings.json",
    "protocol/pmd/control-point-short-empty-android-error.json",
    "protocol/pmd/control-point-short-response-only-android-error.json",
    "protocol/pmd/control-point-short-response-op-android-error.json",
    "protocol/pmd/control-point-short-response-op-type-android-error.json",
    "protocol/pmd/control-point-success-measurement-status.json",
    "protocol/pmd/control-point-success-minimal-no-more-byte.json",
    "protocol/pmd/control-point-success-settings-acc.json",
    "protocol/pmd/control-point-success-start-ppg-more.json",
    "protocol/pmd/control-point-success-stop-ecg.json"
]

private let pmdControlPointReadinessBehaviorFamilies = [
    "active-measurement-bit-decoding",
    "active-measurement-platform-state-names",
    "control-point-success-response-parsing",
    "control-point-more-flag-and-parameters",
    "control-point-settings-response",
    "control-point-measurement-status-response",
    "control-point-status-code-coverage",
    "unknown-measurement-type-policy",
    "short-payload-deterministic-error-policy",
    "platform-control-point-vector-reference-gate",
    "compile-verification-gate"
]

private let pmdControlPointReadinessCommonDecision = "PMD control-point migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS control-point and active-measurement tests continue to reference the same vectors, active-measurement bit decoding and platform state names, success response parsing, more flag and parameter extraction, settings and measurement-status responses, all status-code mappings, unknown measurement type handling, deterministic short-payload error policy, and compile verification remain explicit before production response parsing moves."
