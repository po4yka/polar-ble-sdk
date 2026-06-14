#!/bin/sh
set -eu

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_ROOT="$REPO_ROOT/sources/Android/android-communications"
IOS_PROJECT="$REPO_ROOT/sources/iOS/ios-communications/iOSCommunications.xcodeproj"
DERIVED_DATA="${API_DOCS_DERIVED_DATA:-/tmp/polar-api-docs}"
IOS_DOCS_ARCHIVE="$DERIVED_DATA/Build/Products/Debug-iphoneos/PolarBleSdk.doccarchive"
IOS_DOCS_OUTPUT="$REPO_ROOT/docs/polar-sdk-ios"
IOS_HOSTING_BASE_PATH="${API_DOCS_IOS_HOSTING_BASE_PATH:-/polar-ble-sdk/polar-sdk-ios}"

cd "$ANDROID_ROOT"
./gradlew :library:dokkaGeneratePublicationHtml --no-daemon --warning-mode all

rm -rf "$DERIVED_DATA"
xcodebuild docbuild -scheme PolarBleSdk -destination "generic/platform=iOS" -derivedDataPath "$DERIVED_DATA" -project "$IOS_PROJECT" CODE_SIGNING_ALLOWED=NO -quiet
xcrun docc process-archive transform-for-static-hosting "$IOS_DOCS_ARCHIVE" --hosting-base-path "$IOS_HOSTING_BASE_PATH" --output-path "$IOS_DOCS_OUTPUT"

find "$IOS_DOCS_OUTPUT" -name '*.html' -print0 | xargs -0 perl -0pi -e 's#var baseUrl = "/"#var baseUrl = "'$IOS_HOSTING_BASE_PATH'/"#g; s#(href|src)="/(?!/)#$1="'$IOS_HOSTING_BASE_PATH'/#g'
