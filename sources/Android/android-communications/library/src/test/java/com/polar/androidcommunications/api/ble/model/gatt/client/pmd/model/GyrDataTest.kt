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

class GyrDataTest {

    @Test
    fun `process gyro compressed data type 0`() {
        // Arrange
        // HEX: 05 FF FF FF FF FF FF FF 7F 80
        // index                                                   data:
        // 0        type                                           05 (GYRO)
        // 1..9     timestamp                                      FF FF FF FF FF FF FF 7F
        val timeStamp = 9223372036854775807uL
        // 10       frame type                                     80 (compressed, type 0)

        val gyroDataFrameHeader = byteArrayOf(
            0x05.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F.toByte(),
            0x80.toByte(),
        )
        val previousTimeStamp = 100uL

        // HEX: EA FF 08 00 0D 00 03 01 DF 00
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  EA FF (0xFFEA = -22)
        // 2..3     Sample 0 - channel 1 (ref. sample)  08 00 (0x0008 = 8)
        // 4..5     Sample 0 - channel 2 (ref. sample)  0D 00 (0x000D = 13)
        // 6        Delta size                          03 (3 bit)
        // 7        Sample amount                       01 (1 samples)
        // 8..      Delta data                          DF (binary: 11 011 111) 00 (binary: 0000000 0)
        // Delta channel 0                              111b
        // Delta channel 1                              011b
        // Delta channel 2                              011b
        val expectedSamplesSize = 1 + 1 // reference sample + delta samples

        val sample0channel0 = -22.0f
        val sample0channel1 = 8.0f
        val sample0channel2 = 13.0f

        val sample1channel0 = sample0channel0 - 0x1
        val sample1channel1 = sample0channel1 + 0x3
        val sample1channel2 = sample0channel2 + 0x3

        val gyroDataFrameContent = byteArrayOf(
            0xEA.toByte(), 0xFF.toByte(),
            0x08.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x00.toByte(),
            0x03.toByte(), 0x01.toByte(), 0xDF.toByte(), 0x00.toByte()
        )

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = gyroDataFrameHeader + gyroDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val gyroData = GyrData.parseDataFromDataFrame(dataFrame)

        // Assert

        Assert.assertEquals(expectedSamplesSize, gyroData.gyrSamples.size)
        Assert.assertEquals(sample0channel0, gyroData.gyrSamples[0].x)
        Assert.assertEquals(sample0channel1, gyroData.gyrSamples[0].y)
        Assert.assertEquals(sample0channel2, gyroData.gyrSamples[0].z)

        Assert.assertEquals(sample1channel0, gyroData.gyrSamples[1].x)
        Assert.assertEquals(sample1channel1, gyroData.gyrSamples[1].y)
        Assert.assertEquals(sample1channel2, gyroData.gyrSamples[1].z)

        Assert.assertEquals(timeStamp, gyroData.gyrSamples.last().timeStamp)
    }

