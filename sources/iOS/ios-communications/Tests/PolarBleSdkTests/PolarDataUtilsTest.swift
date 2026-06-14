// Copyright © 2026 Polar. All rights reserved.

import XCTest
import Foundation
@testable import PolarBleSdk

private let AVAILABLE_DATA_TYPES_READINESS_COMMON_DECISION = "Available-data-types shared ownership remains valid while this readiness manifest is executable from shared commonTest, Android and iOS data utility tests continue to pin offline and online PMD-to-public mapping, HR-service availability projection, iOS location/pressure filters, Android full public surface, public-to-PMD measurement lookup, unknown public type boundaries, PMD feature-read boundaries, HR-service discovery boundaries, public error mapping boundaries, platform vector references, and compile verification before broader availability facade behavior moves."
private let AVAILABLE_DATA_TYPES_READINESS_FAMILIES = ["offline-pmd-to-public-mapping", "online-pmd-to-public-mapping", "hr-service-availability-projection", "ios-location-pressure-filter-boundary", "android-full-surface-boundary", "public-to-pmd-measurement-lookup", "unknown-public-type-null-boundary", "pmd-feature-read-platform-boundary", "hr-service-discovery-platform-boundary", "public-error-mapping-boundary", "platform-available-data-type-vector-reference-gate", "compile-verification-gate"]

final class PolarDataUtilsTest: XCTestCase {

    func testPolarBleSdkIosTargetRequiresLinkedSharedFramework() {
        XCTAssertTrue(PolarSharedFrameworkLinkGuard.isSharedFrameworkRequired)
        XCTAssertTrue(PolarSharedFrameworkLinkGuard.isSharedFrameworkLinked)
    }

    // MARK: - mapToPmdClientMeasurementType

    func testMapToPmdClientMeasurementType_ecg() {
        XCTAssertEqual(.ecg, PolarDataUtils.mapToPmdClientMeasurementType(from: .ecg))
    }

    func testMapToPmdClientMeasurementType_acc() {
        XCTAssertEqual(.acc, PolarDataUtils.mapToPmdClientMeasurementType(from: .acc))
    }

    func testMapToPmdClientMeasurementType_ppg() {
        XCTAssertEqual(.ppg, PolarDataUtils.mapToPmdClientMeasurementType(from: .ppg))
    }

    func testMapToPmdClientMeasurementType_ppi() {
        XCTAssertEqual(.ppi, PolarDataUtils.mapToPmdClientMeasurementType(from: .ppi))
    }

    func testMapToPmdClientMeasurementType_gyro() {
        XCTAssertEqual(.gyro, PolarDataUtils.mapToPmdClientMeasurementType(from: .gyro))
    }

    func testMapToPmdClientMeasurementType_magnetometer() {
        XCTAssertEqual(.mgn, PolarDataUtils.mapToPmdClientMeasurementType(from: .magnetometer))
    }

    func testMapToPmdClientMeasurementType_hr() {
        XCTAssertEqual(.offline_hr, PolarDataUtils.mapToPmdClientMeasurementType(from: .hr))
    }

    func testMapToPmdClientMeasurementType_temperature() {
        XCTAssertEqual(.temperature, PolarDataUtils.mapToPmdClientMeasurementType(from: .temperature))
    }

    func testMapToPmdClientMeasurementType_skinTemperature() {
        XCTAssertEqual(.skinTemperature, PolarDataUtils.mapToPmdClientMeasurementType(from: .skinTemperature))
    }

    func testMapToPmdClientMeasurementType_pressure() {
        XCTAssertEqual(.pressure, PolarDataUtils.mapToPmdClientMeasurementType(from: .pressure))
    }

    func testMapToPmdClientMeasurementType_usesSharedPlannerNames() {
        XCTAssertEqual("MAG", PolarPmdMeasurementRuntimePlanner.pmdMeasurementTypeName(forPublicDataTypeName: "MAGNETOMETER"))
        XCTAssertEqual("OFFLINE_HR", PolarPmdMeasurementRuntimePlanner.pmdMeasurementTypeName(forPublicDataTypeName: "HR"))
        XCTAssertEqual("SKIN_TEMP", PolarPmdMeasurementRuntimePlanner.pmdMeasurementTypeName(forPublicDataTypeName: "SKIN_TEMPERATURE"))
    }

    // MARK: - mapToPolarFeature – all supported types

    func testMapToPolarFeature_ecg() throws {
        XCTAssertEqual(.ecg, try PolarDataUtils.mapToPolarFeature(from: .ecg))
    }

    func testMapToPolarFeature_acc() throws {
        XCTAssertEqual(.acc, try PolarDataUtils.mapToPolarFeature(from: .acc))
    }

    func testMapToPolarFeature_ppg() throws {
        XCTAssertEqual(.ppg, try PolarDataUtils.mapToPolarFeature(from: .ppg))
    }

    func testMapToPolarFeature_ppi() throws {
        XCTAssertEqual(.ppi, try PolarDataUtils.mapToPolarFeature(from: .ppi))
    }

    func testMapToPolarFeature_gyro() throws {
        XCTAssertEqual(.gyro, try PolarDataUtils.mapToPolarFeature(from: .gyro))
    }

    func testMapToPolarFeature_mgn() throws {
        XCTAssertEqual(.magnetometer, try PolarDataUtils.mapToPolarFeature(from: .mgn))
    }

    func testMapToPolarFeature_offline_hr() throws {
        XCTAssertEqual(.hr, try PolarDataUtils.mapToPolarFeature(from: .offline_hr))
    }

