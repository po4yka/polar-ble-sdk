# KMP Migration TDD Strategy

This document defines the test-first workflow for migrating the Polar BLE SDK to Kotlin Multiplatform. The migration must be driven by characterization tests that capture current Android and iOS behavior before shared production code replaces platform-specific implementations.

## Testing Principles

- Characterize current behavior before changing it.
- Prefer deterministic byte, JSON, and model tests over device-dependent tests.
- Share test vectors across Android, iOS, and KMP common tests.
- Add one failing test for one migration slice, then implement the smallest shared code needed to pass.
- Keep BLE hardware tests separate from parser and runtime contract tests.
- Preserve platform-specific public API tests until the public API itself is intentionally changed.

## Test Pyramid

```text
Hardware/device smoke tests
Platform integration tests with fake BLE transport
Android facade tests and iOS facade tests
KMP common tests for protocol, models, and runtime rules
Golden-vector parser tests
```

The base of the pyramid should be golden-vector tests. Most migration confidence should come from byte-for-byte deterministic tests, not from physical device coverage.

## Golden-Vector Format

Every golden vector should be stored in a platform-neutral format and used by all relevant test suites.

```json
{
  "id": "pmd-ecg-v0-single-frame",
  "area": "sensor-parser",
  "case": "ecg_raw_type_0_single_frame",
  "source": "existing Android and iOS parser fixtures",
  "input": {
    "hex": "00010203"
  },
  "expected": {
    "samples": []
  },
  "consumerTests": {
    "android": [
      "com.polar.androidcommunications.api.ble.model.gatt.client.pmd.model.EcgDataTest"
    ],
    "ios": [
      "EcgDataTest"
    ],
    "commonPrototype": [
      "com.polar.sharedtest.EcgParserCommonPolicyTest"
    ]
  },
  "platforms": {
    "android": true,
    "ios": true,
    "common": true
  },
  "notes": "Keep this field for documented platform differences or device firmware context."
}
```

Use JSON for structured vectors and plain hex strings for binary payloads. Keep vectors small and focused unless a larger real-world recording is required to reproduce a parsing rule.

## Characterization Workflow

1. Identify one behavior slice.
2. Find existing Android and iOS implementation files and tests.
3. Add or extend Android tests that lock current output for representative inputs.
4. Add or extend iOS tests using the same inputs and expected output.
5. If Android and iOS disagree, document the disagreement in the vector and decide the intended shared behavior before writing shared code.
6. Add the same vector to KMP `commonTest`.
7. Implement shared production code only after the common test fails for the expected reason.
8. Delegate one platform implementation to shared code.
9. Re-run platform tests and common tests.
10. Remove duplicate platform code only after delegation is proven.

## Shared Parser Error Contract

Shared KMP parser code should use deterministic validation instead of inheriting current platform exception differences. Golden vectors that mark a platform difference are characterization evidence, not automatic shared behavior. For byte/type utilities and protocol parsers, common code should reject invalid length, invalid offset, truncated payload, and unsupported enum values with a typed parser failure before reading beyond the available bytes. Android and iOS facade tests may continue asserting their legacy exception shape until a public API decision changes it, but `commonTest` should assert the typed shared failure.

Any golden vector with `platforms.common=false` must carry `expected.commonDecision`, `platformExpectations.commonDecision`, `expected.commonRuntimePrototype`, `expected.commonWorkflowPrototype`, or `expected.migrationOwnership`. `GoldenVectorMigrationPolicyTest` enforces this so platform-only characterization cannot be mistaken for KMP-ready coverage without an explicit migration rationale.

## Shared Presence Semantics

Shared KMP model parsers should preserve protobuf field presence instead of inventing platform defaults. When a protobuf field is omitted, the common model should expose `null` or an explicit absent state unless the protocol specification defines a value-level default. Platform facades may apply compatibility defaults for existing public APIs after common parsing.

For `PolarUserDeviceSettings`, common parsing should keep omitted automatic-training numeric fields and AUTOS state nullable. Common serialization should write telemetry settings only when `telemetryEnabled` is explicitly present in the shared model. Android's deprecated whole-settings setter can keep legacy behavior by stripping telemetry in an Android adapter, while setting-specific telemetry APIs should continue to write telemetry on both platforms.

