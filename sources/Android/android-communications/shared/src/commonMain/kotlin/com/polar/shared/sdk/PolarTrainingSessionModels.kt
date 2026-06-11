package com.polar.shared.sdk

import com.polar.shared.time.PolarTimeUtils

object PolarTrainingSessionModels {
    const val ROOT_PATH: String = "/U/0/"

    fun buildReferences(entries: List<PolarTrainingSessionFileEntry>): List<PolarTrainingSessionReference> {
        val sessionGroups = linkedMapOf<String, MutableTrainingSessionReference>()
        val exerciseGroups = linkedMapOf<String, MutableExerciseReference>()
        entries.forEach { entry ->
            val match = TRAINING_PATH.find(entry.path) ?: return@forEach
            val date = match.groupValues[1]
            val time = match.groupValues[2]
            val exerciseIndex = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
            val fileName = match.groupValues[4]
            val sessionKey = "$date/$time"
            val trainingType = trainingDataTypeOrNull(fileName)
            if (trainingType != null) {
                val session = sessionGroups.getOrPut(sessionKey) {
                    MutableTrainingSessionReference(
                        dateTime = formatDateTime(date, time),
                        date = formatDate(date),
                        path = "$ROOT_PATH$date/E/$time/$fileName"
                    )
                }
                if (!session.trainingDataTypes.contains(trainingType)) {
                    session.trainingDataTypes += trainingType
                }
                session.fileSize += entry.size
                return@forEach
            }
            val exerciseType = exerciseDataTypeOrNull(fileName) ?: return@forEach
            val index = exerciseIndex ?: return@forEach
            val exerciseKey = "$sessionKey/$index"
            val exercise = exerciseGroups.getOrPut(exerciseKey) {
                MutableExerciseReference(
                    index = index,
                    androidPath = entry.path,
                    iosPath = "$ROOT_PATH$date/E/$time/${index.toString().padStart(2, '0')}",
                    sessionKey = sessionKey
                )
            }
            if (!exercise.exerciseDataTypes.contains(exerciseType)) {
                exercise.exerciseDataTypes += exerciseType
            }
            exercise.fileSizes[fileName] = entry.size
        }
        exerciseGroups.values.forEach { exercise ->
            val session = sessionGroups[exercise.sessionKey] ?: return@forEach
            session.exercises += exercise
            session.fileSize += exercise.fileSizes.values.sum()
        }
        return sessionGroups.values.map { session ->
            PolarTrainingSessionReference(
                dateTime = session.dateTime,
                date = session.date,
                path = session.path,
                trainingDataTypes = session.trainingDataTypes,
                exercises = session.exercises
                    .sortedBy { exercise -> exercise.index }
                    .map { exercise ->
                        PolarTrainingExerciseReference(
                            index = exercise.index,
                            androidPath = exercise.androidPath,
                            iosPath = exercise.iosPath,
                            exerciseDataTypes = exercise.exerciseDataTypes,
                            fileSizes = exercise.fileSizes
                        )
                    },
                fileSize = session.fileSize
            )
        }
    }

    fun trainingDataTypeOrNull(fileName: String): String? {
        return when (fileName) {
            "TSESS.BPB" -> "TRAINING_SESSION_SUMMARY"
            else -> null
        }
    }

