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
        "validateGeneratedXcodeProject" -> validateGeneratedXcodeProject()
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
        "sources/iOS/ios-communications/project.yml",
        "scripts/validate_generated_xcode_project.sh",
        "scripts/lint_check.sh",
        ".swiftlint.yml",
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

    listOf(".github/workflows/pr-checks.yml", ".github/workflows/nightly.yml")
        .filterNot { path ->
            val workflow = repoRoot.resolve(path).readTextIfExists()
            workflow.contains(":lintCheck") && workflow.contains("swiftlint lint --strict --config .swiftlint.yml")
        }
        .mapTo(errors) { "$it must run Kotlin and Swift lint check-mode in CI" }

    val validationDoc = repoRoot.resolve("documentation/KmpValidationCommands.md").readTextIfExists()
    listOf(":repo-tools:kmpNonGradleChecks", ":repo-tools:iosXcodeValidationProbe", ":repo-tools:validateGeneratedXcodeProject", ":repo-tools:verifyApiDocsGenerationPolicy", ":lintCheck", "scripts/lint_check.sh", ".swiftlint.yml", "SwiftLint check-mode", "Kotlin lint check-mode", "Swift Package Manager", "package_kmp_xcframework.sh", "ci_xcodebuild_test.sh", "fast", "full", "firmware", "PolarBleApiImplTests", "Generated Protobuf Ownership", "scripts/generate_swift_protobuf.sh")
        .filterNot { validationDoc.contains(it) }
        .mapTo(errors) { "documentation/KmpValidationCommands.md missing $it" }

    listOf(".github/workflows/pr-checks.yml", ".github/workflows/nightly.yml")
        .filterNot { path ->
            val workflow = repoRoot.resolve(path).readTextIfExists()
            workflow.contains("ci_xcodebuild_test.sh") && workflow.contains(" fast")
        }
        .mapTo(errors) { "$it must run the fast iOS XCTest shard in CI" }

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

