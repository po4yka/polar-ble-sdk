// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarD2hRuntimePlanner {
    static func notificationTypeName(notificationId: Int) -> String? {
        #if canImport(PolarBleSdkShared)
        let value = PolarIosSharedBridge.shared.d2hNotificationType(notificationId: Int32(notificationId))
        return value.isEmpty ? nil : value
        #else
        return nil
        #endif
    }

    static func parsedProtoName(notificationType: String, parametersHex: String) -> String? {
        #if canImport(PolarBleSdkShared)
        let value = PolarIosSharedBridge.shared.d2hParsedProtoName(notificationType: notificationType, parametersHex: parametersHex)
        return value.isEmpty ? nil : value
        #else
        return nil
        #endif
    }

    static func notificationPlan(notificationId: Int, parametersHex: String) -> (notificationType: String, parsedProtoName: String?)? {
        #if canImport(PolarBleSdkShared)
        let value = PolarIosSharedBridge.shared.d2hNotificationPlan(notificationId: Int32(notificationId), parametersHex: parametersHex)
        if value.isEmpty { return nil }
        let fields = value.split(separator: ",", omittingEmptySubsequences: false).map(String.init)
        return (notificationType: fields[0], parsedProtoName: fields.count > 1 && !fields[1].isEmpty ? fields[1] : nil)
        #else
        return nil
        #endif
    }
}
