package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.FileReader
import java.lang.Float.intBitsToFloat

class TemperatureDataTest {

    @Test
    fun `process compressed temperature data type 0`() {
        // Arrange
        // HEX: 0C 00 94 35 77 00 00 00 00 80
        // index                                                   data:
        // 0        type                                           0C (Temperature)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     80 (compressed, type 0)
        val temperatureDataFrameHeader = byteArrayOf(
            0x0C.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX: EC 51 DC 41 03 02 00
        // index    type                                data
        // 0..3     Sample 1  (ref. sample)             EC 51 DC 41 (0x41DC51EC)
        // 4        Delta size                          03 (3 bits)
        // 5        Sample amount                       01 (2 samples)
        // 6..      Delta data                          00
        // Delta sample 1                               000b (0)
        // Delta sample 2                               000b (0)
        val expectedSamplesSize = 1 + 2 // reference sample + delta samples
        val sample0 = intBitsToFloat(0x41DC51EC)
        val sample1 = intBitsToFloat(0x41DC51EC + 0x0)
        val sample2 = intBitsToFloat(0x41DC51EC + 0x0)

        val temperatureDataFrameContent = byteArrayOf(
            0xEC.toByte(), 0x51.toByte(), 0xDC.toByte(), 0x41.toByte(), 0x03.toByte(), 0x02.toByte(), 0x00.toByte()
        )
        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = temperatureDataFrameHeader + temperatureDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val temperatureData = TemperatureData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSamplesSize, temperatureData.temperatureSamples.size)
        Assert.assertEquals(sample0, temperatureData.temperatureSamples[0].temperature)
        Assert.assertEquals(sample1, temperatureData.temperatureSamples[1].temperature)
        Assert.assertEquals(sample2, temperatureData.temperatureSamples[2].temperature)
        Assert.assertEquals(timeStamp, temperatureData.temperatureSamples[2].timeStamp)
    }

