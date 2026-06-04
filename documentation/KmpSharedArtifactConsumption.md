# KMP Shared Artifact Consumption

This document records how the behavior-free `:shared` module will be consumed once a migration slice intentionally delegates Android or iOS behavior to shared KMP code. It is a pre-migration contract, not a production wiring change: Android and iOS modules must not depend on `:shared` until a behavior slice adds failing platform characterization tests, common tests, and facade compatibility checks for the delegated behavior.

## Current Artifact Shape

The shared module lives at `sources/Android/android-communications/shared` and is included as Gradle project `:shared` from `sources/Android/android-communications/settings.gradle`. It currently exposes a behavior-free `commonMain` marker, common golden-vector test loading, a JVM test target for executable `commonTest`, an AGP 9 Android KMP library target, and Apple targets `iosX64`, `iosArm64`, and `iosSimulatorArm64`.

## Android Consumption Contract

Android production code should consume shared code through a Gradle project dependency only in the migration slice that delegates behavior: `implementation project(':shared')` in `sources/Android/android-communications/library/build.gradle`. Before adding that dependency, the slice must prove the current Android behavior with existing or new Android characterization tests, add or reuse shared golden vectors, add the matching KMP common tests, and keep `GoldenVectorMigrationPolicyTest` green. The Android artifact smoke gate for target wiring is `ANDROID_HOME=/Users/po4yka/Library/Android/sdk ./gradlew :shared:bundleAndroidMainAar :shared:compileAndroidMain --no-daemon` from `sources/Android/android-communications`.

## iOS Consumption Contract

iOS production code should consume shared code through the generated static framework `PolarBleSdkShared.framework`, keeping the existing Swift `PolarBleSdk` and `iOSCommunications` public APIs as the compatibility boundary. A migration slice that adds the framework to the Xcode project or package must hide shared implementation details behind the current Swift facade, prove current iOS behavior with existing or new XCTest characterization, add or reuse shared golden vectors, and keep the KMP common tests green. The Apple artifact smoke gate for target wiring is `ANDROID_HOME=/Users/po4yka/Library/Android/sdk ./gradlew :shared:linkDebugFrameworkIosX64 :shared:compileKotlinIosX64 --no-daemon` from `sources/Android/android-communications`.

## Review Rules

Do not mix artifact wiring with unrelated parser or runtime migrations. A migration slice that adds Android or iOS consumption must name the delegated behavior family, the Android and iOS tests that prove facade compatibility, the common tests that fail without shared implementation, and the rollback path. Generated API docs under `docs/polar-sdk-android` and `docs/polar-sdk-ios` remain untouched unless public API documentation is intentionally regenerated for release readiness.
