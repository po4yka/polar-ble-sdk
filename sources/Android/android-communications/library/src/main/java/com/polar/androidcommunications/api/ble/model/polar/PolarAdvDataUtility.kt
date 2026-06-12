package com.polar.androidcommunications.api.ble.model.polar

import com.polar.sdk.api.model.PolarSdkModelAdapter

object PolarAdvDataUtility {

    fun getDeviceModelNameFromAdvLocalName(advLocalName: String, withPrefixToTrim: String = "Polar"): String {
        return PolarSdkModelAdapter.advertisementDeviceModelNameFromLocalName(advLocalName, withPrefixToTrim)
    }

    fun isValidDevice(name: String, requiredPrefix: String = "Polar"): Boolean {
        return PolarSdkModelAdapter.isValidAdvertisementLocalName(name, requiredPrefix)
    }
}
