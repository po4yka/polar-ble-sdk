// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.PolarD2HNotificationData
import com.polar.sdk.api.PolarDeviceToHostNotification
import com.polar.sdk.impl.utils.observeDeviceToHostNotifications
import com.polar.shared.runtime.PolarD2hRuntimePlanning
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import protocol.PftpNotification.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileReader

class PolarD2HNotificationsUtilsTest {

    private lateinit var mockClient: BlePsFtpClient

    @Before
    fun setUp() {
        mockClient = mockk(relaxed = true)
    }

    @Test
    fun `test receives sync required notification`() = runTest {
        // Arrange
        val syncRequiredNotificationId = PbPFtpDevToHostNotification.SYNC_REQUIRED.number
        val syncRequiredNotificationParameter = PbPFtpSyncRequiredParams.newBuilder().apply {
            addSyncTriggers(
                PbPFtpSyncTrigger.newBuilder()
                    .setSource(PbPFtpSyncTriggerSource.TIMED)
                    .build()
            )
        }.build()
        val syncRequiredNotificationParamsData = syncRequiredNotificationParameter.toByteArray()
        val keepAliveNotificationId = PbPFtpDevToHostNotification.KEEP_BACKGROUND_ALIVE.number

        every { mockClient.waitForNotification() } returns flowOf(
            createMockNotification(syncRequiredNotificationId, syncRequiredNotificationParamsData),
            createMockNotification(keepAliveNotificationId, ByteArray(0))
        )

        // Act
        val results = mutableListOf<PolarD2HNotificationData>()
        val job = launch { mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) } }
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertNotNull(results)
        assertEquals(2, results.size)

        // Check first notification (SYNC_REQUIRED)
        assertEquals(PolarDeviceToHostNotification.SYNC_REQUIRED, results[0].notificationType)
        assertArrayEquals(syncRequiredNotificationParamsData, results[0].parameters)
        assertNotNull(results[0].parsedParameters)
        assertTrue(results[0].parsedParameters is PbPFtpSyncRequiredParams)
        val parsedParams = results[0].parsedParameters as PbPFtpSyncRequiredParams
        assertEquals(syncRequiredNotificationParameter, parsedParams)

        // Check second notification (KEEP_BACKGROUND_ALIVE)
        assertEquals(PolarDeviceToHostNotification.KEEP_BACKGROUND_ALIVE, results[1].notificationType)
        assertEquals(0, results[1].parameters.size)
    }

    @Test
    fun `test receives filesystem modified notification`() = runTest {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.FILESYSTEM_MODIFIED.number

        val fileSystemModifiedParams = PbPFtpFilesystemModifiedParams.newBuilder()
            .setAction(Action.CREATED)
            .setPath("/U/0/")
            .build()
        val serializedData = fileSystemModifiedParams.toByteArray()

        every { mockClient.waitForNotification() } returns flowOf(createMockNotification(notificationId, serializedData))

        // Act
        val results = mutableListOf<PolarD2HNotificationData>()
        val job = launch { mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) } }
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertEquals(1, results.size)
        val result = results[0]
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.FILESYSTEM_MODIFIED, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPFtpFilesystemModifiedParams)
        val parsedParams = result.parsedParameters as PbPFtpFilesystemModifiedParams
        assertEquals(fileSystemModifiedParams, parsedParams)
    }

    @Test
    fun `test receives inactivity alert notification`() = runTest {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.INACTIVITY_ALERT.number

        val inactivityAlertParams = PbPFtpInactivityAlert.newBuilder().setCountdown(5).build()
        val serializedData = inactivityAlertParams.toByteArray()

        every { mockClient.waitForNotification() } returns flowOf(createMockNotification(notificationId, serializedData))

        // Act
        val results = mutableListOf<PolarD2HNotificationData>()
        val job = launch { mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) } }
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertEquals(1, results.size)
        val result = results[0]
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.INACTIVITY_ALERT, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPFtpInactivityAlert)
        val parsedParams = result.parsedParameters as PbPFtpInactivityAlert
        assertEquals(5, parsedParams.countdown)
    }

    @Test
    fun `test receives training session status notification`() = runTest {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.TRAINING_SESSION_STATUS.number

        val trainingSessionStatus = PbPFtpTrainingSessionStatus.newBuilder().setInprogress(true).build()
        val serializedData = trainingSessionStatus.toByteArray()

        every { mockClient.waitForNotification() } returns flowOf(createMockNotification(notificationId, serializedData))

        // Act
        val results = mutableListOf<PolarD2HNotificationData>()
        val job = launch { mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) } }
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertEquals(1, results.size)
        val result = results[0]
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.TRAINING_SESSION_STATUS, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPFtpTrainingSessionStatus)
        val parsedParams = result.parsedParameters as PbPFtpTrainingSessionStatus
        assertTrue(parsedParams.inprogress)
    }

    @Test
    fun `test receives autosync status notification`() = runTest {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.AUTOSYNC_STATUS.number

        val autoSyncStatus = PbPFtpAutoSyncStatusParams.newBuilder()
            .setSucceeded(true)
            .setDescription("Sync completed successfully")
            .build()
        val serializedData = autoSyncStatus.toByteArray()

        every { mockClient.waitForNotification() } returns flowOf(createMockNotification(notificationId, serializedData))

        // Act
        val results = mutableListOf<PolarD2HNotificationData>()
        val job = launch { mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) } }
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertEquals(1, results.size)
        val result = results[0]
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.AUTOSYNC_STATUS, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPFtpAutoSyncStatusParams)
        val parsedParams = result.parsedParameters as PbPFtpAutoSyncStatusParams
        assertTrue(parsedParams.succeeded)
        assertEquals("Sync completed successfully", parsedParams.description)
    }

    @Test
    fun `test receives notification without parameters`() = runTest {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.STOP_GPS_MEASUREMENT.number

        every { mockClient.waitForNotification() } returns flowOf(createMockNotification(notificationId, ByteArray(0)))

        // Act
        val results = mutableListOf<PolarD2HNotificationData>()
        val job = launch { mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) } }
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertEquals(1, results.size)
        val result = results[0]
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.STOP_GPS_MEASUREMENT, result.notificationType)
        assertEquals(0, result.parameters.size)
        assertNull(result.parsedParameters)
    }

    @Test
    fun `public notification type lookup delegates known ids to shared model and preserves unknown null policy`() {
        assertEquals(PolarDeviceToHostNotification.FILESYSTEM_MODIFIED, PolarDeviceToHostNotification.fromValue(0))
        assertEquals(PolarDeviceToHostNotification.SYNC_REQUIRED, PolarDeviceToHostNotification.fromValue(PbPFtpDevToHostNotification.SYNC_REQUIRED.number))
        assertEquals(PolarDeviceToHostNotification.STOP_GPS_MEASUREMENT, PolarDeviceToHostNotification.fromValue(PbPFtpDevToHostNotification.STOP_GPS_MEASUREMENT.number))
        assertEquals(PolarDeviceToHostNotification.EXERCISE_STATUS, PolarDeviceToHostNotification.fromValue(19))
        assertNull(PolarDeviceToHostNotification.fromValue(6))
        assertNull(PolarDeviceToHostNotification.fromValue(999))
    }

    @Test
    fun `test filters unknown notification types`() = runTest {
        assertD2HStreamRuntimePolicyVectorContains("unknown-notification-between-known-values-is-filtered")
        // Arrange
        val unknownNotificationId = 999
        val validNotificationId = PbPFtpDevToHostNotification.IDLING.number

        every { mockClient.waitForNotification() } returns flowOf(
            createMockNotification(unknownNotificationId, ByteArray(0)),
            createMockNotification(validNotificationId, ByteArray(0))
        )

        // Act
        val results = mutableListOf<PolarD2HNotificationData>()
        val job = launch { mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) } }
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertEquals(1, results.size)
        assertEquals(PolarDeviceToHostNotification.IDLING, results[0].notificationType)
    }

    @Test
    fun `test handles invalid protobuf data gracefully`() = runTest {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.SYNC_REQUIRED.number
        val invalidData = "invalid protobuf data".toByteArray()

        every { mockClient.waitForNotification() } returns flowOf(createMockNotification(notificationId, invalidData))

        // Act
        val results = mutableListOf<PolarD2HNotificationData>()
        val job = launch { mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) } }
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertEquals(1, results.size)
        val result = results[0]
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.SYNC_REQUIRED, result.notificationType)
        assertArrayEquals(invalidData, result.parameters)
        assertNull(result.parsedParameters)
    }

    @Test
    fun `test receives media control request notification`() = runTest {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.MEDIA_CONTROL_REQUEST_DH.number

        val mediaControlRequest = PbPftpDHMediaControlRequest.newBuilder()
            .setRequest(MediaControlRequest.GET_MEDIA_DATA)
            .build()
        val serializedData = mediaControlRequest.toByteArray()

        every { mockClient.waitForNotification() } returns flowOf(createMockNotification(notificationId, serializedData))

        // Act
        val results = mutableListOf<PolarD2HNotificationData>()
        val job = launch { mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) } }
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertEquals(1, results.size)
        val result = results[0]
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.MEDIA_CONTROL_REQUEST_DH, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPftpDHMediaControlRequest)
        val parsedParams = result.parsedParameters as PbPftpDHMediaControlRequest
        assertEquals(MediaControlRequest.GET_MEDIA_DATA, parsedParams.request)
    }

    @Test
    fun `test receives media control command notification`() = runTest {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.MEDIA_CONTROL_COMMAND_DH.number

        val mediaControlCommand = PbPftpDHMediaControlCommand.newBuilder()
            .setCommand(MediaControlCommand.PLAY)
            .build()
        val serializedData = mediaControlCommand.toByteArray()

        every { mockClient.waitForNotification() } returns flowOf(createMockNotification(notificationId, serializedData))

        // Act
        val results = mutableListOf<PolarD2HNotificationData>()
        val job = launch { mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) } }
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertEquals(1, results.size)
        val result = results[0]
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.MEDIA_CONTROL_COMMAND_DH, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPftpDHMediaControlCommand)
        val parsedParams = result.parsedParameters as PbPftpDHMediaControlCommand
        assertEquals(MediaControlCommand.PLAY, parsedParams.command)
    }

    @Test
    fun `test receives start GPS measurement notification`() = runTest {
        // Arrange
        val notificationId = PbPFtpDevToHostNotification.START_GPS_MEASUREMENT.number

        val startGpsMeasurement = PbPftpStartGPSMeasurement.newBuilder()
            .setMinimumInterval(1000)
            .setAccuracy(2)
            .setLatitude(60.1695)
            .setLongitude(24.9354)
            .build()
        val serializedData = startGpsMeasurement.toByteArray()

        every { mockClient.waitForNotification() } returns flowOf(createMockNotification(notificationId, serializedData))

        // Act
        val results = mutableListOf<PolarD2HNotificationData>()
        val job = launch { mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) } }
        testScheduler.advanceUntilIdle()
        job.join()

        // Assert
        assertEquals(1, results.size)
        val result = results[0]
        assertNotNull(result)
        assertEquals(PolarDeviceToHostNotification.START_GPS_MEASUREMENT, result.notificationType)
        assertArrayEquals(serializedData, result.parameters)
        assertNotNull(result.parsedParameters)
        assertTrue(result.parsedParameters is PbPftpStartGPSMeasurement)
        val parsedParams = result.parsedParameters as PbPftpStartGPSMeasurement
        assertEquals(1000, parsedParams.minimumInterval)
        assertEquals(2, parsedParams.accuracy)
        assertEquals(60.1695, parsedParams.latitude, 0.0001)
        assertEquals(24.9354, parsedParams.longitude, 0.0001)
    }

    @Test
    fun `test propagates late notification stream error after emitted values`() = runTest {
        assertD2HStreamRuntimePolicyVectorContains("late-error-after-emitted-notification")
        val notificationId = PbPFtpDevToHostNotification.STOP_GPS_MEASUREMENT.number
        val lateError = IllegalStateException("late d2h failure")
        every { mockClient.waitForNotification() } returns flow {
            emit(createMockNotification(notificationId, ByteArray(0)))
            throw lateError
        }

        val results = mutableListOf<PolarD2HNotificationData>()
        val thrown = try {
            mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) }
            fail("Expected late D2H failure")
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertEquals(lateError, thrown)
        assertEquals(1, results.size)
        assertEquals(PolarDeviceToHostNotification.STOP_GPS_MEASUREMENT, results[0].notificationType)
        assertEquals(0, results[0].parameters.size)
    }

    @Test
    fun `test cancels upstream notification stream when collector is cancelled`() = runTest {
        assertD2HStreamRuntimePolicyVectorContains("consumer-cancels-after-first-notification")
        val notificationId = PbPFtpDevToHostNotification.STOP_GPS_MEASUREMENT.number
        val upstreamCancelled = CompletableDeferred<Unit>()
        every { mockClient.waitForNotification() } returns flow {
            try {
                emit(createMockNotification(notificationId, ByteArray(0)))
                awaitCancellation()
            } finally {
                upstreamCancelled.complete(Unit)
            }
        }

        val results = mutableListOf<PolarD2HNotificationData>()
        val job = launch { mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) } }
        testScheduler.advanceUntilIdle()

        job.cancelAndJoin()

        assertEquals(1, results.size)
        assertEquals(PolarDeviceToHostNotification.STOP_GPS_MEASUREMENT, results[0].notificationType)
        assertTrue("Expected upstream notification flow cancellation", upstreamCancelled.isCompleted)
    }

    @Test
    fun `test failed notification subscribe propagates error without emitted values`() = runTest {
        assertD2HStreamRuntimePolicyVectorContains("failed-subscribe-does-not-register-observer")
        val subscribeError = IllegalStateException("service missing")
        every { mockClient.waitForNotification() } returns flow { throw subscribeError }

        val results = mutableListOf<PolarD2HNotificationData>()
        val thrown = try {
            mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) }
            fail("Expected failed D2H subscription")
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertEquals(subscribeError, thrown)
        assertTrue("Failed D2H subscribe must not emit stale values", results.isEmpty())
    }

    @Test
    fun d2hNotificationGoldenVectors_matchAndroidBehavior() = runTest {
        val vectors = loadD2HNotificationVectors()
        assertTrue("Expected D2H notification golden vectors", vectors.isNotEmpty())

        vectors.forEach { vector ->
            val caseId = vector.get("id").asString
            val input = vector.getAsJsonObject("input")
            val expected = vector.getAsJsonObject("expected")
            val notifications = if (input.has("notifications")) {
                input.getAsJsonArray("notifications").map { notification ->
                    val notificationInput = notification.asJsonObject
                    createMockNotification(notificationInput.get("notificationId").asInt, notificationInput.get("parametersHex").asString.hexToByteArray())
                }
            } else {
                listOf(createMockNotification(input.get("notificationId").asInt, input.get("parametersHex").asString.hexToByteArray()))
            }

            every { mockClient.waitForNotification() } returns flowOf(*notifications.toTypedArray())

            val results = mutableListOf<PolarD2HNotificationData>()
            val job = launch { mockClient.observeDeviceToHostNotifications("test-device-id").collect { results.add(it) } }
            testScheduler.advanceUntilIdle()
            job.join()

            if (expected.has("emittedCount")) {
                assertEquals(caseId, expected.get("emittedCount").asInt, results.size)
                return@forEach
            }

            val expectedEvents = if (expected.has("events")) {
                expected.getAsJsonArray("events").map { it.asJsonObject }
            } else {
                listOf(expected)
            }
            assertEquals(caseId, expectedEvents.size, results.size)
            expectedEvents.forEachIndexed { index, expectedEvent ->
                val result = results[index]
                assertEquals(caseId, PolarDeviceToHostNotification.valueOf(expectedEvent.get("notificationType").asString), result.notificationType)
                expectedEvent.get("parametersHex")?.let { expectedParameters ->
                    assertArrayEquals(caseId, expectedParameters.asString.hexToByteArray(), result.parameters)
                }
                if (!expectedEvent.has("parametersHex") && !input.has("notifications")) {
                    assertArrayEquals(caseId, input.get("parametersHex").asString.hexToByteArray(), result.parameters)
                }
                val parametersHex = expectedEvent.get("parametersHex")?.asString ?: input.get("parametersHex")?.asString ?: ""
                val sharedProtoName = PolarD2hRuntimePlanning.parsedProtoName(expectedEvent.get("notificationType").asString, parametersHex)
                if (sharedProtoName != null) {
                    assertEquals(caseId, expectedEvent.get("parsedProto").asString, sharedProtoName)
                }
                assertParsedParameters(caseId, expectedEvent, result.parsedParameters)
            }
        }
    }

    @Test
    fun `d2h notification golden vectors follow neutral shared vector shape`() {
        loadD2HNotificationVectors().forEach { vector ->
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

    private fun assertParsedParameters(caseId: String, expected: JsonObject, actual: Any?) {
        if (expected.get("parsedProto").isJsonNull) {
            assertNull(caseId, actual)
            return
        }

        when (expected.get("parsedProto").asString) {
            "PbPFtpSyncRequiredParams" -> {
                assertTrue(caseId, actual is PbPFtpSyncRequiredParams)
                val parsed = actual as PbPFtpSyncRequiredParams
                val expectedTriggers = expected.getAsJsonArray("syncTriggers")
                assertEquals(caseId, expectedTriggers.size(), parsed.syncTriggersCount)
                expectedTriggers.forEachIndexed { index, trigger ->
                    assertEquals(caseId, PbPFtpSyncTriggerSource.valueOf(trigger.asJsonObject.get("source").asString), parsed.getSyncTriggers(index).source)
                }
            }
            "PbPFtpFilesystemModifiedParams" -> {
                assertTrue(caseId, actual is PbPFtpFilesystemModifiedParams)
                val parsed = actual as PbPFtpFilesystemModifiedParams
                assertEquals(caseId, Action.valueOf(expected.get("action").asString), parsed.action)
                assertEquals(caseId, expected.get("path").asString, parsed.path)
            }
            "PbPFtpAutoSyncStatusParams" -> {
                assertTrue(caseId, actual is PbPFtpAutoSyncStatusParams)
                val parsed = actual as PbPFtpAutoSyncStatusParams
                assertEquals(caseId, expected.get("succeeded").asBoolean, parsed.succeeded)
                assertEquals(caseId, expected.get("description").asString, parsed.description)
            }
            "PbPftpStartGPSMeasurement" -> {
                assertTrue(caseId, actual is PbPftpStartGPSMeasurement)
                val parsed = actual as PbPftpStartGPSMeasurement
                assertEquals(caseId, expected.get("minimumInterval").asInt, parsed.minimumInterval)
                assertEquals(caseId, expected.get("accuracy").asInt, parsed.accuracy)
                assertEquals(caseId, expected.get("latitude").asDouble, parsed.latitude, 0.0001)
                assertEquals(caseId, expected.get("longitude").asDouble, parsed.longitude, 0.0001)
            }
            else -> fail("$caseId has unsupported parsed proto ${expected.get("parsedProto").asString}")
        }
    }

    private fun loadD2HNotificationVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/d2h-notifications")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
            .filterNot { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString == "d2hStreamRuntimePolicy" }
            .filterNot { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString == "d2hStreamRuntimeReadiness" }
            .filterNot { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString == "d2hNotificationMappingReadiness" }
    }

    private fun assertD2HStreamRuntimePolicyVectorContains(scenarioId: String) {
        val vector = FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/d2h-notifications/stream-runtime-policy.json")).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
        assertEquals("stream-runtime-policy", vector.get("id").asString)
        assertEquals("fake-stream-runtime-policy", vector.getAsJsonObject("execution").get("kind").asString)
        val scenarioIds = vector.getAsJsonObject("input").getAsJsonArray("scenarios").map { scenario ->
            scenario.asJsonObject.get("id").asString
        }
        assertTrue("stream-runtime-policy.json must include $scenarioId", scenarioIds.contains(scenarioId))
    }

    @Test
    fun `d2h stream readiness manifest is pinned before stream runtime shared ownership`() {
        val vector = FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/d2h-notifications/stream-runtime-readiness.json")).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { family -> family.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { family -> family.asString }
        assertEquals("d2h-stream-runtime-readiness", vector.get("id").asString)
        assertEquals("d2hStreamRuntimeReadiness", input.get("kind").asString)
        val policyVectorPath = input.get("policyVectorPath").asString
        assertEquals("sdk/d2h-notifications/stream-runtime-policy.json", policyVectorPath)
        val policy = FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/$policyVectorPath")).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
        val scenarioIds = policy.getAsJsonObject("input").getAsJsonArray("scenarios").map { scenario ->
            scenario.asJsonObject.get("id").asString
        }
        val expectedCaseIds = policy.getAsJsonObject("expected").getAsJsonArray("cases").map { testCase ->
            testCase.asJsonObject.get("id").asString
        }
        assertEquals(D2H_STREAM_RUNTIME_SCENARIO_IDS, scenarioIds)
        assertEquals(D2H_STREAM_RUNTIME_SCENARIO_IDS, expectedCaseIds)
        val expectedFamilies = listOf(
            "mapped-value-before-late-error",
            "late-upstream-error-propagation",
            "consumer-cancellation-upstream-cancel",
            "suppress-notifications-after-cancel",
            "unknown-notification-filtering",
            "known-values-continue-after-unknown",
            "failed-subscribe-no-observer",
            "active-observer-cleanup-gate",
            "facade-error-mapping-gate",
            "platform-facade-vector-reference-gate",
            "compile-verification-gate"
        )
        assertEquals(expectedFamilies, requiredFamilies)
        assertEquals(expectedFamilies, coveredFamilies)
        assertEquals(
            "D2H stream runtime shared ownership remains valid while stream-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, mapped values emitted before late upstream errors are preserved, consumer cancellation cancels upstream work and suppresses later notifications, unknown notifications are filtered without stopping later known values, failed subscribe paths register no observers, public facade error mapping remains pinned, and the shared tests are compile-verified.",
            expected.get("commonDecision").asString
        )
        assertEquals("executable shared commonTest runtime planning guard", expected.getAsJsonObject("commonRuntimePrototype").get("status").asString)
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production shared ownership.", expected.getAsJsonObject("commonRuntimePrototype").get("reason").asString)
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarD2HNotificationsUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("PolarDeviceToHostNotificationsApiTests"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.D2hStreamRuntimePolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun `d2h mapping readiness manifest is pinned before mapping shared ownership`() {
        val vector = FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/d2h-notifications/mapping-readiness.json")).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { path -> path.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { family -> family.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { family -> family.asString }
        assertEquals("d2h-notification-mapping-readiness", vector.get("id").asString)
        assertEquals("d2hNotificationMappingReadiness", input.get("kind").asString)
        assertEquals(
            listOf(
                "sdk/d2h-notifications/filesystem-created.json",
                "sdk/d2h-notifications/sync-required-timed.json",
                "sdk/d2h-notifications/autosync-success.json",
                "sdk/d2h-notifications/start-gps-measurement.json",
                "sdk/d2h-notifications/stop-gps-empty.json",
                "sdk/d2h-notifications/sync-required-invalid-payload.json",
                "sdk/d2h-notifications/unknown-id-filtered.json",
                "sdk/d2h-notifications/repeated-sync-required-and-stop-gps.json"
            ),
            policyVectorPaths
        )
        val expectedFamilies = listOf(
            "known-notification-id-mapping",
            "unknown-notification-id-filtering",
            "raw-parameter-preservation",
            "filesystem-created-typed-field-decoding",
            "sync-required-trigger-decoding",
            "autosync-status-decoding",
            "start-gps-measurement-field-decoding",
            "stop-gps-empty-parameter-policy",
            "invalid-payload-null-parse-policy",
            "repeated-notification-ordering",
            "platform-mapping-vector-reference-gate",
            "compile-verification-gate"
        )
        assertEquals(expectedFamilies, requiredFamilies)
        assertEquals(expectedFamilies, coveredFamilies)
        assertEquals(
            "D2H notification mapping shared ownership remains valid while every mapping vector named by this readiness manifest is executable from shared commonTest, Android and iOS D2H mapping tests continue to reference the same vectors, known IDs, unknown-ID filtering, raw parameter preservation, typed fields for filesystem, sync-required, autosync, and start-GPS notifications, stop-GPS empty parameters, invalid-payload null parsing, repeated-notification ordering, and the shared tests are compile-verified.",
            expected.get("commonDecision").asString
        )
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarD2HNotificationsUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("PolarDeviceToHostNotificationsApiTests"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.D2hNotificationCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
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

    private companion object {
        val D2H_STREAM_RUNTIME_SCENARIO_IDS = listOf(
            "late-error-after-emitted-notification",
            "consumer-cancels-after-first-notification",
            "unknown-notification-between-known-values-is-filtered",
            "failed-subscribe-does-not-register-observer"
        )
    }

    /**
     * Helper function to create a mock notification message
     */
    private fun createMockNotification(id: Int, data: ByteArray): BlePsFtpUtils.PftpNotificationMessage {
        val notification = BlePsFtpUtils.PftpNotificationMessage()
        notification.id = id
        notification.byteArrayOutputStream = ByteArrayOutputStream().apply {
            write(data)
        }
        return notification
    }
}
