//  Copyright © 2024 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

private let TAG = "PolarDeviceBackup"
private let ARABICA_SYS_FOLDER = "/SYS/"
private let WILD_CARD_CHARACTER = "*"

public class PolarBackupManager {

    public struct BackupFileData {
        let data: Data
        let directory: String
        let fileName: String
    }

    private struct DeviceFolderEntry {
        let name: String
        let size: Int64
        let folderPath: String
    }

    let client: BlePsFtpClient

    public init(client: BlePsFtpClient) {
        self.client = client
    }

    public func backupDevice() async throws -> [BackupFileData] {
        BleLogger.trace("backupDevice() called")
        let rootOperation = PolarRuntimePlanner.fileFacadeOperation(id: "backup-read-root-directory", command: "GET", path: ARABICA_SYS_FOLDER)
        let operation = Protocol_PbPFtpOperation.with {
            $0.command = rootOperation?.command ?? .get
            $0.path = rootOperation?.path ?? ARABICA_SYS_FOLDER
        }
        PolarRuntimePlanner.fileFacade(id: "backup-read-root-directory", command: "GET", path: ARABICA_SYS_FOLDER)
        do {
            let content = try await client.request(try operation.serializedBytes())
            let parentDirEntries = try Protocol_PbPFtpDirectory(serializedBytes: content as Data)
            let entries = parentDirEntries.entries.map { ARABICA_SYS_FOLDER + $0.name }
            var backupDirectories: [String] = []
            if let backupEntry = entries.first(where: { $0.hasSuffix("BACKUP.TXT") }) {
                BleLogger.trace("Found BACKUP.TXT: \(backupEntry)")
                let backupData = try await loadFile(path: backupEntry)
                let data = Data(backupData)
                let stream = InputStream(data: data)
                var accumulatedString = ""
                stream.open()
                defer { stream.close() }
                let bufferSize = 1024
                let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
                defer { buffer.deallocate() }
                while stream.hasBytesAvailable {
                    let bytesRead = stream.read(buffer, maxLength: bufferSize)
                    if bytesRead > 0, let chunk = String(bytesNoCopy: buffer, length: bytesRead, encoding: .utf8, freeWhenDone: false) {
                        accumulatedString += chunk
                    }
                }
                backupDirectories.append(contentsOf: PolarRuntimePlanner.parseBackupTextForIos(accumulatedString))
            } else {
                BleLogger.error("No BACKUP.TXT found, using default backup directories")
            }
            backupDirectories = PolarRuntimePlanner.backupRootPaths(backupDirectories)
            BleLogger.trace("Backup directories found: \(backupDirectories)")
            var result = [BackupFileData]()
            for dir in backupDirectories {
                BleLogger.trace("Processing backup directory: \(dir)")
                let files = try await backupDirectory(backupDirectory: dir)
                BleLogger.trace("Backup directory ready: \(dir)")
                result.append(contentsOf: files)
            }
            return result
        } catch {
            BleLogger.error("Failed to get backup content, error: \(error)")
            return []
        }
    }

    public func restoreBackup(backupFiles: [BackupFileData]) async throws {
        BleLogger.trace("Starting backup restoration for \(backupFiles.count) files")
        var failedFiles: [(fileName: String, error: Error)] = []
        for backupFileData in backupFiles {
            BleLogger.trace("Restoring: \(backupFileData.fileName)")
            do {
                var operation = Protocol_PbPFtpOperation()
                let restorePath = backupFileData.directory + backupFileData.fileName
                let payloadHex = backupFileData.data.map { String(format: "%02x", $0) }.joined()
                let plannedOperation = PolarRuntimePlanner.backupRestoreOperation(path: restorePath, payloadHex: payloadHex)
                operation.command = plannedOperation?.command ?? .put
                operation.path = plannedOperation?.path ?? restorePath
                PolarRuntimePlanner.backupRestore(path: restorePath, payloadHex: payloadHex)
                let header = try operation.serializedData() as NSData
                let dataStream = InputStream(data: backupFileData.data)
                _ = PolarRuntimePlanner.psFtpWriteProgress(payloadSize: backupFileData.data.count)
                PolarRuntimePlanner.psFtpWriteAck(payloadSize: backupFileData.data.count)
                for try await bytes in client.write(header, data: dataStream) {
                    BleLogger.trace("Writing firmware update file: \(backupFileData.directory)\(backupFileData.fileName), bytes to write: \(bytes)")
                }
                BleLogger.trace("Restored: \(backupFileData.directory)\(backupFileData.fileName)")
            } catch {
                BleLogger.error("Failed to restore \(backupFileData.directory)\(backupFileData.fileName): \(error)")
                failedFiles.append((fileName: backupFileData.directory + backupFileData.fileName, error: error))
            }
        }
        if !failedFiles.isEmpty {
            let summary = failedFiles.map {"\($0.fileName): \($0.error)" }.joined(separator: ", ")
            BleLogger.error("Backup restoration completed with \(failedFiles.count) failure(s): \(summary)")
            throw NSError(
                domain: "PolarBackupManager",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Restore failed for \(failedFiles.count) file(s): \(summary)"]
            )
        }
        BleLogger.trace("Backup restoration complete")
    }

