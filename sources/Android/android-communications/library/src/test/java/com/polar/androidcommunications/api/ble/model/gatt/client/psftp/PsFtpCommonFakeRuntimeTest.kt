package com.polar.androidcommunications.api.ble.model.gatt.client.psftp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.FileReader

internal class PsFtpCommonFakeRuntimeTest {

    @Test
    fun `common fake response runtime reassembles request responses`() {
        val vector = loadPsFtpVector("psftp-response", "request-response-reassembly")
        val runtime = FakePsFtpRuntime()
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }

        Assert.assertEquals(REQUEST_RESPONSE_REASSEMBLY_CASE_IDS, cases.map { it.get("id").asString })

        cases.forEach { testCase ->
            val response = runtime.request(testCase.getAsJsonArray("responseFramesHex").map { frame ->
                frame.asString.hexToByteArray()
            })
            Assert.assertEquals(
                testCase.get("id").asString,
                testCase.getAsJsonObject("expected").get("payloadHex").asString,
                response.toHexString()
            )
        }
    }

    @Test
    fun `common fake response runtime maps response error frames to typed response error`() {
        val vector = loadPsFtpVector("psftp-response", "request-response-error-policy")
        val runtime = FakePsFtpRuntime()
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }

        Assert.assertEquals(REQUEST_RESPONSE_ERROR_CASE_IDS, cases.map { it.get("id").asString })

        cases.forEach { testCase ->
            val thrown = Assert.assertThrows(FakePsFtpRuntime.ResponseError::class.java) {
                runtime.request(testCase.getAsJsonArray("responseFramesHex").map { frame ->
                    frame.asString.hexToByteArray()
                })
            }
            val iosErrorCode = testCase
                .getAsJsonObject("expected")
                .getAsJsonObject("ios")
                .get("errorCode")
                .asInt
            Assert.assertEquals(testCase.get("id").asString, iosErrorCode, thrown.errorCode)
        }
    }

    @Test
    fun `common fake notification runtime reassembles complete notifications`() {
        val vector = loadPsFtpVector("psftp-notifications", "notification-reassembly")
        val runtime = FakePsFtpRuntime()
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }

        Assert.assertEquals(NOTIFICATION_REASSEMBLY_CASE_IDS, cases.map { it.get("id").asString })

        cases.forEach { testCase ->
            val notification = runtime.waitNotification(testCase.getAsJsonArray("framesHex").map { frame ->
                FakePsFtpRuntime.NotificationPacket(frame.asString.hexToByteArray(), transportStatus = 0)
            }).single()
            val expected = testCase.getAsJsonObject("expected")
            Assert.assertEquals(testCase.get("id").asString, expected.get("id").asInt, notification.id)
            Assert.assertEquals(testCase.get("id").asString, expected.get("parametersHex").asString, notification.parameters.toHexString())
        }
    }

    @Test
    fun `common fake notification runtime preserves complete notification ordering`() {
        val vector = loadPsFtpVector("psftp-notifications", "notification-ordering")
        val runtime = FakePsFtpRuntime()
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }

        Assert.assertEquals(NOTIFICATION_ORDERING_CASE_IDS, cases.map { it.get("id").asString })

        cases.forEach { testCase ->
            val notifications = runtime.waitNotification(testCase.getAsJsonArray("packets").map { packet ->
                val packetObject = packet.asJsonObject
                FakePsFtpRuntime.NotificationPacket(
                    frame = packetObject.get("frameHex").asString.hexToByteArray(),
                    transportStatus = packetObject.get("status").asInt
                )
            })
            val expectedSequence = testCase.getAsJsonObject("expected").getAsJsonArray("sequence")
            Assert.assertEquals(testCase.get("id").asString, expectedSequence.size(), notifications.size)
            expectedSequence.forEachIndexed { index, expectedElement ->
                val expected = expectedElement.asJsonObject
                Assert.assertEquals(testCase.get("id").asString, expected.get("id").asInt, notifications[index].id)
                Assert.assertEquals(testCase.get("id").asString, expected.get("parametersHex").asString, notifications[index].parameters.toHexString())
            }
        }
    }

    @Test
    fun `common fake notification runtime maps missing LAST after MORE to typed continuation timeout`() {
        val vector = loadPsFtpVector("psftp-notifications", "notification-continuation-timeout-policy")
        val commonExpected = vector.getAsJsonObject("expected").getAsJsonObject("common")
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }
        Assert.assertEquals("typedContinuationTimeout", commonExpected.get("outcome").asString)
        Assert.assertEquals(NOTIFICATION_CONTINUATION_TIMEOUT_CASE_IDS, cases.map { it.get("id").asString })

        val runtime = FakePsFtpRuntime()
        cases.forEach { testCase ->
            val thrown = Assert.assertThrows(FakePsFtpRuntime.ContinuationTimeout::class.java) {
                runtime.waitNotification(testCase.getAsJsonArray("packets").map { packet ->
                    val packetObject = packet.asJsonObject
                    FakePsFtpRuntime.NotificationPacket(
                        frame = packetObject.get("frameHex").asString.hexToByteArray(),
                        transportStatus = packetObject.get("status").asInt
                    )
                })
            }
            Assert.assertEquals(testCase.get("id").asString, thrown.caseId)
        }
    }

    @Test
    fun `notification error vector keeps transport status split explicit before migration`() {
        val vector = loadPsFtpVector("psftp-notifications", "notification-error-policy")
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }

        Assert.assertEquals(NOTIFICATION_ERROR_CASE_IDS, cases.map { it.get("id").asString })

        cases.forEach { testCase ->
            val expected = testCase.getAsJsonObject("expected")
            if (testCase.get("id").asString == "transport-error-first-packet") {
                Assert.assertEquals("noEmission", expected.getAsJsonObject("android").get("outcome").asString)
                Assert.assertEquals("throwsResponseError", expected.getAsJsonObject("ios").get("outcome").asString)
                Assert.assertFalse("Common PSFTP notification transport-status policy must be decided before migration", expected.has("common"))
            }
        }
    }

    @Test
    fun `common fake write runtime maps peer interruption to typed response error`() {
        val vector = loadPsFtpVector("psftp-response", "write-interruption-error-policy")
        val thrown = Assert.assertThrows(FakePsFtpRuntime.ResponseError::class.java) {
            FakePsFtpRuntime().writeInterruptedByPeer(vector.getAsJsonObject("input").get("interruptFrameHex").asString.hexToByteArray())
        }
        val iosErrorCode = vector.getAsJsonObject("expected").getAsJsonObject("ios").get("errorCode").asInt
        Assert.assertEquals(iosErrorCode, thrown.errorCode)
        Assert.assertTrue(
            vector.getAsJsonObject("platformExpectations")
                .getAsJsonObject("commonDecision")
                .get("errorPolicy")
                .asString
                .contains("typed write-interrupted response error")
        )
    }

    @Test
    fun `common fake write runtime maps transport transmit failure to typed transport error`() {
        val vector = loadPsFtpVector("psftp-response", "write-transport-failure-policy")
        val failure = vector.getAsJsonObject("input").getAsJsonObject("failure")
        val thrown = Assert.assertThrows(FakePsFtpRuntime.TransportWriteFailure::class.java) {
            FakePsFtpRuntime().write(
                payload = vector.getAsJsonObject("input").get("payloadHex").asString.hexToByteArray(),
                transportTransmit = "failure",
                writeAck = "never",
                failureMessage = failure.get("message").asString
            )
        }
        Assert.assertEquals(failure.get("message").asString, thrown.message)
    }

    @Test
    fun `common fake write runtime maps missing write ack to typed write ack timeout`() {
        val vector = loadPsFtpVector("psftp-response", "write-ack-timeout-policy")
        val commonDecision = vector
            .getAsJsonObject("platformExpectations")
            .getAsJsonObject("commonDecision")
        Assert.assertTrue(commonDecision.get("errorPolicy").asString.contains("write acknowledgement timeout"))

        val input = vector.getAsJsonObject("input")
        val failure = input.getAsJsonObject("failure")
        val thrown = Assert.assertThrows(FakePsFtpRuntime.WriteAckTimeout::class.java) {
            FakePsFtpRuntime().write(
                payload = input.get("payloadHex").asString.hexToByteArray(),
                transportTransmit = failure.get("transportTransmit").asString,
                writeAck = failure.get("writeAck").asString
            )
        }
        Assert.assertEquals("firstMtuWriteAck", thrown.point)
    }

    @Test
    fun `write success progress vector keeps common progress policy explicit before migration`() {
        val vector = loadPsFtpVector("psftp-response", "write-success-progress")
        val progressPolicy = vector
            .getAsJsonObject("platformExpectations")
            .getAsJsonObject("commonDecision")
            .get("progressPolicy")
            .asString
        Assert.assertEquals(
            "android-currently-emits-negative-header-overhead-progress-before-payload-count-while-ios-emits-initial-zero-header-progress-and-final-payload-count",
            progressPolicy
        )
    }

    private fun loadPsFtpVector(directoryName: String, id: String): JsonObject {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/$directoryName")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
            .first { it.get("id").asString == id }
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
        require(length % 2 == 0) { "Hex string must contain an even number of characters" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }

    private companion object {
        val REQUEST_RESPONSE_REASSEMBLY_CASE_IDS = listOf("single-frame", "multi-frame")
        val REQUEST_RESPONSE_ERROR_CASE_IDS = listOf("known-error-no-such-file", "unknown-error-code")
        val NOTIFICATION_REASSEMBLY_CASE_IDS = listOf("single-frame", "multi-frame")
        val NOTIFICATION_ORDERING_CASE_IDS = listOf("two-single-frame-notifications")
        val NOTIFICATION_ERROR_CASE_IDS = listOf("rfc76-error-first-frame", "transport-error-first-packet")
        val NOTIFICATION_CONTINUATION_TIMEOUT_CASE_IDS = listOf("missing-last-frame-after-more")
    }
}

