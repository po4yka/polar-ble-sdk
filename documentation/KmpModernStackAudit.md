# KMP Modern Stack Audit

This audit is the current closeout checklist for the modern KMP stack. It is evidence-backed by the repository guards, shared/common policy tests, platform characterization tests, packaging probes, and artifact wiring that exist in this checkout.

## Fully Migrated Shared KMP Ownership

- Golden-vector governance is active: `scripts/kmp_non_gradle_checks.rb` and `GoldenVectorMigrationPolicyTest.kt` validate schema fields, fixture README coverage, consumer test references, shared common-test references, platform-owned row wording, fake-transport policy wording, shared production portability, generated protobuf boundaries, and stale shared-policy wording.
- Shared common production now owns deterministic parser/model/codec/policy families that are backed by common tests and platform consumers: device ID and UUID utilities, selected type/time/date helpers, product capability lookup decisions, advertisement local-name and HR manufacturer payload helpers, PMD settings/control-point/secret helpers including shared AES ECB/no-padding decryption, sensor parser families with documented platform splits, GNSS raw type 0-3 decoding on Android, offline-recording metadata, available data-type and feature-availability mapping, exercise/training-session planning helpers, activity/sleep/skin-temperature/domain mappings, disk-space/SPo2/watch-face/KVTX/D2H helpers, user-device-settings mapped protobuf byte parsing/building, REST JSON projection, REST gzip/deflate platform-actual codecs, training-session gzip and selected protobuf field parsing, PSFTP byte codecs, stream/runtime planning helpers, command/reset/sync/H10 planning, stored-data cleanup planning, disk/time planning, user-device-settings planning, firmware utility/workflow planning, backup planning, and offline-trigger planning.
- Android production consumes shared KMP through `implementation project(':shared')` in `sources/Android/android-communications/library/build.gradle`; Android local AAR distribution uses the documented two-AAR compatibility model with `polar-ble-sdk.aar` plus `polar-ble-sdk-shared.aar`.
- iOS CocoaPods and Xcode workspace production surfaces consume `PolarBleSdkShared.framework`; the iOS `PolarBleSdk` surface now defines `POLAR_KMP_SHARED_REQUIRED`, so that packaged surface fails compilation instead of silently falling back when the shared framework is not importable.
- Shared Android publication metadata is present in `sources/Android/android-communications/shared/build.gradle` through `maven-publish`, with local-only validation through `scripts/verify_android_shared_maven_publication.sh`; release automation remains artifact-only.

## Intentionally Platform-Owned

- BLE/session/GATT host behavior remains platform-owned: Android `BluetoothGatt`, Bluedroid callbacks, operation queues, advertisement timestamps, iOS `CBCentralManager`, scanner queues, observer lifetime, permissions, lifecycle, and public error mapping are guarded by `session-state-machine-ownership.json` and `BleSessionPlatformOwnershipCommonPolicyTest.kt`.
- GATT client behavior remains platform-owned except explicitly extracted pure codecs such as BAS Battery Level Status bitfield decoding; readiness, subscriptions, CoreBluetooth/Bluedroid behavior, lifecycle, and public error mapping stay in platform adapters.
- Generated public protobuf reconstruction remains platform-owned for training-session public models; shared KMP may parse selected fields and plan neutral reconstruction slots, but Android generated `TrainingSession.Pb*` classes and Swift `Data_Pb*` public model classes stay out of `commonMain`.
- Public facade error translation, filesystem capability gates, BLE/PSFTP transport execution, protobuf builder/parser ownership where not explicitly migrated, cancellation mechanics, timeout scheduling, progress emission text, network/zip/filesystem side effects, reconnect/wait execution, and host-state restoration remain adapter-owned unless a later slice proves a pure deterministic contract with Android, iOS, and common tests.

## Packaging And Public Contract Ownership

- Swift Package Manager and watchOS are fallback-only on a clean checkout. They may consume shared KMP only when `PolarBleSdkShared.xcframework` exists at the conditional local binary target path or a release manifest uses a real remote `binaryTarget(url:checksum:)` artifact with watchOS slices and validation.
- CocoaPods and Xcode iOS are the validated Apple shared-consumption surfaces for this phase. `PolarBleSdk.podspec` links `PolarBleSdkShared`, runs `build_kmp_ios_framework.sh`, and defines `POLAR_KMP_SHARED_REQUIRED`; `Package.swift` intentionally does not define that condition for clean SwiftPM/watchOS fallback.
- CI and release policy remain artifact-only. No Maven, CocoaPods, SwiftPM, JitPack, GitHub Pages, or external package host publication is claimed without a separate secrets/release-policy plan.

## Current Validation Snapshot

- Passed in this final audit slice: `git diff --check`, `ruby scripts/kmp_non_gradle_checks.rb`, `swift package describe`, `pod install --project-directory=sources/iOS/ios-communications`, `pod lib lint PolarBleSdk.podspec --allow-warnings`, and focused XCTest `PolarDataUtilsTest/testPolarBleSdkIosTargetRequiresLinkedSharedFramework`.
- Required Android policy validation for this closeout slice is `./gradlew :library:testSdkDebugUnitTest --tests 'com.polar.sdk.api.model.utils.GoldenVectorMigrationPolicyTest' --no-daemon --warning-mode all` from `sources/Android/android-communications`.
- The latest broad `xcodebuild test -workspace sources/iOS/ios-communications/iOSCommunications.xcworkspace -scheme iOSCommunications -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5'` run reached test execution but failed on existing stale golden-vector or policy expectation assertions outside this packaging guard slice: `PpgDataTest.testPpgGoldenVectorsMatchIOSCommunicationsBehavior`, REST empty/malformed decoding tests, `PolarBackupManagerTest.testRestoreBackup`, `PolarDataUtilsTest.testTriggerRuntimePolicyVectorIsPinnedBeforeRuntimeMigration`, `PolarDeviceRestApiServiceTests.testReceivesRestApiEventWhenUncompressed`, and `PolarTrainingSessionUtilsTests.testPayloadParserPolicyVectorIsPinnedBeforeByteLevelParserMigration`. These failures must be resolved before claiming a green broad iOS gate.

## Regression Guards

- Keep `GoldenVectorMigrationPolicyTest.kt` and `scripts/kmp_non_gradle_checks.rb` mirrored when adding final-boundary guard terms.
- Keep `POLAR_KMP_SHARED_REQUIRED` scoped to CocoaPods/Xcode iOS surfaces until SwiftPM/watchOS shared XCFramework consumption has a real artifact and build validation path.
- Keep every platform-owned boundary named above present in `KmpCoverageInventory.md`, `KmpFullCoverageTddBacklog.md`, `KmpSharedArtifactConsumption.md`, and this audit before moving more runtime delegation into shared KMP.
