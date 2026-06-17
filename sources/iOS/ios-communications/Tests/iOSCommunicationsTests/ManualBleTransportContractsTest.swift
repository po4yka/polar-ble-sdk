// Copyright © 2026 Polar. All rights reserved.

import XCTest
import CoreBluetooth
@testable import iOSCommunications

final class ManualBleTransportContractsTest: XCTestCase {
    func testManualScannerContractRecordsScanLifecycleWithoutCoreBluetooth() {
        let scanner = FakeManualBleScannerController()

        scanner.addClient()
        scanner.powerOn()
        scanner.stopScan()
        scanner.startScan()
        scanner.setServices([CBUUID(string: "180D")])
        scanner.removeClient()
        scanner.powerOff()

        XCTAssertEqual(["client-added", "power-on", "stop", "start", "services:180D", "client-removed", "power-off"], scanner.events)
        XCTAssertFalse(scanner.isScanning)
        XCTAssertEqual(0, scanner.clientCount)
    }

    func testManualSessionStatePublisherPreservesPreviousCurrentAndEmittedStates() {
        let session = FakeManualBleSession()
        let publisher = FakeManualBleSessionStatePublisher()

        publisher.publishSessionState(session, state: .sessionOpening, error: nil)
        publisher.publishSessionState(session, state: .sessionOpen, error: nil)
        publisher.publishSessionState(session, state: .sessionClosing, error: nil)
        publisher.publishSessionState(session, state: .sessionClosed, error: nil)

        XCTAssertEqual(.sessionClosing, session.previousState)
        XCTAssertEqual(.sessionClosed, session.state)
        XCTAssertEqual(["sessionOpening", "sessionOpen", "sessionClosing", "sessionClosed"], publisher.events)
    }

    func testManualGattQueueContractExposesScanPausesAndConnectionState() {
        let queue = FakeManualBleGattOperationQueue()

        queue.connect()
        queue.attributeOperationStarted()
        queue.attributeOperationFinished()
        queue.disconnect()

        XCTAssertFalse(queue.isConnected())
        XCTAssertFalse(queue.scanningPaused)
        XCTAssertEqual(["connect", "pause", "resume", "disconnect"], queue.events)
    }
}

private final class FakeManualBleScannerController: ManualBleScannerController {
    private(set) var events = [String]()
    private(set) var clientCount = 0
    private(set) var isScanning = false

    func setServices(_ services: [CBUUID]?) {
        let serviceList = services?.map(\.uuidString).joined(separator: ",") ?? "nil"
        events.append("services:\(serviceList)")
    }

    func addClient() {
        clientCount += 1
        events.append("client-added")
    }

    func removeClient() {
        clientCount -= 1
        events.append("client-removed")
    }

    func stopScan() {
        isScanning = false
        events.append("stop")
    }

    func startScan() {
        isScanning = true
        events.append("start")
    }

    func powerOn() {
        events.append("power-on")
    }

    func powerOff() {
        isScanning = false
        events.append("power-off")
    }
}

private final class FakeManualBleSession: BleDeviceSession {
    init() {
        super.init(UUID(uuidString: "00000000-0000-0000-0000-000000000001")!)
    }

    func update(_ newState: DeviceSessionState, error: Error?) {
        previousState = state
        state = newState
        self.error = error
    }

    override func isConnectable() -> Bool {
        true
    }

    override func monitorServicesDiscovered(_ checkConnection: Bool) -> AsyncThrowingStream<CBUUID, Error> {
        AsyncThrowingStream { continuation in continuation.finish() }
    }
}

private final class FakeManualBleSessionStatePublisher: ManualBleSessionStatePublisher {
    private(set) var events = [String]()

    func publishSessionState(_ session: BleDeviceSession, state: BleDeviceSession.DeviceSessionState, error: Error?) {
        (session as! FakeManualBleSession).update(state, error: error)
        events.append(state.description())
    }
}

private final class FakeManualBleGattOperationQueue: ManualBleGattOperationQueue {
    private(set) var events = [String]()
    private(set) var scanningPaused = false
    private var connected = false

    func connect() {
        connected = true
        events.append("connect")
    }

    func disconnect() {
        connected = false
        events.append("disconnect")
    }

    func attributeOperationStarted() {
        scanningPaused = true
        events.append("pause")
    }

    func attributeOperationFinished() {
        scanningPaused = false
        events.append("resume")
    }

    func isConnected() -> Bool {
        connected
    }
}
