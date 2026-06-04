package com.polar.androidcommunications.api.ble.model.offlinerecording

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingUtility.mapOfflineRecordingFileNameToMeasurementType
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.FileReader

class OfflineRecordingUtilityTest {

    @Test
    fun `mapOfflineRecordingFileNameToMeasurementType() maps file names to correct measurement types`() {
        Assert.assertEquals(
            PmdMeasurementType.ACC,
            mapOfflineRecordingFileNameToMeasurementType("ACC.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.GYRO,
            mapOfflineRecordingFileNameToMeasurementType("GYRO.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.MAGNETOMETER,
            mapOfflineRecordingFileNameToMeasurementType("MAG.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.PPG,
            mapOfflineRecordingFileNameToMeasurementType("PPG.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.PPI,
            mapOfflineRecordingFileNameToMeasurementType("PPI.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.OFFLINE_HR,
            mapOfflineRecordingFileNameToMeasurementType("HR.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.ACC,
            mapOfflineRecordingFileNameToMeasurementType("ACC0.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.GYRO,
            mapOfflineRecordingFileNameToMeasurementType("GYRO5.REC")
        )
        Assert.assertEquals(
            PmdMeasurementType.MAGNETOMETER,
            mapOfflineRecordingFileNameToMeasurementType("MAG18.REC")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mapOfflineRecordingFileNameToMeasurementType() throws IllegalArgumentException if file name is not supported`() {
        mapOfflineRecordingFileNameToMeasurementType("INVALID.REC")
    }

    @Test
    fun `offline recording filename mapping golden vectors match Android behavior`() {
        val vector = loadOfflineRecordingVector("filename-mapping.json")
        vector.getAsJsonObject("input").getAsJsonArray("cases").forEach { element ->
            val case = element.asJsonObject
            val fileName = case.get("fileName").asString
            if (case.has("error")) {
                Assert.assertThrows(fileName, IllegalArgumentException::class.java) {
                    mapOfflineRecordingFileNameToMeasurementType(fileName)
                }
            } else {
                Assert.assertEquals(fileName, PmdMeasurementType.valueOf(case.getAsJsonObject("measurementType").get("android").asString), mapOfflineRecordingFileNameToMeasurementType(fileName))
            }
        }
    }

    @Test
    fun `offline recording filename mapping golden vector follows neutral KMP vector shape`() {
        assertNeutralKmpVectorShape(loadOfflineRecordingVector("filename-mapping.json"), "filename-mapping.json")
    }

    @Test
    fun `offline recording metadata readiness manifest is pinned before metadata migration`() {
        val readiness = loadOfflineRecordingVector("metadata-readiness.json")
        val input = readiness.getAsJsonObject("input")
        val expected = readiness.getAsJsonObject("expected")
        val consumerTests = readiness.getAsJsonObject("consumerTests")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        Assert.assertEquals("offline-recording-metadata-readiness", readiness.get("id").asString)
        Assert.assertEquals("offlineRecordingMetadataReadiness", input.get("kind").asString)
        Assert.assertEquals(OFFLINE_RECORDING_METADATA_POLICY_VECTOR_PATHS, policyVectorPaths)
        Assert.assertEquals(OFFLINE_RECORDING_METADATA_READINESS_FAMILIES, requiredFamilies)
        Assert.assertEquals(OFFLINE_RECORDING_METADATA_READINESS_FAMILIES, coveredFamilies)
        Assert.assertEquals(OFFLINE_RECORDING_METADATA_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        Assert.assertEquals(listOf("com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingUtilityTest", "com.polar.sdk.impl.utils.PolarDataUtilsTest", "com.polar.sdk.api.model.utils.PolarOfflineRecordingUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("OfflineRecordingUtilsTest", "PolarDataUtilsTest", "PolarOfflineRecordingUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.OfflineRecordingMetadataCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun assertNeutralKmpVectorShape(vector: JsonObject, id: String) {
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

    private fun loadOfflineRecordingVector(fileName: String): JsonObject {
        FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/offline-recording/$fileName")).use { reader ->
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

    private companion object {
        val OFFLINE_RECORDING_METADATA_POLICY_VECTOR_PATHS = listOf(
            "sdk/offline-recording/filename-mapping.json",
            "sdk/offline-recording/pmdfiles-v2-grouping.json",
            "sdk/offline-recording/trigger-mapping.json"
        )

        val OFFLINE_RECORDING_METADATA_READINESS_FAMILIES = listOf(
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

        const val OFFLINE_RECORDING_METADATA_READINESS_COMMON_DECISION = "Offline recording metadata migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS metadata tests continue to reference the same vectors, filename classification, split-file normalization, invalid filename handling, PMDFILES grouping, zero-size and invalid-entry filtering, representative path policy, trigger model projection, disabled-trigger filtering, and compile verification remain explicit before production metadata mapping moves."
    }
}
