package com.polar.sdk.api.model

import com.polar.shared.device.PolarDeviceId

object PolarDeviceUuid {
    fun fromDeviceId(deviceId: String): String {
        return PolarDeviceId.uuidFromDeviceId(deviceId)
    }
}
