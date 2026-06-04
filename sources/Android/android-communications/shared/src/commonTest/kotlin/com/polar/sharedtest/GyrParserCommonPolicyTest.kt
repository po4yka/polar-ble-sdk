package com.polar.sharedtest

import kotlin.math.abs
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GyrParserCommonPolicyTest {
    @Test
    fun gyrGoldenVectorsDefineExecutableCommonCompressedType0AndMalformedPolicy() {
        GYR_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")
            val parsed = parseGyr(
                hex = input.stringValue("dataFrameHex"),
                previousTimeStamp = input.longValue("previousTimeStamp"),
                factor = input.doubleValue("factor"),
                sampleRate = input.intValue("sampleRate")
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
                assertFloatEquals(expectedSample.floatValue("x"), actual.x, "$caseId sample $index x")
                assertFloatEquals(expectedSample.floatValue("y"), actual.y, "$caseId sample $index y")
                assertFloatEquals(expectedSample.floatValue("z"), actual.z, "$caseId sample $index z")
            }
        }
    }

    @Test
    fun gyrReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("protocol/sensors/gyr-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")

        assertEquals("gyr-readiness", manifest.stringValue("id"))
        assertEquals("gyrReadiness", input.stringValue("kind"))
        assertEquals(GYR_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_GYR_FAMILIES, requiredFamilies, "GYR readiness manifest must name every pre-migration behavior family")
        assertEquals(REQUIRED_GYR_FAMILIES, coveredFamilies, "GYR readiness manifest must keep expected coverage aligned with required families")
        assertEquals("coveredByPreMigrationCharacterization", expected.stringValue("migrationReadiness"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.GyrDataTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("GyrDataTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.GyrParserCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun parseGyr(hex: String, previousTimeStamp: Long, factor: Double, sampleRate: Int): GyrParseResult {
        val bytes = hexToBytes(hex)
        if (bytes.size < HEADER_SIZE) return GyrParseResult(error = "malformedFrame")
        val rawFrameType = bytes[9].toInt() and BYTE_MASK
        val compressed = (rawFrameType and COMPRESSED_MASK) != 0
        val frameType = rawFrameType and FRAME_TYPE_MASK
        val frameTimeStamp = bytes.readLittleEndianLong(offset = 1, size = 8)
        if (!compressed || frameType != 0) {
            return GyrParseResult(frameType = frameType, compressed = compressed, timeStamp = frameTimeStamp, error = "unsupportedFrame")
        }
        val rawSamples = try {
            parseDeltaFramesAll(bytes.copyOfRange(HEADER_SIZE, bytes.size), channels = AXIS_COUNT, resolutionBits = 16)
        } catch (_: MalformedFrame) {
            return GyrParseResult(frameType = frameType, compressed = true, timeStamp = frameTimeStamp, error = "malformedFrame")
        }
        val samples = rawSamples.map { sample ->
            GyrSample(
                timeStamp = 0,
                x = (sample[0] * factor).toFloat(),
                y = (sample[1] * factor).toFloat(),
                z = (sample[2] * factor).toFloat()
            )
        }.withTimeStamps(previousTimeStamp, frameTimeStamp, sampleRate)
        return GyrParseResult(frameType = frameType, compressed = true, timeStamp = frameTimeStamp, samples = samples)
    }

    private fun parseDeltaFramesAll(payload: ByteArray, channels: Int, resolutionBits: Int): List<List<Int>> {
        val referenceBytes = channels * ((resolutionBits + BITS_PER_BYTE - 1) / BITS_PER_BYTE)
        if (payload.size < referenceBytes) throw MalformedFrame()
        var offset = 0
        val samples = mutableListOf(
            (0 until channels).map {
                val value = payload.readSignedInt(offset, referenceBytes / channels)
                offset += referenceBytes / channels
                value
            }
        )
        while (offset < payload.size) {
            if (offset + 2 > payload.size) throw MalformedFrame()
            val deltaSize = payload[offset++].toInt() and BYTE_MASK
            val sampleCount = payload[offset++].toInt() and BYTE_MASK
            val bitLength = sampleCount * deltaSize * channels
            val byteLength = (bitLength + BITS_PER_BYTE - 1) / BITS_PER_BYTE
            if (offset + byteLength > payload.size) throw MalformedFrame()
            parseDeltaFrame(payload.copyOfRange(offset, offset + byteLength), channels, deltaSize, bitLength).forEach { delta ->
                val previous = samples.last()
                samples += (0 until channels).map { channel -> previous[channel] + delta[channel] }
            }
            offset += byteLength
        }
        return samples
    }

    private fun parseDeltaFrame(bytes: ByteArray, channels: Int, bitWidth: Int, totalBitLength: Int): List<List<Int>> {
        var offset = 0
        val samples = mutableListOf<List<Int>>()
        val signMask = Int.MAX_VALUE shl (bitWidth - 1)
        while (offset < totalBitLength) {
            samples += (0 until channels).map {
                var value = 0
                for (bitIndex in 0 until bitWidth) {
                    val absoluteBit = offset + bitIndex
                    val byte = bytes[absoluteBit / BITS_PER_BYTE].toInt() and BYTE_MASK
                    if ((byte and (1 shl (absoluteBit % BITS_PER_BYTE))) != 0) {
                        value = value or (1 shl bitIndex)
                    }
                }
                offset += bitWidth
                if ((value and signMask) != 0) value or signMask else value
            }
        }
        return samples
    }

    private fun List<GyrSample>.withTimeStamps(previousTimeStamp: Long, frameTimeStamp: Long, sampleRate: Int): List<GyrSample> {
        if (isEmpty()) return this
        val delta = if (previousTimeStamp == 0L) {
            if (sampleRate <= 0) 0.0 else NANOSECONDS_PER_SECOND.toDouble() / sampleRate.toDouble()
        } else {
            (frameTimeStamp - previousTimeStamp).toDouble() / size.toDouble()
        }
        val first = if (previousTimeStamp == 0L) {
            frameTimeStamp.toDouble() - delta * (size - 1)
        } else {
            previousTimeStamp.toDouble() + delta
        }
        return mapIndexed { index, sample ->
            val timeStamp = if (index == lastIndex) frameTimeStamp else round(first + delta * index).toLong()
            sample.copy(timeStamp = timeStamp)
        }
    }

    private fun ByteArray.readSignedInt(offset: Int, size: Int): Int {
        var value = 0
        for (index in 0 until size) {
            value = value or ((this[offset + index].toInt() and BYTE_MASK) shl (index * BITS_PER_BYTE))
        }
        val signBit = 1 shl (size * BITS_PER_BYTE - 1)
        val mask = -1 shl (size * BITS_PER_BYTE - 1)
        return if ((value and signBit) != 0) value or mask else value
    }

    private fun ByteArray.readLittleEndianLong(offset: Int, size: Int): Long {
        var value = 0L
        for (index in 0 until size) {
            value = value or ((this[offset + index].toLong() and BYTE_MASK.toLong()) shl (index * BITS_PER_BYTE))
        }
        return value
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

    private class MalformedFrame : Exception()

    private data class GyrParseResult(
        val measurementType: String = "GYR",
        val frameType: Int? = null,
        val compressed: Boolean = false,
        val timeStamp: Long? = null,
        val samples: List<GyrSample> = emptyList(),
        val error: String? = null
    )

    private data class GyrSample(
        val timeStamp: Long,
        val x: Float,
        val y: Float,
        val z: Float
    )

    private companion object {
        const val HEADER_SIZE = 10
        const val AXIS_COUNT = 3
        const val BYTE_MASK = 0xFF
        const val COMPRESSED_MASK = 0x80
        const val FRAME_TYPE_MASK = 0x7F
        const val BITS_PER_BYTE = 8
        const val NANOSECONDS_PER_SECOND = 1_000_000_000L
        val GYR_VECTORS = listOf(
            "protocol/sensors/gyr-compressed-type0-factor-half.json",
            "protocol/sensors/gyr-compressed-type0-truncated-delta-header-android-error.json",
            "protocol/sensors/gyr-compressed-type0-truncated-delta-header-ios-reference-only.json",
            "protocol/sensors/gyr-compressed-type0-truncated-delta-payload-android-error.json",
            "protocol/sensors/gyr-compressed-type0-truncated-delta-payload-ios-reference-only.json",
            "protocol/sensors/gyr-compressed-type0-two-samples.json",
            "protocol/sensors/gyr-compressed-type1-android-only.json",
            "protocol/sensors/gyr-compressed-type2-unsupported.json",
            "protocol/sensors/gyr-raw-type0-unsupported.json"
        )
        val REQUIRED_GYR_FAMILIES = listOf(
            "compressed-type0-reference-delta-decoding",
            "compressed-type0-factor-scaling",
            "compressed-type0-timestamp-interpolation",
            "unsupported-raw-frame-policy",
            "unsupported-compressed-frame-policy",
            "android-compressed-type1-ownership",
            "truncated-compressed-delta-header-policy",
            "truncated-compressed-delta-payload-policy",
            "platform-gyr-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}
