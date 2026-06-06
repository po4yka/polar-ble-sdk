package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.activity.Polar247HrSamplesData
import com.polar.sdk.api.model.activity.Polar247PPiSamplesData
import com.polar.sdk.api.model.activity.fromPbPPiDataSamples
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbAutomaticSampleSessions
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

private const val AUTOMATIC_SAMPLES_PATTERN = "AUTOS\\d{3}\\.BPB"
private const val TAG = "PolarAutomaticSamplesUtils"

internal object PolarAutomaticSamplesUtils {
    internal fun automaticSamplesDirectoryReadOperation(): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        return automaticSamplesReadOperation("automatic-samples-read-directory", PolarRuntimePlannerAdapter.automaticSamplesDirectoryPath())
    }

    internal fun automaticSamplesFileReadOperation(fileName: String): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        return automaticSamplesReadOperation("automatic-samples-read-file", PolarRuntimePlannerAdapter.automaticSamplesFilePath(fileName))
    }

    private fun automaticSamplesReadOperation(id: String, path: String): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        val plan = PolarRuntimePlannerAdapter.planFileFacade(id, "GET", path)
        return PolarRuntimePlannerAdapter.fileOperationCommand(plan) to PolarRuntimePlannerAdapter.fileOperationPath(plan)
    }

    /**
     * Read 24/7 heart rate samples for given date range.
     */
    suspend fun read247HrSamples(client: BlePsFtpClient, fromDate: LocalDate, toDate: LocalDate): List<Polar247HrSamplesData> {
        BleLogger.d(TAG, "read247HrSamples: from $fromDate to $toDate")
        val autoSamplesOperation = automaticSamplesDirectoryReadOperation()

        val response = client.request(PolarRuntimePlannerAdapter.fileOperationBytes(autoSamplesOperation))
        val dir = PbPFtpDirectory.parseFrom(response.toByteArray())
        val pattern = Pattern.compile(AUTOMATIC_SAMPLES_PATTERN)
        val filteredFiles = dir.entriesList
            .filter { pattern.matcher(it.name).matches() }
            .map { it.name }

        val hrSamplesDataList = mutableListOf<Polar247HrSamplesData>()
        val requestedDays = requestedBasicDays(fromDate, toDate)

        for (fileName in filteredFiles) {
            val fileOperation = automaticSamplesFileReadOperation(fileName)
            val filePath = fileOperation.second
            BleLogger.d(TAG, "Sending GET request for file: $filePath")
            val fileResponse = client.request(PolarRuntimePlannerAdapter.fileOperationBytes(fileOperation))
            val sampleSessions = PbAutomaticSampleSessions.parseFrom(fileResponse.toByteArray())
            val sampleDate = PolarTimeUtils.pbDateToLocalDate(sampleSessions.day)
            if (sampleDate.format(DateTimeFormatter.BASIC_ISO_DATE) in requestedDays) {
                hrSamplesDataList.add(Polar247HrSamplesData.fromProto(sampleSessions))
            } else {
                BleLogger.d(TAG, "Sample date $sampleDate is out of range: $fromDate to $toDate")
            }
        }

        return hrSamplesDataList
    }

    suspend fun read247PPiSamples(client: BlePsFtpClient, fromDate: LocalDate, toDate: LocalDate): List<Polar247PPiSamplesData> {
        BleLogger.d(TAG, "read247PPiSamples: from $fromDate to $toDate")
        val autoSamplesOperation = automaticSamplesDirectoryReadOperation()

        val response = client.request(PolarRuntimePlannerAdapter.fileOperationBytes(autoSamplesOperation))
        val dir = PbPFtpDirectory.parseFrom(response.toByteArray())
        val pattern = Pattern.compile(AUTOMATIC_SAMPLES_PATTERN)
        val filteredFiles = dir.entriesList
            .filter { pattern.matcher(it.name).matches() }
            .map { it.name }

        val ppiSamplesDataList = mutableListOf<Polar247PPiSamplesData>()
        val requestedDays = requestedBasicDays(fromDate, toDate)

        for (fileName in filteredFiles) {
            val fileOperation = automaticSamplesFileReadOperation(fileName)
            val filePath = fileOperation.second
            BleLogger.d(TAG, "Sending GET request for file: $filePath")
            val fileResponse = client.request(PolarRuntimePlannerAdapter.fileOperationBytes(fileOperation))
            val sampleSessions = PbAutomaticSampleSessions.parseFrom(fileResponse.toByteArray())
            val sampleDateProto = sampleSessions.day
            val sampleDateForCheck = LocalDate.of(sampleDateProto.year, sampleDateProto.month, sampleDateProto.day)
            for (sample in sampleSessions.ppiSamplesList) {
                if (sampleDateForCheck.format(DateTimeFormatter.BASIC_ISO_DATE) in requestedDays) {
                    ppiSamplesDataList.add(Polar247PPiSamplesData(sampleDateForCheck, fromPbPPiDataSamples(sample)))
                } else {
                    BleLogger.d(TAG, "Sample date $sampleDateForCheck is out of range: $fromDate to $toDate")
                }
            }
        }

        return ppiSamplesDataList
    }

    private fun requestedBasicDays(fromDate: LocalDate, toDate: LocalDate): Set<String> {
        return PolarRuntimePlannerAdapter.basicDateRange(
            fromDate.format(DateTimeFormatter.BASIC_ISO_DATE),
            toDate.format(DateTimeFormatter.BASIC_ISO_DATE)
        ).toSet()
    }
}
