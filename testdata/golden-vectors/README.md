# Golden Vector Test Data

This directory stores platform-neutral behavior fixtures for the KMP migration. Vectors in this directory are contracts: Android tests, iOS tests, and future KMP common tests should load the same inputs and assert equivalent outputs.

## Directory Layout

```text
testdata/golden-vectors/schema/golden-vector.schema.json
testdata/golden-vectors/protocol/advertisement/
testdata/golden-vectors/protocol/gatt/
testdata/golden-vectors/protocol/device-id/
testdata/golden-vectors/protocol/gatt/
testdata/golden-vectors/protocol/pmd/
testdata/golden-vectors/protocol/sensors/
testdata/golden-vectors/protocol/type-utils/
testdata/golden-vectors/sdk/activity-samples/
testdata/golden-vectors/sdk/atomic-set/
testdata/golden-vectors/sdk/automatic-samples/
testdata/golden-vectors/sdk/available-data-types/
testdata/golden-vectors/sdk/ble-session/
testdata/golden-vectors/sdk/backup-utils/
testdata/golden-vectors/sdk/command-runtime/
testdata/golden-vectors/sdk/d2h-notifications/
testdata/golden-vectors/sdk/daily-summary/
testdata/golden-vectors/sdk/device-capabilities/
testdata/golden-vectors/sdk/disk-space/
testdata/golden-vectors/sdk/disk-time-runtime/
testdata/golden-vectors/sdk/exercise-session/
testdata/golden-vectors/sdk/fake-transport/
testdata/golden-vectors/sdk/feature-availability/
testdata/golden-vectors/sdk/file-utils/
testdata/golden-vectors/sdk/first-time-use/
testdata/golden-vectors/sdk/firmware-update/
testdata/golden-vectors/sdk/kvtx/
testdata/golden-vectors/sdk/nightly-recharge/
testdata/golden-vectors/sdk/offline-recording/
testdata/golden-vectors/sdk/psftp-message-stream/
testdata/golden-vectors/sdk/psftp-notifications/
testdata/golden-vectors/sdk/psftp-response/
testdata/golden-vectors/sdk/psftp-rfc76/
testdata/golden-vectors/sdk/rest-service/
testdata/golden-vectors/sdk/sd-log/
testdata/golden-vectors/sdk/skin-temperature/
testdata/golden-vectors/sdk/sleep/
testdata/golden-vectors/sdk/spo2-test/
testdata/golden-vectors/sdk/stream-runtime/
testdata/golden-vectors/sdk/stored-data-cleanup/
testdata/golden-vectors/sdk/time-date/
testdata/golden-vectors/sdk/training-session/
testdata/golden-vectors/sdk/user-device-settings/
testdata/golden-vectors/sdk/user-device-settings-runtime/
testdata/golden-vectors/sdk/watch-face/
```

Create directories as slices need them. Do not add broad fixture dumps without a test that consumes them.

## Vector Rules

- Use JSON for vector metadata and expected structured output.
- Keep `id` values in lowercase kebab-case and `case` values in lowercase snake_case.
- Use lowercase hex strings without separators for binary payloads.
- Include `id`, `area`, `case`, `source`, `input`, `expected`, `consumerTests`, and `platforms`.
- Make `source` traceable: it must name characterization, readiness, planning, policy, prototype, migration, Android, iOS, KMP, or shared evidence rather than a generic label.
- Use optional `description` for short human context when the `case` name is not enough.
- Do not store real user data, personal identifiers, secrets, or production device dumps.
- If Android and iOS currently disagree, keep both expectations under `platformExpectations` and document the migration decision in `notes`.
- Use top-level `commonDecision` only for a shared decision that is not naturally part of `expected` or `platformExpectations.commonDecision`.
- Use `execution` only for runtime/planning vectors that need fake time, fake transport, or other non-pure execution controls.
- Keep each vector focused on one behavior unless a larger end-to-end fixture is necessary.

## Minimal Example

```json
{
  "id": "device-id-uppercase-normalization",
  "area": "device-id",
  "case": "uppercase_normalization",
  "source": "characterization",
  "input": {
    "text": "0a3ba92b"
  },
  "expected": {
    "normalized": "0A3BA92B"
  },
  "consumerTests": {
    "android": [
      "com.polar.androidcommunications.api.ble.model.polar.DeviceIdGoldenVectorTest"
    ],
    "ios": [
      "BlePolarDeviceIdUtilityTest"
    ],
    "commonPrototype": [
      "com.polar.sharedtest.DeviceIdCommonPolicyTest"
    ]
  },
  "platforms": {
    "android": true,
    "ios": true,
    "common": true
  },
  "notes": "Use for the first low-risk shared utility migration slice."
}
```

## Review Requirements

Every vector addition must be consumed or explicitly guarded before it is treated as migration-ready. Pure protocol and model fixtures may be guarded by area-level Android/iOS characterization tests plus shared commonTest policy files that reference the fixture path, and every vector still names those concrete guards in `consumerTests`. Runtime planning vectors, fake-transport vectors, and common-style prototype vectors must identify the concrete consumer tests that execute or guard them with `consumerTests`.

`GoldenVectorMigrationPolicyTest` enforces the repository-wide metadata contract: vectors must include the required fields, all `*Hex` fields must be lowercase even-length byte strings, every fixture directory must have README migration ownership notes, and every vector excluded from common KMP must carry an explicit migration rationale. The same policy test also locks `schema/golden-vector.schema.json` to the executable field allowlists, required fields, platform keys, and `consumerTests` platform keys so schema changes cannot drift from the migration gate.

Common-owned vectors and vectors with a shared common decision must be explicitly referenced from shared commonTest sources before production KMP migration. This path-level reference is paired with required per-vector `consumerTests` metadata so broad pure fixtures cannot drift away from their executable Android, iOS, or shared guards.

Runtime planning vectors and common-style prototype vectors must include `consumerTests` so the fixture names the Android, iOS, and common-prototype tests that execute or guard it. Runtime/planning vectors must populate all three keys: `android`, `ios`, and `commonPrototype`, and each populated key must contain at least one non-empty test name. Android and common-prototype entries use fully qualified Kotlin test class names, and iOS entries use Swift test type names. `GoldenVectorMigrationPolicyTest` verifies that every non-schema vector declares `consumerTests`, that each declared consumer resolves to an existing test file or Swift test type, and that the consumer file explicitly references the vector ID, filename, exact vector directory, or an owning readiness manifest that names the vector path.
