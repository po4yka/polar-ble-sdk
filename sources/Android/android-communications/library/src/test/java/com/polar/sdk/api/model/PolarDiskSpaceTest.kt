package com.polar.sdk.api.model

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.assertEquals
import org.junit.Test
import protocol.PftpResponse
import java.io.File
import java.io.FileReader

class PolarDiskSpaceTest {

    @Test
    fun `PolarDiskSpaceData fromProto() should convert proto to PolarDiskSpaceData properly`() {
        // Arrange
        val proto = mockk<PftpResponse.PbPFtpDiskSpaceResult>()
        every { proto.fragmentSize } returns 512
        every { proto.totalFragments } returns 2048
        every { proto.freeFragments } returns 1024

        // Act
        val result = PolarDiskSpaceData.fromProto(proto)

        // Arrange
        assertEquals(1048576, result.totalSpace)
        assertEquals(524288, result.freeSpace)
    }

    @Test
    fun `disk space golden vectors convert proto fields to byte totals`() {
        loadDiskSpaceVectors()
            .filter { it.getAsJsonObject("input").has("proto") }
            .forEach { vector ->
                val caseId = vector.get("id").asString
                val protoFields = vector.getAsJsonObject("input").getAsJsonObject("proto")
                val proto = PftpResponse.PbPFtpDiskSpaceResult.newBuilder()
                    .setFragmentSize(protoFields.get("fragmentSize").asLong.toInt())
                    .setTotalFragments(protoFields.get("totalFragments").asLong)
                    .setFreeFragments(protoFields.get("freeFragments").asLong)
                    .build()
                val expected = vector.getAsJsonObject("expected")

                val result = PolarDiskSpaceData.fromProto(proto)

                assertEquals(caseId, expected.get("totalSpace").asLong, result.totalSpace)
                assertEquals(caseId, expected.get("freeSpace").asLong, result.freeSpace)
            }
    }

    @Test
    fun `disk space malformed protobuf vectors fail to parse`() {
        loadDiskSpaceVectors()
            .filter { it.getAsJsonObject("expected").get("error")?.asString == "parse-error" }
            .forEach { vector ->
                val payload = vector.getAsJsonObject("input").get("hex").asString.hexToBytes()

                val result = runCatching { PftpResponse.PbPFtpDiskSpaceResult.parseFrom(payload) }

                assertTrue(vector.get("id").asString, result.isFailure)
            }
    }

    @Test
    fun `disk space golden vectors follow neutral shared vector shape`() {
        loadDiskSpaceVectors().forEach { vector ->
            val id = vector.get("id").asString
            assertTrue(id, vector.has("area"))
            assertTrue(id, vector.has("case"))
            assertTrue(id, vector.has("source"))
            assertTrue(id, vector.has("input"))
            assertTrue(id, vector.has("expected"))
            assertTrue(id, vector.has("platforms"))
            val platforms = vector.getAsJsonObject("platforms")
            assertTrue(id, platforms.get("android").asBoolean)
            assertTrue(id, platforms.get("ios").asBoolean)
            assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    private fun loadDiskSpaceVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/disk-space")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
            .filterNot { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString == "diskSpaceReadiness" }
    }

    @Test
    fun `disk space readiness manifest is pinned for shared model ownership`() {
        val vector = FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/disk-space/disk-space-readiness.json")).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
        assertEquals("disk-space-readiness", vector.get("id").asString)
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        assertEquals("diskSpaceReadiness", input.get("kind").asString)
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { path -> path.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { family -> family.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { family -> family.asString }
        val consumerTests = vector.getAsJsonObject("consumerTests")
        assertEquals(DISK_SPACE_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        assertEquals(DISK_SPACE_READINESS_FAMILIES, requiredFamilies)
        assertEquals(DISK_SPACE_READINESS_FAMILIES, coveredFamilies)
        assertEquals(DISK_SPACE_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        assertEquals(listOf("com.polar.sdk.api.model.PolarDiskSpaceTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("PolarDiskSpaceDataTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.DiskSpaceCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private val DISK_SPACE_READINESS_POLICY_VECTOR_PATHS = listOf(
        "sdk/disk-space/typical-fragments.json",
        "sdk/disk-space/zero-fragments.json",
        "sdk/disk-space/uint32-max-fragment-platform-difference.json",
        "sdk/disk-space/malformed-truncated-varint.json"
    )

    private val DISK_SPACE_READINESS_FAMILIES = listOf(
        "byte-total-calculation",
        "free-byte-calculation",
        "zero-fragment-counts",
        "unsigned-uint32-fragment-size-policy",
        "android-signed-fragment-platform-reference",
        "ios-unsigned-fragment-platform-reference",
        "typed-malformed-varint-parse-error",
        "platform-disk-space-vector-reference-gate",
        "compile-verification-gate"
    )

    private val DISK_SPACE_READINESS_COMMON_DECISION = "Disk-space model shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS disk-space tests continue to reference the same vectors, byte-total and free-byte calculations remain covered, zero-fragment counts remain explicit, fragment size uses the unsigned 32-bit policy instead of inheriting Android signed-int exposure or Swift UInt32 behavior accidentally, malformed truncated varints map to typed parse errors, and the shared tests are compile-verified."

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
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
}
