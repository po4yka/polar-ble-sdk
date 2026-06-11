package com.polar.sharedtest

import com.polar.shared.sdk.PolarSdkFeatureAvailability
import kotlin.test.Test
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
}
