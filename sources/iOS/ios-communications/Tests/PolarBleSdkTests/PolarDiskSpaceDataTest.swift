//  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk

private let DISK_SPACE_READINESS_POLICY_VECTOR_PATHS = [
    "sdk/disk-space/typical-fragments.json",
    "sdk/disk-space/zero-fragments.json",
    "sdk/disk-space/uint32-max-fragment-platform-difference.json",
    "sdk/disk-space/malformed-truncated-varint.json"
]

private let DISK_SPACE_READINESS_FAMILIES = [
    "byte-total-calculation",
    "free-byte-calculation",
    "zero-fragment-counts",
    "unsigned-uint32-fragment-size-policy",
    "android-signed-fragment-platform-reference",
    "ios-unsigned-fragment-platform-reference",
    "typed-malformed-varint-parse-error",
    "platform-disk-space-vector-reference-gate",
    "compile-verification-gate"
]

private let DISK_SPACE_READINESS_COMMON_DECISION = "Disk-space model migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS disk-space tests continue to reference the same vectors, byte-total and free-byte calculations remain covered, zero-fragment counts remain explicit, fragment size uses the unsigned 32-bit policy instead of inheriting Android signed-int exposure or Swift UInt32 behavior accidentally, malformed truncated varints map to typed parse errors, and the shared tests are compile-verified."

final class PolarDiskSpaceDataTest: XCTestCase {

    func testFromProto() throws {
        // Arrange
        let proto = Protocol_PbPFtpDiskSpaceResult.with {
            $0.fragmentSize = 512
            $0.totalFragments = 2048
            $0.freeFragments = 1024
        }
        // Act
        let result = PolarDiskSpaceData.fromProto(proto: proto)
        
        // Assert
        XCTAssertEqual(1048576, result.totalSpace)
        XCTAssertEqual(524288, result.freeSpace)
    }

    func testGoldenVectorsConvertProtoFieldsToByteTotals() throws {
        for vector in try loadDiskSpaceGoldenVectors().filter({ (($0["input"] as? [String: Any])?["proto"]) != nil }) {
            let id = try XCTUnwrap(vector["id"] as? String)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let protoFields = try XCTUnwrap(input["proto"] as? [String: Any], id)
            let expected = try expectedForIOS(vector, id: id)
            let fragmentSize = try UInt32(number(protoFields, "fragmentSize", id: id))
            let totalFragments = try number(protoFields, "totalFragments", id: id)
            let freeFragments = try number(protoFields, "freeFragments", id: id)
            let proto = Protocol_PbPFtpDiskSpaceResult.with {
                $0.fragmentSize = fragmentSize
                $0.totalFragments = totalFragments
                $0.freeFragments = freeFragments
            }

            let result = PolarDiskSpaceData.fromProto(proto: proto)

            XCTAssertEqual(try number(expected, "totalSpace", id: id), result.totalSpace, id)
            XCTAssertEqual(try number(expected, "freeSpace", id: id), result.freeSpace, id)
        }
    }

    func testMalformedProtobufVectorsFailToParse() throws {
        for vector in try loadDiskSpaceGoldenVectors().filter({ (($0["expected"] as? [String: Any])?["error"] as? String) == "parse-error" }) {
            let id = try XCTUnwrap(vector["id"] as? String)
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let payload = try Data(hex: XCTUnwrap(input["hex"] as? String, id))

            XCTAssertThrowsError(try Protocol_PbPFtpDiskSpaceResult(serializedBytes: payload), id)
        }
    }

    func testDiskSpaceGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadDiskSpaceGoldenVectors() {
            let id = try XCTUnwrap(vector["id"] as? String)
            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertEqual(platforms["android"] as? Bool, true, id)
            XCTAssertEqual(platforms["ios"] as? Bool, true, id)
            XCTAssertEqual(platforms["common"] as? Bool, true, id)
        }
    }

    private func loadDiskSpaceGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/disk-space")
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
                return input?["kind"] as? String != "diskSpaceReadiness"
            }
    }

    func testDiskSpaceReadinessManifestIsPinnedBeforeModelMigration() throws {
        let file = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/disk-space/disk-space-readiness.json")
        let data = try Data(contentsOf: file)
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
        XCTAssertEqual(vector["id"] as? String, "disk-space-readiness")
        let input = try XCTUnwrap(vector["input"] as? [String: Any], "disk-space-readiness.json")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "disk-space-readiness.json")
        XCTAssertEqual(input["kind"] as? String, "diskSpaceReadiness")
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], "disk-space-readiness.json")
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], "disk-space-readiness.json")
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], "disk-space-readiness.json")
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any], "disk-space-readiness.json")
        XCTAssertEqual(DISK_SPACE_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths, "disk-space-readiness")
        XCTAssertEqual(DISK_SPACE_READINESS_FAMILIES, requiredFamilies, "disk-space-readiness")
        XCTAssertEqual(DISK_SPACE_READINESS_FAMILIES, coveredFamilies, "disk-space-readiness")
        XCTAssertEqual(expected["commonDecision"] as? String, DISK_SPACE_READINESS_COMMON_DECISION, "disk-space-readiness")
        XCTAssertEqual(consumerTests["android"] as? [String], ["com.polar.sdk.api.model.PolarDiskSpaceTest"], "disk-space-readiness")
        XCTAssertEqual(consumerTests["ios"] as? [String], ["PolarDiskSpaceDataTest"], "disk-space-readiness")
        XCTAssertEqual(consumerTests["commonPrototype"] as? [String], ["com.polar.sharedtest.DiskSpaceCommonPolicyTest"], "disk-space-readiness")
    }


    private func expectedForIOS(_ vector: [String: Any], id: String) throws -> [String: Any] {
        if let platforms = vector["platformExpectations"] as? [String: Any] {
            let ios = try XCTUnwrap(platforms["ios"] as? [String: Any], id)
            return ios
        }
        return try XCTUnwrap(vector["expected"] as? [String: Any], id)
    }

    private func number(_ object: [String: Any], _ key: String, id: String) throws -> UInt64 {
        if let number = object[key] as? NSNumber {
            return number.uint64Value
        }
        throw NSError(domain: "PolarDiskSpaceDataTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Missing numeric \(key) in \(id)"])
    }
}

private extension Data {
    init(hex: String) throws {
        guard hex.count % 2 == 0 else {
            throw NSError(domain: "PolarDiskSpaceDataTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Odd-length hex"])
        }
        var data = Data()
        var index = hex.startIndex
        while index < hex.endIndex {
            let next = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<next], radix: 16) else {
                throw NSError(domain: "PolarDiskSpaceDataTest", code: 4, userInfo: [NSLocalizedDescriptionKey: "Invalid hex"])
            }
            data.append(byte)
            index = next
        }
        self = data
    }
}
