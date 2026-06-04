package com.polar.sharedtest

import com.polar.shared.time.PolarDateFields
import com.polar.shared.time.PolarDateTimeFields
import com.polar.shared.time.PolarDurationFields
import com.polar.shared.time.PolarTimeFields
import com.polar.shared.time.PolarTimeUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TimeDateCommonPolicyTest {
    @Test
    fun timeDateGoldenVectorsDefineExecutableCommonFieldPolicy() {
        TIME_DATE_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")

            when (input.stringValue("kind")) {
                "dateTimeFields" -> assertDateTimeFields(input, expected, caseId)
                "durationToMillis" -> assertDurationToMillis(input, expected, caseId)
                "nanosToMillis" -> assertNanosToMillis(input, caseId)
                "timezoneOffset" -> assertTimezoneOffset(input, caseId)
                "timeString" -> assertTimeString(input, caseId)
                "plainDateValidation" -> assertPlainDateValidation(input, caseId)
                else -> error("Unsupported time/date vector kind in $caseId")
            }
        }
    }

    @Test
    fun timeDateReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val vector = loadGoldenVectorText("sdk/time-date/time-date-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")
        val platforms = vector.objectValue("platforms")
        assertEquals("time-date-readiness", vector.stringValue("id"))
        assertEquals("timeDateReadiness", input.stringValue("kind"))
        assertEquals(TIME_DATE_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_TIME_DATE_FAMILIES, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(REQUIRED_TIME_DATE_FAMILIES, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(TIME_DATE_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarTimeUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarTimeUtilsTests", "PolarPlainDateTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.TimeDateCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private fun assertDateTimeFields(input: String, expected: String, caseId: String) {
        val fields = PolarDateTimeFields(
            date = PolarDateFields(input.intValue("year"), input.intValue("month"), input.intValue("day")),
            time = PolarTimeFields(input.intValue("hour"), input.intValue("minute"), input.intValue("second"), input.intValue("millis")),
            timeZoneOffsetMinutes = input.optionalIntValue("timeZoneOffsetMinutes"),
            trusted = input.booleanValue("trusted")
        )
        assertEquals(expected.intValue("year"), fields.date.year, caseId)
        assertEquals(expected.intValue("month"), fields.date.month, caseId)
        assertEquals(expected.intValue("day"), fields.date.day, caseId)
        assertEquals(expected.intValue("hour"), fields.time.hour, caseId)
        assertEquals(expected.intValue("minute"), fields.time.minute, caseId)
        assertEquals(expected.intValue("second"), fields.time.second, caseId)
        assertEquals(expected.intValue("millis"), fields.time.millis, caseId)
        assertEquals(expected.optionalIntValue("timeZoneOffsetMinutes"), fields.timeZoneOffsetMinutes, caseId)
        assertEquals(expected.booleanValue("trusted"), fields.trusted, caseId)
        assertEquals(expected.intValue("nanos"), PolarTimeUtils.millisToNanos(fields.time.millis), caseId)
    }

    private fun assertDurationToMillis(input: String, expected: String, caseId: String) {
        val duration = PolarDurationFields(
            hours = input.intValue("hours"),
            minutes = input.intValue("minutes"),
            seconds = input.intValue("seconds"),
            millis = input.intValue("millis")
        )
        assertEquals(expected.intValue("millis"), PolarTimeUtils.durationToMillis(duration), caseId)
    }

    private fun assertNanosToMillis(input: String, caseId: String) {
        input.objectArray("cases").forEach { sample ->
            assertEquals(sample.intValue("expectedMillis"), PolarTimeUtils.nanosToMillis(sample.intValue("nanoseconds")), caseId)
        }
    }

    private fun assertTimezoneOffset(input: String, caseId: String) {
        input.objectArray("cases").forEach { sample ->
            assertEquals(sample.intValue("expectedMinutes"), PolarTimeUtils.secondsToMinutes(sample.intValue("seconds")), caseId)
            assertEquals(sample.intValue("expectedSeconds"), PolarTimeUtils.minutesToSeconds(sample.intValue("expectedMinutes")), caseId)
        }
    }

    private fun assertTimeString(input: String, caseId: String) {
        input.objectArray("cases").forEach { sample ->
            val time = PolarTimeFields(
                hour = sample.intValue("hour"),
                minute = sample.intValue("minute"),
                second = sample.intValue("second"),
                millis = sample.intValue("millis")
            )
            assertEquals(sample.stringValue("expected"), PolarTimeUtils.timeString(time), caseId)
        }
    }

    private fun assertPlainDateValidation(input: String, caseId: String) {
        input.objectArray("cases").forEach { sample ->
            val parsed = PolarTimeUtils.parsePlainDate(sample.stringValue("value"))
            if (sample.booleanValue("valid")) {
                assertNotNull(parsed, caseId)
                assertEquals(sample.stringValue("value"), PolarTimeUtils.formatPlainDate(parsed), caseId)
            } else {
                assertNull(parsed, caseId)
            }
        }
    }

    private fun String.optionalIntValue(field: String): Int? {
        val valueStart = Regex("\"$field\"\\s*:\\s*(-?\\d+|null)").find(this)?.groupValues?.get(1) ?: return null
        return if (valueStart == "null") null else valueStart.toInt()
    }

    private companion object {
        val TIME_DATE_VECTORS = listOf(
            "sdk/time-date/local-date-time-field-mapping.json",
            "sdk/time-date/duration-to-millis.json",
            "sdk/time-date/nanos-to-millis-rounding.json",
            "sdk/time-date/timezone-offset-conversion.json",
            "sdk/time-date/time-string-formatting.json",
            "sdk/time-date/plain-date-validation.json"
        )
        val REQUIRED_TIME_DATE_FAMILIES = listOf(
            "local-date-time-field-mapping",
            "trusted-system-time-flag",
            "timezone-offset-minutes",
            "millis-nanos-conversion",
            "nanos-to-millis-rounding",
            "duration-to-millis",
            "time-string-formatting",
            "plain-date-validation",
            "platform-timezone-calendar-boundary",
            "platform-vector-reference-gate",
            "compile-verification-gate"
        )
        const val TIME_DATE_READINESS_COMMON_DECISION = "Time/date migration owns portable field mapping, timezone-offset minute conversion, millisecond/nanosecond policy, duration-to-millis math, time-string formatting, and plain-date validation in shared KMP code while platform calendar, timezone database, Date, Calendar, LocalDateTime, protobuf, and public facade conversion remain platform adapters until shared artifacts are consumed by iOS production code."
    }
}
