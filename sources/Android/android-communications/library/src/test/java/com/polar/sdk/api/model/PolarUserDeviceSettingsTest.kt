package com.polar.sdk.api.model

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.shared.sdk.PolarUserDeviceSettingsModels
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbDeviceLocation
import fi.polar.remote.representation.protobuf.Types.PbSystemDateTime
import fi.polar.remote.representation.protobuf.Types.PbTime
import fi.polar.remote.representation.protobuf.UserDeviceSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbAutomaticMeasurementSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserDeviceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileReader

class PolarUserDeviceSettingsTest {

    @Test
    fun userDeviceSettingsGoldenVectors_matchAndroidBehavior() {
        val vectors = loadUserDeviceSettingsVectors()
        assertTrue("Expected user device settings golden vectors", vectors.isNotEmpty())

        vectors.forEach { vector ->
            val caseId = vector.get("id").asString
            val input = vector.getAsJsonObject("input")
            val expected = vector.platformExpected("android")

            if (input.has("proto") && expected.has("model")) {
                val proto = input.getAsJsonObject("proto").toUserDeviceSettingsProto()
                val model = PolarUserDeviceSettings().fromBytes(proto.toByteArray())
                assertUserDeviceSettingsModel(caseId, expected.getAsJsonObject("model"), model)
            }

            if (input.has("model") && expected.has("proto")) {
                val model = input.getAsJsonObject("model").toUserDeviceSettingsModel()
                val proto = model.toProto()
                assertUserDeviceSettingsProto(caseId, expected.getAsJsonObject("proto"), proto)
            }
        }
    }

    @Test
    fun `user device settings golden vectors follow neutral KMP vector shape`() {
        loadUserDeviceSettingsVectors().forEach { vector ->
            val id = vector.get("id")?.asString ?: "unknown-vector"

            assertTrue(id, vector.has("area"))
            assertTrue(id, vector.has("case"))
            assertTrue(id, vector.has("source"))
            assertTrue(id, vector.has("input"))
            assertTrue(id, vector.has("expected"))
            assertTrue(id, vector.has("platforms"))
            val platforms = vector.getAsJsonObject("platforms")
            assertTrue(id, platforms.has("android"))
            assertTrue(id, platforms.has("ios"))
            assertTrue(id, platforms.has("common"))
        }
    }

