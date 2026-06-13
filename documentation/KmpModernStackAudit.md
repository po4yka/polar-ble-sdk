# KMP Modern Stack Audit

This audit is the current closeout checklist for the modern KMP stack after the Swift Package Manager and Kotlin tooling migration. It is evidence-backed by repository guards, shared/common policy tests, platform characterization tests, packaging probes, and artifact wiring that exist in this checkout.

## Fully Migrated Shared KMP Ownership

- Golden-vector governance is active: `GoldenVectorMigrationPolicyTest.kt` and `:repo-tools:kmpNonGradleChecks` validate schema fields, fixture README coverage, consumer test references, shared common-test references, platform-owned row wording, fake-transport policy wording, shared production portability, generated protobuf boundaries, SwiftPM artifact boundaries, and stale shared-policy wording.
- Shared common production owns the deterministic parser/model/codec/policy families previously documented for the KMP closeout, while BLE/session/GATT host behavior remains platform-owned, Generated public protobuf reconstruction remains platform-owned, and public facade error translation, filesystem capability gates, transport execution, timeout scheduling, cancellation mechanics, progress emission, and side-effectful firmware/backup execution remain platform-owned.
- Android production consumes shared KMP through `implementation project(':shared')` in `sources/Android/android-communications/library/build.gradle`; Android local AAR distribution uses the documented two-AAR compatibility model with `polar-ble-sdk.aar` plus `polar-ble-sdk-shared.aar`.
- Apple production consumption is Swift Package Manager first. `Package.swift` can consume `PolarBleSdkShared` through a remote `binaryTarget(url:checksum:)` when `POLAR_BLE_SDK_SHARED_BINARY_URL` and `POLAR_BLE_SDK_SHARED_BINARY_CHECKSUM` are supplied, through the generated local `PolarBleSdkShared.xcframework` during repository validation, or through the Swift fallback when no binary artifact is present.
- CI and release policy remain artifact-only. No Maven or SwiftPM registry publication is claimed, required secrets are intentionally absent, and release automation uploads workflow artifacts including `PolarBleSdkShared.xcframework.zip` plus its checksum.

## Artifact Matrix

| Surface | Current state | Validation | Rollback |
|---|---|---|---|
| Android repository build | Android internal project dependency through `implementation project(':shared')`. | Gradle Android and shared tests from `documentation/KmpValidationCommands.md`. | Remove the shared call from the Android adapter and keep `:shared` as an unused project until the slice is reverted. |
| Android local release | Two AARs are required: `polar-ble-sdk.aar` and `polar-ble-sdk-shared.aar`. | `scripts/verify_android_example_aar_consumption.sh`. | Remove `polar-ble-sdk-shared.aar` only after the facade AAR no longer references shared classes or external metadata supplies the dependency. |
| Android shared Maven metadata | Shared local Maven metadata validation only, written under `shared/build/local-maven-validation`. | `scripts/verify_android_shared_maven_metadata.sh`. | Delete the local validation repository; no external package has been published. |
| SwiftPM iOS/watchOS | Supported through remote `PolarBleSdkShared.xcframework` binary target for release consumption, local generated binary target for repository validation, and Swift fallback on clean checkouts without a binary artifact. | `swift package describe`, `package_kmp_xcframework.sh --zip-output`, `swift package compute-checksum`, and `validate_spm_xcframework_consumption.sh`. | Remove the binary target environment or generated XCFramework so `Package.swift` returns to source-only fallback behavior. |
| Xcode project iOS | The project is no longer CocoaPods-integrated; dependencies are SwiftPM products and shared KMP is built by the existing KMP framework build phase for iOS targets. | `:repo-tools:iosXcodeValidationProbe` and the iOS XCTest gate through `scripts/ci_xcodebuild_test.sh` with the `.xcodeproj`. | Remove bridge calls or framework search paths only after Swift fallbacks cover the behavior. |
| CI/release | CI/release remains artifact-only. No Maven or SwiftPM registry publication is claimed, and required secrets are intentionally absent. | `:repo-tools:verifyReleasePackagingPolicy`. | Remove uploaded workflow artifacts; no external package rollback is needed. |

## Current Validation Snapshot

- Required local validation for this migration is `git diff --check`, `./gradlew :repo-tools:kmpNonGradleChecks :repo-tools:verifyReleasePackagingPolicy :repo-tools:iosXcodeValidationProbe --no-daemon --warning-mode all`, focused `GoldenVectorMigrationPolicyTest`, `swift package describe`, `package_kmp_xcframework.sh --zip-output`, and `validate_spm_xcframework_consumption.sh`.
- The broad iOS XCTest gate must be treated as current only when `scripts/ci_xcodebuild_test.sh sources/iOS/ios-communications/iOSCommunications.xcodeproj iOSCommunications 'platform=iOS Simulator,name=iPhone 17,OS=latest' <result-bundle>` passes in the current checkout.

## Regression Guards

- Keep `GoldenVectorMigrationPolicyTest.kt` and `:repo-tools:kmpNonGradleChecks` aligned when adding packaging-boundary, validation, or final-state guard terms.
- Keep SwiftPM remote binary-target release consumption tied to a real `PolarBleSdkShared.xcframework.zip` checksum; do not claim registry publication or external host publication from CI workflow artifact upload alone.
- Keep every platform-owned boundary named in the KMP docs present before moving more runtime delegation into shared KMP.
