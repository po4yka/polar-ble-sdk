//  Copyright © 2024 Polar. All rights reserved.

import Foundation
import XCTest
@testable import PolarBleSdk

class PolarDeviceUuidTest: XCTestCase {
    func testProductionUuidUtilityUsesSharedKmpPolicyWhenLinked() throws {
        XCTAssertEqual("0e030000-0084-0000-0000-000089643A20", try PolarDeviceUuid.fromDeviceId("89643A20"))
    }

    func testFromDeviceIdShouldGeneratePolarDeviceUuidString() throws {
        // Arrange
        let deviceId = "89643A20"
        let expectedUuid = "0e030000-0084-0000-0000-000089643A20"

        // Act
        let generatedUuid = try PolarDeviceUuid.fromDeviceId(deviceId)

        // Assert
        XCTAssertEqual(expectedUuid, generatedUuid)
    }

    func testFromDeviceIdShouldThrowExceptionIfDeviceIdIsInvalid() {
        // Arrange
        let invalidDeviceId = "123456789"

        // Act & Assert
        XCTAssertThrowsError(try PolarDeviceUuid.fromDeviceId(invalidDeviceId)) { error in
            XCTAssertTrue(error is PolarDeviceUuid.PolarDeviceUuidError)
            if case let PolarDeviceUuid.PolarDeviceUuidError.invalidDeviceIdLength(expected, actual) = error {
                XCTAssertEqual(expected, 8)
                XCTAssertEqual(actual, 9)
            } else {
                XCTFail("Expected invalidDeviceIdLength error")
            }
        }
    }

    func testDeviceIdGoldenVectorsMatchPolarDeviceUuidBehavior() throws {
        let vectors = try loadDeviceIdGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected device ID golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
            let deviceId = try XCTUnwrap(input["deviceId"] as? String, id)

            if let expectedUuid = expected["uuid"] as? String {
                XCTAssertEqual(expectedUuid, try PolarDeviceUuid.fromDeviceId(deviceId), id)
            }

            if expected["uuidError"] != nil {
                XCTAssertThrowsError(try PolarDeviceUuid.fromDeviceId(deviceId), id) { error in
                    guard case let PolarDeviceUuid.PolarDeviceUuidError.invalidDeviceIdLength(expectedLength, actualLength) = error else {
                        XCTFail("\(id) expected invalidDeviceIdLength error")
                        return
                    }
                    XCTAssertEqual((expected["expectedLength"] as? NSNumber)?.intValue, expectedLength, id)
                    XCTAssertEqual((expected["actualLength"] as? NSNumber)?.intValue, actualLength, id)
                }
            }
        }
    }

    func testDeviceIdGoldenVectorsFollowNeutralKmpVectorShape() throws {
        for vector in try loadDeviceIdGoldenVectors() {
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

    func testDeviceIdReadinessManifestPinsUuidOwnership() throws {
        let manifest = try loadDeviceIdReadinessManifest()
        let input = try XCTUnwrap(manifest["input"] as? [String: Any])
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        let policyPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])

        XCTAssertEqual("device-id-readiness", manifest["id"] as? String)
        XCTAssertEqual("deviceIdReadiness", input["kind"] as? String)
        XCTAssertEqual(DEVICE_ID_READINESS_POLICY_VECTOR_PATHS, policyPaths)
        XCTAssertEqual(DEVICE_ID_READINESS_FAMILIES, requiredFamilies)
        XCTAssertEqual(DEVICE_ID_READINESS_FAMILIES, coveredFamilies)
        XCTAssertEqual(DEVICE_ID_READINESS_COMMON_DECISION, try XCTUnwrap(expected["commonDecision"] as? String))
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any])
        let platforms = try XCTUnwrap(manifest["platforms"] as? [String: Any])
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.androidcommunications.api.ble.model.polar.DeviceIdGoldenVectorTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["BlePolarDeviceIdUtilityTest", "PolarDeviceUuidTest", "PolarServiceClientUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.DeviceIdCommonPolicyTest"])
        XCTAssertEqual(platforms["android"] as? Bool, true)
        XCTAssertEqual(platforms["ios"] as? Bool, true)
        XCTAssertEqual(platforms["common"] as? Bool, true)
    }

    private func loadDeviceIdGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/device-id")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && !$0.lastPathComponent.hasPrefix("identifier-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                (vector["input"] as? [String: Any])?["kind"] as? String != "deviceIdReadiness"
            }
    }

    private func loadDeviceIdReadinessManifest() throws -> [String: Any] {
        let manifestUrl = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/device-id/device-id-readiness.json")
        let data = try Data(contentsOf: manifestUrl)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], manifestUrl.path)
    }

    private let DEVICE_ID_READINESS_POLICY_VECTOR_PATHS = [
        "protocol/device-id/assemble-seven-digit-device-id.json",
        "protocol/device-id/assemble-six-digit-device-id.json",
        "protocol/device-id/assemble-zero-seven-digit-device-id.json",
        "protocol/device-id/empty-device-id-platform-difference.json",
        "protocol/device-id/identifier-bluetooth-address-android.json",
        "protocol/device-id/identifier-invalid-format.json",
        "protocol/device-id/identifier-uuid-string-ios.json",
        "protocol/device-id/invalid-checksum-device-id.json",
        "protocol/device-id/non-hex-device-id-platform-difference.json",
        "protocol/device-id/polar-device-uuid-invalid-length.json",
        "protocol/device-id/polar-device-uuid-valid.json",
        "protocol/device-id/valid-lowercase-device-id.json"
    ]

    private let DEVICE_ID_READINESS_FAMILIES = [
        "checksum-width-6-assembly",
        "checksum-width-7-assembly",
        "zero-device-id-assembly",
        "valid-device-id-validation",
        "invalid-checksum-validation",
        "lowercase-device-id-validation",
        "empty-input-platform-decision",
        "non-hex-input-platform-decision",
        "uuid-conversion",
        "uuid-invalid-length-error",
        "identifier-invalid-format-error",
        "platform-specific-identifier-routing",
        "platform-device-id-vector-reference-gate",
        "compile-verification-gate"
    ]

    private let DEVICE_ID_READINESS_COMMON_DECISION = "Device ID shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS device ID tests continue to reference the same vectors, checksum width 6 and 7 assembly, zero-value assembly, validation, lowercase acceptance, UUID conversion, invalid UUID length errors, invalid identifier rejection, current empty and non-hex platform decisions, platform-specific identifier routing, and compile verification remain explicit before production checksum or UUID logic moves."

}
