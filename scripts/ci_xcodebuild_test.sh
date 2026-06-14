#!/bin/sh
set -eu

if [ "$#" -ne 4 ]; then
    echo "Usage: $(basename "$0") <xcodeproj> <scheme> <destination> <resultBundlePath>"
    exit 2
fi

XCODE_PROJECT="$1"
SCHEME="$2"
DESTINATION="$3"
RESULT_BUNDLE="$4"
ATTEMPTS="${CI_XCODEBUILD_ATTEMPTS:-2}"
attempt=1

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

while [ "$attempt" -le "$ATTEMPTS" ]; do
    rm -rf "$RESULT_BUNDLE"
    if xcodebuild test -project "$XCODE_PROJECT" -scheme "$SCHEME" -destination "$DESTINATION" -resultBundlePath "$RESULT_BUNDLE"; then
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