    @Test
    fun `process gyro compressed data type 1`() {
        // Arrange
        // HEX: 05 00 94 35 77 00 00 00 00 81
        // index                                                   data:
        // 0        type                                           05 (GYRO)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     81 (compressed, type 1)

        val gyroDataFrameHeader = byteArrayOf(
            0x05.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x81.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX: 00 00 80 3F 00 00 20 41 00 00 A0 41 1C 01 00 00 A0 01 00 00 08 CD CC EC 0D
        // index    type                                data
        // 10..13  Sample 0 - channel 0 (ref. sample)   00 00 80 3F (0x3F800000)
        // 14..17  Sample 0 - channel 1 (ref. sample)   00 00 20 41 (0x41200000)
        // 18..21  Sample 0 - channel 2 (ref. sample)   00 00 A0 41 (0x41A00000)
        // 22      Delta size                           1C (28 bit)
        // 23      Sample amount                        01 (1 samples)
        // 24..35 delta data: 00 00 A0 01 00 00 08 CD CC EC 0D
        //         Sample 1 - channel 0: (0x01A00000)
        //         Sample 1 - channel 1: (0x00800000)
        //         Sample 1 - channel 2  (0xFDECCCCD)
        val expectedSamplesSize = 1 + 1 // reference sample + delta samples
        val sample0channel0 = intBitsToFloat(0x3F800000)
        val sample0channel1 = intBitsToFloat(0x41200000)
        val sample0channel2 = intBitsToFloat(0x41A00000)

        val sample1channel0 = intBitsToFloat(0x3F800000 + 0x1A00000)
        val sample1channel1 = intBitsToFloat(0x41200000 + 0x0800000)
        val sample1channel2 = intBitsToFloat((0x41A00000 + 0xFDECCCCD).toInt())

        val gyroDataFrameContent = byteArrayOf(
            0x00.toByte(), 0x00.toByte(),
            0x80.toByte(), 0x3F.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x20.toByte(), 0x41.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xA0.toByte(), 0x41.toByte(), 0x1C.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0xA0.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x08.toByte(), 0xCD.toByte(),
            0xCC.toByte(), 0xEC.toByte(), 0x0D.toByte()
        )

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = gyroDataFrameHeader + gyroDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val gyroData = GyrData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSamplesSize, gyroData.gyrSamples.size)

        Assert.assertEquals(sample0channel0, gyroData.gyrSamples[0].x)
        Assert.assertEquals(sample0channel1, gyroData.gyrSamples[0].y)
        Assert.assertEquals(sample0channel2, gyroData.gyrSamples[0].z)

        Assert.assertEquals(sample1channel0, gyroData.gyrSamples[1].x)
        Assert.assertEquals(sample1channel1, gyroData.gyrSamples[1].y)
        Assert.assertEquals(sample1channel2, gyroData.gyrSamples[1].z)

        Assert.assertEquals(timeStamp, gyroData.gyrSamples[1].timeStamp)
    }

    @Test
    fun `process gyro compressed data type 1 with factor`() {
        // Arrange
        // HEX: 05 00 94 35 77 00 00 00 00 80
        // index                                                   data:
        // 0        type                                           05 (GYRO)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     80 (compressed, type 0)

        val gyroDataFrameHeader = byteArrayOf(
            0x05.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x81.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX: 00 00 80 3F 00 00 20 41 00 00 A0 41 1C 01 00 00 A0 01 00 00 08 CD CC EC 0D
        // index    type                                data
        // 10..13  Sample 0 - channel 0 (ref. sample)   00 00 80 3F (0x3F800000)
        // 14..17  Sample 0 - channel 1 (ref. sample)   00 00 20 41 (0x41200000)
        // 18..21  Sample 0 - channel 2 (ref. sample)   00 00 A0 41 (0x41A00000)
        // 22      Delta size                           1C (28 bit)
        // 23      Sample amount                        01 (1 samples)
        // 24..35 delta data: 00 00 A0 01 00 00 08 CD CC EC 0D
        //         Sample 1 - channel 0: (0x01A00000)
        //         Sample 1 - channel 1: (0x00800000)
        //         Sample 1 - channel 2  (0xFDECCCCD)

        val expectedSamplesSize = 1 + 1 // reference sample + delta samples
        val sample0channel0 = intBitsToFloat(0x3F800000)
        val sample0channel1 = intBitsToFloat(0x41200000)
        val sample0channel2 = intBitsToFloat(0x41A00000)

        val sample1channel0 = intBitsToFloat(0x3F800000 + 0x1A00000)
        val sample1channel1 = intBitsToFloat(0x41200000 + 0x0800000)
        val sample1channel2 = intBitsToFloat((0x41A00000 + 0xFDECCCCD).toInt())

        val gyroDataFrameContent = byteArrayOf(
            0x00.toByte(), 0x00.toByte(),
            0x80.toByte(), 0x3F.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x20.toByte(), 0x41.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xA0.toByte(), 0x41.toByte(), 0x1C.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0xA0.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x08.toByte(), 0xCD.toByte(),
            0xCC.toByte(), 0xEC.toByte(), 0x0D.toByte()
        )
        val factor = 0.5f
        val dataFrame = PmdDataFrame(
            data = gyroDataFrameHeader + gyroDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val gyroData = GyrData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSamplesSize, gyroData.gyrSamples.size)
        Assert.assertEquals(factor * sample0channel0, gyroData.gyrSamples[0].x)
        Assert.assertEquals(factor * sample0channel1, gyroData.gyrSamples[0].y)
        Assert.assertEquals(factor * sample0channel2, gyroData.gyrSamples[0].z)

        Assert.assertEquals(factor * sample1channel0, gyroData.gyrSamples[1].x)
        Assert.assertEquals(factor * sample1channel1, gyroData.gyrSamples[1].y)
        Assert.assertEquals(factor * sample1channel2, gyroData.gyrSamples[1].z)

        Assert.assertEquals(timeStamp, gyroData.gyrSamples[1].timeStamp)
    }

    @Test
    fun gyrGoldenVectors_matchAndroidBehavior() {
        val vectors = loadGyrVectors()
        Assert.assertTrue("Expected GYR golden vectors", vectors.isNotEmpty())

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

            val gyrData = GyrData.parseDataFromDataFrame(frame)

            Assert.assertEquals(caseId, expected.get("timeStamp").asLong.toULong(), frame.timeStamp)
            assertGyrSamples(caseId, expected.getAsJsonArray("samples"), gyrData.gyrSamples)
        }
    }

