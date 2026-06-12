package com.polar.sdk.api.model

import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserDeviceSettings
import java.time.ZonedDateTime
import java.time.ZoneId

data class PolarUserDeviceSettings(val deviceLocation: Int? = null,
                                   val usbConnectionMode: Boolean? = null,
                                   val automaticTrainingDetectionMode: Boolean? = null,
                                   val automaticTrainingDetectionSensitivity: Int? = null,
                                   val telemetryEnabled: Boolean? = null,
                                   val minimumTrainingDurationSeconds: Int? = null,
                                   val autosFilesEnabled: Boolean? = null
) {
    private val sharedFields: PolarSdkModelAdapter.PlannedUserDeviceSettingsFields
        get() = PolarSdkModelAdapter.userDeviceSettingsFields(
            deviceLocation = deviceLocation,
            usbConnectionMode = usbConnectionMode,
            automaticTrainingDetectionMode = automaticTrainingDetectionMode,
            automaticTrainingDetectionSensitivity = automaticTrainingDetectionSensitivity,
            minimumTrainingDurationSeconds = minimumTrainingDurationSeconds,
            telemetryEnabled = telemetryEnabled,
            autosFilesEnabled = autosFilesEnabled
        )

    enum class DeviceLocation(val value: Int) {
        UNDEFINED(0),
        OTHER(1),
        WRIST_LEFT(2),
        WRIST_RIGHT(3),
        NECKLACE(4),
        CHEST(5),
        UPPER_BACK(6),
        FOOT_LEFT(7),
        FOOT_RIGHT(8),
        LOWER_ARM_LEFT(9),
        LOWER_ARM_RIGHT(10),
        UPPER_ARM_LEFT(11),
        UPPER_ARM_RIGHT(12),
        BIKE_MOUNT(13);
    }

    companion object {
        infix fun from(value: Int): DeviceLocation? {
            return PolarSdkModelAdapter.userDeviceSettingsDeviceLocationName(value)?.let(DeviceLocation::valueOf)
        }
        const val DEVICE_SETTINGS_FILENAME = "/U/0/S/UDEVSET.BPB"
        const val SENSOR_SETTINGS_FILENAME = "/UDEVSET.BPB"
    }

    fun toProto(): PbUserDeviceSettings {
        return PbUserDeviceSettings.parseFrom(
            PolarSdkModelAdapter.buildUserDeviceSettingsBytes(
                model = sharedFields,
                timestamp = createPlannedTimeStamp(),
                includeTelemetry = false
            )
        )
    }

    fun fromBytes(bytes: ByteArray): PolarUserDeviceSettings {
        val shared = PolarSdkModelAdapter.parseUserDeviceSettingsBytes(bytes)

        return PolarUserDeviceSettings(
            deviceLocation = shared.deviceLocation,
            usbConnectionMode = shared.usbConnectionMode,
            automaticTrainingDetectionMode = shared.automaticTrainingDetectionMode,
            automaticTrainingDetectionSensitivity = shared.automaticTrainingDetectionSensitivity ?: 0,
            telemetryEnabled = shared.telemetryEnabled,
            minimumTrainingDurationSeconds = shared.minimumTrainingDurationSeconds ?: 0,
            autosFilesEnabled = shared.autosFilesEnabled ?: true
        )
    }
}

private fun createPlannedTimeStamp(): PolarSdkModelAdapter.PlannedUserDeviceSettingsTimestamp {
    val utcTime = ZonedDateTime.now(ZoneId.of("UTC"))
    return PolarSdkModelAdapter.PlannedUserDeviceSettingsTimestamp(
        year = utcTime.year,
        month = utcTime.monthValue,
        day = utcTime.dayOfMonth,
        hour = utcTime.hour,
        minute = utcTime.minute,
        seconds = utcTime.second,
        millis = utcTime.nano / 1_000_000,
        trusted = true
    )
}
