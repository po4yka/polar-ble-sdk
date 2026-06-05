//  Copyright © 2026 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let SPO2_TEST_DIRECTORY = "SPO2TEST/"
private let SPO2_TEST_PROTO = "SPO2TRES.BPB"
private let dateFormat: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    return formatter
}()
private let TAG = "PolarTestUtils"

internal class PolarTestUtils {

    static func readSpo2TestFromDayDirectory(client: BlePsFtpClient, date: Date) -> AsyncThrowingStream<PolarSpo2TestData, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                BleLogger.trace(TAG, "readSpo2TestFromDayDirectory: \(date)")
                let spo2TestDirPath = "\(ARABICA_USER_ROOT_FOLDER)\(dateFormat.string(from: date))/\(SPO2_TEST_DIRECTORY)"
                let listOperation = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = spo2TestDirPath }
                do {
                    let response = try await client.request(try listOperation.serializedBytes())
                    let dir = try Protocol_PbPFtpDirectory(serializedBytes: Data(response))
                    let timeSubDirs = dir.entries.filter { $0.name.hasSuffix("/") }
                    for subDir in timeSubDirs {
                        let timeDirName = String(subDir.name.dropLast())
                        let filePath = "\(spo2TestDirPath)\(subDir.name)\(SPO2_TEST_PROTO)"
                        let fileOperation = Protocol_PbPFtpOperation.with { $0.command = .get; $0.path = filePath }
                        do {
                            let fileResponse = try await client.request(try fileOperation.serializedBytes())
                            let proto = try Data_PbSpo2TestResult(serializedBytes: Data(fileResponse))
                            continuation.yield(fromProto(proto: proto, date: date, timeDirName: timeDirName))
                        } catch {
                            BleLogger.trace(TAG, "No SPO2 test proto at \(filePath): \(error)")
                        }
                    }
                    continuation.finish()
                } catch {
                    BleLogger.trace(TAG, "SPO2TEST directory listing failed for \(date): \(error)")
                    continuation.finish()
                }
            }
        }
    }

    static func fromProto(proto: Data_PbSpo2TestResult, date: Date, timeDirName: String) -> PolarSpo2TestData {
        let tzOffsetMinutes = proto.timeZoneOffset != 0 ? Int(proto.timeZoneOffset) : nil
        let testDate = dateFromFolderNames(dayDate: date, timeDirName: timeDirName, tzOffsetMinutes: tzOffsetMinutes)
            ?? (proto.testTime != 0 ? Date(timeIntervalSince1970: TimeInterval(proto.testTime) / 1000.0) : nil)
            ?? date
        return PolarSpo2TestData(
            recordingDevice: proto.recordingDevice.isEmpty ? nil : proto.recordingDevice,
            date: testDate, timeZoneOffsetMinutes: tzOffsetMinutes,
            testStatus: PolarSpo2TestData.Spo2TestStatus.fromSharedOrRaw(value: proto.testStatus.rawValue),
            bloodOxygenPercent: proto.hasBloodOxygenPercent ? Int(proto.bloodOxygenPercent) : nil,
            spo2Class: proto.hasSpo2Class ? PolarSpo2TestData.Spo2Class.fromSharedOrRaw(value: proto.spo2Class.rawValue) : nil,
            spo2ValueDeviationFromBaseline: proto.hasSpo2ValueDeviationFromBaseline ? PolarSpo2TestData.DeviationFromBaseline.fromSharedOrRaw(value: proto.spo2ValueDeviationFromBaseline.rawValue) : nil,
            spo2QualityAveragePercent: proto.hasSpo2QualityAveragePercent ? proto.spo2QualityAveragePercent : nil,
            averageHeartRateBpm: proto.hasAverageHeartRateBpm ? UInt(proto.averageHeartRateBpm) : nil,
            heartRateVariabilityMs: proto.hasHeartRateVariabilityMs ? proto.heartRateVariabilityMs : nil,
            spo2HrvDeviationFromBaseline: proto.hasSpo2HrvDeviationFromBaseline ? PolarSpo2TestData.DeviationFromBaseline.fromSharedOrRaw(value: proto.spo2HrvDeviationFromBaseline.rawValue) : nil,
            altitudeMeters: proto.hasAltitudeMeters ? proto.altitudeMeters : nil,
            triggerType: proto.hasTriggerType ? PolarSpo2TestData.Spo2TestTriggerType.fromSharedOrRaw(value: proto.triggerType.rawValue) : nil
        )
    }

    static func dateFromFolderNames(dayDate: Date, timeDirName: String, tzOffsetMinutes: Int?) -> Date? {
        let deviceTz = tzOffsetMinutes.flatMap { TimeZone(secondsFromGMT: $0 * 60) } ?? TimeZone.current
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = deviceTz
        let dayComponents = cal.dateComponents([.year, .month, .day], from: dayDate)
        guard timeDirName.count == 6,
              let hh = Int(timeDirName.prefix(2)),
              let mm = Int(timeDirName.dropFirst(2).prefix(2)),
              let ss = Int(timeDirName.suffix(2)) else { return nil }
        var components = DateComponents()
        components.timeZone = deviceTz
        components.year = dayComponents.year; components.month = dayComponents.month; components.day = dayComponents.day
        components.hour = hh; components.minute = mm; components.second = ss
        return cal.date(from: components)
    }
}

private extension PolarSpo2TestData.Spo2TestStatus {
    static func fromSharedOrRaw(value: Int) -> PolarSpo2TestData.Spo2TestStatus? {
        #if canImport(PolarBleSdkShared)
        if let shared = PolarIosSharedBridge.shared.spo2TestStatus(value: Int32(value)) {
            return fromSharedName(shared)
        }
        #endif
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

private extension PolarSpo2TestData.Spo2Class {
    static func fromSharedOrRaw(value: Int) -> PolarSpo2TestData.Spo2Class? {
        #if canImport(PolarBleSdkShared)
        if let shared = PolarIosSharedBridge.shared.spo2Class(value: Int32(value)) {
            return fromSharedName(shared)
        }
        #endif
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

private extension PolarSpo2TestData.DeviationFromBaseline {
    static func fromSharedOrRaw(value: Int) -> PolarSpo2TestData.DeviationFromBaseline? {
        #if canImport(PolarBleSdkShared)
        if let shared = PolarIosSharedBridge.shared.spo2DeviationFromBaseline(value: Int32(value)) {
            return fromSharedName(shared)
        }
        #endif
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

private extension PolarSpo2TestData.Spo2TestTriggerType {
    static func fromSharedOrRaw(value: Int) -> PolarSpo2TestData.Spo2TestTriggerType? {
        #if canImport(PolarBleSdkShared)
        if let shared = PolarIosSharedBridge.shared.spo2TriggerType(value: Int32(value)) {
            return fromSharedName(shared)
        }
        #endif
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
