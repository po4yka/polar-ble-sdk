package com.polar.sdk.api.model

import com.polar.shared.device.PolarDeviceId
import com.polar.shared.runtime.PolarD2hRuntimePlanning
import com.polar.shared.sdk.PolarFirstTimeUseGenderName
import com.polar.shared.sdk.PolarFirstTimeUseTrainingBackgroundName
import com.polar.shared.sdk.PolarFirstTimeUseTypicalDayName
import com.polar.shared.sdk.PolarRestServiceModels
import com.polar.shared.sdk.PolarSdLogMagnetometerFrequencyName
import com.polar.shared.sdk.PolarSdLogTriggerName
import com.polar.shared.sdk.PolarSdkModelMappers
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
}
