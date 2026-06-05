package com.polar.sdk.api.model.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileReader

class GoldenVectorMigrationPolicyTest {

    @Test
    fun `all golden vectors declare required contract metadata`() {
        val missingMetadata = loadAllGoldenVectors()
            .flatMap { vector ->
                val missing = mutableListOf<String>()
                REQUIRED_FIELDS.forEach { field ->
                    if (!vector.json.has(field)) missing += field
                }
                val platforms = vector.json.getAsJsonObject("platforms")
                if (platforms == null) {
                    missing += "platforms.android"
                    missing += "platforms.ios"
                    missing += "platforms.common"
                } else {
                    PLATFORM_FIELDS.forEach { field ->
                        if (!platforms.has(field)) missing += "platforms.$field"
                    }
                }
                missing.map { field -> "${vector.file.relativeTo(findRepositoryRoot()).path}: missing $field" }
            }

        assertTrue("Golden vectors must declare required metadata: $missingMetadata", missingMetadata.isEmpty())
    }

    @Test
    fun `all golden vectors use stable identifiers and known metadata fields`() {
        val allVectors = loadAllGoldenVectors()
        val duplicateIds = allVectors
            .groupBy { it.json.get("id")?.asString }
            .filterKeys { it != null }
            .filterValues { it.size > 1 }
            .map { (id, vectors) -> "$id in ${vectors.map { it.file.relativeTo(findRepositoryRoot()).path }}" }
        val invalidIds = allVectors
            .filterNot { vector -> vector.json.get("id")?.asString?.let { VECTOR_ID.matches(it) } == true }
            .map { vector -> vector.file.relativeTo(findRepositoryRoot()).path }
        val invalidCases = allVectors
            .filterNot { vector -> vector.json.get("case")?.asString?.let { VECTOR_CASE.matches(it) } == true }
            .map { vector -> vector.file.relativeTo(findRepositoryRoot()).path }
        val untraceableSources = allVectors
            .filterNot { vector ->
                vector.json.get("source")?.asString?.let { source ->
                    SOURCE_PROVENANCE_TERMS.any { term -> source.contains(term) }
                } == true
            }
            .map { vector -> "${vector.file.relativeTo(findRepositoryRoot()).path}: ${vector.json.get("source")}" }
        val unknownFields = allVectors.flatMap { vector ->
            vector.json.keySet()
                .filterNot { TOP_LEVEL_FIELDS.contains(it) }
                .map { field -> "${vector.file.relativeTo(findRepositoryRoot()).path}: unknown top-level field $field" }
        }

        assertTrue("Golden vector IDs must be unique: $duplicateIds", duplicateIds.isEmpty())
        assertTrue("Golden vector IDs must use lowercase kebab-case: $invalidIds", invalidIds.isEmpty())
        assertTrue("Golden vector cases must use lowercase snake_case: $invalidCases", invalidCases.isEmpty())
        assertTrue("Golden vector source values must name traceable characterization/readiness/planning/prototype/platform evidence: $untraceableSources", untraceableSources.isEmpty())
        assertTrue("Golden vectors must use known metadata fields: $unknownFields", unknownFields.isEmpty())
    }

    @Test
    fun `golden vector schema matches executable migration policy fields`() {
        val schema = loadGoldenVectorSchema()
        val schemaRequiredFields = schema.stringArrayAt("required")
        val schemaAdditionalProperties = schema.get("additionalProperties")?.asBoolean
        val schemaTopLevelFields = schema.getAsJsonObject("properties").keySet()
        val schemaIdPattern = schema.getAsJsonObject("properties").getAsJsonObject("id").get("pattern").asString
        val schemaCasePattern = schema.getAsJsonObject("properties").getAsJsonObject("case").get("pattern").asString
        val schemaHexPattern = schema.getAsJsonObject("properties").getAsJsonObject("input").getAsJsonObject("properties").getAsJsonObject("hex").get("pattern").asString
        val schemaPlatformFields = schema.getAsJsonObject("properties")
            .getAsJsonObject("platforms")
            .getAsJsonObject("properties")
            .keySet()
        val schemaRequiredPlatformFields = schema.getAsJsonObject("properties")
            .getAsJsonObject("platforms")
            .stringArrayAt("required")
        val schemaPlatformAdditionalProperties = schema.getAsJsonObject("properties")
            .getAsJsonObject("platforms")
            .get("additionalProperties")
            .asBoolean
        val schemaConsumerTestPlatforms = schema.getAsJsonObject("properties")
            .getAsJsonObject("consumerTests")
            .getAsJsonObject("properties")
            .keySet()
        val schemaConsumerAdditionalProperties = schema.getAsJsonObject("properties")
            .getAsJsonObject("consumerTests")
            .get("additionalProperties")
            .asBoolean
        val schemaConsumerAnyOf = schema.getAsJsonObject("properties")
            .getAsJsonObject("consumerTests")
            .getAsJsonArray("anyOf")
            .map { element -> element.asJsonObject.stringArrayAt("required") }
        val expectedConsumerAnyOf = CONSUMER_TEST_PLATFORMS.map { platform -> listOf(platform) }

        assertTrue(
            "Schema required fields must match GoldenVectorMigrationPolicyTest.REQUIRED_FIELDS: schema=$schemaRequiredFields policy=$REQUIRED_FIELDS",
            schemaRequiredFields == REQUIRED_FIELDS
        )
        assertTrue(
            "Schema root additionalProperties must remain false",
            schemaAdditionalProperties == false
        )
        assertTrue(
            "Schema top-level fields must match GoldenVectorMigrationPolicyTest.TOP_LEVEL_FIELDS: schema=$schemaTopLevelFields policy=$TOP_LEVEL_FIELDS",
            schemaTopLevelFields == TOP_LEVEL_FIELDS
        )
        assertTrue(
            "Schema id/case/hex patterns must match executable policy: id=$schemaIdPattern case=$schemaCasePattern hex=$schemaHexPattern",
            schemaIdPattern == SCHEMA_VECTOR_ID_PATTERN && schemaCasePattern == SCHEMA_VECTOR_CASE_PATTERN && schemaHexPattern == SCHEMA_LOWERCASE_HEX_PATTERN
        )
        assertTrue(
            "Schema platform fields must match GoldenVectorMigrationPolicyTest.PLATFORM_FIELDS: schema=$schemaPlatformFields policy=$PLATFORM_FIELDS",
            schemaPlatformFields == PLATFORM_FIELDS.toSet() && schemaRequiredPlatformFields == PLATFORM_FIELDS && !schemaPlatformAdditionalProperties
        )
        assertTrue(
            "Schema consumerTests platforms must match GoldenVectorMigrationPolicyTest.CONSUMER_TEST_PLATFORMS: schema=$schemaConsumerTestPlatforms policy=$CONSUMER_TEST_PLATFORMS",
            schemaConsumerTestPlatforms == CONSUMER_TEST_PLATFORMS && !schemaConsumerAdditionalProperties && schemaConsumerAnyOf == expectedConsumerAnyOf
        )
    }

    @Test
    fun `all hex fields use lowercase byte strings`() {
        val invalidHexFields = loadAllGoldenVectors()
            .flatMap { vector -> vector.invalidHexFields() }

        assertTrue(
            "Golden vector fields ending in Hex must be lowercase even-length byte strings: $invalidHexFields",
            invalidHexFields.isEmpty()
        )
    }

    @Test
    fun `golden vector directories document migration ownership`() {
        val root = findRepositoryRoot()
        val undocumentedDirectories = root.resolve("testdata/golden-vectors")
            .walkTopDown()
            .filter { directory ->
                directory.isDirectory &&
                    directory.listFiles { file -> file.isFile && file.extension == "json" && file.name != "golden-vector.schema.json" }.orEmpty().isNotEmpty()
            }
            .filterNot { directory -> directory.resolve("README.md").isFile }
            .map { directory -> directory.relativeTo(root).path }
            .toList()

        assertTrue(
            "Every golden-vector directory with fixtures must include README.md migration ownership notes: $undocumentedDirectories",
            undocumentedDirectories.isEmpty()
        )
    }

    @Test
    fun `root golden vector readme lists every fixture directory`() {
        val root = findRepositoryRoot()
        val rootReadme = root.resolve("testdata/golden-vectors/README.md").readText()
        val actualFixtureDirectories = root.resolve("testdata/golden-vectors")
            .walkTopDown()
            .filter { directory ->
                directory.isDirectory &&
                    directory.listFiles { file -> file.isFile && file.extension == "json" && file.name != "golden-vector.schema.json" }.orEmpty().isNotEmpty()
            }
            .map { directory -> directory.relativeTo(root.resolve("testdata/golden-vectors")).path }
            .map { relativePath -> "$relativePath/" }
            .toList()
        val unlistedDirectories = actualFixtureDirectories
            .filterNot { relativePath -> rootReadme.contains(relativePath) }
        val staleDirectoryListings = ROOT_README_FIXTURE_DIRECTORY_LISTING.findAll(rootReadme)
            .map { match -> match.groupValues[1] }
            .filterNot { relativePath -> actualFixtureDirectories.contains(relativePath) }
            .toList()

        assertTrue(
            "Root golden-vector README directory layout must list every fixture directory: $unlistedDirectories",
            unlistedDirectories.isEmpty()
        )
        assertTrue(
            "Root golden-vector README directory layout must not contain stale fixture directory listings: $staleDirectoryListings",
            staleDirectoryListings.isEmpty()
        )
    }

    @Test
    fun `root golden vector readme documents schema metadata fields`() {
        val rootReadme = findRepositoryRoot().resolve("testdata/golden-vectors/README.md").readText()
        val undocumentedTerms = ROOT_GOLDEN_VECTOR_README_SCHEMA_TERMS
            .filterNot { term -> rootReadme.contains(term) }

        assertTrue(
            "Root golden-vector README must document schema-visible metadata fields and migration gates: $undocumentedTerms",
            undocumentedTerms.isEmpty()
        )
    }

    @Test
    fun `golden vector readmes describe migration context`() {
        val root = findRepositoryRoot()
        val weakReadmes = root.resolve("testdata/golden-vectors")
            .walkTopDown()
            .filter { file -> file.isFile && file.name == "README.md" }
            .filterNot { file -> file.readText().hasMigrationContext() }
            .map { file -> file.relativeTo(root).path }
            .toList()

        assertTrue(
            "Golden-vector README files must mention KMP/common migration context: $weakReadmes",
            weakReadmes.isEmpty()
        )
    }

    @Test
    fun `golden vector readmes name executable test artifacts as Kotlin files`() {
        val root = findRepositoryRoot()
        val bareSharedTestReferences = root.resolve("testdata/golden-vectors")
            .walkTopDown()
            .filter { file -> file.isFile && file.name == "README.md" }
            .flatMap { file ->
                FIXTURE_README_BARE_SHARED_COMMON_TEST_REFERENCE.findAll(file.readText())
                    .map { match -> "${file.relativeTo(root).path}: `${match.groupValues[1]}`" }
                    .toList()
            }
            .toList()

        assertTrue(
            "Golden-vector README files must name executable test artifacts as .kt files: $bareSharedTestReferences",
            bareSharedTestReferences.isEmpty()
        )
    }

