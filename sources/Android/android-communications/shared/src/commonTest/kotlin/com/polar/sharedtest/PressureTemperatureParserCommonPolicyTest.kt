package com.polar.sharedtest

import com.polar.shared.pmd.sensors.PolarPmdDataFrame
import com.polar.shared.pmd.sensors.PolarSensorDataParser
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PressureTemperatureParserCommonPolicyTest {
    @Test
    fun pressureAndTemperatureGoldenVectorsDefineExecutableCommonScalarPolicy() {
        PRESSURE_TEMPERATURE_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")
            val frameTypeHex = input.stringValue("dataFrameHex").substring(18, 20)
            val parsed = parseScalarSensor(
                input.stringValue("dataFrameHex"),
                input.longValue("previousTimeStamp"),
                input.floatValue("factor"),
                input.intValue("sampleRate")
            )

            expected.optionalStringValue("migrationOwnership")?.let { ownership ->
                assertEquals(PROTOCOL_ONLY_MIGRATION_OWNERSHIP, ownership, caseId)
                assertEquals(expected.stringValue("measurementType"), parsed.measurementType, caseId)
                assertEquals(expected.intValue("frameType"), parsed.frameType, caseId)
                assertEquals(expected.booleanValue("compressed"), parsed.compressed, caseId)
                assertEquals(expected.longValue("timeStamp"), parsed.timeStamp, caseId)
                expected.optionalStringValue("parseError")?.let { parseError ->
                    assertEquals(parseError, parsed.error, caseId)
                }
                return@forEach
            }

            assertEquals(expected.stringValue("measurementType"), parsed.measurementType, caseId)
            assertEquals(expected.intValue("frameType"), parsed.frameType, caseId)
            assertEquals(expected.booleanValue("compressed"), parsed.compressed, "$caseId frameTypeHex=$frameTypeHex")
            assertEquals(expected.longValue("timeStamp"), parsed.timeStamp, caseId)
            expected.optionalStringValue("parseError")?.let { parseError ->
                assertEquals(parseError, parsed.error, caseId)
                return@forEach
            }

            val valueField = parsed.measurementType!!.valueField()
            val expectedSamples = expected.objectArray("samples")
            assertEquals(expectedSamples.size, parsed.samples.size, "$caseId sample count")
            expectedSamples.forEachIndexed { index, expectedSample ->
                val actual = parsed.samples[index]
                assertEquals(expectedSample.longValue("timeStamp"), actual.timeStamp, "$caseId sample $index timestamp")
                assertFloatEquals(expectedSample.floatValue(valueField), actual.value, "$caseId sample $index $valueField")
            }
        }
    }

    @Test
    fun pressureAndTemperatureCompressedVectorsUseProductionSharedType0Parser() {
        COMPRESSED_COMMON_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")
            val platforms = vector.objectValue("platforms")
            val frameTypeHex = input.stringValue("dataFrameHex").substring(18, 20)
            val parsed = parseScalarSensor(
                input.stringValue("dataFrameHex"),
                input.longValue("previousTimeStamp"),
                input.floatValue("factor"),
                input.intValue("sampleRate")
            )

            assertEquals(expected.stringValue("measurementType"), parsed.measurementType, caseId)
            assertEquals(expected.intValue("frameType"), parsed.frameType, caseId)
            assertEquals(true, parsed.compressed, "$caseId frameTypeHex=$frameTypeHex")
            assertEquals(null, parsed.error, caseId)
            assertEquals(true, platforms.booleanValue("common"), "$caseId common ownership")
            assertEquals(caseId.compressedScalarDecisionNote(), vector.stringValue("notes"), caseId)
            assertEquals(expected.objectArray("samples").size, parsed.samples.size, "$caseId sample count")
        }
    }

    @Test
    fun pressureTemperatureReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("protocol/sensors/pressure-temperature-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")

        assertEquals("pressure-temperature-readiness", manifest.stringValue("id"))
        assertEquals("pressureTemperatureReadiness", input.stringValue("kind"))
        assertEquals(PRESSURE_TEMPERATURE_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_PRESSURE_TEMPERATURE_FAMILIES, requiredFamilies, "Pressure/temperature readiness manifest must name every pre-migration behavior family")
        assertEquals(REQUIRED_PRESSURE_TEMPERATURE_FAMILIES, coveredFamilies, "Pressure/temperature readiness manifest must keep expected coverage aligned with required families")
        assertEquals("coveredByPreMigrationCharacterization", expected.stringValue("migrationReadiness"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PressureDataTest", "com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.TemperatureDataTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("TemperatureDataTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.PressureTemperatureParserCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun parseScalarSensor(hex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): ScalarSensorParseResult {
        val bytes = hexToBytes(hex)
        if (bytes.size < HEADER_SIZE) return ScalarSensorParseResult(error = "malformedFrame")
        val measurementType = (bytes[0].toInt() and 0xFF).measurementType()
        val frameTypeField = hex.substring(18, 20).toInt(16)
        val compressed = (frameTypeField and 0x80) != 0
        val frameType = frameTypeField and 0x7F
        val frame = PolarPmdDataFrame.fromByteArray(
            data = bytes,
            previousTimeStamp = previousTimeStamp,
            factor = factor,
            sampleRate = sampleRate
        )
        val parsed = runCatching {
            when (measurementType) {
                "PRESSURE" -> PolarSensorDataParser.parsePressure(frame).map { sample -> ScalarSample(sample.timeStamp.toLong(), sample.pressure) }
                "TEMPERATURE" -> PolarSensorDataParser.parseTemperature(frame).map { sample -> ScalarSample(sample.timeStamp.toLong(), sample.temperature) }
                else -> emptyList()
            }
        }.getOrElse { error ->
            return ScalarSensorParseResult(
                measurementType = measurementType,
                frameType = frameType,
                compressed = compressed,
                timeStamp = frame.timeStamp.toLong(),
                error = error.message ?: UNSUPPORTED_FRAME
            )
        }
        return ScalarSensorParseResult(
            measurementType = measurementType,
            frameType = frameType,
            compressed = compressed,
            timeStamp = frame.timeStamp.toLong(),
            samples = parsed
        )
    }

    private fun Int.measurementType(): String {
        return when (this) {
            11 -> "PRESSURE"
            12 -> "TEMPERATURE"
            else -> "UNKNOWN"
        }
    }

    private fun String.valueField(): String {
        return when (this) {
            "PRESSURE" -> "pressure"
            "TEMPERATURE" -> "temperature"
            else -> error("Unexpected scalar measurement type $this")
        }
    }

    private fun assertFloatEquals(expected: Float, actual: Float, message: String) {
        val tolerance = maxOf(0.0001f, abs(expected) * 0.000001f)
        assertTrue(abs(expected - actual) <= tolerance, "$message expected $expected but was $actual")
    }

    private fun String.longValue(field: String): Long {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toLong() ?: error("Missing long field $field in $this")
    }

    private fun String.floatValue(field: String): Float {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:e[+-]?\\d+)?)").find(this)?.groupValues?.get(1)?.toFloat() ?: error("Missing float field $field in $this")
    }

    private fun String.booleanValue(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" } ?: error("Missing boolean field $field in $this")
    }

    private fun String.stringArrayValue(field: String): List<String> {
        val arrayBody = Regex("\"$field\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
            .find(this)
            ?.groupValues
            ?.get(1)
            ?: error("Missing array field $field in $this")
        return Regex("\"([^\"]+)\"")
            .findAll(arrayBody)
            .map { it.groupValues[1] }
            .toList()
    }

    private data class ScalarSensorParseResult(
        val measurementType: String? = null,
        val frameType: Int? = null,
        val compressed: Boolean = false,
        val timeStamp: Long? = null,
        val samples: List<ScalarSample> = emptyList(),
        val error: String? = null
    )

    private data class ScalarSample(
        val timeStamp: Long,
        val value: Float
    )

    private companion object {
        const val HEADER_SIZE = 10
        const val UNSUPPORTED_FRAME = "unsupportedFrame"
        val COMPRESSED_COMMON_VECTORS = listOf(
            "protocol/sensors/pressure-compressed-type0-android-factor-half.json",
            "protocol/sensors/temperature-compressed-type0-flat-deltas.json",
            "protocol/sensors/temperature-compressed-type0-flat-deltas-android-two-samples.json"
        )
        val PRESSURE_TEMPERATURE_VECTORS = listOf(
            "protocol/sensors/pressure-compressed-type0-android-factor-half.json",
            "protocol/sensors/pressure-raw-type0-negative-single-sample.json",
            "protocol/sensors/pressure-raw-type0-single-sample.json",
            "protocol/sensors/pressure-raw-type0-truncated-sample-android-error.json",
            "protocol/sensors/pressure-raw-type1-unsupported.json",
            "protocol/sensors/temperature-compressed-type0-flat-deltas-android-two-samples.json",
            "protocol/sensors/temperature-compressed-type0-flat-deltas.json",
            "protocol/sensors/temperature-raw-type0-ieee754-boundaries.json",
            "protocol/sensors/temperature-raw-type0-negative-single-sample.json",
            "protocol/sensors/temperature-raw-type0-single-sample.json",
            "protocol/sensors/temperature-raw-type0-truncated-sample-android-error.json",
            "protocol/sensors/temperature-raw-type1-unsupported.json"
        )
        val REQUIRED_PRESSURE_TEMPERATURE_FAMILIES = listOf(
            "pressure-raw-type0-ieee754-parsing",
            "temperature-raw-type0-ieee754-parsing",
            "negative-and-boundary-float-values",
            "raw-type0-timestamp-interpolation",
            "unsupported-raw-frame-policy",
            "unsupported-compressed-frame-policy",
            "truncated-raw-sample-policy",
            "compressed-pressure-shared-type0-parser",
            "compressed-temperature-shared-type0-parser",
            "platform-pressure-temperature-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}

private fun String.compressedScalarDecisionNote(): String {
    return when (this) {
        "pressure-compressed-type0-android-factor-half" -> "Shared KMP decodes compressed pressure type-0 as an IEEE-754 reference sample and applies frame.factor; Android production and linked iOS production consume this shared parser while unlinked SwiftPM/watchOS keeps the legacy Swift fallback."
        "temperature-compressed-type0-flat-deltas" -> "Shared KMP decodes this compressed temperature type-0 zero-delta payload as the reference sample plus two zero-delta samples with the Android-compatible IEEE-754 bit-pattern interpretation; Android production and linked iOS production consume this shared parser."
        "temperature-compressed-type0-flat-deltas-android-two-samples" -> "Shared KMP preserves the Android-compatible compressed temperature type-0 interpretation for this zero-delta payload: one reference sample followed by two zero-delta samples."
        else -> error("Unexpected compressed scalar vector $this")
    }
}
