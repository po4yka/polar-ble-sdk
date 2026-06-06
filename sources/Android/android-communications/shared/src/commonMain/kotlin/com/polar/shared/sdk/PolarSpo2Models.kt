package com.polar.shared.sdk

data class PolarSpo2TestProjection(
    val policy: String,
    val recordingDevice: String?,
    val sourceDateTimeKey: String,
    val timeZoneOffsetMinutes: Int,
    val testStatus: String?,
    val bloodOxygenPercent: Int?,
    val spo2Class: String?,
    val spo2ValueDeviationFromBaseline: String?,
    val spo2QualityAveragePercent: Float?,
    val averageHeartRateBpm: Int?,
    val heartRateVariabilityMs: Float?,
    val spo2HrvDeviationFromBaseline: String?,
    val altitudeMeters: Float?,
    val triggerType: String?
)

object PolarSpo2Models {
    fun testDirectoryPath(day: String): String {
        return "/U/0/$day/SPO2TEST/"
    }

    fun testResultPath(directoryPath: String, subDirectoryName: String): String {
        return "$directoryPath${subDirectoryName}SPO2TRES.BPB"
    }

    fun projectTestData(
        date: String,
        timeDirName: String,
        recordingDevice: String?,
        timeZoneOffsetMinutes: Int,
        testStatus: Int,
        bloodOxygenPercent: Int?,
        spo2Class: Int?,
        spo2ValueDeviationFromBaseline: Int?,
        spo2QualityAveragePercent: Float?,
        averageHeartRateBpm: Int?,
        heartRateVariabilityMs: Float?,
        spo2HrvDeviationFromBaseline: Int?,
        altitudeMeters: Float?,
        triggerType: Int?
    ): PolarSpo2TestProjection {
        return PolarSpo2TestProjection(
            policy = when {
                spo2Class == 99 -> "document-spo2-unknown-enum-platform-difference"
                triggerType != null -> "map-spo2-trigger-type-when-platform-exposes-it"
                bloodOxygenPercent == null -> "preserve-spo2-optional-field-presence"
                else -> "map-spo2-proto-fields-to-public-model"
            },
            recordingDevice = recordingDevice?.takeIf { value -> value.isNotEmpty() },
            sourceDateTimeKey = "${date}T$timeDirName",
            timeZoneOffsetMinutes = timeZoneOffsetMinutes,
            testStatus = testStatusName(testStatus),
            bloodOxygenPercent = bloodOxygenPercent,
            spo2Class = spo2Class?.let(::spo2ClassName),
            spo2ValueDeviationFromBaseline = spo2ValueDeviationFromBaseline?.let(::deviationFromBaselineName),
            spo2QualityAveragePercent = spo2QualityAveragePercent,
            averageHeartRateBpm = averageHeartRateBpm,
            heartRateVariabilityMs = heartRateVariabilityMs,
            spo2HrvDeviationFromBaseline = spo2HrvDeviationFromBaseline?.let(::deviationFromBaselineName),
            altitudeMeters = altitudeMeters,
            triggerType = triggerType?.let(::triggerTypeName)
        )
    }

    fun testStatusName(value: Int): String? {
        return when (value) {
            0 -> "passed"
            1 -> "inconclusiveTooLowQualityInSamples"
            2 -> "inconclusiveTooLowOverallQuality"
            3 -> "inconclusiveTooManyMissingSamples"
            else -> null
        }
    }

    fun spo2ClassName(value: Int): String? {
        return when (value) {
            0 -> "unknown"
            1 -> "veryLow"
            2 -> "low"
            3 -> "normal"
            else -> null
        }
    }

    fun deviationFromBaselineName(value: Int): String? {
        return when (value) {
            0 -> "noBaseline"
            1 -> "belowUsual"
            2 -> "usual"
            3 -> "aboveUsual"
            else -> null
        }
    }

    fun triggerTypeName(value: Int): String? {
        return when (value) {
            0 -> "manual"
            1 -> "automatic"
            else -> null
        }
    }
}
