package com.polar.sharedtest

import com.polar.shared.ble.PolarBasBatteryStatusCodec
import kotlin.test.Test
import kotlin.test.assertEquals

class BasBatteryStatusCodecCommonPolicyTest {
    @Test
    fun basBatteryStatusBitfieldVectorDefinesPureSharedCodecPolicy() {
        val vector = loadGoldenVectorText("protocol/gatt/bas-battery-status-bitfields.json")
        val inputCases = vector.objectValue("input").objectArray("cases")
        val expectedCases = vector.objectValue("expected").objectArray("cases")

        assertEquals("bas-battery-status-bitfields", vector.stringValue("id"))
        assertEquals("gatt.bas", vector.stringValue("area"))
        assertEquals("bas_battery_status_bitfields", vector.stringValue("case"))
        assertEquals("basBatteryStatusBitfields", vector.objectValue("input").stringValue("kind"))
        assertEquals(BAS_BATTERY_STATUS_CASE_IDS, inputCases.map { it.stringValue("id") })
        assertEquals(inputCases.map { it.stringValue("id") }, expectedCases.map { it.stringValue("id") }, vector.stringValue("id"))
        assertEquals(BAS_BATTERY_STATUS_COMMON_DECISION, vector.objectValue("expected").stringValue("commonDecision"))

        inputCases.zip(expectedCases).forEach { (input, expected) ->
            val decoded = PolarBasBatteryStatusCodec.decode(hexToBytes(input.stringValue("statusHex")))
            assertEquals(input.intValue("statusByte"), decoded.statusByte, input.stringValue("id"))
            assertEquals(expected.stringValue("chargeState"), decoded.chargeState.name, input.stringValue("id"))
            assertEquals(expected.stringValue("batteryPresent"), decoded.batteryPresent.name, input.stringValue("id"))
            assertEquals(expected.stringValue("wiredExternalPowerConnected"), decoded.wiredExternalPowerConnected.name, input.stringValue("id"))
            assertEquals(expected.stringValue("wirelessExternalPowerConnected"), decoded.wirelessExternalPowerConnected.name, input.stringValue("id"))
        }

        val consumerTests = vector.objectValue("consumerTests")
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.BleBattClientTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("BleBasClientTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.BasBatteryStatusCodecCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("android"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("ios"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("common"))
    }
}

private const val BAS_BATTERY_STATUS_COMMON_DECISION = "Only the BAS Battery Level Status byte parser moves to shared KMP; characteristic routing, read/notify behavior, stream caching, disconnection, and platform GATT lifecycle stay in Android BleBattClient and iOS BleBasClient."

private val BAS_BATTERY_STATUS_CASE_IDS = listOf(
    "charging-present-wired-connected-wireless-not-connected",
    "discharging-active-present-wired-not-connected-wireless-connected",
    "discharging-inactive-present-wired-reserved-wireless-reserved",
    "unknown-not-present-wired-unknown-wireless-unknown"
)
