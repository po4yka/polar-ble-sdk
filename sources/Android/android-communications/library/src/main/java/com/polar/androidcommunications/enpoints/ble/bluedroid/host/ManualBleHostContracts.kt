package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.le.ScanFilter
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.connection.ConnectionInterface

enum class ManualBleScanState {
    IDLE,
    STOPPED,
    SCANNING
}

data class ManualBleScannerSnapshot(
    val state: ManualBleScanState,
    val adminStopCount: Int,
    val scanFilterCount: Int,
    val lowPowerEnabled: Boolean,
    val opportunisticRestartEnabled: Boolean,
    val isShutdown: Boolean
)

data class ManualBleSessionStateEvent(
    val previousState: BleDeviceSession.DeviceSessionState,
    val state: BleDeviceSession.DeviceSessionState
)

data class ManualBleGattQueueSnapshot(
    val queuedOperationCount: Int,
    val isConnected: Boolean
)

internal interface ManualBleScannerController {
    fun setScanFilters(filters: List<ScanFilter?>?)
    fun restartScan()
    fun addScanClient()
    fun removeScanClient()
    fun pauseScanningForHostOperation()
    fun resumeScanningAfterHostOperation()
    fun bluetoothPoweredOn()
    fun bluetoothPoweredOff()
    fun scannerSnapshot(): ManualBleScannerSnapshot
    fun shutdown()
}

internal interface ManualBleConnectionController : ConnectionInterface

internal interface ManualBleSessionStatePublisher {
    fun publishSessionState(session: BleDeviceSession, state: BleDeviceSession.DeviceSessionState): ManualBleSessionStateEvent
}

internal interface ManualBleGattOperationQueue {
    fun pauseScanningForGattOperation()
    fun resumeScanningAfterGattOperation()
    fun queuedOperationCount(): Int
    fun isGattConnected(): Boolean
    fun gattQueueSnapshot(): ManualBleGattQueueSnapshot
}
