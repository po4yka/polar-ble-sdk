package com.polar.sharedtest

import com.polar.shared.pmd.PolarPmdParseError
import com.polar.shared.pmd.PolarPmdSettingType
import com.polar.shared.pmd.PolarPmdSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class PmdSettingsCommonPolicyTest {
    @Test
    fun pmdSettingsGoldenVectorsDefineExecutableCommonParsingAndSerializationPolicy() {
        PMD_SETTINGS_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.commonExpectedObject()

            expected.optionalStringValue("parseError")?.let { expectedError ->
                assertEquals(expectedError, parseSettings(input.optionalStringValue("hex") ?: "").error?.vectorName, caseId)
            }
            expected.optionalStringValue("status")?.let { status ->
                assertEquals("undecided", status, caseId)
            }
            expected.optionalObjectValue("settings")?.let { expectedSettings ->
                val actual = if (input.optionalStringValue("hex") != null) {
                    parseSettings(input.stringValue("hex")).settings
                } else {
                    parseSettings(serializeSelectedSettings(input.objectValue("selected"))).settings
                }
                expectedSettings.settingEntries().forEach { (type, expectedValues) ->
                    assertEquals(expectedValues, actual[type], "$caseId:$type")
                }
            }
            expected.optionalStringArrayValue("missingSettings")?.forEach { missingSetting ->
                val actual = if (input.optionalStringValue("hex") != null) {
                    parseSettings(input.stringValue("hex")).settings
                } else {
                    parseSettings(serializeSelectedSettings(input.objectValue("selected"))).settings
                }
                assertEquals(false, actual.containsKey(missingSetting), "$caseId:$missingSetting")
            }
            expected.optionalStringValue("serializedHex")?.let { expectedHex ->
                assertEquals(expectedHex, serializeSelectedSettings(input.objectValue("selected")), caseId)
            }
        }
    }

    @Test
    fun pmdSettingsReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val vector = loadGoldenVectorText("protocol/pmd/settings-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")

        assertEquals("pmd-settings-readiness", vector.stringValue("id"))
        assertEquals("pmdSettingsReadiness", input.stringValue("kind"))
        assertEquals(PMD_SETTINGS_VECTORS, input.stringArrayValue("policyVectorPaths"))
        assertEquals(REQUIRED_PMD_SETTINGS_FAMILIES, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(REQUIRED_PMD_SETTINGS_FAMILIES, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(PMD_SETTINGS_READINESS_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSettingTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PmdSettingTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.PmdSettingsCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals("invalidPMDData", PolarPmdParseError.InvalidPmdData.vectorName)
    }

    private fun String.commonExpectedObject(): String {
        val platformExpectations = optionalObjectValue("platformExpectations")
        return if (platformExpectations?.contains("\"commonDecision\"\\s*:\\s*\\{".toRegex()) == true) {
            platformExpectations.objectValue("commonDecision")
        } else {
            objectValue("expected")
        }
    }

    private fun parseSettings(hex: String): SettingsParseResult {
        val result = PolarPmdSettings.parseSettings(hexToBytes(hex))
        return SettingsParseResult(
            settings = result.settings.mapKeys { it.key.name }.mapValues { it.value.map(Int::toLong) },
            error = result.error
        )
    }

    private fun serializeSelectedSettings(selected: String): String {
        val selectedSettings = SELECTED_SETTING_ORDER.mapNotNull { name ->
            val value = selected.optionalScalarValue(name)?.toIntOrNull()
            val type = PolarPmdSettingType.valueOf(name)
            if (value == null) null else type to value
        }.toMap()
        return PolarPmdSettings.serializeSelectedSettings(selectedSettings).toHex()
    }

    private fun ByteArray.toHex(): String {
        return joinToString(separator = "") { byte ->
            val value = byte.toInt() and 0xFF
            "${(value / 16).toHexDigit()}${(value % 16).toHexDigit()}"
        }
    }

    private fun Int.toHexDigit(): Char {
        return if (this < 10) '0' + this else 'a' + (this - 10)
    }

    private fun String.optionalObjectValue(field: String): String? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val objectStart = indexOf('{', fieldIndex)
        if (objectStart < 0) return null
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.optionalScalarValue(field: String): String? {
        val quoted = Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"").find(this)
        if (quoted != null) return quoted.groupValues[1]
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)
    }

    private fun String.settingEntries(): Map<String, List<Long>> {
        return Regex("\"([A-Z_]+)\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).findAll(this).associate { match ->
            val values = Regex("-?\\d+").findAll(match.groupValues[2]).map { value -> value.value.toLong() }.toList()
            match.groupValues[1] to values
        }
    }

    private fun String.stringArrayValue(field: String): List<String> {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) error("Missing field $field")
        val arrayStart = indexOf('[', fieldIndex)
        if (arrayStart < 0) error("Missing array field $field")
        val array = substring(arrayStart, balancedEnd(arrayStart, '[', ']') + 1)
        return Regex("\"([^\"]+)\"").findAll(array).map { match -> match.groupValues[1] }.toList()
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

    private data class SettingsParseResult(
        val settings: Map<String, List<Long>> = emptyMap(),
        val error: PolarPmdParseError? = null
    )

    private companion object {
        val PMD_SETTINGS_VECTORS = listOf(
            "protocol/pmd/settings-basic-range.json",
            "protocol/pmd/settings-duplicate-sample-rate-factor.json",
            "protocol/pmd/settings-range-milliunit-platform-difference.json",
            "protocol/pmd/settings-security-value-platform-error.json",
            "protocol/pmd/settings-selected-serialization-max-values.json",
            "protocol/pmd/settings-truncated-resolution-platform-difference.json",
            "protocol/pmd/settings-unknown-type-platform-difference.json"
        )
        val REQUIRED_PMD_SETTINGS_FAMILIES = listOf(
            "basic-settings-parsing",
            "duplicate-setting-overwrite",
            "factor-setting-parsing",
            "selected-setting-serialization",
            "selected-factor-skip-policy",
            "range-milliunit-signedness-platform-decision",
            "security-setting-platform-error-policy",
            "truncated-value-platform-decision",
            "unknown-setting-type-platform-decision",
            "platform-pmd-settings-vector-reference-gate",
            "compile-verification-gate"
        )
        const val PMD_SETTINGS_READINESS_DECISION = "PMD settings migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS PMD settings tests continue to reference the same vectors, baseline parsing, duplicate overwrite behavior, FACTOR parsing, selected-setting serialization, skipped FACTOR serialization, RANGE_MILLIUNIT signedness platform decisions, SECURITY setting parse policy, truncated-value policy, unknown-setting-type policy, and compile verification remain explicit before production PMD settings logic moves."
        val SELECTED_SETTING_ORDER = listOf("SAMPLE_RATE", "RESOLUTION", "RANGE", "RANGE_MILLIUNIT", "CHANNELS", "FACTOR")
    }
}
