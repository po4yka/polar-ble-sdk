package com.polar.sdk.impl.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import org.junit.Assert
import org.junit.Test
import protocol.PftpNotification
import protocol.PftpRequest
import java.io.File
import java.io.FileReader

class PolarRuntimePlannerAdapterTest {
    @Test
    fun `shared available data type planner preserves Android public data type surface`() {
        val features = setOf(
            PmdMeasurementType.ECG,
            PmdMeasurementType.ACC,
            PmdMeasurementType.PPG,
            PmdMeasurementType.PPI,
            PmdMeasurementType.GYRO,
            PmdMeasurementType.MAGNETOMETER,
            PmdMeasurementType.PRESSURE,
            PmdMeasurementType.LOCATION,
            PmdMeasurementType.TEMPERATURE,
            PmdMeasurementType.SKIN_TEMP,
            PmdMeasurementType.OFFLINE_HR
        )

        Assert.assertEquals(
            setOf(
                PolarDeviceDataType.ECG,
                PolarDeviceDataType.ACC,
                PolarDeviceDataType.PPG,
                PolarDeviceDataType.PPI,
                PolarDeviceDataType.GYRO,
                PolarDeviceDataType.MAGNETOMETER,
                PolarDeviceDataType.PRESSURE,
                PolarDeviceDataType.LOCATION,
                PolarDeviceDataType.TEMPERATURE,
                PolarDeviceDataType.SKIN_TEMPERATURE,
                PolarDeviceDataType.HR
            ),
            PolarRuntimePlannerAdapter.availableOfflineRecordingDataTypes(features)
        )
        Assert.assertEquals(
            setOf(
                PolarDeviceDataType.HR,
                PolarDeviceDataType.ECG,
                PolarDeviceDataType.ACC,
                PolarDeviceDataType.PPG,
                PolarDeviceDataType.PPI,
                PolarDeviceDataType.GYRO,
                PolarDeviceDataType.MAGNETOMETER,
                PolarDeviceDataType.PRESSURE,
                PolarDeviceDataType.LOCATION,
                PolarDeviceDataType.TEMPERATURE,
                PolarDeviceDataType.SKIN_TEMPERATURE
            ),
            PolarRuntimePlannerAdapter.availableOnlineStreamDataTypes(features, hasHrService = true)
        )
        Assert.assertFalse(PolarRuntimePlannerAdapter.availableOnlineStreamDataTypes(features, hasHrService = false).contains(PolarDeviceDataType.HR))
        Assert.assertEquals(setOf(PolarDeviceDataType.HR), PolarRuntimePlannerAdapter.availableHrServiceDataTypes(hasHrService = true))
        Assert.assertEquals(emptySet<PolarDeviceDataType>(), PolarRuntimePlannerAdapter.availableHrServiceDataTypes(hasHrService = false))
    }

    @Test
    fun `shared GNSS location projection routes through Android runtime adapter DTOs`() {
        val summary = PolarRuntimePlannerAdapter.PlannedGnssSatelliteSummary(
            gpsNbrOfSat = 1u,
            gpsMaxSnr = 2u,
            glonassNbrOfSat = 3u,
            glonassMaxSnr = 4u,
            galileoNbrOfSat = 5u,
            galileoMaxSnr = 6u,
            beidouNbrOfSat = 7u,
            beidouMaxSnr = 8u,
            nbrOfSat = 9u,
            snrTop5Avg = 10u
        )
        val projection = PolarRuntimePlannerAdapter.locationDataProjection(
            listOf(
                PolarRuntimePlannerAdapter.PlannedGnssCoordinateSample(
                    timeStamp = 101uL,
                    latitude = 60.123,
                    longitude = 24.456,
                    date = "2026-06-06T10:11:12.123",
                    cumulativeDistance = 12.3,
                    speed = 4.5f,
                    usedAccelerationSpeed = 5.6f,
                    coordinateSpeed = 6.7f,
                    accelerationSpeedFactor = 7.8f,
                    course = 8.9f,
                    gpsChipSpeed = 9.1f,
                    fix = true,
                    speedFlag = -1,
                    fusionState = 255u
                ),
                PolarRuntimePlannerAdapter.PlannedGnssNmeaSample(
                    timeStamp = 202uL,
                    measurementPeriod = 1000u,
                    messageLength = 12u,
                    statusFlags = 3u,
                    nmeaMessage = "GPGGA"
                ),
                PolarRuntimePlannerAdapter.PlannedGnssSatelliteDilutionSample(
                    timeStamp = 303uL,
                    dilution = 1.25f,
                    altitude = -42,
                    numberOfSatellites = 7u,
                    fix = false
                ),
                PolarRuntimePlannerAdapter.PlannedGnssSatelliteSummarySample(
                    timeStamp = 404uL,
                    seenGnssSatelliteSummaryBand1 = summary,
                    usedGnssSatelliteSummaryBand1 = summary,
                    seenGnssSatelliteSummaryBand2 = summary,
                    usedGnssSatelliteSummaryBand2 = summary,
                    maxSnr = 99u
                )
            )
        )

        Assert.assertTrue(projection[0] is PolarRuntimePlannerAdapter.PlannedLocationCoordinatesProjectionSample)
        Assert.assertTrue(projection[1] is PolarRuntimePlannerAdapter.PlannedLocationNmeaProjectionSample)
        Assert.assertTrue(projection[2] is PolarRuntimePlannerAdapter.PlannedLocationSatelliteDilutionProjectionSample)
        Assert.assertTrue(projection[3] is PolarRuntimePlannerAdapter.PlannedLocationSatelliteSummaryProjectionSample)
        val coordinate = projection[0] as PolarRuntimePlannerAdapter.PlannedLocationCoordinatesProjectionSample
        Assert.assertEquals("2026-06-06T10:11:12.123", coordinate.time)
        val satelliteSummary = projection[3] as PolarRuntimePlannerAdapter.PlannedLocationSatelliteSummaryProjectionSample
        Assert.assertEquals(99u, satelliteSummary.maxSnr)
        Assert.assertEquals(10u.toUByte(), satelliteSummary.seenSatelliteSummaryBand1.snrTop5Avg)
    }

