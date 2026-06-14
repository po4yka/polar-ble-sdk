#!/bin/bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
android_root="$repo_root/sources/Android/android-communications"
proto_version="$(sed -n 's/^protobuf = "\(.*\)"/\1/p' "$android_root/gradle/libs.versions.toml")"
swift_protobuf_version="$(sed -n 's/.*swift-protobuf.git", from: "\(.*\)".*/\1/p' "$repo_root/Package.swift")"
main_proto_root="$android_root/library/src/main/proto"
sdk_proto_root="$android_root/library/src/sdk/proto"
main_swift_output="$repo_root/sources/iOS/ios-communications/Sources/iOSCommunications/ble/api/model/protobuf"
sdk_swift_output="$repo_root/sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/impl/protobuf"
tmp_dir="$(mktemp -d)"

cleanup() {
    rm -rf "$tmp_dir"
}
trap cleanup EXIT

host_classifier() {
    case "$(uname -s)-$(uname -m)" in
        Darwin-arm64) echo "osx-aarch_64" ;;
        Darwin-x86_64) echo "osx-x86_64" ;;
        Linux-x86_64) echo "linux-x86_64" ;;
        Linux-aarch64) echo "linux-aarch_64" ;;
        *) echo "unsupported" ;;
    esac
}

find_cached_protoc() {
    local classifier="$1"
    find "$HOME/.gradle/caches/modules-2/files-2.1/com.google.protobuf/protoc/$proto_version" -name "protoc-$proto_version-$classifier.exe" -type f -perm -u+x 2>/dev/null | head -n 1
}

protoc_bin="${PROTOC:-}"
if [ -z "$protoc_bin" ]; then
    classifier="$(host_classifier)"
    if [ "$classifier" = "unsupported" ]; then
        echo "Unsupported host for automatic protoc lookup: $(uname -s)-$(uname -m). Set PROTOC=/path/to/protoc." >&2
        exit 1
    fi
    protoc_bin="$(find_cached_protoc "$classifier")"
fi
if [ -z "$protoc_bin" ] || [ ! -x "$protoc_bin" ]; then
    echo "Missing executable protoc $proto_version. Run Android Gradle proto generation once or set PROTOC=/path/to/protoc." >&2
    exit 1
fi

protoc_gen_swift="${PROTOC_GEN_SWIFT:-}"
if [ -z "$protoc_gen_swift" ]; then
    protoc_gen_swift="$(find "$repo_root/.build/checkouts/swift-protobuf/.build" -name protoc-gen-swift -type f -perm -u+x | head -n 1)"
    if [ -z "$protoc_gen_swift" ]; then
        swift package resolve --package-path "$repo_root"
        swift build --package-path "$repo_root/.build/checkouts/swift-protobuf" --product protoc-gen-swift
        protoc_gen_swift="$(find "$repo_root/.build/checkouts/swift-protobuf/.build" -name protoc-gen-swift -type f -perm -u+x | head -n 1)"
    fi
fi
if [ -z "$protoc_gen_swift" ] || [ ! -x "$protoc_gen_swift" ]; then
    echo "Missing executable protoc-gen-swift. Set PROTOC_GEN_SWIFT=/path/to/protoc-gen-swift." >&2
    exit 1
fi
if ! "$protoc_gen_swift" --version | grep -q "$swift_protobuf_version"; then
    echo "protoc-gen-swift version must match Package.swift SwiftProtobuf $swift_protobuf_version." >&2
    "$protoc_gen_swift" --version >&2
    exit 1
fi

mapfile -t proto_files < <(
    {
        find "$main_proto_root" -name '*.proto'
        find "$sdk_proto_root" -name '*.proto' ! -path '*/google/protobuf/descriptor.proto'
    } | sort
)

"$protoc_bin" \
    --plugin="protoc-gen-swift=$protoc_gen_swift" \
    --proto_path="$main_proto_root" \
    --proto_path="$sdk_proto_root" \
    --swift_opt=Visibility=Public \
    --swift_out="$tmp_dir" \
    "${proto_files[@]}"

cp "$tmp_dir/communications_pftp_request.pb.swift" "$main_swift_output/communications_pftp_request.pb.swift"
for generated in "$tmp_dir"/*.pb.swift; do
    name="$(basename "$generated")"
    if [ "$name" = "communications_pftp_request.pb.swift" ]; then
        continue
    fi
    cp "$generated" "$sdk_swift_output/$name"
done

echo "Swift protobuf generation OK: protoc $proto_version, protoc-gen-swift $swift_protobuf_version"
