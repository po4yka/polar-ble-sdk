package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals

class UserDeviceSettingsCommonPolicyTest {
    @Test
    fun userDeviceSettingsGoldenVectorsDefineExecutableCommonPresenceAndWritePolicy() {
        USER_DEVICE_SETTINGS_VECTORS.forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val caseId = vector.stringValue("id")
            val input = vector.objectValue("input")
            val expected = vector.objectValue("expected")

            if (input.optionalObjectValue("proto") != null) {
                val proto = input.objectValue("proto")
                val model = parseSettings(proto)
                val expectedModel = expected.optionalObjectValue("model") ?: vector.objectValue("platformExpectations").objectValue("commonDecision").objectValue("model")
                assertModel(expectedModel, model, caseId)
                expected.optionalStringValue("commonDecision")?.let { decision ->
                    assertEquals(true, decision.contains("preserve protobuf field presence"), caseId)
                    assertEquals("preserve-protobuf-presence", expectedModel.stringValue("omittedOptionalPolicy"), caseId)
                }
            } else {
                val model = parseModel(input.objectValue("model"))
                val proto = serializeSettings(model)
                val expectedProto = expected.optionalObjectValue("proto") ?: vector.objectValue("platformExpectations").objectValue("commonDecision").objectValue("proto")
                assertProto(expectedProto, proto, caseId)
                expected.optionalStringValue("commonDecision")?.let { decision ->
                    assertEquals(true, decision.contains("write telemetrySettings only when telemetryEnabled is explicitly present"), caseId)
                    assertEquals("write-explicit-telemetry", expectedProto.stringValue("telemetryWritePolicy"), caseId)
                }
            }
        }
    }

    @Test
    fun userDeviceSettingsModelReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/user-device-settings/settings-model-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val policyVectorPaths = input.stringArrayValue("policyVectorPaths")
        val requiredBehaviorFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredBehaviorFamilies = expected.stringArrayValue("coveredBehaviorFamilies")

        assertEquals("user-device-settings-model-readiness", manifest.stringValue("id"))
        assertEquals("userDeviceSettingsModelReadiness", input.stringValue("kind"))
        assertEquals("compileVerifiedPreMigrationCharacterization", expected.stringValue("migrationReadiness"))
        assertEquals(REQUIRED_USER_DEVICE_SETTINGS_MODEL_FAMILIES, requiredBehaviorFamilies)
        assertEquals(REQUIRED_USER_DEVICE_SETTINGS_MODEL_FAMILIES, coveredBehaviorFamilies)
        assertEquals(USER_DEVICE_SETTINGS_VECTORS, policyVectorPaths)
        assertEquals(listOf("com.polar.sdk.api.model.PolarUserDeviceSettingsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarUserDeviceSettingsUtilsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.UserDeviceSettingsCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun parseSettings(proto: String): UserDeviceSettings {
        return UserDeviceSettings(
            deviceLocation = proto.optionalIntValue("deviceLocation"),
            usbConnectionMode = proto.optionalOnOffBoolean("usbConnectionMode"),
            automaticTrainingDetectionMode = proto.optionalOnOffBoolean("automaticTrainingDetectionMode"),
            automaticTrainingDetectionSensitivity = proto.optionalIntValue("automaticTrainingDetectionSensitivity"),
            minimumTrainingDurationSeconds = proto.optionalIntValue("minimumTrainingDurationSeconds"),
            telemetryEnabled = proto.optionalBooleanValue("telemetryEnabled"),
            autosFilesEnabled = proto.optionalBooleanValue("autosFilesEnabled")
        )
    }

    private fun parseModel(model: String): UserDeviceSettings {
        return UserDeviceSettings(
            deviceLocation = model.optionalIntValue("deviceLocation"),
            usbConnectionMode = model.optionalBooleanValue("usbConnectionMode"),
            automaticTrainingDetectionMode = model.optionalBooleanValue("automaticTrainingDetectionMode"),
            automaticTrainingDetectionSensitivity = model.optionalIntValue("automaticTrainingDetectionSensitivity"),
            minimumTrainingDurationSeconds = model.optionalIntValue("minimumTrainingDurationSeconds"),
            telemetryEnabled = model.optionalBooleanValue("telemetryEnabled"),
            autosFilesEnabled = model.optionalBooleanValue("autosFilesEnabled")
        )
    }

    private fun serializeSettings(model: UserDeviceSettings): SerializedSettings {
        return SerializedSettings(
            deviceLocation = model.deviceLocation,
            hasLastModified = true,
            lastModifiedTrusted = true,
            usbConnectionMode = model.usbConnectionMode?.toOnOff(),
            automaticTrainingDetectionMode = model.automaticTrainingDetectionMode?.toOnOff(),
            automaticTrainingDetectionSensitivity = model.automaticTrainingDetectionSensitivity,
            minimumTrainingDurationSeconds = model.minimumTrainingDurationSeconds,
            hasTelemetryEnabled = model.telemetryEnabled != null,
            telemetryEnabled = model.telemetryEnabled,
            autosFilesEnabled = model.autosFilesEnabled
        )
    }

    private fun assertModel(expected: String, actual: UserDeviceSettings, caseId: String) {
        assertEquals(expected.optionalIntValue("deviceLocation"), actual.deviceLocation, "$caseId deviceLocation")
        assertEquals(expected.optionalBooleanOrNull("usbConnectionMode"), actual.usbConnectionMode, "$caseId usbConnectionMode")
        assertEquals(expected.optionalBooleanOrNull("automaticTrainingDetectionMode"), actual.automaticTrainingDetectionMode, "$caseId automaticTrainingDetectionMode")
        assertEquals(expected.optionalIntOrNull("automaticTrainingDetectionSensitivity"), actual.automaticTrainingDetectionSensitivity, "$caseId sensitivity")
        assertEquals(expected.optionalIntOrNull("minimumTrainingDurationSeconds"), actual.minimumTrainingDurationSeconds, "$caseId duration")
        assertEquals(expected.optionalBooleanOrNull("telemetryEnabled"), actual.telemetryEnabled, "$caseId telemetry")
        assertEquals(expected.optionalBooleanOrNull("autosFilesEnabled"), actual.autosFilesEnabled, "$caseId autos")
    }

    private fun assertProto(expected: String, actual: SerializedSettings, caseId: String) {
        assertEquals(expected.optionalIntValue("deviceLocation"), actual.deviceLocation, "$caseId deviceLocation")
        assertEquals(expected.booleanValue("hasLastModified"), actual.hasLastModified, "$caseId hasLastModified")
        assertEquals(expected.booleanValue("lastModifiedTrusted"), actual.lastModifiedTrusted, "$caseId trusted")
        expected.optionalStringValue("usbConnectionMode")?.let { assertEquals(it, actual.usbConnectionMode, "$caseId usb") }
        expected.optionalStringValue("automaticTrainingDetectionMode")?.let { assertEquals(it, actual.automaticTrainingDetectionMode, "$caseId autos mode") }
        expected.optionalIntValue("automaticTrainingDetectionSensitivity")?.let { assertEquals(it, actual.automaticTrainingDetectionSensitivity, "$caseId sensitivity") }
        expected.optionalIntValue("minimumTrainingDurationSeconds")?.let { assertEquals(it, actual.minimumTrainingDurationSeconds, "$caseId duration") }
        assertEquals(expected.optionalBooleanValue("hasTelemetryEnabled") ?: false, actual.hasTelemetryEnabled, "$caseId hasTelemetry")
        expected.optionalBooleanValue("telemetryEnabled")?.let { assertEquals(it, actual.telemetryEnabled, "$caseId telemetry") }
        expected.optionalBooleanValue("autosFilesEnabled")?.let { assertEquals(it, actual.autosFilesEnabled, "$caseId autos") }
    }

    private fun Boolean.toOnOff(): String {
        return if (this) "ON" else "OFF"
    }

    private fun String.optionalObjectValue(field: String): String? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val objectStart = indexOf('{', fieldIndex)
        if (objectStart < 0) return null
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.optionalIntValue(field: String): Int? {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toInt()
    }

    private fun String.optionalIntOrNull(field: String): Int? {
        return if (hasNull(field)) null else optionalIntValue(field)
    }

    private fun String.optionalBooleanValue(field: String): Boolean? {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" }
    }

    private fun String.optionalBooleanOrNull(field: String): Boolean? {
        return if (hasNull(field)) null else optionalBooleanValue(field)
    }

    private fun String.booleanValue(field: String): Boolean {
        return optionalBooleanValue(field) ?: error("Missing boolean field $field in $this")
    }

    private fun String.stringArrayValue(field: String): List<String> {
        val values = Regex("\"$field\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL)
            .find(this)
            ?.groupValues
            ?.get(1)
            ?: error("Missing array field $field in $this")
        return Regex("\"([^\"]+)\"").findAll(values).map { match -> match.groupValues[1] }.toList()
    }

    private fun String.optionalOnOffBoolean(field: String): Boolean? {
        return optionalStringValue(field)?.let { value ->
            when (value) {
                "ON" -> true
                "OFF" -> false
                else -> error("Unexpected ON/OFF value $value")
            }
        }
    }

    private fun String.hasNull(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*null").containsMatchIn(this)
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

    private data class UserDeviceSettings(
        val deviceLocation: Int?,
        val usbConnectionMode: Boolean?,
        val automaticTrainingDetectionMode: Boolean?,
        val automaticTrainingDetectionSensitivity: Int?,
        val minimumTrainingDurationSeconds: Int?,
        val telemetryEnabled: Boolean?,
        val autosFilesEnabled: Boolean?
    )

    private data class SerializedSettings(
        val deviceLocation: Int?,
        val hasLastModified: Boolean,
        val lastModifiedTrusted: Boolean,
        val usbConnectionMode: String?,
        val automaticTrainingDetectionMode: String?,
        val automaticTrainingDetectionSensitivity: Int?,
        val minimumTrainingDurationSeconds: Int?,
        val hasTelemetryEnabled: Boolean,
        val telemetryEnabled: Boolean?,
        val autosFilesEnabled: Boolean?
    )

    private companion object {
        val REQUIRED_USER_DEVICE_SETTINGS_MODEL_FAMILIES = listOf(
            "protobuf-presence-preservation",
            "nullable-omitted-optional-settings",
            "writable-settings-serialization",
            "encoder-owned-trusted-last-modified",
            "explicit-telemetry-write-policy",
            "platform-default-divergence",
            "platform-user-device-settings-vector-references",
            "compile-verification-gate"
        )
        val USER_DEVICE_SETTINGS_VECTORS = listOf(
            "sdk/user-device-settings/from-proto-full-settings.json",
            "sdk/user-device-settings/from-proto-omitted-optional-settings.json",
            "sdk/user-device-settings/to-proto-telemetry-platform-difference.json",
            "sdk/user-device-settings/to-proto-writable-settings.json"
        )
    }
}
