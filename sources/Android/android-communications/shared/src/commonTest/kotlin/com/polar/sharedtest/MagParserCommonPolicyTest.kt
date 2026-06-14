package com.polar.sharedtest

import kotlin.math.abs
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MagParserCommonPolicyTest {
    @Test
    fun magGoldenVectorsDefineExecutableCommonCompressedCalibrationAndMalformedPolicy() {
        MAG_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")
            val parsed = parseMag(
                hex = input.stringValue("dataFrameHex"),
                previousTimeStamp = input.longValue("previousTimeStamp"),
                factor = input.doubleValue("factor"),
                sampleRate = input.intValue("sampleRate")
            )

            expected.optionalStringValue("sharedOwnership")?.let { ownership ->
                assertEquals(PROTOCOL_ONLY_PLATFORM_OWNERSHIP, ownership, caseId)
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
                assertEquals(expectedSample.stringValue("calibrationStatus"), actual.calibrationStatus, "$caseId sample $index calibration")
            }
        }
    }

    @Test
    fun magReadinessManifestNamesEverySharedContractBehaviorFamily() {
        val manifest = loadGoldenVectorText("protocol/sensors/mag-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")

        assertEquals("mag-readiness", manifest.stringValue("id"))
        assertEquals("magReadiness", input.stringValue("kind"))
        assertEquals(MAG_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_MAG_FAMILIES, requiredFamilies, "MAG readiness manifest must name every shared-contract behavior family")
        assertEquals(REQUIRED_MAG_FAMILIES, coveredFamilies, "MAG readiness manifest must keep expected coverage aligned with required families")
        assertEquals("coveredBySharedContractCharacterization", expected.stringValue("sharedOwnershipStatus"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.MagDataTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("MagDataTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.MagParserCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun parseMag(hex: String, previousTimeStamp: Long, factor: Double, sampleRate: Int): MagParseResult {
        val bytes = hexToBytes(hex)
        if (bytes.size < HEADER_SIZE) return MagParseResult(error = "malformedFrame")
        val rawFrameType = bytes[9].toInt() and BYTE_MASK
        val compressed = (rawFrameType and COMPRESSED_MASK) != 0
        val frameType = rawFrameType and FRAME_TYPE_MASK
        val frameTimeStamp = bytes.readLittleEndianLong(offset = 1, size = 8)
        if (!compressed || frameType !in 0..1) {
            return MagParseResult(frameType = frameType, compressed = compressed, timeStamp = frameTimeStamp, error = "unsupportedFrame")
        }
        val rawSamples = try {
            parseDeltaFramesAll(
                payload = bytes.copyOfRange(HEADER_SIZE, bytes.size),
                channels = if (frameType == 0) TYPE_0_CHANNELS else TYPE_1_CHANNELS,
                resolutionBits = 16
            )
        } catch (_: MalformedFrame) {
            return MagParseResult(frameType = frameType, compressed = true, timeStamp = frameTimeStamp, error = "malformedFrame")
        }
        val samples = rawSamples.map { sample ->
            if (frameType == 0) {
                MagSample(
                    timeStamp = 0,
                    x = (sample[0] * factor).toFloat(),
                    y = (sample[1] * factor).toFloat(),
                    z = (sample[2] * factor).toFloat(),
                    calibrationStatus = "NOT_AVAILABLE"
                )
            } else {
                MagSample(
                    timeStamp = 0,
                    x = ((sample[0] * factor) / MILLIGAUSS_PER_GAUSS).toFloat(),
                    y = ((sample[1] * factor) / MILLIGAUSS_PER_GAUSS).toFloat(),
                    z = ((sample[2] * factor) / MILLIGAUSS_PER_GAUSS).toFloat(),
                    calibrationStatus = calibrationStatus(sample[3])
                )
            }
        }.withTimeStamps(previousTimeStamp, frameTimeStamp, sampleRate)
        return MagParseResult(frameType = frameType, compressed = true, timeStamp = frameTimeStamp, samples = samples)
    }

    private fun calibrationStatus(id: Int): String {
        return when (id) {
            0 -> "UNKNOWN"
            1 -> "POOR"
            2 -> "OK"
            3 -> "GOOD"
            else -> "NOT_AVAILABLE"
        }
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

    private fun List<MagSample>.withTimeStamps(previousTimeStamp: Long, frameTimeStamp: Long, sampleRate: Int): List<MagSample> {
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

    private data class MagParseResult(
        val measurementType: String = "MAG",
        val frameType: Int? = null,
        val compressed: Boolean = false,
        val timeStamp: Long? = null,
        val samples: List<MagSample> = emptyList(),
        val error: String? = null
    )

    private data class MagSample(
        val timeStamp: Long,
        val x: Float,
        val y: Float,
        val z: Float,
        val calibrationStatus: String
    )

    private companion object {
        const val HEADER_SIZE = 10
        const val TYPE_0_CHANNELS = 3
        const val TYPE_1_CHANNELS = 4
        const val BYTE_MASK = 0xFF
        const val COMPRESSED_MASK = 0x80
        const val FRAME_TYPE_MASK = 0x7F
        const val BITS_PER_BYTE = 8
        const val MILLIGAUSS_PER_GAUSS = 1000.0
        const val NANOSECONDS_PER_SECOND = 1_000_000_000L
        val MAG_VECTORS = listOf(
            "protocol/sensors/mag-compressed-type0-factor-half.json",
            "protocol/sensors/mag-compressed-type0-truncated-delta-header-android-error.json",
            "protocol/sensors/mag-compressed-type0-truncated-delta-header-ios-reference-only.json",
            "protocol/sensors/mag-compressed-type0-truncated-delta-payload-android-error.json",
            "protocol/sensors/mag-compressed-type0-truncated-delta-payload-ios-reference-only.json",
            "protocol/sensors/mag-compressed-type0-two-samples.json",
            "protocol/sensors/mag-compressed-type1-calibration-status.json",
            "protocol/sensors/mag-compressed-type2-unsupported.json",
            "protocol/sensors/mag-raw-type0-unsupported.json"
        )
        val REQUIRED_MAG_FAMILIES = listOf(
            "compressed-type0-reference-delta-decoding",
            "compressed-type0-factor-scaling",
            "compressed-type0-timestamp-interpolation",
            "compressed-type1-calibration-status-mapping",
            "compressed-type1-milligauss-to-gauss-conversion",
            "unsupported-raw-frame-policy",
            "unsupported-compressed-frame-policy",
            "truncated-compressed-delta-header-policy",
            "truncated-compressed-delta-payload-policy",
            "platform-mag-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}
