# KMP Modern Stack Audit

This audit is the current closeout checklist for the modern KMP stack. It is evidence-backed by the repository guards, shared/common policy tests, platform characterization tests, packaging probes, and artifact wiring that exist in this checkout.

## Fully Migrated Shared KMP Ownership

- Golden-vector governance is active: `scripts/kmp_non_gradle_checks.rb` and `GoldenVectorMigrationPolicyTest.kt` validate schema fields, fixture README coverage, consumer test references, shared common-test references, platform-owned row wording, fake-transport policy wording, shared production portability, generated protobuf boundaries, and stale shared-policy wording.
- Shared common production now owns deterministic parser/model/codec/policy families that are backed by common tests and platform consumers: device ID and UUID utilities, selected type/time/date helpers, product capability lookup decisions, advertisement local-name and HR manufacturer payload helpers, PMD settings/control-point/secret helpers including shared AES ECB/no-padding decryption, sensor parser families with documented platform splits, GNSS raw type 0-3 decoding on Android, offline-recording metadata, available data-type and feature-availability mapping, exercise/training-session planning helpers, activity/sleep/skin-temperature/domain mappings, disk-space/SPo2/watch-face/KVTX/D2H helpers, user-device-settings mapped protobuf byte parsing/building, REST JSON projection, REST gzip/deflate platform-actual codecs, training-session gzip and selected protobuf field parsing, PSFTP byte codecs, stream/runtime planning helpers, command/reset/sync/H10 planning, stored-data cleanup planning, disk/time planning, user-device-settings planning, firmware utility/workflow planning, backup planning, and offline-trigger planning.
- Android production consumes shared KMP through `implementation project(':shared')` in `sources/Android/android-communications/library/build.gradle`; Android local AAR distribution uses the documented two-AAR compatibility model with `polar-ble-sdk.aar` plus `polar-ble-sdk-shared.aar`.
- iOS CocoaPods and Xcode workspace production surfaces consume `PolarBleSdkShared.framework`; the iOS `PolarBleSdk` surface now defines `POLAR_KMP_SHARED_REQUIRED`, so that packaged surface fails compilation instead of silently falling back when the shared framework is not importable.
- Shared Android local metadata validation is present in `sources/Android/android-communications/shared/build.gradle` through `maven-publish`, with local-only validation through `scripts/verify_android_shared_maven_metadata.sh`; release automation remains artifact-only.

## Intentionally Platform-Owned

- BLE/session/GATT host behavior remains platform-owned: Android `BluetoothGatt`, Bluedroid callbacks, operation queues, advertisement timestamps, iOS `CBCentralManager`, scanner queues, observer lifetime, permissions, lifecycle, and public error mapping are guarded by `session-state-machine-ownership.json` and `BleSessionPlatformOwnershipCommonPolicyTest.kt`.
- GATT client behavior remains platform-owned except explicitly extracted pure codecs such as BAS Battery Level Status bitfield decoding; readiness, subscriptions, CoreBluetooth/Bluedroid behavior, lifecycle, and public error mapping stay in platform adapters.
- Generated public protobuf reconstruction remains platform-owned for training-session public models; shared KMP may parse selected fields, assemble neutral session-summary scalar DTOs, and plan neutral reconstruction slots, but Android generated `TrainingSession.Pb*` classes and Swift `Data_Pb*` public model classes stay out of `commonMain`. Rollback for the session-summary scalar parser-family slice is to ignore the neutral reconstruction fields/bridge columns and continue platform parsing from decoded payload bytes without moving generated public model construction into common KMP.
- Public facade error translation, filesystem capability gates, BLE/PSFTP transport execution, protobuf builder/parser ownership where not explicitly migrated, cancellation mechanics, timeout scheduling, progress emission text, network/zip/filesystem side effects, reconnect/wait execution, and host-state restoration remain adapter-owned unless a later slice proves a pure deterministic contract with Android, iOS, and common tests.

## Packaging And Public Contract Ownership

- Swift Package Manager and watchOS are fallback-only on a clean checkout. They may consume shared KMP only when `PolarBleSdkShared.xcframework` exists at the conditional local binary target path or a release manifest uses a real remote `binaryTarget(url:checksum:)` artifact with watchOS slices and validation.
- CocoaPods and Xcode iOS are the validated Apple shared-consumption surfaces for this phase. `PolarBleSdk.podspec` links `PolarBleSdkShared`, runs `build_kmp_ios_framework.sh`, and defines `POLAR_KMP_SHARED_REQUIRED`; `Package.swift` intentionally does not define that condition for clean SwiftPM/watchOS fallback.
- CI and release policy remain artifact-only. No Maven, CocoaPods, SwiftPM, JitPack, GitHub Pages, or external package host publication is claimed without a separate secrets/release-policy plan.

