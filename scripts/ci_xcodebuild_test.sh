#!/bin/sh
set -eu

if [ "$#" -lt 4 ] || [ "$#" -gt 5 ]; then
    echo "Usage: $(basename "$0") <xcodeproj> <scheme> <destination> <resultBundlePath> [fast|full|firmware]"
    exit 2
fi

XCODE_PROJECT="$1"
SCHEME="$2"
DESTINATION="$3"
RESULT_BUNDLE="$4"
MODE="${5:-${CI_XCODEBUILD_TEST_MODE:-fast}}"
ATTEMPTS="${CI_XCODEBUILD_ATTEMPTS:-2}"
attempt=1
TEST_SELECTION_ARGS=""

case "$XCODE_PROJECT" in
    *.xcodeproj)
        ;;
    *)
        echo "Expected .xcodeproj, got $XCODE_PROJECT" >&2
        exit 2
        ;;
esac

if [ ! -d "$XCODE_PROJECT" ]; then
    echo "Missing Xcode project: $XCODE_PROJECT" >&2
    exit 1
fi

case "$MODE" in
    fast)
        TEST_SELECTION_ARGS="-skip-testing:PolarBleSdkTests/PolarBleApiImplTests"
        ;;
    full)
        TEST_SELECTION_ARGS=""
        ;;
    firmware)
        TEST_SELECTION_ARGS="-only-testing:PolarBleSdkTests/PolarBleApiImplTests"
        ;;
    *)
        echo "Unknown XCTest mode: $MODE" >&2
        echo "Expected one of: fast, full, firmware" >&2
        exit 2
        ;;
esac

echo "Running iOS XCTest mode '$MODE' for scheme '$SCHEME' on '$DESTINATION'"

while [ "$attempt" -le "$ATTEMPTS" ]; do
    rm -rf "$RESULT_BUNDLE"
    if xcodebuild test -project "$XCODE_PROJECT" -scheme "$SCHEME" -destination "$DESTINATION" $TEST_SELECTION_ARGS -resultBundlePath "$RESULT_BUNDLE"; then
        exit 0
    fi
    if [ "$attempt" -eq "$ATTEMPTS" ]; then
        exit 1
    fi
    echo "xcodebuild failed on attempt $attempt; retrying after simulator cleanup"
    xcrun simctl shutdown all || true
    sleep 10
    attempt=$((attempt + 1))
done
