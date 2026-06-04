package com.polar.sharedtest

import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals

class AccParserCommonPolicyTest {
    @Test
    fun accGoldenVectorsDefineExecutableCommonRawCompressedAndMalformedPolicy() {
        ACC_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")
            val parsed = parseAcc(
                hex = input.stringValue("dataFrameHex"),
                previousTimeStamp = input.longValue("previousTimeStamp"),
                factor = input.doubleValue("factor"),
                sampleRate = input.intValue("sampleRate")
            )

            expected.optionalStringValue("migrationOwnership")?.let { ownership ->
                assertEquals(PROTOCOL_ONLY_MIGRATION_OWNERSHIP, ownership, caseId)
                assertEquals(expected.stringValue("measurementType"), parsed.measurementType, caseId)
                assertEquals(expected.intValue("frameType"), parsed.frameType, caseId)
                assertEquals(expected.optionalBooleanValue("compressed") ?: false, parsed.compressed, caseId)
                assertEquals(expected.longValue("timeStamp"), parsed.timeStamp, caseId)
                expected.optionalStringValue("parseError")?.let { parseError ->
                    assertEquals(parseError, parsed.error, caseId)
                }
                return@forEach
            }

            assertEquals(expected.stringValue("measurementType"), parsed.measurementType, caseId)
            assertEquals(expected.intValue("frameType"), parsed.frameType, caseId)
            assertEquals(expected.optionalBooleanValue("compressed") ?: false, parsed.compressed, caseId)
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
                assertEquals(expectedSample.intValue("x"), actual.x, "$caseId sample $index x")
                assertEquals(expectedSample.intValue("y"), actual.y, "$caseId sample $index y")
                assertEquals(expectedSample.intValue("z"), actual.z, "$caseId sample $index z")
            }
        }
    }

    @Test
    fun accReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("protocol/sensors/acc-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")

        assertEquals("acc-readiness", manifest.stringValue("id"))
        assertEquals("accReadiness", input.stringValue("kind"))
        assertEquals(ACC_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_ACC_FAMILIES, requiredFamilies, "ACC readiness manifest must name every pre-migration behavior family")
        assertEquals(REQUIRED_ACC_FAMILIES, coveredFamilies, "ACC readiness manifest must keep expected coverage aligned with required families")
        assertEquals("coveredByPreMigrationCharacterization", expected.stringValue("migrationReadiness"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.AccDataTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("AccDataTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.AccParserCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun parseAcc(hex: String, previousTimeStamp: Long, factor: Double, sampleRate: Int): AccParseResult {
        val bytes = hexToBytes(hex)
        if (bytes.size < HEADER_SIZE) return AccParseResult(error = "malformedFrame")
        val rawFrameType = bytes[9].toInt() and BYTE_MASK
        val compressed = (rawFrameType and COMPRESSED_MASK) != 0
        val frameType = rawFrameType and FRAME_TYPE_MASK
        val frameTimeStamp = bytes.readLittleEndianLong(offset = 1, size = 8)
        val payload = bytes.copyOfRange(HEADER_SIZE, bytes.size)
        val rawSamples = try {
            if (compressed) {
                when (frameType) {
                    0 -> parseCompressed(payload, factor = factor * MILLIG_FACTOR, resolutionBits = 16)
                    1 -> parseCompressed(payload, factor = factor, resolutionBits = 16)
                    else -> return AccParseResult(frameType = frameType, compressed = true, timeStamp = frameTimeStamp, error = "unsupportedCompressedFrame")
                }
            } else {
                when (frameType) {
                    0 -> parseRaw(payload, step = 1)
                    1 -> parseRaw(payload, step = 2)
                    2 -> parseRaw(payload, step = 3)
                    else -> return AccParseResult(frameType = frameType, compressed = false, timeStamp = frameTimeStamp, error = "unsupportedFrame")
                }
            }
        } catch (_: MalformedFrame) {
            return AccParseResult(frameType = frameType, compressed = compressed, timeStamp = frameTimeStamp, error = "malformedFrame")
        }
        val samples = rawSamples.withTimeStamps(previousTimeStamp, frameTimeStamp, sampleRate)
        return AccParseResult(frameType = frameType, compressed = compressed, timeStamp = frameTimeStamp, samples = samples)
    }

    private fun parseRaw(payload: ByteArray, step: Int): List<AccSample> {
        val sampleSize = step * AXIS_COUNT
        if (payload.size % sampleSize != 0) throw MalformedFrame()
        return (0 until payload.size / sampleSize).map { index ->
            val offset = index * sampleSize
            AccSample(
                timeStamp = 0,
                x = payload.readSignedInt(offset, step),
                y = payload.readSignedInt(offset + step, step),
                z = payload.readSignedInt(offset + step * 2, step)
            )
        }
    }

    private fun parseCompressed(payload: ByteArray, factor: Double, resolutionBits: Int): List<AccSample> {
        val rawSamples = parseDeltaFramesAll(payload, channels = AXIS_COUNT, resolutionBits = resolutionBits)
        return rawSamples.map { sample ->
            AccSample(
                timeStamp = 0,
                x = (sample[0] * factor).toInt(),
                y = (sample[1] * factor).toInt(),
                z = (sample[2] * factor).toInt()
            )
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
            val deltas = parseDeltaFrame(payload.copyOfRange(offset, offset + byteLength), channels, deltaSize, bitLength)
            deltas.forEach { delta ->
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
            val channelSamples = (0 until channels).map {
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
            samples += channelSamples
        }
        return samples
    }

    private fun List<AccSample>.withTimeStamps(previousTimeStamp: Long, frameTimeStamp: Long, sampleRate: Int): List<AccSample> {
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

    private fun String.longValue(field: String): Long {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toLong() ?: error("Missing long field $field in $this")
    }

    private fun String.doubleValue(field: String): Double {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:e[+-]?\\d+)?)").find(this)?.groupValues?.get(1)?.toDouble() ?: error("Missing double field $field in $this")
    }

    private fun String.optionalBooleanValue(field: String): Boolean? {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" }
    }

    private fun String.booleanValue(field: String): Boolean {
        return optionalBooleanValue(field) ?: error("Missing boolean field $field in $this")
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

    private data class AccParseResult(
        val measurementType: String = "ACC",
        val frameType: Int? = null,
        val compressed: Boolean = false,
        val timeStamp: Long? = null,
        val samples: List<AccSample> = emptyList(),
        val error: String? = null
    )

    private data class AccSample(
        val timeStamp: Long,
        val x: Int,
        val y: Int,
        val z: Int
    )

    private companion object {
        const val HEADER_SIZE = 10
        const val AXIS_COUNT = 3
        const val BYTE_MASK = 0xFF
        const val COMPRESSED_MASK = 0x80
        const val FRAME_TYPE_MASK = 0x7F
        const val BITS_PER_BYTE = 8
        const val MILLIG_FACTOR = 1000.0
        const val NANOSECONDS_PER_SECOND = 1_000_000_000L
        val ACC_VECTORS = listOf(
            "protocol/sensors/acc-compressed-type0-factor-half.json",
            "protocol/sensors/acc-compressed-type0-truncated-delta-header-android-error.json",
            "protocol/sensors/acc-compressed-type0-truncated-delta-header-ios-reference-only.json",
            "protocol/sensors/acc-compressed-type0-truncated-delta-payload-android-error.json",
            "protocol/sensors/acc-compressed-type0-truncated-delta-payload-ios-reference-only.json",
            "protocol/sensors/acc-compressed-type1-two-samples.json",
            "protocol/sensors/acc-compressed-type2-unsupported.json",
            "protocol/sensors/acc-raw-type0-signed-boundaries.json",
            "protocol/sensors/acc-raw-type0-truncated-sample-android-error.json",
            "protocol/sensors/acc-raw-type1-signed-boundaries.json",
            "protocol/sensors/acc-raw-type1-truncated-sample-android-error.json",
            "protocol/sensors/acc-raw-type1-two-samples.json",
            "protocol/sensors/acc-raw-type2-android-only.json",
            "protocol/sensors/acc-raw-type2-truncated-sample-android-error.json"
        )
        val REQUIRED_ACC_FAMILIES = listOf(
            "raw-type0-signed-axis-boundaries",
            "raw-type1-signed-axis-parsing",
            "raw-type1-timestamp-interpolation",
            "compressed-type0-millig-factor-scaling",
            "compressed-type1-reference-delta-decoding",
            "unsupported-compressed-type-policy",
            "raw-type2-android-ownership",
            "truncated-raw-sample-policy",
            "truncated-compressed-delta-header-policy",
            "truncated-compressed-delta-payload-policy",
            "platform-acc-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}
