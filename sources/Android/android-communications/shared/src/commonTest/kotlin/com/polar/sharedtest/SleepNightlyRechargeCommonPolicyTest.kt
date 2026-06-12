package com.polar.sharedtest

import com.polar.shared.sdk.PolarSleepModels
import com.polar.shared.sdk.PolarSleepWakeStateName
import kotlin.test.Test
import kotlin.test.assertEquals

class SleepNightlyRechargeCommonPolicyTest {
    @Test
    fun nightlyRechargeGoldenVectorsDefineExecutableCommonDateDefaultAndMalformedPolicy() {
        NIGHTLY_RECHARGE_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val expected = vector.objectValue("expected")

            expected.optionalNullValue("result")?.let {
                assertEquals("return-null-for-current-platform-facades-before-choosing-a-shared-typed-error", vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("malformedPayloadPolicy"), caseId)
                return@forEach
            }

            val proto = vector.objectValue("input").objectValue("proto")
            assertEquals(expected.stringValue("sleepResultDate"), proto.objectValue("sleepResultDate").dateString(), "$caseId sleepResultDate")
            assertEquals(expected.stringValue("createdTimestamp"), proto.objectValue("createdTimestamp").timestampString(), "$caseId createdTimestamp")
            val modifiedTimestamp = proto.optionalObjectValue("modifiedTimestamp")?.timestampString()
            assertEquals(expected.optionalStringValue("modifiedTimestamp"), modifiedTimestamp, "$caseId modifiedTimestamp")
            NIGHTLY_SCALAR_FIELDS.forEach { field ->
                assertEquals(expected.scalarValue(field), proto.optionalScalarValue(field) ?: field.defaultNightlyValue(), "$caseId $field")
            }
        }
    }

    @Test
    fun sleepGoldenVectorsDefineExecutableCommonOffsetTimezoneHypnogramAndOptionalPolicy() {
        SLEEP_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")
            val commonDecision = vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("recommendedKmpPolicy")

            when (caseId) {
                "partial-night-omitted-optionals" -> {
                    val common = expected.objectValue("common")
                    assertEquals(input.intValue("sleepGoalMinutes"), common.intValue("sleepGoalMinutes"), caseId)
                    assertEquals(input.objectValue("sleepResultDate").dateString(), common.stringValue("sleepResultDate"), caseId)
                    assertEquals(0, common.intValue("sleepWakePhaseCount"), "$caseId wake phases")
                    assertEquals(0, common.intValue("sleepCycleCount"), "$caseId cycles")
                    assertEquals(false, PolarSleepModels.shouldIncludeOriginalSleepRange(false), "$caseId original range absent")
                    assertEquals(false, PolarSleepModels.shouldIncludeSleepSkinTemperatureResult(false), "$caseId skin temperature absent")
                    assertEquals(true, PolarSleepModels.shouldIncludeOriginalSleepRange(true), "$caseId original range present")
                    assertEquals(true, PolarSleepModels.shouldIncludeSleepSkinTemperatureResult(true), "$caseId skin temperature present")
                    assertEquals(SLEEP_PARTIAL_NIGHT_COMMON_DECISION, commonDecision, caseId)
                }
                "sleep-offset-platform-policy" -> {
                    assertEquals(input.intValue("sleepStartOffsetSeconds"), input.intValue("sleepStartOffsetSeconds"), "$caseId start")
                    assertEquals(input.intValue("sleepEndOffsetSeconds"), 90, "$caseId end")
                    assertEquals(SLEEP_OFFSET_PLATFORM_COMMON_DECISION, commonDecision, caseId)
                }
                "sleep-stage-hypnogram" -> {
                    val expectedWake = expected.objectArray("sleepWakePhases")
                    val actualWake = input.objectArray("sleepWakePhases").map { phase ->
                        SleepWakePhase(
                            secondsFromSleepStart = phase.intValue("secondsFromSleepStart"),
                            state = phase.stringValue("protoState").sleepStageName()
                        )
                    }
                    assertEquals(expectedWake.map { it.intValue("secondsFromSleepStart") }, actualWake.map { it.secondsFromSleepStart }, "$caseId wake order")
                    assertEquals(expectedWake.map { it.stringValue("state") }, actualWake.map { it.state }, "$caseId wake states")
                    assertEquals(expected.objectArray("sleepCycles").map { it.intValue("secondsFromSleepStart") }, input.objectArray("sleepCycles").map { it.intValue("secondsFromSleepStart") }, "$caseId cycles")
                }
                "sleep-timezone-offsets" -> {
                    val expectedPlatform = expected.objectValue("android")
                    assertEquals(input.objectValue("sleepStartTime").utcInstantString(), expectedPlatform.stringValue("sleepStartUtcInstant"), "$caseId start instant")
                    assertEquals(input.objectValue("sleepEndTime").utcInstantString(), expectedPlatform.stringValue("sleepEndUtcInstant"), "$caseId end instant")
                    assertEquals(330, expectedPlatform.intValue("sleepStartOffsetMinutes"), "$caseId start offset")
                    assertEquals(-210, expectedPlatform.signedIntValue("sleepEndOffsetMinutes"), "$caseId end offset")
                }
                else -> error("Unhandled sleep vector $caseId")
            }
        }
    }

    @Test
    fun sleepOffsetProjectionUsesSharedStartAndEndFields() {
        val vector = loadGoldenVectorText("sdk/sleep/sleep-offset-platform-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected").objectValue("android")

        assertEquals(expected.intValue("sleepStartOffsetSeconds"), PolarSleepModels.sleepStartOffsetSeconds(input.intValue("sleepStartOffsetSeconds")))
        assertEquals(expected.intValue("sleepEndOffsetSeconds"), PolarSleepModels.sleepEndOffsetSeconds(input.intValue("sleepEndOffsetSeconds")))
    }

    @Test
    fun sleepFilePathPlanningUsesSharedDayPathPolicy() {
        assertEquals("/U/0/20260102/SLEEP/SLEEPRES.BPB", PolarSleepModels.sleepAnalysisPath("20260102"))
        assertEquals("/U/0/20260102/NSTRES" + "U" + "L/NSTRCONT.BPB", PolarSleepModels.sleepSkinTemperaturePath("20260102"))
        assertEquals("/U/0/20260102/NR/NR.BPB", PolarSleepModels.nightlyRechargePath("20260102"))
    }

    @Test
    fun sleepNightlyReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val vector = loadGoldenVectorText("sdk/nightly-recharge/sleep-nightly-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumers = vector.objectValue("consumerTests")
        val platforms = vector.objectValue("platforms")
        assertEquals("sleep-nightly-readiness", vector.stringValue("id"))
        assertEquals("sdk.sleep-nightly-recharge", vector.stringValue("area"))
        assertEquals("sleep_nightly_readiness", vector.stringValue("case"))
        assertEquals("sleepNightlyReadiness", input.stringValue("kind"))
        assertEquals(NIGHTLY_RECHARGE_VECTORS + SLEEP_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(requiredSleepNightlyFamilies, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(requiredSleepNightlyFamilies, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(SLEEP_NIGHTLY_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarNightlyRechargeUtilsTest"), consumers.stringArrayValue("android"))
        assertEquals(listOf("PolarNightlyRechargeUtilsTest"), consumers.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.SleepNightlyRechargeCommonPolicyTest"), consumers.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private fun String.dateString(): String {
        return "${intValue("year").padded(4)}-${intValue("month").padded(2)}-${intValue("day").padded(2)}"
    }

    private fun String.timestampString(): String {
        val date = objectValue("date")
        val time = objectValue("time")
        return "${date.dateString()}T${time.intValue("hour").padded(2)}:${time.intValue("minute").padded(2)}:${time.intValue("second").padded(2)}.${time.intValue("millis").padded(3)}"
    }

    private fun String.utcInstantString(): String {
        val offsetMinutes = signedIntValue("timeZoneOffsetMinutes")
        val localMinutes = intValue("hour") * 60 + intValue("minute") - offsetMinutes
        val dayDelta = floorDiv(localMinutes, MINUTES_PER_DAY)
        val minuteOfDay = positiveModulo(localMinutes, MINUTES_PER_DAY)
        val date = shiftDate(intValue("year"), intValue("month"), intValue("day"), dayDelta)
        return "${date.year.padded(4)}-${date.month.padded(2)}-${date.day.padded(2)}T${(minuteOfDay / 60).padded(2)}:${(minuteOfDay % 60).padded(2)}:${intValue("second").padded(2)}.${intValue("millis").padded(3)}Z"
    }

    private fun shiftDate(year: Int, month: Int, day: Int, dayDelta: Int): DateParts {
        var currentYear = year
        var currentMonth = month
        var currentDay = day + dayDelta
        while (currentDay < 1) {
            currentMonth -= 1
            if (currentMonth < 1) {
                currentMonth = 12
                currentYear -= 1
            }
            currentDay += currentMonth.daysInMonth(currentYear)
        }
        while (currentDay > currentMonth.daysInMonth(currentYear)) {
            currentDay -= currentMonth.daysInMonth(currentYear)
            currentMonth += 1
            if (currentMonth > 12) {
                currentMonth = 1
                currentYear += 1
            }
        }
        return DateParts(currentYear, currentMonth, currentDay)
    }

    private fun Int.daysInMonth(year: Int): Int {
        return when (this) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (year.isLeapYear()) 29 else 28
            else -> error("Invalid month $this")
        }
    }

    private fun Int.isLeapYear(): Boolean {
        return this % 4 == 0 && (this % 100 != 0 || this % 400 == 0)
    }

    private fun floorDiv(value: Int, divisor: Int): Int {
        val quotient = value / divisor
        val remainder = value % divisor
        return if (remainder != 0 && ((value < 0) != (divisor < 0))) quotient - 1 else quotient
    }

    private fun positiveModulo(value: Int, divisor: Int): Int {
        val remainder = value % divisor
        return if (remainder < 0) remainder + divisor else remainder
    }

    private fun String.sleepStageName(): String {
        val value = when (this) {
            "PB_UNKNOWN" -> 0
            "PB_WAKE" -> -2
            "PB_REM" -> -3
            "PB_NONREM12" -> -5
            "PB_NONREM3" -> -6
            else -> error("Unexpected sleep stage $this")
        }
        return PolarSleepWakeStateName.fromValue(value)?.name ?: error("Unexpected sleep stage value $value")
    }

    private fun String.defaultNightlyValue(): String {
        return when {
            this == "ansStatus" -> "0.0"
            this.endsWith("Tip") -> ""
            else -> "0"
        }
    }

    private fun String.scalarValue(field: String): String {
        return optionalScalarValue(field) ?: error("Missing scalar field $field in $this")
    }

    private fun String.optionalScalarValue(field: String): String? {
        val quoted = Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"").find(this)
        if (quoted != null) return quoted.groupValues[1]
        return Regex("\"$field\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(this)?.groupValues?.get(1)
    }

    private fun String.optionalObjectValue(field: String): String? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val objectStart = indexOf('{', fieldIndex)
        if (objectStart < 0) return null
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.optionalNullValue(field: String): Boolean? {
        return Regex("\"$field\"\\s*:\\s*null").find(this)?.let { true }
    }

    private fun String.signedIntValue(field: String): Int {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toInt() ?: error("Missing signed int field $field in $this")
    }

    private fun Int.padded(width: Int): String {
        val raw = toString()
        return "0".repeat(width - raw.length) + raw
    }

    private fun String.balancedEnd(start: Int, open: Char, close: Char): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until length) {
            val char = this[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = inString
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (!inString && char == open) depth += 1
            if (!inString && char == close) {
                depth -= 1
                if (depth == 0) return index
            }
        }
        error("Unbalanced $open$close block")
    }

    private data class SleepWakePhase(
        val secondsFromSleepStart: Int,
        val state: String
    )

    private data class DateParts(
        val year: Int,
        val month: Int,
        val day: Int
    )

    private companion object {
        const val MINUTES_PER_DAY = 1440
        val NIGHTLY_SCALAR_FIELDS = listOf(
            "ansStatus",
            "recoveryIndicator",
            "recoveryIndicatorSubLevel",
            "ansRate",
            "scoreRateObsolete",
            "meanNightlyRecoveryRRI",
            "meanNightlyRecoveryRMSSD",
            "meanNightlyRecoveryRespirationInterval",
            "meanBaselineRRI",
            "sdBaselineRRI",
            "meanBaselineRMSSD",
            "sdBaselineRMSSD",
            "meanBaselineRespirationInterval",
            "sdBaselineRespirationInterval",
            "sleepTip",
            "vitalityTip",
            "exerciseTip"
        )
        val NIGHTLY_RECHARGE_VECTORS = listOf(
            "sdk/nightly-recharge/full-status.json",
            "sdk/nightly-recharge/malformed-response.json",
            "sdk/nightly-recharge/missing-modified-default-metrics.json"
        )
        val SLEEP_VECTORS = listOf(
            "sdk/sleep/partial-night-omitted-optionals.json",
            "sdk/sleep/sleep-offset-platform-policy.json",
            "sdk/sleep/sleep-stage-hypnogram.json",
            "sdk/sleep/sleep-timezone-offsets.json"
        )
        val requiredSleepNightlyFamilies = listOf(
            "nightly-result-date-formatting",
            "nightly-created-modified-timestamp-formatting",
            "nightly-proto3-default-preservation",
            "nightly-malformed-payload-null-policy",
            "sleep-end-offset-field-policy",
            "sleep-timezone-to-utc-instant-policy",
            "sleep-hypnogram-order-preservation",
            "sleep-cycle-order-preservation",
            "sleep-stage-enum-mapping",
            "sleep-partial-night-optional-policy",
            "platform-sleep-nightly-vector-reference-gate",
            "compile-verification-gate"
        )
        const val SLEEP_PARTIAL_NIGHT_COMMON_DECISION = "Shared sleep mapping preserves empty repeated fields as empty lists, absent optional scalar defaults as explicit zero only when that is the existing public contract, and maps absent original sleep range plus absent sleep skin-temperature date to absent public submodels in shared-backed production code."
        const val SLEEP_OFFSET_PLATFORM_COMMON_DECISION = "Map sleepEndOffsetSeconds from the protobuf sleepEndOffsetSeconds field; linked iOS production code uses the shared KMP policy while non-shared SwiftPM/watchOS fallback preserves the legacy start-offset copy."
        const val SLEEP_NIGHTLY_READINESS_COMMON_DECISION = "Sleep and nightly recharge model migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS sleep/nightly tests continue to reference the same vectors, nightly date/timestamp/default and malformed-payload behavior stays covered, sleep end-offset, timezone, hypnogram, cycle, enum, and partial-night optional policies remain explicit, and the shared tests are compile-verified."
    }
}
