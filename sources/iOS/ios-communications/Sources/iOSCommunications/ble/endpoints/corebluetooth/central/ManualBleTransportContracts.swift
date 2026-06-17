import Foundation
import CoreBluetooth

enum ManualBleScanState: Equatable {
    case idle
    case stopped
    case scanning
}

struct ManualBleScannerSnapshot: Equatable {
    let state: ManualBleScanState
    let isScanning: Bool
    let adminStopCount: Int
    let serviceFilterCount: Int
}

struct ManualBleSessionStateEvent: Equatable {
    let previousState: BleDeviceSession.DeviceSessionState
    let state: BleDeviceSession.DeviceSessionState
    let errorDescription: String?
}

struct ManualBleGattQueueSnapshot: Equatable {
    let isConnected: Bool
    let notificationQueueSize: Int
    let hasPendingWriteWithoutResponse: Bool
}

protocol ManualBleScannerController: AnyObject {
    var isScanning: Bool { get }
    func setServices(_ services: [CBUUID]?)
    func addClient()
    func removeClient()
    func stopScan()
    func startScan()
    func powerOn()
    func powerOff()
    func scannerSnapshot() -> ManualBleScannerSnapshot
}

protocol ManualBleSessionStatePublisher: AnyObject {
    func publishSessionState(_ session: BleDeviceSession, state: BleDeviceSession.DeviceSessionState, error: Error?) -> ManualBleSessionStateEvent?
}

protocol ManualBleGattOperationQueue: AnyObject {
    func attributeOperationStarted()
    func attributeOperationFinished()
    func isConnected() -> Bool
    func gattQueueSnapshot() -> ManualBleGattQueueSnapshot
}
