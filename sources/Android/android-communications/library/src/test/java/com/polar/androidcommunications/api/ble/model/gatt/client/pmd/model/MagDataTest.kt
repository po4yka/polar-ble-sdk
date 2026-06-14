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

class MagDataTest {

    @Test
    fun calibrationStatusLookupDelegatesKnownIdsToSharedModelAndPreservesNotAvailableFallback() {
        Assert.assertEquals(MagData.CalibrationStatus.NOT_AVAILABLE, MagData.CalibrationStatus.getById(-1))
        Assert.assertEquals(MagData.CalibrationStatus.UNKNOWN, MagData.CalibrationStatus.getById(0))
        Assert.assertEquals(MagData.CalibrationStatus.POOR, MagData.CalibrationStatus.getById(1))
        Assert.assertEquals(MagData.CalibrationStatus.OK, MagData.CalibrationStatus.getById(2))
        Assert.assertEquals(MagData.CalibrationStatus.GOOD, MagData.CalibrationStatus.getById(3))
        Assert.assertEquals(MagData.CalibrationStatus.NOT_AVAILABLE, MagData.CalibrationStatus.getById(99))
    }

    @Test
    fun `process magnetometer compressed data type 0`() {
        // Arrange
        // HEX: 06 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           06 (MAG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     80 (compressed, type 0)

        val magDataFrameHeader = byteArrayOf(
            0x06.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
        )
        val previousTimeStamp = 100uL

        // HEX: E2 E6 FA 15 49 0A 06 01 7F 20 FC
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  E2 E6 (0xE6E2 = -6430)
        // 1..2     Sample 0 - channel 1 (ref. sample)  FA 15 (0x15FA = 5626)
        // 3..4     Sample 0 - channel 2 (ref. sample)  49 0A (0x0A49 = 2633)
        // 5        Delta size                          06 (6 bit)
        // 6        Sample amount                       01 (1 samples)
        // 7..      Delta data                          7F (binary: 01 111111) 20 (binary: 0010 0000) FC (binary: 111111 00)
        // Delta channel 0                              111111b
        // Delta channel 1                              000001b
        // Delta channel 2                              000010b
        val expectedSamplesSize = 1 + 1 // reference sample + delta samples
        val magDataFrameContent = byteArrayOf(
            0xE2.toByte(), 0xE6.toByte(), 0xFA.toByte(), 0x15.toByte(), 0x49.toByte(), 0x0A.toByte(),
            0x06.toByte(), 0x01.toByte(), 0x7F.toByte(), 0x20.toByte(), 0xFC.toByte()
        )

        val sample0channel0 = -6430.0f
        val sample0channel1 = 5626.0f
        val sample0channel2 = 2633.0f
        val sample0status = MagData.CalibrationStatus.NOT_AVAILABLE

        val sample1channel0 = sample0channel0 - 0x01
        val sample1channel1 = sample0channel1 + 0x01
        val sample1channel2 = sample0channel2 + 0x02
        val sample1status = MagData.CalibrationStatus.NOT_AVAILABLE

        val factor = 1.0f

        val dataFrame = PmdDataFrame(
            data = magDataFrameHeader + magDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val magData = MagData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSamplesSize, magData.magSamples.size)

        Assert.assertEquals(sample0channel0, magData.magSamples[0].x)
        Assert.assertEquals(sample0channel1, magData.magSamples[0].y)
        Assert.assertEquals(sample0channel2, magData.magSamples[0].z)
        Assert.assertEquals(sample0status, magData.magSamples[0].calibrationStatus)

        Assert.assertEquals(sample1channel0, magData.magSamples[1].x)
        Assert.assertEquals(sample1channel1, magData.magSamples[1].y)
        Assert.assertEquals(sample1channel2, magData.magSamples[1].z)
        Assert.assertEquals(sample1status, magData.magSamples[1].calibrationStatus)

        Assert.assertEquals(timeStamp, magData.magSamples[1].timeStamp)
    }

