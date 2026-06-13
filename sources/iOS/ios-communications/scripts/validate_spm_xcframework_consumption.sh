#!/bin/sh
set -eu

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
IOS_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
REPO_ROOT=$(cd "$IOS_ROOT/../../.." && pwd)
CONFIGURATION=Debug
RUN_PLATFORM_BUILDS=1

while [ "$#" -gt 0 ]; do
    case "$1" in
        --configuration)
            CONFIGURATION=${2:?Missing value for --configuration}
            shift 2
            ;;
        --skip-platform-builds)
            RUN_PLATFORM_BUILDS=0
            shift
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 2
            ;;
    esac
done

XCFRAMEWORK_PATH="$IOS_ROOT/Generated/PolarBleSdkSharedXCFramework/PolarBleSdkShared.xcframework"
XCFRAMEWORK_OUTPUT_DIR="$IOS_ROOT/Generated/PolarBleSdkSharedXCFramework"
PACKAGE_FALLBACK_DESCRIPTION=$(mktemp "${TMPDIR:-/tmp}/polar-spm-fallback-describe.XXXXXX")
PACKAGE_FALLBACK_DUMP=$(mktemp "${TMPDIR:-/tmp}/polar-spm-fallback-dump.XXXXXX")
PACKAGE_BINARY_DESCRIPTION=$(mktemp "${TMPDIR:-/tmp}/polar-spm-binary-describe.XXXXXX")
PACKAGE_BINARY_DUMP=$(mktemp "${TMPDIR:-/tmp}/polar-spm-binary-dump.XXXXXX")
FALLBACK_SCRATCH=$(mktemp -d "${TMPDIR:-/tmp}/polar-spm-fallback-scratch.XXXXXX")
BINARY_SCRATCH=$(mktemp -d "${TMPDIR:-/tmp}/polar-spm-binary-scratch.XXXXXX")
DERIVED_DATA=""
WATCHOS_LOG=""
BACKUP_OUTPUT_DIR=""

cleanup() {
    rm -f "$PACKAGE_FALLBACK_DESCRIPTION" "$PACKAGE_FALLBACK_DUMP" "$PACKAGE_BINARY_DESCRIPTION" "$PACKAGE_BINARY_DUMP"
    rm -rf "$FALLBACK_SCRATCH" "$BINARY_SCRATCH"
    if [ -n "$DERIVED_DATA" ]; then
        rm -rf "$DERIVED_DATA"
    fi
    if [ -n "$WATCHOS_LOG" ]; then
        rm -f "$WATCHOS_LOG"
    fi
    if [ -n "$BACKUP_OUTPUT_DIR" ] && [ -d "$BACKUP_OUTPUT_DIR" ]; then
        rm -rf "$XCFRAMEWORK_OUTPUT_DIR"
        mv "$BACKUP_OUTPUT_DIR" "$XCFRAMEWORK_OUTPUT_DIR"
    elif [ -z "$BACKUP_OUTPUT_DIR" ]; then
        rm -rf "$XCFRAMEWORK_OUTPUT_DIR"
    fi
}
trap cleanup EXIT

if [ -e "$XCFRAMEWORK_OUTPUT_DIR" ]; then
    BACKUP_OUTPUT_DIR="$XCFRAMEWORK_OUTPUT_DIR.backup.$$"
    if [ -e "$BACKUP_OUTPUT_DIR" ]; then
        echo "Backup path already exists: $BACKUP_OUTPUT_DIR" >&2
        exit 1
    fi
    mv "$XCFRAMEWORK_OUTPUT_DIR" "$BACKUP_OUTPUT_DIR"
fi

cd "$REPO_ROOT"
swift package --scratch-path "$FALLBACK_SCRATCH" --manifest-cache none describe > "$PACKAGE_FALLBACK_DESCRIPTION"
swift package --scratch-path "$FALLBACK_SCRATCH" --manifest-cache none dump-package > "$PACKAGE_FALLBACK_DUMP"

