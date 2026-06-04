package com.polar.sdk.api.model.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.impl.utils.PolarOfflineRecordingUtils
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileReader
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime

class PolarOfflineRecordingUtilsTest {

    private val mockClient = mockk<BlePsFtpClient>()

    @Test
    fun `listOfflineRecordingsV1 merges split REC files`() = runTest {
        val sampleEntries = listOf(
            Pair("/U/0/20250730/R/101010/ACC0.REC", 500120L),
            Pair("/U/0/20250730/R/101010/ACC1.REC", 500103L),
            Pair("/U/0/20250730/R/101010/ACC2.REC", 102325L),
            Pair("/U/0/20250730/R/101010/HR0.REC", 500000L),
            Pair("/U/0/20250730/R/101010/HR1.REC", 500050L),
            Pair("/U/0/20250730/R/101010/PPG0.REC", 300L)
        )

        val fetchRecursively: (BlePsFtpClient, String, (String) -> Boolean) -> Flow<Pair<String, Long>> =
            { _, _, _ -> sampleEntries.asFlow() }

        val emitted = mutableListOf<PolarOfflineRecordingEntry>()
        val job = launch {
            PolarOfflineRecordingUtils.listOfflineRecordingsV1(mockClient, fetchRecursively)
                .collect { emitted.add(it) }
        }
        job.join()

        val accEntries = emitted.filter { it.path.contains("ACC") }
        val hrEntries = emitted.filter { it.path.contains("HR") }
        val ppgEntries = emitted.filter { it.path.contains("PPG") }

        assert(accEntries.size == 1)
        assert(accEntries[0].size == 500120L + 500103L + 102325L)
        assert(accEntries[0].path.endsWith(".REC"))

        assert(hrEntries.size == 1)
        assert(hrEntries[0].size == 500000L + 500050L)
        assert(hrEntries[0].path.endsWith(".REC"))

        assert(ppgEntries.size == 1)
        assert(ppgEntries[0].size == 300L)
        assert(ppgEntries[0].path.endsWith(".REC"))

    }

    @Test
    fun `listOfflineRecordingsV1 does not return empty files`() = runTest {
        val sampleEntries = listOf(
            Pair("/U/0/20250730/R/101010/ACC0.REC", 500120L),
            Pair("/U/0/20250730/R/101010/ACC1.REC", 500103L),
            Pair("/U/0/20250730/R/101010/ACC2.REC", 0L),
            Pair("/U/0/20250730/R/101010/HR0.REC", 500000L),
            Pair("/U/0/20250730/R/101010/HR1.REC", 0L),
            Pair("/U/0/20250730/R/101010/PPG0.REC", 0L)
        )

        val fetchRecursively: (BlePsFtpClient, String, (String) -> Boolean) -> Flow<Pair<String, Long>> =
            { _, _, _ -> sampleEntries.asFlow() }

        val emitted = mutableListOf<PolarOfflineRecordingEntry>()
        val job = launch {
            PolarOfflineRecordingUtils.listOfflineRecordingsV1(mockClient, fetchRecursively)
                .collect { emitted.add(it) }
        }
        job.join()

        val accEntries = emitted.filter { it.path.contains("ACC") }
        val hrEntries = emitted.filter { it.path.contains("HR") }
        val ppgEntries = emitted.filter { it.path.contains("PPG") }

        assert(accEntries.size == 1)
        assert(accEntries[0].size == 500120L + 500103L)
        assert(accEntries[0].path.endsWith(".REC"))

        assert(hrEntries.size == 1)
        assert(hrEntries[0].size == 500000L)
        assert(hrEntries[0].path.endsWith(".REC"))

        assert(ppgEntries.isEmpty())

    }

    @Test
    fun `listOfflineRecordingsV2 merges split REC files`() = runTest {
        val pmdTxtContent = """
            500120 /U/0/20250730/R/101010/ACC0.REC
            500103 /U/0/20250730/R/101010/ACC1.REC
            102325 /U/0/20250730/R/101010/ACC2.REC
            500000 /U/0/20250730/R/101010/HR0.REC
            500050 /U/0/20250730/R/101010/HR1.REC
            300 /U/0/20250730/R/101010/PPG0.REC
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val emitted = PolarOfflineRecordingUtils.listOfflineRecordingsV2(pmdTxtContent)

        val accEntries = emitted.filter { it.path.contains("ACC") }
        val hrEntries = emitted.filter { it.path.contains("HR") }
        val ppgEntries = emitted.filter { it.path.contains("PPG") }

        assert(accEntries.size == 1)
        assert(accEntries[0].size == 500120L + 500103L + 102325L)
        assert(accEntries[0].path.endsWith(".REC"))

        assert(hrEntries.size == 1)
        assert(hrEntries[0].size == 500000L + 500050L)
        assert(hrEntries[0].path.endsWith(".REC"))

        assert(ppgEntries.size == 1)
        assert(ppgEntries[0].size == 300L)
        assert(ppgEntries[0].path.endsWith(".REC"))

    }

    @Test
    fun `listOfflineRecordingsV2 does not return empty files`() = runTest {
        val pmdTxtContent = """
            500120 /U/0/20250730/R/101010/ACC0.REC
            500103 /U/0/20250730/R/101010/ACC1.REC
            0 /U/0/20250730/R/101010/ACC2.REC
            500050 /U/0/20250730/R/101010/HR0.REC
            0 /U/0/20250730/R/101010/HR1.REC
            0 /U/0/20250730/R/101010/PPG0.REC
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val emitted = PolarOfflineRecordingUtils.listOfflineRecordingsV2(pmdTxtContent)

        val accEntries = emitted.filter { it.path.contains("ACC") }
        val hrEntries = emitted.filter { it.path.contains("HR") }
        val ppgEntries = emitted.filter { it.path.contains("PPG") }

        assert(accEntries.size == 1)
        assert(accEntries[0].size == 500120L + 500103L)
        assert(accEntries[0].path.endsWith(".REC"))

        assert(hrEntries.size == 1)
        assert(hrEntries[0].size == 500050L)
        assert(hrEntries[0].path.endsWith(".REC"))

        assert(ppgEntries.isEmpty())
    }

