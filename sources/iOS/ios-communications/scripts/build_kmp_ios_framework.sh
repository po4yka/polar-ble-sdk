#!/bin/sh
set -eu

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
IOS_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
REPO_ROOT=$(cd "$IOS_ROOT/../../.." && pwd)
GRADLE_ROOT="$REPO_ROOT/sources/Android/android-communications"
PLATFORM_NAME=${PLATFORM_NAME:-iphonesimulator}
CURRENT_ARCH=${CURRENT_ARCH:-}
if [ -z "$CURRENT_ARCH" ] || [ "$CURRENT_ARCH" = "undefined_arch" ]; then
    CURRENT_ARCH=${ARCHS%% *}
fi
if [ -z "$CURRENT_ARCH" ] || [ "$CURRENT_ARCH" = "undefined_arch" ]; then
    case "$PLATFORM_NAME" in
        iphonesimulator)
            CURRENT_ARCH=x86_64
            ;;
        *)
            CURRENT_ARCH=$(uname -m)
            ;;
    esac
fi
CONFIGURATION=${CONFIGURATION:-Debug}

case "$PLATFORM_NAME:$CURRENT_ARCH" in
    iphoneos:*)
        KMP_TARGET=IosArm64
        FRAMEWORK_SOURCE="$GRADLE_ROOT/shared/build/bin/iosArm64/${CONFIGURATION}Framework/PolarBleSdkShared.framework"
        CACHE_ARCH="$CURRENT_ARCH"
        ;;
    iphonesimulator:*)
        KMP_TARGET=SimulatorUniversal
        FRAMEWORK_SOURCE="$GRADLE_ROOT/shared/build/bin/iosSimulatorArm64/${CONFIGURATION}Framework/PolarBleSdkShared.framework"
        FRAMEWORK_SOURCE_X64="$GRADLE_ROOT/shared/build/bin/iosX64/${CONFIGURATION}Framework/PolarBleSdkShared.framework"
        CACHE_ARCH=universal
        ;;
    *)
        echo "Skipping PolarBleSdkShared build for unsupported platform $PLATFORM_NAME/$CURRENT_ARCH"
        exit 0
        ;;
esac

TASK_CONFIGURATION=$(printf "%s" "$CONFIGURATION" | awk '{print toupper(substr($0,1,1)) substr($0,2)}')
TASK=":shared:link${TASK_CONFIGURATION}Framework${KMP_TARGET}"
FRAMEWORK_DESTINATION="$IOS_ROOT/Generated/PolarBleSdkShared/$PLATFORM_NAME"
FRAMEWORK_OUTPUT="$FRAMEWORK_DESTINATION/PolarBleSdkShared.framework"
FRAMEWORK_STAMP="$FRAMEWORK_OUTPUT/Info.plist"
ARCH_STAMP="$FRAMEWORK_DESTINATION/.built-$CONFIGURATION-$CACHE_ARCH"
LOCK_DIRECTORY="$FRAMEWORK_DESTINATION/.build.lock"

mkdir -p "$FRAMEWORK_DESTINATION"
while ! mkdir "$LOCK_DIRECTORY" 2>/dev/null; do
    sleep 1
done
trap 'rmdir "$LOCK_DIRECTORY"' EXIT

if [ -f "$FRAMEWORK_STAMP" ] && [ -f "$ARCH_STAMP" ] && [ "${POLAR_BLE_SDK_FORCE_KMP_FRAMEWORK_BUILD:-}" != "1" ]; then
    NEWER_SHARED_INPUT=$(find "$GRADLE_ROOT/shared/src" "$GRADLE_ROOT/shared/build.gradle" -newer "$FRAMEWORK_STAMP" -print -quit)
    if [ -z "$NEWER_SHARED_INPUT" ]; then
        if [ -n "${PODS_CONFIGURATION_BUILD_DIR:-}" ]; then
            PODS_FRAMEWORK_DESTINATION="$PODS_CONFIGURATION_BUILD_DIR/PolarBleSdkShared"
            rm -rf "$PODS_FRAMEWORK_DESTINATION"
            mkdir -p "$PODS_FRAMEWORK_DESTINATION"
            cp -R "$FRAMEWORK_OUTPUT" "$PODS_FRAMEWORK_DESTINATION/PolarBleSdkShared.framework"
        fi
        echo "PolarBleSdkShared framework is up to date at $FRAMEWORK_OUTPUT"
        exit 0
    fi
fi

cd "$GRADLE_ROOT"
if [ "$KMP_TARGET" = "SimulatorUniversal" ]; then
    ./gradlew ":shared:link${TASK_CONFIGURATION}FrameworkIosX64" ":shared:link${TASK_CONFIGURATION}FrameworkIosSimulatorArm64" --no-daemon
else
    ./gradlew "$TASK" --no-daemon
fi
rm -rf "$FRAMEWORK_OUTPUT"
cp -R "$FRAMEWORK_SOURCE" "$FRAMEWORK_OUTPUT"
if [ "$KMP_TARGET" = "SimulatorUniversal" ]; then
    lipo -create "$FRAMEWORK_SOURCE_X64/PolarBleSdkShared" "$FRAMEWORK_SOURCE/PolarBleSdkShared" -output "$FRAMEWORK_OUTPUT/PolarBleSdkShared"
fi
rm -f "$FRAMEWORK_DESTINATION"/.built-*
printf "%s\n" "$PLATFORM_NAME:$CONFIGURATION:$CACHE_ARCH" > "$ARCH_STAMP"

if [ -n "${PODS_CONFIGURATION_BUILD_DIR:-}" ]; then
    PODS_FRAMEWORK_DESTINATION="$PODS_CONFIGURATION_BUILD_DIR/PolarBleSdkShared"
    rm -rf "$PODS_FRAMEWORK_DESTINATION"
    mkdir -p "$PODS_FRAMEWORK_DESTINATION"
    cp -R "$FRAMEWORK_OUTPUT" "$PODS_FRAMEWORK_DESTINATION/PolarBleSdkShared.framework"
fi
