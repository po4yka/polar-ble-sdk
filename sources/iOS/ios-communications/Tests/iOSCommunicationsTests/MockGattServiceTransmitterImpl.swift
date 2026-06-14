//  Copyright © 2021 Polar. All rights reserved.

import Foundation
import CoreBluetooth
@testable import iOSCommunications

typealias MockPolarGattServiceTransmitter = MockGattServiceTransmitterImpl

class MockGattServiceTransmitterImpl: BleAttributeTransportProtocol {
    var mockConnectionStatus: Bool = true
    var setCharacteristicsNotifyCache: [(characteristicUuid: CBUUID, notify: Bool)] = []
    var transmittedMessages: [(serviceUuid: CBUUID, characteristicUuid: CBUUID, packet: Data, withResponse: Bool)] = []
    var transmitMessageHandler: ((BleGattClientBase, CBUUID, CBUUID, Data, Bool) -> Void)?
    var setCharacteristicNotifyHandler: ((BleGattClientBase, CBUUID, CBUUID, Bool) -> Void)?
    var setCharacteristicNotifyError: Error?
    
    func isConnected() -> Bool {
        return mockConnectionStatus
    }
    
    func transmitMessage(_ parent: BleGattClientBase, serviceUuid: CBUUID , characteristicUuid: CBUUID , packet: Data, withResponse: Bool) throws {
        transmittedMessages.append((serviceUuid, characteristicUuid, packet, withResponse))
        transmitMessageHandler?(parent, serviceUuid, characteristicUuid, packet, withResponse)
    }
    
    func characteristicWith(uuid: CBUUID) throws -> CBCharacteristic? {
        return nil
    }
    
    func characteristicNameWith(uuid: CBUUID) -> String? {
        return nil
    }
    
    func readValue(_ parent: BleGattClientBase, serviceUuid: CBUUID , characteristicUuid: CBUUID ) throws {
        // Do nothing
    }
    
    func setCharacteristicNotify(_ parent: BleGattClientBase, serviceUuid: CBUUID, characteristicUuid: CBUUID, notify: Bool) throws {
        setCharacteristicsNotifyCache.append((characteristicUuid, notify))
        if let setCharacteristicNotifyError {
            throw setCharacteristicNotifyError
        }
        if let setCharacteristicNotifyHandler {
            setCharacteristicNotifyHandler(parent, serviceUuid, characteristicUuid, notify)
        } else {
            parent.notifyDescriptorWritten(characteristicUuid, enabled: notify, err: 0)
        }
    }
    
    func attributeOperationStarted(){
        // Do nothing
    }
    
    func attributeOperationFinished(){
        // Do nothing
    }
}
