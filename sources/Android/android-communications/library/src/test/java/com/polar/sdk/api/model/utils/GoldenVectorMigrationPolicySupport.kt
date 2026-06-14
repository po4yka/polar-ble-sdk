package com.polar.sdk.api.model.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileReader

internal fun JsonObject.hasMigrationPolicyRationale(): Boolean {
    val expected = getAsJsonObject("expected") ?: return false
    if (expected.has("commonDecision")) return true
    if (expected.has("commonRuntimePrototype")) return true
    if (expected.has("commonWorkflowPrototype")) return true
    if (expected.has("migrationOwnership")) return true
    val platformExpectations = getAsJsonObject("platformExpectations")
    return platformExpectations?.has("commonDecision") == true
}

internal fun JsonObject.requiresSharedCommonVectorConsumer(): Boolean {
    val platforms = getAsJsonObject("platforms") ?: return false
    if (platforms.has("common") && platforms.get("common").asBoolean) return true
    if (has("commonDecision")) return true
    if (getAsJsonObject("platformExpectations")?.has("commonDecision") == true) return true
    val expected = getAsJsonObject("expected") ?: return false
    return expected.has("commonDecision") || expected.has("policy")
}

internal fun JsonObject.hasConsumerTests(): Boolean {
    val consumerTests = getAsJsonObject("consumerTests") ?: return false
    return consumerTests.entrySet().any { entry ->
        val value = entry.value
        value.isJsonArray && value.asJsonArray.any { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank() }
    }
}

internal fun JsonObject.hasNonEmptyConsumerTests(platform: String): Boolean {
    val consumerTests = getAsJsonObject("consumerTests") ?: return false
    val platformTests = consumerTests.get(platform) ?: return false
    return platformTests.isJsonArray && platformTests.asJsonArray.any { it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.isNotBlank() }
}

internal fun JsonObject.consumerTestsFor(platform: String): List<String> {
    val consumerTests = getAsJsonObject("consumerTests") ?: return emptyList()
    val platformTests = consumerTests.get(platform) ?: return emptyList()
    if (!platformTests.isJsonArray) return emptyList()
    return platformTests.asJsonArray
        .filter { element -> element.isJsonPrimitive && element.asJsonPrimitive.isString }
        .map { element -> element.asString }
}

internal fun JsonObject.optionalStringField(field: String): String? {
    val value = get(field) ?: return null
    return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else null
}

internal fun JsonObject.isRuntimePlanningVector(): Boolean {
    return has("execution") || expectedObject().has("commonRuntimePrototype") || expectedObject().has("commonWorkflowPrototype")
}

internal fun JsonObject.hasRuntimePolicyConsumer(): Boolean {
    return consumerTestReferences().any { consumer -> RUNTIME_POLICY_CONSUMER_TEST.matches(consumer.testName) }
}

