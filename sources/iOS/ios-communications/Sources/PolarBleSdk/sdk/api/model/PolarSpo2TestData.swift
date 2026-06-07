/// Copyright © 2026 Polar Electro Oy. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

/// Model for SPO2 test data retrieved from a Polar device.
public struct PolarSpo2TestData: Encodable {

    /// SpO2 classification
    public enum Spo2Class: Int, Encodable {
        case unknown = 0
        case veryLow = 1
        case low = 2
        case normal = 3
    }

    /// Trigger type that initiated the SPO2 test
    public enum Spo2TestTriggerType: Int, Encodable {
        case manual = 0
        case automatic = 1
    }

    /// SPO2 test completion status
    public enum Spo2TestStatus: Int, Encodable {
        case passed = 0
        case inconclusiveTooLowQualityInSamples = 1
        case inconclusiveTooLowOverallQuality = 2
        case inconclusiveTooManyMissingSamples = 3
    }

    /// Deviation of a measurement from the user's baseline
    public enum DeviationFromBaseline: Int, Encodable {
        case noBaseline = 0
        case belowUsual = 1
        case usual = 2
        case aboveUsual = 3
    }

    // MARK: - Properties

    /// Name / model of the recording device
    public let recordingDevice: String?

    /// Date when the test was performed
    public let date: Date

    /// Timezone offset from UTC in minutes at the time of the test
    public let timeZoneOffsetMinutes: Int?

    /// Status of the SPO2 test
    public let testStatus: Spo2TestStatus?

    /// Blood oxygen saturation percentage
    public let bloodOxygenPercent: Int?

    /// SpO2 classification result
    public let spo2Class: Spo2Class?

    /// SpO2 value deviation from the user's baseline
    public let spo2ValueDeviationFromBaseline: DeviationFromBaseline?

    /// Average SpO2 signal quality percentage during the test
    public let spo2QualityAveragePercent: Float?

    /// Average heart rate in BPM during the test
    public let averageHeartRateBpm: UInt?

    /// Heart rate variability in milliseconds during the test
    public let heartRateVariabilityMs: Float?

    /// SpO2 HRV deviation from the user's baseline
    public let spo2HrvDeviationFromBaseline: DeviationFromBaseline?

    /// Altitude in meters at the time of the test
    public let altitudeMeters: Float?

    /// What triggered the test
    public let triggerType: Spo2TestTriggerType?

    public init(
        recordingDevice: String? = nil,
        date: Date,
        timeZoneOffsetMinutes: Int? = nil,
        testStatus: Spo2TestStatus? = nil,
        bloodOxygenPercent: Int? = nil,
        spo2Class: Spo2Class? = nil,
        spo2ValueDeviationFromBaseline: DeviationFromBaseline? = nil,
        spo2QualityAveragePercent: Float? = nil,
        averageHeartRateBpm: UInt? = nil,
        heartRateVariabilityMs: Float? = nil,
        spo2HrvDeviationFromBaseline: DeviationFromBaseline? = nil,
        altitudeMeters: Float? = nil,
        triggerType: Spo2TestTriggerType? = nil
    ) {
        self.recordingDevice = recordingDevice
        self.date = date
        self.timeZoneOffsetMinutes = timeZoneOffsetMinutes
        self.testStatus = testStatus
        self.bloodOxygenPercent = bloodOxygenPercent
        self.spo2Class = spo2Class
        self.spo2ValueDeviationFromBaseline = spo2ValueDeviationFromBaseline
        self.spo2QualityAveragePercent = spo2QualityAveragePercent
        self.averageHeartRateBpm = averageHeartRateBpm
        self.heartRateVariabilityMs = heartRateVariabilityMs
        self.spo2HrvDeviationFromBaseline = spo2HrvDeviationFromBaseline
        self.altitudeMeters = altitudeMeters
        self.triggerType = triggerType
    }
}

extension PolarSpo2TestData.Spo2TestStatus {
    static func fromSharedOrRaw(value: Int) -> PolarSpo2TestData.Spo2TestStatus? {
        if let shared = PolarSpo2ModelSharedBridge.testStatusName(value: value) {
            return fromSharedName(shared)
        }
        return PolarSpo2TestData.Spo2TestStatus(rawValue: value)
    }

    static func fromSharedName(_ value: String) -> PolarSpo2TestData.Spo2TestStatus? {
        switch value {
        case "passed": return .passed
        case "inconclusiveTooLowQualityInSamples": return .inconclusiveTooLowQualityInSamples
        case "inconclusiveTooLowOverallQuality": return .inconclusiveTooLowOverallQuality
        case "inconclusiveTooManyMissingSamples": return .inconclusiveTooManyMissingSamples
        default: return nil
        }
    }
}

extension PolarSpo2TestData.Spo2Class {
    static func fromSharedOrRaw(value: Int) -> PolarSpo2TestData.Spo2Class? {
        if let shared = PolarSpo2ModelSharedBridge.spo2ClassName(value: value) {
            return fromSharedName(shared)
        }
        return PolarSpo2TestData.Spo2Class(rawValue: value)
    }

    static func fromSharedName(_ value: String) -> PolarSpo2TestData.Spo2Class? {
        switch value {
        case "unknown": return .unknown
        case "veryLow": return .veryLow
        case "low": return .low
        case "normal": return .normal
        default: return nil
        }
    }
}

extension PolarSpo2TestData.DeviationFromBaseline {
    static func fromSharedOrRaw(value: Int) -> PolarSpo2TestData.DeviationFromBaseline? {
        if let shared = PolarSpo2ModelSharedBridge.deviationFromBaselineName(value: value) {
            return fromSharedName(shared)
        }
        return PolarSpo2TestData.DeviationFromBaseline(rawValue: value)
    }

    static func fromSharedName(_ value: String) -> PolarSpo2TestData.DeviationFromBaseline? {
        switch value {
        case "noBaseline": return .noBaseline
        case "belowUsual": return .belowUsual
        case "usual": return .usual
        case "aboveUsual": return .aboveUsual
        default: return nil
        }
    }
}

extension PolarSpo2TestData.Spo2TestTriggerType {
    static func fromSharedOrRaw(value: Int) -> PolarSpo2TestData.Spo2TestTriggerType? {
        if let shared = PolarSpo2ModelSharedBridge.triggerTypeName(value: value) {
            return fromSharedName(shared)
        }
        return PolarSpo2TestData.Spo2TestTriggerType(rawValue: value)
    }

    static func fromSharedName(_ value: String) -> PolarSpo2TestData.Spo2TestTriggerType? {
        switch value {
        case "manual": return .manual
        case "automatic": return .automatic
        default: return nil
        }
    }
}

private enum PolarSpo2ModelSharedBridge {
    static func testStatusName(value: Int) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.spo2TestStatus(value: Int32(value))
        #else
        return nil
        #endif
    }

    static func spo2ClassName(value: Int) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.spo2Class(value: Int32(value))
        #else
        return nil
        #endif
    }

    static func deviationFromBaselineName(value: Int) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.spo2DeviationFromBaseline(value: Int32(value))
        #else
        return nil
        #endif
    }

    static func triggerTypeName(value: Int) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.spo2TriggerType(value: Int32(value))
        #else
        return nil
        #endif
    }
}
