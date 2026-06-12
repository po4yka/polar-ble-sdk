// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarRestFacadeRuntimePlanner {
    @discardableResult
    static func get(id: String, path: String, payloadShape: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeRestFacadeGet(id: id, path: path, payloadShape: payloadShape)
        #else
        return "platform-owned"
        #endif
    }

    static func getOperation(id: String, path: String, payloadShape: String) -> (command: Protocol_PbPFtpOperation.Command, path: String)? {
        #if canImport(PolarBleSdkShared)
        return fileOperation(PolarIosSharedBridge.shared.planRuntimeRestFacadeGetOperation(id: id, path: path, payloadShape: payloadShape)).first
        #else
        return nil
        #endif
    }

    private static func fileOperation(_ csv: String) -> [(command: Protocol_PbPFtpOperation.Command, path: String)] {
        return csv.split(separator: ",").compactMap { plannedOperation in
            let parts = plannedOperation.split(separator: ":", maxSplits: 1).map(String.init)
            guard parts.count == 2 else { return nil }
            switch parts[0] {
            case "GET": return (.get, parts[1])
            case "PUT": return (.put, parts[1])
            case "REMOVE": return (.remove, parts[1])
            default: return nil
            }
        }
    }
}
