//  Copyright © 2023 Polar. All rights reserved.
import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

class OfflineRecordingUtils {
        
    static func mapOfflineRecordingFileNameToMeasurementType(fileName: String) throws -> PmdMeasurementType {
        #if canImport(PolarBleSdkShared)
        if let sharedType = PolarIosSharedBridge.shared.offlineRecordingMeasurementType(fileName: fileName) {
            return try mapSharedMeasurementType(sharedType)
        }
        throw BleGattException.gattDataError(description: "Unknown offline file \(fileName)")
        #else
        let fileNameWithoutExtension = fileName.components(separatedBy: ".").first!
        switch fileNameWithoutExtension.replacingOccurrences(of: "\\d+", with: "", options: .regularExpression) {
            case "ACC": return .acc
            case "GYRO": return .gyro
            case "MAG": return .mgn
            case "PPG": return .ppg
            case "PPI": return .ppi
            case "HR": return .offline_hr
            case "TEMP": return .temperature
            case "SKINTEMP": return .skinTemperature
            default: throw BleGattException.gattDataError(description: "Unknown offline file \(fileName)")
        }
        #endif
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
