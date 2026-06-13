#!/usr/bin/env ruby
# frozen_string_literal: true

ROOT = File.expand_path("..", __dir__)

def read(path)
  File.read(File.join(ROOT, path))
end

errors = []
workflow_path = ".github/workflows/release-artifacts.yml"
workflow = read(workflow_path)
ci_doc = read("documentation/CiCd.md")
consumption_doc = read("documentation/KmpSharedArtifactConsumption.md")

required_workflow_terms = [
  "scripts/verify_android_example_aar_consumption.sh",
  "scripts/verify_android_shared_maven_metadata.sh",
  "scripts/verify_release_packaging_policy.rb",
  "polar-ble-sdk.aar",
  "polar-ble-sdk-shared.aar",
  "shared-maven-local",
  "linkReleaseFrameworkIosArm64",
  "linkReleaseFrameworkIosSimulatorArm64",
  "linkReleaseFrameworkIosX64",
  "PolarBleSdkShared.framework",
  "actions/upload-artifact@v4"
]

required_workflow_terms.each do |term|
  errors << "#{workflow_path}: missing #{term}" unless workflow.include?(term)
end

forbidden_workflow_terms = [
  "publishToMavenLocal",
  "publishAllPublicationsToMavenRepository",
  "pod trunk push",
  "pod repo push",
  "swift package-registry publish",
  "mvn deploy",
  "gradle publish",
  "secrets.MAVEN",
  "secrets.Cocoapods",
  "secrets.COCOAPODS",
  "secrets.SWIFT_PACKAGE"
]

forbidden_workflow_terms.each do |term|
  errors << "#{workflow_path}: release artifacts workflow must stay artifact-only and not contain #{term}" if workflow.include?(term)
end

required_doc_terms = [
  "CI/release remains artifact-only",
  "No Maven, CocoaPods, or SwiftPM publication is claimed",
  "required secrets are intentionally absent",
  "Android internal project dependency",
  "polar-ble-sdk.aar",
  "polar-ble-sdk-shared.aar",
  "shared local Maven metadata validation",
  "PolarBleSdkShared.framework",
  "rollback"
]

[["documentation/CiCd.md", ci_doc], ["documentation/KmpSharedArtifactConsumption.md", consumption_doc]].each do |path, text|
  required_doc_terms.each do |term|
    errors << "#{path}: missing #{term}" unless text.include?(term)
  end

  unless text.include?("SwiftPM/watchOS fallback-only") || text.include?("Swift Package Manager and watchOS are fallback-only")
    errors << "#{path}: missing SwiftPM/watchOS fallback-only"
  end
end

modern_stack_path = File.join(ROOT, "documentation/KmpModernStackAudit.md")
if File.exist?(modern_stack_path)
  modern_stack = File.read(modern_stack_path)
  ["artifact-only", "polar-ble-sdk-shared.aar"].each do |term|
    errors << "documentation/KmpModernStackAudit.md: missing #{term}" unless modern_stack.include?(term)
  end

  unless modern_stack.include?("SwiftPM/watchOS fallback-only") || modern_stack.include?("Swift Package Manager and watchOS are fallback-only")
    errors << "documentation/KmpModernStackAudit.md: missing SwiftPM/watchOS fallback-only"
  end
end

abort(errors.join("\n")) unless errors.empty?

puts "Release packaging policy static inspection OK"
