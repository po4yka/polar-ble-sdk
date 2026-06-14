# SDK Disk and Time Runtime Golden Vectors

This directory owns shared-ownership public facade runtime vectors for disk-space and local-time PSFTP query planning. Model conversion stays under `sdk/disk-space/`; fixtures here describe query IDs, query parameter presence, set-local-time sequencing, and transport-error terminals before disk/time orchestration moves into common code.

`disk-time-query-policy.json` is consumed by `DiskTimeRuntimePolicyCommonTest.kt`, `BDBleApiImplTest.kt`, and `PolarBleApiImplTests.swift`. It pins `GET_DISK_SPACE`, `GET_LOCAL_TIME`, V2 `SET_SYSTEM_TIME` plus `SET_LOCAL_TIME`, H10 single `SET_LOCAL_TIME`, disk-space query failure, local-time query failures, and set-local-time failure propagation points. `disk-time-query-readiness.json` pins the complete behavior-family gate, including filesystem capability ownership, facade error mapping, and compile-verified shared execution, before disk/time query planning delegates to shared runtime code.
