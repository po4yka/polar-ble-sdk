package com.polar.shared.sdk

data class PolarFeatureAvailabilityPreconditions(
    val requiredServices: Set<String> = emptySet(),
    val requiredCapabilities: Set<String> = emptySet()
)

object PolarSdkFeatureAvailability {
    const val SERVICE_HR = "HR"
    const val SERVICE_DEVICE_INFO = "DEVICE_INFO"
    const val SERVICE_BATTERY = "BATTERY"
    const val SERVICE_PMD = "PMD"
    const val SERVICE_PSFTP = "PSFTP"
    const val SERVICE_HTS = "HTS"
    const val SERVICE_PFC = "PFC"

    const val CAPABILITY_RECORDING = "RECORDING"
    const val CAPABILITY_ACTIVITY_DATA = "ACTIVITY_DATA"
    const val CAPABILITY_FIRMWARE_UPDATE = "FIRMWARE_UPDATE"
    const val CAPABILITY_H10_FILE_SYSTEM = "H10_FILE_SYSTEM"
    const val CAPABILITY_NOT_SENSOR = "NOT_SENSOR"

    fun preconditions(featureName: String): PolarFeatureAvailabilityPreconditions {
        return when (featureName.normalizedFeatureName()) {
            "FEATURE_HR" -> services(SERVICE_HR)
            "FEATURE_DEVICE_INFO" -> services(SERVICE_DEVICE_INFO)
            "FEATURE_BATTERY_INFO" -> services(SERVICE_BATTERY)
            "FEATURE_POLAR_ONLINE_STREAMING" -> services(SERVICE_PMD)
            "FEATURE_POLAR_OFFLINE_RECORDING" -> services(SERVICE_PMD, SERVICE_PSFTP)
            "FEATURE_POLAR_DEVICE_TIME_SETUP" -> services(SERVICE_PSFTP)
            "FEATURE_POLAR_SDK_MODE" -> services(SERVICE_PMD)
            "FEATURE_POLAR_H10_EXERCISE_RECORDING" -> services(SERVICE_PSFTP, capabilities = setOf(CAPABILITY_RECORDING))
            "FEATURE_POLAR_OFFLINE_EXERCISE_V2" -> PolarFeatureAvailabilityPreconditions(requiredCapabilities = setOf(CAPABILITY_H10_FILE_SYSTEM))
            "FEATURE_POLAR_FILE_TRANSFER" -> services(SERVICE_PSFTP)
            "FEATURE_HTS" -> services(SERVICE_HTS)
            "FEATURE_POLAR_LED_ANIMATION" -> services(SERVICE_PMD, SERVICE_PSFTP)
            "FEATURE_POLAR_FIRMWARE_UPDATE" -> services(SERVICE_PSFTP, capabilities = setOf(CAPABILITY_FIRMWARE_UPDATE))
            "FEATURE_POLAR_ACTIVITY_DATA",
            "FEATURE_POLAR_SLEEP_DATA",
            "FEATURE_POLAR_TEMPERATURE_DATA" -> services(SERVICE_PSFTP, capabilities = setOf(CAPABILITY_ACTIVITY_DATA))
            "FEATURE_POLAR_TRAINING_DATA",
            "FEATURE_POLAR_DEVICE_CONTROL",
            "FEATURE_POLAR_SPO2_TEST_DATA" -> services(SERVICE_PSFTP)
            "FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE" -> services(SERVICE_PFC)
            "FEATURE_WATCH_FACES_CONFIGURATION" -> services(SERVICE_PSFTP, capabilities = setOf(CAPABILITY_NOT_SENSOR))
            else -> PolarFeatureAvailabilityPreconditions()
        }
    }

    fun preconditionsMet(
        featureName: String,
        discoveredServices: Set<String>,
        capabilities: Set<String>
    ): Boolean {
        val preconditions = preconditions(featureName)
        return discoveredServices.containsAll(preconditions.requiredServices) &&
            capabilities.containsAll(preconditions.requiredCapabilities)
    }

    private fun services(
        vararg services: String,
        capabilities: Set<String> = emptySet()
    ): PolarFeatureAvailabilityPreconditions {
        return PolarFeatureAvailabilityPreconditions(
            requiredServices = services.toSet(),
            requiredCapabilities = capabilities
        )
    }

    private fun String.normalizedFeatureName(): String {
        return uppercase()
            .removePrefix("POLARBLESDKFEATURE.")
            .removePrefix("POLARBLESDKFEATURE_")
            .let { if (it.startsWith("FEATURE_")) it else "FEATURE_$it" }
            .replace("FEATURE_POLAR_WATCH_FACES_CONFIGURATION", "FEATURE_WATCH_FACES_CONFIGURATION")
    }
}
