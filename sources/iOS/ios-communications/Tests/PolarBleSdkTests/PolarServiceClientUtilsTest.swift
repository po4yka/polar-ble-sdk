// Copyright © 2026 Polar. All rights reserved.

import XCTest
import CoreBluetooth
import Combine
@testable import PolarBleSdk

final class PolarServiceClientUtilsTest: XCTestCase {

    // MARK: - Properties

    fileprivate var sut: StubPolarServiceClientUtils!

    override func setUpWithError() throws {
        sut = StubPolarServiceClientUtils()
    }

    override func tearDownWithError() throws {
        sut = nil
    }

    // MARK: - fetchSession – UUID format

    func testFetchSession_validUUID_returnsMatchingSession() throws {
        let address = UUID()
        let session = makeSession(address: address)
        sut.stubbedSessions = [session]

        let found = try sut.fetchSession(address.uuidString)

        XCTAssertTrue(found === session)
    }

    func testFetchSession_validUUID_notInList_returnsNil() throws {
        sut.stubbedSessions = []
        let found = try sut.fetchSession(UUID().uuidString)
        XCTAssertNil(found)
    }

    // MARK: - fetchSession – device-ID format

    func testFetchSession_validDeviceId_returnsMatchingSession() throws {
        let deviceId = "1A2B3C4D"
        let session = makeSession(deviceId: deviceId)
        sut.stubbedSessions = [session]

        let found = try sut.fetchSession(deviceId)

        XCTAssertTrue(found === session)
    }

    func testFetchSession_6hexDeviceId_returnsMatchingSession() throws {
        let deviceId = "AABBCC"
        let session = makeSession(deviceId: deviceId)
        sut.stubbedSessions = [session]

        let found = try sut.fetchSession(deviceId)

        XCTAssertTrue(found === session)
    }

    // MARK: - fetchSession – invalid format

    func testFetchSession_invalidIdentifier_throwsInvalidArgument() {
        XCTAssertThrowsError(try sut.fetchSession("not_a_valid_id")) { error in
            guard case PolarErrors.invalidArgument = error else {
                return XCTFail("Expected invalidArgument, got \(error)")
            }
        }
    }

    func testFetchSession_emptyString_throwsInvalidArgument() {
        XCTAssertThrowsError(try sut.fetchSession("")) { error in
            guard case PolarErrors.invalidArgument = error else {
                return XCTFail("Expected invalidArgument, got \(error)")
            }
        }
    }

    func testFetchSession_rejectsAndroidBluetoothAddressEvenThoughSharedClassifiesItPlatformSpecific() {
        let address = "00:11:22:33:44:55"
        let session = makeSession(address: UUID())
        sut.stubbedSessions = [session]

        XCTAssertThrowsError(try sut.fetchSession(address)) { error in
            guard case PolarErrors.invalidArgument = error else {
                return XCTFail("Expected invalidArgument, got \(error)")
            }
        }
    }

    func testIdentifierRoutingGoldenVectorsMatchIOSBehavior() throws {
        let vectors = try loadIdentifierRoutingGoldenVectors()
        XCTAssertFalse(vectors.isEmpty, "Expected identifier routing golden vectors")

        for vector in vectors {
            let id = vector["id"] as? String ?? "unknown-vector"
            let platforms = try XCTUnwrap(vector["platforms"] as? [String: Any], id)
            guard platforms["ios"] as? Bool == true else { continue }
            let input = try XCTUnwrap(vector["input"] as? [String: Any], id)
            let expected = try XCTUnwrap(vector["expected"] as? [String: Any], id)
            let identifier = try XCTUnwrap(input["identifier"] as? String, id)

            if expected["error"] != nil {
                sut.stubbedSessions = []
                XCTAssertThrowsError(try sut.fetchSession(identifier), id) { error in
                    guard case PolarErrors.invalidArgument = error else {
                        return XCTFail("Expected invalidArgument for \(id), got \(error)")
                    }
                }
                continue
            }

            let matchingAddress = try XCTUnwrap(UUID(uuidString: try XCTUnwrap(input["matchingAddress"] as? String, id)), id)
            let session = makeSession(address: matchingAddress)
            sut.stubbedSessions = [session]
            let found = try sut.fetchSession(identifier)
            XCTAssertTrue(found === session, id)
        }
    }

