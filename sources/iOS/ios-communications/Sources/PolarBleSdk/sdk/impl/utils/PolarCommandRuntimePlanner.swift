// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarCommandRuntimePlanner {
    @discardableResult
    static func query(id: String, query: String, parameters: [String] = []) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeCommandQuery(id: id, query: query, parametersCsv: parameters.joined(separator: ","))
        #else
        return "platform-owned"
        #endif
    }

    static func queryValue(id: String, query: String, parameters: [String] = []) -> Int? {
        #if canImport(PolarBleSdkShared)
        return queryRawValues(PolarIosSharedBridge.shared.planRuntimeCommandQueryCommands(id: id, query: query, parametersCsv: parameters.joined(separator: ","))).first
        #else
        return nil
        #endif
    }

    @discardableResult
    static func reset(id: String, sleep: Bool, factoryDefaults: Bool, otaFirmwareUpdate: Bool) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeCommandReset(id: id, sleep: sleep, factoryDefaults: factoryDefaults, otaFirmwareUpdate: otaFirmwareUpdate)
        #else
        return "platform-owned"
        #endif
    }

    static func resetNotification(id: String, sleep: Bool, factoryDefaults: Bool, otaFirmwareUpdate: Bool) -> Int? {
        #if canImport(PolarBleSdkShared)
        return notificationRawValues(PolarIosSharedBridge.shared.planRuntimeCommandResetNotifications(id: id, sleep: sleep, factoryDefaults: factoryDefaults, otaFirmwareUpdate: otaFirmwareUpdate)).first
        #else
        return nil
        #endif
    }

    @discardableResult
    static func syncStart(id: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeCommandSyncStart(id: id)
        #else
        return "platform-owned"
        #endif
    }

    static func syncStartNotifications(id: String) -> [Int]? {
        #if canImport(PolarBleSdkShared)
        return notificationRawValues(PolarIosSharedBridge.shared.planRuntimeCommandSyncStartNotifications(id: id))
        #else
        return nil
        #endif
    }

    @discardableResult
    static func syncStop(id: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeCommandSyncStop(id: id)
        #else
        return "platform-owned"
        #endif
    }

    static func syncStopNotifications(id: String) -> [Int]? {
        #if canImport(PolarBleSdkShared)
        return notificationRawValues(PolarIosSharedBridge.shared.planRuntimeCommandSyncStopNotifications(id: id))
        #else
        return nil
        #endif
    }

    private static func notificationRawValues(_ csv: String) -> [Int] {
        return csv.split(separator: ",").compactMap { plannedName in
            switch plannedName.split(separator: ":").first.map(String.init) {
            case "INITIALIZE_SESSION": return Protocol_PbPFtpHostToDevNotification.initializeSession.rawValue
            case "START_SYNC": return Protocol_PbPFtpHostToDevNotification.startSync.rawValue
            case "STOP_SYNC": return Protocol_PbPFtpHostToDevNotification.stopSync.rawValue
            case "TERMINATE_SESSION": return Protocol_PbPFtpHostToDevNotification.terminateSession.rawValue
            case "RESET": return Protocol_PbPFtpHostToDevNotification.reset.rawValue
            default: return nil
            }
        }
    }

    private static func queryRawValues(_ csv: String) -> [Int] {
        return csv.split(separator: ",").compactMap { plannedName in
            switch String(plannedName) {
            case "GET_DISK_SPACE": return Protocol_PbPFtpQuery.getDiskSpace.rawValue
            case "GET_LOCAL_TIME": return Protocol_PbPFtpQuery.getLocalTime.rawValue
            case "REQUEST_START_RECORDING": return Protocol_PbPFtpQuery.requestStartRecording.rawValue
            case "REQUEST_STOP_RECORDING": return Protocol_PbPFtpQuery.requestStopRecording.rawValue
            case "REQUEST_RECORDING_STATUS": return Protocol_PbPFtpQuery.requestRecordingStatus.rawValue
            case "START_EXERCISE": return Protocol_PbPFtpQuery.startExercise.rawValue
            case "PAUSE_EXERCISE": return Protocol_PbPFtpQuery.pauseExercise.rawValue
            case "RESUME_EXERCISE": return Protocol_PbPFtpQuery.resumeExercise.rawValue
            case "START_DM_EXERCISE": return Protocol_PbPFtpQuery.startDmExercise.rawValue
            case "STOP_EXERCISE": return Protocol_PbPFtpQuery.stopExercise.rawValue
            case "GET_EXERCISE_STATUS": return Protocol_PbPFtpQuery.getExerciseStatus.rawValue
            case "REQUEST_SYNCHRONIZATION": return Protocol_PbPFtpQuery.requestSynchronization.rawValue
            case "SET_LOCAL_TIME": return Protocol_PbPFtpQuery.setLocalTime.rawValue
            case "SET_SYSTEM_TIME": return Protocol_PbPFtpQuery.setSystemTime.rawValue
            default: return nil
            }
        }
    }
}
