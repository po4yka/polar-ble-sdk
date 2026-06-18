import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public enum PmdMeasurementType: UInt8, CaseIterable, Sendable {
    case ecg = 0
    case ppg = 1
    case acc = 2
    case ppi = 3
    case gyro = 5
    case mgn = 6
    case skinTemperature = 7
    case sdkMode = 9
    case location = 10
    case pressure = 11
    case temperature = 12
    case offline_recording = 13
    case offline_hr = 14
    case derivedMeasurement = 15
    case unknown_type = 0x3f
    
    private static let MEASUREMENT_BIT_MASK: UInt8 = 0x3F
    
    func isRawMeasurementDataType() -> Bool {
        switch(self) {
        case .sdkMode, .offline_recording, .derivedMeasurement, .unknown_type:
            return false
        default :
            return true
        }
    }
    
    static func fromId(id: UInt8) -> PmdMeasurementType {
        #if canImport(PolarBleSdkShared)
        if let sharedName = PmdControlPointRuntimePlanner.measurementTypeName(id: Int32(id)),
           let sharedType = PmdMeasurementType(sharedName: sharedName) {
            return sharedType
        }
        #endif
        for type in PmdMeasurementType.allCases {
            if (type.rawValue == (id & PmdMeasurementType.MEASUREMENT_BIT_MASK)) {
                return type
            }
        }
        return .unknown_type
    }
    
    static func fromByteArray(_ data: Data) -> Set<PmdMeasurementType> {
        var measurementTypes:Set<PmdMeasurementType> = []
        if (data[1] & 0x01 != 0) {
            measurementTypes.insert(PmdMeasurementType.ecg)
        }
        if (data[1] & 0x02 != 0) {
            measurementTypes.insert(PmdMeasurementType.ppg)
        }
        if (data[1] & 0x04 != 0) {
            measurementTypes.insert(PmdMeasurementType.acc)
        }
        if (data[1] & 0x08 != 0) {
            measurementTypes.insert(PmdMeasurementType.ppi)
        }
        if (data[1] & 0x20 != 0) {
            measurementTypes.insert(PmdMeasurementType.gyro)
        }
        if (data[1] & 0x40 != 0) {
            measurementTypes.insert(PmdMeasurementType.mgn)
        }
        if (data[1] & 0x80 != 0) {
            measurementTypes.insert(PmdMeasurementType.skinTemperature)
        }
        if (data[2] & 0x02 != 0) {
            measurementTypes.insert(PmdMeasurementType.sdkMode)
        }
        if (data[2] & 0x04 != 0) {
            measurementTypes.insert(PmdMeasurementType.location)
        }
        if (data[2] & 0x08 != 0) {
            measurementTypes.insert(PmdMeasurementType.pressure)
        }
        if (data[2] & 0x10 != 0) {
            measurementTypes.insert(PmdMeasurementType.temperature)
        }
        if (data[2] & 0x20 != 0) {
            measurementTypes.insert(PmdMeasurementType.offline_recording)
        }
        if (data[2] & 0x40 != 0) {
            measurementTypes.insert(PmdMeasurementType.offline_hr)
        }
        
        return measurementTypes
    }
}

#if canImport(PolarBleSdkShared)
private extension PmdMeasurementType {
    init?(sharedName: String) {
        switch sharedName {
        case "ECG":
            self = .ecg
        case "PPG":
            self = .ppg
        case "ACC":
            self = .acc
        case "PPI":
            self = .ppi
        case "GYRO":
            self = .gyro
        case "MAG":
            self = .mgn
        case "SKIN_TEMP":
            self = .skinTemperature
        case "SDK_MODE":
            self = .sdkMode
        case "LOCATION":
            self = .location
        case "PRESSURE":
            self = .pressure
        case "TEMPERATURE":
            self = .temperature
        case "OFFLINE_RECORDING":
            self = .offline_recording
        case "OFFLINE_HR":
            self = .offline_hr
        case "DERIVED_MEASUREMENT":
            self = .derivedMeasurement
        default:
            return nil
        }
    }
}
#endif
