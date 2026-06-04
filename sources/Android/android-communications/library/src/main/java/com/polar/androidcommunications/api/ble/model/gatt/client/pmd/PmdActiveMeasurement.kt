package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.shared.pmd.PolarPmdControlPoint

enum class PmdActiveMeasurement(private val bitMask: Int?) {
    ONLINE_MEASUREMENT_ACTIVE(bitMask = 1),
    OFFLINE_MEASUREMENT_ACTIVE(bitMask = 2),
    ONLINE_AND_OFFLINE_ACTIVE(bitMask = 3),
    NO_ACTIVE_MEASUREMENT(bitMask = 0),
    UNKNOWN_MEASUREMENT_STATUS(bitMask = null);

    companion object {
        fun fromStatusResponse(responseByte: Byte): PmdActiveMeasurement {
            return when (PolarPmdControlPoint.parseActiveMeasurement(responseByte.toInt() and 0xFF).activeBits) {
                ONLINE_MEASUREMENT_ACTIVE.bitMask -> ONLINE_MEASUREMENT_ACTIVE
                OFFLINE_MEASUREMENT_ACTIVE.bitMask -> OFFLINE_MEASUREMENT_ACTIVE
                ONLINE_AND_OFFLINE_ACTIVE.bitMask -> ONLINE_AND_OFFLINE_ACTIVE
                NO_ACTIVE_MEASUREMENT.bitMask -> NO_ACTIVE_MEASUREMENT
                else -> UNKNOWN_MEASUREMENT_STATUS
            }
        }
    }
}
