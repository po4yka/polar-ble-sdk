# KMP Validation Commands

Use these commands when preparing or reviewing KMP migration slices. Run the narrowest command that proves the touched behavior, then run the broader command for the module before merging the slice.

Batch several file, test, vector, and documentation edits before invoking Gradle validation so coverage work does not stall on repeated manual app prompts. Prefer the library and shared-module gates below, and do not run broad app or example Gradle tasks unless the slice actually changes app/example surfaces.

## Android

Run Android commands from `sources/Android/android-communications`.

```bash
./gradlew :library:testSdkDebugUnitTest --tests 'com.polar.sdk.api.model.utils.GoldenVectorPolicyTest' --no-daemon --warning-mode all
```

For parser, model, runtime, or facade slices, include the direct vector consumer test and the relevant shared common test. Before treating a broad Android batch as merge-ready, run:

```bash
./gradlew :library:testSdkDebugUnitTest --no-daemon --warning-mode all
```

## iOS

Swift Package Manager is the supported Apple package path. The Xcode project uses SwiftPM package products and no longer depends on CocoaPods support files.

Run the stable local Xcode infrastructure probe when iOS test wiring, schemes, SwiftPM dependencies, or validation documentation changes:

```bash
cd sources/Android/android-communications
./gradlew :repo-tools:iosXcodeValidationProbe --no-daemon --warning-mode all
```

The current expected result is `ios_xcode_validation_probe OK: project discovery is available and no known local XCTest infrastructure blockers were detected`. This command proves project discovery and classifies local XCTest infrastructure blockers; it does not replace the full XCTest execution gate.

Run the generated Xcode project validation when `Package.swift`, `project.yml`, Xcode schemes, build settings, package dependencies, or KMP script phases change:

```bash
cd sources/Android/android-communications
./gradlew :repo-tools:validateGeneratedXcodeProject --no-daemon --warning-mode all
```

This task delegates to `scripts/validate_generated_xcode_project.sh`, generates an XcodeGen candidate in a temporary directory, and verifies the package-first project contract without modifying the committed compatibility `.xcodeproj`. Run `scripts/validate_generated_xcode_project.sh --build` from the repository root before replacing the committed project with generated output.

Run the current fast simulator XCTest gate from the repository root:

```bash
sh scripts/ci_xcodebuild_test.sh sources/iOS/ios-communications/iOSCommunications.xcodeproj iOSCommunications 'platform=iOS Simulator,name=iPhone 17,OS=latest' /tmp/polar-ios-local.xcresult fast
```

Use XCTest failures from that command as shared ownership evidence: repository-root lookup failures are test harness defects, while expected/actual parser, facade, and model mismatches need contract triage before production code moves to KMP. The `fast` shard skips the long `PolarBleApiImplTests` firmware workflow class so PR and nightly gates do not retry for simulator timeouts in one slow class. Run the same wrapper with `full` before broad release validation, or with `firmware` when changing firmware workflow behavior:

```bash
sh scripts/ci_xcodebuild_test.sh sources/iOS/ios-communications/iOSCommunications.xcodeproj iOSCommunications 'platform=iOS Simulator,name=iPhone 17,OS=latest' /tmp/polar-ios-firmware.xcresult firmware
sh scripts/ci_xcodebuild_test.sh sources/iOS/ios-communications/iOSCommunications.xcodeproj iOSCommunications 'platform=iOS Simulator,name=iPhone 17,OS=latest' /tmp/polar-ios-full.xcresult full
```

## Shared Common

Run the shared common golden-vector helper smoke test from `sources/Android/android-communications`:

```bash
./gradlew :shared:jvmTest --no-daemon --warning-mode all
```

When shared target wiring changes across JVM, Android, and Apple targets, also run:

```bash
./gradlew :shared:compileAndroidMain :shared:compileAndroidHostTest :shared:compileKotlinIosX64 :shared:compileKotlinMetadata :shared:jvmTest --no-daemon --warning-mode all
```

When shared artifact consumption documentation or artifact shape changes, run:

