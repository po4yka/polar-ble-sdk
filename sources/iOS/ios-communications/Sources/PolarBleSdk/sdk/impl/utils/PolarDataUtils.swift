//  Copyright © 2022 Polar. All rights reserved.


import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

internal class PolarDataUtils {
    
    static func mapToPmdClientMeasurementType(from polarDataType : PolarDeviceDataType) -> PmdMeasurementType {
        let sharedName = PolarPmdMeasurementRuntimePlanner.pmdMeasurementTypeName(forPublicDataTypeName: mapToSharedRuntimeFeatureName(from: polarDataType))
        return mapSharedMeasurementTypeNameToPmdClientMeasurementType(sharedName)
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
        case .derivedMeasurement:
            return "DERIVED_MEASUREMENT"
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

    private static func mapSharedMeasurementTypeNameToPmdClientMeasurementType(_ sharedName: String) -> PmdMeasurementType {
        switch sharedName {
        case "ECG": return PmdMeasurementType.ecg
        case "ACC": return PmdMeasurementType.acc
        case "PPG": return PmdMeasurementType.ppg
        case "PPI": return PmdMeasurementType.ppi
        case "GYRO": return PmdMeasurementType.gyro
        case "MAG": return PmdMeasurementType.mgn
        case "OFFLINE_HR": return PmdMeasurementType.offline_hr
        case "TEMPERATURE": return PmdMeasurementType.temperature
        case "SKIN_TEMP": return PmdMeasurementType.skinTemperature
        case "PRESSURE": return PmdMeasurementType.pressure
        case "DERIVED_MEASUREMENT": return PmdMeasurementType.derivedMeasurement
        default: return fallbackPmdClientMeasurementType(fromSharedRuntimeFeatureName: sharedName)
        }
    }

    private static func fallbackPmdClientMeasurementType(fromSharedRuntimeFeatureName sharedName: String) -> PmdMeasurementType {
        switch sharedName {
        case "HR": return PmdMeasurementType.offline_hr
        case "MAGNETOMETER": return PmdMeasurementType.mgn
        case "SKIN_TEMPERATURE": return PmdMeasurementType.skinTemperature
        default:
            BleLogger.error("Unknown shared measurement type name '\(sharedName)'; returning .unknown_type")
            return .unknown_type
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

    static func mapPmdClientDerivedAccDataToPolarDerivedAcc(_ derivedAccData: DerivedAccData) -> PolarDerivedAccData {
        let samples = derivedAccData.derivedSamples.map { sample -> PolarDerivedSample in
            let methodValues: [PolarDerivedMeasurementMethod: [Int32]] = Dictionary(
                uniqueKeysWithValues: sample.methodValues.compactMap { (id, values) -> (PolarDerivedMeasurementMethod, [Int32])? in
                    guard let method = PolarDerivedMeasurementMethod.fromId(id) else { return nil }
                    return (method, values)
                }
            )
            return PolarDerivedSample(
                timeStamp: sample.timeStamp,
                activeMethods: Set(methodValues.keys),
                methodValues: methodValues
            )
        }
        return PolarDerivedAccData(samples: samples)
    }

    static func mapPmdSettingsToPolarDerivedMeasurementSettingsGroup(
        _ pmdSetting: PmdSetting,
        requestedGroupId: Int = 0
    ) -> PolarDerivedMeasurementSettingsGroup {
        let groupId: Int
        if let raw = pmdSetting.settings[.derivedMeasurementSettingsGroupId]?.first {
            groupId = Int(raw)
        } else {
            groupId = requestedGroupId
        }

        let sourceTypes: Set<PolarDeviceDataType> = Set(
            (pmdSetting.settings[.sourceMeasurementType] ?? []).compactMap { id -> PolarDeviceDataType? in
                let pmdType = PmdMeasurementType.fromId(id: UInt8(id))
                return try? mapToPolarFeature(from: pmdType)
            }
        )

        let sourceSampleRates: Set<Int> = Set(
            (pmdSetting.settings[.sourceMeasurementSampleRate] ?? []).map { Int($0) }
        )

        let timeWindowOptions: Set<Int> = Set(
            (pmdSetting.settings[.derivedMeasurementTimeWindow] ?? []).map { Int($0) }
        )

        let supportedMethods: Set<PolarDerivedMeasurementMethod> = Set(
            (pmdSetting.settings[.derivedMeasurementMethod] ?? []).compactMap { PolarDerivedMeasurementMethod.fromId(Int($0)) }
        )

        return PolarDerivedMeasurementSettingsGroup(
            groupId: groupId,
            sourceTypes: sourceTypes,
            sourceSampleRates: sourceSampleRates,
            timeWindowOptions: timeWindowOptions,
            supportedMethods: supportedMethods
        )
    }

    static func mapPolarDerivedMeasurementSettingsToPmdSettings(_ settings: PolarDerivedMeasurementSettings) -> PmdSetting {
        let methodBitmask = settings.selectedMethods.reduce(0) { acc, m in acc | (1 << m.rawValue) }
        let pmdType = mapToPmdClientMeasurementType(from: settings.sourceMeasurementType)
        let selected: [PmdSetting.PmdSettingType: UInt32] = [
            .derivedMeasurementSettingsGroupId: UInt32(settings.groupId),
            .sourceMeasurementType: UInt32(pmdType.rawValue),
            .sourceMeasurementSampleRate: UInt32(settings.sourceSampleRate),
            .derivedMeasurementTimeWindow: UInt32(settings.timeWindowMs),
            .derivedMeasurementMethod: UInt32(methodBitmask)
        ]
        return PmdSetting(selected)
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

    static func pmdMeasurementTypeName(forPublicDataTypeName publicDataTypeName: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.pmdMeasurementTypeNameForPublicDataTypeName(publicDataTypeName: publicDataTypeName) ?? fallbackPmdMeasurementTypeName(forPublicDataTypeName: publicDataTypeName)
        #else
        return fallbackPmdMeasurementTypeName(forPublicDataTypeName: publicDataTypeName)
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

    private static func fallbackPmdMeasurementTypeName(forPublicDataTypeName publicDataTypeName: String) -> String {
        switch publicDataTypeName {
        case "ECG": return "ECG"
        case "ACC": return "ACC"
        case "PPG": return "PPG"
        case "PPI": return "PPI"
        case "GYRO": return "GYRO"
        case "MAGNETOMETER": return "MAG"
        case "HR": return "OFFLINE_HR"
        case "TEMPERATURE": return "TEMPERATURE"
        case "PRESSURE": return "PRESSURE"
        case "SKIN_TEMP": return "SKIN_TEMP"
        case "SKIN_TEMPERATURE": return "SKIN_TEMP"
        default: return publicDataTypeName
        }
    }
}

enum PolarFeatureAvailabilityRuntimePlanner {
    static func preconditionsMet(featureName: String, discoveredServiceNames: Set<String>, capabilityNames: Set<String>) -> Bool {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.featureAvailabilityPreconditionsMet(
            featureName: featureName,
            discoveredServiceNamesCsv: discoveredServiceNames.joined(separator: ","),
            capabilityNamesCsv: capabilityNames.joined(separator: ",")
        )
        #else
        let preconditions = fallbackPreconditions(featureName: featureName)
        return preconditions.services.isSubset(of: discoveredServiceNames) && preconditions.capabilities.isSubset(of: capabilityNames)
        #endif
    }

    private static func fallbackPreconditions(featureName: String) -> (services: Set<String>, capabilities: Set<String>) {
        switch normalizedFeatureName(featureName) {
        case "FEATURE_HR":
            return (["HR"], [])
        case "FEATURE_DEVICE_INFO":
            return (["DEVICE_INFO"], [])
        case "FEATURE_BATTERY_INFO":
            return (["BATTERY"], [])
        case "FEATURE_POLAR_ONLINE_STREAMING":
            return (["PMD"], [])
        case "FEATURE_POLAR_OFFLINE_RECORDING":
            return (["PMD", "PSFTP"], [])
        case "FEATURE_POLAR_DEVICE_TIME_SETUP":
            return (["PSFTP"], [])
        case "FEATURE_POLAR_SDK_MODE":
            return (["PMD"], [])
        case "FEATURE_POLAR_H10_EXERCISE_RECORDING":
            return (["PSFTP"], ["RECORDING"])
        case "FEATURE_POLAR_OFFLINE_EXERCISE_V2":
            return ([], ["H10_FILE_SYSTEM"])
        case "FEATURE_POLAR_FILE_TRANSFER":
            return (["PSFTP"], [])
        case "FEATURE_HTS":
            return (["HTS"], [])
        case "FEATURE_POLAR_LED_ANIMATION":
            return (["PMD", "PSFTP"], [])
        case "FEATURE_POLAR_FIRMWARE_UPDATE":
            return (["PSFTP"], ["FIRMWARE_UPDATE"])
        case "FEATURE_POLAR_ACTIVITY_DATA", "FEATURE_POLAR_SLEEP_DATA", "FEATURE_POLAR_TEMPERATURE_DATA":
            return (["PSFTP"], ["ACTIVITY_DATA"])
        case "FEATURE_POLAR_TRAINING_DATA", "FEATURE_POLAR_DEVICE_CONTROL", "FEATURE_POLAR_SPO2_TEST_DATA":
            return (["PSFTP"], [])
        case "FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE":
            return (["PFC"], [])
        case "FEATURE_WATCH_FACES_CONFIGURATION":
            return (["PSFTP"], ["NOT_SENSOR"])
        default:
            return ([], [])
        }
    }

    private static func normalizedFeatureName(_ featureName: String) -> String {
        var normalized = featureName.uppercased()
        if normalized.hasPrefix("FEATURE_") == false {
            normalized = "FEATURE_\(normalized)"
        }
        if normalized == "FEATURE_POLAR_WATCH_FACES_CONFIGURATION" {
            normalized = "FEATURE_WATCH_FACES_CONFIGURATION"
        }
        return normalized
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
