package com.polar.androidcommunications.api.ble.model.polar

import com.polar.sdk.api.model.PolarSdkModelAdapter

class BlePolarDeviceIdUtility private constructor() {
    init {
        throw IllegalStateException("Utility class")
    }

    companion object {
        fun isValidDeviceId(deviceId: String?): Boolean {
            if (deviceId == null) return false
            return PolarSdkModelAdapter.isValidDeviceId(deviceId)
        }

        fun assemblyFullPolarDeviceId(deviceId: String): String {
            return PolarSdkModelAdapter.assembleFullDeviceId(deviceId)
        }
    }
}
