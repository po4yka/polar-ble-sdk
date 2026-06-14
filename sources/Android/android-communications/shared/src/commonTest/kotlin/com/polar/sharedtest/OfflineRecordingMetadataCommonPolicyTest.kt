package com.polar.sharedtest

import com.polar.shared.sdk.PolarOfflineRecordingModels
import com.polar.shared.sdk.PolarOfflineRecordingMeasurementType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfflineRecordingMetadataCommonPolicyTest {
    @Test
    fun offlineRecordingFilenameGoldenVectorDefinesExecutableCommonTypePolicy() {
        val vector = loadGoldenVectorText("sdk/offline-recording/filename-mapping.json")
        val cases = vector.objectValue("input").objectArray("cases")
        cases.forEach { item ->
            val fileName = item.stringValue("fileName")
            val error = item.optionalStringValue("error")
            if (error != null) {
                assertEquals("unknownOfflineFile", error, fileName)
                assertEquals(null, measurementTypeOrNull(fileName), fileName)
            } else {
                val expected = item.objectValue("measurementType")
                assertEquals(expected.stringValue("android"), PolarOfflineRecordingModels.measurementTypeFromFileName(fileName).name, fileName)
                assertTrue(expected.stringValue("ios").isNotEmpty(), "$fileName keeps iOS public enum expectation")
            }
        }
        assertEquals("strip-split-file-index-and-map-known-rec-filenames", vector.objectValue("expected").stringValue("policy"), vector.stringValue("id"))
    }

    @Test
    fun offlineRecordingPmdFilesGoldenVectorDefinesExecutableCommonGroupingPolicy() {
        val vector = loadGoldenVectorText("sdk/offline-recording/pmdfiles-v2-grouping.json")
        val grouped = PolarOfflineRecordingModels.parsePmdFilesV2(vector.objectValue("input").stringValue("pmdFilesTxt"))
        val expected = vector.objectValue("expected").objectArray("entries")

        assertEquals(expected.size, grouped.size, vector.stringValue("id"))
        expected.forEachIndexed { index, expectedEntry ->
            val actual = grouped[index]
            assertEquals(expectedEntry.stringValue("type"), actual.type, "entry $index type")
            assertEquals(expectedEntry.stringValue("androidPath"), actual.androidPath, "entry $index Android representative path")
            assertEquals(expectedEntry.stringValue("iosPath"), actual.iosPath, "entry $index iOS representative path")
            assertEquals(expectedEntry.intValue("size").toLong(), actual.size, "entry $index size")
            assertEquals(expectedEntry.stringValue("dateTime"), actual.dateTime, "entry $index dateTime")
        }

        val commonDecision = vector.objectValue("platformExpectations").objectValue("commonDecision")
        assertEquals("ignore-zero-size-recording-parts", commonDecision.stringValue("zeroSizePolicy"), vector.stringValue("id"))
        assertEquals("ignore-unknown-file-types-and-unparseable-date-or-time-paths", commonDecision.stringValue("invalidEntryPolicy"), vector.stringValue("id"))
        assertEquals("android-normalizes-split-index-to-base-rec-path-while-ios-keeps-first-split-file-path", commonDecision.stringValue("representativePathPolicy"), vector.stringValue("id"))
    }

    @Test
    fun offlineRecordingTriggerGoldenVectorDefinesExecutableCommonModelProjectionPolicy() {
        val vector = loadGoldenVectorText("sdk/offline-recording/trigger-mapping.json")
        val input = vector.objectValue("input")
        val polarToPmd = input.objectValue("polarToPmd")
        val pmdTriggers = polarToPmd.objectValue("input").objectArray("features").map { feature ->
            TriggerConfig(
                type = pmdType(feature.stringValue("type")),
                status = "TRIGGER_ENABLED",
                settings = feature.optionalObjectValue("selectedSettings")
            )
        }
        val expectedTriggers = polarToPmd.objectValue("expected").objectArray("triggers")
        assertEquals(expectedTriggers.size, pmdTriggers.size, vector.stringValue("id"))
        expectedTriggers.forEachIndexed { index, expected ->
            assertEquals(expected.objectValue("type").stringValue("android"), pmdTriggers[index].type, "trigger $index type")
            assertEquals(expected.objectValue("status").stringValue("android"), pmdTriggers[index].status, "trigger $index status")
        }

        val pmdToPolar = input.objectValue("pmdToPolar")
        val converted = convertEnabledTriggers(pmdToPolar.objectValue("input").objectArray("triggers"))
        val expected = pmdToPolar.objectValue("expected")
        assertEquals(expected.objectArray("features").map { it.stringValue("type") }, converted.features.map { it.type }, vector.stringValue("id"))
        assertEquals(expected.stringArrayValue("excludedFeatures"), converted.excludedFeatures, vector.stringValue("id"))
        assertEquals("offline-trigger-mapping", vector.objectValue("expected").stringValue("policy"), vector.stringValue("id"))
    }

    @Test
    fun offlineRecordingMetadataReadinessManifestNamesEverySharedContractBehaviorFamily() {
        val vector = loadGoldenVectorText("sdk/offline-recording/metadata-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumers = vector.objectValue("consumerTests")
        val platforms = vector.objectValue("platforms")
        assertEquals("offline-recording-metadata-readiness", vector.stringValue("id"))
        assertEquals("sdk.offline-recording", vector.stringValue("area"))
        assertEquals("offline_recording_metadata_readiness", vector.stringValue("case"))
        assertEquals("offlineRecordingMetadataReadiness", input.stringValue("kind"))
        assertEquals(metadataVectors, input.stringArrayValue("policyVectorPaths"))
        assertEquals(requiredMetadataFamilies, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(requiredMetadataFamilies, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(OFFLINE_RECORDING_METADATA_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(offlineRecordingMetadataAndroidConsumers, consumers.stringArrayValue("android"))
        assertEquals(offlineRecordingMetadataIosConsumers, consumers.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.OfflineRecordingMetadataCommonPolicyTest"), consumers.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private fun measurementTypeOrNull(fileName: String): String? {
        return runCatching { PolarOfflineRecordingModels.measurementTypeFromFileName(fileName).name }.getOrNull()
    }

    private fun pmdFilesTypeOrNull(fileName: String): String? {
        return when (measurementTypeOrNull(fileName)) {
            "OFFLINE_HR" -> "HR"
            "SKIN_TEMP" -> "SKIN_TEMPERATURE"
            else -> measurementTypeOrNull(fileName)
        }
    }

    private fun pmdType(publicType: String): String {
        return if (publicType == "HR") PolarOfflineRecordingMeasurementType.OFFLINE_HR.name else publicType
    }

    private fun publicType(pmdType: String): String {
        return if (pmdType == "OFFLINE_HR") "HR" else pmdType
    }

    private fun convertEnabledTriggers(triggers: List<String>): ConvertedTriggers {
        val features = mutableListOf<Feature>()
        val excluded = mutableListOf<String>()
        triggers.forEach { trigger ->
            val type = trigger.objectValue("type").stringValue("android")
            val publicType = publicType(type)
            when (trigger.objectValue("status").stringValue("android")) {
                "TRIGGER_ENABLED" -> features += Feature(type = publicType)
                "TRIGGER_DISABLED" -> excluded += publicType
            }
        }
        return ConvertedTriggers(features = features, excludedFeatures = excluded)
    }

    private fun String.optionalObjectValue(field: String): String? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val objectStart = indexOf('{', fieldIndex)
        if (objectStart < 0) return null
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.balancedEnd(start: Int, open: Char, close: Char): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until length) {
            val char = this[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = inString
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (!inString && char == open) depth += 1
            if (!inString && char == close) {
                depth -= 1
                if (depth == 0) return index
            }
        }
        error("Unbalanced $open$close block")
    }

    private data class TriggerConfig(
        val type: String,
        val status: String,
        val settings: String?
    )

    private data class Feature(
        val type: String
    )

    private data class ConvertedTriggers(
        val features: List<Feature>,
        val excludedFeatures: List<String>
    )

    private companion object {
        val metadataVectors = listOf(
            "sdk/offline-recording/filename-mapping.json",
            "sdk/offline-recording/pmdfiles-v2-grouping.json",
            "sdk/offline-recording/trigger-mapping.json"
        )
        val requiredMetadataFamilies = listOf(
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
        val offlineRecordingMetadataAndroidConsumers = listOf(
            "com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingUtilityTest",
            "com.polar.sdk.impl.utils.PolarDataUtilsTest",
            "com.polar.sdk.api.model.utils.PolarOfflineRecordingUtilsTest"
        )
        val offlineRecordingMetadataIosConsumers = listOf(
            "OfflineRecordingUtilsTest",
            "PolarDataUtilsTest",
            "PolarOfflineRecordingUtilsTest"
        )
        const val OFFLINE_RECORDING_METADATA_READINESS_COMMON_DECISION = "Offline recording metadata shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS metadata tests continue to reference the same vectors, filename classification, split-file normalization, invalid filename handling, PMDFILES grouping, zero-size and invalid-entry filtering, representative path policy, trigger model projection, disabled-trigger filtering, and compile verification remain explicit before production metadata mapping moves."
    }
}
