package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayInputStream
import java.time.LocalDateTime

internal object PolarOfflineRecordingUtils {

    private const val TAG = "OfflineRecordingUtils"

    fun listOfflineRecordingsV1(
        client: BlePsFtpClient,
        fetchRecursively: (client: BlePsFtpClient, path: String, condition: (String) -> Boolean) -> Flow<Pair<String, Long>>
    ): Flow<PolarOfflineRecordingEntry> = flow {

        val entries = mutableListOf<Pair<String, Long>>()
        fetchRecursively(client, "/U/0/") { entry ->
            entry.matches(Regex("^(\\d{8})(/)")) ||
                    entry == "R/" ||
                    entry.matches(Regex("^(\\d{6})(/)")) ||
                    entry.contains(".REC")
        }.collect { entry ->
            entries += entry
        }
        PolarRuntimePlannerAdapter.groupedOfflineRecordingEntries(entries).forEach { entry ->
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
                PolarRuntimePlannerAdapter.parsePmdFilesV2(reader.readText()).map { entry ->
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