    func testMapToPolarFeature_temperature() throws {
        XCTAssertEqual(.temperature, try PolarDataUtils.mapToPolarFeature(from: .temperature))
    }

    func testMapToPolarFeature_pressure() throws {
        XCTAssertEqual(.pressure, try PolarDataUtils.mapToPolarFeature(from: .pressure))
    }

    func testMapToPolarFeature_skinTemperature() throws {
        XCTAssertEqual(.skinTemperature, try PolarDataUtils.mapToPolarFeature(from: .skinTemperature))
    }

    func testMapToPolarFeatureUsesSharedPmdMeasurementNamesWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("ECG", PolarPmdMeasurementRuntimePlanner.measurementTypeName(id: Int(PmdMeasurementType.ecg.rawValue)))
        XCTAssertEqual("ACC", PolarPmdMeasurementRuntimePlanner.measurementTypeName(id: Int(PmdMeasurementType.acc.rawValue)))
        XCTAssertEqual("MAG", PolarPmdMeasurementRuntimePlanner.measurementTypeName(id: Int(PmdMeasurementType.mgn.rawValue)))
        XCTAssertEqual("OFFLINE_HR", PolarPmdMeasurementRuntimePlanner.measurementTypeName(id: Int(PmdMeasurementType.offline_hr.rawValue)))
        #endif
        XCTAssertEqual(.ecg, try PolarDataUtils.mapToPolarFeature(from: .ecg))
        XCTAssertEqual(.acc, try PolarDataUtils.mapToPolarFeature(from: .acc))
        XCTAssertEqual(.magnetometer, try PolarDataUtils.mapToPolarFeature(from: .mgn))
        XCTAssertEqual(.hr, try PolarDataUtils.mapToPolarFeature(from: .offline_hr))
    }

    func testPmdControlPointResponseUsesSharedBackedMaskedMeasurementType() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("ACC", PmdControlPointRuntimePlanner.measurementTypeName(id: 0xC2))
        #endif
        let response = PmdControlPointResponse(Data([PmdControlPointResponse.CONTROL_POINT_RESPONSE_CODE, 0x01, 0xC2, UInt8(PmdResponseCode.success.rawValue), 0x00]))
        XCTAssertEqual(.acc, response.type)
        XCTAssertEqual(.success, response.errorCode)
    }

    // MARK: - mapToPolarFeature – unsupported types throw

    func testMapToPolarFeature_sdkMode_throws() {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("SDK_MODE", PolarPmdMeasurementRuntimePlanner.measurementTypeName(id: Int(PmdMeasurementType.sdkMode.rawValue)))
        #endif
        XCTAssertThrowsError(try PolarDataUtils.mapToPolarFeature(from: .sdkMode)) { error in
            guard case PolarErrors.polarBleSdkInternalException = error else {
                return XCTFail("Expected polarBleSdkInternalException, got \(error)")
            }
        }
    }

    func testMapToPolarFeature_location_throws() {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("LOCATION", PolarPmdMeasurementRuntimePlanner.measurementTypeName(id: Int(PmdMeasurementType.location.rawValue)))
        #endif
        XCTAssertThrowsError(try PolarDataUtils.mapToPolarFeature(from: .location)) { error in
            guard case PolarErrors.polarBleSdkInternalException = error else {
                return XCTFail("Expected polarBleSdkInternalException, got \(error)")
            }
        }
    }

    // MARK: - mapToPmdClientMeasurementType / mapToPolarFeature round-trip

    func testRoundTrip_polarToPmdToPolar_allBidirectionalTypes() throws {
        let bidirectional: [PolarDeviceDataType] = [
            .ecg, .acc, .ppg, .ppi, .gyro, .magnetometer, .hr,
            .temperature, .pressure, .skinTemperature
        ]
        for original in bidirectional {
            let pmd = PolarDataUtils.mapToPmdClientMeasurementType(from: original)
            let back = try PolarDataUtils.mapToPolarFeature(from: pmd)
            XCTAssertEqual(original, back, "Round-trip failed for \(original)")
        }
    }

    func testAvailableDataTypes_useSharedPlannerWithIosPublicSurfaceFilters() throws {
        let pmdTypes: Set<PmdMeasurementType> = [.ecg, .acc, .ppg, .ppi, .gyro, .mgn, .pressure, .temperature, .skinTemperature, .offline_hr]

        XCTAssertEqual(
            PolarPmdMeasurementRuntimePlanner.availableOfflineRecordingDataTypes(from: pmdTypes),
            [.ecg, .acc, .ppg, .ppi, .gyro, .magnetometer, .temperature, .skinTemperature, .hr]
        )
        XCTAssertEqual(
            PolarPmdMeasurementRuntimePlanner.availableOnlineStreamDataTypes(from: pmdTypes, hasHrService: true),
            [.hr, .ecg, .acc, .ppg, .ppi, .gyro, .magnetometer, .pressure, .temperature, .skinTemperature]
        )
        XCTAssertFalse(PolarPmdMeasurementRuntimePlanner.availableOnlineStreamDataTypes(from: pmdTypes, hasHrService: false).contains(.hr))
        XCTAssertEqual(PolarPmdMeasurementRuntimePlanner.availableHrServiceDataTypes(hasHrService: true), [.hr])
        XCTAssertEqual(PolarPmdMeasurementRuntimePlanner.availableHrServiceDataTypes(hasHrService: false), [])
    }

    func testAvailableDataTypesReadinessManifestPinsAvailabilityOwnership() throws {
        let vectorURL = try GoldenVectorTestData.repositoryRoot().appendingPathComponent("testdata/golden-vectors/sdk/available-data-types/available-data-types-readiness.json")
        let manifest = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: vectorURL)) as? [String: Any])
        let input = try XCTUnwrap(manifest["input"] as? [String: Any])
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        let prototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any])

        XCTAssertEqual(manifest["id"] as? String, "available-data-types-readiness")
        XCTAssertEqual(input["kind"] as? String, "availableDataTypesReadiness")
        XCTAssertEqual(requiredFamilies, AVAILABLE_DATA_TYPES_READINESS_FAMILIES)
        XCTAssertEqual(expected["sharedOwnershipStatus"] as? String, "coveredBySharedContractCharacterization")
        XCTAssertEqual(coveredFamilies, AVAILABLE_DATA_TYPES_READINESS_FAMILIES)
        XCTAssertEqual(expected["commonDecision"] as? String, AVAILABLE_DATA_TYPES_READINESS_COMMON_DECISION)
        XCTAssertEqual(prototype["status"] as? String, "executable shared commonTest available-data-types planning guard")
        XCTAssertEqual(prototype["reason"] as? String, "Declared because this vector is consumed by shared commonTest and platform adapter tests before available-data-types runtime delegation moves further into shared.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.sdk.impl.utils.PolarRuntimePlannerAdapterTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["PolarDataUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.AvailableDataTypesCommonPolicyTest"])
    }

    func testFeatureAvailabilityPreconditions_useSharedPlannerForServiceAndCapabilityGuards() throws {
        XCTAssertTrue(
            PolarFeatureAvailabilityRuntimePlanner.preconditionsMet(
                featureName: "feature_polar_firmware_update",
                discoveredServiceNames: ["PSFTP"],
                capabilityNames: ["FIRMWARE_UPDATE"]
            )
        )
        XCTAssertFalse(
            PolarFeatureAvailabilityRuntimePlanner.preconditionsMet(
                featureName: "feature_polar_firmware_update",
                discoveredServiceNames: ["PSFTP"],
                capabilityNames: []
            )
        )
        XCTAssertFalse(
            PolarFeatureAvailabilityRuntimePlanner.preconditionsMet(
                featureName: "feature_polar_led_animation",
                discoveredServiceNames: ["PMD"],
                capabilityNames: []
            )
        )
        XCTAssertTrue(
            PolarFeatureAvailabilityRuntimePlanner.preconditionsMet(
                featureName: "feature_polar_watch_faces_configuration",
                discoveredServiceNames: ["PSFTP"],
                capabilityNames: ["NOT_SENSOR"]
            )
        )
    }

    func testFeatureAvailabilityReadinessVector_usesSharedIosRuntimePlanner() throws {
        let vector = try GoldenVectorTestData.loadObject("sdk/feature-availability/feature-availability-readiness.json")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let cases = try XCTUnwrap(input["cases"] as? [[String: Any]])

        XCTAssertEqual("feature-availability-readiness", vector["id"] as? String)
        XCTAssertEqual("featureAvailabilityReadiness", input["kind"] as? String)
        XCTAssertEqual(featureAvailabilityCaseIds, cases.compactMap { $0["id"] as? String })
        for currentCase in cases {
            let caseId = try XCTUnwrap(currentCase["id"] as? String)
            XCTAssertEqual(
                try XCTUnwrap(currentCase["expectedAvailable"] as? Bool),
                PolarFeatureAvailabilityRuntimePlanner.preconditionsMet(
                    featureName: try XCTUnwrap(currentCase["featureName"] as? String),
                    discoveredServiceNames: Set(try XCTUnwrap(currentCase["discoveredServices"] as? [String])),
                    capabilityNames: Set(try XCTUnwrap(currentCase["capabilities"] as? [String]))
                ),
                caseId
            )
        }
        XCTAssertEqual(featureAvailabilityBehaviorFamilies, input["requiredBehaviorFamilies"] as? [String])
        XCTAssertEqual(featureAvailabilityBehaviorFamilies, expected["coveredBehaviorFamilies"] as? [String])
        XCTAssertEqual(featureAvailabilityCommonDecision, expected["commonDecision"] as? String)
    }

    // MARK: - mapToPmdSecret

    func testMapToPmdSecret_validKey_returnsAes128Secret() throws {
        let key = Data(repeating: 0xAB, count: 16)
        let polarSecret = try PolarRecordingSecret(key: key)

        let pmdSecret = try PolarDataUtils.mapToPmdSecret(from: polarSecret)

        XCTAssertEqual(key, pmdSecret.key)
        XCTAssertEqual(PmdSecret.SecurityStrategy.aes128, pmdSecret.strategy)
    }

    func testMapToPmdSecret_keyIsPreservedVerbatim() throws {
        let key = Data([0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07,
                        0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F])
        let polarSecret = try PolarRecordingSecret(key: key)

        let pmdSecret = try PolarDataUtils.mapToPmdSecret(from: polarSecret)

        XCTAssertEqual(key, pmdSecret.key)
    }

    func testPmdSecretSerializationAndStrategyLookupUseSharedPolicyWhenLinked() throws {
        let aesKey = Data((0..<16).map { UInt8($0) })
        let xorKey = Data([0x0F])
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("AES128", PmdSecretRuntimePlanner.strategyName(strategyByte: 2))
        XCTAssertEqual("060102" + aesKey.map { String(format: "%02x", $0) }.joined(), PmdSecretRuntimePlanner.settingsHex(strategy: "AES128", keyHex: aesKey.map { String(format: "%02x", $0) }.joined()))
        XCTAssertEqual("0601010f", PmdSecretRuntimePlanner.settingsHex(strategy: "XOR", keyHex: "0f"))
        #endif
        XCTAssertEqual(.aes128, try PmdSecret.SecurityStrategy.fromByte(strategyByte: 2))
        XCTAssertEqual(Data([0x06, 0x01, 0x02]) + aesKey, try PmdSecret(strategy: .aes128, key: aesKey).serializeToPmdSettings())
        XCTAssertEqual(Data([0x06, 0x01, 0x01, 0x0F]), try PmdSecret(strategy: .xor, key: xorKey).serializeToPmdSettings())
    }

    func testPmdSecretNoneAndXorDecryptUseSharedPolicyWhenLinked() throws {
        #if canImport(PolarBleSdkShared)
        XCTAssertEqual("010203", PmdSecretRuntimePlanner.decryptHex(strategy: "NONE", keyHex: "", cipherHex: "010203"))
        XCTAssertEqual("f0ff", PmdSecretRuntimePlanner.decryptHex(strategy: "XOR", keyHex: "0f", cipherHex: "fff0"))
        #endif
        XCTAssertEqual(Data([0x01, 0x02, 0x03]), try PmdSecret(strategy: .none, key: Data()).decryptArray(cipherArray: Data([0x01, 0x02, 0x03])))
        XCTAssertEqual(Data([0xF0, 0xFF]), try PmdSecret(strategy: .xor, key: Data([0x0F])).decryptArray(cipherArray: Data([0xFF, 0xF0])))
    }

    // MARK: - mapToPmdOfflineTriggerMode (via mapToPmdOfflineTrigger)

    func testMapToPmdOfflineTrigger_triggerDisabled() throws {
        let trigger = PolarOfflineRecordingTrigger(
            triggerMode: .triggerDisabled, triggerFeatures: [:]
        )
        let pmdTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        XCTAssertEqual(.disabled, pmdTrigger.triggerMode)
    }

    func testMapToPmdOfflineTrigger_triggerSystemStart() throws {
        let trigger = PolarOfflineRecordingTrigger(
            triggerMode: .triggerSystemStart, triggerFeatures: [:]
        )
        let pmdTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        XCTAssertEqual(.systemStart, pmdTrigger.triggerMode)
    }

    func testMapToPmdOfflineTrigger_triggerExerciseStart() throws {
        let trigger = PolarOfflineRecordingTrigger(
            triggerMode: .triggerExerciseStart, triggerFeatures: [:]
        )
        let pmdTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        XCTAssertEqual(.exerciseStart, pmdTrigger.triggerMode)
    }

    func testMapToPmdOfflineTrigger_featuresAreMapped() throws {
        let trigger = PolarOfflineRecordingTrigger(
            triggerMode: .triggerSystemStart,
            triggerFeatures: [.acc: nil, .ppg: nil]
        )
        let pmdTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        XCTAssertTrue(pmdTrigger.triggers.keys.contains(.acc))
        XCTAssertTrue(pmdTrigger.triggers.keys.contains(.ppg))
        XCTAssertEqual(2, pmdTrigger.triggers.count)
    }

    func testMapToPmdOfflineTrigger_allFeaturesMarkedEnabled() throws {
        let trigger = PolarOfflineRecordingTrigger(
            triggerMode: .triggerSystemStart,
            triggerFeatures: [.ecg: nil, .gyro: nil]
        )
        let pmdTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        for (_, value) in pmdTrigger.triggers {
            XCTAssertEqual(.enabled, value.status)
        }
    }

    func testMapToPmdOfflineTrigger_emptyFeatures_producesEmptyTriggers() throws {
        let trigger = PolarOfflineRecordingTrigger(
            triggerMode: .triggerDisabled, triggerFeatures: [:]
        )
        let pmdTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: trigger)
        XCTAssertTrue(pmdTrigger.triggers.isEmpty)
    }

    // MARK: - mapToPolarOfflineTrigger (pmd → polar)

    func testMapToPolarOfflineTrigger_disabled() throws {
        let pmd = PmdOfflineTrigger(triggerMode: .disabled, triggers: [:])
        let polar = try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)
        XCTAssertEqual(.triggerDisabled, polar.triggerMode)
    }

    func testMapToPolarOfflineTrigger_systemStart() throws {
        let pmd = PmdOfflineTrigger(triggerMode: .systemStart, triggers: [:])
        let polar = try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)
        XCTAssertEqual(.triggerSystemStart, polar.triggerMode)
    }

    func testMapToPolarOfflineTrigger_exerciseStart() throws {
        let pmd = PmdOfflineTrigger(triggerMode: .exerciseStart, triggers: [:])
        let polar = try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)
        XCTAssertEqual(.triggerExerciseStart, polar.triggerMode)
    }

    func testMapToPolarOfflineTrigger_enabledTriggerIsMapped() throws {
        let triggers: [PmdMeasurementType: (status: PmdOfflineRecTriggerStatus, setting: PmdSetting?)] = [
            .acc: (.enabled, nil),
            .gyro: (.enabled, nil)
        ]
        let pmd = PmdOfflineTrigger(triggerMode: .systemStart, triggers: triggers)
        let polar = try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)
        XCTAssertTrue(polar.triggerFeatures.keys.contains(.acc))
        XCTAssertTrue(polar.triggerFeatures.keys.contains(.gyro))
    }

    func testMapToPolarOfflineTrigger_disabledTriggerIsNotIncluded() throws {
        let triggers: [PmdMeasurementType: (status: PmdOfflineRecTriggerStatus, setting: PmdSetting?)] = [
            .acc: (.enabled, nil),
            .gyro: (.disabled, nil)  // disabled → must not appear in result
        ]
        let pmd = PmdOfflineTrigger(triggerMode: .systemStart, triggers: triggers)
        let polar = try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)
        XCTAssertTrue(polar.triggerFeatures.keys.contains(.acc))
        XCTAssertFalse(polar.triggerFeatures.keys.contains(.gyro))
    }

    func testMapToPolarOfflineTrigger_unsupportedPmdType_throws() {
        let triggers: [PmdMeasurementType: (status: PmdOfflineRecTriggerStatus, setting: PmdSetting?)] = [
            .sdkMode: (.enabled, nil)
        ]
        let pmd = PmdOfflineTrigger(triggerMode: .systemStart, triggers: triggers)
        XCTAssertThrowsError(try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)) { error in
            guard case PolarErrors.polarBleSdkInternalException = error else {
                return XCTFail("Expected polarBleSdkInternalException, got \(error)")
            }
        }
    }

    // MARK: - mapToPmdOfflineTrigger / mapToPolarOfflineTrigger round-trip

    func testRoundTrip_polarTriggerToPmdToPolar() throws {
        let original = PolarOfflineRecordingTrigger(
            triggerMode: .triggerSystemStart,
            triggerFeatures: [.acc: nil, .ecg: nil]
        )
        let pmd = try PolarDataUtils.mapToPmdOfflineTrigger(from: original)
        let back = try PolarDataUtils.mapToPolarOfflineTrigger(from: pmd)

        XCTAssertEqual(original.triggerMode, back.triggerMode)
        XCTAssertTrue(back.triggerFeatures.keys.contains(.acc))
        XCTAssertTrue(back.triggerFeatures.keys.contains(.ecg))
    }

    func testOfflineRecordingTriggerGoldenVectorsMapPolarTriggerToPmdTrigger() throws {
        let vector = try loadOfflineRecordingVector("trigger-mapping.json")
        let vectorInput = try XCTUnwrap(vector["input"] as? [String: Any])
        let testCase = try XCTUnwrap(vectorInput["polarToPmd"] as? [String: Any])
        let input = try XCTUnwrap(testCase["input"] as? [String: Any])
        let expected = try XCTUnwrap(testCase["expected"] as? [String: Any])
        let polarTrigger = PolarOfflineRecordingTrigger(
            triggerMode: try polarTriggerMode(try XCTUnwrap(input["triggerMode"] as? String)),
            triggerFeatures: Dictionary(uniqueKeysWithValues: try XCTUnwrap(input["features"] as? [[String: Any]]).map { feature in
                (try polarDeviceDataType(try XCTUnwrap(feature["type"] as? String)), try polarSensorSetting(feature["selectedSettings"] as? [String: Any]))
            })
        )

        let pmdTrigger = try PolarDataUtils.mapToPmdOfflineTrigger(from: polarTrigger)
        let expectedMode = try XCTUnwrap(expected["triggerMode"] as? [String: Any])

        XCTAssertEqual(pmdTrigger.triggerMode, try pmdTriggerMode(try XCTUnwrap(expectedMode["ios"] as? String)))
        let expectedTriggers = try XCTUnwrap(expected["triggers"] as? [[String: Any]])
        for expectedTrigger in expectedTriggers {
            let type = try pmdMeasurementType(try XCTUnwrap(try XCTUnwrap(expectedTrigger["type"] as? [String: Any])["ios"] as? String))
            let actual = try XCTUnwrap(pmdTrigger.triggers[type], "\(type)")
            XCTAssertEqual(actual.status, try pmdTriggerStatus(try XCTUnwrap(try XCTUnwrap(expectedTrigger["status"] as? [String: Any])["ios"] as? String)))
            try assertSelectedSettings(actual.setting, expected: expectedTrigger["selectedSettings"] as? [String: Any])
        }
        XCTAssertEqual(pmdTrigger.triggers.count, expectedTriggers.count)
    }

    func testOfflineRecordingTriggerGoldenVectorsMapPmdTriggerToPolarTrigger() throws {
        let vector = try loadOfflineRecordingVector("trigger-mapping.json")
        let vectorInput = try XCTUnwrap(vector["input"] as? [String: Any])
        let testCase = try XCTUnwrap(vectorInput["pmdToPolar"] as? [String: Any])
        let input = try XCTUnwrap(testCase["input"] as? [String: Any])
        let expected = try XCTUnwrap(testCase["expected"] as? [String: Any])
        let inputMode = try XCTUnwrap(input["triggerMode"] as? [String: Any])
        let pmdTrigger = PmdOfflineTrigger(
            triggerMode: try pmdTriggerMode(try XCTUnwrap(inputMode["ios"] as? String)),
            triggers: Dictionary(uniqueKeysWithValues: try XCTUnwrap(input["triggers"] as? [[String: Any]]).map { trigger in
                let type = try pmdMeasurementType(try XCTUnwrap(try XCTUnwrap(trigger["type"] as? [String: Any])["ios"] as? String))
                let status = try pmdTriggerStatus(try XCTUnwrap(try XCTUnwrap(trigger["status"] as? [String: Any])["ios"] as? String))
                return (type, (status: status, setting: try pmdAvailableSetting(trigger["availableSettings"] as? [String: Any])))
            })
        )

        let polarTrigger = try PolarDataUtils.mapToPolarOfflineTrigger(from: pmdTrigger)

        XCTAssertEqual(polarTrigger.triggerMode, try polarTriggerMode(try XCTUnwrap(expected["triggerMode"] as? String)))
        let expectedFeatures = try XCTUnwrap(expected["features"] as? [[String: Any]])
        for expectedFeature in expectedFeatures {
            let type = try polarDeviceDataType(try XCTUnwrap(expectedFeature["type"] as? String))
            let actualSettings = polarTrigger.triggerFeatures[type]
            XCTAssertTrue(polarTrigger.triggerFeatures.keys.contains(type), "\(type)")
            try assertPolarSettings(actualSettings.flatMap { $0 }, expected: expectedFeature["settings"] as? [String: Any])
        }
        for excluded in try XCTUnwrap(expected["excludedFeatures"] as? [String]) {
            XCTAssertFalse(polarTrigger.triggerFeatures.keys.contains(try polarDeviceDataType(excluded)))
        }
        XCTAssertEqual(polarTrigger.triggerFeatures.count, expectedFeatures.count)
    }

    func testOfflineRecordingTriggerGoldenVectorsFollowNeutralKmpVectorShape() throws {
        try assertNeutralKmpVectorShape(try loadOfflineRecordingVector("trigger-mapping.json"), id: "trigger-mapping.json")
    }

    func testOfflineRecordingMetadataReadinessManifestPinsMetadataOwnership() throws {
        let readiness = try loadOfflineRecordingVector("metadata-readiness.json")
        let input = try XCTUnwrap(readiness["input"] as? [String: Any])
        let expected = try XCTUnwrap(readiness["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(readiness["consumerTests"] as? [String: Any])
        let platforms = try XCTUnwrap(readiness["platforms"] as? [String: Any])
        let policyVectorPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])

        XCTAssertEqual(readiness["id"] as? String, "offline-recording-metadata-readiness")
        XCTAssertEqual(input["kind"] as? String, "offlineRecordingMetadataReadiness")
        XCTAssertEqual(policyVectorPaths, offlineRecordingMetadataPolicyVectorPaths)
        XCTAssertEqual(requiredFamilies, offlineRecordingMetadataReadinessFamilies)
        XCTAssertEqual(coveredFamilies, offlineRecordingMetadataReadinessFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, offlineRecordingMetadataReadinessCommonDecision)
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.androidcommunications.api.ble.model.offlinerecording.OfflineRecordingUtilityTest", "com.polar.sdk.impl.utils.PolarDataUtilsTest", "com.polar.sdk.api.model.utils.PolarOfflineRecordingUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["OfflineRecordingUtilsTest", "PolarDataUtilsTest", "PolarOfflineRecordingUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.OfflineRecordingMetadataCommonPolicyTest"])
        XCTAssertEqual(platforms["android"] as? Bool, true)
        XCTAssertEqual(platforms["ios"] as? Bool, true)
        XCTAssertEqual(platforms["common"] as? Bool, true)
    }

    func testTriggerRuntimePolicyVectorIsPinnedBeforeRuntimeOwnership() throws {
        let vector = try loadOfflineRecordingVector("trigger-runtime-policy.json")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let prototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])
        let scenarios = try XCTUnwrap(input["scenarios"] as? [[String: Any]], "trigger-runtime-policy")
        let cases = try XCTUnwrap(prototype["cases"] as? [[String: Any]], "trigger-runtime-policy")
        let scenarioIds = try scenarios.map { try XCTUnwrap($0["id"] as? String) }
        let caseIds = try cases.map { try XCTUnwrap($0["id"] as? String) }

        XCTAssertEqual(vector["id"] as? String, "trigger-runtime-policy")
        XCTAssertEqual(scenarioIds, triggerRuntimeScenarioIds)
        XCTAssertEqual(caseIds, triggerRuntimeScenarioIds)
        XCTAssertEqual(try XCTUnwrap(prototype["status"] as? String), "executable shared commonTest", "trigger-runtime-policy")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String], "trigger-runtime-policy"), ["com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePmdClientTest", "com.polar.sdk.impl.BDBleApiImplTest", "com.polar.sdk.impl.utils.PolarDataUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String], "trigger-runtime-policy"), ["BlePmdClientTest", "PolarBleApiImplTests", "PolarDataUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String], "trigger-runtime-policy"), ["com.polar.sharedtest.OfflineTriggerRuntimePolicyCommonTest"])
    }

    func testTriggerRuntimeReadinessManifestIsPinnedBeforeRuntimeOwnership() throws {
        let vector = try loadOfflineRecordingVector("trigger-runtime-readiness.json")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])
        let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])

        XCTAssertEqual(vector["id"] as? String, "trigger-runtime-readiness")
        XCTAssertEqual(input["kind"] as? String, "offlineTriggerRuntimeReadiness")
        XCTAssertEqual(input["policyVectorPath"] as? String, "sdk/offline-recording/trigger-runtime-policy.json")
        let expectedFamilies = [
            "typed-set-mode",
            "status-read",
            "settings-write",
            "optional-secret-attachment",
            "get-transport-error",
            "set-mode-control-point-error",
            "status-read-transport-error",
            "settings-control-point-error",
            "enabled-feature-projection",
            "excluded-feature-projection",
            "platform-packet-split",
            "facade-error-mapping-pinned",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "Offline trigger runtime shared ownership remains valid while trigger-runtime-policy.json and this readiness manifest are executable from shared commonTest, platform facade tests continue to reference the same policy vector, packet-framing differences are preserved in adapters or reconciled explicitly, public facade error mapping is pinned, and the shared tests are compile-verified.")
        let prototype = try XCTUnwrap(expected["commonRuntimePrototype"] as? [String: Any])
        XCTAssertEqual(prototype["status"] as? String, "executable shared commonTest runtime planning guard")
        XCTAssertEqual(prototype["reason"] as? String, "Declared because this vector is consumed by runtime or fake-transport policy tests before production shared ownership.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.sdk.impl.utils.PolarDataUtilsTest"], "trigger-runtime-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["PolarDataUtilsTest"], "trigger-runtime-readiness")
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.OfflineTriggerRuntimePolicyCommonTest"], "trigger-runtime-readiness")
        XCTAssertEqual(platforms["android"] as? Bool, true)
        XCTAssertEqual(platforms["ios"] as? Bool, true)
        XCTAssertEqual(platforms["common"] as? Bool, true)
    }

    // MARK: - PmdSetting.mapToPolarSettings

    func testMapToPolarSettings_sampleRateMapped() {
        let pmdSetting = PmdSetting([.sampleRate: UInt32(52)])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertEqual(Set([UInt32(52)]), polar.settings[.sampleRate])
    }

    func testMapToPolarSettings_resolutionMapped() {
        let pmdSetting = PmdSetting([.resolution: UInt32(16)])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertEqual(Set([UInt32(16)]), polar.settings[.resolution])
    }

    func testMapToPolarSettings_rangeMapped() {
        let pmdSetting = PmdSetting([.range: UInt32(4)])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertEqual(Set([UInt32(4)]), polar.settings[.range])
    }

    func testMapToPolarSettings_rangeMilliUnitMapped() {
        let pmdSetting = PmdSetting([.rangeMilliUnit: UInt32(100)])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertEqual(Set([UInt32(100)]), polar.settings[.rangeMilliunit])
    }

    func testMapToPolarSettings_channelsMapped() {
        let pmdSetting = PmdSetting([.channels: UInt32(3)])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertEqual(Set([UInt32(3)]), polar.settings[.channels])
    }

    func testMapToPolarSettings_multipleSettingsMapped() {
        let pmdSetting = PmdSetting([
            .sampleRate: UInt32(130),
            .resolution: UInt32(24),
            .channels:   UInt32(4)
        ])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertEqual(Set([UInt32(130)]), polar.settings[.sampleRate])
        XCTAssertEqual(Set([UInt32(24)]),  polar.settings[.resolution])
        XCTAssertEqual(Set([UInt32(4)]),   polar.settings[.channels])
    }

    func testMapToPolarSettings_unknownSettingTypeIsIgnored() {
        // .security is not mapped to any PolarSensorSetting type
        let pmdSetting = PmdSetting([.security: UInt32(1)])
        let polar = pmdSetting.mapToPolarSettings()
        XCTAssertTrue(polar.settings.isEmpty)
    }

    private func assertSelectedSettings(_ actual: PmdSetting?, expected: [String: Any]?) throws {
        guard let expected else {
            XCTAssertNil(actual)
            return
        }
        let actualSelected = actual?.selected ?? [:]
        for (key, value) in expected {
            XCTAssertEqual(actualSelected[try pmdSettingType(key)], UInt32(try number(value)))
        }
        XCTAssertEqual(actualSelected.count, expected.count)
    }

    private func assertPolarSettings(_ actual: PolarSensorSetting?, expected: [String: Any]?) throws {
        guard let expected else {
            XCTAssertNil(actual)
            return
        }
        let actualSettings = actual?.settings ?? [:]
        for (key, value) in expected {
            let values = try array(value).map { UInt32(try number($0)) }
            XCTAssertEqual(actualSettings[try polarSettingType(key)], Set(values))
        }
        XCTAssertEqual(actualSettings.count, expected.count)
    }

    private func polarSensorSetting(_ settings: [String: Any]?) throws -> PolarSensorSetting? {
        guard let settings else { return nil }
        var mapped: [PolarSensorSetting.SettingType: Set<UInt32>] = [:]
        for (key, value) in settings {
            mapped[try polarSettingType(key)] = Set([UInt32(try number(value))])
        }
        return PolarSensorSetting(mapped)
    }

    private func pmdAvailableSetting(_ settings: [String: Any]?) throws -> PmdSetting? {
        guard let settings else { return nil }
        var data = Data()
        for (key, value) in settings {
            let type = try pmdSettingType(key)
            let values = try array(value).map { UInt32(try number($0)) }
            data.append(type.rawValue)
            data.append(UInt8(values.count))
            for item in values {
                for index in 0..<pmdSettingFieldSize(type) {
                    data.append(UInt8((item >> UInt32(index * 8)) & 0xff))
                }
            }
        }
        return try PmdSetting(data)
    }

    private func polarTriggerMode(_ name: String) throws -> PolarOfflineRecordingTriggerMode {
        switch name {
        case "TRIGGER_DISABLED": return .triggerDisabled
        case "TRIGGER_SYSTEM_START": return .triggerSystemStart
        case "TRIGGER_EXERCISE_START": return .triggerExerciseStart
        default: throw NSError(domain: "PolarDataUtilsTest", code: 1, userInfo: [NSLocalizedDescriptionKey: "Unknown trigger mode \(name)"])
        }
    }

    private func pmdTriggerMode(_ name: String) throws -> PmdOfflineRecTriggerMode {
        switch name {
        case "disabled": return .disabled
        case "systemStart": return .systemStart
        case "exerciseStart": return .exerciseStart
        default: throw NSError(domain: "PolarDataUtilsTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Unknown PMD trigger mode \(name)"])
        }
    }

    private func pmdTriggerStatus(_ name: String) throws -> PmdOfflineRecTriggerStatus {
        switch name {
        case "enabled": return .enabled
        case "disabled": return .disabled
        default: throw NSError(domain: "PolarDataUtilsTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Unknown PMD trigger status \(name)"])
        }
    }

    private func polarDeviceDataType(_ name: String) throws -> PolarDeviceDataType {
        switch name {
        case "ACC": return .acc
        case "PPG": return .ppg
        case "HR": return .hr
        case "GYRO": return .gyro
        default: throw NSError(domain: "PolarDataUtilsTest", code: 4, userInfo: [NSLocalizedDescriptionKey: "Unknown Polar data type \(name)"])
        }
    }

    private func pmdMeasurementType(_ name: String) throws -> PmdMeasurementType {
        switch name {
        case "acc": return .acc
        case "ppg": return .ppg
        case "offline_hr": return .offline_hr
        case "gyro": return .gyro
        default: throw NSError(domain: "PolarDataUtilsTest", code: 5, userInfo: [NSLocalizedDescriptionKey: "Unknown PMD measurement type \(name)"])
        }
    }

    private func polarSettingType(_ name: String) throws -> PolarSensorSetting.SettingType {
        switch name {
        case "SAMPLE_RATE": return .sampleRate
        case "RESOLUTION": return .resolution
        default: throw NSError(domain: "PolarDataUtilsTest", code: 6, userInfo: [NSLocalizedDescriptionKey: "Unknown Polar setting type \(name)"])
        }
    }

    private func pmdSettingType(_ name: String) throws -> PmdSetting.PmdSettingType {
        switch name {
        case "SAMPLE_RATE": return .sampleRate
        case "RESOLUTION": return .resolution
        default: throw NSError(domain: "PolarDataUtilsTest", code: 7, userInfo: [NSLocalizedDescriptionKey: "Unknown PMD setting type \(name)"])
        }
    }

    private func pmdSettingFieldSize(_ type: PmdSetting.PmdSettingType) -> Int {
        switch type {
        case .sampleRate, .resolution: return 2
        default: return 0
        }
    }

    private func loadOfflineRecordingVector(_ fileName: String) throws -> [String: Any] {
        let file = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/sdk/offline-recording")
            .appendingPathComponent(fileName)
        let data = try Data(contentsOf: file)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
    }

    private func assertNeutralKmpVectorShape(_ vector: [String: Any], id: String) throws {
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


    private func number(_ value: Any) throws -> Int {
        return try XCTUnwrap(value as? NSNumber).intValue
    }

    private func array(_ value: Any) throws -> [Any] {
        return try XCTUnwrap(value as? [Any])
    }

    private let offlineRecordingMetadataPolicyVectorPaths = [
        "sdk/offline-recording/filename-mapping.json",
        "sdk/offline-recording/pmdfiles-v2-grouping.json",
        "sdk/offline-recording/trigger-mapping.json"
    ]

    private let offlineRecordingMetadataReadinessFamilies = [
        "filename-to-measurement-type-mapping",
        "split-file-index-stripping",
        "invalid-filename-boundary",
        "pmdfiles-grouping",
        "zero-size-recording-filtering",
        "invalid-entry-filtering",
        "representative-path-platform-policy",
        "trigger-model-projection",
        "disabled-trigger-filtering",
        "platform-offline-recording-vector-reference-gate",
        "compile-verification-gate"
    ]

    private let offlineRecordingMetadataReadinessCommonDecision = "Offline recording metadata shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS metadata tests continue to reference the same vectors, filename classification, split-file normalization, invalid filename handling, PMDFILES grouping, zero-size and invalid-entry filtering, representative path policy, trigger model projection, disabled-trigger filtering, and compile verification remain explicit before production metadata mapping moves."

    private let triggerRuntimeScenarioIds = [
        "set-trigger-success-with-secret",
        "set-trigger-mode-error",
        "set-trigger-status-read-error",
        "set-trigger-setting-error",
        "get-trigger-success",
        "get-trigger-transport-error"
    ]

    private let featureAvailabilityCaseIds = [
        "firmware-update-requires-psftp-and-firmware-capability",
        "firmware-update-missing-firmware-capability-is-unavailable",
        "led-animation-requires-pmd-and-psftp-services",
        "watch-face-configuration-requires-psftp-and-not-sensor-capability",
        "offline-exercise-v2-uses-h10-filesystem-capability-without-service-gate",
        "unknown-feature-has-no-shared-preconditions"
    ]

    private let featureAvailabilityBehaviorFamilies = [
        "service-and-capability-gates",
        "feature-name-normalization",
        "h10-filesystem-capability-only-gate",
        "unknown-feature-pass-through",
        "platform-client-readiness-boundary"
    ]

    private let featureAvailabilityCommonDecision = "SDK feature availability shared ownership owns only deterministic service and capability preconditions in shared; GATT client lookup, clientReady waits, PMD feature reads, notification readiness, service discovery, BLE transport execution, and public callback/error behavior remain platform-owned."
}

private extension Dictionary where Key == String, Value == Any {
    func mapKeys<T: Hashable>(_ transform: (String) throws -> T) rethrows -> [T: Any] {
        return Dictionary<T, Any>(uniqueKeysWithValues: try map { (try transform($0.key), $0.value) })
    }
}
