#!/bin/sh
set -eu

if [ "$#" -lt 3 ] || [ "$#" -gt 4 ]; then
    echo "Usage: $(basename "$0") <xcodeproj> <scheme> <destination> [configuration]" >&2
    exit 2
fi

XCODE_PROJECT="$1"
SCHEME="$2"
DESTINATION="$3"
CONFIGURATION="${4:-Debug}"
LOG_FILE=$(mktemp "${TMPDIR:-/tmp}/polar-xcodebuild-build.XXXXXX.log")
WARNINGS_FILE=$(mktemp "${TMPDIR:-/tmp}/polar-xcodebuild-warnings.XXXXXX")
UNEXPECTED_WARNINGS_FILE=$(mktemp "${TMPDIR:-/tmp}/polar-xcodebuild-unexpected-warnings.XXXXXX")

cleanup() {
    rm -f "$LOG_FILE" "$WARNINGS_FILE" "$UNEXPECTED_WARNINGS_FILE"
}
trap cleanup EXIT

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

if ! xcodebuild build -project "$XCODE_PROJECT" -scheme "$SCHEME" -configuration "$CONFIGURATION" -destination "$DESTINATION" CODE_SIGNING_ALLOWED=NO > "$LOG_FILE" 2>&1; then
    cat "$LOG_FILE"
    exit 1
fi

grep -n "warning:" "$LOG_FILE" > "$WARNINGS_FILE" || true
grep -v "tasks in 'Copy Headers' are delayed by unsandboxed script phases" "$WARNINGS_FILE" | grep -v "warning: Metadata extraction skipped. No AppIntents.framework dependency found." > "$UNEXPECTED_WARNINGS_FILE" || true

if [ -s "$UNEXPECTED_WARNINGS_FILE" ]; then
    echo "Unexpected xcodebuild warnings:" >&2
    cat "$UNEXPECTED_WARNINGS_FILE" >&2
    exit 1
fi

cat "$WARNINGS_FILE"
echo "xcodebuild warning hygiene passed for $SCHEME $CONFIGURATION $DESTINATION"
