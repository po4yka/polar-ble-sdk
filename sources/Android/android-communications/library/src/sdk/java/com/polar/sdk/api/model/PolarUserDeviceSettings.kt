package com.polar.sdk.api.model

import fi.polar.remote.representation.protobuf.Types.PbSystemDateTime
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbTime
import fi.polar.remote.representation.protobuf.Types.PbDeviceLocation
import fi.polar.remote.representation.protobuf.UserDeviceSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbAutomaticMeasurementSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserDeviceGeneralSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserDeviceSettings
import com.polar.shared.sdk.PolarUserDeviceSettingsFields
import com.polar.shared.sdk.PolarUserDeviceSettingsModels
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
    private val sharedFields: PolarUserDeviceSettingsFields
        get() = PolarUserDeviceSettingsFields(
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
            return PolarUserDeviceSettingsModels.deviceLocationName(value)?.let(DeviceLocation::valueOf)
        }
        const val DEVICE_SETTINGS_FILENAME = "/U/0/S/UDEVSET.BPB"
        const val SENSOR_SETTINGS_FILENAME = "/UDEVSET.BPB"
    }

    fun toProto(): PbUserDeviceSettings {
        val serialized = PolarUserDeviceSettingsModels.serializePresencePreservingFields(sharedFields)
        val pbSettingsWithDeviceLocation = PbUserDeviceGeneralSettings.newBuilder()
            .setDeviceLocation(serialized.deviceLocation?.let { PbDeviceLocation.forNumber(it) })

        val pbUsbConnectionSettings = UserDeviceSettings.PbUsbConnectionSettings.newBuilder()
        serialized.usbConnectionMode?.let {
            val sharedMode = PolarUserDeviceSettingsModels.usbConnectionModeValue(it)
                ?.let(UserDeviceSettings.PbUsbConnectionSettings.PbUsbConnectionMode::forNumber)
            val fallbackMode = if (it == "ON") {
                UserDeviceSettings.PbUsbConnectionSettings.PbUsbConnectionMode.ON
            } else {
                UserDeviceSettings.PbUsbConnectionSettings.PbUsbConnectionMode.OFF
            }
            pbUsbConnectionSettings.setMode(sharedMode ?: fallbackMode)
        }

        val pbAutomaticTrainingDetectionSettings = UserDeviceSettings.PbAutomaticTrainingDetectionSettings.newBuilder()
        val pbUserAutomaticMeasurementSettings = UserDeviceSettings.PbUserAutomaticMeasurementSettings.newBuilder()

        serialized.automaticTrainingDetectionMode?.let {
            val sharedState = PolarUserDeviceSettingsModels.automaticTrainingDetectionModeValue(it)
                ?.let(UserDeviceSettings.PbAutomaticTrainingDetectionSettings.PbAutomaticTrainingDetectionState::forNumber)
            val fallbackState = if (it == "ON") {
                UserDeviceSettings.PbAutomaticTrainingDetectionSettings.PbAutomaticTrainingDetectionState.ON
            } else {
                UserDeviceSettings.PbAutomaticTrainingDetectionSettings.PbAutomaticTrainingDetectionState.OFF
            }
            pbAutomaticTrainingDetectionSettings.setState(sharedState ?: fallbackState)
        }

        serialized.automaticTrainingDetectionSensitivity?.let {
            pbAutomaticTrainingDetectionSettings.setSensitivity(it)
        }

        serialized.minimumTrainingDurationSeconds?.let {
            pbAutomaticTrainingDetectionSettings.setMinimumTrainingDurationSeconds(it)
        }

        serialized.autosFilesEnabled?.let {
            val sharedState = PolarUserDeviceSettingsModels.automaticMeasurementStateName(it)
            val automaticMeasurementState = UserDeviceSettings.PbAutomaticMeasurementSettings.PbAutomaticMeasurementState.valueOf(sharedState)
            pbUserAutomaticMeasurementSettings.setAutomaticOhrMeasurement(
                PbAutomaticMeasurementSettings.newBuilder()
                    .setState(automaticMeasurementState)
            )
        }

        return PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(pbSettingsWithDeviceLocation.build())
            .setUsbConnectionSettings(pbUsbConnectionSettings.build())
            .setAutomaticMeasurementSettings(
                pbUserAutomaticMeasurementSettings.setAutomaticTrainingDetectionSettings(pbAutomaticTrainingDetectionSettings.build()).build()
            )
            .setLastModified(createTimeStamp())
            .build()
    }

    fun fromBytes(bytes: ByteArray): PolarUserDeviceSettings {
        val proto = PbUserDeviceSettings.parseFrom(bytes)
        val automaticTrainingDetectionSettings = if (proto.hasAutomaticMeasurementSettings() && proto.automaticMeasurementSettings.hasAutomaticTrainingDetectionSettings()) {
            proto.automaticMeasurementSettings.automaticTrainingDetectionSettings
        } else {
            null
        }
        val shared = PolarUserDeviceSettingsModels.parsePresencePreservingFields(
            deviceLocation = if (proto.hasGeneralSettings() && proto.generalSettings.hasDeviceLocation()) {
                proto.generalSettings.deviceLocation.number
            } else {
                null
            },
            usbConnectionMode = if (proto.hasUsbConnectionSettings() && proto.usbConnectionSettings.hasMode()) {
                proto.usbConnectionSettings.mode.name
            } else {
                null
            },
            automaticTrainingDetectionMode = automaticTrainingDetectionSettings?.state?.name,
            automaticTrainingDetectionSensitivity = if (automaticTrainingDetectionSettings?.hasSensitivity() == true) {
                automaticTrainingDetectionSettings.sensitivity
            } else {
                null
            },
            minimumTrainingDurationSeconds = if (automaticTrainingDetectionSettings?.hasMinimumTrainingDurationSeconds() == true) {
                automaticTrainingDetectionSettings.minimumTrainingDurationSeconds
            } else {
                null
            },
            telemetryEnabled = if (proto.hasTelemetrySettings() && proto.telemetrySettings.hasTelemetryEnabled()) {
                proto.telemetrySettings.telemetryEnabled
            } else {
                null
            },
            autosFilesEnabled = if (proto.hasAutomaticMeasurementSettings() &&
                proto.automaticMeasurementSettings.hasAutomaticOhrMeasurement() &&
                proto.automaticMeasurementSettings.automaticOhrMeasurement.hasState()
            ) {
                proto.automaticMeasurementSettings.automaticOhrMeasurement.state != PbAutomaticMeasurementSettings.PbAutomaticMeasurementState.OFF
            } else {
                null
            }
        )

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

private fun createTimeStamp(): PbSystemDateTime {

    val builder = PbSystemDateTime.newBuilder()
    val date = PbDate.newBuilder()
    val time = PbTime.newBuilder()

    val utcTime = ZonedDateTime.now(ZoneId.of("UTC"))

    date.day = utcTime.dayOfMonth
    date.month = utcTime.monthValue
    date.year = utcTime.year

    time.hour = utcTime.hour
    time.minute = utcTime.minute
    time.seconds = utcTime.second
    time.millis = utcTime.nano / 1_000_000

    builder.setDate(date)
    builder.setTime(time)
    builder.trusted = true
    return builder.build()
}
