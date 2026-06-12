//  Copyright © 2023 Polar. All rights reserved.
import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

class OfflineRecordingUtils {
        
    static func mapOfflineRecordingFileNameToMeasurementType(fileName: String) throws -> PmdMeasurementType {
        if let sharedType = OfflineRecordingRuntimePlanner.measurementTypeName(fileName: fileName) {
            return try mapSharedMeasurementType(sharedType)
        }
        throw BleGattException.gattDataError(description: "Unknown offline file \(fileName)")
    }

    private static func mapSharedMeasurementType(_ sharedType: String) throws -> PmdMeasurementType {
        switch sharedType {
            case "ACC": return .acc
            case "GYRO": return .gyro
            case "MAGNETOMETER": return .mgn
            case "PPG": return .ppg
            case "PPI": return .ppi
            case "OFFLINE_HR": return .offline_hr
            case "TEMPERATURE": return .temperature
            case "SKIN_TEMP": return .skinTemperature
            default: throw BleGattException.gattDataError(description: "Unknown offline measurement type \(sharedType)")
        }
    }
}

enum OfflineRecordingRuntimePlanner {
    static func measurementTypeName(fileName: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.offlineRecordingMeasurementType(fileName: fileName)
        #else
        let fileNameWithoutExtension = fileName.components(separatedBy: ".").first!
        switch fileNameWithoutExtension.replacingOccurrences(of: "\\d+", with: "", options: .regularExpression) {
            case "ACC": return "ACC"
            case "GYRO": return "GYRO"
            case "MAG": return "MAGNETOMETER"
            case "PPG": return "PPG"
            case "PPI": return "PPI"
            case "HR": return "OFFLINE_HR"
            case "TEMP": return "TEMPERATURE"
            case "SKINTEMP": return "SKIN_TEMP"
            default: return nil
        }
        #endif
    }
}
