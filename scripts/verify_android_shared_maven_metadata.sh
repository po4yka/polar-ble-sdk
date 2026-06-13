#!/bin/sh
set -eu

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
ANDROID_ROOT="$REPO_ROOT/sources/Android/android-communications"
SHARED_ROOT="$ANDROID_ROOT/shared"
LOCAL_REPO="$SHARED_ROOT/build/local-maven-validation"
GROUP_PATH="com/github/polarofficial"
VERSION="0.0.0-local"

cd "$ANDROID_ROOT"
./gradlew :shared:publishAllPublicationsToLocalKmpReleaseValidationRepository --no-daemon --warning-mode all

require_file() {
    path="$1"
    if [ ! -s "$path" ]; then
        echo "Missing expected shared Maven validation artifact: $path" >&2
        exit 1
    fi
}

require_file "$LOCAL_REPO/$GROUP_PATH/shared/$VERSION/shared-$VERSION.module"
require_file "$LOCAL_REPO/$GROUP_PATH/shared/$VERSION/shared-$VERSION.pom"
require_file "$LOCAL_REPO/$GROUP_PATH/shared-android/$VERSION/shared-android-$VERSION.aar"
require_file "$LOCAL_REPO/$GROUP_PATH/shared-android/$VERSION/shared-android-$VERSION.module"
require_file "$LOCAL_REPO/$GROUP_PATH/shared-android/$VERSION/shared-android-$VERSION.pom"
require_file "$LOCAL_REPO/$GROUP_PATH/shared-iosx64/$VERSION/shared-iosx64-$VERSION.klib"
require_file "$LOCAL_REPO/$GROUP_PATH/shared-iosarm64/$VERSION/shared-iosarm64-$VERSION.klib"
require_file "$LOCAL_REPO/$GROUP_PATH/shared-iossimulatorarm64/$VERSION/shared-iossimulatorarm64-$VERSION.klib"

if ! grep -q "<artifactId>shared</artifactId>" "$LOCAL_REPO/$GROUP_PATH/shared/$VERSION/shared-$VERSION.pom"; then
    echo "Root shared POM does not describe artifactId shared" >&2
    exit 1
fi

if ! grep -q '"org.gradle.category"' "$LOCAL_REPO/$GROUP_PATH/shared-android/$VERSION/shared-android-$VERSION.module"; then
    echo "Android shared Gradle module metadata is missing variant attributes" >&2
    exit 1
fi

if grep -R "https://repo.maven.apache.org\\|oss.sonatype\\|maven.pkg.github.com\\|cocoapods.org\\|swiftpackageindex" "$LOCAL_REPO" >/dev/null; then
    echo "Shared local Maven validation output must not contain external publication endpoints" >&2
    exit 1
fi

echo "Shared local Maven metadata validation OK: $LOCAL_REPO"