    @Test
    fun `process magnetometer compressed data type 1`() {
        // Arrange
        // HEX: 06 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           06 (MAG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     81 (compressed, type 1)

        val magDataFrameHeader = byteArrayOf(
            0x06.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x81.toByte(),
        )
        val previousTimeStamp = 100uL

        // HEX: 37 FF 51 FD 6C F6 00 00 03 01 F8 02
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  37 FF (0xFF37 = -201)
        // 2..3     Sample 0 - channel 1 (ref. sample)  51 FD (0xFD51 = -687)
        // 4..5     Sample 0 - channel 2 (ref. sample)  6C F6 (0xF66C = -2452)
        // 6..7     Status (ref. sample)                00 00 (0x0000 = 0)
        // 8        Delta size                          03 (3 bit)
        // 9        Sample amount                       01 (1 samples)
        // 10..     Delta data                          F8 (binary: 11 111 000) 02 (binary: 0000 0010)
        // Delta channel 0                              000b
        // Delta channel 1                              111b
        // Delta channel 2                              011b
        // Delta status                                 001b
        val expectedSamplesSize = 1 + 1 // reference sample + delta samples
        val magDataFrameContent = byteArrayOf(
            0x37.toByte(), 0xFF.toByte(), 0x51.toByte(), 0xFD.toByte(), 0x6C.toByte(), 0xF6.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x01.toByte(), 0xF8.toByte(), 0x02.toByte()
        )

        val sample0channel0 = -201.0f / 1000
        val sample0channel1 = -687.0f / 1000
        val sample0channel2 = -2452.0f / 1000
        val sample0status = MagData.CalibrationStatus.getById(0x00)

        val sample1channel0 = (-201.0f + 0x00) / 1000
        val sample1channel1 = (-687.0f - 0x01) / 1000
        val sample1channel2 = (-2452.0f + 0x3) / 1000
        val sample1status = MagData.CalibrationStatus.getById(0x00 + 0x01)

        val factor = 1.0f
        val dataFrame = PmdDataFrame(
            data = magDataFrameHeader + magDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val magData = MagData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSamplesSize, magData.magSamples.size)

        Assert.assertEquals(sample0channel0, magData.magSamples[0].x, 0.00001f)
        Assert.assertEquals(sample0channel1, magData.magSamples[0].y, 0.00001f)
        Assert.assertEquals(sample0channel2, magData.magSamples[0].z, 0.00001f)
        Assert.assertEquals(sample0status, magData.magSamples[0].calibrationStatus)

        Assert.assertEquals(sample1channel0, magData.magSamples[1].x, 0.00001f)
        Assert.assertEquals(sample1channel1, magData.magSamples[1].y, 0.00001f)
        Assert.assertEquals(sample1channel2, magData.magSamples[1].z, 0.00001f)
        Assert.assertEquals(sample1status, magData.magSamples[1].calibrationStatus)

        Assert.assertEquals(timeStamp, magData.magSamples[1].timeStamp)
    }

    @Test
    fun `process magnetometer compressed data type 1 with factor`() {
        // Arrange
        // HEX: 06 00 94 35 77 00 00 00 00 01
        // index                                                   data:
        // 0        type                                           06 (MAG)
        // 1..9     timestamp                                      00 94 35 77 00 00 00 00
        val timeStamp = 2000000000uL
        // 10       frame type                                     81 (compressed, type 1)

        val magDataFrameHeader = byteArrayOf(
            0x06.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x81.toByte(),
        )
        val previousTimeStamp = 100uL

        // HEX: 37 FF 51 FD 6C F6 00 00 03 01 F8 02
        // index    type                                data
        // 0..1     Sample 0 - channel 0 (ref. sample)  37 FF (0xFF37 = -201)
        // 2..3     Sample 0 - channel 1 (ref. sample)  51 FD (0xFD51 = -687)
        // 4..5     Sample 0 - channel 2 (ref. sample)  6C F6 (0xF66C = -2452)
        // 6..7     Status (ref. sample)                00 00 (0x0000 = 0)
        // 8        Delta size                          03 (3 bit)
        // 9        Sample amount                       01 (1 samples)
        // 10..     Delta data                          F8 (binary: 11 111 000) 02 (binary: 0000 0010)
        // Delta channel 0                              000b
        // Delta channel 1                              111b
        // Delta channel 2                              011b
        // Delta status                                 001b
        val expectedSamplesSize = 1 + 1 // reference sample + delta samples
        val magDataFrameContent = byteArrayOf(
            0x37.toByte(), 0xFF.toByte(), 0x51.toByte(), 0xFD.toByte(), 0x6C.toByte(), 0xF6.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x01.toByte(), 0xF8.toByte(), 0x02.toByte()
        )

        val sample0channel0 = -201.0f / 1000
        val sample0channel1 = -687.0f / 1000
        val sample0channel2 = -2452.0f / 1000
        val sample0status = MagData.CalibrationStatus.getById(0x00)

        val sample1channel0 = (-201.0f + 0x00) / 1000
        val sample1channel1 = (-687.0f - 0x01) / 1000
        val sample1channel2 = (-2452.0f + 0x3) / 1000
        val sample1status = MagData.CalibrationStatus.getById(0x00 + 0x01)

        val factor = 1.1f

        val dataFrame = PmdDataFrame(
            data = magDataFrameHeader + magDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { factor }
        ) { 0 }

        // Act
        val magData = MagData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSamplesSize, magData.magSamples.size)

        Assert.assertEquals(factor * sample0channel0, magData.magSamples[0].x, 0.00001f)
        Assert.assertEquals(factor * sample0channel1, magData.magSamples[0].y, 0.00001f)
        Assert.assertEquals(factor * sample0channel2, magData.magSamples[0].z, 0.00001f)
        Assert.assertEquals(sample0status, magData.magSamples[0].calibrationStatus)

