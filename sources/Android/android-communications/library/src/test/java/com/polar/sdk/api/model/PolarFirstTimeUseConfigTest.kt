package com.polar.sdk.api.model

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fi.polar.remote.representation.protobuf.PhysData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.FileReader
import java.time.LocalDate

class PolarFirstTimeUseConfigTest {
    @Test
    fun `first time use physical config enum mapping preserves protobuf values`() {
        val config = PolarFirstTimeUseConfig(
            gender = PolarFirstTimeUseConfig.Gender.FEMALE,
            birthDate = LocalDate.of(1990, 1, 2),
            height = 170.0f,
            weight = 65.0f,
            maxHeartRate = 185,
            vo2Max = 45,
            restingHeartRate = 52,
            trainingBackground = 50,
            deviceTime = "2026-05-31T12:00:00Z",
            typicalDay = PolarFirstTimeUseConfig.TypicalDay.MOSTLY_MOVING,
            sleepGoalMinutes = 480
        )

        val proto = config.toProto()

        assertEquals(PhysData.PbUserGender.Gender.FEMALE, proto.gender.value)
        assertEquals(2, proto.gender.value.number)
        assertEquals(PhysData.PbUserTrainingBackground.TrainingBackground.SEMI_PRO, proto.trainingBackground.value)
        assertEquals(50, proto.trainingBackground.value.number)
        assertEquals(PhysData.PbUserTypicalDay.TypicalDay.MOSTLY_MOVING, proto.typicalDay.value)
        assertEquals(3, proto.typicalDay.value.number)

        val physicalConfiguration = proto.toPolarPhysicalConfiguration()

        assertEquals(PolarFirstTimeUseConfig.Gender.FEMALE, physicalConfiguration.gender)
    }

    @Test
    fun `first time use readiness manifest is pinned for shared model ownership`() {
        val manifest = loadFirstTimeUseReadinessManifest()
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val consumerTests = manifest.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        assertEquals("first-time-use-readiness", manifest.get("id").asString)
        assertEquals("firstTimeUseReadiness", input.get("kind").asString)
        assertEquals(FIRST_TIME_USE_READINESS_FAMILIES, requiredFamilies)
        assertEquals(FIRST_TIME_USE_READINESS_FAMILIES, coveredFamilies)
        assertEquals(FIRST_TIME_USE_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        assertEquals(listOf("com.polar.sdk.api.model.PolarFirstTimeUseConfigTest", "com.polar.sdk.impl.BDBleApiImplTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("PolarBleApiImplTests"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.FirstTimeUseModelsCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun loadFirstTimeUseReadinessManifest(): JsonObject {
        return FileReader(File(findRepositoryRoot(), "testdata/golden-vectors/sdk/first-time-use/first-time-use-readiness.json")).use { reader ->
            JsonParser.parseReader(reader).asJsonObject
        }
    }

    private fun findRepositoryRoot(): File {
        return generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { file -> file.parentFile }
            .first { file -> File(file, "testdata/golden-vectors/schema/golden-vector.schema.json").isFile }
    }

    private companion object {
        val FIRST_TIME_USE_READINESS_FAMILIES = listOf(
            "gender-enum-projection",
            "training-background-enum-projection",
            "typical-day-enum-projection",
            "unknown-enum-null-boundary",
            "physical-config-read-write-paths",
            "user-id-read-write-paths",
            "write-progress-policy-gate",
            "sync-sequencing-platform-boundary",
            "protobuf-construction-platform-boundary",
            "public-error-mapping-boundary",
            "platform-first-time-use-vector-reference-gate",
            "compile-verification-gate"
        )
        const val FIRST_TIME_USE_READINESS_COMMON_DECISION = "First-time-use shared ownership remains valid while this readiness manifest is executable from shared commonTest, Android and iOS first-time-use facade tests continue to pin physical config enum projection, unknown enum boundaries, physical-config and user-id file paths, write-progress policy, sync sequencing, protobuf construction boundaries, public error mapping boundaries, platform vector references, and compile verification before broader FTU execution moves."
    }
}
