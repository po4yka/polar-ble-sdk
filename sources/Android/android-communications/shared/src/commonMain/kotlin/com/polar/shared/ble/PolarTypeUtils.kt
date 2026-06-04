package com.polar.shared.ble

object PolarTypeUtils {
    fun convertArrayToUnsignedByte(data: ByteArray): PolarTypeConversion<String> {
        return when {
            data.isEmpty() -> PolarTypeConversion(error = PolarTypeConversionError.EmptyPayload)
            data.size > 1 -> PolarTypeConversion(error = PolarTypeConversionError.PayloadTooLong)
            else -> PolarTypeConversion(value = (data[0].toInt() and 0xFF).toString())
        }
    }

    fun convertArrayToUnsignedInt(data: ByteArray, offset: Int, length: Int): PolarTypeConversion<String> {
        return convertArrayToUnsignedInt(data.copyOfRange(offset, offset + length))
    }

    fun convertArrayToUnsignedInt(data: ByteArray): PolarTypeConversion<String> {
        return when {
            data.isEmpty() -> PolarTypeConversion(error = PolarTypeConversionError.EmptyPayload)
            data.size > 4 -> PolarTypeConversion(error = PolarTypeConversionError.PayloadTooLong)
            else -> PolarTypeConversion(value = data.unsignedLittleEndianLong().toString())
        }
    }

    fun convertArrayToUnsignedLong(data: ByteArray, offset: Int, length: Int): PolarTypeConversion<String> {
        return convertArrayToUnsignedLong(data.copyOfRange(offset, offset + length))
    }

    fun convertArrayToUnsignedLong(data: ByteArray): PolarTypeConversion<String> {
        return when {
            data.isEmpty() -> PolarTypeConversion(error = PolarTypeConversionError.EmptyPayload)
            data.size > 8 -> PolarTypeConversion(error = PolarTypeConversionError.PayloadTooLong)
            else -> PolarTypeConversion(value = data.unsignedLittleEndianDecimalString())
        }
    }

    fun convertArrayToSignedInt(data: ByteArray, offset: Int, length: Int): PolarTypeConversion<String> {
        return convertArrayToSignedInt(data.copyOfRange(offset, offset + length))
    }

    fun convertArrayToSignedInt(data: ByteArray): PolarTypeConversion<String> {
        return when {
            data.isEmpty() -> PolarTypeConversion(error = PolarTypeConversionError.EmptyPayload)
            data.size > 4 -> PolarTypeConversion(error = PolarTypeConversionError.PayloadTooLong)
            else -> {
                val value = data.unsignedLittleEndianLong()
                val bitWidth = data.size * 8
                val signBit = 1L shl (bitWidth - 1)
                val signed = if ((value and signBit) != 0L) value - (1L shl bitWidth) else value
                PolarTypeConversion(value = signed.toString())
            }
        }
    }

    fun convertUnsignedByteToInt(byte: Byte): Int {
        return byte.toInt() and 0xFF
    }

    fun requireUnsignedByte(data: ByteArray): String {
        return convertArrayToUnsignedByte(data).requireValue()
    }

    fun requireUnsignedInt(data: ByteArray): String {
        return convertArrayToUnsignedInt(data).requireValue()
    }

    fun requireUnsignedLong(data: ByteArray): String {
        return convertArrayToUnsignedLong(data).requireValue()
    }

    fun requireSignedInt(data: ByteArray): String {
        return convertArrayToSignedInt(data).requireValue()
    }
}

data class PolarTypeConversion<T>(
    val value: T? = null,
    val error: PolarTypeConversionError? = null
) {
    fun requireValue(): T {
        return value ?: throw IllegalArgumentException(error?.vectorName ?: "conversionFailed")
    }
}

enum class PolarTypeConversionError(val vectorName: String) {
    EmptyPayload("emptyPayload"),
    PayloadTooLong("payloadTooLong")
}

private fun ByteArray.unsignedLittleEndianLong(): Long {
    var result = 0L
    forEachIndexed { index, byte ->
        result = result or ((byte.toLong() and 0xFFL) shl (index * 8))
    }
    return result
}

private fun ByteArray.unsignedLittleEndianDecimalString(): String {
    var result = "0"
    reversedArray().forEach { byte ->
        result = result.multiplyBy(256).add(byte.toInt() and 0xFF)
    }
    return result
}

private fun String.multiplyBy(multiplier: Int): String {
    var carry = 0
    val digits = StringBuilder()
    reversed().forEach { char ->
        val product = (char - '0') * multiplier + carry
        digits.append(product % 10)
        carry = product / 10
    }
    while (carry > 0) {
        digits.append(carry % 10)
        carry /= 10
    }
    return digits.reverse().toString().trimLeadingZeroes()
}

private fun String.add(addend: Int): String {
    var carry = addend
    val digits = StringBuilder()
    reversed().forEach { char ->
        val sum = (char - '0') + carry
        digits.append(sum % 10)
        carry = sum / 10
    }
    while (carry > 0) {
        digits.append(carry % 10)
        carry /= 10
    }
    return digits.reverse().toString().trimLeadingZeroes()
}

private fun String.trimLeadingZeroes(): String {
    val trimmed = trimStart('0')
    return if (trimmed.isEmpty()) "0" else trimmed
}
