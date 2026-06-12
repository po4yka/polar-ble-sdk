//  Copyright © 2022 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

class TypeUtils {
    
    static func convertArrayToSignedInt(_ data: Data, offset: Int, size: Int) -> Int32 {
        if let sharedValue = TypeRuntimePlanner.signedInt(data: data, offset: offset, size: size) {
            return sharedValue
        }
        return convertArrayToSignedInt(data.subdata(in: offset..<(offset+size)))
    }
    
    static func convertArrayToSignedInt(_ data: Data) -> Int32 {
        if let sharedValue = TypeRuntimePlanner.signedInt(data: data) {
            return sharedValue
        }
        assert(data.count <= 4)
        var value: Int32 = 0
        memcpy(&value, (data as NSData).bytes, data.count)
        let mask = (Int32.max << ((data.count * 8) - 1))
        if (value & mask) != 0 {
            value |= mask
        }
        return value
    }
    
    static func convertArrayToUnsignedInt(_ data: Data, offset: Int, size: Int) -> UInt {
        return convertArrayToUnsignedInt(data.subdata(in: offset..<(offset+size)))
    }
    
    static func convertArrayToUnsignedInt(_ data: Data) -> UInt {
        assert(data.count <= 4)
        var value: UInt = 0
        memcpy(&value, (data as NSData).bytes, data.count)
        let mask = (UInt.max << ((data.count * 8) - 1))
        if (value & mask) != 0 {
            value |= mask
        }
        return value
    }

    static func convertArrayToUnsignedInt64(_ data: Data, offset: Int, size: Int) -> UInt64 {
        if let sharedValue = TypeRuntimePlanner.unsignedLong(data: data, offset: offset, size: size) {
            return sharedValue
        }
        return convertArrayToUnsignedInt64(data.subdata(in: offset..<(offset+size)))
    }

    static func convertArrayToUnsignedInt64(_ data: Data) -> UInt64 {
        if let sharedValue = TypeRuntimePlanner.unsignedLong(data: data) {
            return sharedValue
        }
        assert(data.count <= 8)
        var value: UInt64 = 0
        value = data.reversed().reduce(0) { $0 << 8 + UInt64($1) }
        return value
    }
}

enum TypeRuntimePlanner {
    static func signedInt(data: Data, offset: Int, size: Int) -> Int32? {
        #if canImport(PolarBleSdkShared)
        guard !data.isEmpty && size <= 4 else { return nil }
        return PolarIosSharedBridge.shared.signedIntFromLittleEndianHex(hex: data.hexString(), offset: Int32(offset), size: Int32(size))
        #else
        return nil
        #endif
    }

    static func signedInt(data: Data) -> Int32? {
        #if canImport(PolarBleSdkShared)
        guard !data.isEmpty && data.count <= 4 else { return nil }
        return PolarIosSharedBridge.shared.signedIntFromLittleEndianHex(hex: data.hexString())
        #else
        return nil
        #endif
    }

    static func unsignedLong(data: Data, offset: Int, size: Int) -> UInt64? {
        #if canImport(PolarBleSdkShared)
        guard !data.isEmpty && size <= 8 else { return nil }
        return UInt64(PolarIosSharedBridge.shared.unsignedLongFromLittleEndianHex(hex: data.hexString(), offset: Int32(offset), size: Int32(size)))
        #else
        return nil
        #endif
    }

    static func unsignedLong(data: Data) -> UInt64? {
        #if canImport(PolarBleSdkShared)
        guard !data.isEmpty && data.count <= 8 else { return nil }
        return UInt64(PolarIosSharedBridge.shared.unsignedLongFromLittleEndianHex(hex: data.hexString()))
        #else
        return nil
        #endif
    }
}

private extension Data {
    func hexString() -> String {
        map { String(format: "%02x", $0) }.joined()
    }
}
