package com.polar.sdk.impl.utils

import com.polar.shared.runtime.PolarD2hRuntimePlanning
import org.junit.Assert
import org.junit.Test
import protocol.PftpNotification
import protocol.PftpRequest

class PolarRuntimePlannerAdapterTest {
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
            PolarD2hRuntimePlanning.notificationTypeOrNull(PftpNotification.PbPFtpDevToHostNotification.EXERCISE_STATUS_VALUE)
        )
        val plan = PolarD2hRuntimePlanning.planNotificationEmission(
            PftpNotification.PbPFtpDevToHostNotification.EXERCISE_STATUS_VALUE,
            "0a020802"
        )
        Assert.assertEquals("EXERCISE_STATUS", plan?.notificationType)
        Assert.assertEquals("PbPftpDHExerciseStatus", plan?.parsedProto)
    }

    @Test
    fun `shared file and REST plans select Android protobuf operation commands and paths`() {
        val rest = PolarRuntimePlannerAdapter.planRestFacadeGet("list-rest-api-services-success", "/REST/SERVICE.API", "service-list-json")
        val read = PolarRuntimePlannerAdapter.planFileFacade("read-low-level-file-success", "GET", "/U/0/CUSTOM.BIN")
        val write = PolarRuntimePlannerAdapter.planFileFacade("write-low-level-file-success", "PUT", "/U/0/CUSTOM.BIN", "0102")
        val remove = PolarRuntimePlannerAdapter.planFileFacade("delete-low-level-file-success", "REMOVE", "/U/0/CUSTOM.BIN")

        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, PolarRuntimePlannerAdapter.fileOperationCommand(rest))
        Assert.assertEquals("/REST/SERVICE.API", PolarRuntimePlannerAdapter.fileOperationPath(rest))
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.GET, PolarRuntimePlannerAdapter.fileOperationCommand(read))
        Assert.assertEquals("/U/0/CUSTOM.BIN", PolarRuntimePlannerAdapter.fileOperationPath(read))
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, PolarRuntimePlannerAdapter.fileOperationCommand(write))
        Assert.assertEquals("/U/0/CUSTOM.BIN", PolarRuntimePlannerAdapter.fileOperationPath(write))
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.REMOVE, PolarRuntimePlannerAdapter.fileOperationCommand(remove))
        Assert.assertEquals("/U/0/CUSTOM.BIN", PolarRuntimePlannerAdapter.fileOperationPath(remove))
        val writeOperation = PftpRequest.PbPFtpOperation.parseFrom(PolarRuntimePlannerAdapter.fileOperationBytes(write))
        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, writeOperation.command)
        Assert.assertEquals("/U/0/CUSTOM.BIN", writeOperation.path)
    }

    @Test
    fun `shared sleep REST facade paths preserve Android strings`() {
        Assert.assertEquals("/REST/SLEEP.API", PolarRuntimePlannerAdapter.sleepRestApiPath())
        Assert.assertEquals("/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]", PolarRuntimePlannerAdapter.sleepRecordingStateSubscribePath())
        Assert.assertEquals("/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording", PolarRuntimePlannerAdapter.stopSleepRecordingPath())
    }

    @Test
    fun `shared backup restore plans select Android protobuf PUT operation and path`() {
        val operation = PolarRuntimePlannerAdapter.planBackupRestoreOperation("/U/0/BACKUP.TXT", "0102")

        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, operation?.first)
        Assert.assertEquals("/U/0/BACKUP.TXT", operation?.second)
        Assert.assertEquals(
            listOf("/U/0/S/PHYSDATA.BPB", "/U/0/S/UDEVSET.BPB", "/U/0/S/PREFS.BPB", "/U/0/USERID.BPB"),
            PolarRuntimePlannerAdapter.defaultBackupPaths()
        )
        Assert.assertEquals("/U/0/S/UDEVSET.BPB", PolarRuntimePlannerAdapter.backupTraversalRootPath("/U/*/S/UDEVSET.BPB"))
    }

    @Test
    fun `shared firmware workflow plans select Android protobuf PUT operations and ordered paths`() {
        val operations = PolarRuntimePlannerAdapter.planFirmwareWriteOperations(listOf("SYSUPDAT.IMG", "BTUPDAT.BIN"))

        Assert.assertEquals(
            listOf("TCHUPDAT.BIN", "APPUPDAT.BIN", "BTUPDAT.BIN", "SYSUPDAT.IMG"),
            PolarRuntimePlannerAdapter.orderFirmwareFiles(listOf("TCHUPDAT.BIN", "SYSUPDAT.IMG", "APPUPDAT.BIN", "BTUPDAT.BIN"))
        )
        Assert.assertEquals(
            listOf(
                PftpRequest.PbPFtpOperation.Command.PUT to "/BTUPDAT.BIN",
                PftpRequest.PbPFtpOperation.Command.PUT to "/SYSUPDAT.IMG"
            ),
            operations
        )
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
    fun `shared user device settings plans select Android protobuf read and write operations`() {
        val read = PolarRuntimePlannerAdapter.planUserDeviceSettingsOperations("get-user-device-settings", "read", "/U/0/S/UDEVSET.BPB")
        val write = PolarRuntimePlannerAdapter.planUserDeviceSettingsOperations("set-user-device-settings", "write", "/U/0/S/UDEVSET.BPB", listOf("protobufPayload=platform-built"))
        val readThenWrite = PolarRuntimePlannerAdapter.planUserDeviceSettingsOperations("set-telemetry-enabled", "readThenWrite", "/U/0/S/UDEVSET.BPB", listOf("telemetryEnabled=true"))

        Assert.assertEquals(listOf(PftpRequest.PbPFtpOperation.Command.GET to "/U/0/S/UDEVSET.BPB"), read)
        Assert.assertEquals(listOf(PftpRequest.PbPFtpOperation.Command.PUT to "/U/0/S/UDEVSET.BPB"), write)
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
        PolarRuntimePlannerAdapter.planStoredDataCleanup("activityPrune", "/U/0")
        PolarRuntimePlannerAdapter.planStoredDataCleanup("automaticSamplePrune", "/U/0/AUTOS", cutoffDate = "2026-05-31")
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
    }
}
