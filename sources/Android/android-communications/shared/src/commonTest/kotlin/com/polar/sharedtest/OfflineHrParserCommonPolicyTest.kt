package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineHrParserCommonPolicyTest {
    @Test
    fun offlineHrGoldenVectorsDefineExecutableCommonRawAndUnsupportedFramePolicy() {
        OFFLINE_HR_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val expected = vector.objectValue("expected")
            val parsed = parseOfflineHr(vector.objectValue("input").stringValue("dataFrameHex"))

            expected.optionalStringValue("sharedOwnership")?.let { ownership ->
                assertEquals(PROTOCOL_ONLY_PLATFORM_OWNERSHIP, ownership, caseId)
            }
            assertEquals(expected.stringValue("measurementType"), parsed.measurementType, caseId)
            assertEquals(expected.intValue("frameType"), parsed.frameType, caseId)
            assertEquals(expected.booleanValue("compressed"), parsed.compressed, caseId)
            expected.optionalStringValue("parseError")?.let { parseError ->
                assertEquals(parseError, parsed.error, caseId)
                return@forEach
            }

            val expectedSamples = expected.objectArray("samples")
            assertEquals(expectedSamples.size, parsed.samples.size, "$caseId sample count")
            expectedSamples.forEachIndexed { index, expectedSample ->
                val actual = parsed.samples[index]
                assertEquals(expectedSample.intValue("hr"), actual.hr, "$caseId sample $index hr")
                assertEquals(expectedSample.intValue("ppgQuality"), actual.ppgQuality, "$caseId sample $index ppgQuality")
                assertEquals(expectedSample.intValue("correctedHr"), actual.correctedHr, "$caseId sample $index correctedHr")
            }
        }
    }

    @Test
    fun offlineHrReadinessManifestNamesEverySharedContractBehaviorFamily() {
        val manifest = loadGoldenVectorText("protocol/sensors/offline-hr-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")

        assertEquals("offline-hr-readiness", manifest.stringValue("id"))
        assertEquals("offlineHrReadiness", input.stringValue("kind"))
        assertEquals(OFFLINE_HR_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_OFFLINE_HR_FAMILIES, requiredFamilies, "Offline HR readiness manifest must name every shared-contract behavior family")
        assertEquals(REQUIRED_OFFLINE_HR_FAMILIES, coveredFamilies, "Offline HR readiness manifest must keep expected coverage aligned with required families")
        assertEquals("coveredBySharedContractCharacterization", expected.stringValue("sharedOwnershipStatus"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.OfflineHrDataTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("OfflineHrDataTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.OfflineHrParserCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun parseOfflineHr(hex: String): OfflineHrParseResult {
        val bytes = hexToBytes(hex)
        if (bytes.size < HEADER_SIZE) return OfflineHrParseResult(error = "malformedFrame")
        val rawFrameType = bytes[9].toInt() and 0xFF
        val compressed = (rawFrameType and 0x80) != 0
        val frameType = rawFrameType and 0x7F
        if (compressed) {
            return OfflineHrParseResult(frameType = frameType, compressed = true, error = "unsupportedCompressedFrame")
        }
        val payload = bytes.copyOfRange(HEADER_SIZE, bytes.size)
        return when (frameType) {
            0 -> OfflineHrParseResult(
                frameType = frameType,
                samples = payload.map { byte ->
                    OfflineHrSample(hr = byte.toInt() and 0xFF, ppgQuality = 0, correctedHr = 0)
                }
            )
            1 -> {
                if (payload.size % RAW_TYPE_1_SAMPLE_SIZE != 0) {
                    OfflineHrParseResult(frameType = frameType, error = "malformedFrame")
                } else {
                    OfflineHrParseResult(
                        frameType = frameType,
                        samples = (0 until payload.size / RAW_TYPE_1_SAMPLE_SIZE).map { index ->
                            val offset = index * RAW_TYPE_1_SAMPLE_SIZE
                            OfflineHrSample(
                                hr = payload[offset].toInt() and 0xFF,
                                ppgQuality = payload[offset + 1].toInt() and 0xFF,
                                correctedHr = payload[offset + 2].toInt() and 0xFF
                            )
                        }
                    )
                }
            }
            else -> OfflineHrParseResult(frameType = frameType, error = "unsupportedFrame")
        }
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

    private fun String.objectValue(field: String): String {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex == -1) error("Missing object field $field in $this")
        val objectStart = indexOf('{', startIndex = fieldIndex)
        if (objectStart == -1) error("Missing object value for $field in $this")
        var depth = 0
        for (index in objectStart until length) {
            when (this[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return substring(objectStart, index + 1)
                }
            }
        }
        error("Unterminated object field $field in $this")
    }

    private data class OfflineHrParseResult(
        val measurementType: String = "OFFLINE_HR",
        val frameType: Int? = null,
        val compressed: Boolean = false,
        val samples: List<OfflineHrSample> = emptyList(),
        val error: String? = null
    )

    private data class OfflineHrSample(
        val hr: Int,
        val ppgQuality: Int,
        val correctedHr: Int
    )

    private companion object {
        const val HEADER_SIZE = 10
        const val RAW_TYPE_1_SAMPLE_SIZE = 3
        val OFFLINE_HR_VECTORS = listOf(
            "protocol/sensors/offline-hr-compressed-type0-unsupported.json",
            "protocol/sensors/offline-hr-raw-type0-empty.json",
            "protocol/sensors/offline-hr-raw-type0-hr-only-boundaries.json",
            "protocol/sensors/offline-hr-raw-type1-truncated-tuple-android-error.json",
            "protocol/sensors/offline-hr-raw-type1-two-samples.json",
            "protocol/sensors/offline-hr-raw-type2-unsupported.json"
        )
        val REQUIRED_OFFLINE_HR_FAMILIES = listOf(
            "raw-type0-hr-only-samples",
            "raw-type0-empty-recording",
            "raw-type1-hr-ppg-quality-corrected-hr-triples",
            "unsupported-compressed-frame-policy",
            "unsupported-raw-frame-policy",
            "truncated-raw-type1-tuple-policy",
            "platform-offline-hr-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}