    func testIdentifierRoutingGoldenVectorsFollowNeutralKmpVectorShape() throws {
        for vector in try loadIdentifierRoutingGoldenVectors() {
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

    func testDeviceIdReadinessManifestPinsIdentifierRoutingOwnership() throws {
        let manifest = try loadDeviceIdReadinessManifest()
        let input = try XCTUnwrap(manifest["input"] as? [String: Any])
        let expected = try XCTUnwrap(manifest["expected"] as? [String: Any])
        let requiredFamilies = try XCTUnwrap(input["requiredBehaviorFamilies"] as? [String])
        let coveredFamilies = try XCTUnwrap(expected["coveredBehaviorFamilies"] as? [String])
        let policyPaths = try XCTUnwrap(input["policyVectorPaths"] as? [String])
        let consumerTests = try XCTUnwrap(manifest["consumerTests"] as? [String: Any])

        XCTAssertEqual("device-id-readiness", manifest["id"] as? String)
        XCTAssertEqual("deviceIdReadiness", input["kind"] as? String)
        XCTAssertEqual(DEVICE_ID_READINESS_POLICY_VECTOR_PATHS, policyPaths)
        XCTAssertEqual(DEVICE_ID_READINESS_FAMILIES, requiredFamilies)
        XCTAssertEqual(DEVICE_ID_READINESS_FAMILIES, coveredFamilies)
        XCTAssertEqual(DEVICE_ID_READINESS_COMMON_DECISION, try XCTUnwrap(expected["commonDecision"] as? String))
        XCTAssertEqual(try XCTUnwrap(consumerTests["android"] as? [String]), ["com.polar.androidcommunications.api.ble.model.polar.DeviceIdGoldenVectorTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["ios"] as? [String]), ["BlePolarDeviceIdUtilityTest", "PolarDeviceUuidTest", "PolarServiceClientUtilsTest"])
        XCTAssertEqual(try XCTUnwrap(consumerTests["commonPrototype"] as? [String]), ["com.polar.sharedtest.DeviceIdCommonPolicyTest"])
    }

    // MARK: - sessionServiceReady

    func testSessionServiceReady_sessionNotFound_throwsDeviceNotFound() {
        sut.stubbedSessions = []

        XCTAssertThrowsError(
            try sut.sessionServiceReady(UUID().uuidString, service: BlePmdClient.PMD_SERVICE)
        ) { error in
            guard case PolarErrors.deviceNotFound = error else {
                return XCTFail("Expected deviceNotFound, got \(error)")
            }
        }
    }

    func testSessionServiceReady_sessionNotConnected_throwsDeviceNotConnected() throws {
        let session = makeSession()
        session.state = .sessionClosed
        sut.stubbedSessions = [session]

        XCTAssertThrowsError(
            try sut.sessionServiceReady(session.address.uuidString, service: BlePmdClient.PMD_SERVICE)
        ) { error in
            guard case PolarErrors.deviceNotConnected = error else {
                return XCTFail("Expected deviceNotConnected, got \(error)")
            }
        }
    }

    func testSessionServiceReady_connectedButServiceMissing_throwsServiceNotFound() throws {
        let session = makeSession()
        session.state = .sessionOpen
        // No GATT clients added → service not found
        sut.stubbedSessions = [session]

        XCTAssertThrowsError(
            try sut.sessionServiceReady(session.address.uuidString, service: BlePmdClient.PMD_SERVICE)
        ) { error in
            guard case PolarErrors.serviceNotFound = error else {
                return XCTFail("Expected serviceNotFound, got \(error)")
            }
        }
    }

    func testSessionServiceReady_connectedServiceFoundButNotDiscovered_throwsNotificationNotEnabled() throws {
        let session = makeSession()
        session.state = .sessionOpen
        let pmdClient = makePmdClient(serviceDiscovered: false)
        session.gattClients = [pmdClient]
        sut.stubbedSessions = [session]

        XCTAssertThrowsError(
            try sut.sessionServiceReady(session.address.uuidString, service: BlePmdClient.PMD_SERVICE)
        ) { error in
            guard case PolarErrors.notificationNotEnabled = error else {
                return XCTFail("Expected notificationNotEnabled, got \(error)")
            }
        }
    }

    func testSessionServiceReady_connectedAndServiceDiscovered_returnsSession() throws {
        let session = makeSession()
        session.state = .sessionOpen
        let pmdClient = makePmdClient(serviceDiscovered: true)
        session.gattClients = [pmdClient]
        sut.stubbedSessions = [session]

        let result = try sut.sessionServiceReady(
            session.address.uuidString, service: BlePmdClient.PMD_SERVICE
        )

        XCTAssertTrue(result === session)
    }

    // MARK: - pmdNotificationsEnabled (static)

    func testPmdNotificationsEnabled_noPmdClient_returnsFalse() {
        let session = makeSession()
        XCTAssertFalse(PolarServiceClientUtils.pmdNotificationsEnabled(session))
    }

    func testPmdNotificationsEnabled_notificationsDisabled_returnsFalse() {
        let session = makeSession()
        session.gattClients = [makePmdClient(cpEnabled: false, dataEnabled: false)]
        XCTAssertFalse(PolarServiceClientUtils.pmdNotificationsEnabled(session))
    }

    func testPmdNotificationsEnabled_onlyOneNotificationEnabled_returnsFalse() {
        let session = makeSession()
        session.gattClients = [makePmdClient(cpEnabled: true, dataEnabled: false)]
        XCTAssertFalse(PolarServiceClientUtils.pmdNotificationsEnabled(session))
    }

    func testPmdNotificationsEnabled_bothNotificationsEnabled_returnsTrue() {
        let session = makeSession()
        session.gattClients = [makePmdClient(cpEnabled: true, dataEnabled: true)]
        XCTAssertTrue(PolarServiceClientUtils.pmdNotificationsEnabled(session))
    }

    // MARK: - psFtpNotificationsEnabled (static)

    func testPsFtpNotificationsEnabled_noFtpClient_returnsFalse() {
        let session = makeSession()
        XCTAssertFalse(PolarServiceClientUtils.psFtpNotificationsEnabled(session))
    }

    func testPsFtpNotificationsEnabled_notificationsDisabled_returnsFalse() {
        let session = makeSession()
        session.gattClients = [makeFtpClient(mtuEnabled: false, d2hEnabled: false)]
        XCTAssertFalse(PolarServiceClientUtils.psFtpNotificationsEnabled(session))
    }

    func testPsFtpNotificationsEnabled_onlyMtuEnabled_returnsFalse() {
        let session = makeSession()
        session.gattClients = [makeFtpClient(mtuEnabled: true, d2hEnabled: false)]
        XCTAssertFalse(PolarServiceClientUtils.psFtpNotificationsEnabled(session))
    }

    func testPsFtpNotificationsEnabled_bothNotificationsEnabled_returnsTrue() {
        let session = makeSession()
        session.gattClients = [makeFtpClient(mtuEnabled: true, d2hEnabled: true)]
        XCTAssertTrue(PolarServiceClientUtils.psFtpNotificationsEnabled(session))
    }

    // MARK: - sessionPmdClientReady

    func testSessionPmdClientReady_notificationsDisabled_throwsNotificationNotEnabled() throws {
        let session = makeSession()
        session.state = .sessionOpen
        session.gattClients = [makePmdClient(serviceDiscovered: true, cpEnabled: false, dataEnabled: false)]
        sut.stubbedSessions = [session]

        XCTAssertThrowsError(
            try sut.sessionPmdClientReady(session.address.uuidString)
        ) { error in
            guard case PolarErrors.notificationNotEnabled = error else {
                return XCTFail("Expected notificationNotEnabled, got \(error)")
            }
        }
    }

    func testSessionPmdClientReady_notificationsEnabled_returnsSession() throws {
        let session = makeSession()
        session.state = .sessionOpen
        session.gattClients = [makePmdClient(serviceDiscovered: true, cpEnabled: true, dataEnabled: true)]
        sut.stubbedSessions = [session]

        let result = try sut.sessionPmdClientReady(session.address.uuidString)

        XCTAssertTrue(result === session)
    }

    // MARK: - sessionPfcClientReady

    func testSessionPfcClientReady_serviceNotDiscovered_throwsNotificationNotEnabled() throws {
        let session = makeSession()
        session.state = .sessionOpen
        session.gattClients = [makePfcClient(serviceDiscovered: false)]
        sut.stubbedSessions = [session]

        XCTAssertThrowsError(
            try sut.sessionPfcClientReady(session.address.uuidString)
        ) { error in
            guard case PolarErrors.notificationNotEnabled = error else {
                return XCTFail("Expected notificationNotEnabled, got \(error)")
            }
        }
    }

    func testSessionPfcClientReady_serviceDiscovered_returnsSession() throws {
        let session = makeSession()
        session.state = .sessionOpen
        session.gattClients = [makePfcClient(serviceDiscovered: true)]
        sut.stubbedSessions = [session]

        let result = try sut.sessionPfcClientReady(session.address.uuidString)

        XCTAssertTrue(result === session)
    }

    // MARK: - sessionHrClientReady

    func testSessionHrClientReady_notificationDisabled_throwsNotificationNotEnabled() throws {
        let session = makeSession()
        session.state = .sessionOpen
        session.gattClients = [makeHrClient(serviceDiscovered: true, measurementEnabled: false)]
        sut.stubbedSessions = [session]

        XCTAssertThrowsError(
            try sut.sessionHrClientReady(session.address.uuidString)
        ) { error in
            guard case PolarErrors.notificationNotEnabled = error else {
                return XCTFail("Expected notificationNotEnabled, got \(error)")
            }
        }
    }

    func testSessionHrClientReady_notificationEnabled_returnsSession() throws {
        let session = makeSession()
        session.state = .sessionOpen
        session.gattClients = [makeHrClient(serviceDiscovered: true, measurementEnabled: true)]
        sut.stubbedSessions = [session]

        let result = try sut.sessionHrClientReady(session.address.uuidString)

        XCTAssertTrue(result === session)
    }

    // MARK: - getRSSIValue

    func testGetRSSIValue_sessionFound_returnsRssi() throws {
        let session = makeSession()
        session.rssi = -65
        sut.stubbedSessions = [session]

        let rssi = try sut.getRSSIValue(session.address.uuidString)

        XCTAssertEqual(-65, rssi)
    }

    func testGetRSSIValue_sessionNotFound_throwsInvalidArgument() {
        sut.stubbedSessions = []

        XCTAssertThrowsError(try sut.getRSSIValue(UUID().uuidString)) { error in
            guard case PolarErrors.invalidArgument = error else {
                return XCTFail("Expected invalidArgument, got \(error)")
            }
        }
    }

    // MARK: - checkIfDeviceDisconnectedDueRemovedPairing

    func testCheckIfDeviceDisconnectedDueRemovedPairing_noPairingError_returnsFalse() throws {
        let session = makeSession()
        sut.stubbedSessions = [session]

        let result = try sut.checkIfDeviceDisconnectedDueRemovedPairing(
            identifier: session.address.uuidString
        )

        XCTAssertFalse(result)
    }

    func testCheckIfDeviceDisconnectedDueRemovedPairing_sessionNotFound_returnsFalse() throws {
        sut.stubbedSessions = []

        let result = try sut.checkIfDeviceDisconnectedDueRemovedPairing(
            identifier: UUID().uuidString
        )

        XCTAssertFalse(result)
    }

    private func loadIdentifierRoutingGoldenVectors() throws -> [[String: Any]] {
        let vectorDirectory = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/device-id")
        return try FileManager.default
            .contentsOfDirectory(at: vectorDirectory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == "json" && $0.lastPathComponent.hasPrefix("identifier-") }
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
            .map { file in
                let data = try Data(contentsOf: file)
                return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], file.path)
            }
    }

    private func loadDeviceIdReadinessManifest() throws -> [String: Any] {
        let manifestUrl = try GoldenVectorTestData.repositoryRoot()
            .appendingPathComponent("testdata/golden-vectors/protocol/device-id/device-id-readiness.json")
        let data = try Data(contentsOf: manifestUrl)
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any], manifestUrl.path)
    }

