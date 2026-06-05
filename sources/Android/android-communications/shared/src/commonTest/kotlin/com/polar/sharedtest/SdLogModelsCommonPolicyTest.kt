package com.polar.sharedtest

import com.polar.shared.sdk.PolarSdLogMagnetometerFrequencyName
import com.polar.shared.sdk.PolarSdLogTriggerName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SdLogModelsCommonPolicyTest {
    @Test
    fun sdLogEnumMappingsPreservePublicNumericValues() {
        assertEquals("LOG_TRIGGER_SYSTEM", PolarSdLogTriggerName.fromValue(0)?.name)
        assertEquals("LOG_TRIGGER_FORCED", PolarSdLogTriggerName.fromValue(1)?.name)
        assertEquals("LOG_TRIGGER_EXERCISE", PolarSdLogTriggerName.fromValue(2)?.name)
        assertNull(PolarSdLogTriggerName.fromValue(3))

        assertEquals("MAG_LOG_10HZ", PolarSdLogMagnetometerFrequencyName.fromValue(1)?.name)
        assertEquals("MAG_LOG_50HZ", PolarSdLogMagnetometerFrequencyName.fromValue(2)?.name)
        assertEquals("MAG_LOG_100HZ", PolarSdLogMagnetometerFrequencyName.fromValue(3)?.name)
        assertNull(PolarSdLogMagnetometerFrequencyName.fromValue(0))
        assertNull(PolarSdLogMagnetometerFrequencyName.fromValue(4))
    }
}
