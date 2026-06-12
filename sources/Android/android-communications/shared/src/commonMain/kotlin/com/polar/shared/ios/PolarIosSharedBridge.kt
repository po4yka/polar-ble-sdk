package com.polar.shared.ios

import com.polar.shared.device.PolarDeviceId
import com.polar.shared.ble.PolarAdvertisementModels
import com.polar.shared.ble.PolarTypeUtils
import com.polar.shared.device.PolarDeviceCapabilities
import com.polar.shared.device.PolarDeviceCapabilitiesConfig
import com.polar.shared.device.PolarDeviceCapabilityDefaults
import com.polar.shared.pmd.PolarPmdControlPoint
import com.polar.shared.pmd.PolarPmdMeasurementTypeName
import com.polar.shared.pmd.PolarPmdRecordingType
import com.polar.shared.pmd.PolarPmdSecret
import com.polar.shared.pmd.PolarPmdSettingType
import com.polar.shared.pmd.PolarPmdSettings
import com.polar.shared.pmd.sensors.PolarAccSample
import com.polar.shared.pmd.sensors.PolarEcgType0Sample
import com.polar.shared.pmd.sensors.PolarGyrSample
import com.polar.shared.pmd.sensors.PolarMagCalibrationStatus
import com.polar.shared.pmd.sensors.PolarMagSample
import com.polar.shared.pmd.sensors.PolarOfflineHrSample
import com.polar.shared.pmd.sensors.PolarPmdDataFrame
import com.polar.shared.pmd.sensors.PolarPpiSample
import com.polar.shared.pmd.sensors.PolarPressureSample
import com.polar.shared.pmd.sensors.PolarPpgType0Sample
import com.polar.shared.pmd.sensors.PolarPpgType10Sample
import com.polar.shared.pmd.sensors.PolarPpgType13Sample
import com.polar.shared.pmd.sensors.PolarPpgType14Sample
import com.polar.shared.pmd.sensors.PolarPpgType4Sample
import com.polar.shared.pmd.sensors.PolarPpgType5Sample
import com.polar.shared.pmd.sensors.PolarPpgType7Sample
import com.polar.shared.pmd.sensors.PolarPpgType8Sample
import com.polar.shared.pmd.sensors.PolarPpgType9Sample
import com.polar.shared.pmd.sensors.PolarPpgSportIdSample
import com.polar.shared.pmd.sensors.PolarSensorDataParser
import com.polar.shared.pmd.sensors.PolarSkinTemperatureSample
import com.polar.shared.pmd.sensors.PolarTemperatureSample
import com.polar.shared.runtime.PolarDiskTimeOperation
import com.polar.shared.runtime.PolarFacadeCommandOperation
import com.polar.shared.runtime.PolarFileFacadeOperation
import com.polar.shared.runtime.PolarFileRuntimeErrorOperation
import com.polar.shared.runtime.PolarBackupRestoreFile
import com.polar.shared.runtime.PolarD2hRuntimePlanning
import com.polar.shared.runtime.PolarFirmwareWorkflowScenario
import com.polar.shared.runtime.PolarOfflineTriggerDesiredFeature
import com.polar.shared.runtime.PolarOfflineTriggerDeviceTrigger
import com.polar.shared.runtime.PolarOfflineTriggerTransport
import com.polar.shared.runtime.PolarPsFtpNotificationPacket
import com.polar.shared.runtime.PolarRestFacadeOperation
import com.polar.shared.runtime.PolarRestRequestTransportOperation
import com.polar.shared.runtime.PolarRuntimePlan
import com.polar.shared.runtime.PolarRuntimeOrchestration
import com.polar.shared.runtime.PolarStoredDataCleanupScenario
import com.polar.shared.runtime.PolarStreamRuntimePlanning
import com.polar.shared.runtime.PolarUserDeviceSettingsOperation
import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import com.polar.shared.sdk.PolarFirmwareUpdateModels
import com.polar.shared.sdk.PolarExerciseSportProfileName
import com.polar.shared.sdk.PolarFirstTimeUseGenderName
import com.polar.shared.sdk.PolarFirstTimeUseTrainingBackgroundName
import com.polar.shared.sdk.PolarFirstTimeUseTypicalDayName
import com.polar.shared.sdk.PolarKvtxMalformedScriptException
import com.polar.shared.sdk.PolarKvtxScriptCodec
import com.polar.shared.sdk.PolarOfflineRecordingModels
import com.polar.shared.sdk.PolarActivityClassName
import com.polar.shared.sdk.PolarActivityModels
import com.polar.shared.sdk.PolarAutomaticHrTriggerName
import com.polar.shared.sdk.PolarDailyBalanceFeedbackName
import com.polar.shared.sdk.PolarPpiIntervalStatusName
import com.polar.shared.sdk.PolarPpiMovementName
import com.polar.shared.sdk.PolarPpiSampleTriggerName
import com.polar.shared.sdk.PolarPpiSkinContactName
import com.polar.shared.sdk.PolarPpiStatusNames
import com.polar.shared.sdk.PolarRestServiceModels
import com.polar.shared.sdk.PolarSdLogMagnetometerFrequencyName
import com.polar.shared.sdk.PolarSdLogTriggerName
import com.polar.shared.sdk.PolarSdkFeatureAvailability
import com.polar.shared.sdk.PolarSdkModelMappers
import com.polar.shared.sdk.PolarSleepModels
import com.polar.shared.sdk.PolarSleepRatingName
import com.polar.shared.sdk.PolarSleepWakeStateName
import com.polar.shared.sdk.PolarSpo2Models
import com.polar.shared.sdk.PolarStoredDataModels
import com.polar.shared.sdk.PolarTrainingExerciseReference
import com.polar.shared.sdk.PolarTrainingSessionFileEntry
import com.polar.shared.sdk.PolarTrainingSessionModels
import com.polar.shared.sdk.PolarTrainingSessionReference
import com.polar.shared.sdk.PolarTrainingReadinessName
import com.polar.shared.sdk.PolarUserDeviceSettingsFields
import com.polar.shared.sdk.PolarUserDeviceSettingsModels
import com.polar.shared.sdk.PolarUserDeviceSettingsTimestamp
import com.polar.shared.sdk.PolarWatchFaceConfigFlatBuffer
import com.polar.shared.sdk.PolarWatchFaceComplicationName
import com.polar.shared.sdk.PolarWatchFaceFields
import com.polar.shared.time.PolarDateFields
import com.polar.shared.time.PolarDurationFields
import com.polar.shared.time.PolarTimeFields
import com.polar.shared.time.PolarTimeUtils

object PolarIosSharedBridge {
    fun advertisementDeviceModelName(localName: String, prefixToTrim: String): String {
        return PolarAdvertisementModels.deviceModelNameFromLocalName(localName, prefixToTrim)
    }

    fun isValidAdvertisementDeviceName(localName: String, requiredPrefix: String): Boolean {
        return PolarAdvertisementModels.isValidDeviceLocalName(localName, requiredPrefix)
    }

    fun advertisementLocalNameDeviceId(localName: String, deviceNamePrefix: String): String {
        return PolarAdvertisementModels.parseLocalName(localName, deviceNamePrefix).deviceId
    }

    fun polarHrAdvertisementPayloadsHex(manufacturerDataHex: String): String {
        return PolarAdvertisementModels.polarManufacturerHrPayloads(manufacturerDataHex.hexToBytes()).joinToString(separator = "|") { payload ->
            payload.toHex()
        }
    }

    fun isValidDeviceId(deviceId: String): Boolean {
        return PolarDeviceId.isValidOrFalse(deviceId)
    }

    fun assembleFullDeviceId(deviceId: String): String {
        return PolarDeviceId.assembleFull(deviceId)
    }

    fun uuidFromDeviceId(deviceId: String): String {
        return PolarDeviceId.uuidFromDeviceId(deviceId)
    }

    fun identifierClassification(identifier: String): String {
        return when (PolarDeviceId.classifyIdentifier(identifier)) {
            PolarDeviceId.IdentifierClassification.DeviceId -> "deviceId"
            PolarDeviceId.IdentifierClassification.PlatformSpecific -> "platformSpecific"
            PolarDeviceId.IdentifierClassification.Invalid -> "invalid"
        }
    }

    fun millisToNanos(milliseconds: Int): Int {
        return PolarTimeUtils.millisToNanos(milliseconds)
    }

    fun nanosToMillis(nanoseconds: Int): Int {
        return PolarTimeUtils.nanosToMillis(nanoseconds)
    }

    fun secondsToMinutes(seconds: Int): Int {
        return PolarTimeUtils.secondsToMinutes(seconds)
    }

    fun minutesToSeconds(minutes: Int): Int {
        return PolarTimeUtils.minutesToSeconds(minutes)
    }

    fun durationToMillis(hours: Int, minutes: Int, seconds: Int, millis: Int): Int {
        return PolarTimeUtils.durationToMillis(
            PolarDurationFields(
                hours = hours,
                minutes = minutes,
                seconds = seconds,
                millis = millis
            )
        )
    }

    fun timeString(hour: Int, minute: Int, second: Int, millis: Int): String {
        return PolarTimeUtils.timeString(
            PolarTimeFields(
                hour = hour,
                minute = minute,
                second = second,
                millis = millis
            )
        )
    }

    fun isValidPlainDate(value: String): Boolean {
        return PolarTimeUtils.isValidPlainDate(value)
    }

    fun plainDateFieldsCsv(value: String): String? {
        val fields = PolarTimeUtils.parsePlainDate(value) ?: return null
        return listOf(fields.year, fields.month, fields.day).joinToString(separator = ",")
    }

    fun formatPlainDate(year: Int, month: Int, day: Int): String? {
        return runCatching { PolarTimeUtils.formatPlainDate(PolarDateFields(year, month, day)) }.getOrNull()
    }

    fun basicDateRangeCsv(startInclusive: String, endInclusive: String): String {
        return PolarTimeUtils.basicDateRange(startInclusive, endInclusive).joinToString(separator = ",")
    }

    fun signedIntFromLittleEndianHex(hex: String): Int {
        return PolarTypeUtils.requireSignedInt(hex.hexToBytes()).toInt()
    }

    fun signedIntFromLittleEndianHex(hex: String, offset: Int, size: Int): Int {
        return PolarTypeUtils.convertArrayToSignedInt(hex.hexToBytes(), offset, size).requireValue().toInt()
    }

    fun unsignedLongFromLittleEndianHex(hex: String): String {
        return PolarTypeUtils.requireUnsignedLong(hex.hexToBytes())
    }

    fun unsignedLongFromLittleEndianHex(hex: String, offset: Int, size: Int): String {
        return PolarTypeUtils.convertArrayToUnsignedLong(hex.hexToBytes(), offset, size).requireValue()
    }

    fun buildKvtxWriteAndCommitHex(kvKey: Long, dataHex: String): String {
        return PolarKvtxScriptCodec.buildWriteAndCommit(kvKey, dataHex.hexToBytes()).toHex()
    }

