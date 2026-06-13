# CI/CD

This repository uses GitHub Actions for pull-request validation, nightly validation, and artifact-only release builds. CI intentionally mirrors the local validation commands in `KmpValidationCommands.md` so migration slices remain test-first and platform behavior stays characterized before production code moves to KMP.

## Workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `PR Checks` | Pull requests and pushes to `master`, except product-doc-only and issue-template-only changes | Runs repository policy checks, Android unit tests, shared KMP checks, and iOS XCTest. |
| `Nightly Validation` | Daily schedule and manual dispatch | Runs the same validation without path skipping and uploads diagnostic artifacts. |
| `Release Artifacts` | Manual dispatch and version tags | Builds Android AAR, shared KMP Android/iOS artifacts, validates Android shared Gradle module metadata in a temporary local repository, validates Swift Package and podspec surfaces, and uploads artifacts without publishing. |

## Pull Request Gates

The repository policy job runs whitespace checks, the KMP non-Gradle policy mirror, generated API documentation cleanliness checks, and a guard that keeps CocoaPods generated artifacts untracked. The Android job runs the full `:library:testSdkDebugUnitTest` gate from `sources/Android/android-communications`. The shared KMP jobs run JVM/common, Android, metadata, and iOS framework compile/link checks. The iOS job installs CocoaPods, runs `scripts/ios_xcode_validation_probe.rb`, and executes the `iOSCommunications` XCTest scheme through `scripts/ci_xcodebuild_test.sh` on the configured simulator destination with one retry for simulator launch/teardown flakiness.

Product documentation under `documentation/products/` and issue-template-only changes are allowed to skip PR checks. KMP documentation, validation scripts, Gradle files, Android sources, iOS sources, workflows, and `testdata` changes must run the relevant checks.

## Artifact Policy

CI/release remains artifact-only. Release automation does not publish to Maven, CocoaPods, Swift Package registries, GitHub Pages, or any external package host. No Maven, CocoaPods, or SwiftPM publication is claimed by CI, and the required secrets are intentionally absent from release workflow policy until a separate release-policy document names the external destination, credentials, approval path, and rollback plan. Release artifacts are uploaded to the workflow run with limited retention and must be reviewed before any manual publication step outside CI.

The Android release artifact set has an Android internal project dependency during repository builds, then a local release pair for file consumers: `polar-ble-sdk.aar` and `polar-ble-sdk-shared.aar`. `scripts/verify_android_example_aar_consumption.sh` proves the example can consume that AAR pair, and `scripts/verify_android_shared_maven_metadata.sh` validates shared local Maven metadata validation in a build-local repository only. The Apple release artifact set uploads generated `PolarBleSdkShared.framework` outputs for `iosArm64`, `iosSimulatorArm64`, and `iosX64`; CocoaPods/Xcode consume the generated framework, while SwiftPM/watchOS fallback-only validation remains `swift package describe` unless a real `PolarBleSdkShared.xcframework` binary strategy is added. Rollback is to stop consuming the shared artifact at the platform adapter or local release packaging layer without changing public APIs.

Generated API documentation under `docs/polar-sdk-android` and `docs/polar-sdk-ios` remains checked-in release output. CI fails when those directories change outside an explicit release documentation regeneration change.

## Local Equivalents

Run these commands before merging CI-sensitive changes:

```bash
git diff --check
ruby scripts/kmp_non_gradle_checks.rb
```

```bash
cd sources/Android/android-communications
./gradlew :library:testSdkDebugUnitTest --no-daemon --warning-mode all
./gradlew :shared:jvmTest :shared:compileAndroidMain :shared:compileAndroidHostTest :shared:compileKotlinMetadata --no-daemon --warning-mode all
./gradlew :shared:compileKotlinIosX64 :shared:linkDebugFrameworkIosX64 --no-daemon --warning-mode all
```

```bash
scripts/verify_android_example_aar_consumption.sh
scripts/verify_android_shared_maven_publication.sh
```

```bash
ruby scripts/ios_xcode_validation_probe.rb
sh scripts/ci_xcodebuild_test.sh sources/iOS/ios-communications/iOSCommunications.xcworkspace iOSCommunications 'platform=iOS Simulator,name=iPhone 17,OS=latest' /tmp/polar-ios-local.xcresult
```

## Failure Triage

Treat `scripts/kmp_non_gradle_checks.rb` failures as repository contract failures, not cosmetic lint. Treat Android or iOS characterization failures as migration evidence until the current behavior is understood and the matching golden vector or platform test is updated. Treat missing simulator, CocoaPods, or workspace discovery failures as CI infrastructure issues only when `scripts/ios_xcode_validation_probe.rb` classifies them that way.

Use nightly artifacts for failures that do not reproduce locally. Android reports include Gradle test reports and problem reports. iOS reports include the `.xcresult` bundle.
