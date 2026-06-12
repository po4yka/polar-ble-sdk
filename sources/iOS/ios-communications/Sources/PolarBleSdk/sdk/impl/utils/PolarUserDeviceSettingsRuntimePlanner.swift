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

    static func protobufPayloadFields() -> [String] {
        #if canImport(PolarBleSdkShared)
        return csvValues(PolarIosSharedBridge.shared.userDeviceSettingsProtobufPayloadFields())
        #else
        return ["protobufPayload=platform-built"]
        #endif
    }

    static func telemetryPayloadFields(enabled: Bool) -> [String] {
        #if canImport(PolarBleSdkShared)
        return csvValues(PolarIosSharedBridge.shared.userDeviceSettingsTelemetryPayloadFields(enabled: enabled))
        #else
        return ["telemetryEnabled=\(enabled)"]
        #endif
    }

    static func deviceLocationPayloadFields(value: Int) -> [String] {
        #if canImport(PolarBleSdkShared)
        return csvValues(PolarIosSharedBridge.shared.userDeviceSettingsDeviceLocationPayloadFields(value: Int32(value)))
        #else
        return ["deviceLocation=\(value)"]
        #endif
    }

    static func usbConnectionModePayloadFields(enabled: Bool) -> [String] {
        #if canImport(PolarBleSdkShared)
        return csvValues(PolarIosSharedBridge.shared.userDeviceSettingsUsbConnectionModePayloadFields(enabled: enabled))
        #else
        return ["usbConnectionMode=\(enabled ? "ON" : "OFF")"]
        #endif
    }

    static func automaticTrainingDetectionPayloadFields(enabled: Bool, sensitivity: Int, minimumDurationSeconds: Int) -> [String] {
        #if canImport(PolarBleSdkShared)
        return csvValues(PolarIosSharedBridge.shared.userDeviceSettingsAutomaticTrainingDetectionPayloadFields(enabled: enabled, sensitivity: Int32(sensitivity), minimumDurationSeconds: Int32(minimumDurationSeconds)))
        #else
        return ["automaticTrainingDetectionMode=\(enabled ? "ON" : "OFF")", "automaticTrainingDetectionSensitivity=\(sensitivity)", "minimumTrainingDurationSeconds=\(minimumDurationSeconds)"]
        #endif
    }

    static func automaticOhrPayloadFields(enabled: Bool) -> [String] {
        #if canImport(PolarBleSdkShared)
        return csvValues(PolarIosSharedBridge.shared.userDeviceSettingsAutomaticOhrPayloadFields(enabled: enabled))
        #else
        return ["automaticOhrMeasurement=\(enabled ? "ALWAYS_ON" : "OFF")"]
        #endif
    }

    static func daylightSavingPayloadFields() -> [String] {
        #if canImport(PolarBleSdkShared)
        return csvValues(PolarIosSharedBridge.shared.userDeviceSettingsDaylightSavingPayloadFields())
        #else
        return ["daylightSaving.nextDaylightSavingTime=present", "daylightSaving.offset=nonzero"]
        #endif
    }

    static func parseProtoFields(data: Data) -> [String: String]? {
        #if canImport(PolarBleSdkShared)
        return keyValueFields(PolarIosSharedBridge.shared.userDeviceSettingsParseProtoBytesCsv(protoHex: data.hexString()) ?? "")
        #else
        return nil
        #endif
    }

    static func buildProtoData(fields: [String: String], date: Date, includeTelemetry: Bool = true) -> Data? {
        #if canImport(PolarBleSdkShared)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute, .second, .nanosecond], from: date)
        guard let year = components.year,
              let month = components.month,
              let day = components.day,
              let hour = components.hour,
              let minute = components.minute,
              let second = components.second else {
            return nil
        }
        let millis = (components.nanosecond ?? 0) / 1_000_000
        guard let hex = PolarIosSharedBridge.shared.userDeviceSettingsBuildProtoBytesHex(
            fieldsCsv: csvFields(fields),
            year: Int32(year),
            month: Int32(month),
            day: Int32(day),
            hour: Int32(hour),
            minute: Int32(minute),
            seconds: Int32(second),
            millis: Int32(millis),
            trusted: true,
            includeTelemetry: includeTelemetry
        ) else {
            return nil
        }
        return Data(hexString: hex)
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

    private static func csvValues(_ csv: String) -> [String] {
        return csv.split(separator: ",").map(String.init)
    }

    private static func keyValueFields(_ csv: String) -> [String: String]? {
        if csv.isEmpty {
            return [:]
        }
        var fields: [String: String] = [:]
        for field in csv.split(separator: ",").map(String.init) {
            let parts = field.split(separator: "=", maxSplits: 1).map(String.init)
            guard parts.count == 2 else {
                return nil
            }
            fields[parts[0]] = parts[1]
        }
        return fields
    }

    private static func csvFields(_ fields: [String: String]) -> String {
        let order = [
            "deviceLocation",
            "usbConnectionMode",
            "automaticTrainingDetectionMode",
            "automaticTrainingDetectionSensitivity",
            "minimumTrainingDurationSeconds",
            "telemetryEnabled",
            "autosFilesEnabled"
        ]
        return order.compactMap { key in
            fields[key].map { "\(key)=\($0)" }
        }.joined(separator: ",")
    }
}

private extension Data {
    init(hexString: String) {
        var bytes: [UInt8] = []
        bytes.reserveCapacity(hexString.count / 2)
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let next = hexString.index(index, offsetBy: 2)
            guard let byte = UInt8(hexString[index..<next], radix: 16) else {
                self.init()
                return
            }
            bytes.append(byte)
            index = next
        }
        self.init(bytes)
    }

    func hexString() -> String {
        return map { String(format: "%02x", $0) }.joined()
    }
}
