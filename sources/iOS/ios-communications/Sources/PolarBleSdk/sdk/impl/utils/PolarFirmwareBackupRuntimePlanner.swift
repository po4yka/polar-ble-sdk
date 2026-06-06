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

    static func firmwareWritePaths(_ fileNames: [String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        let csv = PolarIosSharedBridge.shared.planRuntimeFirmwareWritePaths(fileNamesCsv: fileNames.joined(separator: ","))
        return csv.isEmpty ? [] : csv.split(separator: ",").map(String.init)
        #else
        return fileNames.map { "/\($0)" }
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

    static func firmwareWriteTerminal(errorCode: Int, fileName: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.firmwareWriteTerminal(errorCode: Int32(errorCode), fileName: fileName)
        #else
        if errorCode == 1 && firmwareFileTriggersRebootWait(fileName) { return "success-rebooting" }
        if errorCode == 209 { return "battery-too-low" }
        return "propagate-error"
        #endif
    }

    static func firmwareWriteProgressPercent(bytesWritten: Int, payloadSize: Int) -> Int {
        #if canImport(PolarBleSdkShared)
        return Int(PolarIosSharedBridge.shared.firmwareWriteProgressPercent(bytesWritten: Int32(bytesWritten), payloadSize: Int32(payloadSize)))
        #else
        return payloadSize <= 0 ? 0 : bytesWritten * 100 / payloadSize
        #endif
    }

    static func shouldEmitFirmwareWriteProgress(lastBytesWritten: Int, bytesWritten: Int, payloadSize: Int, minPercentageIncrement: Int, timeSinceLastEmitMs: Int? = nil, maxEmitIntervalMs: Int = 5_000) -> Bool {
        #if canImport(PolarBleSdkShared)
        if let timeSinceLastEmitMs {
            return PolarIosSharedBridge.shared.shouldEmitFirmwareWriteProgressWithTime(
                lastBytesWritten: Int32(lastBytesWritten),
                bytesWritten: Int32(bytesWritten),
                payloadSize: Int32(payloadSize),
                minPercentageIncrement: Int32(minPercentageIncrement),
                timeSinceLastEmitMs: Int64(timeSinceLastEmitMs),
                maxEmitIntervalMs: Int64(maxEmitIntervalMs)
            )
        }
        return PolarIosSharedBridge.shared.shouldEmitFirmwareWriteProgress(
            lastBytesWritten: Int32(lastBytesWritten),
            bytesWritten: Int32(bytesWritten),
            payloadSize: Int32(payloadSize),
            minPercentageIncrement: Int32(minPercentageIncrement)
        )
        #else
        let delta = bytesWritten - lastBytesWritten
        let deltaPercentage = firmwareWriteProgressPercent(bytesWritten: delta, payloadSize: payloadSize)
        let timeGateReached = timeSinceLastEmitMs.map { $0 >= maxEmitIntervalMs } ?? false
        return lastBytesWritten == 0 || bytesWritten >= payloadSize || deltaPercentage >= minPercentageIncrement || timeGateReached
        #endif
    }

    static func firmwareDeviceInfoPath() -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.firmwareDeviceInfoPath()
        #else
        return "/DEVICE.BPB"
        #endif
    }

    static func firmwareFilePriority(_ fileName: String) -> Int {
        #if canImport(PolarBleSdkShared)
        return Int(PolarIosSharedBridge.shared.firmwareFilePriority(fileName: fileName))
        #else
        return fileName.contains("SYSUPDAT.IMG") ? 1 : 0
        #endif
    }

    static func isFirmwareVersionHigher(currentVersion: String, availableVersion: String) -> Bool {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.isFirmwareVersionHigher(currentVersion: currentVersion, availableVersion: availableVersion)
        #else
        let current = currentVersion.split(separator: ".").map { Int($0)! }
        let available = availableVersion.split(separator: ".").map { Int($0)! }
        for index in 0..<current.count {
            if available.count > index {
                if current[index] < available[index] { return true }
                if current[index] > available[index] { return false }
            }
        }
        return available.count > current.count
        #endif
    }

    static func firmwareDeviceVersion(major: Int, minor: Int, patch: Int) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.firmwareDeviceVersion(major: Int32(major), minor: Int32(minor), patch: Int32(patch))
        #else
        return "\(major).\(minor).\(patch)"
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

    static func parseBackupTextForIos(_ backupText: String) -> [String] {
        #if canImport(PolarBleSdkShared)
        let csv = PolarIosSharedBridge.shared.parseBackupTextForIosCsv(backupText: backupText)
        return csv.isEmpty ? [] : csv.split(separator: ",", omittingEmptySubsequences: false).map(String.init)
        #else
        return backupText.split(separator: "\n", omittingEmptySubsequences: false).dropLast().map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
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