```bash
./gradlew :shared:bundleAndroidMainAar :shared:linkDebugFrameworkIosX64 --no-daemon --warning-mode all
```

## Release Packaging

When Android release packaging or example local-AAR consumption changes, run from the repository root:

```bash
scripts/verify_android_example_aar_consumption.sh
scripts/verify_android_shared_maven_metadata.sh
```

When Apple release packaging changes, validate the SwiftPM manifest surface and generic iOS package build:

```bash
swift package describe
xcodebuild -scheme PolarBleSdk -destination "generic/platform=iOS" build
```

Validate packaging intent without building native frameworks with:

```bash
sources/iOS/ios-communications/scripts/package_kmp_xcframework.sh --dry-run --output /tmp/polar-spm-xcframework
```

Validate local-output artifact consumption before claiming SwiftPM iOS/watchOS shared consumption:

```bash
sources/iOS/ios-communications/scripts/package_kmp_xcframework.sh --configuration Debug
sources/iOS/ios-communications/scripts/validate_spm_xcframework_consumption.sh --configuration Debug
```

For release artifacts, run `package_kmp_xcframework.sh` with `--zip-output`, record the `swift package compute-checksum` value, and provide `POLAR_BLE_SDK_SHARED_BINARY_URL` plus `POLAR_BLE_SDK_SHARED_BINARY_CHECKSUM` for remote SwiftPM binary-target resolution. Do not claim SwiftPM shared consumption until the package manifest resolves with the binary target present and an iOS/watchOS SwiftPM build or equivalent package integration gate has run against that artifact.

When GitHub Release asset publication automation changes, run `:repo-tools:verifyReleasePackagingPolicy` and validate `scripts/prepare_github_release_assets.sh` against a temporary artifact directory containing `android-release-artifacts` and `ios-release-artifacts`. The protected workflow path must keep `release_tag`, optional `artifact_run_id`, default dry-run behavior, `publish_to_github_release`, `release-publication`, `SHA256SUMS`, detached signatures, and `release-manifest.json` aligned with `documentation/ReleasePublicationPolicy.md`.

## Documentation And Fixture Gates

`GoldenVectorPolicyTest` is the repository gate for golden-vector metadata, shared-ownership rationale fields, runtime `consumerTests`, and fixture README ownership notes. Run it whenever a shared-policy slice changes `testdata/golden-vectors` or KMP ownership documentation.

The minimum validation before merging a shared-policy slice is KMP common tests, existing Android tests, existing iOS tests or an equivalent documented Apple-platform command, reviewed golden vectors as API contracts, and no unrelated platform refactor. The current executable way to satisfy that minimum validation set is the focused `GoldenVectorPolicyTest`, the relevant shared common test, the relevant Android characterization test, and the iOS XCTest or documented SwiftPM/Xcode package gate for the touched Apple surface.

Before invoking Gradle repeatedly during coverage expansion, batch edits and run these repository checks:

```bash
git diff --check
cd sources/Android/android-communications
./gradlew :repo-tools:kmpNonGradleChecks :repo-tools:verifyReleasePackagingPolicy :repo-tools:validateGeneratedXcodeProject --no-daemon --warning-mode all
```

Run lint check-mode when workflows, Kotlin repo tooling, Swift facade/API sources, `.swiftlint.yml`, or Gradle lint wiring changes:

```bash
scripts/lint_check.sh
```

The lint gate is intentionally narrow. Kotlin lint check-mode is `:lintCheck`, which currently runs ktlint over `:repo-tools` Kotlin sources, and SwiftLint check-mode uses `.swiftlint.yml` with explicit `only_rules` plus `included` paths. Expand either scope only after the new files pass locally without requiring auto-format in CI.

