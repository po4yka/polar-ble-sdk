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
        XCTAssertEqual(
            ManualBleScannerSnapshot(
                state: .scanning,
                isScanning: true,
                adminStopCount: 0,
                serviceFilterCount: 1
            ),
            contract.scannerSnapshot()
        )
    }

    func testManualSessionStatePublisherPreservesPreviousCurrentAndEmittedStates() {
        let session = FakeManualBleSession()
        let publisher = FakeManualBleSessionStatePublisher()

        publisher.publishSessionState(session, state: .sessionOpening, error: nil)
        publisher.publishSessionState(session, state: .sessionOpen, error: nil)
        publisher.publishSessionState(session, state: .sessionClosing, error: nil)
        let event = publisher.publishSessionState(session, state: .sessionClosed, error: nil)

        XCTAssertEqual(.sessionClosing, session.previousState)
        XCTAssertEqual(.sessionClosed, session.state)
        XCTAssertEqual(
            ManualBleSessionStateEvent(
                previousState: .sessionClosing,
                state: .sessionClosed,
                errorDescription: nil
            ),
            event
        )
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
        XCTAssertEqual(
            ManualBleGattQueueSnapshot(
                isConnected: false,
                notificationQueueSize: 0,
                hasPendingWriteWithoutResponse: false
            ),
            queue.gattQueueSnapshot()
        )
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

    func testPublicBleLifecycleParityFixturePinsIosSdkVisibleBehavior() throws {
        let vector = try GoldenVectorTestData.loadObject("sdk/ble-session/public-ble-lifecycle-parity.json")
        let input = try XCTUnwrap(vector["input"] as? [String: Any])
        let expected = try XCTUnwrap(vector["expected"] as? [String: Any])
        let consumerTests = try XCTUnwrap(vector["consumerTests"] as? [String: Any])
        let scenario = try XCTUnwrap(input["scenario"] as? [[String: Any]])
        let errorMappings = try XCTUnwrap(expected["errorMappings"] as? [String: Any])

        XCTAssertEqual(vector["id"] as? String, "public-ble-lifecycle-parity")
        XCTAssertEqual(input["kind"] as? String, "publicBleLifecycleParityFixture")
        XCTAssertEqual(scenario.compactMap { $0["event"] as? String }, requiredPublicBleParityScript)
        XCTAssertEqual(expected["publicEvents"] as? [String], requiredPublicBleParityEvents)
        XCTAssertEqual(expected["sessionStates"] as? [String], requiredPublicBleParityStates)
        XCTAssertEqual(expected["readyFeatures"] as? [String], ["HR", "PSFTP"])
        XCTAssertEqual(expected["scanPausePolicy"] as? [String], ["pauseBeforeGattOperation", "resumeAfterGattOperation"])
        XCTAssertEqual(errorMappings["linkLoss"] as? String, "deviceDisconnected:linkLoss")
        XCTAssertEqual(errorMappings["pairingProblem"] as? String, "pairingProblem")
        XCTAssertEqual(consumerTests["android"] as? [String], ["com.polar.androidcommunications.enpoints.ble.bluedroid.host.ManualBleHostContractsTest"])
        XCTAssertEqual(consumerTests["ios"] as? [String], ["ManualBleTransportContractsTest"])
        XCTAssertEqual(consumerTests["commonPrototype"] as? [String], ["com.polar.sharedtest.BleSessionPlatformOwnershipCommonPolicyTest"])
        XCTAssertEqual(expected["platformOwnership"] as? String, "manual_transport_platform_owned")
    }

    private func drain(_ queue: DispatchQueue) {
        queue.sync { }
    }

    private var requiredPublicBleParityScript: [String] {
        [
            "scan_result",
            "open_requested",
            "service_discovery_complete",
            "notification_enable_complete",
            "stream_data",
            "disconnect",
            "reconnect_requested",
            "session_reopened",
            "pairing_problem"
        ]
    }

    private var requiredPublicBleParityEvents: [String] {
        [
            "deviceDiscovered",
            "sessionOpening",
            "sessionOpen",
            "featureReady:HR",
            "featureReady:PSFTP",
            "notificationEnabled:HR",
            "notificationEnabled:PSFTP",
            "streamStarted:HR",
            "streamData:HR",
            "deviceDisconnected:linkLoss",
            "reconnectRequested",
            "sessionOpen",
            "pairingProblem"
        ]
    }

    private var requiredPublicBleParityStates: [String] {
        [
            "SESSION_OPENING",
            "SESSION_OPEN",
            "SESSION_CLOSING",
            "SESSION_CLOSED",
            "SESSION_OPENING",
            "SESSION_OPEN"
        ]
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

    func publishSessionState(_ session: BleDeviceSession, state: BleDeviceSession.DeviceSessionState, error: Error?) -> ManualBleSessionStateEvent? {
        (session as! FakeManualBleSession).update(state, error: error)
        events.append(state.description())
        return ManualBleSessionStateEvent(
            previousState: session.previousState,
            state: session.state,
            errorDescription: error?.localizedDescription
        )
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

    func gattQueueSnapshot() -> ManualBleGattQueueSnapshot {
        ManualBleGattQueueSnapshot(
            isConnected: connected,
            notificationQueueSize: 0,
            hasPendingWriteWithoutResponse: false
        )
    }
}
