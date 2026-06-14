package com.polar.sdk.api.model.utils

import org.junit.Assert.assertTrue
import org.junit.Test

class KmpValidationAndChecklistPolicyTest {

    @Test
    fun `KMP validation commands stay current and artifact backed`() {
        val root = findRepositoryRoot()
        val validationDoc = root.resolve("documentation/KmpValidationCommands.md").readText()
        val missingSections = REQUIRED_VALIDATION_SECTIONS
            .filterNot { section -> validationDoc.contains(section) }
        val missingNonGradleGateTerms = VALIDATION_NON_GRADLE_GATE_TERMS
            .filterNot { term -> validationDoc.contains(term) }
        val missingHardwareSmokeTerms = HARDWARE_SMOKE_VALIDATION_TERMS
            .filterNot { term -> validationDoc.contains(term) }
        val missingGradleBatchTerms = GRADLE_BATCH_VALIDATION_TERMS
            .filterNot { term -> validationDoc.contains(term) }
        val missingArtifactReferences = validationDoc.validationArtifactReferences()
            .filterNot { reference -> root.resolve(reference).exists() }
        val iosProbe = root.resolve("sources/Android/android-communications/repo-tools/src/main/kotlin/com/polar/tools/RepoTools.kt")
        val missingIosProbeTerms = if (iosProbe.isFile) {
            IOS_XCODE_PROBE_REQUIRED_TERMS.filterNot { term -> iosProbe.readText().contains(term) }
        } else {
            listOf("sources/Android/android-communications/repo-tools/src/main/kotlin/com/polar/tools/RepoTools.kt")
        }
        val missingAndroidWrapper = if (validationDoc.contains("./gradlew") && !root.resolve("sources/Android/android-communications/gradlew").isFile) {
            listOf("sources/Android/android-communications/gradlew")
        } else {
            emptyList()
        }

        assertTrue(
            "KmpValidationCommands.md must cover Android, iOS, and KMP common validation: $missingSections",
            missingSections.isEmpty()
        )
        assertTrue(
            "KmpValidationCommands.md must document non-Gradle metadata gates: $missingNonGradleGateTerms",
            missingNonGradleGateTerms.isEmpty()
        )
        assertTrue(
            "KmpValidationCommands.md must keep hardware/device smoke validation subordinate to deterministic coverage gates: $missingHardwareSmokeTerms",
            missingHardwareSmokeTerms.isEmpty()
        )
        assertTrue(
            "KmpValidationCommands.md must keep Gradle validation batched and scoped to library/shared gates unless app/example surfaces change: $missingGradleBatchTerms",
            missingGradleBatchTerms.isEmpty()
        )
        assertTrue(
            "KmpValidationCommands.md must reference existing repo artifacts: ${missingArtifactReferences + missingAndroidWrapper}",
            missingArtifactReferences.isEmpty() && missingAndroidWrapper.isEmpty()
        )
        assertTrue(
            "iOS Xcode infrastructure probe must verify expected targets/schemes and classify current XCTest blockers: $missingIosProbeTerms",
            missingIosProbeTerms.isEmpty()
        )
    }

    @Test
    fun `iOS XCTest execution gate remains required after syntax and infrastructure probes`() {
        val validationDoc = findRepositoryRoot().resolve("documentation/KmpValidationCommands.md").readText()
        val validationMissingTerms = IOS_XCTEST_EXECUTION_GATE_REQUIRED_TERMS
            .filterNot { term -> validationDoc.contains(term) }

        assertTrue(
            "KmpValidationCommands.md must keep full simulator XCTest as the required iOS execution gate after swiftc and Xcode probe checks: $validationMissingTerms",
            validationMissingTerms.isEmpty()
        )
    }

    @Test
    fun `Android minimum SDK documentation matches Gradle configuration`() {
        val root = findRepositoryRoot()
        val gradleMinSdk = ANDROID_MIN_SDK_VERSION.find(root.resolve("sources/Android/android-communications/library/build.gradle.kts").readText())
            ?.groupValues
            ?.get(1)
            ?: error("Could not find minSdk in Android library build.gradle.kts")
        val mismatches = ANDROID_MIN_SDK_DOCS.flatMap { relativePath ->
            val documentedValues = ANDROID_MIN_SDK_REFERENCE.findAll(root.resolve(relativePath).readText())
                .map { match -> match.groupValues[1] }
                .toSet()
            when {
                documentedValues.isEmpty() -> listOf("$relativePath: missing Android minSdk $gradleMinSdk")
                documentedValues != setOf(gradleMinSdk) -> listOf("$relativePath: documented $documentedValues but Gradle declares $gradleMinSdk")
                else -> emptyList()
            }
        }

        assertTrue(
            "Android minSdk documentation must match sources/Android/android-communications/library/build.gradle.kts: $mismatches",
            mismatches.isEmpty()
        )
    }

