//  Copyright © 2022 Polar. All rights reserved.


import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

internal class PolarDataUtils {
    
    static func mapToPmdClientMeasurementType(from polarDataType : PolarDeviceDataType) -> PmdMeasurementType {
        switch(polarDataType) {
        case .ecg:
            return PmdMeasurementType.ecg
        case .acc:
            return PmdMeasurementType.acc
        case .ppg:
            return PmdMeasurementType.ppg
        case .ppi:
            return PmdMeasurementType.ppi
        case .gyro:
            return PmdMeasurementType.gyro
        case .magnetometer:
            return PmdMeasurementType.mgn
        case .hr:
            return PmdMeasurementType.offline_hr
        case .temperature:
            return PmdMeasurementType.temperature
        case .skinTemperature:
            return PmdMeasurementType.skinTemperature
        case .pressure:
            return PmdMeasurementType.pressure
        }
    }

    static func mapToSharedRuntimeName(from pmdMeasurementType: PmdMeasurementType) -> String {
        switch pmdMeasurementType {
        case .ecg:
            return "ECG"
        case .ppg:
            return "PPG"
        case .acc:
            return "ACC"
        case .ppi:
            return "PPI"
        case .gyro:
            return "GYRO"
        case .mgn:
            return "MAG"
        case .skinTemperature:
            return "SKIN_TEMP"
        case .sdkMode:
            return "SDK_MODE"
        case .location:
            return "LOCATION"
        case .pressure:
            return "PRESSURE"
        case .temperature:
            return "TEMPERATURE"
        case .offline_recording:
            return "OFFLINE_RECORDING"
        case .offline_hr:
            return "OFFLINE_HR"
        case .unknown_type:
            return "UNKNOWN"
        }
    }

    static func mapToSharedRuntimeFeatureName(from polarDataType: PolarDeviceDataType) -> String {
        switch polarDataType {
        case .ecg:
            return "ECG"
        case .acc:
            return "ACC"
        case .ppg:
            return "PPG"
        case .ppi:
            return "PPI"
        case .gyro:
            return "GYRO"
        case .magnetometer:
            return "MAG"
        case .hr:
            return "HR"
        case .temperature:
            return "TEMPERATURE"
        case .pressure:
            return "PRESSURE"
        case .skinTemperature:
            return "SKIN_TEMP"
        }
    }
    
    static func mapToPolarFeature(from pmdMeasurementType : PmdMeasurementType) throws -> PolarDeviceDataType {
        if let sharedName = PolarPmdMeasurementRuntimePlanner.measurementTypeName(id: Int(pmdMeasurementType.rawValue)) {
            switch sharedName {
            case "ECG": return PolarDeviceDataType.ecg
            case "PPG": return PolarDeviceDataType.ppg
            case "ACC": return PolarDeviceDataType.acc
            case "PPI": return PolarDeviceDataType.ppi
            case "GYRO": return PolarDeviceDataType.gyro
            case "MAG": return PolarDeviceDataType.magnetometer
            case "OFFLINE_HR": return PolarDeviceDataType.hr
            case "TEMPERATURE": return PolarDeviceDataType.temperature
            case "PRESSURE": return PolarDeviceDataType.pressure
            case "SKIN_TEMP": return PolarDeviceDataType.skinTemperature
            default: break
            }
        }
        switch(pmdMeasurementType) {
        case .ecg:
            return PolarDeviceDataType.ecg
        case .ppg:
            return PolarDeviceDataType.ppg
        case .acc:
            return PolarDeviceDataType.acc
        case .ppi:
            return PolarDeviceDataType.ppi
        case .gyro:
            return PolarDeviceDataType.gyro
        case .mgn:
            return PolarDeviceDataType.magnetometer
        case .offline_hr:
            return PolarDeviceDataType.hr
        case .temperature:
            return PolarDeviceDataType.temperature
        case .pressure:
            return PolarDeviceDataType.pressure
        case .skinTemperature:
            return PolarDeviceDataType.skinTemperature
        default:
            throw PolarErrors.polarBleSdkInternalException(description: "Error when map measurement type \(pmdMeasurementType) to Polar feature" )
        }
    }
    
    static func mapToPmdSecret(from polarSecret: PolarRecordingSecret) throws -> PmdSecret {
        return try PmdSecret(
            strategy: PmdSecret.SecurityStrategy.aes128,
            key: polarSecret.key
        )
    }
    
