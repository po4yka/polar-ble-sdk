#!/usr/bin/env ruby
# frozen_string_literal: true

require "open3"
require "timeout"

ROOT = File.expand_path("..", __dir__)
IOS_ROOT = File.join(ROOT, "sources/iOS/ios-communications")
PROJECT = File.join(IOS_ROOT, "iOSCommunications.xcodeproj")
WORKSPACE = File.join(IOS_ROOT, "iOSCommunications.xcworkspace")
PODS = File.join(IOS_ROOT, "Pods")
EXPECTED_TARGETS = %w[iOSCommunications iOSCommunicationsTests PolarBleSdk PolarBleSdkWatchOs PolarBleSdkTests].freeze
EXPECTED_SCHEMES = %w[iOSCommunications PolarBleSdk PolarBleSdkWatchOs].freeze

def relative(path)
  path.delete_prefix("#{ROOT}/")
end

def run_command(*command)
  output = +""
  status = nil
  timed_out = false
  Timeout.timeout(60) do
    output, status = Open3.capture2e(*command)
  end
  [status.exitstatus, output, timed_out]
rescue Timeout::Error
  timed_out = true
  [124, output, timed_out]
end

errors = []
blockers = []

errors << "#{relative(PROJECT)} is missing" unless File.directory?(PROJECT)
errors << "#{relative(WORKSPACE)} is missing" unless File.directory?(WORKSPACE)

unless errors.empty?
  warn errors.join("\n")
  abort "ios_xcode_validation_probe failed with #{errors.size} structural violation(s)"
end

project_exit, project_output, project_timeout = run_command("xcodebuild", "-list", "-project", PROJECT)
if project_timeout
  errors << "xcodebuild project discovery timed out after 60s"
elsif project_exit != 0
  errors << "xcodebuild project discovery failed with exit #{project_exit}"
else
  missing_targets = EXPECTED_TARGETS.reject { |target| project_output.include?(target) }
  missing_schemes = EXPECTED_SCHEMES.reject { |scheme| project_output.include?(scheme) }
  errors << "xcodebuild project discovery missing targets: #{missing_targets.join(", ")}" unless missing_targets.empty?
  errors << "xcodebuild project discovery missing schemes: #{missing_schemes.join(", ")}" unless missing_schemes.empty?
end

workspace_exit, workspace_output, workspace_timeout = run_command("xcodebuild", "-list", "-workspace", WORKSPACE)
if workspace_timeout
  blockers << "workspace-probe-timeout"
elsif workspace_exit != 0 && workspace_output.include?("not a workspace file")
  blockers << "workspace-not-valid-to-xcodebuild"
elsif workspace_exit != 0
  errors << "xcodebuild workspace discovery failed with unexpected exit #{workspace_exit}: #{workspace_output.lines.last&.strip}"
end

blockers << "pods-absent" unless File.directory?(PODS)
if project_output.include?("CoreSimulatorService connection became invalid") || workspace_output.include?("CoreSimulatorService connection became invalid")
  blockers << "coresimulator-unavailable"
end

unless errors.empty?
  warn errors.join("\n")
  abort "ios_xcode_validation_probe failed with #{errors.size} validation violation(s)"
end

if blockers.empty?
  puts "ios_xcode_validation_probe OK: project discovery is available and no known local XCTest infrastructure blockers were detected"
else
  puts "ios_xcode_validation_probe OK: project discovery is available; full XCTest remains blocked by #{blockers.uniq.sort.join(", ")}"
end
