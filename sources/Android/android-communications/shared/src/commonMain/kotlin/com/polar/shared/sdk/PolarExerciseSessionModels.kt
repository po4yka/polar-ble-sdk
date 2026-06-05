package com.polar.shared.sdk

enum class PolarExerciseSportProfileName(val id: Int) {
    UNKNOWN(0),
    RUNNING(1),
    CYCLING(2),
    OTHER_OUTDOOR(16);

    companion object {
        fun fromId(id: Int): PolarExerciseSportProfileName {
            return entries.firstOrNull { profile -> profile.id == id } ?: UNKNOWN
        }
    }
}
