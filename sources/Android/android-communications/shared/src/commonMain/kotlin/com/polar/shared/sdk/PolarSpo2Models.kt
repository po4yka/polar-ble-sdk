package com.polar.shared.sdk

object PolarSpo2Models {
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
