//  Copyright © 2023 Polar. All rights reserved.

import XCTest
import iOSCommunications
import CoreBluetooth

class BleHtsClientTest: XCTestCase {

    var bleHtsClient: BleHtsClient!
    var mockGattServiceTransmitterImpl: MockPolarGattServiceTransmitter!

    override func setUpWithError() throws {
        mockGattServiceTransmitterImpl = MockGattServiceTransmitterImpl()
        bleHtsClient = BleHtsClient(gattServiceTransmitter: mockGattServiceTransmitterImpl)
    }

    override func tearDownWithError() throws {
        mockGattServiceTransmitterImpl = nil
        bleHtsClient = nil
    }

    func testTemperatureMeasurement() async throws {
        // Arrange
        let consumedVectors = [
            "protocol/gatt/hts-temperature-celsius-centesimal.json",
            "protocol/gatt/hts-temperature-fahrenheit-centesimal.json"
        ]
        XCTAssertEqual(consumedVectors.count, 2)
        let characteristic: CBUUID = HealthThermometer.TEMPERATURE_MEASUREMENT
        let status = 0

        let expectedCelsius1 = 27.20
        let measurementFrame1: [UInt8] = [0x00, 0xa0, 0x0a, 0x00, 0xfe]

        let expectedCelsius2 = 27.21
        let measurementFrame2: [UInt8] = [0x00, 0xa1, 0x0a, 0x00, 0xfe]

        let expectedCelsius3 = 37.0
        let expectedFahrenheit3 = 98.6
        let measurementFrame3: [UInt8] = [0x01, 0x84, 0x26, 0x00, 0xfe]

        // Act
        let stream = bleHtsClient.observeHtsNotifications(checkConnection: true)
        let task = Task { () -> [BleHtsClient.TemperatureMeasurement] in
            var results: [BleHtsClient.TemperatureMeasurement] = []
            for try await item in stream {
                results.append(item)
                if results.count == 3 { break }
            }
            return results
        }
        try await Task.sleep(nanoseconds: 20_000_000)

        bleHtsClient.processServiceData(characteristic, data: Data(measurementFrame1), err: status)
        bleHtsClient.processServiceData(characteristic, data: Data(measurementFrame2), err: status)
        bleHtsClient.processServiceData(characteristic, data: Data(measurementFrame3), err: status)

        let recordedEvents = try await task.value

        // Assert
        XCTAssertEqual(recordedEvents.count, 3)
        XCTAssertEqual(recordedEvents[0].temperatureCelsius, Float(expectedCelsius1))
        XCTAssertEqual(recordedEvents[1].temperatureCelsius, Float(expectedCelsius2))
        XCTAssertEqual(recordedEvents[2].temperatureCelsius, Float(expectedCelsius3))
        XCTAssertEqual(recordedEvents[2].temperatureFahrenheit, Float(expectedFahrenheit3), accuracy: 0.001)
    }
}
