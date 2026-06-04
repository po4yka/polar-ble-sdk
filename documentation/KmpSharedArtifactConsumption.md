# KMP Shared Artifact Consumption

This document records how the `:shared` module is consumed as migration slices intentionally delegate Android or iOS behavior to shared KMP code. Android and iOS modules may depend on shared code only when a behavior slice adds failing platform characterization tests, common tests, and facade compatibility checks for the delegated behavior.

## Current Artifact Shape

The shared module lives at `sources/Android/android-communications/shared` and is included as Gradle project `:shared` from `sources/Android/android-communications/settings.gradle`. It exposes production common code for migrated Android slices, common golden-vector test loading, a JVM test target for executable `commonTest`, an AGP 9 Android KMP library target, and Apple targets `iosX64`, `iosArm64`, and `iosSimulatorArm64`.

## Android Consumption Contract

Android production code should consume shared code through a Gradle project dependency only in the migration slice that delegates behavior: `implementation project(':shared')` in `sources/Android/android-communications/library/build.gradle`. Before adding that dependency, the slice must prove the current Android behavior with existing or new Android characterization tests, add or reuse shared golden vectors, add the matching KMP common tests, and keep `GoldenVectorMigrationPolicyTest` green. The Android artifact smoke gate for target wiring is `ANDROID_HOME=/Users/po4yka/Library/Android/sdk ./gradlew :shared:bundleAndroidMainAar :shared:compileAndroidMain --no-daemon` from `sources/Android/android-communications`.

## iOS Consumption Contract

iOS production code consumes shared code through the generated static framework `PolarBleSdkShared.framework`, keeping the existing Swift `PolarBleSdk` and `iOSCommunications` public APIs as the current Swift facade compatibility boundary. `sources/iOS/ios-communications/scripts/build_kmp_ios_framework.sh` selects `iosArm64`, `iosX64`, or `iosSimulatorArm64` from the active Xcode platform and architecture, runs the matching Gradle framework link task, and copies the generated framework under ignored `sources/iOS/ios-communications/Generated/PolarBleSdkShared/$(PLATFORM_NAME)`. The Xcode project and CocoaPods podspec add that path to `FRAMEWORK_SEARCH_PATHS`, link `PolarBleSdkShared`, and run the script before Swift compilation for the iOS framework targets. Swift production code uses `#if canImport(PolarBleSdkShared)` so Swift Package Manager and watchOS builds remain source-compatible through the existing Swift fallback until a later release adds a checked-in or downloaded binary artifact packaging strategy. The first iOS production consumption slice delegates Device ID/UUID and selected time/date utilities through `PolarIosSharedBridge`; future slices should add the same XCTest, common-test, and facade-compatibility evidence before removing additional Swift logic. The Apple artifact smoke gate for target wiring is `ANDROID_HOME=/Users/po4yka/Library/Android/sdk ./gradlew :shared:linkDebugFrameworkIosX64 :shared:compileKotlinIosX64 --no-daemon` from `sources/Android/android-communications`, followed by the iOS XCTest command in `KmpValidationCommands.md`.

## Review Rules

Do not mix artifact wiring with unrelated parser or runtime migrations. A migration slice that adds Android or iOS consumption must name the delegated behavior family, the Android and iOS tests that prove facade compatibility, the common tests that fail without shared implementation, and the rollback path. Generated API docs under `docs/polar-sdk-android` and `docs/polar-sdk-ios` remain untouched unless public API documentation is intentionally regenerated for release readiness.
