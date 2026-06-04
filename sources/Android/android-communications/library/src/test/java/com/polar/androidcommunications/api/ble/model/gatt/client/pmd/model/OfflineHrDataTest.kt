package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import androidx.test.espresso.matcher.ViewMatchers
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.function.ThrowingRunnable
import java.io.File
import java.io.FileReader

internal class OfflineHrDataTest {

    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `test offline Type0 hr data sample`() {
        // Arrange
        // HEX: 0E 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0E (Offline hr)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     00 (raw, type 0)
        val offlineHrDataFrameHeader = byteArrayOf(
            0x0E.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(),
        )
        val previousTimeStamp = 0uL

        // index                                                   data:
        // 0             sample0                                   00
        val expectedSample0 = 0
        // 1             sample0                                   FF
        val expectedSample1 = 255
        // last index    sampleN                                   7F
        val expectedSampleLast = 127
        val expectedSampleSize = 9
        val offlineHrDataFrameContent = byteArrayOf(
            0x00.toByte(), 0xFF.toByte(), 0x32.toByte(), 0x32.toByte(), 0x33.toByte(), 0x33.toByte(), 0x34.toByte(), 0x35.toByte(), 0x7F.toByte(),
        )

        val dataFrame = PmdDataFrame(
            data = offlineHrDataFrameHeader + offlineHrDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { 1.0f }
        ) { 0 }


        // Act
        val offlineHrData = OfflineHrData.parseDataFromDataFrame(dataFrame)

        // Assert
        Assert.assertEquals(expectedSampleSize, offlineHrData.hrSamples.size)
        Assert.assertEquals(expectedSample0, offlineHrData.hrSamples.first().hr)
        Assert.assertEquals(expectedSample1, offlineHrData.hrSamples[1].hr)
        Assert.assertEquals(expectedSampleLast, offlineHrData.hrSamples.last().hr)
    }

    @Test
    fun `test offline Type0 compressed hr data sample throws`() {
        // Arrange
        // HEX: 0E 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           0E (Offline hr)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     00 (raw, type 0)
        val offlineHrDataFrameHeader = byteArrayOf(
            0x0E.toByte(),
            0x00.toByte(), 0x94.toByte(), 0x35.toByte(), 0x77.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x80.toByte(),
        )
        val previousTimeStamp = 0uL

        val dataFrame = PmdDataFrame(
            data = offlineHrDataFrameHeader,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { 1.0f }
        ) { 0 }

        var throwingRunnable = ThrowingRunnable { OfflineHrData.parseDataFromDataFrame(dataFrame) }
        val exception = Assert.assertThrows(Exception::class.java, throwingRunnable)
        ViewMatchers.assertThat(
            exception.message,
            Matchers.equalTo("Compressed FrameType: TYPE_0 is not supported by Offline HR data parser")
        )
    }

    @Test
    fun `test offline type 1 hr data sample`() {

        val offlineHrDataFrameHeader = byteArrayOf(
            0x14.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(),
            0x0.toByte(), 0x0.toByte(), 0x1.toByte()
        )

        val offlineHrDataFrameContent = byteArrayOf(
            72.toByte(), 86.toByte(), 71.toByte(), 81.toByte(), 64.toByte(), 82.toByte()
        )

        val dataFrame = PmdDataFrame(
            data = offlineHrDataFrameHeader + offlineHrDataFrameContent,
            getPreviousTimeStamp = {  pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> 0uL },
            getFactor = { 1.0f }
        ) { 0 }

        val offlineHrData = OfflineHrData.parseDataFromDataFrame(dataFrame)

        Assert.assertEquals(2, offlineHrData.hrSamples.size)
        Assert.assertEquals(72, offlineHrData.hrSamples.first().hr)
        Assert.assertEquals(86, offlineHrData.hrSamples.first().ppgQuality)
        Assert.assertEquals(71, offlineHrData.hrSamples.first().correctedHr)
        Assert.assertEquals(81, offlineHrData.hrSamples.last().hr)
        Assert.assertEquals(64, offlineHrData.hrSamples.last().ppgQuality)
        Assert.assertEquals(82, offlineHrData.hrSamples.last().correctedHr)
    }

