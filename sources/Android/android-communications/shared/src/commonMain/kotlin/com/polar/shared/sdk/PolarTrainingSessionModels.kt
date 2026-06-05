package com.polar.shared.sdk

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

    private fun formatDateTime(date: String, time: String): String {
        return "${formatDate(date)}T${time.substring(0, 2)}:${time.substring(2, 4)}:${time.substring(4, 6)}"
    }

    private fun formatDate(date: String): String {
        return "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}"
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