## Artifact Matrix

| Surface | Current state | Validation | Rollback |
|---|---|---|---|
| Android repository build | Android internal project dependency through `implementation project(':shared')`. | Gradle Android and shared tests from `documentation/KmpValidationCommands.md`. | Remove the shared call from the Android adapter and keep `:shared` as an unused project until the slice is reverted. |
| Android local release | Two AARs are required: `polar-ble-sdk.aar` and `polar-ble-sdk-shared.aar`. | `scripts/verify_android_example_aar_consumption.sh`. | Remove `polar-ble-sdk-shared.aar` only after the facade AAR no longer references shared classes or external metadata supplies the dependency. |
| Android shared Maven metadata | Shared local Maven metadata validation only, written under `shared/build/local-maven-validation`. | `scripts/verify_android_shared_maven_metadata.sh`. | Delete the local validation repository; no external package has been published. |
| CocoaPods/Xcode iOS | Generated `PolarBleSdkShared.framework` is consumed by CocoaPods and Xcode build phases. | `pod lib lint PolarBleSdk.podspec --allow-warnings` and the iOS XCTest gate. | Remove bridge calls or framework search paths only after Swift fallbacks cover the behavior. |
| SwiftPM/watchOS | SwiftPM/watchOS fallback-only unless a real `PolarBleSdkShared.xcframework` exists and `Package.swift` is updated to consume it. | `swift package describe` validates manifest shape only before the artifact exists; `sources/iOS/ios-communications/scripts/validate_spm_xcframework_consumption.sh` validates local generated artifact consumption. | Keep the existing Swift fallback implementation. |
| CI/release | CI/release remains artifact-only. No Maven, CocoaPods, or SwiftPM publication is claimed, and required secrets are intentionally absent. | `scripts/verify_release_packaging_policy.rb`. | Remove uploaded workflow artifacts; no external package rollback is needed. |

## External Publishing Gate

External publication remains disabled. Any future Maven, CocoaPods, or SwiftPM publication requires a release-policy document that names the package host, coordinates, required secrets, protected environment or approval path, validation command, and rollback path before a workflow may reference publication credentials.

## Current Validation Snapshot

- Passed in this final audit slice: `git diff --check`, `ruby scripts/kmp_non_gradle_checks.rb`, `swift package describe`, `pod install --project-directory=sources/iOS/ios-communications`, `pod lib lint PolarBleSdk.podspec --allow-warnings`, and focused XCTest `PolarDataUtilsTest/testPolarBleSdkIosTargetRequiresLinkedSharedFramework`.
- The current broad iOS XCTest closeout gate is green: `xcodebuild test -workspace sources/iOS/ios-communications/iOSCommunications.xcworkspace -scheme iOSCommunications -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5'` passed with 813 tests and 0 failures. Focused repro runs also passed for the previously recorded stale failures: `PpgDataTest/testPpgGoldenVectorsMatchIOSCommunicationsBehavior`, the REST empty/malformed decoding tests, `PolarBackupManagerTest/testRestoreBackup`, `PolarDataUtilsTest/testTriggerRuntimePolicyVectorIsPinnedBeforeRuntimeMigration`, `PolarDeviceRestApiServiceTests/testReceivesRestApiEventWhenUncompressed`, and `PolarTrainingSessionUtilsTests/testPayloadParserPolicyVectorIsPinnedBeforeByteLevelParserMigration`.
- The required Android policy validation for this closeout slice is green: `./gradlew :library:testSdkDebugUnitTest --tests 'com.polar.sdk.api.model.utils.GoldenVectorMigrationPolicyTest' --no-daemon --warning-mode all` passed from `sources/Android/android-communications`.

## Regression Guards

- Keep `GoldenVectorMigrationPolicyTest.kt` and `scripts/kmp_non_gradle_checks.rb` mirrored when adding final-boundary guard terms.
- Keep `POLAR_KMP_SHARED_REQUIRED` scoped to CocoaPods/Xcode iOS surfaces until SwiftPM/watchOS shared XCFramework consumption has a real artifact and build validation path.
- Keep every platform-owned boundary named above present in `KmpCoverageInventory.md`, `KmpFullCoverageTddBacklog.md`, `KmpSharedArtifactConsumption.md`, and this audit before moving more runtime delegation into shared KMP.
