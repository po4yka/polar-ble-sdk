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

    @discardableResult
    static func restRequestTransportGet(path: String, payloadHex: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeRestRequestTransportGet(path: path, payloadHex: payloadHex)
        #else
        return payloadHex.isEmpty ? "requires-empty-response-policy" : "platform-owned"
        #endif
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

    static func offlineTriggerSetCommands(currentTypes: [String], desiredTypes: [String], secretPresent: Bool) -> [String] {
        return PolarStoredDataOfflineRuntimePlanner.offlineTriggerSetCommands(currentTypes: currentTypes, desiredTypes: desiredTypes, secretPresent: secretPresent)
    }

    static func offlineTriggerEnabledFeatures(currentTypes: [String]) -> [String] {
        return PolarStoredDataOfflineRuntimePlanner.offlineTriggerEnabledFeatures(currentTypes: currentTypes)
    }

    @discardableResult
    static func firmwareWorkflow(id: String, statuses: [String] = [], firmwareFiles: [String] = []) -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwareWorkflow(id: id, statuses: statuses, firmwareFiles: firmwareFiles)
    }

    @discardableResult
    static func invalidFirmwarePackageWorkflow() -> String {
        return PolarFirmwareBackupRuntimePlanner.invalidFirmwarePackageWorkflow()
    }

    @discardableResult
    static func firmwarePackageDownloadFailureWorkflow() -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwarePackageDownloadFailureWorkflow()
    }

    @discardableResult
    static func firmwareCheckUpdateAvailableWorkflow() -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwareCheckUpdateAvailableWorkflow()
    }

    @discardableResult
    static func firmwareCheckUpdateNotAvailableWorkflow() -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwareCheckUpdateNotAvailableWorkflow()
    }

    @discardableResult
    static func firmwareRetryableServerFailureWorkflow() -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwareRetryableServerFailureWorkflow()
    }

    static func firmwareRetryableServerFailureTerminalError() -> String? {
        return PolarFirmwareBackupRuntimePlanner.firmwareRetryableServerFailureTerminalError()
    }

    @discardableResult
    static func firmwareClientRequestFailureWorkflow() -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwareClientRequestFailureWorkflow()
    }

    static func firmwareClientRequestFailureTerminalError() -> String? {
        return PolarFirmwareBackupRuntimePlanner.firmwareClientRequestFailureTerminalError()
    }

    @discardableResult
    static func firmwarePackageFetchCancellationWorkflow() -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwarePackageFetchCancellationWorkflow()
    }

    static func firmwarePackageFetchCancellationTerminalError() -> String? {
        return PolarFirmwareBackupRuntimePlanner.firmwarePackageFetchCancellationTerminalError()
    }

    @discardableResult
    static func firmwareSystemUpdateRebootSuccessWorkflow(fileNames: [String]) -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwareSystemUpdateRebootSuccessWorkflow(fileNames: fileNames)
    }

    @discardableResult
    static func firmwareBatteryTooLowTerminalWorkflow(fileNames: [String]) -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwareBatteryTooLowTerminalWorkflow(fileNames: fileNames)
    }

    static func firmwareBatteryTooLowTerminalError(fileNames: [String]) -> String? {
        return PolarFirmwareBackupRuntimePlanner.firmwareBatteryTooLowTerminalError(fileNames: fileNames)
    }

    static func orderFirmwareFiles(_ fileNames: [String]) -> [String] {
        return PolarFirmwareBackupRuntimePlanner.orderFirmwareFiles(fileNames)
    }

    static func firmwarePayloadFileNames(_ fileNames: [String]) -> [String] {
        return PolarFirmwareBackupRuntimePlanner.firmwarePayloadFileNames(fileNames)
    }

    static func firmwareWritePaths(_ fileNames: [String]) -> [String] {
        return PolarFirmwareBackupRuntimePlanner.firmwareWritePaths(fileNames)
    }

    static func firmwareRetryDelaysMillis(maxRetries: Int) -> [Int64] {
        return PolarFirmwareBackupRuntimePlanner.firmwareRetryDelaysMillis(maxRetries: maxRetries)
    }

    static func firmwareAvailabilityFailureIsRetryable(details: String) -> Bool {
        return PolarFirmwareBackupRuntimePlanner.firmwareAvailabilityFailureIsRetryable(details: details)
    }

    static func firmwarePackageEntryIsPayload(_ fileName: String) -> Bool {
        return PolarFirmwareBackupRuntimePlanner.firmwarePackageEntryIsPayload(fileName)
    }

    static func firmwareFileTriggersRebootWait(_ fileName: String) -> Bool {
        return PolarFirmwareBackupRuntimePlanner.firmwareFileTriggersRebootWait(fileName)
    }

    static func firmwareWriteTerminal(errorCode: Int, fileName: String) -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwareWriteTerminal(errorCode: errorCode, fileName: fileName)
    }

    static func firmwareFinalizationSteps(hasH10FileSystem: Bool, isDeviceSensor: Bool) -> [String] {
        return PolarFirmwareBackupRuntimePlanner.firmwareFinalizationSteps(hasH10FileSystem: hasH10FileSystem, isDeviceSensor: isDeviceSensor)
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

    static func firmwareSystemUpdateFilePath() -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwareSystemUpdateFilePath()
    }

    static func firmwareFilePriority(_ fileName: String) -> Int {
        return PolarFirmwareBackupRuntimePlanner.firmwareFilePriority(fileName)
    }

    static func isFirmwareVersionHigher(currentVersion: String, availableVersion: String) -> Bool {
        return PolarFirmwareBackupRuntimePlanner.isFirmwareVersionHigher(currentVersion: currentVersion, availableVersion: availableVersion)
    }

    static func firmwareUpdateIsAvailable(currentVersion: String, availableVersion: String, fileUrl: String) -> Bool {
        return PolarFirmwareBackupRuntimePlanner.firmwareUpdateIsAvailable(currentVersion: currentVersion, availableVersion: availableVersion, fileUrl: fileUrl)
    }

    static func ledConfigPayload(sdkModeLedEnabled: Bool, ppiModeLedEnabled: Bool) -> Data {
        #if canImport(PolarBleSdkShared)
        let csv = PolarIosSharedBridge.shared.ledConfigPayloadCsv(sdkModeLedEnabled: sdkModeLedEnabled, ppiModeLedEnabled: ppiModeLedEnabled)
        let bytes = csv.split(separator: ",").compactMap { UInt8($0) }
        return Data(bytes)
        #else
        return Data([
            sdkModeLedEnabled ? LedConfig.LED_ANIMATION_ENABLE_BYTE : LedConfig.LED_ANIMATION_DISABLE_BYTE,
            ppiModeLedEnabled ? LedConfig.LED_ANIMATION_ENABLE_BYTE : LedConfig.LED_ANIMATION_DISABLE_BYTE
        ])
        #endif
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

    static func backupRestoreWrites(_ files: [(directory: String, fileName: String, payloadHex: String)]) -> [PolarFirmwareBackupRuntimePlanner.BackupRestoreWrite] {
        return PolarFirmwareBackupRuntimePlanner.backupRestoreWrites(files)
    }

    static func psFtpWriteProgress(payloadSize: Int, platform: String = "ios") -> [Int] {
        return PolarFileRuntimePlanner.psFtpWriteProgress(payloadSize: payloadSize, platform: platform)
    }

    @discardableResult
    static func psFtpWriteAck(payloadSize: Int, writeAck: String = "success") -> String {
        return PolarFileRuntimePlanner.psFtpWriteAck(payloadSize: payloadSize, writeAck: writeAck)
    }

    static func ensurePsFtpWriteProgressPlan(payloadSize: Int, platform: String = "ios") throws {
        let progress = psFtpWriteProgress(payloadSize: payloadSize, platform: platform)
        guard progress.isEmpty || progress.contains(payloadSize) else {
            throw NSError(domain: "PolarRuntimePlanner", code: -1, userInfo: [NSLocalizedDescriptionKey: "PSFTP write progress planning failed for payload size \(payloadSize): \(progress)"])
        }
    }

    static func ensurePsFtpWriteAckTerminal(payloadSize: Int, writeAck: String = "success") throws {
        let terminal = psFtpWriteAck(payloadSize: payloadSize, writeAck: writeAck)
        guard terminal == "success" || terminal == "platform-owned" else {
            throw NSError(domain: "PolarRuntimePlanner", code: -1, userInfo: [NSLocalizedDescriptionKey: "PSFTP write ACK planning failed: \(terminal)"])
        }
    }

    static func ensurePsFtpWriteRuntimePlan(payloadSize: Int, platform: String = "ios", writeAck: String = "success") throws {
        try ensurePsFtpWriteProgressPlan(payloadSize: payloadSize, platform: platform)
        try ensurePsFtpWriteAckTerminal(payloadSize: payloadSize, writeAck: writeAck)
    }

    static func trainingSessionPayloadFetchOrder(referenceText: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.trainingSessionPayloadFetchOrder(referenceText: referenceText)
        #else
        return ""
        #endif
    }

    static func trainingSessionPayloadReadPlan(referenceText: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.trainingSessionPayloadReadPlan(referenceText: referenceText)
        #else
        return ""
        #endif
    }

    static func trainingSessionPayloadParserCase(fileName: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.trainingSessionPayloadParserCase(fileName: fileName)
        #else
        return nil
        #endif
    }

    static func trainingSessionPublicModelSlot(fileName: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.trainingSessionPublicModelSlot(fileName: fileName)
        #else
        return nil
        #endif
    }

    static func decodeTrainingSessionPayload(fileName: String, payload: Data) -> Data? {
        #if canImport(PolarBleSdkShared)
        guard let decodedHex = PolarIosSharedBridge.shared.trainingSessionDecodePayloadHex(fileName: fileName, payloadHex: payload.hexString()) else {
            return nil
        }
        return Data(hexString: decodedHex)
        #else
        return nil
        #endif
    }

    static func trainingSessionPayloadMalformed(fileName: String, payload: Data) -> Bool {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.trainingSessionPayloadMalformed(fileName: fileName, payloadHex: payload.hexString())
        #else
        return false
        #endif
    }

    static func trainingSessionDeleteParentPath(referencePath: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.trainingSessionDeleteParentPath(referencePath: referencePath)
        #else
        let components = referencePath.split(separator: "/")
        return "/U/0/" + components[2] + "/E/"
        #endif
    }

    static func trainingSessionDeleteRemovePath(referencePath: String, parentEntryCount: Int) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.trainingSessionDeleteRemovePath(referencePath: referencePath, parentEntryCount: Int32(parentEntryCount))
        #else
        let components = referencePath.split(separator: "/")
        return parentEntryCount <= 1 ? "/U/0/" + components[2] + "/E/" : "/U/0/" + components[2] + "/E/" + components[4] + "/"
        #endif
    }

    static func trainingSessionProgressPercent(completedBytes: Int64, totalBytes: Int64) -> Int {
        #if canImport(PolarBleSdkShared)
        return Int(PolarIosSharedBridge.shared.trainingSessionProgressPercent(completedBytes: completedBytes, totalBytes: totalBytes))
        #else
        return totalBytes > 0 ? max(0, min(Int((completedBytes * 100) / totalBytes), 100)) : 0
        #endif
    }

    static func trainingSessionReferenceDateMatches(date: String, fromDate: String?, toDate: String?) -> Bool? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.trainingSessionReferenceDateMatches(date: date, fromDate: fromDate, toDate: toDate)
        #else
        return nil
        #endif
    }

    static func trainingSessionReferences(entriesText: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.trainingSessionReferences(entriesText: entriesText)
        #else
        return ""
        #endif
    }

    static func trainingSessionDataType(fileName: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.trainingSessionDataType(fileName: fileName)
        #else
        return nil
        #endif
    }

    static func trainingSessionExerciseDataType(fileName: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.trainingSessionExerciseDataType(fileName: fileName)
        #else
        return nil
        #endif
    }

    static func trainingSessionExerciseDataTypeFileName(typeName: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.trainingSessionExerciseDataTypeFileName(typeName: typeName)
        #else
        return nil
        #endif
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

private extension Data {
    init(hexString: String) {
        var data = Data()
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2, limitedBy: hexString.endIndex) ?? hexString.endIndex
            if nextIndex <= hexString.endIndex,
               let byte = UInt8(hexString[index..<nextIndex], radix: 16) {
                data.append(byte)
            }
            index = nextIndex
        }
        self = data
    }

    func hexString() -> String {
        return map { String(format: "%02x", $0) }.joined()
    }
}
