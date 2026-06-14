# Release Publication Policy

This document defines the supported release publication automation after the artifact build workflow. The policy is intentionally staged and manual-first: `.github/workflows/release-artifacts.yml` builds workflow artifacts first, then an optional protected job can promote those immutable artifacts to a draft GitHub Release when `workflow_dispatch` supplies a `release_tag` and `publish_to_github_release` is explicitly enabled.

## Decision

The supported publication path is GitHub Release asset promotion from an already green `Release Artifacts` workflow run. Maven Central publishing and Swift package registry publication remain deferred because they require public package-coordinate ownership, long-lived publishing credentials, consumer-facing dependency metadata, and expanded rollback policy. Local Maven validation under `shared/build/local-maven-validation` remains validation-only and must not be described as external publication.

This policy does not replace artifact-only release behavior for package registries. Release automation may build and retain workflow artifacts for `polar-ble-sdk.aar`, `polar-ble-sdk-shared.aar`, shared local Maven validation metadata, `PolarBleSdkShared.xcframework`, `PolarBleSdkShared.xcframework.zip`, and `PolarBleSdkShared.xcframework.zip.checksum`; the protected publication job may promote the AARs, the XCFramework zip, the SwiftPM checksum, SHA-256 checksums, detached signatures, and the release manifest to a draft GitHub Release only.

## Required GitHub Settings

Repository administrators must maintain a protected environment named `release-publication`. The environment must require manual reviewer approval, restrict deployment branches or tags to version release refs, and expose no signing credentials outside that environment.

The publication job must request `permissions: contents: write` only in the protected publication job. The job must not rely on broad repository permissions inherited by validation or artifact-building jobs.

The protected environment must provide signing material as environment secrets: `POLAR_RELEASE_SIGNING_KEY_ASC`, `POLAR_RELEASE_SIGNING_KEY_ID`, and `POLAR_RELEASE_SIGNING_KEY_PASSPHRASE`. If the repository later uses a different signing system, this document and `:repo-tools:verifyReleasePackagingPolicy` must be updated before the workflow changes.

## Required Publication Workflow Shape

Publication must stay in the separate `github-release-assets` job, depend on successful Android and iOS artifact jobs, and run only through `workflow_dispatch` for a specific version tag. The job must download immutable workflow artifacts from the same run or from an explicitly supplied successful `artifact_run_id`, verify the requested `release_tag`, and fail before upload when any required artifact is missing.

The job must generate SHA-256 checksums for every uploaded asset and detached signatures for the Android AARs, `PolarBleSdkShared.xcframework.zip`, and checksum files. The existing SwiftPM checksum for `PolarBleSdkShared.xcframework.zip` must remain present and must be recomputed in the publication job before upload.

The job must publish assets to a draft GitHub Release first and only when `publish_to_github_release` is true. A human reviewer must compare the artifact list, SHA-256 checksums, detached signatures, SwiftPM checksum, and release manifest against the workflow output before the release is marked non-draft. The workflow must not publish to Maven Central, a Swift package registry, CocoaPods, GitHub Pages, or any package host.

## Required Dry Run

The default `workflow_dispatch` mode is a dry-run mode because `publish_to_github_release` defaults to false. The dry run builds the same artifacts, verifies `scripts/verify_android_example_aar_consumption.sh` against the Android full example and ECG/HR demo, verifies `scripts/verify_android_shared_maven_metadata.sh`, runs `sources/iOS/ios-communications/scripts/package_kmp_xcframework.sh --configuration Release --zip-output <tmp>/PolarBleSdkShared.xcframework.zip`, runs `swift package compute-checksum <tmp>/PolarBleSdkShared.xcframework.zip`, generates SHA-256 checksums and detached signatures through `scripts/prepare_github_release_assets.sh`, writes a local release manifest, and uploads the prepared publication directory as `github-release-publication-dry-run` without creating or modifying a GitHub Release.

## Rollback

Rollback for GitHub Release asset publication is to keep the release draft unpublished, delete the draft assets, and rerun the artifact workflow after the fix. If a non-draft GitHub Release has already been published with bad assets, repository maintainers must mark the release as a prerelease or delete the broken assets, publish corrected assets with new checksums and signatures, and update the release notes with the exact replacement reason. Android rollback must preserve the two-AAR compatibility model until the SDK AAR no longer references shared classes or real external publication metadata supplies the shared dependency. SwiftPM rollback must remove the remote `POLAR_BLE_SDK_SHARED_BINARY_URL` and `POLAR_BLE_SDK_SHARED_BINARY_CHECKSUM` values so clean checkouts return to Swift fallback behavior.

## Guardrail Updates Required Before Automation

Any workflow change that uploads GitHub Release assets must keep `:repo-tools:verifyReleasePackagingPolicy` aligned with the protected environment, manual approval job shape, signing secrets, checksum generation, dry-run validation, draft GitHub Release upload, release manifest, and rollback documentation. Any workflow change that adds Maven Central publication, `gradle publish`, or Swift package registry publication must also update `documentation/KmpSharedArtifactConsumption.md`, `documentation/CiCd.md`, this document, and repo-tools guardrails before the workflow change lands.
