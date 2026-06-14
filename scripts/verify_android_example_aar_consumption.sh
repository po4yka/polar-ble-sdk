#!/bin/sh
set -eu

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
ANDROID_ROOT="$REPO_ROOT/sources/Android/android-communications"
EXAMPLE_ROOT="$REPO_ROOT/examples/example-android/polar-sensor-data-collector"
ECG_HR_DEMO_ROOT="$REPO_ROOT/demos/Android-Demos/PolarSDK-ECG-HR-Demo"
EXAMPLE_AAR_DIR="$EXAMPLE_ROOT/polarBleSdk"
SDK_AAR="$ANDROID_ROOT/library/build/outputs/aar/library-sdk-release.aar"
EXAMPLE_SDK_AAR="$EXAMPLE_AAR_DIR/polar-ble-sdk.aar"
EXAMPLE_SHARED_AAR="$EXAMPLE_AAR_DIR/polar-ble-sdk-shared.aar"

cd "$ANDROID_ROOT"
./gradlew assembleSdkRelease :shared:bundleAndroidMainAar --no-daemon --warning-mode all

SHARED_AAR=$(find "$ANDROID_ROOT/shared/build" -name '*.aar' -type f | head -n 1)
if [ -z "$SHARED_AAR" ]; then
    echo "No shared Android AAR was produced under $ANDROID_ROOT/shared/build" >&2
    exit 1
fi

mkdir -p "$EXAMPLE_AAR_DIR"
cp "$SDK_AAR" "$EXAMPLE_SDK_AAR"
cp "$SHARED_AAR" "$EXAMPLE_SHARED_AAR"

if [ ! -s "$EXAMPLE_SDK_AAR" ] || [ ! -s "$EXAMPLE_SHARED_AAR" ]; then
    echo "Expected both polar-ble-sdk.aar and polar-ble-sdk-shared.aar for local Android release consumption" >&2
    exit 1
fi

if ! unzip -l "$EXAMPLE_SHARED_AAR" | grep -q 'classes.jar'; then
    echo "polar-ble-sdk-shared.aar does not contain classes.jar" >&2
    exit 1
fi

if ! grep -q "polar-ble-sdk-shared.aar" "$EXAMPLE_ROOT/app/build.gradle.kts"; then
    echo "Android example must declare the local shared AAR consumption path" >&2
    exit 1
fi

if ! grep -q "polar-ble-sdk-shared.aar" "$ECG_HR_DEMO_ROOT/app/build.gradle.kts"; then
    echo "Android ECG/HR demo must declare the local shared AAR consumption path" >&2
    exit 1
fi

cd "$EXAMPLE_ROOT"
./gradlew :app:checkDebugAarMetadata :app:checkDebugDuplicateClasses :app:mergeLibDexDebug -PlocalSdk=true --no-daemon --warning-mode all

cd "$ECG_HR_DEMO_ROOT"
./gradlew :app:checkDebugAarMetadata :app:checkDebugDuplicateClasses :app:mergeLibDexDebug -PlocalSdk=true --no-daemon --warning-mode all
