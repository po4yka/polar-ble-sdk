package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualBleHostContractsTest {
    @Test
    fun `manual scanner contract records scan lifecycle without platform bluetooth`() {
        val scanner = FakeManualBleScannerController()

        scanner.addScanClient()
        scanner.bluetoothPoweredOn()
        scanner.pauseScanningForHostOperation()
        scanner.resumeScanningAfterHostOperation()
        scanner.restartScan()
        scanner.removeScanClient()
        scanner.bluetoothPoweredOff()

        assertEquals(
            listOf(
                "client-added",
                "power-on",
                "pause",
                "resume",
                "restart",
                "client-removed",
                "power-off"
            ),
            scanner.events
        )
        assertFalse(scanner.isPaused)
        assertEquals(0, scanner.clientCount)
    }

    @Test
    fun `manual session state publisher preserves previous current and emitted states`() {
        val session = FakeManualBleSession()
        val publisher = FakeManualBleSessionStatePublisher()

        publisher.publishSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_OPENING)
        publisher.publishSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
        publisher.publishSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_CLOSING)
        publisher.publishSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_CLOSED)

        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSING, session.previousState)
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSED, session.sessionState)
        assertEquals(
            listOf("SESSION_OPENING", "SESSION_OPEN", "SESSION_CLOSING", "SESSION_CLOSED"),
            publisher.events
        )
    }

    @Test
    fun `manual gatt queue contract exposes operation count scan pauses and connection state`() {
        val queue = FakeManualBleGattOperationQueue()

        queue.connect()
        queue.enqueueOperation()
        queue.pauseScanningForGattOperation()
        queue.enqueueOperation()
        queue.resumeScanningAfterGattOperation()
        queue.completeOperation()

        assertTrue(queue.isGattConnected())
        assertEquals(1, queue.queuedOperationCount())
        assertEquals(listOf("connect", "enqueue", "pause", "enqueue", "resume", "complete"), queue.events)
        assertFalse(queue.scanningPaused)
    }

    private class FakeManualBleScannerController : ManualBleScannerController {
        val events = mutableListOf<String>()
        var clientCount = 0
            private set
        var isPaused = false
            private set

        override fun setScanFilters(filters: List<android.bluetooth.le.ScanFilter?>?) {
            events += "filters"
        }

        override fun restartScan() {
            events += "restart"
        }

        override fun addScanClient() {
            clientCount += 1
            events += "client-added"
        }

        override fun removeScanClient() {
            clientCount -= 1
            events += "client-removed"
        }

        override fun pauseScanningForHostOperation() {
            isPaused = true
            events += "pause"
        }

        override fun resumeScanningAfterHostOperation() {
            isPaused = false
            events += "resume"
        }

        override fun bluetoothPoweredOn() {
            events += "power-on"
        }

        override fun bluetoothPoweredOff() {
            events += "power-off"
        }
    }

    private class FakeManualBleSession : BleDeviceSession() {
        fun update(state: DeviceSessionState) {
            previousState = sessionState
            sessionState = state
        }

        override val isNonConnectableAdvertisement: Boolean = false
        override val address: String = "00:11:22:33:44:55"
        override val isAuthenticated: Boolean = false
        override val bluetoothDevice: android.bluetooth.BluetoothDevice? = null

        override suspend fun authenticate() = Unit
        override fun monitorServicesDiscovered(checkConnection: Boolean): kotlinx.coroutines.Deferred<List<java.util.UUID>> = kotlinx.coroutines.CompletableDeferred(emptyList())
        override fun clearGattCache(): Boolean = false
        override fun readRssiValue(): kotlinx.coroutines.Deferred<Int>? = kotlinx.coroutines.CompletableDeferred(-60)
        override fun getIndicatesPairingProblem(): Pair<Boolean, Int> = false to -1
    }

    private class FakeManualBleSessionStatePublisher : ManualBleSessionStatePublisher {
        val events = mutableListOf<String>()

        override fun publishSessionState(session: BleDeviceSession, state: BleDeviceSession.DeviceSessionState) {
            (session as FakeManualBleSession).update(state)
            events += state.name
        }
    }

    private class FakeManualBleGattOperationQueue : ManualBleGattOperationQueue {
        val events = mutableListOf<String>()
        var scanningPaused = false
            private set
        private var connected = false
        private var queueSize = 0

        fun connect() {
            connected = true
            events += "connect"
        }

        fun enqueueOperation() {
            queueSize += 1
            events += "enqueue"
        }

        fun completeOperation() {
            queueSize -= 1
            events += "complete"
        }

        override fun pauseScanningForGattOperation() {
            scanningPaused = true
            events += "pause"
        }

        override fun resumeScanningAfterGattOperation() {
            scanningPaused = false
            events += "resume"
        }

        override fun queuedOperationCount(): Int = queueSize

        override fun isGattConnected(): Boolean = connected
    }
}
