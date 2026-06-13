package com.polar.sharedtest

import com.polar.shared.ble.PolarGattHrCodec
import kotlin.test.Test
import kotlin.test.assertEquals

class HrGattCodecCommonPolicyTest {
    @Test
    fun hrMeasurementVectorsExecuteInSharedCommon() {
        HR_MEASUREMENT_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val expected = vector.objectValue("expected")
            val measurement = PolarGattHrCodec.parseHrMeasurement(hexToBytes(vector.objectValue("input").stringValue("payloadHex")))
            assertEquals(expected.intValue("hr"), measurement.hr, message = vector.stringValue("id"))
            assertEquals(expected.booleanValue("sensorContact"), measurement.sensorContact, message = vector.stringValue("id"))
            assertEquals(expected.booleanValue("sensorContactSupported"), measurement.sensorContactSupported, message = vector.stringValue("id"))
            assertEquals(expected.intValue("energy"), measurement.energy, message = vector.stringValue("id"))
            assertEquals(expected.intArrayValue("rrs"), measurement.rrs, message = vector.stringValue("id"))
            assertEquals(expected.intArrayValue("rrsMs"), measurement.rrsMs, message = vector.stringValue("id"))
            assertEquals(expected.booleanValue("rrPresent"), measurement.rrPresent, message = vector.stringValue("id"))
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

    private fun String.intValue(field: String): Int {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toInt() ?: error("Missing int field $field")
    }

    private fun String.booleanValue(field: String): Boolean {
        val value = Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1) ?: error("Missing boolean field $field")
        return value == "true"
    }

    private fun String.intArrayValue(field: String): List<Int> {
        val arrayText = Regex("\"$field\"\\s*:\\s*\\[([^\\]]*)\\]").find(this)?.groupValues?.get(1) ?: error("Missing int array field $field")
        if (arrayText.trim().isEmpty()) return emptyList()
        return arrayText.split(",").map { value -> value.trim().toInt() }
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
        val HR_MEASUREMENT_VECTORS = listOf(
            "protocol/gatt/hr-measurement-uint8-max.json",
            "protocol/gatt/hr-measurement-uint16-boundary.json",
            "protocol/gatt/hr-measurement-sensor-contact.json",
            "protocol/gatt/hr-measurement-energy.json",
            "protocol/gatt/hr-measurement-rr-intervals.json"
        )
    }
}
