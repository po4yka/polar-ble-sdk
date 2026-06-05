///  Copyright © 2024 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public struct Polar247HrSamplesData: Codable {
    
    public let date: DateComponents
    public let samples: [Polar247HrSample]
    
    public struct Polar247HrSample: Codable {

        public var time: DateComponents
        public var hrSamples: [UInt32]
        public var triggerType: AutomaticSampleTriggerType?
    }

    public enum AutomaticSampleTriggerType: String, Codable {
        case highActivity = "highActivity"
        case lowActivity = "lowActivity"
        case timed = "timed"
        case manual = "manual"
        
        init?(stringValue: String) {
            switch stringValue {
            case "highActivity":
                self = .highActivity
            case "lowActivity":
                self = .lowActivity
            case "timed":
                self = .timed
            case "manual":
                self = .manual
            default:
                return nil
            }
        }

        var stringValue: String {
            return self.rawValue
        }

        static func getByValue(value: Data_PbMeasTriggerType) -> AutomaticSampleTriggerType {
            #if canImport(PolarBleSdkShared)
            if let sharedName = PolarIosSharedBridge.shared.automaticHrTriggerName(value: Int32(value.rawValue)),
               let sharedTrigger = AutomaticSampleTriggerType(sharedName: sharedName) {
                return sharedTrigger
            }
            #endif

            switch value {
                case .triggerTypeHighActivity: return .highActivity
                case .triggerTypeLowActivity: return .lowActivity
                case .triggerTypeTimed: return .timed
                case .triggerTypeManual: return .manual
            }
        }
    }
    
    static func fromPbHrDataSamples(samples: [Data_PbAutomaticHeartRateSamples]) throws -> [Polar247HrSample] {

        var hrSamples = [Polar247HrSample]()
        for sample in samples {
            do {
                hrSamples.append(try .init(time: PolarTimeUtils.pbTimeToDateComponents(pbTime: sample.time), hrSamples: sample.heartRate, triggerType: AutomaticSampleTriggerType.getByValue(value: sample.triggerType)))
            } catch let error {
                BleLogger.error("Polar247HrSamplesData.fromPbHrDataSamples failed to parse time from \(sample.time) with error: \(error)")
                throw error
            }
        }
        return hrSamples
    }
}

#if canImport(PolarBleSdkShared)
private extension Polar247HrSamplesData.AutomaticSampleTriggerType {
    init?(sharedName: String) {
        switch sharedName {
        case "TRIGGER_TYPE_HIGH_ACTIVITY": self = .highActivity
        case "TRIGGER_TYPE_LOW_ACTIVITY": self = .lowActivity
        case "TRIGGER_TYPE_TIMED": self = .timed
        case "TRIGGER_TYPE_MANUAL": self = .manual
        default: return nil
        }
    }
}
#endif