    @Test
    fun `available data types readiness manifest is pinned before availability migration`() {
        val vector = loadAvailableDataTypesReadinessVector()
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")

        Assert.assertEquals("available-data-types-readiness", vector.get("id").asString)
        Assert.assertEquals("availableDataTypesReadiness", input.get("kind").asString)
        Assert.assertEquals(AVAILABLE_DATA_TYPES_BEHAVIOR_FAMILIES, input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString })
        Assert.assertEquals("coveredByPreMigrationCharacterization", expected.get("migrationReadiness").asString)
        Assert.assertEquals(AVAILABLE_DATA_TYPES_BEHAVIOR_FAMILIES, expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString })
        Assert.assertEquals(AVAILABLE_DATA_TYPES_COMMON_DECISION, expected.get("commonDecision").asString)
        val prototype = expected.getAsJsonObject("commonRuntimePrototype")
        Assert.assertEquals("executable shared commonTest available-data-types planning guard", prototype.get("status").asString)
        Assert.assertEquals("Declared because this vector is consumed by shared commonTest and platform adapter tests before available-data-types runtime delegation moves further into shared KMP.", prototype.get("reason").asString)
        Assert.assertEquals(listOf("com.polar.sdk.impl.utils.PolarRuntimePlannerAdapterTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        Assert.assertEquals(listOf("PolarDataUtilsTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        Assert.assertEquals(listOf("com.polar.sharedtest.AvailableDataTypesCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun `atomic set delegate routes through Android runtime adapter`() {
        val set = PolarRuntimePlannerAdapter.PlannedAtomicSet<String>()
        val visited = mutableListOf<String>()

        Assert.assertTrue(set.add("first"))
        Assert.assertTrue(set.add("second"))
        Assert.assertFalse(set.add("first"))
        Assert.assertFalse(set.add(null))
        set.accessAll { visited += it }

        Assert.assertEquals(listOf("second", "first"), visited)
        Assert.assertEquals("second", set.fetch { it.startsWith("s") })
        Assert.assertEquals(setOf("first", "second"), set.objects())
        Assert.assertEquals(2, set.size())
        Assert.assertTrue(set.contains("first"))
        set.remove("first")
        Assert.assertFalse(set.contains("first"))
        set.clear()
        Assert.assertEquals(0, set.size())
    }

    @Test
    fun `stream runtime helpers expose Android adapter primitives`() {
        Assert.assertEquals(1, PolarRuntimePlannerAdapter.streamCheckedSubscriptionActiveObserverCount("stream", startConnected = true, checkConnection = true))
        Assert.assertEquals(1, PolarRuntimePlannerAdapter.streamCheckedSubscriptionActiveObserverCount("stream", startConnected = false, checkConnection = false))
        Assert.assertEquals(0, PolarRuntimePlannerAdapter.streamCheckedSubscriptionActiveObserverCount("stream", startConnected = false, checkConnection = true))

        PolarRuntimePlannerAdapter.streamDisconnectAfterSubscription("stream", "BleDisconnected")
        PolarRuntimePlannerAdapter.streamPostCompletionEmissionSuppression("stream", "value")
        PolarRuntimePlannerAdapter.streamDuplicateCompletion("stream")
        PolarRuntimePlannerAdapter.streamConsumerCancellation("stream")
    }

    @Test
    fun `feature availability readiness vector uses shared Android runtime planner adapter`() {
        val vector = loadFeatureAvailabilityReadinessVector()
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val cases = input.getAsJsonArray("cases").map { it.asJsonObject }
        val discoveredServices = PolarRuntimePlannerAdapter.featureAvailabilityServiceNames(
            hasHr = true,
            hasDeviceInfo = true,
            hasBattery = true,
            hasPmd = true,
            hasPsftp = true,
            hasHts = true,
            hasPfc = true
        )
        val capabilities = PolarRuntimePlannerAdapter.featureAvailabilityCapabilityNames(
            recordingSupported = true,
            activityDataSupported = true,
            firmwareUpdateSupported = true,
            h10FileSystem = true,
            notSensor = true
        )

        Assert.assertEquals("feature-availability-readiness", vector.get("id").asString)
        Assert.assertEquals("featureAvailabilityReadiness", input.get("kind").asString)
        Assert.assertEquals(listOf("ACTIVITY_DATA", "FIRMWARE_UPDATE", "H10_FILE_SYSTEM", "NOT_SENSOR", "RECORDING").sorted(), capabilities.sorted())
        Assert.assertEquals(listOf("BATTERY", "DEVICE_INFO", "HR", "HTS", "PFC", "PMD", "PSFTP").sorted(), discoveredServices.sorted())
        Assert.assertEquals("H10", PolarRuntimePlannerAdapter.deviceModelNameFromLocalName("Polar H10 12345678"))
        Assert.assertEquals(FEATURE_AVAILABILITY_CASE_IDS, cases.map { it.get("id").asString })
        cases.forEach { case ->
            Assert.assertEquals(
                case.get("id").asString,
                case.get("expectedAvailable").asBoolean,
                PolarRuntimePlannerAdapter.featureAvailabilityPreconditionsMet(
                    featureName = case.get("featureName").asString,
                    discoveredServices = case.getAsJsonArray("discoveredServices").map { it.asString }.toSet(),
                    capabilities = case.getAsJsonArray("capabilities").map { it.asString }.toSet()
                )
            )
        }
        Assert.assertEquals(FEATURE_AVAILABILITY_BEHAVIOR_FAMILIES, input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString })
        Assert.assertEquals(FEATURE_AVAILABILITY_BEHAVIOR_FAMILIES, expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString })
        Assert.assertEquals(FEATURE_AVAILABILITY_COMMON_DECISION, expected.get("commonDecision").asString)
    }

    @Test
    fun `PMD control point enum helpers route through Android runtime adapter`() {
        val response = PolarRuntimePlannerAdapter.pmdControlPointResponse(
            byteArrayOf(
                0xF0.toByte(),
                0x02.toByte(),
                0x00.toByte(),
                0x00.toByte(),
                0x01.toByte(),
                0x7F.toByte()
            )
        )

        Assert.assertEquals("ECG", PolarRuntimePlannerAdapter.pmdMeasurementTypeNameFromMaskedId(0x00.toByte()))
        Assert.assertEquals("MAG", PolarRuntimePlannerAdapter.pmdMeasurementTypeNameFromMaskedId(0x06.toByte()))
        Assert.assertNull(PolarRuntimePlannerAdapter.pmdMeasurementTypeNameFromMaskedId(0x3F.toByte()))
        Assert.assertEquals(0, PolarRuntimePlannerAdapter.pmdRecordingTypeBitField("ONLINE"))
        Assert.assertEquals(0x80, PolarRuntimePlannerAdapter.pmdRecordingTypeBitField("OFFLINE"))
        Assert.assertEquals(3, PolarRuntimePlannerAdapter.pmdActiveMeasurementBits(0xC0.toByte()))
        Assert.assertEquals(0xF0, response.responseCode)
        Assert.assertEquals(2, response.opCodeValue)
        Assert.assertEquals(0, response.measurementType)
        Assert.assertEquals(0, response.statusValue)
        Assert.assertEquals(true, response.more)
        Assert.assertArrayEquals(byteArrayOf(0x7F.toByte()), response.parameters)
    }

    @Test
    fun `PMD secret helpers route through Android runtime adapter`() {
        val aesKey = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF.toByte())
        val aesCipher = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte()
        )

        Assert.assertArrayEquals(byteArrayOf(0x06, 0x01, 0x00), PolarRuntimePlannerAdapter.pmdSecretSerializeBytes("NONE", byteArrayOf()))
        Assert.assertArrayEquals(byteArrayOf(0x06, 0x01, 0x01, 0x55), PolarRuntimePlannerAdapter.pmdSecretSerializeBytes("XOR", byteArrayOf(0x55)))
        Assert.assertArrayEquals(byteArrayOf(0x55, 0x54, 0x57, 0x56), PolarRuntimePlannerAdapter.pmdSecretDecryptBytes("XOR", byteArrayOf(0x55), byteArrayOf(0x00, 0x01, 0x02, 0x03)))
        Assert.assertArrayEquals(aesCipher, PolarRuntimePlannerAdapter.pmdSecretDecryptBytes("NONE", byteArrayOf(), aesCipher))
        Assert.assertArrayEquals(
            byteArrayOf(0x60.toByte(), 0x08.toByte(), 0x6b.toByte(), 0xda.toByte(), 0x00.toByte(), 0xdb.toByte(), 0x42.toByte(), 0x62.toByte(), 0x34.toByte(), 0x60.toByte(), 0x27.toByte(), 0x43.toByte(), 0x71.toByte(), 0xa7.toByte(), 0x53.toByte(), 0x68.toByte()),
            PolarRuntimePlannerAdapter.pmdSecretDecryptBytes("AES128", aesKey, aesCipher)
        )
        Assert.assertNull(PolarRuntimePlannerAdapter.pmdSecretDecryptBytes("AES128", aesKey, byteArrayOf(0x01)))
        Assert.assertEquals("NONE", PolarRuntimePlannerAdapter.pmdSecretStrategyNameFromByte(0))
        Assert.assertEquals("AES256", PolarRuntimePlannerAdapter.pmdSecretStrategyNameFromByte(3))
        Assert.assertNull(PolarRuntimePlannerAdapter.pmdSecretStrategyNameFromByte(0xFF))
    }

    @Test
    fun `type conversion helpers route through Android runtime adapter`() {
        Assert.assertEquals(255.toUByte(), PolarRuntimePlannerAdapter.convertArrayToUnsignedByte(byteArrayOf(0xFF.toByte())))
        Assert.assertEquals(0x78563412u, PolarRuntimePlannerAdapter.convertArrayToUnsignedInt(byteArrayOf(0x12, 0x34, 0x56, 0x78)))
        Assert.assertEquals(0x5634u, PolarRuntimePlannerAdapter.convertArrayToUnsignedInt(byteArrayOf(0x12, 0x34, 0x56, 0x78), offset = 1, length = 2))
        Assert.assertEquals(0x807060504030201uL, PolarRuntimePlannerAdapter.convertArrayToUnsignedLong(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)))
        Assert.assertEquals(0x060504uL, PolarRuntimePlannerAdapter.convertArrayToUnsignedLong(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08), offset = 3, length = 3))
        Assert.assertEquals(-128, PolarRuntimePlannerAdapter.convertArrayToSignedInt(byteArrayOf(0x80.toByte())))
        Assert.assertEquals(-32768, PolarRuntimePlannerAdapter.convertArrayToSignedInt(byteArrayOf(0x00, 0x80.toByte())))
        Assert.assertEquals(-1, PolarRuntimePlannerAdapter.convertArrayToSignedInt(byteArrayOf(0x00, 0x00, 0x00, 0x80.toByte())))
        Assert.assertEquals(127, PolarRuntimePlannerAdapter.convertArrayToSignedInt(byteArrayOf(0x01, 0x7F, 0x00), offset = 1, length = 1))
        Assert.assertEquals(255, PolarRuntimePlannerAdapter.convertUnsignedByteToInt(0xFF.toByte()))
    }

    @Test
    fun `shared command query plans select Android protobuf query ids`() {
        val cases = listOf(
            "h10-start-recording" to ("REQUEST_START_RECORDING" to PftpRequest.PbPFtpQuery.REQUEST_START_RECORDING_VALUE),
            "h10-stop-recording" to ("REQUEST_STOP_RECORDING" to PftpRequest.PbPFtpQuery.REQUEST_STOP_RECORDING_VALUE),
            "h10-recording-status" to ("REQUEST_RECORDING_STATUS" to PftpRequest.PbPFtpQuery.REQUEST_RECORDING_STATUS_VALUE),
            "live-exercise-start" to ("START_EXERCISE" to PftpRequest.PbPFtpQuery.START_EXERCISE_VALUE),
            "live-exercise-pause" to ("PAUSE_EXERCISE" to PftpRequest.PbPFtpQuery.PAUSE_EXERCISE_VALUE),
            "live-exercise-resume" to ("RESUME_EXERCISE" to PftpRequest.PbPFtpQuery.RESUME_EXERCISE_VALUE),
            "live-exercise-stop" to ("STOP_EXERCISE" to PftpRequest.PbPFtpQuery.STOP_EXERCISE_VALUE),
            "live-exercise-status" to ("GET_EXERCISE_STATUS" to PftpRequest.PbPFtpQuery.GET_EXERCISE_STATUS_VALUE),
            "firmware-prepare-update" to ("PREPARE_FIRMWARE_UPDATE" to PftpRequest.PbPFtpQuery.PREPARE_FIRMWARE_UPDATE_VALUE)
        )

        cases.forEach { (id, queryAndValue) ->
            val (query, expectedValue) = queryAndValue
            val plan = PolarRuntimePlannerAdapter.planCommandQuery(id, query)

            Assert.assertEquals(listOf("query:$query", "parameters:none"), plan.commands)
            Assert.assertEquals(expectedValue, PolarRuntimePlannerAdapter.queryValue(plan))
        }
    }

    @Test
    fun `shared disk time plans select Android protobuf query ids`() {
        val getLocalTime = PolarRuntimePlannerAdapter.planDiskTimeQuery("get-local-time", "GET_LOCAL_TIME")
        val getDiskSpace = PolarRuntimePlannerAdapter.planDiskTimeQuery("get-disk-space", "GET_DISK_SPACE")
        val setLocalTime = PolarRuntimePlannerAdapter.planSetLocalTimeH10(localTimeHour = 14)
        val setLocalTimeV2 = PolarRuntimePlannerAdapter.planSetLocalTimeV2(systemTimeHour = 10, localTimeHour = 12)

        Assert.assertEquals(PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE, PolarRuntimePlannerAdapter.queryValue(getLocalTime))
        Assert.assertEquals(PftpRequest.PbPFtpQuery.GET_DISK_SPACE_VALUE, PolarRuntimePlannerAdapter.queryValue(getDiskSpace))
        Assert.assertEquals(PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE, PolarRuntimePlannerAdapter.queryValue(setLocalTime))
        Assert.assertEquals(listOf("SET_SYSTEM_TIME", "SET_LOCAL_TIME"), PolarRuntimePlannerAdapter.queryNames(setLocalTimeV2))
        Assert.assertEquals(
            listOf(
                PftpRequest.PbPFtpQuery.SET_SYSTEM_TIME_VALUE,
                PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE
            ),
            PolarRuntimePlannerAdapter.queryNames(setLocalTimeV2).map(PolarRuntimePlannerAdapter::queryValue)
        )
    }

    @Test
    fun `shared basic date range planner preserves Android inclusive date iteration`() {
        Assert.assertEquals(
            listOf("20240228", "20240229", "20240301"),
            PolarRuntimePlannerAdapter.basicDateRange("20240228", "20240301")
        )
        Assert.assertEquals(
            listOf("20261231", "20270101"),
            PolarRuntimePlannerAdapter.basicDateRange("20261231", "20270101")
        )
        Assert.assertEquals(emptyList<String>(), PolarRuntimePlannerAdapter.basicDateRange("20240302", "20240301"))
        Assert.assertEquals(emptyList<String>(), PolarRuntimePlannerAdapter.basicDateRange("20230229", "20230301"))
        Assert.assertTrue(PolarRuntimePlannerAdapter.trainingSessionReferenceDateMatches("2024-02-29", "2024-02-28", "2024-03-01"))
        Assert.assertFalse(PolarRuntimePlannerAdapter.trainingSessionReferenceDateMatches("2024-03-02", "2024-02-28", "2024-03-01"))
    }

    @Test
    fun `shared training session references route through Android runtime adapter DTOs`() {
        val references = PolarRuntimePlannerAdapter.trainingSessionReferences(
            listOf(
                PolarRuntimePlannerAdapter.PlannedTrainingSessionFileEntry("/U/0/20260102/E/123456/TSESS.BPB", 10L),
                PolarRuntimePlannerAdapter.PlannedTrainingSessionFileEntry("/U/0/20260102/E/123456/00/BASE.BPB", 20L),
                PolarRuntimePlannerAdapter.PlannedTrainingSessionFileEntry("/U/0/20260102/E/123456/00/ROUTE.GZB", 30L)
            )
        )
        val reference = references.single()

        Assert.assertEquals("2026-01-02", reference.date)
        Assert.assertEquals("/U/0/20260102/E/123456/TSESS.BPB", reference.path)
        Assert.assertEquals(listOf("TRAINING_SESSION_SUMMARY"), reference.trainingDataTypes)
        Assert.assertEquals(60L, reference.fileSize)
        Assert.assertEquals(listOf("EXERCISE_SUMMARY", "ROUTE_GZIP"), reference.exercises.single().exerciseDataTypes)
        Assert.assertEquals(
            listOf(
                "/U/0/20260102/E/123456/TSESS.BPB",
                "/U/0/20260102/E/123456/00/BASE.BPB",
                "/U/0/20260102/E/123456/00/ROUTE.GZB"
            ),
            PolarRuntimePlannerAdapter.trainingSessionPayloadFetchOrder(reference)
        )
    }

    @Test
    fun `shared training session payload read result routes through Android runtime adapter DTOs`() {
        val parsedSamples = PolarRuntimePlannerAdapter.parseTrainingSessionPayloadResponse("SAMPLES.BPB", byteArrayOf(0x10, 0x78))
        val reference = PolarRuntimePlannerAdapter.PlannedTrainingSessionReference(
            dateTime = "2026-01-02T12:34:56",
            date = "2026-01-02",
            path = "/U/0/20260102/E/123456/TSESS.BPB",
            trainingDataTypes = listOf("TRAINING_SESSION_SUMMARY"),
            exercises = listOf(
                PolarRuntimePlannerAdapter.PlannedTrainingExerciseReference(
                    index = 0,
                    androidPath = "/U/0/20260102/E/123456/00/BASE.BPB",
                    iosPath = "/U/0/20260102/E/123456/00",
                    exerciseDataTypes = listOf("EXERCISE_SUMMARY", "SAMPLES", "SAMPLES_ADVANCED_FORMAT_GZIP"),
                    fileSizes = mapOf("BASE.BPB" to 5L, "SAMPLES.BPB" to 4L, "SAMPLES2.GZB" to 6L)
                )
            ),
            fileSize = 25L
        )
        val responses = mapOf(
            "/U/0/20260102/E/123456/TSESS.BPB" to PolarRuntimePlannerAdapter.PlannedTrainingPayloadResponse(
                kind = "trainingSessionSummary",
                fileName = "TSESS.BPB",
                byteSize = 10,
                modelName = "Polar 360",
                durationSeconds = 123,
                distanceMeters = 456,
                calories = 78
            ),
            "/U/0/20260102/E/123456/00/BASE.BPB" to PolarRuntimePlannerAdapter.PlannedTrainingPayloadResponse(
                kind = "exerciseSummary",
                fileName = "BASE.BPB",
                byteSize = 5
            ),
            "/U/0/20260102/E/123456/00/SAMPLES.BPB" to PolarRuntimePlannerAdapter.PlannedTrainingPayloadResponse(
                kind = "samples",
                fileName = "SAMPLES.BPB",
                byteSize = 4,
                heartRateSamples = listOf(120, 121)
            ),
            "/U/0/20260102/E/123456/00/SAMPLES2.GZB" to PolarRuntimePlannerAdapter.PlannedTrainingPayloadResponse(
                kind = "samplesAdvancedGzip",
                fileName = "SAMPLES2.GZB",
                byteSize = 6,
                intervalledSampleLists = listOf(
                    PolarRuntimePlannerAdapter.PlannedTrainingIntervalledSampleList("HEART_RATE", listOf(130, 131)),
                    PolarRuntimePlannerAdapter.PlannedTrainingIntervalledSampleList("CADENCE", listOf(90))
                )
            )
        )

        val result = PolarRuntimePlannerAdapter.trainingSessionPayloadReadResult(reference, responses)
        val exercise = result.exercises.single()

        Assert.assertEquals("samples", parsedSamples.kind)
        Assert.assertEquals("SAMPLES.BPB", parsedSamples.fileName)
        Assert.assertEquals(2, parsedSamples.byteSize)
        Assert.assertEquals(listOf(120), parsedSamples.heartRateSamples)
        Assert.assertEquals(25, result.totalBytes)
        Assert.assertEquals(25, result.completedBytes)
        Assert.assertEquals(100, result.progressPercent)
        Assert.assertEquals("SAMPLES2.GZB", result.currentFileName)
        Assert.assertEquals("Polar 360", result.modelName)
        Assert.assertEquals(123, result.durationSeconds)
        Assert.assertEquals(456, result.distanceMeters)
        Assert.assertEquals(78, result.calories)
        Assert.assertEquals(0, exercise.index)
        Assert.assertTrue(exercise.exerciseSummaryPresent)
        Assert.assertEquals(listOf(120, 121), exercise.samplesHeartRate)
        Assert.assertEquals(listOf(130, 131), exercise.samplesAdvancedHeartRate)
        Assert.assertEquals(1, exercise.unknownAdvancedSampleListsIgnored)
    }

    @Test
    fun `shared time field policy routes through Android runtime adapter`() {
        val fields = PolarRuntimePlannerAdapter.dateTimeFields(
            year = 2026,
            month = 6,
            day = 7,
            hour = 12,
            minute = 34,
            second = 56,
            millis = 789,
            timeZoneOffsetMinutes = 240,
            trusted = true
        )

        Assert.assertEquals(2026, fields.date.year)
        Assert.assertEquals(6, fields.date.month)
        Assert.assertEquals(7, fields.date.day)
        Assert.assertEquals(12, fields.time.hour)
        Assert.assertEquals(34, fields.time.minute)
        Assert.assertEquals(56, fields.time.second)
        Assert.assertEquals(789, fields.time.millis)
        Assert.assertEquals(240, fields.timeZoneOffsetMinutes)
        Assert.assertTrue(fields.trusted)
        Assert.assertEquals(123_000_000, PolarRuntimePlannerAdapter.millisToNanos(123))
        Assert.assertEquals(2, PolarRuntimePlannerAdapter.secondsToMinutes(120))
        Assert.assertEquals(3_723_004, PolarRuntimePlannerAdapter.durationMillis(hours = 1, minutes = 2, seconds = 3, millis = 4))
    }

    @Test
    fun `shared identifier classification preserves neutral routing categories`() {
        Assert.assertEquals("deviceId", PolarRuntimePlannerAdapter.identifierClassification("E123456F"))
        Assert.assertEquals("deviceId", PolarRuntimePlannerAdapter.identifierClassification("123456"))
        Assert.assertEquals("platformSpecific", PolarRuntimePlannerAdapter.identifierClassification("00:11:22:33:44:55"))
        Assert.assertEquals("platformSpecific", PolarRuntimePlannerAdapter.identifierClassification("123E4567-E89B-12D3-A456-426614174000"))
        Assert.assertEquals("invalid", PolarRuntimePlannerAdapter.identifierClassification("not_a_valid_id"))
    }

    @Test
    fun `shared reset and sync plans select Android protobuf notification ids`() {
        val reset = PolarRuntimePlannerAdapter.planCommandReset("factory-reset", sleep = false, factoryDefaults = true, otaFirmwareUpdate = false)
        val syncStart = PolarRuntimePlannerAdapter.planCommandSyncStart("sync-start-success")
        val syncStop = PolarRuntimePlannerAdapter.planCommandSyncStop("sync-stop-success")

        Assert.assertEquals(
            listOf(PftpNotification.PbPFtpHostToDevNotification.RESET_VALUE),
            PolarRuntimePlannerAdapter.notificationNames(reset).map(PolarRuntimePlannerAdapter::notificationValue)
        )
        val resetFields = PolarRuntimePlannerAdapter.resetNotificationFields("factory-reset", sleep = false, factoryDefaults = true, otaFirmwareUpdate = false)
        Assert.assertFalse(resetFields.sleep)
        Assert.assertTrue(resetFields.factoryDefaults)
        Assert.assertFalse(resetFields.otaFirmwareUpdate)
        val h10Fields = PolarRuntimePlannerAdapter.h10StartRecordingFields("h10-start-recording", "myExercise", "SAMPLE_TYPE_HEART_RATE", 1)
        Assert.assertEquals("myExercise", h10Fields.sampleDataIdentifier)
        Assert.assertEquals("SAMPLE_TYPE_HEART_RATE", h10Fields.sampleType)
        Assert.assertEquals(1, h10Fields.recordingIntervalSeconds)
        Assert.assertTrue(PolarRuntimePlannerAdapter.syncStopNotificationFields("sync-stop-success").completed)
        Assert.assertEquals(
            listOf(
                PftpNotification.PbPFtpHostToDevNotification.INITIALIZE_SESSION_VALUE,
                PftpNotification.PbPFtpHostToDevNotification.START_SYNC_VALUE
            ),
            PolarRuntimePlannerAdapter.notificationNames(syncStart).map(PolarRuntimePlannerAdapter::notificationValue)
        )
        Assert.assertEquals(
            listOf(
                PftpNotification.PbPFtpHostToDevNotification.STOP_SYNC_VALUE,
                PftpNotification.PbPFtpHostToDevNotification.TERMINATE_SESSION_VALUE
            ),
            PolarRuntimePlannerAdapter.notificationNames(syncStop).map(PolarRuntimePlannerAdapter::notificationValue)
        )
    }

    @Test
    fun `shared D2H planner identifies exercise status notifications`() {
        Assert.assertEquals(
            "EXERCISE_STATUS",
            PolarRuntimePlannerAdapter.d2hNotificationTypeName(PftpNotification.PbPFtpDevToHostNotification.EXERCISE_STATUS_VALUE)
        )
        val plan = PolarRuntimePlannerAdapter.d2hNotificationPlan(
            PftpNotification.PbPFtpDevToHostNotification.EXERCISE_STATUS_VALUE,
            "0a020802"
        )
        Assert.assertEquals("EXERCISE_STATUS", plan?.notificationType)
        Assert.assertEquals("PbPftpDHExerciseStatus", plan?.parsedProtoName)
        Assert.assertEquals("PbPftpDHExerciseStatus", PolarRuntimePlannerAdapter.d2hParsedProtoName("EXERCISE_STATUS", "0a020802"))
    }

    @Test
    fun `shared file and REST plans select Android protobuf operation commands and paths`() {
        val rest = PolarRuntimePlannerAdapter.planRestFacadeGet("list-rest-api-services-success", "/REST/SERVICE.API", "service-list-json")
        val emptyRestTransport = PolarRuntimePlannerAdapter.planRestRequestTransportGet("/REST/SERVICE.API", payloadHex = "")
        val read = PolarRuntimePlannerAdapter.planFileFacade("read-low-level-file-success", "GET", "/U/0/CUSTOM.BIN")
        val write = PolarRuntimePlannerAdapter.planFileFacade("write-low-level-file-success", "PUT", "/U/0/CUSTOM.BIN", "0102")
        val remove = PolarRuntimePlannerAdapter.planFileFacade("delete-low-level-file-success", "REMOVE", "/U/0/CUSTOM.BIN")

        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, PolarRuntimePlannerAdapter.fileOperationCommand(rest))
        Assert.assertEquals("/REST/SERVICE.API", PolarRuntimePlannerAdapter.fileOperationPath(rest))
        Assert.assertEquals(listOf("GET:/REST/SERVICE.API"), emptyRestTransport.commands)
        Assert.assertEquals("requires-empty-response-policy", emptyRestTransport.terminal)
        Assert.assertEquals("", emptyRestTransport.resultHex)
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, PolarRuntimePlannerAdapter.fileOperationCommand(read))
        Assert.assertEquals("/U/0/CUSTOM.BIN", PolarRuntimePlannerAdapter.fileOperationPath(read))
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, PolarRuntimePlannerAdapter.fileOperationCommand(write))
        Assert.assertEquals("/U/0/CUSTOM.BIN", PolarRuntimePlannerAdapter.fileOperationPath(write))
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.REMOVE, PolarRuntimePlannerAdapter.fileOperationCommand(remove))
        Assert.assertEquals("/U/0/CUSTOM.BIN", PolarRuntimePlannerAdapter.fileOperationPath(remove))
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(PolarRuntimePlannerAdapter.fileOperationBytes(write))
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/CUSTOM.BIN", writeOperation.path)
        val payloads = listOf("one".toByteArray(), "two".toByteArray())
        Assert.assertEquals(payloads, PolarRuntimePlannerAdapter.restEventPayloads(uncompressed = true, payloads = payloads))
    }

    @Test
    fun `shared watch face field defaults route through Android runtime adapter`() {
        val defaults = PolarRuntimePlannerAdapter.watchFaceConfigFields()
        val explicit = PolarRuntimePlannerAdapter.watchFaceConfigFields(
            timeStyleId = 1,
            complicationLayoutId = 2,
            backgroundStyleId = 3,
            accentColor = 0x11223344L,
            complicationIds = listOf(10, 20),
            fontfaceId = 4
        )

        Assert.assertEquals(0, defaults.timeStyleId)
        Assert.assertEquals(0, defaults.complicationLayoutId)
        Assert.assertEquals(0, defaults.backgroundStyleId)
        Assert.assertEquals(0L, defaults.accentColor)
        Assert.assertEquals(emptyList<Int>(), defaults.complicationIds)
        Assert.assertEquals(0, defaults.fontfaceId)
        Assert.assertEquals(1, explicit.timeStyleId)
        Assert.assertEquals(2, explicit.complicationLayoutId)
        Assert.assertEquals(3, explicit.backgroundStyleId)
        Assert.assertEquals(0x11223344L, explicit.accentColor)
        Assert.assertEquals(listOf(10, 20), explicit.complicationIds)
        Assert.assertEquals(4, explicit.fontfaceId)
    }

    @Test
    fun `shared KVTX byte codec routes through Android runtime adapter`() {
        val key = 0x01020304
        val payload = byteArrayOf(0x11, 0x22, 0x33)
        val script = PolarRuntimePlannerAdapter.kvtxBuildWriteAndCommit(key, payload)

        Assert.assertEquals(0x00.toByte(), script.first())
        Assert.assertEquals(0x05.toByte(), script.last())
        Assert.assertArrayEquals(byteArrayOf(0x04, 0x03, 0x02, 0x01), PolarRuntimePlannerAdapter.kvtxU32Le(key))
        Assert.assertArrayEquals(payload, PolarRuntimePlannerAdapter.kvtxExtractValueForKey(script, key))
        Assert.assertNull(PolarRuntimePlannerAdapter.kvtxExtractValueForKey(script, 0x99))
    }

    @Test
    fun `shared offline recording metadata routes through Android runtime adapter`() {
        Assert.assertEquals("ACC", PolarRuntimePlannerAdapter.offlineRecordingMeasurementTypeName("ACC0.REC"))
        Assert.assertEquals("OFFLINE_HR", PolarRuntimePlannerAdapter.offlineRecordingMeasurementTypeName("HR.REC"))

        val grouped = PolarRuntimePlannerAdapter.groupedOfflineRecordingEntries(
            listOf(
                "/U/0/20250730/R/101010/ACC0.REC" to 10L,
                "/U/0/20250730/R/101010/ACC1.REC" to 20L,
                "/U/0/20250730/R/101010/HR.REC" to 30L,
                "/U/0/20250730/R/101010/PPG.REC" to 0L
            )
        )
        Assert.assertEquals(listOf("ACC", "HR"), grouped.map { it.type })
        Assert.assertEquals("/U/0/20250730/R/101010/ACC.REC", grouped.first().androidPath)
        Assert.assertEquals(30L, grouped.first().size)
        Assert.assertEquals("2025-07-30T10:10:10", grouped.first().dateTime)

        val parsed = PolarRuntimePlannerAdapter.parsePmdFilesV2(
            """
            10 /U/0/20250730/R/101010/ACC0.REC
            20 /U/0/20250730/R/101010/ACC1.REC
            bad /U/0/20250730/R/101010/PPG.REC
            """.trimIndent()
        )
        Assert.assertEquals(1, parsed.size)
        Assert.assertEquals("ACC", parsed.single().type)
        Assert.assertEquals(30L, parsed.single().size)
    }

    @Test
    fun `shared sleep REST facade paths preserve Android strings`() {
        Assert.assertEquals("/REST/SLEEP.API", PolarRuntimePlannerAdapter.sleepRestApiPath())
        Assert.assertEquals("/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]", PolarRuntimePlannerAdapter.sleepRecordingStateSubscribePath())
        Assert.assertEquals("/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording", PolarRuntimePlannerAdapter.stopSleepRecordingPath())
    }

    @Test
    fun `shared sleep result policy routes through Android runtime adapter`() {
        Assert.assertEquals(120, PolarRuntimePlannerAdapter.sleepStartOffsetSeconds(120))
        Assert.assertEquals(-60, PolarRuntimePlannerAdapter.sleepEndOffsetSeconds(-60))
        Assert.assertTrue(PolarRuntimePlannerAdapter.shouldIncludeOriginalSleepRange(true))
        Assert.assertFalse(PolarRuntimePlannerAdapter.shouldIncludeOriginalSleepRange(false))
        Assert.assertTrue(PolarRuntimePlannerAdapter.shouldIncludeSleepSkinTemperatureResult(true))
        Assert.assertFalse(PolarRuntimePlannerAdapter.shouldIncludeSleepSkinTemperatureResult(false))
    }

    @Test
    fun `shared spo2 projection routes through Android runtime adapter`() {
        val projection = PolarRuntimePlannerAdapter.spo2TestDataProjection(
            date = "2026-04-14",
            timeDirName = "063635",
            recordingDevice = "0004BF3D",
            timeZoneOffsetMinutes = 180,
            testStatus = 0,
            bloodOxygenPercent = 95,
            spo2Class = 3,
            spo2ValueDeviationFromBaseline = 0,
            spo2QualityAveragePercent = 99.0f,
            averageHeartRateBpm = 66,
            heartRateVariabilityMs = 79.97114f,
            spo2HrvDeviationFromBaseline = 2,
            altitudeMeters = 18.13582f
        )

        Assert.assertEquals(180, projection.timeZoneOffsetMinutes)
        Assert.assertEquals("passed", projection.testStatus)
        Assert.assertEquals(95, projection.bloodOxygenPercent)
        Assert.assertEquals("normal", projection.spo2Class)
        Assert.assertEquals("noBaseline", projection.spo2ValueDeviationFromBaseline)
        Assert.assertEquals(99.0f, projection.spo2QualityAveragePercent)
        Assert.assertEquals(66, projection.averageHeartRateBpm)
        Assert.assertEquals(79.97114f, projection.heartRateVariabilityMs)
        Assert.assertEquals("usual", projection.spo2HrvDeviationFromBaseline)
        Assert.assertEquals(18.13582f, projection.altitudeMeters)
    }

    @Test
    fun `shared backup restore plans select Android protobuf PUT operation and path`() {
        val operation = PolarRuntimePlannerAdapter.planBackupRestoreOperation("/U/0/BACKUP.TXT", "0102")

        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, operation?.first)
        Assert.assertEquals("/U/0/BACKUP.TXT", operation?.second)
        val writes = PolarRuntimePlannerAdapter.planBackupRestoreWrites(
            listOf(
                PolarRuntimePlannerAdapter.PlannedBackupRestoreFile(directory = "/U/0/S/", fileName = "UDEVSET.BPB", dataHex = "0102"),
                PolarRuntimePlannerAdapter.PlannedBackupRestoreFile(directory = "/SYS/BT/", fileName = "BTDEV.BPB", dataHex = "0304")
            )
        )
        Assert.assertEquals(listOf(PftpRequest.PbPFtpOperation.Command.PUT, PftpRequest.PbPFtpOperation.Command.PUT), writes.map { it.operation.first })
        Assert.assertEquals(listOf("/U/0/S/UDEVSET.BPB", "/SYS/BT/BTDEV.BPB"), writes.map { it.operation.second })
        Assert.assertEquals(listOf("0102", "0304"), writes.map { it.payloadHex })
        Assert.assertEquals(
            listOf("/U/0/S/PHYSDATA.BPB", "/U/0/S/UDEVSET.BPB", "/U/0/S/PREFS.BPB", "/U/0/USERID.BPB"),
            PolarRuntimePlannerAdapter.defaultBackupPaths()
        )
        Assert.assertTrue(PolarRuntimePlannerAdapter.firmwareUpdateIsAvailable("1.2.0", "1.2.1", "https://example.invalid/fw.zip"))
        Assert.assertFalse(PolarRuntimePlannerAdapter.firmwareUpdateIsAvailable("1.2.0", "1.2.1", ""))
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", PolarRuntimePlannerAdapter.backupTraversalRootPath("/U/*/S/UDEVSET.BPB"))
        val wildcardPlan = PolarRuntimePlannerAdapter.backupTraversalPlan("/SYS/*/BT/BTDEV.BPB")
        Assert.assertEquals("/SYS/*/BT/BTDEV.BPB", wildcardPlan.path)
        Assert.assertEquals("/SYS/", wildcardPlan.wildcardRootPath)
        Assert.assertEquals("BT", wildcardPlan.wildcardSubFolder)
    }

    @Test
    fun `shared offline trigger runtime exposes planned commands and enabled features`() {
        val setPlan = PolarRuntimePlannerAdapter.planOfflineTriggerSet(
            currentTypes = listOf("ACC", "GYRO", "OFFLINE_HR"),
            desiredFeatures = listOf(
                PolarRuntimePlannerAdapter.PlannedOfflineTriggerDesiredFeature("ACC", hasSelectedSettings = true),
                PolarRuntimePlannerAdapter.PlannedOfflineTriggerDesiredFeature("HR", hasSelectedSettings = false)
            ),
            secretPresent = true
        )
        val getPlan = PolarRuntimePlannerAdapter.planOfflineTriggerGet(
            listOf(
                PolarRuntimePlannerAdapter.PlannedOfflineTriggerDeviceTrigger("ACC", enabled = true),
                PolarRuntimePlannerAdapter.PlannedOfflineTriggerDeviceTrigger("GYRO", enabled = true),
                PolarRuntimePlannerAdapter.PlannedOfflineTriggerDeviceTrigger("OFFLINE_HR", enabled = true)
            )
        )

        Assert.assertEquals(
            listOf(
                "setMode:TRIGGER_SYSTEM_START",
                "getStatus",
                "setSetting:ACC:enabled:settings:secret",
                "setSetting:GYRO:disabled",
                "setSetting:OFFLINE_HR:enabled:no-settings:secret"
            ),
            setPlan.commands
        )
        Assert.assertEquals("success", setPlan.terminal)
        Assert.assertEquals(listOf("getStatus"), getPlan.commands)
        Assert.assertEquals(listOf("ACC", "HR"), getPlan.enabledFeatures)
        Assert.assertEquals(listOf("GYRO"), getPlan.excludedFeatures)
        Assert.assertEquals("success", getPlan.terminal)
    }

    @Test
    fun `shared firmware workflow plans select Android protobuf PUT operations and ordered paths`() {
        val workflow = PolarRuntimePlannerAdapter.planFirmwareWorkflow(
            id = "write-package-success-with-system-update-last",
            statuses = listOf("preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "finalizingFwUpdate", "fwUpdateCompletedSuccessfully"),
            firmwareFiles = listOf("SYSUPDAT.IMG", "BTUPDAT.BIN")
        )
        val operations = PolarRuntimePlannerAdapter.planFirmwareWriteOperations(listOf("SYSUPDAT.IMG", "BTUPDAT.BIN"))

        Assert.assertEquals(listOf("preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "finalizingFwUpdate", "fwUpdateCompletedSuccessfully"), workflow.statuses)
        Assert.assertEquals(listOf("/BTUPDAT.BIN", "/SYSUPDAT.IMG"), workflow.writes)
        Assert.assertEquals("success", workflow.terminal)
        Assert.assertNull(workflow.terminalError)
        Assert.assertTrue(workflow.retryDelaysMillis.isEmpty())
        Assert.assertEquals(
            listOf("TCHUPDAT.BIN", "APPUPDAT.BIN", "BTUPDAT.BIN", "SYSUPDAT.IMG"),
            PolarRuntimePlannerAdapter.orderFirmwareFiles(listOf("TCHUPDAT.BIN", "SYSUPDAT.IMG", "APPUPDAT.BIN", "BTUPDAT.BIN"))
        )
        Assert.assertTrue(PolarRuntimePlannerAdapter.firmwareFilePriority("BTUPDAT.BIN") < PolarRuntimePlannerAdapter.firmwareFilePriority("SYSUPDAT.IMG"))
        Assert.assertEquals("1.2.0", PolarRuntimePlannerAdapter.firmwareDeviceVersion(major = 1, minor = 2, patch = 0))
        Assert.assertEquals("/SYSUPDAT.IMG", PolarRuntimePlannerAdapter.firmwareSystemUpdateFilePath())
        Assert.assertTrue(PolarRuntimePlannerAdapter.isAvailableFirmwareVersionHigher(currentVersion = "1.0.0", availableVersion = "1.0.1"))
        Assert.assertFalse(PolarRuntimePlannerAdapter.isAvailableFirmwareVersionHigher(currentVersion = "2.0.0", availableVersion = "1.0.0"))
        Assert.assertEquals(
            listOf(
                PftpRequest.PbPFtpOperation.Command.PUT to "/BTUPDAT.BIN",
                PftpRequest.PbPFtpOperation.Command.PUT to "/SYSUPDAT.IMG"
            ),
            operations
        )
    }

    @Test
    fun `shared firmware workflow exposes retry delay planning without platform network execution`() {
        val workflow = PolarRuntimePlannerAdapter.planFirmwareWorkflow(
            id = "retryable-server-failure",
            statuses = listOf("preparingDeviceForFwUpdate", "fwUpdateFailed")
        )

        Assert.assertEquals(listOf("preparingDeviceForFwUpdate", "fwUpdateFailed"), workflow.statuses)
        Assert.assertEquals("retryable-server-failure", workflow.terminalError)
        Assert.assertEquals(listOf(1000L, 2000L), workflow.retryDelaysMillis)
        Assert.assertEquals(listOf(1000L, 2000L), PolarRuntimePlannerAdapter.firmwareRetryDelaysMillis(maxRetries = 2))
        Assert.assertEquals(listOf(1000L), PolarRuntimePlannerAdapter.firmwareRetryDelaysMillis(maxRetries = 1))
    }

    @Test
    fun `shared firmware package entry filter preserves Android readme skip policy`() {
        Assert.assertFalse(PolarRuntimePlannerAdapter.firmwarePackageEntryIsPayload("readme.txt"))
        Assert.assertTrue(PolarRuntimePlannerAdapter.firmwarePackageEntryIsPayload("README.TXT"))
        Assert.assertTrue(PolarRuntimePlannerAdapter.firmwarePackageEntryIsPayload("BTUPDAT.BIN"))
        Assert.assertTrue(PolarRuntimePlannerAdapter.firmwarePackageEntryIsPayload("SYSUPDAT.IMG"))
    }

    @Test
    fun `shared firmware reboot wait filter preserves Android system update policy`() {
        Assert.assertTrue(PolarRuntimePlannerAdapter.firmwareFileTriggersRebootWait("SYSUPDAT.IMG"))
        Assert.assertTrue(PolarRuntimePlannerAdapter.firmwareFileTriggersRebootWait("/SYSUPDAT.IMG"))
        Assert.assertFalse(PolarRuntimePlannerAdapter.firmwareFileTriggersRebootWait("BTUPDAT.BIN"))
        Assert.assertFalse(PolarRuntimePlannerAdapter.firmwareFileTriggersRebootWait("sysupdat.img"))
        Assert.assertEquals("success-rebooting", PolarRuntimePlannerAdapter.firmwareWriteTerminal(errorCode = 1, fileName = "/SYSUPDAT.IMG"))
        Assert.assertEquals("propagate-error", PolarRuntimePlannerAdapter.firmwareWriteTerminal(errorCode = 1, fileName = "BTUPDAT.BIN"))
        Assert.assertEquals("battery-too-low", PolarRuntimePlannerAdapter.firmwareWriteTerminal(errorCode = 209, fileName = "/SYSUPDAT.IMG"))
        Assert.assertEquals("propagate-error", PolarRuntimePlannerAdapter.firmwareWriteTerminal(errorCode = 103, fileName = "/SYSUPDAT.IMG"))
        val rebootPlan = PolarRuntimePlannerAdapter.planFirmwareSystemUpdateRebootSuccessWorkflow(listOf("/SYSUPDAT.IMG"))
        Assert.assertEquals(listOf("preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "finalizingFwUpdate", "fwUpdateCompletedSuccessfully"), rebootPlan.statuses)
        Assert.assertEquals(listOf("/SYSUPDAT.IMG"), rebootPlan.writes)
        Assert.assertNull(rebootPlan.terminalError)
        val batteryPlan = PolarRuntimePlannerAdapter.planFirmwareBatteryTooLowTerminalWorkflow(listOf("/SYSUPDAT.IMG"))
        Assert.assertEquals(listOf("preparingDeviceForFwUpdate", "fetchingFwUpdatePackage", "writingFwUpdatePackage", "fwUpdateFailed"), batteryPlan.statuses)
        Assert.assertEquals(listOf("/SYSUPDAT.IMG"), batteryPlan.writes)
        Assert.assertEquals("battery-too-low", batteryPlan.terminalError)
    }

    @Test
    fun `shared firmware write progress policy preserves Android throttle and zero guard`() {
        Assert.assertEquals(0L, PolarRuntimePlannerAdapter.firmwareWriteProgressPercent(bytesWritten = 0, payloadSize = 0))
        Assert.assertEquals(0L, PolarRuntimePlannerAdapter.firmwareWriteProgressPercent(bytesWritten = 12, payloadSize = 0))
        Assert.assertEquals(50L, PolarRuntimePlannerAdapter.firmwareWriteProgressPercent(bytesWritten = 2, payloadSize = 4))
        Assert.assertTrue(PolarRuntimePlannerAdapter.shouldEmitFirmwareWriteProgress(lastBytesWritten = 0, bytesWritten = 0, payloadSize = 0, minPercentageIncrement = 25, timeSinceLastEmitMs = 0))
        Assert.assertTrue(PolarRuntimePlannerAdapter.shouldEmitFirmwareWriteProgress(lastBytesWritten = 2, bytesWritten = 4, payloadSize = 4, minPercentageIncrement = 75, timeSinceLastEmitMs = 0))
        Assert.assertFalse(PolarRuntimePlannerAdapter.shouldEmitFirmwareWriteProgress(lastBytesWritten = 2, bytesWritten = 3, payloadSize = 100, minPercentageIncrement = 25, timeSinceLastEmitMs = 4999))
        Assert.assertTrue(PolarRuntimePlannerAdapter.shouldEmitFirmwareWriteProgress(lastBytesWritten = 2, bytesWritten = 3, payloadSize = 100, minPercentageIncrement = 25, timeSinceLastEmitMs = 5000))
        Assert.assertTrue(PolarRuntimePlannerAdapter.shouldEmitFirmwareWriteProgress(lastBytesWritten = 2, bytesWritten = 52, payloadSize = 100, minPercentageIncrement = 25, timeSinceLastEmitMs = 0))
    }

    @Test
    fun `shared PSFTP write ack terminal preserves Android success policy`() {
        Assert.assertEquals("success", PolarRuntimePlannerAdapter.planPsFtpWriteAck(payloadSize = 2))
        PolarRuntimePlannerAdapter.ensurePsFtpWriteRuntimePlan(payloadSize = 2)
        Assert.assertThrows(IllegalStateException::class.java) {
            PolarRuntimePlannerAdapter.ensurePsFtpWriteRuntimePlan(payloadSize = 2, writeAck = "never")
        }
    }

    @Test
    fun `shared user device settings plans select Android protobuf read and write operations`() {
        val read = PolarRuntimePlannerAdapter.planUserDeviceSettingsOperations("get-user-device-settings", "read", "/U/0/S/UDEVSET.BPB")
        val write = PolarRuntimePlannerAdapter.planUserDeviceSettingsOperations("set-user-device-settings", "write", "/U/0/S/UDEVSET.BPB", listOf("protobufPayload=platform-built"))
        val readThenWrite = PolarRuntimePlannerAdapter.planUserDeviceSettingsOperations("set-telemetry-enabled", "readThenWrite", "/U/0/S/UDEVSET.BPB", listOf("telemetryEnabled=true"))

        Assert.assertEquals(listOf(PftpRequest.PbPFtpOperation.Command.GET to "/U/0/S/UDEVSET.BPB"), read)
        Assert.assertEquals(listOf(PftpRequest.PbPFtpOperation.Command.PUT to "/U/0/S/UDEVSET.BPB"), write)
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET to "/U/0/S/UDEVSET.BPB", PolarRuntimePlannerAdapter.planUserDeviceSettingsRead("/U/0/S/UDEVSET.BPB"))
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT to "/U/0/S/UDEVSET.BPB", PolarRuntimePlannerAdapter.planUserDeviceSettingsWrite("/U/0/S/UDEVSET.BPB", listOf("protobufPayload=platform-built")))
        Assert.assertEquals(readThenWrite, PolarRuntimePlannerAdapter.planUserDeviceSettingsReadThenWrite("set-telemetry-enabled", "/U/0/S/UDEVSET.BPB", listOf("telemetryEnabled=true")))
        Assert.assertEquals(
            listOf(
                PftpRequest.PbPFtpOperation.Command.GET to "/U/0/S/UDEVSET.BPB",
                PftpRequest.PbPFtpOperation.Command.PUT to "/U/0/S/UDEVSET.BPB"
            ),
            readThenWrite
        )
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", PolarRuntimePlannerAdapter.userDeviceSettingsPath("POLAR_FILE_SYSTEM_V2", "/U/0/S/UDEVSET.BPB", "/UDEVSET.BPB", null))
        Assert.assertEquals("/UDEVSET.BPB", PolarRuntimePlannerAdapter.userDeviceSettingsPath("H10_FILE_SYSTEM", "/U/0/S/UDEVSET.BPB", "/UDEVSET.BPB", null))
        Assert.assertNull(PolarRuntimePlannerAdapter.userDeviceSettingsPath("UNKNOWN_FILE_SYSTEM", "/U/0/S/UDEVSET.BPB", "/UDEVSET.BPB", null))
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", PolarRuntimePlannerAdapter.userDeviceSettingsPath("UNKNOWN_FILE_SYSTEM", "/U/0/S/UDEVSET.BPB", "/UDEVSET.BPB", "/U/0/S/UDEVSET.BPB"))
    }

    @Test
    fun `shared user device settings payload fields match Android facade planning tokens`() {
        Assert.assertEquals(listOf("protobufPayload=platform-built"), PolarRuntimePlannerAdapter.userDeviceSettingsProtobufPayloadFields())
        Assert.assertEquals(listOf("telemetryEnabled=true"), PolarRuntimePlannerAdapter.userDeviceSettingsTelemetryPayloadFields(true))
        Assert.assertEquals(listOf("deviceLocation=WRIST_RIGHT"), PolarRuntimePlannerAdapter.userDeviceSettingsDeviceLocationPayloadFields(3))
        Assert.assertEquals(listOf("usbConnectionMode=ON"), PolarRuntimePlannerAdapter.userDeviceSettingsUsbConnectionModePayloadFields(true))
        Assert.assertEquals(
            listOf("automaticTrainingDetectionMode=ON", "automaticTrainingDetectionSensitivity=77", "minimumTrainingDurationSeconds=300"),
            PolarRuntimePlannerAdapter.userDeviceSettingsAutomaticTrainingDetectionPayloadFields(true, 77, 300)
        )
        Assert.assertEquals(listOf("automaticOhrMeasurement=ALWAYS_ON"), PolarRuntimePlannerAdapter.userDeviceSettingsAutomaticOhrPayloadFields(true))
        Assert.assertEquals(listOf("daylightSaving.nextDaylightSavingTime=present", "daylightSaving.offset=nonzero"), PolarRuntimePlannerAdapter.userDeviceSettingsDaylightSavingPayloadFields())
        Assert.assertEquals("ALWAYS_ON", PolarRuntimePlannerAdapter.userDeviceSettingsAutomaticMeasurementStateName(true))
        Assert.assertEquals("WRIST_RIGHT", PolarRuntimePlannerAdapter.userDeviceSettingsDeviceLocationName(3))
        Assert.assertEquals(3, PolarRuntimePlannerAdapter.userDeviceSettingsDeviceLocationValue("WRIST_RIGHT"))
        Assert.assertEquals("ON", PolarRuntimePlannerAdapter.userDeviceSettingsUsbConnectionModeName(true))
        Assert.assertEquals("ON", PolarRuntimePlannerAdapter.userDeviceSettingsAutomaticTrainingDetectionModeName(true))
    }

    @Test
    fun `shared stored data helpers preserve Android facade filters and empty parent cleanup`() {
        Assert.assertTrue(PolarRuntimePlannerAdapter.storedDataEntryMatchesFilter("TRC001.BIN", includePrefixes = listOf("TRC"), includeSuffixes = listOf(".BIN")))
        Assert.assertTrue(PolarRuntimePlannerAdapter.storedDataEntryMatchesFilter("20260530.SLG", includeSuffixes = listOf(".SLG", ".TXT")))
        Assert.assertFalse(PolarRuntimePlannerAdapter.storedDataEntryMatchesFilter("USERID.BPB", includePrefixes = listOf("TRC"), includeSuffixes = listOf(".BIN")))
        Assert.assertTrue(PolarRuntimePlannerAdapter.storedDataCleanupDirectoryEntryMatches("ACT", "20260530/", cutoffFolder = "20260531"))
        Assert.assertTrue(PolarRuntimePlannerAdapter.storedDataCleanupDirectoryEntryMatches("ACT", "ACTIVITY.BPB"))
        Assert.assertFalse(PolarRuntimePlannerAdapter.storedDataCleanupDirectoryEntryMatches("ACT", "USERID.BPB"))
        Assert.assertFalse(PolarRuntimePlannerAdapter.storedDataCleanupDirectoryEntryMatches("ACT", "HIST.BPB"))
        Assert.assertTrue(PolarRuntimePlannerAdapter.storedDataCleanupDirectoryEntryMatches("AUTOS", "AUTOS001.BPB"))
        Assert.assertTrue(PolarRuntimePlannerAdapter.storedDataCleanupDirectoryEntryMatches("SDLOGS", "A.SLG"))
        Assert.assertFalse(PolarRuntimePlannerAdapter.storedDataCleanupDirectoryEntryMatches("SDLOGS", "C.BPB"))
        Assert.assertFalse(PolarRuntimePlannerAdapter.storedDataCleanupDirectoryEntryMatches("UNDEFINED", "20260530/"))
        Assert.assertTrue(PolarRuntimePlannerAdapter.shouldPruneStoredDataEmptyParents("ACT"))
        Assert.assertEquals(
            listOf("/U/0/20260530/ACT", "/U/0/20260530"),
            PolarRuntimePlannerAdapter.storedDataEmptyParentDirectories("/U/0/20260530/ACT/ACTIVITY.BPB", trailingSlash = false)
        )
        Assert.assertEquals(listOf(PftpRequest.PbPFtpOperation.Command.GET to "/U/0/"), PolarRuntimePlannerAdapter.planStoredDataCleanup("activityPrune", "/U/0"))
        Assert.assertEquals(listOf(PftpRequest.PbPFtpOperation.Command.GET to "/U/0/AUTOS/"), PolarRuntimePlannerAdapter.planStoredDataCleanup("automaticSamplePrune", "/U/0/AUTOS", cutoffDate = "2026-05-31"))
    }

    @Test
    fun `shared stored data cleanup plans select Android protobuf remove operations`() {
        val telemetryOperations = PolarRuntimePlannerAdapter.planStoredDataCleanupOperations(
            kind = "filterDirectoryEntries",
            rootPath = "/",
            entries = listOf("TRC001.BIN", "ABC001.BIN", "TRC001.TXT"),
            includePrefixes = listOf("TRC"),
            includeSuffixes = listOf(".BIN")
        )
        val sdLogOperations = PolarRuntimePlannerAdapter.planStoredDataCleanupOperations(
            kind = "filterDirectoryEntries",
            rootPath = "/SDLOGS",
            entries = listOf("A.SLG", "B.TXT", "C.BPB"),
            includeSuffixes = listOf(".SLG", ".TXT")
        )
        val dateFolderOperations = PolarRuntimePlannerAdapter.planStoredDataCleanupOperations(
            kind = "emptyDayFolderRemoval",
            rootPath = "/U/0/20260530/"
        )
        val activityRemoveOperation = PolarRuntimePlannerAdapter.planStoredDataCleanupRemoveOperation(
            rootPath = "/U/0",
            filePath = "/U/0/20260530/ACT/ACTIVITY.BPB"
        )
        val automaticSampleRemoveOperation = PolarRuntimePlannerAdapter.planStoredDataCleanupRemoveOperation(
            rootPath = "/U/0/AUTOS",
            filePath = "/U/0/AUTOS/20260530/AUTOS001.BPB"
        )

        Assert.assertEquals(
            listOf(
                PftpRequest.PbPFtpOperation.Command.GET to "/",
                PftpRequest.PbPFtpOperation.Command.REMOVE to "/TRC001.BIN"
            ),
            telemetryOperations
        )
        Assert.assertEquals(
            listOf(
                PftpRequest.PbPFtpOperation.Command.GET to "/SDLOGS",
                PftpRequest.PbPFtpOperation.Command.REMOVE to "/SDLOGS/A.SLG",
                PftpRequest.PbPFtpOperation.Command.REMOVE to "/SDLOGS/B.TXT"
            ),
            sdLogOperations
        )
        Assert.assertEquals(
            listOf(PftpRequest.PbPFtpOperation.Command.REMOVE to "/U/0/20260530"),
            dateFolderOperations
        )
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.REMOVE to "/U/0/20260530/ACT/ACTIVITY.BPB", activityRemoveOperation)
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.REMOVE to "/U/0/AUTOS/20260530/AUTOS001.BPB", automaticSampleRemoveOperation)
    }

    private fun loadFeatureAvailabilityReadinessVector(): JsonObject {
        FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/feature-availability/feature-availability-readiness.json")).use { reader ->
            return JsonParser().parse(reader).asJsonObject
        }
    }

    private fun loadAvailableDataTypesReadinessVector(): JsonObject {
        FileReader(findRepositoryRoot().resolve("testdata/golden-vectors/sdk/available-data-types/available-data-types-readiness.json")).use { reader ->
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
        val FEATURE_AVAILABILITY_CASE_IDS = listOf(
            "firmware-update-requires-psftp-and-firmware-capability",
            "firmware-update-missing-firmware-capability-is-unavailable",
            "led-animation-requires-pmd-and-psftp-services",
            "watch-face-configuration-requires-psftp-and-not-sensor-capability",
            "offline-exercise-v2-uses-h10-filesystem-capability-without-service-gate",
            "unknown-feature-has-no-shared-preconditions"
        )
        val FEATURE_AVAILABILITY_BEHAVIOR_FAMILIES = listOf(
            "service-and-capability-gates",
            "feature-name-normalization",
            "h10-filesystem-capability-only-gate",
            "unknown-feature-pass-through",
            "platform-client-readiness-boundary"
        )
        const val FEATURE_AVAILABILITY_COMMON_DECISION = "SDK feature availability migration owns only deterministic service and capability preconditions in shared KMP; GATT client lookup, clientReady waits, PMD feature reads, notification readiness, service discovery, BLE transport execution, and public callback/error behavior remain platform-owned."
        val AVAILABLE_DATA_TYPES_BEHAVIOR_FAMILIES = listOf(
            "offline-pmd-to-public-mapping",
            "online-pmd-to-public-mapping",
            "hr-service-availability-projection",
            "ios-location-pressure-filter-boundary",
            "android-full-surface-boundary",
            "public-to-pmd-measurement-lookup",
            "unknown-public-type-null-boundary",
            "pmd-feature-read-platform-boundary",
            "hr-service-discovery-platform-boundary",
            "public-error-mapping-boundary",
            "platform-available-data-type-vector-reference-gate",
            "compile-verification-gate"
        )
        const val AVAILABLE_DATA_TYPES_COMMON_DECISION = "Available-data-types migration may proceed only after this readiness manifest is executable from shared commonTest, Android and iOS data utility tests continue to pin offline and online PMD-to-public mapping, HR-service availability projection, iOS location/pressure filters, Android full public surface, public-to-PMD measurement lookup, unknown public type boundaries, PMD feature-read boundaries, HR-service discovery boundaries, public error mapping boundaries, platform vector references, and compile verification before broader availability facade behavior moves."
    }
}
