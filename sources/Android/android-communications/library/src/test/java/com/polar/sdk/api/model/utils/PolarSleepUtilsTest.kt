package com.polar.sdk.api.model.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.sleep.OriginalSleepRange
import com.polar.sdk.api.model.sleep.PolarSleepAnalysisResult
import com.polar.sdk.api.model.sleep.SleepCycle
import com.polar.sdk.api.model.sleep.SleepRating
import com.polar.sdk.api.model.sleep.SleepSkinTemperatureResult
import com.polar.sdk.api.model.sleep.SleepWakePhase
import com.polar.sdk.api.model.sleep.SleepWakeState
import com.polar.sdk.impl.utils.PolarSleepUtils
import com.polar.services.datamodels.protobuf.SleepSkinTemperatureResult.PbSleepSkinTemperatureResult
import com.polar.services.datamodels.protobuf.Types.PbDateProto3
import fi.polar.remote.representation.protobuf.SleepanalysisResult
import fi.polar.remote.representation.protobuf.Structures
import fi.polar.remote.representation.protobuf.Types
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbLocalDateTime
import fi.polar.remote.representation.protobuf.Types.PbTime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import protocol.PftpRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class PolarSleepUtilsTest {

    @Test
    fun `sleep read headers use shared file facade planning`() {
        val date = LocalDate.of(2026, 1, 2)

        assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/U/0/20260102/SLEEP/SLEEPRES.BPB",
            PolarSleepUtils.sleepDataReadOperation(date)
        )
        assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/U/0/20260102/NSTRESUL/NSTRCONT.BPB",
            PolarSleepUtils.sleepSkinTemperatureReadOperation(date)
        )
    }

    @Test
    fun `readSleepFromDayDirectory() should return sleep analysis data`() = runTest {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)

        val mockClient = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val sleepOutputStream = ByteArrayOutputStream()
        val skintempOutputStream = ByteArrayOutputStream()
        val expectedSleepPath = "/U/0/${date.format(dateFormatter)}/SLEEP/SLEEPRES.BPB"
        val expectedSkintemperaturePath = "/U/0/${date.format(dateFormatter)}/NSTRESUL/NSTRCONT.BPB"
        val expectedResult = createSleepAnalysisResult()

        val sleepProto = SleepanalysisResult.PbSleepAnalysisResult.newBuilder()
            .addSleepwakePhases(createPbSleepWakePhasesMock())
            .addSleepCycles(createPbSleepCycleMock())
            .addSnoozeTime(createPbLocalDateTime(23, 59, 59, 59, 1, 2, 2525, 60))
            .setSleepStartTime(createPbLocalDateTime(23, 45, 45, 1, 1, 2, 2525, 60))
            .setSleepEndTime(createPbLocalDateTime(7, 5, 7, 6, 2, 2, 2525, 60))
            .setLastModified(Types.PbSystemDateTime.newBuilder()
                .setTime(createPbTime(4, 3, 2, 1))
                .setDate(createPbDate(4, 3, 2525))
                .setTrusted(true)
                .build()
            )
            .setSleepGoalMinutes(420)
            .setAlarmTime(createPbLocalDateTime(7, 0, 0, 0, 2, 2, 2525, 60))
            .setSleepStartOffsetSeconds(1)
            .setSleepEndOffsetSeconds(1)
            .setOriginalSleepRange(Types.PbLocalDateTimeRange.newBuilder()
                .setStartTime(createPbLocalDateTime(23, 59, 59, 59, 1, 2, 2525, 60))
                .setEndTime(createPbLocalDateTime(7, 0, 0, 0, 2, 2, 2525, 60)).build()
            )
            .setBatteryRanOut(false)
            .setRecordingDevice(Structures.PbDeviceId.newBuilder().setDeviceId("C8D9G10F11H12").build())
            .setUserSleepRating(Types.PbSleepUserRating.valueOf("PB_SLEPT_WELL"))
            .setSleepResultDate(Types.PbDate.newBuilder().setDay(1).setMonth(2).setYear(2525).build())
            .setCreatedTimestamp(Types.PbSystemDateTime.newBuilder()
                .setTime(createPbTime(1, 2, 3, 4))
                .setDate(createPbDate(2, 2, 2525))
                .setTrusted(true)
                .build()
            )
            .build()

        sleepProto.writeTo(sleepOutputStream)

        val skinTempProto = PbSleepSkinTemperatureResult.newBuilder()
            .setSleepDate(createPbDateProto3(4, 3, 2525))
            .setSleepSkinTemperatureCelsius(35.123455f)
            .setDeviationFromBaselineCelsius(-0.111111f)
            .build()

        skinTempProto.writeTo(skintempOutputStream)

        coEvery { mockClient.request(any()) } answers { sleepOutputStream } andThen skintempOutputStream

        // Act
        val result = PolarSleepUtils.readSleepDataFromDayDirectory(mockClient, date)

        // Assert
        assertEquals(expectedResult, result)

        coVerify {
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedSleepPath)
                    .build()
                    .toByteArray()
            )
        }
        coVerify {
            mockClient.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedSkintemperaturePath)
                    .build()
                    .toByteArray()
            )
        }
        confirmVerified(mockClient)
    }

    @Test
    fun sleepOffsetGoldenVector_preservesAndroidFieldMapping() = runTest {
        val vector = loadSleepVector("sleep-offset-platform-policy")
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("android")
        val mockClient = mockk<BlePsFtpClient>()
        val sleepOutputStream = ByteArrayOutputStream()
        val skinTempOutputStream = ByteArrayOutputStream()
        val sleepProto = SleepanalysisResult.PbSleepAnalysisResult.newBuilder()
            .setSleepStartTime(createPbLocalDateTime(22, 0, 0, 0, 1, 1, 2024, 120))
            .setSleepEndTime(createPbLocalDateTime(6, 30, 0, 0, 2, 1, 2024, 120))
            .setLastModified(Types.PbSystemDateTime.newBuilder()
                .setTime(createPbTime(6, 31, 0, 0))
                .setDate(createPbDate(2, 1, 2024))
                .setTrusted(true)
                .build())
            .setSleepGoalMinutes(480)
            .setSleepResultDate(Types.PbDate.newBuilder().setDay(2).setMonth(1).setYear(2024).build())
            .setOriginalSleepRange(Types.PbLocalDateTimeRange.newBuilder()
                .setStartTime(createPbLocalDateTime(22, 0, 0, 0, 1, 1, 2024, 120))
                .setEndTime(createPbLocalDateTime(6, 30, 0, 0, 2, 1, 2024, 120))
                .build())
            .setSleepStartOffsetSeconds(input.get("sleepStartOffsetSeconds").asInt)
            .setSleepEndOffsetSeconds(input.get("sleepEndOffsetSeconds").asInt)
            .build()
        sleepProto.writeTo(sleepOutputStream)
        PbSleepSkinTemperatureResult.newBuilder().build().writeTo(skinTempOutputStream)
        coEvery { mockClient.request(any()) } answers { sleepOutputStream } andThen skinTempOutputStream

        val result = PolarSleepUtils.readSleepDataFromDayDirectory(mockClient, LocalDate.of(2024, 1, 2))

        assertEquals(expected.get("sleepStartOffsetSeconds").asInt, result.sleepStartOffsetSeconds)
        assertEquals(expected.get("sleepEndOffsetSeconds").asInt, result.sleepEndOffsetSeconds)
    }

    @Test
    fun sleepTimezoneGoldenVector_preservesAndroidOffsetsAndInstants() = runTest {
        val vector = loadSleepVector("sleep-timezone-offsets")
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("android")
        val mockClient = mockk<BlePsFtpClient>()
        val sleepOutputStream = ByteArrayOutputStream()
        val skinTempOutputStream = ByteArrayOutputStream()
        val sleepProto = SleepanalysisResult.PbSleepAnalysisResult.newBuilder()
            .setSleepStartTime(createPbLocalDateTime(input.getAsJsonObject("sleepStartTime")))
            .setSleepEndTime(createPbLocalDateTime(input.getAsJsonObject("sleepEndTime")))
            .setLastModified(Types.PbSystemDateTime.newBuilder()
                .setTime(createPbTime(6, 31, 0, 0))
                .setDate(createPbDate(1, 4, 2024))
                .setTrusted(true)
                .build())
            .setSleepGoalMinutes(480)
            .setSleepResultDate(Types.PbDate.newBuilder().setDay(1).setMonth(4).setYear(2024).build())
            .build()
        sleepProto.writeTo(sleepOutputStream)
        PbSleepSkinTemperatureResult.newBuilder().build().writeTo(skinTempOutputStream)
        coEvery { mockClient.request(any()) } answers { sleepOutputStream } andThen skinTempOutputStream

        val result = PolarSleepUtils.readSleepDataFromDayDirectory(mockClient, LocalDate.of(2024, 4, 1))

        assertEquals(expected.get("sleepStartOffsetMinutes").asInt * 60, result.sleepStartTime?.offset?.totalSeconds)
        assertEquals(expected.get("sleepEndOffsetMinutes").asInt * 60, result.sleepEndTime?.offset?.totalSeconds)
        assertEquals(OffsetDateTime.parse(expected.get("sleepStartUtcInstant").asString).toInstant(), result.sleepStartTime?.toInstant())
        assertEquals(OffsetDateTime.parse(expected.get("sleepEndUtcInstant").asString).toInstant(), result.sleepEndTime?.toInstant())
    }

    @Test
    fun sleepStageHypnogramGoldenVector_preservesAndroidStageAndCycleOrder() = runTest {
        val vector = loadSleepVector("sleep-stage-hypnogram")
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val mockClient = mockk<BlePsFtpClient>()
        val sleepOutputStream = ByteArrayOutputStream()
        val skinTempOutputStream = ByteArrayOutputStream()
        val sleepProtoBuilder = SleepanalysisResult.PbSleepAnalysisResult.newBuilder()
            .setSleepStartTime(createPbLocalDateTime(22, 0, 0, 0, 1, 1, 2024, 120))
            .setSleepEndTime(createPbLocalDateTime(6, 0, 0, 0, 2, 1, 2024, 120))
            .setLastModified(Types.PbSystemDateTime.newBuilder()
                .setTime(createPbTime(6, 1, 0, 0))
                .setDate(createPbDate(2, 1, 2024))
                .setTrusted(true)
                .build())
            .setSleepGoalMinutes(480)
            .setSleepResultDate(Types.PbDate.newBuilder().setDay(2).setMonth(1).setYear(2024).build())
        input.getAsJsonArray("sleepWakePhases").map { it.asJsonObject }.forEach { phase ->
            sleepProtoBuilder.addSleepwakePhases(SleepanalysisResult.PbSleepWakePhase.newBuilder()
                .setSecondsFromSleepStart(phase.get("secondsFromSleepStart").asInt)
                .setSleepwakeState(SleepanalysisResult.PbSleepWakeState.valueOf(phase.get("protoState").asString))
                .build())
        }
        input.getAsJsonArray("sleepCycles").map { it.asJsonObject }.forEach { cycle ->
            sleepProtoBuilder.addSleepCycles(SleepanalysisResult.PbSleepCycle.newBuilder()
                .setSecondsFromSleepStart(cycle.get("secondsFromSleepStart").asInt)
                .setSleepDepthStart(cycle.get("sleepDepthStart").asFloat)
                .build())
        }
        sleepProtoBuilder.build().writeTo(sleepOutputStream)
        PbSleepSkinTemperatureResult.newBuilder().build().writeTo(skinTempOutputStream)
        coEvery { mockClient.request(any()) } answers { sleepOutputStream } andThen skinTempOutputStream

        val result = PolarSleepUtils.readSleepDataFromDayDirectory(mockClient, LocalDate.of(2024, 1, 2))

        val expectedPhases = expected.getAsJsonArray("sleepWakePhases").map { it.asJsonObject }
        assertEquals(expectedPhases.size, result.sleepWakePhases?.size)
        expectedPhases.forEachIndexed { index, phase ->
            assertEquals(phase.get("secondsFromSleepStart").asInt, result.sleepWakePhases?.get(index)?.secondsFromSleepStart)
            assertEquals(SleepWakeState.valueOf(phase.get("state").asString), result.sleepWakePhases?.get(index)?.state)
        }
        val expectedCycles = expected.getAsJsonArray("sleepCycles").map { it.asJsonObject }
        assertEquals(expectedCycles.size, result.sleepCycles?.size)
        expectedCycles.forEachIndexed { index, cycle ->
            assertEquals(cycle.get("secondsFromSleepStart").asInt, result.sleepCycles?.get(index)?.secondsFromSleepStart)
            assertEquals(cycle.get("sleepDepthStart").asFloat, result.sleepCycles?.get(index)?.sleepDepthStart ?: Float.NaN, 0.0001f)
        }
    }

    @Test
    fun partialNightGoldenVector_preservesAndroidOmittedOptionalPolicy() = runTest {
        val vector = loadSleepVector("partial-night-omitted-optionals")
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val commonExpected = expected.getAsJsonObject("common")
        val androidExpected = expected.getAsJsonObject("android")
        val mockClient = mockk<BlePsFtpClient>()
        val sleepOutputStream = ByteArrayOutputStream()
        val skinTempOutputStream = ByteArrayOutputStream()
        val sleepResultDate = input.getAsJsonObject("sleepResultDate")
        val sleepProto = SleepanalysisResult.PbSleepAnalysisResult.newBuilder()
            .setSleepStartTime(createPbLocalDateTime(23, 0, 0, 0, 5, 5, 2024, 180))
            .setSleepEndTime(createPbLocalDateTime(4, 15, 0, 0, 6, 5, 2024, 180))
            .setLastModified(Types.PbSystemDateTime.newBuilder()
                .setTime(createPbTime(4, 16, 0, 0))
                .setDate(createPbDate(6, 5, 2024))
                .setTrusted(true)
                .build())
            .setSleepGoalMinutes(input.get("sleepGoalMinutes").asInt)
            .setSleepResultDate(Types.PbDate.newBuilder()
                .setDay(sleepResultDate.get("day").asInt)
                .setMonth(sleepResultDate.get("month").asInt)
                .setYear(sleepResultDate.get("year").asInt)
                .build())
            .build()
        sleepProto.writeTo(sleepOutputStream)
        PbSleepSkinTemperatureResult.newBuilder().build().writeTo(skinTempOutputStream)
        coEvery { mockClient.request(any()) } answers { sleepOutputStream } andThen skinTempOutputStream

        val result = PolarSleepUtils.readSleepDataFromDayDirectory(mockClient, LocalDate.of(2024, 5, 6))

        assertEquals(commonExpected.get("sleepGoalMinutes").asInt, result.sleepGoalMinutes)
        assertEquals(commonExpected.get("sleepWakePhaseCount").asInt, result.sleepWakePhases?.size)
        assertEquals(commonExpected.get("snoozeTimeCount").asInt, result.snoozeTime?.size)
        assertNull(result.alarmTime)
        assertEquals(commonExpected.get("sleepStartOffsetSeconds").asInt, result.sleepStartOffsetSeconds)
        assertEquals(commonExpected.get("sleepEndOffsetSeconds").asInt, result.sleepEndOffsetSeconds)
        assertNull(result.userSleepRating)
        assertEquals(commonExpected.get("sleepCycleCount").asInt, result.sleepCycles?.size)
        assertEquals(LocalDate.parse(commonExpected.get("sleepResultDate").asString), result.sleepResultDate)
        assertNull(result.sleepSkinTemperatureResult)
        assertEquals(androidExpected.get("deviceId").asString, result.deviceId)
        assertEquals(androidExpected.get("batteryRanOut").asBoolean, result.batteryRanOut)
        assertNull(result.originalSleepRange)
    }

    @Test
    fun sleepGoldenVectors_followNeutralKmpShape() {
        listOf(
            "partial-night-omitted-optionals",
            "sleep-offset-platform-policy",
            "sleep-stage-hypnogram",
            "sleep-timezone-offsets"
        ).forEach { vectorId ->
            val vector = loadSleepVector(vectorId)
            val id = vector.get("id").asString
            assertTrue(id, vector.has("area"))
            assertTrue(id, vector.has("case"))
            assertTrue(id, vector.has("source"))
            assertTrue(id, vector.has("input"))
            assertTrue(id, vector.has("expected"))
            assertTrue(id, vector.has("platforms"))
            assertTrue(id, vector.getAsJsonObject("input").has("kind"))
            val platforms = vector.getAsJsonObject("platforms")
            assertTrue(id, platforms.get("android").asBoolean)
            assertTrue(id, platforms.get("ios").asBoolean)
            assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    private fun createPbSleepCycleMock(): SleepanalysisResult.PbSleepCycle {
        return SleepanalysisResult.PbSleepCycle.newBuilder()
            .setSleepDepthStart(1.0f)
            .setSecondsFromSleepStart(2)
            .build()
    }

    private fun createPbSleepWakePhasesMock(): SleepanalysisResult.PbSleepWakePhase {
        return SleepanalysisResult.PbSleepWakePhase.newBuilder()
            .setSleepwakeState(SleepanalysisResult.PbSleepWakeState.PB_WAKE)
            .setSecondsFromSleepStart(1).build()
    }

    private fun createPbLocalDateTime(hour: Int, minute: Int, second: Int, millis: Int, day: Int, month: Int, year: Int, zoneOffsetInMinutes: Int): PbLocalDateTime {
        return PbLocalDateTime.newBuilder()
            .setTime(PbTime.newBuilder().setHour(hour).setMinute(minute).setSeconds(second).setMillis(millis).build())
            .setDate(Types.PbDate.newBuilder().setDay(day).setMonth(month).setYear(year).build())
            .setTimeZoneOffset(zoneOffsetInMinutes)
            .setOBSOLETETrusted(true)
            .build()
    }

    private fun createPbLocalDateTime(json: JsonObject): PbLocalDateTime {
        return createPbLocalDateTime(
            json.get("hour").asInt,
            json.get("minute").asInt,
            json.get("second").asInt,
            json.get("millis").asInt,
            json.get("day").asInt,
            json.get("month").asInt,
            json.get("year").asInt,
            json.get("timeZoneOffsetMinutes").asInt
        )
    }

    private fun createPbDate(day: Int, month: Int, year: Int): PbDate {
        return PbDate.newBuilder()
            .setDay(day)
            .setMonth(month)
            .setYear(year)
            .build()
    }

    private fun createPbTime(hour: Int, minute: Int, second: Int, millis: Int): PbTime {
        return PbTime.newBuilder()
            .setHour(hour)
            .setMinute(minute)
            .setSeconds(second)
            .setMillis(millis)
            .build()
    }

    private fun createPbDateProto3(day: Int, month: Int, year: Int): PbDateProto3 {
        return PbDateProto3.newBuilder()
            .setDay(day)
            .setMonth(month)
            .setYear(year)
            .build()
    }

    private fun createSleepAnalysisResult(): PolarSleepAnalysisResult {
        val zoneId = ZoneOffset.ofHoursMinutes(1, 0)
        var snoozeTimes = mutableListOf<ZonedDateTime>()
        snoozeTimes.add(ZonedDateTime.of(LocalDateTime.of(2525, 2, 1, 23, 59, 59, 59 * 1000000), zoneId))

        return PolarSleepAnalysisResult(
            ZonedDateTime.of(LocalDateTime.of(2525, 2, 1, 23, // Sleep start
            45, 45, 1 * 1000000), zoneId),
            ZonedDateTime.of(LocalDateTime.of(2525, 2, 2, 7, // Sleep end
                5, 7, 6 * 1000000), zoneId),
            ZonedDateTime.of(LocalDateTime.of(2525, 3, 4, 4, // Last modified
                3, 2, 1 * 1000000), ZoneOffset.UTC),
            420,
            mockSleepWakePhases(),
            snoozeTimes,
            ZonedDateTime.of(LocalDateTime.of(2525, 2, 2, 7, 0, 0, 0), zoneId),
            1,
            1,
            SleepRating.SLEPT_WELL,
            "C8D9G10F11H12",
            false,
            mockSleepCycles(),
            LocalDate.of(2525, 2, 1),
            mockOriginalSleepRange(), mockSleepSkinTemperatureResult()
        )
    }

    private fun mockSleepWakePhases(): List<SleepWakePhase> {

        var sleepWakePhaseMockList = mutableListOf<SleepWakePhase>()
        var sleepWakePhaseMock = SleepWakePhase(1, SleepWakeState.WAKE)

        sleepWakePhaseMockList.add(sleepWakePhaseMock)

        return  sleepWakePhaseMockList
    }

    private fun mockSleepCycles(): List<SleepCycle> {

        var sleepWakeCycleMockList = mutableListOf<SleepCycle>()
        var sleepWakeCycleMock = SleepCycle(2, 1.0f)
        sleepWakeCycleMockList.add(sleepWakeCycleMock)

        return  sleepWakeCycleMockList
    }

    private fun mockOriginalSleepRange(): OriginalSleepRange {
        return OriginalSleepRange(
            LocalDateTime.of(2525, 2, 1, 23, 59,59,59 * 1000000),
            LocalDateTime.of(2525, 2, 2, 7, 0, 0, 0)
        )
    }

    private fun mockSleepSkinTemperatureResult(): SleepSkinTemperatureResult {
        return SleepSkinTemperatureResult(
            LocalDate.of(2525, 3, 4),
            35.123456f,
            -0.111111f
        )
    }

    private fun loadSleepVector(id: String): JsonObject {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/sleep")
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
}
