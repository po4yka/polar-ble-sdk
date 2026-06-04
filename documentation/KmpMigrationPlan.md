# Kotlin Multiplatform Migration Plan

This document describes a safe migration path from the current Android Kotlin and iOS Swift implementations to a Kotlin Multiplatform shared core. The migration must preserve public SDK behavior unless a deliberate major-version API change is documented separately.

## Goals

- Share deterministic Polar protocol logic between Android and iOS.
- Keep Bluetooth transport, permissions, lifecycle integration, and platform packaging native.
- Preserve the existing Android and iOS public APIs during the first migration phase.
- Add characterization tests before moving production code.
- Make every migration slice reversible and independently verifiable.

## Non-Goals

- Do not rewrite the BLE transport layer as common code.
- Do not replace both public SDK APIs in one step.
- Do not change product behavior only to make the KMP shape easier.
- Do not remove platform-specific examples until replacement examples are validated.
- Do not treat plain `swift build` on macOS as the only iOS validation gate; the package is iOS/watchOS-oriented and must be validated through Apple-platform builds or exported framework integration.

## Current Repository Shape

The Android SDK is a Gradle library in `sources/Android/android-communications`. Public SDK APIs live under `library/src/sdk/java/com/polar/sdk`, while lower-level BLE communications internals live under `library/src/main/java/com/polar/androidcommunications`.

The iOS SDK is under `sources/iOS/ios-communications`. Public Swift APIs live under `Sources/PolarBleSdk`, while CoreBluetooth and lower-level communication internals live under `Sources/iOSCommunications`.

Examples live under `examples/` and `demos/`. Product and feature documentation lives under `documentation/`, technical specifications under `technical_documentation/`, and generated API documentation under `docs/`.

## Target Architecture

```text
polar-ble-sdk/
  shared/
    protocol-core/
      src/commonMain/
      src/commonTest/
    sdk-core/
      src/commonMain/
      src/commonTest/
  sources/
    Android/
      android-communications/
    iOS/
      ios-communications/
```

`shared/protocol-core` owns pure protocol and data transformation logic: byte parsing, PMD frames, device identifiers, time conversion, product capability parsing, offline recording metadata, training-session metadata, and firmware/status mapping.

`shared/sdk-core` owns platform-neutral SDK orchestration only after the protocol layer is proven: state machines, feature readiness rules, connection-independent command sequencing, and common error taxonomy.

Android and iOS continue to own BLE scanning, connecting, GATT/CoreBluetooth operations, permission behavior, background behavior, app lifecycle integration, and packaging.

## Module Boundaries

Common code may depend on Kotlin stdlib, `kotlinx.coroutines`, `kotlinx-datetime`, `kotlinx-serialization`, and deterministic binary parsing utilities.

Common code must not depend on Android Bluetooth APIs, CoreBluetooth, UIKit, Swift-only concurrency types, JVM-only classes, Apple-only cryptography, or global mutable platform state.

Platform code should call common code through small interfaces or facades. Shared code should receive byte arrays, timestamps, settings, and command results; platform code should translate those to Android or iOS BLE calls.

## Suggested Shared APIs

```kotlin
interface PolarBleTransport {
    fun scan(filter: PolarScanFilter): Flow<PolarDiscoveredDevice>
    suspend fun connect(identifier: String): PolarBleConnection
}

interface PolarBleConnection {
    suspend fun read(service: PolarUuid, characteristic: PolarUuid): ByteArray
    suspend fun write(service: PolarUuid, characteristic: PolarUuid, payload: ByteArray)
    fun notifications(service: PolarUuid, characteristic: PolarUuid): Flow<ByteArray>
}
```

These interfaces are for internal architecture, not necessarily public API. They make shared runtime tests possible with fake transports while allowing Android and iOS to keep native transport implementations.

## Migration Phases

### Phase 0: Stabilize Build and Test Entry Points

Before moving code, document and fix local validation blockers. The Android Gradle version helper must remain tagless-safe, Android minimum SDK documentation must match Gradle configuration, CocoaPods paths must match the iOS source layout, and generated API documentation under `docs/` must stay generator-owned during migration slices. Use `KmpValidationCommands.md` as the current local validation command list and update it whenever Android, iOS, or shared KMP test entry points change.

### Phase 1: Characterization Tests

Add golden-vector tests for existing Android and iOS behavior before extracting shared code. Each test vector must contain raw input bytes or JSON, expected parsed output, error behavior, and source context.

### Phase 2: Add Empty KMP Structure

Add the KMP build with no production behavior change. The first merged KMP change should compile an empty or minimal shared module and run a trivial common test. This isolates build-system risk from behavior migration risk.

### Phase 3: Move Pure Utilities

Move low-risk deterministic utilities first: byte ordering, time conversion, UUID/device-id normalization, product capability JSON parsing, and settings value conversion.

### Phase 4: Move Protocol Parsers

Move sensor and protocol parsers one group at a time: ECG, ACC, GYR, MAG, PPG, PPI, pressure, temperature, skin temperature, PMD control-point responses, offline recording metadata, and training-session metadata.

### Phase 5: Delegate Platform SDKs to Shared Code

Replace Android internal implementations with shared code first because the language boundary is smaller. Then export the shared module to Apple targets and replace Swift duplicate logic behind the existing Swift public API.

### Phase 6: Shared Runtime Orchestration

Only after parser parity is stable, consider moving platform-neutral runtime behavior to `shared/sdk-core`: feature readiness, connection-independent command sequencing, retry/cancellation policy, and fake-transport integration flows.

### Phase 7: Public API Decision

Decide whether a future major release exposes a KMP-first public API. Until then, Android and iOS public APIs should remain native facades over shared internals.

## Recommended Modern Libraries

- Kotlin Multiplatform Gradle plugin and Android KMP library plugin for new shared Android artifacts.
- `kotlinx.coroutines` and `Flow` for shared asynchronous streams.
- `kotlinx-coroutines-test` for deterministic coroutine tests.
- `kotlinx-datetime` for shared time and date logic.
- `kotlinx.serialization` for shared JSON parsing, especially product capability resources.
- A KMP-compatible build metadata generator instead of Android `BuildConfig` for shared version metadata.
- Fake transport implementations for integration tests instead of device-dependent tests.

## Packaging Strategy

Android should continue publishing an AAR with the same Maven coordinates during the compatibility phase. The AAR may embed or depend on shared KMP artifacts internally.

iOS should continue supporting Swift Package Manager and CocoaPods during the compatibility phase. The KMP framework should be hidden behind the existing Swift API until a deliberate public API migration is planned.

Examples should be updated only after the SDK packaging path they use is validated.

## Compatibility Policy

For the first KMP migration stage, behavior compatibility is required for all public methods unless a test exposes an existing platform inconsistency. When Android and iOS currently differ, document the difference in the test vector and choose the compatibility behavior explicitly.

Breaking API changes require a separate migration guide with before/after examples for Android and iOS consumers.

## Definition of Done

- Shared modules compile on Android and Apple targets.
- Common tests cover every migrated parser and utility.
- Android unit tests pass against platform facades that delegate to shared code.
- iOS tests pass against Swift facades that delegate to shared code.
- Golden vectors are shared or generated from the same source data.
- Example apps build against the packaged SDKs.
- Public API documentation and migration notes are updated for any consumer-visible change.
