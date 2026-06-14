package com.polar.sdk.api.model.utils

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KmpSharedBoundaryPolicyTest {

    @Test
    fun `Android PMD model parsers stay behind runtime adapter boundary`() {
        val root = findRepositoryRoot()
        val modelDirectory = root.resolve("sources/Android/android-communications/library/src/main/java/com/polar/androidcommunications/api/ble/model/gatt/client/pmd/model")
        val retiredFrameAdapter = modelDirectory.resolve("PolarSharedPmdFrameAdapter.kt")
        val directSharedImports = modelDirectory
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (line.startsWith("import com.polar.shared")) {
                        "${file.relativeTo(root).path}:${index + 1}:$line"
                    } else {
                        null
                    }
                }
            }
            .toList()
        val runtimeAdapter = root.resolve("sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/impl/utils/PolarRuntimePlannerAdapter.kt").readText()
        val missingAdapterMethods = PMD_SENSOR_RUNTIME_ADAPTER_METHODS.filterNot { method -> runtimeAdapter.contains("fun $method(") }

        assertTrue(
            "Android PMD model production files must not import shared parser DTOs directly; use PolarRuntimePlannerAdapter DTO bridges instead: $directSharedImports",
            directSharedImports.isEmpty()
        )
        assertTrue(
            "PolarSharedPmdFrameAdapter.kt must stay retired; parser bridge ownership belongs in PolarRuntimePlannerAdapter",
            !retiredFrameAdapter.exists()
        )
        assertTrue(
            "PolarRuntimePlannerAdapter must keep parser bridge methods for every migrated PMD sensor family: $missingAdapterMethods",
            missingAdapterMethods.isEmpty()
        )
    }

    @Test
    fun `byte level common dependency deferrals stay explicit until production common codecs exist`() {
        val root = findRepositoryRoot()
        val sharedBuild = root.resolve("sources/Android/android-communications/shared/build.gradle.kts").readText()
        val backlog = root.resolve("documentation/KmpFullCoverageTddBacklog.md").readText()
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val remainingWork = root.resolve("documentation/KmpPreMigrationRemainingWork.md").readText()
        val trainingSessionPayloadRead = root.resolve("testdata/golden-vectors/sdk/training-session/payload-read-policy.json").readText()
        val trainingSessionPayloadParser = root.resolve("testdata/golden-vectors/sdk/training-session/payload-parser-policy.json").readText()
        val trainingSessionReadiness = root.resolve("testdata/golden-vectors/sdk/training-session/training-session-readiness.json").readText()
        val userDeviceSettingsReadiness = root.resolve("testdata/golden-vectors/sdk/user-device-settings/settings-model-readiness.json").readText()
        val pmdSecretReadiness = root.resolve("testdata/golden-vectors/protocol/pmd/secret-readiness.json").readText()
        val restCompressionReadiness = root.resolve("testdata/golden-vectors/sdk/rest-service/rest-event-compression-readiness.json").readText()
        val watchFaceReadiness = root.resolve("testdata/golden-vectors/sdk/watch-face/watch-face-readiness.json").readText()
        val violations = mutableListOf<String>()

        SHARED_COMMON_PRODUCTION_CODEC_DEPENDENCY_TERMS
            .filter { term -> sharedBuild.contains(term) }
            .mapTo(violations) { term -> "shared/build.gradle.kts declares $term before this policy is updated with production common codec ownership evidence" }
        BYTE_LEVEL_COMMON_DEPENDENCY_DEFERRAL_TERMS.forEach { (artifact, requiredTerms) ->
            val text = when (artifact) {
                "KmpFullCoverageTddBacklog.md" -> backlog
                "KmpCoverageInventory.md" -> inventory
                "KmpPreMigrationRemainingWork.md" -> remainingWork
                "payload-read-policy.json" -> trainingSessionPayloadRead
                "payload-parser-policy.json" -> trainingSessionPayloadParser
                "training-session-readiness.json" -> trainingSessionReadiness
                "settings-model-readiness.json" -> userDeviceSettingsReadiness
                "secret-readiness.json" -> pmdSecretReadiness
                "rest-event-compression-readiness.json" -> restCompressionReadiness
                "watch-face-readiness.json" -> watchFaceReadiness
                else -> ""
            }
            requiredTerms
                .filterNot { term -> text.contains(term) }
                .mapTo(violations) { term -> "$artifact must keep byte-level common dependency deferral term $term" }
        }

        assertTrue(
            "Byte-level protobuf/gzip/crypto/codec migration must remain explicitly deferred until production common dependencies and byte-identical ownership are added: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `training session generated public protobuf reconstruction stays out of shared common production`() {
        val root = findRepositoryRoot()
        val sharedMain = root.resolve("sources/Android/android-communications/shared/src/commonMain/kotlin")
        val generatedTrainingProtoTerms = listOf(
            "fi.polar.remote.representation.protobuf.TrainingSession",
            "fi.polar.remote.representation.protobuf.Training.",
            "fi.polar.remote.representation.protobuf.ExerciseRouteSamples",
            "fi.polar.remote.representation.protobuf.ExerciseRouteSamples2",
            "fi.polar.remote.representation.protobuf.ExerciseSamples",
            "fi.polar.remote.representation.protobuf.ExerciseSamples2",
            "TrainingSession.PbTrainingSession",
            "Training.PbExerciseBase",
            "ExerciseRouteSamples.PbExerciseRouteSamples",
            "ExerciseRouteSamples2.PbExerciseRouteSamples2",
            "ExerciseSamples.PbExerciseSamples",
            "ExerciseSamples2.PbExerciseSamples2",
            "Data_PbTrainingSession",
            "Data_PbExerciseBase",
            "Data_PbExerciseRouteSamples",
            "Data_PbExerciseRouteSamples2",
            "Data_PbExerciseSamples",
            "Data_PbExerciseSamples2"
        )
        val violations = sharedMain.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                generatedTrainingProtoTerms
                    .filter { term -> text.contains(term) }
                    .map { term -> "${file.relativeTo(root).path} references generated training protobuf public model term $term" }
            }
            .toList()

        assertTrue(
            "Shared common training-session code may parse selected protobuf fields and plan neutral reconstruction slots, but generated public protobuf object construction must stay platform-owned: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `KMP common vector helper remains gated until shared common tests exist`() {
        val root = findRepositoryRoot()
        val checklist = root.resolve("documentation/KmpMigrationChecklist.md").readText()
        val validationCommands = root.resolve("documentation/KmpValidationCommands.md").readText()
        val completedItems = CHECKED_CHECKLIST_ITEM.findAll(checklist)
            .map { match -> match.groupValues[1].trimEnd('.') }
            .toSet()
        val commonTestSourceSets = root
            .walkTopDown()
            .filter { file -> file.isDirectory && file.name == "commonTest" }
            .map { file -> file.relativeTo(root).path }
            .toList()

        if (commonTestSourceSets.isEmpty()) {
            val violations = mutableListOf<String>()
            if (completedItems.contains("Add vector-loading helpers for KMP common tests")) {
                violations += "KMP common vector-loading helpers cannot be completed before a commonTest source set exists"
            }
            if (!validationCommands.contains("No shared KMP module exists yet")) {
                violations += "KmpValidationCommands.md must state that no shared KMP module exists yet"
            }
            if (!checklist.contains("No shared KMP module or `commonTest` source set exists yet")) {
                violations += "KmpMigrationChecklist.md must explain why KMP common vector-loading helpers remain open"
            }
            assertTrue(
                "KMP common helper checklist state must match the current absence of a shared module: $violations",
                violations.isEmpty()
            )
        } else {
            val violations = mutableListOf<String>()
            if (!completedItems.contains("Add vector-loading helpers for KMP common tests")) {
                violations += "commonTest exists but KMP common vector-loading helpers are not completed"
            }
            if (!validationCommands.contains(":shared:jvmTest") || !validationCommands.contains("commonTest")) {
                violations += "KmpValidationCommands.md must name the executable shared commonTest command once commonTest exists"
            }
            KMP_COMMON_VECTOR_HELPER_ARTIFACTS
                .filterNot { relativePath -> root.resolve(relativePath).isFile }
                .mapTo(violations) { relativePath -> "missing KMP common vector helper artifact $relativePath" }
            val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GoldenVectorTestDataCommonTest.kt")
            val commonTestText = if (commonTest.isFile) commonTest.readText() else ""
            if (commonTest.isFile && !commonTestText.contains("polar-device-uuid-valid")) {
                violations += "${commonTest.relativeTo(root).path} must load a shared golden vector"
            }
            if (commonTest.isFile && (!commonTestText.contains("does-not-exist.json") || !commonTestText.contains("assertFailsWith"))) {
                violations += "${commonTest.relativeTo(root).path} must prove missing fixture paths fail fast"
            }
            assertTrue(
                "KMP common helper checklist state must match existing commonTest source sets $commonTestSourceSets: $violations",
                violations.isEmpty()
            )
        }
    }

    @Test
    fun `minimal shared KMP module stays behavior free and test executable`() {
        val root = findRepositoryRoot()
        val checklist = root.resolve("documentation/KmpMigrationChecklist.md").readText()
        val validationCommands = root.resolve("documentation/KmpValidationCommands.md").readText()
        val completedItems = CHECKED_CHECKLIST_ITEM.findAll(checklist)
            .map { match -> match.groupValues[1].trimEnd('.') }
            .toSet()
        val settings = root.resolve("sources/Android/android-communications/settings.gradle.kts").readText()
        val sharedBuild = root.resolve("sources/Android/android-communications/shared/build.gradle.kts").readText()
        val sharedMarker = root.resolve("sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/SharedModule.kt")
        val violations = mutableListOf<String>()

        if (!settings.contains("include(\":shared\")")) {
            violations += "settings.gradle.kts must include :shared"
        }
        if (!sharedBuild.contains("alias(libs.plugins.kotlin.multiplatform)")) {
            violations += "shared/build.gradle.kts must apply Kotlin Multiplatform"
        }
        if (!sharedBuild.contains("jvm()")) {
            violations += "shared/build.gradle.kts must keep a JVM target so commonTest is executable now"
        }
        if (!sharedMarker.isFile || !sharedMarker.readText().contains("object SharedModule")) {
            violations += "shared commonMain must retain the module marker"
        }
        if (!completedItems.contains("Add a minimal shared KMP module without moving behavior")) {
            violations += "KmpMigrationChecklist.md must mark the minimal shared module complete"
        }
        if (!completedItems.contains("Add `commonMain`, `commonTest`, and platform-specific test source sets only as needed")) {
            violations += "KmpMigrationChecklist.md must mark minimal source sets complete"
        }
        if (!completedItems.contains("Add a trivial common test and run it in local validation")) {
            violations += "KmpMigrationChecklist.md must mark the trivial common test complete"
        }
        if (!validationCommands.contains(":shared:jvmTest")) {
            violations += "KmpValidationCommands.md must document :shared:jvmTest"
        }

        assertTrue(
            "Shared KMP module must retain the minimal marker and expose an executable commonTest gate: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `shared common tests avoid JVM Android and unsigned-only APIs`() {
        val root = findRepositoryRoot()
        val commonTestRoot = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest")
        val portabilityViolations = commonTestRoot
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .flatMap { file ->
                val relativePath = file.relativeTo(root).path
                file.readLines().mapIndexedNotNull { index, line ->
                    if (!COMMON_TEST_PORTABILITY_FORBIDDEN.containsMatchIn(line)) {
                        null
                    } else if (COMMON_TEST_PORTABILITY_ALLOWED_LINES.any { allowed -> relativePath == allowed.file && line.contains(allowed.text) }) {
                        null
                    } else {
                        "$relativePath:${index + 1}: $line"
                    }
                }
            }
            .toList()

        assertTrue(
            "Shared commonTest files must stay portable before KMP migration; use common-safe APIs or add a reviewed allowlist entry for false positives: $portabilityViolations",
            portabilityViolations.isEmpty()
        )
    }

    @Test
    fun `shared common production code avoids platform only APIs`() {
        val root = findRepositoryRoot()
        val commonMainRoot = root.resolve("sources/Android/android-communications/shared/src/commonMain/kotlin")
        val migrationPlan = root.resolve("documentation/KmpMigrationPlan.md").readText()
        val portabilityViolations = commonMainRoot
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .flatMap { file ->
                val relativePath = file.relativeTo(root).path
                file.readLines().mapIndexedNotNull { index, line ->
                    if (COMMON_MAIN_PLATFORM_FORBIDDEN.containsMatchIn(line)) {
                        "$relativePath:${index + 1}: $line"
                    } else {
                        null
                    }
                }
            }
            .toList()
        val missingPlanTerms = COMMON_MAIN_PORTABILITY_PLAN_TERMS
            .filterNot { term -> migrationPlan.contains(term) }

        assertTrue(
            "Shared commonMain code must avoid platform-only APIs before production KMP migration: $portabilityViolations",
            portabilityViolations.isEmpty()
        )
        assertTrue(
            "KmpMigrationPlan.md must keep the common-code portability boundary explicit: $missingPlanTerms",
            missingPlanTerms.isEmpty()
        )
    }

    @Test
    fun `shared KMP module declares Android and Apple targets without production consumption`() {
        val root = findRepositoryRoot()
        val checklist = root.resolve("documentation/KmpMigrationChecklist.md").readText()
        val validationCommands = root.resolve("documentation/KmpValidationCommands.md").readText()
        val completedItems = CHECKED_CHECKLIST_ITEM.findAll(checklist)
            .map { match -> match.groupValues[1].trimEnd('.') }
            .toSet()
        val sharedBuild = root.resolve("sources/Android/android-communications/shared/build.gradle.kts").readText()
        val manifest = root.resolve("sources/Android/android-communications/shared/src/androidMain/AndroidManifest.xml")
        val violations = mutableListOf<String>()

        if (!sharedBuild.contains("alias(libs.plugins.android.kotlin.multiplatform.library)")) {
            violations += "shared/build.gradle.kts must apply com.android.kotlin.multiplatform.library for the Android KMP target"
        }
        if (!sharedBuild.contains("android {")) {
            violations += "shared/build.gradle.kts must declare the AGP 9 Android KMP target"
        }
        if (!sharedBuild.contains("iosX64()") || !sharedBuild.contains("iosArm64()") || !sharedBuild.contains("iosSimulatorArm64()") || !sharedBuild.contains("watchosX64()") || !sharedBuild.contains("watchosArm64()") || !sharedBuild.contains("watchosSimulatorArm64()")) {
            violations += "shared/build.gradle.kts must declare iOS and watchOS KMP framework targets"
        }
        if (!sharedBuild.contains("namespace = \"com.polar.shared\"") || !Regex("minSdk(?:Version)?\\s*(?:=)?\\s*26").containsMatchIn(sharedBuild)) {
            violations += "shared/build.gradle.kts must declare Android namespace and minSdk 26"
        }
        if (!manifest.isFile) {
            violations += "shared Android target must include a minimal AndroidManifest.xml"
        }
        if (!completedItems.contains("Configure Android and Apple targets")) {
            violations += "KmpMigrationChecklist.md must mark Android and Apple target configuration complete"
        }
        if (!validationCommands.contains("JVM, Android, and Apple targets")) {
            violations += "KmpValidationCommands.md must document shared target shape"
        }
        if (!validationCommands.contains(":shared:compileAndroidMain") || !validationCommands.contains(":shared:compileAndroidHostTest") || !validationCommands.contains(":shared:compileKotlinIosX64")) {
            violations += "KmpValidationCommands.md must document shared Android and iOS target compile gates"
        }
        if (root.resolve("sources/Android/android-communications/library/build.gradle.kts").readText().contains("project(\":shared\")") && !deviceIdSliceMigrated(root)) {
            violations += "Android library must not consume :shared without concrete migrated behavior evidence"
        }
        if (root.resolve("sources/iOS/ios-communications/Package.swift").takeIf { it.isFile }?.readText()?.contains("shared") == true) {
            violations += "iOS package must not consume shared before a behavior migration slice"
        }

        assertTrue(
            "Shared KMP module must declare Android and Apple targets without production consumption: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `shared artifact consumption contract is documented before production wiring`() {
        val root = findRepositoryRoot()
        val checklist = root.resolve("documentation/KmpMigrationChecklist.md").readText()
        val validationCommands = root.resolve("documentation/KmpValidationCommands.md").readText()
        val consumptionDocFile = root.resolve("documentation/KmpSharedArtifactConsumption.md")
        val packageSwift = root.resolve("Package.swift").readText()
        val packageScript = root.resolve("sources/iOS/ios-communications/scripts/package_kmp_xcframework.sh")
        val spmXcframeworkValidationScript = root.resolve("sources/iOS/ios-communications/scripts/validate_spm_xcframework_consumption.sh")
        val sharedBuild = root.resolve("sources/Android/android-communications/shared/build.gradle.kts").readText()
        val completedItems = CHECKED_CHECKLIST_ITEM.findAll(checklist)
            .map { match -> match.groupValues[1].trimEnd('.') }
            .toSet()
        val violations = mutableListOf<String>()

        if (!consumptionDocFile.isFile) {
            violations += "documentation/KmpSharedArtifactConsumption.md must exist"
        } else {
            val consumptionDoc = consumptionDocFile.readText()
            SHARED_CONSUMPTION_REQUIRED_TERMS
                .filterNot { term -> consumptionDoc.contains(term) }
                .mapTo(violations) { term -> "${consumptionDocFile.relativeTo(root).path} missing $term" }
        }
        if (!sharedBuild.contains("baseName = \"PolarBleSdkShared\"") || !sharedBuild.contains("isStatic = true")) {
            violations += "shared/build.gradle.kts must define the static PolarBleSdkShared framework artifact"
        }
        if (!sharedBuild.contains("maven-publish") || !sharedBuild.contains("localKmpReleaseValidation") || !sharedBuild.contains("local-maven-validation")) {
            violations += "shared/build.gradle.kts must define shared Gradle metadata validation for temporary local repository validation"
        }
        if (packageSwift.contains("PolarBleSdkShared") && (!packageSwift.contains(".binaryTarget") || !packageSwift.contains("PolarBleSdkShared.xcframework"))) {
            violations += "Package.swift must use an explicit PolarBleSdkShared.xcframework binaryTarget for SwiftPM shared consumption"
        }
        if (!packageSwift.contains("hasLocalPolarBleSdkSharedXCFramework") || !packageSwift.contains(".binaryTarget") || !packageSwift.contains("PolarBleSdkShared.xcframework")) {
            violations += "Package.swift must keep SwiftPM/watchOS shared consumption conditional on an explicit PolarBleSdkShared.xcframework binaryTarget"
        }
        val xcodeProject = root.resolve("sources/iOS/ios-communications/iOSCommunications.xcodeproj/project.pbxproj").readText()
        if (xcodeProject.contains("Pods") || xcodeProject.contains("[CP]") || xcodeProject.contains("PODS_ROOT")) {
            violations += "iOS Xcode project must not contain CocoaPods integration after SwiftPM migration"
        }
        if (!xcodeProject.contains("XCRemoteSwiftPackageReference") || !xcodeProject.contains("SwiftProtobuf") || !xcodeProject.contains("ZIPFoundation")) {
            violations += "iOS Xcode project must declare SwiftPM package dependencies for SwiftProtobuf and ZIPFoundation"
        }
        if (!packageScript.isFile || !packageScript.canExecute()) {
            violations += "package_kmp_xcframework.sh must exist and be executable"
        }
        if (!spmXcframeworkValidationScript.let { it.isFile && it.canExecute() }) {
            violations += "validate_spm_xcframework_consumption.sh must exist and be executable"
        } else {
            val spmXcframeworkValidationText = spmXcframeworkValidationScript.readText()
            listOf(
                "package_kmp_xcframework.sh",
                "swift package describe",
                "PolarBleSdkShared",
                "binaryTarget",
                "fallback-mode",
                "--manifest-cache none",
                "xcodebuild",
                "watchos"
            ).filterNot { term -> spmXcframeworkValidationText.contains(term) }
                .mapTo(violations) { term -> "validate_spm_xcframework_consumption.sh missing $term" }
        }
        if (!validationCommands.contains(":shared:bundleAndroidMainAar") || !validationCommands.contains(":shared:linkDebugFrameworkIosX64") || !validationCommands.contains("package_kmp_xcframework.sh --dry-run")) {
            violations += "KmpValidationCommands.md must document shared artifact smoke gates"
        }
        if (!validationCommands.contains("scripts/verify_android_shared_maven_metadata.sh")) {
            violations += "KmpValidationCommands.md must document shared Maven metadata validation"
        }
        if (!root.resolve("scripts/verify_android_shared_maven_metadata.sh").let { it.isFile && it.canExecute() }) {
            violations += "verify_android_shared_maven_metadata.sh must exist and be executable"
        }
        if (!completedItems.contains("Document how shared artifacts are consumed by Android and iOS modules")) {
            violations += "KmpMigrationChecklist.md must mark shared artifact consumption documentation complete"
        }
        if (root.resolve("sources/Android/android-communications/library/build.gradle.kts").readText().contains("implementation(project(\":shared\"))") && !deviceIdSliceMigrated(root)) {
            violations += "Android production consumption must name a migrated shared behavior slice"
        }
        if (root.resolve("sources/iOS/ios-communications/iOSCommunications.xcodeproj/project.pbxproj").readText().contains("PolarBleSdkShared.framework") && !iosSharedConsumptionMigrated(root)) {
            violations += "iOS production consumption must name a migrated shared behavior slice"
        }

        assertTrue(
            "Shared artifact consumption must be documented before production modules are wired: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `modern KMP stack audit keeps final ownership boundaries explicit`() {
        val root = findRepositoryRoot()
        val audit = root.resolve("documentation/KmpModernStackAudit.md").readText()
        val missingTerms = KMP_MODERN_STACK_AUDIT_REQUIRED_TERMS
            .filterNot { term -> audit.contains(term) }

        assertTrue(
            "KmpModernStackAudit.md must keep migrated, platform-owned, packaging-owned, and current validation boundaries explicit: $missingTerms",
            missingTerms.isEmpty()
        )
    }

    @Test
    fun `vectors excluded from common KMP declare migration policy rationale`() {
        val missingPolicy = loadAllGoldenVectors()
            .filter { vector ->
                val platforms = vector.json.getAsJsonObject("platforms")
                platforms.has("common") && !platforms.get("common").asBoolean
            }
            .filterNot { it.json.hasMigrationPolicyRationale() }
            .map { vector -> vector.file.relativeTo(findRepositoryRoot()).path }

        assertTrue(
            "Vectors with platforms.common=false must declare expected.commonDecision, platformExpectations.commonDecision, expected.commonRuntimePrototype, expected.commonWorkflowPrototype, or migrationOwnership: $missingPolicy",
            missingPolicy.isEmpty()
        )
    }

    @Test
    fun `runtime planning vectors name their executable consumers`() {
        val missingConsumers = loadSdkGoldenVectors()
            .filter { it.json.has("execution") || it.json.expectedObject().has("commonRuntimePrototype") || it.json.expectedObject().has("commonWorkflowPrototype") }
            .filterNot { it.json.hasConsumerTests() }
            .map { vector -> vector.file.relativeTo(findRepositoryRoot()).path }

        assertTrue(
            "Runtime or planning vectors must list consumerTests with at least one executable consumer: $missingConsumers",
            missingConsumers.isEmpty()
        )
    }

    @Test
    fun `vectors consumed by runtime policy tests declare runtime metadata`() {
        val root = findRepositoryRoot()
        val missingRuntimeMetadata = loadSdkGoldenVectors()
            .filter { vector -> vector.json.hasRuntimePolicyConsumer() }
            .filterNot { vector -> vector.json.isRuntimePlanningVector() }
            .map { vector -> vector.file.relativeTo(root).path }

        assertTrue(
            "Golden vectors consumed by runtime or fake-transport policy tests must declare execution or common runtime metadata: $missingRuntimeMetadata",
            missingRuntimeMetadata.isEmpty()
        )
    }

    @Test
    fun `runtime planning vectors name platform and common prototype consumers`() {
        val root = findRepositoryRoot()
        val incompleteConsumers = loadSdkGoldenVectors()
            .filter { it.json.isRuntimePlanningVector() }
            .flatMap { vector ->
                REQUIRED_RUNTIME_CONSUMER_TESTS
                    .filterNot { platform -> vector.json.hasNonEmptyConsumerTests(platform) }
                    .map { platform -> "${vector.file.relativeTo(root).path}: missing consumerTests.$platform" }
            }

        assertTrue(
            "Runtime or planning vectors must name Android, iOS, and commonPrototype consumers before shared migration: $incompleteConsumers",
            incompleteConsumers.isEmpty()
        )
    }

    @Test
    fun `runtime planning vectors name executable shared common consumers`() {
        val root = findRepositoryRoot()
        val missingSharedConsumers = loadSdkGoldenVectors()
            .filter { it.json.isRuntimePlanningVector() }
            .filterNot { vector -> vector.json.consumerTestsFor("commonPrototype").any { testName -> testName.startsWith("com.polar.sharedtest.") } }
            .map { vector -> vector.file.relativeTo(root).path }

        assertTrue(
            "Runtime planning vectors must name executable shared commonTest consumers before production KMP migration: $missingSharedConsumers",
            missingSharedConsumers.isEmpty()
        )
    }

    @Test
    fun `shared consumed runtime vectors do not keep stale future fake transport wording`() {
        val root = findRepositoryRoot()
        val staleVectors = loadSdkGoldenVectors()
            .filter { vector -> vector.json.consumerTestsFor("commonPrototype").any { testName -> testName.startsWith("com.polar.sharedtest.") } }
            .filter { vector ->
                val notes = vector.json.optionalStringField("notes") ?: return@filter false
                STALE_SHARED_RUNTIME_VECTOR_NOTES.any { stalePhrase -> notes.contains(stalePhrase) }
            }
            .map { vector -> vector.file.relativeTo(root).path }

        assertTrue(
            "Vectors with executable shared common consumers must not describe their coverage as future fake-transport-vector work: $staleVectors",
            staleVectors.isEmpty()
        )
    }

    @Test
    fun `declared vector consumers use known platforms and non-empty test names`() {
        val root = findRepositoryRoot()
        val invalidConsumers = loadAllGoldenVectors()
            .flatMap { vector ->
                vector.json.consumerTestShapeErrors()
                    .map { error -> "${vector.file.relativeTo(root).path}: $error" }
            }

        assertTrue(
            "Golden vector consumerTests must use known platforms and non-empty string test names: $invalidConsumers",
            invalidConsumers.isEmpty()
        )
    }

    @Test
    fun `declared vector consumers resolve to existing platform tests`() {
        val root = findRepositoryRoot()
        val missingConsumers = loadAllGoldenVectors()
            .flatMap { vector ->
                vector.json.consumerTestReferences().mapNotNull { consumer ->
                    val isResolved = when (consumer.platform) {
                        "android" -> root.androidTestFileFor(consumer.testName).isFile
                        "commonPrototype" -> root.commonPrototypeTestFileFor(consumer.testName).isFile
                        "ios" -> root.iosTestExists(consumer.testName)
                        else -> false
                    }
                    if (isResolved) {
                        null
                    } else {
                        "${vector.file.relativeTo(root).path}: ${consumer.platform}:${consumer.testName}"
                    }
                }
            }

        assertTrue(
            "Golden vector consumerTests must reference existing Android/common prototype or iOS tests: $missingConsumers",
            missingConsumers.isEmpty()
        )
    }

    @Test
    fun `declared vector consumers reference the vector they guard`() {
        val root = findRepositoryRoot()
        val missingReferences = loadAllGoldenVectors()
            .flatMap { vector ->
                vector.json.consumerTestReferences().mapNotNull { consumer ->
                    val referencesVector = root.consumerTestReferencesVector(consumer, vector)
                    if (referencesVector) {
                        null
                    } else {
                            "${vector.file.relativeTo(root).path}: ${consumer.platform}:${consumer.testName} must mention ${vector.json.get("id").asString}, ${vector.file.name}, the vector directory, or an owning readiness manifest"
                    }
                }
            }

        assertTrue(
            "Golden vector consumerTests must point to tests that explicitly reference the guarded vector, its vector directory, or an owning readiness manifest: $missingReferences",
            missingReferences.isEmpty()
        )
    }

    @Test
    fun `shared common vector consumers explicitly pin behavior case identifiers`() {
        val root = findRepositoryRoot()
        val missingBehaviorPins = loadAllGoldenVectors()
            .flatMap { vector ->
                val behaviorIds = vector.json.behaviorIds()
                if (behaviorIds.isEmpty()) {
                    emptyList()
                } else {
                    vector.json.consumerTestReferences()
                        .filter { consumer -> consumer.platform == "commonPrototype" && consumer.testName.startsWith("com.polar.sharedtest.") }
                        .flatMap { consumer ->
                            val testFile = root.commonPrototypeTestFileFor(consumer.testName)
                            if (!testFile.isFile) {
                                emptyList()
                            } else {
                                val testSource = testFile.readText()
                                behaviorIds
                                    .filterNot { behaviorId -> testSource.contains(behaviorId) }
                                    .map { behaviorId -> "${vector.file.relativeTo(root).path}: ${consumer.platform}:${consumer.testName} must explicitly reference behavior id $behaviorId" }
                            }
                        }
                }
            }

        assertTrue(
            "Shared common vector consumers must pin exact case/scenario/request identifiers instead of only iterating vectors implicitly: $missingBehaviorPins",
            missingBehaviorPins.isEmpty()
        )
    }

    @Test
    fun `shared common readiness consumers explicitly pin platform flags`() {
        val root = findRepositoryRoot()
        val missingPlatformPins = loadAllGoldenVectors()
            .filter { vector ->
                vector.json.getAsJsonObject("input")
                    ?.get("kind")
                    ?.asString
                    .orEmpty()
                    .contains("readiness", ignoreCase = true)
            }
            .flatMap { vector ->
                val platforms = vector.json.getAsJsonObject("platforms")
                vector.json.consumerTestReferences()
                    .filter { consumer -> consumer.platform == "commonPrototype" && consumer.testName.startsWith("com.polar.sharedtest.") }
                    .flatMap { consumer ->
                        val testFile = root.commonPrototypeTestFileFor(consumer.testName)
                        if (!testFile.isFile) {
                            emptyList()
                        } else {
                            val testSource = testFile.readText()
                            listOf("android", "ios", "common")
                                .filterNot { platform -> testSource.contains(platform) && testSource.contains(platforms.get(platform).asBoolean.toString()) }
                                .map { platform -> "${vector.file.relativeTo(root).path}: ${consumer.platform}:${consumer.testName} must assert platforms.$platform=${platforms.get(platform).asBoolean}" }
                        }
                    }
            }

        assertTrue(
            "Shared common readiness consumers must pin exact platform flags from the guarded manifest: $missingPlatformPins",
            missingPlatformPins.isEmpty()
        )
    }
}
