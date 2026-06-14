// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation
import XCTest
@testable import PolarBleSdk

final class KvtxScriptUtilsTests: XCTestCase {

    // MARK: - buildWriteAndCommit

    func test_buildWriteAndCommit_producesCorrectHeader() {
        let data: [UInt8] = [0xAA, 0xBB, 0xCC]
        let key: UInt32 = 0x0000_0007
        let script = KvtxScriptUtils.buildWriteAndCommit(kvKey: key, data: data)

        // byte 0: CMD_WRITE_BYTES (0x00)
        XCTAssertEqual(script[0], KvtxScriptUtils.CMD_WRITE_BYTES)

        // bytes 1-4: key little-endian
        XCTAssertEqual(script[1], 0x07)
        XCTAssertEqual(script[2], 0x00)
        XCTAssertEqual(script[3], 0x00)
        XCTAssertEqual(script[4], 0x00)

        // bytes 5-8: length little-endian (3)
        XCTAssertEqual(script[5], 0x03)
        XCTAssertEqual(script[6], 0x00)
        XCTAssertEqual(script[7], 0x00)
        XCTAssertEqual(script[8], 0x00)

        // bytes 9-11: payload
        XCTAssertEqual(script[9],  0xAA)
        XCTAssertEqual(script[10], 0xBB)
        XCTAssertEqual(script[11], 0xCC)

        // last byte: CMD_COMMIT (0x05)
        XCTAssertEqual(script.last, KvtxScriptUtils.CMD_COMMIT)

        // total length = 1 + 4 + 4 + 3 + 1 = 13
        XCTAssertEqual(script.count, 13)
    }

    func test_buildWriteAndCommit_emptyData() {
        let script = KvtxScriptUtils.buildWriteAndCommit(kvKey: 1, data: [])
        // 1 (cmd) + 4 (key) + 4 (len=0) + 0 (data) + 1 (commit) = 10
        XCTAssertEqual(script.count, 10)
        XCTAssertEqual(script[0], KvtxScriptUtils.CMD_WRITE_BYTES)
        XCTAssertEqual(script.last, KvtxScriptUtils.CMD_COMMIT)
    }

    // MARK: - extractValueForKey - basic round-trip

    func test_extractValueForKey_roundTrip() {
        let payload: [UInt8] = [1, 2, 3, 4, 5]
        let key: UInt32 = 0xDEAD_BEEF
        let script = KvtxScriptUtils.buildWriteAndCommit(kvKey: key, data: payload)
        let extracted = KvtxScriptUtils.extractValueForKey(script: script, kvKey: key)
        XCTAssertEqual(extracted, payload)
    }

    func test_extractValueForKey_wrongKey_returnsNil() {
        let script = KvtxScriptUtils.buildWriteAndCommit(kvKey: 0x01, data: [0xFF])
        let result = KvtxScriptUtils.extractValueForKey(script: script, kvKey: 0x02)
        XCTAssertNil(result)
    }

    func test_extractValueForKey_emptyScript_returnsNil() {
        let result = KvtxScriptUtils.extractValueForKey(script: [], kvKey: 1)
        XCTAssertNil(result)
    }

    // MARK: - CMD_REMOVE

    func test_extractValueForKey_removedKey_returnsNil() {
        let key: UInt32 = 0x42
        // WRITE_BYTES key data + REMOVE key + COMMIT
        var script = KvtxScriptUtils.buildWriteAndCommit(kvKey: key, data: [0x01])
        // Insert a REMOVE before the trailing COMMIT
        let removeBytes: [UInt8] = [KvtxScriptUtils.CMD_REMOVE] + KvtxScriptUtils.u32Le(key)
        // Remove the trailing COMMIT, add REMOVE + COMMIT
        script = Array(script.dropLast()) + removeBytes + [KvtxScriptUtils.CMD_COMMIT]
        let result = KvtxScriptUtils.extractValueForKey(script: script, kvKey: key)
        XCTAssertNil(result)
    }

    // MARK: - CMD_APPEND_BYTES

