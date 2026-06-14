//  Copyright © 2026 Polar. All rights reserved.

import Foundation

class PolarFileUtils {

    var listener: CBDeviceListenerImpl?
    var serviceClientUtils: PolarServiceClientUtils?
    required init(listener: CBDeviceListenerImpl, serviceClientUtils: PolarServiceClientUtils) {
        self.listener = listener
        self.serviceClientUtils = serviceClientUtils
    }

    func listFiles(identifier: String, folderPath: String = "/", condition: @escaping (_ p: String) -> Bool, recurseDeep: Bool = true) -> AsyncThrowingStream<String, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try self.serviceClientUtils?.sessionFtpClientReady(identifier)
                    guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    let path = PolarRuntimePlanner.normalizeFileListFolderPath(folderPath)
                    let entries = try await fetchRecursive(path, client: client, condition: condition, recurseDeep: recurseDeep)
                    for entry in entries { continuation.yield(entry.name) }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: PolarErrors.deviceError(description: "Error in listing files from \(folderPath) path."))
                }
            }
        }
    }

    func checkAutoSampleFile(identifier: String, filePath: String, until: Date) async throws -> Bool {
        let file = try await getFile(identifier: identifier, filePath: filePath)
        let calendar = Calendar.current
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyyMMdd"
        dateFormatter.timeZone = TimeZone(abbreviation: "UTC")
        let fileData = try Data_PbAutomaticSampleSessions(serializedBytes: file as Data)
        let proto = AutomaticSamples.fromProto(proto: fileData)
        let sampleDay = dateFormatter.string(from: proto.day!)
        let cutoffDate = dateFormatter.string(from: until)
        if let sharedDecision = PolarRuntimePlanner.storedDataDateIsOnOrBefore(day: sampleDay, cutoffDate: cutoffDate) {
            return sharedDecision
        }
        let result = calendar.compare(
            dateFromStringWOTime(dateFrom: sampleDay),
            to: dateFromStringWOTime(dateFrom: cutoffDate),
            toGranularity: .day)
        return result == .orderedSame || result == .orderedAscending
    }

    func deleteDataDirectory(identifier: String, directoryPath: String) async throws {
        do {
            let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
            guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            do {
                let isEmpty = try await checkIfDirectoryIsEmpty(directoryPath: directoryPath, client: client)
                if isEmpty { _ = try await removeSingleFile(identifier: identifier, filePath: directoryPath) }
            } catch {
                if case let BlePsFtpException.responseError(code) = error, code == 103 {
                    BleLogger.trace("Directory not found: \(directoryPath). Treating as already deleted.")
                    return
                }
                throw error
            }
        } catch {
            BleLogger.error("Error while getting session \(error)")
            throw PolarErrors.serviceNotFound
        }
    }

    func checkIfDirectoryIsEmpty(directoryPath: String, client: BlePsFtpClient) async throws -> Bool {
        let path = PolarRuntimePlanner.normalizeFileListFolderPath(directoryPath)
        let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: PolarFileFacadePlanId.listDirectorySuccess, command: "GET", path: path)
        let operation = plannedOperation ?? (.get, path)
        try ensureFileFacadeRuntimePlan(id: PolarFileFacadePlanId.listDirectorySuccess, command: "GET", path: path)
        let request = try PolarRuntimePlanner.fileOperationBytes(operation)
        do {
            let data = try await client.request(request)
            let directory = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
            return directory.entries.count == 0
        } catch {
            if case let BlePsFtpException.responseError(code) = error, code == 103 { return true }
            BleLogger.error("Failed to get data from directory \(directoryPath). Error: \(error.localizedDescription)")
            throw error
        }
    }

    func removeSingleFile(identifier: String, filePath: String) async throws -> NSData {
        let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
        guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: PolarFileFacadePlanId.deleteFileSuccess, command: "REMOVE", path: filePath)
        let operation = plannedOperation ?? (.remove, filePath)
        try ensureFileFacadeRuntimePlan(id: PolarFileFacadePlanId.deleteFileSuccess, command: "REMOVE", path: filePath)
        let request = try PolarRuntimePlanner.fileOperationBytes(operation)
        do {
            return try await client.request(request)
        } catch {
            PolarRuntimePlanner.fileRuntimeError(operation: "removeSingleFile", path: filePath, error: error)
            throw error
        }
    }

    func removeMultipleFiles(identifier: String, filePaths: [String]) async throws {
        let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
        guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw handleError(PolarErrors.serviceNotFound)
        }
        for filePath in filePaths {
            let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: PolarFileFacadePlanId.deleteFileSuccess, command: "REMOVE", path: filePath)
            let operation = plannedOperation ?? (.remove, filePath)
            try ensureFileFacadeRuntimePlan(id: PolarFileFacadePlanId.deleteFileSuccess, command: "REMOVE", path: filePath)
            let request = try PolarRuntimePlanner.fileOperationBytes(operation)
            do {
                _ = try await client.request(request)
            } catch {
                PolarRuntimePlanner.fileRuntimeError(operation: "removeSingleFile", path: filePath, error: error)
                if case let BlePsFtpException.responseError(code) = error, code == 103 {
                    BleLogger.trace("File not found: \(filePath). Treating as already deleted.")
                    continue
                }
                throw error
            }
        }
    }

    func getFile(identifier: String, filePath: String) async throws -> NSData {
        do {
            let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
            guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: PolarFileFacadePlanId.readFileSuccess, command: "GET", path: filePath)
            let operation = plannedOperation ?? (.get, filePath)
            try ensureFileFacadeRuntimePlan(id: PolarFileFacadePlanId.readFileSuccess, command: "GET", path: filePath)
            let request = try PolarRuntimePlanner.fileOperationBytes(operation)
            return try await client.request(request)
        } catch {
            PolarRuntimePlanner.fileRuntimeError(operation: "readFile", path: filePath, error: error)
            throw PolarErrors.deviceError(description: "Failed to list files from \(filePath) path. Error \(error)")
        }
    }

    private func fetchRecursive(_ path: String, client: BlePsFtpClient, condition: @escaping (_ p: String) -> Bool, recurseDeep: Bool) async throws -> [(name: String, size: UInt64)] {
        let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: PolarFileFacadePlanId.readFileSuccess, command: "GET", path: path)
        let operation = plannedOperation ?? (.get, path)
        try ensureFileFacadeRuntimePlan(id: PolarFileFacadePlanId.readFileSuccess, command: "GET", path: path)
        let request = try PolarRuntimePlanner.fileOperationBytes(operation)
        do {
            let data = try await client.request(request)
            let dir = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
            var results: [(name: String, size: UInt64)] = []
            for entry in dir.entries {
                if condition(entry.name) {
                    let fullPath = path + entry.name
                    if fullPath.hasSuffix("/") && recurseDeep {
                        let subResults = try await fetchRecursive(fullPath, client: client, condition: condition, recurseDeep: recurseDeep)
                        results.append(contentsOf: subResults)
                    } else {
                        results.append((name: fullPath, size: entry.size))
                    }
                }
            }
            return results
        } catch {
            PolarRuntimePlanner.fileRuntimeError(operation: "listFiles", path: path, error: error)
            throw handleError(error)
        }
    }

    private func ensureFileFacadeRuntimePlan(id: String, command: String, path: String, payloadHex: String = "") throws {
        let terminal = PolarRuntimePlanner.fileFacade(id: id, command: command, path: path, payloadHex: payloadHex)
        guard terminal == "success" || terminal == "platform-owned" else {
            throw PolarErrors.polarBleSdkInternalException(description: "File facade planning failed: \(terminal)")
        }
    }

    private func handleError(_ error: Error) -> Error {
        let nsError = error as NSError
        if let mapped = Protocol_PbPFtpError(rawValue: nsError.code) {
            return NSError(domain: nsError.domain, code: nsError.code, userInfo: [NSLocalizedDescriptionKey: "\(mapped) (\(nsError.localizedDescription))"])
        }
        return error
    }

    private func dateFromStringWOTime(dateFrom: String) -> Date {
        let year = Int(String(dateFrom[dateFrom.index(dateFrom.startIndex, offsetBy: 0)..<dateFrom.index(dateFrom.endIndex, offsetBy: -4)]))
        let month = Int(String(dateFrom[dateFrom.index(dateFrom.startIndex, offsetBy: 4)..<dateFrom.index(dateFrom.endIndex, offsetBy: -2)]))
        let day = Int(String(dateFrom[dateFrom.index(dateFrom.startIndex, offsetBy: 6)..<dateFrom.index(dateFrom.endIndex, offsetBy: 0)]))
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(abbreviation: "UTC")!
        var dc = DateComponents()
        dc.year = year; dc.month = month; dc.day = day
        dc.hour = 0; dc.minute = 0; dc.second = 0
        return calendar.date(from: dc)!
    }

    // MARK: - BLE Low Level APIs

    func writeFile(identifier: String, filePath: String, fileData: Data) async throws {
        let session = try serviceClientUtils?.sessionFtpClientReady(identifier)
        guard let client = session?.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        let payloadHex = fileData.map { String(format: "%02x", $0) }.joined()
        let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: PolarFileFacadePlanId.writeFileSuccess, command: "PUT", path: filePath, payloadHex: payloadHex)
        let operation = plannedOperation ?? (.put, filePath)
        try ensureFileFacadeRuntimePlan(id: PolarFileFacadePlanId.writeFileSuccess, command: "PUT", path: filePath, payloadHex: payloadHex)
        try PolarRuntimePlanner.ensurePsFtpWriteRuntimePlan(payloadSize: fileData.count)
        let proto = try PolarRuntimePlanner.fileOperationBytes(operation)
        let inputStream = InputStream(data: fileData)
        do {
            for try await _ in client.write(proto as NSData, data: inputStream) {}
        } catch {
            PolarRuntimePlanner.fileRuntimeError(operation: "writeFile", path: filePath, error: error)
            throw error
        }
    }

    func readFile(identifier: String, filePath: String) async throws -> Data {
        let data = try await getFile(identifier: identifier, filePath: filePath)
        return data as Data
    }

    func listFiles(identifier: String, directoryPath: String, recurseDeep: Bool = false) async throws -> [String] {
        let condition = { (entry: String) -> Bool in entry.contains(".") || entry == "" }
        var fileList = [String]()
        for try await file in listFiles(identifier: identifier, folderPath: directoryPath, condition: condition, recurseDeep: recurseDeep) {
            fileList.append(file)
        }
        return fileList
    }

    func deleteFile(identifier: String, filePath: String) async throws {
        _ = try await removeSingleFile(identifier: identifier, filePath: filePath)
    }
}
