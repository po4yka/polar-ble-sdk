package com.polar.sdk.api.model

import com.polar.shared.device.PolarDeviceId
import com.polar.shared.runtime.PolarD2hRuntimePlanning
import com.polar.shared.sdk.PolarActivityClassName
import com.polar.shared.sdk.PolarAutomaticHrTriggerName
import com.polar.shared.sdk.PolarDailyBalanceFeedbackName
import com.polar.shared.sdk.PolarExerciseSportProfileName
import com.polar.shared.sdk.PolarFirstTimeUseGenderName
import com.polar.shared.sdk.PolarFirstTimeUseTrainingBackgroundName
import com.polar.shared.sdk.PolarFirstTimeUseTypicalDayName
import com.polar.shared.sdk.PolarPpiIntervalStatusName
import com.polar.shared.sdk.PolarPpiMovementName
import com.polar.shared.sdk.PolarPpiSampleTriggerName
import com.polar.shared.sdk.PolarPpiSkinContactName
import com.polar.shared.sdk.PolarPpiStatusNames
import com.polar.shared.sdk.PolarRestServiceModels
import com.polar.shared.sdk.PolarSdLogMagnetometerFrequencyName
import com.polar.shared.sdk.PolarSdLogTriggerName
import com.polar.shared.sdk.PolarSdkModelMappers
import com.polar.shared.sdk.PolarSleepRatingName
import com.polar.shared.sdk.PolarSleepWakeStateName
import com.polar.shared.sdk.PolarTrainingReadinessName
import com.polar.shared.sdk.PolarUserDeviceSettingsFields
import com.polar.shared.sdk.PolarUserDeviceSettingsModels
import com.polar.shared.sdk.PolarWatchFaceComplicationName

internal object PolarSdkModelAdapter {
    data class PlannedDiskSpace(
        val totalSpace: Long,
        val freeSpace: Long
    )
    data class PlannedRestServiceList(
        val pathsForServices: Map<String, String>,
        val names: List<String>,
        val paths: List<String>
    )
    data class PlannedRestServiceDescription(
        val events: List<String>,
        val endpoints: List<String>,
        val actions: Map<String, String>,
        val actionNames: List<String>,
        val actionPaths: List<String>,
        val details: Map<String, List<String>>,
        val triggers: Map<String, List<String>>
    )
    data class PlannedPpiStatusNames(
        val skinContact: String,
        val movement: String,
        val intervalStatus: String
    )
    data class PlannedUserDeviceSettingsFields(
        val deviceLocation: Int? = null,
        val usbConnectionMode: Boolean? = null,
        val automaticTrainingDetectionMode: Boolean? = null,
        val automaticTrainingDetectionSensitivity: Int? = null,
        val minimumTrainingDurationSeconds: Int? = null,
        val telemetryEnabled: Boolean? = null,
        val autosFilesEnabled: Boolean? = null
    )
    data class PlannedSerializedUserDeviceSettingsFields(
        val deviceLocation: Int?,
        val usbConnectionMode: String?,
        val automaticTrainingDetectionMode: String?,
        val automaticTrainingDetectionSensitivity: Int?,
        val minimumTrainingDurationSeconds: Int?,
        val autosFilesEnabled: Boolean?
    )

    fun diskSpace(fragmentSize: Long, totalFragments: Long, freeFragments: Long): PlannedDiskSpace {
        val shared = PolarSdkModelMappers.diskSpace(
            fragmentSize = fragmentSize,
            totalFragments = totalFragments,
            freeFragments = freeFragments
        )
        return PlannedDiskSpace(
            totalSpace = shared.totalSpace,
            freeSpace = shared.freeSpace
        )
    }

    fun uuidFromDeviceId(deviceId: String): String {
        return PolarDeviceId.uuidFromDeviceId(deviceId)
    }

    fun d2hNotificationTypeName(value: Int): String? {
        return PolarD2hRuntimePlanning.notificationTypeOrNull(value)
    }

    fun sdLogTriggerName(value: Int): String? {
        return PolarSdLogTriggerName.fromValue(value)?.name
    }

    fun sdLogMagnetometerFrequencyName(value: Int): String? {
        return PolarSdLogMagnetometerFrequencyName.fromValue(value)?.name
    }

