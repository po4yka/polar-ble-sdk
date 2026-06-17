// Copyright © 2026 Polar. All rights reserved.

import XCTest
import CoreBluetooth
@testable import iOSCommunications

final class ManualBleTransportContractsTest: XCTestCase {
    func testManualScannerContractIsBackedByProductionCoreBluetoothScanner() {
        let queue = DispatchQueue(label: "com.polar.test.manual-scanner-contract")
        let central = MockCBCentralManager()
        central.mockState = .poweredOn
        let scanner = CBScanner(central, queue: queue, sessions: AtomicList<CBDeviceSessionImpl>())
        let contract: ManualBleScannerController = scanner

        contract.addClient()
        drain(queue)
        XCTAssertTrue(scanner.isScanning)
        XCTAssertTrue(central.scanForPeripheralsCalled)

        contract.stopScan()
        drain(queue)
        XCTAssertFalse(scanner.isScanning)
        XCTAssertTrue(central.stopScanCalled)

        contract.startScan()
        drain(queue)
        XCTAssertTrue(scanner.isScanning)

        contract.setServices([CBUUID(string: "180D")])
        drain(queue)
        XCTAssertEqual([CBUUID(string: "180D")], scanner.services)
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

    func testManualSessionStatePublisherContractIsBackedByProductionDeviceListener() {
        let listener = CBDeviceListenerImpl(
            DispatchQueue(label: "com.polar.test.manual-session-publisher-contract"),
            clients: [],
            identifier: 0
        )

        let publisher: ManualBleSessionStatePublisher = listener
        XCTAssertTrue(publisher === listener)
    }

    private func drain(_ queue: DispatchQueue) {
        queue.sync { }
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
