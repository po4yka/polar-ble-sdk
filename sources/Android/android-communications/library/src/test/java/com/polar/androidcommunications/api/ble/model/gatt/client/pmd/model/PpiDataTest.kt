package com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdDataFrame
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileReader

internal class PpiDataTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `process ppi raw data type 0`() {
        // Arrange
        // HEX: 03 00 00 00 00 00 00 00 00 00
        // index                                                   data:
        // 0        type                                           03 (PPI)
        // 1..9     timestamp                                      00 00 00 00 00 00 00 00
        // 10       frame type                                     00 (raw, type 0)
        val ppiDataFrameHeader = byteArrayOf(
            0x03.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(),
        )
        val previousTimeStamp = 100uL
        // HEX:  80 80 80 80 80 FF 00 01 00 01 00 00
        // index    type                                            data:
        // 0        HR                                              0x80 (128)
        val heartRate = 128
        // 1..2     PP                                              0x80 0x80 (32896)
        val intervalInMs = 32896
        // 3..4     PP Error Estimate                               0x80 0x80 (32896)
        val errorEstimate = 32896
        // 5        PP flags                                        0xFF
        val ppFlags = 0xFF
        val blockerBit = if (ppFlags and 0x01 != 0) 0x01 else 0x00
        val skinContactStatus = if (ppFlags and 0x02 != 0) 0x01 else 0x00
        val skinContactSupported = if (ppFlags and 0x04 != 0) 0x01 else 0x00

        // 6        HR                                              0x00 (0)
        val heartRate2 = 0
        // 7..8     PP                                              0x01 0x00 (1)
        val intervalInMs2 = 1
        // 9..10     PP Error Estimate                              0x01 0x00 (1)
        val errorEstimate2 = 1
        // 11        PP flags                                       0x00
        val ppFlags2 = 0x00
        val blockerBit2 = if (ppFlags2 and 0x01 != 0) 0x01 else 0x00
        val skinContactStatus2 = if (ppFlags2 and 0x02 != 0) 0x01 else 0x00
        val skinContactSupported2 = if (ppFlags2 and 0x04 != 0) 0x01 else 0x00

        val ppiDataFrameContent = byteArrayOf(
            0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(),
            0x80.toByte(), 0xFF.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte()
        )

        val dataFrame = PmdDataFrame(
            data = ppiDataFrameHeader + ppiDataFrameContent,
            getPreviousTimeStamp = { pmdMeasurementType: PmdMeasurementType, pmdDataFrameType: PmdDataFrame.PmdDataFrameType -> previousTimeStamp },
            getFactor = { 1.0f },
            getSampleRate = { 0 })


        val ppiData = PpiData.parseDataFromDataFrame(dataFrame)

        // Assert
        assertEquals(heartRate.toLong(), ppiData.ppiSamples[0].hr.toLong())
        assertEquals(intervalInMs.toLong(), ppiData.ppiSamples[0].ppInMs.toLong())
        assertEquals(errorEstimate.toLong(), ppiData.ppiSamples[0].ppErrorEstimate.toLong())
        assertEquals(blockerBit.toLong(), ppiData.ppiSamples[0].blockerBit.toLong())
        assertEquals(skinContactStatus.toLong(), ppiData.ppiSamples[0].skinContactStatus.toLong())
        assertEquals(skinContactSupported.toLong(), ppiData.ppiSamples[0].skinContactSupported.toLong())

        assertEquals(heartRate2.toLong(), ppiData.ppiSamples[1].hr.toLong())
        assertEquals(intervalInMs2.toLong(), ppiData.ppiSamples[1].ppInMs.toLong())
        assertEquals(errorEstimate2.toLong(), ppiData.ppiSamples[1].ppErrorEstimate.toLong())
        assertEquals(blockerBit2.toLong(), ppiData.ppiSamples[1].blockerBit.toLong())
        assertEquals(skinContactStatus2.toLong(), ppiData.ppiSamples[1].skinContactStatus.toLong())
        assertEquals(skinContactSupported2.toLong(), ppiData.ppiSamples[1].skinContactSupported.toLong())

