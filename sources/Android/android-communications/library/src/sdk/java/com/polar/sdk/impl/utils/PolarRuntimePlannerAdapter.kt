package com.polar.sdk.impl.utils

import com.polar.shared.runtime.PolarDiskTimeOperation
import com.polar.shared.runtime.PolarFacadeCommandOperation
import com.polar.shared.runtime.PolarFileFacadeOperation
import com.polar.shared.runtime.PolarFileRuntimeErrorOperation
import com.polar.shared.runtime.PolarRestFacadeOperation
import com.polar.shared.runtime.PolarResetNotificationFields
import com.polar.shared.runtime.PolarRuntimePlan
import com.polar.shared.runtime.PolarRuntimeOrchestration
import com.polar.shared.runtime.PolarUserDeviceSettingsOperation
import com.polar.shared.runtime.PolarBackupRestoreFile
import com.polar.shared.runtime.PolarFirmwareWorkflowScenario
import com.polar.shared.runtime.PolarOfflineTriggerDesiredFeature
import com.polar.shared.runtime.PolarOfflineTriggerDeviceTrigger
import com.polar.shared.runtime.PolarOfflineTriggerTransport
import com.polar.shared.runtime.PolarStoredDataCleanupScenario
import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import com.polar.shared.sdk.PolarActivityModels
import com.polar.shared.sdk.PolarFirmwareUpdateModels
import com.polar.shared.sdk.PolarRestServiceModels
import com.polar.shared.sdk.PolarSdkModelMappers
import com.polar.shared.sdk.PolarSleepModels
import com.polar.shared.sdk.PolarSpo2Models
import com.polar.shared.sdk.PolarTrainingSessionModels
import protocol.PftpNotification
import protocol.PftpRequest

