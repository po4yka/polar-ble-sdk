package com.polar.sdk.api.model.utils

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GoldenVectorContractPolicyTest {

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
    fun `golden vector schema matches executable shared ownership policy fields`() {
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
            "Schema required fields must match GoldenVectorPolicyTest.REQUIRED_FIELDS: schema=$schemaRequiredFields policy=$REQUIRED_FIELDS",
            schemaRequiredFields == REQUIRED_FIELDS
        )
        assertTrue(
            "Schema root additionalProperties must remain false",
            schemaAdditionalProperties == false
        )
        assertTrue(
            "Schema top-level fields must match GoldenVectorPolicyTest.TOP_LEVEL_FIELDS: schema=$schemaTopLevelFields policy=$TOP_LEVEL_FIELDS",
            schemaTopLevelFields == TOP_LEVEL_FIELDS
        )
        assertTrue(
            "Schema id/case/hex patterns must match executable policy: id=$schemaIdPattern case=$schemaCasePattern hex=$schemaHexPattern",
            schemaIdPattern == SCHEMA_VECTOR_ID_PATTERN && schemaCasePattern == SCHEMA_VECTOR_CASE_PATTERN && schemaHexPattern == SCHEMA_LOWERCASE_HEX_PATTERN
        )
        assertTrue(
            "Schema platform fields must match GoldenVectorPolicyTest.PLATFORM_FIELDS: schema=$schemaPlatformFields policy=$PLATFORM_FIELDS",
            schemaPlatformFields == PLATFORM_FIELDS.toSet() && schemaRequiredPlatformFields == PLATFORM_FIELDS && !schemaPlatformAdditionalProperties
        )
        assertTrue(
            "Schema consumerTests platforms must match GoldenVectorPolicyTest.CONSUMER_TEST_PLATFORMS: schema=$schemaConsumerTestPlatforms policy=$CONSUMER_TEST_PLATFORMS",
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
    fun `golden vector directories document shared ownership`() {
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
            "Every golden-vector directory with fixtures must include README.md shared ownership notes: $undocumentedDirectories",
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
            "Root golden-vector README must document schema-visible metadata fields and shared ownership gates: $undocumentedTerms",
            undocumentedTerms.isEmpty()
        )
    }

    @Test
    fun `golden vector readmes describe shared ownership context`() {
        val root = findRepositoryRoot()
        val weakReadmes = root.resolve("testdata/golden-vectors")
            .walkTopDown()
            .filter { file -> file.isFile && file.name == "README.md" }
            .filterNot { file -> file.readText().hasSharedOwnershipContext() }
            .map { file -> file.relativeTo(root).path }
            .toList()

        assertTrue(
            "Golden-vector README files must mention shared/common shared ownership context: $weakReadmes",
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
}
