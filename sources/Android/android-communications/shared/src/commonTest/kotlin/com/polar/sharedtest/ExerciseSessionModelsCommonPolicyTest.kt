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

    @Test
    fun exerciseSessionReadinessManifestNamesEverySharedContractBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/exercise-session/exercise-session-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val platforms = manifest.objectValue("platforms")

        assertEquals("exercise-session-readiness", manifest.stringValue("id"))
        assertEquals("exerciseSessionReadiness", input.stringValue("kind"))
        assertEquals(EXERCISE_SESSION_READINESS_FAMILIES, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(EXERCISE_SESSION_READINESS_FAMILIES, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(EXERCISE_SESSION_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.sdk.api.model.PolarExerciseSessionTest", "com.polar.sdk.impl.PolarOfflineExerciseV2ApiImplTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarOfflineExerciseV2Tests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.ExerciseSessionModelsCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private companion object {
        val EXERCISE_SESSION_READINESS_FAMILIES = listOf(
            "sport-profile-id-mapping",
            "unknown-sport-profile-fallback",
            "offline-exercise-start-command-planning",
            "offline-exercise-stop-command-planning",
            "offline-exercise-status-command-planning",
            "offline-exercise-file-read-remove-paths",
            "offline-exercise-device-info-path",
            "protobuf-construction-platform-boundary",
            "status-result-platform-boundary",
            "public-error-mapping-boundary",
            "platform-exercise-session-vector-reference-gate",
            "compile-verification-gate"
        )
        const val EXERCISE_SESSION_READINESS_COMMON_DECISION = "Exercise-session shared ownership remains valid while this readiness manifest is executable from shared commonTest, Android and iOS exercise-session tests continue to pin sport-profile ID mapping, unknown sport-profile fallback, offline exercise command planning, offline exercise file read/remove paths, device-info path planning, protobuf construction boundaries, status-result platform boundaries, public error mapping boundaries, platform vector references, and compile verification before broader exercise execution moves."
    }
}
