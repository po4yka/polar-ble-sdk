package com.polar.sharedtest

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkinTemperatureParserCommonPolicyTest {
    @Test
    fun skinTemperatureGoldenVectorsDefineExecutableCommonRawType0Policy() {
        SKIN_TEMPERATURE_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")
            val parsed = parseSkinTemperature(input.stringValue("dataFrameHex"), input.intValue("sampleRate"))

            expected.optionalStringValue("migrationOwnership")?.let { ownership ->
                assertEquals(PROTOCOL_ONLY_MIGRATION_OWNERSHIP, ownership, caseId)
                assertEquals(expected.stringValue("measurementType"), parsed.measurementType, caseId)
                assertEquals(expected.intValue("frameType"), parsed.frameType, caseId)
                assertEquals(expected.booleanValue("compressed"), parsed.compressed, caseId)
                assertEquals(expected.longValue("timeStamp"), parsed.timeStamp, caseId)
                if (relativePath.contains("ios-empty")) {
                    assertTrue(expected.objectArray("samples").isEmpty(), caseId)
                    assertEquals("malformedFrame", parsed.error, "$caseId common policy rejects instead of returning empty")
                } else {
                    assertEquals(expected.stringValue("parseError"), parsed.error, caseId)
                }
                return@forEach
            }

            assertEquals(expected.stringValue("measurementType"), parsed.measurementType, caseId)
            assertEquals(expected.intValue("frameType"), parsed.frameType, caseId)
            assertEquals(expected.booleanValue("compressed"), parsed.compressed, caseId)
            assertEquals(expected.longValue("timeStamp"), parsed.timeStamp, caseId)
            expected.optionalStringValue("parseError")?.let { parseError ->
                assertEquals(parseError, parsed.error, caseId)
                return@forEach
            }

            val expectedSamples = expected.objectArray("samples")
            assertEquals(expectedSamples.size, parsed.samples.size, "$caseId sample count")
            expectedSamples.forEachIndexed { index, expectedSample ->
                val actual = parsed.samples[index]
                assertEquals(expectedSample.longValue("timeStamp"), actual.timeStamp, "$caseId sample $index timestamp")
                assertFloatEquals(expectedSample.floatValue("skinTemperature"), actual.skinTemperature, "$caseId sample $index skinTemperature")
            }
            vector.optionalObjectValue("platformExpectations")?.objectValue("ios")?.objectArray("samples")?.firstOrNull()?.let { iosSample ->
                assertEquals(input.intValue("sampleRate") == 0, iosSample.booleanValue("isTimestampEstimated"), "$caseId iOS estimation flag policy")
            }
        }
    }

    @Test
    fun skinTemperatureReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("protocol/sensors/skin-temperature-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")

        assertEquals("skin-temperature-readiness", manifest.stringValue("id"))
        assertEquals("skinTemperatureReadiness", input.stringValue("kind"))
        assertEquals(SKIN_TEMPERATURE_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_SKIN_TEMPERATURE_FAMILIES, requiredFamilies, "Skin-temperature readiness manifest must name every pre-migration behavior family")
        assertEquals(REQUIRED_SKIN_TEMPERATURE_FAMILIES, coveredFamilies, "Skin-temperature readiness manifest must keep expected coverage aligned with required families")
        assertEquals("coveredByPreMigrationCharacterization", expected.stringValue("migrationReadiness"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.SkinTemperatureDataTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("SkinTemperatureDataTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.SkinTemperatureParserCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun parseSkinTemperature(hex: String, sampleRate: Int): SkinTemperatureParseResult {
        val bytes = hexToBytes(hex)
        if (bytes.size < HEADER_SIZE) return SkinTemperatureParseResult(error = "malformedFrame")
        val rawFrameType = bytes[9].toInt() and 0xFF
        val compressed = (rawFrameType and 0x80) != 0
        val frameType = rawFrameType and 0x7F
        val frameTimeStamp = bytes.readLittleEndianLong(offset = 1, size = 8)
        if (compressed || frameType != 0) {
            return SkinTemperatureParseResult(frameType = frameType, compressed = compressed, timeStamp = frameTimeStamp, error = "unsupportedFrame")
        }
        val payload = bytes.copyOfRange(HEADER_SIZE, bytes.size)
        if (payload.size % RAW_FLOAT_SAMPLE_SIZE != 0) {
            return SkinTemperatureParseResult(frameType = frameType, compressed = compressed, timeStamp = frameTimeStamp, error = "malformedFrame")
        }
        val sampleCount = payload.size / RAW_FLOAT_SAMPLE_SIZE
        val interval = if (sampleRate > 0) 1_000_000_000L / sampleRate else 0L
        val samples = (0 until sampleCount).map { index ->
            val timeStamp = if (sampleCount == 1 || interval == 0L) {
                frameTimeStamp
            } else {
                frameTimeStamp - interval * (sampleCount - index - 1)
            }
            SkinTemperatureSample(
                timeStamp = timeStamp,
                skinTemperature = payload.readFloatLe(offset = index * RAW_FLOAT_SAMPLE_SIZE)
            )
        }
        return SkinTemperatureParseResult(frameType = frameType, compressed = compressed, timeStamp = frameTimeStamp, samples = samples)
    }

    private fun ByteArray.readFloatLe(offset: Int): Float {
        val bits = (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8) or ((this[offset + 2].toInt() and 0xFF) shl 16) or ((this[offset + 3].toInt() and 0xFF) shl 24)
        return Float.fromBits(bits)
    }

    private fun ByteArray.readLittleEndianLong(offset: Int, size: Int): Long {
        var value = 0L
        for (index in 0 until size) {
            value = value or ((this[offset + index].toLong() and 0xFFL) shl (index * 8))
        }
        return value
    }

    private fun assertFloatEquals(expected: Float, actual: Float, message: String) {
        val tolerance = maxOf(0.0001f, abs(expected) * 0.000001f)
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

    private data class SkinTemperatureParseResult(
        val measurementType: String = "SKIN_TEMPERATURE",
        val frameType: Int? = null,
        val compressed: Boolean = false,
        val timeStamp: Long? = null,
        val samples: List<SkinTemperatureSample> = emptyList(),
        val error: String? = null
    )

    private data class SkinTemperatureSample(
        val timeStamp: Long,
        val skinTemperature: Float
    )

    private companion object {
        const val HEADER_SIZE = 10
        const val RAW_FLOAT_SAMPLE_SIZE = 4
        val SKIN_TEMPERATURE_VECTORS = listOf(
            "protocol/sensors/skin-temperature-raw-type0-estimated-sample-rate.json",
            "protocol/sensors/skin-temperature-raw-type0-single-sample.json",
            "protocol/sensors/skin-temperature-raw-type0-truncated-sample-android-error.json",
            "protocol/sensors/skin-temperature-raw-type0-truncated-sample-ios-empty.json",
            "protocol/sensors/skin-temperature-raw-type1-unsupported.json"
        )
        val REQUIRED_SKIN_TEMPERATURE_FAMILIES = listOf(
            "raw-type0-ieee754-skin-temperature-parsing",
            "sample-rate-timestamp-estimation-policy",
            "unsupported-raw-frame-policy",
            "truncated-raw-sample-policy",
            "ios-empty-malformed-payload-deferral",
            "platform-skin-temperature-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}
