package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceIdCommonPolicyTest {
    @Test
    fun deviceIdGoldenVectorsDefineExecutableCommonChecksumAndUuidPolicy() {
        DEVICE_ID_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.commonExpectedObject()
            val deviceId = input.optionalStringValue("deviceId")

            expected.optionalStringValue("assembled")?.let { expectedAssembled ->
                assertEquals(expectedAssembled, assembleFullDeviceId(deviceId ?: ""), caseId)
            }
            expected.optionalBooleanValue("validAfterAssembly")?.let { expectedValid ->
                assertEquals(expectedValid, validateDeviceId(assembleFullDeviceId(deviceId ?: "")).valid, caseId)
            }
            expected.optionalBooleanValue("valid")?.let { expectedValid ->
                assertEquals(expectedValid, validateDeviceId(deviceId ?: "").valid, caseId)
            }
            expected.optionalStringValue("uuid")?.let { expectedUuid ->
                assertEquals(expectedUuid, polarUuidFromDeviceId(deviceId ?: "").value, caseId)
            }
            expected.optionalStringValue("uuidError")?.let { expectedError ->
                assertEquals(expectedError, polarUuidFromDeviceId(deviceId ?: "").error, caseId)
                assertEquals(expected.intValue("expectedLength").toString(), expected.optionalScalarValue("expectedLength"), caseId)
                assertEquals((deviceId ?: "").length.toString(), expected.optionalScalarValue("actualLength"), caseId)
            }
            expected.optionalStringValue("error")?.let { expectedError ->
                assertEquals(expectedError, routeIdentifier(input.stringValue("identifier")).error, caseId)
            }
            expected.optionalStringValue("status")?.let { status ->
                assertEquals("undecided", status, caseId)
            }
            expected.optionalStringValue("migrationOwnership")?.let { ownership ->
                assertEquals("platform-specific", routeIdentifier(input.stringValue("identifier")).ownership, caseId)
                assertEquals(true, ownership.contains("platform-specific"), caseId)
            }
        }
    }

    @Test
    fun deviceIdReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val vector = loadGoldenVectorText("protocol/device-id/device-id-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")
        assertEquals("device-id-readiness", vector.stringValue("id"))
        assertEquals("deviceIdReadiness", input.stringValue("kind"))
        assertEquals(DEVICE_ID_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_DEVICE_ID_FAMILIES, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(REQUIRED_DEVICE_ID_FAMILIES, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(DEVICE_ID_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.polar.DeviceIdGoldenVectorTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("BlePolarDeviceIdUtilityTest", "PolarDeviceUuidTest", "PolarServiceClientUtilsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.DeviceIdCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun String.commonExpectedObject(): String {
        val platformExpectations = optionalObjectValue("platformExpectations")
        return if (platformExpectations?.contains("\"commonDecision\"\\s*:\\s*\\{".toRegex()) == true) {
            platformExpectations.objectValue("commonDecision")
        } else {
            objectValue("expected")
        }
    }

    private fun assembleFullDeviceId(deviceId: String): String {
        val numeric = deviceId.hexToLongOrNull() ?: return ""
        return when (deviceId.length) {
            6 -> deviceId + "1" + checksum(numeric, width = 6).toUpperHexDigit()
            7 -> deviceId + checksum(numeric, width = 7).toUpperHexDigit()
            else -> deviceId
        }
    }

    private fun validateDeviceId(deviceId: String): ValidationResult {
        val numeric = deviceId.hexToLongOrNull() ?: return ValidationResult(valid = false, error = "invalidHex")
        return if (deviceId.length == 8) {
            ValidationResult(valid = checksum(numeric, width = 8).toLong() == (numeric and 0x0F))
        } else {
            ValidationResult(valid = checksum(numeric, width = deviceId.length) != 0)
        }
    }

    private fun polarUuidFromDeviceId(deviceId: String): PolicyResult {
        if (deviceId.length != 8) {
            return PolicyResult(error = "invalidDeviceIdLength")
        }
        return PolicyResult(value = "0e030000-0084-0000-0000-0000$deviceId")
    }

    private fun routeIdentifier(identifier: String): IdentifierRoute {
        return when {
            identifier.matches(Regex("[0-9A-Fa-f]{6,8}")) -> IdentifierRoute(match = "deviceId")
            identifier.contains(":") || identifier.contains("-") -> IdentifierRoute(ownership = "platform-specific")
            else -> IdentifierRoute(error = "invalidArgument")
        }
    }

    private fun checksum(deviceId: Long, width: Int): Int {
        var shiftOffset = 0
        var a2 = 0x01
        when (width) {
            8 -> {
                a2 = ((deviceId shr 4) and 0x0F).toInt()
                shiftOffset = 8
            }
            7 -> {
                a2 = (deviceId and 0x0F).toInt()
                shiftOffset = 4
            }
        }
        val a3 = ((deviceId shr shiftOffset) and 0x0F).toInt()
        val a4 = ((deviceId shr shiftOffset + 4) and 0x0F).toInt()
        val a5 = ((deviceId shr shiftOffset + 8) and 0x0F).toInt()
        val a6 = ((deviceId shr shiftOffset + 12) and 0x0F).toInt()
        val a7 = ((deviceId shr shiftOffset + 16) and 0x0F).toInt()
        val a8 = ((deviceId shr shiftOffset + 20) and 0x0F).toInt()
        return (3 * (a2 + a4 + a6 + a8) + a3 + a5 + a7) % 16
    }

    private fun String.hexToLongOrNull(): Long? {
        if (isEmpty() || any { char -> char.hexDigitValueOrNull() == null }) return null
        var result = 0L
        forEach { char ->
            result = (result shl 4) or char.hexDigitValueOrNull()!!.toLong()
        }
        return result
    }

    private fun Int.toUpperHexDigit(): String {
        val digit = if (this < 10) {
            '0' + this
        } else {
            'A' + (this - 10)
        }
        return digit.toString()
    }

    private fun Char.hexDigitValueOrNull(): Int? {
        return when (this) {
            in '0'..'9' -> this - '0'
            in 'a'..'f' -> this - 'a' + 10
            in 'A'..'F' -> this - 'A' + 10
            else -> null
        }
    }

    private fun String.optionalObjectValue(field: String): String? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val objectStart = indexOf('{', fieldIndex)
        if (objectStart < 0) return null
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.optionalBooleanValue(field: String): Boolean? {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" }
    }

    private fun String.optionalScalarValue(field: String): String? {
        val quoted = Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"").find(this)
        if (quoted != null) return quoted.groupValues[1]
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)
    }

    private fun String.stringArrayValue(field: String): List<String> {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) error("Missing field $field")
        val arrayStart = indexOf('[', fieldIndex)
        if (arrayStart < 0) error("Missing array field $field")
        val array = substring(arrayStart, balancedEnd(arrayStart, '[', ']') + 1)
        return Regex("\"([^\"]+)\"").findAll(array).map { match -> match.groupValues[1] }.toList()
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

    private data class ValidationResult(
        val valid: Boolean,
        val error: String? = null
    )

    private data class PolicyResult(
        val value: String? = null,
        val error: String? = null
    )

    private data class IdentifierRoute(
        val match: String? = null,
        val error: String? = null,
        val ownership: String? = null
    )

    private companion object {
        val DEVICE_ID_VECTORS = listOf(
            "protocol/device-id/assemble-seven-digit-device-id.json",
            "protocol/device-id/assemble-six-digit-device-id.json",
            "protocol/device-id/assemble-zero-seven-digit-device-id.json",
            "protocol/device-id/empty-device-id-platform-difference.json",
            "protocol/device-id/identifier-bluetooth-address-android.json",
            "protocol/device-id/identifier-invalid-format.json",
            "protocol/device-id/identifier-uuid-string-ios.json",
            "protocol/device-id/invalid-checksum-device-id.json",
            "protocol/device-id/non-hex-device-id-platform-difference.json",
            "protocol/device-id/polar-device-uuid-invalid-length.json",
            "protocol/device-id/polar-device-uuid-valid.json",
            "protocol/device-id/valid-lowercase-device-id.json"
        )
        val REQUIRED_DEVICE_ID_FAMILIES = listOf(
            "checksum-width-6-assembly",
            "checksum-width-7-assembly",
            "zero-device-id-assembly",
            "valid-device-id-validation",
            "invalid-checksum-validation",
            "lowercase-device-id-validation",
            "empty-input-platform-decision",
            "non-hex-input-platform-decision",
            "uuid-conversion",
            "uuid-invalid-length-error",
            "identifier-invalid-format-error",
            "platform-specific-identifier-routing",
            "platform-device-id-vector-reference-gate",
            "compile-verification-gate"
        )
        const val DEVICE_ID_READINESS_COMMON_DECISION = "Device ID migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS device ID tests continue to reference the same vectors, checksum width 6 and 7 assembly, zero-value assembly, validation, lowercase acceptance, UUID conversion, invalid UUID length errors, invalid identifier rejection, current empty and non-hex platform decisions, platform-specific identifier routing, and compile verification remain explicit before production checksum or UUID logic moves."
    }
}
