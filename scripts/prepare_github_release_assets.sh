#!/bin/sh
set -eu

artifacts_dir=""
output_dir=""
release_tag=""
sign_assets=0

usage() {
    echo "Usage: $(basename "$0") --artifacts-dir <dir> --output-dir <dir> --release-tag <tag> [--sign]" >&2
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --artifacts-dir)
            artifacts_dir="${2:-}"
            shift 2
            ;;
        --output-dir)
            output_dir="${2:-}"
            shift 2
            ;;
        --release-tag)
            release_tag="${2:-}"
            shift 2
            ;;
        --sign)
            sign_assets=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            usage
            exit 2
            ;;
    esac
done

if [ -z "$artifacts_dir" ] || [ -z "$output_dir" ] || [ -z "$release_tag" ]; then
    usage
    exit 2
fi

if [ ! -d "$artifacts_dir" ]; then
    echo "Artifacts directory does not exist: $artifacts_dir" >&2
    exit 1
fi

android_dir="$artifacts_dir/android-release-artifacts"
ios_dir="$artifacts_dir/ios-release-artifacts"

for dir in "$android_dir" "$ios_dir"; do
    if [ ! -d "$dir" ]; then
        echo "Missing downloaded release artifact directory: $dir" >&2
        exit 1
    fi
done

mkdir -p "$output_dir"
rm -rf "$output_dir"/*

copy_required() {
    source_path="$1"
    target_name="$2"
    if [ ! -f "$source_path" ]; then
        echo "Missing required release artifact: $source_path" >&2
        exit 1
    fi
    cp "$source_path" "$output_dir/$target_name"
}

copy_required "$android_dir/polar-ble-sdk.aar" "polar-ble-sdk.aar"
copy_required "$android_dir/polar-ble-sdk-shared.aar" "polar-ble-sdk-shared.aar"
copy_required "$ios_dir/PolarBleSdkShared.xcframework.zip" "PolarBleSdkShared.xcframework.zip"
copy_required "$ios_dir/PolarBleSdkShared.xcframework.zip.checksum" "PolarBleSdkShared.xcframework.zip.checksum"

if [ -d "$android_dir/shared-maven-local" ]; then
    tar -C "$android_dir" -czf "$output_dir/polar-shared-local-maven-validation.tar.gz" shared-maven-local
fi

(
    cd "$output_dir"
    find . -type f ! -name SHA256SUMS ! -name release-manifest.json ! -name '*.asc' -print | sed 's#^\./##' | sort | xargs shasum -a 256 > SHA256SUMS
)

if [ "$sign_assets" -eq 1 ]; then
    if [ -z "${POLAR_RELEASE_SIGNING_KEY_ID:-}" ] || [ -z "${POLAR_RELEASE_SIGNING_KEY_PASSPHRASE_FILE:-}" ]; then
        echo "Signing requires POLAR_RELEASE_SIGNING_KEY_ID and POLAR_RELEASE_SIGNING_KEY_PASSPHRASE_FILE" >&2
        exit 1
    fi
    if [ ! -f "$POLAR_RELEASE_SIGNING_KEY_PASSPHRASE_FILE" ]; then
        echo "Signing passphrase file does not exist: $POLAR_RELEASE_SIGNING_KEY_PASSPHRASE_FILE" >&2
        exit 1
    fi
    (
        cd "$output_dir"
        for asset in polar-ble-sdk.aar polar-ble-sdk-shared.aar PolarBleSdkShared.xcframework.zip PolarBleSdkShared.xcframework.zip.checksum SHA256SUMS; do
            gpg --batch --yes --pinentry-mode loopback --passphrase-file "$POLAR_RELEASE_SIGNING_KEY_PASSPHRASE_FILE" --local-user "$POLAR_RELEASE_SIGNING_KEY_ID" --armor --detach-sign "$asset"
        done
    )
fi

asset_count="$(find "$output_dir" -type f | wc -l | tr -d ' ')"
swiftpm_checksum="$(cat "$output_dir/PolarBleSdkShared.xcframework.zip.checksum")"
cat > "$output_dir/release-manifest.json" <<EOF
{
  "releaseTag": "$release_tag",
  "assetCount": $asset_count,
  "swiftPackageChecksum": "$swiftpm_checksum",
  "sha256File": "SHA256SUMS",
  "signatures": $sign_assets
}
EOF

echo "GitHub Release assets prepared in $output_dir"
