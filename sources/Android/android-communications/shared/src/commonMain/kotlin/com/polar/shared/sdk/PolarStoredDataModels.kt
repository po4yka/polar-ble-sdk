package com.polar.shared.sdk

object PolarStoredDataModels {
    fun iosStoredDataTypeName(value: Int): String? {
        return when (value) {
            0 -> "UNDEFINED"
            1 -> "ACTIVITY"
            2 -> "AUTO_SAMPLE"
            3 -> "DAILY_SUMMARY"
            4 -> "NIGHTLY_RECOVERY"
            5 -> "SDLOGS"
            6 -> "SLEEP"
            7 -> "SLEEP_SCORE"
            8 -> "SKIN_CONTACT_CHANGES"
            9 -> "SKINTEMP"
            else -> null
        }
    }

    fun iosStoredDataTypeValue(name: String): Int? {
        return when (name) {
            "UNDEFINED" -> 0
            "ACTIVITY" -> 1
            "AUTO_SAMPLE" -> 2
            "DAILY_SUMMARY" -> 3
            "NIGHTLY_RECOVERY" -> 4
            "SDLOGS" -> 5
            "SLEEP" -> 6
            "SLEEP_SCORE" -> 7
            "SKIN_CONTACT_CHANGES" -> 8
            "SKINTEMP" -> 9
            else -> null
        }
    }
}