    func test_extractValueForKey_appendBytes_concatenatesData() {
        let key: UInt32 = 0x10
        let part1: [UInt8] = [0xAA, 0xBB]
        let part2: [UInt8] = [0xCC, 0xDD]

        // Build: WRITE_BYTES(key, part1) + APPEND_BYTES(key, part2) + COMMIT
        var script: [UInt8] = []
        script += [KvtxScriptUtils.CMD_WRITE_BYTES] + KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(UInt32(part1.count)) + part1
        script += [KvtxScriptUtils.CMD_APPEND_BYTES] + KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(UInt32(part2.count)) + part2
        script += [KvtxScriptUtils.CMD_COMMIT]

        let result = KvtxScriptUtils.extractValueForKey(script: script, kvKey: key)
        XCTAssertEqual(result, part1 + part2)
    }

    // MARK: - Multiple keys

    func test_extractValueForKey_multipleKeys_returnsCorrectOne() {
        let keyA: UInt32 = 1
        let keyB: UInt32 = 2
        var script: [UInt8] = []
        script += [KvtxScriptUtils.CMD_WRITE_BYTES] + KvtxScriptUtils.u32Le(keyA) + KvtxScriptUtils.u32Le(3) + [0xA1, 0xA2, 0xA3]
        script += [KvtxScriptUtils.CMD_WRITE_BYTES] + KvtxScriptUtils.u32Le(keyB) + KvtxScriptUtils.u32Le(2) + [0xB1, 0xB2]
        script += [KvtxScriptUtils.CMD_COMMIT]

        XCTAssertEqual(KvtxScriptUtils.extractValueForKey(script: script, kvKey: keyA), [0xA1, 0xA2, 0xA3])
        XCTAssertEqual(KvtxScriptUtils.extractValueForKey(script: script, kvKey: keyB), [0xB1, 0xB2])
    }

    // MARK: - CMD_COPY / CMD_MOVE are skipped

    func test_extractValueForKey_copyCommand_isSkipped() {
        let key: UInt32 = 5
        var script: [UInt8] = []
        script += [KvtxScriptUtils.CMD_WRITE_BYTES] + KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(1) + [0x99]
        // COPY command: just 2 x uint32 args, no payload
        script += [KvtxScriptUtils.CMD_COPY] + KvtxScriptUtils.u32Le(99) + KvtxScriptUtils.u32Le(100)
        script += [KvtxScriptUtils.CMD_COMMIT]

        let result = KvtxScriptUtils.extractValueForKey(script: script, kvKey: key)
        XCTAssertEqual(result, [0x99])
    }

    // MARK: - u32Le helper

    func test_u32Le_encoding() {
        let bytes = KvtxScriptUtils.u32Le(0x01020304)
        XCTAssertEqual(bytes, [0x04, 0x03, 0x02, 0x01])
    }

    func test_u32Le_zero() {
        XCTAssertEqual(KvtxScriptUtils.u32Le(0), [0, 0, 0, 0])
    }

    func test_u32Le_maxValue() {
        XCTAssertEqual(KvtxScriptUtils.u32Le(0xFFFF_FFFF), [0xFF, 0xFF, 0xFF, 0xFF])
    }

