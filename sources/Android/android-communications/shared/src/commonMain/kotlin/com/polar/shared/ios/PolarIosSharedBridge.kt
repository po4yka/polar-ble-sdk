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
import com.polar.shared.pmd.sensors.PolarPmdDataFrame
import com.polar.shared.pmd.sensors.PolarPpiSample
import com.polar.shared.pmd.sensors.PolarPressureSample
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
import com.polar.shared.runtime.PolarRestFacadeOperation
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
import com.polar.shared.sdk.PolarSdkModelMappers
import com.polar.shared.sdk.PolarSleepRatingName
import com.polar.shared.sdk.PolarSleepWakeStateName
import com.polar.shared.sdk.PolarSpo2Models
import com.polar.shared.sdk.PolarStoredDataModels
import com.polar.shared.sdk.PolarTrainingSessionFileEntry
import com.polar.shared.sdk.PolarTrainingSessionModels
import com.polar.shared.sdk.PolarTrainingReadinessName
import com.polar.shared.sdk.PolarUserDeviceSettingsModels
import com.polar.shared.sdk.PolarWatchFaceComplicationName
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

    fun signedIntFromLittleEndianHex(hex: String): Int {
        return PolarTypeUtils.requireSignedInt(hex.hexToBytes()).toInt()
    }

    fun unsignedLongFromLittleEndianHex(hex: String): String {
        return PolarTypeUtils.requireUnsignedLong(hex.hexToBytes())
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

    fun firmwareDeviceVersion(major: Int, minor: Int, patch: Int): String {
        return PolarFirmwareUpdateModels.deviceVersionToString(major, minor, patch)
    }

    fun isFirmwareVersionHigher(currentVersion: String, availableVersion: String): Boolean {
        return PolarFirmwareUpdateModels.isAvailableFirmwareVersionHigher(currentVersion, availableVersion)
    }

    fun firmwareFilePriority(fileName: String): Int {
        return PolarFirmwareUpdateModels.firmwareFilePriority(fileName)
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

    fun restServiceNames(entries: String): String {
        return PolarRestServiceModels.serviceNames(entries.lineMap()).joinToString("|")
    }

    fun restServicePaths(entries: String): String {
        return PolarRestServiceModels.servicePaths(entries.lineMap()).joinToString("|")
    }

    fun restActionNames(entries: String): String {
        return PolarRestServiceModels.actionNames(entries.lineMap()).joinToString("|")
    }

    fun restActionPaths(entries: String): String {
        return PolarRestServiceModels.actionPaths(entries.lineMap()).joinToString("|")
    }

    fun restEventDetails(detailsCsv: String, triggersCsv: String): String {
        return PolarRestServiceModels.eventDetails(mapOf("details" to detailsCsv.csvFields(), "triggers" to triggersCsv.csvFields())).joinToString("|")
    }

    fun restEventTriggers(detailsCsv: String, triggersCsv: String): String {
        return PolarRestServiceModels.eventTriggers(mapOf("details" to detailsCsv.csvFields(), "triggers" to triggersCsv.csvFields())).joinToString("|")
    }

    fun pmdActiveMeasurementIosState(responseByte: Int): String {
        return PolarPmdControlPoint.parseActiveMeasurement(responseByte).iosStateName
    }

    fun pmdMeasurementTypeName(id: Int): String? {
        return PolarPmdMeasurementTypeName.fromMaskedId(id)?.name
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

    fun pmdSecretSettingsHex(strategy: String, keyHex: String): String? {
        return runCatching { PolarPmdSecret.from(strategy, keyHex.hexToBytes()).serializeHex() }.getOrNull()
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

    fun sleepWakeStateName(value: Int): String? {
        return PolarSleepWakeStateName.fromValue(value)?.name
    }

    fun sleepRatingName(value: Int): String? {
        return PolarSleepRatingName.fromValue(value)?.name
    }

    fun automaticHrTriggerName(value: Int): String? {
        return PolarAutomaticHrTriggerName.fromValue(value)?.name
    }

    fun activityClassName(value: Int): String? {
        return PolarActivityClassName.fromValue(value)?.name
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

    fun planRuntimeStoredDataCleanup(kind: String, rootPath: String): String {
        return PolarWorkflowRuntimePlanning.planStoredDataCleanup(
            PolarStoredDataCleanupScenario(
                id = "platform-stored-data-cleanup",
                kind = kind,
                rootPath = rootPath
            )
        ).terminal
    }

    fun storedDataEntryMatchesFilter(entry: String, includePrefixesCsv: String, includeSuffixesCsv: String): Boolean {
        return PolarWorkflowRuntimePlanning.storedDataEntryMatchesFilter(
            entry = entry,
            includePrefixes = includePrefixesCsv.csvValues(),
            includeSuffixes = includeSuffixesCsv.csvValues()
        )
    }

    fun shouldPruneStoredDataEmptyParents(dataType: String): Boolean {
        return PolarWorkflowRuntimePlanning.shouldPruneStoredDataEmptyParents(dataType)
    }

    fun planRuntimeOfflineTrigger(operation: String, currentTypesCsv: String, desiredTypesCsv: String, secretPresent: Boolean): String {
        return PolarWorkflowRuntimePlanning.planOfflineTriggerRuntime(
            operation = operation,
            currentDeviceTriggers = currentTypesCsv.csvValues().map { type -> PolarOfflineTriggerDeviceTrigger(type, "enabled") },
            desiredFeatures = desiredTypesCsv.csvValues().map { type -> PolarOfflineTriggerDesiredFeature(type, hasSelectedSettings = true) },
            secretPresent = secretPresent,
            transport = PolarOfflineTriggerTransport()
        ).terminal
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

    fun planRuntimeOrderFirmwareFiles(fileNamesCsv: String): String {
        return PolarWorkflowRuntimePlanning.orderFirmwareFiles(fileNamesCsv.csvValues()).joinToString(",")
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

    fun defaultBackupPathsCsv(): String {
        return PolarWorkflowRuntimePlanning.defaultBackupPaths().joinToString(",")
    }

    fun planRuntimePsFtpWriteProgress(payloadSize: Int, platform: String): String {
        return PolarWorkflowRuntimePlanning.planPsFtpWriteProgress(payloadSize, platform).joinToString(",")
    }

    fun planRuntimePsFtpWriteAck(payloadSize: Int, writeAck: String): String {
        return PolarWorkflowRuntimePlanning.planPsFtpWrite(ByteArray(maxOf(payloadSize, 1)) { 0 }, writeAck = writeAck).terminal
    }

    fun d2hNotificationType(notificationId: Int): String {
        return PolarD2hRuntimePlanning.notificationTypeOrNull(notificationId) ?: ""
    }

    fun d2hParsedProtoName(notificationType: String, parametersHex: String): String {
        return PolarD2hRuntimePlanning.parsedProtoName(notificationType, parametersHex) ?: ""
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

    private fun String.csvFields(): List<String> {
        if (isEmpty()) return emptyList()
        return split(",").map { it.trim() }
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

    private fun List<String>.optionalField(index: Int): String? {
        return getOrNull(index)?.takeIf { it.isNotEmpty() }
    }

    private fun List<String>.optionalBooleanField(index: Int): Boolean? {
        return optionalField(index)?.toBooleanStrictOrNull()
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2).map { byte -> byte.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHex(): String {
        return joinToString(separator = "") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
