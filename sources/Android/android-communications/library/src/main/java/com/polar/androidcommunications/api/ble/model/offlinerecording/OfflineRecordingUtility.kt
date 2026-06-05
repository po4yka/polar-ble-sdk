package com.polar.androidcommunications.api.ble.model.offlinerecording

import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.shared.sdk.PolarOfflineRecordingMeasurementType
import com.polar.shared.sdk.PolarOfflineRecordingModels

internal object OfflineRecordingUtility {

    fun mapOfflineRecordingFileNameToMeasurementType(fileName: String): PmdMeasurementType {
        return when (PolarOfflineRecordingModels.measurementTypeFromFileName(fileName)) {
            PolarOfflineRecordingMeasurementType.ACC -> PmdMeasurementType.ACC
            PolarOfflineRecordingMeasurementType.GYRO -> PmdMeasurementType.GYRO
            PolarOfflineRecordingMeasurementType.MAGNETOMETER -> PmdMeasurementType.MAGNETOMETER
            PolarOfflineRecordingMeasurementType.PPG -> PmdMeasurementType.PPG
            PolarOfflineRecordingMeasurementType.PPI -> PmdMeasurementType.PPI
            PolarOfflineRecordingMeasurementType.OFFLINE_HR -> PmdMeasurementType.OFFLINE_HR
            PolarOfflineRecordingMeasurementType.TEMPERATURE -> PmdMeasurementType.TEMPERATURE
            PolarOfflineRecordingMeasurementType.SKIN_TEMP -> PmdMeasurementType.SKIN_TEMP
        }
    }
}
