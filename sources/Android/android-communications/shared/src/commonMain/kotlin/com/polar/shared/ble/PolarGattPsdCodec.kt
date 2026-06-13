package com.polar.shared.ble

object PolarGattPsdCodec {
    data class PsdFeature(
        val ecgSupported: Boolean,
        val ohrSupported: Boolean,
        val accSupported: Boolean,
        val ppSupported: Boolean
    )

    fun parsePsdFeature(data: ByteArray): PsdFeature {
        val first = data[0].toInt() and 0xFF
        return PsdFeature(
            ecgSupported = (first and 0x01) == 0x01,
            ohrSupported = (first and 0x02) == 0x02,
            accSupported = (first and 0x04) == 0x04,
            ppSupported = (first and 0x08) == 0x08
        )
    }
}
