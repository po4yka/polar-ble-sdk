package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileReader

internal class PmdActiveMeasurementTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `test offline recording is active`() {
        // Arrange
        val offlineRecActiveTest1: Byte = 0x80.toByte()
        val offlineRecActiveTest2: Byte = 0xBF.toByte()
        // Act
        val result1 = PmdActiveMeasurement.fromStatusResponse(offlineRecActiveTest1)
        val result2 = PmdActiveMeasurement.fromStatusResponse(offlineRecActiveTest2)
        // Assert
        Assert.assertEquals(result1, PmdActiveMeasurement.OFFLINE_MEASUREMENT_ACTIVE)
        Assert.assertEquals(result2, PmdActiveMeasurement.OFFLINE_MEASUREMENT_ACTIVE)
    }

    @Test
    fun `test online recording is active`() {
        // Arrange
        val onlineRecActiveTest1: Byte = 0x40.toByte()
        val onlineRecActiveTest2: Byte = 0x7F.toByte()
        // Act
        val result1 = PmdActiveMeasurement.fromStatusResponse(onlineRecActiveTest1)
        val result2 = PmdActiveMeasurement.fromStatusResponse(onlineRecActiveTest2)
        // Assert
        Assert.assertEquals(result1, PmdActiveMeasurement.ONLINE_MEASUREMENT_ACTIVE)
        Assert.assertEquals(result2, PmdActiveMeasurement.ONLINE_MEASUREMENT_ACTIVE)
    }

    @Test
    fun `test online and offline recording is active`() {
        // Arrange
        val onlineAndOfflineRecActiveTest1: Byte = 0xC0.toByte()
        val onlineAndOfflineRecActiveTest2: Byte = 0xFF.toByte()
        // Act
        val result1 = PmdActiveMeasurement.fromStatusResponse(onlineAndOfflineRecActiveTest1)
        val result2 = PmdActiveMeasurement.fromStatusResponse(onlineAndOfflineRecActiveTest2)
        // Assert
        Assert.assertEquals(result1, PmdActiveMeasurement.ONLINE_AND_OFFLINE_ACTIVE)
        Assert.assertEquals(result2, PmdActiveMeasurement.ONLINE_AND_OFFLINE_ACTIVE)
    }

    @Test
    fun `test no active recording`() {
        // Arrange
        val noRecordingsActiveTest1: Byte = 0x00.toByte()
        val noRecordingsActiveTest2: Byte = 0x3F.toByte()
        // Act
        val result1 = PmdActiveMeasurement.fromStatusResponse(noRecordingsActiveTest1)
        val result2 = PmdActiveMeasurement.fromStatusResponse(noRecordingsActiveTest2)
        // Assert
        Assert.assertEquals(result1, PmdActiveMeasurement.NO_ACTIVE_MEASUREMENT)
        Assert.assertEquals(result2, PmdActiveMeasurement.NO_ACTIVE_MEASUREMENT)
    }

    @Test
    fun pmdActiveMeasurementGoldenVectors_matchAndroidBehavior() {
        val vectors = loadActiveMeasurementVectors()
        Assert.assertTrue("Expected PMD active-measurement golden vectors", vectors.isNotEmpty())

        vectors.forEach { vector ->
            val caseId = vector.get("id").asString
            val input = vector.getAsJsonObject("input")
            val expected = vector.getAsJsonObject("expected")
            val responseByte = input.get("responseByte").asInt.toByte()
            val activeMeasurement = PmdActiveMeasurement.fromStatusResponse(responseByte)

            Assert.assertEquals(caseId, expected.get("activeMeasurementAndroid").asString, activeMeasurement.name)
            Assert.assertEquals(caseId, expected.get("activeBits").asInt, (responseByte.toInt() and 0xC0) ushr 6)
            Assert.assertEquals(caseId, expected.get("measurementBits").asInt, responseByte.toInt() and 0x3F)
        }
    }

    @Test
    fun `pmd active measurement golden vectors follow neutral shared vector shape`() {
        loadActiveMeasurementVectors().forEach { vector ->
            val id = vector.get("id")?.asString ?: "unknown-vector"

            Assert.assertTrue(id, vector.has("area"))
            Assert.assertTrue(id, vector.has("case"))
            Assert.assertTrue(id, vector.has("source"))
            Assert.assertTrue(id, vector.has("input"))
            Assert.assertTrue(id, vector.has("expected"))
            Assert.assertTrue(id, vector.has("platforms"))
            val platforms = vector.getAsJsonObject("platforms")
            Assert.assertTrue(id, platforms.has("android"))
            Assert.assertTrue(id, platforms.has("ios"))
            Assert.assertTrue(id, platforms.has("common"))
        }
    }

    @Test
    fun `PMD control point readiness manifest pins active measurement coverage before shared ownership`() {
        val manifest = loadPmdControlPointReadinessManifest()
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val policyPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val consumerTests = manifest.getAsJsonObject("consumerTests")

        Assert.assertEquals("pmd-control-point-readiness", manifest.get("id").asString)
        Assert.assertEquals("pmdControlPointReadiness", input.get("kind").asString)
        Assert.assertEquals(PMD_CONTROL_POINT_READINESS_POLICY_PATHS, policyPaths)
        Assert.assertEquals(PMD_CONTROL_POINT_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(PMD_CONTROL_POINT_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(PMD_CONTROL_POINT_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        Assert.assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePmdClientControlPointResponseTest", "com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdActiveMeasurementTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("BlePmdClientTest", "PmdActiveMeasurementTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.PmdControlPointCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private val PMD_CONTROL_POINT_READINESS_POLICY_PATHS = listOf(
        "protocol/pmd/active-measurement-no-active-ecg.json",
        "protocol/pmd/active-measurement-offline-acc.json",
        "protocol/pmd/active-measurement-online-offline-gyro.json",
        "protocol/pmd/active-measurement-online-offline-unknown.json",
        "protocol/pmd/active-measurement-online-ppg.json",
        "protocol/pmd/control-point-error-already-in-state-sdk-mode.json",
        "protocol/pmd/control-point-error-device-in-charger-ecg.json",
        "protocol/pmd/control-point-error-disk-full-offline-recording.json",
        "protocol/pmd/control-point-error-invalid-length-acc.json",
        "protocol/pmd/control-point-error-invalid-measurement-type-unknown.json",
        "protocol/pmd/control-point-error-invalid-mtu-acc.json",
        "protocol/pmd/control-point-error-invalid-number-of-channels-ppg.json",
        "protocol/pmd/control-point-error-invalid-op-code.json",
        "protocol/pmd/control-point-error-invalid-parameter-pressure.json",
        "protocol/pmd/control-point-error-invalid-range-gyro.json",
        "protocol/pmd/control-point-error-invalid-resolution-mag.json",
        "protocol/pmd/control-point-error-invalid-sample-rate-ppg.json",
        "protocol/pmd/control-point-error-invalid-state-stop-temperature.json",
        "protocol/pmd/control-point-error-not-supported-sdk-mode-settings.json",
        "protocol/pmd/control-point-short-empty-android-error.json",
        "protocol/pmd/control-point-short-response-only-android-error.json",
        "protocol/pmd/control-point-short-response-op-android-error.json",
        "protocol/pmd/control-point-short-response-op-type-android-error.json",
        "protocol/pmd/control-point-success-measurement-status.json",
        "protocol/pmd/control-point-success-minimal-no-more-byte.json",
        "protocol/pmd/control-point-success-settings-acc.json",
        "protocol/pmd/control-point-success-start-ppg-more.json",
        "protocol/pmd/control-point-success-stop-ecg.json"
    )

    private val PMD_CONTROL_POINT_READINESS_FAMILIES = listOf(
        "active-measurement-bit-decoding",
        "active-measurement-platform-state-names",
        "control-point-success-response-parsing",
        "control-point-more-flag-and-parameters",
        "control-point-settings-response",
        "control-point-measurement-status-response",
        "control-point-status-code-coverage",
        "unknown-measurement-type-policy",
        "short-payload-deterministic-error-policy",
        "platform-control-point-vector-reference-gate",
        "compile-verification-gate"
    )

    private val PMD_CONTROL_POINT_READINESS_COMMON_DECISION = "PMD control-point shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS control-point and active-measurement tests continue to reference the same vectors, active-measurement bit decoding and platform state names, success response parsing, more flag and parameter extraction, settings and measurement-status responses, all status-code mappings, unknown measurement type handling, deterministic short-payload error policy, and compile verification remain explicit before production response parsing moves."

    private fun loadActiveMeasurementVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/pmd")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" && file.name.startsWith("active-measurement-") }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
            .filter { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString != "pmdControlPointReadiness" }
    }

    private fun loadPmdControlPointReadinessManifest(): JsonObject {
        val manifestFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/pmd/control-point-readiness.json")
        FileReader(manifestFile).use { reader ->
            return JsonParser.parseReader(reader).asJsonObject
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
}
