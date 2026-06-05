package com.polar.shared.pmd

data class PolarPmdSecret(
    val strategy: String,
    val key: ByteArray
) {
    fun serializeHex(): String {
        return "0601${strategyByte().toHexByte()}${key.toHexString()}"
    }

    private fun strategyByte(): Int {
        return when (strategy) {
            "NONE" -> 0
            "XOR" -> 1
            "AES128" -> 2
            "AES256" -> 3
            else -> error("Unexpected strategy $strategy")
        }
    }

    companion object {
        fun from(strategy: String, key: ByteArray): PolarPmdSecret {
            validate(strategy, key)?.let { validationError -> error(validationError) }
            return PolarPmdSecret(strategy, key)
        }

        fun validate(strategy: String, key: ByteArray): String? {
            val valid = when (strategy) {
                "NONE" -> key.isEmpty()
                "XOR" -> key.isNotEmpty()
                "AES128" -> key.size == 16
                "AES256" -> key.size == 32
                else -> false
            }
            return if (valid) null else "invalidSecurityKey"
        }

        fun strategyNameFromByte(strategyByte: Int): String? {
            return when (strategyByte and 0xFF) {
                0 -> "NONE"
                1 -> "XOR"
                2 -> "AES128"
                3 -> "AES256"
                else -> null
            }
        }
    }
}

private fun ByteArray.toHexString(): String {
    return joinToString(separator = "") { byte -> (byte.toInt() and 0xFF).toHexByte() }
}

private fun Int.toHexByte(): String {
    val value = this and 0xFF
    return "${(value / 16).toHexDigit()}${(value % 16).toHexDigit()}"
}

private fun Int.toHexDigit(): Char {
    return if (this < 10) '0' + this else 'a' + (this - 10)
}
