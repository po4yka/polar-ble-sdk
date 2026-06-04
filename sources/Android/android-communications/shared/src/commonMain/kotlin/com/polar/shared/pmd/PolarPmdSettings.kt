package com.polar.shared.pmd

data class PolarPmdSettingsParseResult(
    val settings: Map<PolarPmdSettingType, List<Int>> = emptyMap(),
    val error: PolarPmdParseError? = null
) {
    fun requireSettings(): Map<PolarPmdSettingType, List<Int>> {
        return settings.takeIf { error == null } ?: throw IllegalArgumentException(error?.vectorName ?: "invalidPMDData")
    }
}

enum class PolarPmdParseError(val vectorName: String) {
    InvalidPmdData("invalidPMDData")
}

enum class PolarPmdSettingType(val code: Int, val valueSize: Int?) {
    SAMPLE_RATE(0, 2),
    RESOLUTION(1, 2),
    RANGE(2, 2),
    RANGE_MILLIUNIT(3, 4),
    CHANNELS(4, 1),
    FACTOR(5, 4),
    SECURITY(6, null);

    companion object {
        fun fromCode(code: Int): PolarPmdSettingType? {
            return entries.firstOrNull { it.code == code }
        }
    }
}

object PolarPmdSettings {
    fun parseSettings(data: ByteArray): PolarPmdSettingsParseResult {
        val settings = linkedMapOf<PolarPmdSettingType, List<Int>>()
        var index = 0
        while (index < data.size) {
            val type = PolarPmdSettingType.fromCode(data[index].unsigned())
                ?: return PolarPmdSettingsParseResult(error = PolarPmdParseError.InvalidPmdData)
            val valueSize = type.valueSize
                ?: return PolarPmdSettingsParseResult(error = PolarPmdParseError.InvalidPmdData)
            index += 1
            if (index >= data.size) return PolarPmdSettingsParseResult(error = PolarPmdParseError.InvalidPmdData)
            val count = data[index].unsigned()
            index += 1
            val values = mutableListOf<Int>()
            repeat(count) {
                if (index + valueSize > data.size) return PolarPmdSettingsParseResult(error = PolarPmdParseError.InvalidPmdData)
                values += data.copyOfRange(index, index + valueSize).littleEndianSignedInt()
                index += valueSize
            }
            settings[type] = values
        }
        return PolarPmdSettingsParseResult(settings = settings)
    }

    fun serializeSelectedSettings(selected: Map<PolarPmdSettingType, Int>): ByteArray {
        val bytes = mutableListOf<Byte>()
        SELECTED_SETTING_ORDER.forEach { type ->
            val value = selected[type]
            if (value != null && type != PolarPmdSettingType.FACTOR) {
                val valueSize = type.valueSize ?: return@forEach
                bytes += type.code.toByte()
                bytes += 1
                repeat(valueSize) { index ->
                    bytes += ((value shr (index * 8)) and 0xFF).toByte()
                }
            }
        }
        return bytes.toByteArray()
    }

    private val SELECTED_SETTING_ORDER = listOf(
        PolarPmdSettingType.SAMPLE_RATE,
        PolarPmdSettingType.RESOLUTION,
        PolarPmdSettingType.RANGE,
        PolarPmdSettingType.RANGE_MILLIUNIT,
        PolarPmdSettingType.CHANNELS,
        PolarPmdSettingType.FACTOR
    )
}

private fun Byte.unsigned(): Int {
    return toInt() and 0xFF
}

private fun ByteArray.littleEndianSignedInt(): Int {
    var value = 0L
    forEachIndexed { index, byte ->
        value = value or ((byte.toLong() and 0xFFL) shl (index * 8))
    }
    val bitWidth = size * 8
    val signBit = 1L shl (bitWidth - 1)
    return (if ((value and signBit) != 0L) value - (1L shl bitWidth) else value).toInt()
}
