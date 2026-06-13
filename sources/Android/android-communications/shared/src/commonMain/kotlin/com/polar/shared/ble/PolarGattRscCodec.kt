package com.polar.shared.ble

object PolarGattRscCodec {
    data class RscMeasurement(
        val strideLengthPresent: Boolean,
        val totalDistancePresent: Boolean,
        val running: Boolean,
        val speedRaw: Long,
        val speedKmh: Double,
        val cadence: Int,
        val strideLength: Long,
        val totalDistanceRaw: Long,
        val totalDistanceAndroidRaw: Long,
        val totalDistanceMeters: Double,
        val flags: Int
    )

    fun parseRscMeasurement(data: ByteArray): RscMeasurement {
        var index = 0
        val flags = data[index++].toInt() and 0xFF
        val strideLengthPresent = (flags and 0x01) == 0x01
        val totalDistancePresent = (flags and 0x02) == 0x02
        val running = (flags and 0x04) == 0x04
        val speedRaw = littleEndianUInt16(data, index).toLong()
        index += 2
        val cadence = data[index++].toInt() and 0xFF
        val strideLength = if (strideLengthPresent) {
            val value = littleEndianUInt16(data, index).toLong()
            index += 2
            value
        } else {
            0L
        }
        val totalDistanceRaw = if (totalDistancePresent) {
            littleEndianUInt32(data, index)
        } else {
            0L
        }
        val totalDistanceAndroidRaw = if (totalDistancePresent) {
            littleEndianInt32(data, index).toLong()
        } else {
            0L
        }
        return RscMeasurement(
            strideLengthPresent = strideLengthPresent,
            totalDistancePresent = totalDistancePresent,
            running = running,
            speedRaw = speedRaw,
            speedKmh = (speedRaw.toDouble() / 256.0) * 3.6,
            cadence = cadence,
            strideLength = strideLength,
            totalDistanceRaw = totalDistanceRaw,
            totalDistanceAndroidRaw = totalDistanceAndroidRaw,
            totalDistanceMeters = totalDistanceRaw.toDouble() * 0.1,
            flags = flags
        )
    }

    private fun littleEndianUInt16(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun littleEndianInt32(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun littleEndianUInt32(data: ByteArray, offset: Int): Long {
        return littleEndianInt32(data, offset).toLong() and 0xFFFF_FFFFL
    }
}