    @Test
    fun `golden vector readme Kotlin artifact references resolve to existing tests`() {
        val root = findRepositoryRoot()
        val existingKotlinTestFiles = root.resolve("sources/Android/android-communications")
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" && file.name.endsWith("Test.kt") }
            .map { file -> file.name }
            .toSet()
        val missingReferences = root.resolve("testdata/golden-vectors")
            .walkTopDown()
            .filter { file -> file.isFile && file.name == "README.md" }
            .flatMap { file ->
                FIXTURE_README_KOTLIN_ARTIFACT_REFERENCE.findAll(file.readText())
                    .map { match -> match.groupValues[1] }
                    .filterNot { reference -> existingKotlinTestFiles.contains(reference) }
                    .map { reference -> "${file.relativeTo(root).path}: missing $reference" }
                    .toList()
            }
            .toList()

        assertTrue(
            "Golden-vector README Kotlin artifact references must resolve to existing Android or shared tests: $missingReferences",
            missingReferences.isEmpty()
        )
    }

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
    fun `type utility migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/TypeUtilsCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/type-utils/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            TYPE_UTILS_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing type-utils common policy term $term" }
        }
        if (!inventory.contains("TypeUtilsCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention TypeUtilsCommonPolicyTest.kt in the basic byte/type utilities row"
        }
        if (!readme.contains("TypeUtilsCommonPolicyTest")) {
            violations += "protocol/type-utils/README.md must mention executable shared common policy coverage"
        }

        assertTrue(
            "Type utility migration vectors must have executable shared common policy coverage before parser primitives move to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `device id migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DeviceIdCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/device-id/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            DEVICE_ID_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing device-id common policy term $term" }
        }
        if (!inventory.contains("DeviceIdCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention DeviceIdCommonPolicyTest.kt in the device ID row"
        }
        if (!readme.contains("DeviceIdCommonPolicyTest")) {
            violations += "protocol/device-id/README.md must mention executable shared common policy coverage"
        }

        assertTrue(
            "Device ID migration vectors must have executable shared common policy coverage before UUID/checksum logic moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `advertisement migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/AdvertisementCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/advertisement/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            ADVERTISEMENT_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing advertisement common policy term $term" }
        }
        if (!inventory.contains("AdvertisementCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention AdvertisementCommonPolicyTest.kt in the advertisement row"
        }
        if (!readme.contains("AdvertisementCommonPolicyTest")) {
            violations += "protocol/advertisement/README.md must mention executable shared common policy coverage"
        }

        assertTrue(
            "Advertisement migration vectors must have executable shared common policy coverage before advertisement parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `PMD settings migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PmdSettingsCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/pmd/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            PMD_SETTINGS_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing PMD settings common policy term $term" }
        }
        if (!inventory.contains("PmdSettingsCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention PmdSettingsCommonPolicyTest.kt in the PMD settings row"
        }
        if (!readme.contains("PmdSettingsCommonPolicyTest")) {
            violations += "protocol/pmd/README.md must mention executable shared PMD settings policy coverage"
        }

        assertTrue(
            "PMD settings migration vectors must have executable shared common policy coverage before settings parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `PMD control point migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PmdControlPointCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/pmd/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            PMD_CONTROL_POINT_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing PMD control-point common policy term $term" }
        }
        if (!inventory.contains("PmdControlPointCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention PmdControlPointCommonPolicyTest.kt in the PMD control point row"
        }
        if (!readme.contains("PmdControlPointCommonPolicyTest")) {
            violations += "protocol/pmd/README.md must mention executable shared PMD control-point policy coverage"
        }

        assertTrue(
            "PMD control-point migration vectors must have executable shared common policy coverage before control-point parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `PMD secret migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PmdSecretCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/pmd/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            PMD_SECRET_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing PMD secret common policy term $term" }
        }
        if (!inventory.contains("PmdSecretCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention PmdSecretCommonPolicyTest.kt in the PMD control point row"
        }
        if (!readme.contains("PmdSecretCommonPolicyTest")) {
            violations += "protocol/pmd/README.md must mention executable shared PMD secret policy coverage"
        }

        assertTrue(
            "PMD secret migration vectors must have executable shared common policy coverage before secret strategy code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `ECG parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/EcgParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            ECG_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing ECG common policy term $term" }
        }
        if (!inventory.contains("EcgParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention EcgParserCommonPolicyTest.kt in the ECG parser row"
        }
        if (!readme.contains("EcgParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared ECG parser policy coverage"
        }

        assertTrue(
            "ECG parser migration vectors must have executable shared common policy coverage before ECG parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `ACC parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/AccParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            ACC_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing ACC common policy term $term" }
        }
        if (!inventory.contains("AccParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention AccParserCommonPolicyTest.kt in the ACC parser row"
        }
        if (!readme.contains("AccParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared ACC parser policy coverage"
        }

        assertTrue(
            "ACC parser migration vectors must have executable shared common policy coverage before ACC parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `GYR parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GyrParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            GYR_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing GYR common policy term $term" }
        }
        if (!inventory.contains("GyrParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention GyrParserCommonPolicyTest.kt in the GYR parser row"
        }
        if (!readme.contains("GyrParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared GYR parser policy coverage"
        }

        assertTrue(
            "GYR parser migration vectors must have executable shared common policy coverage before GYR parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `MAG parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/MagParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            MAG_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing MAG common policy term $term" }
        }
        if (!inventory.contains("MagParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention MagParserCommonPolicyTest.kt in the MAG parser row"
        }
        if (!readme.contains("MagParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared MAG parser policy coverage"
        }

        assertTrue(
            "MAG parser migration vectors must have executable shared common policy coverage before MAG parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `PPG parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PpgParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            PPG_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing PPG common policy term $term" }
        }
        if (!inventory.contains("PpgParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention PpgParserCommonPolicyTest.kt in the PPG parser row"
        }
        val ppgInventoryRow = inventory.tableRows().firstOrNull { row -> row.firstOrNull() == "PPG parser" }
        if (ppgInventoryRow == null || !ppgInventoryRow[COVERAGE_STATUS_COLUMN].startsWith("Covered")) {
            violations += "KmpCoverageInventory.md PPG parser row must stay Covered after shared compile verification"
        }
        if (ppgInventoryRow != null && !ppgInventoryRow[COVERAGE_STATUS_COLUMN].contains(":shared:jvmTest")) {
            violations += "KmpCoverageInventory.md PPG parser row must keep :shared:jvmTest compile-verification evidence"
        }
        if (ppgInventoryRow != null && !ppgInventoryRow[COVERAGE_REQUIRED_COLUMN].contains("compile-verification-gate")) {
            violations += "KmpCoverageInventory.md PPG parser row must keep compile-verification-gate migration language"
        }
        if (!readme.contains("PpgParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared PPG parser policy coverage"
        }

        assertTrue(
            "PPG parser migration vectors must have executable shared common policy coverage before PPG parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `PPI parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PpiParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            PPI_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing PPI common policy term $term" }
        }
        if (!inventory.contains("PpiParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention PpiParserCommonPolicyTest.kt in the PPI parser row"
        }
        if (!readme.contains("PpiParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared PPI parser policy coverage"
        }

        assertTrue(
            "PPI parser migration vectors must have executable shared common policy coverage before PPI parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `pressure and temperature parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PressureTemperatureParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            PRESSURE_TEMPERATURE_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing pressure/temperature common policy term $term" }
        }
        if (!inventory.contains("PressureTemperatureParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention PressureTemperatureParserCommonPolicyTest.kt in the pressure and temperature parser rows"
        }
        if (!readme.contains("PressureTemperatureParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared pressure/temperature parser policy coverage"
        }

        assertTrue(
            "Pressure and temperature parser migration vectors must have executable shared common policy coverage before scalar parser code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `offline HR parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/OfflineHrParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            OFFLINE_HR_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing offline HR common policy term $term" }
        }
        if (!inventory.contains("OfflineHrParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention OfflineHrParserCommonPolicyTest.kt in the offline HR parser row"
        }
        if (!readme.contains("OfflineHrParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared offline HR parser policy coverage"
        }

        assertTrue(
            "Offline HR parser migration vectors must have executable shared common policy coverage before offline HR parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `GNSS location parser migration vectors have executable shared ownership policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GnssLocationOwnershipCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            GNSS_LOCATION_OWNERSHIP_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing GNSS ownership common policy term $term" }
        }
        if (!inventory.contains("GnssLocationOwnershipCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention GnssLocationOwnershipCommonPolicyTest.kt in the GNSS/location parser row"
        }
        if (!readme.contains("GnssLocationOwnershipCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared GNSS ownership policy coverage"
        }

        assertTrue(
            "GNSS location migration vectors must have executable shared ownership policy coverage before location parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `offline recording metadata migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/OfflineRecordingMetadataCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/offline-recording/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            OFFLINE_RECORDING_METADATA_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing offline-recording metadata common policy term $term" }
        }
        if (!inventory.contains("OfflineRecordingMetadataCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention OfflineRecordingMetadataCommonPolicyTest.kt in the offline recording row"
        }
        if (!readme.contains("OfflineRecordingMetadataCommonPolicyTest")) {
            violations += "sdk/offline-recording/README.md must mention executable shared offline-recording metadata policy coverage"
        }

        assertTrue(
            "Offline-recording metadata migration vectors must have executable shared common policy coverage before metadata mapping moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `training session migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/TrainingSessionCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/training-session/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            TRAINING_SESSION_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing training-session common policy term $term" }
        }
        if (!inventory.contains("TrainingSessionCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention TrainingSessionCommonPolicyTest.kt in the training-session row"
        }
        if (!readme.contains("TrainingSessionCommonPolicyTest")) {
            violations += "sdk/training-session/README.md must mention executable shared training-session policy coverage"
        }

        assertTrue(
            "Training-session migration vectors must have executable shared common policy coverage before training-session discovery/read orchestration moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `skin temperature parser migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/SkinTemperatureParserCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/protocol/sensors/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            SKIN_TEMPERATURE_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing skin-temperature common policy term $term" }
        }
        if (!inventory.contains("SkinTemperatureParserCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention SkinTemperatureParserCommonPolicyTest.kt in the temperature parser row"
        }
        if (!readme.contains("SkinTemperatureParserCommonPolicyTest")) {
            violations += "protocol/sensors/README.md must mention executable shared skin-temperature parser policy coverage"
        }

        assertTrue(
            "Skin-temperature parser migration vectors must have executable shared common policy coverage before skin-temperature parsing moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `skin temperature domain migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/SkinTemperatureDomainCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/skin-temperature/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            SKIN_TEMPERATURE_DOMAIN_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing skin-temperature domain common policy term $term" }
        }
        if (!inventory.contains("SkinTemperatureDomainCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention SkinTemperatureDomainCommonPolicyTest.kt in the skin temperature domain row"
        }
        if (!readme.contains("SkinTemperatureDomainCommonPolicyTest")) {
            violations += "sdk/skin-temperature/README.md must mention executable shared skin-temperature domain policy coverage"
        }

        assertTrue(
            "Skin-temperature domain migration vectors must have executable shared common policy coverage before public model mapping moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `disk space migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DiskSpaceCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/disk-space/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            DISK_SPACE_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing disk-space common policy term $term" }
        }
        if (!inventory.contains("DiskSpaceCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention DiskSpaceCommonPolicyTest.kt in the disk space row"
        }
        if (!readme.contains("DiskSpaceCommonPolicyTest")) {
            violations += "sdk/disk-space/README.md must mention executable shared disk-space policy coverage"
        }

        assertTrue(
            "Disk-space migration vectors must have executable shared common policy coverage before disk-space model code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `SPo2 migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/Spo2CommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/spo2-test/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            SPO2_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing SPo2 common policy term $term" }
        }
        if (!inventory.contains("Spo2CommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention Spo2CommonPolicyTest.kt in the SPo2 row"
        }
        if (!readme.contains("Spo2CommonPolicyTest")) {
            violations += "sdk/spo2-test/README.md must mention executable shared SPo2 policy coverage"
        }

        assertTrue(
            "SPo2 migration vectors must have executable shared common policy coverage before SPo2 model code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `watch face migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/WatchFaceCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/watch-face/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            WATCH_FACE_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing watch-face common policy term $term" }
        }
        if (!inventory.contains("WatchFaceCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention WatchFaceCommonPolicyTest.kt in the watch face row"
        }
        if (!readme.contains("WatchFaceCommonPolicyTest")) {
            violations += "sdk/watch-face/README.md must mention executable shared watch-face policy coverage"
        }

        assertTrue(
            "Watch-face migration vectors must have executable shared common policy coverage before watch-face model code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `KVTX migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/KvtxCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/kvtx/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            KVTX_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing KVTX common policy term $term" }
        }
        if (!inventory.contains("KvtxCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention KvtxCommonPolicyTest.kt in the KVTX row"
        }
        if (!readme.contains("KvtxCommonPolicyTest")) {
            violations += "sdk/kvtx/README.md must mention executable shared KVTX policy coverage"
        }

        assertTrue(
            "KVTX migration vectors must have executable shared common policy coverage before KVTX script code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `D2H notification migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/D2hNotificationCommonPolicyTest.kt")
        val streamRuntimeTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/D2hStreamRuntimePolicyCommonTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/d2h-notifications/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            D2H_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing D2H common policy term $term" }
        }
        if (!streamRuntimeTest.isFile) {
            violations += streamRuntimeTest.relativeTo(root).path
        } else {
            val streamRuntimeTestText = streamRuntimeTest.readText()
            D2H_STREAM_RUNTIME_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> streamRuntimeTestText.contains(term) }
                .mapTo(violations) { term -> "${streamRuntimeTest.relativeTo(root).path}: missing D2H stream runtime common policy term $term" }
        }
        if (!inventory.contains("D2hNotificationCommonPolicyTest.kt") || !inventory.contains("D2hStreamRuntimePolicyCommonTest.kt")) {
            violations += "KmpCoverageInventory.md must mention D2hNotificationCommonPolicyTest.kt and D2hStreamRuntimePolicyCommonTest.kt in the D2H row"
        }
        if (!readme.contains("D2hNotificationCommonPolicyTest") || !readme.contains("D2hStreamRuntimePolicyCommonTest")) {
            violations += "sdk/d2h-notifications/README.md must mention executable shared D2H mapping and stream policy coverage"
        }

        assertTrue(
            "D2H migration vectors must have executable shared common policy coverage before D2H notification mapping moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `activity automatic sample and daily summary migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/ActivitySummaryCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val activityReadme = root.resolve("testdata/golden-vectors/sdk/activity-samples/README.md").readText()
        val automaticReadme = root.resolve("testdata/golden-vectors/sdk/automatic-samples/README.md").readText()
        val dailyReadme = root.resolve("testdata/golden-vectors/sdk/daily-summary/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            ACTIVITY_SUMMARY_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing activity/automatic/daily common policy term $term" }
        }
        if (!inventory.contains("ActivitySummaryCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention ActivitySummaryCommonPolicyTest.kt in the activity samples and summaries row"
        }
        if (!activityReadme.contains("ActivitySummaryCommonPolicyTest")) {
            violations += "sdk/activity-samples/README.md must mention executable shared activity policy coverage"
        }
        if (!automaticReadme.contains("ActivitySummaryCommonPolicyTest")) {
            violations += "sdk/automatic-samples/README.md must mention executable shared automatic-sample policy coverage"
        }
        if (!dailyReadme.contains("ActivitySummaryCommonPolicyTest")) {
            violations += "sdk/daily-summary/README.md must mention executable shared daily-summary policy coverage"
        }

        assertTrue(
            "Activity, automatic-sample, and daily-summary migration vectors must have executable shared common policy coverage before model mapping moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `sleep and nightly recharge migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/SleepNightlyRechargeCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val sleepReadme = root.resolve("testdata/golden-vectors/sdk/sleep/README.md").readText()
        val nightlyReadme = root.resolve("testdata/golden-vectors/sdk/nightly-recharge/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            SLEEP_NIGHTLY_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing sleep/nightly common policy term $term" }
        }
        if (!inventory.contains("SleepNightlyRechargeCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention SleepNightlyRechargeCommonPolicyTest.kt in the sleep/nightly row"
        }
        if (!sleepReadme.contains("SleepNightlyRechargeCommonPolicyTest")) {
            violations += "sdk/sleep/README.md must mention executable shared sleep policy coverage"
        }
        if (!nightlyReadme.contains("SleepNightlyRechargeCommonPolicyTest")) {
            violations += "sdk/nightly-recharge/README.md must mention executable shared nightly recharge policy coverage"
        }

        assertTrue(
            "Sleep and nightly recharge migration vectors must have executable shared common policy coverage before model code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

    @Test
    fun `user device settings migration vectors have executable shared common policy coverage`() {
        val root = findRepositoryRoot()
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/UserDeviceSettingsCommonPolicyTest.kt")
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val readme = root.resolve("testdata/golden-vectors/sdk/user-device-settings/README.md").readText()
        val violations = mutableListOf<String>()

        if (!commonTest.isFile) {
            violations += commonTest.relativeTo(root).path
        } else {
            val commonTestText = commonTest.readText()
            USER_DEVICE_SETTINGS_COMMON_POLICY_REQUIRED_TERMS
                .filterNot { term -> commonTestText.contains(term) }
                .mapTo(violations) { term -> "${commonTest.relativeTo(root).path}: missing user-device-settings common policy term $term" }
        }
        if (!inventory.contains("UserDeviceSettingsCommonPolicyTest.kt")) {
            violations += "KmpCoverageInventory.md must mention UserDeviceSettingsCommonPolicyTest.kt in the user device settings row"
        }
        if (!readme.contains("UserDeviceSettingsCommonPolicyTest")) {
            violations += "sdk/user-device-settings/README.md must mention executable shared user-device-settings policy coverage"
        }

        assertTrue(
            "User-device-settings migration vectors must have executable shared common policy coverage before settings model code moves to KMP: $violations",
            violations.isEmpty()
        )
    }

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
        val iosProbe = root.resolve("scripts/ios_xcode_validation_probe.rb")
        val missingIosProbeTerms = if (iosProbe.isFile) {
            IOS_XCODE_PROBE_REQUIRED_TERMS.filterNot { term -> iosProbe.readText().contains(term) }
        } else {
            listOf("scripts/ios_xcode_validation_probe.rb")
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
        val remainingWorkMissingTerms = IOS_XCTEST_REMAINING_WORK_REQUIRED_TERMS
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
        val gradleMinSdk = ANDROID_MIN_SDK_VERSION.find(root.resolve("sources/Android/android-communications/library/build.gradle").readText())
            ?.groupValues
            ?.get(1)
            ?: error("Could not find minSdk in Android library build.gradle")
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
            "Android minSdk documentation must match sources/Android/android-communications/library/build.gradle: $mismatches",
            mismatches.isEmpty()
        )
    }

    @Test
    fun `Android Gradle version helper remains tagless safe`() {
        val root = findRepositoryRoot()
        val gradle = root.resolve("sources/Android/android-communications/library/build.gradle").readText()
        val checklist = root.resolve("documentation/KmpMigrationChecklist.md").readText()
        val migrationPlan = root.resolve("documentation/KmpMigrationPlan.md").readText()
        val violations = mutableListOf<String>()

        if (!gradle.contains("new ProcessBuilder('git', 'describe', '--tags', '--always')")) {
            violations += "Android build.gradle must use git describe --tags --always"
        }
        if (!gradle.contains("def exitValue = process.waitFor()") || !gradle.contains("exitValue == 0")) {
            violations += "Android build.gradle must handle nonzero Git describe exits during configuration"
        }
        if (!gradle.contains("matcher.find()")) {
            violations += "Android build.gradle must extract a semver prefix instead of requiring the full git describe output to be semver"
        }
        if (!gradle.contains("def VERSION = \"0.0.0\"")) {
            violations += "Android build.gradle must fall back to parseable semver 0.0.0"
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
    fun `CocoaPods source paths match iOS source layout`() {
        val root = findRepositoryRoot()
        val podspec = root.resolve("PolarBleSdk.podspec").readText()
        val iosReadme = root.resolve("sources/iOS/ios-communications/README.md").readText()
        val sourceRoot = root.resolve("sources/iOS/ios-communications/Sources")
        val sourceFilesPath = PODSPEC_SOURCE_FILES.find(podspec)?.groupValues?.get(1)
        val resources = PODSPEC_RESOURCES.find(podspec)
            ?.groupValues
            ?.get(1)
            ?.let { resourcesDeclaration ->
                PODSPEC_RESOURCE_REFERENCE.findAll(resourcesDeclaration).map { match -> match.groupValues[1] }.toList()
            }
            ?: emptyList()
        val violations = mutableListOf<String>()

        if (sourceFilesPath != "sources/iOS/ios-communications/Sources/**/*.{swift,h}") {
            violations += "PolarBleSdk.podspec source_files must point to sources/iOS/ios-communications/Sources, found $sourceFilesPath"
        }
        if (!sourceRoot.isDirectory) {
            violations += "Missing iOS source root ${sourceRoot.relativeTo(root).path}"
        } else if (sourceRoot.walkTopDown().none { file -> file.isFile && file.extension == "swift" }) {
            violations += "iOS source root ${sourceRoot.relativeTo(root).path} must contain Swift sources"
        }
        resources
            .filterNot { resource -> root.resolve(resource).isFile }
            .mapTo(violations) { resource -> "PolarBleSdk.podspec resource does not exist: $resource" }
        if (resources.isEmpty()) {
            violations += "PolarBleSdk.podspec must declare the iOS capability resource"
        }
        if (iosReadme.contains("<relative_path_to_cloned_repo>/ios-communications/") || iosReadme.contains("`/ios-communications/`")) {
            violations += "sources/iOS/ios-communications/README.md must not reference a nonexistent top-level ios-communications path"
        }

        assertTrue(
            "CocoaPods source paths must match the current iOS source layout before KMP migration: $violations",
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
        val androidGradle = root.resolve("sources/Android/android-communications/library/build.gradle").readText()
        val violations = mutableListOf<String>()

        if (!androidIndex.isFile || !androidIndex.readText().contains("dokka-javadoc-stylesheet.css")) {
            violations += "docs/polar-sdk-android/index.html must exist and remain recognizable as Dokka output"
        }
        if (!iosIndex.isFile || !iosIndex.readText().contains("css/jazzy.css")) {
            violations += "docs/polar-sdk-ios/index.html must exist and remain recognizable as Jazzy output"
        }
        if (!androidGradle.contains("org.jetbrains.dokka") || !androidGradle.contains("tasks.dokkaJavadoc.configure")) {
            violations += "sources/Android/android-communications/library/build.gradle must keep the Android API doc generator visible"
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
        val missingLocalValidationProbeEvidence = !localValidationEvidence.contains("scripts/ios_xcode_validation_probe.rb")

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
    fun `firmware workflow facade ledger stays gated on injectable dependencies and facade compatibility`() {
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
            if (!ledgerRow[PUBLIC_FACADE_LEDGER_STATUS_COLUMN].contains("facade gate open")) {
                violations += "Firmware update workflow must stay facade-gated until production injectable dependencies and facade compatibility tests exist"
            }
        }

        assertTrue(
            "Firmware workflow public facade delegation must remain explicitly blocked by concrete dependency and facade-test gates: $violations",
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

    @Test
    fun `byte level common dependency deferrals stay explicit until production common codecs exist`() {
        val root = findRepositoryRoot()
        val sharedBuild = root.resolve("sources/Android/android-communications/shared/build.gradle").readText()
        val backlog = root.resolve("documentation/KmpFullCoverageTddBacklog.md").readText()
        val inventory = root.resolve("documentation/KmpCoverageInventory.md").readText()
        val remainingWork = root.resolve("documentation/KmpPreMigrationRemainingWork.md").readText()
        val trainingSessionPayloadRead = root.resolve("testdata/golden-vectors/sdk/training-session/payload-read-policy.json").readText()
        val trainingSessionPayloadParser = root.resolve("testdata/golden-vectors/sdk/training-session/payload-parser-policy.json").readText()
        val trainingSessionReadiness = root.resolve("testdata/golden-vectors/sdk/training-session/training-session-readiness.json").readText()
        val pmdSecretReadiness = root.resolve("testdata/golden-vectors/protocol/pmd/secret-readiness.json").readText()
        val restCompressionReadiness = root.resolve("testdata/golden-vectors/sdk/rest-service/rest-event-compression-readiness.json").readText()
        val watchFaceReadiness = root.resolve("testdata/golden-vectors/sdk/watch-face/watch-face-readiness.json").readText()
        val violations = mutableListOf<String>()

        SHARED_COMMON_PRODUCTION_CODEC_DEPENDENCY_TERMS
            .filter { term -> sharedBuild.contains(term) }
            .mapTo(violations) { term -> "shared/build.gradle declares $term before this policy is updated with production common codec ownership evidence" }
        BYTE_LEVEL_COMMON_DEPENDENCY_DEFERRAL_TERMS.forEach { (artifact, requiredTerms) ->
            val text = when (artifact) {
                "KmpFullCoverageTddBacklog.md" -> backlog
                "KmpCoverageInventory.md" -> inventory
                "KmpPreMigrationRemainingWork.md" -> remainingWork
                "payload-read-policy.json" -> trainingSessionPayloadRead
                "payload-parser-policy.json" -> trainingSessionPayloadParser
                "training-session-readiness.json" -> trainingSessionReadiness
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
        val settings = root.resolve("sources/Android/android-communications/settings.gradle").readText()
        val sharedBuild = root.resolve("sources/Android/android-communications/shared/build.gradle").readText()
        val sharedMarker = root.resolve("sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/SharedModule.kt")
        val violations = mutableListOf<String>()

        if (!settings.contains("include ':shared'")) {
            violations += "settings.gradle must include :shared"
        }
        if (!sharedBuild.contains("org.jetbrains.kotlin.multiplatform")) {
            violations += "shared/build.gradle must apply Kotlin Multiplatform"
        }
        if (!sharedBuild.contains("jvm()")) {
            violations += "shared/build.gradle must keep a JVM target so commonTest is executable now"
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
        val sharedBuild = root.resolve("sources/Android/android-communications/shared/build.gradle").readText()
        val manifest = root.resolve("sources/Android/android-communications/shared/src/androidMain/AndroidManifest.xml")
        val violations = mutableListOf<String>()

        if (!sharedBuild.contains("apply plugin: 'com.android.kotlin.multiplatform.library'")) {
            violations += "shared/build.gradle must apply com.android.kotlin.multiplatform.library for the Android KMP target"
        }
        if (!sharedBuild.contains("android {")) {
            violations += "shared/build.gradle must declare the AGP 9 Android KMP target"
        }
        if (!sharedBuild.contains("iosX64()") || !sharedBuild.contains("iosArm64()") || !sharedBuild.contains("iosSimulatorArm64()")) {
            violations += "shared/build.gradle must declare iosX64, iosArm64, and iosSimulatorArm64"
        }
        if (!sharedBuild.contains("namespace = 'com.polar.shared'") || !Regex("minSdk(?:Version)?\\s*(?:=)?\\s*26").containsMatchIn(sharedBuild)) {
            violations += "shared/build.gradle must declare Android namespace and minSdk 26"
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
        if (root.resolve("sources/Android/android-communications/library/build.gradle").readText().contains("project(':shared')") && !deviceIdSliceMigrated(root)) {
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
        val sharedBuild = root.resolve("sources/Android/android-communications/shared/build.gradle").readText()
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
        if (!sharedBuild.contains("baseName = 'PolarBleSdkShared'") || !sharedBuild.contains("isStatic = true")) {
            violations += "shared/build.gradle must define the static PolarBleSdkShared framework artifact"
        }
        if (!validationCommands.contains(":shared:bundleAndroidMainAar") || !validationCommands.contains(":shared:linkDebugFrameworkIosX64")) {
            violations += "KmpValidationCommands.md must document shared artifact smoke gates"
        }
        if (!completedItems.contains("Document how shared artifacts are consumed by Android and iOS modules")) {
            violations += "KmpMigrationChecklist.md must mark shared artifact consumption documentation complete"
        }
        if (root.resolve("sources/Android/android-communications/library/build.gradle").readText().contains("implementation project(':shared')") && !deviceIdSliceMigrated(root)) {
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

    private fun JsonObject.hasMigrationPolicyRationale(): Boolean {
        val expected = getAsJsonObject("expected") ?: return false
        if (expected.has("commonDecision")) return true
        if (expected.has("commonRuntimePrototype")) return true
        if (expected.has("commonWorkflowPrototype")) return true
        if (expected.has("migrationOwnership")) return true
        val platformExpectations = getAsJsonObject("platformExpectations")
        return platformExpectations?.has("commonDecision") == true
    }

    private fun JsonObject.requiresSharedCommonVectorConsumer(): Boolean {
        val platforms = getAsJsonObject("platforms") ?: return false
        if (platforms.has("common") && platforms.get("common").asBoolean) return true
        if (has("commonDecision")) return true
        if (getAsJsonObject("platformExpectations")?.has("commonDecision") == true) return true
        val expected = getAsJsonObject("expected") ?: return false
        return expected.has("commonDecision") || expected.has("policy")
    }

    private fun JsonObject.hasConsumerTests(): Boolean {
        val consumerTests = getAsJsonObject("consumerTests") ?: return false
        return consumerTests.entrySet().any { entry ->
            val value = entry.value
            value.isJsonArray && value.asJsonArray.any { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank() }
        }
    }

    private fun JsonObject.hasNonEmptyConsumerTests(platform: String): Boolean {
        val consumerTests = getAsJsonObject("consumerTests") ?: return false
        val platformTests = consumerTests.get(platform) ?: return false
        return platformTests.isJsonArray && platformTests.asJsonArray.any { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank() }
    }

    private fun JsonObject.consumerTestsFor(platform: String): List<String> {
        val consumerTests = getAsJsonObject("consumerTests") ?: return emptyList()
        val platformTests = consumerTests.get(platform) ?: return emptyList()
        if (!platformTests.isJsonArray) return emptyList()
        return platformTests.asJsonArray
            .filter { element -> element.isJsonPrimitive && element.asJsonPrimitive.isString }
            .map { element -> element.asString }
    }

    private fun JsonObject.optionalStringField(field: String): String? {
        val value = get(field) ?: return null
        return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
    }

    private fun JsonObject.isRuntimePlanningVector(): Boolean {
        return has("execution") || expectedObject().has("commonRuntimePrototype") || expectedObject().has("commonWorkflowPrototype")
    }

    private fun JsonObject.hasRuntimePolicyConsumer(): Boolean {
        return consumerTestReferences().any { consumer -> RUNTIME_POLICY_CONSUMER_TEST.matches(consumer.testName) }
    }

    private fun JsonObject.consumerTestReferences(): List<ConsumerTestReference> {
        val consumerTests = getAsJsonObject("consumerTests") ?: return emptyList()
        return consumerTests.entrySet().flatMap { entry ->
            if (!CONSUMER_TEST_PLATFORMS.contains(entry.key) || !entry.value.isJsonArray || entry.value.asJsonArray.size() == 0) {
                listOf(ConsumerTestReference(entry.key, "<invalid>"))
            } else {
                entry.value.asJsonArray.map { element ->
                    val testName = if (element.isJsonPrimitive && element.asJsonPrimitive.isString) element.asString else "<invalid>"
                    ConsumerTestReference(entry.key, testName)
                }
            }
        }
    }

    private fun JsonObject.consumerTestShapeErrors(): List<String> {
        val consumerTests = getAsJsonObject("consumerTests") ?: return emptyList()
        return consumerTests.entrySet().flatMap { entry ->
            val errors = mutableListOf<String>()
            if (!CONSUMER_TEST_PLATFORMS.contains(entry.key)) {
                errors += "unknown consumerTests.${entry.key}"
            }
            if (!entry.value.isJsonArray) {
                errors += "consumerTests.${entry.key} must be an array"
            } else {
                val references = entry.value.asJsonArray
                if (references.size() == 0) {
                    errors += "consumerTests.${entry.key} must not be empty"
                }
                references.forEachIndexed { index, element ->
                    if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString || element.asString.isBlank()) {
                        errors += "consumerTests.${entry.key}[$index] must be a non-empty string"
                    }
                }
            }
            errors
        }
    }

    private fun JsonObject.behaviorIds(): List<String> {
        val ids = mutableListOf<String>()
        getAsJsonObject("input")?.let { input ->
            listOf("cases", "scenarios", "requests", "operations").forEach { field ->
                ids += input.objectArrayIds(field)
            }
        }
        val expected = getAsJsonObject("expected")
        listOf("commonRuntimePrototype", "commonParserPrototype", "commonWorkflowPrototype", "commonByteCodecPrototype", "commonPrototype").forEach { field ->
            ids += expected?.getAsJsonObject(field)?.objectArrayIds("cases").orEmpty()
            ids += getAsJsonObject(field)?.objectArrayIds("cases").orEmpty()
        }
        return ids.distinct()
    }

    private fun JsonObject.objectArrayIds(field: String): List<String> {
        val value = get(field) ?: return emptyList()
        if (!value.isJsonArray) return emptyList()
        return value.asJsonArray
            .filter { element -> element.isJsonObject }
            .mapNotNull { element ->
                val id = element.asJsonObject.get("id")
                if (id != null && id.isJsonPrimitive && id.asJsonPrimitive.isString) id.asString else null
            }
    }

    private fun JsonObject.expectedObject(): JsonObject {
        return getAsJsonObject("expected") ?: JsonObject()
    }

    private fun JsonObject.stringArrayAt(field: String): List<String> {
        return getAsJsonArray(field).map { it.asString }
    }

    private fun File.androidTestFileFor(className: String): File {
        return resolve("sources/Android/android-communications/library/src/test/java/${className.replace('.', '/')}.kt")
    }

    private fun File.commonPrototypeTestFileFor(className: String): File {
        val sharedCommonTest = resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/${className.replace('.', '/')}.kt")
        return if (sharedCommonTest.isFile) sharedCommonTest else androidTestFileFor(className)
    }

    private fun File.consumerTestReferencesVector(consumer: ConsumerTestReference, vector: VectorFile): Boolean {
        val vectorId = vector.json.get("id").asString
        val vectorFileName = vector.file.name
        return when (consumer.platform) {
            "android" -> {
                val testFile = androidTestFileFor(consumer.testName)
                testFile.isFile && testFile.readText().referencesVectorOrOwningManifest(this, vector, vectorId, vectorFileName)
            }
            "commonPrototype" -> {
                val testFile = commonPrototypeTestFileFor(consumer.testName)
                testFile.isFile && testFile.readText().referencesVectorOrOwningManifest(this, vector, vectorId, vectorFileName)
            }
            "ios" -> iosTestFiles(consumer.testName).any { file ->
                file.readText().referencesVectorOrOwningManifest(this, vector, vectorId, vectorFileName)
            }
            else -> false
        }
    }

    private fun File.iosTestExists(testName: String): Boolean {
        return iosTestFiles(testName).isNotEmpty()
    }

    private fun File.iosTestFiles(testName: String): List<File> {
        val testsRoot = resolve("sources/iOS/ios-communications/Tests")
        return testsRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "swift" }
            .filter { file ->
                file.nameWithoutExtension == testName || file.readText().contains(Regex("\\b(class|struct)\\s+$testName\\b"))
            }
            .toList()
    }

    private fun String.referencesVector(vectorId: String, vectorFileName: String): Boolean {
        return contains(vectorId) || contains(vectorFileName)
    }

    private fun String.referencesVectorOrOwningManifest(root: File, vector: VectorFile, vectorId: String, vectorFileName: String): Boolean {
        val vectorDirectory = requireNotNull(vector.file.parentFile) { "Vector file must have a parent directory: ${vector.file}" }.relativeTo(root).path
        return referencesVector(vectorId, vectorFileName) || contains(vectorDirectory) || root.owningManifestReferencesVector(this, vector)
    }

    private fun File.owningManifestReferencesVector(testSource: String, vector: VectorFile): Boolean {
        val vectorRelativePath = vector.file.relativeTo(this).path.removePrefix("testdata/golden-vectors/")
        return resolve("testdata/golden-vectors")
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "json" && file != vector.file && !file.relativeTo(this).path.contains("/schema/") }
            .map { manifestFile ->
                val manifest = FileReader(manifestFile).use { reader -> JsonParser().parse(reader).asJsonObject }
                manifestFile to manifest
            }
            .filter { (_, manifest) -> manifest.containsStringValue(vectorRelativePath) }
            .any { (manifestFile, manifest) ->
                testSource.referencesVector(manifest.get("id").asString, manifestFile.name)
            }
    }

    private fun JsonObject.containsStringValue(expectedValue: String): Boolean {
        fun visit(value: com.google.gson.JsonElement): Boolean {
            return when {
                value.isJsonPrimitive -> value.asJsonPrimitive.isString && value.asString == expectedValue
                value.isJsonArray -> value.asJsonArray.any { element -> visit(element) }
                value.isJsonObject -> value.asJsonObject.entrySet().any { entry -> visit(entry.value) }
                else -> false
            }
        }
        return visit(this)
    }

    private fun String.hasMigrationContext(): Boolean {
        val lower = lowercase()
        return lower.contains("kmp") && MIGRATION_README_TERMS.any { lower.contains(it) }
    }

    private fun String.hasMigrationGateLanguage(): Boolean {
        val lower = lowercase()
        return PARTIAL_ROW_GATE_TERMS.any { lower.contains(it) }
    }

    private fun String.looksLikeArtifactReference(): Boolean {
        return contains("/") || endsWith(".md") || endsWith(".json") || endsWith(".kt") || endsWith(".swift") || endsWith(".podspec")
    }

    private fun File.resolveEvidenceReference(reference: String): File {
        val direct = resolve(reference)
        return if (direct.exists()) direct else resolve("documentation/$reference")
    }

    private fun String.validationArtifactReferences(): List<String> {
        return VALIDATION_ARTIFACT_REFERENCE.findAll(this)
            .map { match -> match.value.trimEnd('.', ',', ')') }
            .filterNot { reference -> reference.startsWith("Pods/") }
            .distinct()
            .toList()
    }

    private fun String.completedItemEvidenceRows(): Sequence<MatchResult> {
        val section = COMPLETED_ITEM_EVIDENCE_SECTION.find(this)?.value ?: ""
        return CHECKLIST_EVIDENCE_ROW.findAll(section)
    }

    private fun deviceIdSliceMigrated(root: File): Boolean {
        val commonMain = root.resolve("sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/device/PolarDeviceId.kt")
        val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DeviceIdCommonPolicyTest.kt")
        val androidDeviceIdUtility = root.resolve("sources/Android/android-communications/library/src/main/java/com/polar/androidcommunications/api/ble/model/polar/BlePolarDeviceIdUtility.kt")
        val androidDeviceUuid = root.resolve("sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/model/PolarDeviceUuid.kt")
        return commonMain.isFile &&
            commonMain.readText().contains("object PolarDeviceId") &&
            commonTest.isFile &&
            commonTest.readText().contains("PolarDeviceId.uuidFromDeviceId") &&
            androidDeviceIdUtility.isFile &&
            androidDeviceIdUtility.readText().contains("PolarDeviceId.assembleFull") &&
            androidDeviceUuid.isFile &&
            androidDeviceUuid.readText().contains("PolarDeviceId.uuidFromDeviceId")
    }

    private fun iosSharedConsumptionMigrated(root: File): Boolean {
        val bridge = root.resolve("sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/ios/PolarIosSharedBridge.kt")
        val deviceIdUtility = root.resolve("sources/iOS/ios-communications/Sources/iOSCommunications/ble/api/model/polar/BlePolarDeviceIdUtility.swift")
        val deviceUuid = root.resolve("sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/api/model/PolarDeviceUuid.swift")
        val timeUtils = root.resolve("sources/iOS/ios-communications/Sources/PolarBleSdk/sdk/impl/utils/PolarTimeUtils.swift")
        val kmpScript = root.resolve("sources/iOS/ios-communications/scripts/build_kmp_ios_framework.sh")
        return bridge.isFile &&
            bridge.readText().contains("object PolarIosSharedBridge") &&
            bridge.readText().contains("PolarDeviceId.uuidFromDeviceId") &&
            bridge.readText().contains("PolarTimeUtils.nanosToMillis") &&
            deviceIdUtility.isFile &&
            deviceIdUtility.readText().contains("PolarIosSharedBridge.shared.isValidDeviceId") &&
            deviceUuid.isFile &&
            deviceUuid.readText().contains("PolarIosSharedBridge.shared.uuidFromDeviceId") &&
            timeUtils.isFile &&
            timeUtils.readText().contains("PolarIosSharedBridge.shared.durationToMillis") &&
            kmpScript.isFile
    }

    private fun File.gitStatusShort(vararg paths: String): List<String> {
        val process = ProcessBuilder(listOf("git", "status", "--short", "--") + paths)
            .directory(this)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readLines()
        val exitCode = process.waitFor()
        return if (exitCode == 0) output.filter { it.isNotBlank() } else listOf("git status failed with exit $exitCode: ${output.joinToString(" ")}")
    }

    private fun String.sourcesBuildPhaseSection(): String {
        return SOURCES_BUILD_PHASE_SECTION.find(this)?.value ?: ""
    }

    private fun String.tableRows(): List<List<String>> {
        return lineSequence()
            .filter { line -> line.startsWith("|") && !line.startsWith("|---") && !line.contains("Behavior family") }
            .map { line -> line.trim('|').split('|').map { column -> column.trim() } }
            .toList()
    }

    private fun String.sectionBetween(startHeading: String, endHeading: String): String {
        val start = indexOf(startHeading)
        if (start == -1) return ""
        val end = indexOf(endHeading, start + startHeading.length)
        return if (end == -1) substring(start) else substring(start, end)
    }

    private fun VectorFile.invalidHexFields(): List<String> {
        val invalidFields = mutableListOf<String>()
        fun visit(value: Any?, path: String) {
            when (value) {
                is JsonObject -> value.entrySet().forEach { entry ->
                    visit(entry.value, if (path.isEmpty()) entry.key else "$path.${entry.key}")
                }
                is com.google.gson.JsonArray -> value.forEachIndexed { index, element ->
                    visit(element, "$path[$index]")
                }
                is com.google.gson.JsonPrimitive -> {
                    if (path.endsWith("Hex") && value.isString && !LOWERCASE_HEX.matches(value.asString)) {
                        invalidFields += "${file.relativeTo(findRepositoryRoot()).path}: $path=${value.asString}"
                    }
                }
            }
        }
        visit(json, "")
        return invalidFields
    }

    private fun loadAllGoldenVectors(): List<VectorFile> {
        val root = findRepositoryRoot()
        return root
            .resolve("testdata/golden-vectors")
            .walkTopDown()
            .filter { it.isFile && it.extension == "json" && !it.relativeTo(root).path.contains("/schema/") }
            .sortedBy { it.relativeTo(root).path }
            .map { file ->
                VectorFile(
                    file = file,
                    json = FileReader(file).use { reader ->
                        JsonParser().parse(reader).asJsonObject
                    }
                )
            }
            .toList()
    }

    private fun loadSdkGoldenVectors(): List<VectorFile> {
        val root = findRepositoryRoot()
        return root
            .resolve("testdata/golden-vectors/sdk")
            .walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .sortedBy { it.relativeTo(root).path }
            .map { file ->
                VectorFile(
                    file = file,
                    json = FileReader(file).use { reader ->
                        JsonParser().parse(reader).asJsonObject
                    }
                )
            }
            .toList()
    }

    private fun loadGoldenVectorSchema(): JsonObject {
        return FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/schema/golden-vector.schema.json")).use { reader ->
            JsonParser().parse(reader).asJsonObject
        }
    }

    private fun findRepositoryRoot(): File {
        val userDirectory = System.getProperty("user.dir") ?: error("user.dir is not set")
        var directory = File(userDirectory).absoluteFile
        while (true) {
            if (directory.resolve("testdata/golden-vectors").isDirectory) {
                return directory
            }
            directory = directory.parentFile ?: error("Could not find repository root from $userDirectory")
        }
    }

    private data class VectorFile(
        val file: File,
        val json: JsonObject
    )

    private data class ConsumerTestReference(
        val platform: String,
        val testName: String
    )

    private companion object {
        val REQUIRED_FIELDS = listOf("id", "area", "case", "source", "input", "expected", "consumerTests", "platforms")
        val PLATFORM_FIELDS = listOf("android", "ios", "common")
        val CONSUMER_TEST_PLATFORMS = setOf("android", "ios", "commonPrototype")
        val REQUIRED_RUNTIME_CONSUMER_TESTS = listOf("android", "ios", "commonPrototype")
        val ROOT_README_FIXTURE_DIRECTORY_LISTING = Regex("^testdata/golden-vectors/(.+/)$", RegexOption.MULTILINE)
        val SOURCE_PROVENANCE_TERMS = listOf("characterization", "readiness", "planning", "backlog", "policy", "prototype", "migration", "Android", "iOS", "KMP", "shared")
        const val SCHEMA_LOWERCASE_HEX_PATTERN = "^([0-9a-f]{2})*$"
        const val SCHEMA_VECTOR_ID_PATTERN = "^[a-z0-9][a-z0-9-]*$"
        const val SCHEMA_VECTOR_CASE_PATTERN = "^[a-z0-9][a-z0-9_]*$"
        val ROOT_GOLDEN_VECTOR_README_SCHEMA_TERMS = listOf(
            "`description`",
            "`consumerTests`",
            "`execution`",
            "`commonDecision`",
            "`platformExpectations`",
            "`source` traceable",
            "Common-owned vectors",
            "shared commonTest",
            "Runtime planning vectors"
        )
        val STALE_SHARED_RUNTIME_VECTOR_NOTES = listOf(
            "still need dedicated fake-transport vectors",
            "future fake-transport vectors"
        )
        val KMP_COVERAGE_DOCS = listOf(
            "documentation/KmpCoverageInventory.md",
            "documentation/KmpFakeTransportTestPlan.md",
            "documentation/KmpTddStrategy.md"
        )
        val KMP_SHARED_COMMON_TEST_DOCS = listOf(
            "documentation/KmpCoverageInventory.md",
            "documentation/KmpFakeTransportTestPlan.md",
            "documentation/KmpFullCoverageTddBacklog.md",
            "documentation/KmpTddStrategy.md",
            "documentation/KmpValidationCommands.md"
        )
        val SHARED_COMMON_TEST_DOC_EXCLUSIONS = setOf(
            "GoldenVectorJson.kt",
            "GoldenVectorTestData.kt"
        )
        val KMP_DOCS_THAT_MUST_LINK_VALIDATION = listOf(
            "documentation/KmpMigrationPlan.md",
            "documentation/KmpFullCoverageTddBacklog.md",
            "documentation/KmpTddStrategy.md"
        )
        val CURRENT_EXECUTABLE_COMMON_COVERAGE_SECTION = Regex("## Current Executable Common Coverage.*?(?=\\n## |\\z)", RegexOption.DOT_MATCHES_ALL)
        val ANDROID_MIN_SDK_DOCS = listOf(
            "README.md",
            "documentation/MigrationGuide7.0.0-Android.md"
        )
        val KMP_COMMON_VECTOR_HELPER_ARTIFACTS = listOf(
            "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GoldenVectorTestData.kt",
            "sources/Android/android-communications/shared/src/jvmTest/kotlin/com/polar/sharedtest/GoldenVectorTestDataJvm.kt",
            "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/GoldenVectorTestDataCommonTest.kt"
        )
        val TYPE_UTILS_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "typeUtilsGoldenVectorsDefineExecutableCommonByteConversionPolicy",
            "typeUtilsReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/type-utils/type-utils-readiness.json",
            "type-utils-readiness",
            "protocol/type-utils/signed-int-min-32bit.json",
            "protocol/type-utils/unsigned-int-high-bit-platform-difference.json",
            "protocol/type-utils/empty-payload-platform-difference.json",
            "signed-minimum-boundaries",
            "unsigned-high-bit-platform-decision",
            "payload-too-long-error-policy",
            "compile-verification-gate",
            "convertUnsignedInt",
            "convertUnsignedLong",
            "convertSignedInt",
            "payloadTooLong",
            "emptyPayload"
        )
        val DEVICE_ID_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "deviceIdGoldenVectorsDefineExecutableCommonChecksumAndUuidPolicy",
            "deviceIdReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/device-id/device-id-readiness.json",
            "device-id-readiness",
            "protocol/device-id/assemble-seven-digit-device-id.json",
            "protocol/device-id/empty-device-id-platform-difference.json",
            "protocol/device-id/non-hex-device-id-platform-difference.json",
            "protocol/device-id/polar-device-uuid-valid.json",
            "PolarDeviceId.assembleFull",
            "PolarDeviceId.isValid",
            "PolarDeviceId.uuidFromDeviceId",
            "checksum-width-6-assembly",
            "checksum-width-7-assembly",
            "uuid-invalid-length-error",
            "platform-specific-identifier-routing",
            "compile-verification-gate",
            "platform-specific"
        )
        val ADVERTISEMENT_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "advertisementGoldenVectorsDefineExecutableCommonParsingPolicy",
            "advertisementReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/advertisement/advertisement-readiness.json",
            "advertisement-readiness",
            "protocol/advertisement/polar-local-name.json",
            "protocol/advertisement/non-polar-local-name-platform-difference.json",
            "protocol/advertisement/manufacturer-polar-gpb-missing-length-platform-policy.json",
            "protocol/advertisement/rssi-median-seven-sample-window.json",
            "parseLocalName",
            "parseManufacturerHrPresent",
            "parseRssi",
            "polar-local-name-parsing",
            "malformed-gpb-missing-length-policy",
            "service-uuid-membership",
            "compile-verification-gate",
            "malformed"
        )
        val PMD_SETTINGS_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "pmdSettingsGoldenVectorsDefineExecutableCommonParsingAndSerializationPolicy",
            "pmdSettingsReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/pmd/settings-readiness.json",
            "pmd-settings-readiness",
            "protocol/pmd/settings-basic-range.json",
            "protocol/pmd/settings-range-milliunit-platform-difference.json",
            "protocol/pmd/settings-security-value-platform-error.json",
            "protocol/pmd/settings-selected-serialization-max-values.json",
            "parseSettings",
            "serializeSelectedSettings",
            "duplicate-setting-overwrite",
            "selected-setting-serialization",
            "range-milliunit-signedness-platform-decision",
            "security-setting-platform-error-policy",
            "compile-verification-gate",
            "invalidPMDData"
        )
        val PMD_CONTROL_POINT_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "pmdControlPointGoldenVectorsDefineExecutableCommonResponsePolicy",
            "pmdControlPointReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/pmd/control-point-readiness.json",
            "pmd-control-point-readiness",
            "protocol/pmd/active-measurement-online-offline-unknown.json",
            "protocol/pmd/control-point-error-invalid-measurement-type-unknown.json",
            "protocol/pmd/control-point-short-empty-android-error.json",
            "protocol/pmd/control-point-success-start-ppg-more.json",
            "parseActiveMeasurement",
            "parseControlPointResponse",
            "active-measurement-bit-decoding",
            "control-point-status-code-coverage",
            "short-payload-deterministic-error-policy",
            "compile-verification-gate",
            "invalidPMDData"
        )
        val PMD_SECRET_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "pmdSecretGoldenVectorsDefineExecutableCommonStrategySerializationAndValidationPolicy",
            "pmdSecretReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/pmd/secret-readiness.json",
            "pmd-secret-readiness",
            "protocol/pmd/secret-serialization-aes256.json",
            "protocol/pmd/secret-decrypt-xor.json",
            "protocol/pmd/secret-invalid-none-nonempty-key.json",
            "protocol/pmd/secret-strategy-from-byte-unknown.json",
            "security-strategy-byte-mapping",
            "unknown-security-strategy-rejection",
            "aes-fixture-pinning",
            "compile-verification-gate",
            "invalidSecurityKey",
            "unknownSecurityStrategy",
            "AES256",
            "XOR"
        )
        val ECG_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "ecgGoldenVectorsDefineExecutableCommonRawType0Policy",
            "ecgReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/sensors/ecg-readiness.json",
            "ecg-readiness",
            "protocol/sensors/ecg-raw-type0-two-samples.json",
            "protocol/sensors/ecg-raw-type0-signed-24bit-boundaries.json",
            "protocol/sensors/ecg-raw-type0-truncated-sample-android-error.json",
            "protocol/sensors/ecg-raw-type3-android-frame-samples.json",
            "raw-type0-signed-24bit-parsing",
            "raw-type0-malformed-short-sample-policy",
            "android-raw-type3-frame-sample-ownership",
            "compile-verification-gate",
            "readSigned24",
            "malformedFrame",
            "PROTOCOL_ONLY_MIGRATION_OWNERSHIP"
        )
        val ACC_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "accGoldenVectorsDefineExecutableCommonRawCompressedAndMalformedPolicy",
            "accReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/sensors/acc-readiness.json",
            "acc-readiness",
            "protocol/sensors/acc-raw-type0-signed-boundaries.json",
            "protocol/sensors/acc-raw-type1-two-samples.json",
            "protocol/sensors/acc-compressed-type0-factor-half.json",
            "protocol/sensors/acc-compressed-type1-two-samples.json",
            "protocol/sensors/acc-compressed-type2-unsupported.json",
            "protocol/sensors/acc-compressed-type0-truncated-delta-header-android-error.json",
            "protocol/sensors/acc-compressed-type0-truncated-delta-header-ios-reference-only.json",
            "raw-type0-signed-axis-boundaries",
            "compressed-type0-millig-factor-scaling",
            "raw-type2-android-ownership",
            "truncated-compressed-delta-payload-policy",
            "compile-verification-gate",
            "parseDeltaFramesAll",
            "unsupportedCompressedFrame",
            "malformedFrame",
            "PROTOCOL_ONLY_MIGRATION_OWNERSHIP"
        )
        val GYR_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "gyrGoldenVectorsDefineExecutableCommonCompressedType0AndMalformedPolicy",
            "gyrReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/sensors/gyr-readiness.json",
            "gyr-readiness",
            "protocol/sensors/gyr-compressed-type0-two-samples.json",
            "protocol/sensors/gyr-compressed-type0-factor-half.json",
            "protocol/sensors/gyr-compressed-type1-android-only.json",
            "protocol/sensors/gyr-compressed-type2-unsupported.json",
            "protocol/sensors/gyr-raw-type0-unsupported.json",
            "protocol/sensors/gyr-compressed-type0-truncated-delta-header-android-error.json",
            "compressed-type0-reference-delta-decoding",
            "compressed-type0-factor-scaling",
            "android-compressed-type1-ownership",
            "truncated-compressed-delta-payload-policy",
            "compile-verification-gate",
            "parseDeltaFramesAll",
            "unsupportedFrame",
            "malformedFrame",
            "PROTOCOL_ONLY_MIGRATION_OWNERSHIP"
        )
        val MAG_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "magGoldenVectorsDefineExecutableCommonCompressedCalibrationAndMalformedPolicy",
            "magReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/sensors/mag-readiness.json",
            "mag-readiness",
            "protocol/sensors/mag-compressed-type0-two-samples.json",
            "protocol/sensors/mag-compressed-type0-factor-half.json",
            "protocol/sensors/mag-compressed-type1-calibration-status.json",
            "protocol/sensors/mag-compressed-type2-unsupported.json",
            "protocol/sensors/mag-raw-type0-unsupported.json",
            "protocol/sensors/mag-compressed-type0-truncated-delta-header-android-error.json",
            "compressed-type0-reference-delta-decoding",
            "compressed-type1-calibration-status-mapping",
            "compressed-type1-milligauss-to-gauss-conversion",
            "truncated-compressed-delta-payload-policy",
            "compile-verification-gate",
            "parseDeltaFramesAll",
            "calibrationStatus",
            "NOT_AVAILABLE",
            "unsupportedFrame",
            "malformedFrame",
            "PROTOCOL_ONLY_MIGRATION_OWNERSHIP"
        )
        val PPG_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "ppgGoldenVectorsDefineExecutableCommonRawScalarCompressedType13AndMalformedPolicy",
            "ppgFrameFamilyMigrationReadinessManifestNamesEveryPreMigrationParserPolicy",
            "protocol/sensors/ppg-frame-family-migration-readiness.json",
            "protocol/sensors/ppg-raw-type0-two-samples.json",
            "protocol/sensors/ppg-raw-type5-operation-mode-max.json",
            "protocol/sensors/ppg-raw-type6-sport-id.json",
            "protocol/sensors/ppg-compressed-type7-reference-status.json",
            "protocol/sensors/ppg-compressed-type8-reference-status.json",
            "protocol/sensors/ppg-compressed-type10-reference-status.json",
            "protocol/sensors/ppg-compressed-type10-full-status.json",
            "protocol/sensors/ppg-compressed-type13-reference-status.json",
            "protocol/sensors/ppg-compressed-type13-truncated-delta-header-android-error.json",
            "protocol/sensors/ppg-compressed-type7-truncated-delta-payload-malformed.json",
            "protocol/sensors/ppg-raw-type0-truncated-sample-malformed.json",
            "protocol/sensors/ppg-raw-type4-integration-gain-platform-shape.json",
            "protocol/sensors/ppg-raw-type4-truncated-integration-gain-malformed.json",
            "protocol/sensors/ppg-raw-type9-integration-gain-platform-shape.json",
            "protocol/sensors/ppg-raw-type14-integration-gain-platform-shape.json",
            "protocol/sensors/ppg-raw-type5-truncated-operation-mode-malformed.json",
            "protocol/sensors/ppg-raw-type6-truncated-sport-id-malformed.json",
            "parseDeltaFramesAll",
            "assertIntegrationGainPlatformShape",
            "numIntTs1",
            "platform shape equivalence",
            "iOS flattened platform shape",
            "current iOS green/red platform split",
            "platform-split-type10-red-green",
            "platform-split-integration-gain-shape",
            "compile-verified",
            "unsupportedFrame",
            "malformedFrame",
            "PROTOCOL_ONLY_MIGRATION_OWNERSHIP"
        )
        val PPI_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "ppiGoldenVectorsDefineExecutableCommonRawType0AndUnsupportedFramePolicy",
            "ppiReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/sensors/ppi-readiness.json",
            "ppi-readiness",
            "protocol/sensors/ppi-raw-type0-two-samples.json",
            "protocol/sensors/ppi-raw-type0-zero-timestamp-boundary.json",
            "protocol/sensors/ppi-raw-type0-truncated-sample-android-error.json",
            "protocol/sensors/ppi-compressed-type0-unsupported.json",
            "raw-type0-hr-rr-error-status-parsing",
            "raw-type0-zero-timestamp-policy",
            "raw-type0-timestamp-backfill",
            "truncated-raw-sample-policy",
            "compile-verification-gate",
            "unsupportedFrame",
            "malformedFrame",
            "skinContactSupported"
        )
        val PRESSURE_TEMPERATURE_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "pressureAndTemperatureGoldenVectorsDefineExecutableCommonRawType0Policy",
            "pressureAndTemperatureCompressedVectorsPinPlatformParityDeferralBeforeCommonParserMigration",
            "pressureTemperatureReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/sensors/pressure-temperature-readiness.json",
            "pressure-temperature-readiness",
            "COMPRESSED_PLATFORM_PARITY_VECTORS",
            "protocol/sensors/pressure-compressed-type0-android-factor-half.json",
            "protocol/sensors/pressure-raw-type0-single-sample.json",
            "protocol/sensors/pressure-raw-type0-truncated-sample-android-error.json",
            "protocol/sensors/temperature-raw-type0-ieee754-boundaries.json",
            "protocol/sensors/temperature-compressed-type0-flat-deltas.json",
            "protocol/sensors/temperature-compressed-type0-flat-deltas-android-two-samples.json",
            "pressure-raw-type0-ieee754-parsing",
            "temperature-raw-type0-ieee754-parsing",
            "compressed-pressure-one-channel-indexing-deferral",
            "compressed-temperature-sample-count-deferral",
            "compile-verification-gate",
            "compressedScalarDecisionNote",
            "readFloatLe",
            "unsupportedFrame",
            "malformedFrame",
            "PROTOCOL_ONLY_MIGRATION_OWNERSHIP"
        )
        val GNSS_LOCATION_OWNERSHIP_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "gnssLocationGoldenVectorsPinSharedParserPolicyWithAndroidProductionDelegation",
            "gnssLocationReadinessManifestNamesEverySharedParserDelegationFamily",
            "GNSS_LOCATION_SHARED_PARSER_VECTORS",
            "protocol/sensors/gnss-location-readiness.json",
            "gnss-location-readiness",
            "sharedParserAndroidProductionDelegation",
            "protocol/sensors/gnss-location-raw-type0-coordinate.json",
            "protocol/sensors/gnss-location-raw-type1-satellite-dilution.json",
            "protocol/sensors/gnss-location-raw-type2-satellite-summary.json",
            "protocol/sensors/gnss-location-raw-type3-nmea.json",
            "shared-parser-raw-type0-coordinate",
            "shared-parser-raw-type1-satellite-dilution",
            "shared-parser-raw-type2-satellite-summary",
            "shared-parser-raw-type3-nmea",
            "android-production-delegation",
            "non-ios-parser-ownership",
            "shared-parser-parity-gate",
            "compile-verification-gate",
            "coordinate",
            "satelliteDilution",
            "satelliteSummary",
            "nmea",
            "consumerTests.hasStringArray(\"ios\")",
            "platforms.booleanValue(\"ios\")",
            "platforms.booleanValue(\"common\")",
            "SHARED_GNSS_LOCATION_MIGRATION_OWNERSHIP"
        )
        val OFFLINE_HR_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "offlineHrGoldenVectorsDefineExecutableCommonRawAndUnsupportedFramePolicy",
            "offlineHrReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/sensors/offline-hr-readiness.json",
            "offline-hr-readiness",
            "protocol/sensors/offline-hr-raw-type0-empty.json",
            "protocol/sensors/offline-hr-raw-type0-hr-only-boundaries.json",
            "protocol/sensors/offline-hr-raw-type1-two-samples.json",
            "protocol/sensors/offline-hr-raw-type1-truncated-tuple-android-error.json",
            "raw-type0-hr-only-samples",
            "raw-type0-empty-recording",
            "raw-type1-hr-ppg-quality-corrected-hr-triples",
            "truncated-raw-type1-tuple-policy",
            "compile-verification-gate",
            "unsupportedCompressedFrame",
            "unsupportedFrame",
            "malformedFrame",
            "correctedHr"
        )
        val OFFLINE_RECORDING_METADATA_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "offlineRecordingFilenameGoldenVectorDefinesExecutableCommonTypePolicy",
            "offlineRecordingPmdFilesGoldenVectorDefinesExecutableCommonGroupingPolicy",
            "offlineRecordingTriggerGoldenVectorDefinesExecutableCommonModelProjectionPolicy",
            "offlineRecordingMetadataReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/offline-recording/metadata-readiness.json",
            "offline-recording-metadata-readiness",
            "sdk/offline-recording/filename-mapping.json",
            "sdk/offline-recording/pmdfiles-v2-grouping.json",
            "sdk/offline-recording/trigger-mapping.json",
            "filename-to-measurement-type-mapping",
            "pmdfiles-grouping",
            "representative-path-platform-policy",
            "compile-verification-gate",
            "strip-split-file-index-and-map-known-rec-filenames",
            "ignore-zero-size-recording-parts",
            "ignore-unknown-file-types-and-unparseable-date-or-time-paths",
            "android-normalizes-split-index-to-base-rec-path-while-ios-keeps-first-split-file-path",
            "offline-trigger-mapping"
        )
        val TRAINING_SESSION_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "trainingSessionReferenceDiscoveryGoldenVectorDefinesExecutableCommonTraversalAndClassificationPolicy",
            "trainingSessionMissingExerciseFileGoldenVectorDefinesExecutableCommonPlatformPolicy",
            "trainingSessionPayloadReadGoldenVectorDefinesExecutableCommonProgressMalformedAndUnknownSamplePolicy",
            "trainingSessionPayloadParserGoldenVectorDefinesExecutableCommonParserOwnershipPolicy",
            "trainingSessionReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/training-session/training-session-readiness.json",
            "training-session-readiness",
            "sdk/training-session/reference-discovery-two-sessions.json",
            "sdk/training-session/missing-exercise-file-platform-policy.json",
            "sdk/training-session/payload-read-policy.json",
            "sdk/training-session/payload-parser-policy.json",
            "reference-directory-traversal",
            "payload-fetch-order",
            "malformed-component-isolation",
            "byte-level-parser-dependency-gate",
            "platform-training-session-vector-reference-gate",
            "compile-verification-gate",
            "ignore-files-that-do-not-map-to-public-training-or-exercise-data-types",
            "android-currently-stores-first-file-path-while-ios-stores-exercise-directory-path",
            "Choose an explicit shared policy",
            "omit-only-the-malformed-component-and-continue-reading-remaining-files",
            "ignore-unknown-advanced-sample-lists-and-preserve-known-samples",
            "compute-progress-from-reference-file-sizes-and-last-completed-file",
            "trainingSessionByteLevelPayloadParserMigrationRequiresExplicitCommonProtoAndGzipDependencies",
            "add-common-protobuf-and-gzip-parser-dependencies-before-byte-level-payload-migration",
            "deferred-until-common-protobuf-and-gzip-parser-exist",
            "samples-advanced-gzip-protobuf",
            "PbExerciseSamples2",
            "gzip-protobuf",
            "SAMPLES_ADVANCED_FORMAT_GZIP"
        )
        val SKIN_TEMPERATURE_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "skinTemperatureGoldenVectorsDefineExecutableCommonRawType0Policy",
            "skinTemperatureReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "protocol/sensors/skin-temperature-readiness.json",
            "skin-temperature-readiness",
            "protocol/sensors/skin-temperature-raw-type0-single-sample.json",
            "protocol/sensors/skin-temperature-raw-type0-estimated-sample-rate.json",
            "protocol/sensors/skin-temperature-raw-type0-truncated-sample-ios-empty.json",
            "protocol/sensors/skin-temperature-raw-type1-unsupported.json",
            "raw-type0-ieee754-skin-temperature-parsing",
            "sample-rate-timestamp-estimation-policy",
            "ios-empty-malformed-payload-deferral",
            "truncated-raw-sample-policy",
            "compile-verification-gate",
            "isTimestampEstimated",
            "readFloatLe",
            "unsupportedFrame",
            "malformedFrame"
        )
        val SKIN_TEMPERATURE_DOMAIN_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "skinTemperatureDomainGoldenVectorsDefineExecutableCommonSourceDeviceAndUnknownEnumPolicy",
            "skinTemperatureDomainReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/skin-temperature/skin-temperature-domain-readiness.json",
            "skin-temperature-domain-readiness",
            "sdk/skin-temperature/core-proximal-empty-samples.json",
            "sdk/skin-temperature/distal-skin-two-samples.json",
            "sdk/skin-temperature/unknown-enums-platform-policy.json",
            "source-device-id-ownership",
            "empty-sample-list-preservation",
            "unknown-measurement-type-boundary",
            "unknown-sensor-location-boundary",
            "compile-verification-gate",
            "include-nullable-source-device-id-in-shared-model-or-adapt-it-at-platform-facade",
            "choose-null-or-explicit-unknown-before-shared-model-migration",
            "preserve-empty-list"
        )
        val DISK_SPACE_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "diskSpaceGoldenVectorsDefineExecutableCommonUnsignedFragmentPolicy",
            "diskSpaceReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/disk-space/disk-space-readiness.json",
            "disk-space-readiness",
            "sdk/disk-space/typical-fragments.json",
            "sdk/disk-space/uint32-max-fragment-platform-difference.json",
            "sdk/disk-space/malformed-truncated-varint.json",
            "byte-total-calculation",
            "zero-fragment-counts",
            "unsigned-uint32-fragment-size-policy",
            "typed-malformed-varint-parse-error",
            "compile-verification-gate",
            "treat-as-unsigned-uint32",
            "typed-parse-error",
            "parse-error",
            "UNSIGNED_32_BIT_MASK"
        )
        val SPO2_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "spo2GoldenVectorsDefineExecutableCommonOptionalTriggerAndUnknownEnumPolicy",
            "spo2ReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/spo2-test/spo2-readiness.json",
            "spo2-readiness",
            "sdk/spo2-test/full-passed-normal.json",
            "sdk/spo2-test/ios-trigger-automatic.json",
            "sdk/spo2-test/omitted-optionals.json",
            "sdk/spo2-test/unknown-spo2-class-platform-difference.json",
            "optional-protobuf-presence-preservation",
            "nullable-trigger-type-policy",
            "unknown-spo2-class-boundary",
            "compile-verification-gate",
            "preserve-protobuf-presence",
            "include-nullable-trigger-type-in-shared-model-when-source-proto-exposes-it",
            "map-to-null-with-typed-warning-or-error-boundary-before-public-model"
        )
        val WATCH_FACE_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "watchFaceGoldenVectorsDefineExecutableCommonFieldComplicationAndMalformedPolicy",
            "watchFaceReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/watch-face/watch-face-readiness.json",
            "watch-face-readiness",
            "sdk/watch-face/all-fields-with-complications.json",
            "sdk/watch-face/default-fields.json",
            "sdk/watch-face/malformed-too-short.json",
            "sdk/watch-face/unknown-complication-preserved.json",
            "default-field-zeroing",
            "complication-id-order-preservation",
            "unknown-complication-raw-id-preservation",
            "kvtx-wrapper-metadata",
            "compile-verification-gate",
            "preserve-raw-id-in-config-fields-and-return-null-for-enum-lookup",
            "MINIMUM_FLATBUFFER_HEADER_SIZE",
            "complicationNameOrNull"
        )
        val KVTX_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "kvtxGoldenVectorsDefineExecutableCommonScriptPolicy",
            "kvtxReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/kvtx/kvtx-readiness.json",
            "kvtx-readiness",
            "sdk/kvtx/write-commit-basic.json",
            "sdk/kvtx/write-commit-uint32-max-key.json",
            "sdk/kvtx/append-ex-zero-index.json",
            "sdk/kvtx/truncated-write-payload-ios-nil.json",
            "write-and-commit-framing",
            "unsigned-uint32-key-preservation",
            "unknown-command-stop-policy",
            "malformed-script-typed-error-policy",
            "compile-verification-gate",
            "malformedScript",
            "typed malformed-script parse error",
            "PolarKvtxScriptCodec"
        )
        val D2H_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "d2hGoldenVectorsDefineExecutableCommonMappingFilteringAndOrderingPolicy",
            "d2hNotificationMappingReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/d2h-notifications/mapping-readiness.json",
            "d2h-notification-mapping-readiness",
            "sdk/d2h-notifications/filesystem-created.json",
            "sdk/d2h-notifications/repeated-sync-required-and-stop-gps.json",
            "sdk/d2h-notifications/sync-required-invalid-payload.json",
            "sdk/d2h-notifications/unknown-id-filtered.json",
            "known-notification-id-mapping",
            "unknown-notification-id-filtering",
            "raw-parameter-preservation",
            "invalid-payload-null-parse-policy",
            "repeated-notification-ordering",
            "compile-verification-gate",
            "PbPFtpSyncRequiredParams",
            "STOP_GPS_MEASUREMENT",
            "notificationTypeOrNull"
        )
        val D2H_STREAM_RUNTIME_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "d2hStreamRuntimeGoldenVectorDefinesExecutableCommonLateErrorAndCancellationPolicy",
            "d2hStreamRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/d2h-notifications/stream-runtime-policy.json",
            "sdk/d2h-notifications/stream-runtime-readiness.json",
            "d2h-stream-runtime-readiness",
            "late-error-after-emitted-notification",
            "consumer-cancels-after-first-notification",
            "unknown-notification-between-known-values-is-filtered",
            "failed-subscribe-does-not-register-observer",
            "cancelledStreams",
            "upstreamCancelled",
            "ignoredAfterCancel",
            "mapped-value-before-late-error",
            "consumer-cancellation-upstream-cancel",
            "suppress-notifications-after-cancel",
            "unknown-notification-filtering",
            "failed-subscribe-no-observer",
            "active-observer-cleanup-gate",
            "facade-error-mapping-gate",
            "compile-verification-gate"
        )
        val ACTIVITY_SUMMARY_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "activitySampleGoldenVectorsDefineExecutableCommonAggregationAndMalformedPolicy",
            "automaticSampleGoldenVectorsDefineExecutableCommonTriggerDeltaAndStatusPolicy",
            "dailySummaryGoldenVectorDefinesExecutableCommonSummaryProjectionPolicy",
            "activitySummaryReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/activity-samples/activity-summary-readiness.json",
            "activity-summary-readiness",
            "sdk/activity-samples/two-files-step-aggregation.json",
            "sdk/activity-samples/malformed-sample-file-platform-policy.json",
            "sdk/automatic-samples/hr-all-trigger-types.json",
            "sdk/automatic-samples/ppi-deltas-statuses.json",
            "sdk/daily-summary/full-summary.json",
            "activity-step-aggregation",
            "automatic-hr-trigger-mapping",
            "automatic-ppi-delta-decompression",
            "daily-summary-scalar-projection",
            "platform-activity-vector-reference-gate",
            "compile-verification-gate",
            "choose one shared malformed activity sample policy",
            "TRIGGER_TYPE_HIGH_ACTIVITY",
            "INTERVAL_DENOTES_OFFLINE_PERIOD",
            "DSUM.BPB"
        )
        val SLEEP_NIGHTLY_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "nightlyRechargeGoldenVectorsDefineExecutableCommonDateDefaultAndMalformedPolicy",
            "sleepGoldenVectorsDefineExecutableCommonOffsetTimezoneHypnogramAndOptionalPolicy",
            "sleepNightlyReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/nightly-recharge/sleep-nightly-readiness.json",
            "sleep-nightly-readiness",
            "sdk/nightly-recharge/malformed-response.json",
            "sdk/nightly-recharge/missing-modified-default-metrics.json",
            "sdk/sleep/sleep-offset-platform-policy.json",
            "sdk/sleep/sleep-stage-hypnogram.json",
            "sdk/sleep/sleep-timezone-offsets.json",
            "nightly-malformed-payload-null-policy",
            "sleep-end-offset-field-policy",
            "sleep-timezone-to-utc-instant-policy",
            "sleep-partial-night-optional-policy",
            "compile-verification-gate",
            "return-null-for-current-platform-facades-before-choosing-a-shared-typed-error",
            "utcInstantString"
        )
        val USER_DEVICE_SETTINGS_COMMON_POLICY_REQUIRED_TERMS = listOf(
            "userDeviceSettingsGoldenVectorsDefineExecutableCommonPresenceAndWritePolicy",
            "userDeviceSettingsModelReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/user-device-settings/settings-model-readiness.json",
            "user-device-settings-model-readiness",
            "compileVerifiedPreMigrationCharacterization",
            "sdk/user-device-settings/from-proto-full-settings.json",
            "sdk/user-device-settings/from-proto-omitted-optional-settings.json",
            "sdk/user-device-settings/to-proto-telemetry-platform-difference.json",
            "sdk/user-device-settings/to-proto-writable-settings.json",
            "protobuf-presence-preservation",
            "nullable-omitted-optional-settings",
            "writable-settings-serialization",
            "encoder-owned-trusted-last-modified",
            "explicit-telemetry-write-policy",
            "platform-default-divergence",
            "platform-user-device-settings-vector-references",
            "compile-verification-gate",
            "preserve-protobuf-presence",
            "write-explicit-telemetry",
            "lastModifiedTrusted"
        )
        val SHARED_CONSUMPTION_REQUIRED_TERMS = listOf(
            "implementation project(':shared')",
            "PolarBleSdkShared.framework",
            "current Swift facade",
            ":shared:bundleAndroidMainAar",
            ":shared:linkDebugFrameworkIosX64",
            "may depend on shared code only when a behavior slice",
            "scripts/verify_android_example_aar_consumption.sh",
            "polar-ble-sdk-shared.aar",
            "SwiftPM/watchOS",
            "fallback-only",
            "rollback path for every shared-module adoption step"
        )
        val PLATFORM_OWNED_COVERAGE_ROWS = mapOf(
            "BLE device session lifecycle" to listOf("Partial", "platform-owned", "Keep platform-specific"),
            "GATT clients" to listOf("Partial", "platform-owned", "Keep transport clients platform-specific"),
            "Android Bluedroid host behavior" to listOf("Platform-specific", "Do not migrate to common code"),
            "iOS CoreBluetooth host behavior" to listOf("Platform-specific", "Do not migrate to common code")
        )
        val FULL_COVERAGE_EXIT_CRITERIA_TERMS = listOf(
            "Every row marked `Partial` has either new tests, documented platform-specific ownership, or a migration deferral note.",
            "Every parser that moves to KMP has shared golden vectors covering valid, invalid, empty, boundary, and unknown-value cases.",
            "Android and iOS characterization tests use the same vectors or prove equivalent expected behavior.",
            "Public facade tests prove Android and iOS APIs keep current semantics after shared delegation.",
            "Hardware-dependent tests are limited to smoke coverage; deterministic behavior is covered without physical devices.",
            "Validation commands are documented and runnable for the migrated slice."
        )
        val HARDWARE_SMOKE_VALIDATION_TERMS = listOf(
            "## Hardware And Device Smoke Boundary",
            "Physical BLE hardware validation is smoke coverage for adapter wiring, radio availability, and real-device compatibility only",
            "must not replace deterministic golden-vector tests, shared `commonTest` policy tests, Android facade characterization, or iOS XCTest characterization before migration",
            "record the tested device/firmware, feature path, and result in the slice notes"
        )
        val GRADLE_BATCH_VALIDATION_TERMS = listOf(
            "Batch several file, test, vector, and documentation edits before invoking Gradle validation",
            "coverage work does not stall on repeated manual app prompts",
            "Prefer the library and shared-module gates below",
            "do not run broad app or example Gradle tasks unless the slice actually changes app/example surfaces"
        )
        val VALIDATION_MINIMUM_TDD_LINK_TERMS = listOf(
            "`KmpTddStrategy.md` defines the minimum validation before merging a migration slice",
            "KMP common tests, existing Android tests, existing iOS tests or an equivalent documented Apple-platform command, reviewed golden vectors as API contracts, and no unrelated platform refactor",
            "current executable way to satisfy that minimum validation set"
        )
        val PLATFORM_OWNED_BACKLOG_REQUIRED_TERMS = listOf(
            "Platform-owned gaps: Android Bluedroid host behavior, iOS CoreBluetooth host behavior, GATT client host interactions, and platform identifier routing should stay platform-specific unless a future slice defines a pure codec or deterministic state machine contract.",
            "BLE device lifecycle and GATT clients: keep platform-owned unless a slice extracts a pure codec or deterministic state machine with common fake-transport tests."
        )
        val PODSPEC_SOURCE_FILES = Regex("s\\.source_files\\s*=\\s*'([^']+)'")
        val PODSPEC_RESOURCES = Regex("s\\.resources\\s*=\\s*\\[(.*)]")
        val PODSPEC_RESOURCE_REFERENCE = Regex("'(sources/iOS/ios-communications/Sources/[A-Za-z0-9_./-]+)'")
        val REQUIRED_VALIDATION_SECTIONS = listOf("## Android", "## iOS", "## KMP Common")
        val VALIDATION_NON_GRADLE_GATE_TERMS = listOf(
            "consumerTests",
            "shared common test",
            "fixture README `.kt` artifact references",
            "TDD strategy golden-vector example",
            "fake-transport runtime matrix",
            "public facade operation ledger",
            "stale future-fake-transport wording",
            "portability allowlist",
            "ruby scripts/ios_xcode_validation_probe.rb",
            "xcodebuild -list -project sources/iOS/ios-communications/iOSCommunications.xcodeproj",
            "workspace discovery succeeds",
            "sources/iOS/ios-communications/Pods",
            "is present",
            "no known local XCTest infrastructure blockers"
        )
        val IOS_XCODE_PROBE_REQUIRED_TERMS = listOf(
            "EXPECTED_TARGETS",
            "EXPECTED_SCHEMES",
            "workspace-not-valid-to-xcodebuild",
            "pods-absent",
            "coresimulator-unavailable"
        )
        val IOS_XCTEST_EXECUTION_GATE_REQUIRED_TERMS = listOf(
            "swiftc -parse sources/iOS/ios-communications/Tests/**/*.swift",
            "does not replace the full XCTest execution gate",
            "xcodebuild test -workspace sources/iOS/ios-communications/iOSCommunications.xcworkspace -scheme iOSCommunications -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5'",
            "Use XCTest failures from that command as migration evidence"
        )
        val IOS_XCTEST_REMAINING_WORK_REQUIRED_TERMS = listOf(
            "The iOS XCTest gate is unblocked and passes with `xcodebuild test -quiet -workspace sources/iOS/ios-communications/iOSCommunications.xcworkspace -scheme iOSCommunications -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.5'",
            "Keep full iOS XCTest in the required validation set for future slices",
            "`swiftc -parse` is only a syntax gate and must not replace the passing simulator XCTest command"
        )
        val KMP_TDD_STRATEGY_VECTOR_EXAMPLE_TERMS = listOf(
            "\"id\"",
            "\"area\"",
            "\"case\"",
            "\"source\"",
            "\"input\"",
            "\"expected\"",
            "\"consumerTests\"",
            "\"platforms\"",
            "\"android\": true",
            "\"ios\": true",
            "\"common\": true"
        )
        val KMP_TDD_STRATEGY_STALE_VECTOR_EXAMPLE_TERMS = listOf(
            "\"feature\":"
        )
        val TDD_MINIMUM_VALIDATION_TERMS = listOf(
            "## Minimum Validation Before Merging a Slice",
            "KMP common tests for the slice pass.",
            "Existing Android tests for the slice pass.",
            "Existing iOS tests for the slice pass or an equivalent Apple-platform command is documented.",
            "New golden vectors are reviewed as API contracts.",
            "No unrelated platform code is refactored in the same slice."
        )
        val TDD_REGRESSION_POLICY_TERMS = listOf(
            "## Regression Policy",
            "current behavior is documented by a characterization test",
            "new behavior is documented by an updated expected output",
            "reason is written in the migration slice notes",
            "consumer-visible only if release notes or a migration guide are updated"
        )
        val TDD_COVERAGE_EXPECTATION_TERMS = listOf(
            "## Coverage Expectations",
            "Line coverage is less important than input coverage.",
            "every supported frame version",
            "every field boundary",
            "malformed payloads",
            "empty payloads",
            "unknown enum values",
            "every state transition and cancellation path"
        )
        val FIRST_RECOMMENDED_TDD_SLICE_TERMS = listOf(
            "## First Recommended TDD Slice",
            "Start with a low-risk deterministic parser",
            "`PolarDeviceUuid`",
            "time utilities",
            "one PMD sensor parser with strong existing tests",
            "Do not start with BLE scanning, connection lifecycle, firmware network calls, or public API redesign."
        )
        const val COVERAGE_INVENTORY_COLUMN_COUNT = 5
        const val COVERAGE_BEHAVIOR_COLUMN = 0
        const val COVERAGE_STATUS_COLUMN = 3
        const val COVERAGE_REQUIRED_COLUMN = 4
        val PARTIAL_ROW_GATE_TERMS = listOf("before", "keep ", "do not migrate", "platform-specific", "common fake", "fake-transport", "implement", "add ")
        val MIGRATION_README_TERMS = listOf("common", "shared", "migrat")
        val TEST_FILE_NAMES = Regex(".*Tests?\\.(kt|swift)")
        val TEST_REFERENCE = Regex("`([^`]+Tests?\\.(?:kt|swift))`")
        val FIXTURE_README_BARE_SHARED_COMMON_TEST_REFERENCE = Regex("`([A-Za-z0-9]+(?:CommonPolicyTest|CommonTest|UtilityCommonPolicyTest|RuntimePolicyCommonTest|CommonFakeWorkflowTest|ByteCodecCommonPolicyTest|MappingCommonPolicyTest|TransportPolicyCommonTest|CompressionPolicyCommonTest))`")
        val FIXTURE_README_KOTLIN_ARTIFACT_REFERENCE = Regex("`([^`]+\\.kt)`")
        val RUNTIME_POLICY_CONSUMER_TEST = Regex(".*(Runtime|FakeWorkflow|FakeRuntime|TransportPolicy).*")
        val ANDROID_MIN_SDK_VERSION = Regex("minSdk(?:Version)?\\s*(?:=)?\\s*(\\d+)")
        val ANDROID_MIN_SDK_REFERENCE = Regex("minSdk(?:Version)?\\s*(?:=)?\\s*\\**\\s*(\\d+)")
        val CHECKED_CHECKLIST_ITEM = Regex("(?m)^- \\[x] (.+)$")
        val CHECKLIST_EVIDENCE_ROW = Regex("(?m)^\\| ([^|]+) \\| ([^|]+) \\|$")
        val COMPLETED_ITEM_EVIDENCE_SECTION = Regex("(?s)## Completed Item Evidence.*?(?=\\n## |\\z)")
        val OPEN_ITEM_RATIONALE_SECTION = Regex("(?s)## Open Item Rationale.*?(?=\\n## |\\z)")
        val RELEASE_READINESS_ITEMS = listOf(
            "Android AAR builds and is consumed by the Android example.",
            "Swift Package integration builds and is consumed by the iOS example.",
            "CocoaPods integration is verified or explicitly deprecated.",
            "Generated Android and iOS API docs are regenerated if public APIs changed.",
            "Migration guides are updated for consumer-visible changes.",
            "Known platform differences are documented.",
            "A rollback path exists for every shared module adoption step."
        )
        val MIGRATION_STOP_CONDITION_TERMS = listOf(
            "## Stop Conditions",
            "Android and iOS current behavior disagree without a documented decision",
            "a parser has no golden-vector coverage",
            "a shared implementation requires platform APIs",
            "a platform facade changes public behavior unintentionally",
            "validation requires physical hardware for logic that should be fakeable"
        )
        val PER_SLICE_TDD_CHECKLIST_TERMS = listOf(
            "## Per-Slice TDD Checklist",
            "Choose one behavior slice.",
            "List current Android implementation files.",
            "List current iOS implementation files.",
            "List existing Android tests.",
            "List existing iOS tests.",
            "Add or update Android characterization tests.",
            "Add or update iOS characterization tests.",
            "Add shared golden vectors.",
            "Add failing KMP common tests.",
            "Implement shared code.",
            "Delegate Android implementation to shared code.",
            "Delegate iOS implementation to shared code.",
            "Re-run Android, iOS, and KMP tests.",
            "Remove duplicated platform logic only after both facades pass.",
            "Update docs if public behavior changed."
        )
        val REVIEW_CHECKLIST_TERMS = listOf(
            "## Review Checklist",
            "The pull request moves only one coherent behavior slice.",
            "Tests fail without the shared implementation.",
            "Golden vectors are understandable and minimal.",
            "Android and iOS public APIs remain compatible unless the change is explicitly documented.",
            "Platform-specific BLE behavior remains platform-specific.",
            "New common code has no hidden Android/JVM or Apple-only dependency.",
            "Error mapping is covered by tests.",
            "Cancellation and timeout behavior is covered when streams or suspend functions are involved.",
            "Build metadata does not depend on Android `BuildConfig` in common code."
        )
        val SUGGESTED_SLICE_ORDER_TERMS = listOf(
            "## Suggested Slice Order",
            "1. Device ID and UUID utilities.",
            "2. Time and date utilities.",
            "3. Product capability JSON parsing.",
            "4. PMD settings and control-point response parsing.",
            "5. ECG parser.",
            "6. ACC, GYR, and MAG parsers.",
            "7. PPG and PPI parsers.",
            "8. Pressure, temperature, and skin-temperature parsers.",
            "9. Offline recording metadata and status parsing.",
            "10. Training-session metadata parsing.",
            "11. Firmware/status mapping.",
            "12. Shared fake transport contract.",
            "13. Runtime state machines.",
            "14. Public API compatibility adapters."
        )
        val BACKTICK_REFERENCE = Regex("`([^`]+)`")
        val VALIDATION_ARTIFACT_REFERENCE = Regex("(sources|testdata|documentation)/[A-Za-z0-9_./-]+\\.(md|kt|swift|json|podspec)")
        val SOURCES_BUILD_PHASE_SECTION = Regex("(?s)/\\* Begin PBXSourcesBuildPhase section \\*/.*?/\\* End PBXSourcesBuildPhase section \\*/")
        val IOS_HELPER_SOURCE_PHASE_REFERENCE = Regex("/\\* GoldenVectorTestData\\.swift in Sources \\*/")
        const val IOS_TEST_TARGET_COUNT = 2
        val FAKE_TRANSPORT_REQUIRED_OPERATIONS = listOf("read", "write", "subscribe", "unsubscribe")
        val FAKE_TRANSPORT_REQUIRED_OUTCOMES = listOf("Bytes", "TransportError", "ResponseError", "Timeout", "Complete")
        const val FAKE_TRANSPORT_MATRIX_COLUMN_COUNT = 5
        const val FAKE_TRANSPORT_LEDGER_COLUMN_COUNT = 4
        const val FAKE_TRANSPORT_LEDGER_STATUS_COLUMN = 1
        const val FAKE_TRANSPORT_LEDGER_EVIDENCE_COLUMN = 2
        const val FAKE_TRANSPORT_LEDGER_GATE_COLUMN = 3
        const val PUBLIC_FACADE_LEDGER_COLUMN_COUNT = 6
        const val PUBLIC_FACADE_LEDGER_STATUS_COLUMN = 1
        const val PUBLIC_FACADE_LEDGER_ANDROID_COLUMN = 2
        const val PUBLIC_FACADE_LEDGER_IOS_COLUMN = 3
        const val PUBLIC_FACADE_LEDGER_SHARED_COLUMN = 4
        const val PUBLIC_FACADE_LEDGER_GATE_COLUMN = 5
        val PUBLIC_FACADE_OPERATION_FAMILIES = listOf(
            "User device settings writes and reads",
            "REST service discovery and description",
            "Low-level file read write delete and list",
            "Disk and time facade reads/writes",
            "Stored data cleanup and deletion workflows",
            "Reset sync notification and H10 recording commands",
            "Firmware update workflow",
            "Offline trigger runtime"
        )
        val FIRMWARE_FACADE_GATE_REQUIRED_TERMS = listOf(
            "facade gate open",
            "PolarFirmwareUpdateUtilsTest.kt",
            "PolarFirmwareUpdateUtilsTest.swift",
            "workflow-runtime-policy.json",
            "FirmwareWorkflowRuntimePolicyCommonTest.kt",
            "injectable production dependencies",
            "facade progress",
            "cleanup",
            "cancellation",
            "retry scheduling",
            "error-mapping tests before delegation"
        )
        val FACADE_GATE_OPEN_REQUIRED_TERMS = mapOf(
            "User device settings writes and reads" to listOf(
                "facade gate open",
                "BDBleApiImplTest.kt",
                "PolarUserDeviceSettingsTest.kt",
                "UserDeviceSettingsCommonPolicyTest.kt",
                "PolarBleApiImplTests.swift",
                "PolarUserDeviceSettingsUtilsTest.swift",
                "settings-runtime-policy.json",
                "UserDeviceSettingsRuntimePolicyCommonTest.kt",
                "protobuf serialization",
                "platform defaults",
                "daylight-saving time source",
                "public error mapping before delegation"
            ),
            "Stored data cleanup and deletion workflows" to listOf(
                "facade gate open",
                "BDBleApiImplTest.kt",
                "PolarBleApiImplTests.swift",
                "cleanup-workflow-policy.json",
                "StoredDataCleanupRuntimePolicyCommonTest.kt",
                "facade compatibility tests",
                "cleanup error/path splits before delegation"
            ),
            "Reset sync notification and H10 recording commands" to listOf(
                "facade gate open",
                "BDBleApiImplTest.kt",
                "PolarBleApiImplTests.swift",
                "reset-sync-h10-command-policy.json",
                "CommandRuntimePolicyCommonTest.kt",
                "platform facade success/error compatibility tests",
                "sync failure splits before delegation"
            ),
            "Firmware update workflow" to FIRMWARE_FACADE_GATE_REQUIRED_TERMS
        )
        val RUNTIME_PINNED_FACADE_LEDGER_REQUIRED_TERMS = mapOf(
            "REST service discovery and description" to listOf(
                "facade response-error pinned",
                "BDBleApiImplTest.kt",
                "PolarBleApiImplTests.swift",
                "rest-facade-runtime-policy.json",
                "rest-request-transport-policy.json",
                "RestFacadeRuntimePolicyCommonTest.kt",
                "RestRequestTransportPolicyCommonTest.kt",
                "response-error platform mapping",
                "empty-success policy",
                "facade-level public error compatibility assertions",
                "additional delegated REST operations before shared REST runtime delegation"
            ),
            "Low-level file read write delete and list" to listOf(
                "facade empty-read and read/write/delete response-error pinned",
                "BDBleApiImplTest.kt",
                "PolarBleApiImplTests.swift",
                "file-facade-runtime-policy.json",
                "runtime-error-policy.json",
                "FileFacadeRuntimePolicyCommonTest.kt",
                "FileRuntimeErrorPolicyCommonTest.kt",
                "empty read payload success",
                "read/write/delete response errors",
                "write-stream failures",
                "facade-level public error compatibility assertions",
                "every additional delegated file operation before shared runtime delegation"
            ),
            "Disk and time facade reads/writes" to listOf(
                "facade query-error pinned",
                "BDBleApiImplTest.kt",
                "PolarBleApiImplTests.swift",
                "disk-time-query-policy.json",
                "DiskTimeRuntimePolicyCommonTest.kt",
                "disk/local-time transport-error terminals",
                "filesystem capability gates",
                "additional public error mapping before delegation"
            ),
            "Offline trigger runtime" to listOf(
                "Android/iOS facade and adapter gates partially covered",
                "BDBleApiImplTest.kt",
                "PolarBleApiImplTests.swift",
                "BlePmdClientTest.kt",
                "BlePmdClientTest.swift",
                "trigger-runtime-policy.json",
                "OfflineTriggerRuntimePolicyCommonTest.kt",
                "response-queue cleanup split explicit",
                "cancellation coverage only if production shared delegation introduces cancellable tasks, observers, or streams"
            )
        )
        val PSFTP_TIMEOUT_LEDGER_REQUIRED_TERMS = mapOf(
            "Timeout without notification" to listOf(
                "notification-timeout-policy.json",
                "PsFtpRuntimePolicyCommonTest.kt",
                "no built-in timeout",
                "consumer-owned virtual-clock timeout cleanup",
                "fake-clock or injectable-timeout facade compatibility before production timeout delegation"
            ),
            "PSFTP notification continuation timeout" to listOf(
                "notification-continuation-timeout-policy.json",
                "PsFtpRuntimePolicyCommonTest.kt",
                "typed continuation timeout",
                "without wall-clock waits",
                "fake-clock facade compatibility before production PSFTP timeout delegation"
            ),
            "PSFTP write acknowledgement timeout" to listOf(
                "write-ack-timeout-policy.json",
                "PsFtpRuntimePolicyCommonTest.kt",
                "typed write-ack timeout",
                "without wall-clock waits",
                "fake-clock facade compatibility before production PSFTP write-timeout delegation"
            )
        )
        val FAKE_TRANSPORT_PRE_MIGRATION_GATE_REQUIRED_TERMS = listOf(
            "A runtime migration slice must name the exact rows from the matrix that it implements.",
            "A slice that delegates only parser/model code does not need fake transport tests unless it changes stream or facade behavior.",
            "A slice that delegates public facade operations must include both platform facade tests and common fake-transport tests for success, error, cancellation, and timeout where applicable.",
            "A slice that keeps a transport area platform-specific must update `KmpCoverageInventory.md` with the ownership reason and the existing platform tests that remain authoritative.",
            "A runtime test must assert observer cleanup or cancellation propagation whenever it opens a stream, registers a listener, or starts an internal task."
        )
        val FAKE_TRANSPORT_HARNESS_DESCRIPTION_REQUIRED_TERMS = listOf(
            "deterministic command capture",
            "payload capture",
            "scripted byte responses",
            "response errors",
            "transport errors",
            "completion",
            "timeout behavior",
            "connection-state guards",
            "disconnect-after-operation limits",
            "active observer counts",
            "idempotent stream cancellation",
            "cleanup callback counts",
            "upstream cancellation observation",
            "virtual clock for timeout checks without wall-clock sleeps"
        )
        val SHARED_COMMON_PRODUCTION_CODEC_DEPENDENCY_TERMS = listOf(
            "protobuf",
            "flatbuffers",
            "flatbuffer",
            "crypto",
            "cryptography",
            "gzip",
            "zlib",
            "compression",
            "okio",
            "kotlinx-io"
        )
        val BYTE_LEVEL_COMMON_DEPENDENCY_DEFERRAL_TERMS = mapOf(
            "KmpFullCoverageTddBacklog.md" to listOf(
                "add real common protobuf/gzip production dependencies",
                "full AES implementation ownership still must be chosen",
                "add byte-level shared codec vectors before moving gzip/deflate behavior into common code",
                "byte-identical output",
                "KVTX"
            ),
            "KmpCoverageInventory.md" to listOf(
                "Keep generic iOS `Data.deflated`/`Data.inflated` platform-specific unless a future shared REST codec deliberately standardizes gzip/zlib behavior for KMP common code.",
                "Keep iOS nil-on-truncation compatibility adapter-owned if required while common parsing uses typed malformed-script errors.",
                "semantic and codec-ownership/readiness policy executable"
            ),
            "KmpPreMigrationRemainingWork.md" to listOf(
                "Add real common protobuf/gzip/crypto/codec dependencies",
                "training-session payload parsing",
                "PMD AES secret handling",
                "compression helpers",
                "shared FlatBuffer/KVTX byte-identical output decision"
            ),
            "payload-read-policy.json" to listOf(
                "byteLevelParserGate",
                "add-common-protobuf-and-gzip-parser-dependencies-before-byte-level-payload-migration",
                "deferred-until-common-protobuf-and-gzip-parser-exist"
            ),
            "payload-parser-policy.json" to listOf(
                "Before moving byte-level training payload parsing to common code, add production common protobuf and gzip dependencies",
                "without claiming common byte decoding is implemented",
                "training-session-summary-protobuf",
                "exercise-summary-protobuf",
                "route-protobuf",
                "route-gzip-protobuf",
                "route-advanced-protobuf",
                "route-advanced-gzip-protobuf",
                "samples-protobuf",
                "samples-gzip-protobuf",
                "samples-advanced-gzip-protobuf",
                "gzip-protobuf"
            ),
            "training-session-readiness.json" to listOf(
                "byte-level-parser-dependency-gate",
                "real byte-level protobuf/gzip decoding remains deferred until common production parser dependencies exist and are compile-verified"
            ),
            "secret-readiness.json" to listOf(
                "AES block-alignment gating",
                "production common AES provider selection remains an explicit implementation gate rather than a test-only shortcut"
            ),
            "rest-event-compression-readiness.json" to listOf(
                "android-gzip-codec-reference-gate",
                "ios-deflate-codec-reference-gate",
                "normalize-or-preserve-codec-decision-gate"
            ),
            "watch-face-readiness.json" to listOf(
                "byte-identical FlatBuffer output remains platform-specific unless production shared FlatBuffer builders are deliberately introduced and compile-verified"
            )
        )
        val FAKE_TRANSPORT_TEST_REQUIRED_TERMS = listOf(
            "FakeTransportCommand(FakeTransportOperation.READ",
            "FakeTransportCommand(FakeTransportOperation.WRITE",
            "FakeTransportCommand(FakeTransportOperation.SUBSCRIBE",
            "FakeTransportCommand(FakeTransportOperation.UNSUBSCRIBE",
            "FakeTransportOutcome.Bytes",
            "FakeTransportOutcome.ResponseError",
            "FakeTransportOutcome.TransportError",
            "FakeTransportOutcome.Complete",
            "FakeTransportOutcome.Timeout",
            "\"0a0b\""
        )
        val FAKE_TRANSPORT_CLEANUP_REQUIRED_TERMS = listOf(
            "FakeTransportSubscription",
            "activeObserverCount",
            "cancelledStreams",
            "openStream",
            "cancelStream",
            "upstreamCancelled"
        )
        val FAKE_TRANSPORT_CLEANUP_TEST_REQUIRED_TERMS = listOf(
            "stream cancellation removes observer cancels upstream and is idempotent",
            "failed stream subscription does not register observer",
            "transport.activeObserverCount",
            "transport.cancelledStreams",
            "subscription.upstreamCancelled"
        )
        val FAKE_TRANSPORT_COMMON_REQUIRED_TERMS = listOf(
            "ScriptedCommonFakeTransport",
            "CommonFakeTransportCommand",
            "CommonFakeTransportOperation",
            "CommonFakeTransportOutcome",
            "CommonFakeTransportSubscription",
            "REMOVE",
            "startConnected",
            "disconnectAfterOperations",
            "isConnected",
            "activeObserverCount",
            "cancelledStreams",
            "cleanupCallbackCount",
            "upstreamCancelled",
            "CommonFakeServiceReadinessGate",
            "awaitReady",
            "CommonFakeVirtualClock",
            "advanceBy",
            "hasTimedOut"
        )
        val FAKE_TRANSPORT_COMMON_TEST_REQUIRED_TERMS = listOf(
            "capturesCommandOrderPayloadsAndScriptedOutcomes",
            "returnsTimeoutForUnscriptedOperations",
            "connectionStateGuardsDisconnectedStartAndDisconnectAfterOperationLimit",
            "disconnected-after-1-operations",
            "streamCancellationRemovesObserverCancelsUpstreamAndIsIdempotent",
            "failedStreamSubscriptionDoesNotRegisterObserver",
            "serviceReadinessGateRecordsAttemptsAndTimesOutDeterministically",
            "virtualClockAdvancesTimeoutsWithoutWallClockSleep",
            "service-readiness",
            "CommonFakeVirtualClock",
            "transport.cleanupCallbackCount",
            "\"0a0b\""
        )
        val FAKE_TRANSPORT_COMMON_REST_RUNTIME_TEST_REQUIRED_TERMS = listOf(
            "restRequestTransportPolicyVectorRunsThroughProductionCommonPlanner",
            "restRequestTransportReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "rest-request-transport-policy",
            "sdk/rest-service/rest-request-transport-policy.json",
            "sdk/rest-service/rest-request-transport-readiness.json",
            "rest-request-transport-readiness",
            "PolarRuntimeOrchestration.planRestRequestTransport",
            "service-list-request-error-payload",
            "service-description-request-error-payload",
            "service-list-empty-transport-response",
            "service-description-empty-transport-response",
            "requires-empty-response-policy",
            "empty-successful-response-policy-gate",
            "response-error-payload-status",
            "response-error-payload-message",
            "facade-error-mapping-deferred",
            "compile-verification-gate",
            "PolarRestRequestTransportOperation"
        )
        val FAKE_TRANSPORT_COMMON_REST_FACADE_RUNTIME_TEST_REQUIRED_TERMS = listOf(
            "restFacadeRuntimePolicyVectorDefinesExecutableCommonRequestPlanning",
            "restFacadeRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/rest-service/rest-facade-runtime-policy.json",
            "sdk/rest-service/rest-facade-runtime-readiness.json",
            "rest-facade-runtime-policy",
            "rest-facade-runtime-readiness",
            "list-rest-api-services-success",
            "get-rest-api-description-success",
            "list-rest-api-services-request-failure",
            "get-rest-api-description-request-failure",
            "list-rest-api-services-response-error",
            "get-rest-api-description-response-error",
            "list-rest-api-services-empty-success",
            "get-rest-api-description-empty-success",
            "list-rest-api-services-malformed-success",
            "get-rest-api-description-malformed-success",
            "/REST/SERVICE.API",
            "/REST/SLEEP.API",
            "service-list-json",
            "service-description-json",
            "serviceName=sleep",
            "serviceName=training",
            "servicePath.sleep=/REST/SLEEP.API",
            "event=sleep",
            "endpoint=stop",
            "action.post=/REST/SLEEP.API?cmd=post",
            "detail.sleep=state",
            "trigger.sleep=change",
            "transport-error",
            "responseError",
            "response-error",
            "response-error:103:NO_SUCH_FILE_OR_DIRECTORY",
            "pftp-response-error-name",
            "pftp-response-error-code",
            "NO_SUCH_FILE_OR_DIRECTORY",
            "successEmpty",
            "successMalformedJson",
            "empty-response-parse-failure",
            "malformed-response-parse-failure",
            "malformed-json",
            "json-parse-failure",
            "json-decoder-failure",
            "rest-request-transport-policy.json",
            "service-list-request-path",
            "service-description-action-field-mapping",
            "service-list-request-failure",
            "service-description-request-failure",
            "service-list-response-error-platform-mapping",
            "service-description-response-error-platform-mapping",
            "service-list-empty-success-parse-failure",
            "service-description-empty-success-parse-failure",
            "service-list-malformed-success-parse-failure",
            "service-description-malformed-success-parse-failure",
            "model-json-mapping-vector-reference-gate",
            "empty-response-transport-policy-gate",
            "response-error-transport-policy-gate",
            "facade-error-mapping-gate",
            "compile-verification-gate",
            "PolarRuntimeOrchestration",
            "planRestFacade"
        )
        val FAKE_TRANSPORT_COMMON_REST_SERVICE_MAPPING_TEST_REQUIRED_TERMS = listOf(
            "restServiceListGoldenVectorsDefineExecutableCommonMappingPolicy",
            "restServiceDescriptionGoldenVectorsDefineExecutableCommonMappingPolicy",
            "restServiceListWrongTypeGoldenVectorPinsPlatformSplitBeforeCommonDecoderMigration",
            "restServiceMappingReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/rest-service/rest-service-mapping-readiness.json",
            "rest-service-mapping-readiness",
            "compileVerifiedPreMigrationCharacterization",
            "sdk/rest-service/service-list-basic.json",
            "sdk/rest-service/service-list-empty.json",
            "sdk/rest-service/service-description-training.json",
            "sdk/rest-service/service-description-empty.json",
            "sdk/rest-service/service-list-wrong-type-platform-policy.json",
            "service-list-name-path-mapping",
            "service-list-empty-defaults",
            "service-description-action-event-mapping",
            "service-description-empty-defaults",
            "wrong-type-services-platform-split",
            "unknown-field-ignore-policy",
            "platform-rest-service-vector-references",
            "compile-verification-gate",
            "ignore-unknown-fields",
            "return-empty-collections",
            "choose an explicit shared policy"
        )
        val FAKE_TRANSPORT_COMMON_REST_EVENT_COMPRESSION_TEST_REQUIRED_TERMS = listOf(
            "restEventCompressionGoldenVectorDefinesExecutableCommonCodecOwnershipPolicy",
            "restEventCompressionReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/rest-service/rest-event-compression-platform-policy.json",
            "sdk/rest-service/rest-event-compression-readiness.json",
            "rest-event-compression-readiness",
            "uncompressed-batch",
            "empty-uncompressed-batch",
            "compressed-batch",
            "malformed-compressed-payload",
            "android-gzip-codec-reference-gate",
            "ios-deflate-codec-reference-gate",
            "malformed-compressed-payload-platform-split",
            "normalize-or-preserve-codec-decision-gate",
            "compile-verification-gate",
            "GZIPInputStream",
            "normalize or explicitly preserve this platform split"
        )
        val FAKE_TRANSPORT_COMMON_FILE_RUNTIME_TEST_REQUIRED_TERMS = listOf(
            "fileListingGoldenVectorsDefineExecutableCommonTraversalPolicy",
            "sdk/file-utils/list-files-shallow-all.json",
            "sdk/file-utils/list-files-recursive-filtered.json",
            "entry-name-contains-dot",
            "fileReadWriteDeleteGoldenVectorRunsThroughProductionFileFacadePlanner",
            "sdk/file-utils/file-read-write-delete-operations.json",
            "fileRuntimeErrorPolicyVectorRunsThroughProductionCommonPlanner",
            "fileRuntimeErrorReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/file-utils/runtime-error-policy.json",
            "sdk/file-utils/runtime-error-readiness.json",
            "runtime-error-readiness",
            "PolarRuntimeOrchestration.planFileFacade",
            "directory-list-response-error-103",
            "directory-list-malformed-payload",
            "read-file-transport-error",
            "write-file-stream-error-after-header",
            "delete-file-response-error",
            "directory-missing",
            "directory-parse-failure",
            "write-stream-error",
            "capturedPayloadHex",
            "directory-missing-status-103",
            "directory-malformed-payload-parse-failure",
            "write-file-payload-capture-before-stream-error",
            "facade-error-mapping-deferred",
            "compile-verification-gate",
            "PolarRuntimeOrchestration.planFileRuntimeError"
        )
        val FAKE_TRANSPORT_COMMON_FILE_FACADE_RUNTIME_TEST_REQUIRED_TERMS = listOf(
            "fileFacadeRuntimePolicyVectorDefinesExecutableCommonCommandPlanning",
            "fileFacadeRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/file-utils/file-facade-runtime-policy.json",
            "sdk/file-utils/file-facade-runtime-readiness.json",
            "file-facade-runtime-policy",
            "file-facade-runtime-readiness",
            "read-low-level-file-success",
            "read-low-level-file-empty-success",
            "read-low-level-file-request-failure",
            "read-low-level-file-response-error",
            "write-low-level-file-success",
            "write-low-level-file-progress-success",
            "write-low-level-file-stream-failure",
            "write-low-level-file-response-error",
            "delete-low-level-file-success",
            "delete-low-level-file-request-failure",
            "delete-low-level-file-response-error",
            "/U/0/CUSTOM.BIN",
            "/U/0/EMPTY.BIN",
            "/U/0/PROGRESS.BIN",
            "GET",
            "PUT",
            "REMOVE",
            "010203",
            "0a0b",
            "1011",
            "progress:0",
            "progress:2",
            "0c0d",
            "0e0f",
            "transport-error",
            "response-error:103:missing",
            "pftp-response-error-name",
            "pftp-response-error-object",
            "pftp-response-error-code",
            "device-error-wrapper",
            "write-stream-error-after-payload",
            "file-read-write-delete-operations.json",
            "runtime-error-policy.json",
            "list-files-shallow-all.json",
            "list-files-recursive-filtered.json",
            "low-level-file-path-gate",
            "read-file-empty-success",
            "read-file-request-failure",
            "read-file-response-error",
            "write-file-progress-before-completion",
            "write-file-stream-failure-after-payload",
            "write-file-response-error-after-payload",
            "delete-file-remove-success",
            "delete-file-request-failure",
            "directory-list-recursive-vector-reference-gate",
            "runtime-error-policy-reference-gate",
            "response-error-policy-gate",
            "facade-error-mapping-gate",
            "compile-verification-gate",
            "PolarRuntimeOrchestration",
            "planFileFacade"
        )
        val FAKE_TRANSPORT_COMMON_BACKUP_UTILITY_TEST_REQUIRED_TERMS = listOf(
            "backupExpansionAndRestoreWritesGoldenVectorDefinesExecutableCommonPolicy",
            "restoreFailureGoldenVectorPinsPlatformSplitBeforeCommonWorkflowMigration",
            "backupWorkflowReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/backup-utils/backup-expansion-and-restore-writes.json",
            "sdk/backup-utils/restore-failure-platform-policy.json",
            "sdk/backup-utils/backup-workflow-readiness.json",
            "backup-workflow-readiness",
            "BACKUP.TXT",
            "backup-txt-expansion",
            "default-user-file-inclusion",
            "restore-put-command-planning",
            "restore-failure-platform-split",
            "restore-failure-aggregation-decision-gate",
            "compile-verification-gate",
            "choose whether restore failure aggregation belongs in shared code",
            "PolarWorkflowRuntimePlanning.planBackupRestore"
        )
        val FAKE_TRANSPORT_COMMON_OFFLINE_TRIGGER_RUNTIME_TEST_REQUIRED_TERMS = listOf(
            "offlineTriggerRuntimePolicyVectorRunsThroughProductionCommonPlanner",
            "offlineTriggerRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/offline-recording/trigger-runtime-policy.json",
            "sdk/offline-recording/trigger-runtime-readiness.json",
            "PolarWorkflowRuntimePlanning.planOfflineTriggerRuntime",
            "set-trigger-success-with-secret",
            "set-trigger-mode-error",
            "set-trigger-status-read-error",
            "set-trigger-setting-error",
            "get-trigger-success",
            "get-trigger-transport-error",
            "setMode:TRIGGER_SYSTEM_START",
            "setSetting",
            "control-point-error",
            "transport-error",
            "enabledFeatures",
            "typed-set-mode",
            "settings-write",
            "optional-secret-attachment",
            "facade-error-mapping-deferred",
            "compile-verified",
            "explicit length byte"
        )
        val FAKE_TRANSPORT_COMMON_FIRMWARE_UTILITY_TEST_REQUIRED_TERMS = listOf(
            "firmwareDeviceInfoGoldenVectorsDefineExecutableCommonMappingPolicy",
            "firmwareVersionComparisonGoldenVectorDefinesExecutableCommonDottedIntegerPolicy",
            "firmwareInvalidVersionGoldenVectorPinsTypedParseFailureBeforePublicWorkflowMigration",
            "firmwareFileOrderingGoldenVectorDefinesExecutableCommonSystemUpdateLastPolicy",
            "firmwareUtilityReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/firmware-update/utility-readiness.json",
            "firmware-utility-readiness",
            "compileVerifiedPreMigrationCharacterization",
            "sdk/firmware-update/device-info-basic.json",
            "sdk/firmware-update/device-info-zero-version.json",
            "sdk/firmware-update/version-comparison.json",
            "sdk/firmware-update/version-comparison-invalid.json",
            "sdk/firmware-update/file-ordering.json",
            "device-info-protobuf-mapping",
            "zero-version-preservation",
            "dotted-integer-version-comparison",
            "invalid-version-typed-parse-failure",
            "system-update-file-ordering-last",
            "platform-firmware-utility-vector-references",
            "compile-verification-gate",
            "preserve-empty-device-info-strings",
            "typed parse failure",
            "SYSUPDAT.IMG"
        )
        val FAKE_TRANSPORT_COMMON_FIRMWARE_WORKFLOW_TEST_REQUIRED_TERMS = listOf(
            "firmwareWorkflowRuntimePolicyVectorRunsThroughProductionCommonPlanner",
            "firmwareWorkflowRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/firmware-update/workflow-runtime-policy.json",
            "sdk/firmware-update/workflow-runtime-readiness.json",
            "PolarWorkflowRuntimePlanning.planFirmwareWorkflow",
            "check-update-not-available",
            "check-update-available",
            "download-failure",
            "downloadAttempted",
            "empty-or-invalid-zip",
            "zipExtractionAttempted",
            "retryable-server-failure",
            "write-package-success-with-system-update-last",
            "system-update-reboot-response-is-success",
            "SYSUPDAT.IMG",
            "rebooting",
            "battery-too-low-response-is-terminal-failure",
            "battery-too-low",
            "retryable server failure",
            "fake-network-availability",
            "fake-filesystem-zip-extraction",
            "ble-write-progress",
            "cancellation-gate",
            "cancellation-cleanup-after-package-fetch",
            "retryable-server-failure-gate",
            "facade-error-mapping-gate",
            "compile-verified",
            "cancel-after-package-fetch-cleans-up-before-ble-write",
            "cleanupCallbackCount",
            "fwUpdateCancelled",
            "cancelled"
        )
        val FAKE_TRANSPORT_COMMON_PSFTP_RUNTIME_TEST_REQUIRED_TERMS = listOf(
            "commonFakeResponseRuntimeReassemblesRequestResponses",
            "sdk/psftp-response/request-response-reassembly.json",
            "single-frame",
            "multi-frame",
            "sdk/psftp-response/request-response-error-policy.json",
            "known-error-no-such-file",
            "unknown-error-code",
            "sdk/psftp-notifications/notification-reassembly.json",
            "sdk/psftp-notifications/notification-ordering.json",
            "two-single-frame-notifications",
            "commonFakeNotificationRuntimePreservesInitialSilenceAsNoEmissionWithoutBuiltInTimeout",
            "sdk/psftp-notifications/notification-timeout-policy.json",
            "initial-silence",
            "wait-notification-has-no-built-in-initial-silence-timeout",
            "commonFakeNotificationRuntimeConsumerTimeoutCleansObserverWithVirtualClock",
            "PolarWorkflowRuntimePlanning.planConsumerTimeoutObserverCleanup",
            "consumerTimeout",
            "activeObserverCount",
            "cleanupCallbackCount",
            "commonFakeNotificationRuntimePinsRfc76ErrorAndTransportStatusPlatformSplit",
            "sdk/psftp-notifications/notification-error-policy.json",
            "rfc76-error-first-frame",
            "transport-error-first-packet",
            "characterize-current-platform-notification-error-semantics",
            "Nonzero transport status is a current platform split",
            "sdk/psftp-notifications/notification-continuation-timeout-policy.json",
            "missing-last-frame-after-more",
            "commonFakeWriteRuntimePinsPlatformProgressSplitBeforeSharedPolicyChoice",
            "sdk/psftp-response/write-success-progress.json",
            "android-currently-emits-negative-header-overhead-progress-before-payload-count-while-ios-emits-initial-zero-header-progress-and-final-payload-count",
            "sdk/psftp-response/write-interruption-error-policy.json",
            "sdk/psftp-response/write-transport-failure-policy.json",
            "sdk/psftp-response/write-ack-timeout-policy.json",
            "psFtpRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/psftp-response/psftp-runtime-readiness.json",
            "psftp-runtime-readiness",
            "request-response-reassembly",
            "request-response-error-mapping",
            "notification-reassembly",
            "notification-ordering",
            "consumer-timeout-observer-cleanup",
            "notification-transport-status-platform-split",
            "write-progress-platform-split",
            "write-ack-timeout",
            "platform-client-vector-reference-gate",
            "compile-verification-gate",
            "ContinuationTimeout",
            "WriteAckTimeout",
            "TransportWriteFailure"
        )
        val FAKE_TRANSPORT_COMMON_PSFTP_BYTE_CODEC_TEST_REQUIRED_TERMS = listOf(
            "psFtpRfc76FrameGoldenVectorsDecodeHeaderPayloadAndErrorPolicy",
            "sdk/psftp-rfc76/error-frame-ffff.json",
            "sdk/psftp-rfc76/final-last-frame.json",
            "sdk/psftp-rfc76/first-more-frame.json",
            "sdk/psftp-rfc76/header-only-last-frame.json",
            "sdk/psftp-rfc76/header-only-more-frame.json",
            "sdk/psftp-rfc76/middle-more-frame.json",
            "sdk/psftp-rfc76/single-last-frame.json",
            "android-currently-masks-shifted-high-byte-while-ios-uses-little-endian-uint16",
            "psFtpCompleteMessageStreamGoldenVectorDefinesExecutableRfc60EncodingPolicy",
            "sdk/psftp-message-stream/complete-message-streams.json",
            "request-header-only",
            "android-request-with-file-data",
            "query-with-header",
            "notification-with-header",
            "notification-empty-header",
            "encode-rfc60-complete-message-streams",
            "Android makeCompleteMessageStream appends file data",
            "psFtpRfc76FrameSplittingGoldenVectorDefinesExecutableMtuAndSequencePolicy",
            "sdk/psftp-message-stream/rfc76-frame-splitting.json",
            "empty-payload",
            "exactly-one-frame",
            "two-frames",
            "sequence-wraps-after-fifteen",
            "split-rfc76-message-frames",
            "psFtpByteCodecReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/psftp-message-stream/byte-codec-readiness.json",
            "psftp-byte-codec-readiness",
            "rfc76-header-next-bit",
            "rfc76-status-decoding",
            "rfc76-error-frame-platform-split",
            "rfc60-request-stream-encoding",
            "android-request-file-data-append-policy",
            "rfc76-mtu-frame-splitting",
            "rfc76-sequence-wrap",
            "platform-codec-vector-reference-gate",
            "compile-verification-gate"
        )
        val FAKE_TRANSPORT_COMMON_STREAM_RUNTIME_TEST_REQUIRED_TERMS = listOf(
            "orderedEmissionsPolicyVectorRunsThroughCommonFakeStreamRuntime",
            "sdk/stream-runtime/ordered-emissions-policy.json",
            "terminalErrorPolicyVectorRunsThroughCommonFakeStreamRuntime",
            "sdk/stream-runtime/terminal-error-policy.json",
            "initialDisconnectedPolicyVectorRunsThroughCommonFakeStreamRuntime",
            "sdk/stream-runtime/initial-disconnected-policy.json",
            "uncheckedSubscriptionPolicyVectorRunsThroughCommonFakeStreamRuntime",
            "sdk/stream-runtime/unchecked-subscription-policy.json",
            "consumerCancellationPolicyVectorRunsThroughCommonFakeStreamRuntime",
            "sdk/stream-runtime/consumer-cancellation-policy.json",
            "consumerCancellationLateEventsPolicyVectorRunsThroughCommonFakeStreamRuntime",
            "sdk/stream-runtime/consumer-cancellation-late-events-policy.json",
            "disconnectAfterSubscriptionPolicyVectorRunsThroughCommonFakeStreamRuntime",
            "sdk/stream-runtime/disconnect-after-subscription-policy.json",
            "duplicateCompletionPolicyVectorRunsThroughCommonFakeStreamRuntime",
            "sdk/stream-runtime/duplicate-completion-policy.json",
            "lateEmissionAfterCompletionPolicyVectorRunsThroughCommonFakeStreamRuntime",
            "sdk/stream-runtime/late-emission-after-completion-policy.json",
            "streamRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/stream-runtime/stream-runtime-readiness.json",
            "stream-runtime-readiness",
            "genericStreamEmissionPolicy",
            "genericStreamTerminalErrorPolicy",
            "genericStreamConnectionGuardPolicy",
            "genericStreamCancellationPolicy",
            "genericStreamDisconnectPolicy",
            "genericStreamCompletionPolicy",
            "actions",
            "startConnected",
            "checkConnection",
            "connectionChecked",
            "emissions",
            "completionSignals",
            "postCompletionEmissions",
            "completionEventCount",
            "activeObserverCount",
            "emittedValues",
            "errorEventCount",
            "cleanupCallbackCount",
            "cancelledStreams",
            "upstreamCancelled",
            "upstreamStarted",
            "terminalError",
            "lateEventPolicy",
            "suppress-after-consumer-cancellation",
            "preserve-source-order",
            "propagate-error-and-clear-observers",
            "fail-before-observer-registration",
            "skip-connection-check-and-register-observer",
            "idempotent-consumer-cancellation",
            "ignore-after-first-completion",
            "ignore-after-terminal-completion",
            "ordered-emission-before-completion",
            "terminal-error-propagation",
            "checked-disconnected-fails-before-observer",
            "unchecked-subscription-skips-connection-check",
            "consumer-cancellation-upstream-cancel",
            "post-cancellation-late-event-suppression",
            "disconnect-after-subscription-terminal",
            "disconnect-after-subscription-observer-cleanup",
            "disconnect-after-subscription-upstream-cancel",
            "duplicate-completion-idempotence",
            "post-completion-emission-suppression",
            "platform-stream-vector-reference-gate",
            "compile-verification-gate",
            "Stream values emitted before terminal completion must be delivered in source order.",
            "Terminal stream errors must propagate to consumers and clear observers without reporting normal completion.",
            "A checked stream subscription that starts disconnected must fail before observer registration or upstream work starts.",
            "An unchecked stream subscription must register the observer without querying transport connection state.",
            "Consumer cancellation must remove the observer, cancel upstream work once, and remain idempotent.",
            "After consumer cancellation, late stream values, terminal errors, and completion signals must not surface or mutate terminal counters.",
            "A stream that disconnects after observer registration must terminate consumers, clear observers, and cancel upstream work without leaking an active subscription.",
            "Complete or finish signals after the first terminal completion must be idempotent and must not re-register observers.",
            "Values emitted after terminal completion must not surface to consumers and must not re-register observers."
        )
        val FAKE_TRANSPORT_COMMON_COMMAND_RUNTIME_TEST_REQUIRED_TERMS = listOf(
            "resetSyncH10CommandPolicyVectorDefinesExecutableCommonCommandPlanning",
            "resetSyncH10CommandVectorRunsThroughCommonFakeTransportFacadeShape",
            "resetSyncH10CommandReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/command-runtime/reset-sync-h10-command-policy.json",
            "sdk/command-runtime/reset-sync-h10-command-readiness.json",
            "reset-sync-h10-command-policy",
            "reset-sync-h10-command-readiness",
            "h10-start-recording",
            "h10-start-recording-query-failure",
            "REQUEST_START_RECORDING",
            "sampleDataIdentifier=myExercise",
            "start-recording-query-failed",
            "h10-stop-recording",
            "h10-stop-recording-query-failure",
            "REQUEST_STOP_RECORDING",
            "stop-recording-query-failed",
            "h10-recording-status",
            "h10-recording-status-query-failure",
            "REQUEST_RECORDING_STATUS",
            "recording-status-query-failed",
            "queryFailure",
            "transport-error",
            "factory-reset-notification-failure",
            "factory-reset-preserve-pairing",
            "factory-reset-preserve-pairing-notification-failure",
            "warehouse-sleep",
            "warehouse-sleep-notification-failure",
            "turn-device-off",
            "turn-device-off-notification-failure",
            "restart-notification-failure",
            "sync-start-success",
            "sync-start-query-failure",
            "sync-stop-success",
            "sync-stop-notification-failure",
            "platform-split",
            "ScriptedCommonFakeTransport",
            "CommonFakeTransportCommand",
            "syncStartQueryFailure",
            "syncStopNotificationFailure",
            "h10-recording-start-query",
            "h10-recording-start-query-failure",
            "h10-recording-stop-query",
            "h10-recording-stop-query-failure",
            "h10-recording-status-query",
            "h10-recording-status-query-failure",
            "factory-reset-flags",
            "preserve-pairing-reset-flags",
            "preserve-pairing-reset-notification-failure",
            "sync-start-notification-sequence",
            "restart-reset-notification-failure",
            "warehouse-sleep-reset-notification-failure",
            "turn-device-off-reset-notification-failure",
            "sync-start-query-failure-platform-split",
            "sync-stop-notification-failure-platform-split",
            "facade-error-mapping-gate",
            "compile-verification-gate",
            "PolarRuntimeOrchestration"
        )
        val FAKE_TRANSPORT_COMMON_STORED_DATA_CLEANUP_RUNTIME_TEST_REQUIRED_TERMS = listOf(
            "cleanupWorkflowPolicyVectorDefinesExecutableCommonTraversalAndPlatformSplits",
            "cleanupWorkflowVectorRunsThroughCommonFakeTransportFacadeShape",
            "cleanupWorkflowReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/stored-data-cleanup/cleanup-workflow-policy.json",
            "sdk/stored-data-cleanup/cleanup-workflow-readiness.json",
            "stored-data-cleanup-workflow-policy",
            "telemetry-root-trc-bin-filter",
            "sdlogs-extension-filter",
            "activity-prune-empty-parents",
            "automatic-sample-embedded-day-filter",
            "sdlogs-list-failure-platform-policy",
            "telemetry-list-failure-platform-policy",
            "TRC10.BIN",
            "A.SLG",
            "ACTIVITY.BPB",
            "AUTOS001.BPB",
            "platform-path-split",
            "platform-split",
            "ScriptedCommonFakeTransport",
            "CommonFakeTransportCommand",
            "sdlogsListFailure",
            "activityEmptyParentRemovePath",
            "telemetry-trc-filter",
            "list-failure-platform-split",
            "empty-parent-path-platform-split",
            "facade-error-mapping-gate",
            "compile-verified",
            "PolarWorkflowRuntimePlanning.planStoredDataCleanup"
        )
        val FAKE_TRANSPORT_COMMON_DISK_TIME_RUNTIME_TEST_REQUIRED_TERMS = listOf(
            "diskTimeQueryPolicyVectorDefinesExecutableCommonQueryPlanning",
            "diskTimeQueryReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/disk-time-runtime/disk-time-query-policy.json",
            "sdk/disk-time-runtime/disk-time-query-readiness.json",
            "disk-time-query-policy",
            "get-disk-space",
            "GET_DISK_SPACE",
            "get-local-time",
            "GET_LOCAL_TIME",
            "set-local-time-v2",
            "SET_SYSTEM_TIME",
            "SET_LOCAL_TIME",
            "systemTimeHour=10",
            "localTimeHour=12",
            "systemTimeTrusted=true",
            "set-local-time-h10",
            "set-local-time-failure",
            "get-local-time-failure",
            "get-local-time-with-zone-failure",
            "get-disk-space-failure",
            "transport-error",
            "disk-space-query",
            "local-time-query",
            "local-time-with-zone-query",
            "v2-system-and-local-time-sequence",
            "h10-single-local-time-query",
            "local-time-transport-error",
            "local-time-with-zone-transport-error",
            "filesystem-capability-gate",
            "facade-error-mapping-gate",
            "compile-verified",
            "PolarRuntimeOrchestration"
        )
        val FAKE_TRANSPORT_COMMON_USER_DEVICE_SETTINGS_RUNTIME_TEST_REQUIRED_TERMS = listOf(
            "userDeviceSettingsRuntimePolicyVectorDefinesExecutableCommonReadWritePlanning",
            "userDeviceSettingsRuntimeVectorRunsThroughCommonFakeTransportFacadeShape",
            "userDeviceSettingsRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily",
            "sdk/user-device-settings-runtime/settings-runtime-policy.json",
            "sdk/user-device-settings-runtime/settings-runtime-readiness.json",
            "user-device-settings-runtime-policy",
            "user-device-settings-runtime-readiness",
            "/U/0/S/UDEVSET.BPB",
            "get-user-device-settings",
            "get-user-device-settings-read-failure",
            "set-telemetry-enabled",
            "set-telemetry-read-failure",
            "set-telemetry-write-failure",
            "set-user-device-location",
            "set-user-device-location-write-failure",
            "set-usb-connection-mode",
            "set-usb-connection-mode-write-failure",
            "set-automatic-training-detection",
            "set-automatic-training-detection-write-failure",
            "set-automatic-ohr-measurement",
            "set-automatic-ohr-measurement-write-failure",
            "set-daylight-saving-time",
            "telemetryEnabled=true",
            "deviceLocation=WRIST_RIGHT",
            "usbConnectionMode=ON",
            "automaticTrainingDetectionMode=ON",
            "automaticTrainingDetectionSensitivity=77",
            "minimumTrainingDurationSeconds=300",
            "automaticOhrMeasurement=ALWAYS_ON",
            "daylightSaving.nextDaylightSavingTime=present",
            "transport-error-after-payload",
            "ScriptedCommonFakeTransport",
            "CommonFakeTransportCommand",
            "read-failure no-write behavior",
            "settings-read-failure-no-write",
            "telemetry-write-failure-after-payload",
            "usb-connection-mode-write-failure-after-payload",
            "automatic-training-detection-read-then-write",
            "automatic-ohr-measurement-write-failure-after-payload",
            "daylight-saving-payload-shape",
            "protobuf-field-preservation-gate",
            "facade-error-mapping-gate",
            "compile-verification-gate",
            "PolarRuntimeOrchestration",
            "planUserDeviceSettings"
        )
        val COMMON_TEST_PORTABILITY_FORBIDDEN = Regex("digitToInt|toBooleanStrict|uppercase\\(|lowercase\\(|replaceFirstChar|ifEmpty|UL|UInt|UByte|ULong|java\\.|android\\.|com\\.google")
        val COMMON_MAIN_PLATFORM_FORBIDDEN = Regex("android\\.|java\\.|javax\\.|CoreBluetooth|UIKit|Foundation|CryptoKit|SwiftUI|platform\\.Core|com\\.google|Bluetooth|BluetoothGatt|Context\\b|GlobalScope|Dispatchers\\.Main")
        val COMMON_MAIN_PORTABILITY_PLAN_TERMS = listOf(
            "Common code must not depend on Android Bluetooth APIs, CoreBluetooth, UIKit, Swift-only concurrency types, JVM-only classes, Apple-only cryptography, or global mutable platform state.",
            "Shared code should receive byte arrays, timestamps, settings, and command results; platform code should translate those to Android or iOS BLE calls."
        )
        val COMMON_TEST_PORTABILITY_ALLOWED_LINES = listOf(
            AllowedCommonTestPortabilityLine(
                file = "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DeviceIdCommonPolicyTest.kt",
                text = "protocol/device-id/identifier-bluetooth-address-android.json"
            ),
            AllowedCommonTestPortabilityLine(
                file = "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DiskSpaceCommonPolicyTest.kt",
                text = "Swift UInt32 behavior accidentally"
            ),
            AllowedCommonTestPortabilityLine(
                file = "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PmdControlPointCommonPolicyTest.kt",
                text = "ERROR_DISK_FULL"
            ),
            AllowedCommonTestPortabilityLine(
                file = "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/PsFtpRuntimePolicyCommonTest.kt",
                text = "readUInt16Le"
            ),
            AllowedCommonTestPortabilityLine(
                file = "sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/TypeUtilsCommonPolicyTest.kt",
                text = "UInt64 max decimal preservation"
            )
        )
        val TOP_LEVEL_FIELDS = setOf(
            "id",
            "area",
            "case",
            "source",
            "description",
            "input",
            "expected",
            "consumerTests",
            "platformExpectations",
            "platforms",
            "execution",
            "commonDecision",
            "notes"
        )
        val LOWERCASE_HEX = Regex("([0-9a-f]{2})*")
        val VECTOR_ID = Regex("[a-z0-9][a-z0-9-]*")
        val VECTOR_CASE = Regex("[a-z0-9][a-z0-9_]*")
    }

    private data class AllowedCommonTestPortabilityLine(
        val file: String,
        val text: String
    )
}
