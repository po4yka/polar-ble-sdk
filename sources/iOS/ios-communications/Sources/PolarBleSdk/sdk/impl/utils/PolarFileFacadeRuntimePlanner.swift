// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarFileFacadeRuntimePlanner {
    @discardableResult
    static func fileFacade(id: String, command: String, path: String, payloadHex: String = "") -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeFileFacade(id: id, command: command, path: path, payloadHex: payloadHex)
        #else
        return "platform-owned"
        #endif
    }

    static func fileFacadeOperation(id: String, command: String, path: String, payloadHex: String = "") -> (command: Protocol_PbPFtpOperation.Command, path: String)? {
        #if canImport(PolarBleSdkShared)
        return fileOperation(PolarIosSharedBridge.shared.planRuntimeFileFacadeOperation(id: id, command: command, path: path, payloadHex: payloadHex)).first
        #else
        return nil
        #endif
    }

    static func normalizeFileListFolderPath(_ folderPath: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.normalizeFileListFolderPath(folderPath: folderPath)
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