    @Test
    fun `process raw temperature data type 0`() {
        // Arrange
        // HEX: 0C 00 94 35 77 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0C (Temperature)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     00 (raw, type 0)
        val temperatureDataFrameHeader = byteArrayOf(
            0x0C.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX:   F6 28 C0 41
        // index    type                                data
        // 0..3     Temperature data                    F6 28 C0 41 (0x41C028F6)
        val expectedSamplesSize = 1
        val sample0 = intBitsToFloat(0x41C028F6)
        val temperatureDataFrameContent = byteArrayOf(0xF6.toByte(), 0x28.toByte(), 0xC0.toByte(), 0x41.toByte())
        val factor = 1.0f

        val dataFrame = PmdDataFrame(
            data = temperatureDataFrameHeader + temperatureDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val temperatureData = TemperatureData.parseDataFromDataFrame(dataFrame)
        // Assert
        Assert.assertEquals(expectedSamplesSize, temperatureData.temperatureSamples.size)
        Assert.assertEquals(sample0, temperatureData.temperatureSamples[0].temperature)
        Assert.assertEquals(timeStamp, temperatureData.temperatureSamples[0].timeStamp)
    }

    @Test
    fun temperatureGoldenVectors_matchAndroidBehavior() {
        val vectors = loadTemperatureVectors()
        Assert.assertTrue("Expected temperature golden vectors", vectors.isNotEmpty())

        vectors.forEach { vector ->
            val caseId = vector.get("id").asString
            if (vector.getAsJsonObject("platforms")?.get("android")?.asBoolean == false) {
                return@forEach
            }
            val input = vector.getAsJsonObject("input")
            val expected = vector.getAsJsonObject("expected")
            val frame = PmdDataFrame(
                data = input.get("dataFrameHex").asString.hexToByteArray(),
                getPreviousTimeStamp = { _, _ -> input.get("previousTimeStamp").asLong.toULong() },
                getFactor = { input.get("factor").asFloat },
                getSampleRate = { input.get("sampleRate").asInt }
            )

            if (expected.has("parseError")) {
                assertParseError(caseId, expected.get("parseError").asString, frame)
                Assert.assertEquals(caseId, expected.get("timeStamp").asLong.toULong(), frame.timeStamp)
                return@forEach
            }

            val temperatureData = TemperatureData.parseDataFromDataFrame(frame)

            Assert.assertEquals(caseId, expected.get("timeStamp").asLong.toULong(), frame.timeStamp)
            assertTemperatureSamples(caseId, expected.getAsJsonArray("samples"), temperatureData.temperatureSamples)
        }
    }

    private fun assertParseError(caseId: String, expectedError: String, frame: PmdDataFrame) {
        when (expectedError) {
            "unsupportedFrame" -> Assert.assertThrows(caseId, Exception::class.java) {
                TemperatureData.parseDataFromDataFrame(frame)
            }
            "malformedFrame" -> Assert.assertThrows(caseId, Exception::class.java) {
                TemperatureData.parseDataFromDataFrame(frame)
            }
            else -> Assert.fail("$caseId has unsupported parse error expectation $expectedError")
        }
    }

    @Test
    fun `temperature golden vectors follow neutral KMP vector shape`() {
        loadTemperatureVectors().forEach { vector ->
            val id = vector.get("id").asString
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
    fun `pressure temperature readiness manifest is pinned before scalar parser migration`() {
        val manifest = loadPressureTemperatureReadinessManifest()
        val id = manifest.get("id").asString
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val consumerTests = manifest.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }

        Assert.assertEquals("pressure-temperature-readiness", id)
        Assert.assertEquals("pressureTemperatureReadiness", input.get("kind").asString)
        Assert.assertEquals(id, PRESSURE_TEMPERATURE_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        Assert.assertEquals(id, PRESSURE_TEMPERATURE_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(id, PRESSURE_TEMPERATURE_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(id, listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PressureDataTest", "com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.TemperatureDataTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(id, listOf("TemperatureDataTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(id, listOf("com.polar.sharedtest.PressureTemperatureParserCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun assertTemperatureSamples(
        caseId: String,
        expectedSamples: JsonArray,
        actualSamples: List<TemperatureData.TemperatureSample>
    ) {
        Assert.assertEquals(caseId, expectedSamples.size(), actualSamples.size)
        expectedSamples.forEachIndexed { index, expectedSample ->
            val sample = expectedSample.asJsonObject
            val actualSample = actualSamples[index]
            Assert.assertEquals(caseId, sample.get("timeStamp").asLong.toULong(), actualSample.timeStamp)
            Assert.assertEquals(caseId, sample.get("temperature").asFloat, actualSample.temperature)
        }
    }

    private fun loadTemperatureVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/sensors")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" && file.name.startsWith("temperature-") }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
    }

    private fun loadPressureTemperatureReadinessManifest(): JsonObject {
        val vectorFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/sensors/pressure-temperature-readiness.json")
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

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private companion object {
        val PRESSURE_TEMPERATURE_READINESS_POLICY_VECTOR_PATHS = listOf(
            "protocol/sensors/pressure-compressed-type0-android-factor-half.json",
            "protocol/sensors/pressure-raw-type0-negative-single-sample.json",
            "protocol/sensors/pressure-raw-type0-single-sample.json",
            "protocol/sensors/pressure-raw-type0-truncated-sample-android-error.json",
            "protocol/sensors/pressure-raw-type1-unsupported.json",
            "protocol/sensors/temperature-compressed-type0-flat-deltas-android-two-samples.json",
            "protocol/sensors/temperature-compressed-type0-flat-deltas.json",
            "protocol/sensors/temperature-raw-type0-ieee754-boundaries.json",
            "protocol/sensors/temperature-raw-type0-negative-single-sample.json",
            "protocol/sensors/temperature-raw-type0-single-sample.json",
            "protocol/sensors/temperature-raw-type0-truncated-sample-android-error.json",
            "protocol/sensors/temperature-raw-type1-unsupported.json"
        )

        val PRESSURE_TEMPERATURE_READINESS_FAMILIES = listOf(
            "pressure-raw-type0-ieee754-parsing",
            "temperature-raw-type0-ieee754-parsing",
            "negative-and-boundary-float-values",
            "raw-type0-timestamp-interpolation",
            "unsupported-raw-frame-policy",
            "unsupported-compressed-frame-policy",
            "truncated-raw-sample-policy",
            "compressed-pressure-one-channel-indexing-deferral",
            "compressed-temperature-sample-count-deferral",
            "platform-pressure-temperature-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}
