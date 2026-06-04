package com.polar.sharedtest

import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals

class PpgParserCommonPolicyTest {
    @Test
    fun ppgGoldenVectorsDefineExecutableCommonRawScalarCompressedType13AndMalformedPolicy() {
        PPG_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")
            val parsed = parsePpg(
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
                if (caseId == "ppg-raw-type4-integration-gain-platform-shape" || caseId == "ppg-raw-type9-integration-gain-platform-shape") {
                    assertIntegrationGainPlatformShape(vector, parsed, timestampField = "timeStamp", integrationField = "numIntTs", channel1Field = "channel1GainTs", channel2Field = "channel2GainTs")
                }
                if (caseId == "ppg-raw-type14-integration-gain-platform-shape") {
                    assertIntegrationGainPlatformShape(vector, parsed, timestampField = "timeStamp", integrationField = "numIntTs1", channel1Field = "channel1GainTs1", channel2Field = "channel2GainTs1")
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

            if (caseId == "ppg-compressed-type7-reference-status") {
                val androidSample = vector.objectValue("platformExpectations").objectValue("android").objectArray("samples").single()
                val iosSample = vector.objectValue("platformExpectations").objectValue("ios").objectArray("samples").single()
                val sample = parsed.samples.single()
                assertEquals(androidSample.longValue("timeStamp"), sample.timeStamp, caseId)
                assertEquals(androidSample.intArrayValue("ppgDataSamples"), sample.ppgDataSamples, caseId)
                assertEquals(androidSample.intArrayValue("statusBits"), sample.statusBits, caseId)
                assertEquals(iosSample.intArrayValue("ppgDataSamples"), sample.ppgDataSamples + listOf(statusWord(sample.statusBits)), "$caseId iOS status-in-sample shape")
                return@forEach
            }

            if (caseId == "ppg-compressed-type10-reference-status" || caseId == "ppg-compressed-type10-full-status") {
                val androidSample = vector.objectValue("platformExpectations").objectValue("android").objectArray("samples").single()
                val iosSample = vector.objectValue("platformExpectations").objectValue("ios").objectArray("samples").single()
                val sample = parsed.samples.single()
                assertEquals(androidSample.longValue("timeStamp"), sample.timeStamp, caseId)
                assertEquals(androidSample.intArrayValue("greenSamples"), sample.greenSamples, "$caseId Android green")
                assertEquals(androidSample.intArrayValue("redSamples"), sample.redSamples, "$caseId Android red")
                assertEquals(androidSample.intArrayValue("irSamples"), sample.irSamples, "$caseId Android IR")
                assertEquals(androidSample.intArrayValue("statusBits"), sample.statusBits, "$caseId Android status")
                assertEquals(iosSample.intArrayValue("greenSamples"), sample.redSamples, "$caseId current iOS green/red platform split")
                assertEquals(iosSample.intArrayValue("redSamples"), sample.greenSamples, "$caseId current iOS red/green platform split")
                assertEquals(iosSample.intArrayValue("irSamples"), sample.irSamples, "$caseId iOS IR")
                assertEquals(iosSample.intArrayValue("statusBits"), sample.statusBits, "$caseId iOS status")
                return@forEach
            }

            if (caseId == "ppg-compressed-type13-reference-status") {
                val androidSample = vector.objectValue("platformExpectations").objectValue("android").objectArray("samples").single()
                val sample = parsed.samples.single()
                assertEquals(androidSample.longValue("timeStamp"), sample.timeStamp, caseId)
                assertEquals(androidSample.intArrayValue("ppgChannel0"), sample.ppgChannel0, caseId)
                assertEquals(androidSample.intArrayValue("ppgChannel1"), sample.ppgChannel1, caseId)
                assertEquals(androidSample.intArrayValue("statusBits"), sample.statusBits, caseId)
                assertEquals(vector.objectValue("platformExpectations").objectValue("ios").objectArray("samples").single().intArrayValue("ppgDataSamples"), sample.ppgChannel0 + sample.ppgChannel1, "$caseId platform shape equivalence")
                return@forEach
            }

            val expectedSamples = expected.objectArray("samples")
            assertEquals(expectedSamples.size, parsed.samples.size, "$caseId sample count")
            expectedSamples.forEachIndexed { index, expectedSample ->
                val actual = parsed.samples[index]
                assertEquals(expectedSample.longValue("timeStamp"), actual.timeStamp, "$caseId sample $index timestamp")
                expectedSample.optionalIntArrayValue("ppg")?.let { assertEquals(it, actual.ppg, "$caseId sample $index ppg") }
                expectedSample.optionalIntArrayValue("ppgDataSamples")?.let { assertEquals(it, actual.ppgDataSamples, "$caseId sample $index ppgDataSamples") }
                expectedSample.optionalIntArrayValue("statusBits")?.let { assertEquals(it, actual.statusBits, "$caseId sample $index statusBits") }
                expectedSample.optionalIntValue("ambient")?.let { assertEquals(it, actual.ambient, "$caseId sample $index ambient") }
                expectedSample.optionalLongValue("operationMode")?.let { assertEquals(it, actual.operationMode, "$caseId sample $index operationMode") }
                expectedSample.optionalLongValue("sportId")?.let { assertEquals(it, actual.sportId, "$caseId sample $index sportId") }
            }
        }
    }

    @Test
    fun ppgFrameFamilyMigrationReadinessManifestNamesEveryPreMigrationParserPolicy() {
        val vector = loadGoldenVectorText(PPG_READINESS_MANIFEST)
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val requiredVectorPaths = input.stringArrayValue("requiredVectorPaths")
        val requiredFamilies = input.stringArrayValue("requiredFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredFamilies")
        assertEquals("ppgFrameFamilyMigrationReadiness", input.stringValue("kind"))
        assertEquals(PPG_READINESS_VECTOR_PATHS, requiredVectorPaths)
        assertEquals(REQUIRED_PPG_FRAME_FAMILIES, requiredFamilies)
        assertEquals(REQUIRED_PPG_FRAME_FAMILIES, coveredFamilies)
        assertEquals(PPG_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        val consumerTests = vector.objectValue("consumerTests")
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpgDataTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PpgDataTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.PpgParserCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun parsePpg(hex: String, previousTimeStamp: Long, factor: Double, sampleRate: Int): PpgParseResult {
        val bytes = hexToBytes(hex)
        if (bytes.size < HEADER_SIZE) return PpgParseResult(error = "malformedFrame")
        val rawFrameType = bytes[9].toInt() and BYTE_MASK
        val compressed = (rawFrameType and COMPRESSED_MASK) != 0
        val frameType = rawFrameType and FRAME_TYPE_MASK
        val frameTimeStamp = bytes.readLittleEndianLong(offset = 1, size = 8)
        val payload = bytes.copyOfRange(HEADER_SIZE, bytes.size)
        val samples = try {
            when {
                !compressed && frameType == 0 -> parseRawType0(payload).withTimeStamps(previousTimeStamp, frameTimeStamp, sampleRate)
                !compressed && frameType == 4 -> parseRawIntegrationGain(payload, integrationBytes = 12, gainBytes = 24, integrationField = IntegrationField.STANDARD).withTimeStamps(previousTimeStamp, frameTimeStamp, sampleRate)
                !compressed && frameType == 5 -> {
                    if (payload.size != 4) throw MalformedFrame()
                    listOf(PpgSample(timeStamp = frameTimeStamp, operationMode = payload.readUnsignedInt(0, 4)))
                }
                !compressed && frameType == 6 -> {
                    if (payload.size != 8) throw MalformedFrame()
                    listOf(PpgSample(timeStamp = frameTimeStamp, sportId = payload.readUnsignedLong(0, 8)))
                }
                !compressed && frameType == 9 -> parseRawIntegrationGain(payload, integrationBytes = 12, gainBytes = 24, integrationField = IntegrationField.STANDARD).withTimeStamps(previousTimeStamp, frameTimeStamp, sampleRate)
                !compressed && frameType == 14 -> parseRawIntegrationGain(payload, integrationBytes = 1, gainBytes = 2, integrationField = IntegrationField.TYPE_14).withTimeStamps(previousTimeStamp, frameTimeStamp, sampleRate)
                compressed && frameType == 7 -> parseCompressedType7(payload, factor).withTimeStamps(previousTimeStamp, frameTimeStamp, sampleRate)
                compressed && frameType == 8 -> parseCompressedType8(payload, factor).withTimeStamps(previousTimeStamp, frameTimeStamp, sampleRate)
                compressed && frameType == 10 -> parseCompressedType10(payload, factor).withTimeStamps(previousTimeStamp, frameTimeStamp, sampleRate)
                compressed && frameType == 13 -> parseCompressedType13(payload, factor).withTimeStamps(previousTimeStamp, frameTimeStamp, sampleRate)
                else -> return PpgParseResult(frameType = frameType, compressed = compressed, timeStamp = frameTimeStamp, error = "unsupportedFrame")
            }
        } catch (_: MalformedFrame) {
            return PpgParseResult(frameType = frameType, compressed = compressed, timeStamp = frameTimeStamp, error = "malformedFrame")
        }
        return PpgParseResult(frameType = frameType, compressed = compressed, timeStamp = frameTimeStamp, samples = samples)
    }

    private fun parseRawType0(payload: ByteArray): List<PpgSample> {
        val sampleSize = TYPE_0_CHANNELS * TYPE_0_SAMPLE_BYTES
        if (payload.size % sampleSize != 0) throw MalformedFrame()
        return (0 until payload.size / sampleSize).map { index ->
            val offset = index * sampleSize
            PpgSample(
                timeStamp = 0,
                ppg = listOf(
                    payload.readSignedInt(offset, TYPE_0_SAMPLE_BYTES),
                    payload.readSignedInt(offset + TYPE_0_SAMPLE_BYTES, TYPE_0_SAMPLE_BYTES),
                    payload.readSignedInt(offset + TYPE_0_SAMPLE_BYTES * 2, TYPE_0_SAMPLE_BYTES)
                ),
                ambient = payload.readSignedInt(offset + TYPE_0_SAMPLE_BYTES * 3, TYPE_0_SAMPLE_BYTES)
            )
        }
    }

    private fun parseRawIntegrationGain(payload: ByteArray, integrationBytes: Int, gainBytes: Int, integrationField: IntegrationField): List<PpgSample> {
        val sampleSize = integrationBytes + gainBytes
        if (payload.size % sampleSize != 0) throw MalformedFrame()
        return (0 until payload.size / sampleSize).map { index ->
            val offset = index * sampleSize
            val integration = payload.copyOfRange(offset, offset + integrationBytes).map { byte -> byte.toInt() and BYTE_MASK }
            val gains = payload.copyOfRange(offset + integrationBytes, offset + sampleSize).map { byte -> byte.toInt() and 0x07 }
            val channel1 = gains.filterIndexed { gainIndex, _ -> gainIndex % 2 == 0 }
            val channel2 = gains.filterIndexed { gainIndex, _ -> gainIndex % 2 == 1 }
            if (integrationField == IntegrationField.TYPE_14) {
                PpgSample(timeStamp = 0, numIntTs1 = integration, channel1GainTs1 = channel1, channel2GainTs1 = channel2)
            } else {
                PpgSample(timeStamp = 0, numIntTs = integration, channel1GainTs = channel1, channel2GainTs = channel2)
            }
        }
    }

    private fun parseCompressedType13(payload: ByteArray, factor: Double): List<PpgSample> {
        val rawSamples = parseDeltaFramesAll(payload, channels = TYPE_13_CHANNELS, resolutionBits = 24)
        return rawSamples.map { sample ->
            PpgSample(
                timeStamp = 0,
                ppgChannel0 = listOf((sample[0] * factor).toInt()),
                ppgChannel1 = listOf((sample[1] * factor).toInt()),
                statusBits = statusBits(sample[2])
            )
        }
    }

    private fun parseCompressedType7(payload: ByteArray, factor: Double): List<PpgSample> {
        val rawSamples = parseDeltaFramesAll(payload, channels = TYPE_7_CHANNELS, resolutionBits = 24)
        return rawSamples.map { sample ->
            PpgSample(
                timeStamp = 0,
                ppgDataSamples = sample.subList(0, 16).map { value -> (value * factor).toInt() },
                statusBits = statusBits(sample[16])
            )
        }
    }

    private fun parseCompressedType8(payload: ByteArray, factor: Double): List<PpgSample> {
        val rawSamples = parseDeltaFramesAll(payload, channels = TYPE_8_CHANNELS, resolutionBits = 24)
        return rawSamples.map { sample ->
            PpgSample(
                timeStamp = 0,
                ppgDataSamples = sample.subList(0, 24).map { value -> (value * factor).toInt() },
                statusBits = statusBits(sample[24])
            )
        }
    }

    private fun parseCompressedType10(payload: ByteArray, factor: Double): List<PpgSample> {
        val rawSamples = parseDeltaFramesAll(payload, channels = TYPE_10_CHANNELS, resolutionBits = 24)
        return rawSamples.map { sample ->
            PpgSample(
                timeStamp = 0,
                redSamples = sample.subList(0, 8).map { value -> (value * factor).toInt() },
                greenSamples = sample.subList(8, 14).map { value -> (value * factor).toInt() },
                irSamples = sample.subList(14, 20).map { value -> (value * factor).toInt() },
                statusBits = statusBits(sample[20]).leftPad(width = 20)
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

    private fun statusBits(value: Int): List<Int> {
        return value.toString(2).map { char -> if (char == '1') 1 else 0 }
    }

    private fun statusWord(bits: List<Int>): Int {
        return bits.fold(0) { acc, bit -> acc * 2 + bit }
    }

    private fun List<Int>.leftPad(width: Int): List<Int> {
        return if (size >= width) this else List(width - size) { 0 } + this
    }

    private fun assertIntegrationGainPlatformShape(vector: String, parsed: PpgParseResult, timestampField: String, integrationField: String, channel1Field: String, channel2Field: String) {
        val caseId = vector.stringValue("id")
        val expectedSample = vector.objectValue("expected").objectArray("samples").single()
        val androidSample = vector.objectValue("platformExpectations").objectValue("android").objectArray("samples").single()
        val iosSamples = vector.objectValue("platformExpectations").objectValue("ios").objectArray("samples")
        val parsedSample = parsed.samples.single()
        val integration = if (integrationField == "numIntTs1") parsedSample.numIntTs1 else parsedSample.numIntTs
        val channel1 = if (channel1Field == "channel1GainTs1") parsedSample.channel1GainTs1 else parsedSample.channel1GainTs
        val channel2 = if (channel2Field == "channel2GainTs1") parsedSample.channel2GainTs1 else parsedSample.channel2GainTs
        assertEquals(expectedSample.longValue(timestampField), parsedSample.timeStamp, "$caseId parsed timestamp")
        assertEquals(expectedSample.intArrayValue(integrationField), integration, "$caseId expected integration")
        assertEquals(expectedSample.intArrayValue(channel1Field), channel1, "$caseId expected channel1")
        assertEquals(expectedSample.intArrayValue(channel2Field), channel2, "$caseId expected channel2")
        assertEquals(androidSample.intArrayValue(integrationField), integration, "$caseId Android integration")
        assertEquals(androidSample.intArrayValue(channel1Field), channel1, "$caseId Android channel1")
        assertEquals(androidSample.intArrayValue(channel2Field), channel2, "$caseId Android channel2")
        val expectedIosShape = if (iosSamples.size == 1) {
            listOf(integration + channel1 + channel2)
        } else {
            listOf(integration, channel1, channel2)
        }
        assertEquals(expectedIosShape, iosSamples.map { sample -> sample.intArrayValue("ppgDataSamples") }, "$caseId iOS flattened platform shape")
    }

    private fun List<PpgSample>.withTimeStamps(previousTimeStamp: Long, frameTimeStamp: Long, sampleRate: Int): List<PpgSample> {
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

    private fun ByteArray.readUnsignedInt(offset: Int, size: Int): Long {
        return readUnsignedLong(offset, size)
    }

    private fun ByteArray.readUnsignedLong(offset: Int, size: Int): Long {
        var value = 0L
        for (index in 0 until size) {
            value = value or ((this[offset + index].toLong() and BYTE_MASK.toLong()) shl (index * BITS_PER_BYTE))
        }
        return value
    }

    private fun ByteArray.readLittleEndianLong(offset: Int, size: Int): Long {
        return readUnsignedLong(offset, size)
    }

    private fun String.longValue(field: String): Long {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toLong() ?: error("Missing long field $field in $this")
    }

    private fun String.doubleValue(field: String): Double {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:e[+-]?\\d+)?)").find(this)?.groupValues?.get(1)?.toDouble() ?: error("Missing double field $field in $this")
    }

    private fun String.optionalLongValue(field: String): Long? {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toLong()
    }

    private fun String.optionalIntValue(field: String): Int? {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toInt()
    }

    private fun String.intArrayValue(field: String): List<Int> {
        return optionalIntArrayValue(field) ?: error("Missing int array $field")
    }

    private fun String.optionalIntArrayValue(field: String): List<Int>? {
        val match = Regex("\"$field\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(this) ?: return null
        val content = match.groupValues[1].trim()
        if (content.isEmpty()) return emptyList()
        return content.split(',').map { item -> item.trim().toInt() }
    }

    private fun String.booleanValue(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" } ?: error("Missing boolean field $field in $this")
    }

    private class MalformedFrame : Exception()

    private data class PpgParseResult(
        val measurementType: String = "PPG",
        val frameType: Int? = null,
        val compressed: Boolean = false,
        val timeStamp: Long? = null,
        val samples: List<PpgSample> = emptyList(),
        val error: String? = null
    )

    private data class PpgSample(
        val timeStamp: Long,
        val ppg: List<Int> = emptyList(),
        val ppgDataSamples: List<Int> = emptyList(),
        val ambient: Int? = null,
        val operationMode: Long? = null,
        val sportId: Long? = null,
        val numIntTs: List<Int> = emptyList(),
        val channel1GainTs: List<Int> = emptyList(),
        val channel2GainTs: List<Int> = emptyList(),
        val numIntTs1: List<Int> = emptyList(),
        val channel1GainTs1: List<Int> = emptyList(),
        val channel2GainTs1: List<Int> = emptyList(),
        val greenSamples: List<Int> = emptyList(),
        val redSamples: List<Int> = emptyList(),
        val irSamples: List<Int> = emptyList(),
        val ppgChannel0: List<Int> = emptyList(),
        val ppgChannel1: List<Int> = emptyList(),
        val statusBits: List<Int> = emptyList()
    )

    private companion object {
        const val HEADER_SIZE = 10
        const val TYPE_0_CHANNELS = 4
        const val TYPE_0_SAMPLE_BYTES = 3
        const val TYPE_7_CHANNELS = 17
        const val TYPE_8_CHANNELS = 25
        const val TYPE_10_CHANNELS = 21
        const val TYPE_13_CHANNELS = 3
        const val BYTE_MASK = 0xFF
        const val COMPRESSED_MASK = 0x80
        const val FRAME_TYPE_MASK = 0x7F
        const val BITS_PER_BYTE = 8
        const val NANOSECONDS_PER_SECOND = 1_000_000_000L
        const val PPG_READINESS_MANIFEST = "protocol/sensors/ppg-frame-family-migration-readiness.json"
        val PPG_VECTORS = listOf(
            "protocol/sensors/ppg-compressed-type10-full-status.json",
            "protocol/sensors/ppg-compressed-type10-reference-status.json",
            "protocol/sensors/ppg-compressed-type13-reference-status.json",
            "protocol/sensors/ppg-compressed-type13-truncated-delta-header-android-error.json",
            "protocol/sensors/ppg-compressed-type13-truncated-delta-header-ios-reference-only.json",
            "protocol/sensors/ppg-compressed-type7-truncated-delta-payload-malformed.json",
            "protocol/sensors/ppg-compressed-type2-unsupported.json",
            "protocol/sensors/ppg-compressed-type7-reference-status.json",
            "protocol/sensors/ppg-compressed-type8-reference-status.json",
            "protocol/sensors/ppg-raw-type0-truncated-sample-malformed.json",
            "protocol/sensors/ppg-raw-type0-two-samples.json",
            "protocol/sensors/ppg-raw-type1-unsupported.json",
            "protocol/sensors/ppg-raw-type14-integration-gain-platform-shape.json",
            "protocol/sensors/ppg-raw-type4-integration-gain-platform-shape.json",
            "protocol/sensors/ppg-raw-type4-truncated-integration-gain-malformed.json",
            "protocol/sensors/ppg-raw-type5-operation-mode-max.json",
            "protocol/sensors/ppg-raw-type5-truncated-operation-mode-malformed.json",
            "protocol/sensors/ppg-raw-type6-sport-id.json",
            "protocol/sensors/ppg-raw-type6-truncated-sport-id-malformed.json",
            "protocol/sensors/ppg-raw-type9-integration-gain-platform-shape.json"
        )
        val PPG_READINESS_VECTOR_PATHS = listOf(
            "protocol/sensors/ppg-compressed-type10-full-status.json",
            "protocol/sensors/ppg-compressed-type10-reference-status.json",
            "protocol/sensors/ppg-compressed-type13-reference-status.json",
            "protocol/sensors/ppg-compressed-type13-truncated-delta-header-android-error.json",
            "protocol/sensors/ppg-compressed-type13-truncated-delta-header-ios-reference-only.json",
            "protocol/sensors/ppg-compressed-type2-unsupported.json",
            "protocol/sensors/ppg-compressed-type7-reference-status.json",
            "protocol/sensors/ppg-compressed-type7-truncated-delta-payload-malformed.json",
            "protocol/sensors/ppg-compressed-type8-reference-status.json",
            "protocol/sensors/ppg-raw-type0-truncated-sample-malformed.json",
            "protocol/sensors/ppg-raw-type0-two-samples.json",
            "protocol/sensors/ppg-raw-type1-unsupported.json",
            "protocol/sensors/ppg-raw-type14-integration-gain-platform-shape.json",
            "protocol/sensors/ppg-raw-type4-integration-gain-platform-shape.json",
            "protocol/sensors/ppg-raw-type4-truncated-integration-gain-malformed.json",
            "protocol/sensors/ppg-raw-type5-operation-mode-max.json",
            "protocol/sensors/ppg-raw-type5-truncated-operation-mode-malformed.json",
            "protocol/sensors/ppg-raw-type6-sport-id.json",
            "protocol/sensors/ppg-raw-type6-truncated-sport-id-malformed.json",
            "protocol/sensors/ppg-raw-type9-integration-gain-platform-shape.json"
        )
        val REQUIRED_PPG_FRAME_FAMILIES = listOf(
            "raw-type0",
            "raw-type4-integration-gain",
            "raw-type5-operation-mode",
            "raw-type6-sport-id",
            "raw-type9-integration-gain",
            "raw-type14-integration-gain",
            "compressed-type7",
            "compressed-type8",
            "compressed-type10",
            "compressed-type13",
            "unsupported-raw",
            "unsupported-compressed",
            "malformed-raw",
            "malformed-fixed",
            "malformed-compressed",
            "platform-split-type10-red-green",
            "platform-split-type13-shape",
            "platform-split-integration-gain-shape"
        )
        const val PPG_READINESS_COMMON_DECISION = "PPG parser migration may proceed only after every required vector path in this manifest is executable from shared commonTest and compile-verified against the common parser prototype; this manifest does not replace parser execution or Gradle commonTest verification."
    }

    private enum class IntegrationField {
        STANDARD,
        TYPE_14
    }
}
