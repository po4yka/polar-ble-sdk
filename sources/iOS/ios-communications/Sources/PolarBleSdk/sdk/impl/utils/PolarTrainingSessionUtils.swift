//  Copyright © 2025 Polar. All rights reserved.

import Foundation
import zlib
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"

internal class PolarTrainingSessionUtils {
    
    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMdd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()
    
    private static let dateTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyyMMddHHmmss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()

    static func trainingSessionSummaryReadOperation(path: String) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        return trainingSessionFileOperation(id: "training-session-read-summary", command: "GET", path: path)
    }

    static func trainingSessionExerciseFileReadOperation(path: String) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        return trainingSessionFileOperation(id: "training-session-read-exercise-file", command: "GET", path: path)
    }

    static func trainingSessionPayloadFetchOrder(reference: PolarTrainingSessionReference) -> [String] {
        #if canImport(PolarBleSdkShared)
        let sharedReference = trainingSessionSharedReferenceText(reference: reference)
        let sharedOrder = PolarRuntimePlanner.trainingSessionPayloadFetchOrder(referenceText: sharedReference)
        if !sharedOrder.isEmpty {
            return sharedOrder.split(separator: "\n").map(String.init)
        }
        #endif
        return fallbackTrainingSessionPayloadFetchOrder(reference: reference)
    }

    static func trainingSessionPayloadParserCase(fileName: String) -> (parser: String, encoding: String)? {
        #if canImport(PolarBleSdkShared)
        if let shared = PolarRuntimePlanner.trainingSessionPayloadParserCase(fileName: fileName) {
            let fields = shared.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
            if fields.count == 2 {
                return (parser: fields[0], encoding: fields[1])
            }
        }
        #endif
        return fallbackTrainingSessionPayloadParserCase(fileName: fileName)
    }

    static func trainingSessionPayloadEncoding(fileName: String) -> String? {
        return trainingSessionPayloadParserCase(fileName: fileName)?.encoding
    }

    static func trainingSessionExerciseDataTypeFileName(dataType: PolarExerciseDataTypes) -> String {
        return dataType.deviceFileName
    }

    static func trainingSessionDirectoryReadOperation(path: String) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        return trainingSessionFileOperation(id: "training-session-read-directory", command: "GET", path: path)
    }

    static func trainingSessionDeleteParentReadOperation(reference: PolarTrainingSessionReference) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        return trainingSessionFileOperation(id: "training-session-delete-read-parent", command: "GET", path: trainingSessionDeleteParentPath(referencePath: reference.path))
    }

    static func trainingSessionDeleteRemoveOperation(reference: PolarTrainingSessionReference, parentEntryCount: Int) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        let removePath = trainingSessionDeleteRemovePath(referencePath: reference.path, parentEntryCount: parentEntryCount)
        return trainingSessionFileOperation(id: "training-session-delete-remove", command: "REMOVE", path: removePath)
    }

    private static func trainingSessionDeleteParentPath(referencePath: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarRuntimePlanner.trainingSessionDeleteParentPath(referencePath: referencePath)
        #else
        let components = referencePath.split(separator: "/")
        return "/U/0/" + components[2] + "/E/"
        #endif
    }

    private static func trainingSessionDeleteRemovePath(referencePath: String, parentEntryCount: Int) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarRuntimePlanner.trainingSessionDeleteRemovePath(referencePath: referencePath, parentEntryCount: parentEntryCount)
        #else
        let components = referencePath.split(separator: "/")
        return parentEntryCount <= 1 ? "/U/0/" + components[2] + "/E/" : "/U/0/" + components[2] + "/E/" + components[4] + "/"
        #endif
    }

    private static func trainingSessionFileOperation(id: String, command: String, path: String) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        if let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: id, command: command, path: path) {
            return plannedOperation
        }
        switch command {
        case "REMOVE":
            return (.remove, path)
        default:
            return (.get, path)
        }
    }
    
    static func getTrainingSessionReferences(
        client: BlePsFtpClient,
        fromDate: Date? = nil,
        toDate: Date? = nil
    ) async throws -> [PolarTrainingSessionReference] {
        BleLogger.trace("getTrainingSessions: fromDate=\(String(describing: fromDate)), toDate=\(String(describing: toDate))")
        var updatedReferences: [PolarTrainingSessionReference] = []
        var trainingSessionSummaryPaths: Set<String> = []
        
        let entries = try await fetchRecursive(ARABICA_USER_ROOT_FOLDER, client: client) { name in
            return name.range(of: #"^(\d{8}/|\d{6}/|.*\.BPB|.*\.GZB|E/|\d+/)$"#, options: .regularExpression) != nil
        }

        #if canImport(PolarBleSdkShared)
        if let sharedReferences = trainingSessionReferencesFromShared(entries: entries, fromDate: fromDate, toDate: toDate) {
            return sharedReferences
        }
        #endif
        
        for (path, size) in entries {
            let fileName = (path as NSString).lastPathComponent
            if let dataType = PolarTrainingSessionDataTypes.fromSharedOrRaw(fileName: fileName) {
                let regex = try! NSRegularExpression(pattern: "/U/0/(\\d{8})/E/(\\d{6})/TSESS.BPB$")
                if let match = regex.firstMatch(in: path, range: NSRange(path.startIndex..., in: path)) {
                    let dateStr = String(path[Range(match.range(at: 1), in: path)!])
                    let timeStr = String(path[Range(match.range(at: 2), in: path)!])
                    let dateTimeStr = dateStr + timeStr
                    let dateTime = dateTimeFormatter.date(from: dateTimeStr) ?? Date()
                    let dateMatches = trainingSessionDateMatches(date: dateFormatter.date(from: dateStr) ?? Date(), fromDate: fromDate, toDate: toDate)
                    if dateMatches {
                        trainingSessionSummaryPaths.insert(path)
                        if let index = updatedReferences.firstIndex(where: { $0.path.contains(dateStr) && $0.path.contains(timeStr) }) {
                            if !updatedReferences[index].trainingDataTypes.contains(dataType) {
                                updatedReferences[index].trainingDataTypes.append(dataType)
                                updatedReferences[index].fileSize += Int64(size)
                            }
                        } else {
                            updatedReferences.append(PolarTrainingSessionReference(
                                date: dateTime, path: path, trainingDataTypes: [dataType], exercises: [], fileSize: Int64(size)
                            ))
                        }
                    }
                }
            } else if let exerciseDataType = PolarExerciseDataTypes.fromSharedOrRaw(fileName: fileName) {
                let regex = try! NSRegularExpression(pattern: "/U/0/(\\d{8})/E/(\\d{6})/(\\d{2})/\(exerciseDataType.rawValue)$")
                if let match = regex.firstMatch(in: path, range: NSRange(path.startIndex..., in: path)) {
                    let dateStr = String(path[Range(match.range(at: 1), in: path)!])
                    let timeStr = String(path[Range(match.range(at: 2), in: path)!])
                    let exerciseFolder = String(path[Range(match.range(at: 3), in: path)!])
                    let fullExercisePath = "/U/0/\(dateStr)/E/\(timeStr)/\(exerciseFolder)"
                    let summaryPrefix = "/U/0/\(dateStr)/E/\(timeStr)"
                    let possibleSummaries = trainingSessionSummaryPaths.filter { $0.hasPrefix(summaryPrefix) }
                    if let tseSsPath = possibleSummaries.first,
                       let index = updatedReferences.firstIndex(where: { $0.path == tseSsPath }) {
                        updatedReferences[index].fileSize += Int64(size)
                        if let existingIndex = updatedReferences[index].exercises.firstIndex(where: { $0.path == fullExercisePath }) {
                            var existing = updatedReferences[index].exercises[existingIndex]
                            if !existing.exerciseDataTypes.contains(exerciseDataType) {
                                existing.exerciseDataTypes.append(exerciseDataType)
                                updatedReferences[index].exercises[existingIndex] = existing
                            }
                        } else {
                            updatedReferences[index].exercises.append(PolarExercise(index: 0, path: fullExercisePath, exerciseDataTypes: [exerciseDataType]))
                        }
                    }
                }
            }
        }
        
        return updatedReferences
    }
    
    static func readTrainingSession(client: BlePsFtpClient, reference: PolarTrainingSessionReference) async throws -> PolarTrainingSession {
        BleLogger.trace("readTrainingSession: Starting to read session from path: \(reference.path)")
        let summaryOperation = trainingSessionSummaryReadOperation(path: reference.path)
        let response = try await client.request(try PolarRuntimePlanner.fileOperationBytes(summaryOperation))
        let sessionSummary = try Data_PbTrainingSession(serializedBytes: response as Data)
        let payloadFetchOrder = trainingSessionPayloadFetchOrder(reference: reference)
        let exercises = try await withThrowingTaskGroup(of: PolarExercise.self) { group in
            for exercise in reference.exercises {
                group.addTask {
                    return try await readExercise(client: client, exercise: exercise, payloadFetchOrder: payloadFetchOrder)
                }
            }
            var results: [PolarExercise] = []
            for try await exercise in group { results.append(exercise) }
            return results
        }
        return PolarTrainingSession(reference: reference, sessionSummary: sessionSummary, exercises: exercises)
    }
    
    static func readTrainingSessionWithProgress(
        client: BlePsFtpClient,
        reference: PolarTrainingSessionReference,
        progressHandler: @escaping (PolarTrainingSessionProgress) -> Void
    ) async throws -> PolarTrainingSession {
        class ProgressCallbackImpl: BlePsFtpProgressCallback {
            let totalBytes: Int64
            var accumulatedBytes: Int64 = 0
            let progressHandler: (PolarTrainingSessionProgress) -> Void
            let lock = NSLock()
            init(totalBytes: Int64, progressHandler: @escaping (PolarTrainingSessionProgress) -> Void) {
                self.totalBytes = totalBytes
                self.progressHandler = progressHandler
            }
            func onProgressUpdate(bytesReceived: Int) {
                lock.lock(); accumulatedBytes += Int64(bytesReceived); let cur = accumulatedBytes; lock.unlock()
                let percent = PolarRuntimePlanner.trainingSessionProgressPercent(completedBytes: cur, totalBytes: totalBytes)
                progressHandler(PolarTrainingSessionProgress(totalBytes: totalBytes, completedBytes: cur, progressPercent: percent, currentFileName: nil))
            }
        }
        let totalBytes = reference.fileSize
        let progressCallback = ProgressCallbackImpl(totalBytes: totalBytes, progressHandler: progressHandler)
        client.progressCallback = progressCallback
        defer { client.progressCallback = nil }
        progressHandler(PolarTrainingSessionProgress(totalBytes: totalBytes, completedBytes: 0, progressPercent: 0, currentFileName: nil))
        
        let summaryOperation = trainingSessionSummaryReadOperation(path: reference.path)
        let response = try await client.request(try PolarRuntimePlanner.fileOperationBytes(summaryOperation))
        let sessionSummary = try Data_PbTrainingSession(serializedBytes: response as Data)
        let payloadFetchOrder = trainingSessionPayloadFetchOrder(reference: reference)
        let exercises = try await withThrowingTaskGroup(of: PolarExercise.self) { group in
            for exercise in reference.exercises {
                group.addTask { return try await readExercise(client: client, exercise: exercise, payloadFetchOrder: payloadFetchOrder) }
            }
            var results: [PolarExercise] = []
            for try await exercise in group { results.append(exercise) }
            return results
        }
        progressHandler(PolarTrainingSessionProgress(totalBytes: totalBytes, completedBytes: totalBytes, progressPercent: 100, currentFileName: nil))
        return PolarTrainingSession(reference: reference, sessionSummary: sessionSummary, exercises: exercises)
    }
    
    static func deleteTrainingSession(client: BlePsFtpClient, reference: PolarTrainingSessionReference) async throws {
        let parentReadOperation = trainingSessionDeleteParentReadOperation(reference: reference)
        let content = try await client.request(try PolarRuntimePlanner.fileOperationBytes(parentReadOperation))
        let dir = try Protocol_PbPFtpDirectory(serializedBytes: content as Data)
        let removePlan = trainingSessionDeleteRemoveOperation(reference: reference, parentEntryCount: dir.entries.count)
        _ = try await client.request(try PolarRuntimePlanner.fileOperationBytes(removePlan))
    }
    
    // MARK: - Private helpers
    
    private static func readExercise(client: BlePsFtpClient, exercise: PolarExercise, payloadFetchOrder: [String]) async throws -> PolarExercise {
        let basePath = exercise.path
        let dataResults = try await withThrowingTaskGroup(of: (PolarExerciseDataTypes, Data).self) { group in
            let dataTypesByFileName = Dictionary(uniqueKeysWithValues: exercise.exerciseDataTypes.map { ($0.deviceFileName, $0) })
            let plannedFilePaths = payloadFetchOrder
                .filter { $0.hasPrefix(basePath + "/") }
                .filter { dataTypesByFileName[($0 as NSString).lastPathComponent] != nil }
            for filePath in plannedFilePaths {
                group.addTask {
                    let dataType = dataTypesByFileName[(filePath as NSString).lastPathComponent]!
                    let fileOperation = trainingSessionExerciseFileReadOperation(path: filePath)
                    let response = try await client.request(try PolarRuntimePlanner.fileOperationBytes(fileOperation))
                    let responseData = response as Data
                    let data: Data = trainingSessionPayloadEncoding(fileName: dataType.deviceFileName) == "gzip-protobuf" ? try decodePayload(fileName: dataType.deviceFileName, data: responseData) : responseData
                    return (dataType, data)
                }
            }
            var results: [(PolarExerciseDataTypes, Data)] = []
            for try await r in group { results.append(r) }
            return results
        }
        var summary: Data_PbExerciseBase?
        var route: Data_PbExerciseRouteSamples?
        var route2: Data_PbExerciseRouteSamples2?
        var samples: Data_PbExerciseSamples?
        var samples2: Data_PbExerciseSamples2?
        for (type, data) in dataResults {
            if PolarRuntimePlanner.trainingSessionPayloadMalformed(fileName: type.deviceFileName, payload: data) {
                continue
            }
            switch trainingSessionPayloadParserCase(fileName: type.deviceFileName)?.parser {
            case "PbExerciseBase": summary = try? Data_PbExerciseBase(serializedBytes: data)
            case "PbExerciseRouteSamples": route = try? Data_PbExerciseRouteSamples(serializedBytes: data)
            case "PbExerciseRouteSamples2": route2 = try? Data_PbExerciseRouteSamples2(serializedBytes: data)
            case "PbExerciseSamples": samples = try? Data_PbExerciseSamples(serializedBytes: data)
            case "PbExerciseSamples2": samples2 = try? Data_PbExerciseSamples2(serializedBytes: data)
            default: continue
            }
        }
        return PolarExercise(index: exercise.index, path: basePath, exerciseDataTypes: exercise.exerciseDataTypes,
                             exerciseSummary: summary, route: route, routeAdvanced: route2, samples: samples, samplesAdvanced: samples2)
    }

    static func decodePayload(fileName: String, data: Data) throws -> Data {
        guard trainingSessionPayloadEncoding(fileName: fileName) == "gzip-protobuf" else {
            return data
        }
        #if canImport(PolarBleSdkShared)
        if let decoded = PolarRuntimePlanner.decodeTrainingSessionPayload(fileName: fileName, payload: data) {
            return decoded
        }
        #endif
        return try unzipGzip(data)
    }
    
    private static func unzipGzip(_ data: Data) throws -> Data {
        var stream = z_stream()
        var status: Int32 = Z_OK
        let bufferSize = 16384
        var output = Data(capacity: data.count * 2)
        data.withUnsafeBytes { (srcPointer: UnsafeRawBufferPointer) in
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: srcPointer.bindMemory(to: Bytef.self).baseAddress!)
            stream.avail_in = uInt(data.count)
            status = inflateInit2_(&stream, 15 + 16, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        }
        guard status == Z_OK else {
            throw NSError(domain: "DecompressionError", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Failed to init zlib stream"])
        }
        defer { inflateEnd(&stream) }
        let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
        defer { buffer.deallocate() }
        repeat {
            stream.next_out = buffer; stream.avail_out = uInt(bufferSize)
            status = inflate(&stream, Z_NO_FLUSH)
            if status == Z_STREAM_ERROR || status == Z_DATA_ERROR || status == Z_MEM_ERROR {
                throw NSError(domain: "DecompressionError", code: Int(status), userInfo: [NSLocalizedDescriptionKey: "Decompression failed with zlib error"])
            }
            output.append(buffer, count: bufferSize - Int(stream.avail_out))
        } while status != Z_STREAM_END
        return output
    }
    
    private static func fetchRecursive(
        _ path: String,
        client: BlePsFtpClient,
        condition: @escaping (String) -> Bool
    ) async throws -> [(name: String, size: UInt64)] {
        let directoryOperation = trainingSessionDirectoryReadOperation(path: path)
        let data = try await client.request(try PolarRuntimePlanner.fileOperationBytes(directoryOperation))
        let dir = try Protocol_PbPFtpDirectory(serializedBytes: data as Data)
        var results: [(name: String, size: UInt64)] = []
        for entry in dir.entries where condition(entry.name) {
            let fullPath = directoryOperation.path + entry.name
            if fullPath.hasSuffix("/") {
                let children = try await fetchRecursive(fullPath, client: client, condition: condition)
                results.append(contentsOf: children)
            } else {
                results.append((name: fullPath, size: entry.size))
            }
        }
        return results
    }

    #if canImport(PolarBleSdkShared)
    private static func trainingSessionReferencesFromShared(
        entries: [(name: String, size: UInt64)],
        fromDate: Date?,
        toDate: Date?
    ) -> [PolarTrainingSessionReference]? {
        let encodedEntries = entries.map { "\($0.name)|\($0.size)" }.joined(separator: "\n")
        let shared = PolarRuntimePlanner.trainingSessionReferences(entriesText: encodedEntries)
        guard !shared.isEmpty else { return [] }
        var references: [PolarTrainingSessionReference] = []
        let sharedDateFormatter = DateFormatter()
        sharedDateFormatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        sharedDateFormatter.locale = Locale(identifier: "en_US_POSIX")

        for row in shared.split(separator: "\n") {
            let fields = row.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
            guard let marker = fields.first else { return nil }
            switch marker {
            case "R":
                guard fields.count == 6,
                      let date = sharedDateFormatter.date(from: fields[2]),
                      let fileSize = Int64(fields[4]) else {
                    return nil
                }
                guard trainingSessionDateMatches(date: date, fromDate: fromDate, toDate: toDate) else {
                    references.append(PolarTrainingSessionReference(date: date, path: fields[3], trainingDataTypes: [], exercises: [], fileSize: -1))
                    continue
                }
                references.append(PolarTrainingSessionReference(
                    date: date,
                    path: fields[3],
                    trainingDataTypes: fields[5].split(separator: ";").compactMap { PolarTrainingSessionDataTypes.fromSharedTypeName(String($0)) },
                    exercises: [],
                    fileSize: fileSize
                ))
            case "E":
                guard fields.count == 4 || fields.count == 5,
                      let referenceIndex = Int(fields[1]),
                      referenceIndex < references.count else {
                    return nil
                }
                guard references[referenceIndex].fileSize >= 0 else { continue }
                let exerciseDataTypes = fields[3].split(separator: ";").compactMap { PolarExerciseDataTypes.fromSharedTypeName(String($0)) }
                references[referenceIndex].exercises.append(PolarExercise(index: 0, path: fields[2], exerciseDataTypes: exerciseDataTypes, fileSizes: fields.count == 5 ? sharedFileSizes(fields[4]) : nil))
            default:
                return nil
            }
        }
        return references.filter { $0.fileSize >= 0 }
    }

    private static func trainingSessionSharedReferenceText(reference: PolarTrainingSessionReference) -> String {
        let sharedDateFormatter = DateFormatter()
        sharedDateFormatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        sharedDateFormatter.locale = Locale(identifier: "en_US_POSIX")
        let referenceLine = [
            "R",
            sharedDateFormatter.string(from: reference.date),
            reference.path,
            reference.trainingDataTypes.map { $0.sharedTypeName }.joined(separator: ";"),
            String(reference.fileSize)
        ].joined(separator: "|")
        let exerciseLines = reference.exercises.map { exercise -> String in
            let androidPath = exercise.path.hasSuffix(".BPB") || exercise.path.hasSuffix(".GZB") ? exercise.path : "\(exercise.path)/BASE.BPB"
            let fileSizes = (exercise.fileSizes ?? [:])
                .sorted { $0.key < $1.key }
                .map { "\($0.key):\($0.value)" }
                .joined(separator: ";")
            return [
                "E",
                String(exercise.index),
                androidPath,
                exercise.path,
                exercise.exerciseDataTypes.map { $0.sharedTypeName }.joined(separator: ";"),
                fileSizes
            ].joined(separator: "|")
        }
        return ([referenceLine] + exerciseLines).joined(separator: "\n")
    }

    private static func sharedFileSizes(_ value: String) -> [String: Int64] {
        var fileSizes: [String: Int64] = [:]
        for entry in value.split(separator: ";") {
            let fields = entry.split(separator: ":", maxSplits: 1).map(String.init)
            guard fields.count == 2, let size = Int64(fields[1]) else { continue }
            fileSizes[fields[0]] = size
        }
        return fileSizes
    }

    #endif

    private static func trainingSessionDateMatches(date: Date, fromDate: Date?, toDate: Date?) -> Bool {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        if let sharedDecision = PolarRuntimePlanner.trainingSessionReferenceDateMatches(
            date: formatter.string(from: date),
            fromDate: fromDate.map { formatter.string(from: $0) },
            toDate: toDate.map { formatter.string(from: $0) }
        ) {
            return sharedDecision
        }
        formatter.dateFormat = "yyyyMMdd"
        let dateString = formatter.string(from: date)
        let lowerBound = fromDate ?? date
        let upperBound = toDate ?? date
        return Set(PolarTimeUtils.basicDateRangeStrings(fromDate: lowerBound, toDate: upperBound)).contains(dateString)
    }

    private static func fallbackTrainingSessionPayloadFetchOrder(reference: PolarTrainingSessionReference) -> [String] {
        return [reference.path] + reference.exercises.flatMap { exercise in
            exercise.exerciseDataTypes.map { "\(exercise.path)/\($0.deviceFileName)" }
        }
    }

    private static func fallbackTrainingSessionPayloadParserCase(fileName: String) -> (parser: String, encoding: String)? {
        switch fileName {
        case "TSESS.BPB": return (parser: "PbTrainingSession", encoding: "protobuf")
        case "BASE.BPB": return (parser: "PbExerciseBase", encoding: "protobuf")
        case "ROUTE.BPB": return (parser: "PbExerciseRouteSamples", encoding: "protobuf")
        case "ROUTE.GZB": return (parser: "PbExerciseRouteSamples", encoding: "gzip-protobuf")
        case "ROUTE2.BPB": return (parser: "PbExerciseRouteSamples2", encoding: "protobuf")
        case "ROUTE2.GZB": return (parser: "PbExerciseRouteSamples2", encoding: "gzip-protobuf")
        case "SAMPLES.BPB": return (parser: "PbExerciseSamples", encoding: "protobuf")
        case "SAMPLES.GZB": return (parser: "PbExerciseSamples", encoding: "gzip-protobuf")
        case "SAMPLES2.GZB": return (parser: "PbExerciseSamples2", encoding: "gzip-protobuf")
        default: return nil
        }
    }
}

private extension PolarTrainingSessionDataTypes {
    static func fromSharedOrRaw(fileName: String) -> PolarTrainingSessionDataTypes? {
        #if canImport(PolarBleSdkShared)
        if let sharedType = PolarRuntimePlanner.trainingSessionDataType(fileName: fileName) {
            return fromSharedTypeName(sharedType)
        }
        #endif
        return PolarTrainingSessionDataTypes(rawValue: fileName)
    }

    static func fromSharedTypeName(_ value: String) -> PolarTrainingSessionDataTypes? {
        switch value {
        case "TRAINING_SESSION_SUMMARY": return .trainingSessionSummary
        default: return nil
        }
    }

    var sharedTypeName: String {
        switch self {
        case .trainingSessionSummary: return "TRAINING_SESSION_SUMMARY"
        }
    }
}

private extension PolarExerciseDataTypes {
    var deviceFileName: String {
        #if canImport(PolarBleSdkShared)
        return PolarRuntimePlanner.trainingSessionExerciseDataTypeFileName(typeName: sharedTypeName) ?? rawValue
        #else
        return rawValue
        #endif
    }

    static func fromSharedOrRaw(fileName: String) -> PolarExerciseDataTypes? {
        #if canImport(PolarBleSdkShared)
        if let sharedType = PolarRuntimePlanner.trainingSessionExerciseDataType(fileName: fileName) {
            return fromSharedTypeName(sharedType)
        }
        #endif
        return PolarExerciseDataTypes(rawValue: fileName)
    }

    static func fromSharedTypeName(_ value: String) -> PolarExerciseDataTypes? {
        switch value {
        case "EXERCISE_SUMMARY": return .exerciseSummary
        case "ROUTE": return .route
        case "ROUTE_GZIP": return .routeGzip
        case "ROUTE_ADVANCED_FORMAT": return .routeAdvancedFormat
        case "ROUTE_ADVANCED_FORMAT_GZIP": return .routeAdvancedFormatGzip
        case "SAMPLES": return .samples
        case "SAMPLES_GZIP": return .samplesGzip
        case "SAMPLES_ADVANCED_FORMAT_GZIP": return .samplesAdvancedFormatGzip
        default: return nil
        }
    }

    var sharedTypeName: String {
        switch self {
        case .exerciseSummary: return "EXERCISE_SUMMARY"
        case .route: return "ROUTE"
        case .routeGzip: return "ROUTE_GZIP"
        case .routeAdvancedFormat: return "ROUTE_ADVANCED_FORMAT"
        case .routeAdvancedFormatGzip: return "ROUTE_ADVANCED_FORMAT_GZIP"
        case .samples: return "SAMPLES"
        case .samplesGzip: return "SAMPLES_GZIP"
        case .samplesAdvancedFormatGzip: return "SAMPLES_ADVANCED_FORMAT_GZIP"
        }
    }
}
