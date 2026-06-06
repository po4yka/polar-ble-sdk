// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarRuntimePlanner {
    @discardableResult
    static func commandQuery(id: String, query: String, parameters: [String] = []) -> String {
        return PolarCommandRuntimePlanner.query(id: id, query: query, parameters: parameters)
    }

    static func commandQueryValue(id: String, query: String, parameters: [String] = []) -> Int? {
        return PolarCommandRuntimePlanner.queryValue(id: id, query: query, parameters: parameters)
    }

    @discardableResult
    static func commandReset(id: String, sleep: Bool, factoryDefaults: Bool, otaFirmwareUpdate: Bool) -> String {
        return PolarCommandRuntimePlanner.reset(id: id, sleep: sleep, factoryDefaults: factoryDefaults, otaFirmwareUpdate: otaFirmwareUpdate)
    }

    static func commandResetNotification(id: String, sleep: Bool, factoryDefaults: Bool, otaFirmwareUpdate: Bool) -> Int? {
        return PolarCommandRuntimePlanner.resetNotification(id: id, sleep: sleep, factoryDefaults: factoryDefaults, otaFirmwareUpdate: otaFirmwareUpdate)
    }

    static func commandResetFields(id: String, sleep: Bool, factoryDefaults: Bool, otaFirmwareUpdate: Bool) -> (sleep: Bool, factoryDefaults: Bool, otaFirmwareUpdate: Bool) {
        return PolarCommandRuntimePlanner.resetFields(id: id, sleep: sleep, factoryDefaults: factoryDefaults, otaFirmwareUpdate: otaFirmwareUpdate)
    }

    static func h10StartRecordingFields(id: String, sampleDataIdentifier: String, sampleType: String, recordingIntervalSeconds: Int) -> (sampleDataIdentifier: String, sampleType: String, recordingIntervalSeconds: Int) {
        return PolarCommandRuntimePlanner.h10StartRecordingFields(id: id, sampleDataIdentifier: sampleDataIdentifier, sampleType: sampleType, recordingIntervalSeconds: recordingIntervalSeconds)
    }

    static func syncStopNotificationCompleted(id: String) -> Bool {
        return PolarCommandRuntimePlanner.syncStopNotificationCompleted(id: id)
    }

    @discardableResult
    static func commandSyncStart(id: String) -> String {
        return PolarCommandRuntimePlanner.syncStart(id: id)
    }

    static func commandSyncStartNotifications(id: String) -> [Int]? {
        return PolarCommandRuntimePlanner.syncStartNotifications(id: id)
    }

    static func commandSyncStartQueryValue(id: String) -> Int? {
        return PolarCommandRuntimePlanner.syncStartQueryValue(id: id)
    }

    @discardableResult
    static func commandSyncStop(id: String) -> String {
        return PolarCommandRuntimePlanner.syncStop(id: id)
    }

    static func commandSyncStopNotifications(id: String) -> [Int]? {
        return PolarCommandRuntimePlanner.syncStopNotifications(id: id)
    }

    @discardableResult
    static func diskTimeQuery(id: String, query: String) -> String {
        return PolarDiskTimeRuntimePlanner.query(id: id, query: query)
    }

    static func diskTimeQueryValue(id: String, query: String) -> Int? {
        return PolarDiskTimeRuntimePlanner.queryValue(id: id, query: query)
    }

    @discardableResult
    static func setLocalTimeV2(systemTimeHour: Int, localTimeHour: Int) -> String {
        return PolarDiskTimeRuntimePlanner.setLocalTimeV2(systemTimeHour: systemTimeHour, localTimeHour: localTimeHour)
    }

    static func setLocalTimeV2QueryValues(systemTimeHour: Int, localTimeHour: Int) -> [Int]? {
        return PolarDiskTimeRuntimePlanner.setLocalTimeV2QueryValues(systemTimeHour: systemTimeHour, localTimeHour: localTimeHour)
    }

    @discardableResult
    static func setLocalTimeH10(localTimeHour: Int) -> String {
        return PolarDiskTimeRuntimePlanner.setLocalTimeH10(localTimeHour: localTimeHour)
    }

    static func setLocalTimeH10QueryValues(localTimeHour: Int) -> [Int]? {
        return PolarDiskTimeRuntimePlanner.setLocalTimeH10QueryValues(localTimeHour: localTimeHour)
    }

    @discardableResult
    static func restFacadeGet(id: String, path: String, payloadShape: String) -> String {
        return PolarRestFacadeRuntimePlanner.get(id: id, path: path, payloadShape: payloadShape)
    }

    static func restFacadeGetOperation(id: String, path: String, payloadShape: String) -> (command: Protocol_PbPFtpOperation.Command, path: String)? {
        return PolarRestFacadeRuntimePlanner.getOperation(id: id, path: path, payloadShape: payloadShape)
    }

    static func sleepRestApiPath() -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.sleepRestApiPath()
        #else
        return "/REST/SLEEP.API"
        #endif
    }

    static func sleepRecordingStateSubscribePath() -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.sleepRecordingStateSubscribePath()
        #else
        return "/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]"
        #endif
    }

    static func stopSleepRecordingPath() -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.stopSleepRecordingPath()
        #else
        return "/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording"
        #endif
    }

    @discardableResult
    static func fileFacade(id: String, command: String, path: String, payloadHex: String = "") -> String {
        return PolarFileFacadeRuntimePlanner.fileFacade(id: id, command: command, path: path, payloadHex: payloadHex)
    }

    static func fileFacadeOperation(id: String, command: String, path: String, payloadHex: String = "") -> (command: Protocol_PbPFtpOperation.Command, path: String)? {
        return PolarFileFacadeRuntimePlanner.fileFacadeOperation(id: id, command: command, path: path, payloadHex: payloadHex)
    }

    static func fileOperationBytes(_ operation: (command: Protocol_PbPFtpOperation.Command, path: String)) throws -> Data {
        let request = Protocol_PbPFtpOperation.with {
            $0.command = operation.command
            $0.path = operation.path
        }
        return try request.serializedBytes()
    }

    static func normalizeFileListFolderPath(_ folderPath: String) -> String {
        return PolarFileFacadeRuntimePlanner.normalizeFileListFolderPath(folderPath)
    }

    @discardableResult
    static func fileRuntimeError(operation: String, path: String, error: Error) -> String {
        return PolarFileRuntimePlanner.runtimeError(operation: operation, path: path, error: error)
    }

    @discardableResult
    static func userDeviceSettings(id: String, kind: String, path: String, payloadFields: [String] = []) -> String {
        return PolarUserDeviceSettingsRuntimePlanner.plan(id: id, kind: kind, path: path, payloadFields: payloadFields)
    }

    static func userDeviceSettingsOperations(id: String, kind: String, path: String, payloadFields: [String] = []) -> [(command: Protocol_PbPFtpOperation.Command, path: String)]? {
        return PolarUserDeviceSettingsRuntimePlanner.operations(id: id, kind: kind, path: path, payloadFields: payloadFields)
    }

    static func userDeviceSettingsPath(fileSystemType: String, deviceSettingsPath: String = "/U/0/S/UDEVSET.BPB", sensorSettingsPath: String = "/UDEVSET.BPB", unknownSettingsPath: String? = "/U/0/S/UDEVSET.BPB") -> String? {
        return PolarUserDeviceSettingsRuntimePlanner.settingsPath(fileSystemType: fileSystemType, deviceSettingsPath: deviceSettingsPath, sensorSettingsPath: sensorSettingsPath, unknownSettingsPath: unknownSettingsPath)
    }

    static func userDeviceSettingsDeviceLocationName(value: Int) -> String? {
        return PolarUserDeviceSettingsRuntimePlanner.deviceLocationName(value: value)
    }

    static func userDeviceSettingsUsbConnectionModeName(enabled: Bool) -> String? {
        return PolarUserDeviceSettingsRuntimePlanner.usbConnectionModeName(enabled: enabled)
    }

    static func userDeviceSettingsAutomaticTrainingDetectionModeName(enabled: Bool) -> String? {
        return PolarUserDeviceSettingsRuntimePlanner.automaticTrainingDetectionModeName(enabled: enabled)
    }

    static func userDeviceSettingsProtobufPayloadFields() -> [String] {
        return PolarUserDeviceSettingsRuntimePlanner.protobufPayloadFields()
    }

    static func userDeviceSettingsTelemetryPayloadFields(enabled: Bool) -> [String] {
        return PolarUserDeviceSettingsRuntimePlanner.telemetryPayloadFields(enabled: enabled)
    }

    static func userDeviceSettingsDeviceLocationPayloadFields(value: Int) -> [String] {
        return PolarUserDeviceSettingsRuntimePlanner.deviceLocationPayloadFields(value: value)
    }

    static func userDeviceSettingsUsbConnectionModePayloadFields(enabled: Bool) -> [String] {
        return PolarUserDeviceSettingsRuntimePlanner.usbConnectionModePayloadFields(enabled: enabled)
    }

    static func userDeviceSettingsAutomaticTrainingDetectionPayloadFields(enabled: Bool, sensitivity: Int, minimumDurationSeconds: Int) -> [String] {
        return PolarUserDeviceSettingsRuntimePlanner.automaticTrainingDetectionPayloadFields(enabled: enabled, sensitivity: sensitivity, minimumDurationSeconds: minimumDurationSeconds)
    }

    static func userDeviceSettingsAutomaticOhrPayloadFields(enabled: Bool) -> [String] {
        return PolarUserDeviceSettingsRuntimePlanner.automaticOhrPayloadFields(enabled: enabled)
    }

    static func userDeviceSettingsDaylightSavingPayloadFields() -> [String] {
        return PolarUserDeviceSettingsRuntimePlanner.daylightSavingPayloadFields()
    }

    @discardableResult
    static func storedDataCleanup(kind: String, rootPath: String, cutoffDate: String? = nil) -> String {
        return PolarStoredDataOfflineRuntimePlanner.storedDataCleanup(kind: kind, rootPath: rootPath, cutoffDate: cutoffDate)
    }

    static func storedDataEntryMatchesFilter(entry: String, includePrefixes: [String] = [], includeSuffixes: [String] = []) -> Bool? {
        return PolarStoredDataOfflineRuntimePlanner.storedDataEntryMatchesFilter(entry: entry, includePrefixes: includePrefixes, includeSuffixes: includeSuffixes)
    }

    static func storedDataCleanupDirectoryEntryMatches(dataType: String, entry: String, cutoffFolder: String? = nil) -> Bool? {
        return PolarStoredDataOfflineRuntimePlanner.storedDataCleanupDirectoryEntryMatches(dataType: dataType, entry: entry, cutoffFolder: cutoffFolder)
    }

    static func shouldPruneStoredDataEmptyParents(dataType: String) -> Bool? {
        return PolarStoredDataOfflineRuntimePlanner.shouldPruneStoredDataEmptyParents(dataType: dataType)
    }

    static func storedDataCleanupRootPath(dataType: String, defaultRoot: String = "/U/0") -> String? {
        return PolarStoredDataOfflineRuntimePlanner.storedDataCleanupRootPath(dataType: dataType, defaultRoot: defaultRoot)
    }

    static func storedDataCleanupRemovePaths(kind: String, rootPath: String, cutoffDate: String? = nil, entries: [String] = [], includePrefixes: [String] = [], includeSuffixes: [String] = []) -> [String]? {
        return PolarStoredDataOfflineRuntimePlanner.storedDataCleanupRemovePaths(kind: kind, rootPath: rootPath, cutoffDate: cutoffDate, entries: entries, includePrefixes: includePrefixes, includeSuffixes: includeSuffixes)
    }

    static func storedDataDateIsOnOrBefore(day: String, cutoffDate: String) -> Bool? {
        return PolarStoredDataOfflineRuntimePlanner.storedDataDateIsOnOrBefore(day: day, cutoffDate: cutoffDate)
    }

    static func storedDataEmptyParentDirectories(filePath: String, rootPath: String = "/U/0", trailingSlash: Bool = true) -> [String]? {
        return PolarStoredDataOfflineRuntimePlanner.storedDataEmptyParentDirectories(filePath: filePath, rootPath: rootPath, trailingSlash: trailingSlash)
    }

    @discardableResult
    static func offlineTriggerSet(currentTypes: [String], desiredTypes: [String], secretPresent: Bool) -> String {
        return PolarStoredDataOfflineRuntimePlanner.offlineTriggerSet(currentTypes: currentTypes, desiredTypes: desiredTypes, secretPresent: secretPresent)
    }

    @discardableResult
    static func offlineTriggerGet(currentTypes: [String]) -> String {
        return PolarStoredDataOfflineRuntimePlanner.offlineTriggerGet(currentTypes: currentTypes)
    }

    @discardableResult
    static func firmwareWorkflow(id: String, statuses: [String] = [], firmwareFiles: [String] = []) -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwareWorkflow(id: id, statuses: statuses, firmwareFiles: firmwareFiles)
    }

    static func orderFirmwareFiles(_ fileNames: [String]) -> [String] {
        return PolarFirmwareBackupRuntimePlanner.orderFirmwareFiles(fileNames)
    }

    static func firmwareWritePaths(_ fileNames: [String]) -> [String] {
        return PolarFirmwareBackupRuntimePlanner.firmwareWritePaths(fileNames)
    }

    static func firmwarePackageEntryIsPayload(_ fileName: String) -> Bool {
        return PolarFirmwareBackupRuntimePlanner.firmwarePackageEntryIsPayload(fileName)
    }

    static func firmwareFileTriggersRebootWait(_ fileName: String) -> Bool {
        return PolarFirmwareBackupRuntimePlanner.firmwareFileTriggersRebootWait(fileName)
    }

    static func firmwareWriteProgressPercent(bytesWritten: Int, payloadSize: Int) -> Int {
        return PolarFirmwareBackupRuntimePlanner.firmwareWriteProgressPercent(bytesWritten: bytesWritten, payloadSize: payloadSize)
    }

    static func shouldEmitFirmwareWriteProgress(lastBytesWritten: Int, bytesWritten: Int, payloadSize: Int, minPercentageIncrement: Int, timeSinceLastEmitMs: Int? = nil, maxEmitIntervalMs: Int = 5_000) -> Bool {
        return PolarFirmwareBackupRuntimePlanner.shouldEmitFirmwareWriteProgress(
            lastBytesWritten: lastBytesWritten,
            bytesWritten: bytesWritten,
            payloadSize: payloadSize,
            minPercentageIncrement: minPercentageIncrement,
            timeSinceLastEmitMs: timeSinceLastEmitMs,
            maxEmitIntervalMs: maxEmitIntervalMs
        )
    }

    static func firmwareDeviceInfoPath() -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwareDeviceInfoPath()
    }

    static func firmwareFilePriority(_ fileName: String) -> Int {
        return PolarFirmwareBackupRuntimePlanner.firmwareFilePriority(fileName)
    }

    static func isFirmwareVersionHigher(currentVersion: String, availableVersion: String) -> Bool {
        return PolarFirmwareBackupRuntimePlanner.isFirmwareVersionHigher(currentVersion: currentVersion, availableVersion: availableVersion)
    }

    static func firmwareDeviceVersion(major: Int, minor: Int, patch: Int) -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwareDeviceVersion(major: major, minor: minor, patch: patch)
    }

    static func backupRootPaths(_ entries: [String]) -> [String] {
        return PolarFirmwareBackupRuntimePlanner.backupRootPaths(entries)
    }

    static func parseBackupTextForIos(_ backupText: String) -> [String] {
        return PolarFirmwareBackupRuntimePlanner.parseBackupTextForIos(backupText)
    }

    static func backupFilePath(_ path: String) -> (directory: String, fileName: String) {
        return PolarFirmwareBackupRuntimePlanner.backupFilePath(path)
    }

    @discardableResult
    static func backupRestore(path: String, payloadHex: String, writeResult: String = "success") -> String {
        return PolarFirmwareBackupRuntimePlanner.backupRestore(path: path, payloadHex: payloadHex, writeResult: writeResult)
    }

    static func backupRestoreOperation(path: String, payloadHex: String, writeResult: String = "success") -> (command: Protocol_PbPFtpOperation.Command, path: String)? {
        return PolarFirmwareBackupRuntimePlanner.backupRestoreOperation(path: path, payloadHex: payloadHex, writeResult: writeResult)
    }

    static func psFtpWriteProgress(payloadSize: Int, platform: String = "ios") -> [Int] {
        return PolarFileRuntimePlanner.psFtpWriteProgress(payloadSize: payloadSize, platform: platform)
    }

    @discardableResult
    static func psFtpWriteAck(payloadSize: Int, writeAck: String = "success") -> String {
        return PolarFileRuntimePlanner.psFtpWriteAck(payloadSize: payloadSize, writeAck: writeAck)
    }

    static func d2hNotificationTypeName(notificationId: Int) -> String? {
        return PolarD2hRuntimePlanner.notificationTypeName(notificationId: notificationId)
    }

    static func d2hParsedProtoName(notificationType: String, parametersHex: String) -> String? {
        return PolarD2hRuntimePlanner.parsedProtoName(notificationType: notificationType, parametersHex: parametersHex)
    }

    static func d2hNotificationPlan(notificationId: Int, parametersHex: String) -> (notificationType: String, parsedProtoName: String?)? {
        return PolarD2hRuntimePlanner.notificationPlan(notificationId: notificationId, parametersHex: parametersHex)
    }

    @discardableResult
    static func streamSubscription(target: String, startConnected: Bool, checkConnection: Bool) -> String {
        return PolarStreamRuntimePlanner.subscription(target: target, startConnected: startConnected, checkConnection: checkConnection)
    }

    @discardableResult
    static func streamConsumerCancellation(target: String) -> String {
        return PolarStreamRuntimePlanner.consumerCancellation(target: target)
    }

    @discardableResult
    static func streamDisconnect(target: String, error: String) -> String {
        return PolarStreamRuntimePlanner.disconnect(target: target, error: error)
    }

    @discardableResult
    static func streamDuplicateCompletion(target: String) -> Int {
        return PolarStreamRuntimePlanner.duplicateCompletion(target: target)
    }

    @discardableResult
    static func streamPostCompletionEmission(target: String, value: String) -> Int {
        return PolarStreamRuntimePlanner.postCompletionEmission(target: target, value: value)
    }
}
