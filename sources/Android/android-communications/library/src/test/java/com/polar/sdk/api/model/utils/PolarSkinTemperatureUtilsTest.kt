package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.sdk.api.model.PolarSkinTemperatureDataSample
import com.polar.sdk.api.model.PolarSkinTemperatureResult
import com.polar.sdk.api.model.SkinTemperatureMeasurementType
import com.polar.sdk.api.model.SkinTemperatureSensorLocation
import com.polar.sdk.impl.utils.PolarSkinTemperatureUtils
import com.polar.services.datamodels.protobuf.TemperatureMeasurement
import com.polar.services.datamodels.protobuf.TemperatureMeasurement.TemperatureMeasurementSample
import com.polar.services.datamodels.protobuf.Types
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class PolarSkinTemperatureUtilsTest {

    @Test
    fun `readSkinTemperatureData() should return skin temperature data`() = runTest {
        // Arrange
        val mockClient = mockk<BlePsFtpClient>()
        var formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        formatter = formatter.withLocale(Locale.ENGLISH)
        val date: LocalDate = LocalDate.parse("20250101", formatter)

        val outputStream = ByteArrayOutputStream().apply {
            val proto = TemperatureMeasurement.TemperatureMeasurementPeriod.newBuilder()
                .setMeasurementType(Types.TemperatureMeasurementType.TM_SKIN_TEMPERATURE)
                .setSensorLocation(Types.SensorLocation.SL_DISTAL)
                .addTemperatureMeasurementSamples(
                    TemperatureMeasurementSample.newBuilder()
                        .setTemperatureCelsius(37.0f)
                        .setRecordingTimeDeltaMilliseconds(0L)
                        .build()
                ).addTemperatureMeasurementSamples(
                    TemperatureMeasurementSample.newBuilder()
                        .setTemperatureCelsius(37.6f)
                        .setRecordingTimeDeltaMilliseconds(1000L)
                        .build()
                )
                .build()
            proto.writeTo(this)
        }

        val expectedSkinTemperatureSamples: MutableList<PolarSkinTemperatureDataSample> = mutableListOf()
        expectedSkinTemperatureSamples.add(0, PolarSkinTemperatureDataSample(0, 37.0f))
        expectedSkinTemperatureSamples.add(1, PolarSkinTemperatureDataSample(1000, 37.6f))

        val expectedResult = PolarSkinTemperatureResult(
            "",
            SkinTemperatureSensorLocation.SL_DISTAL,
            SkinTemperatureMeasurementType.TM_SKIN_TEMPERATURE,
            expectedSkinTemperatureSamples
        )

        coEvery { mockClient.request(any()) } returns outputStream

        // Act
        val result = PolarSkinTemperatureUtils.readSkinTemperatureDataFromDayDirectory(mockClient, date)

        // Assert
        assertEquals(expectedResult, result)
    }

    @Test
    fun `readSkinTemperatureDataFromDayDirectory() returns null when an error is thrown`() = runTest {
        // Arrange
        val mockClient = mockk<BlePsFtpClient>()
        var formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        formatter = formatter.withLocale(Locale.ENGLISH)
        val date: LocalDate = LocalDate.parse("20250101", formatter)

        coEvery { mockClient.request(any()) } throws Throwable("No skin temperature data found")

        // Act
        val result = PolarSkinTemperatureUtils.readSkinTemperatureDataFromDayDirectory(mockClient, date)

        // Assert
        assertNull(result)
    }

    @Test
    fun `skin temperature public enum lookup delegates known values to shared model and preserves unknown null policy`() {
        assertEquals(SkinTemperatureMeasurementType.TM_SKIN_TEMPERATURE, SkinTemperatureMeasurementType.from(Types.TemperatureMeasurementType.TM_SKIN_TEMPERATURE.number))
        assertEquals(SkinTemperatureMeasurementType.TM_CORE_TEMPERATURE, SkinTemperatureMeasurementType.from(Types.TemperatureMeasurementType.TM_CORE_TEMPERATURE.number))
        assertNull(SkinTemperatureMeasurementType.from(Types.TemperatureMeasurementType.TM_UNKNOWN.number))
        assertNull(SkinTemperatureMeasurementType.from(99))

        assertEquals(SkinTemperatureSensorLocation.SL_DISTAL, SkinTemperatureSensorLocation.from(Types.SensorLocation.SL_DISTAL.number))
        assertEquals(SkinTemperatureSensorLocation.SL_PROXIMAL, SkinTemperatureSensorLocation.from(Types.SensorLocation.SL_PROXIMAL.number))
        assertNull(SkinTemperatureSensorLocation.from(Types.SensorLocation.SL_UNKNOWN.number))
        assertNull(SkinTemperatureSensorLocation.from(99))
    }

    @Test
    fun `skin temperature golden vectors map proto to public model`() = runTest {
        loadSkinTemperatureVectors().forEach { vector ->
            val caseId = vector.get("id").asString
            val mockClient = mockk<BlePsFtpClient>()
            val response = ByteArrayOutputStream().apply {
                buildProtoFromVector(vector.getAsJsonObject("input").getAsJsonObject("proto")).writeTo(this)
            }
            coEvery { mockClient.request(any()) } returns response

            val result = PolarSkinTemperatureUtils.readSkinTemperatureDataFromDayDirectory(mockClient, LocalDate.of(2025, 1, 1))

            assertSkinTemperatureResult(caseId, vector.getAsJsonObject("platformExpectations").getAsJsonObject("android"), result)
        }
    }

    @Test
    fun `skin temperature golden vectors follow neutral KMP vector shape`() {
        loadSkinTemperatureVectors().forEach { vector ->
            val id = vector.get("id").asString
            assertTrue(id, vector.has("area"))
            assertTrue(id, vector.has("case"))
            assertTrue(id, vector.has("source"))
            assertTrue(id, vector.has("input"))
            assertTrue(id, vector.has("expected"))
            assertTrue(id, vector.has("platforms"))
            assertTrue(id, vector.getAsJsonObject("input").has("proto"))
            val platforms = vector.getAsJsonObject("platforms")
            assertTrue(id, platforms.get("android").asBoolean)
            assertTrue(id, platforms.get("ios").asBoolean)
            assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    @Test
    fun `skin temperature domain readiness manifest is pinned before model migration`() {
        val readiness = loadSkinTemperatureDomainReadinessManifest()
        val input = readiness.getAsJsonObject("input")
        val expected = readiness.getAsJsonObject("expected")
        val consumerTests = readiness.getAsJsonObject("consumerTests")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        assertEquals("skin-temperature-domain-readiness", readiness.get("id").asString)
        assertEquals("skinTemperatureDomainReadiness", input.get("kind").asString)
        assertEquals(
            listOf(
                "sdk/skin-temperature/core-proximal-empty-samples.json",
                "sdk/skin-temperature/distal-skin-two-samples.json",
                "sdk/skin-temperature/unknown-enums-platform-policy.json"
            ),
            policyVectorPaths
        )
        val expectedFamilies = listOf(
            "source-device-id-ownership",
            "empty-sample-list-preservation",
            "sample-delta-preservation",
            "sample-temperature-preservation",
            "measurement-type-mapping",
            "sensor-location-mapping",
            "unknown-measurement-type-boundary",
            "unknown-sensor-location-boundary",
            "platform-skin-temperature-vector-reference-gate",
            "compile-verification-gate"
        )
        assertEquals(expectedFamilies, requiredFamilies)
        assertEquals(expectedFamilies, coveredFamilies)
        assertEquals(
            "Skin-temperature domain migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS skin-temperature tests continue to reference the same vectors, sourceDeviceId ownership remains explicit, empty sample lists and sample values are preserved, measurement and sensor-location mappings are covered, unknown enum behavior is handled at a typed boundary before public model exposure, and the shared tests are compile-verified.",
            expected.get("commonDecision").asString
        )
        assertEquals(
            listOf("com.polar.sdk.api.model.utils.PolarSkinTemperatureUtilsTest"),
            consumerTests.getAsJsonArray("android").map { it.asString }
        )
        assertEquals(
            listOf("PolarSkinTemperatureUnitTest"),
            consumerTests.getAsJsonArray("ios").map { it.asString }
        )
        assertEquals(
            listOf("com.polar.sharedtest.SkinTemperatureDomainCommonPolicyTest"),
            consumerTests.getAsJsonArray("commonPrototype").map { it.asString }
        )
    }

    private fun buildProtoFromVector(protoFields: JsonObject): TemperatureMeasurement.TemperatureMeasurementPeriod {
        val builder = TemperatureMeasurement.TemperatureMeasurementPeriod.newBuilder()
            .setSourceDeviceId(protoFields.get("sourceDeviceId").asString)
            .setMeasurementTypeValue(protoFields.get("measurementType").asInt)
            .setSensorLocationValue(protoFields.get("sensorLocation").asInt)
        protoFields.getAsJsonArray("samples").forEach { element ->
            val sample = element.asJsonObject
            builder.addTemperatureMeasurementSamples(
                TemperatureMeasurementSample.newBuilder()
                    .setRecordingTimeDeltaMilliseconds(sample.get("recordingTimeDeltaMs").asLong)
                    .setTemperatureCelsius(sample.get("temperature").asFloat)
                    .build()
            )
        }
        return builder.build()
    }

    private fun assertSkinTemperatureResult(caseId: String, expected: JsonObject, actual: PolarSkinTemperatureResult?) {
        assertEquals(caseId, expected.get("deviceId").asString, actual?.deviceId)
        if (expected.get("sensorLocation").isJsonNull) {
            assertNull("$caseId sensorLocation", actual?.sensorLocation)
        } else {
            assertEquals("$caseId sensorLocation", expected.get("sensorLocation").asString, actual?.sensorLocation?.name)
        }
        if (expected.get("measurementType").isJsonNull) {
            assertNull("$caseId measurementType", actual?.measurementType)
        } else {
            assertEquals("$caseId measurementType", expected.get("measurementType").asString, actual?.measurementType?.name)
        }
        val expectedSamples = expected.getAsJsonArray("samples")
        assertEquals("$caseId sample count", expectedSamples.size(), actual?.skinTemperatureList?.size)
        expectedSamples.forEachIndexed { index, element ->
            val expectedSample = element.asJsonObject
            val actualSample = actual?.skinTemperatureList?.get(index)
            assertEquals("$caseId sample $index time", expectedSample.get("recordingTimeDeltaMs").asLong, actualSample?.recordingTimeDeltaMs)
            assertEquals("$caseId sample $index temp", expectedSample.get("temperature").asFloat, actualSample?.temperature ?: Float.NaN, 0.00001f)
        }
    }

    private fun loadSkinTemperatureVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/skin-temperature")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
            .filterNot { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString == "skinTemperatureDomainReadiness" }
    }

    private fun loadSkinTemperatureDomainReadinessManifest(): JsonObject {
        val vectorFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/skin-temperature/skin-temperature-domain-readiness.json")
        FileReader(vectorFile).use { reader ->
            return JsonParser().parse(reader).asJsonObject
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
