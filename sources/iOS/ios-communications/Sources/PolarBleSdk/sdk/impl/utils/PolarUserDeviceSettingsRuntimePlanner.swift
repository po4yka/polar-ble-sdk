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

    static func settingsPath(fileSystemType: String, deviceSettingsPath: String = "/U/0/S/UDEVSET.BPB", sensorSettingsPath: String = "/UDEVSET.BPB", unknownSettingsPath: String? = "/U/0/S/UDEVSET.BPB") -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.userDeviceSettingsPath(fileSystemType: fileSystemType, deviceSettingsPath: deviceSettingsPath, sensorSettingsPath: sensorSettingsPath, unknownSettingsPath: unknownSettingsPath)
        #else
        switch fileSystemType {
        case "polarFileSystemV2": return deviceSettingsPath
        case "h10FileSystem": return sensorSettingsPath
        default: return unknownSettingsPath
        }
        #endif
    }

    static func deviceLocationName(value: Int) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.userDeviceSettingsDeviceLocationName(value: Int32(value))
        #else
        return nil
        #endif
    }

    static func deviceLocationValue(name: String) -> NSNumber? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.userDeviceSettingsDeviceLocationValue(name: name)
        #else
        return nil
        #endif
    }

    static func usbConnectionModeName(value: Int) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.userDeviceSettingsUsbModeName(value: Int32(value))
        #else
        return nil
        #endif
    }

    static func usbConnectionModeValue(name: String) -> NSNumber? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.userDeviceSettingsUsbModeValue(name: name)
        #else
        return nil
        #endif
    }

    static func usbConnectionModeName(enabled: Bool) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.userDeviceSettingsUsbModeName(value: enabled ? 2 : 1)
        #else
        return nil
        #endif
    }

    static func automaticTrainingDetectionModeName(value: Int) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.userDeviceSettingsAutomaticTrainingDetectionModeName(value: Int32(value))
        #else
        return nil
        #endif
    }

    static func automaticTrainingDetectionModeValue(name: String) -> NSNumber? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.userDeviceSettingsAutomaticTrainingDetectionModeValue(name: name)
        #else
        return nil
        #endif
    }

    static func automaticTrainingDetectionModeName(enabled: Bool) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.userDeviceSettingsAutomaticTrainingDetectionModeName(value: enabled ? 1 : 0)
        #else
        return nil
        #endif
    }

    static func automaticMeasurementStateName(enabled: Bool) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.userDeviceSettingsAutomaticMeasurementStateName(enabled: enabled)
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
