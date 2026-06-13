package com.polar.sharedtest

import com.polar.shared.ble.PolarGattHtsCodec
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtsGattCodecCommonPolicyTest {
    @Test
    fun htsTemperatureMeasurementVectorsExecuteInSharedCommon() {
        HTS_TEMPERATURE_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")
            val measurement = PolarGattHtsCodec.parseTemperatureMeasurement(hexToBytes(input.stringValue("payloadHex")))
            assertClose(expected.floatValue("temperatureCelsius"), measurement.temperatureCelsius, vector.stringValue("id"))
            assertClose(expected.floatValue("temperatureFahrenheit"), measurement.temperatureFahrenheit, vector.stringValue("id"))
            assertEquals(expected.booleanValue("isFahrenheit"), measurement.isFahrenheit, message = vector.stringValue("id"))
            assertEquals(expected.intValue("exponent"), measurement.exponent, message = vector.stringValue("id"))
            assertEquals(expected.intValue("mantissa"), measurement.mantissa, message = vector.stringValue("id"))
        }
    }

    private fun assertClose(expected: Float, actual: Float, message: String) {
        assertTrue(abs(expected - actual) < 0.001f, "$message expected=$expected actual=$actual")
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

    private fun String.floatValue(field: String): Float {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(this)?.groupValues?.get(1)?.toFloat() ?: error("Missing float field $field")
    }

    private fun String.intValue(field: String): Int {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toInt() ?: error("Missing int field $field")
    }

    private fun String.booleanValue(field: String): Boolean {
        val value = Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1) ?: error("Missing boolean field $field")
        return value == "true"
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
        val HTS_TEMPERATURE_VECTORS = listOf(
            "protocol/gatt/hts-temperature-celsius-centesimal.json",
            "protocol/gatt/hts-temperature-fahrenheit-centesimal.json"
        )
    }
}
