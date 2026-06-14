package com.polar.sharedtest

import com.polar.shared.sdk.PolarFirstTimeUseGenderName
import com.polar.shared.sdk.PolarFirstTimeUseTrainingBackgroundName
import com.polar.shared.sdk.PolarFirstTimeUseTypicalDayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FirstTimeUseModelsCommonPolicyTest {
    @Test
    fun firstTimeUseEnumMappingsPreservePublicPhysicalConfigValues() {
        assertEquals("MALE", PolarFirstTimeUseGenderName.fromValue(1)?.name)
        assertEquals(2, PolarFirstTimeUseGenderName.fromName("FEMALE")?.value)
        assertNull(PolarFirstTimeUseGenderName.fromValue(0))
        assertNull(PolarFirstTimeUseGenderName.fromName("UNKNOWN"))

        assertEquals("OCCASIONAL", PolarFirstTimeUseTrainingBackgroundName.fromValue(10)?.name)
        assertEquals(20, PolarFirstTimeUseTrainingBackgroundName.fromValue(20)?.value)
        assertEquals("FREQUENT", PolarFirstTimeUseTrainingBackgroundName.fromValue(30)?.name)
        assertEquals("HEAVY", PolarFirstTimeUseTrainingBackgroundName.fromValue(40)?.name)
        assertEquals("SEMI_PRO", PolarFirstTimeUseTrainingBackgroundName.fromValue(50)?.name)
        assertEquals("PRO", PolarFirstTimeUseTrainingBackgroundName.fromValue(60)?.name)
        assertNull(PolarFirstTimeUseTrainingBackgroundName.fromValue(0))
        assertNull(PolarFirstTimeUseTrainingBackgroundName.fromValue(70))

        assertEquals("MOSTLY_SITTING", PolarFirstTimeUseTypicalDayName.fromValue(1)?.name)
        assertEquals("MOSTLY_STANDING", PolarFirstTimeUseTypicalDayName.fromValue(2)?.name)
        assertEquals("MOSTLY_MOVING", PolarFirstTimeUseTypicalDayName.fromValue(3)?.name)
        assertNull(PolarFirstTimeUseTypicalDayName.fromValue(0))
        assertNull(PolarFirstTimeUseTypicalDayName.fromValue(4))
    }

    @Test
    fun firstTimeUseReadinessManifestNamesEverySharedContractBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/first-time-use/first-time-use-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val platforms = manifest.objectValue("platforms")

        assertEquals("first-time-use-readiness", manifest.stringValue("id"))
        assertEquals("firstTimeUseReadiness", input.stringValue("kind"))
        assertEquals(FIRST_TIME_USE_READINESS_FAMILIES, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(FIRST_TIME_USE_READINESS_FAMILIES, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(FIRST_TIME_USE_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.sdk.api.model.PolarFirstTimeUseConfigTest", "com.polar.sdk.impl.BDBleApiImplTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarBleApiImplTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.FirstTimeUseModelsCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
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
