package com.polar.sdk.api.model.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.Companion.HR_SERVICE
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.errors.PolarDeviceDisconnected
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.FileReader
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class PolarServiceClientUtilsTest {

    @Test
    fun testSessionHrClientReady() {
        // Arrange
        val deviceId = "E123456F"

        val client = mockk<BleHrClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS

        // Act
        val testHrSession = PolarServiceClientUtils.sessionHrClientReady(deviceId, listener)

        // Assert
        Assert.assertEquals(testHrSession.sessionState, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
    }

    @Test
    fun testSessionHrClientThrows() {
        // Arrange
        val deviceId = "E123456F"

        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED // No open connection, throws

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionHrClientReady(deviceId, listener)
            fail("testSessionHrClientReadyThrows, sessionHrClientReady did not throw when no connection to device.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceDisconnected().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionPmdClientReady() {
        // Arrange
        val deviceId = "E123456F"

        val client = mockk<BlePMDClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS

        // Act
        val testPMDSession = PolarServiceClientUtils.sessionPmdClientReady(deviceId, listener)

        // Assert
        Assert.assertEquals(testPMDSession.sessionState, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
    }

    @Test
    fun testSessionPmdClientThrows() {
        // Arrange
        val deviceId = "E123456F"

        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionPmdClientReady(deviceId, listener)
            fail("Test sessionPmdClientThrows failed: sessionPmdClientReady did not throw when no connection to device.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceDisconnected().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionFtpClientReady() {
        // Arrange
        val deviceId = "E123456F"

        val client = mockk<BlePsFtpClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS

        // Act
        val testFtpSession = PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)

        // Assert
        Assert.assertEquals(testFtpSession.sessionState, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
    }

    @Test
    fun testSessionFtpClientThrows() {
        // Arrange
        val deviceId = "E123456F"

        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, listener)
            fail("Test sessionPsFtpClientReady failed: sessionPsFtpClientReady did not throw when no connection to device.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceDisconnected().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionPfcClientReady() {
        // Arrange
        val deviceId = "E123456F"

        val client = mockk<BlePfcClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS

        // Act
        val testPfcSession = PolarServiceClientUtils.sessionPsPfcClientReady(deviceId, listener)

        // Assert
        Assert.assertEquals(testPfcSession.sessionState, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
    }

    @Test
    fun testSessionPfcClientThrows() {
        // Arrange
        val deviceId = "E123456F"

        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionPsPfcClientReady(deviceId, listener)
            fail("Test sessionPsPfcClientReady failed: sessionPsPfcClientReady did not throw when no connection to device.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceDisconnected().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionServiceReady() {
        // Arrange
        val deviceId = "E123456F"

        val client = mockk<BlePfcClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS

        // Act
        val testSessionService = PolarServiceClientUtils.sessionServiceReady(deviceId, HR_SERVICE, listener)

        // Assert
        Assert.assertEquals(testSessionService.sessionState, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
    }

    @Test
    fun testSessionService_Throws_When_DeviceNotFound() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()

        every { listener.deviceSessions() } returns null

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionServiceReady(deviceId, HR_SERVICE, listener)
            fail("Test testSessionServiceThrowsWhenDeviceNotFound failed: sessionServiceReady did not throw when no device found.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceNotFound().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionService_Throws_When_DeviceDisconnected() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionServiceReady(deviceId, HR_SERVICE, listener)
            fail("Test testSessionServiceThrowsWhenDeviceDisconnected failed: sessionServiceReady did not throw when no connection to device.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceDisconnected().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionService_Throws_When_NoDeviceBleClient() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns null

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionServiceReady(deviceId, UUID.randomUUID(), listener)
            fail("Test testSessionServiceThrowsWhenNoDeviceBleClient failed: sessionServiceReady did not throw when device does not support required Ble client.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarServiceNotAvailable().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testSessionService_Throws_When_NoBleService() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        val client = mockk<BlePsFtpClient>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns false

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionServiceReady(deviceId, UUID.randomUUID(), listener)
            fail("Test testSessionServiceThrowsWhenNoClientService failed: sessionServiceReady did not throw when device Ble client does not have required service.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarServiceNotAvailable().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testFetchSession() {
        // Arrange
        val deviceId = "E123456F"

        val client = mockk<BlePfcClient>()
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(any()) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0) // 0 is BleGattBase.ATT_SUCCESS

        // Act
        val testSession = PolarServiceClientUtils.fetchSession(deviceId, listener)

        // Assert
        if (testSession != null) {
            Assert.assertEquals(testSession.sessionState, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
        } else {
            fail("testFetchSession: testSession.sessionState was null")
        }
    }

    @Test
    fun identifierRoutingGoldenVectors_matchAndroidBehavior() {
        val vectors = loadIdentifierRoutingVectors()
        Assert.assertTrue("Expected identifier routing golden vectors", vectors.isNotEmpty())

        vectors
            .filter { it.getAsJsonObject("platforms").get("android").asBoolean }
            .forEach { vector ->
                val caseId = vector.get("id").asString
                val input = vector.getAsJsonObject("input")
                val expected = vector.getAsJsonObject("expected")
                val listener = mockk<BleDeviceListener>()
                val session = mockk<BleDeviceSession>()
                val advContent = mockk<BleAdvertisementContent>()
                every { listener.deviceSessions() } returns setOf(session)
                every { session.address } returns input.get("matchingAddress").asString
                every { session.advertisementContent } returns advContent
                every { advContent.polarDeviceId } returns input.get("matchingDeviceId").asString

                if (expected.has("error")) {
                    Assert.assertThrows(caseId, PolarInvalidArgument::class.java) {
                        PolarServiceClientUtils.fetchSession(input.get("identifier").asString, listener)
                    }
                    return@forEach
                }

                val result = PolarServiceClientUtils.fetchSession(input.get("identifier").asString, listener)
                Assert.assertTrue(caseId, result === session)
            }
    }

    @Test
    fun `identifier routing golden vectors follow neutral KMP vector shape`() {
        loadIdentifierRoutingVectors().forEach { vector ->
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
    fun testFetchSessionThrows() {
        // Arrange
        val deviceId = "Not-A-DeviceId"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()

        every { session.advertisementContent.polarDeviceId } returns deviceId


        // Act&Assert
        try {
            PolarServiceClientUtils.sessionServiceReady(deviceId, UUID.randomUUID(), listener)
            fail("Test testFetchSessionThrows failed: fetchSession did not throw when device does not match deviceID filters.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarInvalidArgument().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun testFetchSessionThrows_when_device_disconnected() {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val sessions = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() } returns session
        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "Polar360"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_CLOSED

        every { session.advertisementContent.polarDeviceId } returns deviceId

        // Act&Assert
        try {
            PolarServiceClientUtils.sessionServiceReady(deviceId, UUID.randomUUID(), listener)
            fail("Test testFetchSessionThrows failed: fetchSession did not throw when device does not match deviceID filters.")
        } catch (e: Exception) {
            Assert.assertEquals(true,  PolarDeviceDisconnected().toString().contentEquals(e.toString()))
        }
    }

    @Test
    fun getRSSIValue_whenMatchingDeviceFound_returnsSessionRssi() {
        // Arrange
        val deviceId = "E123456F"
        val expectedRssi = -55

        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns setOf(session)
        every { session.advertisementContent } returns advContent
        every { advContent.polarDeviceId } returns deviceId
        every { session.rssi } returns expectedRssi

        // Act
        val result = PolarServiceClientUtils.getRSSIValue(deviceId, listener)

        // Assert
        Assert.assertEquals(expectedRssi, result)
    }

    private fun loadIdentifierRoutingVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/device-id")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" && file.name.startsWith("identifier-") }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
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

    @Test
    fun getRSSIValue_whenNoMatchingDevice_returnsMinusOne() {
        // Arrange
        val listener = mockk<BleDeviceListener>()
        val session = mockk<BleDeviceSession>()
        val advContent = mockk<BleAdvertisementContent>()

        every { listener.deviceSessions() } returns setOf(session)
        every { session.advertisementContent } returns advContent
        every { advContent.polarDeviceId } returns "AABBCCDD"

        // Act
        val result = PolarServiceClientUtils.getRSSIValue("12345678", listener)

        // Assert
        Assert.assertEquals(-1, result)
    }

    @Test
    fun getRSSIValue_whenListenerIsNull_returnsMinusOne() {
        // Act
        val result = PolarServiceClientUtils.getRSSIValue("E123456F", null)

        // Assert
        Assert.assertEquals(-1, result)
    }

    @Test
    fun getRSSIValue_whenDeviceSessionsReturnsNull_returnsMinusOne() {
        // Arrange
        val listener = mockk<BleDeviceListener>()
        every { listener.deviceSessions() } returns null

        // Act
        val result = PolarServiceClientUtils.getRSSIValue("E123456F", listener)

        // Assert
        Assert.assertEquals(-1, result)
    }
}
