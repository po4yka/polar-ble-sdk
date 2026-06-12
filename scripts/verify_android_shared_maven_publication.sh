#!/bin/sh
set -eu

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
ANDROID_ROOT="$REPO_ROOT/sources/Android/android-communications"
TEMP_REPO=${POLAR_SHARED_PUBLICATION_REPO:-}
CLEAN_TEMP_REPO=0

if [ -z "$TEMP_REPO" ]; then
    TEMP_REPO=$(mktemp -d "${TMPDIR:-/tmp}/polar-shared-maven.XXXXXX")
    CLEAN_TEMP_REPO=1
fi

cleanup() {
    if [ "$CLEAN_TEMP_REPO" -eq 1 ]; then
        rm -rf "$TEMP_REPO"
    fi
}
trap cleanup EXIT

cd "$ANDROID_ROOT"
./gradlew :shared:publishAllPublicationsToPolarSharedLocalRepository -PpolarSharedPublicationRepository="$TEMP_REPO" --no-daemon --warning-mode all

SHARED_MODULE_DIR="$TEMP_REPO/com/github/polarofficial/polar-ble-sdk-shared"
ANDROID_MODULE_DIR="$TEMP_REPO/com/github/polarofficial/polar-ble-sdk-shared-android"

if [ ! -d "$SHARED_MODULE_DIR" ]; then
    echo "Missing root shared module publication at $SHARED_MODULE_DIR" >&2
    exit 1
fi

if [ ! -d "$ANDROID_MODULE_DIR" ]; then
    echo "Missing Android shared module publication at $ANDROID_MODULE_DIR" >&2
    exit 1
fi

if ! find "$SHARED_MODULE_DIR" -name '*.module' -type f | grep -q .; then
    echo "Missing Gradle module metadata for polar-ble-sdk-shared" >&2
    exit 1
fi

if ! find "$ANDROID_MODULE_DIR" -name '*.aar' -type f | grep -q .; then
    echo "Missing Android AAR artifact for polar-ble-sdk-shared-android" >&2
    exit 1
fi

echo "Shared Maven publication OK: $TEMP_REPO"
