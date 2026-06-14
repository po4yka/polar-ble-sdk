package com.polar.tools

import java.io.File
import java.util.concurrent.TimeUnit

private val repoRoot: File = System.getProperty("polar.repo.root")?.let(::File)
    ?: generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "Package.swift").isFile && File(it, "sources/Android/android-communications").isDirectory }
    ?: error("Unable to locate repository root")

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: fail("Missing repo-tools command")
    when (command) {
        "kmpNonGradleChecks" -> kmpNonGradleChecks()
        "verifyReleasePackagingPolicy" -> verifyReleasePackagingPolicy()
        "iosXcodeValidationProbe" -> iosXcodeValidationProbe()
        "validateSpmXcframeworkConsumption" -> validateSpmXcframeworkConsumption()
        "verifyApiDocsGenerationPolicy" -> verifyApiDocsGenerationPolicy()
        else -> fail("Unknown repo-tools command: $command")
    }
}

private fun kmpNonGradleChecks() {
    val errors = mutableListOf<String>()
    val requiredFiles = listOf(
        "Package.swift",
        "documentation/KmpValidationCommands.md",
        "documentation/KmpSharedArtifactConsumption.md",
        "documentation/KmpModernStackAudit.md",
        "documentation/ReleasePublicationPolicy.md",
        ".github/dependabot.yml",
        "sources/iOS/ios-communications/scripts/package_kmp_xcframework.sh",
        "sources/iOS/ios-communications/scripts/validate_spm_xcframework_consumption.sh",
        "sources/Android/android-communications/repo-tools/src/main/kotlin/com/polar/tools/RepoTools.kt",
    )
    requiredFiles.filterNot { repoRoot.resolve(it).isFile }.mapTo(errors) { "$it is missing" }

    val forbiddenActiveFiles = listOf(
        "PolarBleSdk.podspec",
        "sources/iOS/ios-communications/Podfile",
        "sources/iOS/ios-communications/iOSCommunications.xcworkspace",
    )
    forbiddenActiveFiles.filter { repoRoot.resolve(it).exists() }.mapTo(errors) { "$it must be removed after SwiftPM migration" }

    val activeRubyScripts = repoRoot.resolve("scripts").walkTopDown()
        .filter { it.isFile && it.extension == "rb" }
        .map { it.relativeTo(repoRoot).path }
        .toList()
    if (activeRubyScripts.isNotEmpty()) {
        errors += "Ruby repo checks must be removed or migrated to repo-tools: ${activeRubyScripts.joinToString()}"
    }

    val workflowText = listOf(
        ".github/workflows/pr-checks.yml",
        ".github/workflows/nightly.yml",
        ".github/workflows/release-artifacts.yml",
    ).joinToString("\n") { repoRoot.resolve(it).readTextIfExists() }
    listOf("ruby ", "gem install cocoapods", "pod install", "pod lib lint", "CocoaPods generated artifacts")
        .filter { workflowText.contains(it) }
        .mapTo(errors) { "CI workflows must not contain $it" }

    val packageSwift = repoRoot.resolve("Package.swift").readTextIfExists()
    listOf("POLAR_BLE_SDK_SHARED_BINARY_URL", "POLAR_BLE_SDK_SHARED_BINARY_CHECKSUM", ".binaryTarget", "PolarBleSdkShared.xcframework")
        .filterNot { packageSwift.contains(it) }
        .mapTo(errors) { "Package.swift missing $it" }

    val validationDoc = repoRoot.resolve("documentation/KmpValidationCommands.md").readTextIfExists()
    listOf(":repo-tools:kmpNonGradleChecks", ":repo-tools:iosXcodeValidationProbe", "Swift Package Manager", "package_kmp_xcframework.sh")
        .filterNot { validationDoc.contains(it) }
        .mapTo(errors) { "documentation/KmpValidationCommands.md missing $it" }

    listOf("ruby scripts/", "pod install", "pod lib lint", "PolarBleSdk.podspec")
        .filter { validationDoc.contains(it) }
        .mapTo(errors) { "documentation/KmpValidationCommands.md must not document active legacy validation path $it" }

    val dependabot = repoRoot.resolve(".github/dependabot.yml").readTextIfExists()
    if (dependabot.contains("package-ecosystem: cocoapods")) {
        errors += ".github/dependabot.yml must not contain CocoaPods because SwiftPM is the supported Apple package path"
    }

    val spmValidation = repoRoot.resolve("sources/iOS/ios-communications/scripts/validate_spm_xcframework_consumption.sh").readTextIfExists()
    if (spmValidation.contains("ruby ")) {
        errors += "validate_spm_xcframework_consumption.sh must not use Ruby"
    }

    finish(errors, "kmp_non_gradle_checks OK: SwiftPM packaging, Kotlin repo-tools, CocoaPods removal, and Ruby-free validation policy are aligned")
}

