# Advertisement Golden Vectors

These vectors characterize BLE advertisement parsing before any shared KMP advertisement parser is introduced. `AdvertisementCommonPolicyTest.kt` now executes the intended shared local-name, manufacturer HR-presence, malformed manufacturer payload, service membership, and RSSI median policy over these vectors while production parser migration remains gated by facade compatibility.

`protocol/advertisement/advertisement-readiness.json` is the behavior-family gate for this slice, and `advertisementReadinessManifestNamesEveryPreMigrationBehaviorFamily` pins the manifest from shared common tests. It pins `polar-local-name-parsing`, custom-prefix parsing, seven-digit device ID assembly, non-Polar local-name platform decisions, manufacturer HR presence and absence, non-Polar and unknown company behavior, unknown Polar segment handling, `malformed-gpb-missing-length-policy`, truncated HR-candidate policy, `service-uuid-membership`, `rssi-median-seven-sample-window`, platform advertisement vector references, and the `compile-verification-gate`.

Required groups:

- Local-name parsing for Polar and non-Polar devices.
- Custom advertising name prefix parsing.
- Seven-character device ID assembly from local name.
- Manufacturer data with no HR payload.
- Manufacturer data with SAGRFC23 HR payload.
- Manufacturer data with SAGRFC31 HR payload.
- Non-Polar manufacturer data.
- Malformed Polar manufacturer payloads, including truncated HR candidates and GPB segments with missing length bytes.
- Service UUID membership.
- RSSI median behavior.
