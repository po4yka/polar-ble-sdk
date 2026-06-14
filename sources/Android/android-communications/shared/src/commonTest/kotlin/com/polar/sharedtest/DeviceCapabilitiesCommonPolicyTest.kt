package com.polar.sharedtest

import com.polar.shared.device.PolarDeviceCapabilities
import com.polar.shared.device.PolarDeviceCapabilitiesConfig
import com.polar.shared.device.PolarDeviceCapabilitiesLookup
import com.polar.shared.device.PolarDeviceCapabilityDefaults
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceCapabilitiesCommonPolicyTest {
    @Test
    fun deviceCapabilityGoldenVectorsDefineExecutableCommonLookupPolicy() {
        DEVICE_CAPABILITY_LOOKUP_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val config = input.objectValue("config")
            val expected = vector.objectValue("expected")
            val results = expected.objectArray("results")
            val capabilityConfig = parseCapabilityConfig(config)

            assertEquals("deviceCapabilityLookup", input.stringValue("kind"), caseId)
            input.stringArrayValue("queries").zip(results).forEach { (deviceType, expectedResult) ->
                assertEquals(expectedResult.stringValue("deviceType"), deviceType, caseId)
                val resolved = capabilityConfig.capability(deviceType)
                assertEquals(expectedResult.stringValue("fileSystemType"), resolved.fileSystemType.name, "$caseId filesystem $deviceType")
                assertEquals(expectedResult.booleanValue("recordingSupported"), resolved.recordingSupported, "$caseId recording $deviceType")
                expectedResult.optionalBooleanValue("firmwareUpdateSupported")?.let { expected ->
                    assertEquals(expected, resolved.firmwareUpdateSupported, "$caseId firmware $deviceType")
                }
                expectedResult.optionalBooleanValue("activityDataSupported")?.let { expected ->
                    assertEquals(expected, resolved.activityDataSupported, "$caseId activity $deviceType")
                }
                expectedResult.optionalBooleanValue("isDeviceSensor")?.let { expected ->
                    assertEquals(expected, resolved.isDeviceSensor, "$caseId sensor $deviceType")
                }
            }
        }
    }

    @Test
    fun deviceCapabilityMergeVectorDefinesExecutableCommonConfigMergePolicy() {
        val vector = loadGoldenVectorText(CAPABILITY_MERGE_VECTOR)
        val caseId = vector.stringValue("id")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val mergedConfig = PolarDeviceCapabilitiesLookup.mergeUserConfig(
            user = parseCapabilityConfig(input.objectValue("userConfig")),
            bundled = parseCapabilityConfig(input.objectValue("bundledConfig"))
        )

        assertEquals("deviceCapabilityConfigMerge", input.stringValue("kind"), caseId)
        assertEquals(expected.stringValue("mergedVersion"), mergedConfig.version, caseId)
        assertEquals("user-device-fields-win-missing-user-fields-fall-back-to-bundled-user-only-devices-survive-bundled-defaults-win", expected.stringValue("mergePolicy"), caseId)
        input.stringArrayValue("queries").zip(expected.objectArray("results")).forEach { (deviceType, expectedResult) ->
            val resolved = mergedConfig.capability(deviceType)
            assertEquals(expectedResult.stringValue("deviceType"), deviceType, caseId)
            assertEquals(expectedResult.stringValue("fileSystemType"), resolved.fileSystemType.name, "$caseId filesystem $deviceType")
            assertEquals(expectedResult.booleanValue("recordingSupported"), resolved.recordingSupported, "$caseId recording $deviceType")
            assertEquals(expectedResult.booleanValue("firmwareUpdateSupported"), resolved.firmwareUpdateSupported, "$caseId firmware $deviceType")
            assertEquals(expectedResult.booleanValue("activityDataSupported"), resolved.activityDataSupported, "$caseId activity $deviceType")
            assertEquals(expectedResult.booleanValue("isDeviceSensor"), resolved.isDeviceSensor, "$caseId sensor $deviceType")
        }
    }

    @Test
    fun deviceCapabilityResourceOverrideVectorKeepsPlatformResourceSelectionOutOfCommonCode() {
        val vector = loadGoldenVectorText(CAPABILITY_RESOURCE_OVERRIDE_VECTOR)
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")

        assertEquals("capability-resource-override-ownership", vector.stringValue("id"))
        assertEquals("deviceCapabilityResourceOverrideOwnership", input.stringValue("kind"))
        assertEquals(
            listOf(
                "AssetManager polar_device_capabilities.json plus external Documents/PolarConfig override",
                "Bundle.main polar_device_capabilities.json plus SDK bundle fallback and sandbox PolarConfig merge"
            ),
            input.objectArray("resourceSurfaces").map { it.stringValue("surface") }
        )
        assertEquals(
            listOf(
                "parse portable capability JSON",
                "merge version-mismatched user config with bundled config",
                "perform device-type lookup against an already selected config"
            ),
            input.stringArrayValue("commonScope")
        )
        assertEquals("platformOwnedResourceBoundary", expected.stringValue("sharedOwnershipStatus"))
        assertEquals(CAPABILITY_RESOURCE_PLATFORM_DECISION, expected.stringValue("platformDecision"))
        assertEquals(CAPABILITY_RESOURCE_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(
            listOf(
                "android-asset-and-external-config-boundary",
                "ios-main-bundle-sdk-bundle-sandbox-boundary",
                "common-selected-config-only-boundary"
            ),
            expected.stringArrayValue("coveredBehaviorFamilies")
        )
    }

    @Test
    fun deviceCapabilityReadinessManifestNamesEverySharedContractBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/device-capabilities/capability-lookup-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val platforms = manifest.objectValue("platforms")

        assertEquals("capability-lookup-readiness", manifest.stringValue("id"))
        assertEquals("deviceCapabilityLookupReadiness", input.stringValue("kind"))
        assertEquals(DEVICE_CAPABILITY_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(DEVICE_CAPABILITY_READINESS_FAMILIES, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(DEVICE_CAPABILITY_READINESS_FAMILIES, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals("coveredBySharedContractCharacterization", expected.stringValue("sharedOwnershipStatus"))
        assertEquals(CAPABILITY_LOOKUP_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtilityTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarBleApiImplTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.DeviceCapabilitiesCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private fun parseCapabilityConfig(config: String): PolarDeviceCapabilitiesConfig {
        val defaults = config.objectValue("defaults")
        val devices = config.objectValue("devices")
        val parsedDevices = DEVICE_TYPES.associateWith { deviceType ->
            devices.optionalObjectValue(deviceType)?.let { device ->
                PolarDeviceCapabilities(
                    fileSystemType = device.optionalStringValue("fileSystemType"),
                    recordingSupported = device.optionalBooleanValue("recordingSupported"),
                    firmwareUpdateSupported = device.optionalBooleanValue("firmwareUpdateSupported"),
                    activityDataSupported = device.optionalBooleanValue("activityDataSupported"),
                    isDeviceSensor = device.optionalBooleanValue("isDeviceSensor")
                )
            }
        }.filterValues { it != null }.mapValues { it.value!! }

        return PolarDeviceCapabilitiesConfig(
            version = config.stringValue("version"),
            devices = parsedDevices,
            defaults = PolarDeviceCapabilityDefaults(
                fileSystemType = defaults.optionalStringValue("fileSystemType") ?: "POLAR_FILE_SYSTEM_V2",
                recordingSupported = defaults.optionalBooleanValue("recordingSupported") ?: false,
                firmwareUpdateSupported = defaults.optionalBooleanValue("firmwareUpdateSupported") ?: true,
                activityDataSupported = defaults.optionalBooleanValue("activityDataSupported") ?: false,
                isDeviceSensor = defaults.optionalBooleanValue("isDeviceSensor") ?: false
            )
        )
    }

    private companion object {
        val DEVICE_TYPES = listOf("h10", "ignite3", "mystery", "partial", "newdevice", "legacy")
        val DEVICE_CAPABILITY_VECTORS = listOf(
            "sdk/device-capabilities/capability-boolean-flags.json",
            "sdk/device-capabilities/capability-config-merge.json",
            "sdk/device-capabilities/capability-lookup-basic.json",
            "sdk/device-capabilities/capability-lookup-default-h10.json",
            "sdk/device-capabilities/capability-resource-override-ownership.json"
        )
        val DEVICE_CAPABILITY_LOOKUP_VECTORS = listOf(
            "sdk/device-capabilities/capability-boolean-flags.json",
            "sdk/device-capabilities/capability-lookup-basic.json",
            "sdk/device-capabilities/capability-lookup-default-h10.json"
        )
        const val CAPABILITY_MERGE_VECTOR = "sdk/device-capabilities/capability-config-merge.json"
        const val CAPABILITY_RESOURCE_OVERRIDE_VECTOR = "sdk/device-capabilities/capability-resource-override-ownership.json"
        val DEVICE_CAPABILITY_READINESS_FAMILIES = listOf(
            "filesystem-type-mapping",
            "unknown-filesystem-default",
            "missing-device-defaults",
            "case-insensitive-device-type",
            "recording-support-defaults",
            "firmware-update-defaults",
            "activity-data-defaults",
            "sensor-device-defaults",
            "version-mismatch-user-config-merge",
            "resource-override-platform-ownership",
            "platform-capability-vector-references",
            "compile-verification-gate"
        )
        const val CAPABILITY_LOOKUP_COMMON_DECISION = "Capability lookup shared ownership remains valid while these vectors are executable from shared commonTest, Android and iOS tests continue to reference the same capability fixtures, firmware-update/activity/sensor flags and version-mismatch user-config merge behavior are covered by shared vectors, resource override behavior is explicitly kept platform-owned, and the shared tests are compile-verified."
        const val CAPABILITY_RESOURCE_PLATFORM_DECISION = "Resource selection and app-level override precedence remain platform-owned until common resource loading is deliberately introduced with Android asset/external-file tests, iOS Bundle.main/SDK-bundle/sandbox tests, and shared common parser tests."
        const val CAPABILITY_RESOURCE_COMMON_DECISION = "Shared shared capability code may own parsing, lookup, defaults, boolean fields, and version-mismatch config merge, but must receive an already selected config and must not choose Android AssetManager, external Documents/PolarConfig files, iOS Bundle.main, SDK bundles, or sandbox files."
    }
}