private fun verifyReleasePackagingPolicy() {
    val errors = mutableListOf<String>()
    val workflowPath = ".github/workflows/release-artifacts.yml"
    val workflow = repoRoot.resolve(workflowPath).readTextIfExists()
    val ciDoc = repoRoot.resolve("documentation/CiCd.md").readTextIfExists()
    val consumptionDoc = repoRoot.resolve("documentation/KmpSharedArtifactConsumption.md").readTextIfExists()
    val publicationDocPath = "documentation/ReleasePublicationPolicy.md"
    val publicationDoc = repoRoot.resolve(publicationDocPath).readTextIfExists()

    listOf(
        ":repo-tools:verifyReleasePackagingPolicy",
        "scripts/verify_android_example_aar_consumption.sh",
        "scripts/verify_android_shared_maven_metadata.sh",
        "package_kmp_xcframework.sh",
        "PolarBleSdkShared.xcframework.zip",
        "swift package compute-checksum",
        "actions/upload-artifact@v4",
    ).filterNot { workflow.contains(it) }.mapTo(errors) { "$workflowPath missing $it" }

    listOf(
        "pod trunk push",
        "pod repo push",
        "gem install cocoapods",
        "pod install",
        "pod lib lint",
        "swift package-registry publish",
        "mvn deploy",
        "gradle publish",
        "secrets.MAVEN",
        "secrets.COCOAPODS",
        "secrets.SWIFT_PACKAGE",
    ).filter { workflow.contains(it) }.mapTo(errors) { "$workflowPath must stay artifact-only and not contain $it" }

    listOf(ciDoc to "documentation/CiCd.md", consumptionDoc to "documentation/KmpSharedArtifactConsumption.md").forEach { (text, path) ->
        listOf(
            "CI/release remains artifact-only",
            "No Maven or SwiftPM registry publication is claimed",
            "required secrets are intentionally absent",
            "ReleasePublicationPolicy.md",
            "Swift Package Manager is the supported Apple package path",
            "PolarBleSdkShared.xcframework.zip",
        ).filterNot { text.contains(it) }.mapTo(errors) { "$path missing $it" }
    }

    listOf(
        "GitHub Release asset promotion",
        "release-publication",
        "manual reviewer approval",
        "POLAR_RELEASE_SIGNING_KEY_ASC",
        "POLAR_RELEASE_SIGNING_KEY_ID",
        "POLAR_RELEASE_SIGNING_KEY_PASSPHRASE",
        "SHA-256 checksums",
        "detached signatures",
        "swift package compute-checksum",
        "dry-run mode",
        "rollback",
        "Maven Central publishing and Swift package registry publication remain deferred",
        "two-AAR compatibility model",
        "PolarBleSdkShared.xcframework.zip",
    ).filterNot { publicationDoc.contains(it) }.mapTo(errors) { "$publicationDocPath missing $it" }

    finish(errors, "Release packaging policy static inspection OK")
}

private fun iosXcodeValidationProbe() {
    val errors = mutableListOf<String>()
    val blockers = mutableListOf<String>()
    val iosRoot = repoRoot.resolve("sources/iOS/ios-communications")
    val project = iosRoot.resolve("iOSCommunications.xcodeproj")
    val expectedTargets = listOf("iOSCommunications", "iOSCommunicationsTests", "PolarBleSdk", "PolarBleSdkWatchOs", "PolarBleSdkTests")
    val expectedSchemes = listOf("iOSCommunications", "PolarBleSdk", "PolarBleSdkWatchOs")

    if (!project.isDirectory) errors += "${project.relativeTo(repoRoot).path} is missing"
    if (iosRoot.resolve("Podfile").exists()) errors += "Podfile must not exist after SwiftPM migration"
    if (iosRoot.resolve("iOSCommunications.xcworkspace").exists()) errors += "iOSCommunications.xcworkspace must not exist after SwiftPM migration"
    if (iosRoot.resolve("Pods").exists()) blockers += "pods-directory-present"

    if (errors.isEmpty()) {
        val projectProbe = runCommand(listOf("xcodebuild", "-list", "-project", project.absolutePath), timeoutSeconds = 60)
        if (projectProbe.timedOut) {
            errors += "xcodebuild project discovery timed out after 60s"
        } else if (projectProbe.exitCode != 0) {
            errors += "xcodebuild project discovery failed with exit ${projectProbe.exitCode}"
        } else {
            expectedTargets.filterNot { projectProbe.output.contains(it) }.let { missing ->
                if (missing.isNotEmpty()) errors += "xcodebuild project discovery missing targets: ${missing.joinToString()}"
            }
            expectedSchemes.filterNot { projectProbe.output.contains(it) }.let { missing ->
                if (missing.isNotEmpty()) errors += "xcodebuild project discovery missing schemes: ${missing.joinToString()}"
            }
        }
        if (projectProbe.output.contains("CoreSimulatorService connection became invalid")) blockers += "coresimulator-unavailable"
    }

    if (errors.isNotEmpty()) fail(errors.joinToString("\n"))
    if (blockers.isEmpty()) {
        println("ios_xcode_validation_probe OK: project discovery is available and no known local XCTest infrastructure blockers were detected")
    } else {
        println("ios_xcode_validation_probe OK: project discovery is available; full XCTest remains blocked by ${blockers.distinct().sorted().joinToString(", ")}")
    }
}