private class FakePsFtpRuntime {
    data class NotificationPacket(
        val frame: ByteArray,
        val transportStatus: Int
    )

    data class Notification(
        val id: Int,
        val parameters: ByteArray
    )

    class ResponseError(val errorCode: Int) : RuntimeException("PSFTP response error $errorCode")
    class ContinuationTimeout(val caseId: String) : RuntimeException("PSFTP continuation timed out for $caseId")
    class WriteAckTimeout(val point: String) : RuntimeException("PSFTP write acknowledgement timed out at $point")
    class TransportWriteFailure(message: String) : RuntimeException(message)

    fun request(responseFrames: List<ByteArray>): ByteArray {
        val payload = mutableListOf<Byte>()
        responseFrames.forEach { frame ->
            val status = rfc76Status(frame)
            if (status == BlePsFtpUtils.RFC76_STATUS_ERROR_OR_RESPONSE) {
                throw ResponseError(frame.readUInt16Le(offset = 1))
            }
            payload += frame.drop(1)
        }
        return payload.toByteArray()
    }

    fun waitNotification(packets: List<NotificationPacket>): List<Notification> {
        var waitingForLastFrame = false
        val notifications = mutableListOf<Notification>()
        val payload = mutableListOf<Byte>()
        packets.forEachIndexed { index, packet ->
            if (packet.transportStatus != 0) {
                return@forEachIndexed
            }
            val status = rfc76Status(packet.frame)
            if (status == BlePsFtpUtils.RFC76_STATUS_MORE) {
                waitingForLastFrame = true
                payload += packet.frame.drop(1)
            }
            if (status == BlePsFtpUtils.RFC76_STATUS_LAST) {
                payload += packet.frame.drop(1)
                val payloadBytes = payload.toByteArray()
                notifications += Notification(
                    id = payloadBytes.first().toInt() and 0xFF,
                    parameters = payloadBytes.drop(1).toByteArray()
                )
                payload.clear()
                waitingForLastFrame = false
                return@forEachIndexed
            }
            if (index == packets.lastIndex && waitingForLastFrame) {
                throw ContinuationTimeout("missing-last-frame-after-more")
            }
        }
        return notifications
    }

    fun write(payload: ByteArray, transportTransmit: String, writeAck: String, failureMessage: String = "transport write failure") {
        require(payload.isNotEmpty()) { "payload must not be empty" }
        if (transportTransmit != "success") {
            throw TransportWriteFailure(failureMessage)
        }
        if (transportTransmit == "success" && writeAck == "never") {
            throw WriteAckTimeout("firstMtuWriteAck")
        }
    }

    fun writeInterruptedByPeer(interruptFrame: ByteArray) {
        if (rfc76Status(interruptFrame) == BlePsFtpUtils.RFC76_STATUS_ERROR_OR_RESPONSE) {
            throw ResponseError(interruptFrame.readUInt16Le(offset = 1))
        }
    }

    private fun rfc76Status(frame: ByteArray): Int = (frame.first().toInt() shr 1) and 0x03

    private fun ByteArray.readUInt16Le(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
    }
}
