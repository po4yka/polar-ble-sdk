///  Copyright © 2022 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class PmdSettingTest: XCTestCase {
    func testPmdSettingsWithRange() {
        //Arrange
        let bytes = Data([0x00, 0x01, 0x34, 0x00, 0x01, 0x01, 0x10, 0x00, 0x02, 0x04, 0xF5, 0x00, 0xF4, 0x01, 0xE8, 0x03, 0xD0, 0x07, 0x04, 0x01, 0x03])
        // Parameters
        // Setting Type : 00 (Sample Rate)
        // array_length : 01
        // array of settings values: 34 00 (52Hz)
        let sampleRate: UInt32 = 52
        //Setting Type : 01 (Resolution)
        // array_length : 01
        // array of settings values: 10 00 (16)
        let resolution: UInt32 = 16
        // Setting Type : 02 (Range)
        // array_length : 04
        // array of settings values: F5 00 (245)
        let range1: UInt32 = 245
        // array of settings values: F4 01 (500)
        let range2: UInt32 = 500
        // array of settings values: E8 03 (1000)
        let range3: UInt32 = 1000
        // array of settings values: D0 07 (2000)
        let range4: UInt32 = 2000
        // Setting Type : 04 (Channels)
        // array_length : 01
        // array of settings values: 03 (3 Channels)
        let channels: UInt32 = 3
        let numberOfSettings = 4
        
        //Act
        var pmdSetting: PmdSetting
        do {
            pmdSetting = try PmdSetting(bytes)
        } catch {
            XCTFail(error.localizedDescription); return
        }
        
        // Assert
        XCTAssertEqual(numberOfSettings, pmdSetting.settings.count)
        
        XCTAssertEqual(sampleRate, pmdSetting.settings[PmdSetting.PmdSettingType.sampleRate]!.first!)
        
        XCTAssertEqual(1, pmdSetting.settings[PmdSetting.PmdSettingType.sampleRate]!.count)
        
        XCTAssertEqual(resolution, pmdSetting.settings[PmdSetting.PmdSettingType.resolution]!.first!)
        
        XCTAssertEqual(1, pmdSetting.settings[PmdSetting.PmdSettingType.resolution]!.count)
        
        XCTAssertTrue(pmdSetting.settings[PmdSetting.PmdSettingType.range]!.contains(range1))
        XCTAssertTrue(pmdSetting.settings[PmdSetting.PmdSettingType.range]!.contains(range2))
        XCTAssertTrue(pmdSetting.settings[PmdSetting.PmdSettingType.range]!.contains(range3))
        XCTAssertTrue(pmdSetting.settings[PmdSetting.PmdSettingType.range]!.contains(range4))
        XCTAssertEqual(4, pmdSetting.settings[PmdSetting.PmdSettingType.range]!.count)
        
        XCTAssertEqual(channels, pmdSetting.settings[PmdSetting.PmdSettingType.channels]!.first!)
        
        XCTAssertEqual(1,  pmdSetting.settings[PmdSetting.PmdSettingType.channels]!.count)
        
        XCTAssertNil(pmdSetting.settings[PmdSetting.PmdSettingType.rangeMilliUnit])
        XCTAssertNil(pmdSetting.settings[PmdSetting.PmdSettingType.factor])
    }
    
    func testPmdSelectedSerialization() {
        //Arrange
        var selected = [PmdSetting.PmdSettingType : UInt32]()
        let sampleRate: UInt32 = 0xFFFF
        selected[PmdSetting.PmdSettingType.sampleRate] = sampleRate
        let resolution: UInt32 = 0
        selected[PmdSetting.PmdSettingType.resolution] = resolution
        let range: UInt32 = 15
        selected[PmdSetting.PmdSettingType.range] = range
        let rangeMilliUnit = UInt32.max
        selected[PmdSetting.PmdSettingType.rangeMilliUnit] = rangeMilliUnit
        let channels: UInt32 = 4
        selected[PmdSetting.PmdSettingType.channels] = channels
        let factor: UInt32 = 15
        selected[PmdSetting.PmdSettingType.factor] = factor
        let numberOfSettings = 5
        
        //Act
        let settingsFromSelected = PmdSetting.init(selected)
        let serializedSelected = settingsFromSelected.serialize()
        var settings: PmdSetting
        do {
            settings = try PmdSetting(serializedSelected)
        } catch {
            XCTFail(error.localizedDescription); return
        }
        
        //Assert
        XCTAssertEqual(numberOfSettings, settings.settings.count)
        XCTAssertTrue(settings.settings[PmdSetting.PmdSettingType.sampleRate]!.contains(sampleRate))
        XCTAssertEqual(1, settings.settings[PmdSetting.PmdSettingType.sampleRate]!.count)
        XCTAssertTrue(settings.settings[PmdSetting.PmdSettingType.resolution]!.contains(resolution))
        XCTAssertTrue(settings.settings[PmdSetting.PmdSettingType.range]!.contains(range));
        XCTAssertTrue(settings.settings[PmdSetting.PmdSettingType.rangeMilliUnit]!.contains(rangeMilliUnit))
        XCTAssertTrue(settings.settings[PmdSetting.PmdSettingType.channels]!.contains(channels))
        XCTAssertNil(settings.settings[PmdSetting.PmdSettingType.factor])
    }
    
    func testPmdSetting() {
        
        // Arrange
        let data = Data([PmdSetting.PmdSettingType.rangeMilliUnit.rawValue,
                         0x02,0xFF,0xFF,0xFF,0xFF,0xFF,0x00,0x00,0x00,
                         PmdSetting.PmdSettingType.resolution.rawValue,
                         0x01,0x0E,0x00])
        
        // Act
        var setting: PmdSetting
        do {
            setting = try PmdSetting(data)
        } catch {
            XCTFail(error.localizedDescription); return
        }
        
        // Assert
        XCTAssertEqual(setting.settings.count, 2)
        XCTAssertNotNil(setting.settings[PmdSetting.PmdSettingType.rangeMilliUnit])
        XCTAssertNotNil(setting.settings[PmdSetting.PmdSettingType.resolution])
        XCTAssertEqual(setting.settings[PmdSetting.PmdSettingType.rangeMilliUnit]!.count, 2)
        XCTAssertEqual(setting.settings[PmdSetting.PmdSettingType.resolution]!.count, 1)
        XCTAssertTrue(setting.settings[PmdSetting.PmdSettingType.rangeMilliUnit]!.contains(0xffffffff))
        XCTAssertTrue(setting.settings[PmdSetting.PmdSettingType.rangeMilliUnit]!.contains(0xff))
        XCTAssertTrue(setting.settings[PmdSetting.PmdSettingType.resolution]!.contains(0x0E))
        
        let serialized = setting.serialize()
        XCTAssertEqual(serialized.count, 10)
    }
    
    func testPmdSettingThrows() {
        
        // Arrange
        // Last byte removed. Too short data should produce error.
        let data = Data([PmdSetting.PmdSettingType.rangeMilliUnit.rawValue,
                         0x02,0xFF,0xFF,0xFF,0xFF,0xFF,0x00,0x00,0x00,
                         PmdSetting.PmdSettingType.resolution.rawValue,
                         0x01,0x0E])
        
        //Act & Assert
        XCTAssertThrowsError(
            try PmdSetting(data))
        { error in
            guard case BlePmdError.invalidPMDData = error else {
                return XCTFail()
            }
        }
    }

    func testPmdSettingGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadPmdSettingGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected PMD setting golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
            if let selected = input["selected"] as? [String: NSNumber] {
                let selectedSettings = Dictionary(uniqueKeysWithValues: selected.map { key, value in
                    (PmdSetting.PmdSettingType(vectorName: key), value.uint32Value)
                })
                let serialized = PmdSetting(selectedSettings).serialize()
                if let expectedEntries = expected["iosSerializedEntriesHex"] as? [String] {
                    XCTAssertEqual(Set(try serializedTlvEntries(serialized).map { $0.hexString }), Set(expectedEntries), id)
                } else {
                    let expectedSerializedHex = try bytes(fromHex: try XCTUnwrap(expected["serializedHex"] as? String, id))
                    XCTAssertEqual(Array(serialized), expectedSerializedHex, id)
                }
                let settings = try PmdSetting(serialized).settings
                if let expectedSettings = expected["settings"] as? [String: [NSNumber]] {
                    assertSettings(expectedSettings, actual: settings, id: id)
                }
                if let missingSettings = expected["missingSettings"] as? [String] {
                    for missingSetting in missingSettings {
                        XCTAssertNil(settings[PmdSetting.PmdSettingType(vectorName: missingSetting)], id)
                    }
                }
                continue
            }
            let hex = try XCTUnwrap(input["hex"] as? String, id)
            let platformExpectations = vector["platformExpectations"] as? [String: Any]
            let iosExpectations = platformExpectations?["ios"] as? [String: Any]
            let data = try Data(hexString: hex)

            if let parseError = iosExpectations?["parseError"] as? String {
                if parseError == "publicInitializerEmptyUnknownSelectionCrash" {
                    XCTAssertEqual(hex, "ff0000", id)
                    continue
                }
                XCTAssertThrowsError(try PmdSetting(data), id) { error in
                    switch parseError {
                    case "invalidPMDData":
                        guard case BlePmdError.invalidPMDData = error else {
                            return XCTFail("Expected invalidPMDData for \(id), got \(error)")
                        }
                    default:
                        XCTFail("Unsupported parse error expectation in \(id): \(parseError)")
                    }
                }
                continue
            }

            let settings = try PmdSetting(data).settings

            if let expectedSettings = expected["settings"] as? [String: [NSNumber]] {
                assertSettings(expectedSettings, actual: settings, id: id)
            }

            if let expectedSettings = iosExpectations?["settings"] as? [String: [NSNumber]] {
                assertSettings(expectedSettings, actual: settings, id: id)
            }

            if let missingSettings = expected["missingSettings"] as? [String] {
                for missingSetting in missingSettings {
                    XCTAssertNil(settings[PmdSetting.PmdSettingType(vectorName: missingSetting)], id)
                }
            }
        }
    }

    func testPmdSettingGoldenVectorsFollowNeutralKmpVectorShape() throws {
        for vector in try loadPmdSettingGoldenVectors() {
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

    func testPmdSettingsReadinessManifestIsPinnedBeforeParserMigration() throws {
        let manifest = try loadPmdSettingsReadinessManifest()
        let input = try XCTUnwrap(manifest["input"] as? [String: Any])
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any])
        let platforms = try XCTUnwrap(manifest["platforms"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        let policyPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])

        XCTAssertEqual("pmd-settings-readiness", manifest["id"] as? String)
        XCTAssertEqual("pmdSettingsReadiness", input["kind"] as? String)
        XCTAssertEqual(pmdSettingsReadinessPolicyVectorPaths, policyPaths)
        XCTAssertEqual(pmdSettingsReadinessBehaviorFamilies, requiredFamilies)
        XCTAssertEqual(pmdSettingsReadinessBehaviorFamilies, coveredFamilies)
        XCTAssertEqual(pmdSettingsReadinessDecision, expected["commonDecision"] as? String)
        XCTAssertEqual(["com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSettingTest"], try XCTUnwrap(consumerTests["android"] as? [String]))
        XCTAssertEqual(["PmdSettingTest"], try XCTUnwrap(consumerTests["ios"] as? [String]))
        XCTAssertEqual(["com.polar.sharedtest.PmdSettingsCommonPolicyTest"], try XCTUnwrap(consumerTests["commonPrototype"] as? [String]))
        XCTAssertEqual(platforms["android"] as? Bool, true)
        XCTAssertEqual(platforms["ios"] as? Bool, true)
        XCTAssertEqual(platforms["common"] as? Bool, true)
    }

    private func assertSettings(
        _ expectedSettings: [String: [NSNumber]],
        actual: [PmdSetting.PmdSettingType: Set<UInt32>],
        id: String
    ) {
        for (typeName, expectedValues) in expectedSettings {
            let type = PmdSetting.PmdSettingType(vectorName: typeName)
            let expectedSet = Set(expectedValues.map { $0.uint32Value })
            XCTAssertEqual(expectedSet, actual[type], id)
        }
    }

    private func loadPmdSettingGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/pmd")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("settings-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                (vector["input"] as? [String: Any])?["kind"] as? String != "pmdSettingsReadiness"
            }
    }

    private func loadPmdSettingsReadinessManifest() throws -> [String: Any] {
        let manifestUrl = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/pmd/settings-readiness.json")
        let data = try Data(contentsOf: manifestUrl)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], manifestUrl.path)
    }

}

