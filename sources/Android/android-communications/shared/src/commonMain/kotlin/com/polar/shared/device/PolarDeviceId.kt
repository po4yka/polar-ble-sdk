package com.polar.shared.device

object PolarDeviceId {
    const val REQUIRED_DEVICE_ID_LENGTH: Int = 8
    const val POLAR_UUID_PREFIX: String = "0e030000-0084-0000-0000-0000"

    fun isValid(deviceId: String): Boolean = runCatching {
        val numeric = deviceId.hexToLong()
        if (deviceId.length == REQUIRED_DEVICE_ID_LENGTH) {
            checksum(numeric, width = REQUIRED_DEVICE_ID_LENGTH).toLong() == (numeric and 0x0F)
        } else {
            checksum(numeric, width = REQUIRED_DEVICE_ID_LENGTH) != 0
        }
    }.getOrDefault(false)

    fun assembleFull(deviceId: String): String {
        return when (deviceId.length) {
            6 -> runCatching {
                val numeric = deviceId.hexToLong()
                deviceId + "1" + checksum(numeric, width = 6).toUpperHexDigit()
            }.getOrElse { "" }
            7 -> runCatching {
                val numeric = deviceId.hexToLong()
                deviceId + checksum(numeric, width = 7).toUpperHexDigit()
            }.getOrElse { "" }
            else -> deviceId
        }
    }

    fun uuidFromDeviceId(deviceId: String): String {
        require(deviceId.length == REQUIRED_DEVICE_ID_LENGTH) {
            "deviceId must be $REQUIRED_DEVICE_ID_LENGTH characters long, was: ${deviceId.length}"
        }
        return POLAR_UUID_PREFIX + deviceId
    }

    fun classifyIdentifier(identifier: String): IdentifierClassification {
        return when {
            identifier.matches(DEVICE_ID_REGEX) -> IdentifierClassification.DeviceId
            identifier.contains(":") || identifier.contains("-") -> IdentifierClassification.PlatformSpecific
            else -> IdentifierClassification.Invalid
        }
    }

    private fun checksum(deviceId: Long, width: Int): Int {
        var shiftOffset = 0
        var a2 = 0x01
        when (width) {
            8 -> {
                a2 = ((deviceId shr 4) and 0x0F).toInt()
                shiftOffset = 8
            }
            7 -> {
                a2 = (deviceId and 0x0F).toInt()
                shiftOffset = 4
            }
        }
        val a3 = ((deviceId shr shiftOffset) and 0x0F).toInt()
        val a4 = ((deviceId shr (shiftOffset + 4)) and 0x0F).toInt()
        val a5 = ((deviceId shr (shiftOffset + 8)) and 0x0F).toInt()
        val a6 = ((deviceId shr (shiftOffset + 12)) and 0x0F).toInt()
        val a7 = ((deviceId shr (shiftOffset + 16)) and 0x0F).toInt()
        val a8 = ((deviceId shr (shiftOffset + 20)) and 0x0F).toInt()
        return (3 * (a2 + a4 + a6 + a8) + a3 + a5 + a7) % 16
    }

    private fun String.hexToLong(): Long {
        if (isEmpty()) throw NumberFormatException("Empty device id")
        var result = 0L
        forEach { char ->
            val value = char.hexDigitValueOrNull() ?: throw NumberFormatException("Invalid hex digit: $char")
            result = (result shl 4) or value.toLong()
        }
        return result
    }

    private fun Int.toUpperHexDigit(): String {
        val digit = if (this < 10) {
            '0' + this
        } else {
            'A' + (this - 10)
        }
        return digit.toString()
    }

    private fun Char.hexDigitValueOrNull(): Int? {
        return when (this) {
            in '0'..'9' -> this - '0'
            in 'a'..'f' -> this - 'a' + 10
            in 'A'..'F' -> this - 'A' + 10
            else -> null
        }
    }

    sealed interface IdentifierClassification {
        data object DeviceId : IdentifierClassification
        data object PlatformSpecific : IdentifierClassification
        data object Invalid : IdentifierClassification
    }

    private val DEVICE_ID_REGEX = Regex("[0-9A-Fa-f]{6,8}")
}