## Migration Slice Template

```text
Slice name:
Current Android files:
Current iOS files:
Current Android tests:
Current iOS tests:
Golden vectors:
Shared target package:
Expected public API impact:
Platform differences:
Validation commands:
Rollback plan:
```

Every migration pull request should include this information in its description or in a task note.

## Initial Test Inventory

### Byte and Type Utilities

Cover endian conversion, unsigned conversion, byte slicing, UUID formatting, device-id normalization, and null/empty payload behavior.

### Time Utilities

Cover epoch conversion, device local time conversion, date-only parsing, timezone-independent behavior, leap-day behavior, and invalid values. `TimeDateCommonPolicyTest.kt` now owns the executable common pre-migration gate for portable time/date field mapping, offset conversion, duration math, time-string formatting, and plain-date validation.

### Product Capabilities

Cover `polar_device_capabilities.json`, unknown product IDs, missing capabilities, capability aliases, and firmware-gated feature behavior.

### PMD Settings and Control Point

Cover settings serialization, settings parsing, active measurement parsing, offline trigger parsing, control-point success responses, and all known error responses.

### Sensor Data Parsers

Cover ECG, ACC, GYR, MAG, PPG, PPI, pressure, temperature, skin temperature, offline HR, GNSS/location, and empty data. Include versioned frame formats where the protocol supports multiple formats.

### Offline Recording

Cover listing entries, metadata parsing, recording status, trigger configuration, encrypted/secret metadata boundaries, and invalid file payloads.

### Training Sessions

Cover session references, exercise metadata, sample type flags, progress events, route samples, and missing optional fields.

### Firmware Update and REST Models

Cover status mapping, failed update states, retryable states, payload decoding, and HTTP-independent parsing.

### Runtime State Machines

Cover scan start/stop, connection state transitions, feature readiness, cancellation, duplicate notifications, disconnect during operation, timeout behavior, and retry policy using fake transports.

## Test Data Ownership

Shared vectors live under `testdata/golden-vectors`. Generated fixtures must include generation instructions. Do not store real user data, personal identifiers, or secrets in vectors.

## Platform Facade Tests

Android facade tests should prove that public `com.polar.sdk` APIs still produce the same models and errors after delegating to shared code.

iOS facade tests should prove that public Swift APIs still produce the same models, async streams, Combine publishers, and thrown errors after delegating to shared code.

Facade tests should avoid physical Bluetooth unless the behavior cannot be validated through a fake transport.

## Fake Transport Tests

Fake transports should support scanning, connection success, connection failure, service discovery, read/write responses, notification streams, disconnects, timeouts, and cancellation.

The fake transport contract should be common where possible, with small platform adapters for Android and iOS public API tests.

Use `KmpFakeTransportTestPlan.md` as the runtime migration gate. A parser/model-only slice may proceed without fake transport tests, but a public facade or runtime orchestration slice must implement the applicable matrix rows before delegating platform code to common code.

## Regression Policy

A migration slice may change behavior only when all of these are true: the current behavior is documented by a characterization test, the new behavior is documented by an updated expected output, the reason is written in the migration slice notes, and the change is consumer-visible only if release notes or a migration guide are updated.

## Minimum Validation Before Merging a Slice

- KMP common tests for the slice pass.
- Existing Android tests for the slice pass.
- Existing iOS tests for the slice pass or an equivalent Apple-platform command is documented.
- New golden vectors are reviewed as API contracts.
- No unrelated platform code is refactored in the same slice.

Use `KmpValidationCommands.md` for the current local Android, iOS, shared-module validation commands, and any infrastructure probe state.

## Coverage Expectations

Line coverage is less important than input coverage. For parsers, every supported frame version, every field boundary, malformed payloads, empty payloads, and unknown enum values should be tested. For runtime code, every state transition and cancellation path should be tested.

## First Recommended TDD Slice

Start with a low-risk deterministic parser such as `PolarDeviceUuid`, time utilities, or one PMD sensor parser with strong existing tests. Do not start with BLE scanning, connection lifecycle, firmware network calls, or public API redesign.
