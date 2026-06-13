# BLE Session Golden Vectors

This directory holds BLE/session lifecycle migration-ownership vectors for KMP. `BleSessionPlatformOwnershipCommonPolicyTest.kt` executes `session-state-machine-ownership.json` as a guarded no-migration decision: Android `BDDeviceSessionImplTest.kt` and iOS `CBScannerTest.swift` keep representative host-specific state behavior pinned while no shared session state-machine planner exists.

These vectors intentionally preserve Android BluetoothGatt, Bluedroid callback, operation queue, advertisement timestamp, iOS CoreBluetooth manager-state, scanner queue, permissions, lifecycle, and public error mapping ownership in platform code.
