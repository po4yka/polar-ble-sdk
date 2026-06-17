import Foundation
import CoreBluetooth

protocol ManualBleScannerController: AnyObject {
    var isScanning: Bool { get }
    func setServices(_ services: [CBUUID]?)
    func addClient()
    func removeClient()
    func stopScan()
    func startScan()
    func powerOn()
    func powerOff()
}

protocol ManualBleSessionStatePublisher: AnyObject {
    func publishSessionState(_ session: BleDeviceSession, state: BleDeviceSession.DeviceSessionState, error: Error?)
}

protocol ManualBleGattOperationQueue: AnyObject {
    func attributeOperationStarted()
    func attributeOperationFinished()
    func isConnected() -> Bool
}
