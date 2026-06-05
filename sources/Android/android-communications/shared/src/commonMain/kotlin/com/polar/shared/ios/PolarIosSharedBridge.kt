package com.polar.shared.ios

import com.polar.shared.device.PolarDeviceId
import com.polar.shared.ble.PolarTypeUtils
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
import com.polar.shared.sdk.PolarKvtxMalformedScriptException
import com.polar.shared.sdk.PolarKvtxScriptCodec
import com.polar.shared.sdk.PolarOfflineRecordingModels
import com.polar.shared.sdk.PolarSdkModelMappers
import com.polar.shared.sdk.PolarTrainingSessionModels
import com.polar.shared.time.PolarDurationFields
import com.polar.shared.time.PolarTimeFields
import com.polar.shared.time.PolarTimeUtils

object PolarIosSharedBridge {
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

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2).map { byte -> byte.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.toHex(): String {
        return joinToString(separator = "") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