        Assert.assertEquals(factor * sample1channel0, magData.magSamples[1].x, 0.00001f)
        Assert.assertEquals(factor * sample1channel1, magData.magSamples[1].y, 0.00001f)
        Assert.assertEquals(factor * sample1channel2, magData.magSamples[1].z, 0.00001f)
        Assert.assertEquals(sample1status, magData.magSamples[1].calibrationStatus)

        Assert.assertEquals(timeStamp, magData.magSamples[1].timeStamp)
    }

    @Test
    fun magGoldenVectors_matchAndroidBehavior() {
        val vectors = loadMagVectors()
        Assert.assertTrue("Expected MAG golden vectors", vectors.isNotEmpty())

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

            val magData = MagData.parseDataFromDataFrame(frame)

            Assert.assertEquals(caseId, expected.get("timeStamp").asLong.toULong(), frame.timeStamp)
            assertMagSamples(caseId, expected.getAsJsonArray("samples"), magData.magSamples)
        }
    }

    private fun assertParseError(caseId: String, expectedError: String, frame: PmdDataFrame) {
        when (expectedError) {
            "unsupportedFrame" -> Assert.assertThrows(caseId, Exception::class.java) {
                MagData.parseDataFromDataFrame(frame)
            }
            "malformedFrame" -> Assert.assertThrows(caseId, Exception::class.java) {
                MagData.parseDataFromDataFrame(frame)
            }
            else -> Assert.fail("$caseId has unsupported parse error expectation $expectedError")
        }
    }

    @Test
    fun `mag golden vectors follow neutral KMP vector shape`() {
        loadMagVectors().forEach { vector ->
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
    fun `MAG readiness manifest is pinned before parser migration`() {
        val manifest = loadMagReadinessManifest()
        val id = manifest.get("id").asString
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val consumerTests = manifest.getAsJsonObject("consumerTests")

        Assert.assertEquals("mag-readiness", id)
        Assert.assertEquals("magReadiness", input.get("kind").asString)
        Assert.assertEquals(id, MAG_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        Assert.assertEquals(id, MAG_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(id, MAG_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.MagDataTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("MagDataTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.MagParserCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun assertMagSamples(
        caseId: String,
        expectedSamples: JsonArray,
        actualSamples: List<MagData.MagSample>
    ) {
        Assert.assertEquals(caseId, expectedSamples.size(), actualSamples.size)
        expectedSamples.forEachIndexed { index, expectedSample ->
            val sample = expectedSample.asJsonObject
            val actualSample = actualSamples[index]
            Assert.assertEquals(caseId, sample.get("timeStamp").asLong.toULong(), actualSample.timeStamp)
            Assert.assertEquals(caseId, sample.get("x").asFloat, actualSample.x)
            Assert.assertEquals(caseId, sample.get("y").asFloat, actualSample.y)
            Assert.assertEquals(caseId, sample.get("z").asFloat, actualSample.z)
            Assert.assertEquals(caseId, MagData.CalibrationStatus.valueOf(sample.get("calibrationStatus").asString), actualSample.calibrationStatus)
        }
    }

    private fun loadMagVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/sensors")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" && file.name.startsWith("mag-") }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser.parseReader(reader).asJsonObject
                }
            }
            .filter { vector -> vector.getAsJsonObject("input").get("kind")?.asString != "magReadiness" }
    }

    private fun loadMagReadinessManifest(): JsonObject {
        val vectorFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/sensors/mag-readiness.json")
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
        val MAG_READINESS_POLICY_VECTOR_PATHS = listOf(
            "protocol/sensors/mag-compressed-type0-factor-half.json",
            "protocol/sensors/mag-compressed-type0-truncated-delta-header-android-error.json",
            "protocol/sensors/mag-compressed-type0-truncated-delta-header-ios-reference-only.json",
            "protocol/sensors/mag-compressed-type0-truncated-delta-payload-android-error.json",
            "protocol/sensors/mag-compressed-type0-truncated-delta-payload-ios-reference-only.json",
            "protocol/sensors/mag-compressed-type0-two-samples.json",
            "protocol/sensors/mag-compressed-type1-calibration-status.json",
            "protocol/sensors/mag-compressed-type2-unsupported.json",
            "protocol/sensors/mag-raw-type0-unsupported.json"
        )

        val MAG_READINESS_FAMILIES = listOf(
            "compressed-type0-reference-delta-decoding",
            "compressed-type0-factor-scaling",
            "compressed-type0-timestamp-interpolation",
            "compressed-type1-calibration-status-mapping",
            "compressed-type1-milligauss-to-gauss-conversion",
            "unsupported-raw-frame-policy",
            "unsupported-compressed-frame-policy",
            "truncated-compressed-delta-header-policy",
            "truncated-compressed-delta-payload-policy",
            "platform-mag-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}
