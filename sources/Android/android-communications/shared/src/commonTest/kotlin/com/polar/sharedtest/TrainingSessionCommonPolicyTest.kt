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
import kotlin.test.assertNotNull
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
        val readPlan = PolarTrainingSessionModels.payloadReadPlan(sharedReference)
        val result = PolarTrainingSessionModels.assemblePayloadReadResult(sharedReference, sharedResponses, fetchOrder)

        assertEquals(expected.stringArrayValue("fetchOrder"), fetchOrder, vector.stringValue("id"))
        val expectedReadPlan = expected.objectArray("readPlan")
        assertEquals(expectedReadPlan.map { it.stringValue("path") }, readPlan.map { it.path }, vector.stringValue("id"))
        assertEquals(expectedReadPlan.map { it.stringValue("fileName") }, readPlan.map { it.fileName }, vector.stringValue("id"))
        assertEquals(expectedReadPlan.map { it.stringValue("publicModelSlot") }, readPlan.map { it.publicModelSlot }, vector.stringValue("id"))
        assertEquals(expectedReadPlan.map { it.optionalIntValue("exerciseIndex") }, readPlan.map { it.exerciseIndex }, vector.stringValue("id"))
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
        val reconstructionPlan = PolarTrainingSessionModels.assemblePayloadReconstructionPlan(
            reference = sharedReference,
            decodedPayloadsByPath = syntheticDecodedPayloadsByPath(),
            fetchOrder = fetchOrder
        )
        val expectedReconstructionPlan = expected.objectArray("reconstructionPlan")
        val actualReconstructionEntries = listOfNotNull(reconstructionPlan.sessionSummary) + reconstructionPlan.exercises.flatMap { it.entries }
        assertEquals(expectedReconstructionPlan.map { it.stringValue("publicModelSlot") }, actualReconstructionEntries.map { it.publicModelSlot }, vector.stringValue("id"))
        assertEquals(expectedReconstructionPlan.map { it.optionalIntValue("exerciseIndex") }, actualReconstructionEntries.map { it.exerciseIndex }, vector.stringValue("id"))
        assertEquals(expectedReconstructionPlan.map { it.stringValue("fileName") }, actualReconstructionEntries.map { it.fileName }, vector.stringValue("id"))
        val actualSummaryEntry = assertNotNull(reconstructionPlan.sessionSummary, vector.stringValue("id"))
        val expectedSummaryNeutralFields = expectedReconstructionPlan.first { it.stringValue("publicModelSlot") == "sessionSummary" }.objectValue("neutralParsedFields")
        val actualSummaryNeutralFields = actualSummaryEntry.parsedPayload
        assertEquals(expectedSummaryNeutralFields.stringValue("modelName"), actualSummaryNeutralFields.modelName, vector.stringValue("id"))
        assertEquals(expectedSummaryNeutralFields.intValue("durationSeconds"), actualSummaryNeutralFields.durationSeconds, vector.stringValue("id"))
        assertEquals(expectedSummaryNeutralFields.intValue("distanceMeters"), actualSummaryNeutralFields.distanceMeters, vector.stringValue("id"))
        assertEquals(expectedSummaryNeutralFields.intValue("calories"), actualSummaryNeutralFields.calories, vector.stringValue("id"))
        assertEquals(listOf("ROUTE2.GZB", "SAMPLES.GZB"), reconstructionPlan.exercises.single().malformedFilesIgnored, vector.stringValue("id"))
        assertEquals("Polar 360", PolarTrainingSessionModels.parseDecodedPayloadResponse("TSESS.BPB", actualSummaryEntry.decodedPayload).payload.modelName, vector.stringValue("id"))
        assertEquals(listOf(120, 125, 130), PolarTrainingSessionModels.parseDecodedPayloadResponse("SAMPLES.BPB", reconstructionPlan.exercises.single().entries.first { it.publicModelSlot == "samples" }.decodedPayload).payload.heartRateSamples, vector.stringValue("id"))
        val commonDecision = vector.objectValue("platformExpectations").objectValue("commonDecision")
        assertEquals("compute-progress-from-reference-file-sizes-and-last-completed-file", commonDecision.stringValue("progressPolicy"), vector.stringValue("id"))
        assertEquals("omit-only-the-malformed-component-and-continue-reading-remaining-files", commonDecision.stringValue("malformedPayloadPolicy"), vector.stringValue("id"))
        assertEquals("ignore-unknown-advanced-sample-lists-and-preserve-known-samples", commonDecision.stringValue("unknownSampleListPolicy"), vector.stringValue("id"))
        assertEquals("shared-plan-selects-generated-model-slots-while-platforms-build-public-protobuf-objects", commonDecision.stringValue("publicModelReadPlanPolicy"), vector.stringValue("id"))
        assertEquals("shared-neutral-reconstruction-plan-selects-decoded-payload-bytes-for-platform-generated-model-adapters", commonDecision.stringValue("publicModelReconstructionPlanPolicy"), vector.stringValue("id"))
        assertEquals("shared-neutral-reconstruction-plan-carries-session-summary-scalar-fields-with-decoded-bytes", commonDecision.stringValue("neutralReconstructionFieldsPolicy"), vector.stringValue("id"))
    }

    @Test
    fun trainingSessionPayloadReadResultUsesSharedReadPlanExerciseIndexForUnpaddedAndroidPaths() {
        val reference = PolarTrainingSessionReference(
            dateTime = "2025-01-01T10:12:00",
            date = "2025-01-01",
            path = "/U/0/20250101/E/101200/TSESS.BPB",
            trainingDataTypes = listOf("TRAINING_SESSION_SUMMARY"),
            exercises = listOf(
                PolarTrainingExerciseReference(
                    index = 1,
                    androidPath = "/U/0/20250101/E/101200/1/BASE.BPB",
                    iosPath = "/U/0/20250101/E/101200/01",
                    exerciseDataTypes = listOf("SAMPLES"),
                    fileSizes = mapOf("SAMPLES.BPB" to 3L)
                )
            ),
            fileSize = 6L
        )
        val responses = mapOf(
            "/U/0/20250101/E/101200/TSESS.BPB" to PolarTrainingPayloadResponse(
                kind = "trainingSessionSummary",
                fileName = "TSESS.BPB",
                byteSize = 3,
                payload = PolarTrainingPayloadFields(modelName = "Polar 360")
            ),
            "/U/0/20250101/E/101200/1/SAMPLES.BPB" to PolarTrainingPayloadResponse(
                kind = "samples",
                fileName = "SAMPLES.BPB",
                byteSize = 3,
                payload = PolarTrainingPayloadFields(heartRateSamples = listOf(120, 121))
            )
        )

        val result = PolarTrainingSessionModels.assemblePayloadReadResult(reference, responses)

        assertEquals(1, result.exercises.single().index)
        assertEquals(listOf(120, 121), result.exercises.single().samplesHeartRate)
        assertEquals(100, result.progressPercent)
    }

    @Test
    fun trainingSessionProgressPercentUsesSharedClampPolicy() {
        assertEquals(0, PolarTrainingSessionModels.progressPercent(0, 0))
        assertEquals(25, PolarTrainingSessionModels.progressPercent(25, 100))
        assertEquals(100, PolarTrainingSessionModels.progressPercent(125, 100))
        assertEquals(0, PolarTrainingSessionModels.progressPercent(-5, 100))
        assertEquals(true, PolarTrainingSessionModels.referenceDateMatches("2024-02-29", "2024-02-28", "2024-03-01"))
        assertEquals(true, PolarTrainingSessionModels.referenceDateMatches("20240229", "20240229", null))
        assertEquals(false, PolarTrainingSessionModels.referenceDateMatches("2024-03-02", "2024-02-28", "2024-03-01"))
        assertEquals(false, PolarTrainingSessionModels.referenceDateMatches("2024-02-29", "2024-03-01", "2024-02-28"))
        assertEquals(false, PolarTrainingSessionModels.referenceDateMatches("2023-02-29", null, null))
    }

    @Test
    fun trainingSessionSelectedPayloadParserOwnershipKeepsGeneratedModelBoundaryExplicit() {
        val vector = loadGoldenVectorText("sdk/training-session/payload-read-policy.json")
        val commonDecision = vector.objectValue("platformExpectations").objectValue("commonDecision")

        assertEquals("selected-common-protobuf-field-parser-active-before-generated-model-reconstruction", commonDecision.stringValue("byteLevelParserGate"), vector.stringValue("id"))
        assertEquals("selected-protobuf-fields-parsed-in-common-generated-model-reconstruction-deferred", commonDecision.stringValue("byteLevelPayloadStatus"), vector.stringValue("id"))
        assertEquals("This neutral vector does not embed protobuf bytes; it turns the existing Android and iOS payload-read tests into a shared migration contract for request ordering, progress, parsed component presence, malformed component isolation, unknown advanced sample-list handling, and decoded-payload reconstruction-slot planning with neutral session-summary scalar fields carried beside the bytes for platform adapters. payload-parser-policy.json now pins parser-family ownership; gzip payload decompression, selected protobuf field parsing, malformed payload isolation, deterministic public-model read planning, neutral session-summary scalar DTO assembly, and neutral reconstruction planning are owned by shared KMP, while generated public protobuf object reconstruction remains platform-owned in Android and iOS adapters. Rollback for this parser-family slice is to ignore the neutral reconstruction fields/bridge columns and continue platform parsing from decoded payload bytes without moving generated public model construction into common KMP.", vector.stringValue("notes"), vector.stringValue("id"))
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
                publicModelSlot = testCase.stringValue("publicModelSlot"),
                fields = testCase.stringArrayValue("expectedFields")
            )
        }
        val expected = vector.objectValue("expected")
        val commonParserPrototype = expected.objectValue("commonParserPrototype")
        val expectedCaseList = commonParserPrototype.objectArray("cases")
        val expectedCases = expectedCaseList.associateBy { it.stringValue("id") }

        assertEquals(requiredPayloadParserCaseIds, parserCases.map { parserCase -> parserCase.id }, vector.stringValue("id"))
        assertEquals(requiredPayloadParserCaseIds, expectedCaseList.map { testCase -> testCase.stringValue("id") }, vector.stringValue("id"))
        assertEquals("executable shared parser-policy coverage; gzip decoding and selected protobuf field parsing are shared while generated public model reconstruction remains platform-owned", commonParserPrototype.stringValue("status"), vector.stringValue("id"))
        assertEquals("Selected training payload protobuf field parsing now executes in shared KMP for these parser cases; generated public protobuf object construction remains platform-owned while neutral reconstruction planning maps decoded payload bytes to Android and iOS adapters. Gzip decompression and public-model slot planning are shared KMP production code, and this vector remains the shared parser ownership contract consumed by commonTest and pinned by Android/iOS byte-level characterization tests.", expected.stringValue("commonDecision"), vector.stringValue("id"))
        assertEquals("This vector converts the existing platform byte-level protobuf coverage into an executable shared selected-field parser policy, while gzip payload decoding and public-model slot planning are already shared production code.", vector.stringValue("commonDecision"), vector.stringValue("id"))
        assertEquals("The Android and iOS tests construct real protobuf payloads for these parser families. Shared KMP production now parses selected summary, exercise, route, and sample fields with the portable training protobuf reader and plans neutral reconstruction slots, while platform adapters still build the generated public protobuf models.", vector.stringValue("notes"), vector.stringValue("id"))
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
            assertEquals(expected.stringValue("publicModelSlot"), parserCase.publicModelSlot, parserCase.id)
            assertEquals(expected.stringArrayValue("fields"), parserCase.fields, parserCase.id)
            assertEquals(parserCase.parser, planned.parser, parserCase.id)
            assertEquals(parserCase.encoding, planned.encoding, parserCase.id)
            assertEquals(parserCase.publicModelSlot, PolarTrainingSessionModels.publicModelSlot(parserCase.fileName), parserCase.id)
        }

        val gzipCases = parserCases.filter { parserCase -> parserCase.encoding == "gzip-protobuf" }
        assertEquals(4, gzipCases.size, vector.stringValue("id"))
        assertEquals(listOf("PbTrainingSession", "PbExerciseBase", "PbExerciseRouteSamples", "PbExerciseRouteSamples", "PbExerciseRouteSamples2", "PbExerciseRouteSamples2", "PbExerciseSamples", "PbExerciseSamples", "PbExerciseSamples2"), parserCases.map { parserCase -> parserCase.parser }, vector.stringValue("id"))
        assertEquals(listOf("protobuf", "protobuf", "protobuf", "gzip-protobuf", "protobuf", "gzip-protobuf", "protobuf", "gzip-protobuf", "gzip-protobuf"), parserCases.map { parserCase -> parserCase.encoding }, vector.stringValue("id"))
        assertEquals(listOf("sessionSummary", "exerciseSummary", "route", "route", "routeAdvanced", "routeAdvanced", "samples", "samples", "samplesAdvanced"), parserCases.map { parserCase -> parserCase.publicModelSlot }, vector.stringValue("id"))
        assertEquals(listOf("sampleType=HEART_RATE", "recordingIntervalMs=1000", "heartRateSamples=131,132,133"), expectedCases.getValue("samples-advanced-gzip-protobuf").stringArrayValue("fields"), vector.stringValue("id"))
    }

    @Test
    fun trainingSessionGzipPayloadDecodingUsesSharedCodecForGzipParserCases() {
        val compressed = hexToBytes("1f8b0800d7bd2a6a02ff2b294acccccbcc4bd72d4e2d2ececccfd34dafca2cd02d48acccc94f4c0100a58206c51d000000")
        val expected = hexToBytes("747261696e696e672d73657373696f6e2d677a69702d7061796c6f6164")

        assertEquals(expected.toHexString(), PolarTrainingSessionModels.decodePayloadBytes("ROUTE.GZB", compressed).toHexString())
        assertEquals(expected.toHexString(), PolarTrainingSessionModels.decodePayloadBytes("BASE.BPB", expected).toHexString())
    }

    @Test
    fun trainingSessionDecodedPayloadParserProjectsCommonSummaryFieldsFromProtobufBytes() {
        val payload = protobufBytes {
            stringField(4, "Polar 360")
            messageField(5) {
                varintField(1, 1)
                varintField(2, 2)
                varintField(3, 3)
            }
            fixed32Field(6, 12.0f.toBits())
            varintField(7, 400)
        }

        val response = PolarTrainingSessionModels.parseDecodedPayloadResponse("TSESS.BPB", payload)

        assertEquals("trainingSessionSummary", response.kind)
        assertEquals("Polar 360", response.payload.modelName)
        assertEquals(3723, response.payload.durationSeconds)
        assertEquals(12, response.payload.distanceMeters)
        assertEquals(400, response.payload.calories)
        assertEquals(false, response.malformed)
    }

    @Test
    fun trainingSessionDecodedPayloadParserProjectsSamplesAndAdvancedHeartRateFieldsFromProtobufBytes() {
        val samplesPayload = protobufBytes {
            packedVarintField(2, listOf(120, 125, 130))
        }
        val advancedPayload = protobufBytes {
            messageField(1) {
                varintField(1, 1)
                packedVarintField(5, listOf(zigZag32(131), zigZag32(132), zigZag32(133)))
            }
            messageField(1) {
                varintField(1, 3)
                packedVarintField(5, listOf(zigZag32(222)))
            }
        }

        val samples = PolarTrainingSessionModels.parseDecodedPayloadResponse("SAMPLES.BPB", samplesPayload)
        val advanced = PolarTrainingSessionModels.parseDecodedPayloadResponse("SAMPLES2.GZB", advancedPayload)

        assertEquals(listOf(120, 125, 130), samples.payload.heartRateSamples)
        assertEquals(
            listOf(
                PolarTrainingIntervalledSampleList("HEART_RATE", listOf(131, 132, 133)),
                PolarTrainingIntervalledSampleList("ALTITUDE", listOf(222))
            ),
            advanced.payload.intervalledSampleLists
        )
        assertEquals(false, samples.malformed)
        assertEquals(false, advanced.malformed)
    }

    @Test
    fun trainingSessionDecodedPayloadParserProjectsExerciseSummaryAndRouteFieldsFromProtobufBytes() {
        val exerciseSummaryPayload = protobufBytes {
            messageField(1) {
                messageField(2) {
                    varintField(1, 12)
                }
            }
            messageField(3) {
                varintField(1, 5)
            }
            fixed32Field(18, 10000.0f.toBits())
        }
        val routePayload = protobufBytes {
            fixed64Field(2, 10.0.toBits())
            fixed64Field(3, 20.0.toBits())
            packedVarintField(5, listOf(6))
        }
        val routeAdvancedPayload = protobufBytes {
            messageField(1) {
                varintField(1, 0)
                messageField(2) {
                    fixed64Field(1, 10.0.toBits())
                    fixed64Field(2, 20.0.toBits())
                }
            }
            packedVarintField(2, listOf(3))
            packedVarint64Field(3, listOf(zigZag64(100)))
            packedVarint64Field(4, listOf(zigZag64(200)))
        }

        val exerciseSummary = PolarTrainingSessionModels.parseDecodedPayloadResponse("BASE.BPB", exerciseSummaryPayload)
        val route = PolarTrainingSessionModels.parseDecodedPayloadResponse("ROUTE.BPB", routePayload)
        val routeAdvanced = PolarTrainingSessionModels.parseDecodedPayloadResponse("ROUTE2.BPB", routeAdvancedPayload)

        assertEquals(12, exerciseSummary.payload.startHour)
        assertEquals(5, exerciseSummary.payload.sport)
        assertEquals(10000, exerciseSummary.payload.walkingDistanceMeters)
        assertEquals(listOf(10.0), route.payload.latitude)
        assertEquals(listOf(20.0), route.payload.longitude)
        assertEquals(listOf(6), route.payload.satelliteAmount)
        assertEquals(listOf(0), routeAdvanced.payload.syncPointIndex)
        assertEquals(listOf(10.0), routeAdvanced.payload.syncPointLatitude)
        assertEquals(listOf(20.0), routeAdvanced.payload.syncPointLongitude)
        assertEquals(listOf(100L), routeAdvanced.payload.latitudeDeltas)
        assertEquals(listOf(200L), routeAdvanced.payload.longitudeDeltas)
        assertEquals(listOf(3), routeAdvanced.payload.satelliteAmount)
        assertEquals(false, exerciseSummary.malformed)
        assertEquals(false, route.malformed)
        assertEquals(false, routeAdvanced.malformed)
    }


    @Test
    fun trainingSessionDecodedPayloadParserMarksMalformedProtobufWithoutThrowing() {
        val malformed = byteArrayOf(((4 shl 3) or 2).toByte(), 5, 'P'.code.toByte())

        val response = PolarTrainingSessionModels.parseDecodedPayloadResponse("TSESS.BPB", malformed)

        assertEquals("trainingSessionSummary", response.kind)
        assertEquals(true, response.malformed)
        assertEquals(3, response.byteSize)
    }

    @Test
    fun trainingSessionNeutralDtoKeepsMissingOptionalFieldsOutOfCommonPublicModelConstruction() {
        val reference = payloadReference(
            """
            {
              "date": "2025-01-01",
              "path": "/U/0/20250101/E/101200/TSESS.BPB",
              "trainingDataTypes": ["TRAINING_SESSION_SUMMARY"],
              "exercises": [
                {
                  "index": 0,
                  "androidPath": "/U/0/20250101/E/101200/00/BASE.BPB",
                  "iosPath": "/U/0/20250101/E/101200/00",
                  "exerciseDataTypes": ["EXERCISE_SUMMARY"],
                  "fileSizes": {"BASE.BPB": 2}
                }
              ],
              "fileSize": 4
            }
            """.trimIndent()
        )
        val summaryPayload = protobufBytes {}
        val exercisePayload = protobufBytes {}
        val decodedPayloads = mapOf(
            reference.path to summaryPayload,
            "/U/0/20250101/E/101200/00/BASE.BPB" to exercisePayload
        )

        val summaryResponse = PolarTrainingSessionModels.parseDecodedPayloadResponse("TSESS.BPB", summaryPayload)
        val exerciseResponse = PolarTrainingSessionModels.parseDecodedPayloadResponse("BASE.BPB", exercisePayload)
        val plan = PolarTrainingSessionModels.assemblePayloadReconstructionPlan(reference, decodedPayloads, decodedPayloads.keys.toList())

        assertEquals("trainingSessionSummary", summaryResponse.kind)
        assertEquals(null, summaryResponse.payload.modelName)
        assertEquals(null, summaryResponse.payload.durationSeconds)
        assertEquals(false, summaryResponse.malformed)
        assertEquals("exerciseSummary", exerciseResponse.kind)
        assertEquals(null, exerciseResponse.payload.startHour)
        assertEquals(null, exerciseResponse.payload.sport)
        assertEquals(false, exerciseResponse.malformed)
        assertEquals(summaryPayload.toHexString(), plan.sessionSummary?.decodedPayload?.toHexString())
        assertEquals(listOf("exerciseSummary"), plan.exercises.single().entries.map { entry -> entry.publicModelSlot })
        assertEquals(exercisePayload.toHexString(), plan.exercises.single().entries.single().decodedPayload.toHexString())
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

    private fun syntheticDecodedPayloadsByPath(): Map<String, ByteArray> {
        return mapOf(
            "/U/0/20250101/E/101200/TSESS.BPB" to protobufBytes {
                stringField(4, "Polar 360")
                messageField(5) {
                    varintField(1, 1)
                }
                fixed32Field(6, 12.0f.toBits())
                varintField(7, 400)
            },
            "/U/0/20250101/E/101200/00/BASE.BPB" to protobufBytes {
                messageField(3) {
                    varintField(1, 3)
                }
            },
            "/U/0/20250101/E/101200/00/ROUTE.BPB" to protobufBytes {
                fixed64Field(2, 60.0.toBits())
                fixed64Field(3, 24.0.toBits())
            },
            "/U/0/20250101/E/101200/00/ROUTE.GZB" to protobufBytes {
                fixed64Field(2, 60.0.toBits())
                fixed64Field(3, 24.0.toBits())
            },
            "/U/0/20250101/E/101200/00/ROUTE2.BPB" to protobufBytes {
                messageField(1) {
                    varintField(1, 0)
                    messageField(2) {
                        fixed64Field(1, 61.0.toBits())
                        fixed64Field(2, 25.0.toBits())
                    }
                }
            },
            "/U/0/20250101/E/101200/00/ROUTE2.GZB" to byteArrayOf(((1 shl 3) or 2).toByte(), 5, 0x01),
            "/U/0/20250101/E/101200/00/SAMPLES.BPB" to protobufBytes {
                packedVarintField(2, listOf(120, 125, 130))
            },
            "/U/0/20250101/E/101200/00/SAMPLES.GZB" to byteArrayOf(((2 shl 3) or 2).toByte(), 5, 0x01),
            "/U/0/20250101/E/101200/00/SAMPLES2.GZB" to protobufBytes {
                messageField(1) {
                    varintField(1, 1)
                    packedVarintField(5, listOf(zigZag32(131), zigZag32(132), zigZag32(133)))
                }
            }
        )
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
        val publicModelSlot: String,
        val fields: List<String>
    )

    private class ProtobufBuilder {
        private val bytes = mutableListOf<Byte>()

        fun varintField(fieldNumber: Int, value: Int) {
            writeVarint(((fieldNumber shl 3) or 0).toLong())
            writeVarint(value.toLong())
        }

        fun stringField(fieldNumber: Int, value: String) {
            lengthDelimitedField(fieldNumber, value.encodeToByteArray().toList())
        }

        fun messageField(fieldNumber: Int, block: ProtobufBuilder.() -> Unit) {
            lengthDelimitedField(fieldNumber, ProtobufBuilder().apply(block).build().toList())
        }

        fun packedVarintField(fieldNumber: Int, values: List<Int>) {
            val packed = ProtobufBuilder().apply { values.forEach { writeVarint(it.toLong()) } }.build().toList()
            lengthDelimitedField(fieldNumber, packed)
        }

        fun packedVarint64Field(fieldNumber: Int, values: List<Long>) {
            val packed = ProtobufBuilder().apply { values.forEach { writeVarint(it) } }.build().toList()
            lengthDelimitedField(fieldNumber, packed)
        }

        fun fixed32Field(fieldNumber: Int, value: Int) {
            writeVarint(((fieldNumber shl 3) or 5).toLong())
            bytes += (value and 0xff).toByte()
            bytes += ((value ushr 8) and 0xff).toByte()
            bytes += ((value ushr 16) and 0xff).toByte()
            bytes += ((value ushr 24) and 0xff).toByte()
        }

        fun fixed64Field(fieldNumber: Int, value: Long) {
            writeVarint(((fieldNumber shl 3) or 1).toLong())
            for (index in 0 until 8) {
                bytes += ((value ushr (8 * index)) and 0xff).toByte()
            }
        }

        fun build(): ByteArray {
            return bytes.toByteArray()
        }

        private fun lengthDelimitedField(fieldNumber: Int, value: List<Byte>) {
            writeVarint(((fieldNumber shl 3) or 2).toLong())
            writeVarint(value.size.toLong())
            bytes += value
        }

        private fun writeVarint(value: Long) {
            var current = value
            while (true) {
                if ((current and 0x7f.inv().toLong()) == 0L) {
                    bytes += current.toByte()
                    return
                }
                bytes += (((current and 0x7f) or 0x80).toByte())
                current = current ushr 7
            }
        }
    }

    private fun protobufBytes(block: ProtobufBuilder.() -> Unit): ByteArray {
        return ProtobufBuilder().apply(block).build()
    }

    private fun zigZag32(value: Int): Int {
        return (value shl 1) xor (value shr 31)
    }

    private fun zigZag64(value: Long): Long {
        return (value shl 1) xor (value shr 63)
    }

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
            "selected-protobuf-field-parser-ownership",
            "shared-gzip-payload-codec",
            "public-model-read-plan",
            "generated-public-protobuf-construction-boundary",
            "platform-training-session-vector-reference-gate",
            "public-model-slot-planning",
            "public-generated-model-reconstruction-boundary",
            "compile-verification-gate"
        )
        const val TRAINING_SESSION_MISSING_EXERCISE_FILE_COMMON_DECISION = "Android currently returns a partial exercise when an exercise data file request fails; iOS currently propagates the request failure. Choose an explicit shared policy before moving training-session read orchestration to KMP."
        const val TRAINING_SESSION_READINESS_COMMON_DECISION = "Training-session migration may proceed only after every vector named by this readiness manifest is executable from shared commonTest, Android and iOS training-session tests continue to reference the same vectors, directory traversal, summary discovery, exercise classification, unknown-file ignoring, aggregate size, exercise path policy, missing exercise-file policy, payload fetch order, progress, malformed component isolation, unknown advanced sample-list handling, known sample preservation, parser-family ownership, shared gzip payload decoding, shared selected protobuf field parsing, shared public-model read planning, shared public-model slot planning, generated public protobuf construction boundaries, public generated-model reconstruction boundaries, and compile verification remain explicit before production discovery/read orchestration moves."
    }
}
