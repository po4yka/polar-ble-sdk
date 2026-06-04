package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals

class EcgParserCommonPolicyTest {
    @Test
    fun ecgGoldenVectorsDefineExecutableCommonRawType0Policy() {
        ECG_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")
            val parsed = parseEcg(input.stringValue("dataFrameHex"), input.longValue("previousTimeStamp"))

            expected.optionalStringValue("migrationOwnership")?.let { ownership ->
                assertEquals(PROTOCOL_ONLY_MIGRATION_OWNERSHIP, ownership, caseId)
                assertEquals(expected.intValue("frameType"), parsed.frameType, caseId)
                if (expected.optionalStringValue("parseError") != null) {
                    assertEquals(expected.stringValue("parseError"), parsed.error, caseId)
                }
                return@forEach
            }

            assertEquals(expected.stringValue("measurementType"), parsed.measurementType, caseId)
            assertEquals(expected.intValue("frameType"), parsed.frameType, caseId)
            assertEquals(expected.longValue("timeStamp"), parsed.timeStamp, caseId)
            val expectedSamples = expected.objectArray("samples")
            assertEquals(expectedSamples.size, parsed.samples.size, "$caseId sample count")
            expectedSamples.forEachIndexed { index, expectedSample ->
                val actual = parsed.samples[index]
                assertEquals(expectedSample.longValue("timeStamp"), actual.timeStamp, "$caseId sample $index timestamp")
                assertEquals(expectedSample.signedIntValue("microVolts"), actual.microVolts, "$caseId sample $index microVolts")
            }
        }
    }

    @Test
    fun ecgReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("protocol/sensors/ecg-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val platforms = manifest.objectValue("platforms")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")

        assertEquals("ecg-readiness", manifest.stringValue("id"))
        assertEquals("ecgReadiness", input.stringValue("kind"))
        assertEquals(ECG_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_ECG_FAMILIES, requiredFamilies, "ECG readiness manifest must name every pre-migration behavior family")
        assertEquals(REQUIRED_ECG_FAMILIES, coveredFamilies, "ECG readiness manifest must keep expected coverage aligned with required families")
        assertEquals("coveredByPreMigrationCharacterization", expected.stringValue("migrationReadiness"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.EcgDataTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("EcgDataTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.EcgParserCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private fun parseEcg(hex: String, previousTimeStamp: Long): EcgParseResult {
        val bytes = hexToBytes(hex)
        if (bytes.size < 10) return EcgParseResult(error = "malformedFrame")
        val frameType = bytes[9].toInt() and 0xFF
        if (frameType != 0) {
            return EcgParseResult(frameType = frameType)
        }
        val payload = bytes.copyOfRange(10, bytes.size)
        if (payload.size % 3 != 0) {
            return EcgParseResult(
                frameType = frameType,
                timeStamp = bytes.readLittleEndianLong(offset = 1, size = 8),
                error = "malformedFrame"
            )
        }
        val frameTimeStamp = bytes.readLittleEndianLong(offset = 1, size = 8)
        val sampleCount = payload.size / 3
        val samples = (0 until sampleCount).map { index ->
            val microVolts = payload.readSigned24(offset = index * 3)
            val sampleTimeStamp = previousTimeStamp + ((frameTimeStamp - previousTimeStamp) * (index + 1) / sampleCount)
            EcgSample(timeStamp = sampleTimeStamp, microVolts = microVolts)
        }
        return EcgParseResult(
            measurementType = "ECG",
            frameType = frameType,
            timeStamp = frameTimeStamp,
            samples = samples
        )
    }

    private fun ByteArray.readSigned24(offset: Int): Int {
        val value = (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8) or ((this[offset + 2].toInt() and 0xFF) shl 16)
        return if ((value and 0x800000) != 0) value - 0x1000000 else value
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

    private fun String.signedIntValue(field: String): Int {
        return longValue(field).toInt()
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

    private data class EcgParseResult(
        val measurementType: String? = null,
        val frameType: Int? = null,
        val timeStamp: Long? = null,
        val samples: List<EcgSample> = emptyList(),
        val error: String? = null
    )

    private data class EcgSample(
        val timeStamp: Long,
        val microVolts: Int
    )

    private companion object {
        val ECG_VECTORS = listOf(
            "protocol/sensors/ecg-raw-type0-signed-24bit-boundaries.json",
            "protocol/sensors/ecg-raw-type0-truncated-sample-android-error.json",
            "protocol/sensors/ecg-raw-type0-two-samples.json",
            "protocol/sensors/ecg-raw-type1-android-status-bits.json",
            "protocol/sensors/ecg-raw-type2-android-tags.json",
            "protocol/sensors/ecg-raw-type3-android-frame-samples.json"
        )
        val REQUIRED_ECG_FAMILIES = listOf(
            "raw-type0-signed-24bit-parsing",
            "raw-type0-boundary-values",
            "raw-type0-timestamp-interpolation",
            "raw-type0-malformed-short-sample-policy",
            "android-raw-type1-status-bit-ownership",
            "android-raw-type2-tag-ownership",
            "android-raw-type3-frame-sample-ownership",
            "platform-ecg-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}
