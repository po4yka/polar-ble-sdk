# KMP Migration Checklist

Use this checklist to keep KMP migration work scoped, test-first, and reviewable.

## Repository Readiness

- [x] Android Gradle configuration works in a tagless checkout or clearly documents the tag requirement.
- [x] README Android minimum SDK statements are reconciled with Gradle configuration.
- [x] CocoaPods source paths are verified against the actual source layout.
- [x] A local validation command list exists for Android, iOS, and shared KMP modules.
- [x] Generated API documentation is not edited by hand during migration slices.

## KMP Build Setup

- [x] Add a minimal shared KMP module without moving behavior.
- [x] Configure Android and Apple targets.
- [x] Add `commonMain`, `commonTest`, and platform-specific test source sets only as needed.
- [x] Add a trivial common test and run it in local validation.
- [x] Document how shared artifacts are consumed by Android and iOS modules.
- [x] Delegate Android Device ID and UUID utilities to shared KMP code.
- [x] Delegate Android time/date utilities to shared KMP code.
- [x] Delegate Android product capability lookup to shared KMP code.
- [x] Delegate Android type utilities to shared KMP code.

## Test Infrastructure

- [x] Create a shared golden-vector test-data location.
- [x] Define JSON schema or documented structure for binary vectors.
- [x] Add vector-loading helpers for Android tests.
- [x] Add vector-loading helpers for iOS tests.
- [x] Add vector-loading helpers for KMP common tests.
- [x] Define the fake BLE transport coverage matrix for runtime tests.
- [x] Define the full-coverage TDD backlog before migrating behavior.
- [x] Add fake BLE transport interfaces for runtime tests before moving runtime code.

## Completed Item Evidence

