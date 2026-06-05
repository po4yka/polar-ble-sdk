package com.polar.sharedtest

import com.polar.shared.sdk.PolarExerciseSportProfileName
import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseSessionModelsCommonPolicyTest {
    @Test
    fun exerciseSportProfileMappingPreservesPublicIdsAndUnknownFallback() {
        assertEquals("UNKNOWN", PolarExerciseSportProfileName.fromId(0).name)
        assertEquals("RUNNING", PolarExerciseSportProfileName.fromId(1).name)
        assertEquals("CYCLING", PolarExerciseSportProfileName.fromId(2).name)
        assertEquals("OTHER_OUTDOOR", PolarExerciseSportProfileName.fromId(16).name)
        assertEquals(PolarExerciseSportProfileName.UNKNOWN, PolarExerciseSportProfileName.fromId(3))
        assertEquals(PolarExerciseSportProfileName.UNKNOWN, PolarExerciseSportProfileName.fromId(Int.MAX_VALUE))
    }
}
