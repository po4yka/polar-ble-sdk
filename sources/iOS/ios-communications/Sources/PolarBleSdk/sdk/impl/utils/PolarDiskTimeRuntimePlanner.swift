// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarDiskTimeRuntimePlanner {
    @discardableResult
    static func query(id: String, query: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeDiskTimeQuery(id: id, query: query)
        #else
        return "platform-owned"
        #endif
    }

    static func queryValue(id: String, query: String) -> Int? {
        #if canImport(PolarBleSdkShared)
        return queryRawValues(PolarIosSharedBridge.shared.planRuntimeDiskTimeQueryCommands(id: id, query: query)).first
        #else
        return nil
        #endif
    }

    @discardableResult
    static func setLocalTimeV2(systemTimeHour: Int, localTimeHour: Int) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeSetLocalTimeV2(systemTimeHour: Int32(systemTimeHour), localTimeHour: Int32(localTimeHour))
        #else
        return "platform-owned"
        #endif
    }

    static func setLocalTimeV2QueryValues(systemTimeHour: Int, localTimeHour: Int) -> [Int]? {
        #if canImport(PolarBleSdkShared)
        return queryRawValues(PolarIosSharedBridge.shared.planRuntimeSetLocalTimeV2Commands(systemTimeHour: Int32(systemTimeHour), localTimeHour: Int32(localTimeHour)))
        #else
        return nil
        #endif
    }

    @discardableResult
    static func setLocalTimeH10(localTimeHour: Int) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeSetLocalTimeH10(localTimeHour: Int32(localTimeHour))
        #else
        return "platform-owned"
        #endif
    }

    static func setLocalTimeH10QueryValues(localTimeHour: Int) -> [Int]? {
        #if canImport(PolarBleSdkShared)
        return queryRawValues(PolarIosSharedBridge.shared.planRuntimeSetLocalTimeH10Commands(localTimeHour: Int32(localTimeHour)))
        #else
        return nil
        #endif
    }

    private static func queryRawValues(_ csv: String) -> [Int] {
        return csv.split(separator: ",").compactMap { plannedName in
            switch String(plannedName) {
            case "GET_DISK_SPACE": return Protocol_PbPFtpQuery.getDiskSpace.rawValue
            case "GET_LOCAL_TIME": return Protocol_PbPFtpQuery.getLocalTime.rawValue
            case "REQUEST_START_RECORDING": return Protocol_PbPFtpQuery.requestStartRecording.rawValue
            case "REQUEST_STOP_RECORDING": return Protocol_PbPFtpQuery.requestStopRecording.rawValue
            case "REQUEST_RECORDING_STATUS": return Protocol_PbPFtpQuery.requestRecordingStatus.rawValue
            case "REQUEST_SYNCHRONIZATION": return Protocol_PbPFtpQuery.requestSynchronization.rawValue
            case "SET_LOCAL_TIME": return Protocol_PbPFtpQuery.setLocalTime.rawValue
            case "SET_SYSTEM_TIME": return Protocol_PbPFtpQuery.setSystemTime.rawValue
            default: return nil
            }
        }
    }
}
