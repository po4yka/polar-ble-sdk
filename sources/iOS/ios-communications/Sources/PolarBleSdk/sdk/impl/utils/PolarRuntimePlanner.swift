// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarRuntimePlanner {
    static func commandQuery(id: String, query: String, parameters: [String] = []) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeCommandQuery(id: id, query: query, parametersCsv: parameters.joined(separator: ","))
        #endif
    }

    static func commandReset(id: String, sleep: Bool, factoryDefaults: Bool, otaFirmwareUpdate: Bool) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeCommandReset(id: id, sleep: sleep, factoryDefaults: factoryDefaults, otaFirmwareUpdate: otaFirmwareUpdate)
        #endif
    }

    static func commandSyncStart(id: String) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeCommandSyncStart(id: id)
        #endif
    }

    static func commandSyncStop(id: String) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeCommandSyncStop(id: id)
        #endif
    }

    static func diskTimeQuery(id: String, query: String) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeDiskTimeQuery(id: id, query: query)
        #endif
    }

    static func setLocalTimeV2(systemTimeHour: Int, localTimeHour: Int) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeSetLocalTimeV2(systemTimeHour: Int32(systemTimeHour), localTimeHour: Int32(localTimeHour))
        #endif
    }

    static func setLocalTimeH10(localTimeHour: Int) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeSetLocalTimeH10(localTimeHour: Int32(localTimeHour))
        #endif
    }

    static func restFacadeGet(id: String, path: String, payloadShape: String) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeRestFacadeGet(id: id, path: path, payloadShape: payloadShape)
        #endif
    }

    static func fileFacade(id: String, command: String, path: String, payloadHex: String = "") {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeFileFacade(id: id, command: command, path: path, payloadHex: payloadHex)
        #endif
    }

    static func fileRuntimeError(operation: String, path: String, error: Error) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeFileError(operation: operation, path: path, errorName: String(describing: type(of: error)))
        #endif
    }

    static func userDeviceSettings(id: String, kind: String, path: String, payloadFields: [String] = []) {
        #if canImport(PolarBleSdkShared)
        _ = PolarIosSharedBridge.shared.planRuntimeUserDeviceSettings(id: id, kind: kind, path: path, payloadFieldsCsv: payloadFields.joined(separator: ","))
        #endif
    }
}
