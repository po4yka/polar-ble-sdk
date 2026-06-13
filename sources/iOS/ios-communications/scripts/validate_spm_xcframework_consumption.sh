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
PACKAGE_DESCRIPTION=$(mktemp "${TMPDIR:-/tmp}/polar-spm-describe.XXXXXX")
PACKAGE_DUMP=$(mktemp "${TMPDIR:-/tmp}/polar-spm-dump.XXXXXX")
trap 'rm -f "$PACKAGE_DESCRIPTION" "$PACKAGE_DUMP"' EXIT

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
swift package describe > "$PACKAGE_DESCRIPTION"
swift package dump-package > "$PACKAGE_DUMP"

if ! grep -q "PolarBleSdkShared" "$PACKAGE_DESCRIPTION"; then
    echo "swift package describe did not include PolarBleSdkShared" >&2
    exit 1
fi

ruby -rjson -e '
package = JSON.parse(File.read(ARGV.fetch(0)))
target = package.fetch("targets").find { |candidate| candidate.fetch("name") == "PolarBleSdkShared" }
unless target && target.fetch("type") == "binary"
  abort("Package.swift did not resolve PolarBleSdkShared as a binaryTarget")
end
' "$PACKAGE_DUMP"

if [ "$RUN_PLATFORM_BUILDS" = "1" ]; then
    DERIVED_DATA=$(mktemp -d "${TMPDIR:-/tmp}/polar-spm-xcodebuild.XXXXXX")
    trap 'rm -f "$PACKAGE_DESCRIPTION" "$PACKAGE_DUMP"; rm -rf "$DERIVED_DATA"' EXIT
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
    rm -f "$WATCHOS_LOG"
fi

echo "SwiftPM PolarBleSdkShared binaryTarget validation passed: $XCFRAMEWORK_PATH"
