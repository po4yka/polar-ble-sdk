package com.polar.sharedtest

import com.polar.shared.sdk.PolarSdLogMagnetometerFrequencyName
import com.polar.shared.sdk.PolarSdLogTriggerName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SdLogModelsCommonPolicyTest {
    @Test
    fun sdLogEnumMappingsPreservePublicNumericValues() {
        assertEquals("LOG_TRIGGER_SYSTEM", PolarSdLogTriggerName.fromValue(0)?.name)
        assertEquals("LOG_TRIGGER_FORCED", PolarSdLogTriggerName.fromValue(1)?.name)
        assertEquals("LOG_TRIGGER_EXERCISE", PolarSdLogTriggerName.fromValue(2)?.name)
        assertNull(PolarSdLogTriggerName.fromValue(3))

        assertEquals("MAG_LOG_10HZ", PolarSdLogMagnetometerFrequencyName.fromValue(1)?.name)
        assertEquals("MAG_LOG_50HZ", PolarSdLogMagnetometerFrequencyName.fromValue(2)?.name)
        assertEquals("MAG_LOG_100HZ", PolarSdLogMagnetometerFrequencyName.fromValue(3)?.name)
        assertNull(PolarSdLogMagnetometerFrequencyName.fromValue(0))
        assertNull(PolarSdLogMagnetometerFrequencyName.fromValue(4))
    }

    @Test
    fun sdLogReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/sd-log/sd-log-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val platforms = manifest.objectValue("platforms")

        assertEquals("sd-log-readiness", manifest.stringValue("id"))
        assertEquals("sdLogReadiness", input.stringValue("kind"))
        assertEquals(SD_LOG_READINESS_FAMILIES, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(SD_LOG_READINESS_FAMILIES, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(SD_LOG_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.sdk.impl.BDBleApiImplTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarBleApiImplTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.SdLogModelsCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private companion object {
        val SD_LOG_READINESS_FAMILIES = listOf(
            "log-trigger-enum-projection",
            "magnetometer-frequency-enum-projection",
            "unknown-enum-null-boundary",
            "sd-log-config-read-write-paths",
            "write-progress-policy-gate",
            "session-notification-platform-boundary",
            "protobuf-construction-platform-boundary",
            "optional-field-presence-boundary",
            "public-error-mapping-boundary",
            "platform-sd-log-vector-reference-gate",
            "compile-verification-gate"
        )
        const val SD_LOG_READINESS_COMMON_DECISION = "SD-log migration may proceed only after this readiness manifest is executable from shared commonTest, Android and iOS SD-log facade tests continue to pin trigger and magnetometer-frequency enum projection, unknown enum boundaries, SD-log config file paths, write-progress policy, session-notification boundaries, protobuf construction boundaries, optional field presence, public error mapping boundaries, platform vector references, and compile verification before broader SD-log execution moves."
    }
}