private extension PmdSetting.PmdSettingType {
    init(vectorName: String) {
        switch vectorName {
        case "SAMPLE_RATE":
            self = .sampleRate
        case "RESOLUTION":
            self = .resolution
        case "RANGE":
            self = .range
        case "RANGE_MILLIUNIT":
            self = .rangeMilliUnit
        case "CHANNELS":
            self = .channels
        case "FACTOR":
            self = .factor
        case "SECURITY":
            self = .security
        default:
            self = .unknown
        }
    }
}

private let pmdSettingsReadinessPolicyVectorPaths = [
    "protocol/pmd/settings-basic-range.json",
    "protocol/pmd/settings-duplicate-sample-rate-factor.json",
    "protocol/pmd/settings-range-milliunit-platform-difference.json",
    "protocol/pmd/settings-security-value-platform-error.json",
    "protocol/pmd/settings-selected-serialization-max-values.json",
    "protocol/pmd/settings-truncated-resolution-platform-difference.json",
    "protocol/pmd/settings-unknown-type-platform-difference.json"
]

private let pmdSettingsReadinessBehaviorFamilies = [
    "basic-settings-parsing",
    "duplicate-setting-overwrite",
    "factor-setting-parsing",
    "selected-setting-serialization",
    "selected-factor-skip-policy",
    "range-milliunit-signedness-platform-decision",
    "security-setting-platform-error-policy",
    "truncated-value-platform-decision",
    "unknown-setting-type-platform-decision",
    "platform-pmd-settings-vector-reference-gate",
    "compile-verification-gate"
]

