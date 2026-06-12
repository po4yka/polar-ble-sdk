package com.polar.androidcommunications.api.ble.model.offlinerecording

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter

internal object OfflineRecordingUtility {

    fun mapOfflineRecordingFileNameToMeasurementType(fileName: String): PmdMeasurementType {
        return PmdMeasurementType.valueOf(PolarRuntimePlannerAdapter.offlineRecordingMeasurementTypeName(fileName))
    }
}
