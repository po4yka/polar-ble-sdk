// Copyright © 2025 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

import com.polar.shared.sdk.PolarExerciseSportProfileName
import java.time.LocalDateTime

/**
 * Represents a live exercise session on a Polar device.
 */
class PolarExerciseSession {

    /**
     * Supported sport profiles.
     */
    enum class SportProfile(val id: Int) {
        UNKNOWN(0),
        RUNNING(1),
        CYCLING(2),
        OTHER_OUTDOOR(16);

        companion object {
            /**
             * Resolve [SportProfile] from integer id.
             * Falls back to [UNKNOWN] if no match is found.
             */
            fun fromId(id: Int): SportProfile {
                return when (PolarExerciseSportProfileName.fromId(id)) {
                    PolarExerciseSportProfileName.UNKNOWN -> UNKNOWN
                    PolarExerciseSportProfileName.RUNNING -> RUNNING
                    PolarExerciseSportProfileName.CYCLING -> CYCLING
                    PolarExerciseSportProfileName.OTHER_OUTDOOR -> OTHER_OUTDOOR
                }
            }
        }
    }

    /**
     * Status of an exercise session.
     */
    enum class ExerciseStatus {
        NOT_STARTED,
        IN_PROGRESS,
        PAUSED,
        STOPPED,
        SYNC_REQUIRED
    }

    /**
     * High-level info of current session state.
     */
    data class ExerciseInfo(
        val status: ExerciseStatus,
        val sportProfile: SportProfile,
        val startTime: LocalDateTime? = null
    )
}
