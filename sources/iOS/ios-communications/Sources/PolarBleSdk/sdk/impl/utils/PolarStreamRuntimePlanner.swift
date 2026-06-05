// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarStreamRuntimePlanner {
    @discardableResult
    static func subscription(target: String, startConnected: Bool, checkConnection: Bool) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeStreamSubscription(target: target, startConnected: startConnected, checkConnection: checkConnection)
        #else
        return "platform-owned"
        #endif
    }

    @discardableResult
    static func consumerCancellation(target: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeStreamConsumerCancellation(target: target)
        #else
        return "platform-owned"
        #endif
    }

    @discardableResult
    static func disconnect(target: String, error: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeStreamDisconnect(target: target, error: error)
        #else
        return "platform-owned"
        #endif
    }

    @discardableResult
    static func duplicateCompletion(target: String) -> Int {
        #if canImport(PolarBleSdkShared)
        return Int(PolarIosSharedBridge.shared.planRuntimeStreamDuplicateCompletion(target: target))
        #else
        return 0
        #endif
    }

    @discardableResult
    static func postCompletionEmission(target: String, value: String) -> Int {
        #if canImport(PolarBleSdkShared)
        return Int(PolarIosSharedBridge.shared.planRuntimeStreamPostCompletionEmission(target: target, value: value))
        #else
        return 0
        #endif
    }
}
