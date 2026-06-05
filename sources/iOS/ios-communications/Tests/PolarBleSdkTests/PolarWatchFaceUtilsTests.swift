// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation
import XCTest
@testable import PolarBleSdk

final class PolarWatchFaceUtilsTests: XCTestCase {

    // MARK: - FlatBuffer encode → decode round-trip

    func test_buildAndParseWatchFaceConfig_defaultFields() {
        let fields = WatchfaceConfigFields()
        let bytes = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields: fields)
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: bytes)

        XCTAssertEqual(parsed.timeStyleId, 0)
        XCTAssertEqual(parsed.complicationLayoutId, 0)
        XCTAssertEqual(parsed.backgroundStyleId, 0)
        XCTAssertEqual(parsed.accentColor, 0)
        XCTAssertEqual(parsed.complicationIds, [])
        XCTAssertEqual(parsed.fontfaceId, 0)
    }

    func test_buildAndParseWatchFaceConfig_allScalarsSet() {
        var fields = WatchfaceConfigFields()
        fields.timeStyleId = 3
        fields.complicationLayoutId = 7
        fields.backgroundStyleId = 2
        fields.accentColor = 0xFF_CC_88_00
        fields.fontfaceId = 1

        let bytes = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields: fields)
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: bytes)

        XCTAssertEqual(parsed.timeStyleId, 3)
        XCTAssertEqual(parsed.complicationLayoutId, 7)
        XCTAssertEqual(parsed.backgroundStyleId, 2)
        XCTAssertEqual(parsed.accentColor, 0xFF_CC_88_00)
        XCTAssertEqual(parsed.complicationIds, [])
        XCTAssertEqual(parsed.fontfaceId, 1)
    }

    func test_buildAndParseWatchFaceConfig_withComplications() {
        var fields = WatchfaceConfigFields()
        let heartRateId = PolarWatchFaceComplication.heartRate.id
        let spo2Id      = PolarWatchFaceComplication.spo2.id
        let stepsId     = PolarWatchFaceComplication.activity.id
        fields.complicationIds = [heartRateId, spo2Id, stepsId]

        let bytes = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields: fields)
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: bytes)

        XCTAssertEqual(parsed.complicationIds, [heartRateId, spo2Id, stepsId])
    }

    func test_buildAndParseWatchFaceConfig_preservesOrderOfComplications() {
        var fields = WatchfaceConfigFields()
        let ids: [Int32] = [
            PolarWatchFaceComplication.date.id,
            PolarWatchFaceComplication.battery.id,
            PolarWatchFaceComplication.heartRate.id,
            PolarWatchFaceComplication.empty.id,
            PolarWatchFaceComplication.weather.id,
        ]
        fields.complicationIds = ids

        let bytes = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields: fields)
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: bytes)

        XCTAssertEqual(parsed.complicationIds, ids)
    }

    func test_buildAndParseWatchFaceConfig_singleComplication() {
        var fields = WatchfaceConfigFields()
        fields.complicationIds = [PolarWatchFaceComplication.compass.id]

        let bytes = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields: fields)
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: bytes)

        XCTAssertEqual(parsed.complicationIds, [PolarWatchFaceComplication.compass.id])
    }

    // MARK: - KVTX round-trip (build + extract)

    func test_buildKvtxScript_roundTrip() {
        var fields = WatchfaceConfigFields()
        fields.timeStyleId = 1
        fields.complicationIds = [PolarWatchFaceComplication.heartRate.id]

        let script = PolarWatchFaceUtils.buildKvtxScript(fields: fields)
        let extracted = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script: script)

        XCTAssertNotNil(extracted)

        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: extracted!)
        XCTAssertEqual(parsed.timeStyleId, 1)
        XCTAssertEqual(parsed.complicationIds, [PolarWatchFaceComplication.heartRate.id])
    }

    func test_buildKvtxScript_wrongKey_returnsNil() {
        let fields = WatchfaceConfigFields()
        let script = PolarWatchFaceUtils.buildKvtxScript(fields: fields)

        // Try a different key
        let wrongKey: UInt32 = 0x0000_0001
        let result = KvtxScriptUtils.extractValueForKey(script: script, kvKey: wrongKey)
        XCTAssertNil(result)
    }

    func test_extractWatchFaceConfigFromKvtxScript_emptyScript_returnsNil() {
        let result = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script: [])
        XCTAssertNil(result)
    }

    // MARK: - parseWatchFaceConfigFlatBuffer - edge cases

    func test_parseWatchFaceConfigFlatBuffer_tooShort_returnsDefaults() {
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: [0x00, 0x01])
        XCTAssertEqual(parsed.complicationIds, [])
        XCTAssertEqual(parsed.timeStyleId, 0)
    }

    func test_parseWatchFaceConfigFlatBuffer_emptyBytes_returnsDefaults() {
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: [])
        XCTAssertEqual(parsed.complicationIds, [])
    }

    // MARK: - Complication IDs match Android enum hash codes

    func test_complicationId_heartRate_matchesJavaHashCode() {
        // Java "heart-rate-complication".hashCode() == known value
        let expected = javaHashCode("heart-rate-complication")
        XCTAssertEqual(PolarWatchFaceComplication.heartRate.id, expected)
    }

    func test_complicationId_spo2_matchesJavaHashCode() {
        let expected = javaHashCode("spo2-complication")
        XCTAssertEqual(PolarWatchFaceComplication.spo2.id, expected)
    }

    func test_complicationId_empty_isZero() {
        // empty string has Java hashCode 0
        XCTAssertEqual(PolarWatchFaceComplication.empty.id, 0)
    }

    func test_complicationFromId_roundTrip() {
        for complication in PolarWatchFaceComplication.allCases {
            let resolved = PolarWatchFaceComplication.fromId(complication.id)
            XCTAssertEqual(resolved, complication, "fromId should resolve \(complication)")
        }
    }

    func testWatchFaceKvtxHeadersUseSharedFileFacadePlanning() {
        let readOperation = PolarWatchFaceUtils.watchFaceReadOperation()
        XCTAssertEqual(readOperation.command, .get)
        XCTAssertEqual(readOperation.path, "/SYS/KVTX")

        let writeOperation = PolarWatchFaceUtils.watchFaceWriteOperation()
        XCTAssertEqual(writeOperation.command, .put)
        XCTAssertEqual(writeOperation.path, "/SYS/KVTX")
    }

    // MARK: - Preserve existing non-complication fields on write

    func test_writePreservesExistingFields() {
        // Simulate existing device state
        var existing = WatchfaceConfigFields()
        existing.timeStyleId = 5
        existing.complicationLayoutId = 2
        existing.backgroundStyleId = 3
        existing.accentColor = 0xAABBCCDD
        existing.fontfaceId = 1
        existing.complicationIds = [PolarWatchFaceComplication.date.id]

        // Only change complications
        var merged = existing
        merged.complicationIds = [PolarWatchFaceComplication.heartRate.id, PolarWatchFaceComplication.battery.id]

        let bytes = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields: merged)
        let parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: bytes)

        // Scalars preserved
        XCTAssertEqual(parsed.timeStyleId, 5)
        XCTAssertEqual(parsed.complicationLayoutId, 2)
        XCTAssertEqual(parsed.backgroundStyleId, 3)
        XCTAssertEqual(parsed.accentColor, 0xAABBCCDD)
        XCTAssertEqual(parsed.fontfaceId, 1)
        // Complications updated
        XCTAssertEqual(parsed.complicationIds, [
            PolarWatchFaceComplication.heartRate.id,
            PolarWatchFaceComplication.battery.id
        ])
    }

    func testWatchFaceGoldenVectorsMatchIOSBehavior() throws {
        let vectors = try loadWatchFaceGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected watch-face golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
            let parsed: WatchfaceConfigFields

            if let flatBufferHex = input["flatBufferHex"] as? String {
                parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: try bytes(fromHex: flatBufferHex))
            } else {
                let fields = try watchfaceConfigFields(from: try XCTUnwrap(input["fields"] as? [String: Any], id))
                let flatBuffer = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields: fields)
                parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: flatBuffer)
                if let kvtx = expected["kvtx"] as? [String: Any] {
                    try assertKvtxScript(fields: fields, matches: kvtx, id: id)
                }
            }

            try assertWatchfaceConfigFields(parsed, matches: try XCTUnwrap(expected["fields"] as? [String: Any], id), id: id)
            if let knownComplications = expected["knownComplications"] as? [String] {
                for (index, expectedName) in knownComplications.enumerated() {
                    XCTAssertEqual(PolarWatchFaceComplication.fromId(parsed.complicationIds[index]), try complication(named: expectedName, id: id), id)
                }
            }
        }
    }

    func testWatchFaceGoldenVectorsFollowNeutralKmpVectorShape() throws {
        for vector in try loadWatchFaceGoldenVectors() {
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

    // MARK: - Helpers

    /// Reproduces Java's String.hashCode() algorithm (signed 32-bit).
    private func javaHashCode(_ s: String) -> Int32 {
        var h: Int32 = 0
        for scalar in s.unicodeScalars {
            h = h &* 31 &+ Int32(bitPattern: scalar.value)
        }
        return h
    }

    private func loadWatchFaceGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/watch-face")
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
                return input?["kind"] as? String != "watchFaceReadiness"
            }
    }

    func testWatchFaceReadinessManifestIsPinnedBeforeModelMigration() throws {
        let file = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/watch-face/watch-face-readiness.json")
        let data = try Data(contentsOf: file)
        let vector = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
        XCTAssertEqual(vector["id"] as? String, "watch-face-readiness")
        let input = try XCTUnwrap(vector["input"] as? [String: Any], "watch-face-readiness.json")
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any], "watch-face-readiness.json")
        XCTAssertEqual(input["kind"] as? String, "watchFaceReadiness")
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String], "watch-face-readiness.json")
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String], "watch-face-readiness.json")
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String], "watch-face-readiness.json")
        XCTAssertEqual(policyVectorPaths, [
            "sdk/watch-face/all-fields-with-complications.json",
            "sdk/watch-face/default-fields.json",
            "sdk/watch-face/malformed-too-short.json",
            "sdk/watch-face/ordered-complications-with-empty.json",
            "sdk/watch-face/unknown-complication-preserved.json"
        ])
        let expectedFamilies = [
            "default-field-zeroing",
            "scalar-field-round-trip",
            "complication-id-order-preservation",
            "empty-complication-id-preservation",
            "known-complication-lookup",
            "unknown-complication-raw-id-preservation",
            "unknown-complication-null-lookup-policy",
            "malformed-too-short-defaulting",
            "kvtx-wrapper-metadata",
            "platform-watch-face-vector-reference-gate",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "Watch-face model migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS watch-face tests continue to reference the same vectors, default fields, scalar fields, complication ordering, empty complication IDs, known complication lookup, unknown raw complication ID preservation with null enum lookup, malformed too-short defaulting, KVTX wrapper metadata, and the shared tests are compile-verified.")
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any], "watch-face-readiness.json")
        let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], "watch-face-readiness.json")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "watch-face-readiness.json"), ["com.polar.sdk.api.model.utils.PolarWatchFaceUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "watch-face-readiness.json"), ["PolarWatchFaceUtilsTests"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "watch-face-readiness.json"), ["com.polar.sharedtest.WatchFaceCommonPolicyTest"])
        XCTAssertEqual(platforms["android"] as? Bool, true)
        XCTAssertEqual(platforms["ios"] as? Bool, true)
        XCTAssertEqual(platforms["common"] as? Bool, true)
    }


    private func watchfaceConfigFields(from object: [String: Any]) throws -> WatchfaceConfigFields {
        return WatchfaceConfigFields(
            timeStyleId: UInt16((object["timeStyleId"] as? NSNumber)?.intValue ?? 0),
            complicationLayoutId: UInt16((object["complicationLayoutId"] as? NSNumber)?.intValue ?? 0),
            backgroundStyleId: UInt16((object["backgroundStyleId"] as? NSNumber)?.intValue ?? 0),
            accentColor: (object["accentColor"] as? NSNumber)?.uint32Value ?? 0,
            complicationIds: (object["complicationIds"] as? [NSNumber])?.map { $0.int32Value } ?? [],
            fontfaceId: UInt8((object["fontfaceId"] as? NSNumber)?.intValue ?? 0)
        )
    }

    private func assertWatchfaceConfigFields(_ actual: WatchfaceConfigFields, matches expected: [String: Any], id: String) throws {
        let expectedFields = try watchfaceConfigFields(from: expected)
        XCTAssertEqual(actual.timeStyleId, expectedFields.timeStyleId, id)
        XCTAssertEqual(actual.complicationLayoutId, expectedFields.complicationLayoutId, id)
        XCTAssertEqual(actual.backgroundStyleId, expectedFields.backgroundStyleId, id)
        XCTAssertEqual(actual.accentColor, expectedFields.accentColor, id)
        XCTAssertEqual(actual.complicationIds, expectedFields.complicationIds, id)
        XCTAssertEqual(actual.fontfaceId, expectedFields.fontfaceId, id)
    }

    private func assertKvtxScript(fields: WatchfaceConfigFields, matches expected: [String: Any], id: String) throws {
        let script = PolarWatchFaceUtils.buildKvtxScript(fields: fields)
        XCTAssertEqual(script.first, UInt8(try XCTUnwrap(expected["firstOpcode"] as? NSNumber, id).intValue), id)
        XCTAssertEqual(script.last, UInt8(try XCTUnwrap(expected["commitOpcode"] as? NSNumber, id).intValue), id)
        let key = UInt32(script[1]) | (UInt32(script[2]) << 8) | (UInt32(script[3]) << 16) | (UInt32(script[4]) << 24)
        XCTAssertEqual(key, try XCTUnwrap(expected["key"] as? NSNumber, id).uint32Value, id)
        let length = Int(UInt32(script[5]) | (UInt32(script[6]) << 8) | (UInt32(script[7]) << 16) | (UInt32(script[8]) << 24))
        XCTAssertEqual(script.count, 1 + 4 + 4 + length + 1, id)
        let extracted = try XCTUnwrap(PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script: script), id)
        try assertWatchfaceConfigFields(PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(raw: extracted), matches: [
            "timeStyleId": NSNumber(value: fields.timeStyleId),
            "complicationLayoutId": NSNumber(value: fields.complicationLayoutId),
            "backgroundStyleId": NSNumber(value: fields.backgroundStyleId),
            "accentColor": NSNumber(value: fields.accentColor),
            "complicationIds": fields.complicationIds.map { NSNumber(value: $0) },
            "fontfaceId": NSNumber(value: fields.fontfaceId)
        ], id: id)
    }

    private func complication(named name: String, id: String) throws -> PolarWatchFaceComplication {
        switch name {
        case "SPO2": return .spo2
        case "HEART_RATE": return .heartRate
        case "ACTIVITY": return .activity
        case "DATE": return .date
        case "BATTERY": return .battery
        case "EMPTY": return .empty
        case "WEATHER": return .weather
        default:
            XCTFail("Unsupported complication name in \(id): \(name)")
            return .empty
        }
    }

    private func bytes(fromHex hexString: String) throws -> [UInt8] {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "PolarWatchFaceUtilsTests", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "PolarWatchFaceUtilsTests", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        return bytes
    }
}
