#!/bin/sh
set -eu

if [ "$#" -ne 4 ]; then
    echo "Usage: $(basename "$0") <project-or-workspace> <scheme> <destination> <resultBundlePath>"
    exit 2
fi

PROJECT_OR_WORKSPACE="$1"
SCHEME="$2"
DESTINATION="$3"
RESULT_BUNDLE="$4"
ATTEMPTS="${CI_XCODEBUILD_ATTEMPTS:-2}"
attempt=1

case "$PROJECT_OR_WORKSPACE" in
    *.xcodeproj)
        PROJECT_ARGUMENT="-project"
        ;;
    *.xcworkspace)
        PROJECT_ARGUMENT="-workspace"
        ;;
    *)
        echo "Expected .xcodeproj or .xcworkspace, got $PROJECT_OR_WORKSPACE" >&2
        exit 2
        ;;
esac

if [ ! -d "$PROJECT_OR_WORKSPACE" ]; then
    echo "Missing Xcode project/workspace: $PROJECT_OR_WORKSPACE" >&2
    exit 1
fi

while [ "$attempt" -le "$ATTEMPTS" ]; do
    rm -rf "$RESULT_BUNDLE"
    if xcodebuild test "$PROJECT_ARGUMENT" "$PROJECT_OR_WORKSPACE" -scheme "$SCHEME" -destination "$DESTINATION" -resultBundlePath "$RESULT_BUNDLE"; then
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
