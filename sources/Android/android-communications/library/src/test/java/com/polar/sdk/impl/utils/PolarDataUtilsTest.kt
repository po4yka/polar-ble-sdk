package com.polar.sdk.impl.utils

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdOfflineRecTriggerMode
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdOfflineRecTriggerStatus
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdOfflineTrigger
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.GnssLocationData
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.GpsCoordinatesSample
import com.polar.sdk.api.model.GpsNMEASample
import com.polar.sdk.api.model.GpsSatelliteDilutionSample
import com.polar.sdk.api.model.GpsSatelliteSummarySample
import com.polar.sdk.api.model.PolarOfflineRecordingTrigger
import com.polar.sdk.api.model.PolarOfflineRecordingTriggerMode
import com.polar.sdk.api.model.PolarSensorSetting
import com.polar.sdk.api.model.SatelliteSummary
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader

class PolarDataUtilsTest {

    @Test
    fun `offline recording trigger golden vectors map polar trigger to pmd trigger`() {
        val vector = loadOfflineRecordingVector("trigger-mapping.json")
        val case = vector.getAsJsonObject("input").getAsJsonObject("polarToPmd")
        val polarTrigger = PolarOfflineRecordingTrigger(
            triggerMode = polarTriggerMode(case.getAsJsonObject("input").get("triggerMode").asString),
            triggerFeatures = case.getAsJsonObject("input").getAsJsonArray("features").associate { element ->
                val feature = element.asJsonObject
                polarDeviceDataType(feature.get("type").asString) to polarSensorSetting(feature.get("selectedSettings").nullableObject())
            }
        )

        val pmdTrigger = PolarDataUtils.mapPolarOfflineTriggerToPmdOfflineTrigger(polarTrigger)
        val expected = case.getAsJsonObject("expected")

        Assert.assertEquals(pmdTriggerMode(expected.getAsJsonObject("triggerMode").get("android").asString), pmdTrigger.triggerMode)
        expected.getAsJsonArray("triggers").forEach { element ->
            val trigger = element.asJsonObject
            val type = pmdMeasurementType(trigger.getAsJsonObject("type").get("android").asString)
            val actual = pmdTrigger.triggers[type] ?: error("Missing trigger $type")
            Assert.assertEquals(trigger.getAsJsonObject("status").get("android").asString, actual.first.name)
            assertSelectedSettings(trigger.get("selectedSettings").nullableObject(), actual.second)
        }
        Assert.assertEquals(expected.getAsJsonArray("triggers").size(), pmdTrigger.triggers.size)
    }

    @Test
    fun `offline recording trigger golden vectors map pmd trigger to polar trigger`() {
        val vector = loadOfflineRecordingVector("trigger-mapping.json")
        val case = vector.getAsJsonObject("input").getAsJsonObject("pmdToPolar")
        val pmdTrigger = PmdOfflineTrigger(
            triggerMode = pmdTriggerMode(case.getAsJsonObject("input").getAsJsonObject("triggerMode").get("android").asString),
            triggers = case.getAsJsonObject("input").getAsJsonArray("triggers").associate { element ->
                val trigger = element.asJsonObject
                pmdMeasurementType(trigger.getAsJsonObject("type").get("android").asString) to Pair(
                    PmdOfflineRecTriggerStatus.valueOf(trigger.getAsJsonObject("status").get("android").asString),
                    pmdAvailableSettings(trigger.get("availableSettings").nullableObject())
                )
            }
        )

        val polarTrigger = PolarDataUtils.mapPmdTriggerToPolarTrigger(pmdTrigger)
        val expected = case.getAsJsonObject("expected")

        Assert.assertEquals(polarTriggerMode(expected.get("triggerMode").asString), polarTrigger.triggerMode)
        expected.getAsJsonArray("features").forEach { element ->
            val feature = element.asJsonObject
            val type = polarDeviceDataType(feature.get("type").asString)
            Assert.assertTrue("Missing feature $type", polarTrigger.triggerFeatures.containsKey(type))
            assertPolarSettings(feature.get("settings").nullableObject(), polarTrigger.triggerFeatures[type])
        }
        expected.getAsJsonArray("excludedFeatures").forEach { element ->
            Assert.assertFalse(polarTrigger.triggerFeatures.containsKey(polarDeviceDataType(element.asString)))
        }
        Assert.assertEquals(expected.getAsJsonArray("features").size(), polarTrigger.triggerFeatures.size)
    }

