package com.polar.sdk.api.model

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
