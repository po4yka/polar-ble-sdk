# CI/CD

This repository uses GitHub Actions for pull-request validation, nightly validation, and artifact-only release builds. CI mirrors the local validation commands in `KmpValidationCommands.md` so migration slices remain test-first and platform behavior stays characterized before production code moves to KMP.

## Workflows

| Workflow | Trigger | Purpose |
|---|---|---|
| `PR Checks` | Pull requests and pushes to `master`, except product-doc-only and issue-template-only changes | Runs repository policy checks, Android unit tests, shared KMP checks, SwiftPM validation, and iOS XCTest. |
| `Nightly Validation` | Daily schedule and manual dispatch | Runs the same validation without path skipping and uploads diagnostic artifacts. |
| `Release Artifacts` | Manual dispatch and version tags | Builds Android AARs, validates Android shared Gradle module metadata in a temporary local repository, validates Swift Package surfaces, builds `PolarBleSdkShared.xcframework.zip`, computes its checksum, uploads workflow artifacts, and optionally promotes signed assets to a draft GitHub Release through the protected `release-publication` environment. |

## Pull Request Gates

The repository policy job runs whitespace checks, `actionlint` for `.github/workflows/*.yml`, Kotlin lint check-mode through `:lintCheck`, the Kotlin `:repo-tools:kmpNonGradleChecks` policy mirror, the `:repo-tools:verifyApiDocsGenerationPolicy` documentation-generation policy mirror, and generated API documentation cleanliness checks. CI installs actionlint, SwiftLint, and XcodeGen through checked-in composite actions that download pinned upstream release assets and verify SHA-256 checksums before adding binaries to `PATH`; workflows must not use `bash <(curl ...)` installers or unpinned Homebrew installs for these tools. The Android job runs the full `:library:testSdkDebugUnitTest` gate from `sources/Android/android-communications`. The shared KMP jobs run JVM/common, Android, metadata, and iOS framework compile/link checks. The iOS job runs `:repo-tools:iosXcodeValidationProbe`, `:repo-tools:validateGeneratedXcodeProject`, `swift package describe`, SwiftLint check-mode through `.swiftlint.yml`, a generic iOS `xcodebuild` package build, and the fast `iOSCommunications` XCTest shard through `scripts/ci_xcodebuild_test.sh ... fast` against `iOSCommunications.xcodeproj`.

Product documentation under `documentation/products/` and issue-template-only changes are allowed to skip PR checks. KMP documentation, validation scripts, Gradle files, Android sources, iOS sources, workflows, and `testdata` changes must run the relevant checks.

## Artifact Policy

CI/release remains artifact-only for package distribution and package-registry credentials remain absent from build jobs. Release automation does not publish to Maven, Swift Package registries, GitHub Pages, or any external package host. No Maven or SwiftPM registry publication is claimed by CI. Release artifacts are uploaded to the workflow run with limited retention; a separate protected job may promote those artifacts to a draft GitHub Release with checksums, detached signatures, and a release manifest.

`ReleasePublicationPolicy.md` defines the supported publication step as staged GitHub Release asset promotion from a green artifact workflow run, guarded by a protected `release-publication` environment, manual approval, explicit signing secrets, checksum generation, dry-run validation, and rollback instructions. The `github-release-assets` job runs only through `workflow_dispatch` with a `release_tag`, defaults to dry-run unless `publish_to_github_release` is true, and can promote artifacts from the current run or an explicitly supplied `artifact_run_id`.

The Android release artifact set has an Android internal project dependency during repository builds, then a local release pair for file consumers: `polar-ble-sdk.aar` and `polar-ble-sdk-shared.aar`. `scripts/verify_android_example_aar_consumption.sh` proves the example can consume that AAR pair, and `scripts/verify_android_shared_maven_metadata.sh` validates shared local Maven metadata in a build-local repository only. The Apple release artifact set uploads `PolarBleSdkShared.xcframework`, `PolarBleSdkShared.xcframework.zip`, and `PolarBleSdkShared.xcframework.zip.checksum`. Swift Package Manager is the supported Apple package path; release consumers use the uploaded zip through a remote `binaryTarget(url:checksum:)`, while clean checkouts continue to build through Swift fallback until a binary target URL/checksum is supplied.

