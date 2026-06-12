package com.polar.sdk.api.model

object PolarDeviceUuid {
    fun fromDeviceId(deviceId: String): String {
        return PolarSdkModelAdapter.uuidFromDeviceId(deviceId)
    }
}