internal fun JsonObject.consumerTestReferences(): List<ConsumerTestReference> {
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

internal fun JsonObject.consumerTestShapeErrors(): List<String> {
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

internal fun JsonObject.behaviorIds(): List<String> {
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

internal fun JsonObject.objectArrayIds(field: String): List<String> {
    val value = get(field) ?: return emptyList()
    if (!value.isJsonArray) return emptyList()
    return value.asJsonArray
        .filter { element -> element.isJsonObject }
        .mapNotNull { element ->
            val id = element.asJsonObject.get("id")
            if (id != null && id.isJsonPrimitive && id.asJsonPrimitive.isString) id.asString else null
        }
}

internal fun JsonObject.expectedObject(): JsonObject {
    return getAsJsonObject("expected") ?: JsonObject()
}

internal fun JsonObject.stringArrayAt(field: String): List<String> {
    return getAsJsonArray(field).map { it.asString }
}

internal fun File.androidTestFileFor(className: String): File {
    return resolve("sources/Android/android-communications/library/src/test/java/${className.replace('.', '/')}.kt")
}

internal fun File.commonPrototypeTestFileFor(className: String): File {
    val sharedCommonTest = resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/${className.replace('.', '/')}.kt")
    return if (sharedCommonTest.isFile) sharedCommonTest else androidTestFileFor(className)
}

internal fun File.consumerTestReferencesVector(consumer: ConsumerTestReference, vector: VectorFile): Boolean {
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

internal fun File.iosTestExists(testName: String): Boolean {
    return iosTestFiles(testName).isNotEmpty()
}

internal fun File.iosTestFiles(testName: String): List<File> {
    val testsRoot = resolve("sources/iOS/ios-communications/Tests")
    return testsRoot
        .walkTopDown()
        .filter { it.isFile && it.extension == "swift" }
        .filter { file ->
            file.nameWithoutExtension == testName || file.readText().contains(Regex("\\b(class|struct)\\s+$testName\\b"))
        }
        .toList()
}

internal fun String.referencesVector(vectorId: String, vectorFileName: String): Boolean {
    return contains(vectorId) || contains(vectorFileName)
}

internal fun String.referencesVectorOrOwningManifest(root: File, vector: VectorFile, vectorId: String, vectorFileName: String): Boolean {
    val vectorDirectory = requireNotNull(vector.file.parentFile) { "Vector file must have a parent directory: ${vector.file}" }.relativeTo(root).path
    return referencesVector(vectorId, vectorFileName) || contains(vectorDirectory) || root.owningManifestReferencesVector(this, vector)
}

internal fun File.owningManifestReferencesVector(testSource: String, vector: VectorFile): Boolean {
    val vectorRelativePath = vector.file.relativeTo(this).path.removePrefix("testdata/golden-vectors/")
    return resolve("testdata/golden-vectors")
        .walkTopDown()
        .filter { file -> file.isFile && file.extension == "json" && file != vector.file && !file.relativeTo(this).path.contains("/schema/") }
        .map { manifestFile ->
            val manifest = FileReader(manifestFile).use { reader -> JsonParser.parseReader(reader).asJsonObject }
            manifestFile to manifest
        }
        .filter { (_, manifest) -> manifest.containsStringValue(vectorRelativePath) }
        .any { (manifestFile, manifest) ->
            testSource.referencesVector(manifest.get("id").asString, manifestFile.name)
        }
}

internal fun JsonObject.containsStringValue(expectedValue: String): Boolean {
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

internal fun String.hasMigrationContext(): Boolean {
    val lower = lowercase()
    return lower.contains("kmp") && MIGRATION_README_TERMS.any { lower.contains(it) }
}

internal fun String.hasMigrationGateLanguage(): Boolean {
    val lower = lowercase()
    return PARTIAL_ROW_GATE_TERMS.any { lower.contains(it) }
}

internal fun String.looksLikeArtifactReference(): Boolean {
    return contains("/") || endsWith(".md") || endsWith(".json") || endsWith(".kt") || endsWith(".swift")
}

internal fun File.resolveEvidenceReference(reference: String): File {
    val direct = resolve(reference)
    return if (direct.exists()) direct else resolve("documentation/$reference")
}

internal fun String.validationArtifactReferences(): List<String> {
    return VALIDATION_ARTIFACT_REFERENCE.findAll(this)
        .map { match -> match.value.trimEnd('.', ',', ')') }
        .filterNot { reference -> reference.startsWith("Pods/") }
        .distinct()
        .toList()
}

internal fun deviceIdSliceMigrated(root: File): Boolean {
    val commonMain = root.resolve("sources/Android/android-communications/shared/src/commonMain/kotlin/com/polar/shared/device/PolarDeviceId.kt")
    val commonTest = root.resolve("sources/Android/android-communications/shared/src/commonTest/kotlin/com/polar/sharedtest/DeviceIdCommonPolicyTest.kt")
    val androidDeviceIdUtility = root.resolve("sources/Android/android-communications/library/src/main/java/com/polar/androidcommunications/api/ble/model/polar/BlePolarDeviceIdUtility.kt")
    val androidDeviceUuid = root.resolve("sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/model/PolarDeviceUuid.kt")
    val androidSdkModelAdapter = root.resolve("sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/api/model/PolarSdkModelAdapter.kt")
    val androidDeviceUuidSharedRoute = androidDeviceUuid.isFile &&
        (
            androidDeviceUuid.readText().contains("PolarDeviceId.uuidFromDeviceId") ||
                (
                    androidDeviceUuid.readText().contains("PolarSdkModelAdapter.uuidFromDeviceId") &&
                        androidSdkModelAdapter.isFile &&
                        androidSdkModelAdapter.readText().contains("PolarDeviceId.uuidFromDeviceId")
                )
        )
    return commonMain.isFile &&
        commonMain.readText().contains("object PolarDeviceId") &&
        commonTest.isFile &&
        commonTest.readText().contains("PolarDeviceId.uuidFromDeviceId") &&
        androidDeviceIdUtility.isFile &&
        (
            androidDeviceIdUtility.readText().contains("PolarDeviceId.assembleFull") ||
                (
                    androidDeviceIdUtility.readText().contains("PolarSdkModelAdapter.assembleFullDeviceId") &&
                        androidSdkModelAdapter.isFile &&
                        androidSdkModelAdapter.readText().contains("PolarDeviceId.assembleFull")
                )
        ) &&
        androidDeviceUuidSharedRoute
}

internal fun iosSharedConsumptionMigrated(root: File): Boolean {
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

internal fun File.gitStatusShort(vararg paths: String): List<String> {
    val process = ProcessBuilder(listOf("git", "status", "--short", "--") + paths)
        .directory(this)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readLines()
    val exitCode = process.waitFor()
    return if (exitCode == 0) output.filter { it.isNotBlank() } else listOf("git status failed with exit $exitCode: ${output.joinToString(" ")}")
}

internal fun String.sourcesBuildPhaseSection(): String {
    return SOURCES_BUILD_PHASE_SECTION.find(this)?.value ?: ""
}

internal fun String.tableRows(): List<List<String>> {
    return lineSequence()
        .filter { line -> line.startsWith("|") && !line.startsWith("|---") && !line.contains("Behavior family") }
        .map { line -> line.trim('|').split('|').map { column -> column.trim() } }
        .toList()
}

internal fun String.sectionBetween(startHeading: String, endHeading: String): String {
    val start = indexOf(startHeading)
    if (start == -1) return ""
    val end = indexOf(endHeading, start + startHeading.length)
    return if (end == -1) substring(start) else substring(start, end)
}

internal fun VectorFile.invalidHexFields(): List<String> {
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

internal fun loadAllGoldenVectors(): List<VectorFile> {
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
                    JsonParser.parseReader(reader).asJsonObject
                }
            )
        }
        .toList()
}

internal fun loadSdkGoldenVectors(): List<VectorFile> {
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
                    JsonParser.parseReader(reader).asJsonObject
                }
            )
        }
        .toList()
}

internal fun loadGoldenVectorSchema(): JsonObject {
    return FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/schema/golden-vector.schema.json")).use { reader ->
        JsonParser.parseReader(reader).asJsonObject
    }
}

internal fun findRepositoryRoot(): File {
    val userDirectory = System.getProperty("user.dir") ?: error("user.dir is not set")
    var directory = File(userDirectory).absoluteFile
    while (true) {
        if (directory.resolve("testdata/golden-vectors").isDirectory) {
            return directory
        }
        directory = directory.parentFile ?: error("Could not find repository root from $userDirectory")
    }
}

internal data class VectorFile(
    val file: File,
    val json: JsonObject
)

internal data class ConsumerTestReference(
    val platform: String,
    val testName: String
)


internal data class AllowedCommonTestPortabilityLine(
    val file: String,
    val text: String
)
