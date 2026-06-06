package com.polar.androidcommunications.common.ble

import com.polar.shared.ble.PolarTypeUtils

object TypeUtils {

    fun convertArrayToUnsignedByte(data: ByteArray): UByte {
        BleUtils.validate(data.size == 1, "Array other than 1 cannot be converted to UByte. Input data size was " + data.size)
        return PolarTypeUtils.requireUnsignedByte(data).toUByte()
    }

    fun convertArrayToUnsignedInt(data: ByteArray, offset: Int, length: Int): UInt {
        return PolarTypeUtils.convertArrayToUnsignedInt(data, offset, length).requireValue().toUInt()
    }

    fun convertArrayToUnsignedInt(data: ByteArray): UInt {
        BleUtils.validate(data.size in 1..4, "Array bigger than 4 cannot be converted to UInt. Input data size was " + data.size)
        return PolarTypeUtils.requireUnsignedInt(data).toUInt()
    }

    fun convertArrayToUnsignedLong(data: ByteArray, offset: Int, length: Int): ULong {
        return PolarTypeUtils.convertArrayToUnsignedLong(data, offset, length).requireValue().toULong()
    }

    fun convertArrayToUnsignedLong(data: ByteArray): ULong {
        BleUtils.validate(data.size in 1..8, "Array bigger than 8 cannot be converted to ULong. Input data size was " + data.size)
        return PolarTypeUtils.requireUnsignedLong(data).toULong()
    }

    fun convertArrayToSignedInt(data: ByteArray, offset: Int, length: Int): Int {
        return convertArrayToSignedInt(data.copyOfRange(offset, offset + length))
    }

    fun convertArrayToSignedInt(data: ByteArray): Int {
        BleUtils.validate(data.size in 1..4, "Array bigger than 4 cannot be converted to Int. Input data size was " + data.size)
        var result = PolarTypeUtils.requireUnsignedInt(data).toUInt()
        if (data.last() < 0) {
            val mask: UInt = 0xFFFFFFFFu shl data.size * 8
            result = result or mask
        }
        return result.toInt()
    }

    fun convertUnsignedByteToInt(byte: Byte): Int {
        return PolarTypeUtils.convertUnsignedByteToInt(byte)
    }
}
