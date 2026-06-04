package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals

class TypeUtilsCommonPolicyTest {
    @Test
    fun typeUtilsGoldenVectorsDefineExecutableCommonByteConversionPolicy() {
        TYPE_UTILS_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val bytes = hexToBytes(input.stringValue("hex"))
            val offset = input.optionalIntValue("offset")
            val size = input.optionalIntValue("size")
            val expected = vector.commonExpectedObject()

            expected.optionalScalarValue("unsignedByte")?.let { expectedValue ->
                assertEquals(expectedValue, convertUnsignedByte(bytes).value, caseId)
            }
            expected.optionalScalarValue("unsignedInt")?.let { expectedValue ->
                assertEquals(expectedValue, convertUnsignedInt(bytes, offset, size).value, caseId)
            }
            expected.optionalScalarValue("unsignedLong")?.let { expectedValue ->
                assertEquals(expectedValue, convertUnsignedLong(bytes, offset, size).value, caseId)
            }
            expected.optionalScalarValue("signedInt")?.let { expectedValue ->
                assertEquals(expectedValue, convertSignedInt(bytes, offset, size).value, caseId)
            }
            expected.optionalStringValue("unsignedIntError")?.let { expectedError ->
                assertEquals(expectedError, convertUnsignedInt(bytes, offset, size).error, caseId)
            }
            expected.optionalStringValue("unsignedLongError")?.let { expectedError ->
                assertEquals(expectedError, convertUnsignedLong(bytes, offset, size).error, caseId)
            }
            expected.optionalStringValue("signedIntError")?.let { expectedError ->
                assertEquals(expectedError, convertSignedInt(bytes, offset, size).error, caseId)
            }
        }
    }

    @Test
    fun typeUtilsReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val vector = loadGoldenVectorText("protocol/type-utils/type-utils-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumers = vector.objectValue("consumerTests")
        val platforms = vector.objectValue("platforms")
        assertEquals("type-utils-readiness", vector.stringValue("id"))
        assertEquals("type-utils", vector.stringValue("area"))
        assertEquals("type_utils_readiness", vector.stringValue("case"))
        assertEquals("typeUtilsReadiness", input.stringValue("kind"))
        assertEquals(TYPE_UTILS_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(requiredTypeUtilsFamilies, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(requiredTypeUtilsFamilies, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(TYPE_UTILS_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.androidcommunications.common.ble.TypeUtilsTest"), consumers.stringArrayValue("android"))
        assertEquals(listOf("TypeUtilsTest"), consumers.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.TypeUtilsCommonPolicyTest"), consumers.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private fun String.commonExpectedObject(): String {
        val platformExpectations = optionalObjectValue("platformExpectations")
        return if (platformExpectations?.contains("\"commonDecision\"\\s*:\\s*\\{".toRegex()) == true) {
            platformExpectations.objectValue("commonDecision")
        } else {
            objectValue("expected")
        }
    }

    private fun convertUnsignedByte(bytes: ByteArray): ConversionResult {
        return when {
            bytes.isEmpty() -> ConversionResult(error = "emptyPayload")
            bytes.size > 1 -> ConversionResult(error = "payloadTooLong")
            else -> ConversionResult(value = (bytes[0].toInt() and 0xFF).toString())
        }
    }

    private fun convertUnsignedInt(bytes: ByteArray, offset: Int?, size: Int?): ConversionResult {
        val selected = selectedBytes(bytes, offset, size)
        return when {
            selected.isEmpty() -> ConversionResult(error = "emptyPayload")
            selected.size > 4 -> ConversionResult(error = "payloadTooLong")
            else -> ConversionResult(value = selected.unsignedLittleEndianLong().toString())
        }
    }

    private fun convertUnsignedLong(bytes: ByteArray, offset: Int?, size: Int?): ConversionResult {
        val selected = selectedBytes(bytes, offset, size)
        return when {
            selected.isEmpty() -> ConversionResult(error = "emptyPayload")
            selected.size > 8 -> ConversionResult(error = "payloadTooLong")
            else -> ConversionResult(value = selected.unsignedLittleEndianDecimalString())
        }
    }

    private fun convertSignedInt(bytes: ByteArray, offset: Int?, size: Int?): ConversionResult {
        val selected = selectedBytes(bytes, offset, size)
        return when {
            selected.isEmpty() -> ConversionResult(error = "emptyPayload")
            selected.size > 4 -> ConversionResult(error = "payloadTooLong")
            else -> {
                val value = selected.unsignedLittleEndianLong()
                val bitWidth = selected.size * 8
                val signBit = 1L shl (bitWidth - 1)
                val signed = if ((value and signBit) != 0L) value - (1L shl bitWidth) else value
                ConversionResult(value = signed.toString())
            }
        }
    }

    private fun selectedBytes(bytes: ByteArray, offset: Int?, size: Int?): ByteArray {
        return if (offset != null && size != null) {
            bytes.copyOfRange(offset, offset + size)
        } else {
            bytes
        }
    }

    private fun ByteArray.unsignedLittleEndianLong(): Long {
        var result = 0L
        forEachIndexed { index, byte ->
            result = result or ((byte.toLong() and 0xFFL) shl (index * 8))
        }
        return result
    }

    private fun ByteArray.unsignedLittleEndianDecimalString(): String {
        var result = "0"
        reversedArray().forEach { byte ->
            result = result.multiplyBy(256).add(byte.toInt() and 0xFF)
        }
        return result
    }

    private fun String.multiplyBy(multiplier: Int): String {
        var carry = 0
        val digits = StringBuilder()
        reversed().forEach { char ->
            val product = (char - '0') * multiplier + carry
            digits.append(product % 10)
            carry = product / 10
        }
        while (carry > 0) {
            digits.append(carry % 10)
            carry /= 10
        }
        return digits.reverse().toString().trimLeadingZeroes()
    }

    private fun String.add(addend: Int): String {
        var carry = addend
        val digits = StringBuilder()
        reversed().forEach { char ->
            val sum = (char - '0') + carry
            digits.append(sum % 10)
            carry = sum / 10
        }
        while (carry > 0) {
            digits.append(carry % 10)
            carry /= 10
        }
        return digits.reverse().toString().trimLeadingZeroes()
    }

    private fun String.trimLeadingZeroes(): String {
        val trimmed = trimStart('0')
        return if (trimmed.isEmpty()) "0" else trimmed
    }

    private fun String.optionalObjectValue(field: String): String? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val objectStart = indexOf('{', fieldIndex)
        if (objectStart < 0) return null
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.optionalIntValue(field: String): Int? {
        return Regex("\"$field\"\\s*:\\s*(\\d+)").find(this)?.groupValues?.get(1)?.toInt()
    }

    private fun String.optionalScalarValue(field: String): String? {
        val quoted = Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"").find(this)
        if (quoted != null) return quoted.groupValues[1]
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)
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

    private data class ConversionResult(
        val value: String? = null,
        val error: String? = null
    )

    private companion object {
        val TYPE_UTILS_VECTORS = listOf(
            "protocol/type-utils/empty-payload-platform-difference.json",
            "protocol/type-utils/offset-signed-int-negative-boundary.json",
            "protocol/type-utils/offset-unsigned-int-little-endian.json",
            "protocol/type-utils/signed-int-24bit-negative-one.json",
            "protocol/type-utils/signed-int-max.json",
            "protocol/type-utils/signed-int-min-16bit.json",
            "protocol/type-utils/signed-int-min-24bit.json",
            "protocol/type-utils/signed-int-min-32bit.json",
            "protocol/type-utils/signed-int-negative-one.json",
            "protocol/type-utils/signed-int-too-long.json",
            "protocol/type-utils/unsigned-byte-max.json",
            "protocol/type-utils/unsigned-int-high-bit-16bit-platform-difference.json",
            "protocol/type-utils/unsigned-int-high-bit-platform-difference.json",
            "protocol/type-utils/unsigned-int-little-endian.json",
            "protocol/type-utils/unsigned-int-too-long.json",
            "protocol/type-utils/unsigned-long-max.json",
            "protocol/type-utils/unsigned-long-too-long.json"
        )
        val requiredTypeUtilsFamilies = listOf(
            "unsigned-byte-conversion",
            "little-endian-unsigned-int-conversion",
            "little-endian-unsigned-long-conversion",
            "signed-int-sign-extension",
            "offset-and-size-selection",
            "signed-minimum-boundaries",
            "unsigned-high-bit-platform-decision",
            "empty-payload-error-policy",
            "payload-too-long-error-policy",
            "uint64-max-decimal-preservation",
            "platform-type-utils-vector-reference-gate",
            "compile-verification-gate"
        )
        const val TYPE_UTILS_READINESS_COMMON_DECISION = "Type utility migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS type utility tests continue to reference the same vectors, unsigned byte/int/long conversion, signed sign extension, offset and size selection, signed-minimum boundaries, high-bit unsigned platform decisions, empty payload and payload-too-long typed errors, UInt64 max decimal preservation, and compile verification remain explicit before production parser primitives move."
    }
}