    private let DEVICE_ID_READINESS_POLICY_VECTOR_PATHS = [
        "protocol/device-id/assemble-seven-digit-device-id.json",
        "protocol/device-id/assemble-six-digit-device-id.json",
        "protocol/device-id/assemble-zero-seven-digit-device-id.json",
        "protocol/device-id/empty-device-id-platform-difference.json",
        "protocol/device-id/identifier-bluetooth-address-android.json",
        "protocol/device-id/identifier-invalid-format.json",
        "protocol/device-id/identifier-uuid-string-ios.json",
        "protocol/device-id/invalid-checksum-device-id.json",
        "protocol/device-id/non-hex-device-id-platform-difference.json",
        "protocol/device-id/polar-device-uuid-invalid-length.json",
        "protocol/device-id/polar-device-uuid-valid.json",
        "protocol/device-id/valid-lowercase-device-id.json"
    ]

    private let DEVICE_ID_READINESS_FAMILIES = [
        "checksum-width-6-assembly",
        "checksum-width-7-assembly",
        "zero-device-id-assembly",
        "valid-device-id-validation",
        "invalid-checksum-validation",
        "lowercase-device-id-validation",
        "empty-input-platform-decision",
        "non-hex-input-platform-decision",
        "uuid-conversion",
        "uuid-invalid-length-error",
        "identifier-invalid-format-error",
        "platform-specific-identifier-routing",
        "platform-device-id-vector-reference-gate",
        "compile-verification-gate"
    ]

