package com.polar.sdk.api.model.utils

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KmpDocumentationPolicyTest {

    @Test
    fun `KMP coverage documentation references existing platform tests`() {
        val root = findRepositoryRoot()
        val existingTestFiles = root
            .walkTopDown()
            .filter { file -> file.isFile && TEST_FILE_NAMES.matches(file.name) }
            .map { file -> file.name }
            .toSet()
        val missingReferences = KMP_COVERAGE_DOCS.flatMap { relativePath ->
            val doc = root.resolve(relativePath)
            TEST_REFERENCE.findAll(doc.readText())
                .map { match -> match.groupValues[1] }
                .filterNot { reference -> existingTestFiles.contains(File(reference).name) }
                .map { reference -> "$relativePath: missing $reference" }
                .toList()
        }

        assertTrue(
            "KMP coverage documentation must reference existing Kotlin and Swift test files: $missingReferences",
            missingReferences.isEmpty()
        )
    }

    @Test
    fun `KMP documentation names every executable shared common test artifact`() {
        val root = findRepositoryRoot()
        val documentationText = KMP_SHARED_COMMON_TEST_DOCS.joinToString(separator = "\n") { relativePath ->
            root.resolve(relativePath).readText()
        }
        val undocumentedSharedTests = root
            .resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest")
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .map { file -> file.name }
            .filterNot { fileName -> SHARED_COMMON_TEST_DOC_EXCLUSIONS.contains(fileName) }
            .filterNot { fileName -> documentationText.contains(fileName) }
            .toList()

        assertTrue(
            "Every executable shared common test artifact must be named in KMP migration documentation before production migration: $undocumentedSharedTests",
            undocumentedSharedTests.isEmpty()
        )
    }

    @Test
    fun `common owned golden vectors are consumed by shared common tests`() {
        val root = findRepositoryRoot()
        val sharedCommonTestText = root
            .resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest")
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .joinToString(separator = "\n") { file -> file.readText() }
        val missingSharedConsumers = loadAllGoldenVectors()
            .filter { vector -> vector.json.requiresSharedCommonVectorConsumer() }
            .map { vector -> vector.file.relativeTo(root).path.removePrefix("testdata/golden-vectors/") }
            .filterNot { relativeVectorPath -> sharedCommonTestText.contains(relativeVectorPath) }

        assertTrue(
            "Every common-owned or common-decision golden vector must be explicitly consumed by shared commonTest before KMP migration: $missingSharedConsumers",
            missingSharedConsumers.isEmpty()
        )
    }

    @Test
    fun `KMP coverage inventory has no missing or unassessed behavior rows`() {
        val inventory = findRepositoryRoot().resolve("documentation/KmpCoverageInventory.md").readText()
        val openRows = inventory.tableRows()
            .filter { row -> row.size >= COVERAGE_INVENTORY_COLUMN_COUNT }
            .filter { row -> row[COVERAGE_STATUS_COLUMN].startsWith("Missing") || row[COVERAGE_STATUS_COLUMN].startsWith("Not assessed") }
            .map { row -> "${row[COVERAGE_BEHAVIOR_COLUMN]}: ${row[COVERAGE_STATUS_COLUMN]}" }

        assertTrue(
            "KMP coverage inventory rows must be assessed before migration: $openRows",
            openRows.isEmpty()
        )
    }

    @Test
    fun `KMP coverage inventory partial rows declare migration gates`() {
        val inventory = findRepositoryRoot().resolve("documentation/KmpCoverageInventory.md").readText()
        val weakPartialRows = inventory.tableRows()
            .filter { row -> row.size >= COVERAGE_INVENTORY_COLUMN_COUNT }
            .filter { row -> row[COVERAGE_STATUS_COLUMN].startsWith("Partial") }
            .filterNot { row -> row[COVERAGE_REQUIRED_COLUMN].hasMigrationGateLanguage() }
            .map { row -> "${row[COVERAGE_BEHAVIOR_COLUMN]}: ${row[COVERAGE_REQUIRED_COLUMN]}" }

        assertTrue(
            "KMP coverage inventory Partial rows must include concrete migration gate or platform-ownership language: $weakPartialRows",
            weakPartialRows.isEmpty()
        )
    }

    @Test
    fun `KMP coverage inventory preserves platform owned runtime boundaries`() {
        val inventory = findRepositoryRoot().resolve("documentation/KmpCoverageInventory.md").readText()
        val rowsByBehavior = inventory.tableRows()
            .filter { row -> row.size >= COVERAGE_INVENTORY_COLUMN_COUNT }
            .associateBy { row -> row[COVERAGE_BEHAVIOR_COLUMN] }
        val missingBoundaryRows = PLATFORM_OWNED_COVERAGE_ROWS.flatMap { (behavior, requiredTerms) ->
            val row = rowsByBehavior[behavior]
            if (row == null) {
                listOf("$behavior: missing row")
            } else {
                val combined = "${row[COVERAGE_STATUS_COLUMN]} ${row[COVERAGE_REQUIRED_COLUMN]} $inventory"
                requiredTerms
                    .filterNot { term -> combined.contains(term) }
                    .map { term -> "$behavior: missing $term" }
            }
        }
        val missingBoundarySection = if (inventory.contains("## Platform-Owned Migration Boundary")) emptyList() else listOf("missing Platform-Owned Migration Boundary section")

        assertTrue(
            "Platform host and GATT rows must remain explicitly platform-owned until a stronger shared fake-transport contract exists: ${missingBoundaryRows + missingBoundarySection}",
            missingBoundaryRows.isEmpty() && missingBoundarySection.isEmpty()
        )
    }

    @Test
    fun `SDK feature availability readiness remains vector pinned and platform bounded`() {
        val root = findRepositoryRoot()
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val fakeTransportPlan = root.resolve("documentation/KmpFakeTransportTestPlan.md").readText()
        val backlog = root.resolve("documentation/KmpFullCoverageTddBacklog.md").readText()
        val remainingWork = root.resolve("documentation/KmpPreMigrationRemainingWork.md").readText()
        val vector = root.resolve("testdata/golden-vectors/sdk/feature-availability/feature-availability-readiness.json").readText()
        val sharedTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/FeatureAvailabilityCommonPolicyTest.kt").readText()
        val androidTest = root.resolve("sources/Android/android-communications/library/src/test/java/com/polar/sdk/impl/utils/PolarRuntimePlannerAdapterTest.kt").readText()
        val iosTest = root.resolve("sources/iOS/ios-communications/Tests/PolarBleSdkTests/PolarDataUtilsTest.swift").readText()
        val docs = "$inventory\n$fakeTransportPlan\n$backlog\n$remainingWork"
        val violations = mutableListOf<String>()

        FEATURE_AVAILABILITY_VECTOR_REQUIRED_TERMS
            .filterNot { term -> vector.contains(term) }
            .mapTo(violations) { term -> "feature-availability-readiness.json missing $term" }
        FEATURE_AVAILABILITY_DOC_REQUIRED_TERMS
            .filterNot { term -> docs.contains(term) }
            .mapTo(violations) { term -> "migration docs missing $term" }
        FEATURE_AVAILABILITY_TEST_REQUIRED_TERMS
            .filterNot { term -> sharedTest.contains(term) && androidTest.contains(term) && iosTest.contains(term) }
            .mapTo(violations) { term -> "shared/Android/iOS tests must all assert $term" }

        assertTrue(
            "SDK feature availability migration must stay vector-pinned while client readiness and transport behavior remain platform-owned: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `KMP coverage inventory keeps full coverage exit criteria explicit`() {
        val inventory = findRepositoryRoot().resolve("documentation/KmpCoverageInventory.md").readText()
        val exitCriteriaSection = inventory.substringAfter("## Full-Coverage Exit Criteria Before Migration", missingDelimiterValue = "")
        val missingCriteria = FULL_COVERAGE_EXIT_CRITERIA_TERMS
            .filterNot { term -> exitCriteriaSection.contains(term) }

        assertTrue(
            "KmpCoverageInventory.md must keep the full pre-migration coverage exit criteria explicit so completion cannot be narrowed around current passing tests: $missingCriteria",
            missingCriteria.isEmpty()
        )
    }

    @Test
    fun `KMP backlog keeps platform owned host boundaries explicit`() {
        val backlog = findRepositoryRoot().resolve("documentation/KmpFullCoverageTddBacklog.md").readText()
        val missingTerms = PLATFORM_OWNED_BACKLOG_REQUIRED_TERMS
            .filterNot { term -> backlog.contains(term) }

        assertTrue(
            "KmpFullCoverageTddBacklog.md must keep platform host and GATT boundaries explicit until a pure codec or deterministic state-machine contract exists: $missingTerms",
            missingTerms.isEmpty()
        )
    }

    @Test
    fun `KMP backlog describes current migration state without stale remaining gap language`() {
        val backlog = findRepositoryRoot().resolve("documentation/KmpFullCoverageTddBacklog.md").readText()
        val missingTerms = CURRENT_SHARED_POLICY_STATE_REQUIRED_TERMS
            .filterNot { term -> backlog.contains(term) }
        val staleTerms = STALE_SHARED_POLICY_BACKLOG_TERMS
            .filter { term -> backlog.contains(term) }

        assertTrue(
            "KmpFullCoverageTddBacklog.md must describe implemented-or-deferred shared policy state without stale remaining-gap language: missing=$missingTerms stale=$staleTerms",
            missingTerms.isEmpty() && staleTerms.isEmpty()
        )
    }
}
