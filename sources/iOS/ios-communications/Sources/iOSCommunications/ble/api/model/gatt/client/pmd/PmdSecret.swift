//  Copyright © 2023 Polar. All rights reserved.

import Foundation
import CryptoKit
import CommonCrypto
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

public struct PmdSecret: @unchecked Sendable {
    let strategy: SecurityStrategy
    let key: Data
    private let keySymmetric: SymmetricKey?
    
    init(strategy: SecurityStrategy, key: Data) throws {
        switch(strategy) {
        case .none:
            guard key.isEmpty else {
                throw BleGattException.gattSecurityError(description: "key shall be empty for \(SecurityStrategy.none), key size was \(key.count)")
            }
            self.keySymmetric = nil
        case .xor:
            guard !key.isEmpty else {
                throw BleGattException.gattSecurityError(description: "key shall not be empty for \(SecurityStrategy.xor), key size was \(key.count)")
            }
            self.keySymmetric = nil
        case .aes128:
            guard key.count == 16 else {
                throw BleGattException.gattSecurityError(description: "key must be size of 16 bytes for \(SecurityStrategy.aes128), key size was \(key.count)")
            }
            self.keySymmetric = SymmetricKey(data: key)
            
        case .aes256:
            guard key.count == 32 else {
                throw BleGattException.gattSecurityError(description: "key must be size of 32 bytes for \(SecurityStrategy.aes256), key size was \(key.count)")
            }
            self.keySymmetric = SymmetricKey(data: key)
        }
        self.strategy = strategy
        self.key = key
    }
    
    func serializeToPmdSettings() -> Data {
        #if canImport(PolarBleSdkShared)
        if let sharedHex = PolarIosSharedBridge.shared.pmdSecretSettingsHex(strategy: strategy.sharedName, keyHex: key.hexString),
           let shared = Data(hexBytes: sharedHex) {
            return shared
        }
        #endif
        switch(self.strategy) {
        case .none:
            let securitySetting = PmdSetting.PmdSettingType.security.rawValue
            let securityStrategyByte = SecurityStrategy.none.rawValue
            let length:UInt8 = 1
            return Data([securitySetting, length, securityStrategyByte])
        case .xor:
            let securitySetting = PmdSetting.PmdSettingType.security.rawValue
            let securityStrategyByte = SecurityStrategy.xor.rawValue
            let keyBytes = key
            let length:UInt8 = 1
            return [securitySetting, length, securityStrategyByte] + keyBytes
        case .aes128:
            let securitySetting = PmdSetting.PmdSettingType.security.rawValue
            let securityStrategyByte = SecurityStrategy.aes128.rawValue
            let keyBytes = key
            let length:UInt8 = 1
            return [securitySetting, length, securityStrategyByte] + keyBytes
        case .aes256:
            let securitySetting = PmdSetting.PmdSettingType.security.rawValue
            let securityStrategyByte = SecurityStrategy.aes256.rawValue
            let keyBytes = key
            let length:UInt8 = 1
            return [securitySetting, length, securityStrategyByte] + keyBytes
        }
    }
    
    func decryptArray(cipherArray: Data) throws -> Data {
        switch(self.strategy) {
        case .none:
            return cipherArray
        case .xor:
            return Data(cipherArray.map { $0^key.first! })
        case .aes128, .aes256:
            return try decryptAES(chipperData: cipherArray)
        }
    }
    
    private func decryptAES(chipperData: Data) throws -> Data {
        guard chipperData.count % 16 == 0 else {
            throw BleGattException.gattSecurityError(description: "AES decryption failed. Chipper data is not dividable by 16, chipper size was \(chipperData.count)")
        }
        
        let cryptoLength = chipperData.count + key.count
        var cryptoData = Data(count: cryptoLength)
        var bytesLength = Int(0)
        
        let status = cryptoData.withUnsafeMutableBytes { cryptoBytes in
            chipperData.withUnsafeBytes { dataBytes in
                         key.withUnsafeBytes { keyBytes in
                    CCCrypt(UInt32(kCCDecrypt), CCAlgorithm(kCCAlgorithmAES), CCOptions(kCCOptionECBMode), keyBytes.baseAddress, key.count, nil, dataBytes.baseAddress, chipperData.count, cryptoBytes.baseAddress, cryptoLength, &bytesLength)
                }
            }
        }
        
        guard status == kCCSuccess else {
            debugPrint("Error: Failed to crypt data. Status \(status)")
            throw BleGattException.gattSecurityError(description: "AES decryption failed. Failed to error \(status)")
        }
        
        cryptoData.removeSubrange(bytesLength..<cryptoData.count)
        return cryptoData
    }
    
    enum SecurityStrategy: UInt8 {
        case none = 0
        case xor = 1
        case aes128 = 2
        case aes256 = 3
        
        static func fromByte(strategyByte: UInt8) throws -> SecurityStrategy {
            #if canImport(PolarBleSdkShared)
            if let sharedName = PolarIosSharedBridge.shared.pmdSecretStrategyName(strategyByte: Int32(strategyByte)),
               let sharedStrategy = SecurityStrategy(sharedName: sharedName) {
                return sharedStrategy
            }
            #endif
            switch(strategyByte) {
            case SecurityStrategy.none.rawValue:
                return SecurityStrategy.none
            case SecurityStrategy.xor.rawValue:
                return SecurityStrategy.xor
            case SecurityStrategy.aes128.rawValue:
                return SecurityStrategy.aes128
            case SecurityStrategy.aes256.rawValue:
                return SecurityStrategy.aes256
            default :
                throw BleGattException.gattSecurityError(description: "Cannot decide security strategy from byte \(String(format:"%02X", strategyByte))")
            }
        }
    }
}

#if canImport(PolarBleSdkShared)
private extension PmdSecret.SecurityStrategy {
    var sharedName: String {
        switch self {
        case .none: return "NONE"
        case .xor: return "XOR"
        case .aes128: return "AES128"
        case .aes256: return "AES256"
        }
    }

    init?(sharedName: String) {
        switch sharedName {
        case "NONE": self = .none
        case "XOR": self = .xor
        case "AES128": self = .aes128
        case "AES256": self = .aes256
        default: return nil
        }
    }
}

private extension Data {
    var hexString: String {
        return map { String(format: "%02x", $0) }.joined()
    }

    init?(hexBytes: String) {
        guard hexBytes.count % 2 == 0 else { return nil }
        var bytes: [UInt8] = []
        bytes.reserveCapacity(hexBytes.count / 2)
        var index = hexBytes.startIndex
        while index < hexBytes.endIndex {
            let next = hexBytes.index(index, offsetBy: 2)
            guard let byte = UInt8(hexBytes[index..<next], radix: 16) else { return nil }
            bytes.append(byte)
            index = next
        }
        self.init(bytes)
    }
}
#endif
