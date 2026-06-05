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
import com.polar.shared.sdk.PolarTrainingExerciseReference as SharedPolarTrainingExerciseReference
import com.polar.shared.sdk.PolarTrainingSessionFileEntry
import com.polar.shared.sdk.PolarTrainingSessionModels
import com.polar.shared.sdk.PolarTrainingSessionReference as SharedPolarTrainingSessionReference
import fi.polar.remote.representation.protobuf.ExerciseSamples
import fi.polar.remote.representation.protobuf.ExerciseSamples2
import fi.polar.remote.representation.protobuf.Training
import fi.polar.remote.representation.protobuf.TrainingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import protocol.PftpRequest
import protocol.PftpResponse
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPInputStream

private const val ARABICA_USER_ROOT_FOLDER = "/U/0/"
private const val TAG = "PolarTrainingSessionUtils"

internal object PolarTrainingSessionUtils {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)

    internal fun trainingSessionSummaryReadOperation(path: String): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        return trainingSessionFileOperation("training-session-read-summary", "GET", path)
    }

    internal fun trainingSessionExerciseFileReadOperation(path: String): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        return trainingSessionFileOperation("training-session-read-exercise-file", "GET", path)
    }

    internal fun trainingSessionPayloadFetchOrder(reference: PolarTrainingSessionReference): List<String> {
        return PolarTrainingSessionModels.payloadFetchOrder(reference.toSharedReference())
    }

    internal fun trainingSessionDeleteParentReadOperation(reference: PolarTrainingSessionReference): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        val components = reference.path.split("/").toTypedArray()
        val exerciseParent = ARABICA_USER_ROOT_FOLDER + components[3] + "/E/"
        return trainingSessionFileOperation("training-session-delete-read-parent", "GET", exerciseParent)
    }

    internal fun trainingSessionDeleteRemoveOperation(
        reference: PolarTrainingSessionReference,
        parentEntryCount: Int
    ): Pair<PftpRequest.PbPFtpOperation.Command, String> {
        val components = reference.path.split("/").toTypedArray()
        val removePath = if (parentEntryCount <= 1) {
            "/U/0/${components[3]}/E/"
        } else {
            "/U/0/${components[3]}/E/${components[5]}/"
        }
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

        val startDateInt = fromDate?.let { dateFormatter.format(it).toInt() } ?: Int.MIN_VALUE
        val endDateInt = toDate?.let { dateFormatter.format(it).toInt() } ?: Int.MAX_VALUE

        val entries = mutableListOf<PolarTrainingSessionFileEntry>()

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
            entries += PolarTrainingSessionFileEntry(path = path, size = fileSize)
        }

        val references = PolarTrainingSessionModels.buildReferences(entries)
            .filter { reference -> reference.date.replace("-", "").toInt() in startDateInt..endDateInt }
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
        val tsessOp = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(summaryOperation.first)
            .setPath(summaryOperation.second)
            .build()

        val response = client.request(tsessOp.toByteArray())
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
        val fallbackFilePaths = exercise.exerciseDataTypes
            .map { dataType -> "$basePath/${dataType.deviceFileName}" }
            .filterNot { path -> plannedFilePaths.contains(path) }

        val results = (plannedFilePaths + fallbackFilePaths).map { filePath ->
            val dataType = dataTypesByFileName.getValue(filePath.substringAfterLast("/"))
            BleLogger.d(TAG, "  Fetching file: $filePath")
            val readOperation = trainingSessionExerciseFileReadOperation(filePath)
            val operation = PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(readOperation.first)
                .setPath(readOperation.second)
                .build()
            val data = try {
                val fileResponse = client.request(operation.toByteArray())
                val raw = fileResponse.toByteArray()
                if (filePath.endsWith(".GZB")) {
                    BleLogger.d(TAG, "Unzipping: ${dataType.deviceFileName}")
                    unzipData(raw)
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

    private fun unzipData(data: ByteArray): ByteArray {
        return try {
            ByteArrayInputStream(data).use { bs ->
                GZIPInputStream(bs).use { gz -> gz.readBytes() }
            }
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
                when (type) {
                    PolarExerciseDataTypes.EXERCISE_SUMMARY -> summary = Training.PbExerciseBase.parseFrom(data)
                    PolarExerciseDataTypes.ROUTE, PolarExerciseDataTypes.ROUTE_GZIP ->
                        route = ExerciseRouteSamples.PbExerciseRouteSamples.parseFrom(data)
                    PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT, PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT_GZIP ->
                        routeAdv = ExerciseRouteSamples2.PbExerciseRouteSamples2.parseFrom(data)
                    PolarExerciseDataTypes.SAMPLES, PolarExerciseDataTypes.SAMPLES_GZIP ->
                        samples = ExerciseSamples.PbExerciseSamples.parseFrom(data)
                    PolarExerciseDataTypes.SAMPLES_ADVANCED_FORMAT_GZIP ->
                        samplesAdv = ExerciseSamples2.PbExerciseSamples2.parseFrom(data)
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

        val listOp = PftpRequest.PbPFtpOperation.newBuilder()
            .setCommand(parentReadOperation.first)
            .setPath(parentReadOperation.second)
            .build()

        try {
            val listResponse = client.request(listOp.toByteArray())
            val directory = PftpResponse.PbPFtpDirectory.parseFrom(listResponse.toByteArray())
            val removeOperation = trainingSessionDeleteRemoveOperation(reference, directory.entriesCount)
            val removePath = removeOperation.second
            val removeOp = PftpRequest.PbPFtpOperation.newBuilder()
                .setCommand(removeOperation.first)
                .setPath(removePath)
                .build()
            client.request(removeOp.toByteArray())
            BleLogger.d(TAG, "Deleted training session at $removePath")
        } catch (throwable: Throwable) {
            BleLogger.e(TAG, "Failed to delete: ${throwable.message}")
            throw throwable
        }
    }

    private fun PolarTrainingSessionReference.toSharedReference(): SharedPolarTrainingSessionReference {
        return SharedPolarTrainingSessionReference(
            dateTime = date.toString(),
            date = date.toString(),
            path = path,
            trainingDataTypes = trainingDataTypes.map { dataType -> dataType.name },
            exercises = exercises.map { exercise ->
                SharedPolarTrainingExerciseReference(
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
