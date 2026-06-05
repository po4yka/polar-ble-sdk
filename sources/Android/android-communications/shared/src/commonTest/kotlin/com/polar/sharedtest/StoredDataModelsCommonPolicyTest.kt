package com.polar.sharedtest

import com.polar.shared.sdk.PolarStoredDataModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoredDataModelsCommonPolicyTest {
    @Test
    fun iosStoredDataTypeLookupPreservesSwiftPublicHelperOrder() {
        val names = listOf(
            "UNDEFINED",
            "ACTIVITY",
            "AUTO_SAMPLE",
            "DAILY_SUMMARY",
            "NIGHTLY_RECOVERY",
            "SDLOGS",
            "SLEEP",
            "SLEEP_SCORE",
            "SKIN_CONTACT_CHANGES",
            "SKINTEMP"
        )

        names.forEachIndexed { index, name ->
            assertEquals(name, PolarStoredDataModels.iosStoredDataTypeName(index), name)
            assertEquals(index, PolarStoredDataModels.iosStoredDataTypeValue(name), name)
        }
        assertNull(PolarStoredDataModels.iosStoredDataTypeName(names.size))
        assertNull(PolarStoredDataModels.iosStoredDataTypeValue("SKIN_TEMP"))
        assertNull(PolarStoredDataModels.iosStoredDataTypeValue("SPO2_TEST"))
    }
}
