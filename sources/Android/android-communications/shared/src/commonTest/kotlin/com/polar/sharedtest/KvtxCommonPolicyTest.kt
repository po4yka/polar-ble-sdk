package com.polar.sharedtest

import com.polar.shared.sdk.PolarKvtxMalformedScriptException
import com.polar.shared.sdk.PolarKvtxScriptCodec
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
                PolarKvtxScriptCodec.buildWriteAndCommit(input.longValue("key"), hexToBytes(input.stringValue("dataHex"))).toHex()
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

    private fun extractValue(scriptHex: String, key: Long): ExtractResult {
        return try {
            ExtractResult(valueHex = PolarKvtxScriptCodec.extractValueForKey(hexToBytes(scriptHex), key)?.toHex())
        } catch (_: PolarKvtxMalformedScriptException) {
            ExtractResult(error = "malformedScript")
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
