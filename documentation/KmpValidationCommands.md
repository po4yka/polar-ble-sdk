# KMP Validation Commands

Use these commands when preparing or reviewing KMP migration slices. Run the narrowest command that proves the touched behavior, then run the broader command for the module before merging the slice.

Batch several file, test, vector, and documentation edits before invoking Gradle validation so coverage work does not stall on repeated manual app prompts. Prefer the library and shared-module gates below; do not run broad app or example Gradle tasks unless the slice actually changes app/example surfaces.

`KmpTddStrategy.md` defines the minimum validation before merging a migration slice: KMP common tests, existing Android tests, existing iOS tests or an equivalent documented Apple-platform command, reviewed golden vectors as API contracts, and no unrelated platform refactor in the slice. Treat the commands below as the current executable way to satisfy that minimum validation set.

## Android

Run Android commands from `sources/Android/android-communications`.

```bash
./gradlew :library:testSdkDebugUnitTest --tests 'com.polar.sdk.api.model.utils.GoldenVectorMigrationPolicyTest' --no-daemon
```

Use the same task with additional `--tests` filters for touched Android characterization tests. For parser or model slices, include the direct vector consumer test and `GoldenVectorMigrationPolicyTest`. For runtime/facade slices, include the fake-transport or facade test named in the vector `consumerTests`.

Before treating a broad Android coverage-preparation batch as merge-ready, run the full SDK debug unit-test gate:

```bash
./gradlew :library:testSdkDebugUnitTest --no-daemon
```

## iOS

The iOS project is Xcode/CocoaPods based, not Swift Package based. Do not treat plain `swift build` on macOS as sufficient iOS validation. The local syntax baseline for iOS characterization coverage is to parse the whole iOS test tree:

```bash
swiftc -parse sources/iOS/ios-communications/Tests/**/*.swift
```

Run this after iOS golden-vector consumer edits so Swift syntax drift is caught before Xcode/CocoaPods validation. The current project-discovery probe `xcodebuild -list -project sources/iOS/ios-communications/iOSCommunications.xcodeproj` lists the `iOSCommunications`, `iOSCommunicationsTests`, `PolarBleSdk`, `PolarBleSdkWatchOs`, and `PolarBleSdkTests` targets plus the `iOSCommunications`, `PolarBleSdk`, and `PolarBleSdkWatchOs` schemes. CocoaPods support files are installed, `sources/iOS/ios-communications/Pods` is present, workspace discovery succeeds, and iOS simulator destinations are available. A full XCTest run is now an execution gate rather than an infrastructure probe; remaining failures are test or behavior failures that must be triaged as coverage evidence before migration.

Run the stable local Xcode infrastructure probe when iOS test wiring, schemes, CocoaPods setup, or validation documentation changes:

```bash
ruby scripts/ios_xcode_validation_probe.rb
```

The current expected result is `ios_xcode_validation_probe OK: project discovery is available and no known local XCTest infrastructure blockers were detected`. This command proves project discovery and classifies local XCTest infrastructure blockers; it does not replace the full XCTest execution gate.

When iOS production code consumes shared KMP, the Xcode and CocoaPods paths build a generated `PolarBleSdkShared.framework` through `sources/iOS/ios-communications/scripts/build_kmp_ios_framework.sh` before Swift compilation. The generated framework is copied under ignored `sources/iOS/ios-communications/Generated/PolarBleSdkShared/$(PLATFORM_NAME)`. Validate the shared artifact shape first with:

```bash
cd sources/Android/android-communications
./gradlew :shared:linkDebugFrameworkIosX64 :shared:compileKotlinIosX64 --no-daemon
```

After the probe passes, run the current simulator XCTest gate:

```bash
xcodebuild test -workspace sources/iOS/ios-communications/iOSCommunications.xcworkspace -scheme iOSCommunications -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5'
```

If a slice captures an `.xcresult`, use a fresh `-resultBundlePath` for each run or remove the previous bundle first; `xcodebuild` exits before running tests when the requested result bundle already exists.

Use XCTest failures from that command as migration evidence: repository-root lookup failures are test harness defects, while expected/actual parser, facade, and model mismatches need contract triage before production code moves to KMP.

## KMP Common

