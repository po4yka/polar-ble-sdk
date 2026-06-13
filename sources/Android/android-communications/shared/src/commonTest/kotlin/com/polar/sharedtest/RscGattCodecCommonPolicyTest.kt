package com.polar.sharedtest

import com.polar.shared.ble.PolarGattRscCodec
import kotlin.test.Test
import kotlin.test.assertEquals

class RscGattCodecCommonPolicyTest {
    @Test
    fun rscMeasurementVectorsExecuteInSharedCommon() {
        RSC_MEASUREMENT_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val expected = vector.objectValue("expected")
            val measurement = PolarGattRscCodec.parseRscMeasurement(hexToBytes(vector.objectValue("input").stringValue("payloadHex")))
            assertEquals(expected.booleanValue("strideLengthPresent"), measurement.strideLengthPresent, message = vector.stringValue("id"))
            assertEquals(expected.booleanValue("totalDistancePresent"), measurement.totalDistancePresent, message = vector.stringValue("id"))
            assertEquals(expected.booleanValue("running"), measurement.running, message = vector.stringValue("id"))
            assertEquals(expected.longValue("speedRaw"), measurement.speedRaw, message = vector.stringValue("id"))
            assertEquals(expected.doubleValue("speedKmh"), measurement.speedKmh, absoluteTolerance = 0.000001, message = vector.stringValue("id"))
            assertEquals(expected.intValue("cadence"), measurement.cadence, message = vector.stringValue("id"))
            assertEquals(expected.longValue("strideLength"), measurement.strideLength, message = vector.stringValue("id"))
            assertEquals(expected.longValue("totalDistanceRaw"), measurement.totalDistanceRaw, message = vector.stringValue("id"))
            assertEquals(expected.doubleValue("totalDistanceMeters"), measurement.totalDistanceMeters, absoluteTolerance = 0.000001, message = vector.stringValue("id"))
            assertEquals(expected.intValue("flags"), measurement.flags, message = vector.stringValue("id"))
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

    private fun String.longValue(field: String): Long {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toLong() ?: error("Missing long field $field")
    }

    private fun String.doubleValue(field: String): Double {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(this)?.groupValues?.get(1)?.toDouble() ?: error("Missing double field $field")
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
        val RSC_MEASUREMENT_VECTORS = listOf(
            "protocol/gatt/rsc-measurement-stride-distance.json",
            "protocol/gatt/rsc-measurement-required-fields.json"
        )
    }
}