Generated API documentation under `docs/polar-sdk-android` and `docs/polar-sdk-ios` is generated output and must stay untracked. The canonical generator is the Gradle task `:repo-tools:generateApiDocs`, which owns Android Dokka, iOS DocC `docbuild`, static-hosting transformation, and iOS hosting-base rewrites. Android Dokka suppresses low-level `androidcommunications` and `com.polar.sdk.impl` packages, and Swift implementation code under `Sources/PolarBleSdk/sdk/impl` is guarded against top-level `public` or `open` symbols outside generated protobuf files. `scripts/generate_api_docs.sh` remains a thin compatibility entrypoint that delegates to that Gradle task. PR and nightly CI fail if generated platform docs are checked into the repository. Nightly CI runs `:repo-tools:packageGeneratedApiDocs` on macOS and uploads one compressed `polar-generated-api-docs.tar.gz` artifact instead of committing or uploading thousands of generated files individually.

## Local Equivalents

Run these commands before merging CI-sensitive changes:

```bash
git diff --check
actionlint .github/workflows/*.yml
cd sources/Android/android-communications
./gradlew :repo-tools:kmpNonGradleChecks :repo-tools:verifyReleasePackagingPolicy :repo-tools:verifyApiDocsGenerationPolicy :repo-tools:iosXcodeValidationProbe :repo-tools:validateGeneratedXcodeProject --no-daemon --warning-mode all
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

`scripts/verify_android_example_aar_consumption.sh` is the canonical Android example/demo gate. It generates `polar-ble-sdk.aar` and `polar-ble-sdk-shared.aar` from the current checkout, copies them into the example local artifact directory, and verifies both `examples/example-android/polar-sensor-data-collector` and `demos/Android-Demos/PolarSDK-ECG-HR-Demo`. The copied AARs are generated validation artifacts and are not checked in.

```bash
swift package describe
xcodebuild -scheme PolarBleSdk -destination "generic/platform=iOS" build
sources/iOS/ios-communications/scripts/package_kmp_xcframework.sh --configuration Debug
sources/iOS/ios-communications/scripts/validate_spm_xcframework_consumption.sh --configuration Debug --skip-platform-builds
sh scripts/ci_xcodebuild_test.sh sources/iOS/ios-communications/iOSCommunications.xcodeproj iOSCommunications 'platform=iOS Simulator,name=iPhone 17,OS=latest' /tmp/polar-ios-local.xcresult fast
```

## Lint And Formatting Baseline

The checked-in `.editorconfig` sets LF line endings, UTF-8, final newlines, four-space Kotlin/Swift/Gradle indentation, two-space YAML/JSON/TOML indentation, and no prose `max_line_length` so Markdown and text paragraphs remain soft-wrapped by editors rather than hard-wrapped in source. Generated API docs and generated Swift protobuf sources are excluded from editor-driven newline and trailing-whitespace normalization.

SwiftLint and ktlint are CI gates only in narrow check-mode scopes. Kotlin lint check-mode is `./gradlew :lintCheck --no-daemon --warning-mode all` from `sources/Android/android-communications`, backed by the ktlint Gradle plugin and currently scoped to `:repo-tools` Kotlin sources. SwiftLint check-mode is `swiftlint lint --strict --config .swiftlint.yml`, backed by `only_rules` and explicit `included` paths for the REST API facade file plus selected modern implementation utility files, including command, file, REST facade, stream, and base runtime planners. Generated API docs, generated Swift protobuf sources, SwiftPM checkouts, Android BLE legacy sources, shared KMP tests, examples, and build outputs remain excluded until each scope has its own low-noise config. The pinned CI tool installers are `.github/actions/setup-actionlint`, `.github/actions/setup-swiftlint`, and `.github/actions/setup-xcodegen`; update the version, asset URL, SHA-256 checksum, and `:repo-tools:kmpNonGradleChecks` required terms together when bumping one of these tools.

## Failure Triage

Treat `:repo-tools:kmpNonGradleChecks` failures as repository contract failures, not cosmetic lint. Treat Android or iOS characterization failures as migration evidence until the current behavior is understood and the matching golden vector or platform test is updated. Treat missing simulator or Xcode discovery failures as CI infrastructure issues only when `:repo-tools:iosXcodeValidationProbe` classifies them that way.

Use nightly artifacts for failures that do not reproduce locally. Android reports include Gradle test reports and problem reports. iOS reports include the `.xcresult` bundle. The default iOS XCTest shard is `fast`, which skips the long firmware workflow class that has historically caused simulator timeout retries; use `full` for complete local coverage and `firmware` to isolate that class when changing firmware workflows.
