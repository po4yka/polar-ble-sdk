package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingSessionCommonPolicyTest {
    @Test
    fun trainingSessionReferenceDiscoveryGoldenVectorDefinesExecutableCommonTraversalAndClassificationPolicy() {
        val vector = loadGoldenVectorText("sdk/training-session/reference-discovery-two-sessions.json")
        val directories = vector.objectValue("input").objectValue("directories")
        val discovered = discoverReferences(directories)
        val expected = vector.objectValue("expected").objectArray("references")

        assertEquals(expected.size, discovered.size, vector.stringValue("id"))
        expected.forEachIndexed { index, expectedReference ->
            val actual = discovered[index]
            assertEquals(expectedReference.stringValue("dateTime"), actual.dateTime, "reference $index dateTime")
            assertEquals(expectedReference.stringValue("date"), actual.date, "reference $index date")
            assertEquals(expectedReference.stringValue("path"), actual.path, "reference $index path")
            assertEquals(expectedReference.stringArrayValue("trainingDataTypes"), actual.trainingDataTypes, "reference $index training data types")
            assertEquals(expectedReference.intValue("fileSize"), actual.fileSize, "reference $index aggregate file size")
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
        val fetchOrder = payloadFetchOrder(reference)
        val result = assemblePayloadReadResult(reference, responses, fetchOrder)

        assertEquals(expected.stringArrayValue("fetchOrder"), fetchOrder, vector.stringValue("id"))
        val expectedProgress = expected.objectValue("progress")
        assertEquals(expectedProgress.intValue("totalBytes"), result.totalBytes, vector.stringValue("id"))
        assertEquals(expectedProgress.intValue("completedBytes"), result.completedBytes, vector.stringValue("id"))
        assertEquals(expectedProgress.intValue("progressPercent"), result.progressPercent, vector.stringValue("id"))
        assertEquals(expectedProgress.stringValue("currentFileName"), result.currentFileName, vector.stringValue("id"))
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

            assertEquals(expected.stringValue("parser"), parserCase.parser, parserCase.id)
            assertEquals(expected.stringValue("encoding"), parserCase.encoding, parserCase.id)
            assertEquals(expected.stringArrayValue("fields"), parserCase.fields, parserCase.id)
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

    private fun discoverReferences(directories: String): List<TrainingReference> {
        return directoryEntries(directories, ROOT_PATH)
            .map { it.name }
            .filter { name -> DATE_DIR.matches(name) }
            .flatMap { dateDir ->
                val date = dateDir.removeSuffix("/")
                directoryEntries(directories, "$ROOT_PATH$dateDir")
                    .filter { entry -> entry.name == "E/" }
                    .flatMap {
                        directoryEntries(directories, "$ROOT_PATH$dateDir${it.name}")
                            .map { timeEntry -> timeEntry.name }
                            .filter { timeDir -> TIME_DIR.matches(timeDir) }
                            .mapNotNull { timeDir -> referenceOrNull(directories, date, timeDir.removeSuffix("/")) }
                    }
            }
    }

    private fun referenceOrNull(directories: String, date: String, time: String): TrainingReference? {
        val sessionDirectory = "$ROOT_PATH$date/E/$time/"
        val sessionEntries = directoryEntries(directories, sessionDirectory)
        val summary = sessionEntries.firstOrNull { entry -> entry.name == "TSESS.BPB" } ?: return null
        val exercises = sessionEntries
            .filter { entry -> EXERCISE_DIR.matches(entry.name) }
            .sortedBy { entry -> entry.name }
            .mapNotNull { exerciseEntry -> exerciseOrNull(directories, sessionDirectory, exerciseEntry.name.removeSuffix("/")) }
        return TrainingReference(
            dateTime = "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}T${time.substring(0, 2)}:${time.substring(2, 4)}:${time.substring(4, 6)}",
            date = "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}",
            path = "${sessionDirectory}TSESS.BPB",
            trainingDataTypes = listOf("TRAINING_SESSION_SUMMARY"),
            fileSize = summary.size + exercises.sumOf { exercise -> exercise.fileSizes.values.sum() },
            exercises = exercises
        )
    }

    private fun exerciseOrNull(directories: String, sessionDirectory: String, exerciseIndexText: String): ExerciseReference? {
        val exerciseDirectory = "$sessionDirectory$exerciseIndexText/"
        val knownFiles = directoryEntries(directories, exerciseDirectory)
            .mapNotNull { entry -> exerciseDataTypeOrNull(entry.name)?.let { type -> entry to type } }
        if (knownFiles.isEmpty()) return null
        val fileSizes = knownFiles.associate { pair -> pair.first.name to pair.first.size }
        return ExerciseReference(
            index = exerciseIndexText.toInt(),
            androidPath = "$exerciseDirectory${knownFiles.first().first.name}",
            iosPath = exerciseDirectory.removeSuffix("/"),
            exerciseDataTypes = knownFiles.map { pair -> pair.second },
            fileSizes = fileSizes
        )
    }

    private fun payloadFetchOrder(reference: String): List<String> {
        return listOf(reference.stringValue("path")) + reference.objectArray("exercises").flatMap { exercise ->
            val basePath = exercise.stringValue("androidPath").substringBeforeLast("/")
            exercise.stringArrayValue("exerciseDataTypes").map { dataType -> "$basePath/${dataType.deviceFileName()}" }
        }
    }

    private fun assemblePayloadReadResult(reference: String, responses: String, fetchOrder: List<String>): TrainingPayloadResult {
        val exercises = reference.objectArray("exercises").associate { exercise ->
            exercise.intValue("index") to MutableExercisePayload(index = exercise.intValue("index"))
        }.toMutableMap()
        val result = TrainingPayloadResult(exercises = exercises.values.toList())
        fetchOrder.forEach { path ->
            val response = responses.objectValue(path)
            val fileName = response.stringValue("fileName")
            result.totalBytes += response.intValue("byteSize")
            result.completedBytes += response.intValue("byteSize")
            result.currentFileName = fileName
            val exerciseIndex = path.exerciseIndexOrNull()
            if (response.optionalBooleanValue("malformed") == true) {
                exerciseIndex?.let { index -> exercises.getValue(index).malformedFilesIgnored += fileName }
                return@forEach
            }
            val payload = response.objectValue("payload")
            when (response.stringValue("kind")) {
                "trainingSessionSummary" -> {
                    result.modelName = payload.stringValue("modelName")
                    result.durationSeconds = payload.intValue("durationSeconds")
                    result.distanceMeters = payload.intValue("distanceMeters")
                    result.calories = payload.intValue("calories")
                }
                "exerciseSummary" -> exerciseIndex?.let { index -> exercises.getValue(index).exerciseSummaryPresent = true }
                "route", "routeGzip" -> exerciseIndex?.let { index -> exercises.getValue(index).routePresent = true }
                "routeAdvanced", "routeAdvancedGzip" -> exerciseIndex?.let { index -> exercises.getValue(index).routeAdvancedPresent = true }
                "samples", "samplesGzip" -> exerciseIndex?.let { index -> exercises.getValue(index).samplesHeartRate = payload.intArrayValue("heartRateSamples") }
                "samplesAdvancedGzip" -> exerciseIndex?.let { index ->
                    val sampleLists = payload.objectArray("intervalledSampleLists")
                    exercises.getValue(index).samplesAdvancedHeartRate = sampleLists
                        .filter { sampleList -> sampleList.stringValue("sampleType") == "HEART_RATE" }
                        .flatMap { sampleList -> sampleList.intArrayValue("heartRateSamples") }
                    exercises.getValue(index).unknownAdvancedSampleListsIgnored += sampleLists.count { sampleList -> sampleList.stringValue("sampleType") != "HEART_RATE" }
                }
            }
        }
        result.progressPercent = if (result.totalBytes == 0) 0 else result.completedBytes * 100 / result.totalBytes
        return result
    }

    private fun String.exerciseIndexOrNull(): Int? {
        val match = Regex("/(\\d{2})/[^/]+$").find(this) ?: return null
        return match.groupValues[1].toInt()
    }

    private fun String.deviceFileName(): String {
        return when (this) {
            "EXERCISE_SUMMARY" -> "BASE.BPB"
            "ROUTE" -> "ROUTE.BPB"
            "ROUTE_GZIP" -> "ROUTE.GZB"
            "ROUTE_ADVANCED_FORMAT" -> "ROUTE2.BPB"
            "ROUTE_ADVANCED_FORMAT_GZIP" -> "ROUTE2.GZB"
            "SAMPLES" -> "SAMPLES.BPB"
            "SAMPLES_GZIP" -> "SAMPLES.GZB"
            "SAMPLES_ADVANCED_FORMAT_GZIP" -> "SAMPLES2.GZB"
            else -> error("Unknown training exercise data type $this")
        }
    }

    private fun exerciseDataTypeOrNull(name: String): String? {
        return when (name) {
            "BASE.BPB" -> "EXERCISE_SUMMARY"
            "ROUTE.BPB" -> "ROUTE"
            "ROUTE.GZB" -> "ROUTE_GZIP"
            "ROUTE2.BPB" -> "ROUTE_ADVANCED_FORMAT"
            "ROUTE2.GZB" -> "ROUTE_ADVANCED_FORMAT_GZIP"
            "SAMPLES.BPB" -> "SAMPLES"
            "SAMPLES.GZB" -> "SAMPLES_GZIP"
            "SAMPLES2.GZB" -> "SAMPLES_ADVANCED_FORMAT_GZIP"
            else -> null
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

    private data class DirectoryEntry(
        val name: String,
        val size: Int
    )

    private data class TrainingPayloadResult(
        var totalBytes: Int = 0,
        var completedBytes: Int = 0,
        var progressPercent: Int = 0,
        var currentFileName: String = "",
        var modelName: String = "",
        var durationSeconds: Int = 0,
        var distanceMeters: Int = 0,
        var calories: Int = 0,
        val exercises: List<MutableExercisePayload>
    )

    private data class MutableExercisePayload(
        val index: Int,
        var exerciseSummaryPresent: Boolean = false,
        var routePresent: Boolean = false,
        var routeAdvancedPresent: Boolean = false,
        var samplesHeartRate: List<Int> = emptyList(),
        var samplesAdvancedHeartRate: List<Int> = emptyList(),
        var unknownAdvancedSampleListsIgnored: Int = 0,
        var malformedFilesIgnored: List<String> = emptyList()
    )

    private data class TrainingReference(
        val dateTime: String,
        val date: String,
        val path: String,
        val trainingDataTypes: List<String>,
        val fileSize: Int,
        val exercises: List<ExerciseReference>
    )

    private data class ExerciseReference(
        val index: Int,
        val androidPath: String,
        val iosPath: String,
        val exerciseDataTypes: List<String>,
        val fileSizes: Map<String, Int>
    )

    private data class ParserCase(
        val id: String,
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