if grep -q "PolarBleSdkShared" "$PACKAGE_FALLBACK_DESCRIPTION"; then
    echo "fallback-mode swift package describe unexpectedly included PolarBleSdkShared" >&2
    exit 1
fi

ruby -rjson -e '
package = JSON.parse(File.read(ARGV.fetch(0)))
target_names = package.fetch("targets").map { |target| target.fetch("name") }
abort("fallback-mode Package.swift unexpectedly resolved PolarBleSdkShared") if target_names.include?("PolarBleSdkShared")
' "$PACKAGE_FALLBACK_DUMP"

"$SCRIPT_DIR/package_kmp_xcframework.sh" --configuration "$CONFIGURATION"

if [ ! -d "$XCFRAMEWORK_PATH" ]; then
    echo "Missing generated XCFramework: $XCFRAMEWORK_PATH" >&2
    exit 1
fi

INFO_PLIST="$XCFRAMEWORK_PATH/Info.plist"
plutil -convert json -o - "$INFO_PLIST" | ruby -rjson -e '
info = JSON.parse(STDIN.read)
libraries = info.fetch("AvailableLibraries")
expected = [
  ["ios", nil],
  ["ios", "simulator"],
  ["watchos", nil],
  ["watchos", "simulator"]
]
missing = expected.reject do |platform, variant|
  libraries.any? do |library|
    library["SupportedPlatform"] == platform && library["SupportedPlatformVariant"] == variant
  end
end
abort("PolarBleSdkShared.xcframework is missing #{missing.map { |platform, variant| [platform, variant].compact.join("-") }.join(", ")} support") unless missing.empty?
'

cd "$REPO_ROOT"
swift package --scratch-path "$BINARY_SCRATCH" --manifest-cache none describe > "$PACKAGE_BINARY_DESCRIPTION"
swift package --scratch-path "$BINARY_SCRATCH" --manifest-cache none dump-package > "$PACKAGE_BINARY_DUMP"

if ! grep -q "PolarBleSdkShared" "$PACKAGE_BINARY_DESCRIPTION"; then
    echo "swift package describe did not include PolarBleSdkShared" >&2
    exit 1
fi

ruby -rjson -e '
package = JSON.parse(File.read(ARGV.fetch(0)))
target = package.fetch("targets").find { |candidate| candidate.fetch("name") == "PolarBleSdkShared" }
unless target && target.fetch("type") == "binary"
  abort("Package.swift did not resolve PolarBleSdkShared as a binaryTarget")
end
' "$PACKAGE_BINARY_DUMP"

if [ "$RUN_PLATFORM_BUILDS" = "1" ]; then
    DERIVED_DATA=$(mktemp -d "${TMPDIR:-/tmp}/polar-spm-xcodebuild.XXXXXX")
    xcodebuild -scheme PolarBleSdk -destination "generic/platform=iOS" -derivedDataPath "$DERIVED_DATA/ios" build
    WATCHOS_LOG=$(mktemp "${TMPDIR:-/tmp}/polar-spm-watchos.XXXXXX")
    if xcodebuild -scheme PolarBleSdk -destination "generic/platform=watchOS" -derivedDataPath "$DERIVED_DATA/watchos" build > "$WATCHOS_LOG" 2>&1; then
        cat "$WATCHOS_LOG"
    elif grep -q "Unable to find a destination matching the provided destination specifier" "$WATCHOS_LOG" && grep -q "watchOS .* is not installed" "$WATCHOS_LOG"; then
        cat "$WATCHOS_LOG"
        echo "watchOS SwiftPM package build skipped because no eligible local watchOS destination is installed; watchOS device and simulator XCFramework slices were verified."
    else
        cat "$WATCHOS_LOG"
        exit 1
    fi
fi

echo "SwiftPM fallback and PolarBleSdkShared binaryTarget validation passed: $XCFRAMEWORK_PATH"
