package com.polar.sharedtest

import com.polar.shared.sdk.PolarAutomaticHrTriggerName
import com.polar.shared.sdk.PolarActivityClassName
import com.polar.shared.sdk.PolarActivityModels
import com.polar.shared.sdk.PolarDailyBalanceFeedbackName
import com.polar.shared.sdk.PolarPpiIntervalStatusName
import com.polar.shared.sdk.PolarPpiMovementName
import com.polar.shared.sdk.PolarPpiSkinContactName
import com.polar.shared.sdk.PolarPpiStatusNames
import com.polar.shared.sdk.PolarTrainingReadinessName
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivitySummaryCommonPolicyTest {
    @Test
    fun activitySampleGoldenVectorsDefineExecutableCommonAggregationAndMalformedPolicy() {
        val aggregation = loadGoldenVectorText("sdk/activity-samples/two-files-step-aggregation.json")
        val aggregationInput = aggregation.objectValue("input")
        val files = aggregationInput.objectArray("files")
        val expected = aggregation.objectValue("expected")
        val parsedFiles = files.map { parseActivitySampleEntry(it) }

        assertEquals(expected.stringArrayValue("filePaths"), files.map { it.stringValue("path") }, aggregation.stringValue("id"))
        assertEquals(expected.intValue("totalSteps"), parsedFiles.sumOf { file -> file.stepSamples.sum() }, aggregation.stringValue("id"))
        parsedFiles.forEachIndexed { index, parsed ->
            val fileExpected = files[index].objectValue("expected")
            assertEquals(fileExpected.stringValue("startTime"), parsed.startTime, "activity file $index start")
            assertEquals(fileExpected.intValue("metRecordingIntervalSeconds"), parsed.metRecordingIntervalSeconds, "activity file $index MET interval")
            assertEquals(fileExpected.intValue("stepRecordingIntervalSeconds"), parsed.stepRecordingIntervalSeconds, "activity file $index step interval")
            assertDoubleListEquals(fileExpected.doubleArrayValue("metSamples"), parsed.metSamples, "activity file $index MET samples")
            assertEquals(fileExpected.intArrayValue("stepSamples"), parsed.stepSamples, "activity file $index step samples")
            val activityInfo = fileExpected.objectArray("activityInfo")
            assertEquals(activityInfo.map { it.stringValue("activityClass") }, parsed.activityInfo.map { it.activityClass }, "activity file $index classes")
            assertDoubleListEquals(activityInfo.map { it.doubleValue("factor") }, parsed.activityInfo.map { it.factor }, "activity file $index factors")
        }

        val malformed = loadGoldenVectorText("sdk/activity-samples/malformed-sample-file-platform-policy.json")
        val malformedExpected = malformed.objectValue("expected")
        val malformedConsumerTests = malformed.objectValue("consumerTests")
        val malformedPlatforms = malformed.objectValue("platforms")
        assertEquals("malformed-sample-file-platform-policy", malformed.stringValue("id"))
        assertEquals("sdk.activity-samples", malformed.stringValue("area"))
        assertEquals("malformed_sample_file_platform_policy", malformed.stringValue("case"))
        assertEquals("2025-04-08", malformed.objectValue("input").stringValue("requestDate"), malformed.stringValue("id"))
        assertEquals("/U/0/20250408/ACT/", malformed.objectValue("input").stringValue("directoryPath"), malformed.stringValue("id"))
        assertEquals("ff", malformed.objectValue("input").objectArray("files").single().stringValue("responseHex"), malformed.stringValue("id"))
        assertEquals(listOf("/U/0/20250408/ACT/ASAMPL0.BPB"), malformedExpected.stringArrayValue("filePaths"), malformed.stringValue("id"))
        assertEquals(0, malformedExpected.intValue("steps"), malformed.stringValue("id"))
        assertEquals(0, malformedExpected.objectValue("androidSamples").intValue("count"), malformed.stringValue("id"))
        assertEquals(false, malformedExpected.objectValue("androidSamples").booleanValue("throws"), malformed.stringValue("id"))
        assertEquals(true, malformedExpected.objectValue("iosSamples").booleanValue("throws"), malformed.stringValue("id"))
        assertEquals("Activity sample directory where the listed ASAMPL file contains malformed protobuf bytes, preserving current Android/iOS policy before KMP normalization. KMP must choose one shared malformed activity sample policy before moving the parser or wrappers into common code; Android currently returns an empty/default result while iOS propagates the parse error for sample reads.", malformed.stringValue("notes"), malformed.stringValue("id"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarActivityUtilsTest"), malformedConsumerTests.stringArrayValue("android"), malformed.stringValue("id"))
        assertEquals(listOf("PolarActivityUtilsTest"), malformedConsumerTests.stringArrayValue("ios"), malformed.stringValue("id"))
        assertEquals(listOf("com.polar.sharedtest.ActivitySummaryCommonPolicyTest"), malformedConsumerTests.stringArrayValue("commonPrototype"), malformed.stringValue("id"))
        assertEquals(true, malformedPlatforms.booleanValue("android"), malformed.stringValue("id"))
        assertEquals(true, malformedPlatforms.booleanValue("ios"), malformed.stringValue("id"))
        assertEquals(true, malformedPlatforms.booleanValue("common"), malformed.stringValue("id"))
    }

    @Test
    fun automaticSampleGoldenVectorsDefineExecutableCommonTriggerDeltaAndStatusPolicy() {
        val hr = loadGoldenVectorText("sdk/automatic-samples/hr-all-trigger-types.json")
        val hrSamples = hr.objectValue("input").objectValue("proto").objectArray("samples")
        val hrExpectation = hr.objectValue("platformExpectations").objectValue("android").objectArray("samples")
        val triggerMappings = hrSamples.map { sample -> hrTriggerName(sample.intValue("triggerType")) }
        assertEquals(listOf("TRIGGER_TYPE_HIGH_ACTIVITY", "TRIGGER_TYPE_LOW_ACTIVITY", "TRIGGER_TYPE_TIMED", "TRIGGER_TYPE_MANUAL"), triggerMappings, hr.stringValue("id"))
        hrSamples.forEachIndexed { index, sample ->
            assertEquals(hrExpectation[index].intArrayValue("heartRate"), sample.intArrayValue("heartRate"), "HR sample $index heart-rate array")
            assertEquals(triggerMappings[index], hrExpectation[index].stringValue("triggerType"), "HR sample $index Android trigger")
        }

        val ppi = loadGoldenVectorText("sdk/automatic-samples/ppi-deltas-statuses.json")
        val ppiSample = ppi.objectValue("input").objectValue("proto").objectValue("sample")
        val ppiExpectation = ppi.objectValue("platformExpectations").objectValue("android")
        assertEquals(ppiExpectation.intArrayValue("ppiValueList"), cumulativeValues(ppiSample.intArrayValue("ppiDelta")), ppi.stringValue("id"))
        assertEquals(ppiExpectation.intArrayValue("ppiErrorEstimateList"), cumulativeValues(ppiSample.intArrayValue("ppiErrorEstimateDelta")), ppi.stringValue("id"))
        val decodedStatuses = ppiSample.intArrayValue("status").map { decodePpiStatus(it) }
        assertEquals("NO_SKIN_CONTACT", PolarPpiSkinContactName.fromValue(0)?.name, ppi.stringValue("id"))
        assertEquals("SKIN_CONTACT_DETECTED", PolarPpiSkinContactName.fromValue(1)?.name, ppi.stringValue("id"))
        assertEquals("NO_MOVING_DETECTED", PolarPpiMovementName.fromValue(0)?.name, ppi.stringValue("id"))
        assertEquals("MOVING_DETECTED", PolarPpiMovementName.fromValue(1)?.name, ppi.stringValue("id"))
        assertEquals("INTERVAL_IS_ONLINE", PolarPpiIntervalStatusName.fromValue(0)?.name, ppi.stringValue("id"))
        assertEquals("INTERVAL_DENOTES_OFFLINE_PERIOD", PolarPpiIntervalStatusName.fromValue(1)?.name, ppi.stringValue("id"))
        assertEquals(
            listOf("SKIN_CONTACT_DETECTED", "NO_SKIN_CONTACT", "MOVING_DETECTED", "NO_MOVING_DETECTED", "INTERVAL_DENOTES_OFFLINE_PERIOD", "INTERVAL_IS_ONLINE"),
            PPI_STATUS_POLICY_TERMS,
            ppi.stringValue("id")
        )
        val expectedStatuses = ppiExpectation.objectArray("statusList").map { status ->
            PpiStatus(
                skinContact = status.stringValue("skinContact"),
                movement = status.stringValue("movement"),
                intervalStatus = status.stringValue("intervalStatus")
            )
        }
        assertEquals(expectedStatuses, decodedStatuses, ppi.stringValue("id"))
        assertEquals("choose-common-duration-or-time-components-instead-of-platform-string-format", ppi.objectValue("platformExpectations").objectValue("commonDecision").stringValue("timeFormattingPolicy"), ppi.stringValue("id"))
    }

    @Test
    fun dailySummaryGoldenVectorDefinesExecutableCommonSummaryProjectionPolicy() {
        val vector = loadGoldenVectorText("sdk/daily-summary/full-summary.json")
        val input = vector.objectValue("input")
        val proto = input.objectValue("proto")
        val expected = vector.objectValue("expected")

        assertEquals(input.stringValue("expectedPath"), PolarActivityModels.dailySummaryPath(input.stringValue("requestDate").replace("-", "")), vector.stringValue("id"))
        assertEquals("/U/0/20260102/ACT/", PolarActivityModels.activityDirectoryPath("20260102"), vector.stringValue("id"))
        assertEquals("/U/0/20260102/DSUM/DSUM.BPB", PolarActivityModels.dailySummaryPath("20260102"), vector.stringValue("id"))
        assertEquals(expected.stringValue("date"), dateString(proto.objectValue("date")), vector.stringValue("id"))
        assertEquals(expected.intValue("activityCalories"), proto.intValue("activityCalories"), vector.stringValue("id"))
        assertEquals(expected.intValue("trainingCalories"), proto.intValue("trainingCalories"), vector.stringValue("id"))
        assertEquals(expected.intValue("bmrCalories"), proto.intValue("bmrCalories"), vector.stringValue("id"))
        assertEquals(expected.intValue("steps"), proto.intValue("steps"), vector.stringValue("id"))
        assertDoubleEquals(expected.doubleValue("activityDistance"), proto.doubleValue("activityDistance"), vector.stringValue("id"))
        assertEquals(expected.stringValue("dailyBalanceFeedback"), dailyBalanceFeedbackName(proto.stringValue("dailyBalanceFeedback")), vector.stringValue("id"))
        assertEquals(expected.stringValue("readinessForSpeedAndStrengthTraining"), trainingReadinessName(proto.stringValue("readinessForSpeedAndStrengthTraining")), vector.stringValue("id"))
        val goalExpected = expected.objectValue("activityGoalSummary")
        val goalProto = proto.objectValue("activityGoalSummary")
        assertDoubleEquals(goalExpected.doubleValue("activityGoal"), goalProto.doubleValue("activityGoal"), vector.stringValue("id"))
        assertDoubleEquals(goalExpected.doubleValue("achievedActivity"), goalProto.doubleValue("achievedActivity"), vector.stringValue("id"))
        listOf("timeToGoUp", "timeToGoWalk", "timeToGoJog").forEach { field ->
            assertEquals(durationMillis(goalExpected.objectValue(field)), durationMillis(goalProto.objectValue(field)), "$field duration")
        }
        val classExpected = expected.objectValue("activityClassTimes")
        val classProto = proto.objectValue("activityClassTimes")
        listOf("timeNonWear", "timeSleep", "timeSedentary", "timeLightActivity", "timeContinuousModerate", "timeIntermittentModerate", "timeContinuousVigorous", "timeIntermittentVigorous").forEach { field ->
            assertEquals(durationMillis(classExpected.objectValue(field)), durationMillis(classProto.objectValue(field)), "$field duration")
        }
    }

    @Test
    fun activitySummaryReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val vector = loadGoldenVectorText("sdk/activity-samples/activity-summary-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")
        val platforms = vector.objectValue("platforms")
        assertEquals("activity-summary-readiness", vector.stringValue("id"))
        assertEquals("sdk.activity-summary", vector.stringValue("area"))
        assertEquals("activity_summary_readiness", vector.stringValue("case"))
        assertEquals("activitySummaryReadiness", input.stringValue("kind"))
        assertEquals(activitySummaryVectors, input.stringArrayValue("policyVectorPaths"), vector.stringValue("id"))
        assertEquals(requiredActivitySummaryFamilies, input.stringArrayValue("requiredBehaviorFamilies"), vector.stringValue("id"))
        assertEquals(requiredActivitySummaryFamilies, expected.stringArrayValue("coveredBehaviorFamilies"), vector.stringValue("id"))
        assertEquals(ACTIVITY_SUMMARY_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"), vector.stringValue("id"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarActivityUtilsTest", "com.polar.sdk.api.model.utils.PolarAutomaticSamplesUtilsTest"), consumerTests.stringArrayValue("android"), vector.stringValue("id"))
        assertEquals(listOf("PolarActivityUtilsTest", "PolarAutomaticSamplesUnitTest"), consumerTests.stringArrayValue("ios"), vector.stringValue("id"))
        assertEquals(listOf("com.polar.sharedtest.ActivitySummaryCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"), vector.stringValue("id"))
        assertEquals(true, platforms.booleanValue("android"), vector.stringValue("id"))
        assertEquals(true, platforms.booleanValue("ios"), vector.stringValue("id"))
        assertEquals(true, platforms.booleanValue("common"), vector.stringValue("id"))
    }

    private fun parseActivitySampleEntry(file: String): ActivitySampleEntry {
        val proto = file.objectValue("proto")
        return ActivitySampleEntry(
            startTime = localDateTimeString(proto.objectValue("startTime")),
            metRecordingIntervalSeconds = durationSeconds(proto.objectValue("metRecordingInterval")),
            stepRecordingIntervalSeconds = durationSeconds(proto.objectValue("stepsRecordingInterval")),
            metSamples = proto.doubleArrayValue("metSamples"),
            stepSamples = proto.intArrayValue("stepsSamples"),
            activityInfo = proto.objectArray("activityInfo").map { info ->
                ActivityInfo(
                    activityClass = activityClassName(info.stringValue("value")),
                    factor = info.doubleValue("factor")
                )
            }
        )
    }

    private fun activityClassName(protoName: String): String {
        val value = when (protoName) {
            "SLEEP" -> 1
            "SEDENTARY" -> 2
            "LIGHT" -> 3
            "CONTINUOUS_MODERATE" -> 4
            "INTERMITTENT_MODERATE" -> 5
            "CONTINUOUS_VIGOROUS" -> 6
            "INTERMITTENT_VIGOROUS" -> 7
            "NON_WEAR" -> 8
            else -> error("Unexpected activity class $protoName")
        }
        return PolarActivityClassName.fromValue(value)?.name ?: error("Unexpected activity class value $value")
    }

    private fun dailyBalanceFeedbackName(protoName: String): String {
        return PolarDailyBalanceFeedbackName.valueOf(protoName.removePrefix("DB_")).name
    }

    private fun trainingReadinessName(protoName: String): String {
        return PolarTrainingReadinessName.valueOf(protoName.removePrefix("RSST_B4_")).name
    }

    private fun hrTriggerName(value: Int): String {
        return PolarAutomaticHrTriggerName.fromValue(value)?.name ?: "TRIGGER_TYPE_UNKNOWN"
    }

    private fun cumulativeValues(deltas: List<Int>): List<Int> {
        var current = 0
        return deltas.map { delta ->
            current += delta
            current
        }
    }

    private fun decodePpiStatus(value: Int): PpiStatus {
        val status = PolarPpiStatusNames.fromStatusByte(value) ?: error("Unexpected PPI status $value")
        return PpiStatus(status.skinContact, status.movement, status.intervalStatus)
    }

    private fun localDateTimeString(dateTime: String): String {
        val date = dateString(dateTime.objectValue("date"))
        val time = dateTime.objectValue("time")
        val millis = time.intValue("millis")
        val base = "${date}T${time.twoDigit("hour")}:${time.twoDigit("minute")}:${time.twoDigit("seconds")}"
        return if (millis == 0) base else "$base.${millis.toString().padStart(3, '0')}"
    }

    private fun dateString(date: String): String {
        return "${date.intValue("year")}-${date.twoDigit("month")}-${date.twoDigit("day")}"
    }

    private fun durationSeconds(duration: String): Int {
        return duration.intValue("hours") * 3600 + duration.intValue("minutes") * 60 + duration.intValue("seconds")
    }

    private fun durationMillis(duration: String): Int {
        return durationSeconds(duration) * 1000 + duration.intValue("millis")
    }

    private fun String.twoDigit(field: String): String {
        return intValue(field).toString().padStart(2, '0')
    }

    private fun String.intArrayValue(field: String): List<Int> {
        return numberArrayContent(field).split(',').map { item -> item.trim().toInt() }
    }

    private fun String.doubleArrayValue(field: String): List<Double> {
        return numberArrayContent(field).split(',').map { item -> item.trim().toDouble() }
    }

    private fun String.numberArrayContent(field: String): String {
        val match = Regex("\"$field\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(this) ?: error("Missing number array $field in $this")
        val content = match.groupValues[1].trim()
        if (content.isEmpty()) return ""
        return content
    }

    private fun String.doubleValue(field: String): Double {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:e[+-]?\\d+)?)").find(this)?.groupValues?.get(1)?.toDouble() ?: error("Missing double field $field in $this")
    }

    private fun String.booleanValue(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" } ?: error("Missing boolean field $field in $this")
    }

    private fun assertDoubleEquals(expected: Double, actual: Double, message: String) {
        val tolerance = maxOf(0.000001, abs(expected) * 0.000001)
        assertTrue(abs(expected - actual) <= tolerance, "$message expected $expected but was $actual")
    }

    private fun assertDoubleListEquals(expected: List<Double>, actual: List<Double>, message: String) {
        assertEquals(expected.size, actual.size, "$message size")
        expected.forEachIndexed { index, expectedValue -> assertDoubleEquals(expectedValue, actual[index], "$message $index") }
    }

    private data class ActivitySampleEntry(
        val startTime: String,
        val metRecordingIntervalSeconds: Int,
        val stepRecordingIntervalSeconds: Int,
        val metSamples: List<Double>,
        val stepSamples: List<Int>,
        val activityInfo: List<ActivityInfo>
    )

    private data class ActivityInfo(
        val activityClass: String,
        val factor: Double
    )

    private data class PpiStatus(
        val skinContact: String,
        val movement: String,
        val intervalStatus: String
    )

    private companion object {
        val activitySummaryVectors = listOf(
            "sdk/activity-samples/two-files-step-aggregation.json",
            "sdk/activity-samples/malformed-sample-file-platform-policy.json",
            "sdk/automatic-samples/hr-all-trigger-types.json",
            "sdk/automatic-samples/ppi-deltas-statuses.json",
            "sdk/daily-summary/full-summary.json"
        )
        val requiredActivitySummaryFamilies = listOf(
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
        const val ACTIVITY_SUMMARY_READINESS_COMMON_DECISION = "Activity, automatic-sample, and daily-summary migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS activity/automatic/daily tests continue to reference the same vectors, activity request paths, aggregation, intervals, activity-info projection, malformed activity-sample behavior, automatic HR trigger and heart-rate arrays, PPI delta/status decoding, daily-summary path/scalar/duration projection, and compile verification remain explicit before production model mapping moves."
        val PPI_STATUS_POLICY_TERMS = listOf("SKIN_CONTACT_DETECTED", "NO_SKIN_CONTACT", "MOVING_DETECTED", "NO_MOVING_DETECTED", "INTERVAL_DENOTES_OFFLINE_PERIOD", "INTERVAL_IS_ONLINE")
    }
}