    private func loadFile(path: String) async throws -> [UInt8] {
        var operation = Protocol_PbPFtpOperation()
        let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: "backup-read-file", command: "GET", path: path)
        operation.command = plannedOperation?.command ?? .get
        operation.path = plannedOperation?.path ?? path
        PolarRuntimePlanner.fileFacade(id: "backup-read-file", command: "GET", path: path)
        let data = try await client.request(try operation.serializedBytes())
        return [UInt8](data)
    }

    private func backupDirectory(backupDirectory: String) async throws -> [BackupFileData] {
        let directoryPaths = backupDirectory.components(separatedBy: .newlines)
        var result = [BackupFileData]()
        for directoryPath in directoryPaths {
            guard !directoryPath.isEmpty else { continue }
            let path = PolarFirmwareBackupRuntimePlanner.backupTraversalRootPath(directoryPath)
            if path.contains(WILD_CARD_CHARACTER) {
                let rootPath = path.components(separatedBy: WILD_CARD_CHARACTER).first ?? ""
                let entries = try await fetchRecursively(path: rootPath)
                let subdirs = entries.filter { $0.folderPath.isFolder }.map { rootPath + $0.name }
                for subdir in subdirs {
                    let files = try await backUpDirectories([subdir])
                    result.append(contentsOf: files)
                }
            } else if path.isFolder {
                let subDirs = try await loadSubDirectories(path: path)
                let files = try await backUpDirectories(subDirs)
                result.append(contentsOf: files)
            } else {
                do {
                    let data = try await loadFile(path: path)
                    let filePath = PolarRuntimePlanner.backupFilePath(path)
                    result.append(BackupFileData(data: Data(data), directory: filePath.directory, fileName: filePath.fileName))
                } catch {
                    BleLogger.error("Error loading file: \(path), error: \(error)")
                }
            }
        }
        return result
    }

    private func fetchRecursively(path: String) async throws -> [DeviceFolderEntry] {
        var operation = Protocol_PbPFtpOperation()
        let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: "backup-read-directory", command: "GET", path: path)
        operation.command = plannedOperation?.command ?? .get
        operation.path = plannedOperation?.path ?? path
        PolarRuntimePlanner.fileFacade(id: "backup-read-directory", command: "GET", path: path)
        let request = try operation.serializedData()
        var entries = [DeviceFolderEntry]()
        do {
            let data = try await client.request(request)
            let dir = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
            for entry in dir.entries {
                entries.append(DeviceFolderEntry(name: (entry.name as NSString).lastPathComponent, size: Int64(entry.size), folderPath: entry.name))
                if entry.name.hasSuffix("/") {
                    let subEntries = try await fetchRecursively(path: path + entry.name)
                    entries.append(contentsOf: subEntries)
                }
            }
        } catch {
            BleLogger.error("fetchRecursively() error: \(error)")
        }
        return entries
    }

    private func loadSubDirectories(path: String) async throws -> [String] {
        return try await fetchRecursively(path: path).map { path + $0.name }
    }

    private func backUpDirectories(_ folders: [String]) async throws -> [BackupFileData] {
        var result = [BackupFileData]()
        for folder in folders {
            let files = try await backupDirectory(backupDirectory: folder)
            result.append(contentsOf: files)
        }
        return result
    }
}

extension String {
    var isFolder: Bool { return hasSuffix("/") }
}
