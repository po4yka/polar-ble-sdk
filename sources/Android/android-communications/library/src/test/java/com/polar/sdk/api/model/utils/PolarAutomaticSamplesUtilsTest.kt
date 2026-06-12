package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.sdk.api.model.activity.AutomaticSampleTriggerType
import com.polar.sdk.api.model.activity.IntervalStatus
import com.polar.sdk.api.model.activity.Movement
import com.polar.sdk.api.model.activity.PPiSampleStatus
import com.polar.sdk.api.model.activity.PPiSampleTriggerType
import com.polar.sdk.api.model.activity.SkinContact
import com.polar.sdk.impl.utils.PolarAutomaticSamplesUtils
import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbAutomaticHeartRateSamples
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbAutomaticSampleSessions
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbMeasTriggerType
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbPpIntervalAutoSamples
import fi.polar.remote.representation.protobuf.PpIntervals.PbPpIntervalSamples
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbTime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbPFtpEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.time.LocalDate
import java.time.LocalTime

class PolarAutomaticSamplesUtilsTest {

    private val mockClient = mockk<BlePsFtpClient>()

    @Test
    fun `automatic sample read headers use shared file facade planning`() {
        Assert.assertEquals("/U/0/AUTOS/", PolarRuntimePlannerAdapter.automaticSamplesDirectoryPath())
        Assert.assertEquals("/U/0/AUTOS/AUTOS001.BPB", PolarRuntimePlannerAdapter.automaticSamplesFilePath("AUTOS001.BPB"))
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/U/0/AUTOS/",
            PolarAutomaticSamplesUtils.automaticSamplesDirectoryReadOperation()
        )
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/U/0/AUTOS/AUTOS001.BPB",
            PolarAutomaticSamplesUtils.automaticSamplesFileReadOperation("AUTOS001.BPB")
        )
    }

    @Test
    fun `read247HrSamples() should correctly filter samples by date and parse all trigger types`() = runTest {
        // Arrange
        val fromDate = LocalDate.of(2024, 10, 10)
        val toDate = LocalDate.of(2024, 10, 18)

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(
                    PbPFtpEntry.newBuilder().setName("AUTOS000.BPB").setSize(333L).build(),
                    PbPFtpEntry.newBuilder().setName("AUTOS001.BPB").setSize(444L).build()
                )).build().writeTo(this)
        }

        val mockFileContent1 = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllSamples(listOf(
                    PbAutomaticHeartRateSamples.newBuilder()
                        .addAllHeartRate(listOf(60, 61, 63))
                        .setTime(PbTime.newBuilder().setHour(10).setMinute(12).setSeconds(34).build())
                        .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_HIGH_ACTIVITY).build(),
                    PbAutomaticHeartRateSamples.newBuilder()
                        .addAllHeartRate(listOf(80, 81, 83))
                        .setTime(PbTime.newBuilder().setHour(12).setMinute(0).setSeconds(0).build())
                        .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_MANUAL).build()
                ))
                .setDay(PbDate.newBuilder().setYear(2024).setMonth(10).setDay(18).build())
                .build().writeTo(this)
        }

        val mockFileContent2 = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllSamples(listOf(
                    PbAutomaticHeartRateSamples.newBuilder()
                        .addAllHeartRate(listOf(70, 72, 74))
                        .setTime(PbTime.newBuilder().setHour(16).setMinute(49).setSeconds(36).build())
                        .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_LOW_ACTIVITY).build(),
                    PbAutomaticHeartRateSamples.newBuilder()
                        .addAllHeartRate(listOf(90, 91, 93))
                        .setTime(PbTime.newBuilder().setHour(18).setMinute(0).setSeconds(0).build())
                        .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_TIMED).build()
                ))
                .setDay(PbDate.newBuilder().setYear(2024).setMonth(10).setDay(15).build())
                .build().writeTo(this)
        }

        coEvery { mockClient.request(any<ByteArray>()) } answers { mockDirectoryContent } andThen mockFileContent1 andThen mockFileContent2

        // Act
        val result = PolarAutomaticSamplesUtils.read247HrSamples(mockClient, fromDate, toDate)

        // Assert
        coVerify(atLeast = 1) { mockClient.request(any<ByteArray>()) }
        confirmVerified(mockClient)

        assert(result.size == 2)
        val date1 = LocalDate.of(2024, 10, 18)
        assert(result[0].date == date1)
        assert(result[0].samples[0].startTime == LocalTime.of(10, 12, 34))
        assert(result[0].samples[0].hrSamples == listOf(60, 61, 63))
        assert(result[0].samples[0].triggerType == AutomaticSampleTriggerType.TRIGGER_TYPE_HIGH_ACTIVITY)
        assert(result[0].samples[1].startTime == LocalTime.of(12, 0, 0))
        assert(result[0].samples[1].hrSamples == listOf(80, 81, 83))
        assert(result[0].samples[1].triggerType == AutomaticSampleTriggerType.TRIGGER_TYPE_MANUAL)

        val date2 = LocalDate.of(2024, 10, 15)
        assert(result[1].date == date2)
        assert(result[1].samples[0].startTime == LocalTime.of(16, 49, 36))
        assert(result[1].samples[0].hrSamples == listOf(70, 72, 74))
        assert(result[1].samples[0].triggerType == AutomaticSampleTriggerType.TRIGGER_TYPE_LOW_ACTIVITY)
        assert(result[1].samples[1].startTime == LocalTime.of(18, 0, 0))
        assert(result[1].samples[1].hrSamples == listOf(90, 91, 93))
        assert(result[1].samples[1].triggerType == AutomaticSampleTriggerType.TRIGGER_TYPE_TIMED)
    }

    @Test
    fun `read247HrSamples() should filter out samples outside the date range`() = runTest {
        // Arrange
        val fromDate = LocalDate.of(2024, 10, 10)
        val toDate = LocalDate.of(2024, 10, 18)

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(
                    PbPFtpEntry.newBuilder().setName("AUTOS000.BPB").setSize(333L).build(),
                    PbPFtpEntry.newBuilder().setName("AUTOS001.BPB").setSize(333L).build(),
                    PbPFtpEntry.newBuilder().setName("AUTOS002.BPB").setSize(333L).build()
                )).build().writeTo(this)
        }

        val mockFileContent = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllSamples(listOf(
                    PbAutomaticHeartRateSamples.newBuilder()
                        .addAllHeartRate(listOf(60, 61, 63))
                        .setTime(PbTime.newBuilder().setHour(10).setMinute(12).setSeconds(34).build())
                        .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_HIGH_ACTIVITY).build(),
                    PbAutomaticHeartRateSamples.newBuilder()
                        .addAllHeartRate(listOf(70, 72, 74))
                        .setTime(PbTime.newBuilder().setHour(14).setMinute(30).setSeconds(0).build())
                        .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_LOW_ACTIVITY).build()
                ))
                .setDay(PbDate.newBuilder().setYear(2024).setMonth(11).setDay(20).build())
                .build().writeTo(this)
        }

        val mockFileContent2 = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllSamples(listOf(
                    PbAutomaticHeartRateSamples.newBuilder()
                        .addAllHeartRate(listOf(80, 81, 83))
                        .setTime(PbTime.newBuilder().setHour(16).setMinute(45).setSeconds(0).build())
                        .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_MANUAL).build()
                ))
                .setDay(PbDate.newBuilder().setYear(2024).setMonth(11).setDay(9).build())
                .build().writeTo(this)
        }

        val mockFileContent3 = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllSamples(listOf(
                    PbAutomaticHeartRateSamples.newBuilder()
                        .addAllHeartRate(listOf(80, 81, 83))
                        .setTime(PbTime.newBuilder().setHour(16).setMinute(45).setSeconds(0).build())
                        .setTriggerType(PbMeasTriggerType.TRIGGER_TYPE_MANUAL).build()
                ))
                .setDay(PbDate.newBuilder().setYear(2024).setMonth(10).setDay(15).build())
                .build().writeTo(this)
        }

        coEvery { mockClient.request(any<ByteArray>()) } answers { mockDirectoryContent } andThen mockFileContent andThen mockFileContent2 andThen mockFileContent3

        // Act
        val result = PolarAutomaticSamplesUtils.read247HrSamples(mockClient, fromDate, toDate)

        // Assert
        coVerify(atLeast = 1) { mockClient.request(any<ByteArray>()) }
        confirmVerified(mockClient)
        assert(result.size == 1)
    }

    @Test
    fun `read247ppiSamples() should correctly calculate ppi samples and parse all sample status types`() = runTest {
        // Arrange
        val fromDate = LocalDate.of(2024, 11, 10)
        val toDate = LocalDate.of(2024, 11, 18)

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(
                    PbPFtpEntry.newBuilder().setName("AUTOS000.BPB").setSize(333L).build(),
                    PbPFtpEntry.newBuilder().setName("AUTOS001.BPB").setSize(444L).build(),
                )).build().writeTo(this)
        }

        val mockFileContent1 = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllPpiSamples(listOf(
                    PbPpIntervalAutoSamples.newBuilder()
                        .setRecordingTime(PbTime.newBuilder().setHour(1).setMinute(1).setSeconds(1).setMillis(1).build())
                        .setTriggerType(PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_AUTOMATIC)
                        .setPpi(PbPpIntervalSamples.newBuilder()
                            .addAllPpiDelta(listOf(2500, -634, 20, -100))
                            .addAllPpiErrorEstimateDelta(listOf(700, 0, 600, -50))
                            .addAllStatus(listOf(1, 2, 3, 4)).build()).build(),
                    PbPpIntervalAutoSamples.newBuilder()
                        .setRecordingTime(PbTime.newBuilder().setHour(2).setMinute(2).setSeconds(2).setMillis(2).build())
                        .setTriggerType(PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_AUTOMATIC)
                        .setPpi(PbPpIntervalSamples.newBuilder()
                            .addAllPpiDelta(listOf(1333, 10, -133, -555))
                            .addAllPpiErrorEstimateDelta(listOf(500, 55, -55, -500))
                            .addAllStatus(listOf(1, 2, 3, 4)).build()).build()
                ))
                .setDay(PbDate.newBuilder().setYear(2024).setMonth(11).setDay(18).build())
                .build().writeTo(this)
        }

        val mockFileContent2 = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllPpiSamples(listOf(
                    PbPpIntervalAutoSamples.newBuilder()
                        .setRecordingTime(PbTime.newBuilder().setHour(1).setMinute(1).setSeconds(1).setMillis(1).build())
                        .setTriggerType(PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_AUTOMATIC)
                        .setPpi(PbPpIntervalSamples.newBuilder()
                            .addAllPpiDelta(listOf(2500, -634, 20, -100))
                            .addAllPpiErrorEstimateDelta(listOf(700, 0, 600, -50))
                            .addAllStatus(listOf(1, 2, 3, 4)).build()).build()
                ))
                .setDay(PbDate.newBuilder().setYear(2024).setMonth(11).setDay(12).build())
                .build().writeTo(this)
        }

        coEvery { mockClient.request(any<ByteArray>()) } answers { mockDirectoryContent } andThen mockFileContent1 andThen mockFileContent2

        // Act
        val result = PolarAutomaticSamplesUtils.read247PPiSamples(mockClient, fromDate, toDate)

        // Assert
        coVerify(atLeast = 1) { mockClient.request(any<ByteArray>()) }
        confirmVerified(mockClient)

        assert(result.size == 3)
        val date = LocalDate.of(2024, 11, 18)
        assert(result[0].date == date)
        assert(result[0].samples.startTime == LocalTime.of(1, 1, 1, 1000000))
        assert(result[0].samples.ppiValueList == listOf(2500, 1866, 1886, 1786))
        assert(result[0].samples.ppiErrorEstimateList == listOf(700, 700, 1300, 1250))
        assert(result[0].samples.triggerType == PPiSampleTriggerType.TRIGGER_TYPE_AUTOMATIC)
        assert(result[0].samples.statusList[0] == PPiSampleStatus(skinContact = SkinContact.SKIN_CONTACT_DETECTED, movement = Movement.NO_MOVING_DETECTED, intervalStatus = IntervalStatus.INTERVAL_IS_ONLINE))
        assert(result[0].samples.statusList[1] == PPiSampleStatus(skinContact = SkinContact.NO_SKIN_CONTACT, movement = Movement.MOVING_DETECTED, intervalStatus = IntervalStatus.INTERVAL_IS_ONLINE))
        assert(result[0].samples.statusList[2] == PPiSampleStatus(skinContact = SkinContact.SKIN_CONTACT_DETECTED, movement = Movement.MOVING_DETECTED, intervalStatus = IntervalStatus.INTERVAL_IS_ONLINE))
        assert(result[0].samples.statusList[3] == PPiSampleStatus(skinContact = SkinContact.NO_SKIN_CONTACT, movement = Movement.NO_MOVING_DETECTED, intervalStatus = IntervalStatus.INTERVAL_DENOTES_OFFLINE_PERIOD))
    }

    @Test
    fun `ppi sample status mapping ignores high bits through shared KMP policy`() {
        Assert.assertEquals(
            PPiSampleStatus(
                skinContact = SkinContact.SKIN_CONTACT_DETECTED,
                movement = Movement.MOVING_DETECTED,
                intervalStatus = IntervalStatus.INTERVAL_DENOTES_OFFLINE_PERIOD
            ),
            PPiSampleStatus.from(0xFF)
        )
    }

    @Test
    fun `read247ppiSamples() should filter out dates outside of range`() = runTest {
        // Arrange
        val fromDate = LocalDate.of(2024, 11, 10)
        val toDate = LocalDate.of(2024, 11, 18)

        val mockDirectoryContent = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(
                    PbPFtpEntry.newBuilder().setName("AUTOS000.BPB").setSize(333L).build(),
                    PbPFtpEntry.newBuilder().setName("AUTOS001.BPB").setSize(444L).build(),
                    PbPFtpEntry.newBuilder().setName("AUTOS002.BPB").setSize(555L).build(),
                )).build().writeTo(this)
        }

        val mockFileContentBeforeFromDate = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllPpiSamples(listOf(
                    PbPpIntervalAutoSamples.newBuilder()
                        .setRecordingTime(PbTime.newBuilder().setHour(1).setMinute(1).setSeconds(1).setMillis(1).build())
                        .setTriggerType(PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_AUTOMATIC)
                        .setPpi(PbPpIntervalSamples.newBuilder()
                            .addAllPpiDelta(listOf(2500, -634, 20, -100))
                            .addAllPpiErrorEstimateDelta(listOf(700, 0, 600, -50))
                            .addAllStatus(listOf(1, 2, 3, 4)).build()).build()
                ))
                .setDay(PbDate.newBuilder().setYear(2024).setMonth(11).setDay(9).build())
                .build().writeTo(this)
        }

        val mockFileContentAfterToDate = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllPpiSamples(listOf(
                    PbPpIntervalAutoSamples.newBuilder()
                        .setRecordingTime(PbTime.newBuilder().setHour(1).setMinute(1).setSeconds(1).setMillis(1).build())
                        .setTriggerType(PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_AUTOMATIC)
                        .setPpi(PbPpIntervalSamples.newBuilder()
                            .addAllPpiDelta(listOf(2500, -634, 20, -100))
                            .addAllPpiErrorEstimateDelta(listOf(700, 0, 600, -50))
                            .addAllStatus(listOf(1, 2, 3, 4)).build()).build()
                ))
                .setDay(PbDate.newBuilder().setYear(2024).setMonth(11).setDay(19).build())
                .build().writeTo(this)
        }

        val mockFileContentInsideRange = ByteArrayOutputStream().apply {
            PbAutomaticSampleSessions.newBuilder()
                .addAllPpiSamples(listOf(
                    PbPpIntervalAutoSamples.newBuilder()
                        .setRecordingTime(PbTime.newBuilder().setHour(1).setMinute(1).setSeconds(1).setMillis(1).build())
                        .setTriggerType(PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.PPI_TRIGGER_TYPE_AUTOMATIC)
                        .setPpi(PbPpIntervalSamples.newBuilder()
                            .addAllPpiDelta(listOf(2500, -634, 20, -100))
                            .addAllPpiErrorEstimateDelta(listOf(700, 0, 600, -50))
                            .addAllStatus(listOf(1, 2, 3, 4)).build()).build()
                ))
                .setDay(PbDate.newBuilder().setYear(2024).setMonth(11).setDay(15).build())
                .build().writeTo(this)
        }

        coEvery { mockClient.request(any<ByteArray>()) } answers { mockDirectoryContent } andThen mockFileContentBeforeFromDate andThen mockFileContentAfterToDate andThen mockFileContentInsideRange

        // Act
        val result = PolarAutomaticSamplesUtils.read247PPiSamples(mockClient, fromDate, toDate)

        // Assert
        coVerify(atLeast = 1) { mockClient.request(any<ByteArray>()) }
        confirmVerified(mockClient)

        assert(result.size == 1)
    }

    @Test
    fun `automatic sample golden vectors map proto models`() {
        loadAutomaticSampleVectors().forEach { vector ->
            when (vector.getAsJsonObject("input").get("kind").asString) {
                "hr" -> assertHrVector(vector)
                "ppi" -> assertPpiVector(vector)
            }
        }
    }

    private fun assertHrVector(vector: JsonObject) {
        val proto = buildAutomaticSampleSessions(vector.getAsJsonObject("input").getAsJsonObject("proto"))
        val actual = com.polar.sdk.api.model.activity.Polar247HrSamplesData.fromProto(proto)
        val expected = vector.getAsJsonObject("platformExpectations").getAsJsonObject("android")
        val caseId = vector.get("id").asString

        assert(actual.date.toString() == expected.get("date").asString) { caseId }
        val expectedSamples = expected.getAsJsonArray("samples")
        assert(actual.samples.size == expectedSamples.size()) { caseId }
        expectedSamples.forEachIndexed { index, element ->
            val expectedSample = element.asJsonObject
            val actualSample = actual.samples[index]
            assert(actualSample.startTime.toString() == expectedSample.get("startTime").asString) { "$caseId sample $index time" }
            assert(actualSample.hrSamples == expectedSample.getAsJsonArray("heartRate").map { it.asInt }) { "$caseId sample $index hr" }
            assert(actualSample.triggerType.name == expectedSample.get("triggerType").asString) { "$caseId sample $index trigger" }
        }
    }

    private fun assertPpiVector(vector: JsonObject) {
        val protoFields = vector.getAsJsonObject("input").getAsJsonObject("proto")
        val sample = buildPpiSample(protoFields.getAsJsonObject("sample"))
        val actual = com.polar.sdk.api.model.activity.fromPbPPiDataSamples(sample)
        val expected = vector.getAsJsonObject("platformExpectations").getAsJsonObject("android")
        val caseId = vector.get("id").asString

        assert(actual.startTime.toString() == expected.get("startTime").asString) { "$caseId time" }
        assert(actual.triggerType.name == expected.get("triggerType").asString) { "$caseId trigger" }
        assert(actual.ppiValueList == expected.getAsJsonArray("ppiValueList").map { it.asInt }) { "$caseId ppi" }
        assert(actual.ppiErrorEstimateList == expected.getAsJsonArray("ppiErrorEstimateList").map { it.asInt }) { "$caseId error" }
        val expectedStatuses = expected.getAsJsonArray("statusList")
        assert(actual.statusList.size == expectedStatuses.size()) { "$caseId status count" }
        expectedStatuses.forEachIndexed { index, element ->
            val expectedStatus = element.asJsonObject
            val actualStatus = actual.statusList[index]
            assert(actualStatus.skinContact.name == expectedStatus.get("skinContact").asString) { "$caseId status $index skin" }
            assert(actualStatus.movement.name == expectedStatus.get("movement").asString) { "$caseId status $index movement" }
            assert(actualStatus.intervalStatus.name == expectedStatus.get("intervalStatus").asString) { "$caseId status $index interval" }
        }
    }

    @Test
    fun `automatic sample golden vectors follow neutral KMP vector shape`() {
        loadAutomaticSampleVectors().forEach { vector ->
            val id = vector.get("id").asString
            Assert.assertTrue(id, vector.has("area"))
            Assert.assertTrue(id, vector.has("case"))
            Assert.assertTrue(id, vector.has("source"))
            Assert.assertTrue(id, vector.has("input"))
            Assert.assertTrue(id, vector.has("expected"))
            Assert.assertTrue(id, vector.has("platforms"))
            val input = vector.getAsJsonObject("input")
            Assert.assertTrue(id, input.has("kind"))
            Assert.assertTrue(id, input.has("proto"))
            val platforms = vector.getAsJsonObject("platforms")
            Assert.assertTrue(id, platforms.get("android").asBoolean)
            Assert.assertTrue(id, platforms.get("ios").asBoolean)
            Assert.assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    @Test
    fun `activity summary readiness manifest is pinned before automatic sample migration`() {
        val readiness = loadActivitySummaryReadinessManifest()
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
            "unsupported-field-deferral",
            "public-model-shape-gate",
            "facade-request-error-boundary",
            "platform-activity-vector-reference-gate",
            "compile-verification-gate"
        )
        Assert.assertEquals(expectedFamilies, requiredFamilies)
        Assert.assertEquals(expectedFamilies, coveredFamilies)
        Assert.assertEquals(
            "Activity, automatic-sample, and daily-summary migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS activity/automatic/daily tests continue to reference the same vectors, activity request paths, aggregation, intervals, activity-info projection, malformed activity-sample behavior, automatic HR trigger and heart-rate arrays, PPI delta/status decoding, daily-summary path/scalar/duration projection, unsupported-field deferral, public model shape, facade request/error boundaries, and compile verification remain explicit before production model mapping moves.",
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

    private fun buildAutomaticSampleSessions(protoFields: JsonObject): PbAutomaticSampleSessions {
        val day = protoFields.getAsJsonObject("day")
        val builder = PbAutomaticSampleSessions.newBuilder()
            .setDay(PbDate.newBuilder().setYear(day.get("year").asInt).setMonth(day.get("month").asInt).setDay(day.get("day").asInt))
        protoFields.getAsJsonArray("samples").forEach { element ->
            val sample = element.asJsonObject
            builder.addSamples(
                PbAutomaticHeartRateSamples.newBuilder()
                    .addAllHeartRate(sample.getAsJsonArray("heartRate").map { it.asInt })
                    .setTime(buildPbTime(sample.getAsJsonObject("time")))
                    .setTriggerType(PbMeasTriggerType.forNumber(sample.get("triggerType").asInt))
            )
        }
        return builder.build()
    }

    private fun buildPpiSample(sample: JsonObject): PbPpIntervalAutoSamples {
        return PbPpIntervalAutoSamples.newBuilder()
            .setRecordingTime(buildPbTime(sample.getAsJsonObject("recordingTime")))
            .setTriggerType(PbPpIntervalAutoSamples.PbPpIntervalRecordingTriggerType.forNumber(sample.get("triggerType").asInt))
            .setPpi(
                PbPpIntervalSamples.newBuilder()
                    .addAllPpiDelta(sample.getAsJsonArray("ppiDelta").map { it.asInt })
                    .addAllPpiErrorEstimateDelta(sample.getAsJsonArray("ppiErrorEstimateDelta").map { it.asInt })
                    .addAllStatus(sample.getAsJsonArray("status").map { it.asInt })
            )
            .build()
    }

    private fun buildPbTime(time: JsonObject): PbTime {
        return PbTime.newBuilder()
            .setHour(time.get("hour").asInt)
            .setMinute(time.get("minute").asInt)
            .setSeconds(time.get("second").asInt)
            .setMillis(time.get("millis").asInt)
            .build()
    }

    private fun loadAutomaticSampleVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/automatic-samples")
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

    private fun loadActivitySummaryReadinessManifest(): JsonObject {
        val vectorFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/activity-samples/activity-summary-readiness.json")
        FileReader(vectorFile).use { reader ->
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
