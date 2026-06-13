#!/bin/sh
set -eu

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
IOS_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
REPO_ROOT=$(cd "$IOS_ROOT/../../.." && pwd)
GRADLE_ROOT="$REPO_ROOT/sources/Android/android-communications"
CONFIGURATION=Release
DRY_RUN=0
OUTPUT_DIR="$IOS_ROOT/Generated/PolarBleSdkSharedXCFramework"
ZIP_OUTPUT=""

while [ "$#" -gt 0 ]; do
    case "$1" in
        --configuration)
            CONFIGURATION=${2:?Missing value for --configuration}
            shift 2
            ;;
        --output)
            OUTPUT_DIR=${2:?Missing value for --output}
            shift 2
            ;;
        --zip-output)
            ZIP_OUTPUT=${2:?Missing value for --zip-output}
            shift 2
            ;;
        --dry-run)
            DRY_RUN=1
            shift
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 2
            ;;
    esac
done

TASK_CONFIGURATION=$(printf "%s" "$CONFIGURATION" | awk '{print toupper(substr($0,1,1)) substr($0,2)}')
FRAMEWORK_CONFIGURATION=$(printf "%s" "$CONFIGURATION" | awk '{print tolower($0)}')
XCFRAMEWORK_OUTPUT="$OUTPUT_DIR/PolarBleSdkShared.xcframework"
IOS_DEVICE_FRAMEWORK="$GRADLE_ROOT/shared/build/bin/iosArm64/${FRAMEWORK_CONFIGURATION}Framework/PolarBleSdkShared.framework"
IOS_SIM_ARM64_FRAMEWORK="$GRADLE_ROOT/shared/build/bin/iosSimulatorArm64/${FRAMEWORK_CONFIGURATION}Framework/PolarBleSdkShared.framework"
IOS_SIM_X64_FRAMEWORK="$GRADLE_ROOT/shared/build/bin/iosX64/${FRAMEWORK_CONFIGURATION}Framework/PolarBleSdkShared.framework"
WATCH_DEVICE_FRAMEWORK="$GRADLE_ROOT/shared/build/bin/watchosArm64/${FRAMEWORK_CONFIGURATION}Framework/PolarBleSdkShared.framework"
WATCH_SIM_ARM64_FRAMEWORK="$GRADLE_ROOT/shared/build/bin/watchosSimulatorArm64/${FRAMEWORK_CONFIGURATION}Framework/PolarBleSdkShared.framework"
WATCH_SIM_X64_FRAMEWORK="$GRADLE_ROOT/shared/build/bin/watchosX64/${FRAMEWORK_CONFIGURATION}Framework/PolarBleSdkShared.framework"
BUILD_DIR="$OUTPUT_DIR/.build"
IOS_SIM_UNIVERSAL="$BUILD_DIR/iossimulator/PolarBleSdkShared.framework"
WATCH_SIM_UNIVERSAL="$BUILD_DIR/watchsimulator/PolarBleSdkShared.framework"

GRADLE_TASKS="
:shared:link${TASK_CONFIGURATION}FrameworkIosArm64
:shared:link${TASK_CONFIGURATION}FrameworkIosSimulatorArm64
:shared:link${TASK_CONFIGURATION}FrameworkIosX64
:shared:link${TASK_CONFIGURATION}FrameworkWatchosArm64
:shared:link${TASK_CONFIGURATION}FrameworkWatchosSimulatorArm64
:shared:link${TASK_CONFIGURATION}FrameworkWatchosX64
"

echo "PolarBleSdkShared XCFramework packaging plan"
echo "configuration=$CONFIGURATION"
echo "output=$XCFRAMEWORK_OUTPUT"
for task in $GRADLE_TASKS; do
    echo "gradle_task=$task"
done
echo "ios_device_framework=$IOS_DEVICE_FRAMEWORK"
echo "ios_simulator_frameworks=$IOS_SIM_ARM64_FRAMEWORK,$IOS_SIM_X64_FRAMEWORK"
echo "watchos_device_framework=$WATCH_DEVICE_FRAMEWORK"
echo "watchos_simulator_frameworks=$WATCH_SIM_ARM64_FRAMEWORK,$WATCH_SIM_X64_FRAMEWORK"
if [ -n "$ZIP_OUTPUT" ]; then
    echo "zip_output=$ZIP_OUTPUT"
fi
if [ "$DRY_RUN" = "1" ]; then
    exit 0
fi

cd "$GRADLE_ROOT"
./gradlew $GRADLE_TASKS --no-daemon

for framework in "$IOS_DEVICE_FRAMEWORK" "$IOS_SIM_ARM64_FRAMEWORK" "$IOS_SIM_X64_FRAMEWORK" "$WATCH_DEVICE_FRAMEWORK" "$WATCH_SIM_ARM64_FRAMEWORK" "$WATCH_SIM_X64_FRAMEWORK"; do
    if [ ! -d "$framework" ]; then
        echo "Missing expected framework: $framework" >&2
        exit 1
    fi
done

rm -rf "$BUILD_DIR" "$XCFRAMEWORK_OUTPUT"
mkdir -p "$(dirname "$IOS_SIM_UNIVERSAL")" "$(dirname "$WATCH_SIM_UNIVERSAL")" "$OUTPUT_DIR"
cp -R "$IOS_SIM_ARM64_FRAMEWORK" "$IOS_SIM_UNIVERSAL"
cp -R "$WATCH_SIM_ARM64_FRAMEWORK" "$WATCH_SIM_UNIVERSAL"
lipo -create "$IOS_SIM_X64_FRAMEWORK/PolarBleSdkShared" "$IOS_SIM_ARM64_FRAMEWORK/PolarBleSdkShared" -output "$IOS_SIM_UNIVERSAL/PolarBleSdkShared"
lipo -create "$WATCH_SIM_X64_FRAMEWORK/PolarBleSdkShared" "$WATCH_SIM_ARM64_FRAMEWORK/PolarBleSdkShared" -output "$WATCH_SIM_UNIVERSAL/PolarBleSdkShared"

xcodebuild -create-xcframework \
    -framework "$IOS_DEVICE_FRAMEWORK" \
    -framework "$IOS_SIM_UNIVERSAL" \
    -framework "$WATCH_DEVICE_FRAMEWORK" \
    -framework "$WATCH_SIM_UNIVERSAL" \
    -output "$XCFRAMEWORK_OUTPUT"

if [ -n "$ZIP_OUTPUT" ]; then
    rm -f "$ZIP_OUTPUT"
    mkdir -p "$(dirname "$ZIP_OUTPUT")"
    (cd "$OUTPUT_DIR" && zip -qry "$ZIP_OUTPUT" "PolarBleSdkShared.xcframework")
    swift package compute-checksum "$ZIP_OUTPUT"
fi
