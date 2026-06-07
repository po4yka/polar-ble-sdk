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
}
