package com.polar.sharedtest

import com.polar.shared.ble.PolarGattPsdCodec
import kotlin.test.Test
import kotlin.test.assertEquals

class PsdGattCodecCommonPolicyTest {
    @Test
    fun psdFeatureVectorsExecuteInSharedCommon() {
        PSD_FEATURE_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val expected = vector.objectValue("expected")
            val feature = PolarGattPsdCodec.parsePsdFeature(hexToBytes(vector.objectValue("input").stringValue("payloadHex")))
            assertEquals(expected.booleanValue("ecgSupported"), feature.ecgSupported, message = vector.stringValue("id"))
            assertEquals(expected.booleanValue("ohrSupported"), feature.ohrSupported, message = vector.stringValue("id"))
            assertEquals(expected.booleanValue("accSupported"), feature.accSupported, message = vector.stringValue("id"))
            assertEquals(expected.booleanValue("ppSupported"), feature.ppSupported, message = vector.stringValue("id"))
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
        val PSD_FEATURE_VECTORS = listOf(
            "protocol/gatt/psd-feature-none-supported.json",
            "protocol/gatt/psd-feature-all-supported.json"
        )
    }
}
