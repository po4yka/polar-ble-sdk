package com.polar.shared.sdk

data class PolarUserDeviceSettingsFields(
    val deviceLocation: Int? = null,
    val usbConnectionMode: Boolean? = null,
    val automaticTrainingDetectionMode: Boolean? = null,
    val automaticTrainingDetectionSensitivity: Int? = null,
    val minimumTrainingDurationSeconds: Int? = null,
    val telemetryEnabled: Boolean? = null,
    val autosFilesEnabled: Boolean? = null
)

data class PolarSerializedUserDeviceSettingsFields(
    val deviceLocation: Int?,
    val hasLastModified: Boolean,
    val lastModifiedTrusted: Boolean,
    val usbConnectionMode: String?,
    val automaticTrainingDetectionMode: String?,
    val automaticTrainingDetectionSensitivity: Int?,
    val minimumTrainingDurationSeconds: Int?,
    val hasTelemetryEnabled: Boolean,
    val telemetryEnabled: Boolean?,
    val autosFilesEnabled: Boolean?,
    val omittedOptionalPolicy: String = "preserve-protobuf-presence",
    val telemetryWritePolicy: String = "write-explicit-telemetry"
)

object PolarUserDeviceSettingsModels {
    fun parsePresencePreservingFields(
        deviceLocation: Int? = null,
        usbConnectionMode: String? = null,
        automaticTrainingDetectionMode: String? = null,
        automaticTrainingDetectionSensitivity: Int? = null,
        minimumTrainingDurationSeconds: Int? = null,
        telemetryEnabled: Boolean? = null,
        autosFilesEnabled: Boolean? = null
    ): PolarUserDeviceSettingsFields {
        return PolarUserDeviceSettingsFields(
            deviceLocation = deviceLocation,
            usbConnectionMode = usbConnectionMode?.toOnOffBoolean(),
            automaticTrainingDetectionMode = automaticTrainingDetectionMode?.toOnOffBoolean(),
            automaticTrainingDetectionSensitivity = automaticTrainingDetectionSensitivity,
            minimumTrainingDurationSeconds = minimumTrainingDurationSeconds,
            telemetryEnabled = telemetryEnabled,
            autosFilesEnabled = autosFilesEnabled
        )
    }

    fun serializePresencePreservingFields(model: PolarUserDeviceSettingsFields): PolarSerializedUserDeviceSettingsFields {
        return PolarSerializedUserDeviceSettingsFields(
            deviceLocation = model.deviceLocation,
            hasLastModified = true,
            lastModifiedTrusted = true,
            usbConnectionMode = model.usbConnectionMode?.toOnOffName(),
            automaticTrainingDetectionMode = model.automaticTrainingDetectionMode?.toOnOffName(),
            automaticTrainingDetectionSensitivity = model.automaticTrainingDetectionSensitivity,
            minimumTrainingDurationSeconds = model.minimumTrainingDurationSeconds,
            hasTelemetryEnabled = model.telemetryEnabled != null,
            telemetryEnabled = model.telemetryEnabled,
            autosFilesEnabled = model.autosFilesEnabled
        )
    }

    fun deviceLocationName(value: Int): String? {
        return when (value) {
            0 -> "UNDEFINED"
            1 -> "OTHER"
            2 -> "WRIST_LEFT"
            3 -> "WRIST_RIGHT"
            4 -> "NECKLACE"
            5 -> "CHEST"
            6 -> "UPPER_BACK"
            7 -> "FOOT_LEFT"
            8 -> "FOOT_RIGHT"
            9 -> "LOWER_ARM_LEFT"
            10 -> "LOWER_ARM_RIGHT"
            11 -> "UPPER_ARM_LEFT"
            12 -> "UPPER_ARM_RIGHT"
            13 -> "BIKE_MOUNT"
            else -> null
        }
    }

    fun deviceLocationValue(name: String): Int? {
        return when (name) {
            "UNDEFINED" -> 0
            "OTHER" -> 1
            "WRIST_LEFT" -> 2
            "WRIST_RIGHT" -> 3
            "NECKLACE" -> 4
            "CHEST" -> 5
            "UPPER_BACK" -> 6
            "FOOT_LEFT" -> 7
            "FOOT_RIGHT" -> 8
            "LOWER_ARM_LEFT" -> 9
            "LOWER_ARM_RIGHT" -> 10
            "UPPER_ARM_LEFT" -> 11
            "UPPER_ARM_RIGHT" -> 12
            "BIKE_MOUNT" -> 13
            else -> null
        }
    }

    fun usbConnectionModeName(value: Int): String? {
        return when (value) {
            1 -> "OFF"
            2 -> "ON"
            else -> null
        }
    }

    fun usbConnectionModeValue(name: String): Int? {
        return when (name) {
            "OFF" -> 1
            "ON" -> 2
            else -> null
        }
    }

    fun automaticTrainingDetectionModeName(value: Int): String? {
        return when (value) {
            0 -> "OFF"
            1 -> "ON"
            else -> null
        }
    }

    fun automaticTrainingDetectionModeValue(name: String): Int? {
        return when (name) {
            "OFF" -> 0
            "ON" -> 1
            else -> null
        }
    }

    fun automaticMeasurementStateName(enabled: Boolean): String {
        return if (enabled) "ALWAYS_ON" else "OFF"
    }

    fun automaticMeasurementStateEnabled(name: String): Boolean? {
        return when (name) {
            "ALWAYS_ON" -> true
            "OFF" -> false
            else -> null
        }
    }

    private fun String.toOnOffBoolean(): Boolean {
        return when (this) {
            "ON" -> true
            "OFF" -> false
            else -> error("Unexpected ON/OFF value $this")
        }
    }

    private fun Boolean.toOnOffName(): String {
        return if (this) "ON" else "OFF"
    }
}
