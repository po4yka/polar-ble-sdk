// Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

/// Tests for `BlePolarDeviceIdUtility`.
///
/// All expected values are derived from hand-tracing the algorithm:
///
/// checkSumForDeviceId(width=8)
///   a2 = bits[7:4],  siftOffset = 8
///   a3…a8 = consecutive 4-bit nibbles starting at bit 8
///   component = 3*(a2+a4+a6+a8) + a3+a5+a7
///   return component % 16
///
/// checkSumForDeviceId(width=7)
///   a2 = bits[3:0],  siftOffset = 4
///   (same formula)
///
/// checkSumForDeviceId(width=6)
///   a2 = 0x01 (hardcoded),  siftOffset = 0
///   (same formula — a3 starts at bit 0)
///
/// checkSumForDeviceId(other width) → 0
final class BlePolarDeviceIdUtilityTest: XCTestCase {
    func testProductionDeviceIdUtilityCanUseSharedKmpBridgeWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("1234567C", PolarDeviceIdRuntimePlanner.assembleFullDeviceId(0x1234567, width: 7))
        XCTAssertTrue(try XCTUnwrap(PolarDeviceIdRuntimePlanner.isValidDeviceId("1234567C")))
        XCTAssertEqual("1234567C", BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0x1234567, width: 7))
        XCTAssertTrue(BlePolarDeviceIdUtility.isValidDeviceId("1234567C"))
        #else
        throw XCTSkip("PolarBleSdkShared is not linked in this build")
        #endif
    }

    // MARK: - checkSumForDeviceId – width 8

    func testCheckSum_width8_allZeros_returns0() {
        // All nibbles zero → component = 0 → 0 % 16 = 0
        XCTAssertEqual(0, BlePolarDeviceIdUtility.checkSumForDeviceId(0x00000000, width: 8))
    }

    func testCheckSum_width8_1234567C_returns12() {
        // a2=7, a3=6, a4=5, a5=4, a6=3, a7=2, a8=1
        // 3*(7+5+3+1) + 6+4+2 = 48+12 = 60 → 60%16 = 12
        XCTAssertEqual(12, BlePolarDeviceIdUtility.checkSumForDeviceId(0x1234567C, width: 8))
    }

    func testCheckSum_width8_ABCDEF03_returns3() {
        // a2=0, a3=15, a4=14, a5=13, a6=12, a7=11, a8=10
        // 3*(0+14+12+10) + 15+13+11 = 108+39 = 147 → 147%16 = 3
        XCTAssertEqual(3, BlePolarDeviceIdUtility.checkSumForDeviceId(0xABCDEF03, width: 8))
    }

    func testCheckSum_width8_FFFFFFFF_returns1() {
        // a2=15, a3…a8 all 15
        // 3*(15+15+15+15) + 15+15+15 = 180+45 = 225 → 225%16 = 1
        XCTAssertEqual(1, BlePolarDeviceIdUtility.checkSumForDeviceId(0xFFFFFFFF, width: 8))
    }

    // MARK: - checkSumForDeviceId – width 7

    func testCheckSum_width7_1234567_returns12() {
        // a2=7 (bits[3:0]), siftOffset=4
        // a3=6, a4=5, a5=4, a6=3, a7=2, a8=1
        // component same as w=8 case above → 60%16 = 12
        XCTAssertEqual(12, BlePolarDeviceIdUtility.checkSumForDeviceId(0x1234567, width: 7))
    }

    func testCheckSum_width7_allZeros_returns0() {
        // a2=0, all others 0 → 0
        XCTAssertEqual(0, BlePolarDeviceIdUtility.checkSumForDeviceId(0x00000000, width: 7))
    }

    // MARK: - checkSumForDeviceId – width 6

    func testCheckSum_width6_allZeros_returns3() {
        // a2 is hardcoded to 0x01, siftOffset=0, all a3…a8=0
        // component = 3*1 = 3 → 3%16 = 3
        XCTAssertEqual(3, BlePolarDeviceIdUtility.checkSumForDeviceId(0x00000000, width: 6))
    }

    func testCheckSum_width6_123456_returns10() {
        // a2=0x01, a3=6, a4=5, a5=4, a6=3, a7=2, a8=1
        // 3*(1+5+3+1) + 6+4+2 = 30+12 = 42 → 42%16 = 10
        XCTAssertEqual(10, BlePolarDeviceIdUtility.checkSumForDeviceId(0x123456, width: 6))
    }

    // MARK: - checkSumForDeviceId – unsupported width

    func testCheckSum_unsupportedWidth_returns0() {
        XCTAssertEqual(0, BlePolarDeviceIdUtility.checkSumForDeviceId(0x1234567C, width: 9))
        XCTAssertEqual(0, BlePolarDeviceIdUtility.checkSumForDeviceId(0x1234567C, width: 0))
        XCTAssertEqual(0, BlePolarDeviceIdUtility.checkSumForDeviceId(0x1234567C, width: 5))
    }

    // MARK: - isValidDeviceId – valid 8-character IDs

    func testIsValidDeviceId_allZeros_returnsTrue() {
        // checksum = 0, last nibble = 0 → match
        XCTAssertTrue(BlePolarDeviceIdUtility.isValidDeviceId("00000000"))
    }

    func testIsValidDeviceId_1234567C_returnsTrue() {
        // checksum = 12 (0xC), last nibble = 0xC → match
        XCTAssertTrue(BlePolarDeviceIdUtility.isValidDeviceId("1234567C"))
    }

    func testIsValidDeviceId_1234567c_lowercase_returnsTrue() {
        // UInt32(_:radix:) is case-insensitive for hex digits
        XCTAssertTrue(BlePolarDeviceIdUtility.isValidDeviceId("1234567c"))
    }

    func testIsValidDeviceId_ABCDEF03_returnsTrue() {
        // checksum = 3, last nibble = 3 → match
        XCTAssertTrue(BlePolarDeviceIdUtility.isValidDeviceId("ABCDEF03"))
    }

    // MARK: - isValidDeviceId – invalid 8-character IDs

    func testIsValidDeviceId_12345678_returnsFalse() {
        // checksum = 12 (0xC), last nibble = 8 → mismatch
        XCTAssertFalse(BlePolarDeviceIdUtility.isValidDeviceId("12345678"))
    }

    func testIsValidDeviceId_ABCDEF04_returnsFalse() {
        // checksum = 3, last nibble = 4 → mismatch
        XCTAssertFalse(BlePolarDeviceIdUtility.isValidDeviceId("ABCDEF04"))
    }

    func testIsValidDeviceId_FFFFFFFF_returnsFalse() {
        // checksum = 1, last nibble = 0xF = 15 → mismatch
        XCTAssertFalse(BlePolarDeviceIdUtility.isValidDeviceId("FFFFFFFF"))
    }

    func testIsValidDeviceId_invalidHex8Chars_returnsFalse() {
        // UInt32(_:radix:16) fails for non-hex characters → false
        XCTAssertFalse(BlePolarDeviceIdUtility.isValidDeviceId("ZZZZZZZZ"))
        XCTAssertFalse(BlePolarDeviceIdUtility.isValidDeviceId("ABCDEF0G"))
    }

    // MARK: - isValidDeviceId – non-8-character strings (checksum != 0 path)

    func testIsValidDeviceId_emptyString_returnsFalse() {
        // strtouq("") = 0 → checksum = 0 → false
        XCTAssertFalse(BlePolarDeviceIdUtility.isValidDeviceId(""))
    }

    func testIsValidDeviceId_singleZero_returnsFalse() {
        // strtouq("0") = 0 → checksum = 0 → false
        XCTAssertFalse(BlePolarDeviceIdUtility.isValidDeviceId("0"))
    }

    func testIsValidDeviceId_sevenZeros_returnsFalse() {
        // strtouq("0000000") = 0 → checksum = 0 → false
        XCTAssertFalse(BlePolarDeviceIdUtility.isValidDeviceId("0000000"))
    }

    func testIsValidDeviceId_nonZero7CharString_returnsTrue() {
        // "FFFFFF0" → strtouq = 0x0FFFFFF0
        // a2=15, a3…a7=15, a8=0
        // 3*(15+15+15+0)+15+15+15 = 135+45 = 180 → 180%16 = 4 ≠ 0 → true
        XCTAssertTrue(BlePolarDeviceIdUtility.isValidDeviceId("FFFFFF0"))
    }

    // MARK: - assemblyFullPolarDeviceId – width 6

    func testAssemblyFullId_width6_123456() {
        // checksum(0x123456, w=6) = 10 (A)
        // format "%06X1%01X" → "123456" + "1" + "A" = "1234561A"
        XCTAssertEqual("1234561A", BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0x123456, width: 6))
    }

    func testAssemblyFullId_width6_allZeros() {
        // checksum(0, w=6) = 3
        // "%06X1%01X" → "000000" + "1" + "3" = "00000013"
        XCTAssertEqual("00000013", BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0x000000, width: 6))
    }

    // MARK: - assemblyFullPolarDeviceId – width 7

    func testAssemblyFullId_width7_1234567() {
        // checksum(0x1234567, w=7) = 12 (C)
        // format "%07X%01X" → "1234567" + "C" = "1234567C"
        XCTAssertEqual("1234567C", BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0x1234567, width: 7))
    }

    func testAssemblyFullId_width7_allZeros() {
        // checksum(0, w=7) = 0
        // "%07X%01X" → "0000000" + "0" = "00000000"
        XCTAssertEqual("00000000", BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0x0000000, width: 7))
    }

    // MARK: - assemblyFullPolarDeviceId – width 8

    func testAssemblyFullId_width8_1234567C() {
        // format "%08X" → "1234567C"
        XCTAssertEqual("1234567C", BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0x1234567C, width: 8))
    }

    func testAssemblyFullId_width8_ABCDEF03() {
        XCTAssertEqual("ABCDEF03", BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0xABCDEF03, width: 8))
    }

    func testAssemblyFullId_width8_allZeros() {
        XCTAssertEqual("00000000", BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0x00000000, width: 8))
    }

    // MARK: - assemblyFullPolarDeviceId – unsupported width

    func testAssemblyFullId_unsupportedWidth_returnsEmptyString() {
        XCTAssertEqual("", BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0x1234567C, width: 9))
        XCTAssertEqual("", BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0x1234567C, width: 0))
        XCTAssertEqual("", BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0x1234567C, width: 5))
    }

    // MARK: - polarDeviceIdToInt

    func testPolarDeviceIdToInt_validHex_returnsCorrectValue() {
        XCTAssertEqual(0x1234567C, BlePolarDeviceIdUtility.polarDeviceIdToInt("1234567C"))
    }

    func testPolarDeviceIdToInt_lowercase_returnsCorrectValue() {
        XCTAssertEqual(0x1234567C, BlePolarDeviceIdUtility.polarDeviceIdToInt("1234567c"))
    }

    func testPolarDeviceIdToInt_ABCDEF03_returnsCorrectValue() {
        XCTAssertEqual(0xABCDEF03, BlePolarDeviceIdUtility.polarDeviceIdToInt("ABCDEF03"))
    }

    func testPolarDeviceIdToInt_allZeros_returnsZero() {
        XCTAssertEqual(0x00000000, BlePolarDeviceIdUtility.polarDeviceIdToInt("00000000"))
    }

    func testPolarDeviceIdToInt_maxValue_returnsMaxUInt32() {
        XCTAssertEqual(0xFFFFFFFF, BlePolarDeviceIdUtility.polarDeviceIdToInt("FFFFFFFF"))
    }

    func testPolarDeviceIdToInt_emptyString_returnsZero() {
        XCTAssertEqual(0, BlePolarDeviceIdUtility.polarDeviceIdToInt(""))
    }

    func testPolarDeviceIdToInt_invalidHex_returnsZero() {
        XCTAssertEqual(0, BlePolarDeviceIdUtility.polarDeviceIdToInt("ZZZZZZZZ"))
        XCTAssertEqual(0, BlePolarDeviceIdUtility.polarDeviceIdToInt("ABCDEF0G"))
    }

    // MARK: - Round-trip: assembly → isValid (width 7 & 8)

    func testRoundTrip_width7_assembledIdIsValid() {
        // assemblyFullPolarDeviceId with width=7 produces an 8-char string
        // that must pass isValidDeviceId.
        let assembled = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0x1234567, width: 7)
        XCTAssertEqual(8, assembled.count)
        XCTAssertTrue(BlePolarDeviceIdUtility.isValidDeviceId(assembled))
    }

    func testRoundTrip_width8_assembledIdIsValid() {
        let assembled = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0x1234567C, width: 8)
        XCTAssertTrue(BlePolarDeviceIdUtility.isValidDeviceId(assembled))
    }

    func testRoundTrip_width7_allZeros_assembledIdIsValid() {
        let assembled = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(0x0000000, width: 7)
        XCTAssertTrue(BlePolarDeviceIdUtility.isValidDeviceId(assembled))
    }

    // MARK: - Shared golden vectors

    func testDeviceIdGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadDeviceIdGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected device ID golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
            let deviceId = try XCTUnwrap(input["deviceId"] as? String, id)

            if let expectedAssembly = expected["assembled"] as? String {
                let value = BlePolarDeviceIdUtility.polarDeviceIdToInt(deviceId)
                let assembled = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(value, width: deviceId.count)
                XCTAssertEqual(expectedAssembly, assembled, id)

                if let validAfterAssembly = expected["validAfterAssembly"] as? Bool {
                    XCTAssertEqual(validAfterAssembly, BlePolarDeviceIdUtility.isValidDeviceId(assembled), id)
                }
            }

            if let expectedValid = expected["valid"] as? Bool {
                XCTAssertEqual(expectedValid, BlePolarDeviceIdUtility.isValidDeviceId(deviceId), id)
            }

            let platformExpectations = vector["platformExpectations"] as? [String: Any]
            let iosExpectations = platformExpectations?["ios"] as? [String: Any]
            if let expectedValid = iosExpectations?["valid"] as? Bool {
                XCTAssertEqual(expectedValid, BlePolarDeviceIdUtility.isValidDeviceId(deviceId), id)
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

    func testDeviceIdReadinessManifestIsPinnedBeforeChecksumMigration() throws {
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
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.androidcommunications.api.ble.model.polar.DeviceIdGoldenVectorTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["BlePolarDeviceIdUtilityTest", "PolarDeviceUuidTest", "PolarServiceClientUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.DeviceIdCommonPolicyTest"])
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

    private let DEVICE_ID_READINESS_COMMON_DECISION = "Device ID migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS device ID tests continue to reference the same vectors, checksum width 6 and 7 assembly, zero-value assembly, validation, lowercase acceptance, UUID conversion, invalid UUID length errors, invalid identifier rejection, current empty and non-hex platform decisions, platform-specific identifier routing, and compile verification remain explicit before production checksum or UUID logic moves."

}
