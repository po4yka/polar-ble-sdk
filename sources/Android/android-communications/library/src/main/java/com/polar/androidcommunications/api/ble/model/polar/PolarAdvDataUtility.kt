package com.polar.androidcommunications.api.ble.model.polar

import com.polar.shared.ble.PolarAdvertisementModels

object PolarAdvDataUtility {

    fun getDeviceModelNameFromAdvLocalName(advLocalName: String, withPrefixToTrim: String = "Polar"): String {
        return PolarAdvertisementModels.deviceModelNameFromLocalName(advLocalName, withPrefixToTrim)
    }

    fun isValidDevice(name: String, requiredPrefix: String = "Polar"): Boolean {
        return PolarAdvertisementModels.isValidDeviceLocalName(name, requiredPrefix)
    }
}
