// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarFirmwareBackupRuntimePlanner {
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

    private static func backupRestoreOperation(_ plannedOperation: String) -> (command: Protocol_PbPFtpOperation.Command, path: String)? {
        let parts = plannedOperation.split(separator: ":", maxSplits: 2).map(String.init)
        guard parts.count == 3, parts[0] == "PUT" else { return nil }
        return (.put, parts[1])
    }
}
