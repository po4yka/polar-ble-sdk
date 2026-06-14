//  Copyright © 2023 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class PmdSecretTest: XCTestCase {
    
    let key16bytes = Data([
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF
    ])
    
    func testSerializationStrategyNONE() throws {
        //Arrange
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.none, key: Data())
        
        //Act
        let serialized = pmdSecret.serializeToPmdSettings()
        
        //Assert
        XCTAssertEqual(3, serialized.count)
        XCTAssertEqual(PmdSetting.PmdSettingType.security.rawValue, serialized[0])
        XCTAssertEqual(1, serialized[1])
        XCTAssertEqual(PmdSecret.SecurityStrategy.none.rawValue, serialized[2])
    }
    
    func testSerializationStrategyXOR() throws {
        //Arrange
        let expectedKey = Data([0xFF])
        //val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.XOR, key = expectedKey)
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.xor, key: expectedKey)
        
        //Act
        let serialized = pmdSecret.serializeToPmdSettings()
        
        //Assert
        XCTAssertEqual(1 + 1 + 1 + 1, serialized.count)
        XCTAssertEqual(PmdSetting.PmdSettingType.security.rawValue, serialized[0])
        XCTAssertEqual(1, serialized[1])
        XCTAssertEqual(PmdSecret.SecurityStrategy.xor.rawValue, serialized[2])
        XCTAssertEqual(expectedKey, serialized[3...])
    }
    
    func testSerializationStrategy128() throws {
        //Arrange
        let expectedKey = Data(key16bytes.reversed())
        //val expectedKey = key16bytes.reversed().toByteArray()
        //val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.AES128, key = expectedKey)
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.aes128, key: expectedKey)
        
        //Act
        let serialized = pmdSecret.serializeToPmdSettings()
        
        //Assert
        XCTAssertEqual(1 + 1 + 1 + 16, serialized.count)
        XCTAssertEqual(PmdSetting.PmdSettingType.security.rawValue, serialized[0])
        XCTAssertEqual(1, serialized[1])
        XCTAssertEqual(PmdSecret.SecurityStrategy.aes128.rawValue, serialized[2])
        XCTAssertEqual(expectedKey, serialized[3...])
    }
    
    func testSerializationStrategy256() throws {
        //Arrange
        let expectedKey = key16bytes + Data(key16bytes.reversed())
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.aes256, key: expectedKey)
        
        //Act
        let serialized = pmdSecret.serializeToPmdSettings()
        
        //Assert
        XCTAssertEqual(1 + 1 + 1 + 32, serialized.count)
        XCTAssertEqual(PmdSetting.PmdSettingType.security.rawValue, serialized[0])
        XCTAssertEqual(1, serialized[1])
        XCTAssertEqual(PmdSecret.SecurityStrategy.aes256.rawValue, serialized[2])
        XCTAssertEqual(expectedKey, serialized[3...])
    }
    
    func testDecryptionStrategyNone() throws {
        //Arrange
        let chipper = Data([
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF
        ])
        
        let expectedData = Data([
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF
        ])
        
        let key = Data()
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.none, key: key)
        
        //Act
        let decrypted = try pmdSecret.decryptArray(cipherArray: chipper)
        
        //Assert
        XCTAssertEqual(expectedData, decrypted)
    }
    
    func testDecryptionStrategyXOR() throws {
        //Arrange
        let chipper = Data([
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF
        ])
        
        let expectedData = Data([
            0x55, 0x54, 0x57, 0x56, 0x51, 0x50, 0x53, 0x52, 0x5D, 0x5C, 0x5F, 0x5E, 0x59, 0x58, 0x5B, 0xAA,
        ])
        
        let key = Data([0x55])
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.xor, key: key)
        
        //Act
        let decrypted = try pmdSecret.decryptArray(cipherArray: chipper)
        
        //Assert
        XCTAssertEqual(expectedData, decrypted)
    }
    
    func testDecryptionStrategy128() throws {
        //Arrange
        let chipper = Data([
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF,
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF,
            0xFF, 0xFF, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF
            
        ])
        
        let expectedData = Data([
            0x60, 0x08, 0x6b, 0xda, 0x00, 0xdb, 0x42, 0x62, 0x34, 0x60, 0x27, 0x43, 0x71, 0xa7, 0x53, 0x68,
            0x60, 0x08, 0x6b, 0xda, 0x00, 0xdb, 0x42, 0x62, 0x34, 0x60, 0x27, 0x43, 0x71, 0xa7, 0x53, 0x68,
            0x6f, 0x5e, 0x05, 0x8b, 0x37, 0xdd, 0xd1, 0xed, 0x0e, 0xf2, 0x89, 0xef, 0xf8, 0xb2, 0x85, 0x54,
        ])
        
        let key = key16bytes
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.aes128, key: key)
        
        //Act
        let decrypted = try pmdSecret.decryptArray(cipherArray: chipper)
        
        //Assert
        XCTAssertEqual(expectedData, decrypted)
    }
    
    func testDecryptionStrategy256() throws {
        //Arrange
        let chipper = Data([
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF,
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF,
            0xFF, 0xFF, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF
            
        ])
        
        let expectedData = Data([
            0xc8, 0x0d, 0x56, 0xbb, 0x97, 0x7a, 0x42, 0x5f, 0x5a, 0xa1, 0xcd, 0xfc, 0x24, 0xa2, 0x78, 0x12,
            0xc8, 0x0d, 0x56, 0xbb, 0x97, 0x7a, 0x42, 0x5f, 0x5a, 0xa1, 0xcd, 0xfc, 0x24, 0xa2, 0x78, 0x12,
            0x30, 0x04, 0xb9, 0x9f, 0x6f, 0xfa, 0x3b, 0xb7, 0x73, 0xb1, 0x75, 0xa5, 0x23, 0x5d, 0xcb, 0x93
            
        ])
        
        let key = key16bytes + key16bytes
        let pmdSecret = try PmdSecret(strategy: PmdSecret.SecurityStrategy.aes256, key: key)
        
        //Act
        let decrypted = try pmdSecret.decryptArray(cipherArray: chipper)
        
        //Assert
        XCTAssertEqual(expectedData, decrypted)
    }

    func testPmdSecretGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadPmdSecretGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected PMD secret golden vectors")
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("060100", PmdSecretRuntimePlanner.settingsHex(strategy: "NONE", keyHex: ""))
        XCTAssertEqual("0a", PmdSecretRuntimePlanner.decryptHex(strategy: "NONE", keyHex: "", cipherHex: "0a"))
        XCTAssertEqual("AES128", PmdSecretRuntimePlanner.strategyName(strategyByte: 2))
        #endif

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
            let platformExpectations = vector["platformExpectations"] as? [String: Any]
            let iosExpectations = platformExpectations?["ios"] as? [String: Any]
            let operation = try XCTUnwrap(input["operation"] as? String, id)

            switch operation {
            case "serialize":
                let secret = try PmdSecret(strategy: PmdSecret.SecurityStrategy(vectorName: try XCTUnwrap(input["strategy"] as? String, id)), key: try Data(hexString: try XCTUnwrap(input["keyHex"] as? String, id)))
                let expectedSerialized = try Data(hexString: try XCTUnwrap(expected["serializedHex"] as? String, id))
                XCTAssertEqual(expectedSerialized, secret.serializeToPmdSettings(), id)
            case "decrypt":
                let secret = try PmdSecret(strategy: PmdSecret.SecurityStrategy(vectorName: try XCTUnwrap(input["strategy"] as? String, id)), key: try Data(hexString: try XCTUnwrap(input["keyHex"] as? String, id)))
                let cipher = try Data(hexString: try XCTUnwrap(input["cipherHex"] as? String, id))
                let expectedDecrypted = try Data(hexString: try XCTUnwrap(expected["decryptedHex"] as? String, id))
                XCTAssertEqual(expectedDecrypted, try secret.decryptArray(cipherArray: cipher), id)
            case "construct":
                let strategy = PmdSecret.SecurityStrategy(vectorName: try XCTUnwrap(input["strategy"] as? String, id))
                let key = try Data(hexString: try XCTUnwrap(input["keyHex"] as? String, id))
                if let constructorError = iosExpectations?["constructorError"] as? String {
                    assertConstructorError(constructorError, id: id) {
                        _ = try PmdSecret(strategy: strategy, key: key)
                    }
                } else {
                    _ = try PmdSecret(strategy: strategy, key: key)
                }
            case "fromByte":
                let strategyByte = try XCTUnwrap(try Data(hexString: try XCTUnwrap(input["strategyByteHex"] as? String, id)).first, id)
                if let strategyError = iosExpectations?["strategyError"] as? String {
                    assertStrategyError(strategyError, id: id) {
                        _ = try PmdSecret.SecurityStrategy.fromByte(strategyByte: strategyByte)
                    }
                } else {
                    XCTAssertEqual(PmdSecret.SecurityStrategy(vectorName: try XCTUnwrap(expected["strategy"] as? String, id)), try PmdSecret.SecurityStrategy.fromByte(strategyByte: strategyByte), id)
                }
            default:
                XCTFail("Unsupported operation in \(id): \(operation)")
            }
        }
    }

    func testPmdSecretGoldenVectorsFollowNeutralKmpVectorShape() throws {
        for vector in try loadPmdSecretGoldenVectors() {
            let id = vector["id"] as? String ?? "unknown-vector"

            XCTAssertNotNil(vector["area"], id)
            XCTAssertNotNil(vector["case"], id)
            XCTAssertNotNil(vector["source"], id)
            XCTAssertNotNil(vector["input"], id)
            XCTAssertNotNil(vector["expected"], id)
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            XCTAssertNotNil(platforms["android"], id)
            XCTAssertNotNil(platforms["ios"], id)
            XCTAssertNotNil(platforms["common"], id)
        }
    }

    func testPmdSecretReadinessManifestPinsSecretStrategyOwnership() throws {
        let manifest = try loadPmdSecretReadinessManifest()
        let input = try XCTUnwrap(manifest["input"] as? [String: Any])
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any])
        let platforms = try XCTUnwrap(manifest["platforms"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        let policyPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])

        XCTAssertEqual("pmd-secret-readiness", manifest["id"] as? String)
        XCTAssertEqual("pmdSecretReadiness", input["kind"] as? String)
        XCTAssertEqual(pmdSecretReadinessPolicyVectorPaths, policyPaths)
        XCTAssertEqual(pmdSecretReadinessBehaviorFamilies, requiredFamilies)
        XCTAssertEqual(pmdSecretReadinessBehaviorFamilies, coveredFamilies)
        XCTAssertEqual(pmdSecretReadinessDecision, expected["commonDecision"] as? String)
        XCTAssertEqual(["com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSecretTest"], try XCTUnwrap(consumerTests["android"] as? [String]))
        XCTAssertEqual(["PmdSecretTest"], try XCTUnwrap(consumerTests["ios"] as? [String]))
        XCTAssertEqual(["com.polar.sharedtest.PmdSecretCommonPolicyTest"], try XCTUnwrap(consumerTests["commonPrototype"] as? [String]))
        XCTAssertEqual(platforms["android"] as? Bool, true)
        XCTAssertEqual(platforms["ios"] as? Bool, true)
        XCTAssertEqual(platforms["common"] as? Bool, true)
    }

    private func assertConstructorError(_ expectedError: String, id: String, operation: () throws -> Void) {
        XCTAssertThrowsError(try operation(), id) { error in
            switch expectedError {
            case "gattSecurityError":
                guard case BleGattException.gattSecurityError = error else {
                    return XCTFail("Expected gattSecurityError for \(id), got \(error)")
                }
            default:
                XCTFail("Unsupported constructor error expectation in \(id): \(expectedError)")
            }
        }
    }

    private func assertStrategyError(_ expectedError: String, id: String, operation: () throws -> Void) {
        XCTAssertThrowsError(try operation(), id) { error in
            switch expectedError {
            case "gattSecurityError":
                guard case BleGattException.gattSecurityError = error else {
                    return XCTFail("Expected gattSecurityError for \(id), got \(error)")
                }
            default:
                XCTFail("Unsupported strategy error expectation in \(id): \(expectedError)")
            }
        }
    }

    private func loadPmdSecretGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/pmd")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("secret-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                (vector["input"] as? [String: Any])?["kind"] as? String != "pmdSecretReadiness"
            }
    }

    private func loadPmdSecretReadinessManifest() throws -> [String: Any] {
        let manifestUrl = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/pmd/secret-readiness.json")
        let data = try Data(contentsOf: manifestUrl)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], manifestUrl.path)
    }

}

