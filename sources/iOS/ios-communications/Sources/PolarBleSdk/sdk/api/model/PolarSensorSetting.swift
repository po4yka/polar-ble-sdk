/// Copyright © 2019 Polar Electro Oy. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

/// polar sensor settings class
public struct PolarSensorSetting {
    
    /// settings type
    public enum SettingType: Int {
        /// sample rate in hz
        case sampleRate = 0
        /// resolution in bits
        case resolution = 1
        /// range
        case range = 2
        /// range with min and max allowed values
        case rangeMilliunit = 3
        /// amount of channels available
        case channels = 4
        /// type is unknown
        case unknown = 0xff
    }
    
    /// current settings available / set
    public let settings: [SettingType : Set<UInt32>]
    
    init() {
        self.settings = [SettingType : Set<UInt32>]()
    }
    
    /// Constructor with validation that all values are > 0
    ///
    /// - Parameter settings: single key value pairs to start stream
    /// - Throws: PolarErrors.invalidSensorSettingValue if any value is <= 0
    public init(_ settings: [SettingType : UInt32]) throws {
        for (key, value) in settings {
            if value == 0 {
                throw PolarErrors.invalidSensorSettingValue(setting: key, value: value)
            }
        }
        self.settings = settings.mapValues { Set([$0]) }
    }
    
    init(_ settings: [SettingType : Set<UInt32>]) {
        self.settings = settings.reduce(into: [:]) { (result, arg1) in
            let (key, value) = arg1
            result[SettingType.fromSharedOrRaw(code: Int(key.rawValue))]=value
        }
    }
    
    func map2PmdSetting() -> PmdSetting {
        return PmdSetting(settings.reduce(into: [:]) { (result, arg1) in
            let (key, value) = arg1
            result[PmdSetting.PmdSettingType.fromSharedOrRaw(code: Int(key.rawValue))]=value.first ?? 0
        })
    }
    
    /// helper to retrieve max settings available
    ///
    /// - Returns: PolarSensorSetting with max settings
    public func maxSettings() -> PolarSensorSetting {
        let selected = settings.reduce(into: [:]) { (result, arg1) in
            let (key, value) = arg1
            if let maxValue = value.max() {
                result[key] = Set([maxValue])
            }
        } as [SettingType : Set<UInt32>]
        return PolarSensorSetting(selected)
    }
}

private extension PolarSensorSetting.SettingType {
    static func fromSharedOrRaw(code: Int) -> PolarSensorSetting.SettingType {
        #if canImport(PolarBleSdkShared)
        if let sharedName = PolarSensorSettingRuntimePlanner.pmdSettingTypeName(code: Int32(code)) {
            switch sharedName {
            case "SAMPLE_RATE": return .sampleRate
            case "RESOLUTION": return .resolution
            case "RANGE": return .range
            case "RANGE_MILLIUNIT": return .rangeMilliunit
            case "CHANNELS": return .channels
            default: break
            }
        }
        #endif
        return PolarSensorSetting.SettingType(rawValue: code) ?? .unknown
    }
}

private extension PmdSetting.PmdSettingType {
    static func fromSharedOrRaw(code: Int) -> PmdSetting.PmdSettingType {
        #if canImport(PolarBleSdkShared)
        if let sharedName = PolarSensorSettingRuntimePlanner.pmdSettingTypeName(code: Int32(code)) {
            switch sharedName {
            case "SAMPLE_RATE": return .sampleRate
            case "RESOLUTION": return .resolution
            case "RANGE": return .range
            case "RANGE_MILLIUNIT": return .rangeMilliUnit
            case "CHANNELS": return .channels
            case "FACTOR": return .factor
            case "SECURITY": return .security
            default: break
            }
        }
        #endif
        guard code >= 0 && code <= Int(UInt8.max) else {
            return .unknown
        }
        return PmdSetting.PmdSettingType(rawValue: UInt8(code)) ?? .unknown
    }
}

enum PolarSensorSettingRuntimePlanner {
    static func pmdSettingTypeName(code: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.pmdSettingTypeName(code: code)
        #else
        return nil
        #endif
    }

    static func pmdSettingTypeCode(name: String) -> Int32? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.pmdSettingTypeCode(name: name)?.int32Value
        #else
        return nil
        #endif
    }
}

extension PolarSensorSetting: CustomStringConvertible {
    public var description: String {
        var descriptionString: String = ""
        for setting in settings {
            descriptionString.append(contentsOf: "(\(setting.key):\(setting.value) )")
        }
        return descriptionString
    }
}
