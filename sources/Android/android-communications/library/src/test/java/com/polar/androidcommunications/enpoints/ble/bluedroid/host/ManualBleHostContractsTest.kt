package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.gatt.BleGattFactory
import com.polar.androidcommunications.common.ble.BleUtils.EVENT_TYPE
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class ManualBleHostContractsTest {
    @Before
    fun setUp() {
        mockkStatic(ParcelUuid::class)
        every { ParcelUuid.fromString(any()) } returns mockk(relaxed = true)

        mockkConstructor(ScanFilter.Builder::class)
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(any()) } returns mockk(relaxed = true)
        every { anyConstructed<ScanFilter.Builder>().setManufacturerData(any(), any()) } returns mockk(relaxed = true)
        every { anyConstructed<ScanFilter.Builder>().build() } returns mockk(relaxed = true)

        mockkConstructor(ScanSettings.Builder::class)
        every { anyConstructed<ScanSettings.Builder>().setScanMode(any()) } returns mockk(relaxed = true)
        every { anyConstructed<ScanSettings.Builder>().build() } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `manual scanner contract is backed by production Android scan adapter`() {
        val bluetoothAdapter = mockk<BluetoothAdapter>()
        val bluetoothLeScanner = mockk<BluetoothLeScanner>(relaxed = true)
        val scanningNeeded = AtomicBoolean(true)
        val scanner = createProductionScanner(bluetoothAdapter, bluetoothLeScanner, scanningNeeded)
        val contract: ManualBleScannerController = scanner

        contract.addScanClient()
        contract.pauseScanningForHostOperation()
        contract.resumeScanningAfterHostOperation()
        scanningNeeded.set(false)
        contract.removeScanClient()

        verify(atLeast = 1) { bluetoothLeScanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }
        verify(atLeast = 1) { bluetoothLeScanner.stopScan(any<ScanCallback>()) }
        assertEquals(
            ManualBleScannerSnapshot(
                state = ManualBleScanState.IDLE,
                adminStopCount = 0,
                scanFilterCount = 3,
                lowPowerEnabled = false,
                opportunisticRestartEnabled = false
            ),
            contract.scannerSnapshot()
        )
    }

    @Test
    fun `manual session state publisher preserves previous current and emitted states`() {
        val session = FakeManualBleSession()
        val publisher = FakeManualBleSessionStatePublisher()

        publisher.publishSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_OPENING)
        publisher.publishSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_OPEN)
        publisher.publishSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_CLOSING)
        val event = publisher.publishSessionState(session, BleDeviceSession.DeviceSessionState.SESSION_CLOSED)

        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSING, session.previousState)
        assertEquals(BleDeviceSession.DeviceSessionState.SESSION_CLOSED, session.sessionState)
        assertEquals(
            ManualBleSessionStateEvent(
                previousState = BleDeviceSession.DeviceSessionState.SESSION_CLOSING,
                state = BleDeviceSession.DeviceSessionState.SESSION_CLOSED
            ),
            event
        )
        assertEquals(
            listOf("SESSION_OPENING", "SESSION_OPEN", "SESSION_CLOSING", "SESSION_CLOSED"),
            publisher.events
        )
    }

    @Test
    fun `manual gatt queue contract is backed by production Android device session`() {
        val context = mockk<Context>()
        val bluetoothDevice = mockk<BluetoothDevice>(relaxed = true)
        val scanCallback = mockk<BDScanCallback>(relaxed = true)
        val bondingManager = mockk<BDBondingListener>(relaxed = true)
        val factory = mockk<BleGattFactory>(relaxed = true)
        val looper = mockk<Looper>(relaxed = true)
        every { context.mainLooper } returns looper
        every { bluetoothDevice.address } returns "00:11:22:33:44:55"
        every { factory.getRemoteServices(any()) } returns emptySet()
        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers {
            firstArg<Runnable>().run()
            true
        }

        val session = BDDeviceSessionImpl(context, bluetoothDevice, scanCallback, bondingManager, factory)
        val queue: ManualBleGattOperationQueue = session

        session.sessionState = BleDeviceSession.DeviceSessionState.SESSION_OPEN
        queue.pauseScanningForGattOperation()
        queue.resumeScanningAfterGattOperation()

        assertTrue(queue.isGattConnected())
        assertEquals(0, queue.queuedOperationCount())
        assertEquals(
            ManualBleGattQueueSnapshot(queuedOperationCount = 0, isConnected = true),
            queue.gattQueueSnapshot()
        )
        verify(exactly = 1) { scanCallback.stopScan() }
        verify(exactly = 1) { scanCallback.startScan() }
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

        override fun publishSessionState(session: BleDeviceSession, state: BleDeviceSession.DeviceSessionState): ManualBleSessionStateEvent {
            (session as FakeManualBleSession).update(state)
            events += state.name
            return ManualBleSessionStateEvent(session.previousState, session.sessionState)
        }
    }

    private fun createProductionScanner(
        bluetoothAdapter: BluetoothAdapter,
        bluetoothLeScanner: BluetoothLeScanner,
        scanningNeeded: AtomicBoolean
    ): BDScanCallback {
        val context = mockk<Context>()
        every { context.mainLooper } returns mockk<Looper>(relaxed = true)
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.bluetoothLeScanner } returns bluetoothLeScanner
        every { bluetoothLeScanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) } just runs
        every { bluetoothLeScanner.stopScan(any<ScanCallback>()) } just runs
        return BDScanCallback(
            context,
            bluetoothAdapter,
            object : BDScanCallback.BDScanCallbackInterface {
                override fun deviceDiscovered(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray, type: EVENT_TYPE) = Unit
                override fun scanStartError(error: String) = Unit
                override fun isScanningNeeded(): Boolean = scanningNeeded.get()
            }
        ).also { it.opportunistic = false }
    }
}
