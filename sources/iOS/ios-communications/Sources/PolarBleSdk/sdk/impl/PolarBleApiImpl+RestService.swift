/// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation
import CoreBluetooth
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

/// Lists REST API services and corresponding paths
///
public struct PolarDeviceRestApiServices: Decodable {
    
    /// Maps available REST API service names to corresponding paths
    let pathsForServices: [String: String]?
    enum CodingKeys: String, CodingKey {
        case pathsForServices = "services"
    }

    init(pathsForServices: [String: String]?) {
        self.pathsForServices = pathsForServices
    }
    
    /// Lists REST API service names
    var serviceNames: [String] {
        return PolarRestServiceProjectionPlanner.serviceNames(pathsForServices ?? [:])
    }
    
    /// Lists REST API service paths
    var servicePaths: [String] {
        return PolarRestServiceProjectionPlanner.servicePaths(pathsForServices ?? [:])
    }
}

/// Describes specific service API per SAGRFC95
///
public struct PolarDeviceRestApiServiceDescription: Decodable {

    private let dictionary: [String: Decodable]?
    
    struct CodingKeys: CodingKey {
        var intValue: Int? = nil
        init?(intValue: Int) {
            self.intValue = intValue
        }
        var stringValue: String = ""
        init?(stringValue: String) {
            self.stringValue = stringValue
        }
    }

    init(events: [String], endpoints: [String], actions: [String: String], details: [String: [String]], triggers: [String: [String]]) {
        var dictionary = Dictionary<String, Decodable>()
        dictionary["events"] = events
        dictionary["endpoints"] = endpoints
        dictionary["cmd"] = actions
        for eventName in Set(details.keys).union(triggers.keys) {
            dictionary[eventName] = [
                "details": details[eventName] ?? [],
                "triggers": triggers[eventName] ?? []
            ]
        }
        self.dictionary = dictionary
    }
    
    // By conforming to Decodable, this struct can be parsed with JSONDecoder the usual way
    
    public init(from decoder: any Decoder) throws {
        let keyedDecodingContainer:KeyedDecodingContainer = try decoder.container(keyedBy: CodingKeys.self)
        var dictionary = Dictionary<String, Decodable>()
        for key in keyedDecodingContainer.allKeys {
            switch key.stringValue {
            case "events":
                if let events = try? keyedDecodingContainer.decode([String].self, forKey: key) {
                    dictionary["events"] = events
                }
            case "endpoints":
                if let endpoints = try? keyedDecodingContainer.decode([String].self, forKey: key) {
                    dictionary["endpoints"] = endpoints
                }
                
            case "cmd":
                if let actions = try? keyedDecodingContainer.decode([String:String].self, forKey: key) {
                    dictionary["cmd"] = actions
                }
            default:
                // rest are event descriptions, with details and/or triggers
                if let eventDescription = try? keyedDecodingContainer.decode([String:[String]].self, forKey: key) {
                    dictionary[key.stringValue] = eventDescription
                }
            }
        }
        self.dictionary = dictionary
    }
    
    /// Events that can be acted upon using actions. Actions are returned in `actions` and `actionNames` properties.
    var events: [String] {
        return PolarRestServiceProjectionPlanner.events(dictionary?["events"] as? [String] ?? [])
    }
    
    /// Endpoints that can be applied in **endpoint=** parameter in paths from `actions` and `actionPaths`
    var endpoints: [String] {
        return PolarRestServiceProjectionPlanner.endpoints(dictionary?["endpoints"] as? [String] ?? [])
    }
    
    /// Actions/commands that can be sent, using put operation of corresponding path string
    ///
    /// Path strings can contain following placeholders:
    /// **event=**: event name may follow equal to sign in path. Event names are listed using `events` property. If given, the action targets the event.
    /// **resend=**: true or false may follow equal sign in path. true means client would like to receive old events passed since last drop of connection
    /// **details=[]**: list of detail names may follow equal sign in path, specifying event detailed data. Details are listed using `eventDetails`.
    /// **triggers=[]**: list of triggers may follow equal sign in path, specifying triggering related to action. Triggers are listed using `eventTriggers`.
    /// **endpoint=**: endpoint, listed by `endpoints`, that is related to the action. This can be used in post action paths.
    var actions: [String: String] {
        return PolarRestServiceProjectionPlanner.actions(dictionary?["cmd"] as? [String: String] ?? [:])
    }
    
