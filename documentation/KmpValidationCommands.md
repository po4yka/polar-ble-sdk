# KMP Validation Commands

Use these commands when preparing or reviewing KMP migration slices. Run the narrowest command that proves the touched behavior, then run the broader command for the module before merging the slice.

Batch several file, test, vector, and documentation edits before invoking Gradle validation so coverage work does not stall on repeated manual app prompts. Prefer the library and shared-module gates below, and do not run broad app or example Gradle tasks unless the slice actually changes app/example surfaces.

## Android

Run Android commands from `sources/Android/android-communications`.

```bash
./gradlew :library:testSdkDebugUnitTest --tests 'com.polar.sdk.api.model.utils.GoldenVectorMigrationPolicyTest' --no-daemon --warning-mode all
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

Run the current simulator XCTest gate from the repository root:

```bash
sh scripts/ci_xcodebuild_test.sh sources/iOS/ios-communications/iOSCommunications.xcodeproj iOSCommunications 'platform=iOS Simulator,name=iPhone 17,OS=latest' /tmp/polar-ios-local.xcresult
```

Use XCTest failures from that command as migration evidence: repository-root lookup failures are test harness defects, while expected/actual parser, facade, and model mismatches need contract triage before production code moves to KMP.

## KMP Common

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

## Documentation And Fixture Gates

`GoldenVectorMigrationPolicyTest` is the repository gate for golden-vector metadata, migration-rationale fields, runtime `consumerTests`, fixture README ownership notes, and coverage-doc test references. Run it whenever a migration slice changes `testdata/golden-vectors` or KMP migration documentation.

`KmpTddStrategy.md` defines the minimum validation before merging a migration slice: KMP common tests, existing Android tests, existing iOS tests or an equivalent documented Apple-platform command, reviewed golden vectors as API contracts, and no unrelated platform refactor. The current executable way to satisfy that minimum validation set is the focused `GoldenVectorMigrationPolicyTest`, the relevant shared common test, the relevant Android characterization test, and the iOS XCTest or documented SwiftPM/Xcode package gate for the touched Apple surface.

Before invoking Gradle repeatedly during coverage expansion, batch edits and run these repository checks:

```bash
git diff --check
cd sources/Android/android-communications
./gradlew :repo-tools:kmpNonGradleChecks :repo-tools:verifyReleasePackagingPolicy --no-daemon --warning-mode all
```

`:repo-tools:kmpNonGradleChecks` is the Kotlin/Gradle batching mirror for the metadata-heavy repository contract. It guards SwiftPM packaging, Kotlin repo-tools entrypoints, CocoaPods removal, Ruby-free validation, and the active validation-documentation terms, including fixture README `.kt` artifact references, TDD strategy golden-vector example wording, fake-transport runtime matrix coverage, stale future-fake-transport wording, portability allowlist terms, and the Xcode discovery command `xcodebuild -list -project sources/iOS/ios-communications/iOSCommunications.xcodeproj`. Plain `swift build` is intentionally not a validation gate because SwiftPM builds for the macOS host by default while this package is an iOS/watchOS package using Apple platform APIs; use generic iOS/watchOS `xcodebuild` package builds instead. `GoldenVectorMigrationPolicyTest` remains the deep Android-hosted policy gate for vector schema, consumer resolution, common-test coverage, fixture README references, fake-transport ledger coverage, public facade operation ledger coverage, generated-doc cleanliness, and shared `commonTest` portability.

## Generated API Documentation Ownership

Generated API documentation under `docs/polar-sdk-android` and `docs/polar-sdk-ios` is release output, not hand-edited migration planning documentation. Regenerate both API documentation trees with the repository entrypoint:

```bash
scripts/generate_api_docs.sh
```

During migration slices, this command must print no files unless the slice explicitly regenerates release API docs from the owning generators:

```bash
git diff --name-only -- docs/polar-sdk-android docs/polar-sdk-ios
```

If public APIs change, regenerate the relevant API docs as part of release readiness and review the generated diff separately from behavior migration changes.

## Hardware And Device Smoke Boundary

Physical BLE hardware validation is smoke coverage for adapter wiring, radio availability, and real-device compatibility only; it must not replace deterministic golden-vector tests, shared `commonTest` policy tests, Android facade characterization, or iOS XCTest characterization before migration. If a slice requires a manual hardware run, record the tested device/firmware, feature path, and result in the slice notes, then keep the deterministic commands above as the merge evidence for parser, model, runtime, and public facade behavior.
