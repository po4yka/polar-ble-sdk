# SDK Stored Data Cleanup Golden Vectors

This directory owns workflow-level stored-data cleanup vectors under shared ownership. The fixtures describe deterministic traversal, filtering, and remove-command planning for public cleanup APIs while leaving platform BLE transport and public error mapping in Android/iOS adapters.

`cleanup-workflow-policy.json` is consumed by `StoredDataCleanupRuntimePolicyCommonTest.kt`, `BDBleApiImplTest.kt`, and `PolarBleApiImplTests.swift`. It pins telemetry `TRC*.BIN` filtering, SDLOGS extension filtering, activity-file pruning with empty parent cleanup, automatic-sample deletion by embedded sample date, and current Android/iOS splits for list failures and empty-parent remove path spelling. `cleanup-workflow-readiness.json` pins the complete behavior-family gate, including facade error mapping and compile-verified shared execution, before cleanup traversal or filtering delegates to shared runtime code.