    private fun assertParseError(caseId: String, expectedError: String, frame: PmdDataFrame) {
        when (expectedError) {
            "unsupportedFrame" -> Assert.assertThrows(caseId, Exception::class.java) {
                GyrData.parseDataFromDataFrame(frame)
            }
            "malformedFrame" -> Assert.assertThrows(caseId, Exception::class.java) {
                GyrData.parseDataFromDataFrame(frame)
            }
            else -> Assert.fail("$caseId has unsupported parse error expectation $expectedError")
        }
    }

    @Test
    fun `gyr golden vectors follow neutral shared vector shape`() {
        loadGyrVectors().forEach { vector ->
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
    fun `GYR readiness manifest is pinned for shared parser ownership`() {
        val manifest = loadGyrReadinessManifest()
        val id = manifest.get("id").asString
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val consumerTests = manifest.getAsJsonObject("consumerTests")

        Assert.assertEquals("gyr-readiness", id)
        Assert.assertEquals("gyrReadiness", input.get("kind").asString)
        Assert.assertEquals(id, GYR_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        Assert.assertEquals(id, GYR_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(id, GYR_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.GyrDataTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("GyrDataTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.GyrParserCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun assertGyrSamples(
        caseId: String,
        expectedSamples: JsonArray,
        actualSamples: List<GyrData.GyrSample>
    ) {
        Assert.assertEquals(caseId, expectedSamples.size(), actualSamples.size)
        expectedSamples.forEachIndexed { index, expectedSample ->
            val sample = expectedSample.asJsonObject
            val actualSample = actualSamples[index]
            Assert.assertEquals(caseId, sample.get("timeStamp").asLong.toULong(), actualSample.timeStamp)
            Assert.assertEquals(caseId, sample.get("x").asFloat, actualSample.x)
            Assert.assertEquals(caseId, sample.get("y").asFloat, actualSample.y)
            Assert.assertEquals(caseId, sample.get("z").asFloat, actualSample.z)
        }
    }

    private fun loadGyrVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/sensors")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" && file.name.startsWith("gyr-") }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
            .filter { vector -> vector.getAsJsonObject("input").get("kind")?.asString != "gyrReadiness" }
    }

    private fun loadGyrReadinessManifest(): JsonObject {
        val vectorFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/sensors/gyr-readiness.json")
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
        val GYR_READINESS_POLICY_VECTOR_PATHS = listOf(
            "protocol/sensors/gyr-compressed-type0-factor-half.json",
            "protocol/sensors/gyr-compressed-type0-truncated-delta-header-android-error.json",
            "protocol/sensors/gyr-compressed-type0-truncated-delta-header-ios-reference-only.json",
            "protocol/sensors/gyr-compressed-type0-truncated-delta-payload-android-error.json",
            "protocol/sensors/gyr-compressed-type0-truncated-delta-payload-ios-reference-only.json",
            "protocol/sensors/gyr-compressed-type0-two-samples.json",
            "protocol/sensors/gyr-compressed-type1-android-only.json",
            "protocol/sensors/gyr-compressed-type2-unsupported.json",
            "protocol/sensors/gyr-raw-type0-unsupported.json"
        )

        val GYR_READINESS_FAMILIES = listOf(
            "compressed-type0-reference-delta-decoding",
            "compressed-type0-factor-scaling",
            "compressed-type0-timestamp-interpolation",
            "unsupported-raw-frame-policy",
            "unsupported-compressed-frame-policy",
            "android-compressed-type1-ownership",
            "truncated-compressed-delta-header-policy",
            "truncated-compressed-delta-payload-policy",
            "platform-gyr-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}
