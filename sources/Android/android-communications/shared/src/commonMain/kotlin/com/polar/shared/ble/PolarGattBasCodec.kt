package com.polar.shared.ble

object PolarGattBasCodec {
    enum class ChargeStateName {
        UNKNOWN,
        CHARGING,
        DISCHARGING_ACTIVE,
        DISCHARGING_INACTIVE
    }

    enum class BatteryPresentStateName {
        NOT_PRESENT,
        PRESENT,
        UNKNOWN
    }

    enum class PowerSourceStateName {
        NOT_CONNECTED,
        CONNECTED,
        UNKNOWN,
        RESERVED_FOR_FUTURE_USE
    }

    data class PowerSourcesState(
        val batteryPresent: BatteryPresentStateName,
        val wiredExternalPower: PowerSourceStateName,
        val wirelessExternalPower: PowerSourceStateName
    )

    data class BatteryStatus(
        val chargeState: ChargeStateName,
        val powerSources: PowerSourcesState
    )

    fun parseBatteryStatus(data: ByteArray): BatteryStatus {
        val statusByte = data[1].toInt() and 0xFF
        return BatteryStatus(
            chargeState = parseChargeState(statusByte),
            powerSources = PowerSourcesState(
                batteryPresent = parseBatteryPresent(statusByte),
                wiredExternalPower = parsePowerSource((statusByte and 0x06) shr 1),
                wirelessExternalPower = parsePowerSource((statusByte and 0x18) shr 3)
            )
        )
    }

    private fun parseChargeState(statusByte: Int): ChargeStateName {
        return when ((statusByte and 0x60) shr 5) {
            1 -> ChargeStateName.CHARGING
            2 -> ChargeStateName.DISCHARGING_ACTIVE
            3 -> ChargeStateName.DISCHARGING_INACTIVE
            else -> ChargeStateName.UNKNOWN
        }
    }

    private fun parseBatteryPresent(statusByte: Int): BatteryPresentStateName {
        return when (statusByte and 0x01) {
            0 -> BatteryPresentStateName.NOT_PRESENT
            1 -> BatteryPresentStateName.PRESENT
            else -> BatteryPresentStateName.UNKNOWN
        }
    }

    private fun parsePowerSource(value: Int): PowerSourceStateName {
        return when (value) {
            0 -> PowerSourceStateName.NOT_CONNECTED
            1 -> PowerSourceStateName.CONNECTED
            3 -> PowerSourceStateName.RESERVED_FOR_FUTURE_USE
            else -> PowerSourceStateName.UNKNOWN
        }
    }
}
