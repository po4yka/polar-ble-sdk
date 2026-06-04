package com.polar.shared.device

data class PolarDeviceCapabilitiesConfig(
    val version: String = "1.0",
    val devices: Map<String, PolarDeviceCapabilities> = emptyMap(),
    val defaults: PolarDeviceCapabilityDefaults = PolarDeviceCapabilityDefaults()
) {
    fun capability(deviceType: String): PolarResolvedDeviceCapabilities {
        val device = devices[deviceType.lowerAscii()]
        return PolarResolvedDeviceCapabilities(
            fileSystemType = PolarFileSystemType.from(device?.fileSystemType ?: defaults.fileSystemType),
            recordingSupported = device?.recordingSupported ?: defaults.recordingSupported,
            firmwareUpdateSupported = device?.firmwareUpdateSupported ?: defaults.firmwareUpdateSupported,
            isDeviceSensor = device?.isDeviceSensor ?: defaults.isDeviceSensor,
            activityDataSupported = device?.activityDataSupported ?: defaults.activityDataSupported
        )
    }
}

data class PolarDeviceCapabilities(
    val fileSystemType: String? = null,
    val recordingSupported: Boolean? = null,
    val firmwareUpdateSupported: Boolean? = null,
    val isDeviceSensor: Boolean? = null,
    val activityDataSupported: Boolean? = null
)

data class PolarDeviceCapabilityDefaults(
    val fileSystemType: String = "POLAR_FILE_SYSTEM_V2",
    val recordingSupported: Boolean = false,
    val firmwareUpdateSupported: Boolean = true,
    val isDeviceSensor: Boolean = false,
    val activityDataSupported: Boolean = false
)

data class PolarResolvedDeviceCapabilities(
    val fileSystemType: PolarFileSystemType,
    val recordingSupported: Boolean,
    val firmwareUpdateSupported: Boolean,
    val isDeviceSensor: Boolean,
    val activityDataSupported: Boolean
)

enum class PolarFileSystemType {
    UNKNOWN_FILE_SYSTEM,
    H10_FILE_SYSTEM,
    POLAR_FILE_SYSTEM_V2;

    companion object {
        fun from(value: String?): PolarFileSystemType {
            return when (value) {
                "H10_FILE_SYSTEM" -> H10_FILE_SYSTEM
                "POLAR_FILE_SYSTEM_V2" -> POLAR_FILE_SYSTEM_V2
                else -> UNKNOWN_FILE_SYSTEM
            }
        }
    }
}

object PolarDeviceCapabilitiesLookup {
    fun mergeUserConfig(user: PolarDeviceCapabilitiesConfig, bundled: PolarDeviceCapabilitiesConfig): PolarDeviceCapabilitiesConfig {
        val mergedDevices = bundled.devices.toMutableMap()
        user.devices.forEach { (key, userDevice) ->
            val bundledDevice = bundled.devices[key]
            mergedDevices[key] = PolarDeviceCapabilities(
                fileSystemType = userDevice.fileSystemType ?: bundledDevice?.fileSystemType,
                recordingSupported = userDevice.recordingSupported ?: bundledDevice?.recordingSupported,
                firmwareUpdateSupported = userDevice.firmwareUpdateSupported ?: bundledDevice?.firmwareUpdateSupported,
                isDeviceSensor = userDevice.isDeviceSensor ?: bundledDevice?.isDeviceSensor,
                activityDataSupported = userDevice.activityDataSupported ?: bundledDevice?.activityDataSupported
            )
        }
        return PolarDeviceCapabilitiesConfig(
            version = bundled.version,
            devices = mergedDevices,
            defaults = bundled.defaults
        )
    }
}

private fun String.lowerAscii(): String {
    return map { char -> if (char in 'A'..'Z') char + 32 else char }.joinToString("")
}
