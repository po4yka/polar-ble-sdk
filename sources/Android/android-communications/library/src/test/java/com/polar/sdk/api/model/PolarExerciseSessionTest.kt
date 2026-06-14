package com.polar.sdk.api.model

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.FileReader

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

    @Test
    fun `exercise session readiness manifest is pinned for shared model ownership`() {
        val manifest = loadExerciseSessionReadinessManifest()
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val consumerTests = manifest.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        assertEquals("exercise-session-readiness", manifest.get("id").asString)
        assertEquals("exerciseSessionReadiness", input.get("kind").asString)
        assertEquals(EXERCISE_SESSION_READINESS_FAMILIES, requiredFamilies)
        assertEquals(EXERCISE_SESSION_READINESS_FAMILIES, coveredFamilies)
        assertEquals(EXERCISE_SESSION_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        assertEquals(listOf("com.polar.sdk.api.model.PolarExerciseSessionTest", "com.polar.sdk.impl.PolarOfflineExerciseV2ApiImplTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("PolarOfflineExerciseV2Tests"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.ExerciseSessionModelsCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun loadExerciseSessionReadinessManifest(): JsonObject {
        return FileReader(File(findRepositoryRoot(), "testdata/golden-vectors/sdk/exercise-session/exercise-session-readiness.json")).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
    }

    private fun findRepositoryRoot(): File {
        return generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { file -> file.parentFile }
            .first { file -> File(file, "testdata/golden-vectors/schema/golden-vector.schema.json").isFile }
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
