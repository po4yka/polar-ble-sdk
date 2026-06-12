# GATT Golden Vectors

This directory holds pure GATT client byte-codec fixtures that can move to shared KMP without moving BLE/GATT host behavior. `BasBatteryStatusCodecCommonPolicyTest.kt` executes the Battery Service 1.1 Battery Level Status bitfield parser over `bas-battery-status-bitfields.json` before Android `BleBattClientTest.kt` and iOS `BleBasClientTest.swift` are allowed to consume the shared codec.

These vectors cover only state-free request/response byte parsing. Client readiness, characteristic routing, notifications, reads, GATT transport, CoreBluetooth/Bluedroid lifecycle, and public error mapping remain platform-owned.