internal object PolarRuntimePlannerAdapter {
    fun planCommandQuery(id: String, query: String, parameters: List<String> = emptyList()): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "query",
                query = query,
                parameters = parameters,
                notifications = emptyList(),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        )
    }

    fun planCommandReset(id: String, sleep: Boolean, factoryDefaults: Boolean, otaFirmwareUpdate: Boolean): PolarRuntimePlan {
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
        )
    }

    fun resetNotificationFields(id: String, sleep: Boolean, factoryDefaults: Boolean, otaFirmwareUpdate: Boolean): PolarResetNotificationFields {
        return PolarRuntimeOrchestration.resetNotificationFields(
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
    }

    fun planCommandSyncStart(id: String): PolarRuntimePlan {
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
        )
    }

    fun planCommandSyncStop(id: String): PolarRuntimePlan {
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
        )
    }

    fun planDiskTimeQuery(id: String, query: String): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = id,
                kind = "query",
                query = query,
                queries = emptyList(),
                parameters = emptyList(),
                expectedFields = emptyList()
            )
        )
    }

    fun planSetLocalTimeV2(systemTimeHour: Int, localTimeHour: Int): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = "set-local-time-v2",
                kind = "setLocalTimeV2",
                query = null,
                queries = listOf("SET_SYSTEM_TIME", "SET_LOCAL_TIME"),
                parameters = emptyList(),
                expectedFields = listOf("systemTimeHour=$systemTimeHour", "localTimeHour=$localTimeHour", "systemTimeTrusted=true")
            )
        )
    }

    fun planSetLocalTimeH10(localTimeHour: Int): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = "set-local-time-h10",
                kind = "setLocalTimeH10",
                query = null,
                queries = listOf("SET_LOCAL_TIME"),
                parameters = emptyList(),
                expectedFields = listOf("localTimeHour=$localTimeHour")
            )
        )
    }

    fun planRestFacadeGet(id: String, path: String, payloadShape: String? = null): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planRestFacade(
            PolarRestFacadeOperation(
                id = id,
                command = "GET",
                path = path,
                payloadShape = payloadShape,
                expectedFields = emptyList(),
                transportMode = null,
                responseErrorStatus = null,
                responseErrorMessage = null,
                expectedPlatformTerminal = null
            )
        )
    }

    fun planFileFacade(id: String, command: String, path: String, payloadHex: String? = null, responseHex: String? = null): PolarRuntimePlan {
        return PolarRuntimeOrchestration.planFileFacade(
            PolarFileFacadeOperation(
                id = id,
                command = command,
                path = path,
                payloadHex = payloadHex,
                responseHex = responseHex,
                progress = emptyList(),
                transportMode = null
            )
        )
    }

    fun normalizeFileListFolderPath(folderPath: String): String {
        return PolarRuntimeOrchestration.normalizeFileListFolderPath(folderPath)
    }

    fun planFileRuntimeError(operation: String, path: String, error: Throwable) {
        PolarRuntimeOrchestration.planFileRuntimeError(
            PolarFileRuntimeErrorOperation(
                id = "platform-file-runtime-error",
                operation = operation,
                path = path,
                payloadHex = null,
                transportMode = "transportError",
                status = null,
                message = null,
                error = error.javaClass.simpleName.ifEmpty { error.toString() },
                responsePayloadHex = null
            )
        )
    }

    fun planUserDeviceSettingsRead(path: String) {
        PolarRuntimeOrchestration.planUserDeviceSettings(
            PolarUserDeviceSettingsOperation(
                id = "get-user-device-settings",
                kind = "read",
                path = path,
                payloadFields = emptyList()
            )
        )
    }

    fun planUserDeviceSettingsReadThenWrite(id: String, path: String, payloadFields: List<String>) {
        PolarRuntimeOrchestration.planUserDeviceSettings(
            PolarUserDeviceSettingsOperation(
                id = id,
                kind = "readThenWrite",
                path = path,
                payloadFields = payloadFields
            )
        )
    }

    fun planUserDeviceSettingsWrite(path: String, payloadFields: List<String>) {
        PolarRuntimeOrchestration.planUserDeviceSettings(
            PolarUserDeviceSettingsOperation(
                id = "set-user-device-settings",
                kind = "write",
                path = path,
                payloadFields = payloadFields
            )
        )
    }

    fun userDeviceSettingsProtobufPayloadFields(): List<String> {
        return com.polar.shared.sdk.PolarUserDeviceSettingsModels.protobufPayloadFields()
    }

    fun userDeviceSettingsTelemetryPayloadFields(enabled: Boolean): List<String> {
        return com.polar.shared.sdk.PolarUserDeviceSettingsModels.telemetryPayloadFields(enabled)
    }

    fun userDeviceSettingsDeviceLocationPayloadFields(value: Int): List<String> {
        return com.polar.shared.sdk.PolarUserDeviceSettingsModels.deviceLocationPayloadFields(value)
    }

    fun userDeviceSettingsUsbConnectionModePayloadFields(enabled: Boolean): List<String> {
        return com.polar.shared.sdk.PolarUserDeviceSettingsModels.usbConnectionModePayloadFields(enabled)
    }

    fun userDeviceSettingsAutomaticTrainingDetectionPayloadFields(enabled: Boolean, sensitivity: Int, minimumDurationSeconds: Int): List<String> {
        return com.polar.shared.sdk.PolarUserDeviceSettingsModels.automaticTrainingDetectionPayloadFields(enabled, sensitivity, minimumDurationSeconds)
    }

    fun userDeviceSettingsAutomaticOhrPayloadFields(enabled: Boolean): List<String> {
        return com.polar.shared.sdk.PolarUserDeviceSettingsModels.automaticOhrPayloadFields(enabled)
    }

    fun userDeviceSettingsDaylightSavingPayloadFields(): List<String> {
        return com.polar.shared.sdk.PolarUserDeviceSettingsModels.daylightSavingPayloadFields()
    }

    fun planUserDeviceSettingsOperations(id: String, kind: String, path: String, payloadFields: List<String> = emptyList()): List<Pair<PftpRequest.PbPFtpOperation.Command, String>> {
        return PolarRuntimeOrchestration.planUserDeviceSettings(
            PolarUserDeviceSettingsOperation(
                id = id,
                kind = kind,
                path = path,
                payloadFields = payloadFields
            )
        ).commands.mapNotNull { command ->
            val parts = command.split(":", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            when (parts[0]) {
                "read" -> PftpRequest.PbPFtpOperation.Command.GET to parts[1]
                "write" -> PftpRequest.PbPFtpOperation.Command.PUT to parts[1]
                else -> null
            }
        }
    }

    fun userDeviceSettingsPath(fileSystemType: String, deviceSettingsPath: String, sensorSettingsPath: String, unknownSettingsPath: String?): String? {
        return PolarRuntimeOrchestration.userDeviceSettingsPath(fileSystemType, deviceSettingsPath, sensorSettingsPath, unknownSettingsPath)
    }

    fun planStoredDataCleanup(kind: String, rootPath: String, cutoffDate: String? = null) {
        PolarWorkflowRuntimePlanning.planStoredDataCleanup(
            PolarStoredDataCleanupScenario(
                id = "platform-stored-data-cleanup",
                kind = kind,
                rootPath = rootPath,
                cutoffDate = cutoffDate
            )
        )
    }

    fun planStoredDataCleanupOperations(
        kind: String,
        rootPath: String,
        cutoffDate: String? = null,
        entries: List<String> = emptyList(),
        includePrefixes: List<String> = emptyList(),
        includeSuffixes: List<String> = emptyList()
    ): List<Pair<PftpRequest.PbPFtpOperation.Command, String>> {
        return PolarWorkflowRuntimePlanning.planStoredDataCleanup(
            PolarStoredDataCleanupScenario(
                id = "platform-stored-data-cleanup",
                kind = kind,
                rootPath = rootPath,
                cutoffDate = cutoffDate,
                entries = entries,
                includePrefixes = includePrefixes,
                includeSuffixes = includeSuffixes
            )
        ).commands.mapNotNull(::cleanupCommandOperation)
    }

    fun storedDataEntryMatchesFilter(entry: String, includePrefixes: List<String> = emptyList(), includeSuffixes: List<String> = emptyList()): Boolean {
        return PolarWorkflowRuntimePlanning.storedDataEntryMatchesFilter(entry, includePrefixes, includeSuffixes)
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

    fun storedDataEmptyParentDirectories(filePath: String, trailingSlash: Boolean): List<String> {
        return PolarWorkflowRuntimePlanning.storedDataEmptyParentDirectories(filePath, trailingSlash = trailingSlash)
    }

    fun planOfflineTriggerSet(currentTypes: List<String>, desiredTypes: List<String>, secretPresent: Boolean) {
        PolarWorkflowRuntimePlanning.planOfflineTriggerRuntime(
            operation = "setOfflineRecordingTrigger",
            currentDeviceTriggers = currentTypes.map { type -> PolarOfflineTriggerDeviceTrigger(type, "enabled") },
            desiredFeatures = desiredTypes.map { type -> PolarOfflineTriggerDesiredFeature(type, hasSelectedSettings = true) },
            secretPresent = secretPresent,
            transport = PolarOfflineTriggerTransport()
        )
    }

    fun planOfflineTriggerGet(currentTypes: List<String>) {
        PolarWorkflowRuntimePlanning.planOfflineTriggerRuntime(
            operation = "getOfflineRecordingTriggerSetup",
            currentDeviceTriggers = currentTypes.map { type -> PolarOfflineTriggerDeviceTrigger(type, "enabled") }
        )
    }

    fun planFirmwareWorkflow(id: String, statuses: List<String> = emptyList(), firmwareFiles: List<String> = emptyList()) {
        PolarWorkflowRuntimePlanning.planFirmwareWorkflow(
            PolarFirmwareWorkflowScenario(
                id = id,
                expectedStatuses = statuses,
                expectedTerminalStatus = statuses.lastOrNull(),
                expectedStatusOrder = statuses,
                firmwareFiles = firmwareFiles
            )
        )
    }

    fun planFirmwareWriteOperations(firmwareFiles: List<String>): List<Pair<PftpRequest.PbPFtpOperation.Command, String>> {
        return PolarWorkflowRuntimePlanning.planFirmwareWorkflow(
            PolarFirmwareWorkflowScenario(
                id = "write-package-success-with-system-update-last",
                expectedStatusOrder = listOf("preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "finalizingFwUpdate", "fwUpdateCompletedSuccessfully"),
                firmwareFiles = firmwareFiles
            )
        ).writes.map { path -> PftpRequest.PbPFtpOperation.Command.PUT to path }
    }

    fun orderFirmwareFiles(fileNames: List<String>): List<String> {
        return PolarWorkflowRuntimePlanning.orderFirmwareFiles(fileNames)
    }

    fun firmwarePackageEntryIsPayload(fileName: String): Boolean {
        return PolarWorkflowRuntimePlanning.firmwarePackageEntryIsPayload(fileName)
    }

    fun firmwareFileTriggersRebootWait(fileName: String): Boolean {
        return PolarWorkflowRuntimePlanning.firmwareFileTriggersRebootWait(fileName)
    }

    fun firmwareWriteProgressPercent(bytesWritten: Long, payloadSize: Int): Long {
        return PolarWorkflowRuntimePlanning.firmwareWriteProgressPercent(bytesWritten.toInt(), payloadSize).toLong()
    }

    fun shouldEmitFirmwareWriteProgress(
        lastBytesWritten: Long,
        bytesWritten: Long,
        payloadSize: Int,
        minPercentageIncrement: Long,
        timeSinceLastEmitMs: Long,
        maxEmitIntervalMs: Long = 5_000L
    ): Boolean {
        return PolarWorkflowRuntimePlanning.shouldEmitFirmwareWriteProgress(
            lastBytesWritten = lastBytesWritten.toInt(),
            bytesWritten = bytesWritten.toInt(),
            payloadSize = payloadSize,
            minPercentageIncrement = minPercentageIncrement.toInt()
        ) || timeSinceLastEmitMs >= maxEmitIntervalMs
    }

    fun planBackupRestore(path: String, payloadHex: String, writeResult: String = "success") {
        PolarWorkflowRuntimePlanning.planBackupRestore(
            listOf(
                PolarBackupRestoreFile(
                    directory = path.substringBeforeLast('/', missingDelimiterValue = "") + "/",
                    fileName = path.substringAfterLast('/'),
                    dataHex = payloadHex,
                    writeResult = writeResult
                )
            )
        )
    }

    fun defaultBackupPaths(): List<String> {
        return PolarWorkflowRuntimePlanning.defaultBackupPaths()
    }

    fun backupRootPaths(entries: Collection<String>): List<String> {
        return PolarWorkflowRuntimePlanning.backupRootPaths(entries.toList())
    }

    fun parseBackupTextForAndroid(backupText: String): List<String> {
        return PolarWorkflowRuntimePlanning.parseBackupTextForAndroid(backupText)
    }

    fun backupTraversalRootPath(path: String): String {
        return PolarWorkflowRuntimePlanning.backupTraversalRootPath(path)
    }

    fun backupFilePath(path: String): Pair<String, String> {
        val filePath = PolarWorkflowRuntimePlanning.backupFilePath(path)
        return filePath.directory to filePath.fileName
    }

    fun sleepAnalysisPath(day: String): String {
        return PolarSleepModels.sleepAnalysisPath(day)
    }

    fun sleepSkinTemperaturePath(day: String): String {
        return PolarSleepModels.sleepSkinTemperaturePath(day)
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

    fun nightlyRechargePath(day: String): String {
        return PolarSleepModels.nightlyRechargePath(day)
    }

    fun skinTemperaturePath(day: String): String {
        return PolarSdkModelMappers.skinTemperaturePath(day)
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

    fun spo2TestDirectoryPath(day: String): String {
        return PolarSpo2Models.testDirectoryPath(day)
    }

    fun spo2TestResultPath(directoryPath: String, subDirectoryName: String): String {
        return PolarSpo2Models.testResultPath(directoryPath, subDirectoryName)
    }

    fun trainingSessionDeleteParentPath(referencePath: String): String {
        return PolarTrainingSessionModels.deleteParentPath(referencePath)
    }

    fun trainingSessionDeleteRemovePath(referencePath: String, parentEntryCount: Int): String {
        return PolarTrainingSessionModels.deleteRemovePath(referencePath, parentEntryCount)
    }

    fun firmwareDeviceInfoPath(): String {
        return PolarFirmwareUpdateModels.deviceInfoPath()
    }

    fun planBackupRestoreOperation(path: String, payloadHex: String, writeResult: String = "success"): Pair<PftpRequest.PbPFtpOperation.Command, String>? {
        val command = PolarWorkflowRuntimePlanning.planBackupRestore(
            listOf(
                PolarBackupRestoreFile(
                    directory = path.substringBeforeLast('/', missingDelimiterValue = "") + "/",
                    fileName = path.substringAfterLast('/'),
                    dataHex = payloadHex,
                    writeResult = writeResult
                )
            )
        ).commands.firstOrNull { it.startsWith("PUT:") } ?: return null
        val parts = command.split(":", limit = 3)
        if (parts.size != 3 || parts[0] != "PUT") return null
        return PftpRequest.PbPFtpOperation.Command.PUT to parts[1]
    }

    fun planPsFtpWriteProgress(payloadSize: Int, platform: String): List<Int> {
        return PolarWorkflowRuntimePlanning.planPsFtpWriteProgress(payloadSize, platform)
    }

    fun planPsFtpWriteAck(payloadSize: Int, writeAck: String = "success") {
        PolarWorkflowRuntimePlanning.planPsFtpWrite(ByteArray(maxOf(payloadSize, 1)) { 0 }, writeAck = writeAck)
    }

    fun queryValue(plan: PolarRuntimePlan): Int {
        return queryValue(queryName(plan))
    }

    fun queryValue(queryName: String): Int {
        return when (queryName) {
            "GET_DISK_SPACE" -> PftpRequest.PbPFtpQuery.GET_DISK_SPACE_VALUE
            "GET_LOCAL_TIME" -> PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE
            "REQUEST_START_RECORDING" -> PftpRequest.PbPFtpQuery.REQUEST_START_RECORDING_VALUE
            "REQUEST_STOP_RECORDING" -> PftpRequest.PbPFtpQuery.REQUEST_STOP_RECORDING_VALUE
            "REQUEST_RECORDING_STATUS" -> PftpRequest.PbPFtpQuery.REQUEST_RECORDING_STATUS_VALUE
            "START_EXERCISE" -> PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE
            "PAUSE_EXERCISE" -> PftpRequest.PbPFtpQuery.PAUSE_EXERCISE_VALUE
            "RESUME_EXERCISE" -> PftpRequest.PbPFtpQuery.RESUME_EXERCISE_VALUE
            "START_DM_EXERCISE" -> PftpRequest.PbPFtpQuery.START_DM_EXERCISE_VALUE
            "STOP_EXERCISE" -> PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE
            "GET_EXERCISE_STATUS" -> PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE
            "PREPARE_FIRMWARE_UPDATE" -> PftpRequest.PbPFtpQuery.PREPARE_FIRMWARE_UPDATE_VALUE
            "REQUEST_SYNCHRONIZATION" -> PftpRequest.PbPFtpQuery.REQUEST_SYNCHRONIZATION_VALUE
            "SET_LOCAL_TIME" -> PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE
            "SET_SYSTEM_TIME" -> PftpRequest.PbPFtpQuery.SET_SYSTEM_TIME_VALUE
            else -> error("Unsupported shared runtime query command $queryName")
        }
    }

    fun notificationValue(notificationName: String): Int {
        val normalized = notificationName.substringBefore(':')
        return when (normalized) {
            "RESET" -> PftpNotification.PbPFtpHostToDevNotification.RESET_VALUE
            "INITIALIZE_SESSION" -> PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE
            "START_SYNC" -> PftpNotification.PbPFtpHostToDevNotification.START_SYNC_VALUE
            "STOP_SYNC" -> PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE
            "TERMINATE_SESSION" -> PftpNotification.PbPFtpHostToDevNotification.TERMINATE_SESSION_VALUE
            else -> error("Unsupported shared runtime notification command $notificationName")
        }
    }

    fun queryName(plan: PolarRuntimePlan): String {
        return plan.commands.first { command -> command.startsWith("query:") }.substringAfter("query:")
    }

    fun queryNames(plan: PolarRuntimePlan): List<String> {
        return plan.commands
            .filter { command -> command.startsWith("query:") }
            .map { command -> command.substringAfter("query:") }
    }

    fun notificationNames(plan: PolarRuntimePlan): List<String> {
        return plan.commands
            .filter { command -> command.startsWith("notification:") }
            .map { command -> command.substringAfter("notification:") }
    }

    fun fileOperationCommand(plan: PolarRuntimePlan): PftpRequest.PbPFtpOperation.Command {
        return when (fileOperationCommandName(plan)) {
            "GET" -> PftpRequest.PbPFtpOperation.Command.GET
            "PUT" -> PftpRequest.PbPFtpOperation.Command.PUT
            "REMOVE" -> PftpRequest.PbPFtpOperation.Command.REMOVE
            else -> error("Unsupported shared runtime file operation command ${fileOperationCommandName(plan)}")
        }
    }

    fun fileOperationPath(plan: PolarRuntimePlan): String {
        return fileOperationToken(plan).substringAfter(':')
    }

    fun fileOperationBytes(plan: PolarRuntimePlan): ByteArray {
        return fileOperationBytes(fileOperationCommand(plan) to fileOperationPath(plan))
    }

    fun fileOperationBytes(operation: Pair<PftpRequest.PbPFtpOperation.Command, String>): ByteArray {
        return PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(operation.first)
            .setPath(operation.second)
            .build()
            .toByteArray()
    }

    private fun fileOperationCommandName(plan: PolarRuntimePlan): String {
        return fileOperationToken(plan).substringBefore(':')
    }

    private fun fileOperationToken(plan: PolarRuntimePlan): String {
        return plan.commands.first { command ->
            command.startsWith("GET:") ||
                command.startsWith("PUT:") ||
                command.startsWith("REMOVE:")
        }
    }

    private fun cleanupCommandOperation(command: String): Pair<PftpRequest.PbPFtpOperation.Command, String>? {
        val parts = command.split(":", limit = 2)
        if (parts.size != 2) return null
        return when (parts[0]) {
            "GET" -> PftpRequest.PbPFtpOperation.Command.GET to parts[1]
            "REMOVE" -> PftpRequest.PbPFtpOperation.Command.REMOVE to parts[1]
            else -> null
        }
    }
}
