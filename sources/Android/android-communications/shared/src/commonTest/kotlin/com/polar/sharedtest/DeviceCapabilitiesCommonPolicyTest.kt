package com.polar.sharedtest

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
                assertEquals(expectedResult.stringValue("fileSystemType"), capabilityConfig.fileSystemType(deviceType), "$caseId filesystem $deviceType")
                assertEquals(expectedResult.booleanValue("recordingSupported"), capabilityConfig.recordingSupported(deviceType), "$caseId recording $deviceType")
                expectedResult.optionalBooleanValue("firmwareUpdateSupported")?.let { expected ->
                    assertEquals(expected, capabilityConfig.firmwareUpdateSupported(deviceType), "$caseId firmware $deviceType")
                }
                expectedResult.optionalBooleanValue("activityDataSupported")?.let { expected ->
                    assertEquals(expected, capabilityConfig.activityDataSupported(deviceType), "$caseId activity $deviceType")
                }
                expectedResult.optionalBooleanValue("isDeviceSensor")?.let { expected ->
                    assertEquals(expected, capabilityConfig.isDeviceSensor(deviceType), "$caseId sensor $deviceType")
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
        val mergedConfig = mergeConfigs(
            user = parseCapabilityConfig(input.objectValue("userConfig")),
            bundled = parseCapabilityConfig(input.objectValue("bundledConfig"))
        )

        assertEquals("deviceCapabilityConfigMerge", input.stringValue("kind"), caseId)
        assertEquals(expected.stringValue("mergedVersion"), mergedConfig.version, caseId)
        assertEquals("user-device-fields-win-missing-user-fields-fall-back-to-bundled-user-only-devices-survive-bundled-defaults-win", expected.stringValue("mergePolicy"), caseId)
        input.stringArrayValue("queries").zip(expected.objectArray("results")).forEach { (deviceType, expectedResult) ->
            assertEquals(expectedResult.stringValue("deviceType"), deviceType, caseId)
            assertEquals(expectedResult.stringValue("fileSystemType"), mergedConfig.fileSystemType(deviceType), "$caseId filesystem $deviceType")
            assertEquals(expectedResult.booleanValue("recordingSupported"), mergedConfig.recordingSupported(deviceType), "$caseId recording $deviceType")
            assertEquals(expectedResult.booleanValue("firmwareUpdateSupported"), mergedConfig.firmwareUpdateSupported(deviceType), "$caseId firmware $deviceType")
            assertEquals(expectedResult.booleanValue("activityDataSupported"), mergedConfig.activityDataSupported(deviceType), "$caseId activity $deviceType")
            assertEquals(expectedResult.booleanValue("isDeviceSensor"), mergedConfig.isDeviceSensor(deviceType), "$caseId sensor $deviceType")
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
        assertEquals("platformOwnedResourceBoundary", expected.stringValue("migrationReadiness"))
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
    fun deviceCapabilityReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
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
        assertEquals("coveredByPreMigrationCharacterization", expected.stringValue("migrationReadiness"))
        assertEquals(CAPABILITY_LOOKUP_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtilityTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarBleApiImplTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.DeviceCapabilitiesCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private fun parseCapabilityConfig(config: String): CapabilityConfig {
        val defaults = config.objectValue("defaults")
        val devices = config.objectValue("devices")
        val parsedDevices = DEVICE_TYPES.associateWith { deviceType ->
            devices.optionalObjectValue(deviceType)?.let { device ->
                Capability(
                    fileSystemType = device.optionalStringValue("fileSystemType"),
                    recordingSupported = device.optionalBooleanValue("recordingSupported"),
                    firmwareUpdateSupported = device.optionalBooleanValue("firmwareUpdateSupported"),
                    activityDataSupported = device.optionalBooleanValue("activityDataSupported"),
                    isDeviceSensor = device.optionalBooleanValue("isDeviceSensor")
                )
            }
        }.filterValues { it != null }.mapValues { it.value!! }

        return CapabilityConfig(
            version = config.stringValue("version"),
            devices = parsedDevices,
            defaults = Capability(
                fileSystemType = defaults.optionalStringValue("fileSystemType"),
                recordingSupported = defaults.optionalBooleanValue("recordingSupported"),
                firmwareUpdateSupported = defaults.optionalBooleanValue("firmwareUpdateSupported"),
                activityDataSupported = defaults.optionalBooleanValue("activityDataSupported"),
                isDeviceSensor = defaults.optionalBooleanValue("isDeviceSensor")
            )
        )
    }

    private fun mergeConfigs(user: CapabilityConfig, bundled: CapabilityConfig): CapabilityConfig {
        val mergedDevices = bundled.devices.toMutableMap()
        user.devices.forEach { (key, userDevice) ->
            val bundledDevice = bundled.devices[key]
            mergedDevices[key] = Capability(
                fileSystemType = userDevice.fileSystemType ?: bundledDevice?.fileSystemType,
                recordingSupported = userDevice.recordingSupported ?: bundledDevice?.recordingSupported,
                firmwareUpdateSupported = userDevice.firmwareUpdateSupported ?: bundledDevice?.firmwareUpdateSupported,
                activityDataSupported = userDevice.activityDataSupported ?: bundledDevice?.activityDataSupported,
                isDeviceSensor = userDevice.isDeviceSensor ?: bundledDevice?.isDeviceSensor
            )
        }
        return CapabilityConfig(
            version = bundled.version,
            devices = mergedDevices,
            defaults = bundled.defaults
        )
    }

    private data class CapabilityConfig(
        val version: String,
        val devices: Map<String, Capability>,
        val defaults: Capability
    ) {
        fun fileSystemType(deviceType: String): String {
            return when (devices[deviceType.lowerAscii()]?.fileSystemType ?: defaults.fileSystemType) {
                "H10_FILE_SYSTEM" -> "H10_FILE_SYSTEM"
                "POLAR_FILE_SYSTEM_V2" -> "POLAR_FILE_SYSTEM_V2"
                else -> "UNKNOWN_FILE_SYSTEM"
            }
        }

        fun recordingSupported(deviceType: String): Boolean {
            return devices[deviceType.lowerAscii()]?.recordingSupported ?: defaults.recordingSupported ?: false
        }

        fun firmwareUpdateSupported(deviceType: String): Boolean {
            return devices[deviceType.lowerAscii()]?.firmwareUpdateSupported ?: defaults.firmwareUpdateSupported ?: false
        }

        fun activityDataSupported(deviceType: String): Boolean {
            return devices[deviceType.lowerAscii()]?.activityDataSupported ?: defaults.activityDataSupported ?: false
        }

        fun isDeviceSensor(deviceType: String): Boolean {
            return devices[deviceType.lowerAscii()]?.isDeviceSensor ?: defaults.isDeviceSensor ?: false
        }

        private fun String.lowerAscii(): String {
            return map { char -> if (char in 'A'..'Z') char + 32 else char }.joinToString("")
        }
    }

    private data class Capability(
        val fileSystemType: String?,
        val recordingSupported: Boolean?,
        val firmwareUpdateSupported: Boolean?,
        val activityDataSupported: Boolean?,
        val isDeviceSensor: Boolean?
    )

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
        const val CAPABILITY_LOOKUP_COMMON_DECISION = "Capability lookup migration may proceed only after these vectors are executable from shared commonTest, Android and iOS tests continue to reference the same capability fixtures, firmware-update/activity/sensor flags and version-mismatch user-config merge behavior are covered by shared vectors, resource override behavior is explicitly kept platform-owned, and the shared tests are compile-verified."
        const val CAPABILITY_RESOURCE_PLATFORM_DECISION = "Resource selection and app-level override precedence remain platform-owned until common resource loading is deliberately introduced with Android asset/external-file tests, iOS Bundle.main/SDK-bundle/sandbox tests, and shared common parser tests."
        const val CAPABILITY_RESOURCE_COMMON_DECISION = "Shared KMP capability code may own parsing, lookup, defaults, boolean fields, and version-mismatch config merge, but must receive an already selected config and must not choose Android AssetManager, external Documents/PolarConfig files, iOS Bundle.main, SDK bundles, or sandbox files."
    }
}
