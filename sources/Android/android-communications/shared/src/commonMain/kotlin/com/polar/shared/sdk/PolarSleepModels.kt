package com.polar.shared.sdk

enum class PolarSleepWakeStateName(val value: Int) {
    UNKNOWN(0),
    WAKE(-2),
    REM(-3),
    NONREM12(-5),
    NONREM3(-6);

    companion object {
        fun fromValue(value: Int): PolarSleepWakeStateName? {
            return entries.firstOrNull { state -> state.value == value }
        }
    }
}

enum class PolarSleepRatingName(val value: Int) {
    SLEPT_UNDEFINED(-1),
    SLEPT_POORLY(0),
    SLEPT_SOMEWHAT_POORLY(1),
    SLEPT_NEITHER_POORLY_NOR_WELL(2),
    SLEPT_SOMEWHAT_WELL(3),
    SLEPT_WELL(4);

    companion object {
        fun fromValue(value: Int): PolarSleepRatingName? {
            return entries.firstOrNull { rating -> rating.value == value }
        }
    }
}

object PolarSleepModels {
    fun sleepStartOffsetSeconds(sleepStartOffsetSeconds: Int): Int {
        return sleepStartOffsetSeconds
    }

    fun sleepEndOffsetSeconds(sleepEndOffsetSeconds: Int): Int {
        return sleepEndOffsetSeconds
    }

    fun shouldIncludeOriginalSleepRange(hasOriginalSleepRange: Boolean): Boolean {
        return hasOriginalSleepRange
    }

    fun shouldIncludeSleepSkinTemperatureResult(hasSleepDate: Boolean): Boolean {
        return hasSleepDate
    }

    fun sleepAnalysisPath(day: String): String {
        return "/U/0/$day/SLEEP/SLEEPRES.BPB"
    }

    fun sleepSkinTemperaturePath(day: String): String {
        return "/U/0/$day/NSTRES" + "U" + "L/NSTRCONT.BPB"
    }

    fun nightlyRechargePath(day: String): String {
        return "/U/0/$day/NR/NR.BPB"
    }
}
