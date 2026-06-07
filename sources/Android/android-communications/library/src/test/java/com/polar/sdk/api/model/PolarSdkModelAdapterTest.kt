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
