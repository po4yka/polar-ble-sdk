# SD-Log Golden Vectors

These vectors characterize SD-log configuration enum mapping and facade-operation readiness before broader KMP migration. `sd-log-readiness.json` is consumed by `SdLogModelsCommonPolicyTest.kt`, `BDBleApiImplTest.kt`, and `PolarBleApiImplTests.swift` to keep Android, iOS, and shared KMP coverage aligned while protobuf construction, optional field presence, session notifications, BLE transport/write execution, SwiftPM/watchOS fallback, and public error translation stay platform-owned.
