#!/bin/sh
set -eu

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
ios_root="$repo_root/sources/iOS/ios-communications"
spec="$ios_root/project.yml"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/polar-xcodegen.XXXXXX")"
build_candidate=0

case "${1:-}" in
    "")
        ;;
    --build)
        build_candidate=1
        ;;
    *)
        echo "Usage: $(basename "$0") [--build]" >&2
        exit 2
        ;;
esac

cleanup() {
    rm -rf "$tmp_dir"
}
trap cleanup EXIT

if ! command -v xcodegen >/dev/null 2>&1; then
    echo "xcodegen is required to validate the generated Xcode project model" >&2
    exit 2
fi

if [ "$build_candidate" -eq 1 ]; then
    candidate_root="$tmp_dir/repo"
    candidate_ios_root="$candidate_root/sources/iOS/ios-communications"
    mkdir -p "$candidate_root/sources/iOS" "$candidate_root/sources/Android"
    mkdir -p "$candidate_ios_root"
    cp "$spec" "$candidate_ios_root/project.yml"
    ln -s "$ios_root/Sources" "$candidate_ios_root/Sources"
    ln -s "$ios_root/Tests" "$candidate_ios_root/Tests"
    ln -s "$ios_root/scripts" "$candidate_ios_root/scripts"
    if [ -e "$ios_root/Generated" ]; then
        ln -s "$ios_root/Generated" "$candidate_ios_root/Generated"
    fi
    ln -s "$repo_root/sources/Android/android-communications" "$candidate_root/sources/Android/android-communications"
    xcodegen generate --spec "$candidate_ios_root/project.yml" --project "$candidate_ios_root" --project-root "$candidate_ios_root" --quiet
    generated_project="$candidate_ios_root/iOSCommunications.xcodeproj"
else
    xcodegen generate --spec "$spec" --project "$tmp_dir" --project-root "$ios_root" --quiet
    generated_project="$tmp_dir/iOSCommunications.xcodeproj"
fi

if [ ! -d "$generated_project" ]; then
    echo "Generated project missing: $generated_project" >&2
    exit 1
fi

list_output="$(xcodebuild -list -project "$generated_project")"

for target in iOSCommunications iOSCommunicationsTests PolarBleSdk PolarBleSdkWatchOs PolarBleSdkTests; do
    case "$list_output" in
        *"$target"*) ;;
        *)
            echo "Generated project missing target $target" >&2
            exit 1
            ;;
    esac
done

for scheme in iOSCommunications PolarBleSdk PolarBleSdkWatchOs; do
    case "$list_output" in
        *"$scheme"*) ;;
        *)
            echo "Generated project missing scheme $scheme" >&2
            exit 1
            ;;
    esac
done

pbxproj="$generated_project/project.pbxproj"
while IFS= read -r term; do
    [ -n "$term" ] || continue
    if ! grep -Fq "$term" "$pbxproj"; then
        echo "Generated project missing contract term: $term" >&2
        exit 1
    fi
done <<'EOF'
Build PolarBleSdkShared KMP Framework
scripts/build_kmp_ios_framework.sh
SwiftProtobuf
ZIPFoundation
POLAR_KMP_SHARED_REQUIRED
Generated/PolarBleSdkShared/$(PLATFORM_NAME)
PolarBleSdkTests.xctestplan
ManualBleTransportContractsTest.swift
EOF

if [ "$build_candidate" -eq 1 ]; then
    "$repo_root/scripts/ci_xcodebuild_build.sh" "$generated_project" PolarBleSdk "generic/platform=iOS" Debug >/dev/null
fi

echo "generated_xcode_project OK: XcodeGen spec produces the expected package-first project contract"
