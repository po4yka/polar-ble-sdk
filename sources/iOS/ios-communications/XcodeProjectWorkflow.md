# iOS Xcode Project Workflow

This checkout uses a generated/package-first workflow for source membership, dependency validation, and Xcode project modeling. `project.yml` is the XcodeGen source of truth for the generated project contract, while `iOSCommunications.xcodeproj` remains the committed compatibility harness for XCTest, local KMP framework builds, iOS/watchOS framework targets, and shared schemes until a separately validated replacement commit swaps the generated project into place.

## Current Project Responsibilities

- `Package.swift` at the repository root is the SwiftPM source of truth for the package product `PolarBleSdk`, the `SwiftProtobuf` and `ZIPFoundation` dependencies, the processed `Sources/iOSCommunications/Resources` resource directory, and the optional `PolarBleSdkShared` binary target.
- `project.yml` is the XcodeGen source of truth for the local Xcode harness contract: targets, schemes, SwiftPM product dependencies, KMP script phases, linker/search-path settings, strict `POLAR_KMP_SHARED_REQUIRED` enforcement, watchOS target differences, and XCTest resource wiring.
- `iOSCommunications.xcodeproj` owns five Xcode targets: `iOSCommunications`, `iOSCommunicationsTests`, `PolarBleSdk`, `PolarBleSdkWatchOs`, and `PolarBleSdkTests`.
- The shared schemes are `iOSCommunications`, `PolarBleSdk`, and `PolarBleSdkWatchOs`; `iOSCommunications` and `PolarBleSdk` both run `iOSCommunicationsTests` and `PolarBleSdkTests`, while `PolarBleSdkWatchOs` is build-only.
- `iOSCommunications` and `PolarBleSdk` both run the `Build PolarBleSdkShared KMP Framework` shell script phase before Swift compilation, using `scripts/build_kmp_ios_framework.sh` and the Android shared Gradle sources as declared inputs.
- The iOS framework targets link the local KMP framework from `Generated/PolarBleSdkShared/$(PLATFORM_NAME)` through `FRAMEWORK_SEARCH_PATHS` and `OTHER_LDFLAGS`.
- `PolarBleSdk` is the strict iOS public SDK target and must keep `OTHER_SWIFT_FLAGS = "$(inherited) -D POLAR_KMP_SHARED_REQUIRED"` for both Debug and Release.
- `iOSCommunications` and `PolarBleSdk` keep `ENABLE_USER_SCRIPT_SANDBOXING = NO` because the KMP build phase reaches outside the Xcode project directory into the Android shared module; this produces one documented Xcode infrastructure warning that `scripts/ci_xcodebuild_build.sh` allowlists.
- Debug framework builds keep `BUILD_LIBRARY_FOR_DISTRIBUTION = NO` so local and CI hygiene builds do not emit SwiftPM dependency library-evolution warnings; Release framework builds keep `BUILD_LIBRARY_FOR_DISTRIBUTION = YES` for distribution validation.
- `PolarBleSdkWatchOs` intentionally does not run the KMP script phase and currently keeps only `SwiftProtobuf` as a SwiftPM product dependency.
- `PolarBleSdkTests.xctestplan` and the `testdata` directory are test resources in the project test targets.
- CocoaPods is intentionally absent; do not restore `Podfile`, `Pods`, `.podspec`, or committed `.xcworkspace` behavior that depends on CocoaPods.

## Chosen Path

Use generated/package-first cleanup for the current safe slice. Keep `Package.swift` as the canonical public package manifest, keep `project.yml` as the generated Xcode harness manifest, and keep the committed `.xcodeproj` as the compatibility harness until the generated project passes replacement-level build and XCTest validation in the same commit that swaps it in.

XcodeGen is the selected project generator. `scripts/validate_generated_xcode_project.sh` generates a candidate project in a temporary directory and verifies the expected targets, schemes, SwiftPM products, KMP script phases, strict shared-KMP Swift flag, generated framework search path, and XCTest resource contract without modifying the committed `.xcodeproj`. Use `scripts/validate_generated_xcode_project.sh --build` for the stronger temporary repo-layout build check before replacing the committed project.

