import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.IntentFilter
import android.os.ParcelUuid
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.BleBattClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleBattClient.Companion.BATTERY_SERVICE
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient.Companion.PFC_SERVICE
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient.PfcMessage
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdOfflineRecTriggerMode
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdOfflineRecTriggerStatus
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdOfflineTrigger
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSecret
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSetting
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.Companion.getFileSystemType
import com.polar.androidcommunications.common.ble.BleUtils.AD_TYPE
import com.polar.androidcommunications.common.ble.BleUtils.EVENT_TYPE
import com.polar.androidcommunications.http.fwu.FirmwareUpdateApi
import com.polar.androidcommunications.http.fwu.FirmwareUpdateRequest
import com.polar.androidcommunications.http.fwu.FirmwareUpdateResponse
import com.polar.sdk.api.model.PolarUserDeviceSettings
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDScanCallback
import com.polar.shared.runtime.PolarFileFacadeOperation
import com.polar.shared.runtime.PolarFileRuntimeErrorOperation
import com.polar.shared.runtime.PolarRuntimeOrchestration
import com.polar.shared.runtime.PolarRestFacadeOperation
import com.polar.shared.runtime.PolarUserDeviceSettingsOperation
import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import com.polar.shared.sdk.PolarSdLogMagnetometerFrequencyName
import com.polar.shared.sdk.PolarSdLogTriggerName
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.errors.PolarBleSdkInstanceException
import com.polar.sdk.api.errors.PolarOperationNotSupported
import com.polar.sdk.api.model.CheckFirmwareUpdateStatus
import com.polar.sdk.api.model.FirmwareUpdateStatus
import com.polar.sdk.api.model.LedConfig
import com.polar.sdk.api.model.LogConfig
import com.polar.sdk.api.model.PolarFirstTimeUseConfig
import com.polar.sdk.api.model.PolarFirstTimeUseConfig.Gender
import com.polar.sdk.api.model.PolarFirstTimeUseConfig.TypicalDay
import com.polar.sdk.api.model.PolarOfflineRecordingTrigger
import com.polar.sdk.api.model.PolarOfflineRecordingTriggerMode
import com.polar.sdk.api.model.PolarRecordingSecret
import com.polar.sdk.api.model.PolarSensorSetting
import com.polar.sdk.api.model.UserIdentifierType
import com.polar.sdk.api.model.toProto
import com.polar.sdk.api.model.restapi.actionPaths
import com.polar.sdk.api.model.restapi.events
import com.polar.sdk.impl.BDBleApiImpl
import com.polar.sdk.impl.planSetLocalTimeV2ForCurrentZone
import com.polar.sdk.impl.utils.PolarRuntimePlannerAdapter
import data.SensorDataLog.PbSensorDataLog
import protocol.PftpError.PbPFtpError
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import fi.polar.remote.representation.protobuf.Device
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbDeviceLocation
import fi.polar.remote.representation.protobuf.Types.PbDuration
import fi.polar.remote.representation.protobuf.Types.PbSampleType
import fi.polar.remote.representation.protobuf.Types.PbSystemDateTime
import fi.polar.remote.representation.protobuf.Types.PbTime
import fi.polar.remote.representation.protobuf.Structures.PbVersion
import fi.polar.remote.representation.protobuf.AutomaticSamples.PbAutomaticSampleSessions
import fi.polar.remote.representation.protobuf.UserIds.PbUserIdentifier
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserDeviceGeneralSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserDeviceTelemetrySettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserDeviceSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserAutomaticMeasurementSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUsbConnectionSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbAutomaticMeasurementSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbAutomaticTrainingDetectionSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import protocol.PftpNotification
import protocol.PftpRequest
import protocol.PftpResponse
import retrofit2.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BDBleApiImplTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        BDBleApiImpl.clearInstance()

        mockkStatic(ParcelUuid::class)
        every { ParcelUuid.fromString(any()) } returns mockk(relaxed = true)

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } just runs

        mockkConstructor(ScanFilter.Builder::class)
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(any()) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(null) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().setManufacturerData(any(), any()) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().build() } returns mockk(relaxed = true)

        mockkConstructor(BDScanCallback::class)
        every { anyConstructed<BDScanCallback>().powerOn() } just runs
        every { anyConstructed<BDScanCallback>().powerOff() } just runs

        val bluetoothAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val bluetoothManager = mockk<BluetoothManager>(relaxed = true)
        every { bluetoothManager.adapter } returns bluetoothAdapter

        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { context.registerReceiver(any(), any<IntentFilter>()) } returns null
    }

    @After
    fun tearDown() {
        BDBleApiImpl.clearInstance()
        unmockkStatic(ParcelUuid::class)
        unmockkConstructor(IntentFilter::class)
        unmockkConstructor(ScanFilter.Builder::class)
        unmockkConstructor(BDScanCallback::class)
        unmockkObject(PolarServiceClientUtils)
        unmockkObject(BlePolarDeviceCapabilitiesUtility)
    }

    @Test
    fun singletonInstanceForPolarBleSDK() {
        // Arrange
        val polarBleApiDefaultInstance =
            BDBleApiImpl.getInstance(
                context,
                setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO)
            )

        // Act
        val polarBleApiSecondInstance =
            BDBleApiImpl.getInstance(
                context,
                setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO)
            )

        // Assert
        Assert.assertEquals(polarBleApiDefaultInstance, polarBleApiSecondInstance)
    }

    @Test
    fun singletonInstanceNotPossibleIfDifferentFeaturesRequired() {

        // Arrange
        BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO)
        )

        // Act && Assert
        Assert.assertThrows(PolarBleSdkInstanceException::class.java) {
            BDBleApiImpl.getInstance(
                context,
                setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER)
            )
        }
    }

    @Test
    fun searchForDevice_filtersByPrefixAndMapsFacadeStreamOutput() = runTest {
        val polarSession = searchSession(
            name = "Polar 360 AABBCCDD",
            address = "AA:BB:CC:DD:EE:01",
            rssi = -55,
            services = byteArrayOf(0x0D, 0x18, 0xEE.toByte(), 0xFE.toByte())
        )
        val nonPolarSession = searchSession(name = "Garmin Device 1234", address = "AA:BB:CC:DD:EE:02", rssi = -65)
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.search(false) } returns flowOf(nonPolarSession, polarSession)
        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType("360") } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)).withListener(listener)

        val values = api.searchForDevice(withDeviceNameFilterPrefix = "Polar").toList()

        Assert.assertEquals(1, values.size)
        val value = values.single()
        Assert.assertEquals("AABBCCDD", value.deviceId)
        Assert.assertEquals("AA:BB:CC:DD:EE:01", value.address)
        Assert.assertEquals(-55, value.rssi)
        Assert.assertEquals("Polar 360 AABBCCDD", value.name)
        Assert.assertTrue(value.isConnectable)
        Assert.assertTrue(value.hasHeartRateService)
        Assert.assertTrue(value.hasFileSystemService)
        Assert.assertTrue(value.hasSAGRFCFileSystem)
    }

    @Test
    fun searchForDevice_collectorCancellationCancelsListenerSearchStream() = runTest {
        var upstreamCancelled = false
        val session = searchSession(name = "Polar H10 AABBCCDD")
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.search(false) } returns flow {
            try {
                emit(session)
                awaitCancellation()
            } finally {
                upstreamCancelled = true
            }
        }
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)).withListener(listener)
        val values = mutableListOf<String>()

        val collection = launch {
            api.searchForDevice(withDeviceNameFilterPrefix = "Polar").collect { values.add(it.deviceId) }
        }
        testScheduler.advanceUntilIdle()
        collection.cancelAndJoin()

        Assert.assertEquals(listOf("AABBCCDD"), values)
        Assert.assertTrue(upstreamCancelled)
    }

    @Test
    fun searchForDevice_propagatesListenerSearchError() = runTest {
        val expected = IllegalStateException("scan failed")
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.search(false) } returns flow { throw expected }
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)).withListener(listener)

        try {
            api.searchForDevice(withDeviceNameFilterPrefix = "Polar").toList()
            Assert.fail("Expected listener search error")
        } catch (error: IllegalStateException) {
            Assert.assertEquals(expected, error)
        }
    }

    @Test
    fun searchForDevice_emitsMatchingValuesBeforeListenerSearchError() = runTest {
        val expected = IllegalStateException("scan failed after value")
        val session = searchSession(name = "Polar H10 AABBCCDD")
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.search(false) } returns flow {
            emit(session)
            throw expected
        }
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)).withListener(listener)
        val deviceIds = mutableListOf<String>()

        try {
            api.searchForDevice(withDeviceNameFilterPrefix = "Polar").collect { deviceIds.add(it.deviceId) }
            Assert.fail("Expected listener search error")
        } catch (error: IllegalStateException) {
            Assert.assertEquals(expected, error)
        }

        Assert.assertEquals(listOf("AABBCCDD"), deviceIds)
    }

    @Test
    fun searchForDevice_emitsMatchingValuesBeforeDisconnectError() = runTest {
        val session = searchSession(name = "Polar H10 AABBCCDD")
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.search(false) } returns flow {
            emit(session)
            throw BleDisconnected()
        }
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)).withListener(listener)
        val deviceIds = mutableListOf<String>()

        try {
            api.searchForDevice(withDeviceNameFilterPrefix = "Polar").collect { deviceIds.add(it.deviceId) }
            Assert.fail("Expected disconnect error")
        } catch (error: BleDisconnected) {
            Assert.assertEquals(BleDisconnected().toString(), error.toString())
        }

        Assert.assertEquals(listOf("AABBCCDD"), deviceIds)
    }

    @Test
    fun searchForDevice_emitsMatchingValuesBeforeListenerSearchCompletion() = runTest {
        val session = searchSession(name = "Polar H10 AABBCCDD")
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.search(false) } returns flowOf(session)
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)).withListener(listener)

        val values = api.searchForDevice(withDeviceNameFilterPrefix = "Polar").toList()

        Assert.assertEquals(listOf("AABBCCDD"), values.map { it.deviceId })
    }

    @Test
    fun startListenForPolarHrBroadcasts_filtersDeviceIdsAndMapsUpdatedHrAdvertisement() = runTest {
        val matchingSession = searchSession(
            name = "Polar H10 AABBCCDD",
            address = "AA:BB:CC:DD:EE:03",
            rssi = -50,
            hrPayload = byteArrayOf(0xE3.toByte(), 0xFE.toByte(), 95, 96)
        )
        val otherSession = searchSession(
            name = "Polar H10 11223344",
            address = "AA:BB:CC:DD:EE:04",
            rssi = -60,
            hrPayload = byteArrayOf(0xE7.toByte(), 0xFE.toByte(), 88, 89)
        )
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.search(false) } returns flowOf(otherSession, matchingSession)
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)).withListener(listener)

        val values = api.startListenForPolarHrBroadcasts(setOf("AABBCCDD")).toList()

        Assert.assertEquals(1, values.size)
        val value = values.single()
        Assert.assertEquals("AABBCCDD", value.polarDeviceInfo.deviceId)
        Assert.assertEquals("AA:BB:CC:DD:EE:03", value.polarDeviceInfo.address)
        Assert.assertEquals(-50, value.polarDeviceInfo.rssi)
        Assert.assertEquals("Polar H10 AABBCCDD", value.polarDeviceInfo.name)
        Assert.assertTrue(value.polarDeviceInfo.isConnectable)
        Assert.assertEquals(96, value.hr)
        Assert.assertTrue(value.batteryStatus)
    }

    @Test
    fun startListenForPolarHrBroadcasts_collectorCancellationCancelsListenerSearchStream() = runTest {
        var upstreamCancelled = false
        val session = searchSession(
            name = "Polar H10 AABBCCDD",
            hrPayload = byteArrayOf(0xE3.toByte(), 0xFE.toByte(), 95, 96)
        )
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.search(false) } returns flow {
            try {
                emit(session)
                awaitCancellation()
            } finally {
                upstreamCancelled = true
            }
        }
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)).withListener(listener)
        val values = mutableListOf<Int>()

        val collection = launch {
            api.startListenForPolarHrBroadcasts(null).collect { values.add(it.hr) }
        }
        testScheduler.advanceUntilIdle()
        collection.cancelAndJoin()

        Assert.assertEquals(listOf(96), values)
        Assert.assertTrue(upstreamCancelled)
    }

    @Test
    fun startListenForPolarHrBroadcasts_propagatesListenerSearchError() = runTest {
        val expected = IllegalStateException("scan failed")
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.search(false) } returns flow { throw expected }
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)).withListener(listener)

        try {
            api.startListenForPolarHrBroadcasts(null).toList()
            Assert.fail("Expected listener search error")
        } catch (error: IllegalStateException) {
            Assert.assertEquals(expected, error)
        }
    }

    @Test
    fun startListenForPolarHrBroadcasts_emitsValuesBeforeListenerSearchError() = runTest {
        val expected = IllegalStateException("scan failed after value")
        val session = searchSession(
            name = "Polar H10 AABBCCDD",
            hrPayload = byteArrayOf(0xE3.toByte(), 0xFE.toByte(), 95, 96)
        )
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.search(false) } returns flow {
            emit(session)
            throw expected
        }
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)).withListener(listener)
        val hrs = mutableListOf<Int>()
        val deviceIds = mutableListOf<String>()

        try {
            api.startListenForPolarHrBroadcasts(null).collect {
                hrs.add(it.hr)
                deviceIds.add(it.polarDeviceInfo.deviceId)
            }
            Assert.fail("Expected listener search error")
        } catch (error: IllegalStateException) {
            Assert.assertEquals(expected, error)
        }

        Assert.assertEquals(listOf(96), hrs)
        Assert.assertEquals(listOf("AABBCCDD"), deviceIds)
    }

    @Test
    fun startListenForPolarHrBroadcasts_emitsValuesBeforeDisconnectError() = runTest {
        val session = searchSession(
            name = "Polar H10 AABBCCDD",
            hrPayload = byteArrayOf(0xE3.toByte(), 0xFE.toByte(), 95, 96)
        )
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.search(false) } returns flow {
            emit(session)
            throw BleDisconnected()
        }
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)).withListener(listener)
        val hrs = mutableListOf<Int>()
        val deviceIds = mutableListOf<String>()

        try {
            api.startListenForPolarHrBroadcasts(null).collect {
                hrs.add(it.hr)
                deviceIds.add(it.polarDeviceInfo.deviceId)
            }
            Assert.fail("Expected disconnect error")
        } catch (error: BleDisconnected) {
            Assert.assertEquals(BleDisconnected().toString(), error.toString())
        }

        Assert.assertEquals(listOf(96), hrs)
        Assert.assertEquals(listOf("AABBCCDD"), deviceIds)
    }

    @Test
    fun startListenForPolarHrBroadcasts_emitsValuesBeforeListenerSearchCompletion() = runTest {
        val session = searchSession(
            name = "Polar H10 AABBCCDD",
            hrPayload = byteArrayOf(0xE3.toByte(), 0xFE.toByte(), 95, 96)
        )
        val listener = mockk<BleDeviceListener>(relaxed = true)
        every { listener.search(false) } returns flowOf(session)
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR)).withListener(listener)

        val values = api.startListenForPolarHrBroadcasts(null).toList()

        Assert.assertEquals(listOf(96), values.map { it.hr })
        Assert.assertEquals(listOf("AABBCCDD"), values.map { it.polarDeviceInfo.deviceId })
    }

    @Test
    fun `checkFirmwareUpdate uses injected firmware API`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }
        coEvery { client.request(any()) } returns deviceInfoBytes
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", "https://example.invalid/fw.zip"))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.checkFirmwareUpdate(deviceId).toList()

        Assert.assertEquals(listOf(CheckFirmwareUpdateStatus.CheckFwUpdateAvailable("9.9.9")), statuses)
        Assert.assertEquals(1, firmwareApi.checkRequests.size)
        val request = firmwareApi.checkRequests.single()
        Assert.assertEquals("polar-sensor-data-collector-android", request.clientId)
        Assert.assertEquals("1.2.0", request.firmwareVersion)
        Assert.assertEquals("00112233.01", request.hardwareCode)
    }

    @Test
    fun `checkFirmwareUpdate maps higher server version without package url to not available through shared availability`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }
        coEvery { client.request(any()) } returns deviceInfoBytes
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", ""))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.checkFirmwareUpdate(deviceId).toList()

        Assert.assertEquals(listOf(CheckFirmwareUpdateStatus.CheckFwUpdateNotAvailable("No fw update available, device firmware version 1.2.0")), statuses)
        Assert.assertEquals(1, firmwareApi.checkRequests.size)
    }

    @Test
    fun `checkFirmwareUpdate maps injected retryable server failure to failed status`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }
        coEvery { client.request(any()) } returns deviceInfoBytes
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.error(503, mockk<ResponseBody>(relaxed = true))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.checkFirmwareUpdate(deviceId).toList()

        Assert.assertEquals(1, statuses.size)
        val failed = statuses.single() as CheckFirmwareUpdateStatus.CheckFwUpdateFailed
        Assert.assertEquals("Unexpected response code: 503", failed.details)
        Assert.assertEquals(1, firmwareApi.checkRequests.size)
        Assert.assertEquals(emptyList<String>(), firmwareApi.packageUrls)
    }

    @Test
    fun `checkFirmwareUpdate maps injected client request failure through shared terminal plan`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }
        coEvery { client.request(any()) } returns deviceInfoBytes
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.error(400, mockk<ResponseBody>(relaxed = true))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.checkFirmwareUpdate(deviceId).toList()

        Assert.assertEquals("client-request-failure", PolarRuntimePlannerAdapter.planFirmwareWorkflow(id = "client-request-failure", statuses = listOf("fwUpdateFailed")).terminalError)
        Assert.assertEquals(1, statuses.size)
        val failed = statuses.single() as CheckFirmwareUpdateStatus.CheckFwUpdateFailed
        Assert.assertTrue(failed.details, failed.details.startsWith("Bad request to firmware update API:"))
        Assert.assertEquals(1, firmwareApi.checkRequests.size)
        Assert.assertEquals(emptyList<String>(), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware maps injected firmware check failure to failed status without download`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.request(any()) } returns deviceInfoBytes
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.error(503, mockk<ResponseBody>(relaxed = true))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertEquals(1, statuses.size)
        val failed = statuses.single() as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertEquals("Unexpected response code: 503", failed.details)
        Assert.assertEquals(3, firmwareApi.checkRequests.size)
        Assert.assertEquals(emptyList<String>(), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware maps injected client request failure through shared terminal plan without retry or download`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.request(any()) } returns deviceInfoBytes
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.error(400, mockk<ResponseBody>(relaxed = true))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertEquals("client-request-failure", PolarRuntimePlannerAdapter.planFirmwareWorkflow(id = "client-request-failure", statuses = listOf("fwUpdateFailed")).terminalError)
        Assert.assertEquals(1, statuses.size)
        val failed = statuses.single() as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(failed.details, failed.details.startsWith("Bad request to firmware update API:"))
        Assert.assertEquals(1, firmwareApi.checkRequests.size)
        Assert.assertEquals(emptyList<String>(), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware retries retryable firmware check failures using shared delay plan`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.request(any()) } returns deviceInfoBytes
        val delayedMillis = mutableListOf<Long>()
        api.firmwareRetryDelay = { delayMillis -> delayedMillis.add(delayMillis) }
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponses = listOf(
                Response.error(503, mockk<ResponseBody>(relaxed = true)),
                Response.error(503, mockk<ResponseBody>(relaxed = true)),
                Response.success(FirmwareUpdateResponse("1.0.0", "https://example.invalid/fw.zip"))
            )
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertEquals(1, statuses.size)
        val notAvailable = statuses.single() as FirmwareUpdateStatus.FwUpdateNotAvailable
        Assert.assertEquals("Firmware update not available", notAvailable.details)
        Assert.assertEquals(3, firmwareApi.checkRequests.size)
        Assert.assertEquals(listOf(1000L, 2000L), delayedMillis)
        Assert.assertEquals(emptyList<String>(), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware restores multi BLE mode after no update early return`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, psFtpSession) = mockPsFtpConnection(deviceId)
        val (pfcClient, pfcSession) = mockPfcConnection(deviceId)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } returns psFtpSession
        every { PolarServiceClientUtils.sessionPsPfcClientReady(deviceId, any()) } returns pfcSession
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }
        coEvery { client.request(any()) } returns deviceInfoBytes
        val pfcOperations = mutableListOf<String>()
        coEvery { pfcClient.sendControlPointCommand(PfcMessage.PFC_REQUEST_MULTI_CONNECTION_SETTING, null) } answers {
            pfcOperations += "request"
            BlePfcClient.PfcResponse(byteArrayOf(0, PfcMessage.PFC_REQUEST_MULTI_CONNECTION_SETTING.numVal.toByte(), 1, 1))
        }
        coEvery { pfcClient.sendControlPointCommand(PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING, any<Int>()) } answers {
            pfcOperations += "configure:${secondArg<Int>()}"
            BlePfcClient.PfcResponse(byteArrayOf(0, PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING.numVal.toByte(), 1))
        }
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("1.2.0", "https://example.invalid/fw.zip"))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertEquals(listOf(FirmwareUpdateStatus.FwUpdateNotAvailable("Firmware update not available")), statuses)
        Assert.assertEquals(listOf("request", "configure:0", "configure:1"), pfcOperations)
        Assert.assertEquals(1, firmwareApi.checkRequests.size)
        Assert.assertEquals(emptyList<String>(), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware restores automatic reconnection after no update early return`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }
        coEvery { client.request(any()) } returns deviceInfoBytes
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("1.2.0", "https://example.invalid/fw.zip"))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }
        api.setAutomaticReconnection(false)

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertEquals(listOf(FirmwareUpdateStatus.FwUpdateNotAvailable("Firmware update not available")), statuses)
        Assert.assertEquals(false, automaticReconnection(api))
        Assert.assertEquals(1, firmwareApi.checkRequests.size)
        Assert.assertEquals(emptyList<String>(), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware maps package download failure to failed status before device writes`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.request(any()) } returns deviceInfoBytes
        coEvery { client.write(any(), any()) } returns flowOf(0)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", "https://example.invalid/fw.zip"))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertEquals(3, statuses.size)
        Assert.assertTrue(statuses[0] is FirmwareUpdateStatus.FetchingFwUpdatePackage)
        Assert.assertEquals(FirmwareUpdateStatus.FetchingFwUpdatePackage("Fetching firmware package to 9.9.9"), statuses[1])
        val failed = statuses[2] as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(failed.details, failed.details.contains("backup not available"))
        Assert.assertTrue(failed.details, failed.details.contains("Package download is not used by this test"))
        Assert.assertEquals(listOf("https://example.invalid/fw.zip"), firmwareApi.packageUrls)
        coVerify(exactly = 0) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware with direct firmware url maps package download failure before device writes`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(any(), any()) } returns flowOf(0)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("unused", "https://example.invalid/unused.zip"))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId, "https://example.invalid/manual-fw.zip").toList()

        Assert.assertEquals(FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Preparing for firmware update"), statuses[0])
        Assert.assertEquals(FirmwareUpdateStatus.FetchingFwUpdatePackage("Fetching firmware package to manual-fw.zip"), statuses[1])
        val failed = statuses[2] as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(failed.details, failed.details.contains("backup not available"))
        Assert.assertTrue(failed.details, failed.details.contains("Package download is not used by this test"))
        Assert.assertTrue(firmwareApi.checkRequests.isEmpty())
        Assert.assertEquals(listOf("https://example.invalid/manual-fw.zip"), firmwareApi.packageUrls)
        coVerify(exactly = 0) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware with direct firmware url maps empty package to not available before device writes`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(any(), any()) } returns flowOf(0)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("unused", "https://example.invalid/unused.zip")),
            packageBytes = firmwareZip("readme.txt" to byteArrayOf(0x01))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId, "https://example.invalid/manual-fw.zip").toList()

        Assert.assertEquals(FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Preparing for firmware update"), statuses[0])
        Assert.assertEquals(FirmwareUpdateStatus.FetchingFwUpdatePackage("Fetching firmware package to manual-fw.zip"), statuses[1])
        Assert.assertEquals(FirmwareUpdateStatus.FwUpdateNotAvailable("Can not update, firmware files were not available"), statuses[2])
        val failed = statuses[3] as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(failed.details, failed.details.contains("backup not available"))
        Assert.assertTrue(failed.details, failed.details.contains("Firmware files were not available"))
        Assert.assertTrue(firmwareApi.checkRequests.isEmpty())
        Assert.assertEquals(listOf("https://example.invalid/manual-fw.zip"), firmwareApi.packageUrls)
        coVerify(exactly = 0) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware with direct firmware url maps invalid package to not available before device writes`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(any(), any()) } returns flowOf(0)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("unused", "https://example.invalid/unused.zip")),
            packageBytes = byteArrayOf(0x01, 0x02, 0x03)
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId, "https://example.invalid/manual-fw.zip").toList()

        Assert.assertEquals(FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Preparing for firmware update"), statuses[0])
        Assert.assertEquals(FirmwareUpdateStatus.FetchingFwUpdatePackage("Fetching firmware package to manual-fw.zip"), statuses[1])
        Assert.assertEquals(FirmwareUpdateStatus.FwUpdateNotAvailable("Can not update, firmware files were not available"), statuses[2])
        val failed = statuses[3] as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(failed.details, failed.details.contains("backup not available"))
        Assert.assertTrue(failed.details, failed.details.contains("Firmware files were not available"))
        Assert.assertTrue(firmwareApi.checkRequests.isEmpty())
        Assert.assertEquals(listOf("https://example.invalid/manual-fw.zip"), firmwareApi.packageUrls)
        coVerify(exactly = 0) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware maps empty firmware package to not available before device writes`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.request(any()) } returns deviceInfoBytes
        coEvery { client.write(any(), any()) } returns flowOf(0)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", "https://example.invalid/fw.zip")),
            packageBytes = firmwareZip("readme.txt" to byteArrayOf(0x01))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertEquals(4, statuses.size)
        Assert.assertTrue(statuses[0] is FirmwareUpdateStatus.FetchingFwUpdatePackage)
        Assert.assertEquals(FirmwareUpdateStatus.FetchingFwUpdatePackage("Fetching firmware package to 9.9.9"), statuses[1])
        Assert.assertEquals(FirmwareUpdateStatus.FwUpdateNotAvailable("Can not update, firmware files were not available"), statuses[2])
        val failed = statuses[3] as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(failed.details, failed.details.contains("Firmware files were not available"))
        Assert.assertEquals(listOf("https://example.invalid/fw.zip"), firmwareApi.packageUrls)
        coVerify(exactly = 0) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware maps invalid firmware package to not available before device writes`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.request(any()) } returns deviceInfoBytes
        coEvery { client.write(any(), any()) } returns flowOf(0)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", "https://example.invalid/fw.zip")),
            packageBytes = byteArrayOf(0x01, 0x02, 0x03)
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertEquals(4, statuses.size)
        Assert.assertTrue(statuses[0] is FirmwareUpdateStatus.FetchingFwUpdatePackage)
        Assert.assertEquals(FirmwareUpdateStatus.FetchingFwUpdatePackage("Fetching firmware package to 9.9.9"), statuses[1])
        Assert.assertEquals(FirmwareUpdateStatus.FwUpdateNotAvailable("Can not update, firmware files were not available"), statuses[2])
        val failed = statuses[3] as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(failed.details, failed.details.contains("Firmware files were not available"))
        Assert.assertEquals(listOf("https://example.invalid/fw.zip"), firmwareApi.packageUrls)
        coVerify(exactly = 0) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware cancellation during package download stops before device writes`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.request(any()) } returns deviceInfoBytes
        coEvery { client.write(any(), any()) } returns flowOf(0)
        val packageDownloadStarted = CompletableDeferred<Unit>()
        val packageDownloadCancelled = CompletableDeferred<Unit>()
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", "https://example.invalid/fw.zip")),
            packageDownloadStarted = packageDownloadStarted,
            packageDownloadCancelled = packageDownloadCancelled
        )
        api.firmwareUpdateApiFactory = { firmwareApi }
        val statuses = mutableListOf<FirmwareUpdateStatus>()

        val collection = launch {
            api.updateFirmware(deviceId).toList(statuses)
        }
        withTimeout(1_000) { packageDownloadStarted.await() }
        collection.cancelAndJoin()
        withTimeout(1_000) { packageDownloadCancelled.await() }

        Assert.assertTrue(statuses.any { it is FirmwareUpdateStatus.FetchingFwUpdatePackage })
        Assert.assertEquals(listOf("https://example.invalid/fw.zip"), firmwareApi.packageUrls)
        coVerify(exactly = 0) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware with direct firmware url cancellation during package download stops before device writes`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(any(), any()) } returns flowOf(0)
        val packageDownloadStarted = CompletableDeferred<Unit>()
        val packageDownloadCancelled = CompletableDeferred<Unit>()
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("unused", "https://example.invalid/unused.zip")),
            packageDownloadStarted = packageDownloadStarted,
            packageDownloadCancelled = packageDownloadCancelled
        )
        api.firmwareUpdateApiFactory = { firmwareApi }
        val statuses = mutableListOf<FirmwareUpdateStatus>()

        val collection = launch {
            api.updateFirmware(deviceId, "https://example.invalid/manual-fw.zip").toList(statuses)
        }
        withTimeout(1_000) { packageDownloadStarted.await() }
        collection.cancelAndJoin()
        withTimeout(1_000) { packageDownloadCancelled.await() }

        Assert.assertEquals(listOf(FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Preparing for firmware update"), FirmwareUpdateStatus.FetchingFwUpdatePackage("Fetching firmware package to manual-fw.zip")), statuses)
        Assert.assertTrue(firmwareApi.checkRequests.isEmpty())
        Assert.assertEquals(listOf("https://example.invalid/manual-fw.zip"), firmwareApi.packageUrls)
        coVerify(exactly = 0) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware maps battery too low write terminal to failed status`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }.toByteArray()
        val emptyDirectory = PftpResponse.PbPFtpDirectory.newBuilder().build().toByteArray()
        val requestResponses = ArrayDeque(listOf(deviceInfoBytes, emptyDirectory))
        coEvery { client.request(any()) } answers {
            ByteArrayOutputStream().apply { write(if (requestResponses.isEmpty()) emptyDirectory else requestResponses.removeFirst()) }
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(any(), any()) } returns flow {
            throw BlePsFtpUtils.PftpResponseError("Battery too low", PbPFtpError.BATTERY_TOO_LOW_VALUE)
        }
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", "https://example.invalid/fw.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Reconnecting after factory reset") })
        val failed = statuses.last() as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(statuses.toString(), failed.details.contains("Battery too low to perform firmware update"))
        Assert.assertTrue(statuses.toString(), failed.details.contains("backup restored"))
        Assert.assertEquals(listOf("https://example.invalid/fw.zip"), firmwareApi.packageUrls)
        coVerify(atLeast = 1) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware with direct firmware url maps battery too low write terminal to failed status`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val emptyDirectory = PftpResponse.PbPFtpDirectory.newBuilder().build().toByteArray()
        coEvery { client.request(any()) } returns ByteArrayOutputStream().apply { write(emptyDirectory) }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(any(), any()) } returns flow {
            throw BlePsFtpUtils.PftpResponseError("Battery too low", PbPFtpError.BATTERY_TOO_LOW_VALUE)
        }
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("unused", "https://example.invalid/unused.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId, "https://example.invalid/manual-fw.zip").toList()

        Assert.assertEquals(FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Preparing for firmware update"), statuses[0])
        Assert.assertEquals(FirmwareUpdateStatus.FetchingFwUpdatePackage("Fetching firmware package to manual-fw.zip"), statuses[1])
        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Reconnecting after factory reset") })
        val failed = statuses.last() as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(statuses.toString(), failed.details.contains("Battery too low to perform firmware update"))
        Assert.assertTrue(firmwareApi.checkRequests.isEmpty())
        Assert.assertEquals(listOf("https://example.invalid/manual-fw.zip"), firmwareApi.packageUrls)
        coVerify(atLeast = 1) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware with direct firmware url treats system update reboot write terminal as success`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val emptyDirectory = PftpResponse.PbPFtpDirectory.newBuilder().build().toByteArray()
        coEvery { client.request(any()) } returns ByteArrayOutputStream().apply { write(emptyDirectory) }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(any(), any()) } returns flow {
            throw BlePsFtpUtils.PftpResponseError("Rebooting", PbPFtpError.REBOOTING_VALUE)
        }
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("unused", "https://example.invalid/unused.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId, "https://example.invalid/manual-fw.zip").toList()

        Assert.assertEquals(FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Preparing for firmware update"), statuses[0])
        Assert.assertEquals(FirmwareUpdateStatus.FetchingFwUpdatePackage("Fetching firmware package to manual-fw.zip"), statuses[1])
        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.FinalizingFwUpdate("Reconnecting after updating to manual-fw.zip") })
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateFailed })
        Assert.assertEquals(FirmwareUpdateStatus.FwUpdateCompletedSuccessfully("Firmware update to manual-fw.zip completed successfully"), statuses.last())
        Assert.assertTrue(firmwareApi.checkRequests.isEmpty())
        Assert.assertEquals(listOf("https://example.invalid/manual-fw.zip"), firmwareApi.packageUrls)
        coVerify(atLeast = 1) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware with direct firmware url maps non system reboot write terminal to failed status`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val emptyDirectory = PftpResponse.PbPFtpDirectory.newBuilder().build().toByteArray()
        coEvery { client.request(any()) } returns ByteArrayOutputStream().apply { write(emptyDirectory) }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(any(), any()) } returns flow {
            throw BlePsFtpUtils.PftpResponseError("Rebooting", PbPFtpError.REBOOTING_VALUE)
        }
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("unused", "https://example.invalid/unused.zip")),
            packageBytes = firmwareZip("BTUPDAT.BIN" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId, "https://example.invalid/manual-fw.zip").toList()

        Assert.assertEquals(FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Preparing for firmware update"), statuses[0])
        Assert.assertEquals(FirmwareUpdateStatus.FetchingFwUpdatePackage("Fetching firmware package to manual-fw.zip"), statuses[1])
        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Reconnecting after factory reset") })
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateCompletedSuccessfully })
        val failed = statuses.last() as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(statuses.toString(), failed.details.contains("Rebooting"))
        Assert.assertTrue(firmwareApi.checkRequests.isEmpty())
        Assert.assertEquals(listOf("https://example.invalid/manual-fw.zip"), firmwareApi.packageUrls)
        coVerify(atLeast = 1) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware with direct firmware url emits shared write progress before success`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val emptyDirectory = PftpResponse.PbPFtpDirectory.newBuilder().build().toByteArray()
        val writeHeaders = mutableListOf<ByteArray>()
        coEvery { client.request(any()) } returns ByteArrayOutputStream().apply { write(emptyDirectory) }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(capture(writeHeaders), any()) } returns flowOf(1L, 2L)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("unused", "https://example.invalid/unused.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId, "https://example.invalid/manual-fw.zip").toList()

        val progress = statuses.filterIsInstance<FirmwareUpdateStatus.WritingFwUpdatePackage>().map { it.details }
        Assert.assertEquals(
            listOf(
                "Writing firmware update file SYSUPDAT.IMG (50%), bytes written: 1/2",
                "Writing firmware update file SYSUPDAT.IMG (100%), bytes written: 2/2"
            ),
            progress
        )
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateFailed })
        Assert.assertEquals(FirmwareUpdateStatus.FwUpdateCompletedSuccessfully("Firmware update to manual-fw.zip completed successfully"), statuses.last())
        val writePaths = writeHeaders.map { PftpRequest.PbPFtpOperation.parseFrom(it).path }
        Assert.assertEquals("/SYSUPDAT.IMG", writePaths.first())
        Assert.assertTrue(writePaths.toString(), writePaths.contains("/U/0/S/UDEVSET.BPB"))
        Assert.assertTrue(firmwareApi.checkRequests.isEmpty())
        Assert.assertEquals(listOf("https://example.invalid/manual-fw.zip"), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware emits shared write progress after firmware check before success`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val emptyDirectory = PftpResponse.PbPFtpDirectory.newBuilder().build().toByteArray()
        val writeHeaders = mutableListOf<ByteArray>()
        coEvery { client.request(any()) } answers {
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            ByteArrayOutputStream().apply {
                write(if (operation.path == "/DEVICE.BPB") deviceInfo.toByteArray() else emptyDirectory)
            }
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(capture(writeHeaders), any()) } returns flowOf(1L, 2L)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", "https://example.invalid/fw.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        val progress = statuses.filterIsInstance<FirmwareUpdateStatus.WritingFwUpdatePackage>().map { it.details }
        Assert.assertEquals(
            listOf(
                "Writing firmware update file SYSUPDAT.IMG (50%), bytes written: 1/2",
                "Writing firmware update file SYSUPDAT.IMG (100%), bytes written: 2/2"
            ),
            progress
        )
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateFailed })
        Assert.assertEquals(FirmwareUpdateStatus.FwUpdateCompletedSuccessfully("Firmware update to 9.9.9 completed successfully"), statuses.last())
        val writePaths = writeHeaders.map { PftpRequest.PbPFtpOperation.parseFrom(it).path }
        Assert.assertEquals("/SYSUPDAT.IMG", writePaths.first())
        Assert.assertTrue(writePaths.toString(), writePaths.contains("/U/0/S/UDEVSET.BPB"))
        Assert.assertEquals(1, firmwareApi.checkRequests.size)
        Assert.assertEquals(listOf("https://example.invalid/fw.zip"), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware treats system update reboot write terminal as success`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }.toByteArray()
        val emptyDirectory = PftpResponse.PbPFtpDirectory.newBuilder().build().toByteArray()
        val requestResponses = ArrayDeque(listOf(deviceInfoBytes, emptyDirectory))
        coEvery { client.request(any()) } answers {
            ByteArrayOutputStream().apply { write(if (requestResponses.isEmpty()) emptyDirectory else requestResponses.removeFirst()) }
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(any(), any()) } returns flow {
            throw BlePsFtpUtils.PftpResponseError("Rebooting", PbPFtpError.REBOOTING_VALUE)
        }
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", "https://example.invalid/fw.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.FinalizingFwUpdate("Reconnecting after updating to 9.9.9") })
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateFailed })
        Assert.assertEquals(FirmwareUpdateStatus.FwUpdateCompletedSuccessfully("Firmware update to 9.9.9 completed successfully"), statuses.last())
        Assert.assertEquals(listOf("https://example.invalid/fw.zip"), firmwareApi.packageUrls)
        coVerify(atLeast = 1) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware maps non system reboot write terminal to failed status`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val deviceInfoBytes = ByteArrayOutputStream().apply {
            deviceInfo.writeTo(this)
        }.toByteArray()
        val emptyDirectory = PftpResponse.PbPFtpDirectory.newBuilder().build().toByteArray()
        val requestResponses = ArrayDeque(listOf(deviceInfoBytes, emptyDirectory))
        coEvery { client.request(any()) } answers {
            ByteArrayOutputStream().apply { write(if (requestResponses.isEmpty()) emptyDirectory else requestResponses.removeFirst()) }
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(any(), any()) } returns flow {
            throw BlePsFtpUtils.PftpResponseError("Rebooting", PbPFtpError.REBOOTING_VALUE)
        }
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", "https://example.invalid/fw.zip")),
            packageBytes = firmwareZip("BTUPDAT.BIN" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.PreparingDeviceForFwUpdate("Reconnecting after factory reset") })
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateCompletedSuccessfully })
        val failed = statuses.last() as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(statuses.toString(), failed.details.contains("Rebooting"))
        Assert.assertEquals(listOf("https://example.invalid/fw.zip"), firmwareApi.packageUrls)
        coVerify(atLeast = 1) { client.write(any(), any()) }
    }

    @Test
    fun `updateFirmware keeps success when backup restore write fails after firmware write`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val rootDirectory = PftpResponse.PbPFtpDirectory.newBuilder()
            .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("BACKUP.TXT").setSize(24L))
            .build()
        val emptyDirectory = PftpResponse.PbPFtpDirectory.newBuilder().build().toByteArray()
        coEvery { client.request(any()) } answers {
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            val responseBytes = when (operation.path) {
                "/DEVICE.BPB" -> deviceInfo.toByteArray()
                "/SYS/" -> rootDirectory.toByteArray()
                "/SYS/BACKUP.TXT" -> "/U/0/S/UDEVSET.BPB\n".toByteArray()
                "/U/0/S/UDEVSET.BPB" -> byteArrayOf(0x0A, 0x0B)
                else -> emptyDirectory
            }
            ByteArrayOutputStream().apply { write(responseBytes) }
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        val writeHeaders = mutableListOf<ByteArray>()
        coEvery { client.write(capture(writeHeaders), any()) } answers {
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            if (operation.path == "/U/0/S/UDEVSET.BPB") {
                flow { throw RuntimeException("restore write failed") }
            } else {
                flowOf(2L)
            }
        }
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", "https://example.invalid/fw.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.FinalizingFwUpdate("Restoring backup on device") })
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateFailed })
        Assert.assertEquals(FirmwareUpdateStatus.FwUpdateCompletedSuccessfully("Firmware update to 9.9.9 completed successfully"), statuses.last())
        val writePaths = writeHeaders.map { PftpRequest.PbPFtpOperation.parseFrom(it).path }
        Assert.assertTrue(writePaths.toString(), writePaths.contains("/SYSUPDAT.IMG"))
        Assert.assertTrue(writePaths.toString(), writePaths.contains("/U/0/S/UDEVSET.BPB"))
        Assert.assertEquals(listOf("https://example.invalid/fw.zip"), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware maps final set time failure to failed status`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId, polarDeviceType = "h10")
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val setTimeFailure = RuntimeException("set local time failed")
        coEvery { client.request(any()) } returns ByteArrayOutputStream().apply { write(deviceInfo.toByteArray()) }
        coEvery { client.query(any(), any()) } answers {
            if (firstArg<Int>() == PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE) throw setTimeFailure
            ByteArrayOutputStream()
        }
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(any(), any()) } returns flowOf(2L)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", "https://example.invalid/fw.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.FinalizingFwUpdate("Setting device time") })
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateCompletedSuccessfully })
        val failed = statuses.last() as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(statuses.toString(), failed.details.contains("set local time failed"))
        Assert.assertEquals(listOf("https://example.invalid/fw.zip"), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware maps final restart failure to failed status`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val emptyDirectory = PftpResponse.PbPFtpDirectory.newBuilder().build().toByteArray()
        val requestResponses = ArrayDeque(listOf(deviceInfo.toByteArray(), emptyDirectory))
        val restartFailure = RuntimeException("restart notification failed")
        var resetNotifications = 0
        coEvery { client.request(any()) } answers {
            ByteArrayOutputStream().apply { write(if (requestResponses.isEmpty()) emptyDirectory else requestResponses.removeFirst()) }
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } answers {
            if (firstArg<Int>() == PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal && ++resetNotifications == 2) {
                throw restartFailure
            }
            Unit
        }
        coEvery { client.write(any(), any()) } returns flowOf(2L)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", "https://example.invalid/fw.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.FinalizingFwUpdate("Restarting device") })
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateCompletedSuccessfully })
        val failed = statuses.last() as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(statuses.toString(), failed.details.contains("restart notification failed"))
        Assert.assertEquals(listOf("https://example.invalid/fw.zip"), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware keeps success when final stop sync fails`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId, polarDeviceType = "360")
        val deviceInfo = Device.PbDeviceInfo.newBuilder()
            .setDeviceVersion(PbVersion.newBuilder().setMajor(1).setMinor(2).setPatch(0))
            .setModelName("Model")
            .setHardwareCode("00112233.01")
            .build()
        val emptyDirectory = PftpResponse.PbPFtpDirectory.newBuilder().build().toByteArray()
        val requestResponses = ArrayDeque(listOf(deviceInfo.toByteArray(), emptyDirectory))
        val stopSyncFailure = RuntimeException("stop sync notification failed")
        val notificationIds = mutableListOf<Int>()
        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { BlePolarDeviceCapabilitiesUtility.isDeviceSensor("360") } returns true
        coEvery { client.request(any()) } answers {
            ByteArrayOutputStream().apply { write(if (requestResponses.isEmpty()) emptyDirectory else requestResponses.removeFirst()) }
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(capture(notificationIds), any()) } answers {
            if (firstArg<Int>() == PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE) {
                throw stopSyncFailure
            }
            Unit
        }
        coEvery { client.write(any(), any()) } returns flowOf(2L)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("9.9.9", "https://example.invalid/fw.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId).toList()

        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.FinalizingFwUpdate("Stopping sync") })
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateFailed })
        Assert.assertEquals(FirmwareUpdateStatus.FwUpdateCompletedSuccessfully("Firmware update to 9.9.9 completed successfully"), statuses.last())
        Assert.assertTrue(notificationIds.toString(), notificationIds.contains(PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE))
        Assert.assertEquals(listOf("https://example.invalid/fw.zip"), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware with direct firmware url keeps success when backup restore write fails after firmware write`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val rootDirectory = PftpResponse.PbPFtpDirectory.newBuilder()
            .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("BACKUP.TXT").setSize(24L))
            .build()
        val emptyDirectory = PftpResponse.PbPFtpDirectory.newBuilder().build().toByteArray()
        coEvery { client.request(any()) } answers {
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            val responseBytes = when (operation.path) {
                "/SYS/" -> rootDirectory.toByteArray()
                "/SYS/BACKUP.TXT" -> "/U/0/S/UDEVSET.BPB\n".toByteArray()
                "/U/0/S/UDEVSET.BPB" -> byteArrayOf(0x0A, 0x0B)
                else -> emptyDirectory
            }
            ByteArrayOutputStream().apply { write(responseBytes) }
        }
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } returns Unit
        val writeHeaders = mutableListOf<ByteArray>()
        coEvery { client.write(capture(writeHeaders), any()) } answers {
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            if (operation.path == "/U/0/S/UDEVSET.BPB") {
                flow { throw RuntimeException("restore write failed") }
            } else {
                flowOf(2L)
            }
        }
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("unused", "https://example.invalid/unused.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId, "https://example.invalid/manual-fw.zip").toList()

        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.FinalizingFwUpdate("Restoring backup on device") })
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateFailed })
        Assert.assertEquals(FirmwareUpdateStatus.FwUpdateCompletedSuccessfully("Firmware update to manual-fw.zip completed successfully"), statuses.last())
        val writePaths = writeHeaders.map { PftpRequest.PbPFtpOperation.parseFrom(it).path }
        Assert.assertTrue(writePaths.toString(), writePaths.contains("/SYSUPDAT.IMG"))
        Assert.assertTrue(writePaths.toString(), writePaths.contains("/U/0/S/UDEVSET.BPB"))
        Assert.assertTrue(firmwareApi.checkRequests.isEmpty())
        Assert.assertEquals(listOf("https://example.invalid/manual-fw.zip"), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware with direct firmware url maps final set time failure to failed status`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId, polarDeviceType = "h10")
        val setTimeFailure = RuntimeException("set local time failed")
        coEvery { client.request(any()) } returns ByteArrayOutputStream()
        coEvery { client.query(any(), any()) } answers {
            if (firstArg<Int>() == PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE) throw setTimeFailure
            ByteArrayOutputStream()
        }
        coEvery { client.sendNotification(any(), any()) } returns Unit
        coEvery { client.write(any(), any()) } returns flowOf(2L)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("unused", "https://example.invalid/unused.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId, "https://example.invalid/manual-fw.zip").toList()

        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.FinalizingFwUpdate("Setting device time") })
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateCompletedSuccessfully })
        val failed = statuses.last() as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(statuses.toString(), failed.details.contains("set local time failed"))
        Assert.assertTrue(firmwareApi.checkRequests.isEmpty())
        Assert.assertEquals(listOf("https://example.invalid/manual-fw.zip"), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware with direct firmware url maps final restart failure to failed status`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId)
        val restartFailure = RuntimeException("restart notification failed")
        var resetNotifications = 0
        coEvery { client.request(any()) } returns ByteArrayOutputStream()
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } answers {
            if (firstArg<Int>() == PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal && ++resetNotifications == 2) {
                throw restartFailure
            }
            Unit
        }
        coEvery { client.write(any(), any()) } returns flowOf(2L)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("unused", "https://example.invalid/unused.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId, "https://example.invalid/manual-fw.zip").toList()

        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.FinalizingFwUpdate("Restarting device") })
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateCompletedSuccessfully })
        val failed = statuses.last() as FirmwareUpdateStatus.FwUpdateFailed
        Assert.assertTrue(statuses.toString(), failed.details.contains("restart notification failed"))
        Assert.assertTrue(firmwareApi.checkRequests.isEmpty())
        Assert.assertEquals(listOf("https://example.invalid/manual-fw.zip"), firmwareApi.packageUrls)
    }

    @Test
    fun `updateFirmware with direct firmware url keeps success when final stop sync fails`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        val (client, _) = mockPsFtpConnection(deviceId, polarDeviceType = "360")
        val stopSyncFailure = RuntimeException("stop sync notification failed")
        val notificationIds = mutableListOf<Int>()
        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { BlePolarDeviceCapabilitiesUtility.isDeviceSensor("360") } returns true
        coEvery { client.request(any()) } returns ByteArrayOutputStream()
        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(capture(notificationIds), any()) } answers {
            if (firstArg<Int>() == PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE) {
                throw stopSyncFailure
            }
            Unit
        }
        coEvery { client.write(any(), any()) } returns flowOf(2L)
        val firmwareApi = CapturingFirmwareUpdateApi(
            checkResponse = Response.success(FirmwareUpdateResponse("unused", "https://example.invalid/unused.zip")),
            packageBytes = firmwareZip("SYSUPDAT.IMG" to byteArrayOf(0x01, 0x02))
        )
        api.firmwareUpdateApiFactory = { firmwareApi }

        val statuses = api.updateFirmware(deviceId, "https://example.invalid/manual-fw.zip").toList()

        Assert.assertTrue(statuses.toString(), statuses.any { it == FirmwareUpdateStatus.FinalizingFwUpdate("Stopping sync") })
        Assert.assertFalse(statuses.toString(), statuses.any { it is FirmwareUpdateStatus.FwUpdateFailed })
        Assert.assertEquals(FirmwareUpdateStatus.FwUpdateCompletedSuccessfully("Firmware update to manual-fw.zip completed successfully"), statuses.last())
        Assert.assertTrue(notificationIds.toString(), notificationIds.contains(PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE))
        Assert.assertTrue(firmwareApi.checkRequests.isEmpty())
        Assert.assertEquals(listOf("https://example.invalid/manual-fw.zip"), firmwareApi.packageUrls)
    }

    @Test
    fun `activity data readiness device type uses shared advertisement local name parsing`() {
        Assert.assertEquals("GritX Pro", BDBleApiImpl.activityCapabilityDeviceType("Polar GritX Pro aa123459"))
        Assert.assertEquals("Custom Strap", BDBleApiImpl.activityCapabilityDeviceType("Custom Strap aa123459"))
    }

    @Test
    fun `setLocalTime sends different UTC and local time values for non-UTC timezone`() = runTest {
        assertDiskTimeRuntimePolicyVectorContains("set-local-time-v2")
        // Arrange
        val deviceId = "E123456F"
        val localDateTime = LocalDateTime.of(2024, 3, 15, 12, 0, 0)

        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, session) = mockPsFtpConnection(deviceId)

        val originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+02:00"))

        val capturedQueryIds = mutableListOf<Int>()
        val capturedQueryParams = mutableListOf<ByteArray?>()
        var plannedCommands: List<String> = emptyList()
        coEvery { client.query(capture(capturedQueryIds), captureNullable(capturedQueryParams)) } returns ByteArrayOutputStream()

        try {
            // Act
            api.setLocalTime(deviceId, localDateTime)
            plannedCommands = planSetLocalTimeV2ForCurrentZone(localDateTime).commands
        } finally {
            TimeZone.setDefault(originalTz)
            unmockkObject(PolarServiceClientUtils)
        }

        // Assert – two queries were sent: SET_SYSTEM_TIME and SET_LOCAL_TIME
        val systemTimeIndex = capturedQueryIds.indexOf(PftpRequest.PbPFtpQuery.SET_SYSTEM_TIME_VALUE)
        val localTimeIndex = capturedQueryIds.indexOf(PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE)
        Assert.assertTrue("SET_SYSTEM_TIME_VALUE query was not sent", systemTimeIndex >= 0)
        Assert.assertTrue("SET_LOCAL_TIME_VALUE query was not sent", localTimeIndex >= 0)

        val systemTimeParams = PftpRequest.PbPFtpSetSystemTimeParams.parseFrom(capturedQueryParams[systemTimeIndex])
        val localTimeParams = PftpRequest.PbPFtpSetLocalTimeParams.parseFrom(capturedQueryParams[localTimeIndex])

        // Local time stays at 12:00, UTC is 10:00 (GMT+2 offset)
        Assert.assertEquals("Local time hour should be preserved", 12, localTimeParams.time.hour)
        Assert.assertEquals("System/UTC time hour should be 2 hours behind local (GMT+2)", 10, systemTimeParams.time.hour)
        Assert.assertNotEquals(
            "Local time and UTC system time must differ for a non-UTC timezone",
            localTimeParams.time.hour,
            systemTimeParams.time.hour
        )
        Assert.assertEquals(
            listOf(
                "query:SET_SYSTEM_TIME",
                "field:systemTimeHour=10",
                "field:systemTimeTrusted=true",
                "query:SET_LOCAL_TIME",
                "field:localTimeHour=12"
            ),
            plannedCommands
        )
        Assert.assertTrue("System time must be marked as trusted", systemTimeParams.trusted)
    }

    @Test
    fun `setLocalTime propagates local time query failure`() = runTest {
        val deviceId = "E123456F"
        val localDateTime = LocalDateTime.of(2024, 3, 15, 12, 0, 0)
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val capturedQueryIds = mutableListOf<Int>()
        val capturedQueryParams = mutableListOf<ByteArray?>()
        val transportError = RuntimeException("set local time failed")
        coEvery { client.query(capture(capturedQueryIds), captureNullable(capturedQueryParams)) } answers {
            if (firstArg<Int>() == PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE) {
                throw transportError
            }
            ByteArrayOutputStream()
        }

        try {
            api.setLocalTime(deviceId, localDateTime)
            Assert.fail("Expected local time query failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertTrue(capturedQueryIds.contains(PftpRequest.PbPFtpQuery.SET_SYSTEM_TIME_VALUE))
        Assert.assertTrue(capturedQueryIds.contains(PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE))
        val localTimeIndex = capturedQueryIds.indexOf(PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE)
        val localTimeParams = PftpRequest.PbPFtpSetLocalTimeParams.parseFrom(capturedQueryParams[localTimeIndex])
        Assert.assertEquals(12, localTimeParams.time.hour)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `getLocalTime decodes fake query response`() = runTest {
        assertDiskTimeRuntimePolicyVectorContains("get-local-time")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val localTimeProto = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
            .setDate(PbDate.newBuilder().setYear(2024).setMonth(6).setDay(15).build())
            .setTime(PbTime.newBuilder().setHour(10).setMinute(30).setSeconds(45).setMillis(123).build())
            .setTzOffset(120)
            .build()
        val response = ByteArrayOutputStream().apply {
            write(localTimeProto.toByteArray())
        }
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } returns response

        val result = api.getLocalTime(deviceId)

        Assert.assertEquals(LocalDateTime.of(2024, 6, 15, 10, 30, 45, 123_000_000), result)
        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE), queryIds)
        Assert.assertEquals(listOf(null), queryParams)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `getLocalTime propagates fake query failure`() = runTest {
        assertDiskTimeRuntimePolicyVectorContains("get-local-time-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        val transportError = RuntimeException("local time query failed")
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } throws transportError

        try {
            api.getLocalTime(deviceId)
            Assert.fail("Expected local time query failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE), queryIds)
        Assert.assertEquals(listOf(null), queryParams)
    }

    @Test
    fun `getLocalTimeWithZone decodes fake query response`() = runTest {
        assertDiskTimeRuntimePolicyVectorContains("get-local-time-with-zone")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val localTimeProto = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
            .setDate(PbDate.newBuilder().setYear(2025).setMonth(1).setDay(1).build())
            .setTime(PbTime.newBuilder().setHour(12).setMinute(0).setSeconds(5).setMillis(250).build())
            .setTzOffset(60)
            .build()
        val response = ByteArrayOutputStream().apply {
            write(localTimeProto.toByteArray())
        }
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } returns response

        val result = api.getLocalTimeWithZone(deviceId)

        Assert.assertEquals(LocalDateTime.of(2025, 1, 1, 12, 0, 5, 250_000_000), result.toLocalDateTime())
        Assert.assertEquals(ZoneOffset.ofHours(1), result.offset)
        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE), queryIds)
        Assert.assertEquals(listOf(null), queryParams)
    }

    @Test
    fun `getLocalTimeWithZone propagates fake query failure`() = runTest {
        assertDiskTimeRuntimePolicyVectorContains("get-local-time-with-zone-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        val transportError = RuntimeException("local time with zone query failed")
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } throws transportError

        try {
            api.getLocalTimeWithZone(deviceId)
            Assert.fail("Expected local time with zone query failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE), queryIds)
        Assert.assertEquals(listOf(null), queryParams)
    }

    @Test
    fun `setDaylightSavingTime sends set local time query with platform current local timestamp`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-daylight-saving-time")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        val currentPlatformTime = LocalDateTime.of(2026, 6, 7, 8, 9, 10, 123_000_000)
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } returns ByteArrayOutputStream()

        mockkStatic(LocalDateTime::class)
        every { LocalDateTime.now() } returns currentPlatformTime
        try {
            api.setDaylightSavingTime(deviceId)
        } finally {
            unmockkStatic(LocalDateTime::class)
        }

        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE), queryIds)
        val params = PftpRequest.PbPFtpSetLocalTimeParams.parseFrom(queryParams.single())
        Assert.assertEquals(2026, params.date.year)
        Assert.assertEquals(6, params.date.month)
        Assert.assertEquals(7, params.date.day)
        Assert.assertEquals(8, params.time.hour)
        Assert.assertEquals(9, params.time.minute)
        Assert.assertEquals(10, params.time.seconds)
        Assert.assertEquals(123, params.time.millis)
    }

    @Test
    fun `getBatteryLevel returns cached battery client level`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO))
        val batteryClient = mockk<BleBattClient>()
        val (_, session) = mockPsFtpConnection(deviceId)
        every { session.fetchClient(BATTERY_SERVICE) } returns batteryClient
        every { batteryClient.getBatteryLevel() } returns 87

        val result = api.getBatteryLevel(deviceId)

        Assert.assertEquals(87, result)
    }

    @Test
    fun `getChargerState returns cached battery client charger state`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO))
        val batteryClient = mockk<BleBattClient>()
        val (_, session) = mockPsFtpConnection(deviceId)
        every { session.fetchClient(BATTERY_SERVICE) } returns batteryClient
        every { batteryClient.getChargerStatus() } returns ChargeState.CHARGING

        val result = api.getChargerState(deviceId)

        Assert.assertEquals(ChargeState.CHARGING, result)
    }

    @Test
    fun `getRSSIValue delegates to service client utils`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.getRSSIValue(deviceId, any()) } returns -57

        val result = api.getRSSIValue(deviceId)

        Assert.assertEquals(-57, result)
    }

    @Test
    fun `startRecording sends request start recording query`() = runTest {
        assertCommandRuntimePolicyVectorContains("h10-start-recording")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockRecordingSupported()
        val (client, _) = mockPsFtpConnection(deviceId, polarDeviceType = "h10")
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } returns ByteArrayOutputStream()

        api.startRecording(
            deviceId,
            "myExercise",
            PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S,
            PolarH10OfflineExerciseApi.SampleType.HR
        )

        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.REQUEST_START_RECORDING_VALUE), queryIds)
        val params = PftpRequest.PbPFtpRequestStartRecordingParams.parseFrom(queryParams.single())
        Assert.assertEquals("myExercise", params.sampleDataIdentifier)
        Assert.assertEquals(PbSampleType.SAMPLE_TYPE_HEART_RATE, params.sampleType)
        Assert.assertEquals(PbDuration.newBuilder().setSeconds(1).build(), params.recordingInterval)
    }

    @Test
    fun `startRecording propagates fake query failure`() = runTest {
        assertCommandRuntimePolicyVectorContains("h10-start-recording-query-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockRecordingSupported()
        val (client, _) = mockPsFtpConnection(deviceId, polarDeviceType = "h10")
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        val transportError = RuntimeException("start recording failed")
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } throws transportError

        try {
            api.startRecording(
                deviceId,
                "myExercise",
                PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S,
                PolarH10OfflineExerciseApi.SampleType.HR
            )
            Assert.fail("Expected start recording query failure to propagate")
        } catch (error: Exception) {
            Assert.assertSame(transportError, error.cause)
        }
        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.REQUEST_START_RECORDING_VALUE), queryIds)
    }

    @Test
    fun `stopRecording sends request stop recording query`() = runTest {
        assertCommandRuntimePolicyVectorContains("h10-stop-recording")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockRecordingSupported()
        val (client, _) = mockPsFtpConnection(deviceId, polarDeviceType = "h10")
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } returns ByteArrayOutputStream()

        api.stopRecording(deviceId)

        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.REQUEST_STOP_RECORDING_VALUE), queryIds)
        Assert.assertEquals(listOf(null), queryParams)
    }

    @Test
    fun `stopRecording propagates fake query failure`() = runTest {
        assertCommandRuntimePolicyVectorContains("h10-stop-recording-query-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockRecordingSupported()
        val (client, _) = mockPsFtpConnection(deviceId, polarDeviceType = "h10")
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        val transportError = RuntimeException("stop recording failed")
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } throws transportError

        try {
            api.stopRecording(deviceId)
            Assert.fail("Expected stop recording query failure to propagate")
        } catch (error: Exception) {
            Assert.assertSame(transportError, error.cause)
        }
        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.REQUEST_STOP_RECORDING_VALUE), queryIds)
        Assert.assertEquals(listOf(null), queryParams)
    }

    @Test
    fun `requestRecordingStatus decodes fake query response`() = runTest {
        assertCommandRuntimePolicyVectorContains("h10-recording-status")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockRecordingSupported()
        val (client, _) = mockPsFtpConnection(deviceId, polarDeviceType = "h10")
        val statusProto = PftpResponse.PbRequestRecordingStatusResult.newBuilder()
            .setRecordingOn(true)
            .setSampleDataIdentifier("exercise123")
            .build()
        val response = ByteArrayOutputStream().apply {
            write(statusProto.toByteArray())
        }
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } returns response

        val result = api.requestRecordingStatus(deviceId)

        Assert.assertTrue(result.first)
        Assert.assertEquals("exercise123", result.second)
        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.REQUEST_RECORDING_STATUS_VALUE), queryIds)
        Assert.assertEquals(listOf(null), queryParams)
    }

    @Test
    fun `requestRecordingStatus propagates fake query failure`() = runTest {
        assertCommandRuntimePolicyVectorContains("h10-recording-status-query-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockRecordingSupported()
        val (client, _) = mockPsFtpConnection(deviceId, polarDeviceType = "h10")
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        val transportError = RuntimeException("recording status failed")
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } throws transportError

        try {
            api.requestRecordingStatus(deviceId)
            Assert.fail("Expected recording status query failure to propagate")
        } catch (error: Exception) {
            Assert.assertSame(transportError, error.cause)
        }
        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.REQUEST_RECORDING_STATUS_VALUE), queryIds)
        Assert.assertEquals(listOf(null), queryParams)
    }

    @Test
    fun `setTelemetryEnabled reads current settings and writes telemetry update`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-telemetry-enabled")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val currentSettings = PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(
                PbUserDeviceGeneralSettings.newBuilder()
                    .setDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT)
                    .build()
            )
            .setLastModified(testTimestamp())
            .build()
        val requestResponse = ByteArrayOutputStream().apply {
            write(currentSettings.toByteArray())
        }
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        coEvery { client.request(any(), any()) } returns requestResponse
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        api.setTelemetryEnabled(deviceId, true)

        Assert.assertEquals(1, writeHeaders.size)
        Assert.assertEquals(1, writePayloads.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", writeOperation.path)
        val writtenSettings = PbUserDeviceSettings.parseFrom(writePayloads.single()!!.readBytes())
        Assert.assertTrue(writtenSettings.hasTelemetrySettings())
        Assert.assertTrue(writtenSettings.telemetrySettings.hasTelemetryEnabled())
        Assert.assertTrue(writtenSettings.telemetrySettings.telemetryEnabled)
        Assert.assertEquals(PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT, writtenSettings.generalSettings.deviceLocation)
    }

    @Test
    fun `setTelemetryEnabled propagates current settings read failure without write`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-telemetry-read-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("current settings read failed")

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        coEvery { client.request(any(), any()) } throws transportError
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        try {
            api.setTelemetryEnabled(deviceId, true)
            Assert.fail("Expected current settings read failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertTrue(writeHeaders.isEmpty())
        Assert.assertTrue(writePayloads.isEmpty())
    }

    @Test
    fun `setTelemetryEnabled propagates settings write failure after payload is prepared`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-telemetry-write-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val currentSettings = PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(
                PbUserDeviceGeneralSettings.newBuilder()
                    .setDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT)
                    .build()
            )
            .setLastModified(testTimestamp())
            .build()
        val requestResponse = ByteArrayOutputStream().apply {
            write(currentSettings.toByteArray())
        }
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("settings write failed")

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        coEvery { client.request(any(), any()) } returns requestResponse
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flow { throw transportError }

        try {
            api.setTelemetryEnabled(deviceId, true)
            Assert.fail("Expected settings write failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(1, writeHeaders.size)
        Assert.assertEquals(1, writePayloads.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", writeOperation.path)
        val writtenSettings = PbUserDeviceSettings.parseFrom(writePayloads.single()!!.readBytes())
        Assert.assertTrue(writtenSettings.telemetrySettings.telemetryEnabled)
    }

    @Test
    fun `setUserDeviceLocation reads current settings and writes location update`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-user-device-location")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val currentSettings = PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(
                PbUserDeviceGeneralSettings.newBuilder()
                    .setDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT)
                    .build()
            )
            .setTelemetrySettings(
                PbUserDeviceTelemetrySettings.newBuilder()
                    .setTelemetryEnabled(true)
                    .build()
            )
            .setLastModified(testTimestamp())
            .build()
        val requestResponse = ByteArrayOutputStream().apply {
            write(currentSettings.toByteArray())
        }
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        coEvery { client.request(any(), any()) } returns requestResponse
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        api.setUserDeviceLocation(deviceId, PbDeviceLocation.DEVICE_LOCATION_WRIST_RIGHT.number)

        Assert.assertEquals(1, writeHeaders.size)
        Assert.assertEquals(1, writePayloads.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", writeOperation.path)
        val writtenSettings = PbUserDeviceSettings.parseFrom(writePayloads.single()!!.readBytes())
        Assert.assertEquals(sharedDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_RIGHT.number), writtenSettings.generalSettings.deviceLocation)
        Assert.assertTrue(writtenSettings.hasTelemetrySettings())
        Assert.assertTrue(writtenSettings.telemetrySettings.hasTelemetryEnabled())
        Assert.assertTrue(writtenSettings.telemetrySettings.telemetryEnabled)
    }

    @Test
    fun `setUserDeviceLocation propagates write failure after payload is prepared`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-user-device-location-write-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val currentSettings = PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(
                PbUserDeviceGeneralSettings.newBuilder()
                    .setDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_RIGHT)
                    .build()
            )
            .setTelemetrySettings(
                PbUserDeviceTelemetrySettings.newBuilder()
                    .setTelemetryEnabled(true)
                    .build()
            )
            .setLastModified(testTimestamp())
            .build()
        val requestResponse = ByteArrayOutputStream().apply {
            write(currentSettings.toByteArray())
        }
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("location settings write failed")

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        coEvery { client.request(any(), any()) } returns requestResponse
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flow { throw transportError }

        try {
            api.setUserDeviceLocation(deviceId, PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT.number)
            Assert.fail("Expected location settings write failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", writeOperation.path)
        val writtenSettings = PbUserDeviceSettings.parseFrom(writePayloads.single()!!.readBytes())
        Assert.assertEquals(sharedDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT.number), writtenSettings.generalSettings.deviceLocation)
        Assert.assertTrue(writtenSettings.telemetrySettings.telemetryEnabled)
    }

    @Test
    fun `setUserDeviceLocation propagates current settings read failure without write`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-user-device-location-read-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("location settings read failed")

        mockPolarFileSystemV2()
        coEvery { client.request(any(), any()) } throws transportError
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        try {
            api.setUserDeviceLocation(deviceId, PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT.number)
            Assert.fail("Expected location settings read failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertTrue(writeHeaders.isEmpty())
        Assert.assertTrue(writePayloads.isEmpty())
    }

    @Test
    fun `setUsbConnectionMode reads current settings and writes usb mode update`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-usb-connection-mode")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val currentSettings = PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(
                PbUserDeviceGeneralSettings.newBuilder()
                    .setDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT)
                    .build()
            )
            .setTelemetrySettings(
                PbUserDeviceTelemetrySettings.newBuilder()
                    .setTelemetryEnabled(true)
                    .build()
            )
            .setLastModified(testTimestamp())
            .build()
        val requestResponse = ByteArrayOutputStream().apply {
            write(currentSettings.toByteArray())
        }
        val requestHeaders = mutableListOf<ByteArray>()
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()

        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } returns requestResponse
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        api.setUsbConnectionMode(deviceId, true)

        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", requestOperation.path)
        Assert.assertEquals(1, writeHeaders.size)
        Assert.assertEquals(1, writePayloads.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", writeOperation.path)
        val writtenSettings = PbUserDeviceSettings.parseFrom(writePayloads.single()!!.readBytes())
        Assert.assertTrue(writtenSettings.hasUsbConnectionSettings())
        Assert.assertEquals(sharedUsbConnectionMode(true), writtenSettings.usbConnectionSettings.mode)
        Assert.assertEquals(PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT, writtenSettings.generalSettings.deviceLocation)
        Assert.assertTrue(writtenSettings.telemetrySettings.telemetryEnabled)
    }

    @Test
    fun `setUsbConnectionMode propagates write failure after payload is prepared`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-usb-connection-mode-write-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val currentSettings = PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(
                PbUserDeviceGeneralSettings.newBuilder()
                    .setDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT)
                    .build()
            )
            .setLastModified(testTimestamp())
            .build()
        val requestResponse = ByteArrayOutputStream().apply {
            write(currentSettings.toByteArray())
        }
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("USB settings write failed")

        mockPolarFileSystemV2()
        coEvery { client.request(any(), any()) } returns requestResponse
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flow { throw transportError }

        try {
            api.setUsbConnectionMode(deviceId, false)
            Assert.fail("Expected USB settings write failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(1, writeHeaders.size)
        Assert.assertEquals(1, writePayloads.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", writeOperation.path)
        val writtenSettings = PbUserDeviceSettings.parseFrom(writePayloads.single()!!.readBytes())
        Assert.assertEquals(sharedUsbConnectionMode(false), writtenSettings.usbConnectionSettings.mode)
    }

    @Test
    fun `setUsbConnectionMode propagates current settings read failure without write`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-usb-connection-mode-read-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("USB settings read failed")

        mockPolarFileSystemV2()
        coEvery { client.request(any(), any()) } throws transportError
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        try {
            api.setUsbConnectionMode(deviceId, false)
            Assert.fail("Expected USB settings read failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertTrue(writeHeaders.isEmpty())
        Assert.assertTrue(writePayloads.isEmpty())
    }

    @Test
    fun `setAutomaticTrainingDetectionSettings reads current settings and writes automatic measurement update`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-automatic-training-detection")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val currentSettings = PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(
                PbUserDeviceGeneralSettings.newBuilder()
                    .setDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT)
                    .build()
            )
            .setTelemetrySettings(
                PbUserDeviceTelemetrySettings.newBuilder()
                    .setTelemetryEnabled(true)
                    .build()
            )
            .setLastModified(testTimestamp())
            .build()
        val requestResponse = ByteArrayOutputStream().apply {
            write(currentSettings.toByteArray())
        }
        val requestHeaders = mutableListOf<ByteArray>()
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()

        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } returns requestResponse
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        api.setAutomaticTrainingDetectionSettings(
            identifier = deviceId,
            automaticTrainingDetectionMode = true,
            automaticTrainingDetectionSensitivity = 77,
            minimumTrainingDurationSeconds = 300
        )

        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", requestOperation.path)
        Assert.assertEquals(1, writeHeaders.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", writeOperation.path)
        val writtenSettings = PbUserDeviceSettings.parseFrom(writePayloads.single()!!.readBytes())
        Assert.assertTrue(writtenSettings.hasAutomaticMeasurementSettings())
        val atdSettings = writtenSettings.automaticMeasurementSettings.automaticTrainingDetectionSettings
        Assert.assertEquals(sharedAutomaticTrainingDetectionState(true), atdSettings.state)
        Assert.assertEquals(77, atdSettings.sensitivity)
        Assert.assertEquals(300, atdSettings.minimumTrainingDurationSeconds)
        Assert.assertTrue(writtenSettings.telemetrySettings.telemetryEnabled)
    }

    @Test
    fun `setAutomaticTrainingDetectionSettings propagates write failure after payload is prepared`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-automatic-training-detection-write-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val currentSettings = PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(
                PbUserDeviceGeneralSettings.newBuilder()
                    .setDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT)
                    .build()
            )
            .setLastModified(testTimestamp())
            .build()
        val requestResponse = ByteArrayOutputStream().apply {
            write(currentSettings.toByteArray())
        }
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("automatic training detection write failed")

        mockPolarFileSystemV2()
        coEvery { client.request(any(), any()) } returns requestResponse
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flow { throw transportError }

        try {
            api.setAutomaticTrainingDetectionSettings(deviceId, false, 11, 120)
            Assert.fail("Expected automatic training detection write failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(1, writeHeaders.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", writeOperation.path)
        val writtenSettings = PbUserDeviceSettings.parseFrom(writePayloads.single()!!.readBytes())
        val atdSettings = writtenSettings.automaticMeasurementSettings.automaticTrainingDetectionSettings
        Assert.assertEquals(sharedAutomaticTrainingDetectionState(false), atdSettings.state)
        Assert.assertEquals(11, atdSettings.sensitivity)
        Assert.assertEquals(120, atdSettings.minimumTrainingDurationSeconds)
    }

    @Test
    fun `setAutomaticTrainingDetectionSettings propagates current settings read failure without write`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-automatic-training-detection-read-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("automatic training detection read failed")

        mockPolarFileSystemV2()
        coEvery { client.request(any(), any()) } throws transportError
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        try {
            api.setAutomaticTrainingDetectionSettings(deviceId, false, 11, 120)
            Assert.fail("Expected automatic training detection read failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertTrue(writeHeaders.isEmpty())
        Assert.assertTrue(writePayloads.isEmpty())
    }

    @Test
    fun `setAutomaticOHRMeasurementEnabled reads current settings and writes always on state`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-automatic-ohr-measurement")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val currentAtdSettings = PbAutomaticTrainingDetectionSettings.newBuilder()
            .setState(PbAutomaticTrainingDetectionSettings.PbAutomaticTrainingDetectionState.ON)
            .setSensitivity(44)
            .setMinimumTrainingDurationSeconds(180)
            .build()
        val currentSettings = PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(
                PbUserDeviceGeneralSettings.newBuilder()
                    .setDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT)
                    .build()
            )
            .setAutomaticMeasurementSettings(
                PbUserAutomaticMeasurementSettings.newBuilder()
                    .setAutomaticTrainingDetectionSettings(currentAtdSettings)
                    .build()
            )
            .setLastModified(testTimestamp())
            .build()
        val requestResponse = ByteArrayOutputStream().apply {
            write(currentSettings.toByteArray())
        }
        val requestHeaders = mutableListOf<ByteArray>()
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()

        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } returns requestResponse
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        api.setAutomaticOHRMeasurementEnabled(deviceId, enabled = true)

        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", requestOperation.path)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", writeOperation.path)
        val writtenSettings = PbUserDeviceSettings.parseFrom(writePayloads.single()!!.readBytes())
        val automaticMeasurementSettings = writtenSettings.automaticMeasurementSettings
        Assert.assertEquals(PbAutomaticMeasurementSettings.PbAutomaticMeasurementState.ALWAYS_ON, automaticMeasurementSettings.automaticOhrMeasurement.state)
        Assert.assertEquals(currentAtdSettings, automaticMeasurementSettings.automaticTrainingDetectionSettings)
    }

    @Test
    fun `setAutomaticOHRMeasurementEnabled propagates write failure after off payload is prepared`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-automatic-ohr-measurement-write-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val currentSettings = PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(
                PbUserDeviceGeneralSettings.newBuilder()
                    .setDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT)
                    .build()
            )
            .setLastModified(testTimestamp())
            .build()
        val requestResponse = ByteArrayOutputStream().apply {
            write(currentSettings.toByteArray())
        }
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("automatic OHR write failed")

        mockPolarFileSystemV2()
        coEvery { client.request(any(), any()) } returns requestResponse
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flow { throw transportError }

        try {
            api.setAutomaticOHRMeasurementEnabled(deviceId, enabled = false)
            Assert.fail("Expected automatic OHR write failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", writeOperation.path)
        val writtenSettings = PbUserDeviceSettings.parseFrom(writePayloads.single()!!.readBytes())
        Assert.assertEquals(PbAutomaticMeasurementSettings.PbAutomaticMeasurementState.OFF, writtenSettings.automaticMeasurementSettings.automaticOhrMeasurement.state)
    }

    @Test
    fun `setAutomaticOHRMeasurementEnabled propagates current settings read failure without write`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-automatic-ohr-measurement-read-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("automatic OHR read failed")

        mockPolarFileSystemV2()
        coEvery { client.request(any(), any()) } throws transportError
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        try {
            api.setAutomaticOHRMeasurementEnabled(deviceId, enabled = false)
            Assert.fail("Expected automatic OHR read failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertTrue(writeHeaders.isEmpty())
        Assert.assertTrue(writePayloads.isEmpty())
    }

    @Test
    fun `getUserDeviceSettings reads current settings from fake transport`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("get-user-device-settings")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val currentSettings = PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(
                PbUserDeviceGeneralSettings.newBuilder()
                    .setDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_RIGHT)
                    .build()
            )
            .setTelemetrySettings(
                PbUserDeviceTelemetrySettings.newBuilder()
                    .setTelemetryEnabled(true)
                    .build()
            )
            .setLastModified(testTimestamp())
            .build()
        val requestResponse = ByteArrayOutputStream().apply {
            write(currentSettings.toByteArray())
        }
        val requestHeaders = mutableListOf<ByteArray>()

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        coEvery { client.request(capture(requestHeaders), any()) } returns requestResponse

        val result: PolarUserDeviceSettings = api.getUserDeviceSettings(deviceId)

        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", requestOperation.path)
        Assert.assertEquals(PbDeviceLocation.DEVICE_LOCATION_WRIST_RIGHT.number, result.deviceLocation)
        Assert.assertEquals(true, result.telemetryEnabled)
    }

    @Test
    fun `getUserDeviceSettings propagates fake transport read failure`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("get-user-device-settings-read-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val transportError = RuntimeException("user settings read failed")

        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
        coEvery { client.request(capture(requestHeaders), any()) } throws transportError

        try {
            api.getUserDeviceSettings(deviceId)
            Assert.fail("Expected user settings read failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", requestOperation.path)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `setUserDeviceSettings writes whole settings payload through shared path planning`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-user-device-settings")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val settings = PolarUserDeviceSettings(
            deviceLocation = PbDeviceLocation.DEVICE_LOCATION_WRIST_RIGHT.number,
            usbConnectionMode = true,
            telemetryEnabled = true,
            automaticTrainingDetectionMode = false,
            automaticTrainingDetectionSensitivity = 22,
            minimumTrainingDurationSeconds = 300,
            autosFilesEnabled = false
        )

        mockPolarFileSystemV2()
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        api.setUserDeviceSettings(deviceId, settings)

        Assert.assertEquals(1, writeHeaders.size)
        Assert.assertEquals(1, writePayloads.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", writeOperation.path)
        val writtenSettings = PbUserDeviceSettings.parseFrom(writePayloads.single()!!.readBytes())
        Assert.assertEquals(sharedDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_RIGHT.number), writtenSettings.generalSettings.deviceLocation)
        Assert.assertEquals(sharedUsbConnectionMode(true), writtenSettings.usbConnectionSettings.mode)
        val atdSettings = writtenSettings.automaticMeasurementSettings.automaticTrainingDetectionSettings
        Assert.assertEquals(sharedAutomaticTrainingDetectionState(false), atdSettings.state)
        Assert.assertEquals(22, atdSettings.sensitivity)
        Assert.assertEquals(300, atdSettings.minimumTrainingDurationSeconds)
        Assert.assertEquals(PbAutomaticMeasurementSettings.PbAutomaticMeasurementState.OFF, writtenSettings.automaticMeasurementSettings.automaticOhrMeasurement.state)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `setUserDeviceSettings propagates write failure after whole settings payload is prepared`() = runTest {
        assertUserDeviceSettingsRuntimePolicyVectorContains("set-user-device-settings-write-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("whole settings write failed")
        val settings = PolarUserDeviceSettings(
            deviceLocation = PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT.number,
            telemetryEnabled = false
        )

        mockPolarFileSystemV2()
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flow { throw transportError }

        try {
            api.setUserDeviceSettings(deviceId, settings)
            Assert.fail("Expected whole settings write failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(1, writeHeaders.size)
        Assert.assertEquals(1, writePayloads.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", writeOperation.path)
        val writtenSettings = PbUserDeviceSettings.parseFrom(writePayloads.single()!!.readBytes())
        Assert.assertEquals(sharedDeviceLocation(PbDeviceLocation.DEVICE_LOCATION_WRIST_LEFT.number), writtenSettings.generalSettings.deviceLocation)
        Assert.assertFalse(writtenSettings.telemetrySettings.telemetryEnabled)
    }

    @Test
    fun `getDiskSpace decodes fake query response`() = runTest {
        assertDiskTimeRuntimePolicyVectorContains("get-disk-space")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val diskSpaceProto = PftpResponse.PbPFtpDiskSpaceResult.newBuilder()
            .setFragmentSize(512)
            .setTotalFragments(200)
            .setFreeFragments(100)
            .build()
        val response = ByteArrayOutputStream().apply {
            write(diskSpaceProto.toByteArray())
        }
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } returns response

        val result = api.getDiskSpace(deviceId)

        Assert.assertEquals(512L * 200L, result.totalSpace)
        Assert.assertEquals(512L * 100L, result.freeSpace)
        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.GET_DISK_SPACE_VALUE), queryIds)
        Assert.assertEquals(listOf(null), queryParams)
    }

    @Test
    fun `getDiskSpace propagates fake query failure`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        val transportError = RuntimeException("disk space query failed")
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } throws transportError

        try {
            api.getDiskSpace(deviceId)
            Assert.fail("Expected disk space query failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.GET_DISK_SPACE_VALUE), queryIds)
        Assert.assertEquals(listOf(null), queryParams)
    }

    @Test
    fun `setLedConfig writes led config payload`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        api.setLedConfig(deviceId, LedConfig(sdkModeLedEnabled = true, ppiModeLedEnabled = false))

        Assert.assertEquals(1, writeHeaders.size)
        Assert.assertEquals(1, writePayloads.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals(LedConfig.LED_CONFIG_FILENAME, writeOperation.path)
        Assert.assertArrayEquals(
            byteArrayOf(LedConfig.LED_ANIMATION_ENABLE_BYTE, LedConfig.LED_ANIMATION_DISABLE_BYTE),
            writePayloads.single()!!.readBytes()
        )
    }

    @Test
    fun `setLedConfig headers use shared file facade planning`() {
        val writeOperation = BDBleApiImpl.ledConfigWriteOperation()

        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.first)
        Assert.assertEquals(LedConfig.LED_CONFIG_FILENAME, writeOperation.second)
        Assert.assertArrayEquals(
            byteArrayOf(LedConfig.LED_ANIMATION_ENABLE_BYTE, LedConfig.LED_ANIMATION_DISABLE_BYTE),
            PolarRuntimePlannerAdapter.ledConfigPayloadBytes(sdkModeLedEnabled = true, ppiModeLedEnabled = false)
        )
    }

    @Test
    fun `offline recording headers use shared file facade planning`() {
        val fileOperation = BDBleApiImpl.offlineRecordingFileReadOperation("/U/0/20240615/R/103000/ACC.REC")
        val directoryOperation = BDBleApiImpl.offlineRecordingDirectoryReadOperation("/U/0/20240615/R/103000/")
        val removeOperation = BDBleApiImpl.offlineRecordingRemoveOperation("/U/0/20240615/R/103000/ACC.REC")

        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, fileOperation.first)
        Assert.assertEquals("/U/0/20240615/R/103000/ACC.REC", fileOperation.second)
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, directoryOperation.first)
        Assert.assertEquals("/U/0/20240615/R/103000/", directoryOperation.second)
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.REMOVE, removeOperation.first)
        Assert.assertEquals("/U/0/20240615/R/103000/ACC.REC", removeOperation.second)
    }

    @Test
    fun `stored data date folder removal header uses shared file facade planning`() {
        val removeOperation = BDBleApiImpl.storedDataDateFolderRemoveOperation("/U/0/20260530")

        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.REMOVE, removeOperation.first)
        Assert.assertEquals("/U/0/20260530", removeOperation.second)
    }

    @Test
    fun `setLedConfig propagates write failure after payload is prepared`() = runTest {
        assertFileFacadeRuntimePolicyVectorContains("write-low-level-file-stream-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("led config write failed")
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flow { throw transportError }

        try {
            api.setLedConfig(deviceId, LedConfig(sdkModeLedEnabled = false, ppiModeLedEnabled = true))
            Assert.fail("Expected LED config write failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(1, writeHeaders.size)
        Assert.assertEquals(1, writePayloads.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals(LedConfig.LED_CONFIG_FILENAME, writeOperation.path)
        Assert.assertArrayEquals(
            byteArrayOf(LedConfig.LED_ANIMATION_DISABLE_BYTE, LedConfig.LED_ANIMATION_ENABLE_BYTE),
            writePayloads.single()!!.readBytes()
        )
    }

    @Test
    fun `getLogConfig reads sd log config from fake transport`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val response = ByteArrayOutputStream().apply {
            write(
                PbSensorDataLog.newBuilder()
                    .setOhrLogEnabled(true)
                    .setPpiLogEnabled(false)
                    .setMagnetometerLogFrequency(PbSensorDataLog.PbMagnetometerLogFrequency.MAG_LOG_10HZ)
                    .build()
                    .toByteArray()
            )
        }
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } returns response

        val result = api.getLogConfig(deviceId)

        Assert.assertTrue(result.ohrLogEnabled == true)
        Assert.assertTrue(result.ppiLogEnabled == false)
        Assert.assertEquals(PbSensorDataLog.PbMagnetometerLogFrequency.MAG_LOG_10HZ, result.magnetometerFrequency)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals(LogConfig.LOG_CONFIG_FILENAME, requestOperation.path)
    }

    @Test
    fun `setLogConfig writes sd log config payload`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        api.setLogConfig(
            deviceId,
            LogConfig(
                ohrLogEnabled = true,
                ppiLogEnabled = false,
                magnetometerFrequency = PbSensorDataLog.PbMagnetometerLogFrequency.MAG_LOG_10HZ
            )
        )

        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals(LogConfig.LOG_CONFIG_FILENAME, writeOperation.path)
        val writtenConfig = PbSensorDataLog.parseFrom(writePayloads.single()!!.readBytes())
        Assert.assertTrue(writtenConfig.ohrLogEnabled)
        Assert.assertFalse(writtenConfig.ppiLogEnabled)
        Assert.assertEquals(PbSensorDataLog.PbMagnetometerLogFrequency.MAG_LOG_10HZ, writtenConfig.magnetometerLogFrequency)
    }

    @Test
    fun `LogConfig maps known SD log enum values through shared KMP`() {
        val trigger = PbSensorDataLog.PbLogTrigger.valueOf(PolarSdLogTriggerName.fromValue(2)!!.name)
        val magnetometerFrequency = PbSensorDataLog.PbMagnetometerLogFrequency.valueOf(PolarSdLogMagnetometerFrequencyName.fromValue(3)!!.name)
        val proto = PbSensorDataLog.newBuilder()
            .setLogTrigger(trigger)
            .setMagnetometerLogFrequency(magnetometerFrequency)
            .build()

        val config = LogConfig.fromBytes(proto.toByteArray())

        Assert.assertEquals(PbSensorDataLog.PbLogTrigger.LOG_TRIGGER_EXERCISE, config.logTriggerSettings)
        Assert.assertEquals(PbSensorDataLog.PbMagnetometerLogFrequency.MAG_LOG_100HZ, config.magnetometerFrequency)
        val encoded = config.toProto()
        Assert.assertEquals(PbSensorDataLog.PbLogTrigger.LOG_TRIGGER_EXERCISE, encoded.logTrigger)
        Assert.assertEquals(PbSensorDataLog.PbMagnetometerLogFrequency.MAG_LOG_100HZ, encoded.magnetometerLogFrequency)
    }

    @Test
    fun `LogConfig file headers use shared file facade planning`() {
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to LogConfig.LOG_CONFIG_FILENAME,
            BDBleApiImpl.sdLogConfigReadOperation()
        )
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.PUT to LogConfig.LOG_CONFIG_FILENAME,
            BDBleApiImpl.sdLogConfigWriteOperation()
        )
    }

    @Test
    fun `setLogConfig propagates write failure after payload is prepared`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("sd log write failed")
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flow { throw transportError }

        try {
            api.setLogConfig(deviceId, LogConfig(ohrLogEnabled = false))
            Assert.fail("Expected SD log write failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals(LogConfig.LOG_CONFIG_FILENAME, writeOperation.path)
        val writtenConfig = PbSensorDataLog.parseFrom(writePayloads.single()!!.readBytes())
        Assert.assertFalse(writtenConfig.ohrLogEnabled)
    }

    @Test
    fun `getUserPhysicalConfiguration reads physical configuration from fake transport`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val config = PolarFirstTimeUseConfig(
            gender = Gender.MALE,
            birthDate = LocalDate.of(1990, 1, 2),
            height = 180.5f,
            weight = 75.5f,
            maxHeartRate = 190,
            vo2Max = 50,
            restingHeartRate = 55,
            trainingBackground = 40,
            deviceTime = "2026-05-31T12:00:00Z",
            typicalDay = TypicalDay.MOSTLY_STANDING,
            sleepGoalMinutes = 480
        )
        val response = ByteArrayOutputStream().apply { write(config.toProto().toByteArray()) }
        coEvery { client.request(capture(requestHeaders)) } returns response

        val result = api.getUserPhysicalConfiguration(deviceId)

        Assert.assertNotNull(result)
        Assert.assertEquals(Gender.MALE, result!!.gender)
        Assert.assertEquals(LocalDate.of(1990, 1, 2), result.birthDate)
        Assert.assertEquals(180.5f, result.height)
        Assert.assertEquals(75.5f, result.weight)
        Assert.assertEquals(190, result.maxHeartRate)
        Assert.assertEquals(50, result.vo2Max)
        Assert.assertEquals(55, result.restingHeartRate)
        Assert.assertEquals(40, result.trainingBackground)
        Assert.assertEquals(TypicalDay.MOSTLY_STANDING, result.typicalDay)
        Assert.assertEquals(480, result.sleepGoalMinutes)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals(PolarFirstTimeUseConfig.FTU_CONFIG_FILENAME, requestOperation.path)
    }

    @Test
    fun `getUserPhysicalConfiguration returns null when physical data file is missing`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        coEvery { client.request(capture(requestHeaders)) } throws BlePsFtpUtils.PftpResponseError("missing physical data", PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number)

        val result = api.getUserPhysicalConfiguration(deviceId)

        Assert.assertNull(result)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals(PolarFirstTimeUseConfig.FTU_CONFIG_FILENAME, requestOperation.path)
    }

    @Test
    fun `doFirstTimeUse writes physical config and user id between sync notifications`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        val writeHeaders = mutableListOf<ByteArray>()
        val writeData = mutableListOf<ByteArray>()
        val config = PolarFirstTimeUseConfig(
            gender = Gender.FEMALE,
            birthDate = LocalDate.of(1992, 3, 4),
            height = 171.5f,
            weight = 66.5f,
            maxHeartRate = 188,
            vo2Max = 47,
            restingHeartRate = 53,
            trainingBackground = 30,
            deviceTime = "2026-05-31T12:34:56Z",
            typicalDay = TypicalDay.MOSTLY_SITTING,
            sleepGoalMinutes = 450
        )
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } returns Unit
        every { client.write(capture(writeHeaders), any()) } answers {
            val bytes = ByteArrayOutputStream()
            secondArg<ByteArrayInputStream>().copyTo(bytes)
            writeData.add(bytes.toByteArray())
            flowOf(0L)
        }

        api.doFirstTimeUse(deviceId, config)

        Assert.assertEquals(
            listOf(
                PftpRequest.PbPFtpQuery.REQUEST_SYNCHRONIZATION_VALUE,
                PftpRequest.PbPFtpQuery.SET_SYSTEM_TIME_VALUE,
                PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE
            ),
            queryIds
        )
        Assert.assertEquals(
            listOf(
                PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE,
                PftpNotification.PbPFtpHostToDevNotification.START_SYNC_VALUE,
                PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE,
                PftpNotification.PbPFtpHostToDevNotification.TERMINATE_SESSION_VALUE
            ),
            notificationIds
        )
        Assert.assertEquals(2, writeHeaders.size)
        val writeOperations = writeHeaders.map { PftpRequest.PbPFtpOperation.parseFrom(it) }
        Assert.assertEquals(listOf(PolarFirstTimeUseConfig.FTU_CONFIG_FILENAME, UserIdentifierType.USER_IDENTIFIER_FILENAME), writeOperations.map { it.path })
        Assert.assertTrue(writeOperations.all { it.command == PftpRequest.PbPFtpOperation.Command.PUT })
        Assert.assertArrayEquals(config.toProto().toByteArray(), writeData[0])
        Assert.assertTrue(PbUserIdentifier.parseFrom(writeData[1]).hasMasterIdentifier())
        val stopSyncParams = PftpNotification.PbPFtpStopSyncParams.parseFrom(notificationParams[2])
        Assert.assertTrue(stopSyncParams.completed)
    }

    @Test
    fun `first time use file headers use shared file facade planning`() {
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to PolarFirstTimeUseConfig.FTU_CONFIG_FILENAME,
            BDBleApiImpl.firstTimeUsePhysicalConfigReadOperation()
        )
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.PUT to PolarFirstTimeUseConfig.FTU_CONFIG_FILENAME,
            BDBleApiImpl.firstTimeUsePhysicalConfigWriteOperation()
        )
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to UserIdentifierType.USER_IDENTIFIER_FILENAME,
            BDBleApiImpl.firstTimeUseUserIdReadOperation()
        )
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.PUT to UserIdentifierType.USER_IDENTIFIER_FILENAME,
            BDBleApiImpl.firstTimeUseUserIdWriteOperation()
        )
    }

    @Test
    fun `H10 exercise file headers use shared file facade planning`() {
        val path = "/EXERCISE/E0000001.BPB"

        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to path,
            BDBleApiImpl.h10ExerciseFetchOperation(path)
        )
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.REMOVE to path,
            BDBleApiImpl.h10ExerciseRemoveOperation(path)
        )
    }

    @Test
    fun `offline recording PMDFILES header uses shared file facade planning`() {
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/PMDFILES.TXT",
            BDBleApiImpl.offlineRecordingPmdFilesReadOperation()
        )
    }

    @Test
    fun `doFirstTimeUse terminates sync and propagates physical config write failure`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        val writeHeaders = mutableListOf<ByteArray>()
        val writeData = mutableListOf<ByteArray>()
        val transportError = RuntimeException("FTU physical config write failed")
        val config = PolarFirstTimeUseConfig(
            gender = Gender.MALE,
            birthDate = LocalDate.of(1991, 2, 3),
            height = 181.0f,
            weight = 76.0f,
            maxHeartRate = 190,
            vo2Max = 48,
            restingHeartRate = 54,
            trainingBackground = 40,
            deviceTime = "2026-05-31T12:34:56Z",
            typicalDay = TypicalDay.MOSTLY_STANDING,
            sleepGoalMinutes = 480
        )
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } returns Unit
        every { client.write(capture(writeHeaders), any()) } answers {
            val bytes = ByteArrayOutputStream()
            secondArg<ByteArrayInputStream>().copyTo(bytes)
            writeData.add(bytes.toByteArray())
            flow { throw transportError }
        }

        try {
            api.doFirstTimeUse(deviceId, config)
            Assert.fail("Expected FTU write failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }

        Assert.assertEquals(
            listOf(
                PftpRequest.PbPFtpQuery.REQUEST_SYNCHRONIZATION_VALUE,
                PftpRequest.PbPFtpQuery.SET_SYSTEM_TIME_VALUE,
                PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE
            ),
            queryIds
        )
        Assert.assertEquals(
            listOf(
                PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE,
                PftpNotification.PbPFtpHostToDevNotification.START_SYNC_VALUE,
                PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE,
                PftpNotification.PbPFtpHostToDevNotification.TERMINATE_SESSION_VALUE
            ),
            notificationIds
        )
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals(PolarFirstTimeUseConfig.FTU_CONFIG_FILENAME, writeOperation.path)
        Assert.assertArrayEquals(config.toProto().toByteArray(), writeData.single())
        val stopSyncParams = PftpNotification.PbPFtpStopSyncParams.parseFrom(notificationParams[2])
        Assert.assertTrue(stopSyncParams.completed)
    }

    @Test
    fun `putNotification writes rest notification payload`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        api.putNotification(deviceId, "{\"enabled\":true}", "/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording")

        Assert.assertEquals(1, writeHeaders.size)
        Assert.assertEquals(1, writePayloads.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording", writeOperation.path)
        Assert.assertArrayEquals("{\"enabled\":true}".toByteArray(), writePayloads.single()!!.readBytes())
    }

    @Test
    fun `stopSleepRecording uses shared sleep REST stop path`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flowOf(0L)

        api.stopSleepRecording(deviceId)

        Assert.assertEquals(1, writeHeaders.size)
        Assert.assertEquals(1, writePayloads.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals(PolarRuntimePlannerAdapter.stopSleepRecordingPath(), writeOperation.path)
        Assert.assertArrayEquals("{}".toByteArray(), writePayloads.single()!!.readBytes())
    }

    @Test
    fun `putNotification propagates write failure after payload is prepared`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writePayloads = mutableListOf<ByteArrayInputStream?>()
        val transportError = RuntimeException("REST notification write failed")
        every { client.write(capture(writeHeaders), captureNullable(writePayloads)) } returns flow { throw transportError }

        try {
            api.putNotification(deviceId, "{}", "/REST/TEST.API?cmd=post")
            Assert.fail("Expected REST notification write failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(1, writeHeaders.size)
        Assert.assertEquals(1, writePayloads.size)
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/REST/TEST.API?cmd=post", writeOperation.path)
        Assert.assertArrayEquals("{}".toByteArray(), writePayloads.single()!!.readBytes())
    }

    @Test
    fun `readFile reads low level file path from fake transport`() = runTest {
        assertFileFacadeRuntimePolicyVectorContains("read-low-level-file-success")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val response = ByteArrayOutputStream().apply { write(byteArrayOf(0x01, 0x02, 0x03)) }
        coEvery { client.request(capture(requestHeaders)) } returns response

        val result = api.readFile(deviceId, "/U/0/CUSTOM.BIN")

        Assert.assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), result)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/U/0/CUSTOM.BIN", requestOperation.path)
    }

    @Test
    fun `readFile preserves empty low level file payload as success`() = runTest {
        assertFileFacadeRuntimePolicyVectorContains("read-low-level-file-empty-success")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        coEvery { client.request(capture(requestHeaders)) } returns ByteArrayOutputStream()

        val result = api.readFile(deviceId, "/U/0/EMPTY.BIN")

        Assert.assertArrayEquals(byteArrayOf(), result)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/U/0/EMPTY.BIN", requestOperation.path)
    }

    @Test
    fun `readFile propagates low level request failure`() = runTest {
        assertFileFacadeRuntimePolicyVectorContains("read-low-level-file-request-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val transportError = RuntimeException("low level read failed")
        coEvery { client.request(capture(requestHeaders)) } throws transportError

        try {
            api.readFile(deviceId, "/U/0/CUSTOM.BIN")
            Assert.fail("Expected low level read failure to propagate")
        } catch (error: Exception) {
            Assert.assertSame(transportError, error.cause)
        }
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/U/0/CUSTOM.BIN", requestOperation.path)
    }

    @Test
    fun `readFile maps low level pftp response error to enum name`() = runTest {
        assertFileFacadeRuntimePolicyVectorContains("read-low-level-file-response-error")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        coEvery { client.request(capture(requestHeaders)) } throws BlePsFtpUtils.PftpResponseError(
            "low level read missing",
            PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number
        )

        try {
            api.readFile(deviceId, "/U/0/CUSTOM.BIN")
            Assert.fail("Expected low level read response error to propagate")
        } catch (error: Exception) {
            Assert.assertEquals("NO_SUCH_FILE_OR_DIRECTORY", error.message)
        }
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/U/0/CUSTOM.BIN", requestOperation.path)
    }

    @Test
    fun `writeFile writes low level file payload`() = runTest {
        assertFileFacadeRuntimePolicyVectorContains("write-low-level-file-success")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writeData = ByteArrayOutputStream()
        every { client.write(capture(writeHeaders), any()) } answers {
            secondArg<ByteArrayInputStream>().copyTo(writeData)
            flowOf(0L)
        }

        api.writeFile(deviceId, "/U/0/CUSTOM.BIN", byteArrayOf(0x0A, 0x0B))

        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/CUSTOM.BIN", writeOperation.path)
        Assert.assertArrayEquals(byteArrayOf(0x0A, 0x0B), writeData.toByteArray())
    }

    @Test
    fun `writeFile consumes low level write progress before success`() = runTest {
        assertFileFacadeRuntimePolicyVectorContains("write-low-level-file-progress-success")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writeData = ByteArrayOutputStream()
        every { client.write(capture(writeHeaders), any()) } answers {
            secondArg<ByteArrayInputStream>().copyTo(writeData)
            flowOf(0L, 2L)
        }

        api.writeFile(deviceId, "/U/0/PROGRESS.BIN", byteArrayOf(0x10, 0x11))

        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/PROGRESS.BIN", writeOperation.path)
        Assert.assertArrayEquals(byteArrayOf(0x10, 0x11), writeData.toByteArray())
    }

    @Test
    fun `writeFile propagates low level write failure after payload is prepared`() = runTest {
        assertFileFacadeRuntimePolicyVectorContains("write-low-level-file-stream-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writeData = ByteArrayOutputStream()
        val transportError = RuntimeException("low level write failed")
        every { client.write(capture(writeHeaders), any()) } answers {
            secondArg<ByteArrayInputStream>().copyTo(writeData)
            flow { throw transportError }
        }

        try {
            api.writeFile(deviceId, "/U/0/CUSTOM.BIN", byteArrayOf(0x0C, 0x0D))
            Assert.fail("Expected low level write failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/CUSTOM.BIN", writeOperation.path)
        Assert.assertArrayEquals(byteArrayOf(0x0C, 0x0D), writeData.toByteArray())
    }

    @Test
    fun `writeFile propagates low level pftp response error after payload is prepared`() = runTest {
        assertFileFacadeRuntimePolicyVectorContains("write-low-level-file-response-error")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val writeHeaders = mutableListOf<ByteArray>()
        val writeData = ByteArrayOutputStream()
        val responseError = BlePsFtpUtils.PftpResponseError(
            "low level write missing",
            PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number
        )
        every { client.write(capture(writeHeaders), any()) } answers {
            secondArg<ByteArrayInputStream>().copyTo(writeData)
            flow { throw responseError }
        }

        try {
            api.writeFile(deviceId, "/U/0/CUSTOM.BIN", byteArrayOf(0x0E, 0x0F))
            Assert.fail("Expected low level write response error to propagate")
        } catch (error: BlePsFtpUtils.PftpResponseError) {
            Assert.assertSame(responseError, error)
            Assert.assertEquals(PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number, error.error)
        }
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(writeHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/CUSTOM.BIN", writeOperation.path)
        Assert.assertArrayEquals(byteArrayOf(0x0E, 0x0F), writeData.toByteArray())
    }

    @Test
    fun `deleteFileOrDirectory sends low level remove request`() = runTest {
        assertFileFacadeRuntimePolicyVectorContains("delete-low-level-file-success")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        coEvery { client.request(capture(requestHeaders)) } returns ByteArrayOutputStream()

        api.deleteFileOrDirectory(deviceId, "/U/0/CUSTOM.BIN")

        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.REMOVE, requestOperation.command)
        Assert.assertEquals("/U/0/CUSTOM.BIN", requestOperation.path)
    }

    @Test
    fun `deleteFileOrDirectory propagates low level request failure`() = runTest {
        assertFileFacadeRuntimePolicyVectorContains("delete-low-level-file-request-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val transportError = RuntimeException("low level delete failed")
        coEvery { client.request(capture(requestHeaders)) } throws transportError

        try {
            api.deleteFileOrDirectory(deviceId, "/U/0/CUSTOM.BIN")
            Assert.fail("Expected low level delete failure to propagate")
        } catch (error: Exception) {
            Assert.assertSame(transportError, error.cause ?: error)
        }
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.REMOVE, requestOperation.command)
        Assert.assertEquals("/U/0/CUSTOM.BIN", requestOperation.path)
    }

    @Test
    fun `deleteFileOrDirectory maps low level pftp response error to enum name`() = runTest {
        assertFileFacadeRuntimePolicyVectorContains("delete-low-level-file-response-error")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        coEvery { client.request(capture(requestHeaders)) } throws BlePsFtpUtils.PftpResponseError(
            "low level delete missing",
            PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number
        )

        try {
            api.deleteFileOrDirectory(deviceId, "/U/0/CUSTOM.BIN")
            Assert.fail("Expected low level delete response error to propagate")
        } catch (error: Exception) {
            Assert.assertEquals("NO_SUCH_FILE_OR_DIRECTORY", error.message)
        }
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.REMOVE, requestOperation.command)
        Assert.assertEquals("/U/0/CUSTOM.BIN", requestOperation.path)
    }

    @Test
    fun `deleteDeviceDateFolders sends remove request for each date in range`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        coEvery { client.request(capture(requestHeaders)) } returns ByteArrayOutputStream()

        api.deleteDeviceDateFolders(deviceId, LocalDate.of(2026, 5, 30), LocalDate.of(2026, 6, 1))

        val operations = requestHeaders.map { PftpRequest.PbPFtpOperation.parseFrom(it) }
        Assert.assertEquals(
            listOf("/U/0/20260530", "/U/0/20260531", "/U/0/20260601"),
            operations.map { it.path }
        )
        Assert.assertTrue(operations.all { it.command == PftpRequest.PbPFtpOperation.Command.REMOVE })
    }

    @Test
    fun `deleteDeviceDateFolders ignores missing day directory response`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        coEvery { client.request(capture(requestHeaders)) } throws BlePsFtpUtils.PftpResponseError(
            "PFTP error ${PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number}",
            PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number
        )

        api.deleteDeviceDateFolders(deviceId, LocalDate.of(2026, 5, 31), LocalDate.of(2026, 5, 31))

        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.REMOVE, requestOperation.command)
        Assert.assertEquals("/U/0/20260531", requestOperation.path)
    }

    @Test
    fun `deleteTelemetryData removes telemetry bin files from fake transport`() = runTest {
        assertStoredDataCleanupWorkflowVectorContains("telemetry-root-trc-bin-filter")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val rootDirectory = PftpResponse.PbPFtpDirectory.newBuilder()
            .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("TRC10.BIN").setSize(4L))
            .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("ABC10.BIN").setSize(4L))
            .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("TRC10.TXT").setSize(4L))
            .build()
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders)) } answers {
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            if (operation.command == PftpRequest.PbPFtpOperation.Command.GET) {
                ByteArrayOutputStream().apply { write(rootDirectory.toByteArray()) }
            } else {
                ByteArrayOutputStream()
            }
        }

        api.deleteTelemetryData(deviceId)

        val operations = requestHeaders.map { PftpRequest.PbPFtpOperation.parseFrom(it) }
        Assert.assertEquals(listOf(PftpRequest.PbPFtpOperation.Command.GET, PftpRequest.PbPFtpOperation.Command.REMOVE), operations.map { it.command })
        Assert.assertEquals(listOf("/", "/TRC10.BIN"), operations.map { it.path })
    }

    @Test
    fun `deleteTelemetryData swallows list failure`() = runTest {
        assertStoredDataCleanupWorkflowVectorContains("telemetry-list-failure-platform-policy")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val transportError = RuntimeException("telemetry list failed")
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders)) } throws transportError

        api.deleteTelemetryData(deviceId)

        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/", requestOperation.path)
    }

    @Test
    fun `deleteStoredDeviceData removes sd log files from fake transport`() = runTest {
        assertStoredDataCleanupWorkflowVectorContains("sdlogs-extension-filter")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val sdLogsDirectory = PftpResponse.PbPFtpDirectory.newBuilder()
            .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("A.SLG").setSize(4L))
            .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("B.TXT").setSize(5L))
            .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("C.BPB").setSize(6L))
            .build()
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders)) } answers {
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            if (operation.command == PftpRequest.PbPFtpOperation.Command.GET) {
                ByteArrayOutputStream().apply { write(sdLogsDirectory.toByteArray()) }
            } else {
                ByteArrayOutputStream()
            }
        }

        api.deleteStoredDeviceData(deviceId, PolarBleApi.PolarStoredDataType.SDLOGS, LocalDate.of(2026, 5, 31))

        val operations = requestHeaders.map { PftpRequest.PbPFtpOperation.parseFrom(it) }
        Assert.assertEquals(
            listOf(
                PftpRequest.PbPFtpOperation.Command.GET,
                PftpRequest.PbPFtpOperation.Command.REMOVE,
                PftpRequest.PbPFtpOperation.Command.REMOVE
            ),
            operations.map { it.command }
        )
        Assert.assertEquals(listOf("/SDLOGS/", "/SDLOGS/A.SLG", "/SDLOGS/B.TXT"), operations.map { it.path })
    }

    @Test
    fun `deleteStoredDeviceData swallows sd log list failure`() = runTest {
        assertStoredDataCleanupWorkflowVectorContains("sdlogs-list-failure-platform-policy")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val transportError = RuntimeException("sd log list failed")
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders)) } throws transportError

        api.deleteStoredDeviceData(deviceId, PolarBleApi.PolarStoredDataType.SDLOGS, LocalDate.of(2026, 5, 31))

        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/SDLOGS/", requestOperation.path)
    }

    @Test
    fun `deleteStoredDeviceData removes activity files before cutoff and prunes empty parents`() = runTest {
        assertStoredDataCleanupWorkflowVectorContains("activity-prune-empty-parents")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val getCountsByPath = mutableMapOf<String, Int>()
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders)) } answers {
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            val getCount = getCountsByPath.getOrDefault(operation.path, 0)
            if (operation.command == PftpRequest.PbPFtpOperation.Command.GET) {
                getCountsByPath[operation.path] = getCount + 1
            }
            val directory = when (operation.path) {
                "/U/0/" -> PftpResponse.PbPFtpDirectory.newBuilder()
                    .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("20260530/").setSize(0L))
                    .build()
                "/U/0/20260530/" -> {
                    if (getCount == 0) {
                        PftpResponse.PbPFtpDirectory.newBuilder()
                            .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("ACT/").setSize(0L))
                            .build()
                    } else {
                        PftpResponse.PbPFtpDirectory.newBuilder().build()
                    }
                }
                "/U/0/20260530/ACT/" -> {
                    if (getCount == 0) {
                        PftpResponse.PbPFtpDirectory.newBuilder()
                            .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("ACTIVITY.BPB").setSize(8L))
                            .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("HIST.BPB").setSize(8L))
                            .build()
                    } else {
                        PftpResponse.PbPFtpDirectory.newBuilder().build()
                    }
                }
                else -> null
            }
            if (operation.command == PftpRequest.PbPFtpOperation.Command.GET && directory != null) {
                ByteArrayOutputStream().apply { write(directory.toByteArray()) }
            } else {
                ByteArrayOutputStream()
            }
        }

        api.deleteStoredDeviceData(deviceId, PolarBleApi.PolarStoredDataType.ACTIVITY, LocalDate.of(2026, 5, 31))

        val operations = requestHeaders.map { PftpRequest.PbPFtpOperation.parseFrom(it) }
        Assert.assertEquals(
            listOf(
                PftpRequest.PbPFtpOperation.Command.GET,
                PftpRequest.PbPFtpOperation.Command.GET,
                PftpRequest.PbPFtpOperation.Command.GET,
                PftpRequest.PbPFtpOperation.Command.REMOVE,
                PftpRequest.PbPFtpOperation.Command.GET,
                PftpRequest.PbPFtpOperation.Command.REMOVE,
                PftpRequest.PbPFtpOperation.Command.GET,
                PftpRequest.PbPFtpOperation.Command.REMOVE
            ),
            operations.map { it.command }
        )
        Assert.assertEquals(
            listOf(
                "/U/0/",
                "/U/0/20260530/",
                "/U/0/20260530/ACT/",
                "/U/0/20260530/ACT/ACTIVITY.BPB",
                "/U/0/20260530/ACT/",
                "/U/0/20260530/ACT",
                "/U/0/20260530/",
                "/U/0/20260530"
            ),
            operations.map { it.path }
        )
    }

    @Test
    fun `deleteStoredDeviceData removes automatic sample files by embedded sample date`() = runTest {
        assertStoredDataCleanupWorkflowVectorContains("automatic-sample-embedded-day-filter")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders)) } answers {
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            val data = when (operation.path) {
                "/U/0/AUTOS/" -> PftpResponse.PbPFtpDirectory.newBuilder()
                    .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("20260530/").setSize(0L))
                    .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("20260601/").setSize(0L))
                    .build()
                    .toByteArray()
                "/U/0/AUTOS/20260530/" -> PftpResponse.PbPFtpDirectory.newBuilder()
                    .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("AUTOS001.BPB").setSize(8L))
                    .build()
                    .toByteArray()
                "/U/0/AUTOS/20260601/" -> PftpResponse.PbPFtpDirectory.newBuilder()
                    .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("AUTOS002.BPB").setSize(8L))
                    .build()
                    .toByteArray()
                "/U/0/AUTOS/20260530/AUTOS001.BPB" -> PbAutomaticSampleSessions.newBuilder()
                    .setDay(PbDate.newBuilder().setYear(2026).setMonth(5).setDay(30).build())
                    .build()
                    .toByteArray()
                "/U/0/AUTOS/20260601/AUTOS002.BPB" -> PbAutomaticSampleSessions.newBuilder()
                    .setDay(PbDate.newBuilder().setYear(2026).setMonth(6).setDay(1).build())
                    .build()
                    .toByteArray()
                else -> ByteArray(0)
            }
            ByteArrayOutputStream().apply {
                if (operation.command == PftpRequest.PbPFtpOperation.Command.GET) {
                    write(data)
                }
            }
        }

        api.deleteStoredDeviceData(deviceId, PolarBleApi.PolarStoredDataType.AUTO_SAMPLE, LocalDate.of(2026, 5, 31))

        val operations = requestHeaders.map { PftpRequest.PbPFtpOperation.parseFrom(it) }
        Assert.assertEquals(
            listOf(
                PftpRequest.PbPFtpOperation.Command.GET,
                PftpRequest.PbPFtpOperation.Command.GET,
                PftpRequest.PbPFtpOperation.Command.GET,
                PftpRequest.PbPFtpOperation.Command.REMOVE,
                PftpRequest.PbPFtpOperation.Command.GET,
                PftpRequest.PbPFtpOperation.Command.GET
            ),
            operations.map { it.command }
        )
        Assert.assertEquals(
            listOf(
                "/U/0/AUTOS/",
                "/U/0/AUTOS/20260530/",
                "/U/0/AUTOS/20260530/AUTOS001.BPB",
                "/U/0/AUTOS/20260530/AUTOS001.BPB",
                "/U/0/AUTOS/20260601/",
                "/U/0/AUTOS/20260601/AUTOS002.BPB"
            ),
            operations.map { it.path }
        )
    }

    @Test
    fun `getFileList lists low level directory without recursion`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val response = ByteArrayOutputStream().apply {
            write(
                PftpResponse.PbPFtpDirectory.newBuilder()
                    .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("A.BIN").setSize(2L))
                    .addEntries(PftpResponse.PbPFtpEntry.newBuilder().setName("DIR/").setSize(0L))
                    .build()
                    .toByteArray()
            )
        }
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders)) } returns response

        val result = api.getFileList(deviceId, "U/0", recurseDeep = false)

        Assert.assertEquals(listOf("/U/0/A.BIN", "/U/0/DIR/"), result)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/U/0/", requestOperation.path)
    }

    @Test
    fun `isFtuDone reads user id and returns true when master identifier is present`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val response = ByteArrayOutputStream().apply {
            write(PbUserIdentifier.newBuilder().setMasterIdentifier(-1L).build().toByteArray())
        }
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } returns response

        val result = api.isFtuDone(deviceId)

        Assert.assertTrue(result)
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/U/0/USERID.BPB", requestOperation.path)
    }

    @Test
    fun `isFtuDone reads user id and returns false when master identifier is absent`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val response = ByteArrayOutputStream().apply {
            write(PbUserIdentifier.newBuilder().build().toByteArray())
        }
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } returns response

        val result = api.isFtuDone(deviceId)

        Assert.assertFalse(result)
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/U/0/USERID.BPB", requestOperation.path)
    }

    @Test
    fun `isFtuDone propagates user id request failure`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val transportError = RuntimeException("user id read failed")
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } throws transportError

        try {
            api.isFtuDone(deviceId)
            Assert.fail("Expected user id request failure to propagate")
        } catch (error: Exception) {
            Assert.assertSame(transportError, error.cause)
        }
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/U/0/USERID.BPB", requestOperation.path)
    }

    @Test
    fun `listRestApiServices requests service api and decodes service paths`() = runTest {
        assertRestFacadeRuntimePolicyVectorContains("list-rest-api-services-success")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val response = ByteArrayOutputStream().apply {
            write("""{"services":{"sleep":"/REST/SLEEP.API","training":"/REST/TRAINING.API"}}""".toByteArray())
        }
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } returns response

        val result = api.listRestApiServices(deviceId)

        Assert.assertEquals(setOf("sleep", "training"), result.serviceNames.toSet())
        Assert.assertEquals("/REST/SLEEP.API", result.pathsForServices["sleep"])
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/REST/SERVICE.API", requestOperation.path)
    }

    @Test
    fun `getRestApiDescription requests path and decodes description`() = runTest {
        assertRestFacadeRuntimePolicyVectorContains("get-rest-api-description-success")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val response = ByteArrayOutputStream().apply {
            write("""{"events":["sleep"],"endpoints":["stop"],"cmd":{"post":"/REST/SLEEP.API?cmd=post"},"sleep":{"details":["state"],"triggers":["change"]}}""".toByteArray())
        }
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } returns response

        val result = api.getRestApiDescription(deviceId, "/REST/SLEEP.API")

        Assert.assertEquals(listOf("sleep"), result.events)
        Assert.assertEquals(listOf("/REST/SLEEP.API?cmd=post"), result.actionPaths)
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/REST/SLEEP.API", requestOperation.path)
    }

    @Test
    fun `getRestApiDescription propagates description request failure`() = runTest {
        assertRestFacadeRuntimePolicyVectorContains("get-rest-api-description-request-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val transportError = RuntimeException("service description read failed")
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } throws transportError

        try {
            api.getRestApiDescription(deviceId, "/REST/SLEEP.API")
            Assert.fail("Expected REST service description request failure to propagate")
        } catch (error: Exception) {
            Assert.assertSame(transportError, error.cause)
        }
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/REST/SLEEP.API", requestOperation.path)
    }

    @Test
    fun `getRestApiDescription maps pftp response error to enum name`() = runTest {
        assertRestFacadeRuntimePolicyVectorContains("get-rest-api-description-response-error")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } throws BlePsFtpUtils.PftpResponseError(
            "REST service description failed",
            PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number
        )

        try {
            api.getRestApiDescription(deviceId, "/REST/SLEEP.API")
            Assert.fail("Expected REST service description response error to propagate")
        } catch (error: Exception) {
            Assert.assertEquals("NO_SUCH_FILE_OR_DIRECTORY", error.message)
        }
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/REST/SLEEP.API", requestOperation.path)
    }

    @Test
    fun `getRestApiDescription empty successful response is parse failure`() = runTest {
        assertRestFacadeRuntimePolicyVectorContains("get-rest-api-description-empty-success")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } returns ByteArrayOutputStream()

        try {
            api.getRestApiDescription(deviceId, "/REST/SLEEP.API")
            Assert.fail("Expected REST service description empty response to fail parsing")
        } catch (error: Exception) {
            assertRestJsonParseFailure(error)
        }
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/REST/SLEEP.API", requestOperation.path)
    }

    @Test
    fun `getRestApiDescription malformed successful response is parse failure`() = runTest {
        assertRestFacadeRuntimePolicyVectorContains("get-rest-api-description-malformed-success")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } returns ByteArrayOutputStream().apply {
            write("{".toByteArray())
        }

        try {
            api.getRestApiDescription(deviceId, "/REST/SLEEP.API")
            Assert.fail("Expected REST service description malformed response to fail parsing")
        } catch (error: Exception) {
            assertRestJsonParseFailure(error)
        }
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/REST/SLEEP.API", requestOperation.path)
    }

    @Test
    fun `listRestApiServices propagates service api request failure`() = runTest {
        assertRestFacadeRuntimePolicyVectorContains("list-rest-api-services-request-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        val transportError = RuntimeException("service api read failed")
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } throws transportError

        try {
            api.listRestApiServices(deviceId)
            Assert.fail("Expected REST service request failure to propagate")
        } catch (error: Exception) {
            Assert.assertSame(transportError, error.cause)
        }
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/REST/SERVICE.API", requestOperation.path)
    }

    @Test
    fun `listRestApiServices maps pftp response error to enum name`() = runTest {
        assertRestFacadeRuntimePolicyVectorContains("list-rest-api-services-response-error")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } throws BlePsFtpUtils.PftpResponseError(
            "REST service list failed",
            PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number
        )

        try {
            api.listRestApiServices(deviceId)
            Assert.fail("Expected REST service list response error to propagate")
        } catch (error: Exception) {
            Assert.assertEquals("NO_SUCH_FILE_OR_DIRECTORY", error.message)
        }
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/REST/SERVICE.API", requestOperation.path)
    }

    @Test
    fun `listRestApiServices empty successful response is parse failure`() = runTest {
        assertRestFacadeRuntimePolicyVectorContains("list-rest-api-services-empty-success")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } returns ByteArrayOutputStream()

        try {
            api.listRestApiServices(deviceId)
            Assert.fail("Expected REST service list empty response to fail parsing")
        } catch (error: Exception) {
            assertRestJsonParseFailure(error)
        }
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/REST/SERVICE.API", requestOperation.path)
    }

    @Test
    fun `listRestApiServices malformed successful response is parse failure`() = runTest {
        assertRestFacadeRuntimePolicyVectorContains("list-rest-api-services-malformed-success")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val requestHeaders = mutableListOf<ByteArray>()
        mockPolarFileSystemV2()
        coEvery { client.request(capture(requestHeaders), any()) } returns ByteArrayOutputStream().apply {
            write("{".toByteArray())
        }

        try {
            api.listRestApiServices(deviceId)
            Assert.fail("Expected REST service list malformed response to fail parsing")
        } catch (error: Exception) {
            assertRestJsonParseFailure(error)
        }
        Assert.assertEquals(1, requestHeaders.size)
        val requestOperation = PftpRequest.PbPFtpOperation.parseFrom(requestHeaders.single())
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, requestOperation.command)
        Assert.assertEquals("/REST/SERVICE.API", requestOperation.path)
    }

    @Test
    fun `doFactoryReset sends reset notification`() = runTest {
        assertCommandRuntimePolicyVectorContains("factory-reset")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } returns Unit

        api.doFactoryReset(deviceId)

        Assert.assertEquals(listOf(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal), notificationIds)
        val params = PftpNotification.PbPFtpFactoryResetParams.parseFrom(notificationParams.single())
        Assert.assertFalse(params.sleep)
        Assert.assertTrue(params.doFactoryDefaults)
        Assert.assertFalse(params.otaFwupdate)
    }

    @Test
    fun `doFactoryReset with preserve pairing sends ota firmware update flag`() = runTest {
        assertCommandRuntimePolicyVectorContains("factory-reset-preserve-pairing")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } returns Unit

        @Suppress("DEPRECATION")
        api.doFactoryReset(deviceId, preservePairingInformation = true)

        Assert.assertEquals(listOf(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal), notificationIds)
        val params = PftpNotification.PbPFtpFactoryResetParams.parseFrom(notificationParams.single())
        Assert.assertFalse(params.sleep)
        Assert.assertTrue(params.doFactoryDefaults)
        Assert.assertTrue(params.otaFwupdate)
    }

    @Test
    fun `doRestart sends reset notification without factory defaults`() = runTest {
        assertCommandRuntimePolicyVectorContains("restart")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } returns Unit

        api.doRestart(deviceId)

        Assert.assertEquals(listOf(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal), notificationIds)
        val params = PftpNotification.PbPFtpFactoryResetParams.parseFrom(notificationParams.single())
        Assert.assertFalse(params.sleep)
        Assert.assertFalse(params.doFactoryDefaults)
        Assert.assertFalse(params.otaFwupdate)
    }

    @Test
    fun `setWareHouseSleep sends reset notification with sleep and factory defaults`() = runTest {
        assertCommandRuntimePolicyVectorContains("warehouse-sleep")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } returns Unit

        api.setWareHouseSleep(deviceId)

        Assert.assertEquals(listOf(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal), notificationIds)
        val params = PftpNotification.PbPFtpFactoryResetParams.parseFrom(notificationParams.single())
        Assert.assertTrue(params.sleep)
        Assert.assertTrue(params.doFactoryDefaults)
        Assert.assertFalse(params.otaFwupdate)
    }

    @Test
    fun `turnDeviceOff sends reset notification with sleep only`() = runTest {
        assertCommandRuntimePolicyVectorContains("turn-device-off")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } returns Unit

        api.turnDeviceOff(deviceId)

        Assert.assertEquals(listOf(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal), notificationIds)
        val params = PftpNotification.PbPFtpFactoryResetParams.parseFrom(notificationParams.single())
        Assert.assertTrue(params.sleep)
        Assert.assertFalse(params.doFactoryDefaults)
        Assert.assertFalse(params.otaFwupdate)
    }

    @Test
    fun `doFactoryReset propagates notification failure`() = runTest {
        assertCommandRuntimePolicyVectorContains("factory-reset-notification-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        val transportError = RuntimeException("reset notification failed")
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } throws transportError

        try {
            api.doFactoryReset(deviceId)
            Assert.fail("Expected reset notification failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(listOf(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal), notificationIds)
    }

    @Test
    fun `doFactoryReset with preserve pairing propagates notification failure`() = runTest {
        assertCommandRuntimePolicyVectorContains("factory-reset-preserve-pairing-notification-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        val transportError = RuntimeException("preserve pairing reset notification failed")
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } throws transportError

        try {
            @Suppress("DEPRECATION")
            api.doFactoryReset(deviceId, preservePairingInformation = true)
            Assert.fail("Expected preserve pairing reset notification failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(listOf(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal), notificationIds)
        val params = PftpNotification.PbPFtpFactoryResetParams.parseFrom(notificationParams.single())
        Assert.assertTrue(params.otaFwupdate)
    }

    @Test
    fun `doRestart propagates notification failure`() = runTest {
        assertCommandRuntimePolicyVectorContains("restart-notification-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        val transportError = RuntimeException("restart notification failed")
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } throws transportError

        try {
            api.doRestart(deviceId)
            Assert.fail("Expected restart notification failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(listOf(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal), notificationIds)
        val params = PftpNotification.PbPFtpFactoryResetParams.parseFrom(notificationParams.single())
        Assert.assertFalse(params.doFactoryDefaults)
    }

    @Test
    fun `setWareHouseSleep propagates notification failure`() = runTest {
        assertCommandRuntimePolicyVectorContains("warehouse-sleep-notification-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        val transportError = RuntimeException("warehouse sleep notification failed")
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } throws transportError

        try {
            api.setWareHouseSleep(deviceId)
            Assert.fail("Expected warehouse sleep notification failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(listOf(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal), notificationIds)
        val params = PftpNotification.PbPFtpFactoryResetParams.parseFrom(notificationParams.single())
        Assert.assertTrue(params.sleep)
        Assert.assertTrue(params.doFactoryDefaults)
    }

    @Test
    fun `turnDeviceOff propagates notification failure`() = runTest {
        assertCommandRuntimePolicyVectorContains("turn-device-off-notification-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        val transportError = RuntimeException("turn device off notification failed")
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } throws transportError

        try {
            api.turnDeviceOff(deviceId)
            Assert.fail("Expected turn device off notification failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
        Assert.assertEquals(listOf(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal), notificationIds)
        val params = PftpNotification.PbPFtpFactoryResetParams.parseFrom(notificationParams.single())
        Assert.assertTrue(params.sleep)
        Assert.assertFalse(params.doFactoryDefaults)
    }

    @Test
    fun `sendInitializationAndStartSyncNotifications requests sync then sends initialize and start sync`() = runTest {
        assertCommandRuntimePolicyVectorContains("sync-start-success")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } returns Unit

        val result = api.sendInitializationAndStartSyncNotifications(deviceId)

        Assert.assertTrue(result)
        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.REQUEST_SYNCHRONIZATION_VALUE), queryIds)
        Assert.assertEquals(listOf(null), queryParams)
        Assert.assertEquals(
            listOf(
                PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE,
                PftpNotification.PbPFtpHostToDevNotification.START_SYNC_VALUE
            ),
            notificationIds
        )
        Assert.assertEquals(listOf(null, null), notificationParams)
    }

    @Test
    fun `sendInitializationAndStartSyncNotifications returns false when sync request fails`() = runTest {
        assertCommandRuntimePolicyVectorContains("sync-start-query-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val queryIds = mutableListOf<Int>()
        val queryParams = mutableListOf<ByteArray?>()
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        val transportError = RuntimeException("sync request failed")
        coEvery { client.query(capture(queryIds), captureNullable(queryParams)) } throws transportError
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } returns Unit

        val result = api.sendInitializationAndStartSyncNotifications(deviceId)

        Assert.assertFalse(result)
        Assert.assertEquals(listOf(PftpRequest.PbPFtpQuery.REQUEST_SYNCHRONIZATION_VALUE), queryIds)
        Assert.assertEquals(listOf(null), queryParams)
        Assert.assertTrue(notificationIds.isEmpty())
        Assert.assertTrue(notificationParams.isEmpty())
    }

    @Test
    fun `sendTerminateAndStopSyncNotifications sends completed stop sync then terminate session`() = runTest {
        assertCommandRuntimePolicyVectorContains("sync-stop-success")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } returns Unit

        api.sendTerminateAndStopSyncNotifications(deviceId)

        Assert.assertEquals(
            listOf(
                PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE,
                PftpNotification.PbPFtpHostToDevNotification.TERMINATE_SESSION_VALUE
            ),
            notificationIds
        )
        val stopSyncParams = PftpNotification.PbPFtpStopSyncParams.parseFrom(notificationParams[0])
        Assert.assertTrue(stopSyncParams.completed)
        Assert.assertNull(notificationParams[1])
    }

    @Test
    fun `sendTerminateAndStopSyncNotifications swallows notification failure`() = runTest {
        assertCommandRuntimePolicyVectorContains("sync-stop-notification-failure")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val notificationIds = mutableListOf<Int>()
        val notificationParams = mutableListOf<ByteArray?>()
        coEvery { client.sendNotification(capture(notificationIds), captureNullable(notificationParams)) } throws RuntimeException("stop sync failed")

        api.sendTerminateAndStopSyncNotifications(deviceId)

        Assert.assertEquals(listOf(PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE), notificationIds)
        val stopSyncParams = PftpNotification.PbPFtpStopSyncParams.parseFrom(notificationParams.single())
        Assert.assertTrue(stopSyncParams.completed)
    }

    @Test
    fun `setMultiBLEConnectionMode sends configure command with enable value`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPfcConnection(deviceId)
        val commands = mutableListOf<PfcMessage>()
        val values = mutableListOf<Int>()
        coEvery { client.sendControlPointCommand(capture(commands), capture(values)) } returns BlePfcClient.PfcResponse(byteArrayOf(0, PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING.numVal.toByte(), 1))

        api.setMultiBLEConnectionMode(deviceId, enable = true)

        Assert.assertEquals(listOf(PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING), commands)
        Assert.assertEquals(listOf(1), values)
    }

    @Test
    fun `setMultiBLEConnectionMode maps non success response to operation not supported`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPfcConnection(deviceId)
        val commands = mutableListOf<PfcMessage>()
        val values = mutableListOf<Int>()
        coEvery { client.sendControlPointCommand(capture(commands), capture(values)) } returns BlePfcClient.PfcResponse(byteArrayOf(0, PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING.numVal.toByte(), 2))

        try {
            api.setMultiBLEConnectionMode(deviceId, enable = false)
            Assert.fail("Expected non-success PFC response to map to operation not supported")
        } catch (error: Exception) {
            Assert.assertTrue(error is PolarOperationNotSupported)
        }
        Assert.assertEquals(listOf(PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING), commands)
        Assert.assertEquals(listOf(0), values)
    }

    @Test
    fun `getMultiBLEConnectionMode sends request command and maps enabled payload`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPfcConnection(deviceId)
        val commands = mutableListOf<PfcMessage>()
        val params = mutableListOf<ByteArray?>()
        coEvery { client.sendControlPointCommand(capture(commands), captureNullable(params)) } returns BlePfcClient.PfcResponse(byteArrayOf(0, PfcMessage.PFC_REQUEST_MULTI_CONNECTION_SETTING.numVal.toByte(), 1, 1))

        val result = api.getMultiBLEConnectionMode(deviceId)

        Assert.assertTrue(result)
        Assert.assertEquals(listOf(PfcMessage.PFC_REQUEST_MULTI_CONNECTION_SETTING), commands)
        Assert.assertEquals(listOf(null), params)
    }

    @Test
    fun `setOfflineRecordingTrigger maps public trigger and secret to PMD facade request`() = runTest {
        assertOfflineTriggerRuntimePolicyVectorContains("set-trigger-success-with-secret")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPmdConnection(deviceId)
        val capturedTriggers = mutableListOf<PmdOfflineTrigger>()
        val capturedSecrets = mutableListOf<PmdSecret?>()
        val trigger = PolarOfflineRecordingTrigger(
            triggerMode = PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START,
            triggerFeatures = mapOf(
                PolarBleApi.PolarDeviceDataType.ACC to PolarSensorSetting(
                    mapOf(
                        PolarSensorSetting.SettingType.SAMPLE_RATE to 52,
                        PolarSensorSetting.SettingType.RESOLUTION to 16
                    )
                ),
                PolarBleApi.PolarDeviceDataType.HR to null
            )
        )
        val secretBytes = ByteArray(16) { index -> index.toByte() }
        val secret = PolarRecordingSecret(secretBytes)
        coEvery { client.setOfflineRecordingTrigger(capture(capturedTriggers), captureNullable(capturedSecrets)) } just runs

        api.setOfflineRecordingTrigger(deviceId, trigger, secret)

        val pmdTrigger = capturedTriggers.single()
        Assert.assertEquals(PmdOfflineRecTriggerMode.TRIGGER_SYSTEM_START, pmdTrigger.triggerMode)
        val acc = pmdTrigger.triggers[PmdMeasurementType.ACC] ?: error("ACC trigger missing")
        Assert.assertEquals(PmdOfflineRecTriggerStatus.TRIGGER_ENABLED, acc.first)
        Assert.assertEquals(52, acc.second!!.selected[PmdSetting.PmdSettingType.SAMPLE_RATE])
        Assert.assertEquals(16, acc.second!!.selected[PmdSetting.PmdSettingType.RESOLUTION])
        val offlineHr = pmdTrigger.triggers[PmdMeasurementType.OFFLINE_HR] ?: error("HR trigger missing")
        Assert.assertEquals(PmdOfflineRecTriggerStatus.TRIGGER_ENABLED, offlineHr.first)
        Assert.assertNull(offlineHr.second)
        Assert.assertEquals(2, pmdTrigger.triggers.size)
        val pmdSecret = capturedSecrets.single()!!
        Assert.assertEquals(PmdSecret.SecurityStrategy.AES128, pmdSecret.strategy)
        Assert.assertArrayEquals(secretBytes, pmdSecret.key)
    }

    @Test
    fun `setOfflineRecordingTrigger propagates PMD facade error with mapped payload prepared`() = runTest {
        assertOfflineTriggerRuntimePolicyVectorContains("set-trigger-mode-error")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPmdConnection(deviceId)
        val capturedTriggers = mutableListOf<PmdOfflineTrigger>()
        val transportError = RuntimeException("offline trigger set mode failed")
        val trigger = PolarOfflineRecordingTrigger(
            triggerMode = PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START,
            triggerFeatures = mapOf(PolarBleApi.PolarDeviceDataType.HR to null)
        )
        coEvery { client.setOfflineRecordingTrigger(capture(capturedTriggers), null) } throws transportError

        try {
            api.setOfflineRecordingTrigger(deviceId, trigger, null)
            Assert.fail("Expected offline trigger set failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }

        val pmdTrigger = capturedTriggers.single()
        Assert.assertEquals(PmdOfflineRecTriggerMode.TRIGGER_SYSTEM_START, pmdTrigger.triggerMode)
        Assert.assertTrue(pmdTrigger.triggers.containsKey(PmdMeasurementType.OFFLINE_HR))
    }

    @Test
    fun `getOfflineRecordingTriggerSetup maps PMD status to public trigger and propagates errors`() = runTest {
        assertOfflineTriggerRuntimePolicyVectorContains("get-trigger-success")
        assertOfflineTriggerRuntimePolicyVectorContains("get-trigger-transport-error")
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPmdConnection(deviceId)
        coEvery { client.getOfflineRecordingTriggerStatus() } returns PmdOfflineTrigger(
            triggerMode = PmdOfflineRecTriggerMode.TRIGGER_SYSTEM_START,
            triggers = mapOf(
                PmdMeasurementType.ACC to Pair(
                    PmdOfflineRecTriggerStatus.TRIGGER_ENABLED,
                    PmdSetting(byteArrayOf(0x00, 0x01, 0x34, 0x00))
                ),
                PmdMeasurementType.GYRO to Pair(PmdOfflineRecTriggerStatus.TRIGGER_ENABLED, null),
                PmdMeasurementType.OFFLINE_HR to Pair(PmdOfflineRecTriggerStatus.TRIGGER_ENABLED, null)
            )
        )

        val result = api.getOfflineRecordingTriggerSetup(deviceId)

        Assert.assertEquals(PolarOfflineRecordingTriggerMode.TRIGGER_SYSTEM_START, result.triggerMode)
        Assert.assertTrue(result.triggerFeatures.containsKey(PolarBleApi.PolarDeviceDataType.ACC))
        Assert.assertEquals(setOf(52), result.triggerFeatures[PolarBleApi.PolarDeviceDataType.ACC]!!.settings[PolarSensorSetting.SettingType.SAMPLE_RATE])
        Assert.assertTrue(result.triggerFeatures.containsKey(PolarBleApi.PolarDeviceDataType.HR))
        Assert.assertFalse(result.triggerFeatures.containsKey(PolarBleApi.PolarDeviceDataType.GYRO))

        val transportError = RuntimeException("offline trigger status failed")
        coEvery { client.getOfflineRecordingTriggerStatus() } throws transportError
        try {
            api.getOfflineRecordingTriggerSetup(deviceId)
            Assert.fail("Expected offline trigger status failure to propagate")
        } catch (error: RuntimeException) {
            Assert.assertSame(transportError, error)
        }
    }

    private fun mockPsFtpConnection(
        deviceId: String,
        polarDeviceType: String = "ignite3"
    ): Pair<BlePsFtpClient, BleDeviceSession> {
        val client = mockk<BlePsFtpClient>()
        val session = mockk<BleDeviceSession>()
        val advContent = mockk<BleAdvertisementContent>()

        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns polarDeviceType
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)

        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } returns session

        return Pair(client, session)
    }

    private fun automaticReconnection(api: BDBleApiImpl): Boolean? {
        val method = BDBleApiImpl::class.java.getDeclaredMethod("getAutomaticReconnection")
        method.isAccessible = true
        return method.invoke(api) as Boolean?
    }

    private fun mockPmdConnection(deviceId: String): Pair<BlePMDClient, BleDeviceSession> {
        val client = mockk<BlePMDClient>()
        val session = mockk<BleDeviceSession>()
        val advContent = mockk<BleAdvertisementContent>()

        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "ignite3"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(BlePMDClient.PMD_SERVICE) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)

        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPmdClientReady(deviceId, any()) } returns session

        return Pair(client, session)
    }

    private fun mockPfcConnection(deviceId: String): Pair<BlePfcClient, BleDeviceSession> {
        val client = mockk<BlePfcClient>()
        val session = mockk<BleDeviceSession>()
        val advContent = mockk<BleAdvertisementContent>()

        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType } returns "ignite3"
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(PFC_SERVICE) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)

        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsPfcClientReady(deviceId, any()) } returns session

        return Pair(client, session)
    }

    private fun mockRecordingSupported() {
        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { BlePolarDeviceCapabilitiesUtility.isRecordingSupported(any()) } returns true
    }

    private fun mockPolarFileSystemV2() {
        mockkObject(BlePolarDeviceCapabilitiesUtility)
        every { getFileSystemType(any()) } returns BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2
    }

    private fun assertCommandRuntimePolicyVectorContains(vectorTerm: String) {
        val vector = JsonParser().parse(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/command-runtime/reset-sync-h10-command-policy.json")
                .readText()
        ).asJsonObject
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val operationIds = input.getAsJsonArray("operations").map { it.asJsonObject.get("id").asString }
        val commonRuntimeCaseIds = expected.getAsJsonObject("commonRuntimePrototype").getAsJsonArray("cases").map { it.asJsonObject.get("id").asString }
        Assert.assertEquals("reset-sync-h10-command-policy", vector.get("id").asString)
        Assert.assertEquals("reset_sync_h10_command_policy", vector.get("case").asString)
        Assert.assertEquals(COMMAND_RUNTIME_POLICY_OPERATION_IDS, operationIds)
        Assert.assertEquals(COMMAND_RUNTIME_POLICY_OPERATION_IDS, commonRuntimeCaseIds)
        Assert.assertEquals(vectorTerm, operationIds.firstOrNull { it == vectorTerm })
        Assert.assertEquals("fake-command-runtime-policy", vector.getAsJsonObject("execution").get("kind").asString)
        Assert.assertEquals("public-facade-command-capture", vector.getAsJsonObject("execution").get("transport").asString)
        Assert.assertEquals(COMMAND_RUNTIME_POLICY_COMMON_DECISION, vector.get("commonDecision").asString)
    }

    @Test
    fun `command runtime readiness manifest is pinned before runtime migration`() {
        assertSinglePolicyReadinessManifest(
            manifestPath = "sdk/command-runtime/reset-sync-h10-command-readiness.json",
            id = "reset-sync-h10-command-readiness",
            kind = "resetSyncH10CommandReadiness",
            policyPath = "sdk/command-runtime/reset-sync-h10-command-policy.json",
            families = listOf(
                "h10-recording-start-query",
                "h10-recording-start-query-failure",
                "h10-recording-stop-query",
                "h10-recording-stop-query-failure",
                "h10-recording-status-query",
                "h10-recording-status-query-failure",
                "live-exercise-start-query",
                "live-exercise-pause-query",
                "live-exercise-resume-query",
                "live-exercise-stop-query",
                "live-exercise-status-query",
                "offline-exercise-v2-start-query",
                "offline-exercise-v2-stop-query",
                "offline-exercise-v2-status-query",
                "factory-reset-flags",
                "factory-reset-notification-failure",
                "preserve-pairing-reset-flags",
                "preserve-pairing-reset-notification-failure",
                "restart-reset-flags",
                "restart-reset-notification-failure",
                "warehouse-sleep-reset-flags",
                "warehouse-sleep-reset-notification-failure",
                "turn-device-off-reset-flags",
                "turn-device-off-reset-notification-failure",
                "sync-start-notification-sequence",
                "sync-start-query-failure-platform-split",
                "sync-stop-complete-terminate-sequence",
                "sync-stop-notification-failure-platform-split",
                "facade-error-mapping-gate",
                "platform-facade-vector-reference-gate",
                "compile-verification-gate"
            ),
            commonDecision = COMMAND_RUNTIME_READINESS_COMMON_DECISION,
            androidConsumers = listOf("com.polar.sdk.impl.BDBleApiImplTest"),
            iosConsumers = listOf("PolarBleApiImplTests"),
            commonPrototypeConsumers = listOf("com.polar.sharedtest.CommandRuntimePolicyCommonTest")
        )
    }

    private fun assertStoredDataCleanupWorkflowVectorContains(vectorTerm: String) {
        val vector = JsonParser().parse(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/stored-data-cleanup/cleanup-workflow-policy.json")
                .readText()
        ).asJsonObject
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val scenarioIds = input.getAsJsonArray("scenarios").map { it.asJsonObject.get("id").asString }
        val commonRuntimeCaseIds = expected.getAsJsonObject("commonRuntimePrototype").getAsJsonArray("cases").map { it.asJsonObject.get("id").asString }
        Assert.assertEquals("stored-data-cleanup-workflow-policy", vector.get("id").asString)
        Assert.assertEquals("cleanup_workflow_policy", vector.get("case").asString)
        Assert.assertEquals(STORED_DATA_CLEANUP_POLICY_SCENARIO_IDS, scenarioIds)
        Assert.assertEquals(STORED_DATA_CLEANUP_POLICY_SCENARIO_IDS, commonRuntimeCaseIds)
        Assert.assertEquals(vectorTerm, scenarioIds.firstOrNull { it == vectorTerm })
        Assert.assertEquals("fake-cleanup-runtime-policy", vector.getAsJsonObject("execution").get("kind").asString)
        Assert.assertEquals("directory-list-and-remove-command-capture", vector.getAsJsonObject("execution").get("transport").asString)
        Assert.assertEquals(STORED_DATA_CLEANUP_POLICY_COMMON_DECISION, vector.get("commonDecision").asString)
    }

    @Test
    fun `stored data cleanup readiness manifest is pinned before cleanup migration`() {
        assertSinglePolicyReadinessManifest(
            manifestPath = "sdk/stored-data-cleanup/cleanup-workflow-readiness.json",
            id = "stored-data-cleanup-workflow-readiness",
            kind = "storedDataCleanupWorkflowReadiness",
            policyPath = "sdk/stored-data-cleanup/cleanup-workflow-policy.json",
            families = listOf(
                "telemetry-trc-filter",
                "sdlogs-extension-filter",
                "activity-prune-empty-parents",
                "automatic-sample-embedded-day-filter",
                "list-failure-platform-split",
                "empty-parent-path-platform-split",
                "facade-error-mapping-gate",
                "platform-facade-vector-reference-gate",
                "compile-verification-gate"
            ),
            commonDecision = STORED_DATA_CLEANUP_READINESS_COMMON_DECISION,
            androidConsumers = listOf("com.polar.sdk.impl.BDBleApiImplTest"),
            iosConsumers = listOf("PolarBleApiImplTests"),
            commonPrototypeConsumers = listOf("com.polar.sharedtest.StoredDataCleanupRuntimePolicyCommonTest")
        )
    }

    @Test
    fun `stored data cleanup parent pruning uses shared path split policy`() {
        Assert.assertEquals("/SDLOGS", PolarRuntimePlannerAdapter.storedDataCleanupRootPath(PolarBleApi.PolarStoredDataType.SDLOGS.type, "/U/0"))
        Assert.assertEquals("/U/0/AUTOS", PolarRuntimePlannerAdapter.storedDataCleanupRootPath(PolarBleApi.PolarStoredDataType.AUTO_SAMPLE.type, "/U/0"))
        Assert.assertEquals("/U/0", PolarRuntimePlannerAdapter.storedDataCleanupRootPath(PolarBleApi.PolarStoredDataType.ACTIVITY.type, "/U/0"))
        Assert.assertEquals(
            listOf(
                PftpRequest.PbPFtpOperation.Command.GET to "/SDLOGS",
                PftpRequest.PbPFtpOperation.Command.REMOVE to "/SDLOGS/A.SLG"
            ),
            PolarRuntimePlannerAdapter.planStoredDataCleanupOperations(
                kind = "filterDirectoryEntries",
                rootPath = "/SDLOGS",
                entries = listOf("A.SLG", "C.BPB"),
                includeSuffixes = listOf(".SLG", ".TXT")
            )
        )
        Assert.assertEquals(
            PftpRequest.PbPFtpOperation.Command.REMOVE to "/U/0/20260530/ACT/ACTIVITY.BPB",
            PolarRuntimePlannerAdapter.planStoredDataCleanupRemoveOperation("/U/0", "/U/0/20260530/ACT/ACTIVITY.BPB")
        )
        Assert.assertEquals(
            listOf("/U/0/20260530/ACT", "/U/0/20260530"),
            PolarWorkflowRuntimePlanning.storedDataEmptyParentDirectories("/U/0/20260530/ACT/ACTIVITY.BPB", trailingSlash = false)
        )
    }

    private fun assertDiskTimeRuntimePolicyVectorContains(vectorTerm: String) {
        val vector = JsonParser().parse(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/disk-time-runtime/disk-time-query-policy.json")
                .readText()
        ).asJsonObject
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val operationIds = input.getAsJsonArray("operations").map { it.asJsonObject.get("id").asString }
        val commonRuntimeCaseIds = expected.getAsJsonObject("commonRuntimePrototype").getAsJsonArray("cases").map { it.asJsonObject.get("id").asString }
        Assert.assertEquals("disk-time-query-policy", vector.get("id").asString)
        Assert.assertEquals("disk_time_query_policy", vector.get("case").asString)
        Assert.assertEquals(DISK_TIME_RUNTIME_POLICY_OPERATION_IDS, operationIds)
        Assert.assertEquals(DISK_TIME_RUNTIME_POLICY_OPERATION_IDS, commonRuntimeCaseIds)
        Assert.assertEquals(vectorTerm, operationIds.firstOrNull { it == vectorTerm })
        Assert.assertEquals("fake-disk-time-query-runtime-policy", vector.getAsJsonObject("execution").get("kind").asString)
        Assert.assertEquals("public-facade-query-capture", vector.getAsJsonObject("execution").get("transport").asString)
        Assert.assertEquals(DISK_TIME_RUNTIME_POLICY_COMMON_DECISION, vector.get("commonDecision").asString)
    }

    @Test
    fun `disk time readiness manifest is pinned before runtime migration`() {
        assertSinglePolicyReadinessManifest(
            manifestPath = "sdk/disk-time-runtime/disk-time-query-readiness.json",
            id = "disk-time-query-readiness",
            kind = "diskTimeQueryReadiness",
            policyPath = "sdk/disk-time-runtime/disk-time-query-policy.json",
            families = listOf(
                "disk-space-query",
                "local-time-query",
                "local-time-with-zone-query",
                "v2-system-and-local-time-sequence",
                "h10-single-local-time-query",
                "set-local-time-transport-error",
                "local-time-transport-error",
                "local-time-with-zone-transport-error",
                "disk-space-transport-error",
                "filesystem-capability-gate",
                "facade-error-mapping-gate",
                "platform-facade-vector-reference-gate",
                "compile-verification-gate"
            ),
            commonDecision = DISK_TIME_RUNTIME_READINESS_COMMON_DECISION,
            androidConsumers = listOf("com.polar.sdk.impl.BDBleApiImplTest"),
            iosConsumers = listOf("PolarBleApiImplTests"),
            commonPrototypeConsumers = listOf("com.polar.sharedtest.DiskTimeRuntimePolicyCommonTest")
        )
    }

    private fun assertUserDeviceSettingsRuntimePolicyVectorContains(vectorTerm: String) {
        val vector = JsonParser().parse(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/user-device-settings-runtime/settings-runtime-policy.json")
                .readText()
        ).asJsonObject
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val operationIds = input.getAsJsonArray("operations").map { it.asJsonObject.get("id").asString }
        val commonRuntimeCaseIds = expected.getAsJsonObject("commonRuntimePrototype").getAsJsonArray("cases").map { it.asJsonObject.get("id").asString }
        Assert.assertEquals("user-device-settings-runtime-policy", vector.get("id").asString)
        Assert.assertEquals("user_device_settings_runtime_policy", vector.get("case").asString)
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", input.get("settingsPath").asString)
        Assert.assertEquals(USER_DEVICE_SETTINGS_RUNTIME_POLICY_OPERATION_IDS, operationIds)
        Assert.assertEquals(USER_DEVICE_SETTINGS_RUNTIME_POLICY_OPERATION_IDS, commonRuntimeCaseIds)
        Assert.assertEquals(vectorTerm, operationIds.firstOrNull { it == vectorTerm })
        Assert.assertEquals("fake-user-device-settings-runtime-policy", vector.getAsJsonObject("execution").get("kind").asString)
        Assert.assertEquals("public-facade-psftp-read-write-capture", vector.getAsJsonObject("execution").get("transport").asString)
        Assert.assertEquals(USER_DEVICE_SETTINGS_RUNTIME_POLICY_COMMON_DECISION, vector.get("commonDecision").asString)
    }

    @Test
    fun `user device settings readiness manifest is pinned before runtime migration`() {
        assertSinglePolicyReadinessManifest(
            manifestPath = "sdk/user-device-settings-runtime/settings-runtime-readiness.json",
            id = "user-device-settings-runtime-readiness",
            kind = "userDeviceSettingsRuntimeReadiness",
            policyPath = "sdk/user-device-settings-runtime/settings-runtime-policy.json",
            families = listOf(
                "settings-path-gate",
                "settings-read-success",
                "settings-read-failure-no-write",
                "whole-settings-direct-write",
                "whole-settings-write-failure-after-payload",
                "telemetry-read-then-write",
                "telemetry-read-failure-no-write",
                "telemetry-write-failure-after-payload",
                "device-location-read-then-write",
                "device-location-read-failure-no-write",
                "device-location-write-failure-after-payload",
                "usb-connection-mode-read-then-write",
                "usb-connection-mode-read-failure-no-write",
                "usb-connection-mode-write-failure-after-payload",
                "automatic-training-detection-read-then-write",
                "automatic-training-detection-read-failure-no-write",
                "automatic-training-detection-write-failure-after-payload",
                "automatic-ohr-measurement-read-then-write",
                "automatic-ohr-measurement-read-failure-no-write",
                "automatic-ohr-measurement-write-failure-after-payload",
                "daylight-saving-payload-shape",
                "protobuf-field-preservation-gate",
                "facade-error-mapping-gate",
                "platform-facade-vector-reference-gate",
                "compile-verification-gate"
            ),
            commonDecision = USER_DEVICE_SETTINGS_RUNTIME_READINESS_COMMON_DECISION,
            androidConsumers = listOf("com.polar.sdk.impl.BDBleApiImplTest"),
            iosConsumers = listOf("PolarBleApiImplTests"),
            commonPrototypeConsumers = listOf("com.polar.sharedtest.UserDeviceSettingsRuntimePolicyCommonTest")
        )
    }

    @Test
    fun `user device settings facade vector stays compatible with shared runtime planner`() {
        val vector = loadSdkGoldenVector("sdk/user-device-settings-runtime/settings-runtime-policy.json")
        val operations = vector.getAsJsonObject("input").getAsJsonArray("operations").map { it.asJsonObject }
        val expectedCases = vector.getAsJsonObject("expected")
            .getAsJsonObject("commonRuntimePrototype")
            .getAsJsonArray("cases")
            .associateBy { it.asJsonObject.get("id").asString }

        operations.forEach { operation ->
            val outcome = PolarRuntimeOrchestration.planUserDeviceSettings(
                PolarUserDeviceSettingsOperation(
                    id = operation.get("id").asString,
                    kind = operation.get("kind").asString,
                    path = operation.get("path").asString,
                    payloadFields = operation.optionalStringArray("payloadFields")
                )
            )
            val expected = expectedCases.getValue(operation.get("id").asString).asJsonObject

            Assert.assertEquals(operation.get("id").asString, expected.getAsJsonArray("commands").map { it.asString }, outcome.commands)
            Assert.assertEquals(operation.get("id").asString, expected.get("terminal").asString, outcome.terminal)
        }
    }

    private fun assertRestFacadeRuntimePolicyVectorContains(vectorTerm: String) {
        val vector = loadSdkGoldenVector("sdk/rest-service/rest-facade-runtime-policy.json")
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val operations = input.getAsJsonArray("operations").map { it.asJsonObject }
        val operationIds = operations.map { it.get("id").asString }
        val commonRuntimeCaseIds = expected.getAsJsonObject("commonRuntimePrototype").getAsJsonArray("cases").map { it.asJsonObject.get("id").asString }
        Assert.assertEquals("rest-facade-runtime-policy", vector.get("id").asString)
        Assert.assertEquals("rest_facade_runtime_policy", vector.get("case").asString)
        Assert.assertEquals("restFacadeRuntimePolicy", input.get("kind").asString)
        Assert.assertEquals(REST_FACADE_RUNTIME_POLICY_OPERATION_IDS, operationIds)
        Assert.assertEquals(REST_FACADE_RUNTIME_POLICY_OPERATION_IDS, commonRuntimeCaseIds)
        Assert.assertEquals(vectorTerm, operationIds.firstOrNull { it == vectorTerm })
        Assert.assertEquals("GET", operations.first { it.get("id").asString == "list-rest-api-services-success" }.get("command").asString)
        Assert.assertEquals("/REST/SERVICE.API", operations.first { it.get("id").asString == "list-rest-api-services-success" }.get("path").asString)
        Assert.assertEquals("service-list-json", operations.first { it.get("id").asString == "list-rest-api-services-success" }.get("payloadShape").asString)
        Assert.assertEquals("/REST/SLEEP.API", operations.first { it.get("id").asString == "get-rest-api-description-success" }.get("path").asString)
        Assert.assertEquals("service-description-json", operations.first { it.get("id").asString == "get-rest-api-description-success" }.get("payloadShape").asString)
        Assert.assertEquals("responseError", operations.first { it.get("id").asString == "list-rest-api-services-response-error" }.getAsJsonObject("transport").get("mode").asString)
        Assert.assertEquals(103, operations.first { it.get("id").asString == "list-rest-api-services-response-error" }.getAsJsonObject("transport").get("status").asInt)
        Assert.assertEquals("NO_SUCH_FILE_OR_DIRECTORY", operations.first { it.get("id").asString == "list-rest-api-services-response-error" }.getAsJsonObject("transport").get("message").asString)
        Assert.assertEquals("pftp-response-error-name", operations.first { it.get("id").asString == "list-rest-api-services-response-error" }.getAsJsonObject("expectedPlatformTerminal").get("android").asString)
        Assert.assertEquals("pftp-response-error-code", operations.first { it.get("id").asString == "list-rest-api-services-response-error" }.getAsJsonObject("expectedPlatformTerminal").get("ios").asString)
        Assert.assertEquals("successEmpty", operations.first { it.get("id").asString == "list-rest-api-services-empty-success" }.getAsJsonObject("transport").get("mode").asString)
        Assert.assertEquals("successMalformedJson", operations.first { it.get("id").asString == "get-rest-api-description-malformed-success" }.getAsJsonObject("transport").get("mode").asString)
        Assert.assertEquals("fake-rest-facade-runtime-policy", vector.getAsJsonObject("execution").get("kind").asString)
        Assert.assertEquals("public-facade-psftp-request-capture", vector.getAsJsonObject("execution").get("transport").asString)
        Assert.assertEquals(REST_FACADE_RUNTIME_POLICY_COMMON_DECISION, vector.get("commonDecision").asString)
    }

    @Test
    fun `rest facade readiness manifest is pinned before runtime migration`() {
        assertSinglePolicyReadinessManifest(
            manifestPath = "sdk/rest-service/rest-facade-runtime-readiness.json",
            id = "rest-facade-runtime-readiness",
            kind = "restFacadeRuntimeReadiness",
            policyPath = "sdk/rest-service/rest-facade-runtime-policy.json",
            families = listOf(
                "service-list-request-path",
                "service-list-json-success",
                "service-list-path-field-mapping",
                "service-description-request-path",
                "service-description-json-success",
                "service-description-action-field-mapping",
                "service-description-event-detail-trigger-mapping",
                "service-list-request-failure",
                "service-description-request-failure",
                "service-list-response-error-platform-mapping",
                "service-description-response-error-platform-mapping",
                "service-list-empty-success-parse-failure",
                "service-description-empty-success-parse-failure",
                "service-list-malformed-success-parse-failure",
                "service-description-malformed-success-parse-failure",
                "model-json-mapping-vector-reference-gate",
                "empty-response-transport-policy-gate",
                "response-error-transport-policy-gate",
                "facade-error-mapping-gate",
                "platform-facade-vector-reference-gate",
                "compile-verification-gate"
            ),
            commonDecision = REST_FACADE_RUNTIME_READINESS_COMMON_DECISION,
            androidConsumers = listOf("com.polar.sdk.impl.BDBleApiImplTest"),
            iosConsumers = listOf("PolarBleApiImplTests"),
            commonPrototypeConsumers = listOf("com.polar.sharedtest.RestFacadeRuntimePolicyCommonTest")
        )
    }

    @Test
    fun `rest facade vector stays compatible with shared runtime planner`() {
        val vector = loadSdkGoldenVector("sdk/rest-service/rest-facade-runtime-policy.json")
        val operations = vector.getAsJsonObject("input").getAsJsonArray("operations").map { it.asJsonObject }
        val expectedCases = vector.getAsJsonObject("expected")
            .getAsJsonObject("commonRuntimePrototype")
            .getAsJsonArray("cases")
            .associateBy { it.asJsonObject.get("id").asString }

        operations.forEach { operation ->
            val transport = operation.optionalObject("transport")
            val outcome = PolarRuntimeOrchestration.planRestFacade(
                PolarRestFacadeOperation(
                    id = operation.get("id").asString,
                    command = operation.get("command").asString,
                    path = operation.get("path").asString,
                    payloadShape = operation.optionalString("payloadShape"),
                    expectedFields = operation.optionalStringArray("expectedFields"),
                    transportMode = transport?.optionalString("mode"),
                    responseErrorStatus = transport?.optionalInt("status"),
                    responseErrorMessage = transport?.optionalString("message"),
                    expectedPlatformTerminal = operation.optionalObject("expectedPlatformTerminal")?.optionalString("android")
                )
            )
            val expected = expectedCases.getValue(operation.get("id").asString).asJsonObject

            Assert.assertEquals(operation.get("id").asString, expected.getAsJsonArray("commands").map { it.asString }, outcome.commands)
            Assert.assertEquals(operation.get("id").asString, expected.get("terminal").asString, outcome.terminal)
        }
    }

    private fun assertFileFacadeRuntimePolicyVectorContains(vectorTerm: String) {
        val vector = loadSdkGoldenVector("sdk/file-utils/file-facade-runtime-policy.json")
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val operations = input.getAsJsonArray("operations").map { it.asJsonObject }
        val operationIds = operations.map { it.get("id").asString }
        val commonRuntimeCaseIds = expected.getAsJsonObject("commonRuntimePrototype").getAsJsonArray("cases").map { it.asJsonObject.get("id").asString }
        Assert.assertEquals("file-facade-runtime-policy", vector.get("id").asString)
        Assert.assertEquals("file_facade_runtime_policy", vector.get("case").asString)
        Assert.assertEquals("fileFacadeRuntimePolicy", input.get("kind").asString)
        Assert.assertEquals(FILE_FACADE_RUNTIME_POLICY_OPERATION_IDS, operationIds)
        Assert.assertEquals(FILE_FACADE_RUNTIME_POLICY_OPERATION_IDS, commonRuntimeCaseIds)
        Assert.assertEquals(vectorTerm, operationIds.firstOrNull { it == vectorTerm })
        val operationsById = operations.associateBy { it.get("id").asString }
        Assert.assertEquals("GET", operationsById.getValue("read-low-level-file-success").get("command").asString)
        Assert.assertEquals("/U/0/CUSTOM.BIN", operationsById.getValue("read-low-level-file-success").get("path").asString)
        Assert.assertEquals("010203", operationsById.getValue("read-low-level-file-success").get("responseHex").asString)
        Assert.assertEquals("/U/0/EMPTY.BIN", operationsById.getValue("read-low-level-file-empty-success").get("path").asString)
        Assert.assertEquals("", operationsById.getValue("read-low-level-file-empty-success").get("responseHex").asString)
        Assert.assertEquals("PUT", operationsById.getValue("write-low-level-file-success").get("command").asString)
        Assert.assertEquals("0a0b", operationsById.getValue("write-low-level-file-success").get("payloadHex").asString)
        Assert.assertEquals(listOf(0, 2), operationsById.getValue("write-low-level-file-progress-success").getAsJsonArray("progress").map { it.asInt })
        Assert.assertEquals("writeStreamError", operationsById.getValue("write-low-level-file-stream-failure").getAsJsonObject("transport").get("mode").asString)
        Assert.assertEquals("pftpResponseError", operationsById.getValue("write-low-level-file-response-error").getAsJsonObject("transport").get("mode").asString)
        Assert.assertEquals(103, operationsById.getValue("write-low-level-file-response-error").getAsJsonObject("transport").get("status").asInt)
        Assert.assertEquals("pftp-response-error-object", operationsById.getValue("write-low-level-file-response-error").getAsJsonObject("expectedPlatformTerminal").get("android").asString)
        Assert.assertEquals("pftp-response-error-code", operationsById.getValue("write-low-level-file-response-error").getAsJsonObject("expectedPlatformTerminal").get("ios").asString)
        Assert.assertEquals("REMOVE", operationsById.getValue("delete-low-level-file-success").get("command").asString)
        Assert.assertEquals("transportError", operationsById.getValue("delete-low-level-file-request-failure").getAsJsonObject("transport").get("mode").asString)
        Assert.assertEquals("pftpResponseError", operationsById.getValue("delete-low-level-file-response-error").getAsJsonObject("transport").get("mode").asString)
        Assert.assertEquals("fake-file-facade-runtime-policy", vector.getAsJsonObject("execution").get("kind").asString)
        Assert.assertEquals("public-facade-psftp-command-capture", vector.getAsJsonObject("execution").get("transport").asString)
        Assert.assertEquals(FILE_FACADE_RUNTIME_POLICY_COMMON_DECISION, vector.get("commonDecision").asString)
    }

    private fun assertOfflineTriggerRuntimePolicyVectorContains(vectorTerm: String) {
        val vector = JsonParser().parse(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/offline-recording/trigger-runtime-policy.json")
                .readText()
        ).asJsonObject
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val scenarios = input.getAsJsonArray("scenarios").map { it.asJsonObject }
        val scenarioIds = scenarios.map { it.get("id").asString }
        val commonRuntimeCaseIds = expected.getAsJsonObject("commonRuntimePrototype").getAsJsonArray("cases").map { it.asJsonObject.get("id").asString }
        val scenariosById = scenarios.associateBy { it.get("id").asString }
        val cleanupEvidenceIds = listOf(
            expected.getAsJsonObject("platformCleanupEvidence").getAsJsonObject("android").get("id").asString,
            expected.getAsJsonObject("platformCleanupEvidence").getAsJsonObject("ios").get("id").asString
        )
        Assert.assertEquals("trigger-runtime-policy", vector.get("id").asString)
        Assert.assertEquals("trigger_runtime_policy", vector.get("case").asString)
        Assert.assertEquals("offlineTriggerRuntimePolicy", input.get("kind").asString)
        Assert.assertEquals(OFFLINE_TRIGGER_RUNTIME_POLICY_SCENARIO_IDS, scenarioIds)
        Assert.assertEquals(OFFLINE_TRIGGER_RUNTIME_POLICY_SCENARIO_IDS, commonRuntimeCaseIds)
        Assert.assertTrue((scenarioIds + cleanupEvidenceIds).contains(vectorTerm))
        Assert.assertEquals("TRIGGER_SYSTEM_START", input.getAsJsonObject("desiredTrigger").get("mode").asString)
        Assert.assertEquals(true, input.getAsJsonObject("desiredTrigger").getAsJsonObject("secret").get("present").asBoolean)
        Assert.assertEquals("controlPointError", scenariosById.getValue("set-trigger-mode-error").getAsJsonObject("transport").get("setMode").asString)
        Assert.assertEquals("transportError", scenariosById.getValue("set-trigger-status-read-error").getAsJsonObject("transport").get("getStatus").asString)
        Assert.assertEquals("controlPointError", scenariosById.getValue("set-trigger-setting-error").getAsJsonObject("transport").get("setSettings").asString)
        Assert.assertEquals("transportError", scenariosById.getValue("get-trigger-transport-error").getAsJsonObject("transport").get("getStatus").asString)
        Assert.assertEquals("android-stale-wrong-command-response-discard", expected.getAsJsonObject("platformCleanupEvidence").getAsJsonObject("android").get("id").asString)
        Assert.assertEquals("ios-pre-command-response-queue-clear", expected.getAsJsonObject("platformCleanupEvidence").getAsJsonObject("ios").get("id").asString)
        Assert.assertEquals(OFFLINE_TRIGGER_RUNTIME_POLICY_COMMON_DECISION, expected.get("commonDecision").asString)
        Assert.assertEquals("shared-common-test", vector.getAsJsonObject("execution").get("status").asString)
    }

    @Test
    fun `offline trigger runtime readiness manifest is pinned before runtime migration`() {
        assertSinglePolicyReadinessManifest(
            manifestPath = "sdk/offline-recording/trigger-runtime-readiness.json",
            id = "trigger-runtime-readiness",
            kind = "offlineTriggerRuntimeReadiness",
            policyPath = "sdk/offline-recording/trigger-runtime-policy.json",
            families = listOf(
                "typed-set-mode",
                "status-read",
                "settings-write",
                "optional-secret-attachment",
                "get-transport-error",
                "set-mode-control-point-error",
                "status-read-transport-error",
                "settings-control-point-error",
                "enabled-feature-projection",
                "excluded-feature-projection",
                "platform-packet-split",
                "facade-error-mapping-pinned",
                "compile-verification-gate"
            )
        )
    }

    @Test
    fun `file facade readiness manifest is pinned before runtime migration`() {
        assertSinglePolicyReadinessManifest(
            manifestPath = "sdk/file-utils/file-facade-runtime-readiness.json",
            id = "file-facade-runtime-readiness",
            kind = "fileFacadeRuntimeReadiness",
            policyPath = "sdk/file-utils/file-facade-runtime-policy.json",
            families = listOf(
                "low-level-file-path-gate",
                "read-file-get-success",
                "read-file-empty-success",
                "read-file-request-failure",
                "read-file-response-error",
                "write-file-put-success",
                "write-file-payload-capture",
                "write-file-progress-before-completion",
                "write-file-stream-failure-after-payload",
                "write-file-response-error-after-payload",
                "delete-file-remove-success",
                "delete-file-request-failure",
                "delete-file-response-error",
                "directory-list-shallow-vector-reference-gate",
                "directory-list-recursive-vector-reference-gate",
                "read-write-delete-model-vector-reference-gate",
                "runtime-error-policy-reference-gate",
                "malformed-directory-policy-gate",
                "response-error-policy-gate",
                "facade-error-mapping-gate",
                "platform-facade-vector-reference-gate",
                "compile-verification-gate"
            ),
            commonDecision = FILE_FACADE_RUNTIME_READINESS_COMMON_DECISION,
            androidConsumers = listOf("com.polar.sdk.impl.BDBleApiImplTest", "com.polar.sdk.api.model.utils.PolarFileUtilsTest"),
            iosConsumers = listOf("PolarBleApiImplTests", "PolarFileUtilsTest"),
            commonPrototypeConsumers = listOf("com.polar.sharedtest.FileFacadeRuntimePolicyCommonTest")
        )
    }

    @Test
    fun `file facade vector stays compatible with shared runtime planner`() {
        val vector = loadSdkGoldenVector("sdk/file-utils/file-facade-runtime-policy.json")
        val operations = vector.getAsJsonObject("input").getAsJsonArray("operations").map { it.asJsonObject }
        val expectedCases = vector.getAsJsonObject("expected")
            .getAsJsonObject("commonRuntimePrototype")
            .getAsJsonArray("cases")
            .associateBy { it.asJsonObject.get("id").asString }

        operations.forEach { operation ->
            val outcome = PolarRuntimeOrchestration.planFileFacade(
                PolarFileFacadeOperation(
                    id = operation.get("id").asString,
                    command = operation.get("command").asString,
                    path = operation.get("path").asString,
                    payloadHex = operation.optionalString("payloadHex"),
                    responseHex = operation.optionalString("responseHex"),
                    progress = operation.optionalIntArray("progress"),
                    transportMode = operation.optionalObject("transport")?.optionalString("mode")
                )
            )
            val expected = expectedCases.getValue(operation.get("id").asString).asJsonObject

            Assert.assertEquals(operation.get("id").asString, expected.getAsJsonArray("commands").map { it.asString }, outcome.commands)
            Assert.assertEquals(operation.get("id").asString, expected.get("terminal").asString, outcome.terminal)
            expected.optionalString("resultHex")?.let { resultHex ->
                Assert.assertEquals(operation.get("id").asString, resultHex, outcome.resultHex)
            }
        }
    }

    @Test
    fun `file runtime error vector stays compatible with shared runtime planner`() {
        val vector = loadSdkGoldenVector("sdk/file-utils/runtime-error-policy.json")
        val cases = vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }
        val expectedCases = vector.getAsJsonObject("expected")
            .getAsJsonObject("commonRuntimePrototype")
            .getAsJsonArray("cases")
            .associateBy { it.asJsonObject.get("id").asString }

        cases.forEach { testCase ->
            val transport = testCase.getAsJsonObject("transport")
            val outcome = PolarRuntimeOrchestration.planFileRuntimeError(
                PolarFileRuntimeErrorOperation(
                    id = testCase.get("id").asString,
                    operation = testCase.get("operation").asString,
                    path = testCase.get("path").asString,
                    payloadHex = testCase.optionalString("payloadHex"),
                    transportMode = transport.get("mode").asString,
                    status = transport.optionalInt("status"),
                    message = transport.optionalString("message"),
                    error = transport.optionalString("error"),
                    responsePayloadHex = transport.optionalString("payloadHex")
                )
            )
            val expected = expectedCases.getValue(testCase.get("id").asString).asJsonObject

            Assert.assertEquals(testCase.get("id").asString, expected.get("command").asString, outcome.command)
            Assert.assertEquals(testCase.get("id").asString, expected.get("path").asString, outcome.path)
            Assert.assertEquals(testCase.get("id").asString, expected.get("outcome").asString, outcome.outcome)
            expected.optionalInt("status")?.let { status -> Assert.assertEquals(testCase.get("id").asString, status, outcome.status) }
            expected.optionalString("message")?.let { message -> Assert.assertEquals(testCase.get("id").asString, message, outcome.message) }
            expected.optionalString("error")?.let { error -> Assert.assertEquals(testCase.get("id").asString, error, outcome.error) }
            expected.optionalString("capturedPayloadHex")?.let { payload -> Assert.assertEquals(testCase.get("id").asString, payload, outcome.capturedPayloadHex) }
        }
    }

    private fun assertSinglePolicyReadinessManifest(
        manifestPath: String,
        id: String,
        kind: String,
        policyPath: String,
        families: List<String>,
        commonDecision: String? = null,
        androidConsumers: List<String>? = null,
        iosConsumers: List<String>? = null,
        commonPrototypeConsumers: List<String>? = null
    ) {
        val manifest = JsonParser().parse(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/$manifestPath")
                .readText()
        ).asJsonObject
        val input = manifest.getAsJsonObject("input")
        val expected = manifest.getAsJsonObject("expected")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        Assert.assertEquals(id, manifest.get("id").asString)
        Assert.assertEquals(kind, input.get("kind").asString)
        Assert.assertEquals(policyPath, input.get("policyVectorPath").asString)
        Assert.assertEquals(families, requiredFamilies)
        Assert.assertEquals(families, coveredFamilies)
        val actualCommonDecision = expected.get("commonDecision").asString
        if (commonDecision == null) {
            Assert.assertTrue(actualCommonDecision.contains("compile-verified"))
        } else {
            Assert.assertEquals(commonDecision, actualCommonDecision)
            val commonRuntimePrototype = expected.getAsJsonObject("commonRuntimePrototype")
            Assert.assertEquals("executable shared commonTest runtime planning guard", commonRuntimePrototype.get("status").asString)
            Assert.assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", commonRuntimePrototype.get("reason").asString)
        }
        val consumerTests = manifest.getAsJsonObject("consumerTests")
        androidConsumers?.let { expectedConsumers ->
            Assert.assertEquals(expectedConsumers, consumerTests.getAsJsonArray("android").map { it.asString })
        }
        iosConsumers?.let { expectedConsumers ->
            Assert.assertEquals(expectedConsumers, consumerTests.getAsJsonArray("ios").map { it.asString })
        }
        commonPrototypeConsumers?.let { expectedConsumers ->
            Assert.assertEquals(expectedConsumers, consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
        }
    }

    private fun loadSdkGoldenVector(relativePath: String): JsonObject {
        return JsonParser().parse(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/$relativePath")
                .readText()
        ).asJsonObject
    }

    private fun JsonObject.optionalObject(field: String): JsonObject? {
        return if (has(field)) getAsJsonObject(field) else null
    }

    private fun JsonObject.optionalString(field: String): String? {
        return if (has(field)) get(field).asString else null
    }

    private fun JsonObject.optionalInt(field: String): Int? {
        return if (has(field)) get(field).asInt else null
    }

    private fun JsonObject.optionalStringArray(field: String): List<String> {
        return if (has(field)) getAsJsonArray(field).map { it.asString } else emptyList()
    }

    private fun JsonObject.optionalIntArray(field: String): List<Int> {
        return if (has(field)) getAsJsonArray(field).map { it.asInt } else emptyList()
    }

    private fun findRepositoryRoot(): File {
        val userDirectory = System.getProperty("user.dir") ?: error("user.dir is not set")
        var current = File(userDirectory).absoluteFile
        while (true) {
            if (current.resolve("testdata/golden-vectors").isDirectory) {
                return current
            }
            current = current.parentFile ?: error("Could not find repository root from $userDirectory")
        }
    }

    private fun testTimestamp(): PbSystemDateTime {
        return PbSystemDateTime.newBuilder()
            .setDate(PbDate.newBuilder().setYear(2026).setMonth(5).setDay(28))
            .setTime(PbTime.newBuilder().setHour(12).setMinute(0).setSeconds(0).setMillis(0))
            .setTrusted(true)
            .build()
    }

    private fun sharedAutomaticTrainingDetectionState(enabled: Boolean): PbAutomaticTrainingDetectionSettings.PbAutomaticTrainingDetectionState {
        val sharedName = PolarRuntimePlannerAdapter.userDeviceSettingsAutomaticTrainingDetectionModeName(enabled)
            ?: error("Missing shared automatic training detection state for $enabled")
        return PbAutomaticTrainingDetectionSettings.PbAutomaticTrainingDetectionState.valueOf(sharedName)
    }

    private fun sharedUsbConnectionMode(enabled: Boolean): PbUsbConnectionSettings.PbUsbConnectionMode {
        val sharedName = PolarRuntimePlannerAdapter.userDeviceSettingsUsbConnectionModeName(enabled)
            ?: error("Missing shared USB connection mode for $enabled")
        return PbUsbConnectionSettings.PbUsbConnectionMode.valueOf(sharedName)
    }

    private fun sharedDeviceLocation(value: Int): PbDeviceLocation {
        val sharedValue = PolarRuntimePlannerAdapter.userDeviceSettingsDeviceLocationName(value)
            ?.let(PolarRuntimePlannerAdapter::userDeviceSettingsDeviceLocationValue)
            ?: error("Missing shared device location for $value")
        return PbDeviceLocation.forNumber(sharedValue)
    }

    private fun assertRestJsonParseFailure(error: Exception) {
        Assert.assertTrue(
            "Expected REST JSON parse failure, got ${error::class.qualifiedName}: ${error.message}",
            error is NullPointerException ||
                error is com.google.gson.JsonSyntaxException ||
                error is IllegalArgumentException ||
                error::class.qualifiedName?.startsWith("kotlinx.serialization.") == true
        )
    }

    private fun BDBleApiImpl.withListener(listener: BleDeviceListener): BDBleApiImpl {
        val field = BDBleApiImpl::class.java.getDeclaredField("listener")
        field.isAccessible = true
        field.set(this, listener)
        return this
    }

    private fun searchSession(
        name: String,
        address: String = "AA:BB:CC:DD:EE:FF",
        rssi: Int = -60,
        services: ByteArray = ByteArray(0),
        hrPayload: ByteArray? = null
    ): BleDeviceSession {
        val advertisement = BleAdvertisementContent().apply {
            val data = hashMapOf<AD_TYPE, ByteArray>(
                AD_TYPE.GAP_ADTYPE_LOCAL_NAME_COMPLETE to name.toByteArray()
            )
            if (services.isNotEmpty()) {
                data[AD_TYPE.GAP_ADTYPE_16BIT_COMPLETE] = services
            }
            processAdvertisementData(data, EVENT_TYPE.ADV_IND, rssi)
        }
        hrPayload?.let(advertisement.polarHrAdvertisement::processPolarManufacturerData)
        val session = mockk<BleDeviceSession>()
        every { session.advertisementContent } returns advertisement
        every { session.address } returns address
        every { session.rssi } returns rssi
        every { session.name } returns advertisement.name
        every { session.polarDeviceId } returns advertisement.polarDeviceId
        every { session.polarDeviceType } returns advertisement.polarDeviceType
        every { session.isConnectableAdvertisement } returns true
        every { session.blePolarHrAdvertisement } returns advertisement.polarHrAdvertisement
        return session
    }

    private companion object {
        const val COMMAND_RUNTIME_READINESS_COMMON_DECISION = "Command runtime migration may proceed only after reset-sync-h10-command-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, H10 query failure propagation, live/offline exercise query planning, every reset-style notification failure propagation, and public facade error mapping are pinned, sync-start and sync-stop platform splits are preserved or explicitly reconciled, and the shared tests are compile-verified."
        const val STORED_DATA_CLEANUP_READINESS_COMMON_DECISION = "Stored-data cleanup migration may proceed only after cleanup-workflow-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, cleanup list-failure and empty-parent remove-path splits are preserved in adapters or reconciled explicitly, public facade error mapping is pinned, and the shared tests are compile-verified."
        const val DISK_TIME_RUNTIME_READINESS_COMMON_DECISION = "Disk/time facade runtime migration may proceed only after disk-time-query-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, filesystem capability gates remain platform-owned, public facade error mapping is pinned for disk-space and local-time query failures, V2 two-query time setting and H10 single-query behavior are preserved or explicitly reconciled, and the shared tests are compile-verified."
        const val COMMAND_RUNTIME_POLICY_COMMON_DECISION = "Promote reset/H10/exercise command planning before sync error handling; H10 query failures and reset notification failures are shared transport-error propagation, while sync failure terminals remain platform compatibility gates."
        const val STORED_DATA_CLEANUP_POLICY_COMMON_DECISION = "Promote cleanup traversal and filtering before platform-specific public error/path adapters; do not normalize Android/iOS cleanup failure behavior implicitly."
        const val DISK_TIME_RUNTIME_POLICY_COMMON_DECISION = "Promote disk/time query planning only after facade tests keep current H10 capability behavior and V2 two-query time-setting semantics pinned."
        const val USER_DEVICE_SETTINGS_RUNTIME_READINESS_COMMON_DECISION = "User-device-settings runtime migration may proceed only after settings-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, protobuf field preservation and public facade error mapping are pinned, direct whole-settings writes, read-failure no-write behavior for telemetry, location, USB, automatic-training-detection, and automatic-OHR setters, and write-failure-after-payload behavior for whole-settings, telemetry, location, USB, automatic-training-detection, and automatic-OHR writes remain covered, daylight-saving payload shape is preserved, and the shared tests are compile-verified."
        const val USER_DEVICE_SETTINGS_RUNTIME_POLICY_COMMON_DECISION = "Promote user-device-settings runtime only after direct-write, read/write sequencing, no-write read failures, write-failure payload preservation, and platform protobuf serializer differences remain covered by executable facade and model vectors."
        val COMMAND_RUNTIME_POLICY_OPERATION_IDS = listOf(
            "h10-start-recording",
            "h10-start-recording-query-failure",
            "h10-stop-recording",
            "h10-stop-recording-query-failure",
            "h10-recording-status",
            "h10-recording-status-query-failure",
            "live-exercise-start",
            "live-exercise-pause",
            "live-exercise-resume",
            "live-exercise-stop",
            "live-exercise-status",
            "offline-exercise-v2-start",
            "offline-exercise-v2-stop",
            "offline-exercise-v2-status",
            "factory-reset",
            "factory-reset-notification-failure",
            "factory-reset-preserve-pairing",
            "factory-reset-preserve-pairing-notification-failure",
            "restart",
            "restart-notification-failure",
            "warehouse-sleep",
            "warehouse-sleep-notification-failure",
            "turn-device-off",
            "turn-device-off-notification-failure",
            "sync-start-success",
            "sync-start-query-failure",
            "sync-stop-success",
            "sync-stop-notification-failure"
        )
        val DISK_TIME_RUNTIME_POLICY_OPERATION_IDS = listOf(
            "get-disk-space",
            "get-local-time",
            "get-local-time-with-zone",
            "set-local-time-v2",
            "set-local-time-h10",
            "set-local-time-failure",
            "get-local-time-failure",
            "get-local-time-with-zone-failure",
            "get-disk-space-failure"
        )
        val STORED_DATA_CLEANUP_POLICY_SCENARIO_IDS = listOf(
            "telemetry-root-trc-bin-filter",
            "sdlogs-extension-filter",
            "activity-prune-empty-parents",
            "automatic-sample-embedded-day-filter",
            "sdlogs-list-failure-platform-policy",
            "telemetry-list-failure-platform-policy"
        )
        val USER_DEVICE_SETTINGS_RUNTIME_POLICY_OPERATION_IDS = listOf(
            "get-user-device-settings",
            "get-user-device-settings-read-failure",
            "set-user-device-settings",
            "set-user-device-settings-write-failure",
            "set-telemetry-enabled",
            "set-telemetry-read-failure",
            "set-telemetry-write-failure",
            "set-user-device-location",
            "set-user-device-location-read-failure",
            "set-user-device-location-write-failure",
            "set-usb-connection-mode",
            "set-usb-connection-mode-read-failure",
            "set-usb-connection-mode-write-failure",
            "set-automatic-training-detection",
            "set-automatic-training-detection-read-failure",
            "set-automatic-training-detection-write-failure",
            "set-automatic-ohr-measurement",
            "set-automatic-ohr-measurement-read-failure",
            "set-automatic-ohr-measurement-write-failure",
            "set-daylight-saving-time"
        )
        const val REST_FACADE_RUNTIME_READINESS_COMMON_DECISION = "REST facade runtime migration may proceed only after rest-facade-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, model JSON mapping vectors remain linked, empty-response and malformed-response parse/decode failures plus response-error transport policies stay covered, public facade error mapping is pinned for service-list and service-description response errors, and the shared tests are compile-verified."
        const val REST_FACADE_RUNTIME_POLICY_COMMON_DECISION = "Promote REST facade request planning only after service-list and description success cases, service-list and service-description request failures, response-error platform mapping, empty-success and malformed-success parse/decode failures, model JSON mapping vectors, and lower-level empty-response/response-error transport policy remain explicitly covered."
        val REST_FACADE_RUNTIME_POLICY_OPERATION_IDS = listOf(
            "list-rest-api-services-success",
            "get-rest-api-description-success",
            "list-rest-api-services-request-failure",
            "get-rest-api-description-request-failure",
            "list-rest-api-services-response-error",
            "get-rest-api-description-response-error",
            "list-rest-api-services-empty-success",
            "list-rest-api-services-malformed-success",
            "get-rest-api-description-empty-success",
            "get-rest-api-description-malformed-success"
        )
        const val FILE_FACADE_RUNTIME_READINESS_COMMON_DECISION = "File facade runtime migration may proceed only after file-facade-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, directory-list traversal vectors remain linked, runtime-error-policy.json keeps malformed-directory, response-error, transport-error, empty read payload, delete request failure, write progress before completion, read/write/delete response-error, and write-stream failure behavior covered, public facade error mapping is pinned, and the shared tests are compile-verified."
        const val FILE_FACADE_RUNTIME_POLICY_COMMON_DECISION = "Promote low-level file facade planning only after read/write/delete public APIs reference this vector, directory traversal remains covered by list-files vectors, and runtime-error-policy.json keeps malformed directory, response-error, transport-error, empty read payload, delete request failure, write progress success, and write-stream failure behavior pinned."
        val FILE_FACADE_RUNTIME_POLICY_OPERATION_IDS = listOf(
            "read-low-level-file-success",
            "read-low-level-file-empty-success",
            "read-low-level-file-request-failure",
            "read-low-level-file-response-error",
            "write-low-level-file-success",
            "write-low-level-file-progress-success",
            "write-low-level-file-stream-failure",
            "write-low-level-file-response-error",
            "delete-low-level-file-success",
            "delete-low-level-file-request-failure",
            "delete-low-level-file-response-error"
        )
        const val OFFLINE_TRIGGER_RUNTIME_POLICY_COMMON_DECISION = "Shared offline trigger runtime code should model set-mode, status-read, per-feature setting writes, optional secret attachment, and get/set transport failures as typed steps before mapping them back to Android and iOS public errors."
        val OFFLINE_TRIGGER_RUNTIME_POLICY_SCENARIO_IDS = listOf(
            "set-trigger-success-with-secret",
            "set-trigger-mode-error",
            "set-trigger-status-read-error",
            "set-trigger-setting-error",
            "get-trigger-success",
            "get-trigger-transport-error"
        )
    }
}

private class CapturingFirmwareUpdateApi : FirmwareUpdateApi {
    private val checkResponses: MutableList<Response<FirmwareUpdateResponse>>
    private val packageBytes: ByteArray?
    private val packageDownloadStarted: CompletableDeferred<Unit>?
    private val packageDownloadCancelled: CompletableDeferred<Unit>?
    val checkRequests = mutableListOf<FirmwareUpdateRequest>()
    val packageUrls = mutableListOf<String>()

    constructor(
        checkResponse: Response<FirmwareUpdateResponse>,
        packageBytes: ByteArray? = null,
        packageDownloadStarted: CompletableDeferred<Unit>? = null,
        packageDownloadCancelled: CompletableDeferred<Unit>? = null
    ) {
        this.checkResponses = mutableListOf(checkResponse)
        this.packageBytes = packageBytes
        this.packageDownloadStarted = packageDownloadStarted
        this.packageDownloadCancelled = packageDownloadCancelled
    }

    constructor(
        checkResponses: List<Response<FirmwareUpdateResponse>>,
        packageBytes: ByteArray? = null,
        packageDownloadStarted: CompletableDeferred<Unit>? = null,
        packageDownloadCancelled: CompletableDeferred<Unit>? = null
    ) {
        require(checkResponses.isNotEmpty())
        this.checkResponses = checkResponses.toMutableList()
        this.packageBytes = packageBytes
        this.packageDownloadStarted = packageDownloadStarted
        this.packageDownloadCancelled = packageDownloadCancelled
    }

    override suspend fun checkFirmwareUpdate(firmwareUpdateRequest: FirmwareUpdateRequest): Response<FirmwareUpdateResponse> {
        checkRequests.add(firmwareUpdateRequest)
        return if (checkResponses.size > 1) {
            checkResponses.removeAt(0)
        } else {
            checkResponses.first()
        }
    }

    override suspend fun getFirmwareUpdatePackage(url: String): ResponseBody {
        packageUrls.add(url)
        if (packageDownloadStarted != null) {
            packageDownloadStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                packageDownloadCancelled?.complete(Unit)
            }
        }
        packageBytes?.let { return byteArrayResponseBody(it) }
        throw UnsupportedOperationException("Package download is not used by this test")
    }
}

private fun firmwareZip(vararg entries: Pair<String, ByteArray>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}

private fun byteArrayResponseBody(bytes: ByteArray): ResponseBody = object : ResponseBody() {
    override fun contentType() = null
    override fun contentLength() = bytes.size.toLong()
    override fun source() = okio.Buffer().write(bytes)
}