    fun extractKvtxValueForKeyHex(scriptHex: String, kvKey: Long): String? {
        return try {
            PolarKvtxScriptCodec.extractValueForKey(scriptHex.hexToBytes(), kvKey)?.toHex()
        } catch (_: PolarKvtxMalformedScriptException) {
            null
        }
    }

    fun kvtxU32LeHex(value: Long): String {
        return PolarKvtxScriptCodec.u32Le(value).toHex()
    }

    fun offlineRecordingMeasurementType(fileName: String): String? {
        return runCatching { PolarOfflineRecordingModels.measurementTypeFromFileName(fileName).name }.getOrNull()
    }

    fun offlineRecordingEntriesV2(fileListText: String): String {
        return PolarOfflineRecordingModels.parsePmdFilesV2(fileListText).joinToString("\n") { entry ->
            listOf(entry.type, entry.iosPath, entry.size.toString(), entry.dateTime).joinToString("|")
        }
    }

    fun skinTemperatureMeasurementType(value: Int): String? {
        return PolarSdkModelMappers.skinTemperature(
            sourceDeviceId = null,
            measurementType = value,
            sensorLocation = 0,
            samples = emptyList()
        ).measurementType?.name
    }

    fun skinTemperatureSensorLocation(value: Int): String? {
        return PolarSdkModelMappers.skinTemperature(
            sourceDeviceId = null,
            measurementType = 0,
            sensorLocation = value,
            samples = emptyList()
        ).sensorLocation?.name
    }

    fun skinTemperaturePath(day: String): String {
        return PolarSdkModelMappers.skinTemperaturePath(day)
    }

    fun firmwareDeviceVersion(major: Int, minor: Int, patch: Int): String {
        return PolarFirmwareUpdateModels.deviceVersionToString(major, minor, patch)
    }

    fun isFirmwareVersionHigher(currentVersion: String, availableVersion: String): Boolean {
        return PolarFirmwareUpdateModels.isAvailableFirmwareVersionHigher(currentVersion, availableVersion)
    }

    fun firmwareUpdateIsAvailable(currentVersion: String, availableVersion: String, fileUrl: String): Boolean {
        return PolarWorkflowRuntimePlanning.firmwareUpdateIsAvailable(currentVersion, availableVersion, fileUrl)
    }

    fun ledConfigPayloadCsv(sdkModeLedEnabled: Boolean, ppiModeLedEnabled: Boolean): String {
        return PolarWorkflowRuntimePlanning.ledConfigPayloadBytes(sdkModeLedEnabled, ppiModeLedEnabled).joinToString(",")
    }

    fun firmwareFilePriority(fileName: String): Int {
        return PolarFirmwareUpdateModels.firmwareFilePriority(fileName)
    }

    fun firmwareDeviceInfoPath(): String {
        return PolarFirmwareUpdateModels.deviceInfoPath()
    }

    fun firmwareSystemUpdateFilePath(): String {
        return PolarFirmwareUpdateModels.systemUpdateFilePath()
    }

    fun diskSpaceTotalSpace(fragmentSize: Long, totalFragments: Long, freeFragments: Long): Long {
        return PolarSdkModelMappers.diskSpace(
            fragmentSize = fragmentSize,
            totalFragments = totalFragments,
            freeFragments = freeFragments
        ).totalSpace
    }

    fun diskSpaceFreeSpace(fragmentSize: Long, totalFragments: Long, freeFragments: Long): Long {
        return PolarSdkModelMappers.diskSpace(
            fragmentSize = fragmentSize,
            totalFragments = totalFragments,
            freeFragments = freeFragments
        ).freeSpace
    }

    fun trainingSessionDataType(fileName: String): String? {
        return PolarTrainingSessionModels.trainingDataTypeOrNull(fileName)
    }

    fun trainingSessionExerciseDataType(fileName: String): String? {
        return PolarTrainingSessionModels.exerciseDataTypeOrNull(fileName)
    }

    fun trainingSessionExerciseDataTypeFileName(typeName: String): String? {
        return PolarTrainingSessionModels.exerciseDataTypeFileName(typeName)
    }

    fun trainingSessionReferences(entriesText: String): String {
        val entries = entriesText.lines().mapNotNull { line ->
            val path = line.substringBefore('|', missingDelimiterValue = "")
            val size = line.substringAfter('|', missingDelimiterValue = "").toLongOrNull()
            if (path.isEmpty() || size == null) null else PolarTrainingSessionFileEntry(path = path, size = size)
        }
        return PolarTrainingSessionModels.buildReferences(entries).flatMapIndexed { referenceIndex, reference ->
            listOf(
                listOf(
                    "R",
                    referenceIndex.toString(),
                    reference.dateTime,
                    reference.path,
                    reference.fileSize.toString(),
                    reference.trainingDataTypes.joinToString(";")
                ).joinToString("|")
            ) + reference.exercises.map { exercise ->
                listOf(
                    "E",
                    referenceIndex.toString(),
                    exercise.iosPath,
                    exercise.exerciseDataTypes.joinToString(";"),
                    exercise.fileSizes.entries.joinToString(";") { entry -> "${entry.key}:${entry.value}" }
                ).joinToString("|")
            }
        }.joinToString("\n")
    }

    fun trainingSessionPayloadFetchOrder(referenceText: String): String {
        val lines = referenceText.lines().filter { line -> line.isNotEmpty() }
        val referenceFields = lines.firstOrNull()?.split('|') ?: return ""
        if (referenceFields.size < 5 || referenceFields[0] != "R") return ""
        val exercises = lines.drop(1).mapNotNull { line ->
            val fields = line.split('|')
            if (fields.size < 6 || fields[0] != "E") {
                null
            } else {
                PolarTrainingExerciseReference(
                    index = fields[1].toIntOrNull() ?: return@mapNotNull null,
                    androidPath = fields[2],
                    iosPath = fields[3],
                    exerciseDataTypes = fields[4].semicolonList(),
                    fileSizes = fields[5].semicolonKeyLongMap()
                )
            }
        }
        val reference = PolarTrainingSessionReference(
            dateTime = referenceFields[1],
            date = referenceFields[1].substringBefore('T', missingDelimiterValue = referenceFields[1]),
            path = referenceFields[2],
            trainingDataTypes = referenceFields[3].semicolonList(),
            exercises = exercises,
            fileSize = referenceFields[4].toLongOrNull() ?: 0L
        )
        return PolarTrainingSessionModels.payloadFetchOrder(reference).joinToString("\n")
    }

    fun trainingSessionPayloadReadPlan(referenceText: String): String {
        val lines = referenceText.lines().filter { line -> line.isNotEmpty() }
        val referenceFields = lines.firstOrNull()?.split('|') ?: return ""
        if (referenceFields.size < 5 || referenceFields[0] != "R") return ""
        val exercises = lines.drop(1).mapNotNull { line ->
            val fields = line.split('|')
            if (fields.size < 6 || fields[0] != "E") {
                null
            } else {
                PolarTrainingExerciseReference(
                    index = fields[1].toIntOrNull() ?: return@mapNotNull null,
                    androidPath = fields[2],
                    iosPath = fields[3],
                    exerciseDataTypes = fields[4].semicolonList(),
                    fileSizes = fields[5].semicolonKeyLongMap()
                )
            }
        }
        val reference = PolarTrainingSessionReference(
            dateTime = referenceFields[1],
            date = referenceFields[1].substringBefore('T', missingDelimiterValue = referenceFields[1]),
            path = referenceFields[2],
            trainingDataTypes = referenceFields[3].semicolonList(),
            exercises = exercises,
            fileSize = referenceFields[4].toLongOrNull() ?: 0L
        )
        return PolarTrainingSessionModels.payloadReadPlan(reference)
            .joinToString("\n") { entry ->
                listOf(entry.path, entry.fileName, entry.publicModelSlot, entry.exerciseIndex?.toString().orEmpty()).joinToString("|")
            }
    }

    fun trainingSessionPayloadParserCase(fileName: String): String? {
        val parserCase = PolarTrainingSessionModels.payloadParserCase(fileName) ?: return null
        return "${parserCase.parser}|${parserCase.encoding}"
    }

    fun trainingSessionPublicModelSlot(fileName: String): String? {
        return PolarTrainingSessionModels.publicModelSlot(fileName)
    }

    fun trainingSessionDecodePayloadHex(fileName: String, payloadHex: String): String? {
        return runCatching {
            PolarTrainingSessionModels.decodePayloadBytes(fileName, payloadHex.hexToBytes()).toHex()
        }.getOrNull()
    }

    fun trainingSessionPayloadMalformed(fileName: String, payloadHex: String): Boolean {
        return runCatching {
            PolarTrainingSessionModels.parseDecodedPayloadResponse(fileName, payloadHex.hexToBytes()).malformed
        }.getOrDefault(true)
    }

    fun trainingSessionDeleteParentPath(referencePath: String): String {
        return PolarTrainingSessionModels.deleteParentPath(referencePath)
    }

    fun trainingSessionDeleteRemovePath(referencePath: String, parentEntryCount: Int): String {
        return PolarTrainingSessionModels.deleteRemovePath(referencePath, parentEntryCount)
    }

    fun trainingSessionProgressPercent(completedBytes: Long, totalBytes: Long): Int {
        return PolarTrainingSessionModels.progressPercent(completedBytes, totalBytes)
    }

    fun trainingSessionReferenceDateMatches(date: String, fromDate: String?, toDate: String?): Boolean {
        return PolarTrainingSessionModels.referenceDateMatches(date, fromDate, toDate)
    }

