package com.polar.shared.ble

object PolarBasBatteryStatusCodec {
    enum class ChargeState {
        UNKNOWN,
        CHARGING,
        DISCHARGING_ACTIVE,
        DISCHARGING_INACTIVE
    }

    enum class PowerSourceState {
        NOT_CONNECTED,
        CONNECTED,
        UNKNOWN,
        RESERVED_FOR_FUTURE_USE
    }

    enum class BatteryPresentState {
        NOT_PRESENT,
        PRESENT,
        UNKNOWN
    }

    data class DecodedStatus(
        val statusByte: Int,
        val chargeState: ChargeState,
        val batteryPresent: BatteryPresentState,
        val wiredExternalPowerConnected: PowerSourceState,
        val wirelessExternalPowerConnected: PowerSourceState
    )

    fun decode(data: ByteArray): DecodedStatus {
        val status = data.getOrNull(1)?.toInt()?.and(0xFF) ?: 0
        return decodeStatusByte(status)
    }

    fun decodeStatusByte(status: Int): DecodedStatus {
        val statusByte = status and 0xFF
        return DecodedStatus(
            statusByte = statusByte,
            chargeState = chargeState(statusByte),
            batteryPresent = if ((statusByte and 0x01) == 1) BatteryPresentState.PRESENT else BatteryPresentState.NOT_PRESENT,
            wiredExternalPowerConnected = powerSource((statusByte and 0x06) shr 1),
            wirelessExternalPowerConnected = powerSource((statusByte and 0x18) shr 3)
        )
    }

    private fun chargeState(statusByte: Int): ChargeState {
        return when ((statusByte and 0x60) shr 5) {
            1 -> ChargeState.CHARGING
            2 -> ChargeState.DISCHARGING_ACTIVE
            3 -> ChargeState.DISCHARGING_INACTIVE
            else -> ChargeState.UNKNOWN
        }
    }

    private fun powerSource(value: Int): PowerSourceState {
        return when (value) {
            0 -> PowerSourceState.NOT_CONNECTED
            1 -> PowerSourceState.CONNECTED
            3 -> PowerSourceState.RESERVED_FOR_FUTURE_USE
            else -> PowerSourceState.UNKNOWN
        }
    }
}
