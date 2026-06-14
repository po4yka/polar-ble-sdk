package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.MockKAnnotations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileReader

class BlePmdClientControlPointResponseTest {

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `success control point response for acc stream settings`() {
        //Arrange
        // HEX: F0 01 02 00 00 FF FF FF
        // index    type                                               data:
        // 0:       Response code                          size 1:     0xF0
        val expectedResponseCode = 0xF0.toByte()
        // 1:       Op code                                size 1:     0x01 (Request stream settings)
        val expectedOpCode = PmdControlPointCommandClientToService.GET_MEASUREMENT_SETTINGS
        // 2:       Measurement Type                       size 1:     0x02 (Acc)
        val expectedMeasurementType = 0x02.toByte()
        // 3:       Error Code                             size 1:     0x00 (Success)
        val expectedStatus =
            PmdControlPointResponse.PmdControlPointResponseCode.SUCCESS
        // 4:       More                                   size 1:     0x00 (No more)
        val expectedMore = false
        // 5..n:    Parameters                             size 3:     0xFF 0xFF 0xFF (some data)
        val expectedParamsSize = 3
        val expectedParamsContent = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

        val cpResponse = byteArrayOf(
            0xF0.toByte(),
            0x01.toByte(),
            0x02.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0xFF.toByte(),
            0xFF.toByte(),
            0xFF.toByte()
        )

        //Act
        val response = PmdControlPointResponse(cpResponse)

        //Assert
        assertEquals(expectedResponseCode, response.responseCode)
        assertEquals(expectedOpCode, response.opCode)
        assertEquals(expectedMeasurementType, response.measurementType)
        assertEquals(expectedStatus, response.status)
        assertEquals(expectedMore, response.more)
        assertEquals(expectedParamsSize, response.parameters.size)
        assertTrue(expectedParamsContent.contentEquals(response.parameters))
    }

    @Test
    fun `failing control point response for mag stream settings`() {
        //Arrange
        // HEX: F0 01 06 00 00
        // index    type                                               data:
        // 0:       Response code                          size 1:     0xF0
        val expectedResponseCode = 0xF0.toByte()
        // 1:       Op code                                size 1:     0x01 (Request stream settings)
        val expectedOpCode = PmdControlPointCommandClientToService.GET_MEASUREMENT_SETTINGS
        // 2:       Measurement Type                       size 1:     0x06 (mag)
        val expectedMeasurementType = 0x06.toByte()
        // 3:       Error Code                             size 1:     0x07 (Failure)
        val expectedStatus =
            PmdControlPointResponse.PmdControlPointResponseCode.ERROR_INVALID_RESOLUTION
        // 4:       More                                   size 1:     0x00 (No more)
        val expectedMore = false
        // 5..n:    Parameters                             size 3:     0xFF 0xFF 0xFF (some data)
        val expectedParamsSize = 0
        val expectedParamsContent = byteArrayOf()

        val cpResponse =
            byteArrayOf(0xF0.toByte(), 0x01.toByte(), 0x06.toByte(), 0x07.toByte(), 0x00.toByte())

        //Act
        val response = PmdControlPointResponse(cpResponse)

        //Assert
        assertEquals(expectedResponseCode, response.responseCode)
        assertEquals(expectedOpCode, response.opCode)
        assertEquals(expectedMeasurementType, response.measurementType)
        assertEquals(expectedStatus, response.status)
        assertEquals(expectedMore, response.more)
        assertEquals(expectedParamsSize, response.parameters.size)
        assertTrue(expectedParamsContent.contentEquals(response.parameters))
    }

    @Test
    fun controlPointResponseGoldenVectors_matchAndroidBehavior() {
        val vectors = loadControlPointVectors()
        assertFalse("Expected PMD control-point golden vectors", vectors.isEmpty())

        vectors.forEach { vector ->
            val caseId = vector.get("id").asString
            val input = vector.getAsJsonObject("input")
            val expected = vector.getAsJsonObject("expected")
            val androidExpectations = vector
                .getAsJsonObject("platformExpectations")
                ?.getAsJsonObject("android")
            val data = input.get("hex").asString.hexToByteArray()
            if (androidExpectations?.has("parseError") == true) {
                assertParseError(caseId, androidExpectations.get("parseError").asString, data)
                return@forEach
            }

            val response = PmdControlPointResponse(data)

            assertEquals(caseId, expected.get("responseCode").asInt, response.responseCode.toInt() and 0xFF)
            assertEquals(caseId, expected.get("opCode").asString, response.opCode.name)
            assertEquals(caseId, expected.get("opCodeValue").asInt, response.opCode.code)
            assertEquals(caseId, expected.get("measurementType").asInt, response.measurementType.toInt() and 0xFF)
            assertEquals(caseId, expected.get("status").asString, response.status.name)
            assertEquals(caseId, expected.get("statusValue").asInt, response.status.numVal)
            assertEquals(caseId, expected.get("more").asBoolean, response.more)
            assertEquals(caseId, expected.get("parametersHex").asString, response.parameters.toHexString())
        }
    }

    @Test
    fun `control point response golden vectors follow neutral shared vector shape`() {
        loadControlPointVectors().forEach { vector ->
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
    fun `PMD control point readiness manifest is pinned before response parser shared ownership`() {
        val manifest = loadPmdControlPointReadinessManifest()
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val policyPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val consumerTests = manifest.getAsJsonObject("consumerTests")

        assertEquals("pmd-control-point-readiness", manifest.get("id").asString)
        assertEquals("pmdControlPointReadiness", input.get("kind").asString)
        assertEquals(PMD_CONTROL_POINT_READINESS_POLICY_PATHS, policyPaths)
        assertEquals(PMD_CONTROL_POINT_READINESS_FAMILIES, requiredFamilies)
        assertEquals(PMD_CONTROL_POINT_READINESS_FAMILIES, coveredFamilies)
        assertEquals(PMD_CONTROL_POINT_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePmdClientControlPointResponseTest", "com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdActiveMeasurementTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("BlePmdClientTest", "PmdActiveMeasurementTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.PmdControlPointCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
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

    private fun assertParseError(caseId: String, expectedError: String, data: ByteArray) {
        when (expectedError) {
            "indexOutOfBounds" -> org.junit.Assert.assertThrows(caseId, IndexOutOfBoundsException::class.java) {
                PmdControlPointResponse(data)
            }
            else -> org.junit.Assert.fail("$caseId has unsupported parse error expectation $expectedError")
        }
    }

    private fun loadControlPointVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/pmd")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" && file.name.startsWith("control-point-") }
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

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}
