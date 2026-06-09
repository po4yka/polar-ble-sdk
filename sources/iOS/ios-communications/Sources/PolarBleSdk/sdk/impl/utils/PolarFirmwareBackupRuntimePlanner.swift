// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarFirmwareBackupRuntimePlanner {
    struct BackupRestoreWrite {
        let command: Protocol_PbPFtpOperation.Command
        let path: String
        let payloadHex: String
    }

    @discardableResult
    static func firmwareWorkflow(id: String, statuses: [String] = [], firmwareFiles: [String] = []) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeFirmwareWorkflow(id: id, statusesCsv: statuses.joined(separator: ","), firmwareFilesCsv: firmwareFiles.joined(separator: ","))
        #else
        return "platform-owned"
        #endif
    }

    static func firmwareWorkflowTerminalError(id: String, statuses: [String] = [], firmwareFiles: [String] = []) -> String? {
        #if canImport(PolarBleSdkShared)
        let terminalError = PolarIosSharedBridge.shared.planRuntimeFirmwareWorkflowTerminalError(id: id, statusesCsv: statuses.joined(separator: ","), firmwareFilesCsv: firmwareFiles.joined(separator: ","))
        return terminalError.isEmpty ? nil : terminalError
        #else
        return nil
        #endif
    }

    @discardableResult
    static func invalidFirmwarePackageWorkflow() -> String {
        return firmwareWorkflow(id: "empty-or-invalid-zip", statuses: ["fetchingFwUpdatePackage", "fwUpdateNotAvailable"])
    }

    @discardableResult
    static func firmwarePackageDownloadFailureWorkflow() -> String {
        return firmwareWorkflow(id: "download-failure", statuses: ["fetchingFwUpdatePackage", "fwUpdateFailed"])
    }

    @discardableResult
    static func firmwareCheckUpdateAvailableWorkflow() -> String {
        return firmwareWorkflow(id: "check-update-available", statuses: ["checkFwUpdateAvailable"])
    }

    @discardableResult
    static func firmwareCheckUpdateNotAvailableWorkflow() -> String {
        return firmwareWorkflow(id: "check-update-not-available", statuses: ["checkFwUpdateNotAvailable"])
    }

    @discardableResult
    static func firmwareRetryableServerFailureWorkflow() -> String {
        return firmwareWorkflow(id: "retryable-server-failure", statuses: ["fwUpdateFailed"])
    }

    static func firmwareRetryableServerFailureTerminalError() -> String? {
        return firmwareWorkflowTerminalError(id: "retryable-server-failure", statuses: ["fwUpdateFailed"])
    }

    @discardableResult
    static func firmwareClientRequestFailureWorkflow() -> String {
        return firmwareWorkflow(id: "client-request-failure", statuses: ["fwUpdateFailed"])
    }

    static func firmwareClientRequestFailureTerminalError() -> String? {
        return firmwareWorkflowTerminalError(id: "client-request-failure", statuses: ["fwUpdateFailed"])
    }

    @discardableResult
    static func firmwarePackageFetchCancellationWorkflow() -> String {
        return firmwareWorkflow(id: "cancel-after-package-fetch-cleans-up-before-ble-write", statuses: ["fetchingFwUpdatePackage", "fwUpdateCancelled"])
    }

    static func firmwarePackageFetchCancellationTerminalError() -> String? {
        return firmwareWorkflowTerminalError(id: "cancel-after-package-fetch-cleans-up-before-ble-write", statuses: ["fetchingFwUpdatePackage", "fwUpdateCancelled"])
    }

    @discardableResult
    static func firmwareSystemUpdateRebootSuccessWorkflow(fileNames: [String]) -> String {
        return firmwareWorkflow(id: "system-update-reboot-response-is-success", statuses: ["preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "finalizingFwUpdate", "fwUpdateCompletedSuccessfully"], firmwareFiles: fileNames.map { $0.trimmingCharacters(in: CharacterSet(charactersIn: "/")) })
    }

    @discardableResult
    static func firmwareBatteryTooLowTerminalWorkflow(fileNames: [String]) -> String {
        return firmwareWorkflow(id: "battery-too-low-response-is-terminal-failure", statuses: ["preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "fwUpdateFailed"], firmwareFiles: fileNames.map { $0.trimmingCharacters(in: CharacterSet(charactersIn: "/")) })
    }

    static func firmwareBatteryTooLowTerminalError(fileNames: [String]) -> String? {
        return firmwareWorkflowTerminalError(id: "battery-too-low-response-is-terminal-failure", statuses: ["preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "fwUpdateFailed"], firmwareFiles: fileNames.map { $0.trimmingCharacters(in: CharacterSet(charactersIn: "/")) })
    }

    static func orderFirmwareFiles(_ fileNames: [String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeOrderFirmwareFiles(fileNamesCsv: fileNames.joined(separator: ",")).split(separator: ",").map(String.init)
        #else
        return fileNames
        #endif
    }

    static func firmwarePayloadFileNames(_ fileNames: [String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        let csv = PolarIosSharedBridge.shared.planRuntimeFirmwarePayloadFileNames(fileNamesCsv: fileNames.joined(separator: ","))
        return csv.isEmpty ? [] : csv.split(separator: ",").map(String.init)
        #else
        return orderFirmwareFiles(fileNames.filter { firmwarePackageEntryIsPayload($0) })
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

    static func firmwareRetryDelaysMillis(maxRetries: Int) -> [Int64] {
        #if canImport(PolarBleSdkShared)
        let csv = PolarIosSharedBridge.shared.planRuntimeFirmwareRetryDelaysMillis(maxRetries: Int32(maxRetries))
        return csv.isEmpty ? [] : csv.split(separator: ",").compactMap { Int64($0) }
        #else
        return Array([1000, 2000].prefix(max(0, maxRetries))).map(Int64.init)
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

    static func firmwareFinalizationSteps(hasH10FileSystem: Bool, isDeviceSensor: Bool) -> [String] {
        #if canImport(PolarBleSdkShared)
        let csv = PolarIosSharedBridge.shared.firmwareFinalizationSteps(hasH10FileSystem: hasH10FileSystem, isDeviceSensor: isDeviceSensor)
        return csv.isEmpty ? [] : csv.split(separator: ",").map(String.init)
        #else
        var steps = ["wait-for-device-update"]
        if !hasH10FileSystem { steps.append("restore-backup") }
        steps.append("set-device-time")
        if isDeviceSensor {
            steps.append("stop-sync")
        } else {
            steps.append("restart-device")
            steps.append("wait-for-restart-reconnect")
        }
        return steps
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

    static func backupRestoreWrites(_ files: [(directory: String, fileName: String, payloadHex: String)]) -> [BackupRestoreWrite] {
        #if canImport(PolarBleSdkShared)
        let filesTsv = files.map { "\($0.directory)\t\($0.fileName)\t\($0.payloadHex)" }.joined(separator: "\n")
        return PolarIosSharedBridge.shared.planRuntimeBackupRestoreOperations(filesTsv: filesTsv)
            .split(separator: "\n")
            .compactMap { row in
                let parts = row.split(separator: "\t", maxSplits: 2).map(String.init)
                guard parts.count == 3, parts[0] == "PUT" else { return nil }
                return BackupRestoreWrite(command: .put, path: parts[1], payloadHex: parts[2])
            }
        #else
        return files.map { BackupRestoreWrite(command: .put, path: $0.directory + $0.fileName, payloadHex: $0.payloadHex) }
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
