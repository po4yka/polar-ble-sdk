//  Copyright © 2022 Polar. All rights reserved.

import Foundation
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

class TypeUtils {
    
    static func convertArrayToSignedInt(_ data: Data, offset: Int, size: Int) -> Int32 {
        return convertArrayToSignedInt(data.subdata(in: offset..<(offset+size)))
    }
    
    static func convertArrayToSignedInt(_ data: Data) -> Int32 {
        #if canImport(PolarBleSdkShared)
        if !data.isEmpty && data.count <= 4 {
            return PolarIosSharedBridge.shared.signedIntFromLittleEndianHex(hex: data.hexString())
        }
        #endif
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
        return convertArrayToUnsignedInt64(data.subdata(in: offset..<(offset+size)))
    }

    static func convertArrayToUnsignedInt64(_ data: Data) -> UInt64 {
        #if canImport(PolarBleSdkShared)
        if !data.isEmpty && data.count <= 8, let value = UInt64(PolarIosSharedBridge.shared.unsignedLongFromLittleEndianHex(hex: data.hexString())) {
            return value
        }
        #endif
        assert(data.count <= 8)
        var value: UInt64 = 0
        value = data.reversed().reduce(0) { $0 << 8 + UInt64($1) }
        return value
    }
}

private extension Data {
    func hexString() -> String {
        map { String(format: "%02x", $0) }.joined()
    }
}
