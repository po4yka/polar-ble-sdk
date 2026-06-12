package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.exceptions.SecurityError
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileReader

internal class PmdSecretTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    private val key16bytes: ByteArray = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF.toByte())

    @Test
    fun `test strategy NONE serialization`() {
        //Arrange
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.NONE, key = byteArrayOf())

        //Act
        val serialized = pmdSecret.serializeToPmdSettings()

        //Assert
        Assert.assertEquals(3, serialized.size)
        Assert.assertEquals(PmdSetting.PmdSettingType.SECURITY.numVal.toByte(), serialized[0])
        Assert.assertEquals(1.toByte(), serialized[1])
        Assert.assertEquals(PmdSecret.SecurityStrategy.NONE.numVal.toByte(), serialized[2])
    }

    @Test
    fun `test strategy XOR serialization`() {
        //Arrange
        val expectedKey = byteArrayOf(0xFF.toByte())
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.XOR, key = expectedKey)

        //Act
        val serialized = pmdSecret.serializeToPmdSettings()

        //Assert
        Assert.assertEquals(1 + 1 + 1 + 1, serialized.size)
        Assert.assertEquals(PmdSetting.PmdSettingType.SECURITY.numVal.toByte(), serialized[0])
        Assert.assertEquals(1.toByte(), serialized[1])
        Assert.assertEquals(PmdSecret.SecurityStrategy.XOR.numVal.toByte(), serialized[2])
        Assert.assertArrayEquals(expectedKey, serialized.drop(3).toByteArray())
    }

    @Test
    fun `test strategy AES128 serialization`() {
        //Arrange
        val expectedKey = key16bytes.reversed().toByteArray()
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.AES128, key = expectedKey)

        //Act
        val serialized = pmdSecret.serializeToPmdSettings()

        //Assert
        Assert.assertEquals(1 + 1 + 1 + 16, serialized.size)
        Assert.assertEquals(PmdSetting.PmdSettingType.SECURITY.numVal.toByte(), serialized[0])
        Assert.assertEquals(1.toByte(), serialized[1])
        Assert.assertEquals(PmdSecret.SecurityStrategy.AES128.numVal.toByte(), serialized[2])
        Assert.assertArrayEquals(expectedKey, serialized.drop(3).toByteArray())
    }

    @Test
    fun `test strategy AES256 serialization`() {
        //Arrange
        val expectedKey = key16bytes + key16bytes.reversed()
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.AES256, key = expectedKey)

        //Act
        val serialized = pmdSecret.serializeToPmdSettings()

        //Assert
        Assert.assertEquals(1 + 1 + 1 + 32, serialized.size)
        Assert.assertEquals(PmdSetting.PmdSettingType.SECURITY.numVal.toByte(), serialized[0])
        Assert.assertEquals(1.toByte(), serialized[1])
        Assert.assertEquals(PmdSecret.SecurityStrategy.AES256.numVal.toByte(), serialized[2])
        Assert.assertArrayEquals(expectedKey, serialized.drop(3).toByteArray())
    }

    @Test
    fun `test decryption strategy NONE`() {
        //Arrange
        val chipper = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
        )

        val expectedDecryptedData = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
        )

        val key = byteArrayOf()
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.NONE, key = key)

        //Act
        val decryptedData = pmdSecret.decryptArray(chipper)

        //Assert
        Assert.assertArrayEquals(expectedDecryptedData, decryptedData)
    }

    @Test
    fun `test decryption strategy XOR`() {
        //Arrange
        val chipper = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
        )

        val expectedDecryptedData = byteArrayOf(
            0x55.toByte(), 0x54.toByte(), 0x57.toByte(), 0x56.toByte(), 0x51.toByte(), 0x50.toByte(), 0x53.toByte(), 0x52.toByte(), 0x5D.toByte(), 0x5C.toByte(), 0x5F.toByte(), 0x5E.toByte(), 0x59.toByte(), 0x58.toByte(), 0x5B.toByte(), 0xAA.toByte(),
        )

        val key = byteArrayOf(0x55)
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.XOR, key = key)

        //Act
        val decryptedData = pmdSecret.decryptArray(chipper)

        //Assert
        Assert.assertArrayEquals(expectedDecryptedData, decryptedData)
    }

    @Test
    fun `test decryption strategy AE128`() {
        //Arrange
        val chipper = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte()
        )

        val expectedDecryptedData = byteArrayOf(
            0x60.toByte(), 0x08.toByte(), 0x6b.toByte(), 0xda.toByte(), 0x00.toByte(), 0xdb.toByte(), 0x42.toByte(), 0x62.toByte(), 0x34.toByte(), 0x60.toByte(), 0x27.toByte(), 0x43.toByte(), 0x71.toByte(), 0xa7.toByte(), 0x53.toByte(), 0x68.toByte(),
            0x60.toByte(), 0x08.toByte(), 0x6b.toByte(), 0xda.toByte(), 0x00.toByte(), 0xdb.toByte(), 0x42.toByte(), 0x62.toByte(), 0x34.toByte(), 0x60.toByte(), 0x27.toByte(), 0x43.toByte(), 0x71.toByte(), 0xa7.toByte(), 0x53.toByte(), 0x68.toByte(),
            0x6f.toByte(), 0x5e.toByte(), 0x05.toByte(), 0x8b.toByte(), 0x37.toByte(), 0xdd.toByte(), 0xd1.toByte(), 0xed.toByte(), 0x0e.toByte(), 0xf2.toByte(), 0x89.toByte(), 0xef.toByte(), 0xf8.toByte(), 0xb2.toByte(), 0x85.toByte(), 0x54.toByte(),
        )

        val key = key16bytes
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.AES128, key = key)

        //Act
        val decryptedData = pmdSecret.decryptArray(chipper)

        //Assert
        Assert.assertArrayEquals(expectedDecryptedData, decryptedData)
    }

    @Test
    fun `test decryption strategy AES256`() {
        //Arrange
        val chipper = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte()
        )

        val expectedDecryptedData = byteArrayOf(
            0xc8.toByte(), 0x0d.toByte(), 0x56.toByte(), 0xbb.toByte(), 0x97.toByte(), 0x7a.toByte(), 0x42.toByte(), 0x5f.toByte(), 0x5a.toByte(), 0xa1.toByte(), 0xcd.toByte(), 0xfc.toByte(), 0x24.toByte(), 0xa2.toByte(), 0x78.toByte(), 0x12.toByte(),
            0xc8.toByte(), 0x0d.toByte(), 0x56.toByte(), 0xbb.toByte(), 0x97.toByte(), 0x7a.toByte(), 0x42.toByte(), 0x5f.toByte(), 0x5a.toByte(), 0xa1.toByte(), 0xcd.toByte(), 0xfc.toByte(), 0x24.toByte(), 0xa2.toByte(), 0x78.toByte(), 0x12.toByte(),
            0x30.toByte(), 0x04.toByte(), 0xb9.toByte(), 0x9f.toByte(), 0x6f.toByte(), 0xfa.toByte(), 0x3b.toByte(), 0xb7.toByte(), 0x73.toByte(), 0xb1.toByte(), 0x75.toByte(), 0xa5.toByte(), 0x23.toByte(), 0x5d.toByte(), 0xcb.toByte(), 0x93.toByte(),
        )

        val key = key16bytes + key16bytes
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.AES256, key = key)

        //Act
        val decryptedData = pmdSecret.decryptArray(chipper)

        //Assert
        Assert.assertArrayEquals(expectedDecryptedData, decryptedData)
    }

    @Test
    fun pmdSecretGoldenVectors_matchAndroidBehavior() {
        val vectors = loadPmdSecretVectors()
        Assert.assertTrue("Expected PMD secret golden vectors", vectors.isNotEmpty())

        vectors.forEach { vector ->
            val caseId = vector.get("id").asString
            val input = vector.getAsJsonObject("input")
            val expected = vector.getAsJsonObject("expected")
            val androidExpectations = vector
                .getAsJsonObject("platformExpectations")
                ?.getAsJsonObject("android")

            when (input.get("operation").asString) {
                "serialize" -> {
                    val secret = PmdSecret(input.strategy(), input.get("keyHex").asString.hexToByteArray())
                    Assert.assertEquals(caseId, expected.get("serializedHex").asString, secret.serializeToPmdSettings().toHexString())
                }
                "decrypt" -> {
                    val secret = PmdSecret(input.strategy(), input.get("keyHex").asString.hexToByteArray())
                    val decrypted = secret.decryptArray(input.get("cipherHex").asString.hexToByteArray())
                    Assert.assertEquals(caseId, expected.get("decryptedHex").asString, decrypted.toHexString())
                }
                "construct" -> {
                    val key = input.get("keyHex").asString.hexToByteArray()
                    if (androidExpectations?.has("constructorError") == true) {
                        assertConstructorError(caseId, androidExpectations.get("constructorError").asString, input.strategy(), key)
                    } else {
                        PmdSecret(input.strategy(), key)
                    }
                }
                "fromByte" -> {
                    val strategyByte = input.get("strategyByteHex").asString.hexToByteArray().single().toUByte()
                    if (androidExpectations?.has("strategyError") == true) {
                        assertStrategyError(caseId, androidExpectations.get("strategyError").asString, strategyByte)
                    } else {
                        Assert.assertEquals(caseId, PmdSecret.SecurityStrategy.valueOf(expected.get("strategy").asString), PmdSecret.SecurityStrategy.fromByte(strategyByte))
                    }
                }
                else -> Assert.fail("$caseId has unsupported operation ${input.get("operation").asString}")
            }
        }
    }

    @Test
    fun `pmd secret golden vectors follow neutral KMP vector shape`() {
        loadPmdSecretVectors().forEach { vector ->
            val id = vector.get("id")?.asString ?: "unknown-vector"

            Assert.assertTrue(id, vector.has("area"))
            Assert.assertTrue(id, vector.has("case"))
            Assert.assertTrue(id, vector.has("source"))
            Assert.assertTrue(id, vector.has("input"))
            Assert.assertTrue(id, vector.has("expected"))
            Assert.assertTrue(id, vector.has("platforms"))
            val platforms = vector.getAsJsonObject("platforms")
            Assert.assertTrue(id, platforms.has("android"))
            Assert.assertTrue(id, platforms.has("ios"))
            Assert.assertTrue(id, platforms.has("common"))
        }
    }

    @Test
    fun `PMD secret readiness manifest is pinned before secret strategy migration`() {
        val manifest = loadPmdSecretReadinessManifest()
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val consumerTests = manifest.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val policyPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }

        Assert.assertEquals("pmd-secret-readiness", manifest.get("id").asString)
        Assert.assertEquals("pmdSecretReadiness", input.get("kind").asString)
        Assert.assertEquals(PMD_SECRET_READINESS_POLICY_PATHS, policyPaths)
        Assert.assertEquals(PMD_SECRET_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(PMD_SECRET_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(PMD_SECRET_READINESS_DECISION, expected.get("commonDecision").asString)
        Assert.assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSecretTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("PmdSecretTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.PmdSecretCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private val PMD_SECRET_READINESS_POLICY_PATHS = listOf(
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

    private val PMD_SECRET_READINESS_FAMILIES = listOf(
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
        "shared-common-aes-production-decryption",
        "platform-pmd-secret-vector-reference-gate",
        "compile-verification-gate"
    )

    private val PMD_SECRET_READINESS_DECISION = "PMD secret strategy migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS PMD secret tests continue to reference the same vectors, security strategy byte mapping, unknown strategy rejection, SECURITY setting serialization, NONE/XOR/AES key validation, shared production NONE/XOR decryption, AES fixture pinning, AES block-alignment gating, shared common AES production decryption, and compile verification remain explicit before remaining fallback removal moves."

    private fun assertConstructorError(caseId: String, expectedError: String, strategy: PmdSecret.SecurityStrategy, key: ByteArray) {
        when (expectedError) {
            "illegalArgumentException" -> Assert.assertThrows(caseId, IllegalArgumentException::class.java) {
                PmdSecret(strategy, key)
            }
            else -> Assert.fail("$caseId has unsupported constructor error expectation $expectedError")
        }
    }

    private fun assertStrategyError(caseId: String, expectedError: String, strategyByte: UByte) {
        when (expectedError) {
            "securityStrategyUnknown" -> Assert.assertThrows(caseId, SecurityError.SecurityStrategyUnknown::class.java) {
                PmdSecret.SecurityStrategy.fromByte(strategyByte)
            }
            else -> Assert.fail("$caseId has unsupported strategy error expectation $expectedError")
        }
    }

    private fun loadPmdSecretVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/pmd")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" && file.name.startsWith("secret-") }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
            .filter { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString != "pmdSecretReadiness" }
    }

    private fun loadPmdSecretReadinessManifest(): JsonObject {
        val manifestFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/pmd/secret-readiness.json")
        FileReader(manifestFile).use { reader ->
            return JsonParser().parse(reader).asJsonObject
        }
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

    private fun JsonObject.strategy(): PmdSecret.SecurityStrategy = PmdSecret.SecurityStrategy.valueOf(get("strategy").asString)

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }
}