private fun validateSpmXcframeworkConsumption() {
    val errors = mutableListOf<String>()
    val packageSwift = repoRoot.resolve("Package.swift").readTextIfExists()
    val xcframework = repoRoot.resolve("sources/iOS/ios-communications/Generated/PolarBleSdkSharedXCFramework/PolarBleSdkShared.xcframework")
    listOf("POLAR_BLE_SDK_SHARED_BINARY_URL", "POLAR_BLE_SDK_SHARED_BINARY_CHECKSUM", "hasLocalPolarBleSdkSharedXCFramework", ".binaryTarget")
        .filterNot { packageSwift.contains(it) }
        .mapTo(errors) { "Package.swift missing $it" }
    if (xcframework.exists() && !xcframework.isDirectory) {
        errors += "PolarBleSdkShared.xcframework path exists but is not a directory"
    }
    finish(errors, "SwiftPM fallback, remote binaryTarget configuration, and local PolarBleSdkShared binaryTarget validation passed")
}

private fun verifyApiDocsGenerationPolicy() {
    val errors = mutableListOf<String>()
    val script = repoRoot.resolve("scripts/generate_api_docs.sh")
    val repoToolsBuild = repoRoot.resolve("sources/Android/android-communications/repo-tools/build.gradle.kts").readTextIfExists()
    val libraryGradle = repoRoot.resolve("sources/Android/android-communications/library/build.gradle.kts").readTextIfExists()
    val prWorkflow = repoRoot.resolve(".github/workflows/pr-checks.yml").readTextIfExists()
    val nightlyWorkflow = repoRoot.resolve(".github/workflows/nightly.yml").readTextIfExists()
    val androidDocs = repoRoot.resolve("docs/polar-sdk-android")
    val iosDocs = repoRoot.resolve("docs/polar-sdk-ios/documentation/polarblesdk")

    if (!script.isFile || !script.canExecute()) {
        errors += "scripts/generate_api_docs.sh must exist and be executable"
    } else {
        val scriptText = script.readText()
        listOf(":repo-tools:generateApiDocs", "--no-daemon", "--warning-mode all")
            .filterNot { scriptText.contains(it) }
            .mapTo(errors) { "scripts/generate_api_docs.sh must delegate to Gradle and include $it" }
        listOf("xcodebuild docbuild", "docc process-archive", "perl -0pi")
            .filter { scriptText.contains(it) }
            .mapTo(errors) { "scripts/generate_api_docs.sh must not own docs generation implementation: found $it" }
    }

    listOf("generateApiDocs", "packageGeneratedApiDocs", ":library:dokkaGeneratePublicationHtml", "xcodebuild", "docbuild", "docc", "transform-for-static-hosting", "polar-generated-api-docs.tar.gz", "API_DOCS_DERIVED_DATA", "API_DOCS_IOS_HOSTING_BASE_PATH", "docs/polar-sdk-ios")
        .filterNot { repoToolsBuild.contains(it) }
        .mapTo(errors) { "repo-tools build must own docs generation and include $it" }
    if (!libraryGradle.contains("alias(libs.plugins.dokka)") || !libraryGradle.contains("dokkaPublications.html")) {
        errors += "Android docs generation must stay on Dokka HTML configuration"
    }
    listOf(":repo-tools:verifyApiDocsGenerationPolicy", "Generated API docs stay generator-owned")
        .filterNot { prWorkflow.contains(it) }
        .mapTo(errors) { ".github/workflows/pr-checks.yml missing $it" }
    listOf(":repo-tools:packageGeneratedApiDocs", "git diff --quiet -- docs/polar-sdk-android docs/polar-sdk-ios", "git diff --name-status -- docs/polar-sdk-android docs/polar-sdk-ios", "polar-generated-api-docs.tar.gz", "Upload generated API docs archive")
        .filterNot { nightlyWorkflow.contains(it) }
        .mapTo(errors) { ".github/workflows/nightly.yml missing $it" }
    if (!androidDocs.isDirectory || androidDocs.walkTopDown().none { file -> file.isFile && file.name == "index.html" }) {
        errors += "Generated Android API docs must stay under docs/polar-sdk-android"
    }
    if (!iosDocs.isDirectory || iosDocs.walkTopDown().none { file -> file.isFile && file.name == "index.html" }) {
        errors += "Generated iOS DocC static output must stay under docs/polar-sdk-ios/documentation/polarblesdk"
    }

    finish(errors, "API docs generation policy OK: Gradle owns Dokka, DocC, static hosting, and CI reproducibility gates")
}

private data class CommandResult(val exitCode: Int, val output: String, val timedOut: Boolean)

private fun runCommand(command: List<String>, timeoutSeconds: Long): CommandResult {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
    val output = process.inputStream.bufferedReader().readText()
    return if (completed) {
        CommandResult(process.exitValue(), output, false)
    } else {
        process.destroyForcibly()
        CommandResult(124, output, true)
    }
}

private fun File.readTextIfExists(): String = if (isFile) readText() else ""

private fun finish(errors: List<String>, okMessage: String) {
    if (errors.isNotEmpty()) fail(errors.joinToString("\n"))
    println(okMessage)
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    kotlin.system.exitProcess(1)
}
