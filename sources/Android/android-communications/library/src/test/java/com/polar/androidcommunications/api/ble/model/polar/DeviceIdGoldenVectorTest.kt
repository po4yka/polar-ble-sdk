package com.polar.androidcommunications.api.ble.model.polar

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.sdk.api.model.PolarDeviceUuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileReader

class DeviceIdGoldenVectorTest {

    @Test
    fun deviceIdGoldenVectors_matchAndroidBehavior() {
        val vectors = loadDeviceIdVectors()
        assertTrue("Expected device ID golden vectors", vectors.isNotEmpty())

        vectors.forEach { vector ->
            val input = vector.getAsJsonObject("input")
            val expected = vector.getAsJsonObject("expected")
            val deviceId = input.get("deviceId").asString
            val caseId = vector.get("id").asString

            if (expected.has("assembled")) {
                val assembled = BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(deviceId)
                assertEquals(caseId, expected.get("assembled").asString, assembled)

                if (expected.has("validAfterAssembly")) {
                    assertEquals(
                        caseId,
                        expected.get("validAfterAssembly").asBoolean,
                        BlePolarDeviceIdUtility.isValidDeviceId(assembled)
                    )
                }
            }

            if (expected.has("valid")) {
                assertEquals(
                    caseId,
                    expected.get("valid").asBoolean,
                    BlePolarDeviceIdUtility.isValidDeviceId(deviceId)
                )
            }

            if (expected.has("uuid")) {
                assertEquals(caseId, expected.get("uuid").asString, PolarDeviceUuid.fromDeviceId(deviceId))
            }

            if (expected.has("uuidError")) {
                try {
                    PolarDeviceUuid.fromDeviceId(deviceId)
                    throw AssertionError("$caseId expected ${expected.get("uuidError").asString}")
                } catch (error: IllegalArgumentException) {
                    assertEquals(
                        caseId,
                        "deviceId must be ${expected.get("expectedLength").asInt} characters long, was: ${expected.get("actualLength").asInt}",
                        error.message
                    )
                }
            }

            val androidExpectations = vector
                .getAsJsonObject("platformExpectations")
                ?.getAsJsonObject("android")
            if (androidExpectations?.has("assembled") == true) {
                assertEquals(
                    caseId,
                    androidExpectations.get("assembled").asString,
                    BlePolarDeviceIdUtility.assemblyFullPolarDeviceId(deviceId)
                )
            }
            if (androidExpectations?.has("isValidThrows") == true) {
                try {
                    BlePolarDeviceIdUtility.isValidDeviceId(deviceId)
                    throw AssertionError("$caseId expected ${androidExpectations.get("isValidThrows").asString}")
                } catch (error: NumberFormatException) {
                    assertEquals(caseId, "NumberFormatException", error::class.java.simpleName)
                }
            }
        }
    }

    @Test
    fun `device ID golden vectors follow neutral shared vector shape`() {
        loadDeviceIdVectors().forEach { vector ->
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
    fun `device ID readiness manifest is pinned before checksum and UUID shared ownership`() {
        val manifest = loadDeviceIdReadinessManifest()
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val policyPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val consumerTests = manifest.getAsJsonObject("consumerTests")

        assertEquals("device-id-readiness", manifest.get("id").asString)
        assertEquals("deviceIdReadiness", input.get("kind").asString)
        assertEquals(DEVICE_ID_READINESS_POLICY_VECTOR_PATHS, policyPaths)
        assertEquals(DEVICE_ID_READINESS_FAMILIES, requiredFamilies)
        assertEquals(DEVICE_ID_READINESS_FAMILIES, coveredFamilies)
        assertEquals(DEVICE_ID_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.polar.DeviceIdGoldenVectorTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("BlePolarDeviceIdUtilityTest", "PolarDeviceUuidTest", "PolarServiceClientUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.DeviceIdCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun loadDeviceIdVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/device-id")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" && !file.name.startsWith("identifier-") }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
            .filter { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString != "deviceIdReadiness" }
    }

    private fun loadDeviceIdReadinessManifest(): JsonObject {
        val manifestFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/device-id/device-id-readiness.json")
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

    private companion object {
        val DEVICE_ID_READINESS_POLICY_VECTOR_PATHS = listOf(
            "protocol/device-id/assemble-seven-digit-device-id.json",
            "protocol/device-id/assemble-six-digit-device-id.json",
            "protocol/device-id/assemble-zero-seven-digit-device-id.json",
            "protocol/device-id/empty-device-id-platform-difference.json",
            "protocol/device-id/identifier-bluetooth-address-android.json",
            "protocol/device-id/identifier-invalid-format.json",
            "protocol/device-id/identifier-uuid-string-ios.json",
            "protocol/device-id/invalid-checksum-device-id.json",
            "protocol/device-id/non-hex-device-id-platform-difference.json",
            "protocol/device-id/polar-device-uuid-invalid-length.json",
            "protocol/device-id/polar-device-uuid-valid.json",
            "protocol/device-id/valid-lowercase-device-id.json"
        )

        val DEVICE_ID_READINESS_FAMILIES = listOf(
            "checksum-width-6-assembly",
            "checksum-width-7-assembly",
            "zero-device-id-assembly",
            "valid-device-id-validation",
            "invalid-checksum-validation",
            "lowercase-device-id-validation",
            "empty-input-platform-decision",
            "non-hex-input-platform-decision",
            "uuid-conversion",
            "uuid-invalid-length-error",
            "identifier-invalid-format-error",
            "platform-specific-identifier-routing",
            "platform-device-id-vector-reference-gate",
            "compile-verification-gate"
        )
        const val DEVICE_ID_READINESS_COMMON_DECISION = "Device ID shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS device ID tests continue to reference the same vectors, checksum width 6 and 7 assembly, zero-value assembly, validation, lowercase acceptance, UUID conversion, invalid UUID length errors, invalid identifier rejection, current empty and non-hex platform decisions, platform-specific identifier routing, and compile verification remain explicit before production checksum or UUID logic moves."
    }
}
