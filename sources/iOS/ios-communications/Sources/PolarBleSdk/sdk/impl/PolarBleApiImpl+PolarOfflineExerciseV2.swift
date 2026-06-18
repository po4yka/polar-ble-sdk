import Foundation
import SwiftProtobuf

extension PolarBleApiImpl: PolarOfflineExerciseV2Api {

    private static let samplesFile = "SAMPLES.BPB"
    private static let deviceInfoPath = "/DEVICE.BPB"

    static func offlineExerciseFetchOperation(path: String) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        return offlineExerciseFileOperation(id: "offline-exercise-fetch", command: "GET", path: path)
    }

    static func offlineExerciseRemoveOperation(path: String) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        return offlineExerciseFileOperation(id: "offline-exercise-remove", command: "REMOVE", path: path)
    }

    static func offlineExerciseDeviceInfoReadOperation() -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        return offlineExerciseFileOperation(id: "offline-exercise-read-device-info", command: "GET", path: deviceInfoPath)
    }

    private static func offlineExerciseFileOperation(id: String, command: String, path: String) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
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

    private func handleError(_ error: Error) -> Error {
        let nsError = error as NSError
        if let mapped = Protocol_PbPFtpError(rawValue: nsError.code) {
            return NSError(domain: nsError.domain, code: nsError.code,
                userInfo: [NSLocalizedDescriptionKey: "\(mapped) (\(nsError.localizedDescription))"])
        }
        return error
    }

    // MARK: - PolarOfflineExerciseV2Api

    func startOfflineExerciseV2(
        identifier: String,
        sportProfile: PolarExerciseSession.SportProfile
    ) async throws -> OfflineExerciseStartResult {
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            var sportId = PbSportIdentifier()
            sportId.value = UInt64(sportProfile.rawValue)
            var params = Protocol_PbPFtpStartDmExerciseParams()
            params.sportIdentifier = sportId
            let query = try offlineExerciseCommandQueryValue(id: "offline-exercise-v2-start", query: "START_DM_EXERCISE", parameters: ["sportProfileId=\(sportProfile.rawValue)"]) ?? Protocol_PbPFtpQuery.startDmExercise.rawValue
            let response = try await client.query(query, parameters: try params.serializedData() as NSData)
            let proto = try Protocol_PbPftpStartDmExerciseResult(serializedBytes: Data(response))
            let result: OfflineExerciseStartResultType
            switch proto.result {
            case .resultSuccess:     result = .success
            case .resultExeOngoing:  result = .exerciseOngoing
            case .resultLowBattery:  result = .lowBattery
            case .resultSdkMode:     result = .sdkMode
            case .resultUnknownSport: result = .unknownSport
            default:                 result = .other
            }
            return OfflineExerciseStartResult(result: result, directoryPath: proto.dmDirectoryPath)
        } catch {
            throw handleError(error)
        }
    }

    func stopOfflineExerciseV2(identifier: String) async throws {
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            var params = Protocol_PbPFtpStopExerciseParams()
            params.save = true
            let query = try offlineExerciseCommandQueryValue(id: "offline-exercise-v2-stop", query: "STOP_EXERCISE", parameters: ["save=true"]) ?? Protocol_PbPFtpQuery.stopExercise.rawValue
            _ = try await client.query(query, parameters: try params.serializedData() as NSData)
        } catch {
            throw handleError(error)
        }
    }

    func getOfflineExerciseStatusV2(identifier: String) async throws -> Bool {
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            let query = try offlineExerciseCommandQueryValue(id: "offline-exercise-v2-status", query: "GET_EXERCISE_STATUS") ?? Protocol_PbPFtpQuery.getExerciseStatus.rawValue
            let response = try await client.query(query, parameters: Data() as NSData)
            let proto = try Protocol_PbPftpGetExerciseStatusResult(serializedBytes: Data(response))
            return proto.exerciseType == .exerciseTypeDataMerge && proto.exerciseState == .exerciseStateRunning
        } catch {
            throw handleError(error)
        }
    }

    func listOfflineExercisesV2(identifier: String, directoryPath: String) -> AsyncThrowingStream<PolarExerciseEntry, Error> {
        let fileUtilsLocal = PolarFileUtils(listener: listener, serviceClientUtils: serviceClientUtils)
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    for try await path in fileUtilsLocal.listFiles(identifier: identifier, folderPath: directoryPath, condition: { $0.hasSuffix(Self.samplesFile) }, recurseDeep: true) {
                        continuation.yield((path: path, date: Date(), entryId: URL(fileURLWithPath: path).lastPathComponent))
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }

    func fetchOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry) async throws -> PolarExerciseData {
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            let fetchOperation = Self.offlineExerciseFetchOperation(path: entry.path)
            try ensureOfflineExerciseFileRuntimePlan(id: "offline-exercise-fetch", command: "GET", path: entry.path)
            let response = try await client.request(try PolarRuntimePlanner.fileOperationBytes(fetchOperation))
            let samples = try Data_PbExerciseSamples(serializedBytes: Data(response))
            if samples.hasRrSamples {
                return PolarExerciseData(interval: samples.recordingInterval.seconds, samples: samples.rrSamples.rrIntervals)
            } else {
                return PolarExerciseData(interval: samples.recordingInterval.seconds, samples: samples.heartRateSamples)
            }
        } catch {
            throw handleError(error)
        }
    }

    func removeOfflineExerciseV2(identifier: String, entry: PolarExerciseEntry) async throws {
        do {
            let session = try serviceClientUtils.sessionFtpClientReady(identifier)
            guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                throw PolarErrors.serviceNotFound
            }
            let removeOperation = Self.offlineExerciseRemoveOperation(path: entry.path)
            try ensureOfflineExerciseFileRuntimePlan(id: "offline-exercise-remove", command: "REMOVE", path: entry.path)
            _ = try await client.request(try PolarRuntimePlanner.fileOperationBytes(removeOperation))
        } catch {
            throw handleError(error)
        }
    }

    func isOfflineExerciseV2Supported(identifier: String) async throws -> Bool {
        // Wait for PFTP session to be ready (up to 10 seconds, polling every 5s)
        let timeoutAt = Date().addingTimeInterval(10)
        while Date() < timeoutAt {
            do {
                let session = try serviceClientUtils.sessionFtpClientReady(identifier)
                guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else { return false }
                return try await checkDmExerciseSupport(client)
            } catch {
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }
        throw PolarErrors.timeout(description: "Timeout while waiting for device session, deviceId: \(identifier)")
    }

    private func checkDmExerciseSupport(_ client: BlePsFtpClient) async throws -> Bool {
        let deviceInfoOperation = Self.offlineExerciseDeviceInfoReadOperation()
        do {
            try ensureOfflineExerciseFileRuntimePlan(id: "offline-exercise-read-device-info", command: "GET", path: Self.deviceInfoPath)
            let response = try await client.request(try PolarRuntimePlanner.fileOperationBytes(deviceInfoOperation))
            let deviceInfo = try Data_PbDeviceInfo(serializedBytes: Data(response))
            return deviceInfo.capabilities.contains("dm_exercise")
        } catch {
            BleLogger.error("Failed to check dm_exercise capability: \(error)")
            return false
        }
    }

    private func offlineExerciseCommandQueryValue(id: String, query: String, parameters: [String] = []) throws -> Int? {
        let terminal = PolarRuntimePlanner.commandQuery(id: id, query: query, parameters: parameters)
        guard terminal == "success" || terminal == "platform-owned" else {
            throw PolarErrors.polarBleSdkInternalException(description: "Offline exercise command planning failed: \(terminal)")
        }
        return PolarRuntimePlanner.commandQueryValue(id: id, query: query, parameters: parameters)
    }

    private func ensureOfflineExerciseFileRuntimePlan(id: String, command: String, path: String) throws {
        let terminal = PolarRuntimePlanner.fileFacade(id: id, command: command, path: path)
        guard terminal == "success" || terminal == "platform-owned" else {
            throw PolarErrors.polarBleSdkInternalException(description: "Offline exercise file planning failed: \(terminal)")
        }
    }
}