private let pmdSettingsReadinessDecision = "PMD settings migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS PMD settings tests continue to reference the same vectors, baseline parsing, duplicate overwrite behavior, FACTOR parsing, selected-setting serialization, skipped FACTOR serialization, RANGE_MILLIUNIT signedness platform decisions, SECURITY setting parse policy, truncated-value policy, unknown-setting-type policy, and compile verification remain explicit before production PMD settings logic moves."

private extension Data {
    var hexString: String {
        map { String(format: "%02x", $0) }.joined()
    }

    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "PmdSettingTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "PmdSettingTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }
}

private func bytes(fromHex hexString: String) throws -> [UInt8] {
    guard hexString.count.isMultiple(of: 2) else {
        throw NSError(domain: "PmdSettingTest", code: 4, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
    }
    var bytes: [UInt8] = []
    var index = hexString.startIndex
    while index < hexString.endIndex {
        let nextIndex = hexString.index(index, offsetBy: 2)
        let byteString = String(hexString[index..<nextIndex])
        guard let byte = UInt8(byteString, radix: 16) else {
            throw NSError(domain: "PmdSettingTest", code: 5, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
        }
        bytes.append(byte)
        index = nextIndex
    }
    return bytes
}

private func serializedTlvEntries(_ data: Data) throws -> [Data] {
    var entries: [Data] = []
    var offset = 0
    while offset < data.count {
        guard offset + 2 <= data.count else {
            throw NSError(domain: "PmdSettingTest", code: 6, userInfo: [NSLocalizedDescriptionKey: "Serialized PMD setting entry header is truncated"])
        }
        let type = PmdSetting.PmdSettingType(rawValue: data[offset]) ?? .unknown
        let count = Int(data[offset + 1])
        let valueSize: Int
        switch type {
        case .sampleRate, .resolution, .range:
            valueSize = 2
        case .rangeMilliUnit:
            valueSize = 4
        case .channels:
            valueSize = 1
        case .factor:
            valueSize = 4
        case .security:
            valueSize = 16
        case .unknown:
            throw NSError(domain: "PmdSettingTest", code: 7, userInfo: [NSLocalizedDescriptionKey: "Serialized PMD setting entry has unknown type"])
        }
        let end = offset + 2 + count * valueSize
        guard end <= data.count else {
            throw NSError(domain: "PmdSettingTest", code: 8, userInfo: [NSLocalizedDescriptionKey: "Serialized PMD setting entry payload is truncated"])
        }
        entries.append(data.subdata(in: offset..<end))
        offset = end
    }
    return entries
}
