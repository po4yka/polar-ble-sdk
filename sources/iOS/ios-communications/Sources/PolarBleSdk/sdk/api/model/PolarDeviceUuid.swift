//  Copyright © 2024 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

struct PolarDeviceUuid {
    private static let requiredDeviceIdLength = 8
    
    enum PolarDeviceUuidError: Error {
        case invalidDeviceIdLength(expected: Int, actual: Int)
    }
    
    static func fromDeviceId(_ deviceId: String) throws -> String {
        guard deviceId.count == requiredDeviceIdLength else {
            throw PolarDeviceUuidError.invalidDeviceIdLength(expected: requiredDeviceIdLength, actual: deviceId.count)
        }
        return PolarDeviceUuidRuntimePlanner.uuidFromDeviceId(deviceId)
    }
}

enum PolarDeviceUuidRuntimePlanner {
    static func uuidFromDeviceId(_ deviceId: String) -> String {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.uuidFromDeviceId(deviceId: deviceId)
        #else
        return "0e030000-0084-0000-0000-0000" + deviceId
        #endif
    }
}