    /// Just the action names from `actions` property
    var actionNames: [String] {
        return PolarRestServiceProjectionPlanner.actionNames(actions)
    }
    
    /// Just the action paths from `actions` property
    var actionPaths: [String] {
        return PolarRestServiceProjectionPlanner.actionPaths(actions)
    }
    
    /// Lists event details that may be requested as returned event parameter values using action
    /// path containing **details=[]** parameter placeholder
    /// - parameters:
    ///      - eventName: the REST API event to get details for
    /// - returns: detail names
    func eventDetails(for eventName: String) -> [String] {
        let eventDescription = dictionary?[eventName] as? [String: [String]] ?? [:]
        return PolarRestServiceProjectionPlanner.eventDetails(details: eventDescription["details"] ?? [], triggers: eventDescription["triggers"] ?? [])
    }
    
    /// Lists triggers that may be used as trigger parameter list values when action path contains
    /// **triggers=[]** parameter placheholder
    /// - parameters:
    ///      - eventName: the REST API event to get triggers for
    /// - returns: triggers for the events
    func eventTriggers(for eventName: String) -> [String] {
        let eventDescription = dictionary?[eventName] as? [String: [String]] ?? [:]
        return PolarRestServiceProjectionPlanner.eventTriggers(details: eventDescription["details"] ?? [], triggers: eventDescription["triggers"] ?? [])
    }
}

enum PolarRestServiceProjectionPlanner {
    static func serviceList(jsonData: Data) throws -> PolarDeviceRestApiServices {
        #if canImport(PolarBleSdkShared)
        let jsonPayload = try jsonPayloadString(jsonData)
        let paths = sharedMap(PolarIosSharedBridge.shared.restServiceJsonPathsForServices(jsonPayload: jsonPayload))
        return PolarDeviceRestApiServices(pathsForServices: PolarIosSharedBridge.shared.restServiceJsonHasServices(jsonPayload: jsonPayload) ? paths : nil)
        #else
        return try JSONDecoder().decode(PolarDeviceRestApiServices.self, from: jsonData)
        #endif
    }

    static func serviceDescription(jsonData: Data) throws -> PolarDeviceRestApiServiceDescription {
        #if canImport(PolarBleSdkShared)
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

/// Methods related to working with services conforming to SAGRFC95 Service discovery over PFTP
public protocol PolarRestServiceApi {
    func listRestApiServices(identifier: String) async throws -> PolarDeviceRestApiServices
    func getRestApiDescription(identifier: String, path: String) async throws -> PolarDeviceRestApiServiceDescription
    func putNotification(identifier: String, notification: String, path: String) async throws
    func receiveRestApiEvents<T: Decodable>(identifier: String) -> AsyncThrowingStream<[T], Error>
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
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: "read-low-level-file-success", command: "GET", path: path)
        let operation = plannedOperation ?? (command: .get, path: path)
        try ensureFileFacadeRuntimePlan(id: "read-low-level-file-success", command: "GET", path: path)
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
        let session = try serviceClientUtils.sessionFtpClientReady(identifier)
        guard let client = session.fetchGattClient(BlePsFtpClient.PSFTP_SERVICE) as? BlePsFtpClient else {
            throw PolarErrors.serviceNotFound
        }
        let payloadHex = data.map { String(format: "%02x", $0) }.joined()
        let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: "write-low-level-file-success", command: "PUT", path: path, payloadHex: payloadHex)
        let operation = plannedOperation ?? (command: command, path: path)
        try ensureFileFacadeRuntimePlan(id: "write-low-level-file-success", command: "PUT", path: path, payloadHex: payloadHex)
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
                    let session = try self.serviceClientUtils.sessionFtpClientReady(identifier)
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
