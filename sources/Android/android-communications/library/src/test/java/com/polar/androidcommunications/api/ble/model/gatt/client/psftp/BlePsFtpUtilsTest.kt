package com.polar.androidcommunications.api.ble.model.gatt.client.psftp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.RFC76_STATUS_LAST
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.RFC76_STATUS_MORE
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.processRfc76MessageFrameHeader
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileReader

internal class BlePsFtpUtilsTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `process first frame of sequence more to come`() {
        //Arrange
        val data = byteArrayOf(0x06.toByte(), 0x0A.toByte(), 0x06.toByte(), 0x08.toByte(), 0x02.toByte(), 0x10.toByte(), 0x04.toByte(), 0x18.toByte(), 0x03.toByte(), 0x12.toByte(), 0x06.toByte(), 0x08.toByte(), 0x00.toByte(), 0x10.toByte(), 0x09.toByte(), 0x18.toByte(), 0x05.toByte(), 0x1A.toByte(), 0x06.toByte(), 0x08.toByte())
        val header = BlePsFtpUtils.PftpRfc76ResponseHeader()
        //Act
        processRfc76MessageFrameHeader(header, data)
        //Assert
        Assert.assertEquals(0, header.next)
        Assert.assertEquals(RFC76_STATUS_MORE, header.status)
        Assert.assertEquals(0, header.sequenceNumber)
        Assert.assertEquals(data.size - 1, header.payload?.size)
        Assert.assertArrayEquals(data.copyOfRange(1, data.size), header.payload)
    }

    @Test
    fun `process middle frame of sequence more to come`() {
        //Arrange
        val data = byteArrayOf(0x97.toByte(), 0x22.toByte(), 0x0A.toByte(), 0x03.toByte(), 0x47.toByte(), 0x50.toByte(), 0x53.toByte(), 0x1A.toByte(), 0x1B.toByte(), 0x08.toByte(), 0x01.toByte(), 0x10.toByte(), 0x00.toByte(), 0x18.toByte(), 0x00.toByte(), 0x22.toByte(), 0x13.toByte(), 0x61.toByte(), 0x32.toByte(), 0x30.toByte())
        val header = BlePsFtpUtils.PftpRfc76ResponseHeader()
        //Act
        processRfc76MessageFrameHeader(header, data)
        //Assert
        Assert.assertEquals(1, header.next)
        Assert.assertEquals(RFC76_STATUS_MORE, header.status)
        Assert.assertEquals(9, header.sequenceNumber)
        Assert.assertEquals(data.size - 1, header.payload?.size)
        Assert.assertArrayEquals(data.copyOfRange(1, data.size), header.payload)
    }

    @Test
    fun `process last frame of sequence no more data`() {
        //Arrange
        val data = byteArrayOf(0xC3.toByte(), 0x5A.toByte(), 0x48.toByte(), 0x5F.toByte(), 0x4A.toByte(), 0x41.toByte(), 0x10.toByte(), 0x09.toByte())
        val header = BlePsFtpUtils.PftpRfc76ResponseHeader()
        //Act
        processRfc76MessageFrameHeader(header, data)
        //Assert
        Assert.assertEquals(1, header.next)
        Assert.assertEquals(RFC76_STATUS_LAST, header.status)
        Assert.assertEquals(12, header.sequenceNumber)
        Assert.assertEquals(data.size - 1, header.payload?.size)
        Assert.assertArrayEquals(data.copyOfRange(1, data.size), header.payload)
    }

    @Test
    fun `rfc76 golden vectors decode frame headers`() {
        val vectors = loadRfc76Vectors()
        Assert.assertTrue("Expected PSFTP RFC76 golden vectors", vectors.isNotEmpty())

        vectors.forEach { vector ->
            val caseId = vector.get("id").asString
            val expected = vector.getAsJsonObject("expected")
            val header = BlePsFtpUtils.PftpRfc76ResponseHeader()
            processRfc76MessageFrameHeader(header, vector.getAsJsonObject("input").get("frameHex").asString.hexToByteArray())

            Assert.assertEquals("$caseId next", expected.get("next").asInt, header.next)
            Assert.assertEquals("$caseId status", expected.get("status").asInt, header.status)
            Assert.assertEquals("$caseId sequenceNumber", expected.get("sequenceNumber").asLong, header.sequenceNumber)
            if (expected.get("payloadHex").isJsonNull) {
                Assert.assertNull("$caseId payload", header.payload)
            } else {
                Assert.assertArrayEquals("$caseId payload", expected.get("payloadHex").asString.hexToByteArray(), header.payload)
            }
            if (expected.get("error").isJsonNull) {
                Assert.assertEquals("$caseId error", 0, header.error)
            } else {
                Assert.assertEquals("$caseId error", expected.getAsJsonObject("error").get("android").asInt, header.error)
            }
        }
    }

    @Test
    fun `rfc76 golden vectors follow neutral KMP vector shape`() {
        val vectors = loadRfc76Vectors()
        Assert.assertTrue("Expected PSFTP RFC76 golden vectors", vectors.isNotEmpty())
        vectors.forEach { vector ->
            val id = vector.get("id").asString
            Assert.assertTrue(id, vector.has("area"))
            Assert.assertTrue(id, vector.has("case"))
            Assert.assertTrue(id, vector.has("source"))
            Assert.assertTrue(id, vector.has("input"))
            Assert.assertTrue(id, vector.has("expected"))
            Assert.assertTrue(id, vector.has("platforms"))
            Assert.assertTrue(id, vector.getAsJsonObject("input").has("frameHex"))
            val platforms = vector.getAsJsonObject("platforms")
            Assert.assertTrue(id, platforms.get("android").asBoolean)
            Assert.assertTrue(id, platforms.get("ios").asBoolean)
            Assert.assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    @Test
    fun `message stream golden vectors encode RFC60 messages`() {
        val vector = loadPsFtpVector("psftp-message-stream", "complete-message-streams.json")
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases")
        Assert.assertEquals(COMPLETE_MESSAGE_CASE_IDS, cases.map { element -> element.asJsonObject.get("id").asString })
        cases.forEach { element ->
            val case = element.asJsonObject
            val type = when (case.get("type").asString) {
                "request" -> BlePsFtpUtils.MessageType.REQUEST
                "query" -> BlePsFtpUtils.MessageType.QUERY
                "notification" -> BlePsFtpUtils.MessageType.NOTIFICATION
                else -> error("Unknown message type ${case.get("type").asString}")
            }
            val header = case.get("headerHex").asString.hexToByteArray()
            val data = if (case.has("dataHex")) {
                ByteArrayInputStream(case.get("dataHex").asString.hexToByteArray())
            } else {
                null
            }
            val stream = BlePsFtpUtils.makeCompleteMessageStream(
                ByteArrayInputStream(header),
                data,
                type,
                case.get("idValue").asInt
            )
            Assert.assertArrayEquals(case.get("id").asString, case.get("expectedHex").asString.hexToByteArray(), stream.readBytes())
        }
    }

    @Test
    fun `message stream golden vectors split RFC76 frames`() {
        val vector = loadPsFtpVector("psftp-message-stream", "rfc76-frame-splitting.json")
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases")
        Assert.assertEquals(RFC76_FRAME_SPLITTING_CASE_IDS, cases.map { element -> element.asJsonObject.get("id").asString })
        cases.forEach { element ->
            val case = element.asJsonObject
            val frames = BlePsFtpUtils.buildRfc76MessageFrameAll(
                ByteArrayInputStream(case.get("payloadHex").asString.hexToByteArray()),
                case.get("mtu").asInt,
                BlePsFtpUtils.Rfc76SequenceNumber()
            )
            val actual = frames.map { it.toHexString() }
            val expected = case.getAsJsonArray("expectedFramesHex").map { it.asString }
            Assert.assertEquals(case.get("id").asString, expected, actual)
        }
    }

    @Test
    fun `message stream golden vectors follow neutral KMP vector shape`() {
        listOf("complete-message-streams.json", "rfc76-frame-splitting.json").forEach { fileName ->
            val vector = loadPsFtpVector("psftp-message-stream", fileName)
            val id = vector.get("id").asString
            Assert.assertTrue(id, vector.has("area"))
            Assert.assertTrue(id, vector.has("case"))
            Assert.assertTrue(id, vector.has("source"))
            Assert.assertTrue(id, vector.has("input"))
            Assert.assertTrue(id, vector.has("expected"))
            Assert.assertTrue(id, vector.has("platforms"))
            Assert.assertTrue(id, vector.getAsJsonObject("input").has("cases"))
            val platforms = vector.getAsJsonObject("platforms")
            Assert.assertTrue(id, platforms.get("android").asBoolean)
            Assert.assertTrue(id, platforms.get("ios").asBoolean)
            Assert.assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    @Test
    fun `psftp byte codec readiness manifest is pinned before codec migration`() {
        val vector = loadPsFtpVector("psftp-message-stream", "byte-codec-readiness.json")
        Assert.assertEquals("psftp-byte-codec-readiness", vector.get("id").asString)
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        Assert.assertEquals("psFtpByteCodecReadiness", input.get("kind").asString)
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { path -> path.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { family -> family.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { family -> family.asString }
        Assert.assertEquals(PSFTP_BYTE_CODEC_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        Assert.assertEquals(PSFTP_BYTE_CODEC_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(PSFTP_BYTE_CODEC_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(PSFTP_BYTE_CODEC_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        Assert.assertEquals(
            listOf("com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtilsTest"),
            consumerTests.getAsJsonArray("android").map { test -> test.asString }
        )
        Assert.assertEquals(listOf("BlePsFtpUtilityTest"), consumerTests.getAsJsonArray("ios").map { test -> test.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.PsFtpByteCodecCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { test -> test.asString })
    }

    private val PSFTP_BYTE_CODEC_READINESS_POLICY_VECTOR_PATHS = listOf(
        "sdk/psftp-rfc76/error-frame-ffff.json",
        "sdk/psftp-rfc76/final-last-frame.json",
        "sdk/psftp-rfc76/first-more-frame.json",
        "sdk/psftp-rfc76/header-only-last-frame.json",
        "sdk/psftp-rfc76/header-only-more-frame.json",
        "sdk/psftp-rfc76/middle-more-frame.json",
        "sdk/psftp-rfc76/single-last-frame.json",
        "sdk/psftp-message-stream/complete-message-streams.json",
        "sdk/psftp-message-stream/rfc76-frame-splitting.json"
    )

    private val PSFTP_BYTE_CODEC_READINESS_FAMILIES = listOf(
        "rfc76-header-next-bit",
        "rfc76-status-decoding",
        "rfc76-sequence-number-decoding",
        "rfc76-payload-slicing",
        "rfc76-error-frame-platform-split",
        "rfc60-request-stream-encoding",
        "rfc60-query-stream-encoding",
        "rfc60-notification-stream-encoding",
        "android-request-file-data-append-policy",
        "ios-request-write-frame-splitting",
        "rfc76-mtu-frame-splitting",
        "rfc76-sequence-wrap",
        "platform-codec-vector-reference-gate",
        "compile-verification-gate"
    )

    private companion object {
        val COMPLETE_MESSAGE_CASE_IDS = listOf(
            "request-header-only",
            "android-request-with-file-data",
            "query-with-header",
            "notification-with-header",
            "notification-empty-header"
        )
        val RFC76_FRAME_SPLITTING_CASE_IDS = listOf(
            "empty-payload",
            "exactly-one-frame",
            "two-frames",
            "sequence-wraps-after-fifteen"
        )
        const val PSFTP_BYTE_CODEC_READINESS_COMMON_DECISION = "PSFTP byte-codec migration may proceed only after every RFC76 and RFC60 vector listed in this readiness manifest is executable from shared commonTest, Android and iOS codec tests continue to reference the same vectors, header next/status/sequence/payload decoding, RFC76 error-frame platform split, complete-message stream encoding, Android file-data append behavior, iOS request write frame splitting, MTU frame splitting, sequence wrap, and the shared tests are compile-verified."
    }

    private fun loadRfc76Vectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/psftp-rfc76")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
    }

    private fun loadPsFtpVector(directoryName: String, fileName: String): JsonObject {
        FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/$directoryName/$fileName")).use { reader ->
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
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }
    }
}