    @Test
    fun `offline recording trigger golden vector follows neutral KMP vector shape`() {
        assertNeutralKmpVectorShape(loadOfflineRecordingVector("trigger-mapping.json"), "trigger-mapping.json")
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

    @Test
    fun `trigger runtime policy vector is pinned before runtime migration`() {
        val vector = loadOfflineRecordingVector("trigger-runtime-policy.json")
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val prototype = expected.getAsJsonObject("commonRuntimePrototype")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val scenarioIds = input.getAsJsonArray("scenarios").map { it.asJsonObject.get("id").asString }
        val expectedCaseIds = prototype.getAsJsonArray("cases").map { it.asJsonObject.get("id").asString }

        Assert.assertEquals("trigger-runtime-policy", vector.get("id").asString)
        Assert.assertEquals(TRIGGER_RUNTIME_SCENARIO_IDS, scenarioIds)
        Assert.assertEquals(TRIGGER_RUNTIME_SCENARIO_IDS, expectedCaseIds)
        Assert.assertEquals("executable shared commonTest", prototype.get("status").asString)
        Assert.assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePmdClientTest", "com.polar.sdk.impl.BDBleApiImplTest", "com.polar.sdk.impl.utils.PolarDataUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("BlePmdClientTest", "PolarBleApiImplTests", "PolarDataUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.OfflineTriggerRuntimePolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun `trigger runtime readiness manifest is pinned before runtime migration`() {
        val vector = loadOfflineRecordingVector("trigger-runtime-readiness.json")
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        Assert.assertEquals("trigger-runtime-readiness", vector.get("id").asString)
        Assert.assertEquals("offlineTriggerRuntimeReadiness", input.get("kind").asString)
        Assert.assertEquals("sdk/offline-recording/trigger-runtime-policy.json", input.get("policyVectorPath").asString)
        val expectedFamilies = listOf(
            "typed-set-mode",
            "status-read",
            "settings-write",
            "optional-secret-attachment",
            "get-transport-error",
            "set-mode-control-point-error",
            "status-read-transport-error",
            "settings-control-point-error",
            "enabled-feature-projection",
            "excluded-feature-projection",
            "platform-packet-split",
            "facade-error-mapping-deferred",
            "compile-verification-gate"
        )
        Assert.assertEquals(expectedFamilies, requiredFamilies)
        Assert.assertEquals(expectedFamilies, coveredFamilies)
        Assert.assertEquals(
            "Offline trigger runtime migration may proceed only after trigger-runtime-policy.json and this readiness manifest are executable from shared commonTest, platform facade tests continue to reference the same policy vector, packet-framing differences are preserved in adapters or reconciled explicitly, public facade error mapping is pinned, and the shared tests are compile-verified.",
            expected.get("commonDecision").asString
        )
        Assert.assertEquals(
            "executable shared commonTest runtime planning guard",
            expected.getAsJsonObject("commonRuntimePrototype").get("status").asString
        )
        Assert.assertEquals(
            "Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.",
            expected.getAsJsonObject("commonRuntimePrototype").get("reason").asString
        )
        Assert.assertEquals(listOf("com.polar.sdk.impl.utils.PolarDataUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("PolarDataUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.OfflineTriggerRuntimePolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun `GNSS location public model projection is delegated through shared KMP`() {
        val seenBand1 = GnssLocationData.GnssSatelliteSummary(1u, 2u, 3u, 4u, 5u, 6u, 7u, 8u, 9u, 10u)
        val usedBand1 = GnssLocationData.GnssSatelliteSummary(11u, 12u, 13u, 14u, 15u, 16u, 17u, 18u, 19u, 20u)
        val seenBand2 = GnssLocationData.GnssSatelliteSummary(21u, 22u, 23u, 24u, 25u, 26u, 27u, 28u, 29u, 30u)
        val usedBand2 = GnssLocationData.GnssSatelliteSummary(31u, 32u, 33u, 34u, 35u, 36u, 37u, 38u, 39u, 40u)
        val location = GnssLocationData().apply {
            gnssLocationDataSamples += GnssLocationData.GnssCoordinateSample(101uL, 60.123, 24.456, "2026-06-06T10:11:12.123", 12.3, 4.5f, 5.6f, 6.7f, 7.8f, 8.9f, 9.1f, true, -1, 255u)
            gnssLocationDataSamples += GnssLocationData.GnssSatelliteDilutionSample(202uL, 1.25f, -42, 7u, false)
            gnssLocationDataSamples += GnssLocationData.GnssSatelliteSummarySample(303uL, seenBand1, usedBand1, seenBand2, usedBand2, 99u)
            gnssLocationDataSamples += GnssLocationData.GnssGpsNMEASample(404uL, 1000u, 12u, 3u, "GPGGA")
        }

        val actual = PolarDataUtils.mapPMDClientLocationDataToPolarLocationData(location).samples

        val coordinates = actual[0] as GpsCoordinatesSample
        Assert.assertEquals(101L, coordinates.timeStamp)
        Assert.assertEquals(60.123, coordinates.latitude, 0.00001)
        Assert.assertEquals(24.456, coordinates.longitude, 0.00001)
        Assert.assertEquals("2026-06-06T10:11:12.123", coordinates.time)
        Assert.assertEquals(12.3, coordinates.cumulativeDistance, 0.00001)
        Assert.assertEquals(4.5f, coordinates.speed)
        Assert.assertEquals(5.6f, coordinates.usedAccelerationSpeed)
        Assert.assertEquals(6.7f, coordinates.coordinateSpeed)
        Assert.assertEquals(7.8f, coordinates.accelerationSpeedFactor)
        Assert.assertEquals(8.9f, coordinates.course)
        Assert.assertEquals(9.1f, coordinates.gpsChipSpeed)
        Assert.assertTrue(coordinates.fix)
        Assert.assertEquals(-1, coordinates.speedFlag)
        Assert.assertEquals(255u, coordinates.fusionState)
        val dilution = actual[1] as GpsSatelliteDilutionSample
        Assert.assertEquals(202L, dilution.timeStamp)
        Assert.assertEquals(1.25f, dilution.dilution)
        Assert.assertEquals(-42, dilution.altitude)
        Assert.assertEquals(7u, dilution.numberOfSatellites)
        Assert.assertFalse(dilution.fix)
        val summary = actual[2] as GpsSatelliteSummarySample
        Assert.assertEquals(303L, summary.timeStamp)
        Assert.assertEquals(seenBand1.toPolarSummary(), summary.seenSatelliteSummaryBand1)
        Assert.assertEquals(usedBand1.toPolarSummary(), summary.usedSatelliteSummaryBand1)
        Assert.assertEquals(seenBand2.toPolarSummary(), summary.seenSatelliteSummaryBand2)
        Assert.assertEquals(usedBand2.toPolarSummary(), summary.usedSatelliteSummaryBand2)
        Assert.assertEquals(99u, summary.maxSnr)
        val nmea = actual[3] as GpsNMEASample
        Assert.assertEquals(404L, nmea.timeStamp)
        Assert.assertEquals(1000u, nmea.measurementPeriod)
        Assert.assertEquals(3u.toUByte(), nmea.statusFlags)
        Assert.assertEquals("GPGGA", nmea.nmeaMessage)
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

    private fun assertSelectedSettings(expected: JsonObject?, actual: PmdSetting?) {
        if (expected == null || expected.isJsonNull) {
            Assert.assertNull(actual)
            return
        }
        val actualSelected = actual?.selected.orEmpty()
        expected.entrySet().forEach { entry ->
            Assert.assertEquals(entry.value.asInt, actualSelected[pmdSettingType(entry.key)])
        }
        Assert.assertEquals(expected.entrySet().size, actualSelected.size)
    }

    private fun assertPolarSettings(expected: JsonObject?, actual: PolarSensorSetting?) {
        if (expected == null || expected.isJsonNull) {
            Assert.assertNull(actual)
            return
        }
        val actualSettings = actual?.settings.orEmpty()
        expected.entrySet().forEach { entry ->
            val settingType = polarSettingType(entry.key)
            val expectedValues = entry.value.asJsonArray.map { it.asInt }.toSet()
            Assert.assertEquals(expectedValues, actualSettings[settingType])
        }
        Assert.assertEquals(expected.entrySet().size, actualSettings.size)
    }

    private fun GnssLocationData.GnssSatelliteSummary.toPolarSummary(): SatelliteSummary {
        return SatelliteSummary(
            gpsNbrOfSat = gpsNbrOfSat,
            gpsMaxSnr = gpsMaxSnr,
            glonassNbrOfSat = glonassNbrOfSat,
            glonassMaxSnr = glonassMaxSnr,
            galileoNbrOfSat = galileoNbrOfSat,
            galileoMaxSnr = galileoMaxSnr,
            beidouNbrOfSat = beidouNbrOfSat,
            beidouMaxSnr = beidouMaxSnr,
            nbrOfSat = nbrOfSat,
            snrTop5Avg = snrTop5Avg
        )
    }

    private fun polarSensorSetting(settings: JsonObject?): PolarSensorSetting? {
        if (settings == null || settings.isJsonNull) return null
        return PolarSensorSetting(settings.entrySet().associate { polarSettingType(it.key) to it.value.asInt })
    }

    private fun pmdAvailableSettings(settings: JsonObject?): PmdSetting? {
        if (settings == null || settings.isJsonNull) return null
        return PmdSetting(ByteArrayOutputStream().apply {
            settings.entrySet().forEach { entry ->
                val type = pmdSettingType(entry.key)
                val values = entry.value.asJsonArray.map { it.asInt }
                write(type.numVal)
                write(values.size)
                values.forEach { value ->
                    repeat(pmdSettingFieldSize(type)) { index ->
                        write(value shr (index * 8))
                    }
                }
            }
        }.toByteArray())
    }

    private fun polarTriggerMode(name: String): PolarOfflineRecordingTriggerMode = PolarOfflineRecordingTriggerMode.valueOf(name)

    private fun pmdTriggerMode(name: String): PmdOfflineRecTriggerMode = PmdOfflineRecTriggerMode.valueOf(name)

    private fun polarDeviceDataType(name: String): PolarBleApi.PolarDeviceDataType {
        return when (name) {
            "ACC" -> PolarBleApi.PolarDeviceDataType.ACC
            "PPG" -> PolarBleApi.PolarDeviceDataType.PPG
            "HR" -> PolarBleApi.PolarDeviceDataType.HR
            "GYRO" -> PolarBleApi.PolarDeviceDataType.GYRO
            else -> error("Unsupported Polar data type $name")
        }
    }

    private fun pmdMeasurementType(name: String): PmdMeasurementType {
        return when (name) {
            "ACC" -> PmdMeasurementType.ACC
            "PPG" -> PmdMeasurementType.PPG
            "OFFLINE_HR" -> PmdMeasurementType.OFFLINE_HR
            "GYRO" -> PmdMeasurementType.GYRO
            else -> error("Unsupported PMD measurement type $name")
        }
    }

    private fun polarSettingType(name: String): PolarSensorSetting.SettingType {
        return when (name) {
            "SAMPLE_RATE" -> PolarSensorSetting.SettingType.SAMPLE_RATE
            "RESOLUTION" -> PolarSensorSetting.SettingType.RESOLUTION
            else -> error("Unsupported Polar setting type $name")
        }
    }

    private fun pmdSettingType(name: String): PmdSetting.PmdSettingType {
        return when (name) {
            "SAMPLE_RATE" -> PmdSetting.PmdSettingType.SAMPLE_RATE
            "RESOLUTION" -> PmdSetting.PmdSettingType.RESOLUTION
            else -> error("Unsupported PMD setting type $name")
        }
    }

    private fun pmdSettingFieldSize(type: PmdSetting.PmdSettingType): Int {
        return when (type) {
            PmdSetting.PmdSettingType.SAMPLE_RATE -> 2
            PmdSetting.PmdSettingType.RESOLUTION -> 2
            else -> error("Unsupported PMD setting type $type")
        }
    }

    private fun loadOfflineRecordingVector(fileName: String): JsonObject {
        FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/offline-recording/$fileName")).use { reader ->
            return JsonParser().parse(reader).asJsonObject
        }
    }

    private fun JsonElement?.nullableObject(): JsonObject? {
        return if (this == null || isJsonNull) null else asJsonObject
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

        val TRIGGER_RUNTIME_SCENARIO_IDS = listOf(
            "set-trigger-success-with-secret",
            "set-trigger-mode-error",
            "set-trigger-status-read-error",
            "set-trigger-setting-error",
            "get-trigger-success",
            "get-trigger-transport-error"
        )
    }
}
