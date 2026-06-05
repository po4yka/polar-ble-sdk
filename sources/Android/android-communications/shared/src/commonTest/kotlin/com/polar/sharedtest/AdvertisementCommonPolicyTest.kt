package com.polar.sharedtest

import com.polar.shared.ble.PolarAdvertisementModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdvertisementCommonPolicyTest {
    @Test
    fun advertisementGoldenVectorsDefineExecutableCommonParsingPolicy() {
        ADVERTISEMENT_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.commonExpectedObject()

            input.optionalStringValue("localName")?.let { localName ->
                val parsed = PolarAdvertisementModels.parseLocalName(
                    localName = localName,
                    deviceNamePrefix = input.optionalStringValue("deviceNamePrefix") ?: "Polar"
                )
                expected.optionalStringValue("name")?.let { expectedName ->
                    assertEquals(expectedName, parsed.name, caseId)
                }
                expected.optionalStringValue("deviceType")?.let { expectedType ->
                    assertEquals(expectedType, parsed.deviceType, caseId)
                }
                expected.optionalStringValue("deviceId")?.let { expectedDeviceId ->
                    assertEquals(expectedDeviceId, parsed.deviceId, caseId)
                }
                expected.optionalStringValue("status")?.let { status ->
                    assertEquals("undecided", status, caseId)
                }
            }

            input.optionalStringValue("manufacturerDataHex")?.let { manufacturerDataHex ->
                expected.optionalBooleanValue("hrPresent")?.let { expectedHrPresent ->
                    assertEquals(expectedHrPresent, parseManufacturerHrPresent(hexToBytes(manufacturerDataHex)), caseId)
                }
                expected.optionalStringValue("policy")?.let { policy ->
                    assertEquals(ADVERTISEMENT_MALFORMED_GPB_COMMON_POLICY, policy, caseId)
                    assertEquals(false, parseManufacturerHrPresent(hexToBytes(manufacturerDataHex)), caseId)
                }
            }

            input.optionalStringArrayValue("services16Hex")?.let { services ->
                val containsServices = expected.objectValue("containsServices")
                containsServices.booleanEntries().forEach { (service, expectedContains) ->
                    assertEquals(expectedContains, services.any { candidate -> candidate.equals(service, ignoreCase = true) }, "$caseId:$service")
                }
            }

            input.optionalIntArrayValue("rssiSequence")?.let { sequence ->
                val parsed = parseRssi(sequence)
                assertEquals(expected.intValue("rssi"), parsed.rssi, caseId)
                assertEquals(expected.intValue("medianRssi"), parsed.medianRssi, caseId)
            }
        }
    }

    @Test
    fun advertisementReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val vector = loadGoldenVectorText("protocol/advertisement/advertisement-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")

        assertEquals("advertisement-readiness", vector.stringValue("id"))
        assertEquals("advertisementReadiness", input.stringValue("kind"))
        assertEquals(ADVERTISEMENT_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_ADVERTISEMENT_FAMILIES, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(REQUIRED_ADVERTISEMENT_FAMILIES, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(ADVERTISEMENT_READINESS_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContentTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("BleAdvertisementContentTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.AdvertisementCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun String.commonExpectedObject(): String {
        val platformExpectations = optionalObjectValue("platformExpectations")
        return if (platformExpectations?.contains("\"commonDecision\"\\s*:\\s*\\{".toRegex()) == true) {
            platformExpectations.objectValue("commonDecision")
        } else if (platformExpectations?.contains("\"common\"\\s*:\\s*\\{".toRegex()) == true) {
            platformExpectations.objectValue("common")
        } else {
            objectValue("expected")
        }
    }

    private fun parseManufacturerHrPresent(bytes: ByteArray): Boolean {
        if (bytes.size < 3) return false
        val isPolarCompany = bytes[0] == 0x6b.toByte() && bytes[1] == 0x00.toByte()
        if (!isPolarCompany) return false
        return bytes.drop(2).windowed(size = 6, step = 1, partialWindows = false).any { window ->
            window[0] == 0x7a.toByte() && window[1] == 0x01.toByte()
        }
    }

    private fun parseRssi(sequence: List<Int>): RssiPolicy {
        val window = sequence.takeLast(7)
        val sorted = window.sorted()
        return RssiPolicy(
            rssi = sequence.last(),
            medianRssi = sorted[sorted.size / 2]
        )
    }

    private fun String.optionalObjectValue(field: String): String? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val objectStart = indexOf('{', fieldIndex)
        if (objectStart < 0) return null
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.optionalBooleanValue(field: String): Boolean? {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" }
    }

    private fun String.optionalIntArrayValue(field: String): List<Int>? {
        val match = Regex("\"$field\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(this) ?: return null
        return Regex("-?\\d+").findAll(match.groupValues[1]).map { matchResult -> matchResult.value.toInt() }.toList()
    }

    private fun String.stringArrayValue(field: String): List<String> {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) error("Missing field $field")
        val arrayStart = indexOf('[', fieldIndex)
        if (arrayStart < 0) error("Missing array field $field")
        val array = substring(arrayStart, balancedEnd(arrayStart, '[', ']') + 1)
        return Regex("\"([^\"]+)\"").findAll(array).map { match -> match.groupValues[1] }.toList()
    }

    private fun String.booleanEntries(): Map<String, Boolean> {
        return Regex("\"([^\"]+)\"\\s*:\\s*(true|false)").findAll(this).associate { match ->
            match.groupValues[1] to (match.groupValues[2] == "true")
        }
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

    private data class RssiPolicy(
        val rssi: Int,
        val medianRssi: Int
    )

    private companion object {
        val ADVERTISEMENT_VECTORS = listOf(
            "protocol/advertisement/custom-prefix-local-name.json",
            "protocol/advertisement/manufacturer-hr-sagrfc23.json",
            "protocol/advertisement/manufacturer-no-hr.json",
            "protocol/advertisement/manufacturer-non-polar.json",
            "protocol/advertisement/manufacturer-polar-gpb-missing-length-platform-policy.json",
            "protocol/advertisement/manufacturer-polar-id-only.json",
            "protocol/advertisement/manufacturer-polar-truncated-hr-candidate.json",
            "protocol/advertisement/manufacturer-polar-unknown-gpb-segment.json",
            "protocol/advertisement/manufacturer-unknown-company.json",
            "protocol/advertisement/non-polar-local-name-platform-difference.json",
            "protocol/advertisement/polar-local-name.json",
            "protocol/advertisement/rssi-median-seven-sample-window.json",
            "protocol/advertisement/service-uuid-membership.json",
            "protocol/advertisement/seven-digit-local-name.json"
        )
        val REQUIRED_ADVERTISEMENT_FAMILIES = listOf(
            "polar-local-name-parsing",
            "custom-prefix-local-name-parsing",
            "seven-digit-device-id-assembly",
            "non-polar-local-name-platform-decision",
            "manufacturer-polar-hr-presence",
            "manufacturer-no-hr-policy",
            "manufacturer-non-polar-policy",
            "manufacturer-unknown-company-policy",
            "manufacturer-unknown-segment-policy",
            "malformed-gpb-missing-length-policy",
            "malformed-truncated-hr-candidate-policy",
            "service-uuid-membership",
            "rssi-median-seven-sample-window",
            "platform-advertisement-vector-reference-gate",
            "compile-verification-gate"
        )
        const val ADVERTISEMENT_READINESS_DECISION = "Advertisement parsing migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS advertisement tests continue to reference the same vectors, Polar and custom-prefix local-name parsing, seven-digit device ID assembly, non-Polar local-name platform decisions, manufacturer HR presence and absence, non-Polar and unknown company behavior, unknown Polar segment handling, malformed GPB missing-length and truncated HR-candidate policies, service UUID membership, RSSI median calculation, and compile verification remain explicit before production advertisement parsing moves."
        const val ADVERTISEMENT_MALFORMED_GPB_COMMON_POLICY = "reject or ignore malformed GPB segments deterministically without indexing beyond payload bounds"
    }
}
