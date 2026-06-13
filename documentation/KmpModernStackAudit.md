# KMP Modern Stack Audit

This audit is the release-packaging policy snapshot for the current KMP compatibility phase.

## Artifact Matrix

| Surface | Current state | Validation | Rollback |
|---|---|---|---|
| Android repository build | Android internal project dependency through `implementation project(':shared')`. | Gradle Android and shared tests from `documentation/KmpValidationCommands.md`. | Remove the shared call from the Android adapter and keep `:shared` as an unused project until the slice is reverted. |
| Android local release | Two AARs are required: `polar-ble-sdk.aar` and `polar-ble-sdk-shared.aar`. | `scripts/verify_android_example_aar_consumption.sh`. | Remove `polar-ble-sdk-shared.aar` only after the facade AAR no longer references shared classes or external metadata supplies the dependency. |
| Android shared Maven metadata | Shared local Maven metadata validation only, written under `shared/build/local-maven-validation`. | `scripts/verify_android_shared_maven_metadata.sh`. | Delete the local validation repository; no external package has been published. |
| CocoaPods/Xcode iOS | Generated `PolarBleSdkShared.framework` is consumed by CocoaPods and Xcode build phases. | `pod lib lint PolarBleSdk.podspec --allow-warnings` and the iOS XCTest gate. | Remove bridge calls or framework search paths only after Swift fallbacks cover the behavior. |
| SwiftPM/watchOS | SwiftPM/watchOS fallback-only unless a real `PolarBleSdkShared.xcframework` exists and `Package.swift` is updated to consume it. | `swift package describe` validates manifest shape only. | Keep the existing Swift fallback implementation. |
| CI/release | CI/release remains artifact-only. No Maven, CocoaPods, or SwiftPM publication is claimed, and required secrets are intentionally absent. | `scripts/verify_release_packaging_policy.rb`. | Remove uploaded workflow artifacts; no external package rollback is needed. |

## External Publishing Gate

External publication remains disabled. Any future Maven, CocoaPods, or SwiftPM publication requires a release-policy document that names the package host, coordinates, required secrets, protected environment or approval path, validation command, and rollback path before a workflow may reference publication credentials.
