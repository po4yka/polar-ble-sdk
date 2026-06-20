/// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarRestServiceProjectionPlanner {
    static func serviceList(jsonData: Data) throws -> PolarDeviceRestApiServices {
        #if canImport(PolarBleSdkShared)
        try validateJsonObject(jsonData)
        let jsonPayload = try jsonPayloadString(jsonData)
        let paths = sharedMap(PolarIosSharedBridge.shared.restServiceJsonPathsForServices(jsonPayload: jsonPayload))
        return PolarDeviceRestApiServices(pathsForServices: PolarIosSharedBridge.shared.restServiceJsonHasServices(jsonPayload: jsonPayload) ? paths : nil)
        #else
        return try JSONDecoder().decode(PolarDeviceRestApiServices.self, from: jsonData)
        #endif
    }

    static func serviceDescription(jsonData: Data) throws -> PolarDeviceRestApiServiceDescription {
        #if canImport(PolarBleSdkShared)
        try validateJsonObject(jsonData)
        let jsonPayload = try jsonPayloadString(jsonData)
        return PolarDeviceRestApiServiceDescription(
            events: sharedList(PolarIosSharedBridge.shared.restServiceDescriptionJsonEvents(jsonPayload: jsonPayload)),
            endpoints: sharedList(PolarIosSharedBridge.shared.restServiceDescriptionJsonEndpoints(jsonPayload: jsonPayload)),
            actions: sharedMap(PolarIosSharedBridge.shared.restServiceDescriptionJsonActions(jsonPayload: jsonPayload)),
            details: sharedNestedMap(PolarIosSharedBridge.shared.restServiceDescriptionJsonEventDetails(jsonPayload: jsonPayload)),
            triggers: sharedNestedMap(PolarIosSharedBridge.shared.restServiceDescriptionJsonEventTriggers(jsonPayload: jsonPayload))
        )
        #else
        return try JSONDecoder().decode(PolarDeviceRestApiServiceDescription.self, from: jsonData)
        #endif
    }

    static func serviceNames(_ pathsForServices: [String: String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        return sharedList(PolarIosSharedBridge.shared.restServiceNames(entries: pathsForServices.sharedLineMap))
        #else
        return Array(pathsForServices.keys)
        #endif
    }

    static func servicePaths(_ pathsForServices: [String: String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        return sharedList(PolarIosSharedBridge.shared.restServicePaths(entries: pathsForServices.sharedLineMap))
        #else
        return Array(pathsForServices.values)
        #endif
    }

    static func events(_ events: [String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        return sharedList(PolarIosSharedBridge.shared.restEvents(eventsCsv: events.joined(separator: ",")))
        #else
        return events
        #endif
    }

    static func endpoints(_ endpoints: [String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        return sharedList(PolarIosSharedBridge.shared.restEndpoints(endpointsCsv: endpoints.joined(separator: ",")))
        #else
        return endpoints
        #endif
    }

    static func actions(_ actions: [String: String]) -> [String: String] {
        #if canImport(PolarBleSdkShared)
        return sharedMap(PolarIosSharedBridge.shared.restActions(entries: actions.sharedLineMap))
        #else
        return actions
        #endif
    }

    static func actionNames(_ actions: [String: String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        return sharedList(PolarIosSharedBridge.shared.restActionNames(entries: actions.sharedLineMap))
        #else
        return Array(actions.keys)
        #endif
    }

    static func actionPaths(_ actions: [String: String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        return sharedList(PolarIosSharedBridge.shared.restActionPaths(entries: actions.sharedLineMap))
        #else
        return Array(actions.values)
        #endif
    }

    static func eventDetails(details: [String], triggers: [String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        return sharedList(PolarIosSharedBridge.shared.restEventDetails(detailsCsv: details.joined(separator: ","), triggersCsv: triggers.joined(separator: ",")))
        #else
        return details
        #endif
    }

    static func eventTriggers(details: [String], triggers: [String]) -> [String] {
        #if canImport(PolarBleSdkShared)
        return sharedList(PolarIosSharedBridge.shared.restEventTriggers(detailsCsv: details.joined(separator: ","), triggersCsv: triggers.joined(separator: ",")))
        #else
        return triggers
        #endif
    }
}

private func jsonPayloadString(_ data: Data) throws -> String {
    guard let payload = String(data: data, encoding: .utf8) else {
        throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "REST JSON payload is not valid UTF-8"))
    }
    return payload
}

private func validateJsonObject(_ data: Data) throws {
    do {
        guard try JSONSerialization.jsonObject(with: data) is [String: Any] else {
            throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "REST JSON payload root is not an object"))
        }
    } catch let error as DecodingError {
        throw error
    } catch {
        throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "REST JSON payload is malformed", underlyingError: error))
    }
}

private func sharedList(_ value: String) -> [String] {
    if value.isEmpty { return [] }
    return value.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
}

