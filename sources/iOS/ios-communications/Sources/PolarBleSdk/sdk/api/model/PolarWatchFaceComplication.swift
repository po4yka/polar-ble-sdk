// Copyright 2026 Polar Electro Oy. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

/// Identifiers for individual watch face complications.
public enum PolarWatchFaceComplication: CaseIterable {
    case alarm
    case altitude
    case activity
    case battery
    case breathingExercise
    case calories
    case compass
    case countdownTimer
    case date
    case daylight
    case ecg
    case empty
    case flashlight
    case heartRate
    case jumpTest
    case latestTraining
    case navigation
    case nightlyRecharge
    case polarLogo
    case secondsAnalog
    case secondsDigital
    case spo2
    case timer
    case userName
    case weather
    case weeklySummary

    /// The string complication identifier used in the device KVS.
    public var complicationId: String {
        switch self {
        case .alarm:             return "alarm-complication"
        case .altitude:          return "altitude-complication"
        case .activity:          return "activity-percentage-complication"
        case .battery:           return "battery-complication"
        case .breathingExercise: return "serene-complication"
        case .calories:          return "calories-complication"
        case .compass:           return "compass-complication"
        case .countdownTimer:    return "countdownTimer-complication"
        case .date:              return "date-complication"
        case .daylight:          return "daylight-complication"
        case .ecg:               return "ecg-complication"
        case .empty:             return ""
        case .flashlight:        return "flashlight-complication"
        case .heartRate:         return "heart-rate-complication"
        case .jumpTest:          return "jump-test-complication"
        case .latestTraining:    return "latest-training-complication"
        case .navigation:        return "navigation-complication"
        case .nightlyRecharge:   return "nightly-recharge-complication"
        case .polarLogo:         return "polar-logo-complication"
        case .secondsAnalog:     return "analog-seconds-complication"
        case .secondsDigital:    return "digital-seconds-complication"
        case .spo2:              return "spo2-complication"
        case .timer:             return "timer-complication"
        case .userName:          return "user-name-complication"
        case .weather:           return "weather-complication"
        case .weeklySummary:     return "weeklysummary-complication"
        }
    }

    /// Integer key derived from `complicationId` matching the Android hash code.
    /// Uses Java's `String.hashCode()` algorithm (signed 32-bit polynomial).
    public var id: Int32 {
        var h: Int32 = 0
        for scalar in complicationId.unicodeScalars {
            h = h &* 31 &+ Int32(bitPattern: scalar.value)
        }
        return h
    }

    /// Resolve a complication by its integer id (Java `String.hashCode()`).
    public static func fromId(_ id: Int32) -> PolarWatchFaceComplication? {
        if let sharedName = PolarWatchFaceRuntimePlanner.complicationName(id: id),
           let sharedComplication = PolarWatchFaceComplication(sharedName: sharedName) {
            return sharedComplication
        }
        return allCases.first { $0.id == id }
    }
}

/// Watch face configuration containing the ordered list of enabled complications.
public struct PolarWatchFaceConfig {
    /// Ordered list of complications currently active on the watch face.
    public let enabledComplications: [PolarWatchFaceComplication]

    public init(enabledComplications: [PolarWatchFaceComplication]) {
        self.enabledComplications = enabledComplications
    }
}

enum PolarWatchFaceRuntimePlanner {
    static func complicationName(id: Int32) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.watchFaceComplicationName(id: id)
        #else
        return nil
        #endif
    }

    static func fieldsCsv(
        timeStyleId: UInt16,
        complicationLayoutId: UInt16,
        backgroundStyleId: UInt16,
        accentColor: UInt32,
        complicationIds: [Int32],
        fontfaceId: UInt8
    ) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.watchFaceFieldsCsv(
            timeStyleId: Int32(timeStyleId),
            complicationLayoutId: Int32(complicationLayoutId),
            backgroundStyleId: Int32(backgroundStyleId),
            accentColor: Int64(accentColor),
            complicationIdsCsv: complicationIds.map(String.init).joined(separator: ","),
            fontfaceId: Int32(fontfaceId)
        )
        #else
        return nil
        #endif
    }
}

private extension PolarWatchFaceComplication {
    init?(sharedName: String) {
        switch sharedName {
        case "ALARM": self = .alarm
        case "ALTITUDE": self = .altitude
        case "ACTIVITY": self = .activity
        case "BATTERY": self = .battery
        case "BREATHING_EXERCISE": self = .breathingExercise
        case "CALORIES": self = .calories
        case "COMPASS": self = .compass
        case "COUNTDOWN_TIMER": self = .countdownTimer
        case "DATE": self = .date
        case "DAYLIGHT": self = .daylight
        case "ECG": self = .ecg
        case "EMPTY": self = .empty
        case "FLASHLIGHT": self = .flashlight
        case "HEART_RATE": self = .heartRate
        case "JUMP_TEST": self = .jumpTest
        case "LATEST_TRAINING": self = .latestTraining
        case "NAVIGATION": self = .navigation
        case "NIGHTLY_RECHARGE": self = .nightlyRecharge
        case "POLAR_LOGO": self = .polarLogo
        case "SECONDS_ANALOG": self = .secondsAnalog
        case "SECONDS_DIGITAL": self = .secondsDigital
        case "SPO2": self = .spo2
        case "TIMER": self = .timer
        case "USER_NAME": self = .userName
        case "WEATHER": self = .weather
        case "WEEKLY_SUMMARY": self = .weeklySummary
        default: return nil
        }
    }
}