    private let DEVICE_ID_READINESS_COMMON_DECISION = "Device ID shared ownership remains valid while every vector named by this readiness manifest is executable from shared commonTest, Android and iOS device ID tests continue to reference the same vectors, checksum width 6 and 7 assembly, zero-value assembly, validation, lowercase acceptance, UUID conversion, invalid UUID length errors, invalid identifier rejection, current empty and non-hex platform decisions, platform-specific identifier routing, and compile verification remain explicit before production checksum or UUID logic moves."

}

// MARK: - Test infrastructure

/// A `PolarServiceClientUtils` subclass that overrides `fetchSession` so tests
/// don't need to go through `CBDeviceListenerImpl.allSessions()` (which lives in
/// a Swift extension and cannot be overridden).
private class StubPolarServiceClientUtils: PolarServiceClientUtils {
    var stubbedSessions: [BleDeviceSession] = []

    init() {
        // CBDeviceListenerImpl is only needed for the listener reference, but
        // fetchSession is fully overridden below so the listener is never called.
        super.init(listener: CBDeviceListenerImpl(DispatchQueue(label: "com.polar.test.stub"), clients: [], identifier: 99))
    }

    required init(listener: CBDeviceListenerImpl) {
        super.init(listener: listener)
    }

    override func fetchSession(_ identifier: String) throws -> BleDeviceSession? {
        // Validate the identifier format the same way the real implementation does.
        if identifier.matches("^([0-9a-fA-F]{8})(-[0-9a-fA-F]{4}){3}-([0-9a-fA-F]{12})") {
            return stubbedSessions.first { $0.address.uuidString == identifier }
        } else if identifier.matches("([0-9a-fA-F]){6,8}") {
            return stubbedSessions.first { $0.advertisementContent.polarDeviceIdUntouched == identifier }
        }
        throw PolarErrors.invalidArgument()
    }
}

