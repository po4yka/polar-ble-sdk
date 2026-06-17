package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals

class BleSessionPlatformOwnershipCommonPolicyTest {
    @Test
    fun sessionStateMachineOwnershipVectorPreservesPlatformHostBoundary() {
        val vector = loadGoldenVectorText("sdk/ble-session/session-state-machine-ownership.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")

        assertEquals("session-state-machine-ownership", vector.stringValue("id"))
        assertEquals("sdk.ble-session", vector.stringValue("area"))
        assertEquals("platform_owned_session_state_machine", vector.stringValue("case"))
        assertEquals("bleSessionPlatformOwnershipAudit", input.stringValue("kind"))
        assertEquals(REQUIRED_BLE_SESSION_AUDITED_FAMILIES, input.stringArrayValue("auditedFamilies"))
        assertEquals(REQUIRED_BLE_SESSION_HOST_BOUNDARIES, input.stringArrayValue("hostBoundaries"))
        assertEquals("host_owned_session_state_machine", expected.stringValue("sharedOwnershipDecision"))
        assertEquals(BLE_SESSION_PLATFORM_OWNERSHIP_DECISION, expected.stringValue("commonDecision"))
        assertEquals("none", expected.stringValue("sharedProductionCode"))
        assertEquals(REQUIRED_ANDROID_EVIDENCE, expected.stringArrayValue("androidEvidence"))
        assertEquals(REQUIRED_IOS_EVIDENCE, expected.stringArrayValue("iosEvidence"))
        assertEquals(listOf("com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDDeviceSessionImplTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("CBScannerTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.BleSessionPlatformOwnershipCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("android"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("ios"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("common"))
    }

    @Test
    fun publicBleLifecycleParityVectorPinsSdkVisibleBehavior() {
        val vector = loadGoldenVectorText("sdk/ble-session/public-ble-lifecycle-parity.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")

        assertEquals("public-ble-lifecycle-parity", vector.stringValue("id"))
        assertEquals("sdk.ble-session", vector.stringValue("area"))
        assertEquals("public_ble_lifecycle_parity", vector.stringValue("case"))
        assertEquals("publicBleLifecycleParityFixture", input.stringValue("kind"))
        assertEquals(REQUIRED_PUBLIC_BLE_PARITY_SCRIPT, input.objectArray("scenario").map { it.stringValue("event") })
        assertEquals(REQUIRED_PUBLIC_BLE_PARITY_EVENTS, expected.stringArrayValue("publicEvents"))
        assertEquals(REQUIRED_PUBLIC_BLE_PARITY_STATES, expected.stringArrayValue("sessionStates"))
        assertEquals(listOf("HR", "PSFTP"), expected.stringArrayValue("readyFeatures"))
        assertEquals(listOf("pauseBeforeGattOperation", "resumeAfterGattOperation"), expected.stringArrayValue("scanPausePolicy"))
        assertEquals("deviceDisconnected:linkLoss", expected.objectValue("errorMappings").stringValue("linkLoss"))
        assertEquals("pairingProblem", expected.objectValue("errorMappings").stringValue("pairingProblem"))
        assertEquals("manual_transport_platform_owned", expected.stringValue("platformOwnership"))
        assertEquals(listOf("com.polar.androidcommunications.enpoints.ble.bluedroid.host.ManualBleHostContractsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("ManualBleTransportContractsTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.BleSessionPlatformOwnershipCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("android"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("ios"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("common"))
    }
}

private val REQUIRED_BLE_SESSION_AUDITED_FAMILIES = listOf(
    "android-device-session-state",
    "android-gatt-callback-routing",
    "android-gatt-operation-queue",
    "android-advertisement-aliveness",
    "ios-scanner-state",
    "ios-corebluetooth-manager-state",
    "ios-listener-observers",
    "ios-pairing-error-classification"
)

private val REQUIRED_BLE_SESSION_HOST_BOUNDARIES = listOf(
    "Android BluetoothGatt",
    "Android Bluedroid callbacks",
    "Android operation queues",
    "Android advertisement timestamps",
    "iOS CoreBluetooth CBCentralManager",
    "iOS DispatchQueue scanner serialization",
    "iOS Combine/observer lifetime",
    "platform public error mapping"
)

private val REQUIRED_ANDROID_EVIDENCE = listOf(
    "BDDeviceSessionImplTest resetGatt closes and clears BluetoothGatt",
    "BDDeviceSessionImplTest authentication checks inspect BluetoothGatt services and platform clients",
    "BleDeviceSessionTest aliveness depends on session state and advertisement timestamp windows"
)

private val REQUIRED_IOS_EVIDENCE = listOf(
    "CBScannerTest scanning transitions depend on CBCentralManager powered state and queued scanner actions",
    "CBDeviceListenerImplTest observers depend on Combine/lifetime behavior",
    "CBDeviceSessionImplTest pairing classification depends on CoreBluetooth error domains"
)

private val REQUIRED_PUBLIC_BLE_PARITY_SCRIPT = listOf(
    "scan_result",
    "open_requested",
    "service_discovery_complete",
    "notification_enable_complete",
    "stream_data",
    "disconnect",
    "reconnect_requested",
    "session_reopened",
    "pairing_problem"
)

private val REQUIRED_PUBLIC_BLE_PARITY_EVENTS = listOf(
    "deviceDiscovered",
    "sessionOpening",
    "sessionOpen",
    "featureReady:HR",
    "featureReady:PSFTP",
    "notificationEnabled:HR",
    "notificationEnabled:PSFTP",
    "streamStarted:HR",
    "streamData:HR",
    "deviceDisconnected:linkLoss",
    "reconnectRequested",
    "sessionOpen",
    "pairingProblem"
)

private val REQUIRED_PUBLIC_BLE_PARITY_STATES = listOf(
    "SESSION_OPENING",
    "SESSION_OPEN",
    "SESSION_CLOSING",
    "SESSION_CLOSED",
    "SESSION_OPENING",
    "SESSION_OPEN"
)

private const val BLE_SESSION_PLATFORM_OWNERSHIP_DECISION = "Do not introduce shared BLE/session state-machine planning until a future slice defines a deterministic contract independent of Android BluetoothGatt, Bluedroid callbacks, operation queues, advertisement timestamps, iOS CoreBluetooth manager state, scanner queues, permissions, lifecycle, and public error mapping."