private extension PmdSecret.SecurityStrategy {
    init(vectorName: String) {
        switch vectorName {
        case "NONE":
            self = .none
        case "XOR":
            self = .xor
        case "AES128":
            self = .aes128
        case "AES256":
            self = .aes256
        default:
            fatalError("Unsupported PMD secret strategy vector name \(vectorName)")
        }
    }
}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "PmdSecretTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "PmdSecretTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }
}

private let pmdSecretReadinessPolicyVectorPaths = [
    "protocol/pmd/secret-decrypt-aes128.json",
    "protocol/pmd/secret-decrypt-aes256.json",
    "protocol/pmd/secret-decrypt-none.json",
    "protocol/pmd/secret-decrypt-xor.json",
    "protocol/pmd/secret-invalid-aes128-short-key.json",
    "protocol/pmd/secret-invalid-aes256-short-key.json",
    "protocol/pmd/secret-invalid-none-nonempty-key.json",
    "protocol/pmd/secret-invalid-xor-empty-key.json",
    "protocol/pmd/secret-serialization-aes128.json",
    "protocol/pmd/secret-serialization-aes256.json",
    "protocol/pmd/secret-serialization-none.json",
    "protocol/pmd/secret-serialization-xor.json",
    "protocol/pmd/secret-strategy-from-byte-known.json",
    "protocol/pmd/secret-strategy-from-byte-unknown.json"
]

private let pmdSecretReadinessBehaviorFamilies = [
    "security-strategy-byte-mapping",
    "unknown-security-strategy-rejection",
    "security-setting-serialization",
    "none-key-validation",
    "xor-key-validation",
    "aes128-key-validation",
    "aes256-key-validation",
    "none-decryption-policy",
    "xor-decryption-policy",
    "shared-none-xor-production-decryption",
    "aes-fixture-pinning",
    "aes-block-alignment-gate",
    "shared-common-aes-production-decryption",
    "platform-pmd-secret-vector-reference-gate",
    "compile-verification-gate"
]

private let pmdSecretReadinessDecision = "PMD secret strategy shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS PMD secret tests continue to reference the same vectors, security strategy byte mapping, unknown strategy rejection, SECURITY setting serialization, NONE/XOR/AES key validation, shared production NONE/XOR decryption, AES fixture pinning, AES block-alignment gating, shared common AES production decryption, and compile verification remain explicit before remaining fallback removal moves."
