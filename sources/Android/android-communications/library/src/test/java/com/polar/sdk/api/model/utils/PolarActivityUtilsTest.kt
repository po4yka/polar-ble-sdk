package com.polar.sdk.api.model.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.activity.PolarActiveTime
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.activity.PolarActivityClass
import com.polar.sdk.api.model.activity.PolarActivityInfo
import com.polar.sdk.api.model.activity.PolarDailyBalanceFeedBack
import com.polar.sdk.api.model.activity.PolarReadinessForSpeedAndStrengthTraining
import com.polar.sdk.impl.utils.CaloriesType
import com.polar.sdk.impl.utils.PolarActivityUtils
import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter
import fi.polar.remote.representation.protobuf.ActivitySamples.PbActivityInfo
import fi.polar.remote.representation.protobuf.ActivitySamples.PbActivitySamples
import fi.polar.remote.representation.protobuf.DailySummary
import fi.polar.remote.representation.protobuf.DailySummary.PbActivityGoalSummary
import fi.polar.remote.representation.protobuf.DailySummary.PbDailySummary
import fi.polar.remote.representation.protobuf.Types
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbDuration
import fi.polar.remote.representation.protobuf.Types.PbLocalDateTime
import fi.polar.remote.representation.protobuf.Types.PbTime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbPFtpEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PolarActivityUtilsTest {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    @Test
    fun `activity read headers use shared file facade planning`() {
        val date = LocalDate.of(2026, 1, 2)

        Assert.assertEquals("/U/0/20260102/ACT/", PolarRuntimePlannerAdapter.activityDirectoryPath("20260102"))
        Assert.assertEquals("/U/0/20260102/DSUM/DSUM.BPB", PolarRuntimePlannerAdapter.dailySummaryPath("20260102"))
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/U/0/20260102/ACT/",
            PolarActivityUtils.activityDirectoryReadOperation(date)
        )
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/U/0/20260102/ACT/ASAMPL0.BPB",
            PolarActivityUtils.activitySampleFileReadOperation("/U/0/20260102/ACT/ASAMPL0.BPB")
        )
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/U/0/20260102/DSUM/DSUM.BPB",
            PolarActivityUtils.dailySummaryReadOperation(date)
        )
    }

    @Test
    fun `readStepsFromDayDirectory() should return sum of step samples`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()

        val mockFileContent1 = ByteArrayOutputStream().apply {
            PbActivitySamples.newBuilder()
                .addStepsSamples(10000)
                .addStepsSamples(5000)
                .addStepsSamples(8000)
                .setMetRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStepsRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStartTime(
                    PbLocalDateTime.newBuilder()
                        .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                        .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                        .setOBSOLETETrusted(true)
                )
                .build()
                .writeTo(this)
        }

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(
                    listOf(
                        PbPFtpEntry.newBuilder().setName("ASAMPL0.BPB").setSize(333L).build(),
                    )
                ).build().writeTo(this)
        }

        val date = LocalDate.now()
        val expectedSteps = 23000
        val expectedDirectoryPath = "/U/0/${date.format(dateFormatter)}/ACT/"
        val expectedFilePath = "/U/0/${date.format(dateFormatter)}/ACT/ASAMPL0.BPB"

        coEvery { client.request(any<ByteArray>()) } answers { mockDirectoryContent } andThen mockFileContent1

        // Act
        val result = PolarActivityUtils.readStepsFromDayDirectory(client, date)

        // Assert
        assert(result == expectedSteps)

        coVerify(atLeast = 1) {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedFilePath)
                    .build()
                    .toByteArray()
            )
        }

        coVerify(atLeast = 1) {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedDirectoryPath)
                    .build()
                    .toByteArray()
            )
        }

        confirmVerified(client)
    }

    @Test
    fun `readStepsFromDayDirectory() should return sum of step samples from multiple sample files`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()

        val mockFileContent1 = ByteArrayOutputStream().apply {
            PbActivitySamples.newBuilder()
                .addStepsSamples(10000).addStepsSamples(5000).addStepsSamples(8000)
                .setMetRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStepsRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStartTime(PbLocalDateTime.newBuilder()
                    .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                    .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                    .setOBSOLETETrusted(true))
                .build().writeTo(this)
        }
        val mockFileContent2 = ByteArrayOutputStream().apply {
            PbActivitySamples.newBuilder()
                .addStepsSamples(1000).addStepsSamples(500).addStepsSamples(800)
                .setMetRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStepsRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStartTime(PbLocalDateTime.newBuilder()
                    .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                    .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                    .setOBSOLETETrusted(true))
                .build().writeTo(this)
        }
        val mockFileContent3 = ByteArrayOutputStream().apply {
            PbActivitySamples.newBuilder()
                .addStepsSamples(100).addStepsSamples(50).addStepsSamples(80)
                .setMetRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStepsRecordingInterval(PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0))
                .setStartTime(PbLocalDateTime.newBuilder()
                    .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                    .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                    .setOBSOLETETrusted(true))
                .build().writeTo(this)
        }
        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(
                    PbPFtpEntry.newBuilder().setName("ASAMPL0.BPB").setSize(333L).build(),
                    PbPFtpEntry.newBuilder().setName("ASAMPL1.BPB").setSize(333L).build(),
                    PbPFtpEntry.newBuilder().setName("ASAMPL2.BPB").setSize(333L).build()
                )).build().writeTo(this)
        }

        val date = LocalDate.now()
        val expectedSteps = 23000 + 2300 + 230
        val expectedDirectoryPath = "/U/0/${date.format(dateFormatter)}/ACT/"

        coEvery { client.request(any<ByteArray>()) } answers { mockDirectoryContent } andThen mockFileContent1 andThen mockFileContent2 andThen mockFileContent3

        // Act
        val result = PolarActivityUtils.readStepsFromDayDirectory(client, date)

        // Assert
        assert(result == expectedSteps)

        coVerify {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedDirectoryPath)
                    .build()
                    .toByteArray()
            )
        }
    }

    @Test
    fun `readStepsFromDayDirectory() should return 0 if activity file not found`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedDirectoryPath = "/U/0/${date.format(dateFormatter)}/ACT/"

        coEvery { client.request(any()) } throws Throwable("No files found for date $date")

        // Act
        val result = PolarActivityUtils.readStepsFromDayDirectory(client, date)

        // Assert
        assert(result == 0)

        coVerify(atLeast = 1) {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedDirectoryPath)
                    .build()
                    .toByteArray()
            )
        }

        confirmVerified(client)
    }

    @Test
    fun `readDistanceFromDayDirectory() should return daily distance`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedDistance = 1234.56f
        val outputStream = ByteArrayOutputStream()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        val proto = PbDailySummary.newBuilder()
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
            .setActivityGoalSummary(PbActivityGoalSummary.newBuilder().setActivityGoal(100f).setAchievedActivity(50f))
            .setDailyBalanceFeedback(Types.PbDailyBalanceFeedback.DB_YOU_COULD_DO_MORE_TRAINING)
            .setReadinessForSpeedAndStrengthTraining(Types.PbReadinessForSpeedAndStrengthTraining.RSST_A1_RECOVERED_READY_FOR_ALL_TRAINING)
            .setSteps(10000)
            .build()
        proto.writeTo(outputStream)

        coEvery { client.request(any()) } returns outputStream

        // Act
        val result = PolarActivityUtils.readDistanceFromDayDirectory(client, date)

        // Assert
        assert(result == expectedDistance)

        coVerifyOrder {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedPath)
                    .build()
                    .toByteArray()
            )
        }
        confirmVerified(client)
    }

    @Test
    fun `readDistanceFromDayDirectory() should return 0 if activity file not found`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        coEvery { client.request(any()) } throws Throwable("File not found")

        // Act
        val result = PolarActivityUtils.readDistanceFromDayDirectory(client, date)

        // Assert
        assert(result == 0f)

        coVerifyOrder {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedPath)
                    .build()
                    .toByteArray()
            )
        }
        confirmVerified(client)
    }

    @Test
    fun `readSpecificCaloriesFromDayDirectory() should return specific calories value`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedCalories = 500
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        val dailySummaryBuilder = DailySummary.PbDailySummary.newBuilder()
        val calories = arrayOf(Pair(CaloriesType.ACTIVITY, 500), Pair(CaloriesType.BMR, 500), Pair(CaloriesType.TRAINING, 500))
        for (caloriesType in calories) {
            when (caloriesType.first) {
                CaloriesType.ACTIVITY -> dailySummaryBuilder.activityCalories = expectedCalories
                CaloriesType.TRAINING -> dailySummaryBuilder.trainingCalories = expectedCalories
                CaloriesType.BMR -> dailySummaryBuilder.bmrCalories = expectedCalories
            }
        }
        dailySummaryBuilder
            .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
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
            .setActivityGoalSummary(PbActivityGoalSummary.newBuilder().setActivityGoal(100f).setAchievedActivity(50f))
            .setDailyBalanceFeedback(Types.PbDailyBalanceFeedback.DB_YOU_COULD_DO_MORE_TRAINING)
            .setReadinessForSpeedAndStrengthTraining(Types.PbReadinessForSpeedAndStrengthTraining.RSST_A1_RECOVERED_READY_FOR_ALL_TRAINING)
            .setSteps(10000)
        val outputStream = ByteArrayOutputStream()
        dailySummaryBuilder.build().writeTo(outputStream)

        coEvery { client.request(any()) } returns outputStream

        // Act
        val result1 = PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, calories[0].first)
        val result2 = PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, calories[1].first)
        val result3 = PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, calories[2].first)

        // Assert
        assert(result1 == expectedCalories)
        assert(result2 == expectedCalories)
        assert(result3 == expectedCalories)

        coVerify(exactly = 3) {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedPath)
                    .build()
                    .toByteArray()
            )
        }
    }

    @Test
    fun `readSpecificCaloriesFromDayDirectory() should return 0 if activity file not found`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val caloriesType = CaloriesType.ACTIVITY
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        coEvery { client.request(any()) } throws Throwable("File not found")

        // Act
        val result = PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, caloriesType)

        // Assert
        assert(result == 0)

        coVerifyOrder {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedPath)
                    .build()
                    .toByteArray()
            )
        }
        confirmVerified(client)
    }

    @Test
    fun `readActiveTimeFromDayDirectory() should return PolarActiveTimeData`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        val proto = PbDailySummary.newBuilder()
            .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
            .setActivityDistance(1234.56f)
            .setActivityCalories(100)
            .setBmrCalories(2000)
            .setTrainingCalories(500)
            .setActivityClassTimes(
                DailySummary.PbActivityClassTimes.newBuilder()
                    .setTimeNonWear(Types.PbDuration.newBuilder().setHours(1).setMinutes(30).setSeconds(0).setMillis(0).build())
                    .setTimeSleep(Types.PbDuration.newBuilder().setHours(2).setMinutes(15).setSeconds(0).setMillis(0).build())
                    .setTimeSedentary(Types.PbDuration.newBuilder().setHours(0).setMinutes(45).setSeconds(30).setMillis(0).build())
                    .setTimeLightActivity(Types.PbDuration.newBuilder().setHours(3).setMinutes(0).setSeconds(0).setMillis(0).build())
                    .setTimeContinuousModerate(Types.PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(500).build())
                    .setTimeIntermittentModerate(Types.PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(30).setMillis(0).build())
                    .setTimeContinuousVigorous(Types.PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(0).setMillis(0).build())
                    .setTimeIntermittentVigorous(Types.PbDuration.newBuilder().setHours(0).setMinutes(0).setSeconds(45).setMillis(0).build())
                    .build()
            )
            .setActivityGoalSummary(PbActivityGoalSummary.newBuilder().setActivityGoal(100f).setAchievedActivity(50f))
            .setDailyBalanceFeedback(Types.PbDailyBalanceFeedback.DB_YOU_COULD_DO_MORE_TRAINING)
            .setReadinessForSpeedAndStrengthTraining(Types.PbReadinessForSpeedAndStrengthTraining.RSST_A1_RECOVERED_READY_FOR_ALL_TRAINING)
            .setSteps(10000)
            .build()

        val outputStream = ByteArrayOutputStream()
        proto.writeTo(outputStream)

        coEvery { client.request(any()) } returns outputStream

        // Act
        val result = PolarActivityUtils.readActiveTimeFromDayDirectory(client, date)

        // Assert
        assert(result == PolarActiveTimeData(
            date = date,
            timeNonWear = PolarActiveTime(1, 30, 0, 0),
            timeSleep = PolarActiveTime(2, 15, 0, 0),
            timeSedentary = PolarActiveTime(0, 45, 30, 0),
            timeLightActivity = PolarActiveTime(3, 0, 0, 0),
            timeContinuousModerateActivity = PolarActiveTime(0, 0, 0, 500),
            timeIntermittentModerateActivity = PolarActiveTime(0, 0, 30, 0),
            timeContinuousVigorousActivity = PolarActiveTime(0, 0, 0, 0),
            timeIntermittentVigorousActivity = PolarActiveTime(0, 0, 45, 0)
        ))

        coVerifyOrder {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedPath)
                    .build()
                    .toByteArray()
            )
        }
        confirmVerified(client)
    }

    @Test
    fun `readActiveTimeFromDayDirectory() should return default PolarActiveTimeData if error occurs`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        coEvery { client.request(any()) } throws Throwable("File not found")

        // Act
        val result = PolarActivityUtils.readActiveTimeFromDayDirectory(client, date)

        // Assert
        assert(result == PolarActiveTimeData(
            date = date,
            timeNonWear = PolarActiveTime(0, 0, 0, 0),
            timeSleep = PolarActiveTime(0, 0, 0, 0),
            timeSedentary = PolarActiveTime(0, 0, 0, 0),
            timeLightActivity = PolarActiveTime(0, 0, 0, 0),
            timeContinuousModerateActivity = PolarActiveTime(0, 0, 0, 0),
            timeIntermittentModerateActivity = PolarActiveTime(0, 0, 0, 0),
            timeContinuousVigorousActivity = PolarActiveTime(0, 0, 0, 0),
            timeIntermittentVigorousActivity = PolarActiveTime(0, 0, 0, 0)
        ))

        coVerifyOrder {
            client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                    .setPath(expectedPath)
                    .build()
                    .toByteArray()
            )
        }
        confirmVerified(client)
    }

    @Test
    fun `readActivitySamplesFromDayDirectory() should return all activity samples`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()

        val mockFileContent1 = ByteArrayOutputStream().apply {
            PbActivitySamples.newBuilder()
                .addStepsSamples(1).addStepsSamples(2).addStepsSamples(3)
                .addMetSamples(1.0f).addMetSamples(2.0f).addMetSamples(3.0f)
                .setMetRecordingInterval(PbDuration.newBuilder().setSeconds(30).build())
                .setStepsRecordingInterval(PbDuration.newBuilder().setSeconds(60).build())
                .addActivityInfo(0, PbActivityInfo.newBuilder()
                    .setValue(PbActivityInfo.ActivityClass.valueOf("LIGHT"))
                    .setFactor(1f)
                    .setTimeStamp(PbLocalDateTime.newBuilder()
                        .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                        .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                        .setOBSOLETETrusted(true))
                    .build())
                .setStartTime(PbLocalDateTime.newBuilder()
                    .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
                    .setTime(PbTime.newBuilder().setHour(8).setMinute(0).setSeconds(0))
                    .setOBSOLETETrusted(true))
                .build().writeTo(this)
        }

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(
                    PbPFtpEntry.newBuilder().setName("ASAMPL0.BPB").setSize(333L).build(),
                    PbPFtpEntry.newBuilder().setName("ASAMPL1.BPB").setSize(333L).build(),
                    PbPFtpEntry.newBuilder().setName("ASAMPL2.BPB").setSize(333L).build(),
                )).build().writeTo(this)
        }

        val date = LocalDate.now()
        val expectedDirectoryPath = "/U/0/${date.format(dateFormatter)}/ACT/"
        val expectedFilePath1 = "/U/0/${date.format(dateFormatter)}/ACT/ASAMPL0.BPB"
        val expectedFilePath2 = "/U/0/${date.format(dateFormatter)}/ACT/ASAMPL1.BPB"
        val expectedFilePath3 = "/U/0/${date.format(dateFormatter)}/ACT/ASAMPL2.BPB"

        val expectedMetSamples = listOf(1.0f, 2.0f, 3.0f)
        val expectedStepSamples = listOf(1, 2, 3)
        val expectedActivityInfo = PolarActivityInfo(PolarActivityClass.LIGHT, LocalDateTime.of(2525, 1, 1, 8, 0, 0), 1.0f)
        val expectedStepRecordingInterval = 60
        val expectedMetRecordingInterval = 30
        val expectedStartTime = LocalDateTime.of(2525, 1, 1, 8, 0, 0)

        coEvery { client.request(any<ByteArray>()) } answers { mockDirectoryContent } andThen mockFileContent1

        // Act
        val result = PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client, date)

        // Assert
        assert(result.polarActivitySamplesDataList?.size == 3)

        for (activityInfo in result.polarActivitySamplesDataList!!) {
            assert(activityInfo.activityInfoList[0].activityClass == expectedActivityInfo.activityClass)
            assert(activityInfo.activityInfoList[0].factor == expectedActivityInfo.factor)
            assert(activityInfo.activityInfoList[0].timeStamp == expectedActivityInfo.timeStamp)
            assert(activityInfo.metSamples == expectedMetSamples)
            assert(activityInfo.stepSamples == expectedStepSamples)
            assert(activityInfo.stepRecordingInterval == expectedStepRecordingInterval)
            assert(activityInfo.metRecordingInterval == expectedMetRecordingInterval)
            assert(activityInfo.startTime == expectedStartTime)
        }

        coVerify(exactly = 1) {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedFilePath1).build().toByteArray())
        }
        coVerify(exactly = 1) {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedFilePath2).build().toByteArray())
        }
        coVerify(exactly = 1) {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedFilePath3).build().toByteArray())
        }
        coVerify(exactly = 1) {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedDirectoryPath).build().toByteArray())
        }
        confirmVerified(client)
    }

    @Test
    fun `readActivitySamplesFromDayDirectory() should return default PolarActivitySamplesDayData if error occurs`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/ACT/"

        coEvery { client.request(any()) } throws Throwable("File not found")

        // Act
        val result = PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client, date)

        // Assert
        assert(result.polarActivitySamplesDataList == null || result.polarActivitySamplesDataList!!.isEmpty())

        coVerifyOrder {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedPath).build().toByteArray())
        }
        confirmVerified(client)
    }

    @Test
    fun `readDailySummaryDataFromDayDirectory() should return all daily summary data`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()

        val proto = PbDailySummary.newBuilder()
            .setDate(PbDate.newBuilder().setDay(1).setMonth(1).setYear(2525))
            .setActivityDistance(2000.01f)
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
                    .build()
            )
            .setActivityGoalSummary(PbActivityGoalSummary.newBuilder()
                .setActivityGoal(100f)
                .setAchievedActivity(50f)
                .setTimeToGoUp(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                .setTimeToGoJog(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0))
                .setTimeToGoWalk(PbDuration.newBuilder().setHours(1).setMinutes(0).setSeconds(0).setMillis(0).setMinutes(0))
            )
            .setDailyBalanceFeedback(Types.PbDailyBalanceFeedback.DB_YOU_COULD_DO_MORE_TRAINING)
            .setReadinessForSpeedAndStrengthTraining(Types.PbReadinessForSpeedAndStrengthTraining.RSST_A1_RECOVERED_READY_FOR_ALL_TRAINING)
            .setSteps(10000)
            .build()

        val outputStream = ByteArrayOutputStream()
        proto.writeTo(outputStream)

        coEvery { client.request(any()) } returns outputStream

        val date = LocalDate.now()
        val expectedFilePath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"
        val expectedDate = LocalDate.of(2525, 1, 1)
        val expectedDistance = 2000.01f
        val expectedSteps = 10000
        val expectedCalories = 2600
        val expectedActiveTime = 24
        val expectedReadiness = PolarReadinessForSpeedAndStrengthTraining.RECOVERED_READY_FOR_ALL_TRAINING
        val expectedDBFeedBack = PolarDailyBalanceFeedBack.YOU_COULD_DO_MORE_TRAINING
        val expectedActivityGoal = 100f
        val expectedAchievedGoal = 50f
        val expectedTimeToGo = PolarActiveTime(1)

        // Act
        val result = PolarActivityUtils.readDailySummaryDataFromDayDirectory(client, date)

        // Assert
        assert(result.activityGoalSummary?.activityGoal == expectedActivityGoal)
        assert(result.activityGoalSummary?.achievedActivity == expectedAchievedGoal)
        assert(result.activityDistance == expectedDistance)
        assert(result.activityGoalSummary?.timeToGoUp == expectedTimeToGo)
        assert(result.activityGoalSummary?.timeToGoJog == expectedTimeToGo)
        assert(result.activityGoalSummary?.timeToGoWalk == expectedTimeToGo)
        assert(result.date == expectedDate)
        assert(result.steps == expectedSteps)
        assert((result.activityCalories!! + result.bmrCalories!! + result.trainingCalories!!) == expectedCalories)
        val activityClassTimes = requireNotNull(result.activityClassTimes)
        assert((activityClassTimes.timeLightActivity.hours +
                activityClassTimes.timeSleep.hours +
                activityClassTimes.timeSedentary.hours +
                activityClassTimes.timeContinuousModerateActivity.hours +
                activityClassTimes.timeContinuousVigorousActivity.hours +
                activityClassTimes.timeIntermittentModerateActivity.hours +
                activityClassTimes.timeIntermittentVigorousActivity.hours +
                activityClassTimes.timeNonWear.hours) == expectedActiveTime)
        assert(result.readinessForSpeedAndStrengthTraining == expectedReadiness)
        assert(result.dailyBalanceFeedback == expectedDBFeedBack)

        coVerify(exactly = 1) {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedFilePath).build().toByteArray())
        }
        confirmVerified(client)
    }

    @Test
    fun `readDailySummaryDataFromDayDirectory() should return default PolarDailySummaryData if error occurs`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/DSUM/DSUM.BPB"

        coEvery { client.request(any()) } throws Throwable("File not found")

        // Act
        val result = PolarActivityUtils.readDailySummaryDataFromDayDirectory(client, date)

        // Assert — returns default/empty DailySummaryData without throwing
        assert(result.activityDistance == null || result.activityDistance == 0f)

        coVerifyOrder {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedPath).build().toByteArray())
        }
        confirmVerified(client)
    }

    @Test
    fun `daily summary golden vectors map proto to public model`() = runTest {
        val vector = loadDailySummaryVector("full-summary.json")
        val input = vector.getAsJsonObject("input")
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.parse(input.get("requestDate").asString)
        val outputStream = ByteArrayOutputStream().apply {
            buildDailySummaryProto(input.getAsJsonObject("proto")).writeTo(this)
        }
        coEvery { client.request(any()) } returns outputStream

        val result = PolarActivityUtils.readDailySummaryDataFromDayDirectory(client, date)

        assertDailySummary(vector.get("id").asString, result, vector.getAsJsonObject("expected"))
        coVerify(exactly = 1) {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(input.get("expectedPath").asString).build().toByteArray())
        }
        confirmVerified(client)
    }

    @Test
    fun `daily summary golden vectors cover convenience readers`() = runTest {
        val vector = loadDailySummaryVector("full-summary.json")
        val input = vector.getAsJsonObject("input")
        val proto = buildDailySummaryProto(input.getAsJsonObject("proto"))
        val date = LocalDate.parse(input.get("requestDate").asString)
        val expected = vector.getAsJsonObject("expected")
        val expectedPath = input.get("expectedPath").asString

        val distanceClient = mockDailySummaryClient(proto)
        val distance = PolarActivityUtils.readDistanceFromDayDirectory(distanceClient, date)
        assert(distance == expected.get("activityDistance").asFloat) { "${vector.get("id").asString} distance" }
        verifyDailySummaryPath(distanceClient, expectedPath)

        val activityCaloriesClient = mockDailySummaryClient(proto)
        val activityCalories = PolarActivityUtils.readSpecificCaloriesFromDayDirectory(activityCaloriesClient, date, CaloriesType.ACTIVITY)
        assert(activityCalories == expected.get("activityCalories").asInt) { "${vector.get("id").asString} activityCalories" }
        verifyDailySummaryPath(activityCaloriesClient, expectedPath)

        val trainingCaloriesClient = mockDailySummaryClient(proto)
        val trainingCalories = PolarActivityUtils.readSpecificCaloriesFromDayDirectory(trainingCaloriesClient, date, CaloriesType.TRAINING)
        assert(trainingCalories == expected.get("trainingCalories").asInt) { "${vector.get("id").asString} trainingCalories" }
        verifyDailySummaryPath(trainingCaloriesClient, expectedPath)

        val bmrCaloriesClient = mockDailySummaryClient(proto)
        val bmrCalories = PolarActivityUtils.readSpecificCaloriesFromDayDirectory(bmrCaloriesClient, date, CaloriesType.BMR)
        assert(bmrCalories == expected.get("bmrCalories").asInt) { "${vector.get("id").asString} bmrCalories" }
        verifyDailySummaryPath(bmrCaloriesClient, expectedPath)

        val activeTimeClient = mockDailySummaryClient(proto)
        val activeTime = PolarActivityUtils.readActiveTimeFromDayDirectory(activeTimeClient, date)
        assert(activeTime.date == date) { "${vector.get("id").asString} activeTime date" }
        val times = expected.getAsJsonObject("activityClassTimes")
        assertActiveTime("${vector.get("id").asString} timeNonWear", activeTime.timeNonWear, times.getAsJsonObject("timeNonWear"))
        assertActiveTime("${vector.get("id").asString} timeSleep", activeTime.timeSleep, times.getAsJsonObject("timeSleep"))
        assertActiveTime("${vector.get("id").asString} timeSedentary", activeTime.timeSedentary, times.getAsJsonObject("timeSedentary"))
        assertActiveTime("${vector.get("id").asString} timeLightActivity", activeTime.timeLightActivity, times.getAsJsonObject("timeLightActivity"))
        assertActiveTime("${vector.get("id").asString} timeContinuousModerate", activeTime.timeContinuousModerateActivity, times.getAsJsonObject("timeContinuousModerate"))
        assertActiveTime("${vector.get("id").asString} timeIntermittentModerate", activeTime.timeIntermittentModerateActivity, times.getAsJsonObject("timeIntermittentModerate"))
        assertActiveTime("${vector.get("id").asString} timeContinuousVigorous", activeTime.timeContinuousVigorousActivity, times.getAsJsonObject("timeContinuousVigorous"))
        assertActiveTime("${vector.get("id").asString} timeIntermittentVigorous", activeTime.timeIntermittentVigorousActivity, times.getAsJsonObject("timeIntermittentVigorous"))
        verifyDailySummaryPath(activeTimeClient, expectedPath)
    }

    @Test
    fun `daily summary golden vectors follow neutral KMP vector shape`() {
        val vector = loadDailySummaryVector("full-summary.json")
        val id = vector.get("id").asString
        Assert.assertTrue(id, vector.has("area"))
        Assert.assertTrue(id, vector.has("case"))
        Assert.assertTrue(id, vector.has("source"))
        Assert.assertTrue(id, vector.has("input"))
        Assert.assertTrue(id, vector.has("expected"))
        Assert.assertTrue(id, vector.has("platforms"))
        val input = vector.getAsJsonObject("input")
        Assert.assertTrue(id, input.has("proto"))
        Assert.assertTrue(id, input.has("requestDate"))
        Assert.assertTrue(id, input.has("expectedPath"))
        val platforms = vector.getAsJsonObject("platforms")
        Assert.assertTrue(id, platforms.get("android").asBoolean)
        Assert.assertTrue(id, platforms.get("ios").asBoolean)
        Assert.assertTrue(id, platforms.get("common").asBoolean)
    }

    @Test
    fun `activity sample golden vectors cover step aggregation and sample mapping`() = runTest {
        val vector = loadActivitySamplesVector("two-files-step-aggregation.json")
        val input = vector.getAsJsonObject("input")
        val date = LocalDate.parse(input.get("requestDate").asString)

        val stepsClient = mockActivitySamplesClient(vector)
        val steps = PolarActivityUtils.readStepsFromDayDirectory(stepsClient, date)
        assert(steps == vector.getAsJsonObject("expected").get("totalSteps").asInt) { "${vector.get("id").asString} totalSteps" }
        verifyActivitySampleRequests(stepsClient, vector)

        val samplesClient = mockActivitySamplesClient(vector)
        val samples = PolarActivityUtils.readActivitySamplesDataFromDayDirectory(samplesClient, date)
        assertActivitySamplesData(vector, samples)
        verifyActivitySampleRequests(samplesClient, vector)
    }

    @Test
    fun `activity sample golden vectors preserve malformed file policy`() = runTest {
        val vector = loadActivitySamplesVector("malformed-sample-file-platform-policy.json")
        val input = vector.getAsJsonObject("input")
        val date = LocalDate.parse(input.get("requestDate").asString)

        val stepsClient = mockActivitySamplesClient(vector)
        val steps = PolarActivityUtils.readStepsFromDayDirectory(stepsClient, date)
        assert(steps == vector.getAsJsonObject("expected").get("steps").asInt) { "${vector.get("id").asString} steps" }
        verifyActivitySampleRequests(stepsClient, vector)

        val samplesClient = mockActivitySamplesClient(vector)
        val samples = PolarActivityUtils.readActivitySamplesDataFromDayDirectory(samplesClient, date)
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("androidSamples")
        assert(samples.polarActivitySamplesDataList.orEmpty().size == expected.get("count").asInt) { "${vector.get("id").asString} android sample count" }
        verifyActivitySampleRequests(samplesClient, vector)
    }

    @Test
    fun `activity sample golden vectors follow neutral KMP vector shape`() {
        listOf("two-files-step-aggregation.json", "malformed-sample-file-platform-policy.json").forEach { fileName ->
            val vector = loadActivitySamplesVector(fileName)
            val id = vector.get("id").asString
            Assert.assertTrue(id, vector.has("area"))
            Assert.assertTrue(id, vector.has("case"))
            Assert.assertTrue(id, vector.has("source"))
            Assert.assertTrue(id, vector.has("input"))
            Assert.assertTrue(id, vector.has("expected"))
            Assert.assertTrue(id, vector.has("platforms"))
            val input = vector.getAsJsonObject("input")
            Assert.assertTrue(id, input.has("requestDate"))
            Assert.assertTrue(id, input.has("directoryPath"))
            Assert.assertTrue(id, input.has("directoryEntries"))
            Assert.assertTrue(id, input.has("files"))
            val platforms = vector.getAsJsonObject("platforms")
            Assert.assertTrue(id, platforms.get("android").asBoolean)
            Assert.assertTrue(id, platforms.get("ios").asBoolean)
            Assert.assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    @Test
    fun `activity summary readiness manifest is pinned before model migration`() {
        val readiness = loadActivitySamplesVector("activity-summary-readiness.json")
        val input = readiness.getAsJsonObject("input")
        val expected = readiness.getAsJsonObject("expected")
        val consumerTests = readiness.getAsJsonObject("consumerTests")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        Assert.assertEquals("activity-summary-readiness", readiness.get("id").asString)
        Assert.assertEquals("activitySummaryReadiness", input.get("kind").asString)
        Assert.assertEquals(
            listOf(
                "sdk/activity-samples/two-files-step-aggregation.json",
                "sdk/activity-samples/malformed-sample-file-platform-policy.json",
                "sdk/automatic-samples/hr-all-trigger-types.json",
                "sdk/automatic-samples/ppi-deltas-statuses.json",
                "sdk/daily-summary/full-summary.json"
            ),
            policyVectorPaths
        )
        val expectedFamilies = listOf(
            "activity-file-request-paths",
            "activity-step-aggregation",
            "activity-interval-projection",
            "activity-info-projection",
            "malformed-activity-sample-platform-policy",
            "automatic-hr-trigger-mapping",
            "automatic-hr-array-preservation",
            "automatic-ppi-delta-decompression",
            "automatic-ppi-status-bit-mapping",
            "daily-summary-request-path",
            "daily-summary-scalar-projection",
            "daily-summary-duration-projection",
            "platform-activity-vector-reference-gate",
            "compile-verification-gate"
        )
        Assert.assertEquals(expectedFamilies, requiredFamilies)
        Assert.assertEquals(expectedFamilies, coveredFamilies)
        Assert.assertEquals(
            "Activity, automatic-sample, and daily-summary migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS activity/automatic/daily tests continue to reference the same vectors, activity request paths, aggregation, intervals, activity-info projection, malformed activity-sample behavior, automatic HR trigger and heart-rate arrays, PPI delta/status decoding, daily-summary path/scalar/duration projection, and compile verification remain explicit before production model mapping moves.",
            expected.get("commonDecision").asString
        )
        Assert.assertEquals(
            listOf(
                "com.polar.sdk.api.model.utils.PolarActivityUtilsTest",
                "com.polar.sdk.api.model.utils.PolarAutomaticSamplesUtilsTest"
            ),
            consumerTests.getAsJsonArray("android").map { it.asString }
        )
        Assert.assertEquals(
            listOf(
                "PolarActivityUtilsTest",
                "PolarAutomaticSamplesUnitTest"
            ),
            consumerTests.getAsJsonArray("ios").map { it.asString }
        )
        Assert.assertEquals(
            listOf("com.polar.sharedtest.ActivitySummaryCommonPolicyTest"),
            consumerTests.getAsJsonArray("commonPrototype").map { it.asString }
        )
    }

    private fun buildDailySummaryProto(proto: JsonObject): PbDailySummary {
        return PbDailySummary.newBuilder()
            .setDate(buildPbDate(proto.getAsJsonObject("date")))
            .setActivityCalories(proto.get("activityCalories").asInt)
            .setTrainingCalories(proto.get("trainingCalories").asInt)
            .setBmrCalories(proto.get("bmrCalories").asInt)
            .setSteps(proto.get("steps").asInt)
            .setActivityDistance(proto.get("activityDistance").asFloat)
            .setDailyBalanceFeedback(Types.PbDailyBalanceFeedback.valueOf(proto.get("dailyBalanceFeedback").asString))
            .setReadinessForSpeedAndStrengthTraining(Types.PbReadinessForSpeedAndStrengthTraining.valueOf(proto.get("readinessForSpeedAndStrengthTraining").asString))
            .setActivityGoalSummary(buildActivityGoalSummary(proto.getAsJsonObject("activityGoalSummary")))
            .setActivityClassTimes(buildActivityClassTimes(proto.getAsJsonObject("activityClassTimes")))
            .build()
    }

    private fun buildActivityGoalSummary(fields: JsonObject): PbActivityGoalSummary {
        return PbActivityGoalSummary.newBuilder()
            .setActivityGoal(fields.get("activityGoal").asFloat)
            .setAchievedActivity(fields.get("achievedActivity").asFloat)
            .setTimeToGoUp(buildPbDuration(fields.getAsJsonObject("timeToGoUp")))
            .setTimeToGoWalk(buildPbDuration(fields.getAsJsonObject("timeToGoWalk")))
            .setTimeToGoJog(buildPbDuration(fields.getAsJsonObject("timeToGoJog")))
            .build()
    }

    private fun buildActivityClassTimes(fields: JsonObject): DailySummary.PbActivityClassTimes {
        return DailySummary.PbActivityClassTimes.newBuilder()
            .setTimeNonWear(buildPbDuration(fields.getAsJsonObject("timeNonWear")))
            .setTimeSleep(buildPbDuration(fields.getAsJsonObject("timeSleep")))
            .setTimeSedentary(buildPbDuration(fields.getAsJsonObject("timeSedentary")))
            .setTimeLightActivity(buildPbDuration(fields.getAsJsonObject("timeLightActivity")))
            .setTimeContinuousModerate(buildPbDuration(fields.getAsJsonObject("timeContinuousModerate")))
            .setTimeIntermittentModerate(buildPbDuration(fields.getAsJsonObject("timeIntermittentModerate")))
            .setTimeContinuousVigorous(buildPbDuration(fields.getAsJsonObject("timeContinuousVigorous")))
            .setTimeIntermittentVigorous(buildPbDuration(fields.getAsJsonObject("timeIntermittentVigorous")))
            .build()
    }

    private fun buildPbDate(fields: JsonObject): PbDate {
        return PbDate.newBuilder()
            .setYear(fields.get("year").asInt)
            .setMonth(fields.get("month").asInt)
            .setDay(fields.get("day").asInt)
            .build()
    }

    private fun buildPbDuration(fields: JsonObject): PbDuration {
        return PbDuration.newBuilder()
            .setHours(fields.get("hours").asInt)
            .setMinutes(fields.get("minutes").asInt)
            .setSeconds(fields.get("seconds").asInt)
            .setMillis(fields.get("millis").asInt)
            .build()
    }

    private fun assertDailySummary(caseId: String, actual: com.polar.sdk.api.model.activity.PolarDailySummaryData, expected: JsonObject) {
        assert(actual.date == LocalDate.parse(expected.get("date").asString)) { "$caseId date" }
        assert(actual.activityCalories == expected.get("activityCalories").asInt) { "$caseId activityCalories" }
        assert(actual.trainingCalories == expected.get("trainingCalories").asInt) { "$caseId trainingCalories" }
        assert(actual.bmrCalories == expected.get("bmrCalories").asInt) { "$caseId bmrCalories" }
        assert(actual.steps == expected.get("steps").asInt) { "$caseId steps" }
        assert(actual.activityDistance == expected.get("activityDistance").asFloat) { "$caseId activityDistance" }
        assert(actual.dailyBalanceFeedback?.name == expected.get("dailyBalanceFeedback").asString) { "$caseId dailyBalanceFeedback" }
        assert(actual.readinessForSpeedAndStrengthTraining?.name == expected.get("readinessForSpeedAndStrengthTraining").asString) { "$caseId readiness" }
        val goal = expected.getAsJsonObject("activityGoalSummary")
        assert(actual.activityGoalSummary?.activityGoal == goal.get("activityGoal").asFloat) { "$caseId activityGoal" }
        assert(actual.activityGoalSummary?.achievedActivity == goal.get("achievedActivity").asFloat) { "$caseId achievedActivity" }
        assertActiveTime("$caseId timeToGoUp", actual.activityGoalSummary?.timeToGoUp, goal.getAsJsonObject("timeToGoUp"))
        assertActiveTime("$caseId timeToGoWalk", actual.activityGoalSummary?.timeToGoWalk, goal.getAsJsonObject("timeToGoWalk"))
        assertActiveTime("$caseId timeToGoJog", actual.activityGoalSummary?.timeToGoJog, goal.getAsJsonObject("timeToGoJog"))
        val times = expected.getAsJsonObject("activityClassTimes")
        assertActiveTime("$caseId timeNonWear", actual.activityClassTimes?.timeNonWear, times.getAsJsonObject("timeNonWear"))
        assertActiveTime("$caseId timeSleep", actual.activityClassTimes?.timeSleep, times.getAsJsonObject("timeSleep"))
        assertActiveTime("$caseId timeSedentary", actual.activityClassTimes?.timeSedentary, times.getAsJsonObject("timeSedentary"))
        assertActiveTime("$caseId timeLightActivity", actual.activityClassTimes?.timeLightActivity, times.getAsJsonObject("timeLightActivity"))
        assertActiveTime("$caseId timeContinuousModerate", actual.activityClassTimes?.timeContinuousModerateActivity, times.getAsJsonObject("timeContinuousModerate"))
        assertActiveTime("$caseId timeIntermittentModerate", actual.activityClassTimes?.timeIntermittentModerateActivity, times.getAsJsonObject("timeIntermittentModerate"))
        assertActiveTime("$caseId timeContinuousVigorous", actual.activityClassTimes?.timeContinuousVigorousActivity, times.getAsJsonObject("timeContinuousVigorous"))
        assertActiveTime("$caseId timeIntermittentVigorous", actual.activityClassTimes?.timeIntermittentVigorousActivity, times.getAsJsonObject("timeIntermittentVigorous"))
    }

    private fun assertActiveTime(caseId: String, actual: PolarActiveTime?, expected: JsonObject) {
        assert(actual == PolarActiveTime(expected.get("hours").asInt, expected.get("minutes").asInt, expected.get("seconds").asInt, expected.get("millis").asInt)) { caseId }
    }

    private fun mockDailySummaryClient(proto: PbDailySummary): BlePsFtpClient {
        val client = mockk<BlePsFtpClient>()
        coEvery { client.request(any()) } answers {
            ByteArrayOutputStream().apply { proto.writeTo(this) }
        }
        return client
    }

    private fun verifyDailySummaryPath(client: BlePsFtpClient, expectedPath: String) {
        coVerify(exactly = 1) {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(expectedPath).build().toByteArray())
        }
        confirmVerified(client)
    }

    private fun mockActivitySamplesClient(vector: JsonObject): BlePsFtpClient {
        val input = vector.getAsJsonObject("input")
        val responses = mutableListOf(ByteArrayOutputStream().apply { buildActivityDirectory(vector).writeTo(this) })
        input.getAsJsonArray("files").forEach { file ->
            val fields = file.asJsonObject
            responses.add(ByteArrayOutputStream().apply {
                if (fields.has("responseHex")) {
                    write(fields.get("responseHex").asString.hexToByteArray())
                } else {
                    buildActivitySamplesProto(fields.getAsJsonObject("proto")).writeTo(this)
                }
            })
        }
        val client = mockk<BlePsFtpClient>()
        coEvery { client.request(any()) } answers { responses.removeAt(0) }
        return client
    }

    private fun buildActivityDirectory(vector: JsonObject): PbPFtpDirectory {
        val input = vector.getAsJsonObject("input")
        val builder = PbPFtpDirectory.newBuilder()
        input.getAsJsonArray("directoryEntries").forEach { entry ->
            val fields = entry.asJsonObject
            builder.addEntries(PbPFtpEntry.newBuilder().setName(fields.get("name").asString).setSize(fields.get("size").asLong).build())
        }
        return builder.build()
    }

    private fun buildActivitySamplesProto(fields: JsonObject): PbActivitySamples {
        val builder = PbActivitySamples.newBuilder()
            .setStartTime(buildPbLocalDateTime(fields.getAsJsonObject("startTime")))
            .setMetRecordingInterval(buildPbDuration(fields.getAsJsonObject("metRecordingInterval")))
            .setStepsRecordingInterval(buildPbDuration(fields.getAsJsonObject("stepsRecordingInterval")))
        fields.getAsJsonArray("metSamples").forEach { builder.addMetSamples(it.asFloat) }
        fields.getAsJsonArray("stepsSamples").forEach { builder.addStepsSamples(it.asInt) }
        fields.getAsJsonArray("activityInfo").forEach { activityInfo ->
            val info = activityInfo.asJsonObject
            builder.addActivityInfo(PbActivityInfo.newBuilder()
                .setValue(PbActivityInfo.ActivityClass.valueOf(info.get("value").asString))
                .setTimeStamp(buildPbLocalDateTime(info.getAsJsonObject("timeStamp")))
                .setFactor(info.get("factor").asFloat)
                .build())
        }
        return builder.build()
    }

    private fun buildPbLocalDateTime(fields: JsonObject): PbLocalDateTime {
        return PbLocalDateTime.newBuilder()
            .setDate(buildPbDate(fields.getAsJsonObject("date")))
            .setTime(PbTime.newBuilder()
                .setHour(fields.getAsJsonObject("time").get("hour").asInt)
                .setMinute(fields.getAsJsonObject("time").get("minute").asInt)
                .setSeconds(fields.getAsJsonObject("time").get("seconds").asInt)
                .setMillis(fields.getAsJsonObject("time").get("millis").asInt)
                .build())
            .setTimeZoneOffset(fields.get("timeZoneOffset").asInt)
            .setOBSOLETETrusted(fields.get("trusted").asBoolean)
            .build()
    }

    private fun assertActivitySamplesData(vector: JsonObject, actual: com.polar.sdk.api.model.activity.PolarActivitySamplesDayData) {
        val files = vector.getAsJsonObject("input").getAsJsonArray("files")
        val samples = actual.polarActivitySamplesDataList ?: error("${vector.get("id").asString} samples missing")
        assert(samples.size == files.size()) { "${vector.get("id").asString} sample count" }
        files.forEachIndexed { index, file ->
            val expected = file.asJsonObject.getAsJsonObject("expected")
            val sample = samples[index]
            assert(sample.startTime == LocalDateTime.parse(expected.get("startTime").asString)) { "${vector.get("id").asString}[$index] startTime" }
            assert(sample.metRecordingInterval == expected.get("metRecordingIntervalSeconds").asInt) { "${vector.get("id").asString}[$index] met interval" }
            assert(sample.stepRecordingInterval == expected.get("stepRecordingIntervalSeconds").asInt) { "${vector.get("id").asString}[$index] step interval" }
            assert(sample.metSamples == expected.getAsJsonArray("metSamples").toFloatList()) { "${vector.get("id").asString}[$index] met samples" }
            assert(sample.stepSamples == expected.getAsJsonArray("stepSamples").toIntList()) { "${vector.get("id").asString}[$index] step samples" }
            val expectedInfo = expected.getAsJsonArray("activityInfo")
            assert(sample.activityInfoList.size == expectedInfo.size()) { "${vector.get("id").asString}[$index] info count" }
            expectedInfo.forEachIndexed { infoIndex, infoElement ->
                val info = infoElement.asJsonObject
                val actualInfo = sample.activityInfoList[infoIndex]
                assert(actualInfo.activityClass?.name == info.get("activityClass").asString) { "${vector.get("id").asString}[$index][$infoIndex] class" }
                assert(actualInfo.timeStamp == LocalDateTime.parse(info.get("timeStamp").asString)) { "${vector.get("id").asString}[$index][$infoIndex] timestamp" }
                assert(actualInfo.factor == info.get("factor").asFloat) { "${vector.get("id").asString}[$index][$infoIndex] factor" }
            }
        }
    }

    private fun verifyActivitySampleRequests(client: BlePsFtpClient, vector: JsonObject) {
        val input = vector.getAsJsonObject("input")
        coVerify(exactly = 1) {
            client.request(PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(input.get("directoryPath").asString).build().toByteArray())
        }
        vector.getAsJsonObject("expected").getAsJsonArray("filePaths").forEach { path ->
            coVerify(exactly = 1) {
                client.request(PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(path.asString).build().toByteArray())
            }
        }
        confirmVerified(client)
    }

    private fun JsonArray.toIntList(): List<Int> = map { it.asInt }

    private fun JsonArray.toFloatList(): List<Float> = map { it.asFloat }

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must contain an even number of characters" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun loadActivitySamplesVector(fileName: String): JsonObject {
        FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/activity-samples/$fileName")).use { reader ->
            return JsonParser().parse(reader).asJsonObject
        }
    }

    private fun loadDailySummaryVector(fileName: String): JsonObject {
        FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/daily-summary/$fileName")).use { reader ->
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
}
