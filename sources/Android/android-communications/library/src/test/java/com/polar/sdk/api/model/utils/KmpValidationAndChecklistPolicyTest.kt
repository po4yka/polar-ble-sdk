package com.polar.sdk.api.model.utils

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KmpValidationAndChecklistPolicyTest {

    @Test
    fun `KMP documentation keeps validation commands discoverable`() {
        val root = findRepositoryRoot()
        val missingLinks = KMP_DOCS_THAT_MUST_LINK_VALIDATION
            .filterNot { relativePath -> root.resolve(relativePath).readText().contains("KmpValidationCommands.md") }
        val validationDoc = root.resolve("documentation/KmpValidationCommands.md").readText()
        val missingSections = REQUIRED_VALIDATION_SECTIONS
            .filterNot { section -> validationDoc.contains(section) }
        val missingNonGradleGateTerms = VALIDATION_NON_GRADLE_GATE_TERMS
            .filterNot { term -> validationDoc.contains(term) }
        val missingHardwareSmokeTerms = HARDWARE_SMOKE_VALIDATION_TERMS
            .filterNot { term -> validationDoc.contains(term) }
        val missingGradleBatchTerms = GRADLE_BATCH_VALIDATION_TERMS
            .filterNot { term -> validationDoc.contains(term) }
        val missingMinimumTddLinkTerms = VALIDATION_MINIMUM_TDD_LINK_TERMS
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
            "KMP docs must link validation commands: $missingLinks",
            missingLinks.isEmpty()
        )
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
            "KmpValidationCommands.md must tie executable commands to the TDD minimum validation set: $missingMinimumTddLinkTerms",
            missingMinimumTddLinkTerms.isEmpty()
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
        val root = findRepositoryRoot()
        val validationDoc = root.resolve("documentation/KmpValidationCommands.md").readText()
        val remainingWork = root.resolve("documentation/KmpPreMigrationRemainingWork.md").readText()
        val validationMissingTerms = IOS_XCTEST_EXECUTION_GATE_REQUIRED_TERMS
            .filterNot { term -> validationDoc.contains(term) }
        val remainingWorkMissingTerms = IOS_XCTEST_CLOSEOUT_REQUIRED_TERMS
            .filterNot { term -> remainingWork.contains(term) }

        assertTrue(
            "KmpValidationCommands.md must keep full simulator XCTest as the required iOS execution gate after swiftc and Xcode probe checks: $validationMissingTerms",
            validationMissingTerms.isEmpty()
        )
        assertTrue(
            "KmpPreMigrationRemainingWork.md must keep full iOS XCTest in the future-slice validation set: $remainingWorkMissingTerms",
            remainingWorkMissingTerms.isEmpty()
        )
    }

    @Test
    fun `KMP backlog lists every executable shared common test artifact`() {
        val root = findRepositoryRoot()
        val backlog = root.resolve("documentation/KmpFullCoverageTddBacklog.md").readText()
        val currentCoverageSection = CURRENT_EXECUTABLE_COMMON_COVERAGE_SECTION.find(backlog)?.value.orEmpty()
        val commonTests = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest")
            .walkTopDown()
            .filter { file -> file.isFile && file.name.endsWith("Test.kt") }
            .map { file -> file.name }
            .filterNot { name -> SHARED_COMMON_TEST_DOC_EXCLUSIONS.contains(name) }
            .toList()
        val missingTests = commonTests.filterNot { testName -> currentCoverageSection.contains(testName) }

        assertTrue(
            "KmpFullCoverageTddBacklog.md Current Executable Common Coverage must list every executable shared common test file: $missingTests",
            missingTests.isEmpty()
        )
    }

    @Test
    fun `KMP TDD strategy golden vector example matches schema contract`() {
        val strategy = findRepositoryRoot().resolve("documentation/KmpTddStrategy.md").readText()
        val missingSchemaTerms = KMP_TDD_STRATEGY_VECTOR_EXAMPLE_TERMS
            .filterNot { term -> strategy.contains(term) }
        val missingMinimumValidationTerms = TDD_MINIMUM_VALIDATION_TERMS
            .filterNot { term -> strategy.contains(term) }
        val missingRegressionPolicyTerms = TDD_REGRESSION_POLICY_TERMS
            .filterNot { term -> strategy.contains(term) }
        val missingCoverageExpectationTerms = TDD_COVERAGE_EXPECTATION_TERMS
            .filterNot { term -> strategy.contains(term) }
        val missingFirstSliceTerms = FIRST_RECOMMENDED_TDD_SLICE_TERMS
            .filterNot { term -> strategy.contains(term) }
        val staleTerms = KMP_TDD_STRATEGY_STALE_VECTOR_EXAMPLE_TERMS
            .filter { term -> strategy.contains(term) }

        assertTrue(
            "KmpTddStrategy.md golden-vector example, minimum validation rules, regression policy, coverage expectations, and first-slice guidance must match the current coverage contract: missingSchema=$missingSchemaTerms missingMinimumValidation=$missingMinimumValidationTerms missingRegression=$missingRegressionPolicyTerms missingCoverage=$missingCoverageExpectationTerms missingFirstSlice=$missingFirstSliceTerms stale=$staleTerms",
            missingSchemaTerms.isEmpty() && missingMinimumValidationTerms.isEmpty() && missingRegressionPolicyTerms.isEmpty() && missingCoverageExpectationTerms.isEmpty() && missingFirstSliceTerms.isEmpty() && staleTerms.isEmpty()
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
        val root = findRepositoryRoot()
        val gradle = root.resolve("sources/Android/android-communications/library/build.gradle.kts").readText()
        val checklist = root.resolve("documentation/KmpMigrationChecklist.md").readText()
        val migrationPlan = root.resolve("documentation/KmpMigrationPlan.md").readText()
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
        if (!checklist.contains("- [x] Android Gradle configuration works in a tagless checkout or clearly documents the tag requirement.")) {
            violations += "KmpMigrationChecklist.md must mark Android tagless Gradle readiness complete only while this policy passes"
        }
        if (!migrationPlan.contains("Android Gradle version helper must remain tagless-safe")) {
            violations += "KmpMigrationPlan.md must document tagless-safe Android Gradle readiness"
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

        if (root.resolve("PolarBleSdk.podspec").exists() || root.resolve("sources/iOS/ios-communications/Podfile").exists()) {
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
        if (!iosReadme.contains("CocoaPods is no longer supported") || !iosReadme.contains("Swift Package Manager")) {
            violations += "sources/iOS/ios-communications/README.md must document SwiftPM as the supported package path and CocoaPods removal"
        }
        if (iosReadme.contains("<relative_path_to_cloned_repo>/ios-communications/") || iosReadme.contains("`/ios-communications/`")) {
            violations += "sources/iOS/ios-communications/README.md must not reference a nonexistent top-level ios-communications path"
        }

        assertTrue(
            "SwiftPM source paths must match the current iOS source layout before KMP migration: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `generated API documentation remains generator owned during migration slices`() {
        val root = findRepositoryRoot()
        val checklist = root.resolve("documentation/KmpMigrationChecklist.md").readText()
        val validationCommands = root.resolve("documentation/KmpValidationCommands.md").readText()
        val androidIndex = root.resolve("docs/polar-sdk-android/index.html")
        val iosIndex = root.resolve("docs/polar-sdk-ios/index.html")
        val androidGradle = root.resolve("sources/Android/android-communications/library/build.gradle.kts").readText()
        val violations = mutableListOf<String>()

        if (!androidIndex.isFile || !androidIndex.readText().contains("dokka-javadoc-stylesheet.css")) {
            violations += "docs/polar-sdk-android/index.html must exist and remain recognizable as Dokka output"
        }
        if (!iosIndex.isFile || !iosIndex.readText().contains("css/jazzy.css")) {
            violations += "docs/polar-sdk-ios/index.html must exist and remain recognizable as Jazzy output"
        }
        val hasDokkaTaskConfiguration = androidGradle.contains("tasks.named<DokkaTask>(\"dokkaJavadoc\")") ||
            androidGradle.contains("tasks.named<org.jetbrains.dokka.gradle.DokkaTask>(\"dokkaJavadoc\")")
        if (!androidGradle.contains("alias(libs.plugins.dokka)") || !hasDokkaTaskConfiguration) {
            violations += "sources/Android/android-communications/library/build.gradle.kts must keep the Android API doc generator visible"
        }
        if (!validationCommands.contains("## Generated API Documentation Ownership")) {
            violations += "KmpValidationCommands.md must document generated API documentation ownership"
        }
        if (!validationCommands.contains("git diff --name-only -- docs/polar-sdk-android docs/polar-sdk-ios")) {
            violations += "KmpValidationCommands.md must include the generated docs cleanliness command"
        }
        if (!checklist.contains("- [x] Generated API documentation is not edited by hand during migration slices.")) {
            violations += "KmpMigrationChecklist.md must keep generated API documentation ownership checked only while this policy passes"
        }
        val generatedDocDiffs = root.gitStatusShort("docs/polar-sdk-android", "docs/polar-sdk-ios")
        if (generatedDocDiffs.isNotEmpty()) {
            violations += "Generated API documentation must stay clean during migration slices: $generatedDocDiffs"
        }

        assertTrue(
            "Generated API docs must stay generator-owned and unchanged during migration coverage work: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `completed KMP checklist items cite supporting evidence`() {
        val root = findRepositoryRoot()
        val checklist = root.resolve("documentation/KmpMigrationChecklist.md").readText()
        val completedItems = CHECKED_CHECKLIST_ITEM.findAll(checklist)
            .map { match -> match.groupValues[1].trimEnd('.') }
            .toSet()
        val evidenceRows = checklist.completedItemEvidenceRows()
        val evidenceItems = evidenceRows
            .map { match -> match.groupValues[1].trimEnd('.') }
            .filterNot { item -> item == "Completed checklist item" || item == "---" }
            .toSet()
        val missingEvidence = completedItems
            .filterNot { item -> evidenceItems.contains(item) }
            .toList()
        val missingStopConditionTerms = MIGRATION_STOP_CONDITION_TERMS
            .filterNot { term -> checklist.contains(term) }
        val missingPerSliceChecklistTerms = PER_SLICE_TDD_CHECKLIST_TERMS
            .filterNot { term -> checklist.contains(term) }
        val missingReviewChecklistTerms = REVIEW_CHECKLIST_TERMS
            .filterNot { term -> checklist.contains(term) }
        val missingSuggestedSliceOrderTerms = SUGGESTED_SLICE_ORDER_TERMS
            .filterNot { term -> checklist.contains(term) }
        val missingEvidenceArtifacts = evidenceRows
            .flatMap { match ->
                val item = match.groupValues[1].trimEnd('.')
                if (item == "Completed checklist item" || item == "---") {
                    return@flatMap emptySequence()
                }
                val evidence = match.groupValues[2]
                val references = BACKTICK_REFERENCE.findAll(evidence)
                    .map { reference -> reference.groupValues[1] }
                    .filter { reference -> reference.looksLikeArtifactReference() }
                    .toList()
                if (references.isEmpty()) {
                    sequenceOf("$item: no artifact reference")
                } else {
                    references
                        .asSequence()
                        .filterNot { reference -> root.resolveEvidenceReference(reference).exists() }
                        .map { reference -> "$item: missing $reference" }
                }
            }
            .toList()
        val localValidationEvidence = evidenceRows
            .firstOrNull { match -> match.groupValues[1].trimEnd('.') == "A local validation command list exists for Android, iOS, and shared KMP modules" }
            ?.groupValues
            ?.get(2)
            .orEmpty()
        val missingLocalValidationProbeEvidence = !localValidationEvidence.contains(":repo-tools:iosXcodeValidationProbe")

        assertTrue(
            "Every completed KMP checklist item must have a Completed Item Evidence row: $missingEvidence",
            missingEvidence.isEmpty()
        )
        assertTrue(
            "KmpMigrationChecklist.md must keep migration stop conditions explicit before shared-code movement continues: $missingStopConditionTerms",
            missingStopConditionTerms.isEmpty()
        )
        assertTrue(
            "KmpMigrationChecklist.md must keep the per-slice TDD checklist explicit and ordered for future migration slices: $missingPerSliceChecklistTerms",
            missingPerSliceChecklistTerms.isEmpty()
        )
        assertTrue(
            "KmpMigrationChecklist.md must keep review gates explicit for public compatibility, platform ownership, error mapping, cancellation, and build metadata: $missingReviewChecklistTerms",
            missingReviewChecklistTerms.isEmpty()
        )
        assertTrue(
            "KmpMigrationChecklist.md must keep deterministic parser/model slices ahead of runtime and public API adapter slices: $missingSuggestedSliceOrderTerms",
            missingSuggestedSliceOrderTerms.isEmpty()
        )
        assertTrue(
            "Completed KMP checklist evidence rows must cite existing repo artifacts: $missingEvidenceArtifacts",
            missingEvidenceArtifacts.isEmpty()
        )
        assertTrue(
            "Completed local-validation evidence must cite the repeatable iOS Xcode infrastructure probe",
            !missingLocalValidationProbeEvidence
        )
    }

    @Test
    fun `completed platform vector loading helpers stay executable and wired`() {
        val root = findRepositoryRoot()
        val checklist = root.resolve("documentation/KmpMigrationChecklist.md").readText()
        val completedItems = CHECKED_CHECKLIST_ITEM.findAll(checklist)
            .map { match -> match.groupValues[1].trimEnd('.') }
            .toSet()
        val missingOrWeakArtifacts = mutableListOf<String>()

        if (completedItems.contains("Add vector-loading helpers for Android tests")) {
            val helper = root.resolve("sources/Android/android-communications/library/src/test/java/com/polar/testutils/GoldenVectorTestData.kt")
            val smokeTest = root.resolve("sources/Android/android-communications/library/src/test/java/com/polar/testutils/GoldenVectorTestDataTest.kt")
            if (!helper.isFile) missingOrWeakArtifacts += helper.relativeTo(root).path
            if (!smokeTest.isFile) {
                missingOrWeakArtifacts += smokeTest.relativeTo(root).path
            } else {
                val smokeTestText = smokeTest.readText()
                if (!smokeTestText.contains("GoldenVectorTestData.loadObjects") || !smokeTestText.contains("GoldenVectorTestData.loadObject")) {
                    missingOrWeakArtifacts += "${smokeTest.relativeTo(root).path}: must prove directory and single-object loading"
                }
                if (!smokeTestText.contains("does-not-exist.json") || !smokeTestText.contains("FileNotFoundException")) {
                    missingOrWeakArtifacts += "${smokeTest.relativeTo(root).path}: must prove missing fixture paths fail fast"
                }
            }
        }

        if (completedItems.contains("Add vector-loading helpers for iOS tests")) {
            val helper = root.resolve("sources/iOS/ios-communications/Tests/GoldenVectorTestData.swift")
            val helperTest = root.resolve("sources/iOS/ios-communications/Tests/iOSCommunicationsTests/GoldenVectorTestDataTest.swift")
            val project = root.resolve("sources/iOS/ios-communications/iOSCommunications.xcodeproj/project.pbxproj")
            if (!helper.isFile) missingOrWeakArtifacts += helper.relativeTo(root).path
            if (!helperTest.isFile) {
                missingOrWeakArtifacts += helperTest.relativeTo(root).path
            } else {
                val helperTestText = helperTest.readText()
                if (!helperTestText.contains("GoldenVectorTestData.loadObjects") || !helperTestText.contains("GoldenVectorTestData.loadObject")) {
                    missingOrWeakArtifacts += "${helperTest.relativeTo(root).path}: must prove directory and single-object loading"
                }
                if (!helperTestText.contains("does-not-exist.json") || !helperTestText.contains("XCTAssertThrowsError")) {
                    missingOrWeakArtifacts += "${helperTest.relativeTo(root).path}: must prove missing fixture paths fail fast"
                }
            }
            if (!project.isFile) {
                missingOrWeakArtifacts += project.relativeTo(root).path
            } else {
                val projectText = project.readText()
                val helperSourceBuildPhaseReferences = IOS_HELPER_SOURCE_PHASE_REFERENCE.findAll(projectText.sourcesBuildPhaseSection()).count()
                if (!projectText.contains("path = GoldenVectorTestData.swift")) {
                    missingOrWeakArtifacts += "${project.relativeTo(root).path}: missing GoldenVectorTestData.swift file reference"
                }
                if (helperSourceBuildPhaseReferences < IOS_TEST_TARGET_COUNT) {
                    missingOrWeakArtifacts += "${project.relativeTo(root).path}: GoldenVectorTestData.swift must be in both iOS test target source phases"
                }
                if (!projectText.contains("GoldenVectorTestDataTest.swift") || !projectText.contains("GoldenVectorTestDataTest.swift in Sources")) {
                    missingOrWeakArtifacts += "${project.relativeTo(root).path}: GoldenVectorTestDataTest.swift must be wired into iOSCommunicationsTests"
                }
            }
        }

        assertTrue(
            "Completed Android/iOS vector-loading helper checklist items must stay executable and wired: $missingOrWeakArtifacts",
            missingOrWeakArtifacts.isEmpty()
        )
    }

    @Test
    fun `completed fake transport contract keeps runtime migration controls executable`() {
        val root = findRepositoryRoot()
        val checklist = root.resolve("documentation/KmpMigrationChecklist.md").readText()
        val completedItems = CHECKED_CHECKLIST_ITEM.findAll(checklist)
            .map { match -> match.groupValues[1].trimEnd('.') }
            .toSet()
        if (!completedItems.contains("Add fake BLE transport interfaces for runtime tests before moving runtime code")) {
            return
        }

        val contract = root.resolve("sources/Android/android-communications/library/src/test/java/com/polar/testutils/FakeTransportContract.kt")
        val contractTest = root.resolve("sources/Android/android-communications/library/src/test/java/com/polar/testutils/FakeTransportContractTest.kt")
        val weakContract = mutableListOf<String>()
        if (!contract.isFile) {
            weakContract += contract.relativeTo(root).path
        } else {
            val contractText = contract.readText()
            FAKE_TRANSPORT_REQUIRED_OPERATIONS
                .filterNot { operation -> contractText.contains("fun $operation(") }
                .mapTo(weakContract) { operation -> "${contract.relativeTo(root).path}: missing $operation operation" }
            FAKE_TRANSPORT_REQUIRED_OUTCOMES
                .filterNot { outcome -> contractText.contains(outcome) }
                .mapTo(weakContract) { outcome -> "${contract.relativeTo(root).path}: missing $outcome outcome" }
            if (!contractText.contains("payload.toHex()")) {
                weakContract += "${contract.relativeTo(root).path}: write operations must capture payload bytes as hex"
            }
            FAKE_TRANSPORT_CLEANUP_REQUIRED_TERMS
                .filterNot { term -> contractText.contains(term) }
                .mapTo(weakContract) { term -> "${contract.relativeTo(root).path}: missing stream cleanup control $term" }
        }
        if (!contractTest.isFile) {
            weakContract += contractTest.relativeTo(root).path
        } else {
            val contractTestText = contractTest.readText()
            FAKE_TRANSPORT_TEST_REQUIRED_TERMS
                .filterNot { term -> contractTestText.contains(term) }
                .mapTo(weakContract) { term -> "${contractTest.relativeTo(root).path}: missing assertion coverage for $term" }
            FAKE_TRANSPORT_CLEANUP_TEST_REQUIRED_TERMS
                .filterNot { term -> contractTestText.contains(term) }
                .mapTo(weakContract) { term -> "${contractTest.relativeTo(root).path}: missing stream cleanup assertion for $term" }
        }
        val commonContract = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FakeTransportContract.kt")
        val commonContractTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FakeTransportContractCommonTest.kt")
        val commonRestServiceMappingTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/RestServiceMappingCommonPolicyTest.kt")
        val commonRestRuntimeTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/RestRequestTransportPolicyCommonTest.kt")
        val commonRestFacadeRuntimeTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/RestFacadeRuntimePolicyCommonTest.kt")
        val commonRestEventCompressionTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/RestEventCompressionPolicyCommonTest.kt")
        val commonFileRuntimeTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FileRuntimeErrorPolicyCommonTest.kt")
        val commonFileFacadeRuntimeTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FileFacadeRuntimePolicyCommonTest.kt")
        val commonBackupUtilityTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/BackupUtilityCommonPolicyTest.kt")
        val commonOfflineTriggerRuntimeTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/OfflineTriggerRuntimePolicyCommonTest.kt")
        val commonFirmwareUtilityTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FirmwareUpdateUtilityCommonPolicyTest.kt")
        val commonFirmwareWorkflowTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FirmwareWorkflowRuntimePolicyCommonTest.kt")
        val commonPsFtpByteCodecTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PsFtpByteCodecCommonPolicyTest.kt")
        val commonPsFtpRuntimeTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PsFtpRuntimePolicyCommonTest.kt")
        val commonStreamRuntimeTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/StreamRuntimePolicyCommonTest.kt")
        val commonCommandRuntimeTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/CommandRuntimePolicyCommonTest.kt")
        val commonStoredDataCleanupRuntimeTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/StoredDataCleanupRuntimePolicyCommonTest.kt")
        val commonDiskTimeRuntimeTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DiskTimeRuntimePolicyCommonTest.kt")
        val commonUserDeviceSettingsRuntimeTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/UserDeviceSettingsRuntimePolicyCommonTest.kt")
        val runtimeOrchestrationCommon = root.resolve("sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/runtime/PolarRuntimeOrchestration.kt")
        if (!commonContract.isFile) {
            weakContract += commonContract.relativeTo(root).path
        } else {
            val commonContractText = commonContract.readText()
            FAKE_TRANSPORT_COMMON_REQUIRED_TERMS
                .filterNot { term -> commonContractText.contains(term) }
                .mapTo(weakContract) { term -> "${commonContract.relativeTo(root).path}: missing common fake transport control $term" }
        }
        if (!commonContractTest.isFile) {
            weakContract += commonContractTest.relativeTo(root).path
        } else {
            val commonContractTestText = commonContractTest.readText()
            FAKE_TRANSPORT_COMMON_TEST_REQUIRED_TERMS
                .filterNot { term -> commonContractTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonContractTest.relativeTo(root).path}: missing common fake transport assertion for $term" }
        }
        if (!commonRestRuntimeTest.isFile) {
            weakContract += commonRestRuntimeTest.relativeTo(root).path
        } else {
            val commonRestRuntimeTestText = commonRestRuntimeTest.readText()
            FAKE_TRANSPORT_COMMON_REST_RUNTIME_TEST_REQUIRED_TERMS
                .filterNot { term -> commonRestRuntimeTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonRestRuntimeTest.relativeTo(root).path}: missing common REST runtime assertion for $term" }
        }
        if (!commonRestFacadeRuntimeTest.isFile) {
            weakContract += commonRestFacadeRuntimeTest.relativeTo(root).path
        } else {
            val commonRestFacadeRuntimeTestText = commonRestFacadeRuntimeTest.readText() + (if (runtimeOrchestrationCommon.isFile) runtimeOrchestrationCommon.readText() else "")
            FAKE_TRANSPORT_COMMON_REST_FACADE_RUNTIME_TEST_REQUIRED_TERMS
                .filterNot { term -> commonRestFacadeRuntimeTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonRestFacadeRuntimeTest.relativeTo(root).path}: missing common REST facade runtime assertion for $term" }
        }
        if (!commonRestServiceMappingTest.isFile) {
            weakContract += commonRestServiceMappingTest.relativeTo(root).path
        } else {
            val commonRestServiceMappingTestText = commonRestServiceMappingTest.readText()
            FAKE_TRANSPORT_COMMON_REST_SERVICE_MAPPING_TEST_REQUIRED_TERMS
                .filterNot { term -> commonRestServiceMappingTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonRestServiceMappingTest.relativeTo(root).path}: missing common REST service mapping assertion for $term" }
        }
        if (!commonRestEventCompressionTest.isFile) {
            weakContract += commonRestEventCompressionTest.relativeTo(root).path
        } else {
            val commonRestEventCompressionTestText = commonRestEventCompressionTest.readText()
            FAKE_TRANSPORT_COMMON_REST_EVENT_COMPRESSION_TEST_REQUIRED_TERMS
                .filterNot { term -> commonRestEventCompressionTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonRestEventCompressionTest.relativeTo(root).path}: missing common REST event compression assertion for $term" }
        }
        if (!commonFileRuntimeTest.isFile) {
            weakContract += commonFileRuntimeTest.relativeTo(root).path
        } else {
            val commonFileRuntimeTestText = commonFileRuntimeTest.readText()
            FAKE_TRANSPORT_COMMON_FILE_RUNTIME_TEST_REQUIRED_TERMS
                .filterNot { term -> commonFileRuntimeTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonFileRuntimeTest.relativeTo(root).path}: missing common file runtime assertion for $term" }
        }
        if (!commonFileFacadeRuntimeTest.isFile) {
            weakContract += commonFileFacadeRuntimeTest.relativeTo(root).path
        } else {
            val commonFileFacadeRuntimeTestText = commonFileFacadeRuntimeTest.readText() + (if (runtimeOrchestrationCommon.isFile) runtimeOrchestrationCommon.readText() else "")
            FAKE_TRANSPORT_COMMON_FILE_FACADE_RUNTIME_TEST_REQUIRED_TERMS
                .filterNot { term -> commonFileFacadeRuntimeTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonFileFacadeRuntimeTest.relativeTo(root).path}: missing common file facade runtime assertion for $term" }
        }
        if (!commonBackupUtilityTest.isFile) {
            weakContract += commonBackupUtilityTest.relativeTo(root).path
        } else {
            val commonBackupUtilityTestText = commonBackupUtilityTest.readText()
            FAKE_TRANSPORT_COMMON_BACKUP_UTILITY_TEST_REQUIRED_TERMS
                .filterNot { term -> commonBackupUtilityTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonBackupUtilityTest.relativeTo(root).path}: missing common backup utility assertion for $term" }
        }
        if (!commonOfflineTriggerRuntimeTest.isFile) {
            weakContract += commonOfflineTriggerRuntimeTest.relativeTo(root).path
        } else {
            val commonOfflineTriggerRuntimeTestText = commonOfflineTriggerRuntimeTest.readText()
            FAKE_TRANSPORT_COMMON_OFFLINE_TRIGGER_RUNTIME_TEST_REQUIRED_TERMS
                .filterNot { term -> commonOfflineTriggerRuntimeTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonOfflineTriggerRuntimeTest.relativeTo(root).path}: missing common offline trigger runtime assertion for $term" }
        }
        if (!commonFirmwareUtilityTest.isFile) {
            weakContract += commonFirmwareUtilityTest.relativeTo(root).path
        } else {
            val commonFirmwareUtilityTestText = commonFirmwareUtilityTest.readText()
            FAKE_TRANSPORT_COMMON_FIRMWARE_UTILITY_TEST_REQUIRED_TERMS
                .filterNot { term -> commonFirmwareUtilityTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonFirmwareUtilityTest.relativeTo(root).path}: missing common firmware utility assertion for $term" }
        }
        if (!commonFirmwareWorkflowTest.isFile) {
            weakContract += commonFirmwareWorkflowTest.relativeTo(root).path
        } else {
            val commonFirmwareWorkflowTestText = commonFirmwareWorkflowTest.readText()
            FAKE_TRANSPORT_COMMON_FIRMWARE_WORKFLOW_TEST_REQUIRED_TERMS
                .filterNot { term -> commonFirmwareWorkflowTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonFirmwareWorkflowTest.relativeTo(root).path}: missing common firmware workflow assertion for $term" }
        }
        if (!commonPsFtpByteCodecTest.isFile) {
            weakContract += commonPsFtpByteCodecTest.relativeTo(root).path
        } else {
            val commonPsFtpByteCodecTestText = commonPsFtpByteCodecTest.readText()
            FAKE_TRANSPORT_COMMON_PSFTP_BYTE_CODEC_TEST_REQUIRED_TERMS
                .filterNot { term -> commonPsFtpByteCodecTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonPsFtpByteCodecTest.relativeTo(root).path}: missing common PSFTP byte codec assertion for $term" }
        }
        if (!commonPsFtpRuntimeTest.isFile) {
            weakContract += commonPsFtpRuntimeTest.relativeTo(root).path
        } else {
            val commonPsFtpRuntimeTestText = commonPsFtpRuntimeTest.readText()
            FAKE_TRANSPORT_COMMON_PSFTP_RUNTIME_TEST_REQUIRED_TERMS
                .filterNot { term -> commonPsFtpRuntimeTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonPsFtpRuntimeTest.relativeTo(root).path}: missing common PSFTP runtime assertion for $term" }
        }
        if (!commonStreamRuntimeTest.isFile) {
            weakContract += commonStreamRuntimeTest.relativeTo(root).path
        } else {
            val commonStreamRuntimeTestText = commonStreamRuntimeTest.readText()
            FAKE_TRANSPORT_COMMON_STREAM_RUNTIME_TEST_REQUIRED_TERMS
                .filterNot { term -> commonStreamRuntimeTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonStreamRuntimeTest.relativeTo(root).path}: missing common stream runtime assertion for $term" }
        }
        if (!commonCommandRuntimeTest.isFile) {
            weakContract += commonCommandRuntimeTest.relativeTo(root).path
        } else {
            val commonCommandRuntimeTestText = commonCommandRuntimeTest.readText() + (if (runtimeOrchestrationCommon.isFile) runtimeOrchestrationCommon.readText() else "")
            FAKE_TRANSPORT_COMMON_COMMAND_RUNTIME_TEST_REQUIRED_TERMS
                .filterNot { term -> commonCommandRuntimeTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonCommandRuntimeTest.relativeTo(root).path}: missing common command runtime assertion for $term" }
        }
        if (!commonStoredDataCleanupRuntimeTest.isFile) {
            weakContract += commonStoredDataCleanupRuntimeTest.relativeTo(root).path
        } else {
            val commonStoredDataCleanupRuntimeTestText = commonStoredDataCleanupRuntimeTest.readText()
            FAKE_TRANSPORT_COMMON_STORED_DATA_CLEANUP_RUNTIME_TEST_REQUIRED_TERMS
                .filterNot { term -> commonStoredDataCleanupRuntimeTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonStoredDataCleanupRuntimeTest.relativeTo(root).path}: missing common stored-data cleanup runtime assertion for $term" }
        }
        if (!commonDiskTimeRuntimeTest.isFile) {
            weakContract += commonDiskTimeRuntimeTest.relativeTo(root).path
        } else {
            val commonDiskTimeRuntimeTestText = commonDiskTimeRuntimeTest.readText() + (if (runtimeOrchestrationCommon.isFile) runtimeOrchestrationCommon.readText() else "")
            FAKE_TRANSPORT_COMMON_DISK_TIME_RUNTIME_TEST_REQUIRED_TERMS
                .filterNot { term -> commonDiskTimeRuntimeTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonDiskTimeRuntimeTest.relativeTo(root).path}: missing common disk/time runtime assertion for $term" }
        }
        if (!commonUserDeviceSettingsRuntimeTest.isFile) {
            weakContract += commonUserDeviceSettingsRuntimeTest.relativeTo(root).path
        } else {
            val commonUserDeviceSettingsRuntimeTestText = commonUserDeviceSettingsRuntimeTest.readText() + (if (runtimeOrchestrationCommon.isFile) runtimeOrchestrationCommon.readText() else "")
            FAKE_TRANSPORT_COMMON_USER_DEVICE_SETTINGS_RUNTIME_TEST_REQUIRED_TERMS
                .filterNot { term -> commonUserDeviceSettingsRuntimeTestText.contains(term) }
                .mapTo(weakContract) { term -> "${commonUserDeviceSettingsRuntimeTest.relativeTo(root).path}: missing common user-device-settings runtime assertion for $term" }
        }

        assertTrue(
            "Completed fake transport contract must keep command capture and scripted runtime outcomes executable: $weakContract",
            weakContract.isEmpty()
        )
    }

    @Test
    fun `fake transport runtime matrix has evidence ledger for every row`() {
        val root = findRepositoryRoot()
        val plan = root.resolve("documentation/KmpFakeTransportTestPlan.md").readText()
        val matrixRows = plan.sectionBetween("## Required Runtime Test Matrix", "## Runtime Matrix Coverage Ledger")
            .tableRows()
            .filter { row -> row.size >= FAKE_TRANSPORT_MATRIX_COLUMN_COUNT && row[0] != "Behavior" }
        val ledgerRows = plan.sectionBetween("## Runtime Matrix Coverage Ledger", "## Public Facade Operation Coverage Ledger")
            .tableRows()
            .filter { row -> row.size >= FAKE_TRANSPORT_LEDGER_COLUMN_COUNT && row[0] != "Behavior" }
        val matrixBehaviors = matrixRows.map { row -> row[0] }
        val ledgerByBehavior = ledgerRows.associateBy { row -> row[0] }
        val knownFileNames = root.walkTopDown()
            .filter { file -> file.isFile }
            .map { file -> file.name }
            .toSet()
        val missingLedgerRows = matrixBehaviors.filterNot { behavior -> ledgerByBehavior.containsKey(behavior) }
        val extraLedgerRows = ledgerByBehavior.keys.filterNot { behavior -> matrixBehaviors.contains(behavior) }
        val weakRows = ledgerRows.flatMap { row ->
            val behavior = row[0]
            val status = row[FAKE_TRANSPORT_LEDGER_STATUS_COLUMN]
            val evidence = row[FAKE_TRANSPORT_LEDGER_EVIDENCE_COLUMN]
            val gate = row[FAKE_TRANSPORT_LEDGER_GATE_COLUMN]
            val issues = mutableListOf<String>()
            if (status.isBlank()) issues += "$behavior: missing status"
            if (evidence.isBlank()) issues += "$behavior: missing evidence"
            if (!gate.hasMigrationGateLanguage()) issues += "$behavior: migration gate must contain concrete before/add/keep language"
            BACKTICK_REFERENCE.findAll("$evidence $gate")
                .map { match -> match.groupValues[1] }
                .filter { reference -> reference.endsWith(".json") || reference.endsWith(".kt") || reference.endsWith(".swift") || reference.endsWith(".md") }
                .filterNot { reference -> knownFileNames.contains(File(reference).name) }
                .mapTo(issues) { reference -> "$behavior: missing artifact reference $reference" }
            issues
        }

        assertTrue(
            "KmpFakeTransportTestPlan.md runtime matrix must have a complete evidence ledger: missing=$missingLedgerRows extra=$extraLedgerRows weak=$weakRows",
            missingLedgerRows.isEmpty() && extraLedgerRows.isEmpty() && weakRows.isEmpty()
        )
    }

    @Test
    fun `public facade operation ledger names evidence and migration gates`() {
        val root = findRepositoryRoot()
        val plan = root.resolve("documentation/KmpFakeTransportTestPlan.md").readText()
        val ledgerRows = plan.sectionBetween("## Public Facade Operation Coverage Ledger", "## Pre-Migration Gates")
            .tableRows()
            .filter { row -> row.size >= PUBLIC_FACADE_LEDGER_COLUMN_COUNT && row[0] != "Operation family" }
        val ledgerByFamily = ledgerRows.associateBy { row -> row[0] }
        val knownFileNames = root.walkTopDown()
            .filter { file -> file.isFile }
            .map { file -> file.name }
            .toSet()
        val missingFamilies = PUBLIC_FACADE_OPERATION_FAMILIES.filterNot { family -> ledgerByFamily.containsKey(family) }
        val weakRows = ledgerRows.flatMap { row ->
            val family = row[0]
            val status = row[PUBLIC_FACADE_LEDGER_STATUS_COLUMN]
            val androidEvidence = row[PUBLIC_FACADE_LEDGER_ANDROID_COLUMN]
            val iosEvidence = row[PUBLIC_FACADE_LEDGER_IOS_COLUMN]
            val sharedEvidence = row[PUBLIC_FACADE_LEDGER_SHARED_COLUMN]
            val gate = row[PUBLIC_FACADE_LEDGER_GATE_COLUMN]
            val issues = mutableListOf<String>()
            if (status.isBlank()) issues += "$family: missing status"
            if (androidEvidence.isBlank()) issues += "$family: missing Android evidence"
            if (iosEvidence.isBlank()) issues += "$family: missing iOS evidence"
            if (sharedEvidence.isBlank()) issues += "$family: missing shared/runtime evidence"
            if (!gate.hasMigrationGateLanguage()) issues += "$family: migration gate must contain concrete before/add/keep language"
            BACKTICK_REFERENCE.findAll("$androidEvidence $iosEvidence $sharedEvidence $gate")
                .map { match -> match.groupValues[1] }
                .filter { reference -> reference.looksLikeArtifactReference() }
                .filterNot { reference -> knownFileNames.contains(File(reference).name) }
                .mapTo(issues) { reference -> "$family: missing artifact reference $reference" }
            issues
        }

        assertTrue(
            "KmpFakeTransportTestPlan.md public facade operation ledger must name required families with resolvable evidence and concrete gates: missing=$missingFamilies weak=$weakRows",
            missingFamilies.isEmpty() && weakRows.isEmpty()
        )
    }

    @Test
    fun `firmware workflow facade ledger stays pinned on injectable dependencies and facade compatibility`() {
        val root = findRepositoryRoot()
        val plan = root.resolve("documentation/KmpFakeTransportTestPlan.md").readText()
        val ledgerRow = plan.sectionBetween("## Public Facade Operation Coverage Ledger", "## Pre-Migration Gates")
            .tableRows()
            .firstOrNull { row -> row.firstOrNull() == "Firmware update workflow" }
        val violations = mutableListOf<String>()
        if (ledgerRow == null) {
            violations += "KmpFakeTransportTestPlan.md must keep a Firmware update workflow facade ledger row"
        } else {
            val rowText = ledgerRow.joinToString(" | ")
            FIRMWARE_FACADE_GATE_REQUIRED_TERMS
                .filterNot { term -> rowText.contains(term) }
                .mapTo(violations) { term -> "Firmware update workflow ledger row missing $term" }
            if (!ledgerRow[PUBLIC_FACADE_LEDGER_STATUS_COLUMN].contains("facade compatibility pinned")) {
                violations += "Firmware update workflow must stay pinned once production injectable dependencies and facade compatibility tests exist"
            }
        }

        assertTrue(
            "Firmware workflow public facade delegation must remain explicitly pinned by concrete dependency and facade-test evidence: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `facade gate open ledger rows stay blocked on concrete compatibility evidence`() {
        val root = findRepositoryRoot()
        val plan = root.resolve("documentation/KmpFakeTransportTestPlan.md").readText()
        val ledgerRowsByFamily = plan.sectionBetween("## Public Facade Operation Coverage Ledger", "## Pre-Migration Gates")
            .tableRows()
            .filter { row -> row.size >= PUBLIC_FACADE_LEDGER_COLUMN_COUNT && row[0] != "Operation family" }
            .associateBy { row -> row[0] }
        val violations = FACADE_GATE_OPEN_REQUIRED_TERMS.flatMap { (family, requiredTerms) ->
            val row = ledgerRowsByFamily[family]
            if (row == null) {
                listOf("KmpFakeTransportTestPlan.md must keep a $family facade ledger row")
            } else {
                val rowText = row.joinToString(" | ")
                val termViolations = requiredTerms
                    .filterNot { term -> rowText.contains(term) }
                    .map { term -> "$family ledger row missing $term" }
                val statusViolations = if (row[PUBLIC_FACADE_LEDGER_STATUS_COLUMN].contains("facade gate open")) {
                    emptyList()
                } else {
                    listOf("$family must stay facade-gated until platform facade and shared fake-transport compatibility evidence exists")
                }
                termViolations + statusViolations
            }
        }

        assertTrue(
            "Facade-gated public operation families must remain explicitly blocked by concrete platform/shared compatibility evidence: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `runtime pinned facade ledger rows keep error mapping and cleanup gates explicit`() {
        val root = findRepositoryRoot()
        val plan = root.resolve("documentation/KmpFakeTransportTestPlan.md").readText()
        val ledgerRowsByFamily = plan.sectionBetween("## Public Facade Operation Coverage Ledger", "## Pre-Migration Gates")
            .tableRows()
            .filter { row -> row.size >= PUBLIC_FACADE_LEDGER_COLUMN_COUNT && row[0] != "Operation family" }
            .associateBy { row -> row[0] }
        val violations = RUNTIME_PINNED_FACADE_LEDGER_REQUIRED_TERMS.flatMap { (family, requiredTerms) ->
            val row = ledgerRowsByFamily[family]
            if (row == null) {
                listOf("KmpFakeTransportTestPlan.md must keep a $family facade ledger row")
            } else {
                val rowText = row.joinToString(" | ")
                requiredTerms
                    .filterNot { term -> rowText.contains(term) }
                    .map { term -> "$family ledger row missing $term" }
            }
        }

        assertTrue(
            "Runtime-pinned public operation families must keep exact platform error, cleanup, timeout, or conditional cancellation gates before delegation: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `PSFTP timeout runtime ledger stays blocked on fake clock facade compatibility`() {
        val root = findRepositoryRoot()
        val plan = root.resolve("documentation/KmpFakeTransportTestPlan.md").readText()
        val ledgerRowsByBehavior = plan.sectionBetween("## Runtime Matrix Coverage Ledger", "## Public Facade Operation Coverage Ledger")
            .tableRows()
            .filter { row -> row.size >= FAKE_TRANSPORT_LEDGER_COLUMN_COUNT && row[0] != "Behavior" }
            .associateBy { row -> row[0] }
        val violations = PSFTP_TIMEOUT_LEDGER_REQUIRED_TERMS.flatMap { (behavior, requiredTerms) ->
            val row = ledgerRowsByBehavior[behavior]
            if (row == null) {
                listOf("KmpFakeTransportTestPlan.md must keep a $behavior runtime ledger row")
            } else {
                val rowText = row.joinToString(" | ")
                requiredTerms
                    .filterNot { term -> rowText.contains(term) }
                    .map { term -> "$behavior runtime ledger row missing $term" }
            }
        }

        assertTrue(
            "PSFTP timeout runtime rows must keep fake-clock and facade compatibility gates explicit before production runtime delegation: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `fake transport pre migration gates keep runtime facade and cleanup requirements explicit`() {
        val root = findRepositoryRoot()
        val plan = root.resolve("documentation/KmpFakeTransportTestPlan.md").readText()
        val gateSection = plan.sectionBetween("## Pre-Migration Gates", "## PSFTP Runtime Harness Requirements")
        val missingGateTerms = FAKE_TRANSPORT_PRE_MIGRATION_GATE_REQUIRED_TERMS
            .filterNot { term -> gateSection.contains(term) }
        val missingHarnessTerms = FAKE_TRANSPORT_HARNESS_DESCRIPTION_REQUIRED_TERMS
            .filterNot { term -> gateSection.contains(term) }

        assertTrue(
            "KmpFakeTransportTestPlan.md Pre-Migration Gates must keep runtime/facade migration requirements explicit: $missingGateTerms",
            missingGateTerms.isEmpty()
        )
        assertTrue(
            "KmpFakeTransportTestPlan.md must keep the fake-transport harness controls observable before runtime delegation: $missingHarnessTerms",
            missingHarnessTerms.isEmpty()
        )
    }
}
