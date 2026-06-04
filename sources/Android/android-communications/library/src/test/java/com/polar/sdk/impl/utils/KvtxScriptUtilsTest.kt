// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.io.FileReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KvtxScriptUtilsTest {

    @Test
    fun `buildWriteAndCommit produces correct structure`() {
        val key = 0x12345678
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val script = KvtxScriptUtils.buildWriteAndCommit(key, data)
        assertEquals(KvtxScriptUtils.CMD_WRITE_BYTES, script[0])
        val parsedKey = ByteBuffer.wrap(script, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(key, parsedKey)
        val parsedLen = ByteBuffer.wrap(script, 5, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(data.size, parsedLen)
        assertArrayEquals(data, script.sliceArray(9..11))
        assertEquals(KvtxScriptUtils.CMD_COMMIT, script.last())
        assertEquals(1 + 4 + 4 + data.size + 1, script.size)
    }

    @Test
    fun `extractValueForKey finds correct key among multiple`() {
        val key1 = 0x11110000
        val key2 = 0x22220000
        val data1 = byteArrayOf(0xDE.toByte(), 0xAD.toByte())
        val data2 = byteArrayOf(0xBE.toByte(), 0xEF.toByte())
        val script = KvtxScriptUtils.buildWriteAndCommit(key1, data1) +
                KvtxScriptUtils.buildWriteAndCommit(key2, data2)
        assertArrayEquals(data1, KvtxScriptUtils.extractValueForKey(script, key1))
        assertArrayEquals(data2, KvtxScriptUtils.extractValueForKey(script, key2))
    }

    @Test
    fun `extractValueForKey returns null for missing key`() {
        val script = KvtxScriptUtils.buildWriteAndCommit(0x11111111, byteArrayOf(0x01))
        assertNull(KvtxScriptUtils.extractValueForKey(script, 0x22222222))
    }

    @Test
    fun `extractValueForKey handles empty script`() {
        assertNull(KvtxScriptUtils.extractValueForKey(byteArrayOf(), 0x12345678))
    }

    @Test
    fun `u32Le encodes little-endian 32-bit integers`() {
        val value = 0x12345678
        val bytes = KvtxScriptUtils.u32Le(value)
        assertEquals(4, bytes.size)
        assertEquals(0x78.toByte(), bytes[0])
        assertEquals(0x56.toByte(), bytes[1])
        assertEquals(0x34.toByte(), bytes[2])
        assertEquals(0x12.toByte(), bytes[3])
        assertEquals(value, ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int)
    }

    @Test
    fun `extractValueForKey APPEND appends to existing value`() {
        val key = 0x99999999.toInt()
        val part1 = byteArrayOf(0x11)
        val part2 = byteArrayOf(0x22, 0x33)
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(part1.size) + part1 +
                    byteArrayOf(KvtxScriptUtils.CMD_APPEND_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(part2.size) + part2 +
                    byteArrayOf(KvtxScriptUtils.CMD_COMMIT)
        assertArrayEquals(byteArrayOf(0x11, 0x22, 0x33), KvtxScriptUtils.extractValueForKey(script, key))
    }

    @Test
    fun `extractValueForKey REMOVE clears the value`() {
        val key = 0x33333333
        val data = byteArrayOf(0x42)
        val script =
            byteArrayOf(KvtxScriptUtils.CMD_WRITE_BYTES) +
                    KvtxScriptUtils.u32Le(key) + KvtxScriptUtils.u32Le(data.size) + data +
                    byteArrayOf(KvtxScriptUtils.CMD_REMOVE) +
                    KvtxScriptUtils.u32Le(key) +
                    byteArrayOf(KvtxScriptUtils.CMD_COMMIT)
        assertNull(KvtxScriptUtils.extractValueForKey(script, key))
    }

    @Test
    fun kvtxGoldenVectors_matchAndroidBehavior() {
        val vectors = loadKvtxVectors()
        assertEquals("Expected KVTX golden vectors", true, vectors.isNotEmpty())

        vectors.forEach { vector ->
            val caseId = vector.get("id").asString
            if (vector.getAsJsonObject("platforms")?.get("android")?.asBoolean == false) {
                return@forEach
            }
            val input = vector.getAsJsonObject("input")
            val expected = vector.getAsJsonObject("expected")
            val script = if (input.get("operation")?.asString == "buildWriteAndCommit") {
                KvtxScriptUtils.buildWriteAndCommit(input.get("key").asLong.toInt(), input.get("dataHex").asString.hexToByteArray())
            } else {
                input.get("scriptHex").asString.hexToByteArray()
            }

            if (expected.has("scriptHex")) {
                assertArrayEquals(caseId, expected.get("scriptHex").asString.hexToByteArray(), script)
            }
            if (expected.has("firstOpcode")) {
                assertEquals(caseId, expected.get("firstOpcode").asInt.toByte(), script.first())
            }
            if (expected.has("commitOpcode")) {
                assertEquals(caseId, expected.get("commitOpcode").asInt.toByte(), script.last())
            }

            val extractKey = (input.get("extractKey") ?: expected.get("extractKey")).asLong.toInt()
            if (expected.has("parseError")) {
                assertEquals(caseId, "malformedScript", expected.get("parseError").asString)
                org.junit.Assert.assertThrows(caseId, RuntimeException::class.java) {
                    KvtxScriptUtils.extractValueForKey(script, extractKey)
                }
                return@forEach
            }
            val extracted = KvtxScriptUtils.extractValueForKey(script, extractKey)
            if (expected.get("extractedHex").isJsonNull) {
                assertNull(caseId, extracted)
            } else {
                assertArrayEquals(caseId, expected.get("extractedHex").asString.hexToByteArray(), extracted)
            }
        }
    }

    @Test
    fun `kvtx golden vectors follow neutral KMP vector shape`() {
        loadKvtxVectors().forEach { vector ->
            val id = vector.get("id")?.asString ?: "unknown-vector"

            org.junit.Assert.assertTrue(id, vector.has("area"))
            org.junit.Assert.assertTrue(id, vector.has("case"))
            org.junit.Assert.assertTrue(id, vector.has("source"))
            org.junit.Assert.assertTrue(id, vector.has("input"))
            org.junit.Assert.assertTrue(id, vector.has("expected"))
            org.junit.Assert.assertTrue(id, vector.has("platforms"))
            val platforms = vector.getAsJsonObject("platforms")
            org.junit.Assert.assertTrue(id, platforms.has("android"))
            org.junit.Assert.assertTrue(id, platforms.has("ios"))
            org.junit.Assert.assertTrue(id, platforms.has("common"))
        }
    }

    private fun loadKvtxVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/kvtx")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
            .filterNot { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString == "kvtxReadiness" }
    }

    @Test
    fun `kvtx readiness manifest is pinned before script migration`() {
        val vector = FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/kvtx/kvtx-readiness.json")).use { reader ->
            JsonParser().parse(reader).asJsonObject
        }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { path -> path.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { family -> family.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { family -> family.asString }
        assertEquals("kvtx-readiness", vector.get("id").asString)
        assertEquals("kvtxReadiness", input.get("kind").asString)
        assertEquals(
            listOf(
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
            ),
            policyVectorPaths
        )
        val expectedFamilies = listOf(
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
        )
        assertEquals(expectedFamilies, requiredFamilies)
        assertEquals(expectedFamilies, coveredFamilies)
        assertEquals(
            "KVTX migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS KVTX tests continue to reference the same vectors, write-and-commit framing, empty data writes, unsigned 32-bit keys, multiple-key selection, append/remove behavior, EX zero-index behavior, non-empty EX index ignore policy, unknown-command stop policy, malformed-script typed error policy, truncated payload platform vectors, and the shared tests are compile-verified.",
            expected.get("commonDecision").asString
        )
        val consumerTests = vector.getAsJsonObject("consumerTests")
        assertEquals(listOf("com.polar.sdk.impl.utils.KvtxScriptUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("KvtxScriptUtilsTests"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.KvtxCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
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
