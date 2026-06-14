# CI/CD

This repository uses GitHub Actions for pull-request validation, nightly validation, and artifact-only release builds. CI mirrors the local validation commands in `KmpValidationCommands.md` so migration slices remain test-first and platform behavior stays characterized before production code moves to KMP.

## Workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `PR Checks` | Pull requests and pushes to `master`, except product-doc-only and issue-template-only changes | Runs repository policy checks, Android unit tests, shared KMP checks, SwiftPM validation, and iOS XCTest. |
| `Nightly Validation` | Daily schedule and manual dispatch | Runs the same validation without path skipping and uploads diagnostic artifacts. |
| `Release Artifacts` | Manual dispatch and version tags | Builds Android AARs, validates Android shared Gradle module metadata in a temporary local repository, validates Swift Package surfaces, builds `PolarBleSdkShared.xcframework.zip`, computes its checksum, and uploads artifacts without publishing. |

## Pull Request Gates

The repository policy job runs whitespace checks, `actionlint` for `.github/workflows/*.yml`, the Kotlin `:repo-tools:kmpNonGradleChecks` policy mirror, and generated API documentation cleanliness checks. The Android job runs the full `:library:testSdkDebugUnitTest` gate from `sources/Android/android-communications`. The shared KMP jobs run JVM/common, Android, metadata, and iOS framework compile/link checks. The iOS job runs `:repo-tools:iosXcodeValidationProbe`, `swift package describe`, a generic iOS `xcodebuild` package build, and the `iOSCommunications` XCTest scheme through `scripts/ci_xcodebuild_test.sh` against `iOSCommunications.xcodeproj`.

Product documentation under `documentation/products/` and issue-template-only changes are allowed to skip PR checks. KMP documentation, validation scripts, Gradle files, Android sources, iOS sources, workflows, and `testdata` changes must run the relevant checks.

## Artifact Policy

CI/release remains artifact-only. Release automation does not publish to Maven, Swift Package registries, GitHub Pages, or any external package host. No Maven or SwiftPM registry publication is claimed by CI, and the required secrets are intentionally absent from release workflow policy until a separate release-policy document names the external destination, credentials, approval path, and rollback plan. Release artifacts are uploaded to the workflow run with limited retention and must be reviewed before any manual publication step outside CI.

`ReleasePublicationPolicy.md` defines the next safe publication step as staged GitHub Release asset promotion from a green artifact workflow run, guarded by a protected `release-publication` environment, manual approval, explicit signing secrets, checksum generation, dry-run validation, and rollback instructions. That policy is not active automation yet: `.github/workflows/release-artifacts.yml` must remain artifact-only until the protected environment exists and `:repo-tools:verifyReleasePackagingPolicy` is updated to enforce every publication guard.

The Android release artifact set has an Android internal project dependency during repository builds, then a local release pair for file consumers: `polar-ble-sdk.aar` and `polar-ble-sdk-shared.aar`. `scripts/verify_android_example_aar_consumption.sh` proves the example can consume that AAR pair, and `scripts/verify_android_shared_maven_metadata.sh` validates shared local Maven metadata in a build-local repository only. The Apple release artifact set uploads `PolarBleSdkShared.xcframework`, `PolarBleSdkShared.xcframework.zip`, and `PolarBleSdkShared.xcframework.zip.checksum`. Swift Package Manager is the supported Apple package path; release consumers use the uploaded zip through a remote `binaryTarget(url:checksum:)`, while clean checkouts continue to build through Swift fallback until a binary target URL/checksum is supplied.

Generated API documentation under `docs/polar-sdk-android` and `docs/polar-sdk-ios` remains checked-in release output. CI fails when those directories change outside an explicit release documentation regeneration change.

## Local Equivalents

Run these commands before merging CI-sensitive changes:

```bash
git diff --check
actionlint .github/workflows/*.yml
cd sources/Android/android-communications
./gradlew :repo-tools:kmpNonGradleChecks :repo-tools:verifyReleasePackagingPolicy :repo-tools:iosXcodeValidationProbe --no-daemon --warning-mode all
```

```bash
cd sources/Android/android-communications
./gradlew :library:testSdkDebugUnitTest --no-daemon --warning-mode all
./gradlew :shared:jvmTest :shared:compileAndroidMain :shared:compileAndroidHostTest :shared:compileKotlinMetadata --no-daemon --warning-mode all
./gradlew :shared:compileKotlinIosX64 :shared:linkDebugFrameworkIosX64 --no-daemon --warning-mode all
```

```bash
scripts/verify_android_example_aar_consumption.sh
scripts/verify_android_shared_maven_metadata.sh
```

```bash
swift package describe
xcodebuild -scheme PolarBleSdk -destination "generic/platform=iOS" build
sources/iOS/ios-communications/scripts/package_kmp_xcframework.sh --configuration Debug
sources/iOS/ios-communications/scripts/validate_spm_xcframework_consumption.sh --configuration Debug --skip-platform-builds
sh scripts/ci_xcodebuild_test.sh sources/iOS/ios-communications/iOSCommunications.xcodeproj iOSCommunications 'platform=iOS Simulator,name=iPhone 17,OS=latest' /tmp/polar-ios-local.xcresult
```

## Lint And Formatting Baseline

The checked-in `.editorconfig` sets LF line endings, UTF-8, final newlines, four-space Kotlin/Swift/Gradle indentation, two-space YAML/JSON/TOML indentation, and no prose `max_line_length` so Markdown and text paragraphs remain soft-wrapped by editors rather than hard-wrapped in source. Generated API docs and generated Swift protobuf sources are excluded from editor-driven newline and trailing-whitespace normalization.

SwiftLint and ktlint were evaluated for check-mode adoption. They are intentionally not CI gates yet because default rules produce broad existing noise across SwiftPM checkouts, generated protobuf Swift, historical Swift style, Android Kotlin sources, shared KMP tests, and Gradle scripts. Future SwiftLint or ktlint adoption should start with a small checked-in configuration that excludes generated outputs and historical examples, then prove check mode locally before adding CI enforcement.

## Failure Triage

Treat `:repo-tools:kmpNonGradleChecks` failures as repository contract failures, not cosmetic lint. Treat Android or iOS characterization failures as migration evidence until the current behavior is understood and the matching golden vector or platform test is updated. Treat missing simulator or Xcode discovery failures as CI infrastructure issues only when `:repo-tools:iosXcodeValidationProbe` classifies them that way.

Use nightly artifacts for failures that do not reproduce locally. Android reports include Gradle test reports and problem reports. iOS reports include the `.xcresult` bundle.
