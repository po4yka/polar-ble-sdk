//  Copyright © 2022 Polar. All rights reserved.

import XCTest
import CoreBluetooth
@testable import iOSCommunications

class BleAdvertisementContentTest: XCTestCase {
    var bleAdvertisementContent:BleAdvertisementContent!
    
    override func setUpWithError() throws {
        bleAdvertisementContent = BleAdvertisementContent()
    }
    
    override func tearDownWithError() throws {
        bleAdvertisementContent = nil
    }
    
    func testParseNameFromCompleteLocalName() throws {
        // Arrange
        let testInputString = "ABC EDE aa123459"
        let name: [String : String] = [CBAdvertisementDataLocalNameKey : testInputString]
        // Act
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: name)
        // Assert
        XCTAssertEqual(testInputString, bleAdvertisementContent.name)
        XCTAssertTrue(bleAdvertisementContent.polarDeviceType.isEmpty)
        XCTAssertEqual(bleAdvertisementContent.polarDeviceId, "AA123459")
    }
    
    func testParseNameFromCompleteLocalNameWhenPolarDevice() throws {
        // Arrange
        let testInputString = "Polar GritX Pro AA123459"
        let name: [String : String] = [CBAdvertisementDataLocalNameKey : testInputString]
        
        // Act
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: name)
        
        // Assert
        XCTAssertEqual(testInputString, bleAdvertisementContent.name)
        XCTAssertEqual("GritX Pro", bleAdvertisementContent.polarDeviceType)
        XCTAssertEqual("AA123459", bleAdvertisementContent.polarDeviceId)
    }
    
    func testParseHrFromManufacturerDataWithoutHr() throws {
        // Arrange
        let onlyGpbManufacturerData = Data([0x6b, 0x00,
                                            0x72, 0x08, 0x97, 0xc9, 0xc3, 0x00, 0x00, 0x00, 0x00, 0x00])
        
        let onlyGpb:[String : Data] = [CBAdvertisementDataManufacturerDataKey : onlyGpbManufacturerData]
        
        // Act
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: onlyGpb)
        
        // Assert
        XCTAssertNotNil(bleAdvertisementContent.polarHrAdvertisementData)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
    }
    
    func testParseHrFromManufacturerDataSAGRFC23format() {
        // Arrange
        let gpbAndHrManufacturerData = Data([0x6b, 0x00,
                                             0x72, 0x08, 0x97, 0xc9, 0xc3, 0x00, 0x00, 0x00, 0x00, 0x00,
                                             0x7a, 0x01, 0x03, 0x33, 0x00, 0x00])
        
        let gbpAndHr: [String : Data] = [CBAdvertisementDataManufacturerDataKey : gpbAndHrManufacturerData]
        
        // Act
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: gbpAndHr)
        
        // Assert
        XCTAssertNotNil(bleAdvertisementContent.polarHrAdvertisementData)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
    }
    
    func testParseHrFromManufacturerDataSAGRFC31format() throws {
        // Arrange
        let onlyHrManufacturerData = Data([0x6b, 0x00, 0x2b, 0x0b, 0xb6, 0xac])
        let onlyHr: [String : Data] = [CBAdvertisementDataManufacturerDataKey : onlyHrManufacturerData]
        
        // Act
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: onlyHr)
        
        // Assert
        XCTAssertNotNil(bleAdvertisementContent.polarHrAdvertisementData)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
    }
    
    func testParseHrFromManufacturerDataNotPolarAdv() throws {
        // Arrange
        let nonPolarManufacturerData = Data([0x6b, 0x01, // 0x006B is Polar manufacturer Id, 0x016B is not Polar
                                             0x72, 0x08, 0x97, 0xc9, 0xc3, 0x00, 0x00, 0x00, 0x00, 0x00,
                                             0x7a, 0x01, 0x03, 0x33, 0x00, 0x00])
        
        let nonPolar: [String : Data] = [CBAdvertisementDataManufacturerDataKey : nonPolarManufacturerData]

        // Act
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: nonPolar)

        // Assert
        XCTAssertNotNil(bleAdvertisementContent.polarHrAdvertisementData)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
    }
    
    func testProcessOfRssiWhenLessThan7RssiValuesArrived() throws {
        // Arrange
        let rssiValues:[Int32] = [-0, -1, -2, -3, -4, -99]

        // Act
        for rssi in rssiValues {
            bleAdvertisementContent.processAdvertisementData(rssi, advertisementData: [CBAdvertisementDataManufacturerDataKey : Data()])
        }

        // Assert
        XCTAssertEqual(rssiValues.last, bleAdvertisementContent.rssiFilter.rssi)
        XCTAssertEqual(rssiValues.last, bleAdvertisementContent.medianRssi)
    }
    
    func testProcessOfRssiWhenMoreThan7RssiValuesArrived() throws {
        // Arrange
        let rssiValues:[Int32] = [-10, -21, -2, -5, -9, -0, -8, -1, -8, -2]
        let median:Int32 = -5 // -0, -1, -2, !-5!, -8, -8, -9,
        
        // Act
        for rssi in rssiValues {
            bleAdvertisementContent.processAdvertisementData(rssi, advertisementData: [CBAdvertisementDataManufacturerDataKey : Data()])
        }

        // Assert
        XCTAssertEqual(rssiValues.last, bleAdvertisementContent.rssiFilter.rssi)
        XCTAssertEqual(median, bleAdvertisementContent.medianRssi)
    }
    
    func testAdvContainsService() throws {
        // Arrange
        let services:[CBUUID] = [BleHrClient.HR_SERVICE, BlePsFtpClient.PSFTP_SERVICE]
        let servicesAdvData:[String : [CBUUID]] = [CBAdvertisementDataServiceUUIDsKey : services]
        let singleServiceAdvData: [String : [CBUUID]] = [CBAdvertisementDataServiceUUIDsKey : [BleHrClient.HR_SERVICE]]
        let emptyServicesAdvData: [String : Data] = [CBAdvertisementDataServiceUUIDsKey : Data()]
                
        // Act & Assert
        bleAdvertisementContent.processAdvertisementData(0, advertisementData:servicesAdvData)
        XCTAssertTrue(bleAdvertisementContent.containsService(BlePsFtpClient.PSFTP_SERVICE))
        XCTAssertTrue(bleAdvertisementContent.containsService(BleHrClient.HR_SERVICE))
        bleAdvertisementContent.processAdvertisementData(0, advertisementData:emptyServicesAdvData)
        XCTAssertFalse(bleAdvertisementContent.containsService(BlePsFtpClient.PSFTP_SERVICE))
        XCTAssertFalse(bleAdvertisementContent.containsService(BleHrClient.HR_SERVICE))
        bleAdvertisementContent.processAdvertisementData(0, advertisementData:singleServiceAdvData)
        XCTAssertFalse(bleAdvertisementContent.containsService(BlePsFtpClient.PSFTP_SERVICE))
        XCTAssertTrue(bleAdvertisementContent.containsService(BleHrClient.HR_SERVICE))
    }
    
    func testProcessingOfConsecutiveAdvPackets() throws {
        // Arrange
        let gpbAndHrManufacturerData = Data([0x6b, 0x00,
                                             0x72, 0x08, 0x97, 0xc9, 0xc3, 0x00, 0x00, 0x00, 0x00, 0x00,
                                             0x7a, 0x01, 0x03, 0x33, 0x00, 0x00])
        let onlyGpbManufacturerData = Data([0x6b, 0x00,
                                            0x72, 0x08, 0x97, 0xc9, 0xc3, 0x00, 0x00, 0x00, 0x00, 0x00])
        let emptyManufacturerData = Data()
        
        let gbpAndHr: [String : Data] = [CBAdvertisementDataManufacturerDataKey : gpbAndHrManufacturerData]
        let onlyGpb:[String : Data] = [CBAdvertisementDataManufacturerDataKey : onlyGpbManufacturerData]
        let emptyManufacturer: [String : Data] = [CBAdvertisementDataManufacturerDataKey : emptyManufacturerData]
        let nilManufacturer: [String : Any] = [CBAdvertisementDataManufacturerDataKey : Optional<Data>.none as Any]
        
        // Act & Assert
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: gbpAndHr)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isHrDataUpdated)
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: onlyGpb)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isHrDataUpdated)
        
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: gbpAndHr)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isHrDataUpdated)
        
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: emptyManufacturer)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isHrDataUpdated)
        
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: gbpAndHr)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
        XCTAssertTrue(bleAdvertisementContent.polarHrAdvertisementData.isHrDataUpdated)
        
        bleAdvertisementContent.processAdvertisementData(0, advertisementData: nilManufacturer)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isPresent)
        XCTAssertFalse(bleAdvertisementContent.polarHrAdvertisementData.isHrDataUpdated)
        
    }

    func testAdvertisementGoldenVectorsMatchIOSCommunicationsBehavior() throws {
        let vectors = try loadAdvertisementGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected advertisement golden vectors")

        for vector in vectors {
            try setUpWithError()
            let id = vector["id"] as? String ?? "unknown-vector"
            if let platforms = vector["platforms"] as? [String: Any],
               let supported = platforms["ios"] as? Bool,
               !supported {
                continue
            }
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)

            if let deviceNamePrefix = input["deviceNamePrefix"] as? String {
                bleAdvertisementContent.advertisingDeviceNamePrefix = deviceNamePrefix
            }

            if let localName = input["localName"] as? String {
                bleAdvertisementContent.processAdvertisementData(0, advertisementData: [CBAdvertisementDataLocalNameKey: localName])

                if let expectedName = expected["name"] as? String {
                    XCTAssertEqual(expectedName, bleAdvertisementContent.name, id)
                }
                if let expectedDeviceType = expected["deviceType"] as? String {
                    XCTAssertEqual(expectedDeviceType, bleAdvertisementContent.polarDeviceType, id)
                }
                if let expectedDeviceId = expected["deviceId"] as? String {
                    XCTAssertEqual(expectedDeviceId, bleAdvertisementContent.polarDeviceId, id)
                }

                let platformExpectations = vector["platformExpectations"] as? [String: Any]
                let iosExpectations = platformExpectations?["ios"] as? [String: Any]
                if let expectedDeviceId = iosExpectations?["deviceId"] as? String {
                    XCTAssertEqual(expectedDeviceId, bleAdvertisementContent.polarDeviceId, id)
                }
            }

            if let manufacturerHex = input["manufacturerDataHex"] as? String {
                let manufacturerData = try Data(hexString: manufacturerHex)
                bleAdvertisementContent.processAdvertisementData(0, advertisementData: [CBAdvertisementDataManufacturerDataKey: manufacturerData])

                if let expectedHrPresent = expected["hrPresent"] as? Bool {
                    XCTAssertEqual(expectedHrPresent, bleAdvertisementContent.polarHrAdvertisementData.isPresent, id)
                }
            }

            if let services16Hex = input["services16Hex"] as? [String] {
                let services = services16Hex.map { CBUUID(string: $0) }
                bleAdvertisementContent.processAdvertisementData(0, advertisementData: [CBAdvertisementDataServiceUUIDsKey: services])

                if let containsServices = expected["containsServices"] as? [String: Bool] {
                    for (service, isPresent) in containsServices {
                        XCTAssertEqual(isPresent, bleAdvertisementContent.containsService(CBUUID(string: service)), "\(id): \(service)")
                    }
                }
            }

            if let rssiSequence = input["rssiSequence"] as? [NSNumber] {
                for rssi in rssiSequence {
                    bleAdvertisementContent.processAdvertisementData(rssi.int32Value, advertisementData: [CBAdvertisementDataManufacturerDataKey: Data()])
                }

                if let expectedRssi = expected["rssi"] as? NSNumber {
                    XCTAssertEqual(expectedRssi.int32Value, bleAdvertisementContent.rssiFilter.rssi, id)
                }
                if let expectedMedianRssi = expected["medianRssi"] as? NSNumber {
                    XCTAssertEqual(expectedMedianRssi.int32Value, bleAdvertisementContent.medianRssi, id)
                }
            }
        }
    }

    func testAdvertisementGoldenVectorsFollowNeutralKmpShape() throws {
        for vector in try loadAdvertisementGoldenVectors() {
            let id = try XCTUnwrap(vector["id"] as? String)
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

    func testAdvertisementReadinessManifestIsPinnedBeforeParserMigration() throws {
        let manifest = try loadAdvertisementReadinessManifest()
        let input = try XCTUnwrap(manifest["input"] as? [String: Any])
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any])
        let platforms = try XCTUnwrap(manifest["platforms"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        let policyPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])

        XCTAssertEqual("advertisement-readiness", manifest["id"] as? String)
        XCTAssertEqual(input["kind"] as? String, "advertisementReadiness")
        XCTAssertEqual(policyPaths, [
            "protocol/advertisement/custom-prefix-local-name.json",
            "protocol/advertisement/manufacturer-hr-sagrfc23.json",
            "protocol/advertisement/manufacturer-no-hr.json",
            "protocol/advertisement/manufacturer-non-polar.json",
            "protocol/advertisement/manufacturer-polar-gpb-missing-length-platform-policy.json",
            "protocol/advertisement/manufacturer-polar-id-only.json",
            "protocol/advertisement/manufacturer-polar-truncated-hr-candidate.json",
            "protocol/advertisement/manufacturer-polar-unknown-gpb-segment.json",
            "protocol/advertisement/manufacturer-unknown-company.json",
            "protocol/advertisement/non-polar-local-name-platform-difference.json",
            "protocol/advertisement/polar-local-name.json",
            "protocol/advertisement/rssi-median-seven-sample-window.json",
            "protocol/advertisement/service-uuid-membership.json",
            "protocol/advertisement/seven-digit-local-name.json"
        ])
        let expectedFamilies = [
            "polar-local-name-parsing",
            "custom-prefix-local-name-parsing",
            "seven-digit-device-id-assembly",
            "non-polar-local-name-platform-decision",
            "manufacturer-polar-hr-presence",
            "manufacturer-no-hr-policy",
            "manufacturer-non-polar-policy",
            "manufacturer-unknown-company-policy",
            "manufacturer-unknown-segment-policy",
            "malformed-gpb-missing-length-policy",
            "malformed-truncated-hr-candidate-policy",
            "service-uuid-membership",
            "rssi-median-seven-sample-window",
            "platform-advertisement-vector-reference-gate",
            "compile-verification-gate"
        ]
        XCTAssertEqual(requiredFamilies, expectedFamilies)
        XCTAssertEqual(coveredFamilies, expectedFamilies)
        XCTAssertEqual(expected["commonDecision"] as? String, "Advertisement parsing migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS advertisement tests continue to reference the same vectors, Polar and custom-prefix local-name parsing, seven-digit device ID assembly, non-Polar local-name platform decisions, manufacturer HR presence and absence, non-Polar and unknown company behavior, unknown Polar segment handling, malformed GPB missing-length and truncated HR-candidate policies, service UUID membership, RSSI median calculation, and compile verification remain explicit before production advertisement parsing moves.")
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContentTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["BleAdvertisementContentTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.AdvertisementCommonPolicyTest"])
        XCTAssertEqual(platforms["android"] as? Bool, true)
        XCTAssertEqual(platforms["ios"] as? Bool, true)
        XCTAssertEqual(platforms["common"] as? Bool, true)
    }

    private func loadAdvertisementGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/advertisement")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
            .filter { vector in
                (vector["input"] as? [String: Any])?["kind"] as? String != "advertisementReadiness"
            }
    }

    private func loadAdvertisementReadinessManifest() throws -> [String: Any] {
        let manifestUrl = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/advertisement/advertisement-readiness.json")
        let data = try Data(contentsOf: manifestUrl)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], manifestUrl.path)
    }

}

private extension Data {
    init(hexString: String) throws {
        guard hexString.count.isMultiple(of: 2) else {
            throw NSError(domain: "BleAdvertisementContentTest", code: 2, userInfo: [NSLocalizedDescriptionKey: "Hex string must have an even length"])
        }
        var bytes: [UInt8] = []
        var index = hexString.startIndex
        while index < hexString.endIndex {
            let nextIndex = hexString.index(index, offsetBy: 2)
            let byteString = String(hexString[index..<nextIndex])
            guard let byte = UInt8(byteString, radix: 16) else {
                throw NSError(domain: "BleAdvertisementContentTest", code: 3, userInfo: [NSLocalizedDescriptionKey: "Invalid hex byte \(byteString)"])
            }
            bytes.append(byte)
            index = nextIndex
        }
        self.init(bytes)
    }
}
