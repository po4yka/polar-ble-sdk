package com.polar.sharedtest

import com.polar.shared.sdk.PolarTrainingSessionFileEntry
import com.polar.shared.sdk.PolarTrainingExerciseReference
import com.polar.shared.sdk.PolarTrainingIntervalledSampleList
import com.polar.shared.sdk.PolarTrainingPayloadFields
import com.polar.shared.sdk.PolarTrainingPayloadParserCase
import com.polar.shared.sdk.PolarTrainingPayloadResponse
import com.polar.shared.sdk.PolarTrainingSessionReference
import com.polar.shared.sdk.PolarTrainingSessionModels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingSessionCommonPolicyTest {
    @Test
    fun trainingSessionReferenceDiscoveryGoldenVectorDefinesExecutableCommonTraversalAndClassificationPolicy() {
        val vector = loadGoldenVectorText("sdk/training-session/reference-discovery-two-sessions.json")
        val directories = vector.objectValue("input").objectValue("directories")
        val discovered = PolarTrainingSessionModels.buildReferences(flattenDirectoryEntries(directories))
        val expected = vector.objectValue("expected").objectArray("references")

        assertEquals(expected.size, discovered.size, vector.stringValue("id"))
        expected.forEachIndexed { index, expectedReference ->
            val actual = discovered[index]
            assertEquals(expectedReference.stringValue("dateTime"), actual.dateTime, "reference $index dateTime")
            assertEquals(expectedReference.stringValue("date"), actual.date, "reference $index date")
            assertEquals(expectedReference.stringValue("path"), actual.path, "reference $index path")
            assertEquals(expectedReference.stringArrayValue("trainingDataTypes"), actual.trainingDataTypes, "reference $index training data types")
            assertEquals(expectedReference.intValue("fileSize").toLong(), actual.fileSize, "reference $index aggregate file size")
            val expectedExercises = expectedReference.objectArray("exercises")
            assertEquals(expectedExercises.size, actual.exercises.size, "reference $index exercise count")
            expectedExercises.forEachIndexed { exerciseIndex, expectedExercise ->
                val exercise = actual.exercises[exerciseIndex]
                assertEquals(expectedExercise.intValue("index"), exercise.index, "reference $index exercise $exerciseIndex index")
                assertEquals(expectedExercise.stringValue("androidPath"), exercise.androidPath, "reference $index exercise $exerciseIndex Android path")
                assertEquals(expectedExercise.stringValue("iosPath"), exercise.iosPath, "reference $index exercise $exerciseIndex iOS path")
                assertEquals(expectedExercise.stringArrayValue("exerciseDataTypes"), exercise.exerciseDataTypes, "reference $index exercise $exerciseIndex data types")
            }
        }

        val commonDecision = vector.objectValue("platformExpectations").objectValue("commonDecision")
        assertEquals("ignore-files-that-do-not-map-to-public-training-or-exercise-data-types", commonDecision.stringValue("unknownFilePolicy"), vector.stringValue("id"))
        assertEquals("android-currently-stores-first-file-path-while-ios-stores-exercise-directory-path", commonDecision.stringValue("exercisePathPolicy"), vector.stringValue("id"))
    }

    @Test
    fun trainingSessionMissingExerciseFileGoldenVectorDefinesExecutableCommonPlatformPolicy() {
        val vector = loadGoldenVectorText("sdk/training-session/missing-exercise-file-platform-policy.json")
        val input = vector.objectValue("input")
        val reference = input.objectValue("reference")
        val missingPaths = input.stringArrayValue("missingPaths")
        val expected = vector.objectValue("expected")

        assertEquals("/U/0/20250101/E/101200/00/ROUTE.BPB", missingPaths.single(), vector.stringValue("id"))
        assertEquals(reference.objectArray("exercises").single().stringArrayValue("exerciseDataTypes"), listOf("EXERCISE_SUMMARY", "ROUTE"), vector.stringValue("id"))
        assertEquals(false, expected.objectValue("android").booleanValue("throws"), vector.stringValue("id"))
        assertEquals(true, expected.objectValue("android").booleanValue("sessionSummaryPresent"), vector.stringValue("id"))
        assertEquals(1, expected.objectValue("android").intValue("exerciseCount"), vector.stringValue("id"))
        assertEquals(true, expected.objectValue("android").booleanValue("exerciseSummaryPresent"), vector.stringValue("id"))
        assertEquals(false, expected.objectValue("android").booleanValue("routePresent"), vector.stringValue("id"))
        assertEquals(true, expected.objectValue("ios").booleanValue("throws"), vector.stringValue("id"))
        assertEquals(TRAINING_SESSION_MISSING_EXERCISE_FILE_COMMON_DECISION, vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("missingExerciseFilePolicy"), vector.stringValue("id"))
    }

    @Test
    fun trainingSessionPayloadReadGoldenVectorDefinesExecutableCommonProgressMalformedAndUnknownSamplePolicy() {
        val vector = loadGoldenVectorText("sdk/training-session/payload-read-policy.json")
        val input = vector.objectValue("input")
        val reference = input.objectValue("reference")
        val responses = input.objectValue("responses")
        val expected = vector.objectValue("expected")
        val sharedReference = payloadReference(reference)
        val sharedResponses = payloadResponses(responses)
        val fetchOrder = PolarTrainingSessionModels.payloadFetchOrder(sharedReference)
        val result = PolarTrainingSessionModels.assemblePayloadReadResult(sharedReference, sharedResponses, fetchOrder)

        assertEquals(expected.stringArrayValue("fetchOrder"), fetchOrder, vector.stringValue("id"))
        assertEquals("/U/0/20250101/E/", PolarTrainingSessionModels.deleteParentPath(sharedReference.path), vector.stringValue("id"))
        assertEquals("/U/0/20250101/E/", PolarTrainingSessionModels.deleteRemovePath(sharedReference.path, parentEntryCount = 1), vector.stringValue("id"))
        assertEquals("/U/0/20250101/E/101200/", PolarTrainingSessionModels.deleteRemovePath(sharedReference.path, parentEntryCount = 2), vector.stringValue("id"))
        val expectedProgress = expected.objectValue("progress")
        assertEquals(expectedProgress.intValue("totalBytes"), result.totalBytes, vector.stringValue("id"))
        assertEquals(expectedProgress.intValue("completedBytes"), result.completedBytes, vector.stringValue("id"))
        assertEquals(expectedProgress.intValue("progressPercent"), result.progressPercent, vector.stringValue("id"))
        assertEquals(expectedProgress.stringValue("currentFileName"), result.currentFileName, vector.stringValue("id"))
        assertEquals("SAMPLES2.GZB", PolarTrainingSessionModels.exerciseDataTypeFileName("SAMPLES_ADVANCED_FORMAT_GZIP"), vector.stringValue("id"))
        val expectedSession = expected.objectValue("sessionSummary")
        assertEquals(expectedSession.stringValue("modelName"), result.modelName, vector.stringValue("id"))
        assertEquals(expectedSession.intValue("durationSeconds"), result.durationSeconds, vector.stringValue("id"))
        assertEquals(expectedSession.intValue("distanceMeters"), result.distanceMeters, vector.stringValue("id"))
        assertEquals(expectedSession.intValue("calories"), result.calories, vector.stringValue("id"))
        val expectedExercise = expected.objectArray("exercises").single()
        val actualExercise = result.exercises.single()
        assertEquals(expectedExercise.intValue("index"), actualExercise.index, vector.stringValue("id"))
        assertEquals(expectedExercise.booleanValue("exerciseSummaryPresent"), actualExercise.exerciseSummaryPresent, vector.stringValue("id"))
        assertEquals(expectedExercise.booleanValue("routePresent"), actualExercise.routePresent, vector.stringValue("id"))
        assertEquals(expectedExercise.booleanValue("routeAdvancedPresent"), actualExercise.routeAdvancedPresent, vector.stringValue("id"))
        assertEquals(expectedExercise.intArrayValue("samplesHeartRate"), actualExercise.samplesHeartRate, vector.stringValue("id"))
        assertEquals(expectedExercise.intArrayValue("samplesAdvancedHeartRate"), actualExercise.samplesAdvancedHeartRate, vector.stringValue("id"))
        assertEquals(expectedExercise.intValue("unknownAdvancedSampleListsIgnored"), actualExercise.unknownAdvancedSampleListsIgnored, vector.stringValue("id"))
        assertEquals(expectedExercise.stringArrayValue("malformedFilesIgnored"), actualExercise.malformedFilesIgnored, vector.stringValue("id"))
        val commonDecision = vector.objectValue("platformExpectations").objectValue("commonDecision")
        assertEquals("compute-progress-from-reference-file-sizes-and-last-completed-file", commonDecision.stringValue("progressPolicy"), vector.stringValue("id"))
        assertEquals("omit-only-the-malformed-component-and-continue-reading-remaining-files", commonDecision.stringValue("malformedPayloadPolicy"), vector.stringValue("id"))
        assertEquals("ignore-unknown-advanced-sample-lists-and-preserve-known-samples", commonDecision.stringValue("unknownSampleListPolicy"), vector.stringValue("id"))
    }

    @Test
    fun trainingSessionByteLevelPayloadParserMigrationRequiresExplicitCommonProtoAndGzipDependencies() {
        val vector = loadGoldenVectorText("sdk/training-session/payload-read-policy.json")
        val commonDecision = vector.objectValue("platformExpectations").objectValue("commonDecision")

        assertEquals("add-common-protobuf-and-gzip-parser-dependencies-before-byte-level-payload-migration", commonDecision.stringValue("byteLevelParserGate"), vector.stringValue("id"))
        assertEquals("deferred-until-common-protobuf-and-gzip-parser-exist", commonDecision.stringValue("byteLevelPayloadStatus"), vector.stringValue("id"))
        assertEquals("This neutral vector does not embed protobuf bytes; it turns the existing Android and iOS payload-read tests into a shared migration contract for request ordering, progress, parsed component presence, malformed component isolation, and unknown advanced sample-list handling. payload-parser-policy.json now pins the parser-family ownership cases; real byte-level protobuf/gzip decoding remains an explicit production gate because the current shared module has no common protobuf or gzip parser dependency.", vector.stringValue("notes"), vector.stringValue("id"))
    }

    @Test
    fun trainingSessionPayloadParserGoldenVectorDefinesExecutableCommonParserOwnershipPolicy() {
        val vector = loadGoldenVectorText("sdk/training-session/payload-parser-policy.json")
        assertEquals("payload-parser-policy", vector.stringValue("id"))
        assertEquals("sdk.training-session", vector.stringValue("area"))
        assertEquals("payload_parser_policy", vector.stringValue("case"))
        assertEquals("payloadParserPolicy", vector.objectValue("input").stringValue("kind"))
        val parserCases = vector.objectValue("input").objectArray("cases").map { testCase ->
            ParserCase(
                id = testCase.stringValue("id"),
                fileName = testCase.stringValue("fileName"),
                parser = testCase.stringValue("parser"),
                encoding = testCase.stringValue("encoding"),
                fields = testCase.stringArrayValue("expectedFields")
            )
        }
        val expected = vector.objectValue("expected")
        val commonParserPrototype = expected.objectValue("commonParserPrototype")
        val expectedCaseList = commonParserPrototype.objectArray("cases")
        val expectedCases = expectedCaseList.associateBy { it.stringValue("id") }

        assertEquals(requiredPayloadParserCaseIds, parserCases.map { parserCase -> parserCase.id }, vector.stringValue("id"))
        assertEquals(requiredPayloadParserCaseIds, expectedCaseList.map { testCase -> testCase.stringValue("id") }, vector.stringValue("id"))
        assertEquals("executable shared parser-policy coverage; byte decoding remains gated on common protobuf and gzip dependencies", commonParserPrototype.stringValue("status"), vector.stringValue("id"))
        assertEquals("Before moving byte-level training payload parsing to common code, add production common protobuf and gzip dependencies that can execute these parser cases against real bytes; until then this vector is the shared parser ownership contract consumed by commonTest and pinned by Android/iOS byte-level characterization tests.", expected.stringValue("commonDecision"), vector.stringValue("id"))
        assertEquals("This vector converts the existing platform byte-level protobuf/gzip coverage into an executable shared parser-policy gate, without claiming common byte decoding is implemented.", vector.stringValue("commonDecision"), vector.stringValue("id"))
        assertEquals("The Android and iOS tests currently construct real protobuf and gzip payloads for these parser families. Shared KMP production parser migration still requires real common protobuf/gzip decoding and compile verification.", vector.stringValue("notes"), vector.stringValue("id"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarTrainingSessionUtilsTest"), vector.objectValue("consumerTests").stringArrayValue("android"), vector.stringValue("id"))
        assertEquals(listOf("PolarTrainingSessionUtilsTest"), vector.objectValue("consumerTests").stringArrayValue("ios"), vector.stringValue("id"))
        assertEquals(listOf("com.polar.sharedtest.TrainingSessionCommonPolicyTest"), vector.objectValue("consumerTests").stringArrayValue("commonPrototype"), vector.stringValue("id"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("android"), vector.stringValue("id"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("ios"), vector.stringValue("id"))
        assertEquals(true, vector.objectValue("platforms").booleanValue("common"), vector.stringValue("id"))

        parserCases.forEach { parserCase ->
            val expected = expectedCases.getValue(parserCase.id)
            val planned: PolarTrainingPayloadParserCase = requireNotNull(PolarTrainingSessionModels.payloadParserCase(parserCase.fileName)) { parserCase.id }

            assertEquals(expected.stringValue("parser"), parserCase.parser, parserCase.id)
            assertEquals(expected.stringValue("encoding"), parserCase.encoding, parserCase.id)
            assertEquals(expected.stringArrayValue("fields"), parserCase.fields, parserCase.id)
            assertEquals(parserCase.parser, planned.parser, parserCase.id)
            assertEquals(parserCase.encoding, planned.encoding, parserCase.id)
        }

        val gzipCases = parserCases.filter { parserCase -> parserCase.encoding == "gzip-protobuf" }
        assertEquals(4, gzipCases.size, vector.stringValue("id"))
        assertEquals(listOf("PbTrainingSession", "PbExerciseBase", "PbExerciseRouteSamples", "PbExerciseRouteSamples", "PbExerciseRouteSamples2", "PbExerciseRouteSamples2", "PbExerciseSamples", "PbExerciseSamples", "PbExerciseSamples2"), parserCases.map { parserCase -> parserCase.parser }, vector.stringValue("id"))
        assertEquals(listOf("protobuf", "protobuf", "protobuf", "gzip-protobuf", "protobuf", "gzip-protobuf", "protobuf", "gzip-protobuf", "gzip-protobuf"), parserCases.map { parserCase -> parserCase.encoding }, vector.stringValue("id"))
        assertEquals(listOf("sampleType=HEART_RATE", "recordingIntervalMs=1000", "heartRateSamples=131,132,133"), expectedCases.getValue("samples-advanced-gzip-protobuf").stringArrayValue("fields"), vector.stringValue("id"))
    }

    @Test
    fun trainingSessionReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val vector = loadGoldenVectorText("sdk/training-session/training-session-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumers = vector.objectValue("consumerTests")
        val platforms = vector.objectValue("platforms")
        assertEquals("training-session-readiness", vector.stringValue("id"))
        assertEquals("sdk.training-session", vector.stringValue("area"))
        assertEquals("training_session_readiness", vector.stringValue("case"))
        assertEquals("trainingSessionReadiness", input.stringValue("kind"))
        assertEquals(trainingSessionVectors, input.stringArrayValue("policyVectorPaths"))
        assertEquals(requiredTrainingSessionFamilies, input.stringArrayValue("requiredBehaviorFamilies"))
        assertEquals(requiredTrainingSessionFamilies, expected.stringArrayValue("coveredBehaviorFamilies"))
        assertEquals(TRAINING_SESSION_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarTrainingSessionUtilsTest"), consumers.stringArrayValue("android"))
        assertEquals(listOf("PolarTrainingSessionUtilsTest"), consumers.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.TrainingSessionCommonPolicyTest"), consumers.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private fun exerciseDataTypeOrNull(name: String): String? {
        return PolarTrainingSessionModels.exerciseDataTypeOrNull(name)
    }

    private fun payloadReference(reference: String): PolarTrainingSessionReference {
        return PolarTrainingSessionReference(
            dateTime = "${reference.stringValue("date")}T00:00:00",
            date = reference.stringValue("date"),
            path = reference.stringValue("path"),
            trainingDataTypes = reference.stringArrayValue("trainingDataTypes"),
            exercises = reference.objectArray("exercises").map { exercise ->
                PolarTrainingExerciseReference(
                    index = exercise.intValue("index"),
                    androidPath = exercise.stringValue("androidPath"),
                    iosPath = exercise.stringValue("iosPath"),
                    exerciseDataTypes = exercise.stringArrayValue("exerciseDataTypes"),
                    fileSizes = exercise.objectValue("fileSizes").intObject().mapValues { entry -> entry.value.toLong() }
                )
            },
            fileSize = reference.intValue("fileSize").toLong()
        )
    }

    private fun payloadResponses(responses: String): Map<String, PolarTrainingPayloadResponse> {
        return responses.objectMap().mapValues { entry ->
            val response = entry.value
            val payload = response.optionalObjectValue("payload")
            PolarTrainingPayloadResponse(
                kind = response.stringValue("kind"),
                fileName = response.stringValue("fileName"),
                byteSize = response.intValue("byteSize"),
                malformed = response.optionalBooleanValue("malformed") == true,
                payload = PolarTrainingPayloadFields(
                    modelName = payload?.optionalStringValue("modelName"),
                    durationSeconds = payload?.optionalIntValue("durationSeconds"),
                    distanceMeters = payload?.optionalIntValue("distanceMeters"),
                    calories = payload?.optionalIntValue("calories"),
                    heartRateSamples = payload?.optionalSignedIntArrayValue("heartRateSamples").orEmpty(),
                    intervalledSampleLists = payload?.optionalObjectArrayValue("intervalledSampleLists").orEmpty().map { sampleList ->
                        PolarTrainingIntervalledSampleList(
                            sampleType = sampleList.stringValue("sampleType"),
                            heartRateSamples = sampleList.optionalSignedIntArrayValue("heartRateSamples").orEmpty()
                        )
                    }
                )
            )
        }
    }

    private fun directoryEntries(directories: String, path: String): List<DirectoryEntry> {
        return directories.objectArray(path).map { entry ->
            DirectoryEntry(
                name = entry.stringValue("name"),
                size = entry.intValue("size")
            )
        }
    }

    private fun flattenDirectoryEntries(directories: String): List<PolarTrainingSessionFileEntry> {
        val root = directoryEntries(directories, ROOT_PATH)
        return root
            .map { it.name }
            .filter { name -> DATE_DIR.matches(name) }
            .flatMap { dateDir ->
                directoryEntries(directories, "$ROOT_PATH$dateDir")
                    .filter { entry -> entry.name == "E/" }
                    .flatMap {
                        directoryEntries(directories, "$ROOT_PATH$dateDir${it.name}")
                            .map { timeEntry -> timeEntry.name }
                            .filter { timeDir -> TIME_DIR.matches(timeDir) }
                            .flatMap { timeDir ->
                                val sessionDirectory = "$ROOT_PATH$dateDir${it.name}$timeDir"
                                directoryEntries(directories, sessionDirectory)
                                    .flatMap { entry ->
                                        if (EXERCISE_DIR.matches(entry.name)) {
                                            val exerciseDirectory = "$sessionDirectory${entry.name}"
                                            directoryEntries(directories, exerciseDirectory).map { file ->
                                                PolarTrainingSessionFileEntry(path = "$exerciseDirectory${file.name}", size = file.size.toLong())
                                            }
                                        } else {
                                            listOf(PolarTrainingSessionFileEntry(path = "$sessionDirectory${entry.name}", size = entry.size.toLong()))
                                        }
                                    }
                            }
                    }
            }
    }

    private fun String.booleanValue(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" } ?: error("Missing boolean field $field in $this")
    }

    private fun String.optionalBooleanValue(field: String): Boolean? {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" }
    }

    private fun String.intArrayValue(field: String): List<Int> {
        val match = Regex("\"$field\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(this) ?: error("Missing int array field $field")
        val content = match.groupValues[1].trim()
        return if (content.isEmpty()) emptyList() else content.split(',').map { item -> item.trim().toInt() }
    }

    private fun String.optionalIntValue(field: String): Int? {
        return Regex("\"$field\"\\s*:\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toInt()
    }

    private fun String.intObject(): Map<String, Int> {
        val content = trim().removeSurrounding("{", "}")
        if (content.trim().isEmpty()) return emptyMap()
        return Regex("\"([^\"]+)\"\\s*:\\s*(-?\\d+)").findAll(content).associate { match ->
            match.groupValues[1] to match.groupValues[2].toInt()
        }
    }

    private fun String.objectMap(): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        var index = 0
        while (index < length) {
            val keyStart = indexOf('"', index)
            if (keyStart < 0) return entries
            val keyToken = readJsonStringToken(keyStart)
            var colonIndex = keyToken.nextIndex
            while (colonIndex < length && this[colonIndex].isWhitespace()) colonIndex += 1
            if (colonIndex >= length || this[colonIndex] != ':') {
                index = keyToken.nextIndex
                continue
            }
            var valueStart = colonIndex + 1
            while (valueStart < length && this[valueStart].isWhitespace()) valueStart += 1
            if (valueStart < length && this[valueStart] == '{') {
                val valueEnd = balancedEnd(valueStart, '{', '}')
                entries[keyToken.value] = substring(valueStart, valueEnd + 1)
                index = valueEnd + 1
            } else {
                index = valueStart + 1
            }
        }
        return entries
    }

    private fun String.optionalObjectArrayValue(field: String): List<String>? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val colonIndex = indexOf(':', fieldIndex)
        if (colonIndex < 0) return null
        var arrayStart = colonIndex + 1
        while (arrayStart < length && this[arrayStart].isWhitespace()) arrayStart += 1
        if (arrayStart >= length || this[arrayStart] != '[') return null
        val arrayEnd = balancedEnd(arrayStart, '[', ']')
        val content = substring(arrayStart + 1, arrayEnd)
        val objects = mutableListOf<String>()
        var index = 0
        while (index < content.length) {
            val objectStart = content.indexOf('{', index)
            if (objectStart < 0) return objects
            val objectEnd = content.balancedEnd(objectStart, '{', '}')
            objects += content.substring(objectStart, objectEnd + 1)
            index = objectEnd + 1
        }
        return objects
    }

    private fun String.readJsonStringToken(start: Int): JsonStringToken {
        require(this[start] == '"') { "Expected JSON string at $start" }
        val value = StringBuilder()
        var index = start + 1
        var escaped = false
        while (index < length) {
            val char = this[index]
            if (escaped) {
                value.append(char)
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else if (char == '"') {
                return JsonStringToken(value.toString(), index + 1)
            } else {
                value.append(char)
            }
            index += 1
        }
        error("Unterminated JSON string at $start")
    }

    private data class DirectoryEntry(
        val name: String,
        val size: Int
    )

    private data class JsonStringToken(
        val value: String,
        val nextIndex: Int
    )

    private data class ParserCase(
        val id: String,
        val fileName: String,
        val parser: String,
        val encoding: String,
        val fields: List<String>
    )

    private companion object {
        const val ROOT_PATH = "/U/0/"
        val DATE_DIR = Regex("\\d{8}/")
        val TIME_DIR = Regex("\\d{6}/")
        val EXERCISE_DIR = Regex("\\d{2}/")
        val requiredPayloadParserCaseIds = listOf(
            "training-session-summary-protobuf",
            "exercise-summary-protobuf",
            "route-protobuf",
            "route-gzip-protobuf",
            "route-advanced-protobuf",
            "route-advanced-gzip-protobuf",
            "samples-protobuf",
            "samples-gzip-protobuf",
            "samples-advanced-gzip-protobuf"
        )
        val trainingSessionVectors = listOf(
            "sdk/training-session/reference-discovery-two-sessions.json",
            "sdk/training-session/missing-exercise-file-platform-policy.json",
            "sdk/training-session/payload-read-policy.json",
            "sdk/training-session/payload-parser-policy.json"
        )
        val requiredTrainingSessionFamilies = listOf(
            "reference-directory-traversal",
            "training-summary-discovery",
            "exercise-file-classification",
            "unknown-file-ignoring",
            "aggregate-file-size-policy",
            "exercise-path-shape-policy",
            "missing-exercise-file-platform-policy",
            "payload-fetch-order",
            "payload-progress-calculation",
            "malformed-component-isolation",
            "unknown-advanced-sample-list-ignoring",
            "known-sample-preservation",
            "payload-parser-family-ownership",
            "byte-level-parser-dependency-gate",
            "platform-training-session-vector-reference-gate",
            "compile-verification-gate"
        )
        const val TRAINING_SESSION_MISSING_EXERCISE_FILE_COMMON_DECISION = "Android currently returns a partial exercise when an exercise data file request fails; iOS currently propagates the request failure. Choose an explicit shared policy before moving training-session read orchestration to KMP."
        const val TRAINING_SESSION_READINESS_COMMON_DECISION = "Training-session migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS training-session tests continue to reference the same vectors, directory traversal, summary discovery, exercise classification, unknown-file ignoring, aggregate size, exercise path policy, missing exercise-file policy, payload fetch order, progress, malformed component isolation, unknown advanced sample-list handling, known sample preservation, parser-family ownership, byte-level parser dependency gates, and compile verification remain explicit before production discovery/read orchestration moves."
    }
}
