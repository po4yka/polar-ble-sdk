import Foundation
import CoreBluetooth

public class BleH7SettingsClient: BleGattClientBase, @unchecked Sendable {

    public static let H7_SETTINGS_SERVICE = CBUUID(string: "6217FF49-AC7B-547E-EECF-016A06970BA9")
    let H7_SETTINGS_CHARACTERISTIC        = CBUUID(string: "6217FF4A-B07D-5DEB-261E-2586752D942E")

    // Pending async read continuation — resolved by processServiceData or disconnected().
    // Access is serialised by pendingReadLock.
    private let pendingReadLock = NSLock()
    private var pendingReadContinuation: CheckedContinuation<[Data: Int], Error>?

    public init(gattServiceTransmitter: BleAttributeTransportProtocol) {
        super.init(serviceUuid: BleH7SettingsClient.H7_SETTINGS_SERVICE, gattServiceTransmitter: gattServiceTransmitter)
        addCharacteristicRead(H7_SETTINGS_CHARACTERISTIC)
    }

    public enum H7SettingsMessage: Int, Sendable {
        case h7ConfigureBroadcast = 1
        case h7Configure5khz = 2
        case h7RequestCurrentSettings = 3

        public var description: String {
            switch self {
            case .h7ConfigureBroadcast:      return "H7_CONFIGURE_BROADCAST"
            case .h7Configure5khz:           return "H7_CONFIGURE_5KHZ"
            case .h7RequestCurrentSettings:  return "H7_REQUEST_CURRENT_SETTINGS"
            }
        }
    }

    public class H7SettingsResponse {
        public let broadcastValue: UInt8
        public let khzValue: UInt8

        init(data: Data) {
            self.khzValue = (data[0] & 0x02) >> 1
            self.broadcastValue = (data[0] & 0x01)
        }

        init(broadcastValue: UInt8, khzValue: UInt8) {
            self.broadcastValue = broadcastValue
            self.khzValue = khzValue
        }

        public func description() -> String {
            return "BC value: \(broadcastValue) khz value: \(khzValue)"
        }
    }

    override public func disconnected() {
        super.disconnected()
        let continuation = takePendingContinuation()
        continuation?.resume(throwing: BleGattException.gattDisconnected)
    }

    override public func processServiceData(_ chr: CBUUID, data: Data, err: Int) {
        if chr.isEqual(H7_SETTINGS_CHARACTERISTIC) {
            let continuation = takePendingContinuation()
            continuation?.resume(returning: [data: err])
        }
    }

    // Atomically removes and returns the pending continuation, if any.
    private func takePendingContinuation() -> CheckedContinuation<[Data: Int], Error>? {
        pendingReadLock.lock()
        defer { pendingReadLock.unlock() }
        let c = pendingReadContinuation
        pendingReadContinuation = nil
        return c
    }

    // Suspends the caller until processServiceData delivers a response for
    // H7_SETTINGS_CHARACTERISTIC. No thread is blocked during the wait.
    private func readSettingsValueAsync() async throws -> [Data: Int] {
        guard let transport = gattServiceTransmitter else {
            throw BleGattException.gattTransportNotAvailable
        }
        return try await withCheckedThrowingContinuation { continuation in
            pendingReadLock.lock()
            pendingReadContinuation = continuation
            pendingReadLock.unlock()
            do {
                try transport.readValue(self, serviceUuid: BleH7SettingsClient.H7_SETTINGS_SERVICE, characteristicUuid: H7_SETTINGS_CHARACTERISTIC)
            } catch {
                // readValue failed synchronously; remove the continuation we just stored
                // and resume with the error so the caller is not permanently suspended.
                let stored = takePendingContinuation()
                stored?.resume(throwing: error)
            }
        }
    }

    /// Send a settings command to the H7 device.
    ///
    /// - Parameters:
    ///   - command: the H7 settings command
    ///   - parameter: command parameter byte
    /// - Returns: H7SettingsResponse
    public func sendSettingsCommand(_ command: H7SettingsMessage, parameter: UInt8) async throws -> H7SettingsResponse {
        guard gattServiceTransmitter?.isConnected() ?? false else {
            throw BleGattException.gattDisconnected
        }
        guard isServiceDiscovered() else {
            throw BleGattException.gattServiceNotFound
        }

        let packet = try await readSettingsValueAsync()
        guard let packetEntry = packet.first else {
            throw BleGattException.gattAttributeError(errorCode: -1)
        }
        guard packetEntry.1 == 0 else {
            throw BleGattException.gattAttributeError(errorCode: packetEntry.1)
        }
        let bytes = packetEntry.0
        let khzValue = (bytes[0] & 0x02) >> 1
        let broadcastValue = (bytes[0] & 0x01)

        switch command {
        case .h7ConfigureBroadcast, .h7Configure5khz:
            var values = [UInt8](repeating: 0, count: 1)
            if command == .h7ConfigureBroadcast {
                values[0] = (khzValue << 1) | parameter
            } else {
                values[0] = (parameter << 1) | broadcastValue
            }
            try gattServiceTransmitter?.transmitMessage(self, serviceUuid: BleH7SettingsClient.H7_SETTINGS_SERVICE,
                                                        characteristicUuid: H7_SETTINGS_CHARACTERISTIC,
                                                        packet: Data(values), withResponse: true)
            let response = try await readSettingsValueAsync()
            guard let responseEntry = response.first else {
                throw BleGattException.gattAttributeError(errorCode: -1)
            }
            return H7SettingsResponse(data: responseEntry.0)
        case .h7RequestCurrentSettings:
            return H7SettingsResponse(broadcastValue: broadcastValue, khzValue: khzValue)
        }
    }
}
