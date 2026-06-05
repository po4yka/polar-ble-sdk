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

    @discardableResult
    static func commandSyncStart(id: String) -> String {
        return PolarCommandRuntimePlanner.syncStart(id: id)
    }

    static func commandSyncStartNotifications(id: String) -> [Int]? {
        return PolarCommandRuntimePlanner.syncStartNotifications(id: id)
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
    static func fileFacade(id: String, command: String, path: String, payloadHex: String = "") -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeFileFacade(id: id, command: command, path: path, payloadHex: payloadHex)
        #else
        return "platform-owned"
        #endif
    }

    static func fileFacadeOperation(id: String, command: String, path: String, payloadHex: String = "") -> (command: Protocol_PbPFtpOperation.Command, path: String)? {
        return PolarFileFacadeRuntimePlanner.fileFacadeOperation(id: id, command: command, path: path, payloadHex: payloadHex)
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

    @discardableResult
    static func storedDataCleanup(kind: String, rootPath: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeStoredDataCleanup(kind: kind, rootPath: rootPath)
        #else
        return "platform-owned"
        #endif
    }

    static func storedDataEntryMatchesFilter(entry: String, includePrefixes: [String] = [], includeSuffixes: [String] = []) -> Bool? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.storedDataEntryMatchesFilter(
            entry: entry,
            includePrefixesCsv: includePrefixes.joined(separator: ","),
            includeSuffixesCsv: includeSuffixes.joined(separator: ",")
        )
        #else
        return nil
        #endif
    }

    static func shouldPruneStoredDataEmptyParents(dataType: String) -> Bool? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.shouldPruneStoredDataEmptyParents(dataType: dataType)
        #else
        return nil
        #endif
    }

    static func storedDataEmptyParentDirectories(filePath: String, rootPath: String = "/U/0", trailingSlash: Bool = true) -> [String]? {
        #if canImport(PolarBleSdkShared)
        let csv = PolarIosSharedBridge.shared.storedDataEmptyParentDirectories(filePath: filePath, rootPath: rootPath, trailingSlash: trailingSlash)
        return csv.isEmpty ? [] : csv.split(separator: ",").map(String.init)
        #else
        return nil
        #endif
    }

    @discardableResult
    static func offlineTriggerSet(currentTypes: [String], desiredTypes: [String], secretPresent: Bool) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeOfflineTrigger(operation: "setOfflineRecordingTrigger", currentTypesCsv: currentTypes.joined(separator: ","), desiredTypesCsv: desiredTypes.joined(separator: ","), secretPresent: secretPresent)
        #else
        return "platform-owned"
        #endif
    }

    @discardableResult
    static func offlineTriggerGet(currentTypes: [String]) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeOfflineTrigger(operation: "getOfflineRecordingTriggerSetup", currentTypesCsv: currentTypes.joined(separator: ","), desiredTypesCsv: "", secretPresent: false)
        #else
        return "platform-owned"
        #endif
    }

    @discardableResult
    static func firmwareWorkflow(id: String, statuses: [String] = [], firmwareFiles: [String] = []) -> String {
        return PolarFirmwareBackupRuntimePlanner.firmwareWorkflow(id: id, statuses: statuses, firmwareFiles: firmwareFiles)
    }

    static func orderFirmwareFiles(_ fileNames: [String]) -> [String] {
        return PolarFirmwareBackupRuntimePlanner.orderFirmwareFiles(fileNames)
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
