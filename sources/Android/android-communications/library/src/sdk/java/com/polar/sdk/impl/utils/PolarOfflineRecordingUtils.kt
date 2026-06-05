package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import com.polar.shared.sdk.PolarOfflineRecordingFileEntry
import com.polar.shared.sdk.PolarOfflineRecordingMeasurementType
import com.polar.shared.sdk.PolarOfflineRecordingModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

internal object PolarOfflineRecordingUtils {

    private const val TAG = "OfflineRecordingUtils"
    private const val PMD_FILE_PATH = "/PMDFILES.TXT"

    private fun mapOfflineRecordingFileNameToMeasurementType(fileName: String): PmdMeasurementType {
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

    private fun mapPmdMeasurementTypeToPolarDeviceDataType(type: PmdMeasurementType): PolarBleApi.PolarDeviceDataType {
        return when (type) {
            PmdMeasurementType.ACC -> PolarBleApi.PolarDeviceDataType.ACC
            PmdMeasurementType.GYRO -> PolarBleApi.PolarDeviceDataType.GYRO
            PmdMeasurementType.MAGNETOMETER -> PolarBleApi.PolarDeviceDataType.MAGNETOMETER
            PmdMeasurementType.PPG -> PolarBleApi.PolarDeviceDataType.PPG
            PmdMeasurementType.PPI -> PolarBleApi.PolarDeviceDataType.PPI
            PmdMeasurementType.OFFLINE_HR -> PolarBleApi.PolarDeviceDataType.HR
            PmdMeasurementType.TEMPERATURE -> PolarBleApi.PolarDeviceDataType.TEMPERATURE
            PmdMeasurementType.SKIN_TEMP -> PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE
            else -> throw IllegalArgumentException("Unknown PMD measurement type: $type")
        }
    }

    fun listOfflineRecordingsV1(
        client: BlePsFtpClient,
        fetchRecursively: (client: BlePsFtpClient, path: String, condition: (String) -> Boolean) -> Flow<Pair<String, Long>>
    ): Flow<PolarOfflineRecordingEntry> = flow {

        val entries = mutableListOf<PolarOfflineRecordingFileEntry>()
        fetchRecursively(client, "/U/0/") { entry ->
            entry.matches(Regex("^(\\d{8})(/)")) ||
                    entry == "R/" ||
                    entry.matches(Regex("^(\\d{6})(/)")) ||
                    entry.contains(".REC")
        }.collect { entry ->
            entries += PolarOfflineRecordingFileEntry(path = entry.first, size = entry.second)
        }
        PolarOfflineRecordingModels.groupedRecordingEntries(entries).forEach { entry ->
            emit(
                PolarOfflineRecordingEntry(
                    path = entry.androidPath,
                    size = entry.size,
                    date = LocalDateTime.parse(entry.dateTime),
                    type = entry.type.toPolarDeviceDataType()
                )
            )
        }
    }

    fun listOfflineRecordingsV2(file: ByteArray): List<PolarOfflineRecordingEntry> {
        return ByteArrayInputStream(file)
            .bufferedReader()
            .use { reader ->
                PolarOfflineRecordingModels.parsePmdFilesV2(reader.readText()).map { entry ->
                    PolarOfflineRecordingEntry(
                        path = entry.androidPath,
                        size = entry.size,
                        date = LocalDateTime.parse(entry.dateTime),
                        type = entry.type.toPolarDeviceDataType()
                    )
                }
            }
    }

    private fun String.toPolarDeviceDataType(): PolarBleApi.PolarDeviceDataType {
        return when (this) {
            "ACC" -> PolarBleApi.PolarDeviceDataType.ACC
            "GYRO" -> PolarBleApi.PolarDeviceDataType.GYRO
            "MAGNETOMETER" -> PolarBleApi.PolarDeviceDataType.MAGNETOMETER
            "PPG" -> PolarBleApi.PolarDeviceDataType.PPG
            "PPI" -> PolarBleApi.PolarDeviceDataType.PPI
            "HR" -> PolarBleApi.PolarDeviceDataType.HR
            "TEMPERATURE" -> PolarBleApi.PolarDeviceDataType.TEMPERATURE
            "SKIN_TEMPERATURE" -> PolarBleApi.PolarDeviceDataType.SKIN_TEMPERATURE
            else -> throw PolarInvalidArgument("Unknown offline recording type: $this")
        }
    }
}
