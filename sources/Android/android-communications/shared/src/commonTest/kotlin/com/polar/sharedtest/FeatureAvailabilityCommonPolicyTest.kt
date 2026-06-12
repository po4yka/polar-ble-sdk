package com.polar.sharedtest

import com.polar.shared.sdk.PolarSdkFeatureAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureAvailabilityCommonPolicyTest {
    @Test
    fun featureAvailabilityPreconditionsPinServiceAndCapabilityBoundaries() {
        assertTrue(
            PolarSdkFeatureAvailability.preconditionsMet(
                featureName = "FEATURE_POLAR_FIRMWARE_UPDATE",
                discoveredServices = setOf(PolarSdkFeatureAvailability.SERVICE_PSFTP),
                capabilities = setOf(PolarSdkFeatureAvailability.CAPABILITY_FIRMWARE_UPDATE)
            )
        )
        assertFalse(
            PolarSdkFeatureAvailability.preconditionsMet(
                featureName = "FEATURE_POLAR_FIRMWARE_UPDATE",
                discoveredServices = setOf(PolarSdkFeatureAvailability.SERVICE_PSFTP),
                capabilities = emptySet()
            )
        )
        assertTrue(
            PolarSdkFeatureAvailability.preconditionsMet(
                featureName = "feature_polar_watch_faces_configuration",
                discoveredServices = setOf(PolarSdkFeatureAvailability.SERVICE_PSFTP),
                capabilities = setOf(PolarSdkFeatureAvailability.CAPABILITY_NOT_SENSOR)
            )
        )
        assertFalse(
            PolarSdkFeatureAvailability.preconditionsMet(
                featureName = "feature_polar_watch_faces_configuration",
                discoveredServices = setOf(PolarSdkFeatureAvailability.SERVICE_PSFTP),
                capabilities = emptySet()
            )
        )
        assertFalse(
            PolarSdkFeatureAvailability.preconditionsMet(
                featureName = "FEATURE_POLAR_LED_ANIMATION",
                discoveredServices = setOf(PolarSdkFeatureAvailability.SERVICE_PMD),
                capabilities = emptySet()
            )
        )
    }

    @Test
    fun featureAvailabilityReadinessVectorExecutesSharedPreconditionPolicy() {
        val vector = loadGoldenVectorText("sdk/feature-availability/feature-availability-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val platforms = vector.objectValue("platforms")

        assertEquals("feature-availability-readiness", vector.stringValue("id"))
        assertEquals("featureAvailabilityReadiness", input.stringValue("kind"))
        input.objectArray("cases").forEach { case ->
            val caseId = case.stringValue("id")
            assertTrue(FEATURE_AVAILABILITY_CASE_IDS.contains(caseId), caseId)
            assertEquals(
                case.booleanValue("expectedAvailable"),
                PolarSdkFeatureAvailability.preconditionsMet(
                    featureName = case.stringValue("featureName"),
                    discoveredServices = case.stringArrayValue("discoveredServices").toSet(),
                    capabilities = case.stringArrayValue("capabilities").toSet()
                ),
                caseId
            )
        }
        assertEquals(FEATURE_AVAILABILITY_CASE_IDS, input.objectArray("cases").map { it.stringValue("id") })
        assertEquals(FEATURE_AVAILABILITY_BEHAVIOR_FAMILIES, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(FEATURE_AVAILABILITY_BEHAVIOR_FAMILIES, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals("coveredByPreMigrationCharacterization", expected.stringValue("migrationReadiness"))
        assertEquals(FEATURE_AVAILABILITY_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private companion object {
        val FEATURE_AVAILABILITY_CASE_IDS = listOf(
            "firmware-update-requires-psftp-and-firmware-capability",
            "firmware-update-missing-firmware-capability-is-unavailable",
            "led-animation-requires-pmd-and-psftp-services",
            "watch-face-configuration-requires-psftp-and-not-sensor-capability",
            "offline-exercise-v2-uses-h10-filesystem-capability-without-service-gate",
            "unknown-feature-has-no-shared-preconditions"
        )
        val FEATURE_AVAILABILITY_BEHAVIOR_FAMILIES = listOf(
            "service-and-capability-gates",
            "feature-name-normalization",
            "h10-filesystem-capability-only-gate",
            "unknown-feature-pass-through",
            "platform-client-readiness-boundary"
        )
        const val FEATURE_AVAILABILITY_COMMON_DECISION = "SDK feature availability migration owns only deterministic service and capability preconditions in shared KMP; GATT client lookup, clientReady waits, PMD feature reads, notification readiness, service discovery, BLE transport execution, and public callback/error behavior remain platform-owned."
    }
}