/// A `BleDeviceSession` subclass with a no-op `isConnectable()`.
private class StubBleDeviceSession: BleDeviceSession {
    init(address: UUID = UUID()) {
        super.init(address)
    }

    override func isConnectable() -> Bool { false }

    override func monitorServicesDiscovered(_ checkConnection: Bool) -> AsyncThrowingStream<CBUUID, Error> {
        AsyncThrowingStream { $0.finish() }
    }
}

/// An `BleAdvertisementContent` subclass with a settable `polarDeviceIdUntouched`.
private class StubAdvertisementContent: BleAdvertisementContent {
    private let _deviceId: String
    init(deviceId: String) { _deviceId = deviceId }
    override var polarDeviceIdUntouched: String { _deviceId }
}

// MARK: - Factory helpers

private func makeSession(address: UUID = UUID()) -> StubBleDeviceSession {
    StubBleDeviceSession(address: address)
}

private func makeSession(deviceId: String) -> BleDeviceSession {
    let content = StubAdvertisementContent(deviceId: deviceId)
    return BleDeviceSession(UUID(), advertisementContent: content)
}

private func makeTransmitter() -> MockPolarGattServiceTransmitter {
    MockPolarGattServiceTransmitter()
}

private func makePmdClient(
    serviceDiscovered: Bool = true,
    cpEnabled: Bool = false,
    dataEnabled: Bool = false
) -> BlePmdClient {
    let client = BlePmdClient(gattServiceTransmitter: makeTransmitter())
    client.setServiceDiscovered(serviceDiscovered)
    if cpEnabled   { client.notifyDescriptorWritten(BlePmdClient.PMD_CP,   enabled: true, err: 0) }
    if dataEnabled { client.notifyDescriptorWritten(BlePmdClient.PMD_DATA, enabled: true, err: 0) }
    return client
}

private func makeFtpClient(
    mtuEnabled: Bool = false,
    d2hEnabled: Bool = false
) -> BlePsFtpClient {
    let client = BlePsFtpClient(gattServiceTransmitter: makeTransmitter())
    client.setServiceDiscovered(true)
    if mtuEnabled { client.notifyDescriptorWritten(BlePsFtpClient.PSFTP_MTU_CHARACTERISTIC,            enabled: true, err: 0) }
    if d2hEnabled { client.notifyDescriptorWritten(BlePsFtpClient.PSFTP_D2H_NOTIFICATION_CHARACTERISTIC, enabled: true, err: 0) }
    return client
}

private func makePfcClient(serviceDiscovered: Bool) -> BlePfcClient {
    let client = BlePfcClient(gattServiceTransmitter: makeTransmitter())
    client.setServiceDiscovered(serviceDiscovered)
    return client
}

private func makeHrClient(
    serviceDiscovered: Bool = true,
    measurementEnabled: Bool = false
) -> BleHrClient {
    let client = BleHrClient(gattServiceTransmitter: makeTransmitter())
    client.setServiceDiscovered(serviceDiscovered)
    if measurementEnabled {
        client.notifyDescriptorWritten(BleHrClient.HR_MEASUREMENT, enabled: true, err: 0)
    }
    return client
}
