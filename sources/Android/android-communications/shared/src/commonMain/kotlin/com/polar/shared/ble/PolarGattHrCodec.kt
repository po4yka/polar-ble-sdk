package com.polar.shared.ble

import kotlin.math.roundToInt

object PolarGattHrCodec {
    data class HrMeasurement(
        val hr: Int,
        val sensorContact: Boolean,
        val sensorContactSupported: Boolean,
        val energy: Int,
        val rrs: List<Int>,
        val rrsMs: List<Int>,
        val rrPresent: Boolean
    )

    fun parseHrMeasurement(data: ByteArray): HrMeasurement {
        val flags = data[0].toInt() and 0xFF
        val hrFormat = flags and 0x01
        val sensorContact = ((flags and 0x06) shr 1) == 0x03
        val contactSupported = (flags and 0x04) != 0
        val energyExpended = (flags and 0x08) shr 3
        val rrPresent = (flags and 0x10) shr 4
        val hrValue = if (hrFormat == 1) {
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else {
            data[1].toInt() and 0xFF
        }
        var offset = hrFormat + 2
        var energy = 0
        if (energyExpended == 1) {
            energy = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
        }
        val rrs = mutableListOf<Int>()
        val rrsMs = mutableListOf<Int>()
        if (rrPresent == 1) {
            while (offset < data.size) {
                val rrValue = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
                offset += 2
                rrs.add(rrValue)
                rrsMs.add(mapRr1024ToRrMs(rrValue))
            }
        }
        return HrMeasurement(
            hr = hrValue,
            sensorContact = sensorContact,
            sensorContactSupported = contactSupported,
            energy = energy,
            rrs = rrs,
            rrsMs = rrsMs,
            rrPresent = rrPresent == 1
        )
    }

    private fun mapRr1024ToRrMs(rrsRaw: Int): Int {
        return (rrsRaw.toFloat() / 1024.0f * 1000.0f).roundToInt()
    }
}