    fun ecgType0Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) != 0 || (frameType and 0x7F) != 0) return null
        return runCatching {
            PolarSensorDataParser.parseEcg(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarEcgType0Sample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.microVolts}" }
        }.getOrNull()
    }

    fun accSamples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        val frameTypeId = frameType and 0x7F
        if (frameTypeId !in setOf(0, 1)) return null
        return runCatching {
            PolarSensorDataParser.parseAcc(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarAccSample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.x},${sample.y},${sample.z}" }
        }.getOrNull()
    }

    fun gyrCompressedType0Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) == 0 || (frameType and 0x7F) != 0) return null
        return runCatching {
            PolarSensorDataParser.parseGyr(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarGyrSample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.x},${sample.y},${sample.z}" }
        }.getOrNull()
    }

    fun magCompressedSamples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        val frameTypeId = frameType and 0x7F
        if ((frameType and 0x80) == 0 || frameTypeId !in setOf(0, 1)) return null
        return runCatching {
            PolarSensorDataParser.parseMag(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarMagSample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.x},${sample.y},${sample.z},${sample.calibrationStatus.name}" }
        }.getOrNull()
    }

    fun ppiRawType0Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) != 0 || (frameType and 0x7F) != 0) return null
        return runCatching {
            PolarSensorDataParser.parsePpi(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPpiSample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.hr},${sample.ppInMs},${sample.ppErrorEstimate},${sample.blockerBit},${sample.skinContactStatus},${sample.skinContactSupported}" }
        }.getOrNull()
    }

    fun ppgRawType0Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) != 0 || (frameType and 0x7F) != 0) return null
        return runCatching {
            PolarSensorDataParser.parsePpg(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPpgType0Sample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.ppgDataSamples.joinToString(separator = ";")},${sample.ambientSample}" }
        }.getOrNull()
    }

    fun ppgRawType4Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) != 0 || (frameType and 0x7F) != 4) return null
        return runCatching {
            PolarSensorDataParser.parsePpg(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPpgType4Sample>()
                .joinToString(separator = "|") { sample ->
                    "${sample.timeStamp},${sample.numIntTs.joinToString(separator = ";")},${sample.channel1GainTs.joinToString(separator = ";")},${sample.channel2GainTs.joinToString(separator = ";")}"
                }
        }.getOrNull()
    }

    fun ppgRawType5Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) != 0 || (frameType and 0x7F) != 5) return null
        return runCatching {
            PolarSensorDataParser.parsePpg(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPpgType5Sample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.operationMode}" }
        }.getOrNull()
    }

    fun ppgRawType6Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) != 0 || (frameType and 0x7F) != 6) return null
        return runCatching {
            PolarSensorDataParser.parsePpg(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPpgSportIdSample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.sportId}" }
        }.getOrNull()
    }

    fun ppgRawType9Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) != 0 || (frameType and 0x7F) != 9) return null
        return runCatching {
            PolarSensorDataParser.parsePpg(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPpgType9Sample>()
                .joinToString(separator = "|") { sample ->
                    "${sample.timeStamp},${sample.numIntTs.joinToString(separator = ";")},${sample.channel1GainTs.joinToString(separator = ";")},${sample.channel2GainTs.joinToString(separator = ";")}"
                }
        }.getOrNull()
    }

    fun ppgRawType14Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) != 0 || (frameType and 0x7F) != 14) return null
        return runCatching {
            PolarSensorDataParser.parsePpg(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPpgType14Sample>()
                .joinToString(separator = "|") { sample ->
                    "${sample.timeStamp},${sample.numIntTs1.joinToString(separator = ";")},${sample.channel1GainTs1.joinToString(separator = ";")},${sample.channel2GainTs1.joinToString(separator = ";")}"
                }
        }.getOrNull()
    }

    fun ppgCompressedType0Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) == 0 || (frameType and 0x7F) != 0) return null
        return runCatching {
            PolarSensorDataParser.parsePpg(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPpgType0Sample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.ppgDataSamples.joinToString(separator = ";")},${sample.ambientSample}" }
        }.getOrNull()
    }

    fun ppgCompressedType7Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) == 0 || (frameType and 0x7F) != 7) return null
        return runCatching {
            PolarSensorDataParser.parsePpg(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPpgType7Sample>()
                .joinToString(separator = "|") { sample ->
                    val statusWord = sample.statusBits.fold(0) { acc, bit -> acc * 2 + bit }
                    "${sample.timeStamp},${(sample.ppgDataSamples + statusWord).joinToString(separator = ";")}"
                }
        }.getOrNull()
    }

    fun ppgCompressedType8Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) == 0 || (frameType and 0x7F) != 8) return null
        return runCatching {
            PolarSensorDataParser.parsePpg(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPpgType8Sample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.ppgDataSamples.joinToString(separator = ";")},${sample.statusBits.joinToString(separator = ";")}" }
        }.getOrNull()
    }

    fun ppgCompressedType10IosSamples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) == 0 || (frameType and 0x7F) != 10) return null
        return runCatching {
            PolarSensorDataParser.parsePpg(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPpgType10Sample>()
                .joinToString(separator = "|") { sample ->
                    "${sample.timeStamp},${sample.redSamples.joinToString(separator = ";")},${sample.greenSamples.joinToString(separator = ";")},${sample.irSamples.joinToString(separator = ";")},${sample.statusBits.joinToString(separator = ";")}"
                }
        }.getOrNull()
    }

    fun ppgCompressedType13Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) == 0 || (frameType and 0x7F) != 13) return null
        return runCatching {
            PolarSensorDataParser.parsePpg(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPpgType13Sample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.ppgChannel0.joinToString(separator = ";")},${sample.ppgChannel1.joinToString(separator = ";")},${sample.statusBits.joinToString(separator = ";")}" }
        }.getOrNull()
    }

    fun offlineHrRawSamples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        val frameTypeId = frameType and 0x7F
        if ((frameType and 0x80) != 0 || frameTypeId !in setOf(0, 1)) return null
        return runCatching {
            PolarSensorDataParser.parseOfflineHr(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarOfflineHrSample>()
                .joinToString(separator = "|") { sample -> "${sample.hr},${sample.ppgQuality},${sample.correctedHr}" }
        }.getOrNull()
    }

    fun pressureRawType0Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) != 0 || (frameType and 0x7F) != 0) return null
        return runCatching {
            PolarSensorDataParser.parsePressure(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPressureSample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.pressure}" }
        }.getOrNull()
    }

    fun pressureCompressedType0Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) == 0 || (frameType and 0x7F) != 0) return null
        return runCatching {
            PolarSensorDataParser.parsePressure(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarPressureSample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.pressure}" }
        }.getOrNull()
    }

    fun temperatureRawType0Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) != 0 || (frameType and 0x7F) != 0) return null
        return runCatching {
            PolarSensorDataParser.parseTemperature(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarTemperatureSample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.temperature}" }
        }.getOrNull()
    }

    fun temperatureCompressedType0Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) == 0 || (frameType and 0x7F) != 0) return null
        return runCatching {
            PolarSensorDataParser.parseTemperature(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarTemperatureSample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.temperature}" }
        }.getOrNull()
    }

    fun skinTemperatureRawType0Samples(dataFrameHex: String, previousTimeStamp: Long, factor: Float, sampleRate: Int): String? {
        val bytes = runCatching { dataFrameHex.hexToBytes() }.getOrNull() ?: return null
        if (bytes.size < 10) return null
        val frameType = bytes[9].toInt() and 0xFF
        if ((frameType and 0x80) != 0 || (frameType and 0x7F) != 0) return null
        return runCatching {
            PolarSensorDataParser.parseSkinTemperature(
                PolarPmdDataFrame.fromByteArray(
                    data = bytes,
                    previousTimeStamp = previousTimeStamp,
                    factor = factor,
                    sampleRate = sampleRate
                )
            ).filterIsInstance<PolarSkinTemperatureSample>()
                .joinToString(separator = "|") { sample -> "${sample.timeStamp},${sample.skinTemperature}" }
        }.getOrNull()
    }

    fun spo2TestStatus(value: Int): String? {
        return PolarSpo2Models.testStatusName(value)
    }

    fun spo2Class(value: Int): String? {
        return PolarSpo2Models.spo2ClassName(value)
    }

    fun spo2DeviationFromBaseline(value: Int): String? {
        return PolarSpo2Models.deviationFromBaselineName(value)
    }

    fun spo2TriggerType(value: Int): String? {
        return PolarSpo2Models.triggerTypeName(value)
    }

    fun spo2TestDirectoryPath(day: String): String {
        return PolarSpo2Models.testDirectoryPath(day)
    }

    fun spo2TestResultPath(directoryPath: String, subDirectoryName: String): String {
        return PolarSpo2Models.testResultPath(directoryPath, subDirectoryName)
    }

    fun spo2TestTimeDirectoryPartsCsv(timeDirName: String): String? {
        val parts = PolarSpo2Models.testTimeDirectoryParts(timeDirName) ?: return null
        return listOf(parts.hour, parts.minute, parts.second).joinToString(separator = ",")
    }

    fun spo2ProjectionFields(
        date: String,
        timeDirName: String,
        recordingDevice: String,
        timeZoneOffsetMinutes: Int,
        testStatus: Int,
        bloodOxygenPercent: String,
        spo2Class: String,
        spo2ValueDeviationFromBaseline: String,
        spo2QualityAveragePercent: String,
        averageHeartRateBpm: String,
        heartRateVariabilityMs: String,
        spo2HrvDeviationFromBaseline: String,
        altitudeMeters: String,
        triggerType: String
    ): String {
        val projection = PolarSpo2Models.projectIosTestData(
            date = date,
            timeDirName = timeDirName,
            recordingDevice = recordingDevice,
            timeZoneOffsetMinutes = timeZoneOffsetMinutes,
            testStatus = testStatus,
            bloodOxygenPercent = bloodOxygenPercent.optionalIntValue(),
            spo2Class = spo2Class.optionalIntValue(),
            spo2ValueDeviationFromBaseline = spo2ValueDeviationFromBaseline.optionalIntValue(),
            spo2QualityAveragePercent = spo2QualityAveragePercent.optionalFloatValue(),
            averageHeartRateBpm = averageHeartRateBpm.optionalIntValue(),
            heartRateVariabilityMs = heartRateVariabilityMs.optionalFloatValue(),
            spo2HrvDeviationFromBaseline = spo2HrvDeviationFromBaseline.optionalIntValue(),
            altitudeMeters = altitudeMeters.optionalFloatValue(),
            triggerType = triggerType.optionalIntValue()
        )
        return listOf(
            projection.recordingDevice,
            projection.testStatus,
            projection.bloodOxygenPercent?.toString(),
            projection.spo2Class,
            projection.spo2ValueDeviationFromBaseline,
            projection.spo2QualityAveragePercent?.toString(),
            projection.averageHeartRateBpm?.toString(),
            projection.heartRateVariabilityMs?.toString(),
            projection.spo2HrvDeviationFromBaseline,
            projection.altitudeMeters?.toString(),
            projection.triggerType
        ).joinToString(separator = "\u001F") { value -> value ?: "" }
    }

    fun restServiceNames(entries: String): String {
        return PolarRestServiceModels.serviceNames(entries.lineMap()).joinToString("|")
    }

    fun sleepRestApiPath(): String {
        return PolarRestServiceModels.sleepApiPath()
    }

    fun sleepRecordingStateSubscribePath(): String {
        return PolarRestServiceModels.sleepRecordingStateSubscribePath()
    }

    fun stopSleepRecordingPath(): String {
        return PolarRestServiceModels.stopSleepRecordingPath()
    }

    fun restServicePaths(entries: String): String {
        return PolarRestServiceModels.servicePaths(entries.lineMap()).joinToString("|")
    }

    fun restServiceJsonPathsForServices(jsonPayload: String): String {
        return PolarRestServiceModels.serviceListJson(jsonPayload).pathsForServices.lineString()
    }

    fun restServiceJsonHasServices(jsonPayload: String): Boolean {
        return PolarRestServiceModels.serviceListJsonHasServices(jsonPayload)
    }

    fun restActionNames(entries: String): String {
        return PolarRestServiceModels.actionNames(entries.lineMap()).joinToString("|")
    }

    fun restActionPaths(entries: String): String {
        return PolarRestServiceModels.actionPaths(entries.lineMap()).joinToString("|")
    }

    fun restEvents(eventsCsv: String): String {
        return PolarRestServiceModels.serviceDescription(eventsCsv.csvFields(), null, null, emptyMap()).events.joinToString("|")
    }

    fun restEndpoints(endpointsCsv: String): String {
        return PolarRestServiceModels.serviceDescription(null, endpointsCsv.csvFields(), null, emptyMap()).endpoints.joinToString("|")
    }

    fun restActions(entries: String): String {
        return PolarRestServiceModels.serviceDescription(null, null, entries.lineMap(), emptyMap()).actions.lineString()
    }

    fun restEventDetails(detailsCsv: String, triggersCsv: String): String {
        return PolarRestServiceModels.eventDetails(mapOf("details" to detailsCsv.csvFields(), "triggers" to triggersCsv.csvFields())).joinToString("|")
    }

    fun restEventTriggers(detailsCsv: String, triggersCsv: String): String {
        return PolarRestServiceModels.eventTriggers(mapOf("details" to detailsCsv.csvFields(), "triggers" to triggersCsv.csvFields())).joinToString("|")
    }

    fun restServiceDescriptionJsonEvents(jsonPayload: String): String {
        return PolarRestServiceModels.serviceDescriptionJson(jsonPayload).events.joinToString("|")
    }

    fun restServiceDescriptionJsonEndpoints(jsonPayload: String): String {
        return PolarRestServiceModels.serviceDescriptionJson(jsonPayload).endpoints.joinToString("|")
    }

    fun restServiceDescriptionJsonActions(jsonPayload: String): String {
        return PolarRestServiceModels.serviceDescriptionJson(jsonPayload).actions.lineString()
    }

    fun restServiceDescriptionJsonEventDetails(jsonPayload: String): String {
        return PolarRestServiceModels.serviceDescriptionJson(jsonPayload).details.nestedLineString()
    }

    fun restServiceDescriptionJsonEventTriggers(jsonPayload: String): String {
        return PolarRestServiceModels.serviceDescriptionJson(jsonPayload).triggers.nestedLineString()
    }

    fun restUncompressedEventPayloadsHex(payloadsHex: String): String {
        return PolarRestServiceModels.restEventPayloads(
            uncompressed = true,
            payloads = payloadsHex.csvFields().map { payload -> payload.hexToBytes() }
        ).joinToString(separator = "|") { payload -> payload.toHex() }
    }

    fun restCompressedEventPayloadsHex(payloadsHex: String): String {
        return PolarRestServiceModels.restEventPayloads(
            uncompressed = false,
            payloads = payloadsHex.csvFields().map { payload -> payload.hexToBytes() }
        ).joinToString(separator = "|") { payload -> payload.toHex() }
    }

    fun pmdActiveMeasurementIosState(responseByte: Int): String {
        return PolarPmdControlPoint.parseActiveMeasurement(responseByte).iosStateName
    }

    fun pmdControlPointResponseFields(responseHex: String): String? {
        val parsed = PolarPmdControlPoint.parseControlPointResponse(responseHex.hexToBytes()).response ?: return null
        return listOf(
            parsed.responseCode.toString(),
            parsed.opCodeValue.toString(),
            parsed.measurementType.toString(),
            parsed.statusValue.toString(),
            if (parsed.more) "1" else "0",
            parsed.parametersHex
        ).joinToString(separator = ",")
    }

    fun pmdMeasurementTypeName(id: Int): String? {
        return PolarPmdMeasurementTypeName.fromMaskedId(id)?.name
    }

    fun availableOfflineRecordingDataTypesCsv(pmdMeasurementTypesCsv: String, includeLocation: Boolean, includePressure: Boolean): String {
        return PolarSdkModelMappers.availableOfflineRecordingDataTypeNames(
            pmdMeasurementTypeNames = pmdMeasurementTypesCsv.csvValues().toSet(),
            includeLocation = includeLocation,
            includePressure = includePressure
        ).joinToString(separator = ",")
    }

    fun availableOnlineStreamDataTypesCsv(pmdMeasurementTypesCsv: String, hasHrService: Boolean, includeLocation: Boolean, includePressure: Boolean): String {
        return PolarSdkModelMappers.availableOnlineStreamDataTypeNames(
            pmdMeasurementTypeNames = pmdMeasurementTypesCsv.csvValues().toSet(),
            hasHrService = hasHrService,
            includeLocation = includeLocation,
            includePressure = includePressure
        ).joinToString(separator = ",")
    }

    fun availableHrServiceDataTypesCsv(hasHrService: Boolean): String {
        return PolarSdkModelMappers.availableHrServiceDataTypeNames(hasHrService).joinToString(separator = ",")
    }

    fun featureAvailabilityPreconditionsMet(featureName: String, discoveredServiceNamesCsv: String, capabilityNamesCsv: String): Boolean {
        return PolarSdkFeatureAvailability.preconditionsMet(
            featureName = featureName,
            discoveredServices = discoveredServiceNamesCsv.csvValues().toSet(),
            capabilities = capabilityNamesCsv.csvValues().toSet()
        )
    }

    fun pmdMeasurementTypeNameForPublicDataTypeName(publicDataTypeName: String): String? {
        return PolarSdkModelMappers.pmdMeasurementTypeNameForPublicDataTypeName(publicDataTypeName)
    }

    fun magCalibrationStatusName(id: Int): String {
        return PolarMagCalibrationStatus.fromId(id).name
    }

    fun pmdRecordingTypeBitField(name: String): Int {
        return PolarPmdRecordingType.fromName(name)?.asBitField() ?: PolarPmdRecordingType.ONLINE.asBitField()
    }

    fun pmdSelectedSettingsHex(selectedCsv: String): String {
        val selected = selectedCsv.csvValues().mapNotNull { field ->
            val code = field.substringBefore('=', missingDelimiterValue = "").toIntOrNull()
            val value = field.substringAfter('=', missingDelimiterValue = "").toLongOrNull()
            val type = code?.let { PolarPmdSettingType.fromCode(it) }
            if (type == null || value == null || value !in 0L..0xFFFF_FFFFL) null else type to value.toInt()
        }.toMap()
        return PolarPmdSettings.serializeSelectedSettings(selected).toHex()
    }

    fun pmdParsedSettingsCsv(settingsHex: String): String? {
        val parsed = PolarPmdSettings.parseSettings(settingsHex.hexToBytes())
        if (parsed.error != null) return null
        return parsed.settings.entries.joinToString(separator = ";") { (type, values) ->
            val mask = when (type.valueSize) {
                1 -> 0xFFL
                2 -> 0xFFFFL
                4 -> 0xFFFF_FFFFL
                else -> 0xFFFF_FFFFL
            }
            val encodedValues = values.joinToString(separator = ",") { value -> ((value.toLong()) and mask).toString() }
            "${type.code}=$encodedValues"
        }
    }

    fun pmdSecretSettingsHex(strategy: String, keyHex: String): String? {
        return runCatching { PolarPmdSecret.from(strategy, keyHex.hexToBytes()).serializeHex() }.getOrNull()
    }

    fun pmdSecretDecryptHex(strategy: String, keyHex: String, cipherHex: String): String? {
        return runCatching { PolarPmdSecret.from(strategy, keyHex.hexToBytes()).decryptHex(cipherHex.hexToBytes()) }.getOrNull()
    }

    fun pmdSecretStrategyName(strategyByte: Int): String? {
        return PolarPmdSecret.strategyNameFromByte(strategyByte)
    }

    fun pmdSettingTypeName(code: Int): String? {
        return PolarPmdSettingType.fromCode(code)?.name
    }

    fun pmdSettingTypeCode(name: String): Int? {
        return runCatching { PolarPmdSettingType.valueOf(name).code }.getOrNull()
    }

    fun watchFaceComplicationName(id: Int): String? {
        return PolarWatchFaceComplicationName.fromId(id)?.name
    }

    fun watchFaceFieldsCsv(
        timeStyleId: Int,
        complicationLayoutId: Int,
        backgroundStyleId: Int,
        accentColor: Long,
        complicationIdsCsv: String,
        fontfaceId: Int
    ): String {
        val fields = PolarWatchFaceFields.fromNullableFields(
            timeStyleId = timeStyleId,
            complicationLayoutId = complicationLayoutId,
            backgroundStyleId = backgroundStyleId,
            accentColor = accentColor,
            complicationIds = complicationIdsCsv.csvValues().mapNotNull { value -> value.toIntOrNull() },
            fontfaceId = fontfaceId
        )
        return listOf(
            fields.timeStyleId.toString(),
            fields.complicationLayoutId.toString(),
            fields.backgroundStyleId.toString(),
            fields.accentColor.toString(),
            fields.complicationIds.joinToString(separator = ";"),
            fields.fontfaceId.toString()
        ).joinToString(separator = ",")
    }

    fun parseWatchFaceConfigFlatBufferHex(rawHex: String): String {
        val fields = PolarWatchFaceConfigFlatBuffer.parse(rawHex.hexToBytes())
        return listOf(
            fields.timeStyleId.toString(),
            fields.complicationLayoutId.toString(),
            fields.backgroundStyleId.toString(),
            fields.accentColor.toString(),
            fields.complicationIds.joinToString(separator = ";"),
            fields.fontfaceId.toString()
        ).joinToString(separator = ",")
    }

    fun buildWatchFaceConfigFlatBufferHex(
        timeStyleId: Int,
        complicationLayoutId: Int,
        backgroundStyleId: Int,
        accentColor: Long,
        complicationIdsCsv: String,
        fontfaceId: Int
    ): String {
        val fields = PolarWatchFaceFields.fromNullableFields(
            timeStyleId = timeStyleId,
            complicationLayoutId = complicationLayoutId,
            backgroundStyleId = backgroundStyleId,
            accentColor = accentColor,
            complicationIds = complicationIdsCsv.csvValues().mapNotNull { value -> value.toIntOrNull() },
            fontfaceId = fontfaceId
        )
        return PolarWatchFaceConfigFlatBuffer.build(fields).toHex()
    }

    fun sleepWakeStateName(value: Int): String? {
        return PolarSleepWakeStateName.fromValue(value)?.name
    }

    fun sleepRatingName(value: Int): String? {
        return PolarSleepRatingName.fromValue(value)?.name
    }

    fun sleepStartOffsetSeconds(value: Int): Int {
        return PolarSleepModels.sleepStartOffsetSeconds(value)
    }

    fun sleepEndOffsetSeconds(value: Int): Int {
        return PolarSleepModels.sleepEndOffsetSeconds(value)
    }

    fun shouldIncludeOriginalSleepRange(hasOriginalSleepRange: Boolean): Boolean {
        return PolarSleepModels.shouldIncludeOriginalSleepRange(hasOriginalSleepRange)
    }

    fun shouldIncludeSleepSkinTemperatureResult(hasSleepDate: Boolean): Boolean {
        return PolarSleepModels.shouldIncludeSleepSkinTemperatureResult(hasSleepDate)
    }

    fun sleepAnalysisPath(day: String): String {
        return PolarSleepModels.sleepAnalysisPath(day)
    }

    fun sleepSkinTemperaturePath(day: String): String {
        return PolarSleepModels.sleepSkinTemperaturePath(day)
    }

    fun nightlyRechargePath(day: String): String {
        return PolarSleepModels.nightlyRechargePath(day)
    }

    fun automaticHrTriggerName(value: Int): String? {
        return PolarAutomaticHrTriggerName.fromValue(value)?.name
    }

    fun activityClassName(value: Int): String? {
        return PolarActivityClassName.fromValue(value)?.name
    }

    fun activityDirectoryPath(day: String): String {
        return PolarActivityModels.activityDirectoryPath(day)
    }

    fun dailySummaryPath(day: String): String {
        return PolarActivityModels.dailySummaryPath(day)
    }

    fun automaticSamplesDirectoryPath(): String {
        return PolarActivityModels.automaticSamplesDirectoryPath()
    }

    fun automaticSamplesFilePath(fileName: String): String {
        return PolarActivityModels.automaticSamplesFilePath(fileName)
    }

    fun dailyBalanceFeedbackName(value: Int): String? {
        return PolarDailyBalanceFeedbackName.fromValue(value)?.name
    }

    fun trainingReadinessName(value: Int): String? {
        return PolarTrainingReadinessName.fromValue(value)?.name
    }

    fun iosStoredDataTypeName(value: Int): String? {
        return PolarStoredDataModels.iosStoredDataTypeName(value)
    }

    fun iosStoredDataTypeValue(name: String): Int? {
        return PolarStoredDataModels.iosStoredDataTypeValue(name)
    }

    fun userDeviceSettingsDeviceLocationName(value: Int): String? {
        return PolarUserDeviceSettingsModels.deviceLocationName(value)
    }

    fun userDeviceSettingsDeviceLocationValue(name: String): Int? {
        return PolarUserDeviceSettingsModels.deviceLocationValue(name)
    }

    fun userDeviceSettingsUsbModeName(value: Int): String? {
        return PolarUserDeviceSettingsModels.usbConnectionModeName(value)
    }

    fun userDeviceSettingsUsbModeValue(name: String): Int? {
        return PolarUserDeviceSettingsModels.usbConnectionModeValue(name)
    }

    fun userDeviceSettingsAutomaticTrainingDetectionModeName(value: Int): String? {
        return PolarUserDeviceSettingsModels.automaticTrainingDetectionModeName(value)
    }

    fun userDeviceSettingsAutomaticTrainingDetectionModeValue(name: String): Int? {
        return PolarUserDeviceSettingsModels.automaticTrainingDetectionModeValue(name)
    }

    fun userDeviceSettingsAutomaticMeasurementStateName(enabled: Boolean): String {
        return PolarUserDeviceSettingsModels.automaticMeasurementStateName(enabled)
    }

    fun userDeviceSettingsAutomaticMeasurementStateEnabled(name: String): Boolean? {
        return PolarUserDeviceSettingsModels.automaticMeasurementStateEnabled(name)
    }

    fun userDeviceSettingsProtobufPayloadFields(): String {
        return PolarUserDeviceSettingsModels.protobufPayloadFields().joinToString(separator = ",")
    }

    fun userDeviceSettingsTelemetryPayloadFields(enabled: Boolean): String {
        return PolarUserDeviceSettingsModels.telemetryPayloadFields(enabled).joinToString(separator = ",")
    }

    fun userDeviceSettingsDeviceLocationPayloadFields(value: Int): String {
        return PolarUserDeviceSettingsModels.deviceLocationPayloadFields(value).joinToString(separator = ",")
    }

    fun userDeviceSettingsUsbConnectionModePayloadFields(enabled: Boolean): String {
        return PolarUserDeviceSettingsModels.usbConnectionModePayloadFields(enabled).joinToString(separator = ",")
    }

    fun userDeviceSettingsAutomaticTrainingDetectionPayloadFields(enabled: Boolean, sensitivity: Int, minimumDurationSeconds: Int): String {
        return PolarUserDeviceSettingsModels.automaticTrainingDetectionPayloadFields(enabled, sensitivity, minimumDurationSeconds).joinToString(separator = ",")
    }

    fun userDeviceSettingsAutomaticOhrPayloadFields(enabled: Boolean): String {
        return PolarUserDeviceSettingsModels.automaticOhrPayloadFields(enabled).joinToString(separator = ",")
    }

    fun userDeviceSettingsDaylightSavingPayloadFields(): String {
        return PolarUserDeviceSettingsModels.daylightSavingPayloadFields().joinToString(separator = ",")
    }

    fun userDeviceSettingsParseProtoBytesCsv(protoHex: String): String? {
        return runCatching {
            val fields = PolarUserDeviceSettingsModels.parseProtoBytes(protoHex.hexToBytes())
            buildList {
                fields.deviceLocation?.let { add("deviceLocation=$it") }
                fields.usbConnectionMode?.let { add("usbConnectionMode=$it") }
                fields.automaticTrainingDetectionMode?.let { add("automaticTrainingDetectionMode=$it") }
                fields.automaticTrainingDetectionSensitivity?.let { add("automaticTrainingDetectionSensitivity=$it") }
                fields.minimumTrainingDurationSeconds?.let { add("minimumTrainingDurationSeconds=$it") }
                fields.telemetryEnabled?.let { add("telemetryEnabled=$it") }
                fields.autosFilesEnabled?.let { add("autosFilesEnabled=$it") }
            }.joinToString(separator = ",")
        }.getOrNull()
    }

    fun userDeviceSettingsBuildProtoBytesHex(fieldsCsv: String, year: Int, month: Int, day: Int, hour: Int, minute: Int, seconds: Int, millis: Int, trusted: Boolean, includeTelemetry: Boolean): String? {
        return runCatching {
            val fields = fieldsCsv.csvKeyValueFields()
            PolarUserDeviceSettingsModels.buildProtoBytes(
                model = PolarUserDeviceSettingsFields(
                    deviceLocation = fields["deviceLocation"]?.toInt(),
                    usbConnectionMode = fields["usbConnectionMode"]?.toBooleanStrictOrNull(),
                    automaticTrainingDetectionMode = fields["automaticTrainingDetectionMode"]?.toBooleanStrictOrNull(),
                    automaticTrainingDetectionSensitivity = fields["automaticTrainingDetectionSensitivity"]?.toInt(),
                    minimumTrainingDurationSeconds = fields["minimumTrainingDurationSeconds"]?.toInt(),
                    telemetryEnabled = fields["telemetryEnabled"]?.toBooleanStrictOrNull(),
                    autosFilesEnabled = fields["autosFilesEnabled"]?.toBooleanStrictOrNull()
                ),
                timestamp = PolarUserDeviceSettingsTimestamp(
                    year = year,
                    month = month,
                    day = day,
                    hour = hour,
                    minute = minute,
                    seconds = seconds,
                    millis = millis,
                    trusted = trusted
                ),
                includeTelemetry = includeTelemetry
            ).toHex()
        }.getOrNull()
    }

    fun firstTimeUseTrainingBackgroundName(value: Int): String? {
        return PolarFirstTimeUseTrainingBackgroundName.fromValue(value)?.name
    }

    fun firstTimeUseTrainingBackgroundValue(value: Int): Int? {
        return PolarFirstTimeUseTrainingBackgroundName.fromValue(value)?.value
    }

    fun firstTimeUseTypicalDayName(value: Int): String? {
        return PolarFirstTimeUseTypicalDayName.fromValue(value)?.name
    }

    fun firstTimeUseTypicalDayValue(value: Int): Int? {
        return PolarFirstTimeUseTypicalDayName.fromValue(value)?.value
    }

    fun firstTimeUseGenderName(value: Int): String? {
        return PolarFirstTimeUseGenderName.fromValue(value)?.name
    }

    fun firstTimeUseGenderValue(name: String): Int? {
        return PolarFirstTimeUseGenderName.fromName(name)?.value
    }

    fun exerciseSportProfileName(id: Int): String {
        return PolarExerciseSportProfileName.fromId(id).name
    }

    fun sdLogTriggerValue(value: Int): Int? {
        return PolarSdLogTriggerName.fromValue(value)?.value
    }

    fun sdLogMagnetometerFrequencyValue(value: Int): Int? {
        return PolarSdLogMagnetometerFrequencyName.fromValue(value)?.value
    }

    fun ppiSampleTriggerName(value: Int): String? {
        return PolarPpiSampleTriggerName.fromValue(value)?.name
    }

    fun ppiSkinContactName(value: Int): String? {
        return PolarPpiSkinContactName.fromValue(value)?.name
    }

    fun ppiMovementName(value: Int): String? {
        return PolarPpiMovementName.fromValue(value)?.name
    }

    fun ppiIntervalStatusName(value: Int): String? {
        return PolarPpiIntervalStatusName.fromValue(value)?.name
    }

    fun ppiStatusNames(statusByte: Int): String? {
        return PolarPpiStatusNames.fromStatusByte(statusByte)?.let { status ->
            "${status.skinContact},${status.movement},${status.intervalStatus}"
        }
    }

    fun resolveDeviceCapabilities(
        deviceType: String,
        deviceTypesCsv: String,
        fileSystemTypesCsv: String,
        recordingSupportedCsv: String,
        firmwareUpdateSupportedCsv: String,
        activityDataSupportedCsv: String,
        isDeviceSensorCsv: String,
        defaultFileSystemType: String,
        defaultRecordingSupported: Boolean,
        defaultFirmwareUpdateSupported: Boolean,
        defaultActivityDataSupported: Boolean,
        defaultIsDeviceSensor: Boolean
    ): String {
        val deviceTypes = deviceTypesCsv.csvFields()
        val fileSystemTypes = fileSystemTypesCsv.csvFields()
        val recordingSupported = recordingSupportedCsv.csvFields()
        val firmwareUpdateSupported = firmwareUpdateSupportedCsv.csvFields()
        val activityDataSupported = activityDataSupportedCsv.csvFields()
        val isDeviceSensor = isDeviceSensorCsv.csvFields()
        val devices = deviceTypes.mapIndexed { index, type ->
            type to PolarDeviceCapabilities(
                fileSystemType = fileSystemTypes.optionalField(index),
                recordingSupported = recordingSupported.optionalBooleanField(index),
                firmwareUpdateSupported = firmwareUpdateSupported.optionalBooleanField(index),
                activityDataSupported = activityDataSupported.optionalBooleanField(index),
                isDeviceSensor = isDeviceSensor.optionalBooleanField(index)
            )
        }.toMap()
        val resolved = PolarDeviceCapabilitiesConfig(
            devices = devices,
            defaults = PolarDeviceCapabilityDefaults(
                fileSystemType = defaultFileSystemType,
                recordingSupported = defaultRecordingSupported,
                firmwareUpdateSupported = defaultFirmwareUpdateSupported,
                activityDataSupported = defaultActivityDataSupported,
                isDeviceSensor = defaultIsDeviceSensor
            )
        ).capability(deviceType)

        return listOf(
            resolved.fileSystemType.name,
            resolved.recordingSupported.toString(),
            resolved.firmwareUpdateSupported.toString(),
            resolved.activityDataSupported.toString(),
            resolved.isDeviceSensor.toString()
        ).joinToString(",")
    }

    fun planRuntimeCommandQuery(id: String, query: String, parametersCsv: String): String {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "query",
                query = query,
                parameters = parametersCsv.csvValues(),
                notifications = emptyList(),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        ).terminal
    }

    fun planRuntimeCommandQueryCommands(id: String, query: String, parametersCsv: String): String {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "query",
                query = query,
                parameters = parametersCsv.csvValues(),
                notifications = emptyList(),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        ).queryCommandsCsv()
    }

    fun planRuntimeCommandReset(id: String, sleep: Boolean, factoryDefaults: Boolean, otaFirmwareUpdate: Boolean): String {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "resetNotification",
                query = null,
                parameters = emptyList(),
                notifications = emptyList(),
                sleep = sleep,
                factoryDefaults = factoryDefaults,
                otaFirmwareUpdate = otaFirmwareUpdate
            )
        ).terminal
    }

    fun planRuntimeCommandResetNotifications(id: String, sleep: Boolean, factoryDefaults: Boolean, otaFirmwareUpdate: Boolean): String {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "resetNotification",
                query = null,
                parameters = emptyList(),
                notifications = emptyList(),
                sleep = sleep,
                factoryDefaults = factoryDefaults,
                otaFirmwareUpdate = otaFirmwareUpdate
            )
        ).notificationCommandsCsv()
    }

    fun planRuntimeCommandResetFields(id: String, sleep: Boolean, factoryDefaults: Boolean, otaFirmwareUpdate: Boolean): String {
        val fields = PolarRuntimeOrchestration.resetNotificationFields(
            PolarFacadeCommandOperation(
                id = id,
                kind = "resetNotification",
                query = null,
                parameters = emptyList(),
                notifications = emptyList(),
                sleep = sleep,
                factoryDefaults = factoryDefaults,
                otaFirmwareUpdate = otaFirmwareUpdate
            )
        )
        return listOf(
            "sleep=${fields.sleep}",
            "factoryDefaults=${fields.factoryDefaults}",
            "otaFirmwareUpdate=${fields.otaFirmwareUpdate}"
        ).joinToString(separator = ",")
    }

    fun planRuntimeH10StartRecordingFields(id: String, sampleDataIdentifier: String, sampleType: String, recordingIntervalSeconds: Int): String {
        val fields = PolarRuntimeOrchestration.h10StartRecordingFields(
            PolarFacadeCommandOperation(
                id = id,
                kind = "query",
                query = "REQUEST_START_RECORDING",
                parameters = listOf(
                    "sampleDataIdentifier=$sampleDataIdentifier",
                    "sampleType=$sampleType",
                    "recordingIntervalSeconds=$recordingIntervalSeconds"
                ),
                notifications = emptyList(),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        )
        return listOf(
            "sampleDataIdentifier=${fields.sampleDataIdentifier}",
            "sampleType=${fields.sampleType}",
            "recordingIntervalSeconds=${fields.recordingIntervalSeconds}"
        ).joinToString(separator = ",")
    }

    fun planRuntimeSyncStopNotificationFields(id: String): String {
        val fields = PolarRuntimeOrchestration.syncStopNotificationFields(
            PolarFacadeCommandOperation(
                id = id,
                kind = "syncStop",
                query = null,
                parameters = emptyList(),
                notifications = listOf("STOP_SYNC:completed=true", "TERMINATE_SESSION"),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        )
        return "completed=${fields.completed}"
    }

    fun planRuntimeCommandSyncStart(id: String): String {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "syncStart",
                query = "REQUEST_SYNCHRONIZATION",
                parameters = emptyList(),
                notifications = listOf("INITIALIZE_SESSION", "START_SYNC"),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        ).terminal
    }

    fun planRuntimeCommandSyncStartNotifications(id: String): String {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "syncStart",
                query = "REQUEST_SYNCHRONIZATION",
                parameters = emptyList(),
                notifications = listOf("INITIALIZE_SESSION", "START_SYNC"),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        ).notificationCommandsCsv()
    }

    fun planRuntimeCommandSyncStartCommands(id: String): String {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "syncStart",
                query = "REQUEST_SYNCHRONIZATION",
                parameters = emptyList(),
                notifications = listOf("INITIALIZE_SESSION", "START_SYNC"),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        ).queryCommandsCsv()
    }

    fun planRuntimeCommandSyncStop(id: String): String {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "syncStop",
                query = null,
                parameters = emptyList(),
                notifications = listOf("STOP_SYNC:completed=true", "TERMINATE_SESSION"),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        ).terminal
    }

    fun planRuntimeCommandSyncStopNotifications(id: String): String {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "syncStop",
                query = null,
                parameters = emptyList(),
                notifications = listOf("STOP_SYNC:completed=true", "TERMINATE_SESSION"),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        ).notificationCommandsCsv()
    }

    fun planRuntimeDiskTimeQuery(id: String, query: String): String {
        return PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = id,
                kind = "query",
                query = query,
                queries = emptyList(),
                parameters = emptyList(),
                expectedFields = emptyList()
            )
        ).terminal
    }

    fun planRuntimeDiskTimeQueryCommands(id: String, query: String): String {
        return PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = id,
                kind = "query",
                query = query,
                queries = emptyList(),
                parameters = emptyList(),
                expectedFields = emptyList()
            )
        ).queryCommandsCsv()
    }

    fun planRuntimeSetLocalTimeV2(systemTimeHour: Int, localTimeHour: Int): String {
        return PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = "set-local-time-v2",
                kind = "setLocalTimeV2",
                query = null,
                queries = listOf("SET_SYSTEM_TIME", "SET_LOCAL_TIME"),
                parameters = emptyList(),
                expectedFields = listOf("systemTimeHour=$systemTimeHour", "localTimeHour=$localTimeHour", "systemTimeTrusted=true")
            )
        ).terminal
    }

    fun planRuntimeSetLocalTimeV2Commands(systemTimeHour: Int, localTimeHour: Int): String {
        return PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = "set-local-time-v2",
                kind = "setLocalTimeV2",
                query = null,
                queries = listOf("SET_SYSTEM_TIME", "SET_LOCAL_TIME"),
                parameters = emptyList(),
                expectedFields = listOf("systemTimeHour=$systemTimeHour", "localTimeHour=$localTimeHour", "systemTimeTrusted=true")
            )
        ).queryCommandsCsv()
    }

    fun planRuntimeSetLocalTimeH10(localTimeHour: Int): String {
        return PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = "set-local-time-h10",
                kind = "setLocalTimeH10",
                query = null,
                queries = listOf("SET_LOCAL_TIME"),
                parameters = emptyList(),
                expectedFields = listOf("localTimeHour=$localTimeHour")
            )
        ).terminal
    }

    fun planRuntimeSetLocalTimeH10Commands(localTimeHour: Int): String {
        return PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = "set-local-time-h10",
                kind = "setLocalTimeH10",
                query = null,
                queries = listOf("SET_LOCAL_TIME"),
                parameters = emptyList(),
                expectedFields = listOf("localTimeHour=$localTimeHour")
            )
        ).queryCommandsCsv()
    }

    fun planRuntimeRestFacadeGet(id: String, path: String, payloadShape: String): String {
        return PolarRuntimeOrchestration.planRestFacade(
            PolarRestFacadeOperation(
                id = id,
                command = "GET",
                path = path,
                payloadShape = payloadShape.takeIf { it.isNotEmpty() },
                expectedFields = emptyList(),
                transportMode = null,
                responseErrorStatus = null,
                responseErrorMessage = null,
                expectedPlatformTerminal = null
            )
        ).terminal
    }

    fun planRuntimeRestFacadeGetOperation(id: String, path: String, payloadShape: String): String {
        return PolarRuntimeOrchestration.planRestFacade(
            PolarRestFacadeOperation(
                id = id,
                command = "GET",
                path = path,
                payloadShape = payloadShape.takeIf { it.isNotEmpty() },
                expectedFields = emptyList(),
                transportMode = null,
                responseErrorStatus = null,
                responseErrorMessage = null,
                expectedPlatformTerminal = null
            )
        ).fileOperationCommandsCsv()
    }

    fun planRuntimeRestRequestTransportGet(path: String, payloadHex: String): String {
        return PolarRuntimeOrchestration.planRestRequestTransport(
            PolarRestRequestTransportOperation(
                id = "platform-rest-request-transport",
                path = path,
                transportMode = "success",
                status = null,
                message = null,
                payloadHex = payloadHex
            )
        ).terminal
    }

    fun planRuntimeFileFacade(id: String, command: String, path: String, payloadHex: String): String {
        return PolarRuntimeOrchestration.planFileFacade(
            PolarFileFacadeOperation(
                id = id,
                command = command,
                path = path,
                payloadHex = payloadHex.takeIf { it.isNotEmpty() },
                responseHex = null,
                progress = emptyList(),
                transportMode = null
            )
        ).terminal
    }

    fun planRuntimeFileFacadeOperation(id: String, command: String, path: String, payloadHex: String): String {
        return PolarRuntimeOrchestration.planFileFacade(
            PolarFileFacadeOperation(
                id = id,
                command = command,
                path = path,
                payloadHex = payloadHex.takeIf { it.isNotEmpty() },
                responseHex = null,
                progress = emptyList(),
                transportMode = null
            )
        ).fileOperationCommandsCsv()
    }

    fun normalizeFileListFolderPath(folderPath: String): String {
        return PolarRuntimeOrchestration.normalizeFileListFolderPath(folderPath)
    }

    fun planRuntimeFileError(operation: String, path: String, errorName: String): String {
        return PolarRuntimeOrchestration.planFileRuntimeError(
            PolarFileRuntimeErrorOperation(
                id = "platform-file-runtime-error",
                operation = operation,
                path = path,
                payloadHex = null,
                transportMode = "transportError",
                status = null,
                message = null,
                error = errorName.ifEmpty { "Error" },
                responsePayloadHex = null
            )
        ).outcome
    }

    fun planRuntimeUserDeviceSettings(id: String, kind: String, path: String, payloadFieldsCsv: String): String {
        return PolarRuntimeOrchestration.planUserDeviceSettings(
            PolarUserDeviceSettingsOperation(
                id = id,
                kind = kind,
                path = path,
                payloadFields = payloadFieldsCsv.csvValues()
            )
        ).terminal
    }

    fun planRuntimeUserDeviceSettingsOperations(id: String, kind: String, path: String, payloadFieldsCsv: String): String {
        return PolarRuntimeOrchestration.planUserDeviceSettings(
            PolarUserDeviceSettingsOperation(
                id = id,
                kind = kind,
                path = path,
                payloadFields = payloadFieldsCsv.csvValues()
            )
        ).userDeviceSettingsOperationCommandsCsv()
    }

    fun userDeviceSettingsPath(fileSystemType: String, deviceSettingsPath: String, sensorSettingsPath: String, unknownSettingsPath: String?): String? {
        return PolarRuntimeOrchestration.userDeviceSettingsPath(fileSystemType, deviceSettingsPath, sensorSettingsPath, unknownSettingsPath)
    }

    fun planRuntimeStoredDataCleanup(kind: String, rootPath: String): String {
        return PolarWorkflowRuntimePlanning.planStoredDataCleanup(
            PolarStoredDataCleanupScenario(
                id = "platform-stored-data-cleanup",
                kind = kind,
                rootPath = rootPath
            )
        ).terminal
    }

    fun planRuntimeStoredDataCleanupWithCutoff(kind: String, rootPath: String, cutoffDate: String): String {
        return PolarWorkflowRuntimePlanning.planStoredDataCleanup(
            PolarStoredDataCleanupScenario(
                id = "platform-stored-data-cleanup",
                kind = kind,
                rootPath = rootPath,
                cutoffDate = cutoffDate
            )
        ).terminal
    }

    fun planRuntimeStoredDataCleanupOperations(
        kind: String,
        rootPath: String,
        cutoffDate: String,
        entriesCsv: String,
        includePrefixesCsv: String,
        includeSuffixesCsv: String
    ): String {
        return PolarWorkflowRuntimePlanning.planStoredDataCleanup(
            PolarStoredDataCleanupScenario(
                id = "platform-stored-data-cleanup",
                kind = kind,
                rootPath = rootPath,
                cutoffDate = cutoffDate.ifEmpty { null },
                entries = entriesCsv.csvValues(),
                includePrefixes = includePrefixesCsv.csvValues(),
                includeSuffixes = includeSuffixesCsv.csvValues()
            )
        ).commands.joinToString(separator = ",")
    }

    fun storedDataEntryMatchesFilter(entry: String, includePrefixesCsv: String, includeSuffixesCsv: String): Boolean {
        return PolarWorkflowRuntimePlanning.storedDataEntryMatchesFilter(
            entry = entry,
            includePrefixes = includePrefixesCsv.csvValues(),
            includeSuffixes = includeSuffixesCsv.csvValues()
        )
    }

    fun storedDataCleanupDirectoryEntryMatches(dataType: String, entry: String, cutoffFolder: String?): Boolean {
        return PolarWorkflowRuntimePlanning.storedDataCleanupDirectoryEntryMatches(dataType, entry, cutoffFolder)
    }

    fun shouldPruneStoredDataEmptyParents(dataType: String): Boolean {
        return PolarWorkflowRuntimePlanning.shouldPruneStoredDataEmptyParents(dataType)
    }

    fun storedDataCleanupRootPath(dataType: String, defaultRoot: String): String {
        return PolarWorkflowRuntimePlanning.storedDataCleanupRootPath(dataType, defaultRoot)
    }

    fun storedDataDateIsOnOrBefore(day: String, cutoffDate: String): Boolean {
        return PolarWorkflowRuntimePlanning.storedDataDateIsOnOrBefore(day, cutoffDate)
    }

    fun storedDataEmptyParentDirectories(filePath: String, rootPath: String, trailingSlash: Boolean): String {
        return PolarWorkflowRuntimePlanning.storedDataEmptyParentDirectories(filePath, rootPath, trailingSlash).joinToString(separator = ",")
    }

    fun planRuntimeOfflineTrigger(operation: String, currentTypesCsv: String, desiredTypesCsv: String, secretPresent: Boolean): String {
        return PolarWorkflowRuntimePlanning.planOfflineTriggerRuntime(
            operation = operation,
            currentDeviceTriggers = currentTypesCsv.csvValues().map { encoded -> offlineTriggerDeviceTrigger(encoded) },
            desiredFeatures = desiredTypesCsv.csvValues().map { encoded -> offlineTriggerDesiredFeature(encoded) },
            secretPresent = secretPresent,
            transport = PolarOfflineTriggerTransport()
        ).terminal
    }

    fun planRuntimeOfflineTriggerCommands(operation: String, currentTypesCsv: String, desiredTypesCsv: String, secretPresent: Boolean): String {
        return PolarWorkflowRuntimePlanning.planOfflineTriggerRuntime(
            operation = operation,
            currentDeviceTriggers = currentTypesCsv.csvValues().map { encoded -> offlineTriggerDeviceTrigger(encoded) },
            desiredFeatures = desiredTypesCsv.csvValues().map { encoded -> offlineTriggerDesiredFeature(encoded) },
            secretPresent = secretPresent,
            transport = PolarOfflineTriggerTransport()
        ).commands.joinToString(separator = ",")
    }

    fun planRuntimeOfflineTriggerEnabledFeatures(currentTypesCsv: String): String {
        return PolarWorkflowRuntimePlanning.planOfflineTriggerRuntime(
            operation = "getOfflineRecordingTriggerSetup",
            currentDeviceTriggers = currentTypesCsv.csvValues().map { encoded -> offlineTriggerDeviceTrigger(encoded) },
            transport = PolarOfflineTriggerTransport()
        ).enabledFeatures.joinToString(separator = ",")
    }

    fun planRuntimeFirmwareWorkflow(id: String, statusesCsv: String, firmwareFilesCsv: String): String {
        val statuses = statusesCsv.csvValues()
        return PolarWorkflowRuntimePlanning.planFirmwareWorkflow(
            PolarFirmwareWorkflowScenario(
                id = id,
                expectedStatuses = statuses,
                expectedTerminalStatus = statuses.lastOrNull(),
                expectedStatusOrder = statuses,
                firmwareFiles = firmwareFilesCsv.csvValues()
            )
        ).terminal
    }

    fun planRuntimeFirmwareWorkflowTerminalError(id: String, statusesCsv: String, firmwareFilesCsv: String): String {
        val statuses = statusesCsv.csvValues()
        return PolarWorkflowRuntimePlanning.planFirmwareWorkflow(
            PolarFirmwareWorkflowScenario(
                id = id,
                expectedStatuses = statuses,
                expectedTerminalStatus = statuses.lastOrNull(),
                expectedTerminalError = when (id) {
                    "retryable-server-failure" -> "retryable-server-failure"
                    "client-request-failure" -> "client-request-failure"
                    "cancel-after-package-fetch-cleans-up-before-ble-write" -> "cancelled"
                    else -> null
                },
                expectedStatusOrder = statuses,
                firmwareFiles = firmwareFilesCsv.csvValues()
            )
        ).terminalError.orEmpty()
    }

    fun planRuntimeOrderFirmwareFiles(fileNamesCsv: String): String {
        return PolarWorkflowRuntimePlanning.orderFirmwareFiles(fileNamesCsv.csvValues()).joinToString(",")
    }

    fun planRuntimeFirmwarePayloadFileNames(fileNamesCsv: String): String {
        return PolarWorkflowRuntimePlanning.firmwarePayloadFileNames(fileNamesCsv.csvValues()).joinToString(",")
    }

    fun planRuntimeFirmwareWritePaths(fileNamesCsv: String): String {
        val fileNames = fileNamesCsv.csvValues()
        return PolarWorkflowRuntimePlanning.planFirmwareWorkflow(
            PolarFirmwareWorkflowScenario(
                id = "write-package-success-with-system-update-last",
                expectedStatusOrder = listOf(
                    "preparingDeviceForFwUpdate",
                    "fetchingFwUpdatePackage",
                    "writingFwUpdatePackage",
                    "finalizingFwUpdate",
                    "fwUpdateCompletedSuccessfully"
                ),
                firmwareFiles = fileNames
            )
        ).writes.joinToString(",")
    }

    fun planRuntimeFirmwareRetryDelaysMillis(maxRetries: Int): String {
        return PolarWorkflowRuntimePlanning.firmwareRetryDelaysMillis(maxRetries).joinToString(",")
    }

    fun firmwareAvailabilityFailureIsRetryable(details: String): Boolean {
        return PolarWorkflowRuntimePlanning.firmwareAvailabilityFailureIsRetryable(details)
    }

    fun firmwarePackageEntryIsPayload(fileName: String): Boolean {
        return PolarWorkflowRuntimePlanning.firmwarePackageEntryIsPayload(fileName)
    }

    fun firmwareFileTriggersRebootWait(fileName: String): Boolean {
        return PolarWorkflowRuntimePlanning.firmwareFileTriggersRebootWait(fileName)
    }

    fun firmwareWriteTerminal(errorCode: Int, fileName: String): String {
        return PolarWorkflowRuntimePlanning.firmwareWriteTerminal(errorCode, fileName)
    }

    fun firmwareFinalizationSteps(hasH10FileSystem: Boolean, isDeviceSensor: Boolean): String {
        return PolarWorkflowRuntimePlanning.firmwareFinalizationSteps(hasH10FileSystem, isDeviceSensor).joinToString(",")
    }

    fun firmwareWriteProgressPercent(bytesWritten: Int, payloadSize: Int): Int {
        return PolarWorkflowRuntimePlanning.firmwareWriteProgressPercent(bytesWritten, payloadSize)
    }

    fun shouldEmitFirmwareWriteProgress(lastBytesWritten: Int, bytesWritten: Int, payloadSize: Int, minPercentageIncrement: Int): Boolean {
        return PolarWorkflowRuntimePlanning.shouldEmitFirmwareWriteProgress(
            lastBytesWritten = lastBytesWritten,
            bytesWritten = bytesWritten,
            payloadSize = payloadSize,
            minPercentageIncrement = minPercentageIncrement
        )
    }

    fun shouldEmitFirmwareWriteProgressWithTime(
        lastBytesWritten: Int,
        bytesWritten: Int,
        payloadSize: Int,
        minPercentageIncrement: Int,
        timeSinceLastEmitMs: Long,
        maxEmitIntervalMs: Long
    ): Boolean {
        return PolarWorkflowRuntimePlanning.shouldEmitFirmwareWriteProgress(
            lastBytesWritten = lastBytesWritten,
            bytesWritten = bytesWritten,
            payloadSize = payloadSize,
            minPercentageIncrement = minPercentageIncrement,
            timeSinceLastEmitMs = timeSinceLastEmitMs,
            maxEmitIntervalMs = maxEmitIntervalMs
        )
    }

    fun planRuntimeBackupRestore(path: String, payloadHex: String, writeResult: String): String {
        return PolarWorkflowRuntimePlanning.planBackupRestore(
            listOf(
                PolarBackupRestoreFile(
                    directory = path.substringBeforeLast('/', missingDelimiterValue = "") + "/",
                    fileName = path.substringAfterLast('/'),
                    dataHex = payloadHex,
                    writeResult = writeResult
                )
            )
        ).terminal
    }

    fun planRuntimeBackupRestoreOperation(path: String, payloadHex: String, writeResult: String): String {
        return PolarWorkflowRuntimePlanning.planBackupRestore(
            listOf(
                PolarBackupRestoreFile(
                    directory = path.substringBeforeLast('/', missingDelimiterValue = "") + "/",
                    fileName = path.substringAfterLast('/'),
                    dataHex = payloadHex,
                    writeResult = writeResult
                )
            )
        ).commands.firstOrNull { command -> command.startsWith("PUT:") }.orEmpty()
    }

    fun planRuntimeBackupRestoreOperations(filesTsv: String): String {
        val restoreFiles = if (filesTsv.isEmpty()) {
            emptyList()
        } else {
            filesTsv.lineSequence().filter { line -> line.isNotEmpty() }.map { line ->
                val parts = line.split("\t")
                require(parts.size == 3) { "Backup restore file row must contain directory, fileName, and dataHex" }
                PolarBackupRestoreFile(
                    directory = parts[0],
                    fileName = parts[1],
                    dataHex = parts[2]
                )
            }.toList()
        }
        return PolarWorkflowRuntimePlanning.planBackupRestore(restoreFiles).commands
            .filter { command -> command.startsWith("PUT:") }
            .joinToString("\n") { command ->
                val parts = command.split(":", limit = 3)
                "${parts[0]}\t${parts[1]}\t${parts[2]}"
            }
    }

    fun defaultBackupPathsCsv(): String {
        return PolarWorkflowRuntimePlanning.defaultBackupPaths().joinToString(",")
    }

    fun backupRootPathsCsv(entriesCsv: String): String {
        return PolarWorkflowRuntimePlanning.backupRootPaths(entriesCsv.csvValues()).joinToString(",")
    }

    fun parseBackupTextForIosCsv(backupText: String): String {
        return PolarWorkflowRuntimePlanning.parseBackupTextForIos(backupText).joinToString(",")
    }

    fun backupTraversalRootPath(path: String): String {
        return PolarWorkflowRuntimePlanning.backupTraversalRootPath(path)
    }

    fun backupTraversalPlanParts(path: String): String {
        val plan = PolarWorkflowRuntimePlanning.backupTraversalPlan(path)
        return "${plan.path}\t${plan.wildcardRootPath.orEmpty()}\t${plan.wildcardSubFolder.orEmpty()}"
    }

    fun backupFilePathParts(path: String): String {
        val filePath = PolarWorkflowRuntimePlanning.backupFilePath(path)
        return "${filePath.directory}\t${filePath.fileName}"
    }

    fun planRuntimePsFtpWriteProgress(payloadSize: Int, platform: String): String {
        return PolarWorkflowRuntimePlanning.planPsFtpWriteProgress(payloadSize, platform).joinToString(",")
    }

    fun planRuntimePsFtpWriteAck(payloadSize: Int, writeAck: String): String {
        return PolarWorkflowRuntimePlanning.psFtpWriteAckTerminal(payloadSize, writeAck)
    }

    fun psFtpWriteTimeoutSeconds(filePath: String, defaultTimeoutSeconds: Int, extendedTimeoutSeconds: Int): Int {
        return PolarWorkflowRuntimePlanning.psFtpWriteTimeoutSeconds(
            filePath = filePath,
            defaultTimeoutSeconds = defaultTimeoutSeconds,
            extendedTimeoutSeconds = extendedTimeoutSeconds
        )
    }

    fun psFtpCompleteMessageStreamHex(type: String, headerHex: String, dataHex: String, idValue: Int): String {
        return PolarWorkflowRuntimePlanning.encodeCompleteMessageStream(
            type = type,
            header = headerHex.hexToBytes(),
            idValue = idValue,
            data = dataHex.hexToBytes()
        ).toHex()
    }

    fun psFtpDecodedRfc76Frame(frameHex: String): String {
        val frame = PolarWorkflowRuntimePlanning.decodeRfc76Frame(frameHex.hexToBytes())
        return listOf(
            frame.next.toString(),
            frame.status.toString(),
            frame.sequenceNumber.toString(),
            frame.iosErrorCode?.toString().orEmpty(),
            frame.payload?.toHex().orEmpty()
        ).joinToString(separator = ",")
    }

    fun psFtpEncodeRfc76FrameChunkHex(chunkHex: String, hasMore: Boolean, next: Int, sequenceNumber: Int): String {
        return PolarWorkflowRuntimePlanning.encodeRfc76FrameChunk(
            chunk = chunkHex.hexToBytes(),
            hasMore = hasMore,
            next = next,
            sequenceNumber = sequenceNumber
        ).toHex()
    }

    fun psFtpSplitRfc76FramesHex(payloadHex: String, mtu: Int): String {
        return PolarWorkflowRuntimePlanning.splitRfc76Frames(payloadHex.hexToBytes(), mtu).joinToString(separator = "|") { frame ->
            frame.toHex()
        }
    }

    fun psFtpSplitRfc76RequestWriteFramesHex(headerHex: String, dataHex: String, mtu: Int): String {
        return PolarWorkflowRuntimePlanning.splitRfc76RequestWriteFrames(headerHex.hexToBytes(), dataHex.hexToBytes(), mtu).joinToString(separator = "|") { frame ->
            frame.toHex()
        }
    }

    fun psFtpReassembledNotifications(packets: String): String {
        val packetList = packets
            .split("|")
            .filter { it.isNotBlank() }
            .mapNotNull { packet ->
                val parts = packet.split(":", limit = 2)
                if (parts.size != 2) {
                    null
                } else {
                    PolarPsFtpNotificationPacket(
                        transportStatus = parts[0].toIntOrNull() ?: return@mapNotNull null,
                        frame = parts[1].hexToBytes()
                    )
                }
            }
        return try {
            PolarWorkflowRuntimePlanning.reassembleNotifications(packetList).joinToString(separator = "|") { notification ->
                "${notification.id}:${notification.parameters.toHex()}"
            }
        } catch (_: Throwable) {
            ""
        }
    }

    fun d2hNotificationType(notificationId: Int): String {
        return PolarD2hRuntimePlanning.notificationTypeOrNull(notificationId) ?: ""
    }

    fun d2hParsedProtoName(notificationType: String, parametersHex: String): String {
        return PolarD2hRuntimePlanning.parsedProtoName(notificationType, parametersHex) ?: ""
    }

    fun d2hNotificationPlan(notificationId: Int, parametersHex: String): String {
        val plan = PolarD2hRuntimePlanning.planNotificationEmission(notificationId, parametersHex) ?: return ""
        return listOf(plan.notificationType, plan.parsedProto.orEmpty()).joinToString(separator = ",")
    }

    fun planRuntimeStreamSubscription(target: String, startConnected: Boolean, checkConnection: Boolean): String {
        val snapshot = PolarStreamRuntimePlanning.planCheckedSubscription(target, startConnected, checkConnection)
        return snapshot.terminalError ?: "success"
    }

    fun planRuntimeStreamConsumerCancellation(target: String): String {
        return PolarStreamRuntimePlanning.planConsumerCancellation(target).cancelledStreams.joinToString(",")
    }

    fun planRuntimeStreamDisconnect(target: String, error: String): String {
        return PolarStreamRuntimePlanning.planDisconnectAfterSubscription(target, error).terminalError ?: ""
    }

    fun planRuntimeStreamDuplicateCompletion(target: String): Int {
        return PolarStreamRuntimePlanning.planDuplicateCompletion(target).completionEventCount
    }

    fun planRuntimeStreamPostCompletionEmission(target: String, value: String): Int {
        return PolarStreamRuntimePlanning.planPostCompletionEmissionSuppression(target, value).emittedValues.size
    }

    private fun String.csvValues(): List<String> {
        return split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun offlineTriggerDeviceTrigger(encoded: String): PolarOfflineTriggerDeviceTrigger {
        val parts = encoded.split(":", limit = 2)
        return PolarOfflineTriggerDeviceTrigger(
            type = parts[0],
            status = parts.getOrNull(1) ?: "enabled"
        )
    }

    private fun offlineTriggerDesiredFeature(encoded: String): PolarOfflineTriggerDesiredFeature {
        val parts = encoded.split(":", limit = 2)
        return PolarOfflineTriggerDesiredFeature(
            type = parts[0],
            hasSelectedSettings = parts.getOrNull(1) != "no-settings"
        )
    }

    private fun String.semicolonList(): List<String> {
        return split(";").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun String.semicolonKeyLongMap(): Map<String, Long> {
        if (isEmpty()) return emptyMap()
        return semicolonList().mapNotNull { entry ->
            val key = entry.substringBefore(':', missingDelimiterValue = "")
            val value = entry.substringAfter(':', missingDelimiterValue = "").toLongOrNull()
            if (key.isEmpty() || value == null) null else key to value
        }.toMap()
    }

    private fun PolarRuntimePlan.notificationCommandsCsv(): String {
        return commands
            .filter { command -> command.startsWith("notification:") }
            .joinToString(separator = ",") { command -> command.substringAfter("notification:") }
    }

    private fun PolarRuntimePlan.queryCommandsCsv(): String {
        return commands
            .filter { command -> command.startsWith("query:") }
            .joinToString(separator = ",") { command -> command.substringAfter("query:") }
    }

    private fun PolarRuntimePlan.fileOperationCommandsCsv(): String {
        return commands
            .filter { command ->
                command.startsWith("GET:") ||
                    command.startsWith("PUT:") ||
                    command.startsWith("REMOVE:")
            }
            .joinToString(separator = ",")
    }

    private fun PolarRuntimePlan.userDeviceSettingsOperationCommandsCsv(): String {
        return commands
            .filter { command ->
                command.startsWith("read:") ||
                    command.startsWith("write:")
            }
            .joinToString(separator = ",")
    }

    private fun String.csvFields(): List<String> {
        if (isEmpty()) return emptyList()
        return split(",").map { it.trim() }
    }

    private fun String.csvKeyValueFields(): Map<String, String> {
        return csvFields().mapNotNull { field ->
            val separator = field.indexOf('=')
            if (separator < 0) {
                null
            } else {
                field.substring(0, separator) to field.substring(separator + 1)
            }
        }.toMap()
    }

    private fun String.lineMap(): Map<String, String> {
        if (isEmpty()) return emptyMap()
        return lineSequence()
            .filter { line -> line.isNotEmpty() }
            .associate { line ->
                val separator = line.indexOf('\t')
                require(separator >= 0) { "Missing tab separator in line-map entry" }
                line.substring(0, separator) to line.substring(separator + 1)
            }
    }

    private fun Map<String, String>.lineString(): String {
        return entries.joinToString("\n") { (key, value) -> "$key\t$value" }
    }

    private fun Map<String, List<String>>.nestedLineString(): String {
        return entries.joinToString("\n") { (key, values) -> "$key\t${values.joinToString("|")}" }
    }

    private fun List<String>.optionalField(index: Int): String? {
        return getOrNull(index)?.takeIf { it.isNotEmpty() }
    }

    private fun List<String>.optionalBooleanField(index: Int): Boolean? {
        return optionalField(index)?.toBooleanStrictOrNull()
    }

    private fun String.optionalIntValue(): Int? {
        return takeIf { it.isNotEmpty() }?.toIntOrNull()
    }

    private fun String.optionalFloatValue(): Float? {
        return takeIf { it.isNotEmpty() }?.toFloatOrNull()
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2).map { byte -> byte.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHex(): String {
        return joinToString(separator = "") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