A minimal shared KMP module exists at `sources/Android/android-communications/shared`. It has JVM, Android, and Apple targets, while `commonTest` execution currently runs through the JVM target. Run the common golden-vector helper smoke test from `sources/Android/android-communications`:

```bash
./gradlew :shared:jvmTest --no-daemon
```

The first deferred Gradle batch after a broad non-Gradle coverage sweep should include `:shared:jvmTest` because it executes `GoldenVectorTestDataCommonTest.kt`, `TimeDateCommonPolicyTest.kt`, the common parser/model policies, PSFTP byte-codec policy, fake-transport contract policy, REST/file/backup/offline-trigger/firmware/PSFTP runtime policies, D2H stream policy, generic stream duplicate-completion policy, `RemainingRuntimeWorkflowPlanningCommonTest.kt`, and codec-ownership policies in one shared gate. Common-style runtime prototypes that still live under the Android library test source set remain Android-hosted until the shared module owns the corresponding runtime abstractions and fake transports.

When shared target wiring changes, also run the narrow target compile gate:

```bash
./gradlew :shared:compileAndroidMain :shared:compileAndroidHostTest :shared:compileKotlinIosX64 :shared:compileKotlinMetadata :shared:jvmTest --no-daemon
```

When shared artifact consumption documentation or artifact shape changes, run the artifact smoke gate:

```bash
./gradlew :shared:bundleAndroidMainAar :shared:linkDebugFrameworkIosX64 --no-daemon
```

When Android release packaging or example local-AAR consumption changes, run the example AAR gate from the repository root:

```bash
scripts/verify_android_example_aar_consumption.sh
```

The Android packaging model is a two-AAR compatibility model for direct file consumers plus Gradle module metadata for Maven-style consumers. Validate the shared metadata publication only against a temporary local repository; this does not publish externally and keeps the artifact-only release policy intact:

```bash
scripts/verify_android_shared_maven_publication.sh
```

When iOS release packaging changes, validate the SwiftPM manifest surface and the CocoaPods shared-framework path from the repository root:

```bash
swift package describe
pod install --project-directory=sources/iOS/ios-communications
pod lib lint PolarBleSdk.podspec --allow-warnings
```

`swift package describe` proves only that the SwiftPM manifest remains valid. On a clean checkout, SwiftPM iOS and SwiftPM/watchOS do not link shared KMP; they use the Swift fallback code behind `#if canImport(PolarBleSdkShared)`. The explicit SwiftPM/watchOS shared-consumption path is `PolarBleSdkShared.xcframework` through the conditional `Package.swift` `binaryTarget`, which is active only after the XCFramework exists locally or after a release manifest points to a remote URL/checksum artifact. Validate packaging intent without building native frameworks with:

```bash
sources/iOS/ios-communications/scripts/package_kmp_xcframework.sh --dry-run --output /tmp/polar-spm-xcframework
```

Validate the local-output artifact path before claiming SwiftPM/watchOS shared consumption with:

```bash
sources/iOS/ios-communications/scripts/package_kmp_xcframework.sh --configuration Debug
swift package describe
```

The release-ready local validation gate is:

```bash
sources/iOS/ios-communications/scripts/validate_spm_xcframework_consumption.sh --configuration Debug
```

That script builds the local `PolarBleSdkShared.xcframework`, verifies the iOS device, iOS simulator, watchOS device, and watchOS simulator libraries, runs `swift package describe`, asserts the conditional `PolarBleSdkShared` binary target is present, runs a SwiftPM package build through `xcodebuild` for generic iOS, and runs the generic watchOS package build when the local Xcode installation exposes an eligible watchOS destination. If watchOS package build destinations are unavailable locally, the script keeps the watchOS claim limited to verified XCFramework device/simulator slices and prints the skip reason. For release artifacts, run `package_kmp_xcframework.sh` with `--zip-output`, record the printed `swift package compute-checksum` value, and use a remote `binaryTarget(url:checksum:)` entry that points at the zipped `PolarBleSdkShared.xcframework`; the local generated artifact path remains only for repository-local validation. Do not claim SwiftPM/watchOS shared consumption until the package manifest resolves with the binary target present and an iOS/watchOS SwiftPM build or equivalent package integration gate has run against that artifact. CocoaPods and the Xcode workspace remain supported through `PolarBleSdkShared.framework` built by `sources/iOS/ios-communications/scripts/build_kmp_ios_framework.sh`.

