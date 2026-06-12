package com.polar.sdk.impl.utils

import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import fi.polar.remote.representation.protobuf.ExerciseRouteSamples
import fi.polar.remote.representation.protobuf.ExerciseRouteSamples2
import com.polar.sdk.api.model.trainingsession.PolarExercise
import com.polar.sdk.api.model.trainingsession.PolarExerciseDataTypes
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionDataTypes
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import fi.polar.remote.representation.protobuf.ExerciseSamples
import fi.polar.remote.representation.protobuf.ExerciseSamples2
import fi.polar.remote.representation.protobuf.Training
import fi.polar.remote.representation.protobuf.TrainingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import protocol.PftpRequest
import protocol.PftpResponse
import java.time.LocalDate

private val ARABICA_USER_ROOT_FOLDER = PolarRuntimePlannerAdapter.trainingSessionRootPath()
private const val TAG = "PolarTrainingSessionUtils"

internal object PolarTrainingSessionUtils {

    internal fun trainingSessionSummaryReadOperation(path: String): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        return trainingSessionFileOperation("training-session-read-summary", "GET", path)
    }

    internal fun trainingSessionExerciseFileReadOperation(path: String): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        return trainingSessionFileOperation("training-session-read-exercise-file", "GET", path)
    }

    internal fun trainingSessionPayloadFetchOrder(reference: PolarTrainingSessionReference): List<String> {
        return PolarRuntimePlannerAdapter.trainingSessionPayloadFetchOrder(reference.toPlannedReference())
    }

    internal fun trainingSessionPayloadEncoding(fileName: String): String? {
        return PolarRuntimePlannerAdapter.trainingSessionPayloadEncoding(fileName)
    }

    internal fun trainingSessionDeleteParentReadOperation(reference: PolarTrainingSessionReference): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        val exerciseParent = PolarRuntimePlannerAdapter.trainingSessionDeleteParentPath(reference.path)
        return trainingSessionFileOperation("training-session-delete-read-parent", "GET", exerciseParent)
    }

    internal fun trainingSessionDeleteRemoveOperation(
        reference: PolarTrainingSessionReference,
        parentEntryCount: Int
    ): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        val removePath = PolarRuntimePlannerAdapter.trainingSessionDeleteRemovePath(reference.path, parentEntryCount)
        return trainingSessionFileOperation("training-session-delete-remove", "REMOVE", removePath)
    }

    private fun trainingSessionFileOperation(
        id: String,
        command: String,
        path: String
    ): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        val plan = PolarRuntimePlannerAdapter.planFileFacade(id, command, path)
        return PolarRuntimePlannerAdapter.fileOperationCommand(plan) to PolarRuntimePlannerAdapter.fileOperationPath(plan)
    }

    fun getTrainingSessionReferences(
        client: BlePsFtpClient,
        fromDate: LocalDate? = null,
        toDate: LocalDate? = null
    ): Flow<PolarTrainingSessionReference> = flow {
        BleLogger.d(TAG, "getTrainingSessions: fromDate=$fromDate, toDate=$toDate")

        val entries = mutableListOf<PolarRuntimePlannerAdapter.PlannedTrainingSessionFileEntry>()

        PolarFileUtils.fetchRecursively(
            client = client,
            path = ARABICA_USER_ROOT_FOLDER,
            condition = PolarFileUtils.FetchRecursiveCondition { name ->
                name.matches(Regex("^\\d{8}/$")) ||
                        name.matches(Regex("^\\d{6}/$")) ||
                        name.matches(Regex("^\\d+/$")) ||
                        name.endsWith(".BPB") ||
                        name.endsWith(".GZB") ||
                        name == "E/"
            },
            tag = TAG,
            recurseDeep = true
        ).collect { (path, fileSize) ->
            BleLogger.d(TAG, "path: $path, size: $fileSize bytes")
            entries += PolarRuntimePlannerAdapter.PlannedTrainingSessionFileEntry(path = path, size = fileSize)
        }

        val references = PolarRuntimePlannerAdapter.trainingSessionReferences(entries)
            .filter { reference -> PolarRuntimePlannerAdapter.trainingSessionReferenceDateMatches(reference.date, fromDate?.toString(), toDate?.toString()) }
            .map { reference ->
                PolarTrainingSessionReference(
                    date = LocalDate.parse(reference.date),
                    path = reference.path,
                    trainingDataTypes = reference.trainingDataTypes.map { it.toTrainingDataType() },
                    exercises = reference.exercises.map { exercise ->
                        PolarExercise(
                            index = exercise.index,
                            path = exercise.androidPath,
                            exerciseDataTypes = exercise.exerciseDataTypes.map { it.toExerciseDataType() },
                            fileSizes = exercise.fileSizes
                        )
                    },
                    fileSize = reference.fileSize
                )
            }

        BleLogger.d(TAG, "Collected ${references.size} training session references:")
        references.forEachIndexed { index, ref ->
            BleLogger.d(TAG, "[$index] date=${ref.date}, totalSize=${ref.fileSize} bytes, path=${ref.path}")
            BleLogger.d(TAG, "  Training data types: ${ref.trainingDataTypes}")
            ref.exercises.forEach { exercise ->
                BleLogger.d(TAG, "  Exercise ${exercise.index}: ${exercise.fileSizes.values.sum()} bytes")
                exercise.fileSizes.forEach { (fileName, size) ->
                    BleLogger.d(TAG, "    - $fileName: $size bytes")
                }
            }
        }

        references.forEach { emit(it) }
    }

    private fun String.toTrainingDataType(): PolarTrainingSessionDataTypes {
        return PolarTrainingSessionDataTypes.valueOf(this)
    }

    private fun String.toExerciseDataType(): PolarExerciseDataTypes {
        return PolarExerciseDataTypes.valueOf(this)
    }

    suspend fun readTrainingSessionWithProgress(
        client: BlePsFtpClient,
        reference: PolarTrainingSessionReference
    ): PolarTrainingSession {
        val summaryOperation = trainingSessionSummaryReadOperation(reference.path)

        val response = client.request(PolarRuntimePlannerAdapter.fileOperationBytes(summaryOperation))
        val sessionSummary = TrainingSession.PbTrainingSession.parseFrom(response.toByteArray())
        BleLogger.d(TAG, "Session summary received, processing ${reference.exercises.size} exercises")

        val payloadFetchOrder = trainingSessionPayloadFetchOrder(reference)
        val exercises = reference.exercises.map { fetchExerciseData(client, it, payloadFetchOrder) }
        BleLogger.d(TAG, "All exercises combined: ${exercises.size}")
        return PolarTrainingSession(reference, sessionSummary, exercises)
    }

    private suspend fun fetchExerciseData(
        client: BlePsFtpClient,
        exercise: PolarExercise,
        payloadFetchOrder: List<String>
    ): PolarExercise {
        BleLogger.d(TAG, "Fetching exercise ${exercise.index} data, path: ${exercise.path}")
        val basePath = exercise.path.substringBeforeLast("/")
        val dataTypesByFileName = exercise.exerciseDataTypes.associateBy { dataType -> dataType.deviceFileName }
        val plannedFilePaths = payloadFetchOrder
            .filter { path -> path.startsWith("$basePath/") }
            .filter { path -> dataTypesByFileName.containsKey(path.substringAfterLast("/")) }

        val results = plannedFilePaths.map { filePath ->
            val dataType = dataTypesByFileName.getValue(filePath.substringAfterLast("/"))
            BleLogger.d(TAG, "  Fetching file: $filePath")
            val readOperation = trainingSessionExerciseFileReadOperation(filePath)
            val data = try {
                val fileResponse = client.request(PolarRuntimePlannerAdapter.fileOperationBytes(readOperation))
                val raw = fileResponse.toByteArray()
                if (trainingSessionPayloadEncoding(dataType.deviceFileName) == "gzip-protobuf") {
                    BleLogger.d(TAG, "Unzipping: ${dataType.deviceFileName}")
                    decodePayloadBytes(dataType.deviceFileName, raw)
                } else raw
            } catch (e: Exception) {
                BleLogger.e(TAG, "Failed to fetch ${dataType.deviceFileName}: ${e.message}")
                ByteArray(0)
            }
            BleLogger.d(TAG, "${dataType.deviceFileName} received: ${data.size} bytes")
            Pair(dataType, data)
        }

        return parseExerciseData(exercise, results)
    }

    internal fun decodePayloadBytes(fileName: String, data: ByteArray): ByteArray {
        return try {
            PolarRuntimePlannerAdapter.decodeTrainingSessionPayloadBytes(fileName, data)
        } catch (e: Exception) {
            BleLogger.e(TAG, "Failed to unzip data: ${e.message}")
            data
        }
    }

    private fun parseExerciseData(
        exercise: PolarExercise,
        results: List<Pair<PolarExerciseDataTypes, ByteArray>>
    ): PolarExercise {
        var summary: Training.PbExerciseBase? = null
        var route: ExerciseRouteSamples.PbExerciseRouteSamples? = null
        var routeAdv: ExerciseRouteSamples2.PbExerciseRouteSamples2? = null
        var samples: ExerciseSamples.PbExerciseSamples? = null
        var samplesAdv: ExerciseSamples2.PbExerciseSamples2? = null

        for ((type, data) in results) {
            if (data.isEmpty()) continue
            try {
                if (PolarRuntimePlannerAdapter.trainingSessionPayloadMalformed(type.deviceFileName, data)) {
                    BleLogger.e(TAG, "  Failed to parse $type: shared protobuf preflight marked payload malformed")
                    continue
                }
                when (PolarRuntimePlannerAdapter.trainingSessionPublicModelSlot(type.deviceFileName)) {
                    "exerciseSummary" -> summary = Training.PbExerciseBase.parseFrom(data)
                    "route" ->
                        route = ExerciseRouteSamples.PbExerciseRouteSamples.parseFrom(data)
                    "routeAdvanced" ->
                        routeAdv = ExerciseRouteSamples2.PbExerciseRouteSamples2.parseFrom(data)
                    "samples" ->
                        samples = ExerciseSamples.PbExerciseSamples.parseFrom(data)
                    "samplesAdvanced" ->
                        samplesAdv = ExerciseSamples2.PbExerciseSamples2.parseFrom(data)
                    else -> Unit
                }
            } catch (e: Exception) {
                BleLogger.e(TAG, "  Failed to parse $type: ${e.message}")
            }
        }

        return exercise.copy(
            exerciseSummary = summary,
            route = route,
            routeAdvanced = routeAdv,
            samples = samples,
            samplesAdvanced = samplesAdv
        )
    }

    suspend fun readTrainingSession(
        client: BlePsFtpClient,
        reference: PolarTrainingSessionReference
    ): PolarTrainingSession = readTrainingSessionWithProgress(client, reference)

    suspend fun deleteTrainingSession(client: BlePsFtpClient, reference: PolarTrainingSessionReference) {
        val parentReadOperation = trainingSessionDeleteParentReadOperation(reference)

        try {
            val listResponse = client.request(PolarRuntimePlannerAdapter.fileOperationBytes(parentReadOperation))
            val directory = PftpResponse.PbPFtpDirectory.parseFrom(listResponse.toByteArray())
            val removeOperation = trainingSessionDeleteRemoveOperation(reference, directory.entriesCount)
            val removePath = removeOperation.second
            client.request(PolarRuntimePlannerAdapter.fileOperationBytes(removeOperation))
            BleLogger.d(TAG, "Deleted training session at $removePath")
        } catch (throwable: Throwable) {
            BleLogger.e(TAG, "Failed to delete: ${throwable.message}")
            throw throwable
        }
    }

    private fun PolarTrainingSessionReference.toPlannedReference(): PolarRuntimePlannerAdapter.PlannedTrainingSessionReference {
        return PolarRuntimePlannerAdapter.PlannedTrainingSessionReference(
            dateTime = date.toString(),
            date = date.toString(),
            path = path,
            trainingDataTypes = trainingDataTypes.map { dataType -> dataType.name },
            exercises = exercises.map { exercise ->
                PolarRuntimePlannerAdapter.PlannedTrainingExerciseReference(
                    index = exercise.index,
                    androidPath = exercise.path,
                    iosPath = exercise.path.substringBeforeLast("/"),
                    exerciseDataTypes = exercise.exerciseDataTypes.map { dataType -> dataType.name },
                    fileSizes = exercise.fileSizes
                )
            },
            fileSize = fileSize
        )
    }
}
