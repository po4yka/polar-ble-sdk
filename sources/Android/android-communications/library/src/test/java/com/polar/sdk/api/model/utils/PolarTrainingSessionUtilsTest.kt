package com.polar.sdk.api.model.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.protobuf.ByteString
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionDataTypes
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import com.polar.sdk.impl.utils.PolarTrainingSessionUtils
import fi.polar.remote.representation.protobuf.Structures
import fi.polar.remote.representation.protobuf.Training
import fi.polar.remote.representation.protobuf.TrainingSession
import fi.polar.remote.representation.protobuf.Types
import com.polar.sdk.api.model.trainingsession.PolarExercise
import com.polar.sdk.api.model.trainingsession.PolarExerciseDataTypes
import fi.polar.remote.representation.protobuf.ExerciseRouteSamples
import fi.polar.remote.representation.protobuf.ExerciseRouteSamples2
import fi.polar.remote.representation.protobuf.ExerciseSamples
import fi.polar.remote.representation.protobuf.ExerciseSamples2
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import protocol.PftpRequest
import protocol.PftpResponse.PbPFtpDirectory
import protocol.PftpResponse.PbPFtpEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPOutputStream

class PolarTrainingSessionUtilsTest {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ENGLISH)

    @Test
    fun `training session read and delete headers use shared file facade planning`() {
        val reference = PolarTrainingSessionReference(
            date = LocalDate.of(2026, 1, 2),
            path = "/U/0/20260102/E/123456/TSESS.BPB",
            trainingDataTypes = listOf(PolarTrainingSessionDataTypes.TRAINING_SESSION_SUMMARY),
            exercises = emptyList(),
            fileSize = 1024L
        )

        assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/U/0/20260102/E/123456/TSESS.BPB",
            PolarTrainingSessionUtils.trainingSessionSummaryReadOperation(reference.path)
        )
        assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/U/0/20260102/E/123456/00/BASE.BPB",
            PolarTrainingSessionUtils.trainingSessionExerciseFileReadOperation("/U/0/20260102/E/123456/00/BASE.BPB")
        )
        assertEquals(
            PftpRequest.PbPFtpOperation.Command.GET to "/U/0/20260102/E/",
            PolarTrainingSessionUtils.trainingSessionDeleteParentReadOperation(reference)
        )
        assertEquals(
            PftpRequest.PbPFtpOperation.Command.REMOVE to "/U/0/20260102/E/",
            PolarTrainingSessionUtils.trainingSessionDeleteRemoveOperation(reference, parentEntryCount = 1)
        )
        assertEquals(
            PftpRequest.PbPFtpOperation.Command.REMOVE to "/U/0/20260102/E/123456/",
            PolarTrainingSessionUtils.trainingSessionDeleteRemoveOperation(reference, parentEntryCount = 2)
        )
    }

    @Test
    fun `training session payload fetch order uses shared planner`() {
        val reference = PolarTrainingSessionReference(
            date = LocalDate.of(2026, 1, 2),
            path = "/U/0/20260102/E/123456/TSESS.BPB",
            trainingDataTypes = listOf(PolarTrainingSessionDataTypes.TRAINING_SESSION_SUMMARY),
            exercises = listOf(
                PolarExercise(
                    index = 0,
                    path = "/U/0/20260102/E/123456/00/BASE.BPB",
                    exerciseDataTypes = listOf(
                        PolarExerciseDataTypes.EXERCISE_SUMMARY,
                        PolarExerciseDataTypes.ROUTE,
                        PolarExerciseDataTypes.ROUTE_GZIP,
                        PolarExerciseDataTypes.SAMPLES_ADVANCED_FORMAT_GZIP
                    ),
                    fileSizes = mapOf(
                        "BASE.BPB" to 10L,
                        "ROUTE.BPB" to 20L,
                        "ROUTE.GZB" to 30L,
                        "SAMPLES2.GZB" to 40L
                    )
                )
            ),
            fileSize = 100L
        )

        assertEquals(
            listOf(
                "/U/0/20260102/E/123456/TSESS.BPB",
                "/U/0/20260102/E/123456/00/BASE.BPB",
                "/U/0/20260102/E/123456/00/ROUTE.BPB",
                "/U/0/20260102/E/123456/00/ROUTE.GZB",
                "/U/0/20260102/E/123456/00/SAMPLES2.GZB"
            ),
            PolarTrainingSessionUtils.trainingSessionPayloadFetchOrder(reference)
        )
    }

    @Test
    fun `getTrainingSessionReferences() should return all training session references`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()

        val dateDirectories = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(mutableListOf(
                    PbPFtpEntry.newBuilder().setName("20250101/").setSize(8192L).build(),
                    PbPFtpEntry.newBuilder().setName("20250202/").setSize(8192L).build()
                )).build().writeTo(this)
        }

        val exerciseDirectory = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(PbPFtpEntry.newBuilder().setName("E/").setSize(4096L).build()))
                .build().writeTo(this)
        }

        val timeDirectory = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(PbPFtpEntry.newBuilder().setName("204507/").setSize(2048L).build()))
                .build().writeTo(this)
        }

        val timeDirectory2 = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(PbPFtpEntry.newBuilder().setName("163020/").setSize(2048L).build()))
                .build().writeTo(this)
        }

        val trainingSessionEntry = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(PbPFtpEntry.newBuilder().setName("TSESS.BPB").setSize(1024L).build()))
                .build().writeTo(this)
        }

        val trainingSessionEntry2 = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(
                    PbPFtpEntry.newBuilder().setName("TSESS.BPB").setSize(1024L).build(),
                    PbPFtpEntry.newBuilder().setName("00/").setSize(1024L).build(),
                    PbPFtpEntry.newBuilder().setName("01/").setSize(1024L).build()
                )).build().writeTo(this)
        }

        val exerciseBaseEntry = ByteArrayOutputStream().apply {
            PbPFtpDirectory.newBuilder()
                .addAllEntries(listOf(
                    PbPFtpEntry.newBuilder().setName("BASE.BPB").setSize(512L).build(),
                    PbPFtpEntry.newBuilder().setName("ROUTE.BPB").setSize(512L).build(),
                    PbPFtpEntry.newBuilder().setName("ROUTE2.BPB").setSize(512L).build(),
                    PbPFtpEntry.newBuilder().setName("ROUTE.GZB").setSize(512L).build(),
                    PbPFtpEntry.newBuilder().setName("ROUTE2.GZB").setSize(512L).build(),
                    PbPFtpEntry.newBuilder().setName("SAMPLES.BPB").setSize(512L).build(),
                    PbPFtpEntry.newBuilder().setName("SAMPLES.GZB").setSize(512L).build(),
                    PbPFtpEntry.newBuilder().setName("SAMPLES2.GZB").setSize(512L).build(),
                )).build().writeTo(this)
        }

        val expectedReferences = listOf(
            LocalDate.parse("20250101204507", dateFormatter)?.let {
                PolarTrainingSessionReference(
                    date = it,
                    path = "/U/0/20250101/E/204507/TSESS.BPB",
                    trainingDataTypes = listOf(PolarTrainingSessionDataTypes.TRAINING_SESSION_SUMMARY),
                    exercises = emptyList(),
                    fileSize = 1024L
                )
            },
            LocalDate.parse("20250202163020", dateFormatter)?.let {
                PolarTrainingSessionReference(
                    date = it,
                    path = "/U/0/20250202/E/163020/TSESS.BPB",
                    trainingDataTypes = listOf(PolarTrainingSessionDataTypes.TRAINING_SESSION_SUMMARY),
                    exercises = listOf(
                        PolarExercise(
                            index = 0,
                            path = "/U/0/20250202/E/163020/00/BASE.BPB",
                            exerciseDataTypes = listOf(
                                PolarExerciseDataTypes.EXERCISE_SUMMARY,
                                PolarExerciseDataTypes.ROUTE,
                                PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT,
                                PolarExerciseDataTypes.ROUTE_GZIP,
                                PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT_GZIP,
                                PolarExerciseDataTypes.SAMPLES,
                                PolarExerciseDataTypes.SAMPLES_GZIP,
                                PolarExerciseDataTypes.SAMPLES_ADVANCED_FORMAT_GZIP,
                            ),
                            exerciseSummary = null,
                            fileSizes = mapOf(
                                "BASE.BPB" to 512L, "ROUTE.BPB" to 512L, "ROUTE2.BPB" to 512L,
                                "ROUTE.GZB" to 512L, "ROUTE2.GZB" to 512L, "SAMPLES.BPB" to 512L,
                                "SAMPLES.GZB" to 512L, "SAMPLES2.GZB" to 512L
                            )
                        ),
                        PolarExercise(
                            index = 1,
                            path = "/U/0/20250202/E/163020/01/BASE.BPB",
                            exerciseDataTypes = listOf(
                                PolarExerciseDataTypes.EXERCISE_SUMMARY,
                                PolarExerciseDataTypes.ROUTE,
                                PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT,
                                PolarExerciseDataTypes.ROUTE_GZIP,
                                PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT_GZIP,
                                PolarExerciseDataTypes.SAMPLES,
                                PolarExerciseDataTypes.SAMPLES_GZIP,
                                PolarExerciseDataTypes.SAMPLES_ADVANCED_FORMAT_GZIP,
                            ),
                            exerciseSummary = null,
                            fileSizes = mapOf(
                                "BASE.BPB" to 512L, "ROUTE.BPB" to 512L, "ROUTE2.BPB" to 512L,
                                "ROUTE.GZB" to 512L, "ROUTE2.GZB" to 512L, "SAMPLES.BPB" to 512L,
                                "SAMPLES.GZB" to 512L, "SAMPLES2.GZB" to 512L
                            )
                        )
                    ),
                    fileSize = 9216L
                )
            }
        )

        coEvery { client.request(any<ByteArray>()) } answers { dateDirectories } andThen
                exerciseDirectory andThen timeDirectory andThen trainingSessionEntry andThen
                exerciseDirectory andThen timeDirectory2 andThen trainingSessionEntry2 andThen
                exerciseBaseEntry andThen exerciseBaseEntry

        // Act
        val emitted = mutableListOf<PolarTrainingSessionReference>()
        val job = launch {
            PolarTrainingSessionUtils.getTrainingSessionReferences(client).collect { emitted.add(it) }
        }
        job.join()

        // Assert
        assertEquals(expectedReferences, emitted)

        coVerify {
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250101/").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250101/E/").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250101/E/204507/").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250202/").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250202/E/").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250202/E/163020/").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250202/E/163020/00/").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250202/E/163020/01/").build().toByteArray())
        }
        confirmVerified(client)
    }

    @Test
    fun `training session reference discovery golden vectors match Android behavior`() = runTest {
        val vectors = loadTrainingSessionVectors()
        assertTrue("Expected training-session golden vectors", vectors.isNotEmpty())

        vectors.forEach { vector ->
            val caseId = vector.get("id").asString
            val input = vector.getAsJsonObject("input")
            if (!input.has("directories")) {
                return@forEach
            }
            val client = mockk<BlePsFtpClient>()
            val directories = input.getAsJsonObject("directories")
            coEvery { client.request(any<ByteArray>()) } answers {
                val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
                ByteArrayOutputStream().apply {
                    buildDirectory(directories.getAsJsonArray(operation.path).map { it.asJsonObject }).writeTo(this)
                }
            }

            val emitted = mutableListOf<PolarTrainingSessionReference>()
            val job = launch {
                PolarTrainingSessionUtils.getTrainingSessionReferences(client).collect { emitted.add(it) }
            }
            job.join()

            assertTrainingSessionReferences(caseId, emitted, vector.getAsJsonObject("expected"))
        }
    }

    @Test
    fun `training session read golden vectors preserve missing exercise file policy`() = runTest {
        val vector = loadTrainingSessionVectors().first { it.get("id").asString == "missing-exercise-file-platform-policy" }
        val client = mockk<BlePsFtpClient>()
        val input = vector.getAsJsonObject("input")
        val reference = buildTrainingSessionReference(input.getAsJsonObject("reference"), androidPaths = true)
        val responses = input.getAsJsonObject("responses")

        coEvery { client.request(any<ByteArray>()) } answers {
            val operation = PftpRequest.PbPFtpOperation.parseFrom(firstArg<ByteArray>())
            val responseType = responses.get(operation.path)?.asString ?: throw Exception("Missing file: ${operation.path}")
            ByteArrayOutputStream().apply { writeTrainingSessionFixture(responseType, this) }
        }

        val result = PolarTrainingSessionUtils.readTrainingSession(client, reference)
        val expected = vector.getAsJsonObject("expected").getAsJsonObject("android")

        assertEquals(vector.get("id").asString, expected.get("sessionSummaryPresent").asBoolean, result.sessionSummary != null)
        assertEquals(vector.get("id").asString, expected.get("exerciseCount").asInt, result.exercises.size)
        assertEquals(vector.get("id").asString, expected.get("exerciseSummaryPresent").asBoolean, result.exercises[0].exerciseSummary != null)
        assertEquals(vector.get("id").asString, expected.get("routePresent").asBoolean, result.exercises[0].route != null)
    }

    @Test
    fun `training session golden vectors follow neutral KMP vector shape`() {
        val vectors = loadTrainingSessionVectors()
        assertTrue("Expected training-session golden vectors", vectors.isNotEmpty())
        vectors.forEach { vector ->
            val id = vector.get("id").asString
            assertTrue(id, vector.has("area"))
            assertTrue(id, vector.has("case"))
            assertTrue(id, vector.has("source"))
            assertTrue(id, vector.has("input"))
            assertTrue(id, vector.has("expected"))
            assertTrue(id, vector.has("platforms"))
            assertTrue(id, vector.getAsJsonObject("input").has("kind"))
            val platforms = vector.getAsJsonObject("platforms")
            assertTrue(id, platforms.get("android").asBoolean)
            assertTrue(id, platforms.get("ios").asBoolean)
            assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    @Test
    fun `payload parser policy vector is pinned before byte level parser migration`() {
        val vector = loadTrainingSessionVectors().first { it.get("id").asString == "payload-parser-policy" }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val cases = input.getAsJsonArray("cases").map { it.asJsonObject }
        val prototypeCases = expected.getAsJsonObject("commonParserPrototype").getAsJsonArray("cases").map { it.asJsonObject }

        assertEquals("payloadParserPolicy", input.get("kind").asString)
        assertEquals(TRAINING_SESSION_PAYLOAD_PARSER_CASE_IDS, cases.map { it.get("id").asString })
        assertEquals(TRAINING_SESSION_PAYLOAD_PARSER_CASE_IDS, prototypeCases.map { it.get("id").asString })
        cases.zip(prototypeCases).forEach { (inputCase, prototypeCase) ->
            val id = inputCase.get("id").asString
            assertEquals(id, inputCase.get("parser").asString, prototypeCase.get("parser").asString)
            assertEquals(id, inputCase.get("encoding").asString, prototypeCase.get("encoding").asString)
            assertEquals(id, inputCase.getAsJsonArray("expectedFields").map { it.asString }, prototypeCase.getAsJsonArray("fields").map { it.asString })
        }
        assertEquals(4, cases.count { it.get("encoding").asString == "gzip-protobuf" })
        assertEquals("executable shared parser-policy coverage; byte decoding remains gated on common protobuf and gzip dependencies", expected.getAsJsonObject("commonParserPrototype").get("status").asString)
        assertEquals(TRAINING_SESSION_PAYLOAD_PARSER_COMMON_DECISION, expected.get("commonDecision").asString)
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarTrainingSessionUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("PolarTrainingSessionUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.TrainingSessionCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun `training session readiness manifest is pinned before migration`() {
        val readiness = loadTrainingSessionReadinessManifest()
        val input = readiness.getAsJsonObject("input")
        val expected = readiness.getAsJsonObject("expected")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        assertEquals("training-session-readiness", readiness.get("id").asString)
        assertEquals("trainingSessionReadiness", input.get("kind").asString)
        assertEquals(
            listOf(
                "sdk/training-session/reference-discovery-two-sessions.json",
                "sdk/training-session/missing-exercise-file-platform-policy.json",
                "sdk/training-session/payload-read-policy.json",
                "sdk/training-session/payload-parser-policy.json"
            ),
            policyVectorPaths
        )
        val expectedFamilies = listOf(
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
        assertEquals(expectedFamilies, requiredFamilies)
        assertEquals(expectedFamilies, coveredFamilies)
        assertEquals(
            "Training-session migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS training-session tests continue to reference the same vectors, directory traversal, summary discovery, exercise classification, unknown-file ignoring, aggregate size, exercise path policy, missing exercise-file policy, payload fetch order, progress, malformed component isolation, unknown advanced sample-list handling, known sample preservation, parser-family ownership, byte-level parser dependency gates, and compile verification remain explicit before production discovery/read orchestration moves.",
            expected.get("commonDecision").asString
        )
    }

    @Test
    fun `readTrainingSession() should return training session data`() = runTest {
        // Arrange
        val client = mockk<BlePsFtpClient>()
        val reference = PolarTrainingSessionReference(
            date = LocalDate.parse("20250101101200", dateFormatter),
            path = "/U/0/20250101/E/101200/TSESS.BPB",
            trainingDataTypes = listOf(PolarTrainingSessionDataTypes.TRAINING_SESSION_SUMMARY),
            exercises = listOf(
                PolarExercise(
                    index = 0,
                    path = "/U/0/20250101/E/101200/BASE.BPB",
                    exerciseDataTypes = listOf(
                        PolarExerciseDataTypes.EXERCISE_SUMMARY,
                        PolarExerciseDataTypes.ROUTE,
                        PolarExerciseDataTypes.ROUTE_GZIP,
                        PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT,
                        PolarExerciseDataTypes.ROUTE_ADVANCED_FORMAT_GZIP,
                        PolarExerciseDataTypes.SAMPLES,
                        PolarExerciseDataTypes.SAMPLES_GZIP,
                        PolarExerciseDataTypes.SAMPLES_ADVANCED_FORMAT_GZIP,
                    )
                )
            )
        )

        val mockProto = TrainingSession.PbTrainingSession.newBuilder()
            .setStart(Types.PbLocalDateTime.newBuilder()
                .setDate(Types.PbDate.newBuilder().setYear(2025).setMonth(1).setDay(1))
                .setTime(Types.PbTime.newBuilder().setHour(10).setMinute(12).setSeconds(0))
                .setOBSOLETETrusted(true))
            .setEnd(Types.PbLocalDateTime.newBuilder()
                .setDate(Types.PbDate.newBuilder().setYear(2025).setMonth(1).setDay(1))
                .setTime(Types.PbTime.newBuilder().setHour(11).setMinute(12).setSeconds(0))
                .setOBSOLETETrusted(true))
            .setExerciseCount(1).setDeviceId("123ABC").setModelName("Polar 360")
            .setDuration(Types.PbDuration.newBuilder().setSeconds(3600))
            .setDistance(12.5f).setCalories(400)
            .setHeartRate(TrainingSession.PbSessionHeartRateStatistics.newBuilder().setAverage(110).setMaximum(150))
            .addHeartRateZoneDuration(Types.PbDuration.newBuilder().setSeconds(300))
            .setTrainingLoad(Structures.PbTrainingLoad.newBuilder().setTrainingLoadVal(50))
            .setSessionName(Structures.PbOneLineText.newBuilder().setText("Running"))
            .setFeeling(4.5f).setNote(Structures.PbMultiLineText.newBuilder().setText("Note"))
            .setPlace(Structures.PbOneLineText.newBuilder().setText("Place"))
            .setLatitude(60.1699).setLongitude(24.9384)
            .setBenefit(Types.PbExerciseFeedback.FEEDBACK_1)
            .setSport(Structures.PbSportIdentifier.newBuilder().setValue(3))
            .setCardioLoad(Types.PbCardioLoad.newBuilder().setExerciseLoad(100f).setActivityLoad(50f))
            .setCardioLoadInterpretation(3).setMuscleLoad(200.5f).setMuscleLoadInterpretation(4)
            .setPeriodUuid(ByteString.copyFromUtf8("123e4567-e89b-12d3-a456-426614174000"))
            .setStartTrigger(TrainingSession.PbTrainingSession.PbTrainingStartTrigger.MANUAL)
            .build()

        val exerciseProto = Training.PbExerciseBase.newBuilder()
            .setStart(Types.PbLocalDateTime.newBuilder()
                .setDate(Types.PbDate.newBuilder().setYear(2025).setMonth(1).setDay(1))
                .setTime(Types.PbTime.newBuilder().setHour(10).setMinute(12).setSeconds(0))
                .setOBSOLETETrusted(true))
            .setDuration(Types.PbDuration.newBuilder().setSeconds(3600))
            .setSport(Structures.PbSportIdentifier.newBuilder().setValue(3))
            .setCalories(400).setDistance(12.5f)
            .setTrainingLoad(Structures.PbTrainingLoad.newBuilder().setTrainingLoadVal(50))
            .addAllAvailableSensorFeatures(listOf(Types.PbFeatureType.FEATURE_TYPE_HEART_RATE, Types.PbFeatureType.FEATURE_TYPE_GPS_LOCATION))
            .setRunningIndex(Structures.PbRunningIndex.newBuilder().setValue(55).build())
            .setAscent(100.5f).setDescent(90.3f).setLatitude(60.1699).setLongitude(24.9384).setPlace("Place")
            .setExerciseCounters(Training.PbExerciseCounters.newBuilder().setSprintCount(10))
            .setWalkingDistance(5.0f).setWalkingDuration(Types.PbDuration.newBuilder().setSeconds(1800))
            .setAccumulatedTorque(150).setCyclingPowerEnergy(200)
            .setCardioLoad(Types.PbCardioLoad.newBuilder().setExerciseLoad(100f).setActivityLoad(50f))
            .setCardioLoadInterpretation(3)
            .setPerceivedLoad(Types.PbPerceivedLoad.newBuilder().setSessionRpe(Types.PbSessionRPE.RPE_HARD).setDuration(3600))
            .setPerceivedLoadInterpretation(2).setMuscleLoad(200.5f).setMuscleLoadInterpretation(4)
            .setLastModified(Types.PbSystemDateTime.newBuilder()
                .setDate(Types.PbDate.newBuilder().setYear(2025).setMonth(1).setDay(2))
                .setTime(Types.PbTime.newBuilder().setHour(12).setMinute(0).setSeconds(0))
                .setTrusted(true))
            .build()

        val routeProto = ExerciseRouteSamples.PbExerciseRouteSamples.newBuilder()
            .addDuration(0).addLatitude(60.17).addLongitude(24.94).addGpsAltitude(10)
            .addSatelliteAmount(7).addOBSOLETEFix(true)
            .setFirstLocationTime(Types.PbSystemDateTime.newBuilder()
                .setDate(Types.PbDate.newBuilder().setYear(2025).setMonth(1).setDay(2))
                .setTime(Types.PbTime.newBuilder().setHour(12).setMinute(0).setSeconds(0))
                .setTrusted(true))
            .build()

        val routeBytesGzip = ByteArrayOutputStream().use { byteOut ->
            GZIPOutputStream(byteOut).use { gzipOut -> routeProto.writeTo(gzipOut) }
            byteOut.toByteArray()
        }

        val routeAdvancedProto = ExerciseRouteSamples2.PbExerciseRouteSamples2.newBuilder()
            .addSyncPoint(ExerciseRouteSamples2.PbExerciseRouteSyncPoint.newBuilder()
                .setIndex(0)
                .setLocation(ExerciseRouteSamples2.PbLocationSyncPoint.newBuilder().setLatitude(60.17).setLongitude(24.94))
                .setGpsDateTime(Types.PbSystemDateTime.newBuilder()
                    .setDate(Types.PbDate.newBuilder().setYear(2024).setMonth(5).setDay(21))
                    .setTime(Types.PbTime.newBuilder().setHour(12).setMinute(0).setSeconds(0))
                    .setTrusted(true)))
            .addSatelliteAmount(7).addLatitude(100).addLongitude(200).addTimestamp(1000).addAltitude(50)
            .build()

        val routeAdvancedBytesGzip = ByteArrayOutputStream().use { byteOut ->
            GZIPOutputStream(byteOut).use { gzipOut -> routeAdvancedProto.writeTo(gzipOut) }
            byteOut.toByteArray()
        }

        val sampleProto = ExerciseSamples.PbExerciseSamples.newBuilder()
            .setRecordingInterval(Types.PbDuration.newBuilder().setMinutes(40))
            .addAltitudeSamples(100F).addAltitudeSamples(101F)
            .build()

        val sampleBytesGzip = ByteArrayOutputStream().use { byteOut ->
            GZIPOutputStream(byteOut).use { gzipOut -> sampleProto.writeTo(gzipOut) }
            byteOut.toByteArray()
        }

        val sampleAdvancedProto = ExerciseSamples2.PbExerciseSamples2.newBuilder()
            .addExerciseIntervalledSample2List(ExerciseSamples2.PbExerciseIntervalledSample2List.newBuilder()
                .setSampleType(Types.PbSampleType.SAMPLE_TYPE_HEART_RATE)
                .setRecordingIntervalMs(1000)
                .addAllHeartRateSamples(listOf(50, 60, 70))
                .build())
            .build()

        val sampleAdvancedBytesGzip = ByteArrayOutputStream().use { byteOut ->
            GZIPOutputStream(byteOut).use { gzipOut -> sampleAdvancedProto.writeTo(gzipOut) }
            byteOut.toByteArray()
        }

        val sessionBytes    = ByteArrayOutputStream().apply { mockProto.writeTo(this) }.toByteArray()
        val exerciseBytes   = ByteArrayOutputStream().apply { exerciseProto.writeTo(this) }.toByteArray()
        val routeBytes      = ByteArrayOutputStream().apply { routeProto.writeTo(this) }.toByteArray()
        val routeAdvancedBytes = ByteArrayOutputStream().apply { routeAdvancedProto.writeTo(this) }.toByteArray()
        val sampleBytes     = ByteArrayOutputStream().apply { sampleProto.writeTo(this) }.toByteArray()

        coEvery { client.request(any<ByteArray>()) } returnsMany listOf(
            ByteArrayOutputStream().apply { write(sessionBytes) },
            ByteArrayOutputStream().apply { write(exerciseBytes) },
            ByteArrayOutputStream().apply { write(routeBytes) },
            ByteArrayOutputStream().apply { write(routeBytesGzip) },
            ByteArrayOutputStream().apply { write(routeAdvancedBytes) },
            ByteArrayOutputStream().apply { write(routeAdvancedBytesGzip) },
            ByteArrayOutputStream().apply { write(sampleBytes) },
            ByteArrayOutputStream().apply { write(sampleBytesGzip) },
            ByteArrayOutputStream().apply { write(sampleAdvancedBytesGzip) }
        )

        // Act
        val trainingSession = PolarTrainingSessionUtils.readTrainingSession(client, reference)

        // Assert
        assertNotNull(trainingSession.sessionSummary)
        assertEquals(1, trainingSession.exercises.size)

        val sessionSummary = trainingSession.sessionSummary!!
        assertEquals("Polar 360", sessionSummary.modelName)
        assertEquals(3600, sessionSummary.duration.seconds)
        assertEquals(12.5f, sessionSummary.distance)
        assertEquals(400, sessionSummary.calories)

        val exercise = trainingSession.exercises[0]
        assertNotNull(exercise.exerciseSummary)
        assertNotNull(exercise.route)
        assertNotNull(exercise.routeAdvanced)
        assertNotNull(exercise.samples)
        assertNotNull(exercise.samplesAdvanced)

        val route = exercise.route!!
        assertEquals(60.17, route.latitudeList.first())
        assertEquals(24.94, route.longitudeList.first())
        assertEquals(7, route.satelliteAmountList.first())

        val routeAdvanced = exercise.routeAdvanced!!
        assertEquals(60.17, routeAdvanced.syncPointList.first().location.latitude)
        assertEquals(24.94, routeAdvanced.syncPointList.first().location.longitude)
        assertEquals(100, routeAdvanced.latitudeList.first())
        assertEquals(200, routeAdvanced.longitudeList.first())

        coVerify {
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath(reference.path).build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250101/E/101200/BASE.BPB").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250101/E/101200/ROUTE.BPB").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250101/E/101200/ROUTE.GZB").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250101/E/101200/ROUTE2.BPB").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250101/E/101200/ROUTE2.GZB").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250101/E/101200/SAMPLES.BPB").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250101/E/101200/SAMPLES.GZB").build().toByteArray())
            client.request(PftpRequest.PbPFtpOperation.newBuilder().setCommand(PftpRequest.PbPFtpOperation.Command.GET).setPath("/U/0/20250101/E/101200/SAMPLES2.GZB").build().toByteArray())
        }
        confirmVerified(client)
    }

    private fun buildDirectory(entries: List<JsonObject>): PbPFtpDirectory {
        return PbPFtpDirectory.newBuilder()
            .addAllEntries(entries.map { entry ->
                PbPFtpEntry.newBuilder()
                    .setName(entry.get("name").asString)
                    .setSize(entry.get("size").asLong)
                    .build()
            })
            .build()
    }

    private fun assertTrainingSessionReferences(caseId: String, actual: List<PolarTrainingSessionReference>, expected: JsonObject) {
        val expectedReferences = expected.getAsJsonArray("references").map { it.asJsonObject }
        assertEquals("$caseId reference count", expectedReferences.size, actual.size)
        expectedReferences.forEachIndexed { index, expectedReference ->
            val actualReference = actual[index]
            assertEquals("$caseId reference $index date", LocalDate.parse(expectedReference.get("date").asString), actualReference.date)
            assertEquals("$caseId reference $index path", expectedReference.get("path").asString, actualReference.path)
            assertEquals("$caseId reference $index fileSize", expectedReference.get("fileSize").asLong, actualReference.fileSize)
            assertEquals("$caseId reference $index trainingDataTypes", expectedReference.getAsJsonArray("trainingDataTypes").map { PolarTrainingSessionDataTypes.valueOf(it.asString) }, actualReference.trainingDataTypes)
            val expectedExercises = expectedReference.getAsJsonArray("exercises").map { it.asJsonObject }
            assertEquals("$caseId reference $index exercises", expectedExercises.size, actualReference.exercises.size)
            expectedExercises.forEachIndexed { exerciseIndex, expectedExercise ->
                val actualExercise = actualReference.exercises[exerciseIndex]
                assertEquals("$caseId exercise $exerciseIndex index", expectedExercise.get("index").asInt, actualExercise.index)
                assertEquals("$caseId exercise $exerciseIndex path", expectedExercise.get("androidPath").asString, actualExercise.path)
                assertEquals("$caseId exercise $exerciseIndex dataTypes", expectedExercise.getAsJsonArray("exerciseDataTypes").map { PolarExerciseDataTypes.valueOf(it.asString) }, actualExercise.exerciseDataTypes)
                assertEquals("$caseId exercise $exerciseIndex fileSizes", expectedExercise.getAsJsonObject("fileSizes").entrySet().associate { it.key to it.value.asLong }, actualExercise.fileSizes)
            }
        }
    }

    private fun buildTrainingSessionReference(fields: JsonObject, androidPaths: Boolean): PolarTrainingSessionReference {
        return PolarTrainingSessionReference(
            date = LocalDate.parse(fields.get("date").asString),
            path = fields.get("path").asString,
            trainingDataTypes = fields.getAsJsonArray("trainingDataTypes").map { PolarTrainingSessionDataTypes.valueOf(it.asString) },
            exercises = fields.getAsJsonArray("exercises").map { exercise ->
                val exerciseFields = exercise.asJsonObject
                PolarExercise(
                    index = exerciseFields.get("index").asInt,
                    path = exerciseFields.get(if (androidPaths) "androidPath" else "iosPath").asString,
                    exerciseDataTypes = exerciseFields.getAsJsonArray("exerciseDataTypes").map { PolarExerciseDataTypes.valueOf(it.asString) }
                )
            }
        )
    }

    private fun writeTrainingSessionFixture(responseType: String, outputStream: ByteArrayOutputStream) {
        when (responseType) {
            "trainingSessionSummary" -> TrainingSession.PbTrainingSession.newBuilder()
                .setStart(buildFixtureLocalDateTime())
                .setExerciseCount(1)
                .setModelName("Polar 360")
                .build()
                .writeTo(outputStream)
            "exerciseSummary" -> Training.PbExerciseBase.newBuilder()
                .setStart(buildFixtureLocalDateTime())
                .setDuration(Types.PbDuration.newBuilder().setSeconds(3600))
                .setWalkingDistance(1000f)
                .setSport(Structures.PbSportIdentifier.newBuilder().setValue(3))
                .build()
                .writeTo(outputStream)
            else -> error("Unknown training-session fixture response type $responseType")
        }
    }

    private fun buildFixtureLocalDateTime(): Types.PbLocalDateTime {
        return Types.PbLocalDateTime.newBuilder()
            .setDate(Types.PbDate.newBuilder().setYear(2025).setMonth(1).setDay(1))
            .setTime(Types.PbTime.newBuilder().setHour(10).setMinute(12).setSeconds(0))
            .setOBSOLETETrusted(true)
            .build()
    }

    private fun loadTrainingSessionVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/training-session")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
            .filterNot { vector -> vector.getAsJsonObject("input")?.get("kind")?.asString == "trainingSessionReadiness" }
    }

    private fun loadTrainingSessionReadinessManifest(): JsonObject {
        val vectorFile = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/training-session/training-session-readiness.json")
        FileReader(vectorFile).use { reader ->
            return JsonParser().parse(reader).asJsonObject
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

    private companion object {
        val TRAINING_SESSION_PAYLOAD_PARSER_CASE_IDS = listOf(
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

        const val TRAINING_SESSION_PAYLOAD_PARSER_COMMON_DECISION = "Before moving byte-level training payload parsing to common code, add production common protobuf and gzip dependencies that can execute these parser cases against real bytes; until then this vector is the shared parser ownership contract consumed by commonTest and pinned by Android/iOS byte-level characterization tests."
    }
}
