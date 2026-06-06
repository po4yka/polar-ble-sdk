package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.sleep.*
import com.polar.services.datamodels.protobuf.SleepSkinTemperatureResult
import fi.polar.remote.representation.protobuf.SleepanalysisResult
import protocol.PftpRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)
private const val TAG = "PolarSleepUtils"

internal object PolarSleepUtils {
    internal fun sleepDataReadOperation(date: LocalDate): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        val path = PolarRuntimePlannerAdapter.sleepAnalysisPath(date.format(dateFormatter))
        val plan = PolarRuntimePlannerAdapter.planFileFacade("sleep-read-analysis", "GET", path)
        return PolarRuntimePlannerAdapter.fileOperationCommand(plan) to PolarRuntimePlannerAdapter.fileOperationPath(plan)
    }

    internal fun sleepSkinTemperatureReadOperation(date: LocalDate): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        val path = PolarRuntimePlannerAdapter.sleepSkinTemperaturePath(date.format(dateFormatter))
        val plan = PolarRuntimePlannerAdapter.planFileFacade("sleep-read-skin-temperature", "GET", path)
        return PolarRuntimePlannerAdapter.fileOperationCommand(plan) to PolarRuntimePlannerAdapter.fileOperationPath(plan)
    }

    /**
     * Read sleep data for a given date.
     */
    suspend fun readSleepDataFromDayDirectory(
        client: BlePsFtpClient,
        date: LocalDate
    ): PolarSleepAnalysisResult {
        val response = readSleepData(client, date)
        return readSleepSkinTemperatureResult(client, date, response)
    }

    /**
     * Read sleep data.
     */
    private suspend fun readSleepData(client: BlePsFtpClient, date: LocalDate): PolarSleepAnalysisResult {
        BleLogger.d(TAG, "readSleepData: $date")
        return try {
            val readOperation = sleepDataReadOperation(date)
            val response = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(readOperation.first)
                    .setPath(readOperation.second)
                    .build()
                    .toByteArray()
            )
            val proto = SleepanalysisResult.PbSleepAnalysisResult.parseFrom(response.toByteArray())
            PolarSleepAnalysisResult(
                PolarTimeUtils.pbLocalDateTimeToZonedDateTime(proto.sleepStartTime),
                PolarTimeUtils.pbLocalDateTimeToZonedDateTime(proto.sleepEndTime),
                PolarTimeUtils.pbSystemDateTimeToZonedDateTime(proto.lastModified),
                proto.sleepGoalMinutes,
                fromPbSleepwakePhasesListProto(proto.sleepwakePhasesList),
                convertSnoozeTimeListToZonedDateTimeList(proto.snoozeTimeList),
                if (proto.hasAlarmTime()) {
                    PolarTimeUtils.pbLocalDateTimeToZonedDateTime(proto.alarmTime)
                } else null,
                proto.sleepStartOffsetSeconds,
                proto.sleepEndOffsetSeconds,
                if (proto.hasUserSleepRating()) {
                    SleepRating.from(proto.userSleepRating.number)
                } else null,
                proto.recordingDevice.deviceId,
                proto.batteryRanOut,
                fromPbSleepCyclesList(proto.sleepCyclesList),
                PolarTimeUtils.pbDateToLocalDate(proto.sleepResultDate),
                if (proto.hasOriginalSleepRange()) {
                    fromPbOriginalSleepRange(proto.originalSleepRange)
                } else null,
                null
            )
        } catch (_: Throwable) {
            PolarSleepAnalysisResult(
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null
            )
        }
    }

    /**
     * Read skin temperature data.
     */
    private suspend fun readSleepSkinTemperatureResult(
        client: BlePsFtpClient,
        date: LocalDate,
        sleepAnalysisResult: PolarSleepAnalysisResult
    ): PolarSleepAnalysisResult {
        BleLogger.d(TAG, "readSleepSkinTemperatureResult: $date")
        return try {
            val result = sleepAnalysisResult
            val readOperation = sleepSkinTemperatureReadOperation(date)
            val response = client.request(
                PftpRequest.PbPFtpOperation.newBuilder()
                    .setCommand(readOperation.first)
                    .setPath(readOperation.second)
                    .build()
                    .toByteArray()
            )
            val proto = SleepSkinTemperatureResult.PbSleepSkinTemperatureResult.parseFrom(response.toByteArray())
            if (proto.hasSleepDate()) {
                result.sleepSkinTemperatureResult = fromPbSleepSkinTemperatureResult(proto)
            }
            result
        } catch (_: Throwable) {
            sleepAnalysisResult
        }
    }
}
