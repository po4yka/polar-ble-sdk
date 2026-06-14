import org.gradle.api.tasks.bundling.Compression

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

dependencies {
    implementation(libs.kotlin.stdlib)
}

ktlint {
    filter {
        include("**/repo-tools/src/main/kotlin/**/*.kt")
        exclude("**/build/**")
    }
}

tasks.matching { task -> task.name.contains("KotlinScript") }.configureEach {
    enabled = false
}

val repositoryRoot = rootProject.projectDir.parentFile.parentFile.parentFile
val iosProject = repositoryRoot.resolve("sources/iOS/ios-communications/iOSCommunications.xcodeproj")
val iosDocsOutput = repositoryRoot.resolve("docs/polar-sdk-ios")

fun runApiDocsCommand(command: List<String>) {
    val process = ProcessBuilder(command)
        .directory(repositoryRoot)
        .inheritIO()
        .start()
    val exitCode = process.waitFor()
    if (exitCode != 0) {
        throw GradleException("API docs command failed with exit $exitCode: ${command.joinToString(" ")}")
    }
}

fun repoToolTask(taskName: String, commandName: String) {
    tasks.register<JavaExec>(taskName) {
        dependsOn(tasks.named("classes"))
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("com.polar.tools.RepoToolsKt")
        args(commandName)
        workingDir = repositoryRoot
        systemProperty("polar.repo.root", repositoryRoot.absolutePath)
    }
}

repoToolTask("kmpNonGradleChecks", "kmpNonGradleChecks")
repoToolTask("verifyReleasePackagingPolicy", "verifyReleasePackagingPolicy")
repoToolTask("iosXcodeValidationProbe", "iosXcodeValidationProbe")
repoToolTask("validateSpmXcframeworkConsumption", "validateSpmXcframeworkConsumption")
repoToolTask("verifyApiDocsGenerationPolicy", "verifyApiDocsGenerationPolicy")
repoToolTask("validateGeneratedXcodeProject", "validateGeneratedXcodeProject")

tasks.register("generateApiDocs") {
    group = "documentation"
    description = "Generate Android Dokka and iOS DocC static API documentation."
    dependsOn(":library:dokkaGeneratePublicationHtml")
    inputs.files(
        repositoryRoot.resolve("Package.swift"),
        iosProject,
        repositoryRoot.resolve("sources/iOS/ios-communications/Sources"),
        repositoryRoot.resolve("sources/Android/android-communications/library/src/sdk"),
    )
    outputs.dirs(
        repositoryRoot.resolve("docs/polar-sdk-android"),
        iosDocsOutput,
    )

    doLast {
        val derivedData = providers.environmentVariable("API_DOCS_DERIVED_DATA").orElse("/tmp/polar-api-docs").get()
        val hostingBasePath = providers.environmentVariable("API_DOCS_IOS_HOSTING_BASE_PATH").orElse("/polar-ble-sdk/polar-sdk-ios").get()
        val docsArchive = File(derivedData, "Build/Products/Debug-iphoneos/PolarBleSdk.doccarchive")

        delete(derivedData)
        runApiDocsCommand(
            listOf(
                "xcodebuild",
                "docbuild",
                "-scheme", "PolarBleSdk",
                "-destination", "generic/platform=iOS",
                "-derivedDataPath", derivedData,
                "-project", iosProject.absolutePath,
                "CODE_SIGNING_ALLOWED=NO",
                "-quiet",
            )
        )
        runApiDocsCommand(
            listOf(
                "xcrun",
                "docc",
                "process-archive",
                "transform-for-static-hosting",
                docsArchive.absolutePath,
                "--hosting-base-path", hostingBasePath,
                "--output-path", iosDocsOutput.absolutePath,
            )
        )
        fileTree(iosDocsOutput) {
            include("**/*.html")
        }.forEach { html ->
            val updated = html.readText()
                .replace("var baseUrl = \"/\"", "var baseUrl = \"$hostingBasePath/\"")
                .replace(Regex("""(href|src)="/(?!/)"""), "$1=\"$hostingBasePath/")
            html.writeText(updated)
        }
    }
}

tasks.register<Tar>("packageGeneratedApiDocs") {
    group = "documentation"
    description = "Package generated API documentation into one compressed CI artifact."
    dependsOn("generateApiDocs")
    archiveFileName.set("polar-generated-api-docs.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("api-docs-artifacts"))
    compression = Compression.GZIP
    from(repositoryRoot.resolve("docs/polar-sdk-android")) {
        into("polar-sdk-android")
    }
    from(repositoryRoot.resolve("docs/polar-sdk-ios")) {
        into("polar-sdk-ios")
    }
}
