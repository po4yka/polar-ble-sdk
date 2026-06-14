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

private const val BLE_SESSION_PLATFORM_OWNERSHIP_DECISION = "Do not introduce shared BLE/session state-machine planning until a future slice defines a deterministic contract independent of Android BluetoothGatt, Bluedroid callbacks, operation queues, advertisement timestamps, iOS CoreBluetooth manager state, scanner queues, permissions, lifecycle, and public error mapping."
