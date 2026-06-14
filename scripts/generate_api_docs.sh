#!/bin/sh
set -eu

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_ROOT="$REPO_ROOT/sources/Android/android-communications"

cd "$ANDROID_ROOT"
./gradlew :repo-tools:generateApiDocs --no-daemon --warning-mode all
