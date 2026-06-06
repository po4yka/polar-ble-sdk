package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.sdk.api.model.DeviationFromBaseline
import com.polar.sdk.api.model.PolarSpo2TestData
import com.polar.sdk.api.model.Spo2Class
import com.polar.sdk.api.model.Spo2TestStatus
import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter
import com.polar.sdk.impl.utils.PolarTestUtils
import com.polar.sdk.impl.utils.Spo2TestEntry
import com.polar.services.datamodels.protobuf.Spo2TestResult.PbDeviationFromBaseline
import com.polar.services.datamodels.protobuf.Spo2TestResult.PbSpo2Class
import com.polar.services.datamodels.protobuf.Spo2TestResult.PbSpo2TestResult
import com.polar.services.datamodels.protobuf.Spo2TestResult.PbSpo2TestStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbPFtpEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PolarTestUtilsTest {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    @Test
    fun `spo2 test read headers use shared file facade planning`() {
        val date = LocalDate.of(2026, 4, 13)

        assertEquals("/U/0/20260413/SPO2TEST/", PolarRuntimePlannerAdapter.spo2TestDirectoryPath("20260413"))
        assertEquals("/U/0/20260413/SPO2TEST/142507/SPO2TRES.BPB", PolarRuntimePlannerAdapter.spo2TestResultPath("/U/0/20260413/SPO2TEST/", "142507/"))
        assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/U/0/20260413/SPO2TEST/",
            PolarTestUtils.spo2TestDirectoryReadOperation(date)
        )
        assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/U/0/20260413/SPO2TEST/142507/SPO2TRES.BPB",
            PolarTestUtils.spo2TestFileReadOperation("/U/0/20260413/SPO2TEST/", "142507/")
        )
    }

    @Test
    fun `readSpo2TestProtoFromDayDirectory() returns one entry per time subdirectory`() = runTest {
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.of(2026, 4, 8)
        val dateStr = date.format(dateFormatter)
        val spo2Dir = "/U/0/$dateStr/SPO2TEST/"

        val dirContent = buildDirectory("083906/", "112658/")
        val file1 = buildProto { setBloodOxygenPercent(97).setAverageHeartRateBpm(63).setTimeZoneOffset(180) }
        val file2 = buildProto { setBloodOxygenPercent(97).setAverageHeartRateBpm(73).setTimeZoneOffset(180) }

        coEvery { client.request(requestFor(spo2Dir)) } returns dirContent
        coEvery { client.request(requestFor("${spo2Dir}083906/SPO2TRES.BPB")) } returns file1
        coEvery { client.request(requestFor("${spo2Dir}112658/SPO2TRES.BPB")) } returns file2

        val result = PolarTestUtils.readSpo2TestProtoFromDayDirectory(client, date)

        assertEquals(2, result.size)
        assertEquals(date, result[0].date)
        assertEquals("083906", result[0].timeDirName)
        assertEquals(date, result[1].date)
        assertEquals("112658", result[1].timeDirName)

        val proto1 = PbSpo2TestResult.parseFrom(result[0].protoBytes)
        assertEquals(63, proto1.averageHeartRateBpm)
        val proto2 = PbSpo2TestResult.parseFrom(result[1].protoBytes)
        assertEquals(73, proto2.averageHeartRateBpm)
    }

    @Test
    fun `readSpo2TestProtoFromDayDirectory() returns empty list when directory listing fails`() = runTest {
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.of(2026, 4, 8)

        coEvery { client.request(any()) } throws Exception("network error")

        val result = PolarTestUtils.readSpo2TestProtoFromDayDirectory(client, date)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `readSpo2TestProtoFromDayDirectory() returns empty list when SPO2TEST dir has no subdirectories`() = runTest {
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.of(2026, 4, 8)
        val dateStr = date.format(dateFormatter)
        val spo2Dir = "/U/0/$dateStr/SPO2TEST/"

        // Directory listing contains only a file, no subdirectory entries
        val emptyDir = buildDirectory()
        coEvery { client.request(requestFor(spo2Dir)) } returns emptyDir

        val result = PolarTestUtils.readSpo2TestProtoFromDayDirectory(client, date)

        assertTrue(result.isEmpty())
        coVerify(exactly = 1) { client.request(requestFor(spo2Dir)) }
    }

    @Test
    fun `readSpo2TestProtoFromDayDirectory() skips subdirectory when proto file fetch fails`() = runTest {
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.of(2026, 4, 8)
        val dateStr = date.format(dateFormatter)
        val spo2Dir = "/U/0/$dateStr/SPO2TEST/"

        val dirContent = buildDirectory("083906/", "112658/")
        val file2 = buildProto { setBloodOxygenPercent(95).setAverageHeartRateBpm(66).setTimeZoneOffset(180) }

        coEvery { client.request(requestFor(spo2Dir)) } returns dirContent
        coEvery { client.request(requestFor("${spo2Dir}083906/SPO2TRES.BPB")) } throws Exception("file not found")
        coEvery { client.request(requestFor("${spo2Dir}112658/SPO2TRES.BPB")) } returns file2

        val result = PolarTestUtils.readSpo2TestProtoFromDayDirectory(client, date)

        // Only the second subdirectory succeeds
        assertEquals(1, result.size)
        assertEquals("112658", result[0].timeDirName)
    }

    @Test
    fun `readSpo2TestProtoFromDayDirectory() returns single entry when only one time subdirectory exists`() = runTest {
        val client = mockk<BlePsFtpClient>()
        val date = LocalDate.of(2026, 4, 14)
        val dateStr = date.format(dateFormatter)
        val spo2Dir = "/U/0/$dateStr/SPO2TEST/"

        val dirContent = buildDirectory("063635/")
        val fileContent = buildProto { setBloodOxygenPercent(95).setAverageHeartRateBpm(66).setTimeZoneOffset(180) }

        coEvery { client.request(requestFor(spo2Dir)) } returns dirContent
        coEvery { client.request(requestFor("${spo2Dir}063635/SPO2TRES.BPB")) } returns fileContent

        val result = PolarTestUtils.readSpo2TestProtoFromDayDirectory(client, date)

        assertEquals(1, result.size)
        assertEquals("063635", result[0].timeDirName)
        assertEquals(date, result[0].date)
    }

    @Test
    fun `dateTimeFromFolderNames() returns correctly formatted string for valid HHMMSS`() {
        val date = LocalDate.of(2026, 4, 8)
        val result = PolarTestUtils.dateTimeFromFolderNames(date, "083906")

        assertEquals("2026-04-08 08:39:06", result)
    }

    @Test
    fun `dateTimeFromFolderNames() returns null when timeDirName is not 6 characters`() {
        val date = LocalDate.of(2026, 4, 8)
        assertNull(PolarTestUtils.dateTimeFromFolderNames(date, "0839"))
        assertNull(PolarTestUtils.dateTimeFromFolderNames(date, "08390600"))
        assertNull(PolarTestUtils.dateTimeFromFolderNames(date, ""))
    }

    @Test
    fun `dateTimeFromFolderNames() returns null when timeDirName contains non-numeric characters`() {
        val date = LocalDate.of(2026, 4, 8)
        assertNull(PolarTestUtils.dateTimeFromFolderNames(date, "AB3906"))
        assertNull(PolarTestUtils.dateTimeFromFolderNames(date, "0839XY"))
    }

    @Test
    fun `dateTimeFromFolderNames() pads single-digit hours minutes and seconds correctly`() {
        val date = LocalDate.of(2026, 4, 8)
        val result = PolarTestUtils.dateTimeFromFolderNames(date, "010203")
        assertEquals("2026-04-08 01:02:03", result)
    }

    @Test
    fun `mapSpo2TestProto() returns null testTime when folder is invalid and proto testTime is zero`() {
        val date = LocalDate.of(2026, 4, 8)
        val proto = PbSpo2TestResult.newBuilder()
            .setRecordingDevice("0004BF3D")
            .setTimeZoneOffset(180)
            .setTestStatus(PbSpo2TestStatus.SPO2_TEST_PASSED)
            .buildPartial()

        val result = PolarTestUtils.mapSpo2TestProto(proto, date, "BADDIR")

        assertNull(result.testTime)
    }

    @Test
    fun `mapSpo2TestProto() maps all proto fields correctly`() {
        val date = LocalDate.of(2026, 4, 14)
        val proto = PbSpo2TestResult.newBuilder()
            .setRecordingDevice("0004BF3D")
            .setTimeZoneOffset(180)
            .setTestStatus(PbSpo2TestStatus.SPO2_TEST_PASSED)
            .setBloodOxygenPercent(95)
            .setSpo2Class(PbSpo2Class.SPO2_CLASS_NORMAL)
            .setSpo2ValueDeviationFromBaseline(PbDeviationFromBaseline.DEVIATION_NO_BASELINE)
            .setSpo2QualityAveragePercent(99.0f)
            .setAverageHeartRateBpm(66)
            .setHeartRateVariabilityMs(79.97114f)
            .setSpo2HrvDeviationFromBaseline(PbDeviationFromBaseline.DEVIATION_USUAL)
            .setAltitudeMeters(18.13582f)
            .buildPartial()

        val result = PolarTestUtils.mapSpo2TestProto(proto, date, "063635")

        assertEquals("0004BF3D", result.recordingDevice)
        assertEquals(180, result.timeZoneOffsetMinutes)
        assertEquals(Spo2TestStatus.PASSED, result.testStatus)
        assertEquals(95, result.bloodOxygenPercent)
        assertEquals(Spo2Class.NORMAL, result.spo2Class)
        assertEquals(DeviationFromBaseline.NO_BASELINE, result.spo2ValueDeviationFromBaseline)
        assertEquals(99.0f, result.spo2QualityAveragePercent)
        assertEquals(66u, result.averageHeartRateBpm)
        assertEquals(79.97114f, result.heartRateVariabilityMs)
        assertEquals(DeviationFromBaseline.USUAL, result.spo2HrvDeviationFromBaseline)
        assertEquals(18.13582f, result.altitudeMeters)
        assertEquals("2026-04-14 06:36:35", result.testTime)
    }

    @Test
    fun `mapSpo2TestProto() maps ABOVE_USUAL hrv deviation`() {
        val date = LocalDate.of(2026, 4, 14)
        val proto = PbSpo2TestResult.newBuilder()
            .setTimeZoneOffset(180)
            .setTestStatus(PbSpo2TestStatus.SPO2_TEST_PASSED)
            .setSpo2HrvDeviationFromBaseline(PbDeviationFromBaseline.DEVIATION_ABOVE_USUAL)
            .setBloodOxygenPercent(96)
            .buildPartial()

        val result = PolarTestUtils.mapSpo2TestProto(proto, date, "063751")

        assertEquals(DeviationFromBaseline.ABOVE_USUAL, result.spo2HrvDeviationFromBaseline)
        assertEquals(96, result.bloodOxygenPercent)
    }

    @Test
    fun `mapSpo2TestProto() returns null for optional fields when not set in proto`() {
        val date = LocalDate.of(2026, 4, 8)
        val proto = PbSpo2TestResult.newBuilder()
            .setTimeZoneOffset(180)
            .setTestStatus(PbSpo2TestStatus.SPO2_TEST_PASSED)
            .buildPartial()

        val result = PolarTestUtils.mapSpo2TestProto(proto, date, "083906")

        assertNull(result.bloodOxygenPercent)
        assertNull(result.spo2Class)
        assertNull(result.spo2ValueDeviationFromBaseline)
        assertNull(result.spo2QualityAveragePercent)
        assertNull(result.averageHeartRateBpm)
        assertNull(result.heartRateVariabilityMs)
        assertNull(result.spo2HrvDeviationFromBaseline)
        assertNull(result.altitudeMeters)
    }

    @Test
    fun `mapSpo2TestEntry() parses proto bytes and maps to PolarSpo2TestData`() {
        val date = LocalDate.of(2026, 4, 8)
        val protoBytes = ByteArrayOutputStream().apply {
            PbSpo2TestResult.newBuilder()
                .setRecordingDevice("0004BF3D")
                .setTimeZoneOffset(180)
                .setTestStatus(PbSpo2TestStatus.SPO2_TEST_PASSED)
                .setBloodOxygenPercent(97)
                .setAverageHeartRateBpm(63)
                .buildPartial()
                .writeTo(this)
        }.toByteArray()

        val entry = Spo2TestEntry(date = date, timeDirName = "083906", protoBytes = protoBytes)
        val result = PolarTestUtils.mapSpo2TestEntry(entry)

        assertEquals("0004BF3D", result.recordingDevice)
        assertEquals(97, result.bloodOxygenPercent)
        assertEquals(63u, result.averageHeartRateBpm)
        assertEquals("2026-04-08 08:39:06", result.testTime)
    }

    @Test
    fun `spo2 golden vectors map proto fields to public model`() {
        loadSpo2GoldenVectors()
            .filterNot { vector ->
                vector.getAsJsonObject("platformExpectations")
                    .getAsJsonObject("android")
                    .has("skipReason")
            }
            .forEach { vector ->
                val caseId = vector.get("id").asString
                val input = vector.getAsJsonObject("input")
                val proto = buildProtoFromVector(input.getAsJsonObject("proto"))
                val date = LocalDate.parse(input.get("date").asString)
                val timeDirName = input.get("timeDirName").asString
                val expected = inputExpectedForPlatform(vector, "android")

                val result = runCatching { PolarTestUtils.mapSpo2TestProto(proto, date, timeDirName) }

                if (expected.has("error")) {
                    assertTrue(caseId, result.isFailure)
                } else {
                    assertTrue(caseId, result.isSuccess)
                    assertSpo2Result(caseId, expected, result.getOrThrow())
                }
            }
    }

    @Test
    fun `spo2 golden vectors follow neutral KMP vector shape`() {
        loadSpo2GoldenVectors().forEach { vector ->
            val id = vector.get("id").asString
            assertTrue(id, vector.has("area"))
            assertTrue(id, vector.has("case"))
            assertTrue(id, vector.has("source"))
            assertTrue(id, vector.has("input"))
            assertTrue(id, vector.has("expected"))
            assertTrue(id, vector.has("platforms"))
            val input = vector.getAsJsonObject("input")
            assertTrue(id, input.has("date"))
            assertTrue(id, input.has("timeDirName"))
            assertTrue(id, input.has("proto"))
            val platforms = vector.getAsJsonObject("platforms")
            assertTrue(id, platforms.get("android").asBoolean)
            assertTrue(id, platforms.get("ios").asBoolean)
            assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    @Test
    fun `spo2 readiness manifest is pinned before model migration`() {
        val readiness = loadSpo2ReadinessManifest()
        val input = readiness.getAsJsonObject("input")
        val expected = readiness.getAsJsonObject("expected")
        val consumerTests = readiness.getAsJsonObject("consumerTests")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        assertEquals("spo2-readiness", readiness.get("id").asString)
        assertEquals("spo2Readiness", input.get("kind").asString)
        assertEquals(
            listOf(
                "sdk/spo2-test/full-passed-normal.json",
                "sdk/spo2-test/ios-trigger-automatic.json",
                "sdk/spo2-test/omitted-optionals.json",
                "sdk/spo2-test/unknown-spo2-class-platform-difference.json"
            ),
            policyVectorPaths
        )
        val expectedFamilies = listOf(
            "full-passed-normal-field-mapping",
            "optional-protobuf-presence-preservation",
            "empty-recording-device-normalization",
            "nullable-trigger-type-policy",
            "android-no-trigger-field-platform-reference",
            "ios-trigger-field-platform-reference",
            "unknown-spo2-class-boundary",
            "platform-spo2-vector-reference-gate",
            "compile-verification-gate"
        )
        assertEquals(expectedFamilies, requiredFamilies)
        assertEquals(expectedFamilies, coveredFamilies)
        assertEquals(
            "SPo2 model migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS SPo2 tests continue to reference the same vectors, optional protobuf presence and empty recording-device normalization remain covered, nullable triggerType policy remains explicit, unknown SPo2 class behavior is handled at a typed boundary before public model exposure, and the shared tests are compile-verified.",
            expected.get("commonDecision").asString
        )
        assertEquals(
            listOf("com.polar.sdk.api.model.utils.PolarTestUtilsTest"),
            consumerTests.getAsJsonArray("android").map { it.asString }
        )
        assertEquals(
            listOf("PolarTestUtilsTest"),
            consumerTests.getAsJsonArray("ios").map { it.asString }
        )
        assertEquals(
            listOf("com.polar.sharedtest.Spo2CommonPolicyTest"),
            consumerTests.getAsJsonArray("commonPrototype").map { it.asString }
        )
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    private fun requestFor(path: String): ByteArray =
        PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(PftpRequest.PbPFtpOperation.Command.GET)
            .setPath(path)
            .build()
            .toByteArray()

    private fun buildDirectory(vararg entryNames: String): ByteArrayOutputStream =
        ByteArrayOutputStream().apply {
            val entries = entryNames.map {
                PbPFtpEntry.newBuilder().setName(it).setSize(0L).buildPartial()
            }
            write(PbPFtpDirectory.newBuilder().addAllEntries(entries).buildPartial().toByteArray())
        }

    private fun buildProto(block: PbSpo2TestResult.Builder.() -> PbSpo2TestResult.Builder): ByteArrayOutputStream =
        ByteArrayOutputStream().apply {
            PbSpo2TestResult.newBuilder().block().buildPartial().writeTo(this)
        }

    private fun buildProtoFromVector(protoFields: JsonObject): PbSpo2TestResult {
        val builder = PbSpo2TestResult.newBuilder()
        if (protoFields.has("recordingDevice")) builder.recordingDevice = protoFields.get("recordingDevice").asString
        if (protoFields.has("timeZoneOffsetMinutes")) builder.timeZoneOffset = protoFields.get("timeZoneOffsetMinutes").asInt
        if (protoFields.has("testStatus")) builder.setTestStatusValue(protoFields.get("testStatus").asInt)
        if (protoFields.has("bloodOxygenPercent")) builder.bloodOxygenPercent = protoFields.get("bloodOxygenPercent").asInt
        if (protoFields.has("spo2Class")) builder.setSpo2ClassValue(protoFields.get("spo2Class").asInt)
        if (protoFields.has("spo2ValueDeviationFromBaseline")) {
            builder.setSpo2ValueDeviationFromBaselineValue(protoFields.get("spo2ValueDeviationFromBaseline").asInt)
        }
        if (protoFields.has("spo2QualityAveragePercent")) builder.spo2QualityAveragePercent = protoFields.get("spo2QualityAveragePercent").asFloat
        if (protoFields.has("averageHeartRateBpm")) builder.averageHeartRateBpm = protoFields.get("averageHeartRateBpm").asInt
        if (protoFields.has("heartRateVariabilityMs")) builder.heartRateVariabilityMs = protoFields.get("heartRateVariabilityMs").asFloat
        if (protoFields.has("spo2HrvDeviationFromBaseline")) {
            builder.setSpo2HrvDeviationFromBaselineValue(protoFields.get("spo2HrvDeviationFromBaseline").asInt)
        }
        if (protoFields.has("altitudeMeters")) builder.altitudeMeters = protoFields.get("altitudeMeters").asFloat
        return builder.buildPartial()
    }

    private fun assertSpo2Result(caseId: String, expected: JsonObject, actual: PolarSpo2TestData) {
        assertNullableString(caseId, expected, "recordingDevice", actual.recordingDevice)
        assertNullableString(caseId, expected, "testTime", actual.testTime)
        assertNullableInt(caseId, expected, "timeZoneOffsetMinutes", actual.timeZoneOffsetMinutes)
        assertNullableString(caseId, expected, "testStatus", actual.testStatus?.name)
        assertNullableInt(caseId, expected, "bloodOxygenPercent", actual.bloodOxygenPercent)
        assertNullableString(caseId, expected, "spo2Class", actual.spo2Class?.name)
        assertNullableString(caseId, expected, "spo2ValueDeviationFromBaseline", actual.spo2ValueDeviationFromBaseline?.name)
        assertNullableFloat(caseId, expected, "spo2QualityAveragePercent", actual.spo2QualityAveragePercent)
        assertNullableLong(caseId, expected, "averageHeartRateBpm", actual.averageHeartRateBpm?.toLong())
        assertNullableFloat(caseId, expected, "heartRateVariabilityMs", actual.heartRateVariabilityMs)
        assertNullableString(caseId, expected, "spo2HrvDeviationFromBaseline", actual.spo2HrvDeviationFromBaseline?.name)
        assertNullableFloat(caseId, expected, "altitudeMeters", actual.altitudeMeters)
    }

    private fun assertNullableString(caseId: String, expected: JsonObject, key: String, actual: String?) {
        if (!expected.has(key)) return
        if (expected.get(key).isJsonNull) {
            assertNull("$caseId $key", actual)
        } else {
            assertEquals("$caseId $key", expected.get(key).asString, actual)
        }
    }

    private fun assertNullableInt(caseId: String, expected: JsonObject, key: String, actual: Int?) {
        if (!expected.has(key)) return
        if (expected.get(key).isJsonNull) {
            assertNull("$caseId $key", actual)
        } else {
            assertEquals("$caseId $key", expected.get(key).asInt, actual)
        }
    }

    private fun assertNullableLong(caseId: String, expected: JsonObject, key: String, actual: Long?) {
        if (!expected.has(key)) return
        if (expected.get(key).isJsonNull) {
            assertNull("$caseId $key", actual)
        } else {
            assertEquals("$caseId $key", expected.get(key).asLong, actual)
        }
    }

    private fun assertNullableFloat(caseId: String, expected: JsonObject, key: String, actual: Float?) {
        if (!expected.has(key)) return
        if (expected.get(key).isJsonNull) {
            assertNull("$caseId $key", actual)
        } else {
            assertNotNull("$caseId $key", actual)
            assertEquals("$caseId $key", expected.get(key).asFloat, actual!!, 0.00001f)
        }
    }

    private fun inputExpectedForPlatform(vector: JsonObject, platform: String): JsonObject {
        return vector.getAsJsonObject("platformExpectations").getAsJsonObject(platform)
    }

    private fun loadSpo2GoldenVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/spo2-test")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
            .filterNot { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString == "spo2Readiness" }
    }

    private fun loadSpo2ReadinessManifest(): JsonObject {
        val vectorFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/spo2-test/spo2-readiness.json")
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
