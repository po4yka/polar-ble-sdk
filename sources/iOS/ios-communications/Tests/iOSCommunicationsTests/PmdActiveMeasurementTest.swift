//  Copyright © 2026 Polar. All rights reserved.

import XCTest
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif
@testable import iOSCommunications

final class PmdActiveMeasurementTest: XCTestCase {
    func testPmdMeasurementTypeLookupDelegatesKnownIdsToSharedBridgeWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("ACC", PmdControlPointRuntimePlanner.measurementTypeName(id: 0xC2))
        XCTAssertNil(PmdControlPointRuntimePlanner.measurementTypeName(id: 4))
        #endif
        XCTAssertEqual(.ecg, PmdMeasurementType.fromId(id: 0))
        XCTAssertEqual(.acc, PmdMeasurementType.fromId(id: 0xC2))
        XCTAssertEqual(.gyro, PmdMeasurementType.fromId(id: 5))
        XCTAssertEqual(.mgn, PmdMeasurementType.fromId(id: 6))
        XCTAssertEqual(.skinTemperature, PmdMeasurementType.fromId(id: 7))
        XCTAssertEqual(.offline_recording, PmdMeasurementType.fromId(id: 13))
        XCTAssertEqual(.offline_hr, PmdMeasurementType.fromId(id: 14))
        XCTAssertEqual(.unknown_type, PmdMeasurementType.fromId(id: 4))
        XCTAssertEqual(.unknown_type, PmdMeasurementType.fromId(id: 0xFF))
    }

    func testPmdActiveMeasurementGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadActiveMeasurementGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected PMD active-measurement golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
            let responseByte = UInt8(truncating: try XCTUnwrap(input["responseByte"] as? NSNumber, id))
            let activeMeasurement = PmdActiveMeasurement.fromStatusResponse(responseByte: responseByte)

            XCTAssertEqual(try XCTUnwrap(expected["activeMeasurementIOS"] as? String, id), activeMeasurement.vectorName, id)
            XCTAssertEqual(try XCTUnwrap(expected["activeBits"] as? NSNumber, id).uint8Value, (responseByte & 0xC0) >> 6, id)
            XCTAssertEqual(try XCTUnwrap(expected["measurementBits"] as? NSNumber, id).uint8Value, responseByte & 0x3F, id)
        }
    }

    func testPmdActiveMeasurementGoldenVectorsFollowNeutralKmpVectorShape() throws {
        for vector in try loadActiveMeasurementGoldenVectors() {
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

    func testPmdControlPointReadinessManifestPinsActiveMeasurementCoverage() throws {
        let manifest = try loadPmdControlPointReadinessManifest()
        let input = try XCTUnwrap(manifest["input"] as? [String: Any])
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        let policyPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let platforms = try XCTUnwrap(manifest["platforms"] as? [String: Any])

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
        XCTAssertEqual(platforms["android"] as? Bool, true)
        XCTAssertEqual(platforms["ios"] as? Bool, true)
        XCTAssertEqual(platforms["common"] as? Bool, true)
    }

    private func loadActiveMeasurementGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/pmd")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("active-measurement-") }
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

}

private extension PmdActiveMeasurement {
    var vectorName: String {
        switch self {
        case .no_measurement_active:
            return "no_measurement_active"
        case .online_measurement_active:
            return "online_measurement_active"
        case .offline_measurement_active:
            return "offline_measurement_active"
        case .online_offline_measurement_active:
            return "online_offline_measurement_active"
        }
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

private let pmdControlPointReadinessCommonDecision = "PMD control-point shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS control-point and active-measurement tests continue to reference the same vectors, active-measurement bit decoding and platform state names, success response parsing, more flag and parameter extraction, settings and measurement-status responses, all status-code mappings, unknown measurement type handling, deterministic short-payload error policy, and compile verification remain explicit before production response parsing moves."