private fun validateGeneratedXcodeProject() {
    val errors = mutableListOf<String>()
    val iosRoot = repoRoot.resolve("sources/iOS/ios-communications")
    val spec = iosRoot.resolve("project.yml")
    val validator = repoRoot.resolve("scripts/validate_generated_xcode_project.sh")
    val workflow = iosRoot.resolve("XcodeProjectWorkflow.md").readTextIfExists()

    if (!spec.isFile) {
        errors += "sources/iOS/ios-communications/project.yml must define the generated Xcode project model"
    } else {
        val specText = spec.readText()
        listOf("SwiftProtobuf", "ZIPFoundation", "Build PolarBleSdkShared KMP Framework", "POLAR_KMP_SHARED_REQUIRED", "PolarBleSdkWatchOs", "PolarBleSdkTests.xctestplan")
            .filterNot { specText.contains(it) }
            .mapTo(errors) { "project.yml missing $it" }
    }
    if (!validator.isFile || !validator.canExecute()) {
        errors += "scripts/validate_generated_xcode_project.sh must exist and be executable"
    } else {
        val validatorText = validator.readText()
        listOf("ManualBleTransportContractsTest.swift")
            .filterNot { validatorText.contains(it) }
            .mapTo(errors) {
                "scripts/validate_generated_xcode_project.sh missing generated project registration guard for $it"
            }
    }
    listOf("project.yml", "XcodeGen", "validate_generated_xcode_project.sh", ":repo-tools:validateGeneratedXcodeProject")
        .filterNot { workflow.contains(it) }
        .mapTo(errors) { "XcodeProjectWorkflow.md missing $it" }

    if (errors.isEmpty()) {
        val result = runCommand(listOf("sh", validator.absolutePath), timeoutSeconds = 120)
        if (result.timedOut) {
            errors += "generated Xcode project validation timed out after 120s"
        } else if (result.exitCode != 0) {
            errors += "generated Xcode project validation failed with exit ${result.exitCode}:\n${result.output}"
        }
    }

    finish(errors, "Generated Xcode project model OK: XcodeGen spec, package dependencies, KMP phases, and schemes validate in a temporary project")
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
        "IOS_PROJECT: sources/iOS/ios-communications/iOSCommunications.xcodeproj",
        "ci_xcodebuild_build.sh \"\${IOS_PROJECT}\"",
        "scripts/verify_android_example_aar_consumption.sh",
        "scripts/verify_android_shared_maven_metadata.sh",
        "package_kmp_xcframework.sh",
        "PolarBleSdkShared.xcframework.zip",
        "swift package compute-checksum",
        "actions/upload-artifact@v4",
        "release_tag",
        "artifact_run_id",
        "publish_to_github_release",
        "github-release-assets",
        "environment: release-publication",
        "contents: write",
        "POLAR_RELEASE_SIGNING_KEY_ASC",
        "POLAR_RELEASE_SIGNING_KEY_ID",
        "POLAR_RELEASE_SIGNING_KEY_PASSPHRASE",
        "scripts/prepare_github_release_assets.sh",
        "gh run download",
        "gh release create",
        "gh release upload",
        "--draft",
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
            "package-registry credentials remain absent",
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
        "draft GitHub Release",
        "publish_to_github_release",
        "artifact_run_id",
        "release manifest",
        "rollback",
        "Maven Central publishing and Swift package registry publication remain deferred",
        "two-AAR compatibility model",
        "PolarBleSdkShared.xcframework.zip",
    ).filterNot { publicationDoc.contains(it) }.mapTo(errors) { "$publicationDocPath missing $it" }

    val publicationScript = repoRoot.resolve("scripts/prepare_github_release_assets.sh").readTextIfExists()
    listOf(
        "polar-ble-sdk.aar",
        "polar-ble-sdk-shared.aar",
        "PolarBleSdkShared.xcframework.zip",
        "PolarBleSdkShared.xcframework.zip.checksum",
        "SHA256SUMS",
        "release-manifest.json",
        "gpg --batch",
        "shasum -a 256",
    ).filterNot { publicationScript.contains(it) }.mapTo(errors) { "scripts/prepare_github_release_assets.sh missing $it" }

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
    val docsGitignore = repoRoot.resolve("docs/.gitignore").readTextIfExists()
    val swiftProtobufDir = repoRoot.resolve("sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/impl/protobuf")
    val androidProtoDir = repoRoot.resolve("sources/Android/android-communications/library/src/sdk/proto")
    val swiftProtobufGenerator = repoRoot.resolve("scripts/generate_swift_protobuf.sh")

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
    listOf(""".*\\.androidcommunications.*""", """com\\.polar\\.sdk\\.impl(\\..*)?""")
        .filterNot { libraryGradle.contains(it) }
        .mapTo(errors) { "Android Dokka public docs surface must suppress $it" }
    val swiftImplPublicSymbols = repoRoot.resolve("sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/impl")
        .walkTopDown()
        .filter { it.isFile && it.extension == "swift" && !it.name.endsWith(".pb.swift") }
        .flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (Regex("""^\s*(public|open)\s+(class|struct|enum|protocol|actor|func|var|let|typealias|extension)\b""").containsMatchIn(line)) {
                    "${file.relativeTo(repoRoot).path}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }
        .toList()
    if (swiftImplPublicSymbols.isNotEmpty()) {
        errors += "Swift implementation package must not expose public API symbols: ${swiftImplPublicSymbols.joinToString()}"
    }
    listOf(":repo-tools:verifyApiDocsGenerationPolicy", "Generated API docs stay untracked", "git ls-files docs/polar-sdk-android docs/polar-sdk-ios")
        .filterNot { prWorkflow.contains(it) }
        .mapTo(errors) { ".github/workflows/pr-checks.yml missing $it" }
    listOf(":repo-tools:packageGeneratedApiDocs", "Generated API docs stay untracked", "git ls-files docs/polar-sdk-android docs/polar-sdk-ios", "polar-generated-api-docs.tar.gz", "Upload generated API docs archive")
        .filterNot { nightlyWorkflow.contains(it) }
        .mapTo(errors) { ".github/workflows/nightly.yml missing $it" }
    listOf("/polar-sdk-android/", "/polar-sdk-ios/")
        .filterNot { docsGitignore.contains(it) }
        .mapTo(errors) { "docs/.gitignore must ignore generated docs output $it" }
    listOf("docs/polar-sdk-android", "docs/polar-sdk-ios")
        .filter { gitTrackedFilesUnder(it).isNotEmpty() }
        .mapTo(errors) { "Generated API docs must not be checked in under $it" }
    if (!swiftProtobufGenerator.isFile || !swiftProtobufGenerator.canExecute()) {
        errors += "scripts/generate_swift_protobuf.sh must exist and be executable"
    } else {
        val generatorText = swiftProtobufGenerator.readText()
        listOf("proto_version", "swift_protobuf_version", "PROTOC", "PROTOC_GEN_SWIFT", "--swift_opt=Visibility=Public", "protoc-gen-swift version must match Package.swift SwiftProtobuf")
            .filterNot { generatorText.contains(it) }
            .mapTo(errors) { "scripts/generate_swift_protobuf.sh missing generated protobuf ownership term $it" }
    }
    val androidProtoReadme = androidProtoDir.resolve("README.md").readTextIfExists()
    listOf("protobuf schema source of truth", "scripts/generate_swift_protobuf.sh", "Do not place generated protobuf output")
        .filterNot { androidProtoReadme.contains(it) }
        .mapTo(errors) { "sources/Android/android-communications/library/src/sdk/proto/README.md missing $it" }
    val swiftProtobufReadme = swiftProtobufDir.resolve("README.md").readTextIfExists()
    listOf("checked-in Swift protobuf output", "Do not hand-edit", "scripts/generate_swift_protobuf.sh", "Package.swift")
        .filterNot { swiftProtobufReadme.contains(it) }
        .mapTo(errors) { "sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/impl/protobuf/README.md missing $it" }
    val generatedSwiftProtobufFiles = swiftProtobufDir.listFiles { file -> file.isFile && file.name.endsWith(".pb.swift") }
        ?.sortedBy { it.name }
        .orEmpty()
    if (generatedSwiftProtobufFiles.isEmpty()) {
        errors += "Swift generated protobuf directory must contain checked-in *.pb.swift output"
    }
    generatedSwiftProtobufFiles.flatMap { file ->
        val text = file.readText()
        listOf("// DO NOT EDIT.", "swift-format-ignore-file", "swiftlint:disable all", "Generated by the Swift generator plugin for the protocol buffer compiler.", "Source:")
            .filterNot { text.contains(it) }
            .map { term -> "${file.relativeTo(repoRoot).path} missing generated header term $term" }
    }.let(errors::addAll)
    if (androidProtoDir.listFiles { file -> file.isFile && file.name.endsWith(".proto") }.isNullOrEmpty()) {
        errors += "Android SDK protobuf schema directory must contain checked-in *.proto schema files"
    }

    finish(errors, "Generated ownership policy OK: Gradle owns API docs, generated docs stay untracked, and Swift protobuf output is tied to schema-owned regeneration")
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

private fun gitTrackedFilesUnder(path: String): List<String> {
    val result = runCommand(listOf("git", "ls-files", path), timeoutSeconds = 30)
    if (result.exitCode != 0 || result.timedOut) return listOf("$path:git-ls-files-failed")
    return result.output.lineSequence().filter { it.isNotBlank() }.toList()
}

private fun finish(errors: List<String>, okMessage: String) {
    if (errors.isNotEmpty()) fail(errors.joinToString("\n"))
    println(okMessage)
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    kotlin.system.exitProcess(1)
}
