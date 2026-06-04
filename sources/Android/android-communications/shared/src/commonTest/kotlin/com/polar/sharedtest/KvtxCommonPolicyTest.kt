package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KvtxCommonPolicyTest {
    @Test
    fun kvtxGoldenVectorsDefineExecutableCommonScriptPolicy() {
        KVTX_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")
            val scriptHex = if (input.optionalStringValue("operation") == "buildWriteAndCommit") {
                buildWriteAndCommit(input.longValue("key"), input.stringValue("dataHex"))
            } else {
                input.stringValue("scriptHex")
            }

            expected.optionalStringValue("scriptHex")?.let { expectedScript ->
                assertEquals(expectedScript, scriptHex, "$caseId script")
                assertEquals(expected.intValue("firstOpcode"), hexToBytes(scriptHex).first().toInt() and 0xFF, "$caseId first opcode")
                assertEquals(expected.intValue("commitOpcode"), hexToBytes(scriptHex).last().toInt() and 0xFF, "$caseId commit opcode")
            }
            expected.optionalStringValue("commonDecision")?.let { decision ->
                assertEquals(true, decision.contains("typed malformed-script parse error"), caseId)
                assertEquals("malformedScript", extractValue(scriptHex, input.longValue("extractKey")).error, caseId)
                return@forEach
            }
            expected.optionalStringValue("parseError")?.let { parseError ->
                assertEquals(parseError, extractValue(scriptHex, input.longValue("extractKey")).error, caseId)
                return@forEach
            }

            val extracted = extractValue(scriptHex, expected.optionalLongValue("extractKey") ?: input.longValue("extractKey"))
            assertEquals(null, extracted.error, "$caseId parse error")
            assertEquals(expected.optionalStringValue("extractedHex"), extracted.valueHex, "$caseId extracted")
        }
    }

    @Test
    fun kvtxReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/kvtx/kvtx-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPaths = input.stringArrayValue("policyVectorPaths")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        assertEquals("kvtx-readiness", manifest.stringValue("id"))
        assertEquals("kvtxReadiness", input.stringValue("kind"))
        assertEquals(KVTX_READINESS_VECTOR_PATHS, policyVectorPaths)
        assertEquals(requiredKvtxFamilies, requiredFamilies)
        assertEquals(requiredKvtxFamilies, coveredFamilies)
        assertEquals(kvtxReadinessCommonDecision, expected.stringValue("commonDecision"))
        val consumerTests = manifest.objectValue("consumerTests")
        assertEquals(listOf("com.polar.sdk.impl.utils.KvtxScriptUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("KvtxScriptUtilsTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.KvtxCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun buildWriteAndCommit(key: Long, dataHex: String): String {
        return "00${key.toLeU32Hex()}${(dataHex.length / 2).toLeI32Hex()}${dataHex}05"
    }

    private fun extractValue(scriptHex: String, key: Long): ExtractResult {
        val bytes = hexToBytes(scriptHex)
        var index = 0
        var current: ByteArray? = null
        while (index < bytes.size) {
            when (val opcode = bytes[index].toInt() and 0xFF) {
                0 -> {
                    val command = readKeyAndData(bytes, index + 1) ?: return ExtractResult(error = "malformedScript")
                    if (command.key == key) current = command.data
                    index = command.nextIndex
                }
                1 -> {
                    val command = readKeyAndData(bytes, index + 1) ?: return ExtractResult(error = "malformedScript")
                    if (command.key == key) current = (current ?: ByteArray(0)) + command.data
                    index = command.nextIndex
                }
                2 -> {
                    val commandKey = bytes.readLeU32OrNull(index + 1) ?: return ExtractResult(error = "malformedScript")
                    if (commandKey == key) current = null
                    index += 5
                }
                5 -> return ExtractResult(valueHex = current?.toHex())
                6 -> {
                    val command = readExtendedKeyAndData(bytes, index + 1) ?: return ExtractResult(error = "malformedScript")
                    if (command.key == key && command.indexBytes.isEmpty()) current = command.data
                    index = command.nextIndex
                }
                7 -> {
                    val command = readExtendedKeyAndData(bytes, index + 1) ?: return ExtractResult(error = "malformedScript")
                    if (command.key == key && command.indexBytes.isEmpty()) current = (current ?: ByteArray(0)) + command.data
                    index = command.nextIndex
                }
                8 -> {
                    val command = readExtendedKey(bytes, index + 1) ?: return ExtractResult(error = "malformedScript")
                    if (command.key == key && command.indexBytes.isEmpty()) current = null
                    index = command.nextIndex
                }
                else -> return ExtractResult(valueHex = current?.toHex(), stoppedAtOpcode = opcode)
            }
        }
        return ExtractResult(valueHex = current?.toHex())
    }

    private fun readKeyAndData(bytes: ByteArray, start: Int): CommandData? {
        val key = bytes.readLeU32OrNull(start) ?: return null
        val length = bytes.readLeI32OrNull(start + 4) ?: return null
        val dataStart = start + 8
        val dataEnd = dataStart + length
        if (length < 0 || dataEnd > bytes.size) return null
        return CommandData(key = key, data = bytes.copyOfRange(dataStart, dataEnd), nextIndex = dataEnd)
    }

    private fun readExtendedKeyAndData(bytes: ByteArray, start: Int): ExtendedCommandData? {
        val command = readExtendedKey(bytes, start) ?: return null
        val length = bytes.readLeI32OrNull(command.nextIndex) ?: return null
        val dataStart = command.nextIndex + 4
        val dataEnd = dataStart + length
        if (length < 0 || dataEnd > bytes.size) return null
        return ExtendedCommandData(
            key = command.key,
            indexBytes = command.indexBytes,
            data = bytes.copyOfRange(dataStart, dataEnd),
            nextIndex = dataEnd
        )
    }

    private fun readExtendedKey(bytes: ByteArray, start: Int): ExtendedCommandKey? {
        val key = bytes.readLeU32OrNull(start) ?: return null
        if (start + 5 > bytes.size) return null
        val indexLength = bytes[start + 4].toInt() and 0xFF
        val indexStart = start + 5
        val indexEnd = indexStart + indexLength
        if (indexLength < 0 || indexEnd > bytes.size) return null
        return ExtendedCommandKey(
            key = key,
            indexBytes = bytes.copyOfRange(indexStart, indexEnd),
            nextIndex = indexEnd
        )
    }

    private fun ByteArray.readLeU32OrNull(offset: Int): Long? {
        if (offset + 4 > size) return null
        var value = 0L
        for (index in 0 until 4) {
            value = value or ((this[offset + index].toLong() and 0xFFL) shl (index * 8))
        }
        return value
    }

    private fun ByteArray.readLeI32OrNull(offset: Int): Int? {
        if (offset + 4 > size) return null
        return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8) or ((this[offset + 2].toInt() and 0xFF) shl 16) or ((this[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun Long.toLeU32Hex(): String {
        return (0 until 4).joinToString(separator = "") { index ->
            ((this shr (index * 8)) and 0xFF).toInt().toHexByte()
        }
    }

    private fun Int.toLeI32Hex(): String {
        return (0 until 4).joinToString(separator = "") { index ->
            ((this shr (index * 8)) and 0xFF).toHexByte()
        }
    }

    private fun ByteArray.toHex(): String {
        return joinToString(separator = "") { byte -> (byte.toInt() and 0xFF).toHexByte() }
    }

    private fun Int.toHexByte(): String {
        val value = this and 0xFF
        return "${(value / 16).toHexDigit()}${(value % 16).toHexDigit()}"
    }

    private fun Int.toHexDigit(): Char {
        return if (this < 10) '0' + this else 'a' + (this - 10)
    }

    private fun String.longValue(field: String): Long {
        return Regex("\"$field\"\\s*:\\s*(\\d+)").find(this)?.groupValues?.get(1)?.toLong() ?: error("Missing long field $field in $this")
    }

    private fun String.optionalLongValue(field: String): Long? {
        return Regex("\"$field\"\\s*:\\s*(\\d+)").find(this)?.groupValues?.get(1)?.toLong()
    }

    private data class ExtractResult(
        val valueHex: String? = null,
        val error: String? = null,
        val stoppedAtOpcode: Int? = null
    )

    private data class CommandData(
        val key: Long,
        val data: ByteArray,
        val nextIndex: Int
    )

    private data class ExtendedCommandKey(
        val key: Long,
        val indexBytes: ByteArray,
        val nextIndex: Int
    )

    private data class ExtendedCommandData(
        val key: Long,
        val indexBytes: ByteArray,
        val data: ByteArray,
        val nextIndex: Int
    )

    private companion object {
        val KVTX_VECTORS = listOf(
            "sdk/kvtx/append-concatenates.json",
            "sdk/kvtx/append-ex-zero-index.json",
            "sdk/kvtx/multiple-keys-select-second.json",
            "sdk/kvtx/remove-clears-value.json",
            "sdk/kvtx/remove-ex-zero-index.json",
            "sdk/kvtx/truncated-write-payload-android-error.json",
            "sdk/kvtx/truncated-write-payload-ios-nil.json",
            "sdk/kvtx/unknown-command-stops-with-current-value.json",
            "sdk/kvtx/write-commit-basic.json",
            "sdk/kvtx/write-commit-empty-data.json",
            "sdk/kvtx/write-commit-uint32-max-key.json",
            "sdk/kvtx/write-ex-nonempty-index-ignored.json",
            "sdk/kvtx/write-ex-zero-index.json"
        )
        val KVTX_READINESS_VECTOR_PATHS = listOf(
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
        )
        val requiredKvtxFamilies = listOf(
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
        val kvtxReadinessCommonDecision = "KVTX migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS KVTX tests continue to reference the same vectors, write-and-commit framing, empty data writes, unsigned 32-bit keys, multiple-key selection, append/remove behavior, EX zero-index behavior, non-empty EX index ignore policy, unknown-command stop policy, malformed-script typed error policy, truncated payload platform vectors, and the shared tests are compile-verified."
    }
}
