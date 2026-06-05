package com.polar.sdk.api.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PolarExerciseSessionTest {
    @Test
    fun `sport profile fromId preserves public ids and unknown fallback`() {
        assertEquals(PolarExerciseSession.SportProfile.UNKNOWN, PolarExerciseSession.SportProfile.fromId(0))
        assertEquals(PolarExerciseSession.SportProfile.RUNNING, PolarExerciseSession.SportProfile.fromId(1))
        assertEquals(PolarExerciseSession.SportProfile.CYCLING, PolarExerciseSession.SportProfile.fromId(2))
        assertEquals(PolarExerciseSession.SportProfile.OTHER_OUTDOOR, PolarExerciseSession.SportProfile.fromId(16))
        assertEquals(PolarExerciseSession.SportProfile.UNKNOWN, PolarExerciseSession.SportProfile.fromId(3))
        assertEquals(PolarExerciseSession.SportProfile.UNKNOWN, PolarExerciseSession.SportProfile.fromId(Int.MAX_VALUE))
    }
}
