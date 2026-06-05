//  Copyright © 2022 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public enum PmdRecordingType: UInt8, CaseIterable, Sendable {
    case online = 0
    case offline = 1
    
    func asBitField() -> UInt8 {
        #if canImport(PolarBleSdkShared)
        return UInt8(PolarIosSharedBridge.shared.pmdRecordingTypeBitField(name: sharedName))
        #else
        return self.rawValue << 7
        #endif
    }
}

#if canImport(PolarBleSdkShared)
private extension PmdRecordingType {
    var sharedName: String {
        switch self {
        case .online: return "ONLINE"
        case .offline: return "OFFLINE"
        }
    }
}
#endif
