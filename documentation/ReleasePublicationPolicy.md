# Release Publication Policy

This document defines the next safe release publication step after the current artifact-only workflow. The policy is intentionally staged and manual-first: `.github/workflows/release-artifacts.yml` continues to upload workflow artifacts only, and no external publication automation is enabled until the repository has the protected environment, signing credentials, and rollback process described here.

## Decision

The next safe publication path is GitHub Release asset promotion from an already green `Release Artifacts` workflow run. Maven Central publishing and Swift package registry publication remain deferred because they require public package-coordinate ownership, long-lived publishing credentials, consumer-facing dependency metadata, and expanded rollback policy. Local Maven validation under `shared/build/local-maven-validation` remains validation-only and must not be described as external publication.

This policy does not replace artifact-only release behavior. Until an approved publication workflow is added, release automation may upload only workflow artifacts: `polar-ble-sdk.aar`, `polar-ble-sdk-shared.aar`, shared local Maven validation metadata, `PolarBleSdkShared.xcframework`, `PolarBleSdkShared.xcframework.zip`, and `PolarBleSdkShared.xcframework.zip.checksum`.

## Required GitHub Settings

Before any GitHub Release asset publication job is added, repository administrators must create a protected environment named `release-publication`. The environment must require manual reviewer approval, restrict deployment branches or tags to version release refs, and expose no publishing credentials outside that environment.

The publication job must request `permissions: contents: write` only in the protected publication job. The job must not rely on broad repository permissions inherited by validation or artifact-building jobs.

The protected environment must provide signing material as environment secrets: `POLAR_RELEASE_SIGNING_KEY_ASC`, `POLAR_RELEASE_SIGNING_KEY_ID`, and `POLAR_RELEASE_SIGNING_KEY_PASSPHRASE`. If the repository later uses a different signing system, this document and `:repo-tools:verifyReleasePackagingPolicy` must be updated before the workflow changes.

## Required Publication Workflow Shape

Publication must be a separate job that depends on a successful artifact build and runs only through `workflow_dispatch` for a specific version tag. The job must download the immutable workflow artifacts from the same run or from an explicitly supplied successful run id, verify that the tag in the request matches the artifact version, and fail before upload when any required artifact is missing.

The job must generate SHA-256 checksums for every uploaded asset and detached signatures for the Android AARs, `PolarBleSdkShared.xcframework.zip`, and checksum files. The existing SwiftPM checksum for `PolarBleSdkShared.xcframework.zip` must remain present and must be recomputed in the publication job before upload.

The job must publish assets to a draft GitHub Release first. A human reviewer must compare the artifact list, SHA-256 checksums, detached signatures, and SwiftPM checksum against the workflow output before the release is marked non-draft. The workflow must not publish to Maven Central, a Swift package registry, CocoaPods, GitHub Pages, or any package host.

## Required Dry Run

Before the first real publication job is enabled, a dry-run mode must exist and pass in CI. The dry run must build the same artifacts, verify `scripts/verify_android_example_aar_consumption.sh`, verify `scripts/verify_android_shared_maven_metadata.sh`, run `sources/iOS/ios-communications/scripts/package_kmp_xcframework.sh --configuration Release --zip-output <tmp>/PolarBleSdkShared.xcframework.zip`, run `swift package compute-checksum <tmp>/PolarBleSdkShared.xcframework.zip`, generate checksums and detached signatures, and write a local release manifest without creating or modifying a GitHub Release.

## Rollback

Rollback for GitHub Release asset publication is to keep the release draft unpublished, delete the draft assets, and rerun the artifact workflow after the fix. If a non-draft GitHub Release has already been published with bad assets, repository maintainers must mark the release as a prerelease or delete the broken assets, publish corrected assets with new checksums and signatures, and update the release notes with the exact replacement reason. Android rollback must preserve the two-AAR compatibility model until the SDK AAR no longer references shared classes or real external publication metadata supplies the shared dependency. SwiftPM rollback must remove the remote `POLAR_BLE_SDK_SHARED_BINARY_URL` and `POLAR_BLE_SDK_SHARED_BINARY_CHECKSUM` values so clean checkouts return to Swift fallback behavior.

## Guardrail Updates Required Before Automation

Any workflow change that uploads GitHub Release assets must update `:repo-tools:verifyReleasePackagingPolicy` to require the protected environment, manual approval job shape, signing secrets, checksum generation, dry-run validation, and rollback documentation. Any workflow change that adds Maven Central publication, `gradle publish`, or Swift package registry publication must also update `documentation/KmpSharedArtifactConsumption.md`, `documentation/CiCd.md`, this document, and repo-tools guardrails before the workflow change lands.
