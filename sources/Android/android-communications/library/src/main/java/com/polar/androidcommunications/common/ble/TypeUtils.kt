package com.polar.androidcommunications.common.ble

import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter

object TypeUtils {

    fun convertArrayToUnsignedByte(data: ByteArray): UByte {
        BleUtils.validate(data.size == 1, "Array other than 1 cannot be converted to UByte. Input data size was " + data.size)
        return PolarRuntimePlannerAdapter.convertArrayToUnsignedByte(data)
    }

    fun convertArrayToUnsignedInt(data: ByteArray, offset: Int, length: Int): UInt {
        return PolarRuntimePlannerAdapter.convertArrayToUnsignedInt(data, offset, length)
    }

    fun convertArrayToUnsignedInt(data: ByteArray): UInt {
        BleUtils.validate(data.size in 1..4, "Array bigger than 4 cannot be converted to UInt. Input data size was " + data.size)
        return PolarRuntimePlannerAdapter.convertArrayToUnsignedInt(data)
    }

    fun convertArrayToUnsignedLong(data: ByteArray, offset: Int, length: Int): ULong {
        return PolarRuntimePlannerAdapter.convertArrayToUnsignedLong(data, offset, length)
    }

    fun convertArrayToUnsignedLong(data: ByteArray): ULong {
        BleUtils.validate(data.size in 1..8, "Array bigger than 8 cannot be converted to ULong. Input data size was " + data.size)
        return PolarRuntimePlannerAdapter.convertArrayToUnsignedLong(data)
    }

    fun convertArrayToSignedInt(data: ByteArray, offset: Int, length: Int): Int {
        return convertArrayToSignedInt(data.copyOfRange(offset, offset + length))
    }

    fun convertArrayToSignedInt(data: ByteArray): Int {
        BleUtils.validate(data.size in 1..4, "Array bigger than 4 cannot be converted to Int. Input data size was " + data.size)
        return PolarRuntimePlannerAdapter.convertArrayToSignedInt(data)
    }

    fun convertUnsignedByteToInt(byte: Byte): Int {
        return PolarRuntimePlannerAdapter.convertUnsignedByteToInt(byte)
    }
}
