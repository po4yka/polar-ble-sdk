package com.polar.shared.ble

import com.polar.shared.device.PolarDeviceId

data class PolarAdvertisementName(
    val name: String,
    val deviceType: String,
    val deviceId: String
)

object PolarAdvertisementModels {
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
}
