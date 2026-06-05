import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

open class PolarAdvDataUtility {
        
    public static func getDeviceNameFromAdvLocalName(advLocalName: String, withPrefixToTrim prefix: String = "Polar") -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.advertisementDeviceModelName(localName: advLocalName, prefixToTrim: prefix)
        #else
        if (isValidDevice(advLocalName: advLocalName, requiredPrefix: prefix)) {
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
        #endif
    }
    
    public static func isValidDevice(advLocalName: String, requiredPrefix: String = "Polar") -> Bool {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.isValidAdvertisementDeviceName(localName: advLocalName, requiredPrefix: requiredPrefix)
        #else
        return advLocalName.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix(requiredPrefix) &&
            advLocalName.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: " ").count > 2
        #endif
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
