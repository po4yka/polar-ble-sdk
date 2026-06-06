//  Copyright © 2026 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

private let dateFormat: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    return formatter
}()
private let TAG = "PolarTestUtils"

internal class PolarTestUtils {
    static func spo2TestDirectoryReadOperation(date: Date) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        return spo2TestReadOperation(id: "spo2-test-read-directory", path: spo2TestDirectoryPath(day: dateFormat.string(from: date)))
    }

    static func spo2TestFileReadOperation(directoryPath: String, subDirectoryName: String) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        return spo2TestReadOperation(id: "spo2-test-read-file", path: spo2TestResultPath(directoryPath: directoryPath, subDirectoryName: subDirectoryName))
    }

    private static func spo2TestDirectoryPath(day: String) -> String {
        if let plannedPath = PolarSpo2RuntimePlanner.testDirectoryPath(day: day) {
            return plannedPath
        }
        return "/U/0/\(day)/SPO2TEST/"
    }

    private static func spo2TestResultPath(directoryPath: String, subDirectoryName: String) -> String {
        if let plannedPath = PolarSpo2RuntimePlanner.testResultPath(directoryPath: directoryPath, subDirectoryName: subDirectoryName) {
            return plannedPath
        }
        return "\(directoryPath)\(subDirectoryName)SPO2TRES.BPB"
    }

    private static func spo2TestReadOperation(id: String, path: String) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        if let plannedOperation = PolarRuntimePlanner.fileFacadeOperation(id: id, command: "GET", path: path) {
            return plannedOperation
        }
        return (.get, path)
    }

    static func readSpo2TestFromDayDirectory(client: BlePsFtpClient, date: Date) -> AsyncThrowingStream<PolarSpo2TestData, Error> {
        return AsyncThrowingStream { continuation in
            Task {
                BleLogger.trace(TAG, "readSpo2TestFromDayDirectory: \(date)")
                let plannedListOperation = spo2TestDirectoryReadOperation(date: date)
                let spo2TestDirPath = plannedListOperation.path
                do {
                    let response = try await client.request(try PolarRuntimePlanner.fileOperationBytes(plannedListOperation))
                    let dir = try Protocol_PbPFtpDirectory(serializedBytes: Data(response))
                    let timeSubDirs = dir.entries.filter { $0.name.hasSuffix("/") }
                    for subDir in timeSubDirs {
                        let timeDirName = String(subDir.name.dropLast())
                        let plannedFileOperation = spo2TestFileReadOperation(directoryPath: spo2TestDirPath, subDirectoryName: subDir.name)
                        let filePath = plannedFileOperation.path
                        do {
                            let fileResponse = try await client.request(try PolarRuntimePlanner.fileOperationBytes(plannedFileOperation))
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
        if let projection = Spo2SharedProjection.fromShared(proto: proto, date: date, timeDirName: timeDirName, timeZoneOffsetMinutes: tzOffsetMinutes) {
            return PolarSpo2TestData(
                recordingDevice: projection.recordingDevice,
                date: testDate,
                timeZoneOffsetMinutes: tzOffsetMinutes,
                testStatus: projection.testStatus.flatMap(PolarSpo2TestData.Spo2TestStatus.fromSharedName),
                bloodOxygenPercent: projection.bloodOxygenPercent,
                spo2Class: projection.spo2Class.flatMap(PolarSpo2TestData.Spo2Class.fromSharedName),
                spo2ValueDeviationFromBaseline: projection.spo2ValueDeviationFromBaseline.flatMap(PolarSpo2TestData.DeviationFromBaseline.fromSharedName),
                spo2QualityAveragePercent: projection.spo2QualityAveragePercent,
                averageHeartRateBpm: projection.averageHeartRateBpm,
                heartRateVariabilityMs: projection.heartRateVariabilityMs,
                spo2HrvDeviationFromBaseline: projection.spo2HrvDeviationFromBaseline.flatMap(PolarSpo2TestData.DeviationFromBaseline.fromSharedName),
                altitudeMeters: projection.altitudeMeters,
                triggerType: projection.triggerType.flatMap(PolarSpo2TestData.Spo2TestTriggerType.fromSharedName)
            )
        }
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
        guard let parts = sharedTimeDirectoryParts(timeDirName: timeDirName) else { return nil }
        var components = DateComponents()
        components.timeZone = deviceTz
        components.year = dayComponents.year; components.month = dayComponents.month; components.day = dayComponents.day
        components.hour = parts.hour; components.minute = parts.minute; components.second = parts.second
        return cal.date(from: components)
    }

    private static func sharedTimeDirectoryParts(timeDirName: String) -> (hour: Int, minute: Int, second: Int)? {
        if let parts = PolarSpo2RuntimePlanner.timeDirectoryParts(timeDirName: timeDirName) {
            return parts
        }
        guard timeDirName.count == 6,
              let hour = Int(timeDirName.prefix(2)),
              let minute = Int(timeDirName.dropFirst(2).prefix(2)),
              let second = Int(timeDirName.suffix(2)) else { return nil }
        return (hour, minute, second)
    }
}

private struct Spo2SharedProjection {
    let recordingDevice: String?
    let testStatus: String?
    let bloodOxygenPercent: Int?
    let spo2Class: String?
    let spo2ValueDeviationFromBaseline: String?
    let spo2QualityAveragePercent: Float?
    let averageHeartRateBpm: UInt?
    let heartRateVariabilityMs: Float?
    let spo2HrvDeviationFromBaseline: String?
    let altitudeMeters: Float?
    let triggerType: String?

    static func fromShared(proto: Data_PbSpo2TestResult, date: Date, timeDirName: String, timeZoneOffsetMinutes: Int?) -> Spo2SharedProjection? {
        guard let fields = PolarSpo2RuntimePlanner.projectionFields(proto: proto, date: date, timeDirName: timeDirName, timeZoneOffsetMinutes: timeZoneOffsetMinutes) else { return nil }
        guard fields.count == 11 else { return nil }
        return Spo2SharedProjection(
            recordingDevice: fields[0].nilIfEmpty,
            testStatus: fields[1].nilIfEmpty,
            bloodOxygenPercent: fields[2].nilIfEmpty.flatMap(Int.init),
            spo2Class: fields[3].nilIfEmpty,
            spo2ValueDeviationFromBaseline: fields[4].nilIfEmpty,
            spo2QualityAveragePercent: fields[5].nilIfEmpty.flatMap(Float.init),
            averageHeartRateBpm: fields[6].nilIfEmpty.flatMap(UInt.init),
            heartRateVariabilityMs: fields[7].nilIfEmpty.flatMap(Float.init),
            spo2HrvDeviationFromBaseline: fields[8].nilIfEmpty,
            altitudeMeters: fields[9].nilIfEmpty.flatMap(Float.init),
            triggerType: fields[10].nilIfEmpty
        )
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}

private extension PolarSpo2TestData.Spo2TestStatus {
    static func fromSharedOrRaw(value: Int) -> PolarSpo2TestData.Spo2TestStatus? {
        if let shared = PolarSpo2RuntimePlanner.testStatusName(value: value) {
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

private extension PolarSpo2TestData.Spo2Class {
    static func fromSharedOrRaw(value: Int) -> PolarSpo2TestData.Spo2Class? {
        if let shared = PolarSpo2RuntimePlanner.spo2ClassName(value: value) {
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

private extension PolarSpo2TestData.DeviationFromBaseline {
    static func fromSharedOrRaw(value: Int) -> PolarSpo2TestData.DeviationFromBaseline? {
        if let shared = PolarSpo2RuntimePlanner.deviationFromBaselineName(value: value) {
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

private extension PolarSpo2TestData.Spo2TestTriggerType {
    static func fromSharedOrRaw(value: Int) -> PolarSpo2TestData.Spo2TestTriggerType? {
        if let shared = PolarSpo2RuntimePlanner.triggerTypeName(value: value) {
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

private enum PolarSpo2RuntimePlanner {
    static func testDirectoryPath(day: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.spo2TestDirectoryPath(day: day)
        #else
        return nil
        #endif
    }

    static func testResultPath(directoryPath: String, subDirectoryName: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.spo2TestResultPath(directoryPath: directoryPath, subDirectoryName: subDirectoryName)
        #else
        return nil
        #endif
    }

    static func timeDirectoryParts(timeDirName: String) -> (hour: Int, minute: Int, second: Int)? {
        #if canImport(PolarBleSdkShared)
        guard let csv = PolarIosSharedBridge.shared.spo2TestTimeDirectoryPartsCsv(timeDirName: timeDirName) else { return nil }
        let parts = csv.split(separator: ",").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return (parts[0], parts[1], parts[2])
        #else
        return nil
        #endif
    }

    static func projectionFields(proto: Data_PbSpo2TestResult, date: Date, timeDirName: String, timeZoneOffsetMinutes: Int?) -> [String]? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.spo2ProjectionFields(
            date: dateFormat.string(from: date),
            timeDirName: timeDirName,
            recordingDevice: proto.recordingDevice,
            timeZoneOffsetMinutes: Int32(timeZoneOffsetMinutes ?? 0),
            testStatus: Int32(proto.testStatus.rawValue),
            bloodOxygenPercent: proto.hasBloodOxygenPercent ? String(proto.bloodOxygenPercent) : "",
            spo2Class: proto.hasSpo2Class ? String(proto.spo2Class.rawValue) : "",
            spo2ValueDeviationFromBaseline: proto.hasSpo2ValueDeviationFromBaseline ? String(proto.spo2ValueDeviationFromBaseline.rawValue) : "",
            spo2QualityAveragePercent: proto.hasSpo2QualityAveragePercent ? String(proto.spo2QualityAveragePercent) : "",
            averageHeartRateBpm: proto.hasAverageHeartRateBpm ? String(proto.averageHeartRateBpm) : "",
            heartRateVariabilityMs: proto.hasHeartRateVariabilityMs ? String(proto.heartRateVariabilityMs) : "",
            spo2HrvDeviationFromBaseline: proto.hasSpo2HrvDeviationFromBaseline ? String(proto.spo2HrvDeviationFromBaseline.rawValue) : "",
            altitudeMeters: proto.hasAltitudeMeters ? String(proto.altitudeMeters) : "",
            triggerType: proto.hasTriggerType ? String(proto.triggerType.rawValue) : ""
        ).split(separator: "\u{1F}", omittingEmptySubsequences: false).map(String.init)
        #else
        return nil
        #endif
    }

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
