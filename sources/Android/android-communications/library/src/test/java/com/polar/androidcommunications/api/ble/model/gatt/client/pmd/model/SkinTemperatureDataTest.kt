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

class SkinTemperatureDataTest {

    @Test
    fun `dataFromRawType0_parse-data_with-not-compresed-data`() {

        // 10 first values form the header part of the data frame. The rest is temperature data.
        // 0x07(decimal 7) = measurement type for skin temp
        // 0x40...0x0A = timestamp
        // 0x00(decimal 0) = uncompressed, raw data. Results 0 (false) for check compressed mask (0x80, decimal 128)
        // 0x00...0x41 the uncompressed skin temperature value
        val temperatureDataFrame = byteArrayOf(
            0x07.toByte(), 0x40.toByte(), 0xAE.toByte(), 0x21.toByte(), 0xAE.toByte(), 0x31.toByte(),
            0xB2.toByte(), 0xEE.toByte(), 0x0A.toByte(), 0x00.toByte(), 0x00.toByte(), 0x60.toByte(),
            0xEE.toByte(), 0x41.toByte()
        )
        val timeStamp = 787762911281000000uL
        val previousTimeStamp = 787762910281000000uL

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = temperatureDataFrame,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val temperatureData = SkinTemperatureData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(1, temperatureData.skinTemperatureSamples.size)
        Assert.assertEquals(29.796875f, temperatureData.skinTemperatureSamples[0].skinTemperature)
        Assert.assertEquals(timeStamp, temperatureData.skinTemperatureSamples[0].timeStamp)
    }

    @Test
    fun `dataFromCompressedType0_parse-data_with-compressed-data`() {

        // 10 first values form the header part of the data frame. The rest is temperature data.
        // 0x07(decimal 7) = measurement type for skin temp
        // 0x40...0x0A = timestamp
        // 0x80(decimal 128) = compressed data. Results 1 (true for check compressed mask (0x80, decimal 128)
        // 0xEC...0x00 the delta compressed skin temperature values
        val temperatureDataFrame = byteArrayOf(
            0x07.toByte(), 0x40.toByte(), 0xAE.toByte(), 0x21.toByte(), 0xAE.toByte(), 0x31.toByte(),
            0xB2.toByte(), 0xEE.toByte(), 0x0A.toByte(), 0x80.toByte(), 0xEC.toByte(), 0x51.toByte(),
            0xDC.toByte(), 0x41.toByte(), 0x03.toByte(), 0x02.toByte(), 0x00.toByte()
        )
        val dataFrame = PmdDataFrame(
            data = temperatureDataFrame,
            getPreviousTimeStamp = {  pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> 1000uL },
            getFactor = { 1.0f }
        ) { 13 }

        // Act
        val temperatureData = SkinTemperatureData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(3, temperatureData.skinTemperatureSamples.size)
        Assert.assertEquals(27.54f, temperatureData.skinTemperatureSamples[0].skinTemperature)
        Assert.assertEquals(27.54f, temperatureData.skinTemperatureSamples[1].skinTemperature)
        Assert.assertEquals(27.54f, temperatureData.skinTemperatureSamples[2].skinTemperature)
    }

    @Test
    fun skinTemperatureGoldenVectors_matchAndroidBehavior() {
        val vectors = loadSkinTemperatureVectors()
        Assert.assertTrue("Expected skin-temperature golden vectors", vectors.isNotEmpty())

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

            val skinTemperatureData = SkinTemperatureData.parseDataFromDataFrame(frame)

            Assert.assertEquals(caseId, expected.get("timeStamp").asLong.toULong(), frame.timeStamp)
            assertSkinTemperatureSamples(caseId, expected.getAsJsonArray("samples"), skinTemperatureData.skinTemperatureSamples)
        }
    }

    private fun assertParseError(caseId: String, expectedError: String, frame: PmdDataFrame) {
        when (expectedError) {
            "unsupportedFrame" -> Assert.assertThrows(caseId, Exception::class.java) {
                SkinTemperatureData.parseDataFromDataFrame(frame)
            }
            "malformedFrame" -> Assert.assertThrows(caseId, Exception::class.java) {
                SkinTemperatureData.parseDataFromDataFrame(frame)
            }
            else -> Assert.fail("$caseId has unsupported parse error expectation $expectedError")
        }
    }

    @Test
    fun `skin temperature golden vectors follow neutral KMP vector shape`() {
        loadSkinTemperatureVectors().forEach { vector ->
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
    fun `skin temperature readiness manifest is pinned before parser migration`() {
        val manifest = loadSkinTemperatureReadinessManifest()
        val id = manifest.get("id").asString
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val consumerTests = manifest.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }

        Assert.assertEquals("skin-temperature-readiness", id)
        Assert.assertEquals("skinTemperatureReadiness", input.get("kind").asString)
        Assert.assertEquals(id, SKIN_TEMPERATURE_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        Assert.assertEquals(id, SKIN_TEMPERATURE_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(id, SKIN_TEMPERATURE_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(id, listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.SkinTemperatureDataTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(id, listOf("SkinTemperatureDataTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(id, listOf("com.polar.sharedtest.SkinTemperatureParserCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun assertSkinTemperatureSamples(
        caseId: String,
        expectedSamples: JsonArray,
        actualSamples: List<SkinTemperatureData.SkinTemperatureSample>
    ) {
        Assert.assertEquals(caseId, expectedSamples.size(), actualSamples.size)
        expectedSamples.forEachIndexed { index, expectedSample ->
            val sample = expectedSample.asJsonObject
            val actualSample = actualSamples[index]
            Assert.assertEquals(caseId, sample.get("timeStamp").asLong.toULong(), actualSample.timeStamp)
            Assert.assertEquals(caseId, sample.get("skinTemperature").asFloat, actualSample.skinTemperature)
        }
    }

    private fun loadSkinTemperatureVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/sensors")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" && file.name.startsWith("skin-temperature-") }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
            .filter { vector -> vector.getAsJsonObject("input").get("kind")?.asString != "skinTemperatureReadiness" }
    }

    private fun loadSkinTemperatureReadinessManifest(): JsonObject {
        val vectorFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/sensors/skin-temperature-readiness.json")
        FileReader(vectorFile).use { reader ->
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

    private companion object {
        val SKIN_TEMPERATURE_READINESS_POLICY_VECTOR_PATHS = listOf(
            "protocol/sensors/skin-temperature-raw-type0-estimated-sample-rate.json",
            "protocol/sensors/skin-temperature-raw-type0-single-sample.json",
            "protocol/sensors/skin-temperature-raw-type0-truncated-sample-android-error.json",
            "protocol/sensors/skin-temperature-raw-type0-truncated-sample-ios-empty.json",
            "protocol/sensors/skin-temperature-raw-type1-unsupported.json"
        )

        val SKIN_TEMPERATURE_READINESS_FAMILIES = listOf(
            "raw-type0-ieee754-skin-temperature-parsing",
            "sample-rate-timestamp-estimation-policy",
            "unsupported-raw-frame-policy",
            "truncated-raw-sample-policy",
            "ios-empty-malformed-payload-deferral",
            "platform-skin-temperature-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}
