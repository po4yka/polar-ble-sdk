package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.Companion.getFileSystemType
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.errors.PolarOperationNotSupported
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.impl.utils.PolarFileUtils
import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter
import fi.polar.remote.representation.protobuf.DailySummary
import fi.polar.remote.representation.protobuf.DailySummary.PbActivityGoalSummary
import fi.polar.remote.representation.protobuf.DailySummary.PbDailySummary
import fi.polar.remote.representation.protobuf.Types
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbDuration
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbPFtpEntry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.util.concurrent.atomic.AtomicInteger

class PolarFileUtilsTest {

    @Test
    fun testListFilesRecurseShallowSuccess() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener, _) = mockBleConnection(deviceId)

        val dateDirectories = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    mutableListOf(
                        PbPFtpEntry.newBuilder().setName("20250101/").setSize(8192L).build(),
                        PbPFtpEntry.newBuilder().setName("20250202/").setSize(8192L).build()
                    )
                ).build().writeTo(this)
        }

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        coEvery { client.request(any<ByteArray>()) } returns dateDirectories

        val expectedPaths = mutableListOf("/U/0/20250101/", "/U/0/20250202/")

        // Act
        val result = PolarFileUtils.getFileList(deviceId, "/U/0/", false, listener, "")

        // Assert
        Assert.assertEquals(expectedPaths, result)
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 1) { client.request(any()) }
    }

    @Test
    fun testListFilesRecurseDeepSuccess() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener, _) = mockBleConnection(deviceId)

        val dateDirectories = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    mutableListOf(
                        PbPFtpEntry.newBuilder().setName("20250101/").setSize(8192L).build(),
                        PbPFtpEntry.newBuilder().setName("20250202/").setSize(8192L).build()
                    )
                ).build().writeTo(this)
        }

        val actDirectory = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    mutableListOf(
                        PbPFtpEntry.newBuilder().setName("ACT/").setSize(8192L).build(),
                    )
                ).build().writeTo(this)
        }

        val actFile = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    mutableListOf(
                        PbPFtpEntry.newBuilder().setName("ASAMPL0.BPB").setSize(333L).build(),
                    )
                ).build().writeTo(this)
        }

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        coEvery { client.request(any<ByteArray>()) } answers { dateDirectories } andThen actDirectory andThen actFile andThen actDirectory andThen actFile

        val expectedPaths = mutableListOf("/U/0/20250101/ACT/ASAMPL0.BPB", "/U/0/20250202/ACT/ASAMPL0.BPB")

        // Act
        val result = PolarFileUtils.getFileList(deviceId, "/U/0/", true, listener, "")

        // Assert
        Assert.assertEquals(expectedPaths, result)
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 5) { client.request(any()) }
    }

    @Test
    fun testListFiles_Throws_When_NoSession() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val sessions = mockk<Set<BleDeviceSession>>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns false

        // Act & Assert
        try {
            PolarFileUtils.getFileList(deviceId, "/U/0/", true, listener, "")
            Assert.fail("Expected PolarDeviceNotFound")
        } catch (e: PolarDeviceNotFound) {
            // expected
        }
    }

    @Test
    fun testListFiles_Throws_When_FileSystemNotSupported() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener, session) = mockBleConnection(deviceId)

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.H10_FILE_SYSTEM
        every { session.polarDeviceType } returns "h10"

        // Act & Assert
        try {
            PolarFileUtils.getFileList(deviceId, "/U/0/", true, listener, "")
            Assert.fail("Expected PolarOperationNotSupported")
        } catch (e: PolarOperationNotSupported) {
            // expected
        }
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 0) { client.request(any()) }
    }

    @Test
    fun testWriteFile() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener) = mockBleConnection(deviceId)

        val proto = buildDailySummaryProto()
        val outputStream = ByteArrayOutputStream()
        proto.writeTo(outputStream)

        every { client.write(any(), any()) } returns flowOf(0L)

        // Act
        PolarFileUtils.writeFile(deviceId, "/U/0/20000101/DSUM/", listener, outputStream.toByteArray(), "")

        val verifyWriteBuilder = PftpRequest.PbPFtpOperation.newBuilder()
        verifyWriteBuilder.command = PftpRequest.PbPFtpOperation.Command.PUT
        verifyWriteBuilder.path = "/U/0/20000101/DSUM/"

        // Assert
        verify(exactly = 1) { client.isServiceDiscovered }
        verify(exactly = 1) { client.write(verifyWriteBuilder.build().toByteArray(), any()) }
    }

    @Test
    fun testReadFile() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener) = mockBleConnection(deviceId)

        val proto = buildDailySummaryProto()
        val outputStream = ByteArrayOutputStream()
        proto.writeTo(outputStream)

        coEvery { client.request(any<ByteArray>()) } returns outputStream

        // Act
        val result = PolarFileUtils.readFile(deviceId, "/U/0/20000101/DSUM/DSUM.BPB", listener, "")

        // Assert
        Assert.assertNotNull(result)
        Assert.assertTrue(proto.toByteArray().contentEquals(result))
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 1) { client.request(any()) }
    }

    @Test
    fun testReadFile_Throws_When_NoSession() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val listener = mockk<BleDeviceListener>()
        val sessions = mockk<Set<BleDeviceSession>>()

        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns false

        // Act & Assert
        try {
            PolarFileUtils.readFile(deviceId, "/U/0/", listener, "")
            Assert.fail("Expected PolarDeviceNotFound")
        } catch (e: PolarDeviceNotFound) {
            // expected
        }
    }

    @Test
    fun testReadFile_Throws_When_NoFtpClient() = runTest {
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

        // Act & Assert
        try {
            PolarFileUtils.readFile(deviceId, "/U/0/", listener, "")
            Assert.fail("Expected PolarServiceNotAvailable")
        } catch (e: PolarServiceNotAvailable) {
            // expected
        }
        coVerify(exactly = 0) { client.request(any()) }
    }

    @Test
    fun testReadFile_Throws_When_FtpRequestFails() = runTest {
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
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)
        coEvery { client.request(any()) } throws BlePsFtpUtils.PftpResponseError("All is lost!", 0)

        // Act & Assert
        try {
            PolarFileUtils.readFile(deviceId, "/U/0/", listener, "")
            Assert.fail("Expected exception")
        } catch (e: Exception) {
            // handleError always converts PftpResponseError into a plain Exception (never rethrows it directly)
            Assert.assertFalse(e is BlePsFtpUtils.PftpResponseError)
        }
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 1) { client.request(any()) }
    }

    @Test
    fun testRemoveSingleFile() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, listener) = mockBleConnection(deviceId)

        val outputStream = ByteArrayOutputStream()
        coEvery { client.request(any<ByteArray>()) } returns outputStream

        // Act
        val result = PolarFileUtils.removeSingleFile(deviceId, "/U/0/20000101/DSUM/DSUM.BPB", listener, "")

        // Assert
        Assert.assertEquals(outputStream, result)
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 1) { client.request(any()) }
    }

    @Test
    fun testRemoveSingleFile_Throws_When_NoFtpClient() = runTest {
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

        // Act & Assert
        try {
            PolarFileUtils.removeSingleFile(deviceId, "/U/0/", listener, "")
            Assert.fail("Expected PolarServiceNotAvailable")
        } catch (e: PolarServiceNotAvailable) {
            // expected
        }
        coVerify(exactly = 0) { client.request(any()) }
    }

    @Test
    fun testRemoveSingleFile_Throws_When_FtpRequestFails() = runTest {
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
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)
        coEvery { client.request(any()) } throws BlePsFtpUtils.PftpResponseError("All is lost!", 0)

        // Act & Assert
        try {
            PolarFileUtils.removeSingleFile(deviceId, "/U/0/", listener, "")
            Assert.fail("Expected exception")
        } catch (e: Exception) {
            // handleError always converts PftpResponseError into a plain Exception (never rethrows it directly)
            Assert.assertFalse(e is BlePsFtpUtils.PftpResponseError)
        }
        verify(exactly = 1) { client.isServiceDiscovered }
        coVerify(exactly = 1) { client.request(any()) }
    }

    private fun buildDailySummaryProto(): PbDailySummary {
        return PbDailySummary.newBuilder()
            .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
            .setActivityDistance(1234.56f)
            .setActivityCalories(100)
            .setBmrCalories(2000)
            .setTrainingCalories(500)
            .setActivityClassTimes(
                DailySummary.PbActivityClassTimes.newBuilder()
                    .setTimeLightActivity(PbDuration.newBuilder().setHours(5).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeSleep(PbDuration.newBuilder().setHours(8).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeSedentary(PbDuration.newBuilder().setHours(7).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeContinuousModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeContinuousVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeIntermittentModerate(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeIntermittentVigorous(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                    .setTimeNonWear(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
            )
            .setActivityGoalSummary(
                PbActivityGoalSummary.newBuilder()
                    .setActivityGoal(100f)
                    .setAchievedActivity(50f)
            )
            .setDailyBalanceFeedback(Types.PbDailyBalanceFeedback.DB_YOU_COULD_DO_MORE_TRAINING)
            .setReadinessForSpeedAndStrengthTraining(Types.PbReadinessForSpeedAndStrengthTraining.RSST_A1_RECOVERED_READY_FOR_ALL_TRAINING)
            .setSteps(10000)
            .build()
    }

    @Test
    fun testFetchRecursively_Handles_Error103_NoSuchFileOrDirectory() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val (client, _, _) = mockBleConnection(deviceId)

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2

        coEvery { client.request(any<ByteArray>()) } throws
            BlePsFtpUtils.PftpResponseError("Directory not found", 103)

        // Act
        val results = mutableListOf<Pair<String, Long>>()
        PolarFileUtils.fetchRecursively(
            client = client,
            path = "/U/0/missing_directory/",
            condition = null,
            recurseDeep = true,
            tag = "TestTag"
        ).collect { results.add(it) }

        // Assert
        Assert.assertTrue("Expected empty results but got $results", results.isEmpty())
        coVerify(exactly = 1) { client.request(any<ByteArray>()) }
    }

    @Test
    fun `file utility golden vectors list expected paths`() = runTest {
        Assert.assertEquals("/U/0/", PolarRuntimePlannerAdapter.normalizeFileListFolderPath("U/0"))
        Assert.assertEquals("/", PolarRuntimePlannerAdapter.normalizeFileListFolderPath(""))
        loadFileUtilityVectors().forEach { vector ->
            val caseId = vector.get("id").asString
            val input = vector.getAsJsonObject("input")
            if (!input.has("directories")) return@forEach
            val deviceId = "E123456F"
            val (client, listener, _) = mockBleConnection(deviceId)
            val directories = input.getAsJsonObject("directories")

            mockkObject(BlePolarDeviceCapabilitiesUtility)
            every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
            coEvery { client.request(any<ByteArray>()) } answers {
                val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
                directoryResponse(directories.getAsJsonArray(operation.path) ?: JsonArray())
            }

            val result = PolarFileUtils.getFileList(
                deviceId,
                input.get("rootPath").asString,
                input.get("recurseDeep").asBoolean,
                listener,
                ""
            )

            Assert.assertEquals(caseId, vector.getAsJsonObject("expected").getAsJsonArray("paths").map { it.asString }, result)
        }
    }

    @Test
    fun `file utility golden vectors preserve low level file operations`() = runTest {
        val vector = loadFileUtilityVectors().first { it.get("id").asString == "file-read-write-delete-operations" }
        val inputOperations = vector.getAsJsonObject("input").getAsJsonArray("operations").map { it.asJsonObject }
        val expectedOperations = vector.getAsJsonObject("expected").getAsJsonArray("operations").map { it.asJsonObject }

        inputOperations.zip(expectedOperations).forEach { (input, expected) ->
            val caseLabel = "${vector.get("id").asString}:${input.get("action").asString}"
            val deviceId = "E123456F"
            val (client, listener, _) = mockBleConnection(deviceId)

            when (input.get("action").asString) {
                "read" -> {
                    coEvery { client.request(any<ByteArray>()) } returns ByteArrayOutputStream().apply {
                        write(hexToBytes(input.get("responseHex").asString))
                    }

                    val result = PolarFileUtils.readFile(deviceId, input.get("path").asString, listener, "")

                    Assert.assertEquals(caseLabel, expected.get("resultHex").asString, result!!.toHex())
                    coVerify(exactly = 1) {
                        client.request(match { bytes ->
                            val operation = PftpRequest.PbPFtpOperation.parseFrom(bytes)
                            operation.command.name == expected.get("command").asString &&
                                operation.path == expected.get("path").asString
                        })
                    }
                }
                "write" -> {
                    val headerSlot = io.mockk.slot<ByteArray>()
                    val dataSlot = io.mockk.slot<ByteArrayInputStream>()
                    every { client.write(capture(headerSlot), capture(dataSlot)) } answers {
                        flowOf(0L)
                    }

                    PolarFileUtils.writeFile(deviceId, input.get("path").asString, listener, hexToBytes(input.get("payloadHex").asString), "")

                    val operation = PftpRequest.PbPFtpOperation.parseFrom(headerSlot.captured)
                    Assert.assertEquals(caseLabel, expected.get("command").asString, operation.command.name)
                    Assert.assertEquals(caseLabel, expected.get("path").asString, operation.path)
                    Assert.assertEquals(caseLabel, expected.get("writtenHex").asString, dataSlot.captured.readBytes().toHex())
                    verify(exactly = 1) { client.write(any(), any()) }
                }
                "delete" -> {
                    coEvery { client.request(any<ByteArray>()) } returns ByteArrayOutputStream().apply {
                        write(hexToBytes(input.get("responseHex").asString))
                    }

                    val result = PolarFileUtils.removeSingleFile(deviceId, input.get("path").asString, listener, "")

                    Assert.assertEquals(caseLabel, expected.get("resultHex").asString, result.toHex())
                    coVerify(exactly = 1) {
                        client.request(match { bytes ->
                            val operation = PftpRequest.PbPFtpOperation.parseFrom(bytes)
                            operation.command.name == expected.get("command").asString &&
                                operation.path == expected.get("path").asString
                        })
                    }
                }
                else -> error("Unsupported file utility action ${input.get("action").asString}")
            }
        }
    }

    @Test
    fun `file utility golden vectors follow neutral KMP vector shape`() {
        loadFileUtilityVectors().forEach { vector ->
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
    }

    private fun mockBleConnection(deviceId: String): Triple<BlePsFtpClient, BleDeviceListener, BleDeviceSession> {
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
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)

        return Triple(client, listener, session)
    }

    private fun directoryResponse(entries: JsonArray): ByteArrayOutputStream {
        return ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    entries.map { element ->
                        val entry = element.asJsonObject
                        PbPFtpEntry.newBuilder()
                            .setName(entry.get("name").asString)
                            .setSize(entry.get("size").asLong)
                            .build()
                    }
                )
                .build()
                .writeTo(this)
        }
    }

    @Test
    fun runtimeErrorPolicyVector_isPinnedBeforeRuntimeMigration() {
        val vector = loadFileUtilityVectors().first { it.get("id").asString == "runtime-error-policy" }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val inputCaseIds = input.getAsJsonArray("cases").map { it.asJsonObject.get("id").asString }
        val expectedCaseIds = expected.getAsJsonObject("commonRuntimePrototype").getAsJsonArray("cases").map { it.asJsonObject.get("id").asString }

        Assert.assertTrue("runtime-error-policy", vector.has("execution"))
        Assert.assertEquals(FILE_RUNTIME_ERROR_CASE_IDS, inputCaseIds)
        Assert.assertEquals(FILE_RUNTIME_ERROR_CASE_IDS, expectedCaseIds)
        Assert.assertEquals(FILE_RUNTIME_ERROR_MIGRATION_REQUIREMENT, expected.get("migrationRequirement").asString)
        Assert.assertEquals(listOf("com.polar.sdk.api.model.utils.PolarFileUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("PolarFileUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.FileRuntimeErrorPolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun runtimeErrorReadinessManifest_isPinnedBeforeRuntimeMigration() {
        val vector = loadFileUtilityVectors().first { it.get("id").asString == "runtime-error-readiness" }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val consumerTests = vector.getAsJsonObject("consumerTests")

        Assert.assertEquals("runtime-error-readiness", vector.get("id").asString)
        Assert.assertEquals("fileRuntimeErrorReadiness", input.get("kind").asString)
        Assert.assertEquals("sdk/file-utils/runtime-error-policy.json", input.get("policyVectorPath").asString)
        Assert.assertEquals(FILE_RUNTIME_ERROR_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(FILE_RUNTIME_ERROR_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(FILE_RUNTIME_ERROR_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        val commonRuntimePrototype = expected.getAsJsonObject("commonRuntimePrototype")
        Assert.assertEquals("executable shared commonTest runtime planning guard", commonRuntimePrototype.get("status").asString)
        Assert.assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", commonRuntimePrototype.get("reason").asString)
        Assert.assertEquals(listOf("com.polar.sdk.api.model.utils.PolarFileUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("PolarFileUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.FileRuntimeErrorPolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun fileFacadeRuntimePolicyVector_isPinnedBeforeRuntimeMigration() {
        val vector = loadFileUtilityVectors().first { it.get("id").asString == "file-facade-runtime-policy" }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val execution = vector.getAsJsonObject("execution")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val operationIds = input.getAsJsonArray("operations").map { it.asJsonObject.get("id").asString }
        val expectedCaseIds = expected.getAsJsonObject("commonRuntimePrototype").getAsJsonArray("cases").map { it.asJsonObject.get("id").asString }

        Assert.assertEquals("fileFacadeRuntimePolicy", input.get("kind").asString)
        Assert.assertEquals(FILE_FACADE_RUNTIME_OPERATION_IDS, operationIds)
        Assert.assertEquals(FILE_FACADE_RUNTIME_OPERATION_IDS, expectedCaseIds)
        Assert.assertEquals(FILE_FACADE_RUNTIME_POLICY_COMMON_DECISION, expected.get("commonDecision").asString)
        Assert.assertEquals(FILE_FACADE_RUNTIME_MIGRATION_DECISION, vector.get("commonDecision").asString)
        Assert.assertEquals("fake-file-facade-runtime-policy", execution.get("kind").asString)
        Assert.assertEquals("public-facade-psftp-command-capture", execution.get("transport").asString)
        Assert.assertEquals(listOf("com.polar.sdk.impl.BDBleApiImplTest", "com.polar.sdk.api.model.utils.PolarFileUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("PolarBleApiImplTests", "PolarFileUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.FileFacadeRuntimePolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun fileFacadeRuntimeReadinessManifest_isPinnedBeforeRuntimeMigration() {
        val vector = loadFileUtilityVectors().first { it.get("id").asString == "file-facade-runtime-readiness" }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        Assert.assertEquals("file-facade-runtime-readiness", vector.get("id").asString)
        Assert.assertEquals("fileFacadeRuntimeReadiness", input.get("kind").asString)
        Assert.assertEquals("sdk/file-utils/file-facade-runtime-policy.json", input.get("policyVectorPath").asString)
        Assert.assertEquals(FILE_FACADE_RUNTIME_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(FILE_FACADE_RUNTIME_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(FILE_FACADE_RUNTIME_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        val commonRuntimePrototype = expected.getAsJsonObject("commonRuntimePrototype")
        Assert.assertEquals("executable shared commonTest runtime planning guard", commonRuntimePrototype.get("status").asString)
        Assert.assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", commonRuntimePrototype.get("reason").asString)
        Assert.assertEquals(listOf("com.polar.sdk.impl.BDBleApiImplTest", "com.polar.sdk.api.model.utils.PolarFileUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("PolarBleApiImplTests", "PolarFileUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.FileFacadeRuntimePolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun loadFileUtilityVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/file-utils")
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

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have even length" }
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun ByteArrayOutputStream.toHex(): String = toByteArray().toHex()

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

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

    private companion object {
        val FILE_RUNTIME_ERROR_READINESS_FAMILIES = listOf(
            "directory-missing-status-103",
            "directory-malformed-payload-parse-failure",
            "read-file-transport-error",
            "write-file-put-header-before-stream-error",
            "write-file-payload-capture-before-stream-error",
            "delete-file-response-error-status-message",
            "command-path-capture-for-every-operation",
            "facade-error-mapping-deferred",
            "platform-runtime-vector-reference-gate",
            "compile-verification-gate"
        )

        const val FILE_RUNTIME_ERROR_MIGRATION_REQUIREMENT = "Before moving file utility orchestration into common KMP code, implement fake PFTP request and write-stream tests that cover malformed directory payloads, request-level transport errors, response-error status mapping, and write-stream failures after the PUT header is prepared."

        val FILE_RUNTIME_ERROR_CASE_IDS = listOf(
            "directory-list-response-error-103",
            "directory-list-malformed-payload",
            "read-file-transport-error",
            "write-file-stream-error-after-header",
            "delete-file-response-error"
        )

        const val FILE_RUNTIME_ERROR_READINESS_COMMON_DECISION = "File runtime error migration may proceed only after runtime-error-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS file tests continue to reference the same vectors, directory missing status 103, malformed directory payload parse failure, read transport errors, write PUT header and payload capture before stream failure, delete response-error status/message mapping, command/path capture, public facade error mapping, and the shared tests are compile-verified."

        val FILE_FACADE_RUNTIME_OPERATION_IDS = listOf(
            "read-low-level-file-success",
            "read-low-level-file-empty-success",
            "read-low-level-file-request-failure",
            "read-low-level-file-response-error",
            "write-low-level-file-success",
            "write-low-level-file-progress-success",
            "write-low-level-file-stream-failure",
            "write-low-level-file-response-error",
            "delete-low-level-file-success",
            "delete-low-level-file-request-failure",
            "delete-low-level-file-response-error"
        )

        const val FILE_FACADE_RUNTIME_POLICY_COMMON_DECISION = "A shared file facade runtime may own deterministic GET/PUT/REMOVE planning, empty read payloads, write progress consumption, and payload capture only after platform facades keep public error mapping, read/write/delete request and response-error propagation, and directory-list traversal policies pinned."

        const val FILE_FACADE_RUNTIME_MIGRATION_DECISION = "Promote low-level file facade planning only after read/write/delete public APIs reference this vector, directory traversal remains covered by list-files vectors, and runtime-error-policy.json keeps malformed directory, response-error, transport-error, empty read payload, delete request failure, write progress success, and write-stream failure behavior pinned."

        val FILE_FACADE_RUNTIME_READINESS_FAMILIES = listOf(
            "low-level-file-path-gate",
            "read-file-get-success",
            "read-file-empty-success",
            "read-file-request-failure",
            "read-file-response-error",
            "write-file-put-success",
            "write-file-payload-capture",
            "write-file-progress-before-completion",
            "write-file-stream-failure-after-payload",
            "write-file-response-error-after-payload",
            "delete-file-remove-success",
            "delete-file-request-failure",
            "delete-file-response-error",
            "directory-list-shallow-vector-reference-gate",
            "directory-list-recursive-vector-reference-gate",
            "read-write-delete-model-vector-reference-gate",
            "runtime-error-policy-reference-gate",
            "malformed-directory-policy-gate",
            "response-error-policy-gate",
            "facade-error-mapping-gate",
            "platform-facade-vector-reference-gate",
            "compile-verification-gate"
        )

        const val FILE_FACADE_RUNTIME_READINESS_COMMON_DECISION = "File facade runtime migration may proceed only after file-facade-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, directory-list traversal vectors remain linked, runtime-error-policy.json keeps malformed-directory, response-error, transport-error, empty read payload, delete request failure, write progress before completion, read/write/delete response-error, and write-stream failure behavior covered, public facade error mapping is pinned, and the shared tests are compile-verified."
    }
}
