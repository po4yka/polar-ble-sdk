package com.polar.shared.ble

object PolarGattPfcCodec {
    data class PfcFeature(
        val broadcastSupported: Boolean,
        val khzSupported: Boolean,
        val otaUpdateSupported: Boolean,
        val whisperModeSupported: Boolean,
        val bleModeConfigureSupported: Boolean,
        val multiConnectionSupported: Boolean,
        val antSupported: Boolean,
        val securityModeSupported: Boolean,
        val sensorInitiatedSecurityModeSupported: Boolean
    )

    fun parsePfcFeature(data: ByteArray): PfcFeature {
        val first = data[0].toInt() and 0xFF
        val second = data[1].toInt() and 0xFF
        return PfcFeature(
            broadcastSupported = (first and 0x01) == 0x01,
            khzSupported = (first and 0x02) == 0x02,
            otaUpdateSupported = (first and 0x04) == 0x04,
            whisperModeSupported = (first and 0x10) == 0x10,
            bleModeConfigureSupported = (first and 0x40) == 0x40,
            multiConnectionSupported = (first and 0x80) == 0x80,
            antSupported = (second and 0x01) == 0x01,
            securityModeSupported = (second and 0x02) == 0x02,
            sensorInitiatedSecurityModeSupported = (second and 0x08) == 0x08
        )
    }
}
