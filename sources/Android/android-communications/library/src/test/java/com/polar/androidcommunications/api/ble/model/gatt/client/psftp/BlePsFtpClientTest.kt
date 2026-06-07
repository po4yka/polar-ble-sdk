package com.polar.androidcommunications.api.ble.model.gatt.client.psftp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.BleGattBase
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.RFC77_PFTP_D2H_CHARACTERISTIC
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.RFC77_PFTP_MTU_CHARACTERISTIC
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils.RFC77_PFTP_SERVICE
import com.polar.androidcommunications.api.ble.model.proto.CommunicationsPftpRequest
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.util.*

internal class BlePsFtpClientTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @MockK
    lateinit var mockGattTxInterface: BleGattTxInterface

    private lateinit var blePsFtpClient: BlePsFtpClient

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        blePsFtpClient = BlePsFtpClient(mockGattTxInterface)
        every { mockGattTxInterface.isConnected() } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `test psftp client request`() = runTest {
        // Arrange
        val transmitService = slot<UUID>()
        val transmitCharacteristics = slot<UUID>()
        val responsePayload: Byte = 0x22

        every { mockGattTxInterface.transmitMessages(capture(transmitService), capture(transmitCharacteristics), any(), any()) } answers {
            // only after the request() has sent data to device, simulate the device responses
            blePsFtpClient.processServiceDataWritten(RFC77_PFTP_MTU_CHARACTERISTIC, 0)
            val validDeviceResponse = byteArrayOf(0x02, responsePayload)
            blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, validDeviceResponse, 0, true)
        }
        every { mockGattTxInterface.gattClientRequestStopScanning() } returns Unit
        every { mockGattTxInterface.gattClientResumeScanning() } returns Unit

        val randomRequestData = byteArrayOf(0x01.toByte(), 0x38.toByte(), 0x6C.toByte(), 0x31.toByte(), 0x72.toByte(), 0xA4.toByte(), 0xD3.toByte(), 0x23.toByte(), 0x0D.toByte(), 0x7F.toByte())

        // Enable the PsFTP
        blePsFtpClient.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)

        // Act
        val result = blePsFtpClient.request(randomRequestData)

        // Assert
        verify(exactly = 1) { mockGattTxInterface.gattClientRequestStopScanning() }
        verify(exactly = 1) { mockGattTxInterface.gattClientResumeScanning() }
        assertTrue(RFC77_PFTP_SERVICE.equals(transmitService.captured))
        assertTrue(RFC77_PFTP_MTU_CHARACTERISTIC.equals(transmitCharacteristics.captured))
        assertEquals(responsePayload, result.toByteArray()[0])
    }

    @Test
    fun `test psftp client read response`() {
        // Arrange
        val frame0 = byteArrayOf(0x06.toByte(), 0x0A.toByte(), 0x06.toByte(), 0x08.toByte(), 0x02.toByte(), 0x10.toByte(), 0x04.toByte(), 0x18.toByte(), 0x03.toByte(), 0x12.toByte(), 0x06.toByte(), 0x08.toByte(), 0x00.toByte(), 0x10.toByte(), 0x09.toByte(), 0x18.toByte(), 0x05.toByte(), 0x1A.toByte(), 0x06.toByte(), 0x08.toByte())
        val frame1 = byteArrayOf(0x17.toByte(), 0x02.toByte(), 0x10.toByte(), 0x00.toByte(), 0x18.toByte(), 0x07.toByte(), 0x32.toByte(), 0x08.toByte(), 0x41.toByte(), 0x31.toByte(), 0x34.toByte(), 0x37.toByte(), 0x38.toByte(), 0x43.toByte(), 0x32.toByte(), 0x43.toByte(), 0x3A.toByte(), 0x0E.toByte(), 0x50.toByte(), 0x6F.toByte())
        val frame2 = byteArrayOf(0x27.toByte(), 0x50.toByte(), 0x6F.toByte(), 0x6C.toByte(), 0x61.toByte(), 0x72.toByte(), 0x20.toByte(), 0x49.toByte(), 0x4E.toByte(), 0x57.toByte(), 0x33.toByte(), 0x4E.toByte(), 0x5F.toByte(), 0x42.toByte(), 0x0B.toByte(), 0x30.toByte(), 0x30.toByte(), 0x37.toByte(), 0x38.toByte(), 0x35.toByte())
        val frame3 = byteArrayOf(0x37.toByte(), 0x30.toByte(), 0x30.toByte(), 0x37.toByte(), 0x38.toByte(), 0x35.toByte(), 0x36.toByte(), 0x4A.toByte(), 0x06.toByte(), 0x43.toByte(), 0x6F.toByte(), 0x70.toByte(), 0x70.toByte(), 0x65.toByte(), 0x72.toByte(), 0x52.toByte(), 0x06.toByte(), 0x55.toByte(), 0x6E.toByte(), 0x69.toByte())
        val frame4 = byteArrayOf(0x47.toByte(), 0x55.toByte(), 0x6E.toByte(), 0x69.toByte(), 0x5A.toByte(), 0x10.toByte(), 0x41.toByte(), 0x30.toByte(), 0x39.toByte(), 0x45.toByte(), 0x31.toByte(), 0x41.toByte(), 0x46.toByte(), 0x46.toByte(), 0x46.toByte(), 0x45.toByte(), 0x41.toByte(), 0x31.toByte(), 0x34.toByte(), 0x37.toByte())
        val frame5 = byteArrayOf(0x57.toByte(), 0x41.toByte(), 0x30.toByte(), 0x62.toByte(), 0x14.toByte(), 0xC1.toByte(), 0xA8.toByte(), 0x78.toByte(), 0xD5.toByte(), 0x02.toByte(), 0x4B.toByte(), 0x46.toByte(), 0x1D.toByte(), 0xC6.toByte(), 0x6D.toByte(), 0x38.toByte(), 0xED.toByte(), 0x0E.toByte(), 0x53.toByte(), 0xB5.toByte())
        val frame6 = byteArrayOf(0x67.toByte(), 0xC1.toByte(), 0xA8.toByte(), 0x78.toByte(), 0xD5.toByte(), 0x02.toByte(), 0x6A.toByte(), 0x06.toByte(), 0x08.toByte(), 0x03.toByte(), 0x10.toByte(), 0x0B.toByte(), 0x18.toByte(), 0x00.toByte(), 0x72.toByte(), 0x10.toByte(), 0x0A.toByte(), 0x06.toByte(), 0x42.toByte(), 0x6C.toByte())
        val frame7 = byteArrayOf(0x77.toByte(), 0x42.toByte(), 0x6C.toByte(), 0x65.toByte(), 0x41.toByte(), 0x1A.toByte(), 0x06.toByte(), 0x08.toByte(), 0x09.toByte(), 0x10.toByte(), 0x00.toByte(), 0x18.toByte(), 0x00.toByte(), 0x72.toByte(), 0x17.toByte(), 0x0A.toByte(), 0x0D.toByte(), 0x42.toByte(), 0x6C.toByte(), 0x65.toByte())
        val frame8 = byteArrayOf(0x87.toByte(), 0x42.toByte(), 0x6C.toByte(), 0x65.toByte(), 0x42.toByte(), 0x6F.toByte(), 0x6F.toByte(), 0x74.toByte(), 0x6C.toByte(), 0x6F.toByte(), 0x61.toByte(), 0x1A.toByte(), 0x06.toByte(), 0x08.toByte(), 0x04.toByte(), 0x10.toByte(), 0x01.toByte(), 0x18.toByte(), 0x00.toByte(), 0x72.toByte())
        val frame9 = byteArrayOf(0x97.toByte(), 0x22.toByte(), 0x0A.toByte(), 0x03.toByte(), 0x47.toByte(), 0x50.toByte(), 0x53.toByte(), 0x1A.toByte(), 0x1B.toByte(), 0x08.toByte(), 0x01.toByte(), 0x10.toByte(), 0x00.toByte(), 0x18.toByte(), 0x00.toByte(), 0x22.toByte(), 0x13.toByte(), 0x61.toByte(), 0x32.toByte(), 0x30.toByte())
        val frame10 = byteArrayOf(0xA7.toByte(), 0x61.toByte(), 0x32.toByte(), 0x30.toByte(), 0x30.toByte(), 0x32.toByte(), 0x30.toByte(), 0x5F.toByte(), 0x66.toByte(), 0x34.toByte(), 0x64.toByte(), 0x33.toByte(), 0x38.toByte(), 0x36.toByte(), 0x38.toByte(), 0x5F.toByte(), 0x31.toByte(), 0x78.toByte(), 0x01.toByte(), 0x82.toByte())
        val frame11 = byteArrayOf(0xB7.toByte(), 0x82.toByte(), 0x08.toByte(), 0x0A.toByte(), 0x06.toByte(), 0x08.toByte(), 0x03.toByte(), 0x10.toByte(), 0x00.toByte(), 0x18.toByte(), 0x02.toByte(), 0x8A.toByte(), 0x01.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x09.toByte(), 0x5A.toByte(), 0x48.toByte(), 0x5F.toByte(), 0x4A.toByte())
        val frame12 = byteArrayOf(0xC3.toByte(), 0x5A.toByte(), 0x48.toByte(), 0x5F.toByte(), 0x4A.toByte(), 0x41.toByte(), 0x10.toByte(), 0x09.toByte())
        val output = ByteArrayOutputStream()
        val timeoutSeconds = 90L

        // Act
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame0, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame1, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame2, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame3, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame4, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame5, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame6, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame7, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame8, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame9, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame10, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame11, 0, true)
        blePsFtpClient.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame12, 0, true)
        blePsFtpClient.readResponse(output, timeoutSeconds)

        // Assert
        val expectedArray = (frame0.drop(1) +
                frame1.drop(1) +
                frame2.drop(1) +
                frame3.drop(1) +
                frame4.drop(1) +
                frame5.drop(1) +
                frame6.drop(1) +
                frame7.drop(1) +
                frame8.drop(1) +
                frame9.drop(1) +
                frame10.drop(1) +
                frame11.drop(1) +
                frame12.drop(1)).toByteArray()

        Assert.assertArrayEquals(expectedArray, output.toByteArray())
    }

    @Test
    fun `write timeout selection delegates extended sync package policy to shared runtime`() {
        assertEquals(900L, blePsFtpClient.getWriteTimeoutForFilePath("/SYNCPART.TGZ"))
        assertEquals(900L, blePsFtpClient.getWriteTimeoutForFilePath("/SYNCPART.TGZ/part0"))
        assertEquals(90L, blePsFtpClient.getWriteTimeoutForFilePath("/U/0/S/UDEVSET.BPB"))
    }

    @Test
    fun `psftp response golden vectors reassemble request responses`() = runTest {
        val vector = loadPsFtpResponseVector("request-response-reassembly")
        val requestHeader = vector.getAsJsonObject("input").get("requestHeaderHex").asString.hexToByteArray()
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }

        every { mockGattTxInterface.gattClientRequestStopScanning() } returns Unit
        every { mockGattTxInterface.gattClientResumeScanning() } returns Unit

        Assert.assertEquals(REQUEST_RESPONSE_REASSEMBLY_CASE_IDS, cases.map { it.get("id").asString })

        cases.forEach { testCase ->
            val client = BlePsFtpClient(mockGattTxInterface)
            client.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)
            every { mockGattTxInterface.transmitMessages(RFC77_PFTP_SERVICE, RFC77_PFTP_MTU_CHARACTERISTIC, any(), false) } answers {
                thirdArg<List<ByteArray>>().forEach { _ ->
                    client.processServiceDataWritten(RFC77_PFTP_MTU_CHARACTERISTIC, 0)
                }
                testCase.getAsJsonArray("responseFramesHex").forEach { frame ->
                    client.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame.asString.hexToByteArray(), 0, true)
                }
            }

            val response = client.request(requestHeader)

            Assert.assertArrayEquals(
                testCase.get("id").asString,
                testCase.getAsJsonObject("expected").get("payloadHex").asString.hexToByteArray(),
                response.toByteArray()
            )
            client.reset()
        }
    }

    @Test
    fun `psftp response golden vectors characterize request error policy`() = runTest {
        val vector = loadPsFtpResponseVector("request-response-error-policy")
        val requestHeader = vector.getAsJsonObject("input").get("requestHeaderHex").asString.hexToByteArray()
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }

        every { mockGattTxInterface.gattClientRequestStopScanning() } returns Unit
        every { mockGattTxInterface.gattClientResumeScanning() } returns Unit

        Assert.assertEquals(REQUEST_RESPONSE_ERROR_CASE_IDS, cases.map { it.get("id").asString })

        cases.forEach { testCase ->
            val client = BlePsFtpClient(mockGattTxInterface)
            client.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)
            every { mockGattTxInterface.transmitMessages(RFC77_PFTP_SERVICE, RFC77_PFTP_MTU_CHARACTERISTIC, any(), false) } answers {
                thirdArg<List<ByteArray>>().forEach { _ ->
                    client.processServiceDataWritten(RFC77_PFTP_MTU_CHARACTERISTIC, 0)
                }
                testCase.getAsJsonArray("responseFramesHex").forEach { frame ->
                    client.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame.asString.hexToByteArray(), 0, true)
                }
            }

            val expected = testCase.getAsJsonObject("expected").getAsJsonObject("android")
            when (expected.get("outcome").asString) {
                "throwsException" -> {
                    val thrown = Assert.assertThrows(Exception::class.java) {
                        runBlocking {
                            client.request(requestHeader)
                        }
                    }
                    expected.getAsJsonArray("messageContains").forEach { expectedText ->
                        Assert.assertTrue(testCase.get("id").asString, thrown.message?.contains(expectedText.asString) == true)
                    }
                }
                else -> Assert.fail("Unsupported Android PSFTP response expectation ${expected.get("outcome").asString}")
            }
            client.reset()
        }
    }

    @Test
    fun `psftp response golden vectors characterize write success progress`() = runTest {
        val vector = loadPsFtpResponseVector("write-success-progress")
        val input = vector.getAsJsonObject("input")
        val header = buildPftpOperationHeader(input.get("command").asString, input.get("path").asString)
        val payload = input.get("payloadHex").asString.hexToByteArray()
        val expectedProgress = vector.getAsJsonObject("expected").getAsJsonObject("android").getAsJsonArray("progress").map { it.asLong }
        val client = BlePsFtpClient(mockGattTxInterface)
        client.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)

        every { mockGattTxInterface.gattClientRequestStopScanning() } returns Unit
        every { mockGattTxInterface.gattClientResumeScanning() } returns Unit
        every { mockGattTxInterface.transmitMessage(RFC77_PFTP_SERVICE, RFC77_PFTP_MTU_CHARACTERISTIC, any(), any()) } answers {
            val packet = thirdArg<ByteArray>()
            val status = (packet[0].toInt() shr 1) and 0x03
            if (status == BlePsFtpUtils.RFC76_STATUS_LAST) {
                input.getAsJsonArray("responseFramesHex").forEach { frame ->
                    client.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, frame.asString.hexToByteArray(), 0, true)
                }
            }
        }

        val progress = client.write(header, ByteArrayInputStream(payload)).toList()

        assertEquals(vector.get("id").asString, expectedProgress, progress)
        client.reset()
    }

    @Test
    fun `psftp response golden vectors characterize write interruption error policy`() = runTest {
        val vector = loadPsFtpResponseVector("write-interruption-error-policy")
        val input = vector.getAsJsonObject("input")
        val header = buildPftpOperationHeader(input.get("command").asString, input.get("path").asString)
        val payload = input.get("payloadHex").asString.hexToByteArray()
        val interruptFrame = input.get("interruptFrameHex").asString.hexToByteArray()
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("android")
        val client = BlePsFtpClient(mockGattTxInterface)
        var didInjectInterrupt = false
        var didObserveMoreFrame = false
        client.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)
        client.setMtuSize(8)
        client.setPacketsCount(Int.MAX_VALUE)

        every { mockGattTxInterface.gattClientRequestStopScanning() } returns Unit
        every { mockGattTxInterface.gattClientResumeScanning() } returns Unit
        every { mockGattTxInterface.transmitMessage(RFC77_PFTP_SERVICE, RFC77_PFTP_MTU_CHARACTERISTIC, any(), any()) } answers {
            val packet = thirdArg<ByteArray>()
            val withResponse = invocation.args[3] as Boolean
            if (withResponse) {
                client.processServiceDataWrittenWithResponse(RFC77_PFTP_MTU_CHARACTERISTIC, 0)
            } else {
                client.processServiceDataWritten(RFC77_PFTP_MTU_CHARACTERISTIC, 0)
            }
            val status = (packet[0].toInt() shr 1) and 0x03
            didObserveMoreFrame = didObserveMoreFrame || status == BlePsFtpUtils.RFC76_STATUS_MORE
            if (!didInjectInterrupt) {
                didInjectInterrupt = true
                client.processServiceData(RFC77_PFTP_MTU_CHARACTERISTIC, interruptFrame, 0, true)
            }
        }

        when (expected.get("outcome").asString) {
            "throwsPftpResponseError" -> {
                val thrown = Assert.assertThrows(BlePsFtpUtils.PftpResponseError::class.java) {
                    runBlocking {
                        withTimeout(1_000) {
                            client.write(header, ByteArrayInputStream(payload)).toList()
                        }
                    }
                }
                assertEquals(vector.get("id").asString, expected.get("errorCode").asInt, thrown.error)
                Assert.assertTrue(vector.get("id").asString, didObserveMoreFrame)
                expected.getAsJsonArray("messageContains").forEach { expectedText ->
                    Assert.assertTrue(vector.get("id").asString, thrown.message?.contains(expectedText.asString) == true)
                }
            }
            else -> Assert.fail("Unsupported Android PSFTP write interruption expectation ${expected.get("outcome").asString}")
        }
        client.reset()
    }

    @Test
    fun `psftp response golden vectors characterize write transport failure policy`() = runTest {
        val vector = loadPsFtpResponseVector("write-transport-failure-policy")
        val input = vector.getAsJsonObject("input")
        val header = buildPftpOperationHeader(input.get("command").asString, input.get("path").asString)
        val payload = input.get("payloadHex").asString.hexToByteArray()
        val failure = input.getAsJsonObject("failure")
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("android")
        val client = BlePsFtpClient(mockGattTxInterface)
        client.descriptorWritten(RFC77_PFTP_MTU_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)

        every { mockGattTxInterface.gattClientRequestStopScanning() } returns Unit
        every { mockGattTxInterface.gattClientResumeScanning() } returns Unit
        every { mockGattTxInterface.transmitMessage(RFC77_PFTP_SERVICE, RFC77_PFTP_MTU_CHARACTERISTIC, any(), any()) } answers {
            throw IllegalStateException(failure.get("message").asString)
        }

        when (expected.get("outcome").asString) {
            "throwsTransportException" -> {
                try {
                    withTimeout(1_000) {
                        client.write(header, ByteArrayInputStream(payload)).toList()
                    }
                    Assert.fail("${vector.get("id").asString} should throw a transport exception")
                } catch (thrown: IllegalStateException) {
                    expected.getAsJsonArray("messageContains").forEach { expectedText ->
                        Assert.assertTrue(vector.get("id").asString, thrown.message?.contains(expectedText.asString) == true)
                    }
                }
                verify(exactly = 1) { mockGattTxInterface.gattClientResumeScanning() }
            }
            else -> Assert.fail("Unsupported Android PSFTP write transport failure expectation ${expected.get("outcome").asString}")
        }
        client.reset()
    }

    @Test
    fun `psftp notification golden vectors reassemble complete notifications`() = runBlocking {
        val vector = loadPsFtpNotificationVector("notification-reassembly")
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }

        Assert.assertEquals(NOTIFICATION_REASSEMBLY_CASE_IDS, cases.map { it.get("id").asString })

        cases.forEach { testCase ->
            val client = BlePsFtpClient(mockGattTxInterface)
            client.descriptorWritten(RFC77_PFTP_D2H_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)

            try {
                val notificationDeferred = async(Dispatchers.IO) {
                    withTimeout(1_000) {
                        client.waitForNotification().first()
                    }
                }
                testCase.getAsJsonArray("framesHex").forEach { frame ->
                    client.processServiceData(RFC77_PFTP_D2H_CHARACTERISTIC, frame.asString.hexToByteArray(), 0, true)
                }

                val notification = notificationDeferred.await()
                val expected = testCase.getAsJsonObject("expected")
                assertEquals(testCase.get("id").asString, expected.get("id").asInt, notification.id)
                Assert.assertArrayEquals(
                    testCase.get("id").asString,
                    expected.get("parametersHex").asString.hexToByteArray(),
                    notification.byteArrayOutputStream.toByteArray()
                )
            } finally {
                client.reset()
            }
        }
    }

    @Test
    fun `psftp notification golden vectors characterize error policy`() = runBlocking {
        val vector = loadPsFtpNotificationVector("notification-error-policy")
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }

        Assert.assertEquals(NOTIFICATION_ERROR_CASE_IDS, cases.map { it.get("id").asString })

        cases.forEach { testCase ->
            val client = BlePsFtpClient(mockGattTxInterface)
            client.descriptorWritten(RFC77_PFTP_D2H_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)

            try {
                val notificationDeferred = async(Dispatchers.IO) {
                    withTimeout(250) {
                        client.waitForNotification().first()
                    }
                }
                testCase.getAsJsonArray("packets").forEach { packet ->
                    val packetObject = packet.asJsonObject
                    client.processServiceData(
                        RFC77_PFTP_D2H_CHARACTERISTIC,
                        packetObject.get("frameHex").asString.hexToByteArray(),
                        packetObject.get("status").asInt,
                        true
                    )
                }

                val expected = testCase.getAsJsonObject("expected").getAsJsonObject("android")
                when (expected.get("outcome").asString) {
                    "noEmission" -> {
                        try {
                            notificationDeferred.await()
                            Assert.fail("${testCase.get("id").asString} should not emit a notification")
                        } catch (ex: TimeoutCancellationException) {
                            // Expected: current Android implementation ignores this error policy.
                        }
                    }
                    else -> Assert.fail("Unsupported Android PSFTP notification expectation ${expected.get("outcome").asString}")
                }
            } finally {
                client.reset()
            }
        }
    }

    @Test
    fun `psftp notification golden vectors characterize timeout policy`() = runBlocking {
        val vector = loadPsFtpNotificationVector("notification-timeout-policy")

        vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }.forEach { testCase ->
            val client = BlePsFtpClient(mockGattTxInterface)
            client.descriptorWritten(RFC77_PFTP_D2H_CHARACTERISTIC, true, BleGattBase.ATT_SUCCESS)

            try {
                val expected = testCase.getAsJsonObject("expected").getAsJsonObject("android")
                when (expected.get("outcome").asString) {
                    "noEmission" -> {
                        try {
                            withTimeout(testCase.get("observerTimeoutMs").asLong) {
                                client.waitForNotification().first()
                            }
                            Assert.fail("${testCase.get("id").asString} should not emit a notification")
                        } catch (ex: TimeoutCancellationException) {
                            // Expected: initial silence is consumer-timeout owned.
                        }
                    }
                    else -> Assert.fail("Unsupported Android PSFTP notification timeout expectation ${expected.get("outcome").asString}")
                }
            } finally {
                client.reset()
            }
        }
    }

    @Test
    fun `psftp notification golden vectors follow neutral KMP vector shape`() {
        loadPsFtpNotificationVectors().forEach { vector ->
            assertNeutralKmpVectorShape(vector)
            val id = vector.get("id").asString
            Assert.assertTrue(id, vector.getAsJsonObject("input").has("kind"))
            Assert.assertTrue(id, vector.getAsJsonObject("input").has("cases"))
        }
    }

    @Test
    fun `psftp response golden vectors follow neutral KMP vector shape`() {
        loadPsFtpVectors("psftp-response").forEach { vector ->
            if (vector.getAsJsonObject("input").get("kind").asString == "psFtpRuntimeReadiness") return@forEach
            assertNeutralKmpVectorShape(vector)
            val id = vector.get("id").asString
            val input = vector.getAsJsonObject("input")
            Assert.assertTrue(id, input.has("kind"))
            Assert.assertTrue(id, input.has("cases") || input.has("payloadHex"))
            Assert.assertTrue(id, input.has("requestHeaderHex") || input.has("path"))
        }
    }

    @Test
    fun `psftp timeout planning vectors require fake clock before shared runtime migration`() {
        assertFakeClockPlanningVector(
            vector = loadPsFtpNotificationVector("notification-continuation-timeout-policy"),
            androidExecution = "planned-fake-clock-or-injectable-timeout-required",
            iosExecution = "planned-fake-clock-or-injectable-timeout-required",
            commonExecution = "shared-common-test",
            expectedCaseIds = NOTIFICATION_CONTINUATION_TIMEOUT_CASE_IDS
        )
        assertFakeClockPlanningVector(
            vector = loadPsFtpResponseVector("write-ack-timeout-policy"),
            androidExecution = "planned-fake-clock-or-injectable-timeout-required",
            iosExecution = "planned-fake-clock-or-injectable-timeout-required",
            commonExecution = "shared-common-test"
        )
    }

    @Test
    fun `psftp runtime readiness manifest is pinned before shared runtime migration`() {
        val vector = loadPsFtpResponseVector("psftp-runtime-readiness")
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val runtimePrototype = expected.getAsJsonObject("commonRuntimePrototype")
        Assert.assertEquals("psftp-runtime-readiness", vector.get("id").asString)
        Assert.assertEquals("psFtpRuntimeReadiness", input.get("kind").asString)
        val policyPaths = input.getAsJsonArray("policyVectorPaths").map { path -> path.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { family -> family.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { family -> family.asString }
        Assert.assertEquals(requiredPsFtpRuntimePolicyPaths, policyPaths)
        Assert.assertEquals(requiredPsFtpRuntimeFamilies, requiredFamilies)
        Assert.assertEquals(requiredPsFtpRuntimeFamilies, coveredFamilies)
        Assert.assertEquals(PSFTP_RUNTIME_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        Assert.assertEquals("executable shared commonTest runtime planning guard", runtimePrototype.get("status").asString)
        Assert.assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", runtimePrototype.get("reason").asString)
        Assert.assertEquals(
            listOf("com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClientTest"),
            consumerTests.getAsJsonArray("android").map { test -> test.asString }
        )
        Assert.assertEquals(listOf("BlePsFtpClientTest"), consumerTests.getAsJsonArray("ios").map { test -> test.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.PsFtpRuntimePolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { test -> test.asString })
    }

    private fun loadPsFtpNotificationVector(id: String): JsonObject {
        return loadPsFtpNotificationVectors()
            .first { it.get("id").asString == id }
    }

    private fun loadPsFtpNotificationVectors(): List<JsonObject> {
        return loadPsFtpVectors("psftp-notifications")
    }

    private fun loadPsFtpResponseVector(id: String): JsonObject {
        return loadPsFtpVectors("psftp-response")
            .first { it.get("id").asString == id }
    }

    private fun loadPsFtpVectors(directoryName: String): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/$directoryName")
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

    private val requiredPsFtpRuntimePolicyPaths = listOf(
        "sdk/psftp-response/request-response-reassembly.json",
        "sdk/psftp-response/request-response-error-policy.json",
        "sdk/psftp-notifications/notification-reassembly.json",
        "sdk/psftp-notifications/notification-ordering.json",
        "sdk/psftp-notifications/notification-timeout-policy.json",
        "sdk/psftp-notifications/notification-error-policy.json",
        "sdk/psftp-notifications/notification-continuation-timeout-policy.json",
        "sdk/psftp-response/write-success-progress.json",
        "sdk/psftp-response/write-interruption-error-policy.json",
        "sdk/psftp-response/write-transport-failure-policy.json",
        "sdk/psftp-response/write-ack-timeout-policy.json"
    )

    private val requiredPsFtpRuntimeFamilies = listOf(
        "request-response-reassembly",
        "request-response-error-mapping",
        "notification-reassembly",
        "notification-ordering",
        "initial-silence-no-built-in-timeout",
        "consumer-timeout-observer-cleanup",
        "notification-rfc76-error-policy",
        "notification-transport-status-platform-split",
        "notification-continuation-timeout",
        "write-progress-platform-split",
        "write-interruption-response-error",
        "write-transport-failure",
        "write-ack-timeout",
        "fake-clock-timeout-gate",
        "platform-client-vector-reference-gate",
        "compile-verification-gate"
    )

    private companion object {
        val REQUEST_RESPONSE_REASSEMBLY_CASE_IDS = listOf("single-frame", "multi-frame")
        val REQUEST_RESPONSE_ERROR_CASE_IDS = listOf("known-error-no-such-file", "unknown-error-code")
        val NOTIFICATION_REASSEMBLY_CASE_IDS = listOf("single-frame", "multi-frame")
        val NOTIFICATION_ERROR_CASE_IDS = listOf("rfc76-error-first-frame", "transport-error-first-packet")
        val NOTIFICATION_CONTINUATION_TIMEOUT_CASE_IDS = listOf("missing-last-frame-after-more")
        const val PSFTP_RUNTIME_READINESS_COMMON_DECISION = "PSFTP runtime migration may proceed only after every policy vector listed in this readiness manifest is executable from shared commonTest, Android and iOS PSFTP client tests continue to reference the same vectors, request response reassembly, response-error mapping, notification reassembly and ordering, initial-silence policy, consumer timeout cleanup, notification error platform split, continuation timeout, write progress split, write interruption, transport failure, write acknowledgement timeout, fake-clock timeout gates, and the shared tests are compile-verified."
    }

    private fun assertNeutralKmpVectorShape(vector: JsonObject) {
        val id = vector.get("id").asString
        Assert.assertTrue(id, vector.has("area"))
        Assert.assertTrue(id, vector.has("case"))
        Assert.assertTrue(id, vector.has("source"))
        Assert.assertTrue(id, vector.has("input"))
        Assert.assertTrue(id, vector.has("expected"))
        Assert.assertTrue(id, vector.has("platforms"))
        val platforms = vector.getAsJsonObject("platforms")
        Assert.assertTrue(id, platforms.get("android").asBoolean)
        Assert.assertTrue(id, platforms.get("ios").asBoolean)
        Assert.assertTrue(id, platforms.get("common").asBoolean)
    }

    private fun assertFakeClockPlanningVector(
        vector: JsonObject,
        androidExecution: String,
        iosExecution: String,
        commonExecution: String,
        expectedCaseIds: List<String>? = null
    ) {
        val id = vector.get("id").asString
        assertNeutralKmpVectorShape(vector)
        expectedCaseIds?.let { caseIds ->
            Assert.assertEquals(id, caseIds, vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject.get("id").asString })
        }
        val execution = vector.getAsJsonObject("execution")
        assertEquals(id, androidExecution, execution.get("android").asString)
        assertEquals(id, iosExecution, execution.get("ios").asString)
        assertEquals(id, commonExecution, execution.get("common").asString)
        val commonDecision = vector.getAsJsonObject("platformExpectations").getAsJsonObject("commonDecision")
        Assert.assertTrue(id, commonDecision.get("errorPolicy").asString.isNotBlank())
    }

    private fun buildPftpOperationHeader(command: String, path: String): ByteArray {
        val commandValue = when (command) {
            "GET" -> CommunicationsPftpRequest.PbPFtpOperation.Command.GET
            "PUT" -> CommunicationsPftpRequest.PbPFtpOperation.Command.PUT
            "MERGE" -> CommunicationsPftpRequest.PbPFtpOperation.Command.MERGE
            "REMOVE" -> CommunicationsPftpRequest.PbPFtpOperation.Command.REMOVE
            else -> error("Unsupported PFTP command $command")
        }
        return CommunicationsPftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(commandValue)
            .setPath(path)
            .build()
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

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must contain an even number of characters" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
