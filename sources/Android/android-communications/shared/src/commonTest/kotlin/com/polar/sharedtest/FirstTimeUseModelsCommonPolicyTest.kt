package com.polar.sharedtest

import com.polar.shared.sdk.PolarFirstTimeUseGenderName
import com.polar.shared.sdk.PolarFirstTimeUseTrainingBackgroundName
import com.polar.shared.sdk.PolarFirstTimeUseTypicalDayName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FirstTimeUseModelsCommonPolicyTest {
    @Test
    fun firstTimeUseEnumMappingsPreservePublicPhysicalConfigValues() {
        assertEquals("MALE", PolarFirstTimeUseGenderName.fromValue(1)?.name)
        assertEquals(2, PolarFirstTimeUseGenderName.fromName("FEMALE")?.value)
        assertNull(PolarFirstTimeUseGenderName.fromValue(0))
        assertNull(PolarFirstTimeUseGenderName.fromName("UNKNOWN"))

        assertEquals("OCCASIONAL", PolarFirstTimeUseTrainingBackgroundName.fromValue(10)?.name)
        assertEquals(20, PolarFirstTimeUseTrainingBackgroundName.fromValue(20)?.value)
        assertEquals("FREQUENT", PolarFirstTimeUseTrainingBackgroundName.fromValue(30)?.name)
        assertEquals("HEAVY", PolarFirstTimeUseTrainingBackgroundName.fromValue(40)?.name)
        assertEquals("SEMI_PRO", PolarFirstTimeUseTrainingBackgroundName.fromValue(50)?.name)
        assertEquals("PRO", PolarFirstTimeUseTrainingBackgroundName.fromValue(60)?.name)
        assertNull(PolarFirstTimeUseTrainingBackgroundName.fromValue(0))
        assertNull(PolarFirstTimeUseTrainingBackgroundName.fromValue(70))

        assertEquals("MOSTLY_SITTING", PolarFirstTimeUseTypicalDayName.fromValue(1)?.name)
        assertEquals("MOSTLY_STANDING", PolarFirstTimeUseTypicalDayName.fromValue(2)?.name)
        assertEquals("MOSTLY_MOVING", PolarFirstTimeUseTypicalDayName.fromValue(3)?.name)
        assertNull(PolarFirstTimeUseTypicalDayName.fromValue(0))
        assertNull(PolarFirstTimeUseTypicalDayName.fromValue(4))
    }
}
