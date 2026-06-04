# CI/CD

This repository uses GitHub Actions for pull-request validation, nightly validation, and artifact-only release builds. CI intentionally mirrors the local validation commands in `KmpValidationCommands.md` so migration slices remain test-first and platform behavior stays characterized before production code moves to KMP.

## Workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `PR Checks` | Pull requests and pushes to `master`, except product-doc-only and issue-template-only changes | Runs repository policy checks, Android unit tests, shared KMP checks, and iOS XCTest. |
| `Nightly Validation` | Daily schedule and manual dispatch | Runs the same validation without path skipping and uploads diagnostic artifacts. |
| `Release Artifacts` | Manual dispatch and version tags | Builds Android AAR, shared KMP Android/iOS artifacts, validates Swift Package and podspec surfaces, and uploads artifacts without publishing. |

## Pull Request Gates

The repository policy job runs whitespace checks, the KMP non-Gradle policy mirror, generated API documentation cleanliness checks, and a guard that keeps CocoaPods generated artifacts untracked. The Android job runs the full `:library:testSdkDebugUnitTest` gate from `sources/Android/android-communications`. The shared KMP jobs run JVM/common, Android, metadata, and iOS framework compile/link checks. The iOS job installs CocoaPods, runs `scripts/ios_xcode_validation_probe.rb`, and executes the `iOSCommunications` XCTest scheme through `scripts/ci_xcodebuild_test.sh` on the configured simulator destination with one retry for simulator launch/teardown flakiness.

Product documentation under `documentation/products/` and issue-template-only changes are allowed to skip PR checks. KMP documentation, validation scripts, Gradle files, Android sources, iOS sources, workflows, and `testdata` changes must run the relevant checks.

## Artifact Policy

Release automation is artifact-only. It does not publish to Maven, CocoaPods, Swift Package registries, GitHub Pages, or any external package host. Release artifacts are uploaded to the workflow run with limited retention and must be reviewed before any manual publication step outside CI.

Generated API documentation under `docs/polar-sdk-android` and `docs/polar-sdk-ios` remains checked-in release output. CI fails when those directories change outside an explicit release documentation regeneration change.

## Local Equivalents

Run these commands before merging CI-sensitive changes:

```bash
git diff --check
ruby scripts/kmp_non_gradle_checks.rb
```

```bash
cd sources/Android/android-communications
ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew :library:testSdkDebugUnitTest --no-daemon --warning-mode all
ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew :shared:jvmTest :shared:compileAndroidMain :shared:compileAndroidHostTest :shared:compileKotlinMetadata --no-daemon --warning-mode all
ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew :shared:compileKotlinIosX64 :shared:linkDebugFrameworkIosX64 --no-daemon --warning-mode all
```

```bash
ruby scripts/ios_xcode_validation_probe.rb
sh scripts/ci_xcodebuild_test.sh sources/iOS/ios-communications/iOSCommunications.xcworkspace iOSCommunications 'platform=iOS Simulator,name=iPhone 17,OS=26.5' /tmp/polar-ios-local.xcresult
```

## Failure Triage

Treat `scripts/kmp_non_gradle_checks.rb` failures as repository contract failures, not cosmetic lint. Treat Android or iOS characterization failures as migration evidence until the current behavior is understood and the matching golden vector or platform test is updated. Treat missing simulator, CocoaPods, or workspace discovery failures as CI infrastructure issues only when `scripts/ios_xcode_validation_probe.rb` classifies them that way.

Use nightly artifacts for failures that do not reproduce locally. Android reports include Gradle test reports and problem reports. iOS reports include the `.xcresult` bundle.
