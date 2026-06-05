package com.polar.sharedtest

import com.polar.shared.sdk.PolarSdkModelMappers
import com.polar.shared.sdk.PolarSkinTemperatureMeasurementType
import com.polar.shared.sdk.PolarSkinTemperatureSampleModel
import com.polar.shared.sdk.PolarSkinTemperatureSensorLocation
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkinTemperatureDomainCommonPolicyTest {
    @Test
    fun skinTemperatureDomainEnumLookupsPreserveKnownValuesAndUnknownNullPolicy() {
        assertEquals(PolarSkinTemperatureMeasurementType.TM_SKIN_TEMPERATURE, PolarSkinTemperatureMeasurementType.fromValue(1))
        assertEquals(PolarSkinTemperatureMeasurementType.TM_CORE_TEMPERATURE, PolarSkinTemperatureMeasurementType.fromValue(2))
        assertNull(PolarSkinTemperatureMeasurementType.fromValue(0))
        assertNull(PolarSkinTemperatureMeasurementType.fromValue(99))

        assertEquals(PolarSkinTemperatureSensorLocation.SL_DISTAL, PolarSkinTemperatureSensorLocation.fromValue(1))
        assertEquals(PolarSkinTemperatureSensorLocation.SL_PROXIMAL, PolarSkinTemperatureSensorLocation.fromValue(2))
        assertNull(PolarSkinTemperatureSensorLocation.fromValue(0))
        assertNull(PolarSkinTemperatureSensorLocation.fromValue(99))
    }

    @Test
    fun skinTemperatureDomainGoldenVectorsDefineExecutableCommonSourceDeviceAndUnknownEnumPolicy() {
        SKIN_TEMPERATURE_DOMAIN_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val proto = vector.objectValue("input").objectValue("proto")
            val model = parseSkinTemperature(proto)
            val expected = vector.objectValue("expected")

            assertEquals(expected.optionalStringValue("measurementType"), model.measurementType?.name, "$caseId measurementType")
            assertEquals(expected.optionalStringValue("sensorLocation"), model.sensorLocation?.name, "$caseId sensorLocation")
            val expectedSamples = expected.objectArray("samples")
            assertEquals(expectedSamples.size, model.samples.size, "$caseId sample count")
            expectedSamples.forEachIndexed { index, expectedSample ->
                val actual = model.samples[index]
                assertEquals(expectedSample.longValue("recordingTimeDeltaMs"), actual.recordingTimeDeltaMs, "$caseId sample $index delta")
                assertDoubleEquals(expectedSample.doubleValue("temperature"), actual.temperature.toDouble(), "$caseId sample $index temperature")
            }

            val platformExpectations = vector.objectValue("platformExpectations")
            val androidExpectation = platformExpectations.objectValue("android")
            assertEquals(androidExpectation.stringValue("deviceId"), model.sourceDeviceId, "$caseId Android deviceId/sourceDeviceId characterization")
            platformExpectations.optionalObjectValue("commonDecision")?.let { commonDecision ->
                commonDecision.optionalStringValue("sourceDeviceIdPolicy")?.let { policy ->
                    assertEquals("include-nullable-source-device-id-in-shared-model-or-adapt-it-at-platform-facade", policy, caseId)
                }
                commonDecision.optionalStringValue("emptySamplesPolicy")?.let { policy ->
                    assertEquals("preserve-empty-list", policy, caseId)
                    assertTrue(model.samples.isEmpty(), "$caseId preserve empty samples")
                }
                commonDecision.optionalStringValue("unknownEnumPolicy")?.let { policy ->
                    assertEquals("choose-null-or-explicit-unknown-before-shared-model-migration", policy, caseId)
                    assertNull(model.measurementType, "$caseId unresolved unknown measurement policy")
                    assertNull(model.sensorLocation, "$caseId unresolved unknown sensor-location policy")
                    assertEquals("SL_UNKNOWN", platformExpectations.objectValue("ios").stringValue("sensorLocation"), "$caseId iOS unknown enum characterization")
                    assertEquals("TM_UNKNOWN", platformExpectations.objectValue("ios").stringValue("measurementType"), "$caseId iOS unknown enum characterization")
                }
            }
        }
    }

    @Test
    fun skinTemperatureDomainReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val vector = loadGoldenVectorText("sdk/skin-temperature/skin-temperature-domain-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumers = vector.objectValue("consumerTests")
        val platforms = vector.objectValue("platforms")
        assertEquals("skin-temperature-domain-readiness", vector.stringValue("id"))
        assertEquals("sdk.skin-temperature", vector.stringValue("area"))
        assertEquals("skin_temperature_domain_readiness", vector.stringValue("case"))
        assertEquals("skinTemperatureDomainReadiness", input.stringValue("kind"))
        assertEquals(SKIN_TEMPERATURE_DOMAIN_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(requiredSkinTemperatureDomainFamilies, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(requiredSkinTemperatureDomainFamilies, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(SKIN_TEMPERATURE_DOMAIN_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarSkinTemperatureUtilsTest"), consumers.stringArrayValue("android"))
        assertEquals(listOf("PolarSkinTemperatureUnitTest"), consumers.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.SkinTemperatureDomainCommonPolicyTest"), consumers.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private fun parseSkinTemperature(proto: String) =
        PolarSdkModelMappers.skinTemperature(
            sourceDeviceId = proto.stringValue("sourceDeviceId"),
            measurementType = proto.intValue("measurementType"),
            sensorLocation = proto.intValue("sensorLocation"),
            samples = proto.objectArray("samples").map { sample ->
                PolarSkinTemperatureSampleModel(
                    recordingTimeDeltaMs = sample.longValue("recordingTimeDeltaMs"),
                    temperature = sample.doubleValue("temperature").toFloat()
                )
            }
        )

    private fun assertDoubleEquals(expected: Double, actual: Double, message: String) {
        val tolerance = maxOf(0.000001, abs(expected) * 0.000001)
        assertTrue(abs(expected - actual) <= tolerance, "$message expected $expected but was $actual")
    }

    private fun String.optionalObjectValue(field: String): String? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val objectStart = indexOf('{', fieldIndex)
        if (objectStart < 0) return null
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.longValue(field: String): Long {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toLong() ?: error("Missing long field $field in $this")
    }

    private fun String.doubleValue(field: String): Double {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:e[+-]?\\d+)?)").find(this)?.groupValues?.get(1)?.toDouble() ?: error("Missing double field $field in $this")
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
        val SKIN_TEMPERATURE_DOMAIN_VECTORS = listOf(
            "sdk/skin-temperature/core-proximal-empty-samples.json",
            "sdk/skin-temperature/distal-skin-two-samples.json",
            "sdk/skin-temperature/unknown-enums-platform-policy.json"
        )
        val requiredSkinTemperatureDomainFamilies = listOf(
            "source-device-id-ownership",
            "empty-sample-list-preservation",
            "sample-delta-preservation",
            "sample-temperature-preservation",
            "measurement-type-mapping",
            "sensor-location-mapping",
            "unknown-measurement-type-boundary",
            "unknown-sensor-location-boundary",
            "platform-skin-temperature-vector-reference-gate",
            "compile-verification-gate"
        )
        const val SKIN_TEMPERATURE_DOMAIN_READINESS_COMMON_DECISION = "Skin-temperature domain migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS skin-temperature tests continue to reference the same vectors, sourceDeviceId ownership remains explicit, empty sample lists and sample values are preserved, measurement and sensor-location mappings are covered, unknown enum behavior is handled at a typed boundary before public model exposure, and the shared tests are compile-verified."
    }
}
