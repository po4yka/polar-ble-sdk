package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.common.ble.TypeUtils
import com.polar.shared.pmd.PolarPmdSettingType
import com.polar.shared.pmd.PolarPmdSettings
import java.util.AbstractMap
import java.util.Collections
import java.util.EnumMap
import java.util.TreeMap

class PmdSetting {
    enum class PmdSettingType(val numVal: Int) {
        SAMPLE_RATE(0),
        RESOLUTION(1),
        RANGE(2),
        RANGE_MILLIUNIT(3),
        CHANNELS(4),
        FACTOR(5),
        SECURITY(6);
    }

    // available settings
    var settings: EnumMap<PmdSettingType, Set<Int>> = EnumMap(PmdSettingType.values().associateWith { emptySet() })

    // selected by user
    var selected: MutableMap<PmdSettingType, Int> = mutableMapOf()
        private set

    constructor(data: ByteArray) {
        val parsedSettings = parsePmdSettingsData(data)
        validateSettings(parsedSettings)
        settings = parsedSettings
    }

    constructor(selected: Map<PmdSettingType, Int>) {
        validateSelected(selected)
        this.selected = selected.toMutableMap()
    }

    private fun parsePmdSettingsData(data: ByteArray): EnumMap<PmdSettingType, Set<Int>> {
        val parsed = PolarPmdSettings.parseSettings(data)
        if (parsed.error != null) {
            throwAndroidCompatibleParseError(data)
        }
        val parsedSettings = EnumMap<PmdSettingType, Set<Int>>(PmdSettingType::class.java)
        parsed.settings.forEach { (type, values) ->
            parsedSettings[type.toAndroidType()] = values.toSet()
        }
        return parsedSettings
    }

    fun updateSelectedFromStartResponse(data: ByteArray) {

        val settingsFromStartResponse: EnumMap<PmdSettingType, Set<Int>> = try {
            parsePmdSettingsData(data)
        } catch (e: Exception) {
            throw e
        }

        if (settingsFromStartResponse.containsKey(PmdSettingType.FACTOR)) {
            selected[PmdSettingType.FACTOR] = settingsFromStartResponse[PmdSettingType.FACTOR]!!.iterator().next()
        }
    }

    fun serializeSelected(): ByteArray {
        return PolarPmdSettings.serializeSelectedSettings(selected.mapKeys { it.key.toSharedType() })
    }

    fun maxSettings(): PmdSetting {
        val set: MutableMap<PmdSettingType, Int> = TreeMap()
        for ((key, value) in settings) {
            set[key] = Collections.max(value)
        }
        return PmdSetting(set)
    }

    override fun toString(): String {
        val stringBuilder: StringBuilder = StringBuilder("\navailable settings: ")
        for (setting in settings) {
            stringBuilder.append("${setting.key} : ${setting.value} , ")
        }
        stringBuilder.append("\nselected settings: ")
        for (setting in selected) {
            stringBuilder.append("${setting.key} : ${setting.value} , ")
        }
        return stringBuilder.toString()

    }

    companion object {
        private fun typeToFieldSize(type: PmdSettingType): Int {
            return when (type) {
                PmdSettingType.SAMPLE_RATE -> 2
                PmdSettingType.RESOLUTION -> 2
                PmdSettingType.RANGE -> 2
                PmdSettingType.RANGE_MILLIUNIT -> 4
                PmdSettingType.CHANNELS -> 1
                PmdSettingType.FACTOR -> 4
                PmdSettingType.SECURITY -> 16
            }
        }

        private fun validateSettings(settings: Map<PmdSettingType, Set<Int>>) {
            for ((key, value1) in settings) {
                for (value in value1) {
                    val entry: Map.Entry<PmdSettingType, Int> = AbstractMap.SimpleEntry(key, value)
                    validateSetting(entry)
                }
            }
        }

        private fun validateSelected(settings: Map<PmdSettingType, Int>) {
            for (setting in settings.entries) {
                validateSetting(setting)
            }
        }

        private fun validateSetting(setting: Map.Entry<PmdSettingType, Int>) {
            val fieldSize = typeToFieldSize(setting.key)
            val value = setting.value
            if (fieldSize == 1 && (value < 0x0 || 0xFF < value)) {
                throw RuntimeException("PmdSetting not in valid range. Field size: $fieldSize value: $value")
            }
            if (fieldSize == 2 && (value < 0x0 || 0xFFFF < value)) {
                throw RuntimeException("PmdSetting not in valid range. Field size: $fieldSize value: $value")
            }
            if (fieldSize == 3 && (value < 0x0 || 0xFFFFFF < value)) {
                throw RuntimeException("PmdSetting not in valid range. Field size: $fieldSize value: $value")
            }
        }

        private fun throwAndroidCompatibleParseError(data: ByteArray): Nothing {
            var offset = 0
            while (offset < data.size) {
                val type = PmdSettingType.values()[data[offset++].toInt()]
                var count = data[offset++].toInt()
                while (count-- > 0) {
                    val fieldSize = typeToFieldSize(type)
                    TypeUtils.convertArrayToSignedInt(data, offset, fieldSize)
                    offset += fieldSize
                }
            }
            throw RuntimeException("invalidPMDData")
        }

        private fun PmdSettingType.toSharedType(): PolarPmdSettingType {
            return when (this) {
                PmdSettingType.SAMPLE_RATE -> PolarPmdSettingType.SAMPLE_RATE
                PmdSettingType.RESOLUTION -> PolarPmdSettingType.RESOLUTION
                PmdSettingType.RANGE -> PolarPmdSettingType.RANGE
                PmdSettingType.RANGE_MILLIUNIT -> PolarPmdSettingType.RANGE_MILLIUNIT
                PmdSettingType.CHANNELS -> PolarPmdSettingType.CHANNELS
                PmdSettingType.FACTOR -> PolarPmdSettingType.FACTOR
                PmdSettingType.SECURITY -> PolarPmdSettingType.SECURITY
            }
        }

        private fun PolarPmdSettingType.toAndroidType(): PmdSettingType {
            return when (this) {
                PolarPmdSettingType.SAMPLE_RATE -> PmdSettingType.SAMPLE_RATE
                PolarPmdSettingType.RESOLUTION -> PmdSettingType.RESOLUTION
                PolarPmdSettingType.RANGE -> PmdSettingType.RANGE
                PolarPmdSettingType.RANGE_MILLIUNIT -> PmdSettingType.RANGE_MILLIUNIT
                PolarPmdSettingType.CHANNELS -> PmdSettingType.CHANNELS
                PolarPmdSettingType.FACTOR -> PmdSettingType.FACTOR
                PolarPmdSettingType.SECURITY -> PmdSettingType.SECURITY
            }
        }
    }
}
