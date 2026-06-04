# Device ID Golden Vectors

This slice is the recommended first KMP migration candidate because it is deterministic, low-risk, and already has Android and iOS tests. `DeviceIdCommonPolicyTest.kt` now executes the shared checksum, assembly, validation, UUID, invalid-format, and platform-specific identifier-routing policy over these vectors before moving device ID or UUID normalization logic into shared code.

`protocol/device-id/device-id-readiness.json` is the behavior-family gate for this slice, and `deviceIdReadinessManifestNamesEveryPreMigrationBehaviorFamily` pins the manifest from shared common tests. It pins `checksum-width-6-assembly`, `checksum-width-7-assembly`, zero-device assembly, validation, lowercase acceptance, empty and non-hex platform decisions, UUID conversion, `uuid-invalid-length-error`, invalid identifier rejection, `platform-specific-identifier-routing`, platform vector references, and the `compile-verification-gate`.

Required cases:

- Printed Polar device ID normalization.
- Lowercase and uppercase input.
- Bluetooth address input where supported.
- UUID string input where supported.
- Invalid length.
- Invalid characters.
- Empty input.
- Zero-value input.