## Update Workflow

1. Prefer SwiftPM/package changes for source membership, package dependencies, and resources when they affect the public package build.
2. Edit `project.yml` first for Xcode-only responsibilities: target build settings, scheme membership, XCTest resources, KMP script phases, framework search paths, or Xcode-specific platform targets. Mirror changes into `iOSCommunications.xcodeproj` only while it remains the committed compatibility harness.
3. Keep `SwiftProtobuf` and `ZIPFoundation` as SwiftPM dependencies unless a separate approved task changes ZIPFoundation.
4. Keep `POLAR_KMP_SHARED_REQUIRED` on the `PolarBleSdk` iOS target for both Debug and Release.
5. Keep `scripts/build_kmp_ios_framework.sh` wired into both `iOSCommunications` and `PolarBleSdk` before Swift compilation.
6. Keep SwiftPM binary target validation in `Package.swift` and the generated XCFramework path `sources/iOS/ios-communications/Generated/PolarBleSdkSharedXCFramework/PolarBleSdkShared.xcframework`.
7. Do not hand-edit generated protobuf Swift files, generated documentation, or generated KMP framework outputs as part of project maintenance.
8. After any project or package maintenance, run the validation bundle in the section below before committing.

## Generated Project Gate

Run `scripts/validate_generated_xcode_project.sh` or `./gradlew :repo-tools:validateGeneratedXcodeProject --no-daemon --warning-mode all` after any project/package maintenance. Do not replace `iOSCommunications.xcodeproj` with generated output until a candidate generated from `project.yml` satisfies all of these checks in a temporary path and then passes the build/test validation bundle:

- The generated project lists the same targets, build configurations, and shared schemes as `xcodebuild -list -project sources/iOS/ios-communications/iOSCommunications.xcodeproj`.
- `SwiftProtobuf` and `ZIPFoundation` remain SwiftPM package products with the same target attachment boundaries.
- The generated iOS framework targets keep the KMP script phase names, script bodies, input paths, order before Swift source compilation, `Generated/PolarBleSdkShared/$(PLATFORM_NAME)` framework search paths, and `-framework PolarBleSdkShared` linker flags.
- `PolarBleSdk` keeps `POLAR_KMP_SHARED_REQUIRED` in both Debug and Release; watchOS does not gain that required flag unless a separate compatibility task changes the packaging boundary.
- `iOSCommunicationsTests` and `PolarBleSdkTests` keep their test resources and scheme membership, including the skipped `OfflineRecordingDataTest/testParseOfflineRecordingDataContainingOnlyHeader()` entry in the `iOSCommunications` scheme.
- The generated project passes the full validation bundle below before the committed project is replaced.

## Required Validation

Run these commands from the repository root unless a command specifies the Android Gradle wrapper directory:

```sh
git diff --check
swift package describe
xcodebuild -list -project sources/iOS/ios-communications/iOSCommunications.xcodeproj
scripts/validate_generated_xcode_project.sh
scripts/validate_generated_xcode_project.sh --build
scripts/ci_xcodebuild_build.sh sources/iOS/ios-communications/iOSCommunications.xcodeproj PolarBleSdk "generic/platform=iOS" Debug
scripts/ci_xcodebuild_test.sh sources/iOS/ios-communications/iOSCommunications.xcodeproj iOSCommunications 'platform=iOS Simulator,name=iPhone 17,OS=latest' /tmp/polar-ios-projectgen.xcresult fast
cd sources/Android/android-communications && ./gradlew :repo-tools:iosXcodeValidationProbe :repo-tools:validateGeneratedXcodeProject :repo-tools:kmpNonGradleChecks --no-daemon --warning-mode all
```

Run the focused `GoldenVectorPolicyTest` only when project workflow changes also alter KMP guardrail wording, packaging-boundary wording, or mirrored shared ownership policy terms.