| Completed checklist item | Evidence |
|---|---|
| Android Gradle configuration works in a tagless checkout or clearly documents the tag requirement. | `sources/Android/android-communications/library/build.gradle` runs `git describe --tags --always` through `ProcessBuilder`, handles nonzero Git exits during configuration, extracts a semver prefix when one exists, and falls back to `0.0.0` so `BuildConfig.GIT_VERSION` remains parseable in tagless checkouts. `sources/Android/android-communications/library/src/test/java/com/polar/sdk/api/model/utils/GoldenVectorMigrationPolicyTest.kt` rejects regressions in that tagless-safe version helper. |
| A local validation command list exists for Android, iOS, and shared KMP modules. | `KmpValidationCommands.md` lists Android, iOS, KMP common, and fixture/documentation gates, including current iOS local blockers; `scripts/ios_xcode_validation_probe.rb` makes the current iOS Xcode/CocoaPods/simulator blocker classification repeatable. |
| README Android minimum SDK statements are reconciled with Gradle configuration. | `sources/Android/android-communications/library/build.gradle` declares `minSdkVersion 26`; `README.md` and `documentation/MigrationGuide7.0.0-Android.md` now document the same Android minimum. `sources/Android/android-communications/library/src/test/java/com/polar/sdk/api/model/utils/GoldenVectorMigrationPolicyTest.kt` rejects README minSdk drift from Gradle. |
| CocoaPods source paths are verified against the actual source layout. | `PolarBleSdk.podspec` declares the current source root under `sources/iOS/ios-communications/Sources` and the existing capability resource under `sources/iOS/ios-communications/Sources/iOSCommunications/Resources/polar_device_capabilities.json`; `sources/iOS/ios-communications/README.md` local CocoaPods and Swift Package examples now point to `sources/iOS/ios-communications/`. `sources/Android/android-communications/library/src/test/java/com/polar/sdk/api/model/utils/GoldenVectorMigrationPolicyTest.kt` rejects podspec source/resource drift. |
| Generated API documentation is not edited by hand during migration slices. | `docs/polar-sdk-android/index.html` is Dokka output, `docs/polar-sdk-ios/index.html` is Jazzy output, and `KmpValidationCommands.md` requires the generated-docs git diff check to stay empty during migration slices unless release documentation is explicitly regenerated. |
| Add a minimal shared KMP module without moving behavior. | `sources/Android/android-communications/settings.gradle` includes `:shared`, `sources/Android/android-communications/shared/build.gradle` applies Kotlin Multiplatform, and `sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/SharedModule.kt` is a behavior-free marker. |
| Configure Android and Apple targets. | `sources/Android/android-communications/shared/build.gradle` declares the AGP 9 `com.android.kotlin.multiplatform.library` Android target plus `iosX64()`, `iosArm64()`, and `iosSimulatorArm64()` for the behavior-free shared module, `sources/Android/android-communications/shared/src/androidMain/AndroidManifest.xml` provides the minimal Android library manifest, and `KmpValidationCommands.md` names the shared Android/iOS target compile gate. |
| Document how shared artifacts are consumed by Android and iOS modules. | `documentation/KmpSharedArtifactConsumption.md` defines the Android `project(':shared')` dependency contract, the iOS `PolarBleSdkShared.framework` contract, the required facade/common-test gates before production consumption, and the target artifact smoke commands. |
| Delegate Android Device ID and UUID utilities to shared KMP code. | `sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/device/PolarDeviceId.kt` owns checksum validation, six- and seven-digit assembly, UUID string construction, and protocol-only identifier classification; `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DeviceIdCommonPolicyTest.kt` exercises that production common code against the shared golden vectors; `sources/Android/android-communications/library/build.gradle` consumes `project(':shared')`; `sources/Android/android-communications/library/src/main/java/com/polar/androidcommunications/api/ble/model/polar/BlePolarDeviceIdUtility.kt` and `sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/model/PolarDeviceUuid.kt` delegate to `PolarDeviceId` while preserving their existing Android public API surface. |
| Delegate Android time/date utilities to shared KMP code. | `sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/time/PolarTimeUtils.kt` owns portable date/time fields, timezone-offset minute conversion, millisecond/nanosecond conversion, duration-to-millis math, time-string formatting, and plain-date validation; `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/TimeDateCommonPolicyTest.kt` exercises that production common code against `testdata/golden-vectors/sdk/time-date/time-date-readiness.json` and its policy vectors; `sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/impl/utils/PolarTimeUtils.kt` delegates pure field construction and duration/nanosecond math to the shared utility while preserving Java time and protobuf adapter behavior. |
| Delegate Android product capability lookup to shared KMP code. | `sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/device/PolarDeviceCapabilities.kt` owns selected-config capability lookup, filesystem mapping, boolean defaults, case-insensitive device-type matching, and version-mismatch user-config merge; `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DeviceCapabilitiesCommonPolicyTest.kt` exercises that production common code against `testdata/golden-vectors/sdk/device-capabilities/capability-lookup-readiness.json` and its policy vectors; `sources/Android/android-communications/library/src/main/java/com/polar/androidcommunications/api/ble/model/polar/BlePolarDeviceCapabilitiesUtility.kt` delegates lookup and merge to shared KMP while preserving Android asset/external-file selection, Gson parsing, and the existing Android API surface. |
| Delegate Android type utilities to shared KMP code. | `sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/ble/PolarTypeUtils.kt` owns unsigned byte/int/long conversion, little-endian offset selection, shared signed-int sign extension, typed empty-payload and payload-too-long errors, and UInt64 max decimal preservation; `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/TypeUtilsCommonPolicyTest.kt` exercises that production common code against `testdata/golden-vectors/protocol/type-utils/type-utils-readiness.json` and its policy vectors; `sources/Android/android-communications/library/src/main/java/com/polar/androidcommunications/common/ble/TypeUtils.kt` delegates byte conversion primitives to shared KMP while preserving Android `UByte`/`UInt`/`ULong` return types, existing `BleUtils.validate` assertion behavior, and the pinned Android full-width signed-int sign-extension platform difference. |
| Add `commonMain`, `commonTest`, and platform-specific test source sets only as needed. | `sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/SharedModule.kt`, `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GoldenVectorTestData.kt`, and `sources/Android/android-communications/shared/src/jvmTest/kotlin/com/polar/sharedtest/GoldenVectorTestDataJvm.kt` establish the minimal source-set shape with only the JVM test target needed to execute common tests now. |
| Add a trivial common test and run it in local validation. | `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GoldenVectorTestDataCommonTest.kt` runs through `:shared:jvmTest`, and `KmpValidationCommands.md` documents the command. |
| Create a shared golden-vector test-data location. | `testdata/golden-vectors/README.md` defines the shared fixture root and repository-wide vector rules. |
| Define JSON schema or documented structure for binary vectors. | `testdata/golden-vectors/schema/golden-vector.schema.json` defines the JSON contract and `sources/Android/android-communications/library/src/test/java/com/polar/sdk/api/model/utils/GoldenVectorMigrationPolicyTest.kt` enforces required metadata and lowercase `*Hex` byte strings. |
| Add vector-loading helpers for Android tests. | `sources/Android/android-communications/library/src/test/java/com/polar/testutils/GoldenVectorTestData.kt` centralizes repository-root discovery and JSON fixture loading; `sources/Android/android-communications/library/src/test/java/com/polar/testutils/GoldenVectorTestDataTest.kt` verifies access to shared vectors. |
| Add vector-loading helpers for iOS tests. | `sources/iOS/ios-communications/Tests/GoldenVectorTestData.swift` centralizes repository-root discovery and JSON fixture loading and is wired into both iOS test targets by `sources/iOS/ios-communications/iOSCommunications.xcodeproj/project.pbxproj`. |
| Add vector-loading helpers for KMP common tests. | `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GoldenVectorTestData.kt` declares the common helper API, `sources/Android/android-communications/shared/src/jvmTest/kotlin/com/polar/sharedtest/GoldenVectorTestDataJvm.kt` provides the current executable JVM test actual, and `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GoldenVectorTestDataCommonTest.kt` proves shared vector loading through `commonTest`. |
| Define the fake BLE transport coverage matrix for runtime tests. | `KmpFakeTransportTestPlan.md` defines fake transport capabilities, required runtime matrix rows, and pre-migration gates. |
| Define the full-coverage TDD backlog before migrating behavior. | `KmpFullCoverageTddBacklog.md` converts the coverage inventory into prioritized pre-migration TDD slices, names current executable common policy tests, and defines non-Gradle batching checks so large coverage/documentation edits can happen before Gradle validation. |
| Add fake BLE transport interfaces for runtime tests before moving runtime code. | `sources/Android/android-communications/library/src/test/java/com/polar/testutils/FakeTransportContract.kt` defines the Android-hosted fake transport contract for command capture, scripted outcomes, active observer counts, idempotent stream cancellation, and upstream cancellation observation; `sources/Android/android-communications/library/src/test/java/com/polar/testutils/FakeTransportContractTest.kt` verifies read/write/stream command capture, response errors, transport errors, completion, deterministic timeout behavior, and cancellation cleanup. `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FakeTransportContract.kt` and `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FakeTransportContractCommonTest.kt` mirror the same contract as an executable KMP `commonTest` gate, `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/RestRequestTransportPolicyCommonTest.kt` consumes the REST request runtime vector through that common fake transport, `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FileRuntimeErrorPolicyCommonTest.kt` consumes the file runtime-error vector through the same common fake transport, `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/OfflineTriggerRuntimePolicyCommonTest.kt` consumes the offline-trigger runtime vector to pin set-mode/status/settings/secret/error sequencing before production trigger orchestration moves, `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FirmwareWorkflowRuntimePolicyCommonTest.kt` consumes the firmware workflow vector to pin availability/download/zip/write-order/reboot/battery policies before production workflow delegation, and `sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PsFtpRuntimePolicyCommonTest.kt` consumes the deterministic PSFTP response, notification, write-interruption, transport-failure, continuation-timeout, and write-ack-timeout vectors before production PSFTP runtime delegation. |

