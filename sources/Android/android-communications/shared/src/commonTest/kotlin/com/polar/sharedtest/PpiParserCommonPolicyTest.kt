package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals

class PpiParserCommonPolicyTest {
    @Test
    fun ppiGoldenVectorsDefineExecutableCommonRawType0AndUnsupportedFramePolicy() {
        PPI_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")
            val parsed = parsePpi(input.stringValue("dataFrameHex"))

            expected.optionalStringValue("sharedOwnership")?.let { ownership ->
                assertEquals(PROTOCOL_ONLY_PLATFORM_OWNERSHIP, ownership, caseId)
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
                assertEquals(expectedSample.intValue("hr"), actual.hr, "$caseId sample $index hr")
                assertEquals(expectedSample.intValue("ppInMs"), actual.ppInMs, "$caseId sample $index ppInMs")
                assertEquals(expectedSample.intValue("ppErrorEstimate"), actual.ppErrorEstimate, "$caseId sample $index ppErrorEstimate")
                assertEquals(expectedSample.intValue("blockerBit"), actual.blockerBit, "$caseId sample $index blockerBit")
                assertEquals(expectedSample.intValue("skinContactStatus"), actual.skinContactStatus, "$caseId sample $index skinContactStatus")
                assertEquals(expectedSample.intValue("skinContactSupported"), actual.skinContactSupported, "$caseId sample $index skinContactSupported")
            }
        }
    }

    @Test
    fun ppiReadinessManifestNamesEverySharedContractBehaviorFamily() {
        val manifest = loadGoldenVectorText("protocol/sensors/ppi-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")

        assertEquals("ppi-readiness", manifest.stringValue("id"))
        assertEquals("ppiReadiness", input.stringValue("kind"))
        assertEquals(PPI_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_PPI_FAMILIES, requiredFamilies, "PPI readiness manifest must name every shared-contract behavior family")
        assertEquals(REQUIRED_PPI_FAMILIES, coveredFamilies, "PPI readiness manifest must keep expected coverage aligned with required families")
        assertEquals("coveredBySharedContractCharacterization", expected.stringValue("sharedOwnershipStatus"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpiDataTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PpiDataTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.PpiParserCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun parsePpi(hex: String): PpiParseResult {
        val bytes = hexToBytes(hex)
        if (bytes.size < 10) return PpiParseResult(error = "malformedFrame")
        val rawFrameType = bytes[9].toInt() and 0xFF
        val compressed = (rawFrameType and 0x80) != 0
        val frameType = rawFrameType and 0x7F
        val frameTimeStamp = bytes.readLittleEndianLong(offset = 1, size = 8)
        if (compressed || frameType != 0) {
            return PpiParseResult(frameType = frameType, compressed = compressed, timeStamp = frameTimeStamp, error = "unsupportedFrame")
        }
        val payload = bytes.copyOfRange(10, bytes.size)
        if (payload.size % RAW_PPI_SAMPLE_SIZE != 0) {
            return PpiParseResult(frameType = frameType, compressed = compressed, timeStamp = frameTimeStamp, error = "malformedFrame")
        }
        val samples = (0 until payload.size / RAW_PPI_SAMPLE_SIZE).map { index ->
            val offset = index * RAW_PPI_SAMPLE_SIZE
            val flags = payload[offset + 5].toInt() and 0xFF
            PpiSample(
                timeStamp = 0,
                hr = payload[offset].toInt() and 0xFF,
                ppInMs = payload.readU16Le(offset = offset + 1),
                ppErrorEstimate = payload.readU16Le(offset = offset + 3),
                blockerBit = flags and 0x01,
                skinContactStatus = (flags shr 1) and 0x01,
                skinContactSupported = (flags shr 2) and 0x01
            )
        }.toMutableList()
        if (frameTimeStamp != 0L && samples.isNotEmpty()) {
            var timestamp = frameTimeStamp
            for (index in samples.size - 1 downTo 0) {
                samples[index] = samples[index].copy(timeStamp = timestamp)
                if (index > 0) {
                    timestamp -= samples[index].ppInMs * 1_000_000L
                }
            }
        }
        return PpiParseResult(frameType = frameType, compressed = compressed, timeStamp = frameTimeStamp, samples = samples)
    }

    private fun ByteArray.readU16Le(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun ByteArray.readLittleEndianLong(offset: Int, size: Int): Long {
        var value = 0L
        for (index in 0 until size) {
            value = value or ((this[offset + index].toLong() and 0xFFL) shl (index * 8))
        }
        return value
    }

    private fun String.longValue(field: String): Long {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toLong() ?: error("Missing long field $field in $this")
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

    private data class PpiParseResult(
        val measurementType: String = "PPI",
        val frameType: Int? = null,
        val compressed: Boolean = false,
        val timeStamp: Long? = null,
        val samples: List<PpiSample> = emptyList(),
        val error: String? = null
    )

    private data class PpiSample(
        val timeStamp: Long,
        val hr: Int,
        val ppInMs: Int,
        val ppErrorEstimate: Int,
        val blockerBit: Int,
        val skinContactStatus: Int,
        val skinContactSupported: Int
    )

    private companion object {
        const val RAW_PPI_SAMPLE_SIZE = 6
        val PPI_VECTORS = listOf(
            "protocol/sensors/ppi-compressed-type0-unsupported.json",
            "protocol/sensors/ppi-raw-type0-truncated-sample-android-error.json",
            "protocol/sensors/ppi-raw-type0-two-samples.json",
            "protocol/sensors/ppi-raw-type0-zero-timestamp-boundary.json",
            "protocol/sensors/ppi-raw-type1-unsupported.json"
        )
        val REQUIRED_PPI_FAMILIES = listOf(
            "raw-type0-hr-rr-error-status-parsing",
            "raw-type0-zero-timestamp-policy",
            "raw-type0-timestamp-backfill",
            "unsupported-compressed-frame-policy",
            "unsupported-raw-frame-policy",
            "truncated-raw-sample-policy",
            "platform-ppi-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}
