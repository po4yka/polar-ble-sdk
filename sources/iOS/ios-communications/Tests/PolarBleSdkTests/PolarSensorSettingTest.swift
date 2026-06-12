//  Copyright © 2025 Polar. All rights reserved.

import XCTest
@testable import PolarBleSdk
#if canImport(PolarBleSdkShared)
import PolarBleSdkShared
#endif

class PolarSensorSettingTests: XCTestCase {
    
    func testInitWithValidValues() throws {
        // Arrange
        let settings: [PolarSensorSetting.SettingType: UInt32] = [
            .sampleRate: 10,
            .resolution: 16
        ]
        
        // Act
        let sensorSetting = try PolarSensorSetting(settings)
        
        // Assert
        XCTAssertEqual(sensorSetting.settings[.sampleRate], [10])
        XCTAssertEqual(sensorSetting.settings[.resolution], [16])
    }
    
    func testInitWithZeroValueThrowsError() throws {
        // Arrange
        let settings: [PolarSensorSetting.SettingType: UInt32] = [
            .sampleRate: 0
        ]
        
        // Act & Assert
        XCTAssertThrowsError(try PolarSensorSetting(settings)) { error in
            guard case PolarErrors.invalidSensorSettingValue(let type, let value) = error else {
                XCTFail("Expected invalidSensorSettingValue error")
                return
            }
            XCTAssertEqual(type, .sampleRate)
            XCTAssertEqual(value, 0)
        }
    }
    
    func testInitWithSetOfValues() throws {
        // Arrange
        let input: [PolarSensorSetting.SettingType: Set<UInt32>] = [
            .range: [1, 2, 3]
        ]
        
        // Act
        let sensorSetting = PolarSensorSetting(input)
        
        // Assert
        XCTAssertEqual(sensorSetting.settings[.range], [1, 2, 3])
    }

    func testPmdSettingTypeMappingUsesSharedKnownCodesAndPreservesPublicKeys() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("SAMPLE_RATE", PolarSensorSettingRuntimePlanner.pmdSettingTypeName(code: 0))
        XCTAssertEqual("RESOLUTION", PolarSensorSettingRuntimePlanner.pmdSettingTypeName(code: 1))
        XCTAssertEqual("RANGE", PolarSensorSettingRuntimePlanner.pmdSettingTypeName(code: 2))
        XCTAssertEqual("RANGE_MILLIUNIT", PolarSensorSettingRuntimePlanner.pmdSettingTypeName(code: 3))
        XCTAssertEqual("CHANNELS", PolarSensorSettingRuntimePlanner.pmdSettingTypeName(code: 4))
        XCTAssertEqual(4, PolarSensorSettingRuntimePlanner.pmdSettingTypeCode(name: "CHANNELS"))
        XCTAssertNil(PolarSensorSettingRuntimePlanner.pmdSettingTypeName(code: 255))
        XCTAssertNil(PolarSensorSettingRuntimePlanner.pmdSettingTypeCode(name: "UNKNOWN"))
        #endif

        let sensorSetting = PolarSensorSetting([
            .sampleRate: [52],
            .resolution: [16],
            .range: [2000],
            .rangeMilliunit: [4000],
            .channels: [3]
        ])

        XCTAssertEqual([52], sensorSetting.settings[.sampleRate])
        XCTAssertEqual([16], sensorSetting.settings[.resolution])
        XCTAssertEqual([2000], sensorSetting.settings[.range])
        XCTAssertEqual([4000], sensorSetting.settings[.rangeMilliunit])
        XCTAssertEqual([3], sensorSetting.settings[.channels])

        let pmdSetting = sensorSetting.map2PmdSetting()

        XCTAssertEqual(52, pmdSetting.selected[.sampleRate])
        XCTAssertEqual(16, pmdSetting.selected[.resolution])
        XCTAssertEqual(2000, pmdSetting.selected[.range])
        XCTAssertEqual(4000, pmdSetting.selected[.rangeMilliUnit])
        XCTAssertEqual(3, pmdSetting.selected[.channels])
    }
}
