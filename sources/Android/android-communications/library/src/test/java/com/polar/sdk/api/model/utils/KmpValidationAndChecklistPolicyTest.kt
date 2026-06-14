package com.polar.sdk.api.model.utils

import org.junit.Assert.assertTrue
import org.junit.Test

class KmpValidationAndChecklistPolicyTest {

    @Test
    fun `shared validation commands stay current and artifact backed`() {
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
            "KmpValidationCommands.md must cover Android, iOS, and shared common validation: $missingSections",
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
            "KmpValidationCommands.md must keep simulator XCTest execution shards documented after swiftc and Xcode probe checks: $validationMissingTerms",
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
            violations += "CocoaPods files must be removed after SwiftPM shared ownership"
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
    fun `Xcode project model remains generated and package first`() {
        val root = findRepositoryRoot()
        val projectSpec = root.resolve("sources/iOS/ios-communications/project.yml")
        val validator = root.resolve("scripts/validate_generated_xcode_project.sh")
        val workflow = root.resolve("sources/iOS/ios-communications/XcodeProjectWorkflow.md").readText()
        val violations = mutableListOf<String>()

        if (!projectSpec.isFile) {
            violations += "sources/iOS/ios-communications/project.yml must exist"
        } else {
            val spec = projectSpec.readText()
            listOf("XcodeGen", "SwiftProtobuf", "ZIPFoundation", "Build PolarBleSdkShared KMP Framework", "scripts/build_kmp_ios_framework.sh", "POLAR_KMP_SHARED_REQUIRED", "PolarBleSdkWatchOs", "PolarBleSdkTests.xctestplan")
                .filterNot { term -> spec.contains(term) }
                .mapTo(violations) { term -> "project.yml missing $term" }
        }
        if (!validator.isFile || !validator.canExecute()) {
            violations += "scripts/validate_generated_xcode_project.sh must exist and be executable"
        } else {
            val script = validator.readText()
            listOf("xcodegen generate", "xcodebuild -list", "Generated/PolarBleSdkShared/$(PLATFORM_NAME)", "POLAR_KMP_SHARED_REQUIRED")
                .filterNot { term -> script.contains(term) }
                .mapTo(violations) { term -> "validate_generated_xcode_project.sh missing $term" }
        }
        listOf("project.yml", "XcodeGen", "generated/package-first", ":repo-tools:validateGeneratedXcodeProject", "committed compatibility harness")
            .filterNot { term -> workflow.contains(term) }
            .mapTo(violations) { term -> "XcodeProjectWorkflow.md missing $term" }

        assertTrue(
            "Xcode project model must stay generated/package-first while the committed project remains a compatibility harness: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `generated API documentation remains generator owned`() {
        val root = findRepositoryRoot()
        val docsGitignore = root.resolve("docs/.gitignore")
        val generateScript = root.resolve("scripts/generate_api_docs.sh")
        val swiftProtobufScript = root.resolve("scripts/generate_swift_protobuf.sh")
        val androidProtoDir = root.resolve("sources/Android/android-communications/library/src/sdk/proto")
        val swiftProtobufDir = root.resolve("sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/impl/protobuf")
        val androidGradle = root.resolve("sources/Android/android-communications/library/build.gradle.kts").readText()
        val violations = mutableListOf<String>()

        if (!docsGitignore.isFile) {
            violations += "docs/.gitignore must exist"
        } else {
            val ignored = docsGitignore.readText()
            listOf("/polar-sdk-android/", "/polar-sdk-ios/")
                .filterNot { term -> ignored.contains(term) }
                .mapTo(violations) { term -> "docs/.gitignore must ignore $term" }
        }
        if (!generateScript.isFile || !generateScript.canExecute()) {
            violations += "scripts/generate_api_docs.sh must exist and be executable"
        } else {
            val script = generateScript.readText()
            listOf(":repo-tools:generateApiDocs", "--no-daemon", "--warning-mode all")
                .filterNot { term -> script.contains(term) }
                .mapTo(violations) { term -> "scripts/generate_api_docs.sh missing $term" }
            listOf("xcodebuild docbuild", "docc process-archive", "perl -0pi")
                .filter { term -> script.contains(term) }
                .mapTo(violations) { term -> "scripts/generate_api_docs.sh must delegate docs generation to Gradle instead of containing $term" }
        }
        if (!androidGradle.contains("alias(libs.plugins.dokka)") || !androidGradle.contains("dokkaPublications.html")) {
            violations += "Android docs generation must stay on Dokka HTML configuration"
        }
        listOf(""".*\\.androidcommunications.*""", """com\\.polar\\.sdk\\.impl(\\..*)?""")
            .filterNot { term -> androidGradle.contains(term) }
            .mapTo(violations) { term -> "Android Dokka public docs surface must suppress $term" }
        val swiftImplPublicSymbols = root.resolve("sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/impl")
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "swift" && !file.name.endsWith(".pb.swift") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (Regex("""^\s*(public|open)\s+(class|struct|enum|protocol|actor|func|var|let|typealias|extension)\b""").containsMatchIn(line)) {
                        "${file.relativeTo(root).path}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()
        if (swiftImplPublicSymbols.isNotEmpty()) {
            violations += "Swift implementation package must not expose public API symbols: $swiftImplPublicSymbols"
        }
        val repoToolsBuild = root.resolve("sources/Android/android-communications/repo-tools/build.gradle.kts").readText()
        listOf("generateApiDocs", "packageGeneratedApiDocs", ":library:dokkaGeneratePublicationHtml", "xcodebuild", "docbuild", "docc", "transform-for-static-hosting", "polar-generated-api-docs.tar.gz")
            .filterNot { term -> repoToolsBuild.contains(term) }
            .mapTo(violations) { term -> "repo-tools Gradle docs generation missing $term" }
        val trackedGeneratedDocs = runCatching {
            ProcessBuilder("git", "ls-files", "docs/polar-sdk-android", "docs/polar-sdk-ios")
                .directory(root)
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .readText()
                .lineSequence()
                .filter { it.isNotBlank() }
                .toList()
        }.getOrElse { listOf("git ls-files failed: ${it.message}") }
        if (trackedGeneratedDocs.isNotEmpty()) {
            violations += "Generated API docs must stay untracked: $trackedGeneratedDocs"
        }
        if (!swiftProtobufScript.isFile || !swiftProtobufScript.canExecute()) {
            violations += "scripts/generate_swift_protobuf.sh must exist and be executable"
        } else {
            val script = swiftProtobufScript.readText()
            listOf("proto_version", "swift_protobuf_version", "PROTOC", "PROTOC_GEN_SWIFT", "--swift_opt=Visibility=Public", "protoc-gen-swift version must match Package.swift SwiftProtobuf")
                .filterNot { term -> script.contains(term) }
                .mapTo(violations) { term -> "scripts/generate_swift_protobuf.sh missing $term" }
        }
        val androidProtoReadme = androidProtoDir.resolve("README.md").readText()
        listOf("protobuf schema source of truth", "scripts/generate_swift_protobuf.sh", "Do not place generated protobuf output")
            .filterNot { term -> androidProtoReadme.contains(term) }
            .mapTo(violations) { term -> "Android proto README missing $term" }
        val swiftProtobufReadme = swiftProtobufDir.resolve("README.md").readText()
        listOf("checked-in Swift protobuf output", "Do not hand-edit", "scripts/generate_swift_protobuf.sh", "Package.swift")
            .filterNot { term -> swiftProtobufReadme.contains(term) }
            .mapTo(violations) { term -> "Swift protobuf README missing $term" }
        val generatedSwiftProtobufFiles = swiftProtobufDir.walkTopDown()
            .filter { file -> file.isFile && file.name.endsWith(".pb.swift") }
            .toList()
        if (generatedSwiftProtobufFiles.isEmpty()) {
            violations += "Swift generated protobuf directory must contain checked-in *.pb.swift output"
        }
        generatedSwiftProtobufFiles.flatMap { file ->
            val text = file.readText()
            listOf("// DO NOT EDIT.", "swift-format-ignore-file", "swiftlint:disable all", "Generated by the Swift generator plugin for the protocol buffer compiler.", "Source:")
                .filterNot { term -> text.contains(term) }
                .map { term -> "${file.relativeTo(root).path} missing generated header term $term" }
        }.let(violations::addAll)
        if (androidProtoDir.walkTopDown().none { file -> file.isFile && file.extension == "proto" }) {
            violations += "Android SDK protobuf schema directory must contain checked-in *.proto schema files"
        }

        assertTrue(
            "Generated API docs and protobuf outputs must remain generator-owned and untracked where applicable: $violations",
            violations.isEmpty()
        )
    }
}