    @Test
    fun `Android Gradle version helper remains tagless safe`() {
        val gradle = findRepositoryRoot().resolve("sources/Android/android-communications/library/build.gradle.kts").readText()
        val violations = mutableListOf<String>()

        if (!gradle.contains("ProcessBuilder(\"git\", \"describe\", \"--tags\", \"--always\")")) {
            violations += "Android build.gradle.kts must use git describe --tags --always"
        }
        if (!gradle.contains("val exitValue = process.waitFor()") || !gradle.contains("exitValue == 0")) {
            violations += "Android build.gradle.kts must handle nonzero Git describe exits during configuration"
        }
        if (!gradle.contains("matcher.find()")) {
            violations += "Android build.gradle.kts must extract a semver prefix instead of requiring the full git describe output to be semver"
        }
        if (!gradle.contains("\"0.0.0\"")) {
            violations += "Android build.gradle.kts must fall back to parseable semver 0.0.0"
        }

        assertTrue(
            "Android Gradle version helper must configure in tagless checkouts and keep BuildConfig.GIT_VERSION parseable: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `SwiftPM source paths match iOS source layout`() {
        val root = findRepositoryRoot()
        val iosReadme = root.resolve("sources/iOS/ios-communications/README.md").readText()
        val packageSwift = root.resolve("Package.swift").readText()
        val sourceRoot = root.resolve("sources/iOS/ios-communications/Sources")
        val violations = mutableListOf<String>()

        if (root.resolve("PolarBleSdk.podspec").exists() || root.resolve("sources/iOS/ios-communications/Podfile").exists() || root.resolve("sources/iOS/ios-communications/iOSCommunications.xcworkspace").exists()) {
            violations += "CocoaPods files must be removed after SwiftPM migration"
        }
        if (!sourceRoot.isDirectory) {
            violations += "Missing iOS source root ${sourceRoot.relativeTo(root).path}"
        } else if (sourceRoot.walkTopDown().none { file -> file.isFile && file.extension == "swift" }) {
            violations += "iOS source root ${sourceRoot.relativeTo(root).path} must contain Swift sources"
        }
        listOf("SwiftProtobuf", "ZIPFoundation", "sources/iOS/ios-communications/Sources", "POLAR_BLE_SDK_SHARED_BINARY_URL", "POLAR_BLE_SDK_SHARED_BINARY_CHECKSUM")
            .filterNot { term -> packageSwift.contains(term) }
            .mapTo(violations) { term -> "Package.swift missing $term" }
        if (!iosReadme.contains("Swift Package Manager") || !iosReadme.contains("Only Swift Package Manager, XCFramework, and direct Xcode project integration are supported")) {
            violations += "sources/iOS/ios-communications/README.md must document current SwiftPM-first package paths"
        }
        if (iosReadme.contains("CocoaPods (unsupported)") || iosReadme.contains("using Carthage") || iosReadme.contains("project or workspace")) {
            violations += "sources/iOS/ios-communications/README.md must not keep legacy CocoaPods, Carthage, or workspace guidance"
        }
        if (iosReadme.contains("<relative_path_to_cloned_repo>/ios-communications/") || iosReadme.contains("`/ios-communications/`")) {
            violations += "sources/iOS/ios-communications/README.md must not reference a nonexistent top-level ios-communications path"
        }

        assertTrue(
            "SwiftPM metadata and documentation must match the current iOS source layout: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `generated API documentation remains generator owned`() {
        val root = findRepositoryRoot()
        val androidDocs = root.resolve("docs/polar-sdk-android")
        val iosDocs = root.resolve("docs/polar-sdk-ios/documentation/polarblesdk")
        val generateScript = root.resolve("scripts/generate_api_docs.sh")
        val androidGradle = root.resolve("sources/Android/android-communications/library/build.gradle.kts").readText()
        val violations = mutableListOf<String>()

        if (!androidDocs.isDirectory || androidDocs.walkTopDown().none { file -> file.isFile && file.name == "index.html" }) {
            violations += "Generated Android API docs must stay under docs/polar-sdk-android"
        }
        if (!iosDocs.isDirectory || iosDocs.walkTopDown().none { file -> file.isFile && file.name == "index.html" }) {
            violations += "Generated iOS DocC static output must stay under docs/polar-sdk-ios/documentation/polarblesdk"
        }
        if (!generateScript.isFile || !generateScript.canExecute()) {
            violations += "scripts/generate_api_docs.sh must exist and be executable"
        } else {
            val script = generateScript.readText()
            listOf("dokkaGeneratePublicationHtml", "docc process-archive transform-for-static-hosting", "docs/polar-sdk-ios")
                .filterNot { term -> script.contains(term) }
                .mapTo(violations) { term -> "scripts/generate_api_docs.sh missing $term" }
        }
        if (!androidGradle.contains("alias(libs.plugins.dokka)") || !androidGradle.contains("dokkaPublications.html")) {
            violations += "Android docs generation must stay on Dokka HTML configuration"
        }

        assertTrue(
            "Generated API docs must remain generator-owned and reproducible: $violations",
            violations.isEmpty()
        )
    }
}
