/// Copyright © 2025 Polar Electro Oy. All rights reserved.

import Foundation

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
        var intValue: Int?
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
        let keyedDecodingContainer: KeyedDecodingContainer = try decoder.container(keyedBy: CodingKeys.self)
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
                if let actions = try? keyedDecodingContainer.decode([String: String].self, forKey: key) {
                    dictionary["cmd"] = actions
                }
            default:
                // rest are event descriptions, with details and/or triggers
                if let eventDescription = try? keyedDecodingContainer.decode([String: [String]].self, forKey: key) {
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

    /// Lists event details that may be requested as returned event parameter values using action path containing **details=[]** parameter placeholder
    /// - parameters:
    ///      - eventName: the REST API event to get details for
    /// - returns: detail names
    func eventDetails(for eventName: String) -> [String] {
        let eventDescription = dictionary?[eventName] as? [String: [String]] ?? [:]
        return PolarRestServiceProjectionPlanner.eventDetails(details: eventDescription["details"] ?? [], triggers: eventDescription["triggers"] ?? [])
    }

    /// Lists triggers that may be used as trigger parameter list values when action path contains **triggers=[]** parameter placeholder
    /// - parameters:
    ///      - eventName: the REST API event to get triggers for
    /// - returns: triggers for the events
    func eventTriggers(for eventName: String) -> [String] {
        let eventDescription = dictionary?[eventName] as? [String: [String]] ?? [:]
        return PolarRestServiceProjectionPlanner.eventTriggers(details: eventDescription["details"] ?? [], triggers: eventDescription["triggers"] ?? [])
    }
}

/// Methods related to working with services conforming to SAGRFC95 Service discovery over PFTP
public protocol PolarRestServiceApi {
    func listRestApiServices(identifier: String) async throws -> PolarDeviceRestApiServices
    func getRestApiDescription(identifier: String, path: String) async throws -> PolarDeviceRestApiServiceDescription
    func putNotification(identifier: String, notification: String, path: String) async throws
    func receiveRestApiEvents<T: Decodable>(identifier: String) -> AsyncThrowingStream<[T], Error>
}
