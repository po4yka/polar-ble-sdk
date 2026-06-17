package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.le.ScanFilter
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionInterface

internal interface ManualBleScannerController {
    fun setScanFilters(filters: List<ScanFilter?>?)
    fun restartScan()
    fun addScanClient()
    fun removeScanClient()
    fun pauseScanningForHostOperation()
    fun resumeScanningAfterHostOperation()
    fun bluetoothPoweredOn()
    fun bluetoothPoweredOff()
}

internal interface ManualBleConnectionController : ConnectionInterface

internal interface ManualBleSessionStatePublisher {
    fun publishSessionState(session: BleDeviceSession, state: BleDeviceSession.DeviceSessionState)
}

internal interface ManualBleGattOperationQueue {
    fun pauseScanningForGattOperation()
    fun resumeScanningAfterGattOperation()
    fun queuedOperationCount(): Int
    fun isGattConnected(): Boolean
}
