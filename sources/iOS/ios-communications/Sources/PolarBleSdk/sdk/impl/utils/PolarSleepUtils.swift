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
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.sleepAnalysisPath(day: day)
        #else
        return "\(ARABICA_USER_ROOT_FOLDER)\(day)/\(SLEEP_DIRECTORY)\(SLEEP_PROTO)"
        #endif
    }

    private static func sleepSkinTemperaturePath(day: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.sleepSkinTemperaturePath(day: day)
        #else
        return "\(ARABICA_USER_ROOT_FOLDER)\(day)/\(NRST_DIRECTORY)\(NRST_PROTO)"
        #endif
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
        let operation = Protocol_PbPFtpOperation.with { $0.command = plannedOperation.command; $0.path = plannedOperation.path }
        do {
            let response = try await client.request(try operation.serializedBytes())
            let proto = try Data_PbSleepAnalysisResult(serializedBytes: Data(response))
            return PolarSleepData.PolarSleepAnalysisResult(
                sleepStartTime: try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: proto.sleepStartTime),
                sleepEndTime: try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: proto.sleepEndTime),
                lastModified: proto.hasLastModified ? try PolarTimeUtils.pbSystemDateTimeToDate(pbSystemDateTime: proto.lastModified) : nil,
                sleepGoalMinutes: proto.sleepGoalMinutes,
                sleepWakePhases: PolarSleepData.fromPbSleepwakePhasesListProto(pbSleepwakePhasesList: proto.sleepwakePhases),
                snoozeTime: try PolarSleepData.convertSnoozeTimeListToLocalTime(snoozeTimeList: proto.snoozeTime),
                alarmTime: proto.hasAlarmTime ? try PolarTimeUtils.pbLocalDateTimeToDate(pbLocalDateTime: proto.alarmTime) : nil,
                sleepStartOffsetSeconds: proto.sleepStartOffsetSeconds,
                sleepEndOffsetSeconds: proto.sleepStartOffsetSeconds,
                userSleepRating: proto.hasUserSleepRating ? PolarSleepData.SleepRating.optionalFromProtoValue(value: proto.userSleepRating.rawValue) : nil,
                deviceId: proto.hasRecordingDevice ? proto.recordingDevice.deviceID : nil,
                batteryRanOut: proto.hasBatteryRanOut ? proto.batteryRanOut : nil,
                sleepCycles: PolarSleepData.fromPbSleepCyclesList(pbSleepCyclesList: proto.sleepCycles),
                sleepResultDate: try PolarTimeUtils.pbDateToDateComponents(pbDate: proto.sleepResultDate),
                originalSleepRange: try PolarSleepData.fromPbOriginalSleepRange(pbOriginalSleepRange: proto.originalSleepRange)
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

    static func readSleepSkinTemperatureResult(client: BlePsFtpClient, date: Date, sleepAnalysisResult: PolarSleepData.PolarSleepAnalysisResult) async throws -> PolarSleepData.PolarSleepAnalysisResult {
        BleLogger.trace(TAG, "readSleepSkinTemperature: \(date)")
        var result = sleepAnalysisResult
        let plannedOperation = sleepSkinTemperatureReadOperation(date: date)
        let filePath = plannedOperation.path
        let operation = Protocol_PbPFtpOperation.with { $0.command = plannedOperation.command; $0.path = plannedOperation.path }
        do {
            let response = try await client.request(try operation.serializedBytes())
            let proto = try Data_PbSleepSkinTemperatureResult(serializedBytes: Data(response))
            result.sleepSkinTemperatureResult = try PolarSleepData.fromPbSleepTemperatureResult(pbSleepTemperatureResult: proto)
        } catch {
            BleLogger.trace("readSleepSkinTemperature() failed for path: \(filePath), error: \(error). No sleep skin temperature data?")
        }
        return result
    }
}