    fun firstTimeUseGenderName(value: Int): String? {
        return PolarFirstTimeUseGenderName.fromValue(value)?.name
    }

    fun firstTimeUseGenderValue(name: String): Int? {
        return PolarFirstTimeUseGenderName.fromName(name)?.value
    }

    fun firstTimeUseTrainingBackgroundValue(value: Int): Int? {
        return PolarFirstTimeUseTrainingBackgroundName.fromValue(value)?.value
    }

    fun firstTimeUseTypicalDayValue(value: Int): Int? {
        return PolarFirstTimeUseTypicalDayName.fromValue(value)?.value
    }

    fun watchFaceComplicationId(complicationId: String): Int {
        return PolarWatchFaceComplicationName.idFor(complicationId)
    }

    fun watchFaceComplicationName(id: Int): String? {
        return PolarWatchFaceComplicationName.fromId(id)?.name
    }

    fun exerciseSportProfileName(id: Int): String {
        return PolarExerciseSportProfileName.fromId(id).name
    }

    fun sleepWakeStateName(value: Int): String? {
        return PolarSleepWakeStateName.fromValue(value)?.name
    }

    fun sleepRatingName(value: Int): String? {
        return PolarSleepRatingName.fromValue(value)?.name
    }

    fun activityClassName(value: Int): String? {
        return PolarActivityClassName.fromValue(value)?.name
    }

    fun automaticHrTriggerName(value: Int): String? {
        return PolarAutomaticHrTriggerName.fromValue(value)?.name
    }

    fun dailyBalanceFeedbackName(value: Int): String? {
        return PolarDailyBalanceFeedbackName.fromValue(value)?.name
    }

    fun trainingReadinessName(value: Int): String? {
        return PolarTrainingReadinessName.fromValue(value)?.name
    }

    fun ppiSampleTriggerName(value: Int): String? {
        return PolarPpiSampleTriggerName.fromValue(value)?.name
    }

    fun ppiStatusNames(value: Int): PlannedPpiStatusNames? {
        val shared = PolarPpiStatusNames.fromStatusByte(value) ?: return null
        return PlannedPpiStatusNames(
            skinContact = shared.skinContact,
            movement = shared.movement,
            intervalStatus = shared.intervalStatus
        )
    }

    fun ppiSkinContactName(value: Int): String? {
        return PolarPpiSkinContactName.fromValue(value)?.name
    }

    fun ppiMovementName(value: Int): String? {
        return PolarPpiMovementName.fromValue(value)?.name
    }

    fun ppiIntervalStatusName(value: Int): String? {
        return PolarPpiIntervalStatusName.fromValue(value)?.name
    }

    fun userDeviceSettingsFields(
        deviceLocation: Int? = null,
        usbConnectionMode: Boolean? = null,
        automaticTrainingDetectionMode: Boolean? = null,
        automaticTrainingDetectionSensitivity: Int? = null,
        minimumTrainingDurationSeconds: Int? = null,
        telemetryEnabled: Boolean? = null,
        autosFilesEnabled: Boolean? = null
    ): PlannedUserDeviceSettingsFields {
        return PlannedUserDeviceSettingsFields(
            deviceLocation = deviceLocation,
            usbConnectionMode = usbConnectionMode,
            automaticTrainingDetectionMode = automaticTrainingDetectionMode,
            automaticTrainingDetectionSensitivity = automaticTrainingDetectionSensitivity,
            minimumTrainingDurationSeconds = minimumTrainingDurationSeconds,
            telemetryEnabled = telemetryEnabled,
            autosFilesEnabled = autosFilesEnabled
        )
    }

    fun serializeUserDeviceSettingsFields(model: PlannedUserDeviceSettingsFields): PlannedSerializedUserDeviceSettingsFields {
        val shared = PolarUserDeviceSettingsModels.serializePresencePreservingFields(model.toShared())
        return PlannedSerializedUserDeviceSettingsFields(
            deviceLocation = shared.deviceLocation,
            usbConnectionMode = shared.usbConnectionMode,
            automaticTrainingDetectionMode = shared.automaticTrainingDetectionMode,
            automaticTrainingDetectionSensitivity = shared.automaticTrainingDetectionSensitivity,
            minimumTrainingDurationSeconds = shared.minimumTrainingDurationSeconds,
            autosFilesEnabled = shared.autosFilesEnabled
        )
    }

