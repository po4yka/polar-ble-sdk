package com.polar.androidcommunications.common.ble

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileReader

class TypeUtilsTest {

    @Test
    fun `test array conversion to unsigned byte max value`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte())
        val expectedValue = 0xFFu.toUByte()

        // Act
        val result = TypeUtils.convertArrayToUnsignedByte(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned int max value`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val expectedValue = 0xFFFFFFFFu

        // Act
        val result = TypeUtils.convertArrayToUnsignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned int min value`() {
        // Arrange
        val byteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val expectedValue = 0x00000000u

        // Act
        val result = TypeUtils.convertArrayToUnsignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned int max positive int`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F.toByte())
        val expectedValue = 0x7FFFFFFFu

        // Act
        val result = TypeUtils.convertArrayToUnsignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned int small array`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val expectedValue = 0xFFFFu

        // Act
        val result = TypeUtils.convertArrayToUnsignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned int too big array`() {
        // Arrange
        val byteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00)

        // Act & Assert
        assertThrows(AssertionError::class.java) {
            TypeUtils.convertArrayToUnsignedInt(byteArray)
        }
    }

    @Test
    fun `test array conversion to unsigned long max value`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val expectedValue = 0xFFFFFFFFFFFFFFFFu

        // Act
        val result = TypeUtils.convertArrayToUnsignedLong(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned long min value`() {
        // Arrange
        val byteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val expectedValue = 0x0000000000000000u.toULong()

        // Act
        val result = TypeUtils.convertArrayToUnsignedLong(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned long max positive int`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F.toByte())
        val expectedValue = 0x7FFFFFFFFFFFFFFFu

        // Act
        val result = TypeUtils.convertArrayToUnsignedLong(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned long small array`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val expectedValue = 0xFFFFu.toULong()

        // Act
        val result = TypeUtils.convertArrayToUnsignedLong(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to unsigned long too big array`() {
        // Arrange
        val byteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)

        // Act & Assert
        assertThrows(AssertionError::class.java) {
            TypeUtils.convertArrayToUnsignedLong(byteArray)
        }
    }

    @Test
    fun `test array conversion to signed int max value`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val expectedValue = -1

        // Act
        val result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to signed int min value`() {
        // Arrange
        val byteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val expectedValue = 0

        // Act
        val result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to signed int max positive int`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F.toByte())
        val expectedValue = Int.MAX_VALUE

        // Act
        val result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }

    @Test
    fun `test array conversion to signed int small array`() {
        // Arrange
        val byteArray = byteArrayOf(0xFF.toByte())
        val expectedValue = -1

        // Act
        val result = TypeUtils.convertArrayToSignedInt(byteArray)

        // Assert
        assertEquals(expectedValue, result)
    }


    @Test
    fun `test array conversion to signed int too big array`() {
        // Arrange
        val byteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00)

        // Act & Assert
        assertThrows(AssertionError::class.java) {
            TypeUtils.convertArrayToSignedInt(byteArray)
        }
    }

    @Test
    fun `test conversion unsigned byte to int`() {
        // Arrange
        val testByte1: Byte = 0x00.toByte()
        val testByte2: Byte = 0x80.toByte()
        val testByte3: Byte = 0xFF.toByte()
        val testByte4: Byte = 0x55.toByte()

        // Act & Assert
        assertEquals(0, TypeUtils.convertUnsignedByteToInt(testByte1))
        assertEquals(128, TypeUtils.convertUnsignedByteToInt(testByte2))
        assertEquals(255, TypeUtils.convertUnsignedByteToInt(testByte3))
        assertEquals(85, TypeUtils.convertUnsignedByteToInt(testByte4))

    }

    @Test
    fun typeUtilsGoldenVectors_matchAndroidBehavior() {
        val vectors = loadTypeUtilsVectors()
        assertTrue("Expected type utility golden vectors", vectors.isNotEmpty())

        vectors.forEach { vector ->
            val input = vector.getAsJsonObject("input")
            val expected = vector.platformExpected("android")
            val bytes = input.get("hex").asString.hexToByteArray()
            val caseId = vector.get("id").asString
            val offset = input.get("offset")?.asInt
            val size = input.get("size")?.asInt

            if (expected.has("unsignedByte")) {
                assertEquals(caseId, expected.get("unsignedByte").asInt.toUByte(), TypeUtils.convertArrayToUnsignedByte(bytes))
            }

            if (expected.has("unsignedInt")) {
                assertEquals(caseId, expected.get("unsignedInt").asLong.toUInt(), bytes.convertToUnsignedInt(offset, size))
            }

            if (expected.has("unsignedLong")) {
                assertEquals(caseId, expected.get("unsignedLong").asString.toULong(), bytes.convertToUnsignedLong(offset, size))
            }

            if (expected.has("signedInt")) {
                assertEquals(caseId, expected.get("signedInt").asInt, bytes.convertToSignedInt(offset, size))
            }

            if (expected.has("unsignedIntError")) {
                assertThrows(caseId, AssertionError::class.java) {
                    bytes.convertToUnsignedInt(offset, size)
                }
            }

            if (expected.has("unsignedLongError")) {
                assertThrows(caseId, AssertionError::class.java) {
                    bytes.convertToUnsignedLong(offset, size)
                }
            }

            if (expected.has("signedIntError")) {
                assertThrows(caseId, AssertionError::class.java) {
                    bytes.convertToSignedInt(offset, size)
                }
            }
        }
    }

    @Test
    fun `type utility golden vectors follow neutral KMP vector shape`() {
        loadTypeUtilsVectors().forEach { vector ->
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

    @Test
    fun `type utils readiness manifest is pinned before parser primitive migration`() {
        val readiness = loadTypeUtilsReadinessManifest()
        val input = readiness.getAsJsonObject("input")
        val expected = readiness.getAsJsonObject("expected")
        val consumerTests = readiness.getAsJsonObject("consumerTests")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        assertEquals("type-utils-readiness", readiness.get("id").asString)
        assertEquals("typeUtilsReadiness", input.get("kind").asString)
        assertEquals(
            listOf(
                "protocol/type-utils/empty-payload-platform-difference.json",
                "protocol/type-utils/offset-signed-int-negative-boundary.json",
                "protocol/type-utils/offset-unsigned-int-little-endian.json",
                "protocol/type-utils/signed-int-24bit-negative-one.json",
                "protocol/type-utils/signed-int-max.json",
                "protocol/type-utils/signed-int-min-16bit.json",
                "protocol/type-utils/signed-int-min-24bit.json",
                "protocol/type-utils/signed-int-min-32bit.json",
                "protocol/type-utils/signed-int-negative-one.json",
                "protocol/type-utils/signed-int-too-long.json",
                "protocol/type-utils/unsigned-byte-max.json",
                "protocol/type-utils/unsigned-int-high-bit-16bit-platform-difference.json",
                "protocol/type-utils/unsigned-int-high-bit-platform-difference.json",
                "protocol/type-utils/unsigned-int-little-endian.json",
                "protocol/type-utils/unsigned-int-too-long.json",
                "protocol/type-utils/unsigned-long-max.json",
                "protocol/type-utils/unsigned-long-too-long.json"
            ),
            policyVectorPaths
        )
        val expectedFamilies = listOf(
            "unsigned-byte-conversion",
            "little-endian-unsigned-int-conversion",
            "little-endian-unsigned-long-conversion",
            "signed-int-sign-extension",
            "offset-and-size-selection",
            "signed-minimum-boundaries",
            "unsigned-high-bit-platform-decision",
            "empty-payload-error-policy",
            "payload-too-long-error-policy",
            "uint64-max-decimal-preservation",
            "platform-type-utils-vector-reference-gate",
            "compile-verification-gate"
        )
        assertEquals(expectedFamilies, requiredFamilies)
        assertEquals(expectedFamilies, coveredFamilies)
        assertEquals(
            "Type utility migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS type utility tests continue to reference the same vectors, unsigned byte/int/long conversion, signed sign extension, offset and size selection, signed-minimum boundaries, high-bit unsigned platform decisions, empty payload and payload-too-long typed errors, UInt64 max decimal preservation, and compile verification remain explicit before production parser primitives move.",
            expected.get("commonDecision").asString
        )
        assertEquals(listOf("com.polar.androidcommunications.common.ble.TypeUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("TypeUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.TypeUtilsCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun JsonObject.platformExpected(platform: String): JsonObject {
        return if (has("platformExpectations")) {
            getAsJsonObject("platformExpectations").getAsJsonObject(platform)
        } else {
            getAsJsonObject("expected")
        }
    }

    private fun ByteArray.convertToUnsignedInt(offset: Int?, size: Int?): UInt {
        return if (offset != null && size != null) {
            TypeUtils.convertArrayToUnsignedInt(this, offset, size)
        } else {
            TypeUtils.convertArrayToUnsignedInt(this)
        }
    }

    private fun ByteArray.convertToUnsignedLong(offset: Int?, size: Int?): ULong {
        return if (offset != null && size != null) {
            TypeUtils.convertArrayToUnsignedLong(this, offset, size)
        } else {
            TypeUtils.convertArrayToUnsignedLong(this)
        }
    }

    private fun ByteArray.convertToSignedInt(offset: Int?, size: Int?): Int {
        return if (offset != null && size != null) {
            TypeUtils.convertArrayToSignedInt(this, offset, size)
        } else {
            TypeUtils.convertArrayToSignedInt(this)
        }
    }

    private fun loadTypeUtilsVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/type-utils")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
            .filterNot { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString == "typeUtilsReadiness" }
    }

    private fun loadTypeUtilsReadinessManifest(): JsonObject {
        val vectorFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/type-utils/type-utils-readiness.json")
        FileReader(vectorFile).use { reader ->
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

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
