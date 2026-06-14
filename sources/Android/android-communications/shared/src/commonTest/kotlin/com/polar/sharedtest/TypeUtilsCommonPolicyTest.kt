package com.polar.sharedtest

import com.polar.shared.ble.PolarTypeConversion
import com.polar.shared.ble.PolarTypeConversionError
import com.polar.shared.ble.PolarTypeUtils
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
                assertEquals(expectedValue, PolarTypeUtils.convertArrayToUnsignedByte(bytes).value, caseId)
            }
            expected.optionalScalarValue("unsignedInt")?.let { expectedValue ->
                assertEquals(expectedValue, bytes.convertUnsignedInt(offset, size).value, caseId)
            }
            expected.optionalScalarValue("unsignedLong")?.let { expectedValue ->
                assertEquals(expectedValue, bytes.convertUnsignedLong(offset, size).value, caseId)
            }
            expected.optionalScalarValue("signedInt")?.let { expectedValue ->
                assertEquals(expectedValue, bytes.convertSignedInt(offset, size).value, caseId)
            }
            expected.optionalStringValue("unsignedIntError")?.let { expectedError ->
                assertEquals(expectedError, bytes.convertUnsignedInt(offset, size).error?.vectorName, caseId)
            }
            expected.optionalStringValue("unsignedLongError")?.let { expectedError ->
                assertEquals(expectedError, bytes.convertUnsignedLong(offset, size).error?.vectorName, caseId)
            }
            expected.optionalStringValue("signedIntError")?.let { expectedError ->
                assertEquals(expectedError, bytes.convertSignedInt(offset, size).error?.vectorName, caseId)
            }
        }
    }

    @Test
    fun typeUtilsReadinessManifestNamesEverySharedContractBehaviorFamily() {
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
        assertEquals("emptyPayload", PolarTypeConversionError.EmptyPayload.vectorName)
        assertEquals("payloadTooLong", PolarTypeConversionError.PayloadTooLong.vectorName)
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

    private fun ByteArray.convertUnsignedInt(offset: Int?, size: Int?): PolarTypeConversion<String> {
        return if (offset != null && size != null) {
            PolarTypeUtils.convertArrayToUnsignedInt(this, offset, size)
        } else {
            PolarTypeUtils.convertArrayToUnsignedInt(this)
        }
    }

    private fun ByteArray.convertUnsignedLong(offset: Int?, size: Int?): PolarTypeConversion<String> {
        return if (offset != null && size != null) {
            PolarTypeUtils.convertArrayToUnsignedLong(this, offset, size)
        } else {
            PolarTypeUtils.convertArrayToUnsignedLong(this)
        }
    }

    private fun ByteArray.convertSignedInt(offset: Int?, size: Int?): PolarTypeConversion<String> {
        return if (offset != null && size != null) {
            PolarTypeUtils.convertArrayToSignedInt(this, offset, size)
        } else {
            PolarTypeUtils.convertArrayToSignedInt(this)
        }
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
        const val TYPE_UTILS_READINESS_COMMON_DECISION = "Type utility shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS type utility tests continue to reference the same vectors, unsigned byte/int/long conversion, signed sign extension, offset and size selection, signed-minimum boundaries, high-bit unsigned platform decisions, empty payload and payload-too-long typed errors, UInt64 max decimal preservation, and compile verification remain explicit before production parser primitives move."
    }
}