    fun parseUserDeviceSettingsFields(
        deviceLocation: Int? = null,
        usbConnectionMode: String? = null,
        automaticTrainingDetectionMode: String? = null,
        automaticTrainingDetectionSensitivity: Int? = null,
        minimumTrainingDurationSeconds: Int? = null,
        telemetryEnabled: Boolean? = null,
        autosFilesEnabled: Boolean? = null
    ): PlannedUserDeviceSettingsFields {
        val shared = PolarUserDeviceSettingsModels.parsePresencePreservingFields(
            deviceLocation = deviceLocation,
            usbConnectionMode = usbConnectionMode,
            automaticTrainingDetectionMode = automaticTrainingDetectionMode,
            automaticTrainingDetectionSensitivity = automaticTrainingDetectionSensitivity,
            minimumTrainingDurationSeconds = minimumTrainingDurationSeconds,
            telemetryEnabled = telemetryEnabled,
            autosFilesEnabled = autosFilesEnabled
        )
        return PlannedUserDeviceSettingsFields(
            deviceLocation = shared.deviceLocation,
            usbConnectionMode = shared.usbConnectionMode,
            automaticTrainingDetectionMode = shared.automaticTrainingDetectionMode,
            automaticTrainingDetectionSensitivity = shared.automaticTrainingDetectionSensitivity,
            minimumTrainingDurationSeconds = shared.minimumTrainingDurationSeconds,
            telemetryEnabled = shared.telemetryEnabled,
            autosFilesEnabled = shared.autosFilesEnabled
        )
    }

    fun userDeviceSettingsDeviceLocationName(value: Int): String? {
        return PolarUserDeviceSettingsModels.deviceLocationName(value)
    }

    fun userDeviceSettingsUsbConnectionModeValue(name: String): Int? {
        return PolarUserDeviceSettingsModels.usbConnectionModeValue(name)
    }

    fun userDeviceSettingsAutomaticTrainingDetectionModeValue(name: String): Int? {
        return PolarUserDeviceSettingsModels.automaticTrainingDetectionModeValue(name)
    }

    fun userDeviceSettingsAutomaticMeasurementStateName(enabled: Boolean): String {
        return PolarUserDeviceSettingsModels.automaticMeasurementStateName(enabled)
    }

    fun restServiceList(pathsForServices: Map<String, String>?): PlannedRestServiceList {
        val shared = PolarRestServiceModels.serviceList(pathsForServices)
        return PlannedRestServiceList(
            pathsForServices = shared.pathsForServices,
            names = shared.names,
            paths = shared.paths
        )
    }

    fun restServiceDescription(
        events: List<String>?,
        endpoints: List<String>?,
        actions: Map<String, String>?,
        eventDescriptions: Map<String, Map<String, List<String>>>
    ): PlannedRestServiceDescription {
        val shared = PolarRestServiceModels.serviceDescription(
            events = events,
            endpoints = endpoints,
            actions = actions,
            eventDescriptions = eventDescriptions
        )
        return PlannedRestServiceDescription(
            events = shared.events,
            endpoints = shared.endpoints,
            actions = shared.actions,
            actionNames = shared.actionNames,
            actionPaths = shared.actionPaths,
            details = shared.details,
            triggers = shared.triggers
        )
    }

    private fun PlannedUserDeviceSettingsFields.toShared(): PolarUserDeviceSettingsFields {
        return PolarUserDeviceSettingsFields(
            deviceLocation = deviceLocation,
            usbConnectionMode = usbConnectionMode,
            automaticTrainingDetectionMode = automaticTrainingDetectionMode,
            automaticTrainingDetectionSensitivity = automaticTrainingDetectionSensitivity,
            minimumTrainingDurationSeconds = minimumTrainingDurationSeconds,
            telemetryEnabled = telemetryEnabled,
            autosFilesEnabled = autosFilesEnabled
        )
    }
}
