// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarRuntimePlanner {
    static func commandQuery(id: String, query: String, parameters: [String] = []) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeCommandQuery(id: id, query: query, parametersCsv: parameters.joined(separator: ","))
        #endif
    }

    static func commandReset(id: String, sleep: Bool, factoryDefaults: Bool, otaFirmwareUpdate: Bool) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeCommandReset(id: id, sleep: sleep, factoryDefaults: factoryDefaults, otaFirmwareUpdate: otaFirmwareUpdate)
        #endif
    }

    static func commandSyncStart(id: String) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeCommandSyncStart(id: id)
        #endif
    }

    static func commandSyncStop(id: String) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeCommandSyncStop(id: id)
        #endif
    }

    static func diskTimeQuery(id: String, query: String) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeDiskTimeQuery(id: id, query: query)
        #endif
    }

    static func setLocalTimeV2(systemTimeHour: Int, localTimeHour: Int) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeSetLocalTimeV2(systemTimeHour: Int32(systemTimeHour), localTimeHour: Int32(localTimeHour))
        #endif
    }

    static func setLocalTimeH10(localTimeHour: Int) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeSetLocalTimeH10(localTimeHour: Int32(localTimeHour))
        #endif
    }

    static func restFacadeGet(id: String, path: String, payloadShape: String) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeRestFacadeGet(id: id, path: path, payloadShape: payloadShape)
        #endif
    }

    static func fileFacade(id: String, command: String, path: String, payloadHex: String = "") {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeFileFacade(id: id, command: command, path: path, payloadHex: payloadHex)
        #endif
    }

    static func fileRuntimeError(operation: String, path: String, error: Error) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeFileError(operation: operation, path: path, errorName: String(describing: type(of: error)))
        #endif
    }

    static func userDeviceSettings(id: String, kind: String, path: String, payloadFields: [String] = []) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeUserDeviceSettings(id: id, kind: kind, path: path, payloadFieldsCsv: payloadFields.joined(separator: ","))
        #endif
    }

    static func storedDataCleanup(kind: String, rootPath: String) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeStoredDataCleanup(kind: kind, rootPath: rootPath)
        #endif
    }

    static func offlineTriggerSet(currentTypes: [String], desiredTypes: [String], secretPresent: Bool) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeOfflineTrigger(operation: "setOfflineRecordingTrigger", currentTypesCsv: currentTypes.joined(separator: ","), desiredTypesCsv: desiredTypes.joined(separator: ","), secretPresent: secretPresent)
        #endif
    }

    static func offlineTriggerGet(currentTypes: [String]) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeOfflineTrigger(operation: "getOfflineRecordingTriggerSetup", currentTypesCsv: currentTypes.joined(separator: ","), desiredTypesCsv: "", secretPresent: false)
        #endif
    }

    static func firmwareWorkflow(id: String, statuses: [String] = [], firmwareFiles: [String] = []) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeFirmwareWorkflow(id: id, statusesCsv: statuses.joined(separator: ","), firmwareFilesCsv: firmwareFiles.joined(separator: ","))
        #endif
    }

    static func orderFirmwareFiles(_ fileNames: [String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeOrderFirmwareFiles(fileNamesCsv: fileNames.joined(separator: ",")).split(separator: ",").map(String.init)
        #else
        return fileNames
        #endif
    }

    static func backupRestore(path: String, payloadHex: String, writeResult: String = "success") {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeBackupRestore(path: path, payloadHex: payloadHex, writeResult: writeResult)
        #endif
    }

    static func psFtpWriteProgress(payloadSize: Int, platform: String = "ios") -> [Int] {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimePsFtpWriteProgress(payloadSize: Int32(payloadSize), platform: platform).split(separator: ",").compactMap { Int($0) }
        #else
        return []
        #endif
    }

    static func psFtpWriteAck(payloadSize: Int, writeAck: String = "success") {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimePsFtpWriteAck(payloadSize: Int32(payloadSize), writeAck: writeAck)
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

    static func streamSubscription(target: String, startConnected: Bool, checkConnection: Bool) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeStreamSubscription(target: target, startConnected: startConnected, checkConnection: checkConnection)
        #endif
    }

    static func streamConsumerCancellation(target: String) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeStreamConsumerCancellation(target: target)
        #endif
    }

    static func streamDisconnect(target: String, error: String) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeStreamDisconnect(target: target, error: error)
        #endif
    }

    static func streamDuplicateCompletion(target: String) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeStreamDuplicateCompletion(target: target)
        #endif
    }

    static func streamPostCompletionEmission(target: String, value: String) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeStreamPostCompletionEmission(target: target, value: value)
        #endif
    }
}
