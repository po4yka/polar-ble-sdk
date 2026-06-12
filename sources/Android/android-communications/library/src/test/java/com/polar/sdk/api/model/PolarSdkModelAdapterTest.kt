package com.polar.sdk.api.model

import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserDeviceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PolarSdkModelAdapterTest {
    @Test
    fun `disk space projection routes through sdk model adapter`() {
        val planned = PolarSdkModelAdapter.diskSpace(
            fragmentSize = 512L,
            totalFragments = 100L,
            freeFragments = 25L
        )

        assertEquals(51_200L, planned.totalSpace)
        assertEquals(12_800L, planned.freeSpace)
    }

    @Test
    fun `device uuid construction routes through sdk model adapter`() {
        assertEquals("0e030000-0084-0000-0000-000089643A20", PolarSdkModelAdapter.uuidFromDeviceId("89643A20"))
    }

    @Test
    fun `advertisement local name and manufacturer payload parsing route through sdk model adapter`() {
        val localName = PolarSdkModelAdapter.parseAdvertisementLocalName("Polar GritX Pro aa123459", "Polar")
        val manufacturerData = byteArrayOf(
            0x6b.toByte(), 0x00.toByte(),
            0x72.toByte(), 0x08.toByte(), 0x97.toByte(), 0xc9.toByte(), 0xc3.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x7a.toByte(), 0x01.toByte(), 0x03.toByte(), 0x33.toByte(),
            0x00.toByte(), 0x00.toByte()
        )

        assertEquals("GritX Pro", localName.deviceType)
        assertEquals("aa123459", localName.deviceId)
        assertEquals("GritX Pro", PolarSdkModelAdapter.advertisementDeviceModelNameFromLocalName("Polar GritX Pro aa123459", "Polar"))
        assertEquals(true, PolarSdkModelAdapter.isValidAdvertisementLocalName("Polar GritX Pro aa123459", "Polar"))
        assertEquals(false, PolarSdkModelAdapter.isValidAdvertisementLocalName("Custom GritX Pro aa123459", "Polar"))
        assertEquals(listOf(byteArrayOf(0x33.toByte(), 0x00.toByte(), 0x00.toByte()).toList()), PolarSdkModelAdapter.polarManufacturerHrPayloads(manufacturerData).map { it.toList() })
        assertEquals(emptyList<List<Byte>>(), PolarSdkModelAdapter.polarManufacturerHrPayloads(byteArrayOf(0x6b.toByte(), 0x00.toByte(), 0x40.toByte())).map { it.toList() })
    }

    @Test
    fun `d2h notification type lookup routes through sdk model adapter`() {
        assertEquals("EXERCISE_STATUS", PolarSdkModelAdapter.d2hNotificationTypeName(19))
        assertNull(PolarSdkModelAdapter.d2hNotificationTypeName(99))
    }

    @Test
    fun `sd log enum lookups route through sdk model adapter`() {
        assertEquals("LOG_TRIGGER_EXERCISE", PolarSdkModelAdapter.sdLogTriggerName(2))
        assertNull(PolarSdkModelAdapter.sdLogTriggerName(3))
        assertEquals("MAG_LOG_100HZ", PolarSdkModelAdapter.sdLogMagnetometerFrequencyName(3))
        assertNull(PolarSdkModelAdapter.sdLogMagnetometerFrequencyName(0))
    }

    @Test
    fun `first time use enum lookups route through sdk model adapter`() {
        assertEquals("FEMALE", PolarSdkModelAdapter.firstTimeUseGenderName(2))
        assertEquals(1, PolarSdkModelAdapter.firstTimeUseGenderValue("MALE"))
        assertNull(PolarSdkModelAdapter.firstTimeUseGenderValue("UNKNOWN"))
        assertEquals(50, PolarSdkModelAdapter.firstTimeUseTrainingBackgroundValue(50))
        assertNull(PolarSdkModelAdapter.firstTimeUseTrainingBackgroundValue(70))
        assertEquals(3, PolarSdkModelAdapter.firstTimeUseTypicalDayValue(3))
        assertNull(PolarSdkModelAdapter.firstTimeUseTypicalDayValue(4))
    }

    @Test
    fun `watch face complication lookup routes through sdk model adapter`() {
        val id = PolarSdkModelAdapter.watchFaceComplicationId("ecg-complication")

        assertEquals("ECG", PolarSdkModelAdapter.watchFaceComplicationName(id))
        assertNull(PolarSdkModelAdapter.watchFaceComplicationName(Int.MIN_VALUE))
    }

    @Test
    fun `activity sleep and exercise enum lookups route through sdk model adapter`() {
        assertEquals("RUNNING", PolarSdkModelAdapter.exerciseSportProfileName(1))
        assertEquals("UNKNOWN", PolarSdkModelAdapter.exerciseSportProfileName(Int.MAX_VALUE))
        assertEquals("WAKE", PolarSdkModelAdapter.sleepWakeStateName(-2))
        assertNull(PolarSdkModelAdapter.sleepWakeStateName(99))
        assertEquals("SLEPT_WELL", PolarSdkModelAdapter.sleepRatingName(4))
        assertEquals("LIGHT", PolarSdkModelAdapter.activityClassName(3))
        assertEquals("TRIGGER_TYPE_TIMED", PolarSdkModelAdapter.automaticHrTriggerName(3))
        assertEquals("RESPONDING_WELL_CAN_CONTINUE", PolarSdkModelAdapter.dailyBalanceFeedbackName(6))
        assertEquals("RECOVERED_READY_FOR_SPEED_AND_STRENGTH_TRAINING", PolarSdkModelAdapter.trainingReadinessName(3))
    }

    @Test
    fun `ppi enum lookups route through sdk model adapter`() {
        val status = PolarSdkModelAdapter.ppiStatusNames(0b111)

        assertEquals("TRIGGER_TYPE_MANUAL", PolarSdkModelAdapter.ppiSampleTriggerName(2))
        assertEquals("SKIN_CONTACT_DETECTED", status?.skinContact)
        assertEquals("MOVING_DETECTED", status?.movement)
        assertEquals("INTERVAL_DENOTES_OFFLINE_PERIOD", status?.intervalStatus)
        assertEquals("NO_SKIN_CONTACT", PolarSdkModelAdapter.ppiSkinContactName(0))
        assertEquals("NO_MOVING_DETECTED", PolarSdkModelAdapter.ppiMovementName(0))
        assertEquals("INTERVAL_IS_ONLINE", PolarSdkModelAdapter.ppiIntervalStatusName(0))
    }

    @Test
    fun `user device settings field projection routes through sdk model adapter`() {
        val fields = PolarSdkModelAdapter.userDeviceSettingsFields(
            deviceLocation = 3,
            usbConnectionMode = true,
            automaticTrainingDetectionMode = false,
            automaticTrainingDetectionSensitivity = 77,
            minimumTrainingDurationSeconds = 300,
            telemetryEnabled = true,
            autosFilesEnabled = true
        )
        val serialized = PolarSdkModelAdapter.serializeUserDeviceSettingsFields(fields)
        val parsed = PolarSdkModelAdapter.parseUserDeviceSettingsFields(
            deviceLocation = serialized.deviceLocation,
            usbConnectionMode = serialized.usbConnectionMode,
            automaticTrainingDetectionMode = serialized.automaticTrainingDetectionMode,
            automaticTrainingDetectionSensitivity = serialized.automaticTrainingDetectionSensitivity,
            minimumTrainingDurationSeconds = serialized.minimumTrainingDurationSeconds,
            telemetryEnabled = true,
            autosFilesEnabled = serialized.autosFilesEnabled
        )

        assertEquals("WRIST_RIGHT", PolarSdkModelAdapter.userDeviceSettingsDeviceLocationName(3))
        assertEquals(2, PolarSdkModelAdapter.userDeviceSettingsUsbConnectionModeValue(serialized.usbConnectionMode!!))
        assertEquals(0, PolarSdkModelAdapter.userDeviceSettingsAutomaticTrainingDetectionModeValue(serialized.automaticTrainingDetectionMode!!))
        assertEquals("ALWAYS_ON", PolarSdkModelAdapter.userDeviceSettingsAutomaticMeasurementStateName(true))
        assertEquals(fields, parsed)
    }

    @Test
    fun `user device settings protobuf bytes route through shared sdk model adapter`() {
        val fields = PolarSdkModelAdapter.userDeviceSettingsFields(
            deviceLocation = 2,
            usbConnectionMode = true,
            automaticTrainingDetectionMode = true,
            automaticTrainingDetectionSensitivity = 75,
            minimumTrainingDurationSeconds = 300,
            telemetryEnabled = true,
            autosFilesEnabled = true
        )
        val timestamp = PolarSdkModelAdapter.PlannedUserDeviceSettingsTimestamp(
            year = 2026,
            month = 5,
            day = 28,
            hour = 12,
            minute = 0,
            seconds = 0,
            millis = 0,
            trusted = true
        )

        val sharedBytes = PolarSdkModelAdapter.buildUserDeviceSettingsBytes(fields, timestamp, includeTelemetry = true)
        val generated = PbUserDeviceSettings.parseFrom(sharedBytes)
        val parsed = PolarSdkModelAdapter.parseUserDeviceSettingsBytes(sharedBytes)
        val legacyAndroidBytes = PolarSdkModelAdapter.buildUserDeviceSettingsBytes(fields, timestamp, includeTelemetry = false)
        val legacyAndroidGenerated = PbUserDeviceSettings.parseFrom(legacyAndroidBytes)

        assertEquals(fields, parsed)
        assertEquals(2, generated.generalSettings.deviceLocation.number)
        assertEquals("ON", generated.usbConnectionSettings.mode.name)
        assertEquals("ON", generated.automaticMeasurementSettings.automaticTrainingDetectionSettings.state.name)
        assertEquals(75, generated.automaticMeasurementSettings.automaticTrainingDetectionSettings.sensitivity)
        assertEquals(300, generated.automaticMeasurementSettings.automaticTrainingDetectionSettings.minimumTrainingDurationSeconds)
        assertEquals(true, generated.telemetrySettings.telemetryEnabled)
        assertEquals(false, legacyAndroidGenerated.hasTelemetrySettings())
    }

    @Test
    fun `spo2 enum lookups route through sdk model adapter`() {
        assertEquals("normal", PolarSdkModelAdapter.spo2ClassName(3))
        assertNull(PolarSdkModelAdapter.spo2ClassName(99))
        assertEquals("automatic", PolarSdkModelAdapter.spo2TriggerTypeName(1))
        assertEquals("passed", PolarSdkModelAdapter.spo2TestStatusName(0))
        assertEquals("aboveUsual", PolarSdkModelAdapter.spo2DeviationFromBaselineName(3))
    }

    @Test
    fun `training session file lookup routes through sdk model adapter`() {
        assertEquals("BASE.BPB", PolarSdkModelAdapter.trainingSessionExerciseDataTypeFileName("EXERCISE_SUMMARY"))
        assertEquals("SAMPLES2.GZB", PolarSdkModelAdapter.trainingSessionExerciseDataTypeFileName("SAMPLES_ADVANCED_FORMAT_GZIP"))
        assertNull(PolarSdkModelAdapter.trainingSessionExerciseDataTypeFileName("UNKNOWN"))
    }

    @Test
    fun `skin temperature projection routes through sdk model adapter`() {
        val sample = PolarSdkModelAdapter.PlannedSkinTemperatureSample(
            recordingTimeDeltaMs = 1_000L,
            temperature = 36.5f
        )
        val projection = PolarSdkModelAdapter.skinTemperature(
            sourceDeviceId = "device",
            measurementType = 1,
            sensorLocation = 1,
            samples = listOf(sample)
        )

        assertEquals("TM_SKIN_TEMPERATURE", PolarSdkModelAdapter.skinTemperatureMeasurementTypeName(1))
        assertNull(PolarSdkModelAdapter.skinTemperatureMeasurementTypeName(0))
        assertEquals("SL_DISTAL", PolarSdkModelAdapter.skinTemperatureSensorLocationName(1))
        assertEquals("device", projection.sourceDeviceId)
        assertEquals("TM_SKIN_TEMPERATURE", projection.measurementType)
        assertEquals("SL_DISTAL", projection.sensorLocation)
        assertEquals(listOf(sample), projection.samples)
    }

    @Test
    fun `rest service projection routes through sdk model adapter`() {
        val serviceList = PolarSdkModelAdapter.restServiceList(
            linkedMapOf(
                "sleep" to "/REST/SLEEP.API",
                "training" to "/REST/TRAINING.API"
            )
        )
        val description = PolarSdkModelAdapter.restServiceDescription(
            events = listOf("sleep_recording_state"),
            endpoints = listOf("stop_sleep_recording"),
            actions = linkedMapOf("subscribe" to "/REST/SLEEP.API?cmd=subscribe"),
            eventDescriptions = mapOf(
                "sleep_recording_state" to mapOf(
                    "details" to listOf("enabled"),
                    "triggers" to listOf("change")
                )
            )
        )

        assertEquals(listOf("sleep", "training"), serviceList.names)
        assertEquals(listOf("/REST/SLEEP.API", "/REST/TRAINING.API"), serviceList.paths)
        assertEquals(listOf("sleep_recording_state"), description.events)
        assertEquals(listOf("stop_sleep_recording"), description.endpoints)
        assertEquals(listOf("subscribe"), description.actionNames)
        assertEquals(listOf("/REST/SLEEP.API?cmd=subscribe"), description.actionPaths)
        assertEquals(listOf("enabled"), description.details["sleep_recording_state"])
        assertEquals(listOf("change"), description.triggers["sleep_recording_state"])
    }
}
