package com.polar.sharedtest

import com.polar.shared.device.PolarDeviceId
import com.polar.shared.device.PolarDeviceId.IdentifierClassification
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
                assertEquals(expectedAssembled, PolarDeviceId.assembleFull(deviceId ?: ""), caseId)
            }
            expected.optionalBooleanValue("validAfterAssembly")?.let { expectedValid ->
                assertEquals(expectedValid, PolarDeviceId.isValid(PolarDeviceId.assembleFull(deviceId ?: "")), caseId)
            }
            expected.optionalBooleanValue("valid")?.let { expectedValid ->
                assertEquals(expectedValid, PolarDeviceId.isValid(deviceId ?: ""), caseId)
            }
            expected.optionalStringValue("uuid")?.let { expectedUuid ->
                assertEquals(expectedUuid, PolarDeviceId.uuidFromDeviceId(deviceId ?: ""), caseId)
            }
            expected.optionalStringValue("uuidError")?.let { expectedError ->
                val error = runCatching { PolarDeviceId.uuidFromDeviceId(deviceId ?: "") }.exceptionOrNull()
                assertTrue(error is IllegalArgumentException, caseId)
                assertEquals(expectedError, "invalidDeviceIdLength", caseId)
                assertEquals(expected.intValue("expectedLength").toString(), expected.optionalScalarValue("expectedLength"), caseId)
                assertEquals((deviceId ?: "").length.toString(), expected.optionalScalarValue("actualLength"), caseId)
            }
            expected.optionalStringValue("error")?.let { expectedError ->
                assertEquals(expectedError, PolarDeviceId.classifyIdentifier(input.stringValue("identifier")).error, caseId)
            }
            expected.optionalStringValue("status")?.let { status ->
                assertEquals("undecided", status, caseId)
            }
            expected.optionalStringValue("sharedOwnership")?.let { ownership ->
                assertEquals("platform-specific", PolarDeviceId.classifyIdentifier(input.stringValue("identifier")).ownership, caseId)
                assertEquals(true, ownership.contains("platform-specific"), caseId)
            }
        }
    }

    @Test
    fun deviceIdReadinessManifestNamesEverySharedContractBehaviorFamily() {
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

    private val IdentifierClassification.error: String?
        get() = if (this == IdentifierClassification.Invalid) "invalidArgument" else null

    private val IdentifierClassification.ownership: String?
        get() = if (this == IdentifierClassification.PlatformSpecific) "platform-specific" else null

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
        const val DEVICE_ID_READINESS_COMMON_DECISION = "Device ID shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS device ID tests continue to reference the same vectors, checksum width 6 and 7 assembly, zero-value assembly, validation, lowercase acceptance, UUID conversion, invalid UUID length errors, invalid identifier rejection, current empty and non-hex platform decisions, platform-specific identifier routing, and compile verification remain explicit before production checksum or UUID logic moves."
    }
}
