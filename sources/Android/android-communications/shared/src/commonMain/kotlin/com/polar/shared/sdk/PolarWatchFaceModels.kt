package com.polar.shared.sdk

data class PolarWatchFaceFields(
    val timeStyleId: Int = 0,
    val complicationLayoutId: Int = 0,
    val backgroundStyleId: Int = 0,
    val accentColor: Long = 0L,
    val complicationIds: List<Int> = emptyList(),
    val fontfaceId: Int = 0
) {
    companion object {
        fun fromNullableFields(
            timeStyleId: Int?,
            complicationLayoutId: Int?,
            backgroundStyleId: Int?,
            accentColor: Long?,
            complicationIds: List<Int>?,
            fontfaceId: Int?
        ): PolarWatchFaceFields {
            return PolarWatchFaceFields(
                timeStyleId = timeStyleId ?: 0,
                complicationLayoutId = complicationLayoutId ?: 0,
                backgroundStyleId = backgroundStyleId ?: 0,
                accentColor = accentColor ?: 0L,
                complicationIds = complicationIds ?: emptyList(),
                fontfaceId = fontfaceId ?: 0
            )
        }
    }
}

enum class PolarWatchFaceComplicationName(val complicationId: String) {
    ALARM("alarm-complication"),
    ALTITUDE("altitude-complication"),
    ACTIVITY("activity-percentage-complication"),
    BATTERY("battery-complication"),
    BREATHING_EXERCISE("serene-complication"),
    CALORIES("calories-complication"),
    COMPASS("compass-complication"),
    COUNTDOWN_TIMER("countdownTimer-complication"),
    DATE("date-complication"),
    DAYLIGHT("daylight-complication"),
    ECG("ecg-complication"),
    EMPTY(""),
    FLASHLIGHT("flashlight-complication"),
    HEART_RATE("heart-rate-complication"),
    JUMP_TEST("jump-test-complication"),
    LATEST_TRAINING("latest-training-complication"),
    NAVIGATION("navigation-complication"),
    NIGHTLY_RECHARGE("nightly-recharge-complication"),
    POLAR_LOGO("polar-logo-complication"),
    SECONDS_ANALOG("analog-seconds-complication"),
    SECONDS_DIGITAL("digital-seconds-complication"),
    SPO2("spo2-complication"),
    TIMER("timer-complication"),
    USER_NAME("user-name-complication"),
    WEATHER("weather-complication"),
    WEEKLY_SUMMARY("weeklysummary-complication");

    val id: Int get() = complicationId.javaStringHashCode()

    companion object {
        fun fromId(id: Int): PolarWatchFaceComplicationName? {
            return entries.firstOrNull { complication -> complication.id == id }
        }

        fun idFor(complicationId: String): Int {
            return complicationId.javaStringHashCode()
        }
    }
}

private fun String.javaStringHashCode(): Int {
    var hash = 0
    for (char in this) {
        hash = hash * 31 + char.code
    }
    return hash
}