private func sharedMap(_ value: String) -> [String: String] {
    if value.isEmpty { return [:] }
    return Dictionary(uniqueKeysWithValues: value.split(separator: "\n", omittingEmptySubsequences: false).compactMap { line in
        let parts = line.split(separator: "\t", maxSplits: 1, omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 2 else { return nil }
        return (parts[0], parts[1])
    })
}

private func sharedNestedMap(_ value: String) -> [String: [String]] {
    if value.isEmpty { return [:] }
    return Dictionary(uniqueKeysWithValues: value.split(separator: "\n", omittingEmptySubsequences: false).compactMap { line in
        let parts = line.split(separator: "\t", maxSplits: 1, omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 2 else { return nil }
        return (parts[0], sharedList(parts[1]))
    })
}

private extension Dictionary where Key == String, Value == String {
    var sharedLineMap: String {
        return map { key, value in "\(key)\t\(value)" }.joined(separator: "\n")
    }
}

extension PolarBleApiImpl: PolarRestServiceApi {

    func listRestApiServices(identifier: String) async throws -> PolarDeviceRestApiServices {
        let serviceApiPath = "/REST/SERVICE.API"
        let plannedOperation = PolarRuntimePlanner.restFacadeGetOperation(id: "list-rest-api-services-success", path: serviceApiPath, payloadShape: "service-list-json")
        try ensureRestFacadeRuntimePlan(id: "list-rest-api-services-success", path: serviceApiPath, payloadShape: "service-list-json")
        let data = try await getDataFromPath(identifier: identifier, path: plannedOperation?.path ?? serviceApiPath)
        return try PolarRestServiceProjectionPlanner.serviceList(jsonData: data)
    }

    func getRestApiDescription(identifier: String, path: String) async throws -> PolarDeviceRestApiServiceDescription {
        let plannedOperation = PolarRuntimePlanner.restFacadeGetOperation(id: "get-rest-api-description-success", path: path, payloadShape: "service-description-json")
        try ensureRestFacadeRuntimePlan(id: "get-rest-api-description-success", path: path, payloadShape: "service-description-json")
        let data = try await getDataFromPath(identifier: identifier, path: plannedOperation?.path ?? path)
        return try PolarRestServiceProjectionPlanner.serviceDescription(jsonData: data)
    }

    private func getDataFromPath(identifier: String, path: String) async throws -> Data {
        let session = try await serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: PolarFileFacadePlanId.readFileSuccess, command: "GET", path: path)
        let operation = plannedOperation ?? (command: .get, path: path)
        try ensureFileFacadeRuntimePlan(id: PolarFileFacadePlanId.readFileSuccess, command: "GET", path: path)
        let requestData = try PolarRuntimePlanner.fileOperationBytes(operation)
        let responseData = try await client.request(requestData)
        let data = responseData as Data
        if data.isEmpty {
            PolarRuntimePlanner.restRequestTransportGet(path: path, payloadHex: "")
        }
        return data
    }

    func putNotification(identifier: String, notification: String, path: String) async throws {
        try await pFtpPutOperation(identifier: identifier, path: path, data: notification.data(using: .utf8)!)
    }

    private func pFtpPutOperation(identifier: String, path: String, data: Data) async throws {
        try await pFtpWriteOperation(identifier: identifier, command: .put, path: path, data: data)
    }

    private func pFtpWriteOperation(identifier: String, command: Protocol_PbPFtpOperation.Command, path: String, data: Data) async throws {
        let session = try await serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        let payloadHex = data.map { String(format: "%02x", $0) }.joined()
        let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: PolarFileFacadePlanId.writeFileSuccess, command: "PUT", path: path, payloadHex: payloadHex)
        let operation = plannedOperation ?? (command: command, path: path)
        try ensureFileFacadeRuntimePlan(id: PolarFileFacadePlanId.writeFileSuccess, command: "PUT", path: path, payloadHex: payloadHex)
        try PolarRuntimePlanner.ensurePsFtpWriteRuntimePlan(payloadSize: data.count)
        let proto = try PolarRuntimePlanner.fileOperationBytes(operation)
        let inputStream = InputStream(data: data)
        // Consume the write stream to completion (ignore progress values)
        do {
            for try await _ in client.write(proto as NSData, data: inputStream) {}
        } catch {
            PolarRuntimePlanner.fileRuntimeError(operation: "writeFile", path: path, error: error)
            throw error
        }
    }

    private func ensureRestFacadeRuntimePlan(id: String, path: String, payloadShape: String) throws {
        let terminal = PolarRuntimePlanner.restFacadeGet(id: id, path: path, payloadShape: payloadShape)
        guard terminal == "success" || terminal == "platform-owned" else {
            throw PolarErrors.polarBleSdkInternalException(description: "REST facade planning failed: \(terminal)")
        }
    }

    private func ensureFileFacadeRuntimePlan(id: String, command: String, path: String, payloadHex: String = "") throws {
        let terminal = PolarRuntimePlanner.fileFacade(id: id, command: command, path: path, payloadHex: payloadHex)
        guard terminal == "success" || terminal == "platform-owned" else {
            throw PolarErrors.polarBleSdkInternalException(description: "File facade planning failed: \(terminal)")
        }
    }

    func receiveRestApiEvents<T: Decodable>(identifier: String) -> AsyncThrowingStream<[T], Error> {
        return AsyncThrowingStream { continuation in
            Task {
                do {
                    let session = try await self.serviceClientUtils.sessionFtpClientReady(identifier)
                    guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
                        continuation.finish(throwing: PolarErrors.serviceNotFound)
                        return
                    }
                    for try await items in client.receiveRestApiEvents(identifier: identifier) as AsyncThrowingStream<[T], Error> {
                        continuation.yield(items)
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
}
