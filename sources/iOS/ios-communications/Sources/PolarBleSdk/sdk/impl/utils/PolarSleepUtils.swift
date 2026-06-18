//  Copyright © 2024 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

private let ARABICA_USER_ROOT_FOLDER = "/U/0/"
private let SLEEP_DIRECTORY = "SLEEP/"
private let SLEEP_PROTO = "SLEEPRES.BPB"
private let NRST_DIRECTORY = "NSTRESUL/"
private let NRST_PROTO = "NSTRCONT.BPB"
private let dateFormat: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyyMMdd"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(abbreviation: "UTC")
    return formatter
}()
private let TAG = "PolarSleepUtils"

internal class PolarSleepUtils {
    static func sleepDataReadOperation(date: Date) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        let path = sleepAnalysisPath(day: dateFormat.string(from: date))
        return fileReadOperation(id: "sleep-read-analysis", path: path) ?? (.get, path)
    }

    static func sleepSkinTemperatureReadOperation(date: Date) -> (command: Protocol_PbPFtpOperation.Command, path: String) {
        let path = sleepSkinTemperaturePath(day: dateFormat.string(from: date))
        return fileReadOperation(id: "sleep-read-skin-temperature", path: path) ?? (.get, path)
    }

    private static func fileReadOperation(id: String, path: String) -> (command: Protocol_PbPFtpOperation.Command, path: String)? {
        return PolarRuntimePlanner.fileFacadeOperation(id: id, command: "GET", path: path)
    }

    private static func sleepAnalysisPath(day: String) -> String {
        return PolarSleepRuntimePlanner.sleepAnalysisPath(day: day) ?? "\(ARABICA_USER_ROOT_FOLDER)\(day)/\(SLEEP_DIRECTORY)\(SLEEP_PROTO)"
    }

    private static func sleepSkinTemperaturePath(day: String) -> String {
        return PolarSleepRuntimePlanner.sleepSkinTemperaturePath(day: day) ?? "\(ARABICA_USER_ROOT_FOLDER)\(day)/\(NRST_DIRECTORY)\(NRST_PROTO)"
    }

    static func readSleepFromDayDirectory(client: BlePsFtpClient, date: Date) async throws -> PolarSleepData.PolarSleepAnalysisResult {
        var result = try await readSleepData(client: client, date: date)
        result = try await readSleepSkinTemperatureResult(client: client, date: date, sleepAnalysisResult: result)
        return result
    }

    static func readSleepData(client: BlePsFtpClient, date: Date) async throws -> PolarSleepData.PolarSleepAnalysisResult {
        BleLogger.trace(TAG, "readSleepFromDayDirectory: \(date)")
        let plannedOperation = sleepDataReadOperation(date: date)
        let sleepDataFilePath = plannedOperation.path
        do {
            let response = try await client.request(try PolarRuntimePlanner.fileOperationBytes(plannedOperation))
            let proto = try Data_PbSleepAnalysisResult(serializedBytes: Data(response))
            return PolarSleepData.PolarSleepAnalysisResult(
                sleepStartTime: try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: proto.sleepStartTime),
                sleepEndTime: try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: proto.sleepEndTime),
                lastModified: proto.hasLastModified ? try PolarTimeUtils.pbSystemDateTimeToDate(pbSystemDateTime: proto.lastModified) : nil,
                sleepGoalMinutes: proto.sleepGoalMinutes,
                sleepWakePhases: PolarSleepData.fromPbSleepwakePhasesListProto(pbSleepwakePhasesList: proto.sleepwakePhases),
                snoozeTime: try PolarSleepData.convertSnoozeTimeListToLocalTime(snoozeTimeList: proto.snoozeTime),
                alarmTime: proto.hasAlarmTime ? try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: proto.alarmTime) : nil,
                sleepStartOffsetSeconds: sharedSleepStartOffsetSeconds(proto.sleepStartOffsetSeconds),
                sleepEndOffsetSeconds: sharedSleepEndOffsetSeconds(startOffsetSeconds: proto.sleepStartOffsetSeconds, endOffsetSeconds: proto.sleepEndOffsetSeconds),
                userSleepRating: proto.hasUserSleepRating ? PolarSleepData.SleepRating.optionalFromProtoValue(value: proto.userSleepRating.rawValue) : nil,
                deviceId: proto.hasRecordingDevice ? proto.recordingDevice.deviceID : nil,
                batteryRanOut: proto.hasBatteryRanOut ? proto.batteryRanOut : nil,
                sleepCycles: PolarSleepData.fromPbSleepCyclesList(pbSleepCyclesList: proto.sleepCycles),
                sleepResultDate: try PolarTimeUtils.pbDateToDateComponents(pbDate: proto.sleepResultDate),
                originalSleepRange: sharedShouldIncludeOriginalSleepRange(proto.hasOriginalSleepRange) ? try PolarSleepData.fromPbOriginalSleepRange(pbOriginalSleepRange: proto.originalSleepRange) : nil
            )
        } catch {
            BleLogger.trace("readSleepFromDayDirectory() failed for path: \(sleepDataFilePath), error: \(error). No sleep data?")
            return PolarSleepData.PolarSleepAnalysisResult(
                sleepStartTime: nil, sleepEndTime: nil, lastModified: nil, sleepGoalMinutes: nil,
                sleepWakePhases: nil, snoozeTime: nil, alarmTime: nil, sleepStartOffsetSeconds: nil,
                sleepEndOffsetSeconds: nil, userSleepRating: nil, deviceId: nil, batteryRanOut: nil,
                sleepCycles: nil, sleepResultDate: nil, originalSleepRange: nil
            )
        }
    }

    private static func sharedSleepStartOffsetSeconds(_ value: Int32) -> Int32 {
        return PolarSleepRuntimePlanner.sleepStartOffsetSeconds(value) ?? value
    }

    private static func sharedSleepEndOffsetSeconds(startOffsetSeconds: Int32, endOffsetSeconds: Int32) -> Int32 {
        return PolarSleepRuntimePlanner.sleepEndOffsetSeconds(endOffsetSeconds) ?? startOffsetSeconds
    }

    private static func sharedShouldIncludeOriginalSleepRange(_ hasOriginalSleepRange: Bool) -> Bool {
        return PolarSleepRuntimePlanner.shouldIncludeOriginalSleepRange(hasOriginalSleepRange) ?? true
    }

    private static func sharedShouldIncludeSleepSkinTemperatureResult(_ hasSleepDate: Bool) -> Bool {
        return PolarSleepRuntimePlanner.shouldIncludeSleepSkinTemperatureResult(hasSleepDate) ?? true
    }

    static func readSleepSkinTemperatureResult(client: BlePsFtpClient, date: Date, sleepAnalysisResult: PolarSleepData.PolarSleepAnalysisResult) async throws -> PolarSleepData.PolarSleepAnalysisResult {
        BleLogger.trace(TAG, "readSleepSkinTemperature: \(date)")
        var result = sleepAnalysisResult
        let plannedOperation = sleepSkinTemperatureReadOperation(date: date)
        let filePath = plannedOperation.path
        do {
            let response = try await client.request(try PolarRuntimePlanner.fileOperationBytes(plannedOperation))
            let proto = try Data_PbSleepSkinTemperatureResult(serializedBytes: Data(response))
            if sharedShouldIncludeSleepSkinTemperatureResult(proto.hasSleepDate) {
                result.sleepSkinTemperatureResult = try PolarSleepData.fromPbSleepTemperatureResult(pbSleepTemperatureResult: proto)
            }
        } catch {
            BleLogger.trace("readSleepSkinTemperature() failed for path: \(filePath), error: \(error). No sleep skin temperature data?")
        }
        return result
    }
}

