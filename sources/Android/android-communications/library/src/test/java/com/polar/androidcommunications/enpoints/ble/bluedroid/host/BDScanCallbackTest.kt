package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Looper
import android.os.ParcelUuid
import com.polar.androidcommunications.common.ble.BleUtils.EVENT_TYPE
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BDScanCallbackTest {

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
    fun commandState_serializesScannerTransitions() {
        val isScanningNeededEntered = CountDownLatch(1)
        val releaseIsScanningNeeded = CountDownLatch(1)
        val stopScanCompleted = AtomicBoolean(false)
        val sut = createSut(
            bluetoothEnabled = true,
            isScanningNeeded = {
                isScanningNeededEntered.countDown()
                assertTrue(releaseIsScanningNeeded.await(2, TimeUnit.SECONDS))
                true
            }
        )
        sut.opportunistic = false

        val clientThread = Thread { sut.clientAdded() }
        clientThread.start()
        assertTrue(isScanningNeededEntered.await(2, TimeUnit.SECONDS))

        val stopThread = Thread {
            sut.stopScan()
            stopScanCompleted.set(true)
        }
        stopThread.start()

        assertFalse(stopScanCompleted.get())
        releaseIsScanningNeeded.countDown()
        clientThread.join(2_000)
        stopThread.join(2_000)

        assertFalse(clientThread.isAlive)
        assertFalse(stopThread.isAlive)
        assertTrue(stopScanCompleted.get())
        assertEquals("STOPPED", scannerState(sut))
    }

    @Test
    fun shutdown_stopsScanningCancelsLifecycleAndIgnoresLaterCommands() {
        val startCount = java.util.concurrent.atomic.AtomicInteger(0)
        val stopCount = java.util.concurrent.atomic.AtomicInteger(0)
        val sut = createSut(
            bluetoothEnabled = true,
            isScanningNeeded = { true },
            onStartScan = { startCount.incrementAndGet() },
            onStopScan = { stopCount.incrementAndGet() }
        )
        sut.opportunistic = true

        sut.clientAdded()
        assertEquals(1, startCount.get())
        assertEquals("SCANNING", scannerState(sut))

        sut.shutdown()
        sut.startScan()
        sut.clientAdded()
        sut.powerOn()

        assertEquals(1, startCount.get())
        assertEquals(1, stopCount.get())
        assertEquals("IDLE", scannerState(sut))
        assertTrue(sut.scannerSnapshot().isShutdown)
    }

    private fun createSut(
        bluetoothEnabled: Boolean,
        isScanningNeeded: () -> Boolean,
        onStartScan: () -> Unit = {},
        onStopScan: () -> Unit = {}
    ): BDScanCallback {
        val context = mockk<Context>()
        val bluetoothAdapter = mockk<BluetoothAdapter>()
        val scanner = mockk<BluetoothLeScanner>(relaxed = true)
        val callbackInterface = object : BDScanCallback.BDScanCallbackInterface {
            override fun deviceDiscovered(device: BluetoothDevice, rssi: Int, scanRecord: ByteArray, type: EVENT_TYPE) = Unit
            override fun scanStartError(error: String) = Unit
            override fun isScanningNeeded(): Boolean = isScanningNeeded()
        }

        every { context.mainLooper } returns mockk<Looper>(relaxed = true)
        every { bluetoothAdapter.isEnabled } returns bluetoothEnabled
        every { bluetoothAdapter.bluetoothLeScanner } returns scanner
        every { scanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) } answers {
            onStartScan()
        }
        every { scanner.stopScan(any<ScanCallback>()) } answers {
            onStopScan()
        }

        return BDScanCallback(context, bluetoothAdapter, callbackInterface)
    }

    private fun scannerState(scanner: BDScanCallback): String {
        val field = scanner.javaClass.getDeclaredField("state")
        field.isAccessible = true
        return requireNotNull(field.get(scanner)).toString()
    }
}
