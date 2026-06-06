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

    static func firmwarePackageEntryIsPayload(_ fileName: String) -> Bool {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.firmwarePackageEntryIsPayload(fileName: fileName)
        #else
        return fileName != "readme.txt"
        #endif
    }

    static func firmwareFileTriggersRebootWait(_ fileName: String) -> Bool {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.firmwareFileTriggersRebootWait(fileName: fileName)
        #else
        return fileName.contains("SYSUPDAT.IMG")
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

    static func defaultBackupPaths() -> [String] {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.defaultBackupPathsCsv().split(separator: ",").map(String.init)
        #else
        return ["/U/*/S/PHYSDATA.BPB", "/U/*/S/UDEVSET.BPB", "/U/*/S/PREFS.BPB", "/U/*/USERID.BPB"]
        #endif
    }

    static func backupRootPaths(_ entries: [String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.backupRootPathsCsv(entriesCsv: entries.joined(separator: ",")).split(separator: ",").map(String.init)
        #else
        var paths = entries.filter { !$0.isEmpty }
        for defaultPath in defaultBackupPaths() {
            if !paths.contains(where: { $0.replacingOccurrences(of: "/U/*/", with: "/U/0/") == defaultPath.replacingOccurrences(of: "/U/*/", with: "/U/0/") }) {
                paths.append(defaultPath)
            }
        }
        return paths
        #endif
    }

    static func backupTraversalRootPath(_ path: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.backupTraversalRootPath(path: path)
        #else
        return path.replacingOccurrences(of: "/U/*/", with: "/U/0/")
        #endif
    }

    static func backupFilePath(_ path: String) -> (directory: String, fileName: String) {
        #if canImport(PolarBleSdkShared)
        let parts = PolarIosSharedBridge.shared.backupFilePathParts(path: path).split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 2 else {
            return fallbackBackupFilePath(path)
        }
        return (parts[0], parts[1])
        #else
        return fallbackBackupFilePath(path)
        #endif
    }

    private static func backupRestoreOperation(_ plannedOperation: String) -> (command: Protocol_PbPFtpOperation.Command, path: String)? {
        let parts = plannedOperation.split(separator: ":", maxSplits: 2).map(String.init)
        guard parts.count == 3, parts[0] == "PUT" else { return nil }
        return (.put, parts[1])
    }

    private static func fallbackBackupFilePath(_ path: String) -> (directory: String, fileName: String) {
        let fileName = (path as NSString).lastPathComponent
        let directory = (path as NSString).deletingLastPathComponent + "/"
        return (directory, fileName)
    }
}
