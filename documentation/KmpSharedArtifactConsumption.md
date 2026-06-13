# KMP Shared Artifact Consumption

This document records how the `:shared` module is consumed after the Swift Package Manager migration. Android and iOS modules may depend on shared code only when a behavior slice adds failing platform characterization tests, common tests, and facade compatibility checks for the delegated behavior.

## Android Consumption Contract

Android production code consumes shared code through `implementation project(':shared')` in `sources/Android/android-communications/library/build.gradle`. The slice that delegates behavior must prove current Android behavior with characterization tests, shared golden vectors, matching KMP common tests, and a green `GoldenVectorMigrationPolicyTest`. Direct local AAR consumers use the two-AAR compatibility model: `polar-ble-sdk.aar` plus `polar-ble-sdk-shared.aar`. `scripts/verify_android_example_aar_consumption.sh` validates that file-consumption path, while `scripts/verify_android_shared_maven_metadata.sh` validates local-only shared KMP metadata without external publication.

## Apple Consumption Contract

Swift Package Manager is the supported Apple package path. `Package.swift` supports three modes: remote release consumption through `POLAR_BLE_SDK_SHARED_BINARY_URL` and `POLAR_BLE_SDK_SHARED_BINARY_CHECKSUM`, local repository validation through `sources/iOS/ios-communications/Generated/PolarBleSdkSharedXCFramework/PolarBleSdkShared.xcframework`, and clean-checkout Swift fallback when no binary artifact is present. `sources/iOS/ios-communications/scripts/package_kmp_xcframework.sh` builds iOS device, iOS simulator, watchOS device, and watchOS simulator framework slices, creates `PolarBleSdkShared.xcframework`, optionally writes `PolarBleSdkShared.xcframework.zip`, and prints the SwiftPM checksum. `sources/iOS/ios-communications/scripts/validate_spm_xcframework_consumption.sh` verifies the fallback mode, builds the local XCFramework, checks the binary target, and runs generic iOS/watchOS package build validation when local destinations permit it.

CocoaPods is no longer a supported distribution or validation surface. The former podspec, Podfile, CocoaPods CI install/lint steps, and CocoaPods Xcode project integration are removed. Existing Podfile consumers must migrate to Swift Package Manager.

## Release Packaging Matrix

| Consumer surface | Shared KMP consumption status | Verification |
|---|---|---|
| Android Gradle project inside this repository | Direct `implementation project(':shared')` from the SDK library. | `:library:testSdkDebugUnitTest`, `:shared:jvmTest`, and target compile gates in `KmpValidationCommands.md`. |
| Android example local AAR | Supported with `polar-ble-sdk.aar` plus `polar-ble-sdk-shared.aar`. | `scripts/verify_android_example_aar_consumption.sh`. |
| Android shared local Maven metadata | Validation-only metadata for `:shared` in `shared/build/local-maven-validation`; not an external publication. | `scripts/verify_android_shared_maven_metadata.sh`. |
| SwiftPM iOS | Supported through remote or local `PolarBleSdkShared.xcframework` binary target; clean checkout falls back to Swift source behavior. | `swift package describe`, and `validate_spm_xcframework_consumption.sh`. |
| SwiftPM/watchOS | Supported through the same `PolarBleSdkShared.xcframework` binary target when watchOS device and simulator slices are present; clean checkout falls back to Swift source behavior. | `package_kmp_xcframework.sh --zip-output`, `swift package compute-checksum`, and generic watchOS package build validation when available. |
| Xcode project iOS | Uses SwiftPM product dependencies plus the KMP framework build phase for local project validation. | `:repo-tools:iosXcodeValidationProbe` and `scripts/ci_xcodebuild_test.sh` against `iOSCommunications.xcodeproj`. |

## Rollback Paths

Every shared-module adoption step must keep a rollback path that can be applied without changing public SDK APIs. For Android utility/model/parser slices, rollback is to remove the shared call from the platform adapter and restore the characterized platform implementation while keeping golden vectors and common tests as regression evidence. For Android release packaging, rollback is to remove `polar-ble-sdk-shared.aar` only after the SDK AAR no longer references shared classes or after publication metadata supplies the dependency. For SwiftPM iOS/watchOS, rollback is to remove the remote binary target environment values or delete the local generated XCFramework so the manifest falls back to source-only Swift behavior.

## Publication Claims

CI/release remains artifact-only. `:repo-tools:verifyReleasePackagingPolicy` prevents the release workflow from claiming Maven or SwiftPM registry publication without policy evidence. No Maven or SwiftPM registry publication is claimed, and required secrets are intentionally absent from `.github/workflows/release-artifacts.yml`. Any future external publication must first add a release-policy document that names the package host, coordinates, required secrets, manual approval or protected environment, validation command, and rollback path; until then, release automation may upload only workflow artifacts.