## Open Item Rationale

| Open checklist item | Rationale |
|---|---|
| Remaining production consumption of shared code. | Android now consumes `:shared` for the Device ID/UUID, time/date, product capability lookup, and type utility slices. iOS production framework consumption and every other Android/iOS behavior family remain open until each slice adds failing common tests, platform facade compatibility tests, and a rollback path. |
| Release readiness. | The release-readiness checklist stays open during pre-migration coverage work until production shared consumption is implemented and verified through Android example AAR consumption, iOS Swift Package or explicit deprecation evidence, CocoaPods verification or explicit deprecation, regenerated API docs when public APIs change, migration guide updates, documented platform differences, and a rollback path for every shared-module adoption step. |

## Per-Slice TDD Checklist

- [ ] Choose one behavior slice.
- [ ] List current Android implementation files.
- [ ] List current iOS implementation files.
- [ ] List existing Android tests.
- [ ] List existing iOS tests.
- [ ] Add or update Android characterization tests.
- [ ] Add or update iOS characterization tests.
- [ ] Add shared golden vectors.
- [ ] Add failing KMP common tests.
- [ ] Implement shared code.
- [ ] Delegate Android implementation to shared code.
- [ ] Delegate iOS implementation to shared code.
- [ ] Re-run Android, iOS, and KMP tests.
- [ ] Remove duplicated platform logic only after both facades pass.
- [ ] Update docs if public behavior changed.

## Suggested Slice Order

1. Device ID and UUID utilities.
2. Time and date utilities.
3. Product capability JSON parsing.
4. PMD settings and control-point response parsing.
5. ECG parser.
6. ACC, GYR, and MAG parsers.
7. PPG and PPI parsers.
8. Pressure, temperature, and skin-temperature parsers.
9. Offline recording metadata and status parsing.
10. Training-session metadata parsing.
11. Firmware/status mapping.
12. Shared fake transport contract.
13. Runtime state machines.
14. Public API compatibility adapters.

## Review Checklist

- [ ] The pull request moves only one coherent behavior slice.
- [ ] Tests fail without the shared implementation.
- [ ] Golden vectors are understandable and minimal.
- [ ] Android and iOS public APIs remain compatible unless the change is explicitly documented.
- [ ] Platform-specific BLE behavior remains platform-specific.
- [ ] New common code has no hidden Android/JVM or Apple-only dependency.
- [ ] Error mapping is covered by tests.
- [ ] Cancellation and timeout behavior is covered when streams or suspend functions are involved.
- [ ] Build metadata does not depend on Android `BuildConfig` in common code.

## Release Readiness

- [ ] Android AAR builds and is consumed by the Android example.
- [ ] Swift Package integration builds and is consumed by the iOS example.
- [ ] CocoaPods integration is verified or explicitly deprecated.
- [ ] Generated Android and iOS API docs are regenerated if public APIs changed.
- [ ] Migration guides are updated for consumer-visible changes.
- [ ] Known platform differences are documented.
- [ ] A rollback path exists for every shared module adoption step.

## Stop Conditions

Stop the migration slice and resolve the issue before continuing when Android and iOS current behavior disagree without a documented decision, a parser has no golden-vector coverage, a shared implementation requires platform APIs, a platform facade changes public behavior unintentionally, or validation requires physical hardware for logic that should be fakeable.
