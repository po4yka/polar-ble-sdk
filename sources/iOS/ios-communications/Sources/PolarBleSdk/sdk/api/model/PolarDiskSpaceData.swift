//  Copyright © 2023 Polar. All rights reserved.

#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public struct PolarDiskSpaceData {
    public let totalSpace: UInt64
    public let freeSpace: UInt64
    
    static func fromProto(proto: Protocol_PbPFtpDiskSpaceResult) -> PolarDiskSpaceData {
        #if canImport(PolarBleSdkShared)
        if let sharedInput = SharedDiskSpaceInput(proto: proto) {
            return PolarDiskSpaceData(
                totalSpace: UInt64(PolarDiskSpaceRuntimePlanner.totalSpace(fragmentSize: sharedInput.fragmentSize, totalFragments: sharedInput.totalFragments, freeFragments: sharedInput.freeFragments)),
                freeSpace: UInt64(PolarDiskSpaceRuntimePlanner.freeSpace(fragmentSize: sharedInput.fragmentSize, totalFragments: sharedInput.totalFragments, freeFragments: sharedInput.freeFragments))
            )
        }
        #endif
        return PolarDiskSpaceData(
            totalSpace: UInt64(proto.fragmentSize) * proto.totalFragments,
            freeSpace: UInt64(proto.fragmentSize) * proto.freeFragments
        )
    }
}

enum PolarDiskSpaceRuntimePlanner {
    static func totalSpace(fragmentSize: Int64, totalFragments: Int64, freeFragments: Int64) -> Int64 {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.diskSpaceTotalSpace(fragmentSize: fragmentSize, totalFragments: totalFragments, freeFragments: freeFragments)
        #else
        return fragmentSize * totalFragments
        #endif
    }

    static func freeSpace(fragmentSize: Int64, totalFragments: Int64, freeFragments: Int64) -> Int64 {
        #if canImport(PolarBleSdkShared)
        return PolarIosSharedBridge.shared.diskSpaceFreeSpace(fragmentSize: fragmentSize, totalFragments: totalFragments, freeFragments: freeFragments)
        #else
        return fragmentSize * freeFragments
        #endif
    }
}

#if canImport(PolarBleSdkShared)
private struct SharedDiskSpaceInput {
    let fragmentSize: Int64
    let totalFragments: Int64
    let freeFragments: Int64

    init?(proto: Protocol_PbPFtpDiskSpaceResult) {
        guard proto.totalFragments <= UInt64(Int64.max), proto.freeFragments <= UInt64(Int64.max) else {
            return nil
        }
        fragmentSize = Int64(proto.fragmentSize)
        totalFragments = Int64(proto.totalFragments)
        freeFragments = Int64(proto.freeFragments)
    }
}
#endif
