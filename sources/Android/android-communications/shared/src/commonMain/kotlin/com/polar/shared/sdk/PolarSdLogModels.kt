package com.polar.shared.sdk

enum class PolarSdLogTriggerName(val value: Int) {
    LOG_TRIGGER_SYSTEM(0),
    LOG_TRIGGER_FORCED(1),
    LOG_TRIGGER_EXERCISE(2);

    companion object {
        fun fromValue(value: Int): PolarSdLogTriggerName? {
            return entries.firstOrNull { trigger -> trigger.value == value }
        }
    }
}

enum class PolarSdLogMagnetometerFrequencyName(val value: Int) {
    MAG_LOG_10HZ(1),
    MAG_LOG_50HZ(2),
    MAG_LOG_100HZ(3);

    companion object {
        fun fromValue(value: Int): PolarSdLogMagnetometerFrequencyName? {
            return entries.firstOrNull { frequency -> frequency.value == value }
        }
    }
}
