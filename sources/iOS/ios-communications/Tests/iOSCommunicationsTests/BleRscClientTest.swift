//  Copyright © 2026 Polar. All rights reserved.

import XCTest
@testable import iOSCommunications
import CoreBluetooth

class BleRscClientTest: XCTestCase {
    var bleRscClient: BleRscClient!
    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!

    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        bleRscClient = BleRscClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }

    override func tearDownWithError() throws {
        mockGattServiceTransmitterImpl = nil
        bleRscClient = nil
    }

    func testRscMeasurementCharacterizationReferencesSharedRscGoldenVectors() {
        let consumedVectors = [
            "protocol/gatt/rsc-measurement-stride-distance.json",
            "protocol/gatt/rsc-measurement-required-fields.json"
        ]
        XCTAssertEqual(consumedVectors.count, 2)
    }

    func testMeasurementWithStrideAndDistanceEmitsParsedNotification() async throws {
        let characteristic = CBUUID(string: "2a53")
        let stream = bleRscClient.observeRscNotifications(true)
        let task = Task { try await self.collect(1, from: stream) }

        try await Task.sleep(nanoseconds: 20_000_000)

        bleRscClient.processServiceData(characteristic, data: Data([0x07, 0x34, 0x12, 0x56, 0x89, 0x07, 0x04, 0x03, 0x02, 0x01]), err: 0)

        let results = try await task.value

        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].speed, 65.53125, accuracy: 0.000001)
        XCTAssertEqual(results[0].candence, 86)
        XCTAssertEqual(results[0].strideLength, 1929)
        XCTAssertEqual(results[0].distance, 1690906.0, accuracy: 0.000001)
        XCTAssertTrue(results[0].running)
        XCTAssertEqual(results[0].flags, 0x07)
    }

    func testMeasurementWithoutOptionalFieldsEmitsZeros() async throws {
        let characteristic = CBUUID(string: "2a53")
        let stream = bleRscClient.observeRscNotifications(true)
        let task = Task { try await self.collect(1, from: stream) }

        try await Task.sleep(nanoseconds: 20_000_000)

        bleRscClient.processServiceData(characteristic, data: Data([0x00, 0x0A, 0x00, 0x14]), err: 0)

        let results = try await task.value

        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].speed, 0.140625, accuracy: 0.000001)
        XCTAssertEqual(results[0].candence, 20)
        XCTAssertEqual(results[0].strideLength, 0)
        XCTAssertEqual(results[0].distance, 0.0, accuracy: 0.000001)
        XCTAssertFalse(results[0].running)
        XCTAssertEqual(results[0].flags, 0x00)
    }

    private func collect(_ count: Int, from stream: AsyncThrowingStream<BleRscClient.BleRscNotification, Error>) async throws -> [BleRscClient.BleRscNotification] {
        var results: [BleRscClient.BleRscNotification] = []
        for try await item in stream {
            results.append(item)
            if results.count == count { break }
        }
        return results
    }
}
