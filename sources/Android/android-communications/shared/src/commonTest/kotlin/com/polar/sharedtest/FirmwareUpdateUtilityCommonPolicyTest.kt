package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FirmwareUpdateUtilityCommonPolicyTest {
    @Test
    fun firmwareDeviceInfoGoldenVectorsDefineExecutableCommonMappingPolicy() {
        listOf(
            "sdk/firmware-update/device-info-basic.json",
            "sdk/firmware-update/device-info-zero-version.json"
        ).forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val proto = vector.objectValue("input").objectValue("proto")
            val expected = vector.objectValue("expected")
            val mapped = mapDeviceInfo(proto)

            assertEquals(expected.stringValue("deviceFwVersion"), mapped.deviceFwVersion, vector.stringValue("id"))
            assertEquals(expected.stringValue("deviceModelName"), mapped.deviceModelName, vector.stringValue("id"))
            assertEquals(expected.stringValue("deviceHardwareCode"), mapped.deviceHardwareCode, vector.stringValue("id"))
            vector.optionalObjectValue("platformExpectations")?.optionalObjectValue("commonDecision")?.let { decision ->
                assertEquals("preserve-empty-device-info-strings", decision.stringValue("emptyStringPolicy"), vector.stringValue("id"))
            }
        }
    }

    @Test
    fun firmwareVersionComparisonGoldenVectorDefinesExecutableCommonDottedIntegerPolicy() {
        val vector = loadGoldenVectorText("sdk/firmware-update/version-comparison.json")
        assertEquals("dotted-integer-version-comparison", vector.objectValue("expected").stringValue("policy"))

        vector.objectValue("input").objectArray("cases").forEach { testCase ->
            assertEquals(
                testCase.booleanValue("expectedHigher"),
                isAvailableVersionHigher(testCase.stringValue("currentVersion"), testCase.stringValue("availableVersion")),
                "${testCase.stringValue("currentVersion")} -> ${testCase.stringValue("availableVersion")}"
            )
        }
        assertEquals(FIRMWARE_VERSION_POLICY_DECISION, vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("versionPolicy"))
    }

    @Test
    fun firmwareInvalidVersionGoldenVectorPinsTypedParseFailureBeforePublicWorkflowMigration() {
        val vector = loadGoldenVectorText("sdk/firmware-update/version-comparison-invalid.json")
        assertEquals("invalid-version-error", vector.objectValue("expected").stringValue("policy"))

        vector.objectValue("input").objectArray("cases").forEach { testCase ->
            assertFailsWith<VersionParseFailure>(testCase.stringValue("currentVersion")) {
                isAvailableVersionHigher(testCase.stringValue("currentVersion"), testCase.stringValue("availableVersion"))
            }
            val expectedError = testCase.objectValue("expectedError")
            assertEquals("NumberFormatException", expectedError.stringValue("android"), testCase.stringValue("currentVersion"))
            assertEquals("fatal", expectedError.stringValue("ios"), testCase.stringValue("currentVersion"))
        }
        assertEquals(FIRMWARE_INVALID_VERSION_POLICY_DECISION, vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("invalidVersionPolicy"))
    }

    @Test
    fun firmwareFileOrderingGoldenVectorDefinesExecutableCommonSystemUpdateLastPolicy() {
        val vector = loadGoldenVectorText("sdk/firmware-update/file-ordering.json")
        assertEquals("system-update-last", vector.objectValue("expected").stringValue("policy"))

        vector.objectValue("input").objectArray("cases").forEach { testCase ->
            assertEquals(testCase.stringArrayValue("expected"), orderFirmwareFiles(testCase.stringArrayValue("input")), testCase.stringArrayValue("input").joinToString(","))
        }
        assertEquals(FIRMWARE_FILE_ORDERING_POLICY_DECISION, vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("orderingPolicy"))
    }

    @Test
    fun firmwareUtilityReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/firmware-update/utility-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPaths = input.stringArrayValue("policyVectorPaths")
        val requiredBehaviorFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredBehaviorFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val consumerTests = manifest.objectValue("consumerTests")

        assertEquals("firmware-utility-readiness", manifest.stringValue("id"))
        assertEquals("firmwareUtilityReadiness", input.stringValue("kind"))
        assertEquals("compileVerifiedPreMigrationCharacterization", expected.stringValue("migrationReadiness"))
        assertEquals(REQUIRED_FIRMWARE_UTILITY_FAMILIES, requiredBehaviorFamilies)
        assertEquals(REQUIRED_FIRMWARE_UTILITY_FAMILIES, coveredBehaviorFamilies)
        assertEquals(FIRMWARE_UTILITY_POLICY_VECTORS, policyVectorPaths)
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarFirmwareUpdateUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarFirmwareUpdateUtilsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.FirmwareUpdateUtilityCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun mapDeviceInfo(proto: String): FirmwareDeviceInfo {
        val version = proto.objectValue("version")
        return FirmwareDeviceInfo(
            deviceFwVersion = "${version.intValue("major")}.${version.intValue("minor")}.${version.intValue("patch")}",
            deviceModelName = proto.stringValue("modelName"),
            deviceHardwareCode = proto.stringValue("hardwareCode")
        )
    }

    private fun isAvailableVersionHigher(currentVersion: String, availableVersion: String): Boolean {
        val currentParts = currentVersion.versionParts()
        val availableParts = availableVersion.versionParts()
        val sharedSize = minOf(currentParts.size, availableParts.size)
        for (index in 0 until sharedSize) {
            if (availableParts[index] > currentParts[index]) return true
            if (availableParts[index] < currentParts[index]) return false
        }
        return availableParts.size > currentParts.size
    }

    private fun String.versionParts(): List<Int> {
        return split(".").map { part ->
            part.toIntOrNull() ?: throw VersionParseFailure(this)
        }
    }

    private fun orderFirmwareFiles(files: List<String>): List<String> {
        return files.filterNot { file -> file.contains(SYSTEM_UPDATE_FILE) } + files.filter { file -> file.contains(SYSTEM_UPDATE_FILE) }
    }

    private fun String.optionalObjectValue(field: String): String? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val objectStart = indexOf('{', fieldIndex)
        if (objectStart < 0) return null
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.booleanValue(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" } ?: error("Missing boolean field $field in $this")
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

    private data class FirmwareDeviceInfo(
        val deviceFwVersion: String,
        val deviceModelName: String,
        val deviceHardwareCode: String
    )

    private class VersionParseFailure(version: String) : RuntimeException("Invalid firmware version $version")

    private companion object {
        const val SYSTEM_UPDATE_FILE = "SYSUPDAT.IMG"
        val FIRMWARE_UTILITY_POLICY_VECTORS = listOf(
            "sdk/firmware-update/device-info-basic.json",
            "sdk/firmware-update/device-info-zero-version.json",
            "sdk/firmware-update/version-comparison.json",
            "sdk/firmware-update/version-comparison-invalid.json",
            "sdk/firmware-update/file-ordering.json"
        )
        val REQUIRED_FIRMWARE_UTILITY_FAMILIES = listOf(
            "device-info-protobuf-mapping",
            "zero-version-preservation",
            "dotted-integer-version-comparison",
            "invalid-version-typed-parse-failure",
            "system-update-file-ordering-last",
            "platform-firmware-utility-vector-references",
            "compile-verification-gate"
        )
        const val FIRMWARE_VERSION_POLICY_DECISION = "compare-dot-separated-integer-components-with-longer-available-version-higher-only-after-equal-prefix"
        const val FIRMWARE_INVALID_VERSION_POLICY_DECISION = "current-platform-utilities-crash-or-throw-on-non-integer-components; KMP should replace this with a typed parse failure before public workflow migration"
        const val FIRMWARE_FILE_ORDERING_POLICY_DECISION = "move-any-file-containing-SYSUPDAT.IMG-after-all-other-files-and-preserve-relative-order-for-equal-priority-files"
    }
}