enum PolarSleepRuntimePlanner {
    static func sleepAnalysisPath(day: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.sleepAnalysisPath(day: day)
        #else
        return nil
        #endif
    }

    static func sleepSkinTemperaturePath(day: String) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.sleepSkinTemperaturePath(day: day)
        #else
        return nil
        #endif
    }

    static func sleepStartOffsetSeconds(_ value: Int32) -> Int32? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.sleepStartOffsetSeconds(value: value)
        #else
        return nil
        #endif
    }

    static func sleepEndOffsetSeconds(_ value: Int32) -> Int32? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.sleepEndOffsetSeconds(value: value)
        #else
        return nil
        #endif
    }

    static func shouldIncludeOriginalSleepRange(_ hasOriginalSleepRange: Bool) -> Bool? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.shouldIncludeOriginalSleepRange(hasOriginalSleepRange: hasOriginalSleepRange)
        #else
        return nil
        #endif
    }

    static func shouldIncludeSleepSkinTemperatureResult(_ hasSleepDate: Bool) -> Bool? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.shouldIncludeSleepSkinTemperatureResult(hasSleepDate: hasSleepDate)
        #else
        return nil
        #endif
    }

    static func sleepWakeStateName(value: Int) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.sleepWakeStateName(value: Int32(value))
        #else
        return nil
        #endif
    }

    static func sleepRatingName(value: Int) -> String? {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.sleepRatingName(value: Int32(value))
        #else
        return nil
        #endif
    }
}