    @Test
    fun `offline recording PMDFILES golden vectors match Android behavior`() {
        val vector = loadOfflineRecordingVector("pmdfiles-v2-grouping.json")
        val emitted = PolarOfflineRecordingUtils.listOfflineRecordingsV2(
            vector.getAsJsonObject("input").get("pmdFilesTxt").asString.toByteArray(StandardCharsets.UTF_8)
        )

        assertOfflineRecordingEntries(emitted, vector.getAsJsonObject("expected"), platformPathKey = "androidPath")
    }

    @Test
    fun `offline recording golden vectors follow neutral KMP vector shape`() {
        loadOfflineRecordingVectors().forEach { vector ->
            val id = vector.get("id").asString
            assertTrue(id, vector.has("area"))
            assertTrue(id, vector.has("case"))
            assertTrue(id, vector.has("source"))
            assertTrue(id, vector.has("input"))
            assertTrue(id, vector.has("expected"))
            assertTrue(id, vector.has("platforms"))
            val platforms = vector.getAsJsonObject("platforms")
            assertTrue(id, platforms.get("android").asBoolean)
            assertTrue(id, platforms.get("ios").asBoolean)
            assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    @Test
    fun `offline recording metadata readiness manifest is pinned before migration`() {
        val readiness = loadOfflineRecordingVector("metadata-readiness.json")
        val input = readiness.getAsJsonObject("input")
        val expected = readiness.getAsJsonObject("expected")
        val consumerTests = readiness.getAsJsonObject("consumerTests")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        assertEquals("offline-recording-metadata-readiness", readiness.get("id").asString)
        assertEquals("offlineRecordingMetadataReadiness", input.get("kind").asString)
        assertEquals(
            listOf(
                "sdk/offline-recording/filename-mapping.json",
                "sdk/offline-recording/pmdfiles-v2-grouping.json",
                "sdk/offline-recording/trigger-mapping.json"
            ),
            policyVectorPaths
        )
        val expectedFamilies = listOf(
            "filename-to-measurement-type-mapping",
            "split-file-index-stripping",
            "invalid-filename-boundary",
            "pmdfiles-grouping",
            "zero-size-recording-filtering",
            "invalid-entry-filtering",
            "representative-path-platform-policy",
            "trigger-model-projection",
            "disabled-trigger-filtering",
            "platform-offline-recording-vector-reference-gate",
            "compile-verification-gate"
        )
        assertEquals(expectedFamilies, requiredFamilies)
        assertEquals(expectedFamilies, coveredFamilies)
        assertEquals(
            "Offline recording metadata migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS metadata tests continue to reference the same vectors, filename classification, split-file normalization, invalid filename handling, PMDFILES grouping, zero-size and invalid-entry filtering, representative path policy, trigger model projection, disabled-trigger filtering, and compile verification remain explicit before production metadata mapping moves.",
            expected.get("commonDecision").asString
        )
        assertEquals(
            listOf(
                "com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingUtilityTest",
                "com.polar.sdk.impl.utils.PolarDataUtilsTest",
                "com.polar.sdk.api.model.utils.PolarOfflineRecordingUtilsTest"
            ),
            consumerTests.getAsJsonArray("android").map { it.asString }
        )
        assertEquals(
            listOf(
                "OfflineRecordingUtilsTest",
                "PolarDataUtilsTest",
                "PolarOfflineRecordingUtilsTest"
            ),
            consumerTests.getAsJsonArray("ios").map { it.asString }
        )
        assertEquals(
            listOf("com.polar.sharedtest.OfflineRecordingMetadataCommonPolicyTest"),
            consumerTests.getAsJsonArray("commonPrototype").map { it.asString }
        )
    }

    private fun assertOfflineRecordingEntries(actual: List<PolarOfflineRecordingEntry>, expected: JsonObject, platformPathKey: String) {
        val expectedEntries = expected.getAsJsonArray("entries").map { it.asJsonObject }
        assertEquals("entry count", expectedEntries.size, actual.size)
        expectedEntries.forEach { expectedEntry ->
            val actualEntry = actual.firstOrNull { it.type.name == expectedEntry.get("type").asString } ?: error("Missing ${expectedEntry.get("type").asString}")
            assertEquals(expectedEntry.get(platformPathKey).asString, actualEntry.path)
            assertEquals(expectedEntry.get("size").asLong, actualEntry.size)
            assertEquals(LocalDateTime.parse(expectedEntry.get("dateTime").asString), actualEntry.date)
        }
    }

    private fun loadOfflineRecordingVector(fileName: String): JsonObject {
        FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/offline-recording/$fileName")).use { reader ->
            return JsonParser().parse(reader).asJsonObject
        }
    }

    private fun loadOfflineRecordingVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/offline-recording")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
            .filterNot { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString == "offlineRecordingMetadataReadiness" }
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
