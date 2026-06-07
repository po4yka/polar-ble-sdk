package com.polar.sdk.api.model

import com.polar.shared.device.PolarDeviceId
import com.polar.shared.runtime.PolarD2hRuntimePlanning
import com.polar.shared.sdk.PolarSdkModelMappers

internal object PolarSdkModelAdapter {
    data class PlannedDiskSpace(
        val totalSpace: Long,
        val freeSpace: Long
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
}