`:repo-tools:kmpNonGradleChecks` is the Kotlin/Gradle batching mirror for the metadata-heavy repository contract. It guards SwiftPM packaging, Kotlin repo-tools entrypoints, CocoaPods removal, Ruby-free validation, and the active validation-documentation terms, including fixture README `.kt` artifact references, TDD strategy golden-vector example wording, fake-transport runtime matrix coverage, stale future-fake-transport wording, portability allowlist terms, and the Xcode discovery command `xcodebuild -list -project sources/iOS/ios-communications/iOSCommunications.xcodeproj`. Plain `swift build` is intentionally not a validation gate because SwiftPM builds for the macOS host by default while this package is an iOS/watchOS package using Apple platform APIs; use generic iOS/watchOS `xcodebuild` package builds instead. `GoldenVectorPolicyTest` remains the deep Android-hosted policy gate for vector schema, consumer resolution, common-test coverage, fixture README references, fake-transport ledger coverage, public facade operation ledger coverage, generated-doc cleanliness, and shared `commonTest` portability.

## Generated API Documentation Ownership

Generated API documentation under `docs/polar-sdk-android` and `docs/polar-sdk-ios` is generated output, not checked-in source or hand-edited migration planning documentation. The canonical CI-native generator is the Gradle task in `:repo-tools`; the shell script remains a compatibility entrypoint that delegates to it.

The public API documentation surface is intentionally narrower than the full source tree. Android Dokka must suppress the low-level `androidcommunications` package family and `com.polar.sdk.impl`; Swift implementation code under `Sources/PolarBleSdk/sdk/impl` must not declare top-level `public` or `open` symbols outside generated protobuf files. Public API additions belong in the explicit facade/API source areas, not in implementation packages.

```bash
cd sources/Android/android-communications
./gradlew :repo-tools:generateApiDocs --no-daemon --warning-mode all
```

Run the static policy mirror whenever docs-generation wiring, workflows, Dokka, DocC, or generated-doc ownership changes:

```bash
cd sources/Android/android-communications
./gradlew :repo-tools:verifyApiDocsGenerationPolicy --no-daemon --warning-mode all
```

`scripts/generate_api_docs.sh` is still supported for local muscle memory, but it must only delegate to `:repo-tools:generateApiDocs`; it must not own `xcodebuild`, `docc`, Dokka, or HTML rewrite logic. Generated platform docs are ignored by `docs/.gitignore`; this command must print no tracked files:

```bash
git ls-files docs/polar-sdk-android docs/polar-sdk-ios
```

Nightly CI runs the Gradle packaging task on macOS and verifies `git ls-files docs/polar-sdk-android docs/polar-sdk-ios` is empty, then uploads one compressed `polar-generated-api-docs.tar.gz` artifact from `:repo-tools:packageGeneratedApiDocs` instead of uploading the full generated directory trees. If public APIs change, regenerate the relevant API docs as part of release readiness and review the generated artifact separately from behavior ownership changes.

## Generated Protobuf Ownership

The Android SDK `.proto` files under `sources/Android/android-communications/library/src/sdk/proto` are checked-in schema source, not generated output. Swift `*.pb.swift` files under `sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/impl/protobuf` are checked-in generated output and must keep their `DO NOT EDIT`, `swift-format-ignore-file`, `swiftlint:disable all`, and SwiftProtobuf generator header markers.

Use the canonical generator when protobuf schema changes need Swift regeneration:

```bash
scripts/generate_swift_protobuf.sh
```

The generator reads the Android protobuf version catalog, verifies `protoc-gen-swift` against `Package.swift`, writes Swift output into the iOS protobuf implementation directory, and keeps Android generated protobuf output in Gradle build directories. Run `:repo-tools:verifyApiDocsGenerationPolicy` after protobuf or generated-doc ownership changes because it also checks these protobuf ownership markers.

## Hardware And Device Smoke Boundary

Physical BLE hardware validation is smoke coverage for adapter wiring, radio availability, and real-device compatibility only; it must not replace deterministic golden-vector tests, shared `commonTest` policy tests, Android facade characterization, or iOS XCTest characterization before shared ownership. If a slice requires a manual hardware run, record the tested device/firmware, feature path, and result in the slice notes, then keep the deterministic commands above as the merge evidence for parser, model, runtime, and public facade behavior.
