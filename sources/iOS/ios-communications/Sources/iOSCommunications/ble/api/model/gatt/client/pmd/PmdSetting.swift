import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public struct PmdSetting: @unchecked Sendable {
    public enum PmdSettingType: UInt8, CaseIterable {
        case sampleRate = 0
        case resolution
        case range
        case rangeMilliUnit
        case channels
        case factor
        case security
        case unknown = 0xff
    }
    
    static let mapTypeToFieldSize = [PmdSettingType.sampleRate : 2,
                                     PmdSettingType.resolution : 2,
                                     PmdSettingType.range : 2,
                                     PmdSettingType.rangeMilliUnit : 4,
                                     PmdSettingType.channels : 1,
                                     PmdSettingType.factor : 4]
    
    public var settings = [PmdSettingType : Set<UInt32>]()
    public var selected = [PmdSettingType : UInt32]()
    
    public init(_ selected: [PmdSettingType : UInt32]){
        self.selected = selected
    }
    
    public init(_ data: Data) throws {
        self.settings = try PmdSetting.parsePmdSettingsData(data)
        self.selected = settings.reduce(into: [:]) { (result, arg1) in
            let (key, value) = arg1
            result[PmdSetting.PmdSettingType(rawValue: UInt8(key.rawValue)) ?? PmdSetting.PmdSettingType.unknown]=value.max()!
        }
    }
    
    static func parsePmdSettingsData(_ data: Data) throws -> [PmdSettingType : Set<UInt32>] {
        #if canImport(PolarBleSdkShared)
        if let shared = sharedParsedSettings(data) {
            return shared
        }
        #endif
        var offset = 0
        var settings = [PmdSettingType : Set<UInt32>]()
        while (offset+2) < data.count {
            let type = PmdSettingType(rawValue: data[offset]) ?? .unknown
            offset += 1
            let count = Int(data[offset])
            offset += 1
            let advanceStep = mapTypeToFieldSize[type] ?? data.count
            settings[type] = try Set(stride(from: offset, to: offset + (count*advanceStep), by: advanceStep).map { (start) -> UInt32 in
                if (start.advanced(by: advanceStep) <= data.count ) {
                    let value = data.subdata(in: start..<start.advanced(by: advanceStep))
                    offset += advanceStep
                    return BlePmdClient.arrayToUInt(value, offset: 0, size: advanceStep)
                } else {
                    throw BlePmdError.invalidPMDData(description: "Broken PMD settings data.")
                }
            })
        }
        return settings
    }
    
    mutating func updatePmdSettingsFromStartResponse(_ data: Data) throws {
        let settingsFromStartResponse = try PmdSetting.parsePmdSettingsData(data)
        if let factor = settingsFromStartResponse[PmdSettingType.factor] {
            selected[PmdSettingType.factor] = factor.first!
        }
    }
    
    public func serialize() -> Data {
        #if canImport(PolarBleSdkShared)
        if let shared = sharedSelectedSettingsSerialization() {
            return shared
        }
        #endif
        return selected.reduce(into: NSMutableData()) { (result, entry) in
            if entry.key != .factor {
                result.append([UInt8(entry.key.rawValue)], length: 1)
                result.append([0x01], length: 1)
                let fieldSize = UInt32(PmdSetting.mapTypeToFieldSize[entry.key] ?? 0)
                for i in 0..<fieldSize {
                    result.append([UInt8((entry.value >> (i*8)) & 0xff)], length: 1)
                }
            }
        } as Data
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedParsedSettings(_ data: Data) -> [PmdSettingType : Set<UInt32>]? {
        guard let encoded = PmdSettingRuntimePlanner.parsedSettingsCsv(settingsHex: data.pmdHexString) else { return nil }
        var settings = [PmdSettingType : Set<UInt32>]()
        for group in encoded.split(separator: ";", omittingEmptySubsequences: false) where !group.isEmpty {
            let fields = group.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            guard fields.count == 2,
                  let code = UInt8(fields[0]),
                  let type = PmdSettingType(sharedCode: code) else {
                return nil
            }
            let values = fields[1].split(separator: ",", omittingEmptySubsequences: false).reduce(into: Set<UInt32>()) { result, value in
                if let parsed = UInt32(value) {
                    result.insert(parsed)
                }
            }
            settings[type] = values
        }
        return settings
    }

    private func sharedSelectedSettingsSerialization() -> Data? {
        let supportedTypes: Set<PmdSettingType> = [.sampleRate, .resolution, .range, .rangeMilliUnit, .channels, .factor]
        guard Set(selected.keys).isSubset(of: supportedTypes) else { return nil }
        let selectedCsv = selected
            .compactMap { entry -> String? in
                guard let code = entry.key.sharedCode else { return nil }
                return "\(code)=\(entry.value)"
            }
            .sorted()
            .joined(separator: ",")
        guard selectedCsv.split(separator: ",").count == selected.count else { return nil }
        return Data(hexBytes: PmdSettingRuntimePlanner.selectedSettingsHex(selectedCsv: selectedCsv))
    }
    #endif
}

enum PmdSettingRuntimePlanner {
    static func parsedSettingsCsv(settingsHex: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.pmdParsedSettingsCsv(settingsHex: settingsHex)
        #else
        return nil
        #endif
    }

    static func selectedSettingsHex(selectedCsv: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.pmdSelectedSettingsHex(selectedCsv: selectedCsv)
        #else
        return ""
        #endif
    }

    static func settingTypeName(code: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.pmdSettingTypeName(code: code)
        #else
        return nil
        #endif
    }

    static func settingTypeCode(name: String) -> NSNumber? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.pmdSettingTypeCode(name: name)
        #else
        return nil
        #endif
    }
}

#if canImport(PolarBleSdkShared)
private extension Data {
    init?(hexBytes: String) {
        guard hexBytes.count % 2 == 0 else { return nil }
        var bytes: [UInt8] = []
        bytes.reserveCapacity(hexBytes.count / 2)
        var index = hexBytes.startIndex
        while index < hexBytes.endIndex {
            let next = hexBytes.index(index, offsetBy: 2)
            guard let byte = UInt8(hexBytes[index..<next], radix: 16) else { return nil }
            bytes.append(byte)
            index = next
        }
        self.init(bytes)
    }

    var pmdHexString: String {
        map { String(format: "%02x", $0) }.joined()
    }
}

private extension PmdSetting.PmdSettingType {
    init?(sharedCode: UInt8) {
        guard let sharedName = PmdSettingRuntimePlanner.settingTypeName(code: Int32(sharedCode)) else {
            self.init(rawValue: sharedCode)
            return
        }
        switch sharedName {
        case "SAMPLE_RATE": self = .sampleRate
        case "RESOLUTION": self = .resolution
        case "RANGE": self = .range
        case "RANGE_MILLIUNIT": self = .rangeMilliUnit
        case "CHANNELS": self = .channels
        case "FACTOR": self = .factor
        case "SECURITY": self = .security
        default: return nil
        }
    }

    var sharedCode: UInt8? {
        let sharedName: String
        switch self {
        case .sampleRate: sharedName = "SAMPLE_RATE"
        case .resolution: sharedName = "RESOLUTION"
        case .range: sharedName = "RANGE"
        case .rangeMilliUnit: sharedName = "RANGE_MILLIUNIT"
        case .channels: sharedName = "CHANNELS"
        case .factor: sharedName = "FACTOR"
        case .security: sharedName = "SECURITY"
        case .unknown: return rawValue
        }
        guard let code = PmdSettingRuntimePlanner.settingTypeCode(name: sharedName) else { return rawValue }
        return UInt8(truncating: code)
    }
}
#endif