## Documentation And Fixture Gates

`GoldenVectorMigrationPolicyTest` is the repository gate for golden-vector metadata, migration-rationale fields, runtime `consumerTests`, fixture README ownership notes, and coverage-doc test references. Run it whenever a migration slice changes `testdata/golden-vectors` or KMP migration documentation.

Before invoking Gradle repeatedly during coverage expansion, batch edits and run these non-Gradle checks from the repository root:

```bash
git diff --check
ruby scripts/kmp_non_gradle_checks.rb
```

`scripts/kmp_non_gradle_checks.rb` is the local batching mirror for the metadata-heavy part of `GoldenVectorMigrationPolicyTest`: it parses every vector JSON file, verifies schema-required fields/platform keys/`consumerTests` platforms, enforces stable vector IDs, lowercase snake_case `case` values, known top-level fields, lowercase even-length `*Hex` strings, fixture README directory coverage, common-exclusion migration rationale, runtime-planning metadata and consumer completeness, non-empty consumers, Android/iOS/commonPrototype test resolution, consumer references to the vector ID, filename, exact vector directory, or owning readiness manifest, common-owned vector references from shared commonTest sources, non-orphan vector path references from tests or migration docs, stale shared-runtime wording rejection, extracted policy-required terms in shared/common runtime tests, extracted policy-required doc strings for active current-state branches, README and KMP documentation test-artifact references, missing/unassessed coverage-inventory row rejection, weak partial-gate rejection, completed checklist evidence rows, fake-transport runtime matrix ledger coverage, public facade operation ledger coverage, shared commonTest portability allowlist, Android minSdk/tagless-version/CocoaPods/generated-doc guardrails, behavior-free shared module and target shape, shared artifact consumption docs, and validation/TDD docs aligned with the required schema terms. To inspect raw portability matches manually, run `rg -n "digitToInt|toBooleanStrict|uppercase\(|lowercase\(|replaceFirstChar|ifEmpty|UL|UInt|UByte|ULong|java\.|android\.|com\.google" sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest`; it should print only reviewed allowlisted lines such as Android-owned fixture path strings, PMD status string literals, or `readUInt16Le`.

`GoldenVectorMigrationPolicyTest` also makes these non-Gradle checks executable inside the Android-hosted policy gate: every declared `consumerTests` entry must have a known platform, a non-empty test name, a resolvable Android/iOS/common test file, and a test body that references the guarded vector id, filename, exact vector directory, or an owning readiness manifest that names the vector path; every executable shared common test must be named in the KMP documentation; fixture README `.kt` artifact references must resolve to existing Kotlin tests; the TDD strategy golden-vector example must match the current schema contract; the fake-transport runtime matrix must have a complete evidence ledger for every row; the public facade operation ledger must name required operation families with resolvable Android, iOS, and shared evidence; vectors with executable shared common runtime consumers must not keep stale future-fake-transport wording; and shared `commonTest` source must avoid JVM/Android-only APIs except for the reviewed portability allowlist.

## Generated API Documentation Ownership

Generated API documentation under `docs/polar-sdk-android` and `docs/polar-sdk-ios` is release output, not hand-edited migration planning documentation. During pre-migration coverage work and each KMP migration slice, this command must print no files unless the slice explicitly regenerates release API docs from the owning generators:

```bash
git diff --name-only -- docs/polar-sdk-android docs/polar-sdk-ios
```

If public APIs change, regenerate the relevant API docs as part of release readiness and review the generated diff separately from behavior migration changes. Android API docs are Dokka output and iOS API docs are Jazzy output.

## Hardware And Device Smoke Boundary

Physical BLE hardware validation is smoke coverage for adapter wiring, radio availability, and real-device compatibility only; it must not replace deterministic golden-vector tests, shared `commonTest` policy tests, Android facade characterization, or iOS XCTest characterization before migration. If a slice requires a manual hardware run, record the tested device/firmware, feature path, and result in the slice notes, then keep the deterministic commands above as the merge evidence for parser, model, runtime, and public facade behavior.
