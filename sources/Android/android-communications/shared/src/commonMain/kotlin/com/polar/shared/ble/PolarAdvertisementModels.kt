package com.polar.shared.ble

import com.polar.shared.device.PolarDeviceId

data class PolarAdvertisementName(
    val name: String,
    val deviceType: String,
    val deviceId: String
)

object PolarAdvertisementModels {
    private const val POLAR_COMPANY_ID_LSB = 0x6B
    private const val POLAR_COMPANY_ID_MSB = 0x00
    private const val GPB_DATA_BIT = 0x40
    private const val OLD_H7_MANUFACTURER_DATA_LENGTH = 3
    private const val H7_UPDATE_MANUFACTURER_DATA_LENGTH = 4

    fun isValidDeviceLocalName(localName: String, requiredPrefix: String = "Polar"): Boolean {
        val trimmed = localName.trim()
        return trimmed.startsWith(requiredPrefix) && trimmed.split(" ").filter { part -> part.isNotEmpty() }.size > 2
    }

    fun deviceModelNameFromLocalName(localName: String, prefixToTrim: String = "Polar"): String {
        if (!isValidDeviceLocalName(localName, prefixToTrim)) return ""
        val modelName = localName.trim().replaceFirst(if (prefixToTrim != "") "$prefixToTrim " else "", "")
        val endIndex = modelName.lastIndexOf(" ")
        return if (endIndex >= 0) modelName.substring(0, endIndex) else ""
    }

    fun parseLocalName(localName: String, deviceNamePrefix: String = "Polar"): PolarAdvertisementName {
        val parts = localName.split(" ").filter { part -> part.isNotEmpty() }
        if (parts.isEmpty() || parts.first() != deviceNamePrefix) {
            return PolarAdvertisementName(name = localName, deviceType = "", deviceId = "")
        }
        val idToken = parts.last()
        return PolarAdvertisementName(
            name = localName,
            deviceType = parts.drop(1).dropLast(1).joinToString(" "),
            deviceId = PolarDeviceId.assembleFull(idToken)
        )
    }

    fun polarManufacturerHrPayloads(manufacturerData: ByteArray): List<ByteArray> {
        if (manufacturerData.size < 2) return emptyList()
        val isPolarCompany = manufacturerData[0].unsigned() == POLAR_COMPANY_ID_LSB && manufacturerData[1].unsigned() == POLAR_COMPANY_ID_MSB
        if (!isPolarCompany) return emptyList()

        val payloadLength = manufacturerData.size - 2
        if (payloadLength == OLD_H7_MANUFACTURER_DATA_LENGTH || payloadLength == H7_UPDATE_MANUFACTURER_DATA_LENGTH) {
            return listOf(manufacturerData.copyOfRange(2, manufacturerData.size))
        }

        var offset = 2
        val payloads = mutableListOf<ByteArray>()
        while (offset < manufacturerData.size) {
            val header = manufacturerData[offset].unsigned()
            if ((header and GPB_DATA_BIT) == 0) {
                if (offset + OLD_H7_MANUFACTURER_DATA_LENGTH <= manufacturerData.size) {
                    payloads += manufacturerData.copyOfRange(offset, manufacturerData.size)
                }
                offset += OLD_H7_MANUFACTURER_DATA_LENGTH
            } else {
                val lengthOffset = offset + 1
                if (lengthOffset >= manufacturerData.size) return payloads
                offset += manufacturerData[lengthOffset].unsigned() + 2
            }
        }
        return payloads
    }
}

private fun Byte.unsigned(): Int {
    return toInt() and 0xFF
}
