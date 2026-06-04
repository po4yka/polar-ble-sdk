package com.polar.androidcommunications.api.ble.model.polar

import com.polar.shared.device.PolarDeviceId

class BlePolarDeviceIdUtility private constructor() {
    init {
        throw IllegalStateException("Utility class")
    }

    companion object {
        fun isValidDeviceId(deviceId: String?): Boolean {
            if (deviceId == null) return false
            return PolarDeviceId.isValid(deviceId)
        }

        fun assemblyFullPolarDeviceId(deviceId: String): String {
            return PolarDeviceId.assembleFull(deviceId)
        }
    }
}