    @Test
    fun `test offline Type1 compressed hr data sample throws`() {

        val offlineHrDataFrameHeader = byteArrayOf(
            0x14.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(), 0x0.toByte(),
            0x0.toByte(), 0x0.toByte(), 0x81.toByte()
        )
        val previousTimeStamp = 0uL

        val dataFrame = PmdDataFrame(
            data = offlineHrDataFrameHeader,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { 1.0f }
        ) { 0 }

        var throwingRunnable = ThrowingRunnable { OfflineHrData.parseDataFromDataFrame(dataFrame) }
        val exception = Assert.assertThrows(Exception::class.java, throwingRunnable)
        ViewMatchers.assertThat(
            exception.message,
            Matchers.equalTo("Compressed FrameType: TYPE_1 is not supported by Offline HR data parser")
        )
    }

    @Test
    fun offlineHrGoldenVectors_matchAndroidBehavior() {
        val vectors = loadOfflineHrVectors()
        Assert.assertTrue("Expected offline HR golden vectors", vectors.isNotEmpty())

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
                return@forEach
            }

            val offlineHrData = OfflineHrData.parseDataFromDataFrame(frame)

            assertOfflineHrSamples(caseId, expected.getAsJsonArray("samples"), offlineHrData.hrSamples)
        }
    }

    private fun assertParseError(caseId: String, expectedError: String, frame: PmdDataFrame) {
        when (expectedError) {
            "unsupportedFrame" -> Assert.assertThrows(caseId, Exception::class.java) {
                OfflineHrData.parseDataFromDataFrame(frame)
            }
            "unsupportedCompressedFrame" -> Assert.assertThrows(caseId, Exception::class.java) {
                OfflineHrData.parseDataFromDataFrame(frame)
            }
            "malformedFrame" -> Assert.assertThrows(caseId, Exception::class.java) {
                OfflineHrData.parseDataFromDataFrame(frame)
            }
            else -> Assert.fail("$caseId has unsupported parse error expectation $expectedError")
        }
    }

    @Test
    fun `offline HR golden vectors follow neutral KMP vector shape`() {
        loadOfflineHrVectors().forEach { vector ->
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
    fun `offline HR readiness manifest is pinned before parser migration`() {
        val manifest = loadOfflineHrReadinessManifest()
        val id = manifest.get("id").asString
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val consumerTests = manifest.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }

        Assert.assertEquals("offline-hr-readiness", id)
        Assert.assertEquals("offlineHrReadiness", input.get("kind").asString)
        Assert.assertEquals(id, OFFLINE_HR_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        Assert.assertEquals(id, OFFLINE_HR_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(id, OFFLINE_HR_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(id, listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.OfflineHrDataTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(id, listOf("OfflineHrDataTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(id, listOf("com.polar.sharedtest.OfflineHrParserCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun assertOfflineHrSamples(
        caseId: String,
        expectedSamples: JsonArray,
        actualSamples: List<OfflineHrData.OfflineHrSample>
    ) {
        Assert.assertEquals(caseId, expectedSamples.size(), actualSamples.size)
        expectedSamples.forEachIndexed { index, expectedSample ->
            val sample = expectedSample.asJsonObject
            val actualSample = actualSamples[index]
            Assert.assertEquals(caseId, sample.get("hr").asInt, actualSample.hr)
            Assert.assertEquals(caseId, sample.get("ppgQuality").asInt, actualSample.ppgQuality)
            Assert.assertEquals(caseId, sample.get("correctedHr").asInt, actualSample.correctedHr)
        }
    }

    private fun loadOfflineHrVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/sensors")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" && file.name.startsWith("offline-hr-") }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
            .filter { vector -> vector.getAsJsonObject("input").get("kind")?.asString != "offlineHrReadiness" }
    }

    private fun loadOfflineHrReadinessManifest(): JsonObject {
        val vectorFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/sensors/offline-hr-readiness.json")
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
        val OFFLINE_HR_READINESS_POLICY_VECTOR_PATHS = listOf(
            "protocol/sensors/offline-hr-compressed-type0-unsupported.json",
            "protocol/sensors/offline-hr-raw-type0-empty.json",
            "protocol/sensors/offline-hr-raw-type0-hr-only-boundaries.json",
            "protocol/sensors/offline-hr-raw-type1-truncated-tuple-android-error.json",
            "protocol/sensors/offline-hr-raw-type1-two-samples.json",
            "protocol/sensors/offline-hr-raw-type2-unsupported.json"
        )

        val OFFLINE_HR_READINESS_FAMILIES = listOf(
            "raw-type0-hr-only-samples",
            "raw-type0-empty-recording",
            "raw-type1-hr-ppg-quality-corrected-hr-triples",
            "unsupported-compressed-frame-policy",
            "unsupported-raw-frame-policy",
            "truncated-raw-type1-tuple-policy",
            "platform-offline-hr-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}
