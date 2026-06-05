// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarUserDeviceSettingsRuntimePlanner {
    @discardableResult
    static func plan(id: String, kind: String, path: String, payloadFields: [String] = []) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeUserDeviceSettings(id: id, kind: kind, path: path, payloadFieldsCsv: payloadFields.joined(separator: ","))
        #else
        return "platform-owned"
        #endif
    }

    static func operations(id: String, kind: String, path: String, payloadFields: [String] = []) -> [(command: Protocol_PbPFtpOperation.Command, path: String)]? {
        #if canImport(PolarBleSdkShared)
        return operations(PolarIosSharedBridge.shared.planRuntimeUserDeviceSettingsOperations(id: id, kind: kind, path: path, payloadFieldsCsv: payloadFields.joined(separator: ",")))
        #else
        return nil
        #endif
    }

    private static func operations(_ csv: String) -> [(command: Protocol_PbPFtpOperation.Command, path: String)] {
        return csv.split(separator: ",").compactMap { plannedOperation in
            let parts = plannedOperation.split(separator: ":", maxSplits: 1).map(String.init)
            guard parts.count == 2 else { return nil }
            switch parts[0] {
            case "read": return (.get, parts[1])
            case "write": return (.put, parts[1])
            default: return nil
            }
        }
    }
}
