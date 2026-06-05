// Copyright © 2026 Polar. All rights reserved.

import Foundation

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

enum PolarFileRuntimePlanner {
    @discardableResult
    static func runtimeError(operation: String, path: String, error: Error) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimeFileError(operation: operation, path: path, errorName: String(describing: type(of: error)))
        #else
        return "platform-owned"
        #endif
    }

    static func psFtpWriteProgress(payloadSize: Int, platform: String = "ios") -> [Int] {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimePsFtpWriteProgress(payloadSize: Int32(payloadSize), platform: platform).split(separator: ",").compactMap { Int($0) }
        #else
        return []
        #endif
    }

    @discardableResult
    static func psFtpWriteAck(payloadSize: Int, writeAck: String = "success") -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.planRuntimePsFtpWriteAck(payloadSize: Int32(payloadSize), writeAck: writeAck)
        #else
        return "platform-owned"
        #endif
    }
}
