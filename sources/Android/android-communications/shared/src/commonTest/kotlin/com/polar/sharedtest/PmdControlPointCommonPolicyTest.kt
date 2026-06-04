package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PmdControlPointCommonPolicyTest {
    @Test
    fun pmdControlPointGoldenVectorsDefineExecutableCommonResponsePolicy() {
        PMD_CONTROL_POINT_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val expected = vector.objectValue("expected")

            if (relativePath.contains("active-measurement")) {
                val parsed = parseActiveMeasurement(vector.objectValue("input").intValue("responseByte"))
                assertEquals(expected.intValue("activeBits"), parsed.activeBits, caseId)
                assertEquals(expected.intValue("measurementBits"), parsed.measurementBits, caseId)
                assertEquals(expected.stringValue("measurementTypeName"), parsed.measurementTypeName, caseId)
                assertEquals(expected.stringValue("activeMeasurementAndroid"), parsed.androidStateName, caseId)
                assertEquals(expected.stringValue("activeMeasurementIOS"), parsed.iosStateName, caseId)
            } else {
                val parsed = parseControlPointResponse(vector.objectValue("input").stringValue("hex"))
                expected.optionalStringValue("commonDecision")?.let {
                    assertEquals("invalidPMDData", parsed.error, caseId)
                } ?: run {
                    assertEquals(expected.intValue("responseCode"), parsed.responseCode, caseId)
                    assertEquals(expected.stringValue("opCode"), parsed.opCode, caseId)
                    assertEquals(expected.intValue("opCodeValue"), parsed.opCodeValue, caseId)
                    assertEquals(expected.intValue("measurementType"), parsed.measurementType, caseId)
                    assertEquals(expected.stringValue("measurementTypeName"), parsed.measurementTypeName, caseId)
                    assertEquals(expected.stringValue("status"), parsed.status, caseId)
                    assertEquals(expected.intValue("statusValue"), parsed.statusValue, caseId)
                    assertEquals(expected.optionalBooleanValue("more") ?: false, parsed.more, caseId)
                    assertEquals(expected.optionalStringValue("parametersHex") ?: "", parsed.parametersHex, caseId)
                }
            }
        }
    }

    @Test
    fun pmdControlPointReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val vector = loadGoldenVectorText("protocol/pmd/control-point-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")
        assertEquals("pmd-control-point-readiness", vector.stringValue("id"))
        assertEquals("pmdControlPointReadiness", input.stringValue("kind"))
        assertEquals(PMD_CONTROL_POINT_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_PMD_CONTROL_POINT_FAMILIES, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(REQUIRED_PMD_CONTROL_POINT_FAMILIES, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(PMD_CONTROL_POINT_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePmdClientControlPointResponseTest", "com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdActiveMeasurementTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("BlePmdClientTest", "PmdActiveMeasurementTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.PmdControlPointCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun parseActiveMeasurement(responseByte: Int): ActiveMeasurement {
        val activeBits = (responseByte shr 6) and 0x03
        val measurementBits = responseByte and 0x3F
        return ActiveMeasurement(
            activeBits = activeBits,
            measurementBits = measurementBits,
            measurementTypeName = measurementBits.measurementName(),
            androidStateName = activeBits.androidActiveStateName(),
            iosStateName = activeBits.iosActiveStateName()
        )
    }

    private fun parseControlPointResponse(hex: String): ControlPointResponse {
        val bytes = hexToBytes(hex)
        if (bytes.size < 4) {
            return ControlPointResponse(error = "invalidPMDData")
        }
        return ControlPointResponse(
            responseCode = bytes[0].toInt() and 0xFF,
            opCodeValue = bytes[1].toInt() and 0xFF,
            opCode = (bytes[1].toInt() and 0xFF).opCodeName(),
            measurementType = bytes[2].toInt() and 0xFF,
            measurementTypeName = (bytes[2].toInt() and 0xFF).measurementName(),
            statusValue = bytes[3].toInt() and 0xFF,
            status = (bytes[3].toInt() and 0xFF).statusName(),
            more = bytes.size >= 5 && bytes[4].toInt() != 0,
            parametersHex = if (bytes.size > 5) bytes.copyOfRange(5, bytes.size).toHex() else ""
        )
    }

    private fun Int.opCodeName(): String {
        return when (this) {
            1 -> "GET_MEASUREMENT_SETTINGS"
            2 -> "REQUEST_MEASUREMENT_START"
            3 -> "STOP_MEASUREMENT"
            4 -> "GET_SDK_MODE_MEASUREMENT_SETTINGS"
            5 -> "GET_MEASUREMENT_STATUS"
            else -> "UNKNOWN"
        }
    }

    private fun Int.measurementName(): String {
        return when (this) {
            0 -> "ECG"
            1 -> "PPG"
            2 -> "ACC"
            5 -> "GYRO"
            6 -> "MAG"
            9 -> "SDK_MODE"
            11 -> "PRESSURE"
            12 -> "TEMPERATURE"
            13 -> "OFFLINE_RECORDING"
            else -> "UNKNOWN"
        }
    }

    private fun Int.statusName(): String {
        return when (this) {
            0 -> "SUCCESS"
            1 -> "ERROR_INVALID_OP_CODE"
            2 -> "ERROR_INVALID_MEASUREMENT_TYPE"
            3 -> "ERROR_NOT_SUPPORTED"
            4 -> "ERROR_INVALID_LENGTH"
            5 -> "ERROR_INVALID_PARAMETER"
            6 -> "ERROR_ALREADY_IN_STATE"
            7 -> "ERROR_INVALID_RESOLUTION"
            8 -> "ERROR_INVALID_SAMPLE_RATE"
            9 -> "ERROR_INVALID_RANGE"
            10 -> "ERROR_INVALID_MTU"
            11 -> "ERROR_INVALID_NUMBER_OF_CHANNELS"
            12 -> "ERROR_INVALID_STATE"
            13 -> "ERROR_DEVICE_IN_CHARGER"
            14 -> "ERROR_DISK_FULL"
            else -> "UNKNOWN"
        }
    }

    private fun Int.androidActiveStateName(): String {
        return when (this) {
            0 -> "NO_ACTIVE_MEASUREMENT"
            1 -> "ONLINE_MEASUREMENT_ACTIVE"
            2 -> "OFFLINE_MEASUREMENT_ACTIVE"
            3 -> "ONLINE_AND_OFFLINE_ACTIVE"
            else -> "UNKNOWN"
        }
    }

    private fun Int.iosActiveStateName(): String {
        return when (this) {
            0 -> "no_measurement_active"
            1 -> "online_measurement_active"
            2 -> "offline_measurement_active"
            3 -> "online_offline_measurement_active"
            else -> "unknown"
        }
    }

    private fun ByteArray.toHex(): String {
        return joinToString(separator = "") { byte ->
            val value = byte.toInt() and 0xFF
            "${(value / 16).toHexDigit()}${(value % 16).toHexDigit()}"
        }
    }

    private fun Int.toHexDigit(): Char {
        return if (this < 10) '0' + this else 'a' + (this - 10)
    }

    private fun String.optionalBooleanValue(field: String): Boolean? {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" }
    }

    private fun String.stringArrayValue(field: String): List<String> {
        val match = Regex("\"$field\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(this) ?: error("Missing array field $field")
        return Regex("\"([^\"]+)\"").findAll(match.groupValues[1]).map { item -> item.groupValues[1] }.toList()
    }

    private data class ActiveMeasurement(
        val activeBits: Int,
        val measurementBits: Int,
        val measurementTypeName: String,
        val androidStateName: String,
        val iosStateName: String
    )

    private data class ControlPointResponse(
        val responseCode: Int? = null,
        val opCode: String? = null,
        val opCodeValue: Int? = null,
        val measurementType: Int? = null,
        val measurementTypeName: String? = null,
        val status: String? = null,
        val statusValue: Int? = null,
        val more: Boolean = false,
        val parametersHex: String = "",
        val error: String? = null
    )

    private companion object {
        val PMD_CONTROL_POINT_VECTORS = listOf(
            "protocol/pmd/active-measurement-no-active-ecg.json",
            "protocol/pmd/active-measurement-offline-acc.json",
            "protocol/pmd/active-measurement-online-offline-gyro.json",
            "protocol/pmd/active-measurement-online-offline-unknown.json",
            "protocol/pmd/active-measurement-online-ppg.json",
            "protocol/pmd/control-point-error-already-in-state-sdk-mode.json",
            "protocol/pmd/control-point-error-device-in-charger-ecg.json",
            "protocol/pmd/control-point-error-disk-full-offline-recording.json",
            "protocol/pmd/control-point-error-invalid-length-acc.json",
            "protocol/pmd/control-point-error-invalid-measurement-type-unknown.json",
            "protocol/pmd/control-point-error-invalid-mtu-acc.json",
            "protocol/pmd/control-point-error-invalid-number-of-channels-ppg.json",
            "protocol/pmd/control-point-error-invalid-op-code.json",
            "protocol/pmd/control-point-error-invalid-parameter-pressure.json",
            "protocol/pmd/control-point-error-invalid-range-gyro.json",
            "protocol/pmd/control-point-error-invalid-resolution-mag.json",
            "protocol/pmd/control-point-error-invalid-sample-rate-ppg.json",
            "protocol/pmd/control-point-error-invalid-state-stop-temperature.json",
            "protocol/pmd/control-point-error-not-supported-sdk-mode-settings.json",
            "protocol/pmd/control-point-short-empty-android-error.json",
            "protocol/pmd/control-point-short-response-only-android-error.json",
            "protocol/pmd/control-point-short-response-op-android-error.json",
            "protocol/pmd/control-point-short-response-op-type-android-error.json",
            "protocol/pmd/control-point-success-measurement-status.json",
            "protocol/pmd/control-point-success-minimal-no-more-byte.json",
            "protocol/pmd/control-point-success-settings-acc.json",
            "protocol/pmd/control-point-success-start-ppg-more.json",
            "protocol/pmd/control-point-success-stop-ecg.json"
        )
        val REQUIRED_PMD_CONTROL_POINT_FAMILIES = listOf(
            "active-measurement-bit-decoding",
            "active-measurement-platform-state-names",
            "control-point-success-response-parsing",
            "control-point-more-flag-and-parameters",
            "control-point-settings-response",
            "control-point-measurement-status-response",
            "control-point-status-code-coverage",
            "unknown-measurement-type-policy",
            "short-payload-deterministic-error-policy",
            "platform-control-point-vector-reference-gate",
            "compile-verification-gate"
        )
        const val PMD_CONTROL_POINT_READINESS_COMMON_DECISION = "PMD control-point migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS control-point and active-measurement tests continue to reference the same vectors, active-measurement bit decoding and platform state names, success response parsing, more flag and parameter extraction, settings and measurement-status responses, all status-code mappings, unknown measurement type handling, deterministic short-payload error policy, and compile verification remain explicit before production response parsing moves."
    }
}