        assertEquals(2, ppiData.ppiSamples.size)
    }

    @Test
    fun ppiGoldenVectors_matchAndroidBehavior() {
        val vectors = loadPpiVectors()
        assertTrue("Expected PPI golden vectors", vectors.isNotEmpty())

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
                assertEquals(caseId, expected.get("timeStamp").asLong.toULong(), frame.timeStamp)
                return@forEach
            }

            val ppiData = PpiData.parseDataFromDataFrame(frame)

            assertEquals(caseId, expected.get("timeStamp").asLong.toULong(), frame.timeStamp)
            assertPpiSamples(caseId, expected.getAsJsonArray("samples"), ppiData.ppiSamples)
        }
    }

    private fun assertParseError(caseId: String, expectedError: String, frame: PmdDataFrame) {
        when (expectedError) {
            "unsupportedFrame" -> assertThrows(caseId, Exception::class.java) {
                PpiData.parseDataFromDataFrame(frame)
            }
            "malformedFrame" -> assertThrows(caseId, Exception::class.java) {
                PpiData.parseDataFromDataFrame(frame)
            }
            else -> throw AssertionError("$caseId has unsupported parse error expectation $expectedError")
        }
    }

    @Test
    fun `ppi golden vectors follow neutral KMP vector shape`() {
        loadPpiVectors().forEach { vector ->
            val id = vector.get("id").asString
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
    fun `PPI readiness manifest is pinned before parser migration`() {
        val manifest = loadPpiReadinessManifest()
        val id = manifest.get("id").asString
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val consumerTests = manifest.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }

        assertEquals("ppi-readiness", id)
        assertEquals("ppiReadiness", input.get("kind").asString)
        assertEquals(id, PPI_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        assertEquals(id, PPI_READINESS_FAMILIES, requiredFamilies)
        assertEquals(id, PPI_READINESS_FAMILIES, coveredFamilies)
        assertEquals(id, listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.PpiDataTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(id, listOf("PpiDataTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(id, listOf("com.polar.sharedtest.PpiParserCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun assertPpiSamples(
        caseId: String,
        expectedSamples: JsonArray,
        actualSamples: List<PpiData.PpiSample>
    ) {
        assertEquals(caseId, expectedSamples.size(), actualSamples.size)
        expectedSamples.forEachIndexed { index, expectedSample ->
            val sample = expectedSample.asJsonObject
            val actualSample = actualSamples[index]
            assertEquals(caseId, sample.get("timeStamp").asLong.toULong(), actualSample.timeStamp)
            assertEquals(caseId, sample.get("hr").asInt, actualSample.hr)
            assertEquals(caseId, sample.get("ppInMs").asInt, actualSample.ppInMs)
            assertEquals(caseId, sample.get("ppErrorEstimate").asInt, actualSample.ppErrorEstimate)
            assertEquals(caseId, sample.get("blockerBit").asInt, actualSample.blockerBit)
            assertEquals(caseId, sample.get("skinContactStatus").asInt, actualSample.skinContactStatus)
            assertEquals(caseId, sample.get("skinContactSupported").asInt, actualSample.skinContactSupported)
        }
    }

    private fun loadPpiVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/sensors")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" && file.name.startsWith("ppi-") }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
            .filter { vector -> vector.getAsJsonObject("input").get("kind")?.asString != "ppiReadiness" }
    }

    private fun loadPpiReadinessManifest(): JsonObject {
        val vectorFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/protocol/sensors/ppi-readiness.json")
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
        val PPI_READINESS_POLICY_VECTOR_PATHS = listOf(
            "protocol/sensors/ppi-compressed-type0-unsupported.json",
            "protocol/sensors/ppi-raw-type0-truncated-sample-android-error.json",
            "protocol/sensors/ppi-raw-type0-two-samples.json",
            "protocol/sensors/ppi-raw-type0-zero-timestamp-boundary.json",
            "protocol/sensors/ppi-raw-type1-unsupported.json"
        )

        val PPI_READINESS_FAMILIES = listOf(
            "raw-type0-hr-rr-error-status-parsing",
            "raw-type0-zero-timestamp-policy",
            "raw-type0-timestamp-backfill",
            "unsupported-compressed-frame-policy",
            "unsupported-raw-frame-policy",
            "truncated-raw-sample-policy",
            "platform-ppi-vector-reference-gate",
            "compile-verification-gate"
        )
    }
}
