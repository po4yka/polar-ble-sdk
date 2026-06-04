# SDK User Device Settings Runtime Golden Vectors

This directory owns pre-KMP public facade runtime vectors for user-device-settings PSFTP read/write orchestration. Model and protobuf presence fixtures stay under `sdk/user-device-settings/`; fixtures here describe `GET`/`PUT` sequencing, the `/U/0/S/UDEVSET.BPB` path, payload-field preservation, read failures that must not write, and write failures that occur after payload construction before settings orchestration moves into common code.

`settings-runtime-policy.json` is consumed by `UserDeviceSettingsRuntimePolicyCommonTest.kt`, `BDBleApiImplTest.kt`, and `PolarBleApiImplTests.swift`. It pins settings reads, telemetry writes, location writes, USB mode writes, automatic training detection writes, automatic OHR writes, daylight-saving writes, and selected transport-error propagation points.

`settings-runtime-readiness.json` names the pre-migration behavior-family gate for this runtime slice: the settings path, read success, read-failure no-write behavior, read-then-write payload planning, write-failure-after-payload terminals, daylight-saving payload shape, protobuf field preservation, public facade error mapping, platform facade vector references, and the `compile-verification-gate` must remain covered before production settings runtime planning moves into shared KMP code.