    static func mapToPmdOfflineTrigger(from polarTrigger: PolarOfflineRecordingTrigger) throws -> PmdOfflineTrigger {
        let pmdTriggerMode = mapToPmdOfflineTriggerMode(from: polarTrigger.triggerMode)
        var pmdTriggers = [PmdMeasurementType : (PmdOfflineRecTriggerStatus, PmdSetting?)]()
        
        for trigger in polarTrigger.triggerFeatures {
            let pmdMeasurementType = mapToPmdClientMeasurementType(from: trigger.key)
            let pmdSettings = trigger.value?.map2PmdSetting() ?? nil
            pmdTriggers[pmdMeasurementType] = (PmdOfflineRecTriggerStatus.enabled , pmdSettings)
        }
        return PmdOfflineTrigger(triggerMode: pmdTriggerMode, triggers: pmdTriggers)
    }
    
    private static func mapToPmdOfflineTriggerMode(from polarTriggerMode: PolarOfflineRecordingTriggerMode) -> PmdOfflineRecTriggerMode {
        switch(polarTriggerMode) {
        case .triggerDisabled:
            return PmdOfflineRecTriggerMode.disabled
        case .triggerSystemStart:
            return PmdOfflineRecTriggerMode.systemStart
        case .triggerExerciseStart:
            return PmdOfflineRecTriggerMode.exerciseStart
        }
    }
    
    static func mapToPolarOfflineTrigger(from pmdTrigger: PmdOfflineTrigger) throws -> PolarOfflineRecordingTrigger {
        let triggerMode = mapToPolarOfflineTriggerMode(from: pmdTrigger.triggerMode)
        var polarTriggerSettings = [PolarDeviceDataType: PolarSensorSetting?]()

        for (pmdMeasurementType, triggerStatus) in pmdTrigger.triggers {
            let polarDataType = try PolarDataUtils.mapToPolarFeature(from: pmdMeasurementType)
            if triggerStatus.status == .enabled {
                // Map only the enabled
                let polarSettings = triggerStatus.setting.flatMap {
                    $0.mapToPolarSettings()
                }
                polarTriggerSettings[polarDataType] = polarSettings
            }
        }
        return PolarOfflineRecordingTrigger(
            triggerMode: triggerMode,
            triggerFeatures: polarTriggerSettings
        )
    }
    
    private static func mapToPolarOfflineTriggerMode(from pmdTriggerMode: PmdOfflineRecTriggerMode) -> PolarOfflineRecordingTriggerMode {
        switch pmdTriggerMode {
        case .disabled:
            return .triggerDisabled
        case .systemStart:
            return .triggerSystemStart
        case .exerciseStart:
            return .triggerExerciseStart
        }
    }
}

