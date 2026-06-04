package com.polar.sdk.api.model.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbSystemDateTime
import fi.polar.remote.representation.protobuf.Types.PbTime
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.sleep.PolarNightlyRechargeData
import com.polar.sdk.impl.utils.PolarNightlyRechargeUtils
import fi.polar.remote.representation.protobuf.NightlyRecovery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import protocol.PftpRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class PolarNightlyRechargeUtilsTest {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    @Test
    fun `readNightlyRechargeData() should return nightly recharge data`() = runTest {
        // Arrange
        val mockClient = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/NR/NR.BPB"

        val outputStream = ByteArrayOutputStream().apply {
            val proto = NightlyRecovery.PbNightlyRecoveryStatus.newBuilder()
                    .setSleepResultDate(PbDate.newBuilder().setYear(2024).setMonth(12).setDay(5).build())
                    .setCreatedTimestamp(PbSystemDateTime.newBuilder()
                            .setDate(PbDate.newBuilder().setYear(2023).setMonth(12).setDay(5).build())
                            .setTime(PbTime.newBuilder().setHour(10).setMinute(0).setSeconds(0).setMillis(0).build())
                            .setTrusted(true)
                            .build())
                    .setModifiedTimestamp(PbSystemDateTime.newBuilder()
                            .setDate(PbDate.newBuilder().setYear(2023).setMonth(12).setDay(5).build())
                            .setTime(PbTime.newBuilder().setHour(10).setMinute(0).setSeconds(0).setMillis(0).build())
                            .setTrusted(true)
                            .build())
                    .setAnsStatus(5.5f)
                    .setRecoveryIndicator(3)
                    .setRecoveryIndicatorSubLevel(50)
                    .setAnsRate(4)
                    .setScoreRateOBSOLETE(2)
                    .setMeanNightlyRecoveryRRI(800)
                    .setMeanNightlyRecoveryRMSSD(50)
                    .setMeanNightlyRecoveryRespirationInterval(1000)
                    .setMeanBaselineRRI(750)
                    .setSdBaselineRRI(30)
                    .setMeanBaselineRMSSD(45)
                    .setSdBaselineRMSSD(20)
                    .setMeanBaselineRespirationInterval(950)
                    .setSdBaselineRespirationInterval(25)
                    .setSleepTip("Sleep tip 1")
                    .setVitalityTip("Vitality tip 2")
                    .setExerciseTip("Exercise tip 3")
                    .build()
            proto.writeTo(this)
        }

        val createdTimestamp = LocalDateTime.of(2023, 12, 5, 10, 0, 0, 0)
        val sleepResultDate = LocalDate.of(2024, Calendar.DECEMBER + 1, 5)

        val expectedResult = PolarNightlyRechargeData(
                createdTimestamp = createdTimestamp,
                modifiedTimestamp = createdTimestamp,
                ansStatus = 5.5f,
                recoveryIndicator = 3,
                recoveryIndicatorSubLevel = 50,
                ansRate = 4,
                scoreRateObsolete = 2,
                meanNightlyRecoveryRRI = 800,
                meanNightlyRecoveryRMSSD = 50,
                meanNightlyRecoveryRespirationInterval = 1000,
                meanBaselineRRI = 750,
                sdBaselineRRI = 30,
                meanBaselineRMSSD = 45,
                sdBaselineRMSSD = 20,
                meanBaselineRespirationInterval = 950,
                sdBaselineRespirationInterval = 25,
                sleepTip = "Sleep tip 1",
                vitalityTip = "Vitality tip 2",
                exerciseTip = "Exercise tip 3",
                sleepResultDate = sleepResultDate
        )

        coEvery { mockClient.request(any()) } returns outputStream

        // Act
        val result = PolarNightlyRechargeUtils.readNightlyRechargeData(mockClient, date)

        // Assert
        assertEquals(expectedResult, result)

        coVerify {
            mockClient.request(
                    PftpRequest.PbPFtpOperation.newBuilder()
                            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                            .setPath(expectedPath)
                            .build()
                            .toByteArray()
            )
        }
        confirmVerified(mockClient)
    }

    @Test
    fun `readNightlyRechargeData() should return null when an error is thrown`() = runTest {
        // Arrange
        val mockClient = mockk<BlePsFtpClient>()
        val date = LocalDate.now()
        val expectedPath = "/U/0/${date.format(dateFormatter)}/NR/NR.BPB"

        coEvery { mockClient.request(any()) } throws Throwable("No nightly recharge data found")

        // Act
        val result = PolarNightlyRechargeUtils.readNightlyRechargeData(mockClient, date)

        // Assert
        assertNull(result)

        coVerify {
            mockClient.request(
                    PftpRequest.PbPFtpOperation.newBuilder()
                            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                            .setPath(expectedPath)
                            .build()
                            .toByteArray()
            )
        }
        confirmVerified(mockClient)
    }

    @Test
    fun `nightly recharge golden vectors map protobuf to public model`() = runTest {
        val vectors = loadNightlyRechargeVectors()
        assertTrue("Expected nightly recharge golden vectors", vectors.isNotEmpty())
        val date = LocalDate.of(2026, 1, 2)
        val expectedPath = "/U/0/${date.format(dateFormatter)}/NR/NR.BPB"

        vectors.forEach { vector ->
            val caseId = vector.get("id").asString
            val input = vector.getAsJsonObject("input")
            val mockClient = mockk<BlePsFtpClient>()
            val outputStream = if (input.has("responseHex")) {
                ByteArrayOutputStream().apply {
                    write(input.get("responseHex").asString.hexToByteArray())
                }
            } else {
                ByteArrayOutputStream().apply {
                    buildNightlyRechargeProto(input.getAsJsonObject("proto")).writeTo(this)
                }
            }
            coEvery { mockClient.request(any()) } returns outputStream

            val result = PolarNightlyRechargeUtils.readNightlyRechargeData(mockClient, date)

            val expected = vector.getAsJsonObject("expected")
            if (expected.has("result") && expected.get("result").isJsonNull) {
                assertNull("$caseId result", result)
            } else {
                assertNightlyRechargeResult(caseId, result, expected)
            }
            coVerify {
                mockClient.request(
                        PftpRequest.PbPFtpOperation.newBuilder()
                                .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
                                .setPath(expectedPath)
                                .build()
                                .toByteArray()
                )
            }
            confirmVerified(mockClient)
        }
    }

    @Test
    fun `nightly recharge golden vectors follow neutral KMP vector shape`() {
        val vectors = loadNightlyRechargeVectors()
        assertTrue("Expected nightly recharge golden vectors", vectors.isNotEmpty())
        vectors.forEach { vector ->
            val id = vector.get("id").asString
            assertTrue(id, vector.has("area"))
            assertTrue(id, vector.has("case"))
            assertTrue(id, vector.has("source"))
            assertTrue(id, vector.has("input"))
            assertTrue(id, vector.has("expected"))
            assertTrue(id, vector.has("platforms"))
            val input = vector.getAsJsonObject("input")
            assertTrue(id, input.has("proto") || input.has("responseHex"))
            val platforms = vector.getAsJsonObject("platforms")
            assertTrue(id, platforms.get("android").asBoolean)
            assertTrue(id, platforms.get("ios").asBoolean)
            assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    @Test
    fun `sleep nightly readiness manifest is pinned before model migration`() {
        val readiness = loadSleepNightlyReadinessManifest()
        val input = readiness.getAsJsonObject("input")
        val expected = readiness.getAsJsonObject("expected")
        val consumerTests = readiness.getAsJsonObject("consumerTests")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        assertEquals("sleep-nightly-readiness", readiness.get("id").asString)
        assertEquals("sleepNightlyReadiness", input.get("kind").asString)
        assertEquals(
            listOf(
                "sdk/nightly-recharge/full-status.json",
                "sdk/nightly-recharge/malformed-response.json",
                "sdk/nightly-recharge/missing-modified-default-metrics.json",
                "sdk/sleep/partial-night-omitted-optionals.json",
                "sdk/sleep/sleep-offset-platform-policy.json",
                "sdk/sleep/sleep-stage-hypnogram.json",
                "sdk/sleep/sleep-timezone-offsets.json"
            ),
            policyVectorPaths
        )
        val expectedFamilies = listOf(
            "nightly-result-date-formatting",
            "nightly-created-modified-timestamp-formatting",
            "nightly-proto3-default-preservation",
            "nightly-malformed-payload-null-policy",
            "sleep-end-offset-field-policy",
            "sleep-timezone-to-utc-instant-policy",
            "sleep-hypnogram-order-preservation",
            "sleep-cycle-order-preservation",
            "sleep-stage-enum-mapping",
            "sleep-partial-night-optional-policy",
            "platform-sleep-nightly-vector-reference-gate",
            "compile-verification-gate"
        )
        assertEquals(expectedFamilies, requiredFamilies)
        assertEquals(expectedFamilies, coveredFamilies)
        assertEquals(
            "Sleep and nightly recharge model migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS sleep/nightly tests continue to reference the same vectors, nightly date/timestamp/default and malformed-payload behavior stays covered, sleep end-offset, timezone, hypnogram, cycle, enum, and partial-night optional policies remain explicit, and the shared tests are compile-verified.",
            expected.get("commonDecision").asString
        )
        assertEquals(
            listOf("com.polar.sdk.api.model.utils.PolarNightlyRechargeUtilsTest"),
            consumerTests.getAsJsonArray("android").map { it.asString }
        )
        assertEquals(
            listOf("PolarNightlyRechargeUtilsTest"),
            consumerTests.getAsJsonArray("ios").map { it.asString }
        )
        assertEquals(
            listOf("com.polar.sharedtest.SleepNightlyRechargeCommonPolicyTest"),
            consumerTests.getAsJsonArray("commonPrototype").map { it.asString }
        )
    }

    private fun buildNightlyRechargeProto(proto: JsonObject): NightlyRecovery.PbNightlyRecoveryStatus {
        val builder = NightlyRecovery.PbNightlyRecoveryStatus.newBuilder()
                .setSleepResultDate(buildPbDate(proto.getAsJsonObject("sleepResultDate")))
                .setCreatedTimestamp(buildPbSystemDateTime(proto.getAsJsonObject("createdTimestamp")))

        if (proto.has("modifiedTimestamp")) builder.setModifiedTimestamp(buildPbSystemDateTime(proto.getAsJsonObject("modifiedTimestamp")))
        if (proto.has("ansStatus")) builder.setAnsStatus(proto.get("ansStatus").asFloat)
        if (proto.has("recoveryIndicator")) builder.setRecoveryIndicator(proto.get("recoveryIndicator").asInt)
        if (proto.has("recoveryIndicatorSubLevel")) builder.setRecoveryIndicatorSubLevel(proto.get("recoveryIndicatorSubLevel").asInt)
        if (proto.has("ansRate")) builder.setAnsRate(proto.get("ansRate").asInt)
        if (proto.has("scoreRateObsolete")) builder.setScoreRateOBSOLETE(proto.get("scoreRateObsolete").asInt)
        if (proto.has("meanNightlyRecoveryRRI")) builder.setMeanNightlyRecoveryRRI(proto.get("meanNightlyRecoveryRRI").asInt)
        if (proto.has("meanNightlyRecoveryRMSSD")) builder.setMeanNightlyRecoveryRMSSD(proto.get("meanNightlyRecoveryRMSSD").asInt)
        if (proto.has("meanNightlyRecoveryRespirationInterval")) builder.setMeanNightlyRecoveryRespirationInterval(proto.get("meanNightlyRecoveryRespirationInterval").asInt)
        if (proto.has("meanBaselineRRI")) builder.setMeanBaselineRRI(proto.get("meanBaselineRRI").asInt)
        if (proto.has("sdBaselineRRI")) builder.setSdBaselineRRI(proto.get("sdBaselineRRI").asInt)
        if (proto.has("meanBaselineRMSSD")) builder.setMeanBaselineRMSSD(proto.get("meanBaselineRMSSD").asInt)
        if (proto.has("sdBaselineRMSSD")) builder.setSdBaselineRMSSD(proto.get("sdBaselineRMSSD").asInt)
        if (proto.has("meanBaselineRespirationInterval")) builder.setMeanBaselineRespirationInterval(proto.get("meanBaselineRespirationInterval").asInt)
        if (proto.has("sdBaselineRespirationInterval")) builder.setSdBaselineRespirationInterval(proto.get("sdBaselineRespirationInterval").asInt)
        if (proto.has("sleepTip")) builder.setSleepTip(proto.get("sleepTip").asString)
        if (proto.has("vitalityTip")) builder.setVitalityTip(proto.get("vitalityTip").asString)
        if (proto.has("exerciseTip")) builder.setExerciseTip(proto.get("exerciseTip").asString)
        return builder.build()
    }

    private fun buildPbSystemDateTime(fields: JsonObject): PbSystemDateTime {
        val time = fields.getAsJsonObject("time")
        return PbSystemDateTime.newBuilder()
                .setDate(buildPbDate(fields.getAsJsonObject("date")))
                .setTime(PbTime.newBuilder()
                        .setHour(time.get("hour").asInt)
                        .setMinute(time.get("minute").asInt)
                        .setSeconds(time.get("second").asInt)
                        .setMillis(time.get("millis").asInt)
                        .build())
                .setTrusted(fields.get("trusted").asBoolean)
                .build()
    }

    private fun buildPbDate(fields: JsonObject): PbDate {
        return PbDate.newBuilder()
                .setYear(fields.get("year").asInt)
                .setMonth(fields.get("month").asInt)
                .setDay(fields.get("day").asInt)
                .build()
    }

    private fun assertNightlyRechargeResult(caseId: String, actual: PolarNightlyRechargeData?, expected: JsonObject) {
        actual ?: error("Expected nightly recharge result for $caseId")
        assertEquals("$caseId createdTimestamp", parseLocalDateTime(expected.get("createdTimestamp").asString), actual.createdTimestamp)
        if (expected.get("modifiedTimestamp").isJsonNull) {
            assertNull("$caseId modifiedTimestamp", actual.modifiedTimestamp)
        } else {
            assertEquals("$caseId modifiedTimestamp", parseLocalDateTime(expected.get("modifiedTimestamp").asString), actual.modifiedTimestamp)
        }
        assertEquals("$caseId ansStatus", expected.get("ansStatus").asFloat, actual.ansStatus ?: Float.NaN, 0.00001f)
        assertEquals("$caseId recoveryIndicator", expected.get("recoveryIndicator").asInt, actual.recoveryIndicator)
        assertEquals("$caseId recoveryIndicatorSubLevel", expected.get("recoveryIndicatorSubLevel").asInt, actual.recoveryIndicatorSubLevel)
        assertEquals("$caseId ansRate", expected.get("ansRate").asInt, actual.ansRate)
        assertEquals("$caseId scoreRateObsolete", expected.get("scoreRateObsolete").asInt, actual.scoreRateObsolete)
        assertEquals("$caseId meanNightlyRecoveryRRI", expected.get("meanNightlyRecoveryRRI").asInt, actual.meanNightlyRecoveryRRI)
        assertEquals("$caseId meanNightlyRecoveryRMSSD", expected.get("meanNightlyRecoveryRMSSD").asInt, actual.meanNightlyRecoveryRMSSD)
        assertEquals("$caseId meanNightlyRecoveryRespirationInterval", expected.get("meanNightlyRecoveryRespirationInterval").asInt, actual.meanNightlyRecoveryRespirationInterval)
        assertEquals("$caseId meanBaselineRRI", expected.get("meanBaselineRRI").asInt, actual.meanBaselineRRI)
        assertEquals("$caseId sdBaselineRRI", expected.get("sdBaselineRRI").asInt, actual.sdBaselineRRI)
        assertEquals("$caseId meanBaselineRMSSD", expected.get("meanBaselineRMSSD").asInt, actual.meanBaselineRMSSD)
        assertEquals("$caseId sdBaselineRMSSD", expected.get("sdBaselineRMSSD").asInt, actual.sdBaselineRMSSD)
        assertEquals("$caseId meanBaselineRespirationInterval", expected.get("meanBaselineRespirationInterval").asInt, actual.meanBaselineRespirationInterval)
        assertEquals("$caseId sdBaselineRespirationInterval", expected.get("sdBaselineRespirationInterval").asInt, actual.sdBaselineRespirationInterval)
        assertEquals("$caseId sleepTip", expected.get("sleepTip").asString, actual.sleepTip)
        assertEquals("$caseId vitalityTip", expected.get("vitalityTip").asString, actual.vitalityTip)
        assertEquals("$caseId exerciseTip", expected.get("exerciseTip").asString, actual.exerciseTip)
        assertEquals("$caseId sleepResultDate", LocalDate.parse(expected.get("sleepResultDate").asString), actual.sleepResultDate)
    }

    private fun parseLocalDateTime(value: String): LocalDateTime {
        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"))
    }

    private fun loadNightlyRechargeVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/nightly-recharge")
        return vectorDirectory
                .listFiles { file -> file.isFile && file.extension == "json" }
                .orEmpty()
                .sortedBy { it.name }
                .map { file ->
                    FileReader(file).use { reader ->
                        JsonParser().parse(reader).asJsonObject
                    }
                }
                .filterNot { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString == "sleepNightlyReadiness" }
    }

    private fun loadSleepNightlyReadinessManifest(): JsonObject {
        val vectorFile = findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/nightly-recharge/sleep-nightly-readiness.json")
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
}
