package com.polar.sharedtest

import com.polar.shared.ble.PolarGattBasCodec
import kotlin.test.Test
import kotlin.test.assertEquals

class BasGattCodecCommonPolicyTest {
    @Test
    fun basBatteryStatusVectorsExecuteInSharedCommon() {
        BAS_STATUS_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val expected = vector.objectValue("expected")
            val status = PolarGattBasCodec.parseBatteryStatus(hexToBytes(vector.objectValue("input").stringValue("payloadHex")))
            assertEquals(expected.stringValue("chargeState"), status.chargeState.name, message = vector.stringValue("id"))
            assertEquals(expected.stringValue("batteryPresent"), status.powerSources.batteryPresent.name, message = vector.stringValue("id"))
            assertEquals(expected.stringValue("wiredExternalPower"), status.powerSources.wiredExternalPower.name, message = vector.stringValue("id"))
            assertEquals(expected.stringValue("wirelessExternalPower"), status.powerSources.wirelessExternalPower.name, message = vector.stringValue("id"))
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have an even length" }
        return hex.chunked(2).map { byte -> byte.toInt(16).toByte() }.toByteArray()
    }

    private fun String.objectValue(field: String): String {
        val fieldIndex = indexOf("\"$field\"")
        require(fieldIndex >= 0) { "Missing object field $field" }
        val objectStart = indexOf('{', fieldIndex)
        require(objectStart >= 0) { "Missing object value for $field" }
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.stringValue(field: String): String {
        return Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"").find(this)?.groupValues?.get(1) ?: error("Missing string field $field")
    }

    private fun String.balancedEnd(start: Int, open: Char, close: Char): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until length) {
            val char = this[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = inString
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (!inString && char == open) depth += 1
            if (!inString && char == close) {
                depth -= 1
                if (depth == 0) return index
            }
        }
        error("Unbalanced $open$close block")
    }

    private companion object {
        val BAS_STATUS_VECTORS = listOf(
            "protocol/gatt/bas-status-unknown-no-power.json",
            "protocol/gatt/bas-status-charging-wired-present.json",
            "protocol/gatt/bas-status-discharging-active-unknown-power.json",
            "protocol/gatt/bas-status-discharging-inactive-reserved-power.json"
        )
    }
}
