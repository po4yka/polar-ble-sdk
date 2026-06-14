#!/bin/sh
set -eu

repo_root="$(cd "$(dirname "$0")/.." && pwd)"

if ! command -v swiftlint >/dev/null 2>&1; then
    echo "swiftlint is required for iOS lint check-mode. Install SwiftLint 0.63.x or newer." >&2
    exit 2
fi

(
    cd "$repo_root/sources/Android/android-communications"
    ./gradlew :lintCheck --no-daemon --warning-mode all
)

(
    cd "$repo_root"
    swiftlint lint --strict --config .swiftlint.yml
)