enum PolarPmdMeasurementRuntimePlanner {
    static func measurementTypeName(id: Int) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.pmdMeasurementTypeName(id: Int32(id))
        #else
        return nil
        #endif
    }

    static func availableOfflineRecordingDataTypes(from pmdMeasurementTypes: Set<PmdMeasurementType>) -> Set<PolarDeviceDataType> {
        let measurementNames = pmdMeasurementTypes.map { PolarDataUtils.mapToSharedRuntimeName(from: $0) }
        #if canImport(PolarBleSdkShared)
        let csv = PolarIosSharedBridge.shared.availableOfflineRecordingDataTypesCsv(pmdMeasurementTypesCsv: measurementNames.joined(separator: ","), includeLocation: false, includePressure: false)
        return polarDataTypes(fromCsv: csv)
        #else
        return polarDataTypes(fromNames: offlineDataTypeNames(from: Set(measurementNames), includeLocation: false, includePressure: false))
        #endif
    }

    static func availableOnlineStreamDataTypes(from pmdMeasurementTypes: Set<PmdMeasurementType>, hasHrService: Bool) -> Set<PolarDeviceDataType> {
        let measurementNames = pmdMeasurementTypes.map { PolarDataUtils.mapToSharedRuntimeName(from: $0) }
        #if canImport(PolarBleSdkShared)
        let csv = PolarIosSharedBridge.shared.availableOnlineStreamDataTypesCsv(pmdMeasurementTypesCsv: measurementNames.joined(separator: ","), hasHrService: hasHrService, includeLocation: false, includePressure: true)
        return polarDataTypes(fromCsv: csv)
        #else
        return polarDataTypes(fromNames: onlineDataTypeNames(from: Set(measurementNames), hasHrService: hasHrService, includeLocation: false, includePressure: true))
        #endif
    }

    static func availableHrServiceDataTypes(hasHrService: Bool) -> Set<PolarDeviceDataType> {
        #if canImport(PolarBleSdkShared)
        return polarDataTypes(fromCsv: PolarIosSharedBridge.shared.availableHrServiceDataTypesCsv(hasHrService: hasHrService))
        #else
        return hasHrService ? [.hr] : []
        #endif
    }

    private static func polarDataTypes(fromCsv csv: String) -> Set<PolarDeviceDataType> {
        return polarDataTypes(fromNames: Set(csv.split(separator: ",").map(String.init)))
    }

    private static func polarDataTypes(fromNames names: Set<String>) -> Set<PolarDeviceDataType> {
        var result: Set<PolarDeviceDataType> = Set()
        for name in names {
            switch name {
            case "ECG": result.insert(.ecg)
            case "ACC": result.insert(.acc)
            case "PPG": result.insert(.ppg)
            case "PPI": result.insert(.ppi)
            case "GYRO": result.insert(.gyro)
            case "MAGNETOMETER": result.insert(.magnetometer)
            case "HR": result.insert(.hr)
            case "TEMPERATURE": result.insert(.temperature)
            case "PRESSURE": result.insert(.pressure)
            case "SKIN_TEMPERATURE": result.insert(.skinTemperature)
            default: break
            }
        }
        return result
    }

    private static func offlineDataTypeNames(from pmdMeasurementTypeNames: Set<String>, includeLocation: Bool, includePressure: Bool) -> Set<String> {
        var names = pmdDataTypeNames(from: pmdMeasurementTypeNames, includeLocation: includeLocation, includePressure: includePressure)
        if pmdMeasurementTypeNames.contains("OFFLINE_HR") {
            names.insert("HR")
        }
        return names
    }

    private static func onlineDataTypeNames(from pmdMeasurementTypeNames: Set<String>, hasHrService: Bool, includeLocation: Bool, includePressure: Bool) -> Set<String> {
        var names = pmdDataTypeNames(from: pmdMeasurementTypeNames, includeLocation: includeLocation, includePressure: includePressure)
        if hasHrService {
            names.insert("HR")
        }
        return names
    }

    private static func pmdDataTypeNames(from pmdMeasurementTypeNames: Set<String>, includeLocation: Bool, includePressure: Bool) -> Set<String> {
        var result: Set<String> = Set()
        let mappings = [
            ("ECG", "ECG"),
            ("ACC", "ACC"),
            ("PPG", "PPG"),
            ("PPI", "PPI"),
            ("GYRO", "GYRO"),
            ("MAG", "MAGNETOMETER"),
            ("PRESSURE", "PRESSURE"),
            ("LOCATION", "LOCATION"),
            ("TEMPERATURE", "TEMPERATURE"),
            ("SKIN_TEMP", "SKIN_TEMPERATURE")
        ]
        for (measurementType, dataType) in mappings {
            if !includeLocation && dataType == "LOCATION" {
                continue
            }
            if !includePressure && dataType == "PRESSURE" {
                continue
            }
            if pmdMeasurementTypeNames.contains(measurementType) {
                result.insert(dataType)
            }
        }
        return result
    }
}

internal extension PmdSetting {
    func mapToPolarSettings() -> PolarSensorSetting {
        var settings: [PolarSensorSetting.SettingType : Set<UInt32>] = [:]
        let source: [PmdSetting.PmdSettingType: Set<UInt32>] = self.settings.isEmpty
            ? self.selected.mapValues { Set([$0]) }
            : self.settings
        for (key, value) in source {
            switch(key) {
            case .sampleRate:
                settings[PolarSensorSetting.SettingType.sampleRate] = value
            case .resolution:
                settings[PolarSensorSetting.SettingType.resolution] = value
            case .range:
                settings[PolarSensorSetting.SettingType.range] = value
            case .rangeMilliUnit:
                settings[PolarSensorSetting.SettingType.rangeMilliunit] = value
            case .channels:
                settings[PolarSensorSetting.SettingType.channels] = value
            default:
                //nop
                break
            }
        }
        return PolarSensorSetting(settings)
    }
}
