package com.polar.sdk.api.model.trainingsession

import fi.polar.remote.representation.protobuf.ExerciseRouteSamples
import fi.polar.remote.representation.protobuf.ExerciseRouteSamples2
import fi.polar.remote.representation.protobuf.ExerciseSamples
import fi.polar.remote.representation.protobuf.ExerciseSamples2
import fi.polar.remote.representation.protobuf.Training
import fi.polar.remote.representation.protobuf.TrainingSession
import com.polar.shared.sdk.PolarTrainingSessionModels
import java.time.LocalDate

data class PolarTrainingSessionReference(
    val date: LocalDate,
    val path: String,
    val trainingDataTypes: List<PolarTrainingSessionDataTypes>,
    val exercises: List<PolarExercise>,
    val fileSize: Long = 0L
)

enum class PolarTrainingSessionDataTypes(val deviceFileName: String) {
    TRAINING_SESSION_SUMMARY("TSESS.BPB");
}

data class PolarTrainingSession(
    val reference: PolarTrainingSessionReference,
    val sessionSummary: TrainingSession.PbTrainingSession? = null,
    val exercises: List<PolarExercise> = emptyList()
)

data class PolarExercise(
    val index: Int,
    @Transient val path: String,
    val exerciseDataTypes: List<PolarExerciseDataTypes> = emptyList(),
    val exerciseSummary: Training.PbExerciseBase? = null,
    val route: ExerciseRouteSamples.PbExerciseRouteSamples? = null,
    val routeAdvanced: ExerciseRouteSamples2.PbExerciseRouteSamples2? = null,
    val samples: ExerciseSamples.PbExerciseSamples? = null,
    val samplesAdvanced: ExerciseSamples2.PbExerciseSamples2? = null,
    val fileSizes: Map<String, Long> = emptyMap()
)

enum class PolarExerciseDataTypes {
    EXERCISE_SUMMARY,
    ROUTE,
    ROUTE_GZIP,
    ROUTE_ADVANCED_FORMAT,
    ROUTE_ADVANCED_FORMAT_GZIP,
    SAMPLES,
    SAMPLES_GZIP,
    SAMPLES_ADVANCED_FORMAT_GZIP;

    val deviceFileName: String
        get() = requireNotNull(PolarTrainingSessionModels.exerciseDataTypeFileName(name)) { "Unknown exercise data type $name" }
}

/**
 * Progress information for training session fetching.
 *
 * @param totalBytes Total size of all files to fetch in bytes
 * @param completedBytes Number of bytes fetched so far
 * @param progressPercent Progress as a percentage (0-100)
 * @param currentFileName Name of the file currently being fetched (optional)
 */
data class PolarTrainingSessionProgress(
    val totalBytes: Long,
    val completedBytes: Long,
    val progressPercent: Int,
    val currentFileName: String? = null
)

/**
 * Result class to emit both progress and final result when fetching training sessions.
 */
sealed class PolarTrainingSessionFetchResult {
    /**
     * Progress update during training session fetch.
     */
    data class Progress(val progress: PolarTrainingSessionProgress) : PolarTrainingSessionFetchResult()

    /**
     * Complete training session fetch with the final session data.
     */
    data class Complete(val session: PolarTrainingSession) : PolarTrainingSessionFetchResult()
}
