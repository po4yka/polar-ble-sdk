package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.PolarSkinTemperatureResult
import com.polar.sdk.api.model.sharedSkinTemperatureResult
import com.polar.services.datamodels.protobuf.TemperatureMeasurement.TemperatureMeasurementPeriod
import protocol.PftpRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)
private const val TAG = "PolarSkinTemperatureUtils"

internal object PolarSkinTemperatureUtils {
    internal fun skinTemperatureReadOperation(date: LocalDate): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        val path = PolarRuntimePlannerAdapter.skinTemperaturePath(date.format(dateFormatter))
        val plan = PolarRuntimePlannerAdapter.planFileFacade("skin-temperature-read", "GET", path)
        return PolarRuntimePlannerAdapter.fileOperationCommand(plan) to PolarRuntimePlannerAdapter.fileOperationPath(plan)
    }

    /**
     * Read skin temperature data for a given date.
     */
    suspend fun readSkinTemperatureDataFromDayDirectory(client: BlePsFtpClient, date: LocalDate): PolarSkinTemperatureResult? {
        BleLogger.d(TAG, "readSkinTemperatureDataFromDayDirectory: $date")
        val readOperation = skinTemperatureReadOperation(date)
        val skinTempFilePath = readOperation.second
        return try {
            val response = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(readOperation.first)
                    .setPath(skinTempFilePath)
                    .build()
                    .toByteArray()
            )
            val proto = TemperatureMeasurementPeriod.parseFrom(response.toByteArray())
            sharedSkinTemperatureResult(
                sourceDeviceId = proto.sourceDeviceId,
                sensorLocation = proto.sensorLocationValue,
                measurementType = proto.measurementTypeValue,
                samples = proto.temperatureMeasurementSamplesList
            )
        } catch (error: Throwable) {
            BleLogger.w(TAG, "Failed to fetch skin temperature data for date: $date, error: $error")
            null
        }
    }
}