    func testKvtxGoldenVectorsMatchIOSBehavior() throws {
        let vectors = try loadKvtxGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected KVTX golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            if let platforms = vector["platforms"] as? [String: Any],
               let supported = platforms["ios"] as? Bool,
               !supported {
                continue
            }
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
            let script: [UInt8]
            if (input["operation"] as? String) == "buildWriteAndCommit" {
                let key = try XCTUnwrap(input["key"] as? NSNumber, id).uint32Value
                let data = try bytes(fromHex: try XCTUnwrap(input["dataHex"] as? String, id))
                script = KvtxScriptUtils.buildWriteAndCommit(kvKey: key, data: data)
            } else {
                script = try bytes(fromHex: try XCTUnwrap(input["scriptHex"] as? String, id))
            }

            if let scriptHex = expected["scriptHex"] as? String {
                XCTAssertEqual(script, try bytes(fromHex: scriptHex), id)
            }
            if let firstOpcode = expected["firstOpcode"] as? NSNumber {
                XCTAssertEqual(script.first, UInt8(firstOpcode.intValue), id)
            }
            if let commitOpcode = expected["commitOpcode"] as? NSNumber {
                XCTAssertEqual(script.last, UInt8(commitOpcode.intValue), id)
            }

            let extractKey = ((input["extractKey"] as? NSNumber) ?? (expected["extractKey"] as? NSNumber))
            let extracted = KvtxScriptUtils.extractValueForKey(script: script, kvKey: try XCTUnwrap(extractKey, id).uint32Value)
            if expected["extractedHex"] is NSNull {
                XCTAssertNil(extracted, id)
            } else {
                XCTAssertEqual(extracted, try bytes(fromHex: try XCTUnwrap(expected["extractedHex"] as? String, id)), id)
            }
        }
    }

    func testKvtxGoldenVectorsFollowNeutralKmpVectorShape() throws {
        for vector in try loadKvtxGoldenVectors() {
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

    private func loadKvtxGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/kvtx")
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
                return input?["kind"] as? String != "kvtxReadiness"
            }
    }

    func testKvtxReadinessManifestPinsScriptOwnership() throws {
        let file = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/kvtx/kvtx-readiness.json")
        let data = try Data(contentsOf: file)
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
        XCTAssertEqual(vector["id"] as? String, "kvtx-readiness")
        let input = try XCTUnwrap(vector["input"] as? [String: Any], "kvtx-readiness.json")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "kvtx-readiness.json")
        XCTAssertEqual(input["kind"] as? String, "kvtxReadiness")
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], "kvtx-readiness.json")
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], "kvtx-readiness.json")
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], "kvtx-readiness.json")
        XCTAssertEqual(policyVectorPaths, [
            "sdk/kvtx/write-commit-basic.json",
            "sdk/kvtx/write-commit-empty-data.json",
            "sdk/kvtx/write-commit-uint32-max-key.json",
            "sdk/kvtx/multiple-keys-select-second.json",
            "sdk/kvtx/append-concatenates.json",
            "sdk/kvtx/remove-clears-value.json",
            "sdk/kvtx/write-ex-zero-index.json",
            "sdk/kvtx/append-ex-zero-index.json",
            "sdk/kvtx/remove-ex-zero-index.json",
            "sdk/kvtx/write-ex-nonempty-index-ignored.json",
            "sdk/kvtx/unknown-command-stops-with-current-value.json",
            "sdk/kvtx/truncated-write-payload-android-error.json",
            "sdk/kvtx/truncated-write-payload-ios-nil.json"
        ])
        let expectedFamilies = [
            "write-and-commit-framing",
            "empty-data-write-framing",
            "unsigned-uint32-key-preservation",
            "multiple-key-selection",
            "append-concatenation",
            "remove-clears-current-value",
            "extended-write-zero-index",
            "extended-append-zero-index",
            "extended-remove-zero-index",
            "extended-nonempty-index-ignore-policy",
            "unknown-command-stop-policy",
            "malformed-script-typed-error-policy",
            "platform-truncated-payload-vector-reference-gate",
            "platform-kvtx-vector-reference-gate",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "KVTX shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS KVTX tests continue to reference the same vectors, write-and-commit framing, empty data writes, unsigned 32-bit keys, multiple-key selection, append/remove behavior, EX zero-index behavior, non-empty EX index ignore policy, unknown-command stop policy, malformed-script typed error policy, truncated payload platform vectors, and the shared tests are compile-verified.")
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any], "kvtx-readiness.json")
        let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], "kvtx-readiness.json")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "kvtx-readiness.json"), ["com.polar.sdk.impl.utils.KvtxScriptUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "kvtx-readiness.json"), ["KvtxScriptUtilsTests"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "kvtx-readiness.json"), ["com.polar.sharedtest.KvtxCommonPolicyTest"])
        XCTAssertEqual(platforms["android"] as? Bool, true)
        XCTAssertEqual(platforms["ios"] as? Bool, true)
        XCTAssertEqual(platforms["common"] as? Bool, true)
    }


    private func bytes(fromHex hexString: String) throws -> [UInt8] {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "KvtxScriptUtilsTests", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "KvtxScriptUtilsTests", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        return bytes
    }
}