    @Test
    fun `user device settings model readiness manifest is pinned before model migration`() {
        val manifest = loadUserDeviceSettingsModelReadinessManifest()
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val consumerTests = manifest.getAsJsonObject("consumerTests")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val requiredBehaviorFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredBehaviorFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        assertEquals("user-device-settings-model-readiness", manifest.get("id").asString)
        assertEquals("userDeviceSettingsModelReadiness", input.get("kind").asString)
        assertEquals("compileVerifiedPreMigrationCharacterization", expected.get("migrationReadiness").asString)
        assertEquals(USER_DEVICE_SETTINGS_MODEL_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        val expectedBehaviorFamilies = listOf(
            "protobuf-presence-preservation",
            "nullable-omitted-optional-settings",
            "writable-settings-serialization",
            "encoder-owned-trusted-last-modified",
            "explicit-telemetry-write-policy",
            "platform-default-divergence",
            "mapped-protobuf-byte-codec",
            "platform-user-device-settings-vector-references",
            "compile-verification-gate"
        )
        assertEquals(expectedBehaviorFamilies, requiredBehaviorFamilies)
        assertEquals(expectedBehaviorFamilies, coveredBehaviorFamilies)
        assertEquals(listOf("com.polar.sdk.api.model.PolarUserDeviceSettingsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("PolarUserDeviceSettingsUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.UserDeviceSettingsCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun assertUserDeviceSettingsModel(caseId: String, expected: JsonObject, actual: PolarUserDeviceSettings) {
        assertNullableInt(caseId, expected, "deviceLocation", actual.deviceLocation)
        assertNullableBoolean(caseId, expected, "usbConnectionMode", actual.usbConnectionMode)
        assertNullableBoolean(caseId, expected, "automaticTrainingDetectionMode", actual.automaticTrainingDetectionMode)
        assertNullableInt(caseId, expected, "automaticTrainingDetectionSensitivity", actual.automaticTrainingDetectionSensitivity)
        assertNullableInt(caseId, expected, "minimumTrainingDurationSeconds", actual.minimumTrainingDurationSeconds)
        assertNullableBoolean(caseId, expected, "telemetryEnabled", actual.telemetryEnabled)
        assertNullableBoolean(caseId, expected, "autosFilesEnabled", actual.autosFilesEnabled)
    }

    private fun assertUserDeviceSettingsProto(caseId: String, expected: JsonObject, actual: PbUserDeviceSettings) {
        if (expected.has("deviceLocation")) {
            assertTrue(caseId, actual.hasGeneralSettings())
            assertEquals(caseId, expected.get("deviceLocation").asInt, actual.generalSettings.deviceLocation.number)
        }
        if (expected.has("hasLastModified")) {
            assertEquals(caseId, expected.get("hasLastModified").asBoolean, actual.hasLastModified())
        }
        if (expected.has("lastModifiedTrusted")) {
            assertTrue(caseId, actual.hasLastModified())
            assertEquals(caseId, expected.get("lastModifiedTrusted").asBoolean, actual.lastModified.trusted)
        }
        if (expected.has("usbConnectionMode")) {
            assertTrue(caseId, actual.hasUsbConnectionSettings())
            assertEquals(caseId, expected.get("usbConnectionMode").asString, actual.usbConnectionSettings.mode.name)
        }
        if (expected.has("automaticTrainingDetectionMode")) {
            assertTrue(caseId, actual.hasAutomaticMeasurementSettings())
            assertTrue(caseId, actual.automaticMeasurementSettings.hasAutomaticTrainingDetectionSettings())
            assertEquals(caseId, expected.get("automaticTrainingDetectionMode").asString, actual.automaticMeasurementSettings.automaticTrainingDetectionSettings.state.name)
        }
        if (expected.has("automaticTrainingDetectionSensitivity")) {
            assertEquals(caseId, expected.get("automaticTrainingDetectionSensitivity").asInt, actual.automaticMeasurementSettings.automaticTrainingDetectionSettings.sensitivity)
        }
        if (expected.has("minimumTrainingDurationSeconds")) {
            assertEquals(caseId, expected.get("minimumTrainingDurationSeconds").asInt, actual.automaticMeasurementSettings.automaticTrainingDetectionSettings.minimumTrainingDurationSeconds)
        }
        if (expected.has("autosFilesEnabled")) {
            assertTrue(caseId, actual.automaticMeasurementSettings.hasAutomaticOhrMeasurement())
            val sharedState = PolarUserDeviceSettingsModels.automaticMeasurementStateName(expected.get("autosFilesEnabled").asBoolean)
            val expectedState = PbAutomaticMeasurementSettings.PbAutomaticMeasurementState.valueOf(sharedState)
            assertEquals(caseId, expectedState, actual.automaticMeasurementSettings.automaticOhrMeasurement.state)
        }
        if (expected.has("hasTelemetryEnabled")) {
            if (expected.get("hasTelemetryEnabled").asBoolean) {
                assertTrue(caseId, actual.hasTelemetrySettings())
                assertTrue(caseId, actual.telemetrySettings.hasTelemetryEnabled())
            } else {
                assertFalse(caseId, actual.hasTelemetrySettings() && actual.telemetrySettings.hasTelemetryEnabled())
            }
        }
        if (expected.has("telemetryEnabled")) {
            assertEquals(caseId, expected.get("telemetryEnabled").asBoolean, actual.telemetrySettings.telemetryEnabled)
        }
    }

    private fun JsonObject.toUserDeviceSettingsProto(): PbUserDeviceSettings {
        val generalSettings = UserDeviceSettings.PbUserDeviceGeneralSettings.newBuilder()
        if (has("deviceLocation")) {
            generalSettings.deviceLocation = PbDeviceLocation.forNumber(get("deviceLocation").asInt)
        }

        val settings = PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(generalSettings)
            .setLastModified(testTimestamp())

        if (has("usbConnectionMode")) {
            settings.usbConnectionSettings = UserDeviceSettings.PbUsbConnectionSettings.newBuilder()
                .setMode(UserDeviceSettings.PbUsbConnectionSettings.PbUsbConnectionMode.valueOf(get("usbConnectionMode").asString))
                .build()
        }

        if (has("automaticTrainingDetectionMode") || has("automaticTrainingDetectionSensitivity") || has("minimumTrainingDurationSeconds") || has("autosFilesEnabled")) {
            val automaticMeasurement = UserDeviceSettings.PbUserAutomaticMeasurementSettings.newBuilder()
            if (has("automaticTrainingDetectionMode") || has("automaticTrainingDetectionSensitivity") || has("minimumTrainingDurationSeconds")) {
                val trainingDetection = UserDeviceSettings.PbAutomaticTrainingDetectionSettings.newBuilder()
                if (has("automaticTrainingDetectionMode")) {
                    trainingDetection.state = UserDeviceSettings.PbAutomaticTrainingDetectionSettings.PbAutomaticTrainingDetectionState.valueOf(get("automaticTrainingDetectionMode").asString)
                }
                if (has("automaticTrainingDetectionSensitivity")) {
                    trainingDetection.sensitivity = get("automaticTrainingDetectionSensitivity").asInt
                }
                if (has("minimumTrainingDurationSeconds")) {
                    trainingDetection.minimumTrainingDurationSeconds = get("minimumTrainingDurationSeconds").asInt
                }
                automaticMeasurement.automaticTrainingDetectionSettings = trainingDetection.build()
            }
            if (has("autosFilesEnabled")) {
                val sharedState = PolarUserDeviceSettingsModels.automaticMeasurementStateName(get("autosFilesEnabled").asBoolean)
                val ohrState = PbAutomaticMeasurementSettings.PbAutomaticMeasurementState.valueOf(sharedState)
                automaticMeasurement.automaticOhrMeasurement = PbAutomaticMeasurementSettings.newBuilder()
                    .setState(ohrState)
                    .build()
            }
            settings.automaticMeasurementSettings = automaticMeasurement.build()
        }

        if (has("telemetryEnabled")) {
            settings.telemetrySettings = UserDeviceSettings.PbUserDeviceTelemetrySettings.newBuilder()
                .setTelemetryEnabled(get("telemetryEnabled").asBoolean)
                .build()
        }

        return settings.build()
    }

    private fun testTimestamp(): PbSystemDateTime {
        return PbSystemDateTime.newBuilder()
            .setDate(
                PbDate.newBuilder()
                    .setYear(2026)
                    .setMonth(5)
                    .setDay(28)
            )
            .setTime(
                PbTime.newBuilder()
                    .setHour(12)
                    .setMinute(0)
                    .setSeconds(0)
                    .setMillis(0)
            )
            .setTrusted(true)
            .build()
    }

    private fun JsonObject.toUserDeviceSettingsModel(): PolarUserDeviceSettings {
        return PolarUserDeviceSettings(
            deviceLocation = optionalInt("deviceLocation"),
            usbConnectionMode = optionalBoolean("usbConnectionMode"),
            automaticTrainingDetectionMode = optionalBoolean("automaticTrainingDetectionMode"),
            automaticTrainingDetectionSensitivity = optionalInt("automaticTrainingDetectionSensitivity"),
            telemetryEnabled = optionalBoolean("telemetryEnabled"),
            minimumTrainingDurationSeconds = optionalInt("minimumTrainingDurationSeconds"),
            autosFilesEnabled = optionalBoolean("autosFilesEnabled")
        )
    }

    private fun assertNullableInt(caseId: String, expected: JsonObject, name: String, actual: Int?) {
        if (!expected.has(name)) {
            return
        }
        if (expected.get(name).isJsonNull) {
            assertEquals(caseId, null, actual)
        } else {
            assertEquals(caseId, expected.get(name).asInt, actual)
        }
    }

    private fun assertNullableBoolean(caseId: String, expected: JsonObject, name: String, actual: Boolean?) {
        if (!expected.has(name)) {
            return
        }
        if (expected.get(name).isJsonNull) {
            assertEquals(caseId, null, actual)
        } else {
            assertEquals(caseId, expected.get(name).asBoolean, actual)
        }
    }

    private fun JsonObject.optionalInt(name: String): Int? {
        return if (has(name) && !get(name).isJsonNull) get(name).asInt else null
    }

    private fun JsonObject.optionalBoolean(name: String): Boolean? {
        return if (has(name) && !get(name).isJsonNull) get(name).asBoolean else null
    }

    private fun JsonObject.platformExpected(platform: String): JsonObject {
        return if (has("platformExpectations")) {
            getAsJsonObject("platformExpectations").getAsJsonObject(platform)
        } else {
            getAsJsonObject("expected")
        }
    }

    private fun loadUserDeviceSettingsVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/user-device-settings")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
            .filter { vector -> vector.getAsJsonObject("input").get("kind")?.asString != "userDeviceSettingsModelReadiness" }
    }

    private fun loadUserDeviceSettingsModelReadinessManifest(): JsonObject {
        val file = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/user-device-settings/settings-model-readiness.json")
        return FileReader(file).use { reader ->
            JsonParser().parse(reader).asJsonObject
        }
    }

    private fun findRepositoryRoot(): File {
        val userDirectory = System.getProperty("user.dir") ?: error("user.dir is not set")
        var directory = File(userDirectory).absoluteFile
        while (true) {
            if (directory.resolve("testdata/golden-vectors").isDirectory) {
                return directory
            }
            directory = directory.parentFile ?: error("Could not find repository root from $userDirectory")
        }
    }

    private companion object {
        val USER_DEVICE_SETTINGS_MODEL_READINESS_POLICY_VECTOR_PATHS = listOf(
            "sdk/user-device-settings/from-proto-full-settings.json",
            "sdk/user-device-settings/from-proto-omitted-optional-settings.json",
            "sdk/user-device-settings/to-proto-telemetry-platform-difference.json",
            "sdk/user-device-settings/to-proto-writable-settings.json"
        )
    }
}
