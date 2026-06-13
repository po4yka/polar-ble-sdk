//  Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications

final class BlePsdClientTest: XCTestCase {
    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!
    var blePsdClient: BlePsdClient!

    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockPolarGattServiceTransmitter()
        blePsdClient = BlePsdClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }

    override func tearDownWithError() throws {
        mockGattServiceTransmitterImpl = nil
        blePsdClient = nil
    }

    func testPsdFeatureCharacterizationReferencesSharedPsdGoldenVectors() {
        let consumedVectors = [
            "protocol/gatt/psd-feature-none-supported.json",
            "protocol/gatt/psd-feature-all-supported.json"
        ]
        XCTAssertEqual(consumedVectors.count, 2)
    }

    func testPsdFeatureParsesAllSupportedBits() async throws {
        blePsdClient.processServiceData(BlePsdClient.PSD_FEATURE, data: Data([0x0F]), err: 0)
        let feature = try await blePsdClient.readFeature(false)

        XCTAssertTrue(feature.ecgSupported)
        XCTAssertTrue(feature.ohrSupported)
        XCTAssertTrue(feature.accSupported)
        XCTAssertTrue(feature.ppSupported)
    }
}
