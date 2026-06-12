// Copyright © 2023 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.PolarOfflineExerciseV2Api
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter
import fi.polar.remote.representation.protobuf.ExerciseSamples.PbExerciseSamples
import fi.polar.remote.representation.protobuf.Structures
import fi.polar.remote.representation.protobuf.Types
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for PolarOfflineExerciseV2ApiImpl.
 *
 * These tests validate the API implementation using mocked PFTP requests/responses.
 */
class PolarOfflineExerciseV2ApiImplTest {

    private val deviceId = "E123456F"

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `startOfflineExerciseV2() should return SUCCESS result`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)
        val sportProfile = PolarExerciseSession.SportProfile.RUNNING

        val mockResponse = PftpResponse.PbPftpStartDmExerciseResult.newBuilder()
            .setResult(PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_SUCCESS)
            .setDmDirectoryPath("/exercise")
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.startOfflineExerciseV2(deviceId, sportProfile)

        // Assert
        coVerify { client.query(PftpRequest.PbPFtpQuery.START_DM_EXERCISE_VALUE, any()) }
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.SUCCESS, result.result)
        Assert.assertEquals("/exercise", result.directoryPath)
    }

    @Test
    fun `startOfflineExerciseV2() should return EXERCISE_ONGOING result`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)
        val sportProfile = PolarExerciseSession.SportProfile.CYCLING

        val mockResponse = PftpResponse.PbPftpStartDmExerciseResult.newBuilder()
            .setResult(PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_EXE_ONGOING)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.startOfflineExerciseV2(deviceId, sportProfile)

        // Assert
        coVerify { client.query(PftpRequest.PbPFtpQuery.START_DM_EXERCISE_VALUE, any()) }
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.EXERCISE_ONGOING, result.result)
    }

    @Test
    fun `startOfflineExerciseV2() should return LOW_BATTERY result`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpStartDmExerciseResult.newBuilder()
            .setResult(PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_LOW_BATTERY)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.startOfflineExerciseV2(deviceId, PolarExerciseSession.SportProfile.RUNNING)

        // Assert
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.LOW_BATTERY, result.result)
    }

    @Test
    fun `startOfflineExerciseV2() should return SDK_MODE result`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpStartDmExerciseResult.newBuilder()
            .setResult(PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_SDK_MODE)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.startOfflineExerciseV2(deviceId, PolarExerciseSession.SportProfile.RUNNING)

        // Assert
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.SDK_MODE, result.result)
    }

    @Test
    fun `startOfflineExerciseV2() should return UNKNOWN_SPORT result`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpStartDmExerciseResult.newBuilder()
            .setResult(PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_UNKNOWN_SPORT)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.startOfflineExerciseV2(deviceId, PolarExerciseSession.SportProfile.OTHER_OUTDOOR)

        // Assert
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.UNKNOWN_SPORT, result.result)
    }

    @Test
    fun `startOfflineExerciseV2() should use default directory path when not provided`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpStartDmExerciseResult.newBuilder()
            .setResult(PftpResponse.PbPftpStartDmExerciseResult.PbStartDmExerciseResult.RESULT_SUCCESS)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.startOfflineExerciseV2(deviceId, PolarExerciseSession.SportProfile.RUNNING)

        // Assert
        Assert.assertEquals("/", result.directoryPath)
    }

    @Test
    fun `startOfflineExerciseV2() should throw PolarDeviceNotFound when no session`() = runTest {
        // Arrange
        val listener = mockk<BleDeviceListener>()
        val sessions = mockk<Set<BleDeviceSession>>()
        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns false

        val api = PolarOfflineExerciseV2ApiImpl(listener)

        // Act & Assert
        try {
            api.startOfflineExerciseV2(deviceId, PolarExerciseSession.SportProfile.RUNNING)
            Assert.fail("Expected PolarDeviceNotFound")
        } catch (e: PolarDeviceNotFound) {
            // expected
        }
    }

    @Test
    fun `startOfflineExerciseV2() should throw PolarServiceNotAvailable when client not available`() = runTest {
        // Arrange
        val (_, listener, session) = mockBleConnectionWithoutClient(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)
        every { session.fetchClient(any()) } returns null

        // Act & Assert
        try {
            api.startOfflineExerciseV2(deviceId, PolarExerciseSession.SportProfile.RUNNING)
            Assert.fail("Expected PolarServiceNotAvailable")
        } catch (e: PolarServiceNotAvailable) {
            // expected
        }
    }

    @Test
    fun `stopOfflineExerciseV2() should complete successfully`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()

        // Act
        api.stopOfflineExerciseV2(deviceId)

        // Assert
        coVerify { client.query(PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE, any()) }
    }

    @Test
    fun `stopOfflineExerciseV2() should throw PolarServiceNotAvailable when client not available`() = runTest {
        // Arrange
        val (_, listener, session) = mockBleConnectionWithoutClient(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)
        every { session.fetchClient(any()) } returns null

        // Act & Assert
        try {
            api.stopOfflineExerciseV2(deviceId)
            Assert.fail("Expected PolarServiceNotAvailable")
        } catch (e: PolarServiceNotAvailable) {
            // expected
        }
    }

    @Test
    fun `getOfflineExerciseStatusV2() should return true when exercise is running`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpGetExerciseStatusResult.newBuilder()
            .setExerciseType(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseType.EXERCISE_TYPE_DATA_MERGE)
            .setExerciseState(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.getOfflineExerciseStatusV2(deviceId)

        // Assert
        Assert.assertEquals(true, result)
        coVerify { client.query(PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE, any()) }
    }

    @Test
    fun `getOfflineExerciseStatusV2() should return false when exercise is paused`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpGetExerciseStatusResult.newBuilder()
            .setExerciseType(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseType.EXERCISE_TYPE_DATA_MERGE)
            .setExerciseState(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_PAUSED)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.getOfflineExerciseStatusV2(deviceId)

        // Assert
        Assert.assertEquals(false, result)
    }

    @Test
    fun `getOfflineExerciseStatusV2() should return false when exercise type is not DATA_MERGE`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val mockResponse = PftpResponse.PbPftpGetExerciseStatusResult.newBuilder()
            .setExerciseType(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseType.EXERCISE_TYPE_NORMAL)
            .setExerciseState(PftpResponse.PbPftpGetExerciseStatusResult.PbExerciseState.EXERCISE_STATE_RUNNING)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.query(any(), any()) } returns mockResponseContent

        // Act
        val result = api.getOfflineExerciseStatusV2(deviceId)

        // Assert
        Assert.assertEquals(false, result)
    }

    @Test
    fun `fetchOfflineExerciseV2() should return heart rate samples`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val entry = PolarExerciseEntry(
            path = "/exercise/session1/SAMPLES.BPB",
            date = LocalDateTime.now(),
            identifier = "SAMPLES.BPB"
        )

        val hrSamples = listOf(60, 62, 65, 70)
        val mockResponse = PbExerciseSamples.newBuilder()
            .setRecordingInterval(Types.PbDuration.newBuilder().setSeconds(1).build())
            .addAllHeartRateSamples(hrSamples)
            .build()

        val mockResponseContent = ByteArrayOutputStream().apply { mockResponse.writeTo(this) }
        coEvery { client.request(any()) } returns mockResponseContent

        // Act
        val result = api.fetchOfflineExerciseV2(deviceId, entry)

        // Assert
        coVerify {
            client.request(match { bytes ->
                val operation = PftpRequest.PbPFtpOperation.parseFrom(bytes)
                operation.command == PftpRequest.PbPFtpOperation.Command.GET &&
                operation.path == entry.path
            })
        }
        Assert.assertEquals(1, result.recordingInterval)
        Assert.assertEquals(hrSamples, result.hrSamples)
    }

    @Test
    fun `fetchOfflineExerciseV2() should throw PolarServiceNotAvailable when client not available`() = runTest {
        // Arrange
        val (_, listener, session) = mockBleConnectionWithoutClient(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val entry = PolarExerciseEntry(
            path = "/exercise/session1/SAMPLES.BPB",
            date = LocalDateTime.now(),
            identifier = "SAMPLES.BPB"
        )

        every { session.fetchClient(any()) } returns null

        // Act & Assert
        try {
            api.fetchOfflineExerciseV2(deviceId, entry)
            Assert.fail("Expected PolarServiceNotAvailable")
        } catch (e: PolarServiceNotAvailable) {
            // expected
        }
    }

    @Test
    fun `removeOfflineExerciseV2() should complete successfully`() = runTest {
        // Arrange
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val entry = PolarExerciseEntry(
            path = "/exercise/session1/SAMPLES.BPB",
            date = LocalDateTime.now(),
            identifier = "SAMPLES.BPB"
        )

        coEvery { client.request(any()) } returns ByteArrayOutputStream()

        // Act
        api.removeOfflineExerciseV2(deviceId, entry)

        // Assert
        coVerify {
            client.request(match { bytes ->
                val operation = PftpRequest.PbPFtpOperation.parseFrom(bytes)
                operation.command == PftpRequest.PbPFtpOperation.Command.REMOVE &&
                operation.path == entry.path
            })
        }
    }

    @Test
    fun `removeOfflineExerciseV2() should throw PolarServiceNotAvailable when client not available`() = runTest {
        // Arrange
        val (_, listener, session) = mockBleConnectionWithoutClient(deviceId)
        val api = PolarOfflineExerciseV2ApiImpl(listener)

        val entry = PolarExerciseEntry(
            path = "/exercise/session1/SAMPLES.BPB",
            date = LocalDateTime.now(),
            identifier = "SAMPLES.BPB"
        )

        every { session.fetchClient(any()) } returns null

        // Act & Assert
        try {
            api.removeOfflineExerciseV2(deviceId, entry)
            Assert.fail("Expected PolarServiceNotAvailable")
        } catch (e: PolarServiceNotAvailable) {
            // expected
        }
    }

    @Test
    fun `offline exercise file headers use shared file facade planning`() {
        val path = "/U/0/20260225/E/123456/SAMPLES.BPB"

        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to path,
            PolarOfflineExerciseV2ApiImpl.offlineExerciseFetchOperation(path)
        )
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.REMOVE to path,
            PolarOfflineExerciseV2ApiImpl.offlineExerciseRemoveOperation(path)
        )
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/DEVICE.BPB",
            PolarOfflineExerciseV2ApiImpl.offlineExerciseDeviceInfoReadOperation()
        )
    }

    @Test
    fun `offline exercise command queries use shared command planning`() {
        Assert.assertEquals(
            PftpRequest.PbPFtpQuery.START_DM_EXERCISE_VALUE,
            PolarRuntimePlannerAdapter.queryValue(
                PolarRuntimePlannerAdapter.planCommandQuery(
                    id = "offline-exercise-v2-start",
                    query = "START_DM_EXERCISE",
                    parameters = listOf("sportProfileId=${PolarExerciseSession.SportProfile.RUNNING.id}")
                )
            )
        )
        Assert.assertEquals(
            PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE,
            PolarRuntimePlannerAdapter.queryValue(
                PolarRuntimePlannerAdapter.planCommandQuery(
                    id = "offline-exercise-v2-stop",
                    query = "STOP_EXERCISE",
                    parameters = listOf("save=true")
                )
            )
        )
        Assert.assertEquals(
            PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE,
            PolarRuntimePlannerAdapter.queryValue(
                PolarRuntimePlannerAdapter.planCommandQuery("offline-exercise-v2-status", "GET_EXERCISE_STATUS")
            )
        )
    }

    @Test
    fun `exercise session readiness manifest is pinned before offline exercise facade migration`() {
        val manifest = loadExerciseSessionReadinessManifest()
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val consumerTests = manifest.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        Assert.assertEquals("exercise-session-readiness", manifest.get("id").asString)
        Assert.assertEquals("exerciseSessionReadiness", input.get("kind").asString)
        Assert.assertEquals(EXERCISE_SESSION_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(EXERCISE_SESSION_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(EXERCISE_SESSION_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        Assert.assertEquals(listOf("com.polar.sdk.api.model.PolarExerciseSessionTest", "com.polar.sdk.impl.PolarOfflineExerciseV2ApiImplTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("PolarOfflineExerciseV2Tests"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.ExerciseSessionModelsCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun `test StartResult enum values`() {
        // Arrange
        // Act
        val results = PolarOfflineExerciseV2Api.StartResult.values()

        // Assert
        Assert.assertEquals(6, results.size)
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.SUCCESS, results[0])
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.EXERCISE_ONGOING, results[1])
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.LOW_BATTERY, results[2])
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.SDK_MODE, results[3])
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.UNKNOWN_SPORT, results[4])
        Assert.assertEquals(PolarOfflineExerciseV2Api.StartResult.OTHER, results[5])
    }

    private fun mockBleConnection(deviceId: String): Pair<BlePsFtpClient, BleDeviceListener> {
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
        every { session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)

        return Pair(client, listener)
    }

    private fun mockBleConnectionWithoutClient(deviceId: String): Triple<BlePsFtpClient, BleDeviceListener, BleDeviceSession> {
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
        every { client.isServiceDiscovered } returns false

        return Triple(client, listener, session)
    }

    private fun loadExerciseSessionReadinessManifest(): JsonObject {
        return JsonParser().parse(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/exercise-session/exercise-session-readiness.json")
                .readText()
        ).asJsonObject
    }

    private fun findRepositoryRoot(): File {
        return generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { file -> file.parentFile }
            .first { file -> File(file, "testdata/golden-vectors/schema/golden-vector.schema.json").isFile }
    }

    private companion object {
        val EXERCISE_SESSION_READINESS_FAMILIES = listOf(
            "sport-profile-id-mapping",
            "unknown-sport-profile-fallback",
            "offline-exercise-start-command-planning",
            "offline-exercise-stop-command-planning",
            "offline-exercise-status-command-planning",
            "offline-exercise-file-read-remove-paths",
            "offline-exercise-device-info-path",
            "protobuf-construction-platform-boundary",
            "status-result-platform-boundary",
            "public-error-mapping-boundary",
            "platform-exercise-session-vector-reference-gate",
            "compile-verification-gate"
        )
        const val EXERCISE_SESSION_READINESS_COMMON_DECISION = "Exercise-session migration may proceed only after this readiness manifest is executable from shared commonTest, Android and iOS exercise-session tests continue to pin sport-profile ID mapping, unknown sport-profile fallback, offline exercise command planning, offline exercise file read/remove paths, device-info path planning, protobuf construction boundaries, status-result platform boundaries, public error mapping boundaries, platform vector references, and compile verification before broader exercise execution moves."
    }
}
