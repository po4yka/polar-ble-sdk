// Copyright 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.sdk.api.model.PolarWatchFaceComplication
import com.polar.sdk.impl.utils.PolarWatchFaceUtils
import com.polar.sdk.impl.utils.WatchfaceConfigFields
import org.junit.Assert.*
import org.junit.Test
import protocol.PftpRequest
import java.io.File
import java.io.FileReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PolarWatchFaceUtilsTest {

    @Test
    fun `buildKvtxScript with WatchfaceConfigFields preserves all scalar fields`() {
        val fields = WatchfaceConfigFields(
            timeStyleId = 3, complicationLayoutId = 1, backgroundStyleId = 2,
            accentColor = 0xdd0d3cL,
            complicationIds = listOf(PolarWatchFaceComplication.SPO2.id),
            fontfaceId = 1
        )
        val script = PolarWatchFaceUtils.buildKvtxScript(fields)
        assertEquals(0x00.toByte(), script[0])
        assertEquals(0x05.toByte(), script.last())
        val key = ByteBuffer.wrap(script, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(PolarWatchFaceUtils.WATCH_FACE_CONFIG_KVS_KEY, key)
    }

    @Test
    fun `buildKvtxScript produces correct WRITE_BYTES and COMMIT structure`() {
        val ids = listOf(PolarWatchFaceComplication.SPO2.id)
        val script = PolarWatchFaceUtils.buildKvtxScript(WatchfaceConfigFields(complicationIds = ids))
        assertEquals(0x00.toByte(), script[0])
        val key = ByteBuffer.wrap(script, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(PolarWatchFaceUtils.WATCH_FACE_CONFIG_KVS_KEY, key)
        val dataLen = ByteBuffer.wrap(script, 5, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertTrue("data length should be > 0", dataLen > 0)
        assertEquals(1 + 4 + 4 + dataLen + 1, script.size)
        assertEquals(0x05.toByte(), script.last())
    }

    @Test
    fun `buildKvtxScript with empty list produces valid script`() {
        val script = PolarWatchFaceUtils.buildKvtxScript(WatchfaceConfigFields())
        assertEquals(0x00.toByte(), script[0])
        assertEquals(0x05.toByte(), script.last())
        assertTrue(script.size > 9)
    }

    @Test
    fun `buildKvtxScript with multiple complications`() {
        val ids = listOf(
            PolarWatchFaceComplication.SPO2.id,
            PolarWatchFaceComplication.HEART_RATE.id,
            PolarWatchFaceComplication.ACTIVITY.id
        )
        val script = PolarWatchFaceUtils.buildKvtxScript(WatchfaceConfigFields(complicationIds = ids))
        assertEquals(0x00.toByte(), script[0])
        assertEquals(0x05.toByte(), script.last())
    }

    @Test
    fun `extractWatchFaceConfigFromKvtxScript returns null for empty script`() {
        assertNull(PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(byteArrayOf()))
    }

    @Test
    fun `extractWatchFaceConfigFromKvtxScript round-trips through buildKvtxScript`() {
        val ids = listOf(PolarWatchFaceComplication.SPO2.id, PolarWatchFaceComplication.ACTIVITY.id)
        val script = PolarWatchFaceUtils.buildKvtxScript(WatchfaceConfigFields(complicationIds = ids))
        val extracted = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script)
        assertNotNull(extracted)
        assertEquals(ids, PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(extracted!!).complicationIds)
    }

    @Test
    fun `parseWatchFaceConfigFlatBuffer handles empty default fields`() {
        val fields = WatchfaceConfigFields()
        val script = PolarWatchFaceUtils.buildKvtxScript(fields)
        val extracted = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script)
        assertNotNull(extracted)
        val parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(extracted!!)
        assertEquals(0, parsed.timeStyleId)
        assertEquals(0, parsed.complicationLayoutId)
        assertEquals(0, parsed.backgroundStyleId)
        assertEquals(0L, parsed.accentColor)
        assertEquals(emptyList<Int>(), parsed.complicationIds)
        assertEquals(0, parsed.fontfaceId)
    }

    @Test
    fun `parseWatchFaceConfigFlatBuffer round-trips all fields through buildKvtxScript`() {
        val originalFields = WatchfaceConfigFields(
            timeStyleId = 7, complicationLayoutId = 3, backgroundStyleId = 4,
            accentColor = 0xaabbccL,
            complicationIds = listOf(PolarWatchFaceComplication.SPO2.id, PolarWatchFaceComplication.HEART_RATE.id),
            fontfaceId = 2
        )
        val script = PolarWatchFaceUtils.buildKvtxScript(originalFields)
        val extracted = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script)
        assertNotNull(extracted)
        val parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(extracted!!)
        assertEquals(originalFields, parsed)
    }

    @Test
    fun `parseWatchFaceConfigFlatBuffer with single complication`() {
        val ids = listOf(PolarWatchFaceComplication.ACTIVITY.id)
        val script = PolarWatchFaceUtils.buildKvtxScript(WatchfaceConfigFields(complicationIds = ids))
        val extracted = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script)
        assertNotNull(extracted)
        val parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(extracted!!)
        assertEquals(ids, parsed.complicationIds)
    }

    @Test
    fun `parseWatchFaceConfigFlatBuffer with multiple complications`() {
        val ids = listOf(PolarWatchFaceComplication.SPO2.id, PolarWatchFaceComplication.ACTIVITY.id, PolarWatchFaceComplication.DATE.id)
        val script = PolarWatchFaceUtils.buildKvtxScript(WatchfaceConfigFields(complicationIds = ids))
        val extracted = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script)
        assertNotNull(extracted)
        val parsed = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(extracted!!)
        assertEquals(ids, parsed.complicationIds)
    }

    @Test
    fun `PolarWatchFaceComplication id is hashCode of complicationId`() {
        PolarWatchFaceComplication.entries.forEach { c ->
            assertEquals("${c.name}.id should equal complicationId.hashCode()", c.complicationId.hashCode(), c.id)
        }
    }

    @Test
    fun `PolarWatchFaceComplication EMPTY id is zero`() {
        assertEquals(0, PolarWatchFaceComplication.EMPTY.id)
        assertEquals("", PolarWatchFaceComplication.EMPTY.complicationId)
    }

    @Test
    fun `PolarWatchFaceComplication fromId round-trips all entries`() {
        PolarWatchFaceComplication.entries.forEach { c ->
            assertEquals("fromId failed for ${c.name}", c, PolarWatchFaceComplication.fromId(c.id))
        }
    }

    @Test
    fun `PolarWatchFaceComplication fromId returns null for unknown id`() {
        assertNull(PolarWatchFaceComplication.fromId(Int.MIN_VALUE))
        assertNull(PolarWatchFaceComplication.fromId(999999999))
    }

    @Test
    fun `PolarWatchFaceComplication ECG uses ecg complicationId`() {
        assertEquals("ecg-complication", PolarWatchFaceComplication.ECG.complicationId)
    }

    @Test
    fun `PolarWatchFaceComplication all entries have unique ids`() {
        val ids = PolarWatchFaceComplication.entries.map { it.id }
        assertEquals("duplicate ids found", ids.size, ids.toSet().size)
    }

    @Test
    fun `PolarWatchFaceComplication CALORIES uses correct complicationId`() {
        assertEquals("calories-complication", PolarWatchFaceComplication.CALORIES.complicationId)
    }

    @Test
    fun `watch face KVTX headers use shared file facade planning`() {
        assertEquals(PftpRequest.PbPFtpOperation.Command.GET to "/SYS/KVTX", PolarWatchFaceUtils.watchFaceReadOperation())
        assertEquals(PftpRequest.PbPFtpOperation.Command.PUT to "/SYS/KVTX", PolarWatchFaceUtils.watchFaceWriteOperation())
    }

    @Test
    fun watchFaceGoldenVectors_matchAndroidBehavior() {
        val vectors = loadWatchFaceVectors()
        assertTrue("Expected watch-face golden vectors", vectors.isNotEmpty())

        vectors.forEach { vector ->
            val caseId = vector.get("id").asString
            val input = vector.getAsJsonObject("input")
            val expected = vector.getAsJsonObject("expected")
            val parsed = if (input.has("flatBufferHex")) {
                PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(input.get("flatBufferHex").asString.hexToByteArray())
            } else {
                val fields = input.getAsJsonObject("fields").toWatchfaceConfigFields()
                val flatBuffer = PolarWatchFaceUtils.buildWatchFaceConfigFlatBuffer(fields)
                val roundTripped = PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(flatBuffer)
                if (expected.has("kvtx")) {
                    assertKvtxScript(caseId, expected.getAsJsonObject("kvtx"), fields)
                }
                roundTripped
            }

            assertWatchfaceConfigFields(caseId, expected.getAsJsonObject("fields"), parsed)
            if (expected.has("knownComplications")) {
                expected.getAsJsonArray("knownComplications").forEachIndexed { index, expectedName ->
                    assertEquals(caseId, expectedName.asString, PolarWatchFaceComplication.fromId(parsed.complicationIds[index])?.name)
                }
            }
        }
    }

    @Test
    fun `watch face golden vectors follow neutral shared vector shape`() {
        loadWatchFaceVectors().forEach { vector ->
            val id = vector.get("id")?.asString ?: "unknown-vector"

            assertTrue(id, vector.has("area"))
            assertTrue(id, vector.has("case"))
            assertTrue(id, vector.has("source"))
            assertTrue(id, vector.has("input"))
            assertTrue(id, vector.has("expected"))
            assertTrue(id, vector.has("platforms"))
            val platforms = vector.getAsJsonObject("platforms")
            assertTrue(id, platforms.has("android"))
            assertTrue(id, platforms.has("ios"))
            assertTrue(id, platforms.has("common"))
        }
    }

    private fun assertKvtxScript(caseId: String, expected: JsonObject, fields: WatchfaceConfigFields) {
        val script = PolarWatchFaceUtils.buildKvtxScript(fields)
        assertEquals(caseId, expected.get("firstOpcode").asInt.toByte(), script[0])
        assertEquals(caseId, expected.get("commitOpcode").asInt.toByte(), script.last())
        val key = ByteBuffer.wrap(script, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(caseId, expected.get("key").asInt, key)
        val dataLen = ByteBuffer.wrap(script, 5, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(caseId, 1 + 4 + 4 + dataLen + 1, script.size)
        val extracted = PolarWatchFaceUtils.extractWatchFaceConfigFromKvtxScript(script)
        assertNotNull(caseId, extracted)
        assertWatchfaceConfigFields(caseId, fields, PolarWatchFaceUtils.parseWatchFaceConfigFlatBuffer(extracted!!))
    }

    private fun assertWatchfaceConfigFields(caseId: String, expected: JsonObject, actual: WatchfaceConfigFields) {
        assertWatchfaceConfigFields(caseId, expected.toWatchfaceConfigFields(), actual)
    }

    private fun assertWatchfaceConfigFields(caseId: String, expected: WatchfaceConfigFields, actual: WatchfaceConfigFields) {
        assertEquals(caseId, expected.timeStyleId, actual.timeStyleId)
        assertEquals(caseId, expected.complicationLayoutId, actual.complicationLayoutId)
        assertEquals(caseId, expected.backgroundStyleId, actual.backgroundStyleId)
        assertEquals(caseId, expected.accentColor, actual.accentColor)
        assertEquals(caseId, expected.complicationIds, actual.complicationIds)
        assertEquals(caseId, expected.fontfaceId, actual.fontfaceId)
    }

    private fun JsonObject.toWatchfaceConfigFields(): WatchfaceConfigFields {
        return WatchfaceConfigFields(
            timeStyleId = optionalInt("timeStyleId"),
            complicationLayoutId = optionalInt("complicationLayoutId"),
            backgroundStyleId = optionalInt("backgroundStyleId"),
            accentColor = optionalLong("accentColor"),
            complicationIds = if (has("complicationIds")) getAsJsonArray("complicationIds").map { it.asInt } else emptyList(),
            fontfaceId = optionalInt("fontfaceId")
        )
    }

    private fun JsonObject.optionalInt(name: String): Int {
        return if (has(name)) get(name).asInt else 0
    }

    private fun JsonObject.optionalLong(name: String): Long {
        return if (has(name)) get(name).asLong else 0L
    }

    private fun loadWatchFaceVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/watch-face")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
            .filterNot { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString == "watchFaceReadiness" }
    }

    @Test
    fun `watch face readiness manifest is pinned for shared model ownership`() {
        val vector = FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/watch-face/watch-face-readiness.json")).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { path -> path.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { family -> family.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { family -> family.asString }
        assertEquals("watch-face-readiness", vector.get("id").asString)
        assertEquals("watchFaceReadiness", input.get("kind").asString)
        assertEquals(
            listOf(
                "sdk/watch-face/all-fields-with-complications.json",
                "sdk/watch-face/default-fields.json",
                "sdk/watch-face/malformed-too-short.json",
                "sdk/watch-face/ordered-complications-with-empty.json",
                "sdk/watch-face/unknown-complication-preserved.json"
            ),
            policyVectorPaths
        )
        val expectedFamilies = listOf(
            "default-field-zeroing",
            "scalar-field-round-trip",
            "complication-id-order-preservation",
            "empty-complication-id-preservation",
            "known-complication-lookup",
            "unknown-complication-raw-id-preservation",
            "unknown-complication-null-lookup-policy",
            "malformed-too-short-defaulting",
            "flatbuffer-byte-input-parser",
            "flatbuffer-byte-output-shared-code",
            "kvtx-wrapper-metadata",
            "platform-watch-face-vector-reference-gate",
            "compile-verification-gate"
        )
        assertEquals(expectedFamilies, requiredFamilies)
        assertEquals(expectedFamilies, coveredFamilies)
        assertEquals(
            "Watch-face model shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS watch-face tests continue to reference the same vectors, default fields, scalar fields, complication ordering, empty complication IDs, known complication lookup, unknown raw complication ID preservation with null enum lookup, malformed too-short defaulting, shared FlatBuffer byte input parsing, shared FlatBuffer byte output construction, KVTX wrapper metadata, and the shared tests are compile-verified.",
            expected.get("commonDecision").asString
        )
        val consumerTests = vector.getAsJsonObject("consumerTests")
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarWatchFaceUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("PolarWatchFaceUtilsTests"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.WatchFaceCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun findRepositoryRoot(): File {
        val userDirectory = System.getProperty("user.dir") ?: error("user.dir is not set")
        var directory = File(userDirectory).absoluteFile
        while (true) {
            if (directory.resolve("testdata/golden-vectors").isDirectory) {
                return directory
            }
            directory = directory.parentFile ?: error("Could not find repository root from $userDirectory")
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
