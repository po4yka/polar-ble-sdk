// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarRuntimePlanner {
    @discardableResult
    static func commandQuery(id: String, query: String, parameters: [String] = []) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeCommandQuery(id: id, query: query, parametersCsv: parameters.joined(separator: ","))
        #else
        return "platform-owned"
        #endif
    }

    static func commandQueryValue(id: String, query: String, parameters: [String] = []) -> Int? {
        #if canImport(PolarBleSdkShared)
        return queryRawValues(PolarIosSharedBridge.shared.planRuntimeCommandQueryCommands(id: id, query: query, parametersCsv: parameters.joined(separator: ","))).first
        #else
        return nil
        #endif
    }

    @discardableResult
    static func commandReset(id: String, sleep: Bool, factoryDefaults: Bool, otaFirmwareUpdate: Bool) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeCommandReset(id: id, sleep: sleep, factoryDefaults: factoryDefaults, otaFirmwareUpdate: otaFirmwareUpdate)
        #else
        return "platform-owned"
        #endif
    }

    static func commandResetNotification(id: String, sleep: Bool, factoryDefaults: Bool, otaFirmwareUpdate: Bool) -> Int? {
        #if canImport(PolarBleSdkShared)
        return notificationRawValues(PolarIosSharedBridge.shared.planRuntimeCommandResetNotifications(id: id, sleep: sleep, factoryDefaults: factoryDefaults, otaFirmwareUpdate: otaFirmwareUpdate)).first
        #else
        return nil
        #endif
    }

    @discardableResult
    static func commandSyncStart(id: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeCommandSyncStart(id: id)
        #else
        return "platform-owned"
        #endif
    }

    static func commandSyncStartNotifications(id: String) -> [Int]? {
        #if canImport(PolarBleSdkShared)
        return notificationRawValues(PolarIosSharedBridge.shared.planRuntimeCommandSyncStartNotifications(id: id))
        #else
        return nil
        #endif
    }

    @discardableResult
    static func commandSyncStop(id: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeCommandSyncStop(id: id)
        #else
        return "platform-owned"
        #endif
    }

    static func commandSyncStopNotifications(id: String) -> [Int]? {
        #if canImport(PolarBleSdkShared)
        return notificationRawValues(PolarIosSharedBridge.shared.planRuntimeCommandSyncStopNotifications(id: id))
        #else
        return nil
        #endif
    }

    @discardableResult
    static func diskTimeQuery(id: String, query: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeDiskTimeQuery(id: id, query: query)
        #else
        return "platform-owned"
        #endif
    }

    static func diskTimeQueryValue(id: String, query: String) -> Int? {
        #if canImport(PolarBleSdkShared)
        return queryRawValues(PolarIosSharedBridge.shared.planRuntimeDiskTimeQueryCommands(id: id, query: query)).first
        #else
        return nil
        #endif
    }

    @discardableResult
    static func setLocalTimeV2(systemTimeHour: Int, localTimeHour: Int) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeSetLocalTimeV2(systemTimeHour: Int32(systemTimeHour), localTimeHour: Int32(localTimeHour))
        #else
        return "platform-owned"
        #endif
    }

    static func setLocalTimeV2QueryValues(systemTimeHour: Int, localTimeHour: Int) -> [Int]? {
        #if canImport(PolarBleSdkShared)
        return queryRawValues(PolarIosSharedBridge.shared.planRuntimeSetLocalTimeV2Commands(systemTimeHour: Int32(systemTimeHour), localTimeHour: Int32(localTimeHour)))
        #else
        return nil
        #endif
    }

    @discardableResult
    static func setLocalTimeH10(localTimeHour: Int) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeSetLocalTimeH10(localTimeHour: Int32(localTimeHour))
        #else
        return "platform-owned"
        #endif
    }

    static func setLocalTimeH10QueryValues(localTimeHour: Int) -> [Int]? {
        #if canImport(PolarBleSdkShared)
        return queryRawValues(PolarIosSharedBridge.shared.planRuntimeSetLocalTimeH10Commands(localTimeHour: Int32(localTimeHour)))
        #else
        return nil
        #endif
    }

    private static func notificationRawValues(_ csv: String) -> [Int] {
        return csv.split(separator: ",").compactMap { plannedName in
            switch plannedName.split(separator: ":").first.map(String.init) {
            case "INITIALIZE_SESSION": return Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue
            case "START_SYNC": return Protocol_PbPFtpHostToDevNotification.startSync.rawValue
            case "STOP_SYNC": return Protocol_PbPFtpHostToDevNotification.stopSync.rawValue
            case "TERMINATE_SESSION": return Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue
            case "RESET": return Protocol_PbPFtpHostToDevNotification.reset.rawValue
            default: return nil
            }
        }
    }

    private static func queryRawValues(_ csv: String) -> [Int] {
        return csv.split(separator: ",").compactMap { plannedName in
            switch String(plannedName) {
            case "GET_DISK_SPACE": return Protocol_PbPFtpQuery.getDiskSpace.rawValue
            case "GET_LOCAL_TIME": return Protocol_PbPFtpQuery.getLocalTime.rawValue
            case "REQUEST_START_RECORDING": return Protocol_PbPFtpQuery.requestStartRecording.rawValue
            case "REQUEST_STOP_RECORDING": return Protocol_PbPFtpQuery.requestStopRecording.rawValue
            case "REQUEST_RECORDING_STATUS": return Protocol_PbPFtpQuery.requestRecordingStatus.rawValue
            case "REQUEST_SYNCHRONIZATION": return Protocol_PbPFtpQuery.requestSynchronization.rawValue
            case "SET_LOCAL_TIME": return Protocol_PbPFtpQuery.setLocalTime.rawValue
            case "SET_SYSTEM_TIME": return Protocol_PbPFtpQuery.setSystemTime.rawValue
            default: return nil
            }
        }
    }

    private static func fileOperation(_ csv: String) -> [(command: Protocol_PbPFtpOperation.Command, path: String)] {
        return csv.split(separator: ",").compactMap { plannedOperation in
            let parts = plannedOperation.split(separator: ":", maxSplits: 1).map(String.init)
            guard parts.count == 2 else { return nil }
            switch parts[0] {
            case "GET": return (.get, parts[1])
            case "PUT": return (.put, parts[1])
            case "REMOVE": return (.remove, parts[1])
            default: return nil
            }
        }
    }

    private static func backupRestoreOperation(_ plannedOperation: String) -> (command: Protocol_PbPFtpOperation.Command, path: String)? {
        let parts = plannedOperation.split(separator: ":", maxSplits: 2).map(String.init)
        guard parts.count == 3, parts[0] == "PUT" else { return nil }
        return (.put, parts[1])
    }

    @discardableResult
    static func restFacadeGet(id: String, path: String, payloadShape: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeRestFacadeGet(id: id, path: path, payloadShape: payloadShape)
        #else
        return "platform-owned"
        #endif
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
        #if canImport(PolarBleSdkShared)
        return fileOperation(PolarIosSharedBridge.shared.planRuntimeFileFacadeOperation(id: id, command: command, path: path, payloadHex: payloadHex)).first
        #else
        return nil
        #endif
    }

    @discardableResult
    static func fileRuntimeError(operation: String, path: String, error: Error) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeFileError(operation: operation, path: path, errorName: String(describing: type(of: error)))
        #else
        return "platform-owned"
        #endif
    }

    @discardableResult
    static func userDeviceSettings(id: String, kind: String, path: String, payloadFields: [String] = []) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeUserDeviceSettings(id: id, kind: kind, path: path, payloadFieldsCsv: payloadFields.joined(separator: ","))
        #else
        return "platform-owned"
        #endif
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
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeFirmwareWorkflow(id: id, statusesCsv: statuses.joined(separator: ","), firmwareFilesCsv: firmwareFiles.joined(separator: ","))
        #else
        return "platform-owned"
        #endif
    }

    static func orderFirmwareFiles(_ fileNames: [String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeOrderFirmwareFiles(fileNamesCsv: fileNames.joined(separator: ",")).split(separator: ",").map(String.init)
        #else
        return fileNames
        #endif
    }

    @discardableResult
    static func backupRestore(path: String, payloadHex: String, writeResult: String = "success") -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeBackupRestore(path: path, payloadHex: payloadHex, writeResult: writeResult)
        #else
        return "platform-owned"
        #endif
    }

    static func backupRestoreOperation(path: String, payloadHex: String, writeResult: String = "success") -> (command: Protocol_PbPFtpOperation.Command, path: String)? {
        #if canImport(PolarBleSdkShared)
        return backupRestoreOperation(PolarIosSharedBridge.shared.planRuntimeBackupRestoreOperation(path: path, payloadHex: payloadHex, writeResult: writeResult))
        #else
        return nil
        #endif
    }

    static func psFtpWriteProgress(payloadSize: Int, platform: String = "ios") -> [Int] {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimePsFtpWriteProgress(payloadSize: Int32(payloadSize), platform: platform).split(separator: ",").compactMap { Int($0) }
        #else
        return []
        #endif
    }

    @discardableResult
    static func psFtpWriteAck(payloadSize: Int, writeAck: String = "success") -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimePsFtpWriteAck(payloadSize: Int32(payloadSize), writeAck: writeAck)
        #else
        return "platform-owned"
        #endif
    }

    static func d2hNotificationTypeName(notificationId: Int) -> String? {
        #if canImport(PolarBleSdkShared)
        let value = PolarIosSharedBridge.shared.d2hNotificationType(notificationId: Int32(notificationId))
        return value.isEmpty ? nil : value
        #else
        return nil
        #endif
    }

    static func d2hParsedProtoName(notificationType: String, parametersHex: String) -> String? {
        #if canImport(PolarBleSdkShared)
        let value = PolarIosSharedBridge.shared.d2hParsedProtoName(notificationType: notificationType, parametersHex: parametersHex)
        return value.isEmpty ? nil : value
        #else
        return nil
        #endif
    }

    static func d2hNotificationPlan(notificationId: Int, parametersHex: String) -> (notificationType: String, parsedProtoName: String?)? {
        #if canImport(PolarBleSdkShared)
        let value = PolarIosSharedBridge.shared.d2hNotificationPlan(notificationId: Int32(notificationId), parametersHex: parametersHex)
        if value.isEmpty { return nil }
        let fields = value.split(separator: ",", omittingEmptySubsequences: false).map(String.init)
        return (notificationType: fields[0], parsedProtoName: fields.count > 1 && !fields[1].isEmpty ? fields[1] : nil)
        #else
        return nil
        #endif
    }

    @discardableResult
    static func streamSubscription(target: String, startConnected: Bool, checkConnection: Bool) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeStreamSubscription(target: target, startConnected: startConnected, checkConnection: checkConnection)
        #else
        return "platform-owned"
        #endif
    }

    @discardableResult
    static func streamConsumerCancellation(target: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeStreamConsumerCancellation(target: target)
        #else
        return "platform-owned"
        #endif
    }

    @discardableResult
    static func streamDisconnect(target: String, error: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeStreamDisconnect(target: target, error: error)
        #else
        return "platform-owned"
        #endif
    }

    @discardableResult
    static func streamDuplicateCompletion(target: String) -> Int {
        #if canImport(PolarBleSdkShared)
        return Int(PolarIosSharedBridge.shared.planRuntimeStreamDuplicateCompletion(target: target))
        #else
        return 0
        #endif
    }

    @discardableResult
    static func streamPostCompletionEmission(target: String, value: String) -> Int {
        #if canImport(PolarBleSdkShared)
        return Int(PolarIosSharedBridge.shared.planRuntimeStreamPostCompletionEmission(target: target, value: value))
        #else
        return 0
        #endif
    }
}
