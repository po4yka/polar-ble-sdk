package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.DeviationFromBaseline
import com.polar.sdk.api.model.PolarSpo2TestData
import com.polar.sdk.api.model.Spo2Class
import com.polar.sdk.api.model.Spo2TestStatus
import com.polar.shared.sdk.PolarSpo2Models
import com.polar.services.datamodels.protobuf.Spo2TestResult
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
private val testTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private const val TAG = "PolarTestUtils"

/**
 * Represents a single SPO2 test result proto together with its time-subdirectory name (HHMMSS)
 * and the date it belongs to.
 */
data class Spo2TestEntry(val date: LocalDate, val timeDirName: String, val protoBytes: ByteArray)

internal object PolarTestUtils {
    internal fun spo2TestDirectoryReadOperation(date: LocalDate): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        return spo2TestReadOperation("spo2-test-read-directory", PolarRuntimePlannerAdapter.spo2TestDirectoryPath(date.format(dateFormatter)))
    }

    internal fun spo2TestFileReadOperation(directoryPath: String, subDirectoryName: String): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        return spo2TestReadOperation("spo2-test-read-file", PolarRuntimePlannerAdapter.spo2TestResultPath(directoryPath, subDirectoryName))
    }

    private fun spo2TestReadOperation(id: String, path: String): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        val plan = PolarRuntimePlannerAdapter.planFileFacade(id, "GET", path)
        return PolarRuntimePlannerAdapter.fileOperationCommand(plan) to PolarRuntimePlannerAdapter.fileOperationPath(plan)
    }

    /**
     * Read and return all SPO2 test proto entries for a given date.
     *
     * Files reside at `/U/0/<yyyyMMdd>/SPO2TEST/<HHMMSS>/SPO2TRES.BPB`.
     * Multiple time subdirectories may exist; all are read and returned.
     */
    suspend fun readSpo2TestProtoFromDayDirectory(client: BlePsFtpClient, date: LocalDate): List<Spo2TestEntry> {
        BleLogger.d(TAG, "readSpo2TestProtoFromDayDirectory: $date")
        val directoryOperation = spo2TestDirectoryReadOperation(date)
        val spo2TestDirPath = directoryOperation.second

        return try {
            val response = client.request(PolarRuntimePlannerAdapter.fileOperationBytes(directoryOperation))

            val dir = PbPFtpDirectory.parseFrom(response.toByteArray())
            val timeSubDirs = dir.entriesList.filter { it.name.endsWith("/") }

            if (timeSubDirs.isEmpty()) {
                BleLogger.d(TAG, "No time subdirectory found in $spo2TestDirPath")
                return emptyList()
            }

            val results = mutableListOf<Spo2TestEntry>()
            for (subDir in timeSubDirs) {
                val timeDirName = subDir.name.trimEnd('/')
                val fileOperation = spo2TestFileReadOperation(spo2TestDirPath, subDir.name)
                val filePath = fileOperation.second
                try {
                    val fileResponse = client.request(PolarRuntimePlannerAdapter.fileOperationBytes(fileOperation))
                    results.add(Spo2TestEntry(date = date, timeDirName = timeDirName, protoBytes = fileResponse.toByteArray()))
                } catch (error: Throwable) {
                    BleLogger.w(TAG, "No SPO2 test proto at $filePath: $error")
                }
            }
            results
        } catch (error: Throwable) {
            BleLogger.w(TAG, "readSpo2TestProtoFromDayDirectory() failed while reading $spo2TestDirPath, error occurred $error.")
            emptyList()
        }
    }

    fun mapSpo2TestEntry(entry: Spo2TestEntry): PolarSpo2TestData {
        val proto = Spo2TestResult.PbSpo2TestResult.parseFrom(entry.protoBytes)
        return mapSpo2TestProto(proto, entry.date, entry.timeDirName)
    }

    internal fun mapSpo2TestProto(
        proto: Spo2TestResult.PbSpo2TestResult,
        date: LocalDate,
        timeDirName: String
    ): PolarSpo2TestData {
        val tzOffsetMinutes = proto.timeZoneOffset
        val testTime = dateTimeFromFolderNames(date, timeDirName)
            ?: if (proto.testTime != 0L) {
                val localDateTime = java.time.Instant.ofEpochMilli(proto.testTime)
                    .atOffset(ZoneOffset.ofTotalSeconds(tzOffsetMinutes * 60))
                    .toLocalDateTime()
                testTimeFormatter.format(localDateTime)
            } else null
        val projection = PolarSpo2Models.projectTestData(
            date = date.toString(),
            timeDirName = timeDirName,
            recordingDevice = proto.recordingDevice,
            timeZoneOffsetMinutes = tzOffsetMinutes,
            testStatus = proto.testStatus.number,
            bloodOxygenPercent = if (proto.hasBloodOxygenPercent()) proto.bloodOxygenPercent else null,
            spo2Class = if (proto.hasSpo2Class()) proto.spo2Class.number else null,
            spo2ValueDeviationFromBaseline = if (proto.hasSpo2ValueDeviationFromBaseline()) proto.spo2ValueDeviationFromBaseline.number else null,
            spo2QualityAveragePercent = if (proto.hasSpo2QualityAveragePercent()) proto.spo2QualityAveragePercent else null,
            averageHeartRateBpm = if (proto.hasAverageHeartRateBpm()) proto.averageHeartRateBpm else null,
            heartRateVariabilityMs = if (proto.hasHeartRateVariabilityMs()) proto.heartRateVariabilityMs else null,
            spo2HrvDeviationFromBaseline = if (proto.hasSpo2HrvDeviationFromBaseline()) proto.spo2HrvDeviationFromBaseline.number else null,
            altitudeMeters = if (proto.hasAltitudeMeters()) proto.altitudeMeters else null,
            triggerType = null
        )
        return PolarSpo2TestData(
            recordingDevice = proto.recordingDevice,
            testTime = testTime,
            timeZoneOffsetMinutes = projection.timeZoneOffsetMinutes,
            testStatus = projection.testStatus?.toSpo2TestStatus(),
            bloodOxygenPercent = projection.bloodOxygenPercent,
            spo2Class = projection.spo2Class?.toSpo2Class(),
            spo2ValueDeviationFromBaseline = projection.spo2ValueDeviationFromBaseline?.toDeviationFromBaseline(),
            spo2QualityAveragePercent = projection.spo2QualityAveragePercent,
            averageHeartRateBpm = projection.averageHeartRateBpm?.toUInt(),
            heartRateVariabilityMs = projection.heartRateVariabilityMs,
            spo2HrvDeviationFromBaseline = projection.spo2HrvDeviationFromBaseline?.toDeviationFromBaseline(),
            altitudeMeters = projection.altitudeMeters
        )
    }

    internal fun dateTimeFromFolderNames(date: LocalDate, timeDirName: String): String? {
        return PolarSpo2Models.testTimeDirectoryParts(timeDirName)?.let { parts ->
            testTimeFormatter.format(LocalDateTime.of(date.year, date.monthValue, date.dayOfMonth, parts.hour, parts.minute, parts.second))
        }
    }

    private fun String.toSpo2TestStatus(): Spo2TestStatus? {
        return when (this) {
            "passed" -> Spo2TestStatus.PASSED
            "inconclusiveTooLowQualityInSamples" -> Spo2TestStatus.INCONCLUSIVE_TOO_LOW_QUALITY_IN_SAMPLES
            "inconclusiveTooLowOverallQuality" -> Spo2TestStatus.INCONCLUSIVE_TOO_LOW_OVERALL_QUALITY
            "inconclusiveTooManyMissingSamples" -> Spo2TestStatus.INCONCLUSIVE_TOO_MANY_MISSING_SAMPLES
            else -> null
        }
    }

    private fun String.toSpo2Class(): Spo2Class? {
        return when (this) {
            "unknown" -> Spo2Class.UNKNOWN
            "veryLow" -> Spo2Class.VERY_LOW
            "low" -> Spo2Class.LOW
            "normal" -> Spo2Class.NORMAL
            else -> null
        }
    }

    private fun String.toDeviationFromBaseline(): DeviationFromBaseline? {
        return when (this) {
            "noBaseline" -> DeviationFromBaseline.NO_BASELINE
            "belowUsual" -> DeviationFromBaseline.BELOW_USUAL
            "usual" -> DeviationFromBaseline.USUAL
            "aboveUsual" -> DeviationFromBaseline.ABOVE_USUAL
            else -> null
        }
    }
}
