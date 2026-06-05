package com.polar.sharedtest

import com.polar.shared.pmd.PolarPmdControlPoint
import com.polar.shared.pmd.PolarPmdMeasurementTypeName
import com.polar.shared.pmd.PolarPmdParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PmdControlPointCommonPolicyTest {
    @Test
    fun pmdMeasurementTypeLookupPreservesRawAndMaskedPolicies() {
        assertEquals(PolarPmdMeasurementTypeName.ECG, PolarPmdMeasurementTypeName.fromRawValue(0))
        assertEquals(PolarPmdMeasurementTypeName.ACC, PolarPmdMeasurementTypeName.fromRawValue(2))
        assertEquals(PolarPmdMeasurementTypeName.GYRO, PolarPmdMeasurementTypeName.fromRawValue(5))
        assertEquals(PolarPmdMeasurementTypeName.MAG, PolarPmdMeasurementTypeName.fromRawValue(6))
        assertEquals(PolarPmdMeasurementTypeName.SKIN_TEMP, PolarPmdMeasurementTypeName.fromRawValue(7))
        assertEquals(PolarPmdMeasurementTypeName.SDK_MODE, PolarPmdMeasurementTypeName.fromRawValue(9))
        assertEquals(PolarPmdMeasurementTypeName.OFFLINE_RECORDING, PolarPmdMeasurementTypeName.fromRawValue(13))
        assertEquals(PolarPmdMeasurementTypeName.OFFLINE_HR, PolarPmdMeasurementTypeName.fromRawValue(14))
        assertNull(PolarPmdMeasurementTypeName.fromRawValue(0xC2))
        assertEquals(PolarPmdMeasurementTypeName.ACC, PolarPmdMeasurementTypeName.fromMaskedId(0xC2))
        assertNull(PolarPmdMeasurementTypeName.fromMaskedId(4))
        assertNull(PolarPmdMeasurementTypeName.fromMaskedId(0xFF))
    }

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
                    assertEquals("invalidPMDData", parsed.error?.vectorName, caseId)
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
        val parsed = PolarPmdControlPoint.parseActiveMeasurement(responseByte)
        return ActiveMeasurement(parsed.activeBits, parsed.measurementBits, parsed.measurementTypeName, parsed.androidStateName, parsed.iosStateName)
    }

    private fun parseControlPointResponse(hex: String): ControlPointResponse {
        val parsed = PolarPmdControlPoint.parseControlPointResponse(hexToBytes(hex))
        val response = parsed.response ?: return ControlPointResponse(error = parsed.error)
        return ControlPointResponse(
            responseCode = response.responseCode,
            opCodeValue = response.opCodeValue,
            opCode = response.opCodeName,
            measurementType = response.measurementType,
            measurementTypeName = response.measurementTypeName,
            statusValue = response.statusValue,
            status = response.statusName,
            more = response.more,
            parametersHex = response.parametersHex
        )
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
        val error: PolarPmdParseError? = null
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
