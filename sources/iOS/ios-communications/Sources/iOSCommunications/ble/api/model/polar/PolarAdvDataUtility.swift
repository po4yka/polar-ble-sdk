import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

open class PolarAdvDataUtility {
        
    public static func getDeviceNameFromAdvLocalName(advLocalName: String, withPrefixToTrim prefix: String = "Polar") -> String {
        if let sharedModelName = PolarAdvertisementRuntimePlanner.deviceModelName(localName: advLocalName, prefixToTrim: prefix) {
            return sharedModelName
        }
        return fallbackDeviceNameFromAdvLocalName(advLocalName: advLocalName, prefix: prefix)
    }

    public static func isValidDevice(advLocalName: String, requiredPrefix: String = "Polar") -> Bool {
        if let sharedIsValid = PolarAdvertisementRuntimePlanner.isValidDeviceName(localName: advLocalName, requiredPrefix: requiredPrefix) {
            return sharedIsValid
        }
        return fallbackIsValidDevice(advLocalName: advLocalName, requiredPrefix: requiredPrefix)
    }

    static func fallbackDeviceNameFromAdvLocalName(advLocalName: String, prefix: String) -> String {
        if fallbackIsValidDevice(advLocalName: advLocalName, requiredPrefix: prefix) {
            let modelName = advLocalName.trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: prefix != "" ? prefix + " " : "", with: "")

            if let endIndex = modelName.lastIndex(of: " ") {
                let mySubstring = modelName[..<(endIndex)]
                return String(mySubstring)
            } else {
                return ""
            }
        } else {
            return ""
        }
    }

    static func fallbackIsValidDevice(advLocalName: String, requiredPrefix: String = "Polar") -> Bool {
        return advLocalName.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix(requiredPrefix) &&
            advLocalName.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: " ").count > 2
    }

    public static func extractDeviceModelFromName(_ deviceName: String) -> String {
        let deviceModel = deviceName
            .lowercased()
            .replacingOccurrences(of: "polar ", with: "")
            .components(separatedBy: " ")
            .dropLast()
            .joined(separator: " ")
        return deviceModel
    }
}

enum PolarAdvertisementRuntimePlanner {
    static func deviceModelName(localName: String, prefixToTrim: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.advertisementDeviceModelName(localName: localName, prefixToTrim: prefixToTrim)
        #else
        return nil
        #endif
    }

    static func isValidDeviceName(localName: String, requiredPrefix: String) -> Bool? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.isValidAdvertisementDeviceName(localName: localName, requiredPrefix: requiredPrefix)
        #else
        return nil
        #endif
    }
}
