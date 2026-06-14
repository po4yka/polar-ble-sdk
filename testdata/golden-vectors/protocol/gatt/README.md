# GATT Golden Vectors

This directory holds pure GATT client byte-codec fixtures that can move to shared without moving BLE/GATT host behavior. `BasBatteryStatusCodecCommonPolicyTest.kt` executes the Battery Service 1.1 Battery Level Status bitfield parser over `bas-battery-status-bitfields.json`, and `HtsGattCodecCommonPolicyTest.kt` executes the HTS temperature measurement byte parser over the HTS temperature vectors, before Android and iOS adapters are allowed to consume the shared codecs.

These vectors cover only state-free request/response byte parsing. Client readiness, characteristic routing, notifications, reads, GATT transport, CoreBluetooth/Bluedroid lifecycle, callback ordering, permissions, reconnect logic, and public error mapping remain platform-owned unless a vector explicitly names a pure shared codec and the adapters keep their platform fallback path.