    fun exerciseDataTypeOrNull(fileName: String): String? {
        return when (fileName) {
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

    fun exerciseDataTypeFileName(dataType: String): String? {
        return when (dataType) {
            "EXERCISE_SUMMARY" -> "BASE.BPB"
            "ROUTE" -> "ROUTE.BPB"
            "ROUTE_GZIP" -> "ROUTE.GZB"
            "ROUTE_ADVANCED_FORMAT" -> "ROUTE2.BPB"
            "ROUTE_ADVANCED_FORMAT_GZIP" -> "ROUTE2.GZB"
            "SAMPLES" -> "SAMPLES.BPB"
            "SAMPLES_GZIP" -> "SAMPLES.GZB"
            "SAMPLES_ADVANCED_FORMAT_GZIP" -> "SAMPLES2.GZB"
            else -> null
        }
    }

    fun payloadParserCase(fileName: String): PolarTrainingPayloadParserCase? {
        return when (fileName) {
            "TSESS.BPB" -> PolarTrainingPayloadParserCase(fileName, "PbTrainingSession", "protobuf")
            "BASE.BPB" -> PolarTrainingPayloadParserCase(fileName, "PbExerciseBase", "protobuf")
            "ROUTE.BPB" -> PolarTrainingPayloadParserCase(fileName, "PbExerciseRouteSamples", "protobuf")
            "ROUTE.GZB" -> PolarTrainingPayloadParserCase(fileName, "PbExerciseRouteSamples", "gzip-protobuf")
            "ROUTE2.BPB" -> PolarTrainingPayloadParserCase(fileName, "PbExerciseRouteSamples2", "protobuf")
            "ROUTE2.GZB" -> PolarTrainingPayloadParserCase(fileName, "PbExerciseRouteSamples2", "gzip-protobuf")
            "SAMPLES.BPB" -> PolarTrainingPayloadParserCase(fileName, "PbExerciseSamples", "protobuf")
            "SAMPLES.GZB" -> PolarTrainingPayloadParserCase(fileName, "PbExerciseSamples", "gzip-protobuf")
            "SAMPLES2.GZB" -> PolarTrainingPayloadParserCase(fileName, "PbExerciseSamples2", "gzip-protobuf")
            else -> null
        }
    }

    fun decodePayloadBytes(fileName: String, payload: ByteArray): ByteArray {
        return if (payloadParserCase(fileName)?.encoding == "gzip-protobuf") {
            PolarTrainingSessionPayloadCodec.decodeGzipPayload(payload)
        } else {
            payload
        }
    }

    fun parseDecodedPayloadResponse(fileName: String, payload: ByteArray): PolarTrainingPayloadResponse {
        val parserCase = payloadParserCase(fileName)
        val kind = payloadKind(fileName)
        return runCatching {
            PolarTrainingPayloadResponse(
                kind = kind,
                fileName = fileName,
                byteSize = payload.size,
                payload = parserCase?.let { parsePayloadFields(it.parser, payload) } ?: PolarTrainingPayloadFields()
            )
        }.getOrElse {
            PolarTrainingPayloadResponse(
                kind = kind,
                fileName = fileName,
                byteSize = payload.size,
                malformed = true
            )
        }
    }

    fun payloadFetchOrder(reference: PolarTrainingSessionReference): List<String> {
        return listOf(reference.path) + reference.exercises.flatMap { exercise ->
            val basePath = exercise.androidPath.substringBeforeLast("/")
            exercise.exerciseDataTypes.mapNotNull { dataType -> exerciseDataTypeFileName(dataType)?.let { fileName -> "$basePath/$fileName" } }
        }
    }

    fun deleteParentPath(referencePath: String): String {
        val components = referencePath.split("/")
        return "$ROOT_PATH${components[3]}/E/"
    }

    fun deleteRemovePath(referencePath: String, parentEntryCount: Int): String {
        val components = referencePath.split("/")
        return if (parentEntryCount <= 1) {
            "$ROOT_PATH${components[3]}/E/"
        } else {
            "$ROOT_PATH${components[3]}/E/${components[5]}/"
        }
    }

    fun progressPercent(completedBytes: Long, totalBytes: Long): Int {
        return if (totalBytes <= 0L) {
            0
        } else {
            ((completedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        }
    }

    fun referenceDateMatches(date: String, fromDate: String? = null, toDate: String? = null): Boolean {
        val day = date.toBasicDateOrNull() ?: return false
        val from = fromDate?.toBasicDateOrNull()
        val to = toDate?.toBasicDateOrNull()
        if (fromDate != null && from == null) return false
        if (toDate != null && to == null) return false
        return (from == null || day >= from) && (to == null || day <= to)
    }

    fun assemblePayloadReadResult(reference: PolarTrainingSessionReference, responsesByPath: Map<String, PolarTrainingPayloadResponse>, fetchOrder: List<String> = payloadFetchOrder(reference)): PolarTrainingPayloadReadResult {
        val exercises = reference.exercises.associate { exercise ->
            exercise.index to MutableTrainingPayloadExercise(index = exercise.index)
        }.toMutableMap()
        val result = MutableTrainingPayloadReadResult(exercises = exercises.values.toList())
        fetchOrder.forEach { path ->
            val response = responsesByPath.getValue(path)
            result.totalBytes += response.byteSize
            result.completedBytes += response.byteSize
            result.currentFileName = response.fileName
            val exerciseIndex = path.exerciseIndexOrNull()
            if (response.malformed) {
                exerciseIndex?.let { index -> exercises.getValue(index).malformedFilesIgnored += response.fileName }
                return@forEach
            }
            when (response.kind) {
                "trainingSessionSummary" -> {
                    result.modelName = response.payload.modelName.orEmpty()
                    result.durationSeconds = response.payload.durationSeconds ?: 0
                    result.distanceMeters = response.payload.distanceMeters ?: 0
                    result.calories = response.payload.calories ?: 0
                }
                "exerciseSummary" -> exerciseIndex?.let { index -> exercises.getValue(index).exerciseSummaryPresent = true }
                "route", "routeGzip" -> exerciseIndex?.let { index -> exercises.getValue(index).routePresent = true }
                "routeAdvanced", "routeAdvancedGzip" -> exerciseIndex?.let { index -> exercises.getValue(index).routeAdvancedPresent = true }
                "samples", "samplesGzip" -> exerciseIndex?.let { index -> exercises.getValue(index).samplesHeartRate = response.payload.heartRateSamples }
                "samplesAdvancedGzip" -> exerciseIndex?.let { index ->
                    val sampleLists = response.payload.intervalledSampleLists
                    exercises.getValue(index).samplesAdvancedHeartRate = sampleLists
                        .filter { sampleList -> sampleList.sampleType == "HEART_RATE" }
                        .flatMap { sampleList -> sampleList.heartRateSamples }
                    exercises.getValue(index).unknownAdvancedSampleListsIgnored += sampleLists.count { sampleList -> sampleList.sampleType != "HEART_RATE" }
                }
            }
        }
        return PolarTrainingPayloadReadResult(
            totalBytes = result.totalBytes,
            completedBytes = result.completedBytes,
            progressPercent = progressPercent(result.completedBytes.toLong(), result.totalBytes.toLong()),
            currentFileName = result.currentFileName,
            modelName = result.modelName,
            durationSeconds = result.durationSeconds,
            distanceMeters = result.distanceMeters,
            calories = result.calories,
            exercises = result.exercises.map { exercise ->
                PolarTrainingPayloadExercise(
                    index = exercise.index,
                    exerciseSummaryPresent = exercise.exerciseSummaryPresent,
                    routePresent = exercise.routePresent,
                    routeAdvancedPresent = exercise.routeAdvancedPresent,
                    samplesHeartRate = exercise.samplesHeartRate,
                    samplesAdvancedHeartRate = exercise.samplesAdvancedHeartRate,
                    unknownAdvancedSampleListsIgnored = exercise.unknownAdvancedSampleListsIgnored,
                    malformedFilesIgnored = exercise.malformedFilesIgnored
                )
            }
        )
    }

    private fun formatDateTime(date: String, time: String): String {
        return "${formatDate(date)}T${time.substring(0, 2)}:${time.substring(2, 4)}:${time.substring(4, 6)}"
    }

    private fun formatDate(date: String): String {
        return "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}"
    }

    private fun String.toBasicDateOrNull(): String? {
        val value = when (length) {
            8 -> this
            10 -> if (this[4] == '-' && this[7] == '-') "${substring(0, 4)}${substring(5, 7)}${substring(8, 10)}" else return null
            else -> return null
        }
        return value.takeIf { PolarTimeUtils.basicDateRange(it, it).isNotEmpty() }
    }

    private fun payloadKind(fileName: String): String {
        return when (fileName) {
            "TSESS.BPB" -> "trainingSessionSummary"
            "BASE.BPB" -> "exerciseSummary"
            "ROUTE.BPB" -> "route"
            "ROUTE.GZB" -> "routeGzip"
            "ROUTE2.BPB" -> "routeAdvanced"
            "ROUTE2.GZB" -> "routeAdvancedGzip"
            "SAMPLES.BPB" -> "samples"
            "SAMPLES.GZB" -> "samplesGzip"
            "SAMPLES2.GZB" -> "samplesAdvancedGzip"
            else -> "unknown"
        }
    }

    private fun parsePayloadFields(parser: String, payload: ByteArray): PolarTrainingPayloadFields {
        return when (parser) {
            "PbTrainingSession" -> parseTrainingSessionSummary(payload)
            "PbExerciseSamples" -> parseExerciseSamples(payload)
            "PbExerciseSamples2" -> parseExerciseSamples2(payload)
            else -> {
                PolarTrainingProtobufReader(payload).forEachField { _, wireType, reader -> reader.skip(wireType) }
                PolarTrainingPayloadFields()
            }
        }
    }

    private fun parseTrainingSessionSummary(payload: ByteArray): PolarTrainingPayloadFields {
        var modelName: String? = null
        var durationSeconds: Int? = null
        var distanceMeters: Int? = null
        var calories: Int? = null
        PolarTrainingProtobufReader(payload).forEachField { fieldNumber, wireType, reader ->
            when (fieldNumber) {
                4 -> modelName = reader.readStringField(wireType)
                5 -> reader.readLengthDelimitedField(wireType) { durationSeconds = it.readDurationSeconds() }
                6 -> distanceMeters = reader.readFloatField(wireType).toInt()
                7 -> calories = reader.readVarintField(wireType).toInt()
                else -> reader.skip(wireType)
            }
        }
        return PolarTrainingPayloadFields(
            modelName = modelName,
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters,
            calories = calories
        )
    }

    private fun parseExerciseSamples(payload: ByteArray): PolarTrainingPayloadFields {
        val heartRateSamples = mutableListOf<Int>()
        PolarTrainingProtobufReader(payload).forEachField { fieldNumber, wireType, reader ->
            when (fieldNumber) {
                2 -> heartRateSamples += reader.readUInt32RepeatedField(wireType)
                else -> reader.skip(wireType)
            }
        }
        return PolarTrainingPayloadFields(heartRateSamples = heartRateSamples)
    }

    private fun parseExerciseSamples2(payload: ByteArray): PolarTrainingPayloadFields {
        val sampleLists = mutableListOf<PolarTrainingIntervalledSampleList>()
        PolarTrainingProtobufReader(payload).forEachField { fieldNumber, wireType, reader ->
            when (fieldNumber) {
                1 -> reader.readLengthDelimitedField(wireType) { sampleLists += it.readIntervalledSample2List() }
                else -> reader.skip(wireType)
            }
        }
        return PolarTrainingPayloadFields(intervalledSampleLists = sampleLists)
    }

    private fun PolarTrainingProtobufReader.readDurationSeconds(): Int {
        var hours = 0
        var minutes = 0
        var seconds = 0
        forEachField { fieldNumber, wireType, reader ->
            when (fieldNumber) {
                1 -> hours = reader.readVarintField(wireType).toInt()
                2 -> minutes = reader.readVarintField(wireType).toInt()
                3 -> seconds = reader.readVarintField(wireType).toInt()
                else -> reader.skip(wireType)
            }
        }
        return hours * 3600 + minutes * 60 + seconds
    }

    private fun PolarTrainingProtobufReader.readIntervalledSample2List(): PolarTrainingIntervalledSampleList {
        var sampleType = "UNDEFINED"
        val heartRateSamples = mutableListOf<Int>()
        forEachField { fieldNumber, wireType, reader ->
            when (fieldNumber) {
                1 -> sampleType = sampleTypeName(reader.readVarintField(wireType).toInt())
                5 -> heartRateSamples += reader.readSInt32RepeatedField(wireType)
                else -> reader.skip(wireType)
            }
        }
        return PolarTrainingIntervalledSampleList(sampleType = sampleType, heartRateSamples = heartRateSamples)
    }

    private fun sampleTypeName(value: Int): String {
        return when (value) {
            1 -> "HEART_RATE"
            2 -> "CADENCE"
            3 -> "ALTITUDE"
            5 -> "TEMPERATURE"
            6 -> "SPEED"
            7 -> "DISTANCE"
            8 -> "STRIDE_LENGTH"
            10 -> "FORWARD_ACCELERATION"
            11 -> "MOVING_TYPE"
            16 -> "RR_INTERVAL"
            17 -> "ACCELERATION_MAD"
            else -> "UNDEFINED"
        }
    }

    private data class MutableTrainingSessionReference(
        val dateTime: String,
        val date: String,
        val path: String,
        val trainingDataTypes: MutableList<String> = mutableListOf(),
        val exercises: MutableList<MutableExerciseReference> = mutableListOf(),
        var fileSize: Long = 0L
    )

    private data class MutableExerciseReference(
        val index: Int,
        val androidPath: String,
        val iosPath: String,
        val sessionKey: String,
        val exerciseDataTypes: MutableList<String> = mutableListOf(),
        val fileSizes: MutableMap<String, Long> = linkedMapOf()
    )

    private val TRAINING_PATH = Regex("^/U/0/(\\d{8})/E/(\\d{6})(?:/(\\d+))?/([^/]+)$")

    private fun String.exerciseIndexOrNull(): Int? {
        val match = Regex("/(\\d{2})/[^/]+$").find(this) ?: return null
        return match.groupValues[1].toInt()
    }
}

data class PolarTrainingSessionFileEntry(
    val path: String,
    val size: Long
)

data class PolarTrainingSessionReference(
    val dateTime: String,
    val date: String,
    val path: String,
    val trainingDataTypes: List<String>,
    val exercises: List<PolarTrainingExerciseReference>,
    val fileSize: Long
)

data class PolarTrainingExerciseReference(
    val index: Int,
    val androidPath: String,
    val iosPath: String,
    val exerciseDataTypes: List<String>,
    val fileSizes: Map<String, Long>
)

data class PolarTrainingPayloadResponse(
    val kind: String,
    val fileName: String,
    val byteSize: Int,
    val malformed: Boolean = false,
    val payload: PolarTrainingPayloadFields = PolarTrainingPayloadFields()
)

data class PolarTrainingPayloadFields(
    val modelName: String? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Int? = null,
    val calories: Int? = null,
    val heartRateSamples: List<Int> = emptyList(),
    val intervalledSampleLists: List<PolarTrainingIntervalledSampleList> = emptyList()
)

data class PolarTrainingPayloadParserCase(
    val fileName: String,
    val parser: String,
    val encoding: String
)

data class PolarTrainingIntervalledSampleList(
    val sampleType: String,
    val heartRateSamples: List<Int> = emptyList()
)

data class PolarTrainingPayloadReadResult(
    val totalBytes: Int,
    val completedBytes: Int,
    val progressPercent: Int,
    val currentFileName: String,
    val modelName: String,
    val durationSeconds: Int,
    val distanceMeters: Int,
    val calories: Int,
    val exercises: List<PolarTrainingPayloadExercise>
)

data class PolarTrainingPayloadExercise(
    val index: Int,
    val exerciseSummaryPresent: Boolean,
    val routePresent: Boolean,
    val routeAdvancedPresent: Boolean,
    val samplesHeartRate: List<Int>,
    val samplesAdvancedHeartRate: List<Int>,
    val unknownAdvancedSampleListsIgnored: Int,
    val malformedFilesIgnored: List<String>
)

private data class MutableTrainingPayloadReadResult(
    var totalBytes: Int = 0,
    var completedBytes: Int = 0,
    var currentFileName: String = "",
    var modelName: String = "",
    var durationSeconds: Int = 0,
    var distanceMeters: Int = 0,
    var calories: Int = 0,
    val exercises: List<MutableTrainingPayloadExercise>
)

private data class MutableTrainingPayloadExercise(
    val index: Int,
    var exerciseSummaryPresent: Boolean = false,
    var routePresent: Boolean = false,
    var routeAdvancedPresent: Boolean = false,
    var samplesHeartRate: List<Int> = emptyList(),
    var samplesAdvancedHeartRate: List<Int> = emptyList(),
    var unknownAdvancedSampleListsIgnored: Int = 0,
    var malformedFilesIgnored: List<String> = emptyList()
)
