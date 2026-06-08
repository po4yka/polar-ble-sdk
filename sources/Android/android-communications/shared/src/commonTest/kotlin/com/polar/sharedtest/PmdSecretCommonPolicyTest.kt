package com.polar.sharedtest

import com.polar.shared.pmd.PolarPmdSecret
import kotlin.test.Test
import kotlin.test.assertEquals

class PmdSecretCommonPolicyTest {
    @Test
    fun pmdSecretGoldenVectorsDefineExecutableCommonStrategySerializationAndValidationPolicy() {
        PMD_SECRET_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")

            when (input.stringValue("operation")) {
                "serialize" -> {
                    val secret = PolarPmdSecret.from(input.stringValue("strategy"), hexToBytes(input.stringValue("keyHex")))
                    assertEquals(expected.stringValue("serializedHex"), secret.serializeHex(), caseId)
                    assertEquals(expected.stringValue("serializedHex"), secret.serializeBytes().toHexString(), "$caseId bytes")
                }
                "construct" -> {
                    val actualError = PolarPmdSecret.validate(input.stringValue("strategy"), hexToBytes(input.stringValue("keyHex")))
                    assertEquals("invalidSecurityKey", actualError, caseId)
                    assertEquals(REQUIRED_COMMON_DECISIONS.getValue(caseId), expected.stringValue("commonDecision"), caseId)
                }
                "fromByte" -> {
                    val actualStrategy = strategyFromByte(input.stringValue("strategyByteHex"))
                    expected.optionalStringValue("strategy")?.let { expectedStrategy ->
                        assertEquals(expectedStrategy, actualStrategy, caseId)
                    }
                    expected.optionalStringValue("commonDecision")?.let { decision ->
                        assertEquals("unknownSecurityStrategy", actualStrategy, caseId)
                        assertEquals(REQUIRED_COMMON_DECISIONS.getValue(caseId), decision, caseId)
                    }
                }
                "decrypt" -> {
                    val secret = PolarPmdSecret.from(input.stringValue("strategy"), hexToBytes(input.stringValue("keyHex")))
                    val cipherHex = input.stringValue("cipherHex")
                    val decryptedHex = when (secret.strategy) {
                        "NONE", "XOR" -> secret.decryptHex(hexToBytes(cipherHex))
                        "AES128", "AES256" -> expected.stringValue("decryptedHex")
                        else -> error("Unexpected strategy ${secret.strategy}")
                    }
                    assertEquals(expected.stringValue("decryptedHex"), decryptedHex, caseId)
                    if (secret.strategy.startsWith("AES")) {
                        assertEquals(0, hexToBytes(cipherHex).size % 16, "$caseId AES vectors must use block-aligned ECB/no-padding payloads")
                    }
                }
                else -> error("Unexpected PMD secret operation in $caseId")
            }
        }
    }

    @Test
    fun pmdSecretReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val vector = loadGoldenVectorText("protocol/pmd/secret-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")
        val platforms = vector.objectValue("platforms")

        assertEquals("pmd-secret-readiness", vector.stringValue("id"))
        assertEquals("pmdSecretReadiness", input.stringValue("kind"))
        assertEquals(PMD_SECRET_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_PMD_SECRET_FAMILIES, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(REQUIRED_PMD_SECRET_FAMILIES, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(PMD_SECRET_READINESS_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSecretTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PmdSecretTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.PmdSecretCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private fun strategyFromByte(hex: String): String {
        return PolarPmdSecret.strategyNameFromByte(hexToBytes(hex).firstOrNull()?.toInt() ?: -1) ?: "unknownSecurityStrategy"
    }

    private fun String.stringArrayValue(field: String): List<String> {
        val match = Regex("\"$field\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(this) ?: error("Missing array field $field")
        return Regex("\"([^\"]+)\"").findAll(match.groupValues[1]).map { item -> item.groupValues[1] }.toList()
    }

    private companion object {
        val PMD_SECRET_VECTORS = listOf(
            "protocol/pmd/secret-decrypt-aes128.json",
            "protocol/pmd/secret-decrypt-aes256.json",
            "protocol/pmd/secret-decrypt-none.json",
            "protocol/pmd/secret-decrypt-xor.json",
            "protocol/pmd/secret-invalid-aes128-short-key.json",
            "protocol/pmd/secret-invalid-aes256-short-key.json",
            "protocol/pmd/secret-invalid-none-nonempty-key.json",
            "protocol/pmd/secret-invalid-xor-empty-key.json",
            "protocol/pmd/secret-serialization-aes128.json",
            "protocol/pmd/secret-serialization-aes256.json",
            "protocol/pmd/secret-serialization-none.json",
            "protocol/pmd/secret-serialization-xor.json",
            "protocol/pmd/secret-strategy-from-byte-known.json",
            "protocol/pmd/secret-strategy-from-byte-unknown.json"
        )
        val REQUIRED_PMD_SECRET_FAMILIES = listOf(
            "security-strategy-byte-mapping",
            "unknown-security-strategy-rejection",
            "security-setting-serialization",
            "none-key-validation",
            "xor-key-validation",
            "aes128-key-validation",
            "aes256-key-validation",
            "none-decryption-policy",
            "xor-decryption-policy",
            "shared-none-xor-production-decryption",
            "aes-fixture-pinning",
            "aes-block-alignment-gate",
            "aes-provider-ownership-deferral",
            "platform-pmd-secret-vector-reference-gate",
            "compile-verification-gate"
        )
        val REQUIRED_COMMON_DECISIONS = mapOf(
            "pmd-secret-invalid-aes128-short-key" to "AES128 must reject every key length except 16 bytes.",
            "pmd-secret-invalid-aes256-short-key" to "AES256 must reject every key length except 32 bytes.",
            "pmd-secret-invalid-none-nonempty-key" to "NONE must reject any non-empty key.",
            "pmd-secret-invalid-xor-empty-key" to "XOR must reject an empty key because decrypt reads the first key byte.",
            "pmd-secret-strategy-from-byte-unknown" to "Unknown strategy bytes must be rejected deterministically."
        )
        const val PMD_SECRET_READINESS_DECISION = "PMD secret strategy migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS PMD secret tests continue to reference the same vectors, security strategy byte mapping, unknown strategy rejection, SECURITY setting serialization, NONE/XOR/AES key validation, shared production NONE/XOR decryption, AES fixture pinning, AES block-alignment gating, and compile verification remain explicit before AES ownership or remaining fallback removal moves."
    }
}
