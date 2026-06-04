#!/bin/sh
set -eu

if [ "$#" -ne 4 ]; then
    echo "Usage: $(basename "$0") <workspace> <scheme> <destination> <resultBundlePath>"
    exit 2
fi

WORKSPACE="$1"
SCHEME="$2"
DESTINATION="$3"
RESULT_BUNDLE="$4"
ATTEMPTS="${CI_XCODEBUILD_ATTEMPTS:-2}"
attempt=1

resolve_destination() {
    ruby -rjson -e '
destination = ARGV.fetch(0)

begin
  parts = destination.split(",").map { |part| part.split("=", 2) }.to_h
  unless parts["platform"] == "iOS Simulator" && parts["OS"] == "latest" && parts["name"]
    puts destination
    exit
  end

  devices = JSON.parse(`xcrun simctl list devices available -j`).fetch("devices")
  candidates = devices.flat_map do |runtime, runtime_devices|
    version = runtime[/SimRuntime\.iOS-(.+)\z/, 1]
    next [] unless version

    runtime_devices.filter_map do |device|
      next unless device["name"] == parts["name"] && device.fetch("isAvailable", true)

      [Gem::Version.new(version.tr("-", ".")), device.fetch("udid")]
    end
  end

  selected = candidates.max_by(&:first)
  puts(selected ? "platform=iOS Simulator,id=#{selected.last}" : destination)
rescue StandardError => e
  warn "Unable to resolve XCTest destination #{destination.inspect}: #{e.message}"
  puts destination
end
' "$1"
}

RESOLVED_DESTINATION="$(resolve_destination "$DESTINATION")"
if [ "$RESOLVED_DESTINATION" != "$DESTINATION" ]; then
    echo "Resolved XCTest destination: $RESOLVED_DESTINATION"
fi

while [ "$attempt" -le "$ATTEMPTS" ]; do
    rm -rf "$RESULT_BUNDLE"
    if xcodebuild test -workspace "$WORKSPACE" -scheme "$SCHEME" -destination "$RESOLVED_DESTINATION" -resultBundlePath "$RESULT_BUNDLE"; then
        exit 0
    fi
    if [ "$attempt" -eq "$ATTEMPTS" ]; then
        exit 1
    fi
    echo "xcodebuild failed on attempt $attempt; retrying after simulator cleanup"
    xcrun simctl shutdown all || true
    sleep 10
    attempt=$((attempt + 1))
done
