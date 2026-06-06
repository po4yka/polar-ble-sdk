//  Copyright © 2023 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

internal struct PmdControlPointResponse {
    static let CONTROL_POINT_RESPONSE_CODE: UInt8 = 0xF0
    public let response: UInt8
    public let opCode: UInt8
    public let type: PmdMeasurementType
    public let errorCode: PmdResponseCode
    public let more: Bool
    public let parameters = NSMutableData()
    public init(_ data: Data) {
        #if canImport(PolarBleSdkShared)
        if let shared = PmdControlPointResponse.sharedParsedResponse(data) {
            response = shared.response
            opCode = shared.opCode
            type = shared.type
            errorCode = shared.errorCode
            more = shared.more
            parameters.append(shared.parameters)
            return
        }
        #endif
        response = data[0]
        opCode = data[1]
        type = PmdMeasurementType(rawValue: data[2]) ?? PmdMeasurementType.unknown_type
        errorCode = PmdResponseCode(rawValue: Int(data[3])) ?? PmdResponseCode.unknown_error
        if data.count > 4 {
            more = data[4] != 0
            parameters.append(data.subdata(in: 5..<data.count))
        } else {
            more = false
        }
    }

    #if canImport(PolarBleSdkShared)
    private static func sharedParsedResponse(_ data: Data) -> (response: UInt8, opCode: UInt8, type: PmdMeasurementType, errorCode: PmdResponseCode, more: Bool, parameters: Data)? {
        guard let encoded = PmdControlPointRuntimePlanner.responseFields(responseHex: data.controlPointHexString) else { return nil }
        let fields = encoded.split(separator: ",", omittingEmptySubsequences: false)
        guard fields.count == 6,
              let response = UInt8(fields[0]),
              let opCode = UInt8(fields[1]),
              let typeValue = UInt8(fields[2]),
              let statusValue = Int(fields[3]) else {
            return nil
        }
        if statusValue != PmdResponseCode.success.rawValue && data.count > 4 {
            return nil
        }
        let more = fields[4] == "1"
        let type = PmdMeasurementType(rawValue: typeValue) ?? PmdMeasurementType.unknown_type
        let errorCode = PmdResponseCode(rawValue: statusValue) ?? PmdResponseCode.unknown_error
        return (response, opCode, type, errorCode, more, Data(hexBytes: String(fields[5])))
    }
    #endif
}

enum PmdControlPointRuntimePlanner {
    static func responseFields(responseHex: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.pmdControlPointResponseFields(responseHex: responseHex)
        #else
        return nil
        #endif
    }

    static func activeMeasurementIosState(responseByte: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.pmdActiveMeasurementIosState(responseByte: responseByte)
        #else
        return nil
        #endif
    }

    static func measurementTypeName(id: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.pmdMeasurementTypeName(id: id)
        #else
        return nil
        #endif
    }

    static func recordingTypeBitField(name: String) -> UInt8? {
        #if canImport(PolarBleSdkShared)
        return UInt8(PolarIosSharedBridge.shared.pmdRecordingTypeBitField(name: name))
        #else
        return nil
        #endif
    }
}

public enum PmdResponseCode: Int {
    case success = 0
    case errorInvalidOpCode = 1
    case errorInvalidMeasurementType = 2
    case errorNotSupported = 3
    case errorInvalidLength = 4
    case errorInvalidParameter = 5
    case errorAlreadyInState = 6
    case errorInvalidResolution = 7
    case errorInvalidSampleRate = 8
    case errorInvalidRange = 9
    case errorInvalidMTU = 10
    case errorInvalidNumberOfChannels = 11
    case errorInvalidState = 12
    case errorDeviceInCharger = 13
    case errorDiskFull = 14
    case unknown_error = 0xffff
    
    var description : String {
        switch self {
        case .success: return "Success"
        case .errorInvalidOpCode: return "Invalid op code"
        case .errorInvalidMeasurementType: return "Invalid measurement type"
        case .errorNotSupported: return "Not supported"
        case .errorInvalidLength: return "Invalid length"
        case .errorInvalidParameter: return "Invalid parameter"
        case .errorAlreadyInState: return "Already in state"
        case .errorInvalidResolution: return "Invalid Resolution"
        case .errorInvalidSampleRate: return "Invalid Sample rate"
        case .errorInvalidRange: return "Invalid Range"
        case .errorInvalidMTU: return "Invalid MTU"
        case .errorInvalidNumberOfChannels: return "Invalid Number of channels"
        case .errorInvalidState: return "Invalid state"
        case .errorDeviceInCharger: return "Device in charger"
        case .errorDiskFull: return "Disk full"
        case .unknown_error: return "unknown error"
        }
    }
}

#if canImport(PolarBleSdkShared)
private extension Data {
    init(hexBytes: String) {
        var bytes = [UInt8]()
        var index = hexBytes.startIndex
        while index < hexBytes.endIndex {
            let nextIndex = hexBytes.index(index, offsetBy: 2)
            bytes.append(UInt8(hexBytes[index..<nextIndex], radix: 16)!)
            index = nextIndex
        }
        self.init(bytes)
    }

    var controlPointHexString: String {
        map { String(format: "%02x", $0) }.joined()
    }
}
#endif
