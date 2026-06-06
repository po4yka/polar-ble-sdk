package com.polar.sdk.impl.utils

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
            "h10-recording-status" to ("REQUEST_RECORDING_STATUS" to PftpRequest.PbPFtpQuery.REQUEST_RECORDING_STATUS_VALUE)
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
    fun `shared reset and sync plans select Android protobuf notification ids`() {
        val reset = PolarRuntimePlannerAdapter.planCommandReset("factory-reset", sleep = false, factoryDefaults = true, otaFirmwareUpdate = false)
        val syncStart = PolarRuntimePlannerAdapter.planCommandSyncStart("sync-start-success")
        val syncStop = PolarRuntimePlannerAdapter.planCommandSyncStop("sync-stop-success")

        Assert.assertEquals(
            listOf(PftpNotification.PbPFtpHostToDevNotification.RESET_VALUE),
            PolarRuntimePlannerAdapter.notificationNames(reset).map(PolarRuntimePlannerAdapter::notificationValue)
        )
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
    }

    @Test
    fun `shared stored data helpers preserve Android facade filters and empty parent cleanup`() {
        Assert.assertTrue(PolarRuntimePlannerAdapter.storedDataEntryMatchesFilter("TRC001.BIN", includePrefixes = listOf("TRC"), includeSuffixes = listOf(".BIN")))
        Assert.assertTrue(PolarRuntimePlannerAdapter.storedDataEntryMatchesFilter("20260530.SLG", includeSuffixes = listOf(".SLG", ".TXT")))
        Assert.assertFalse(PolarRuntimePlannerAdapter.storedDataEntryMatchesFilter("USERID.BPB", includePrefixes = listOf("TRC"), includeSuffixes = listOf(".BIN")))
        Assert.assertTrue(PolarRuntimePlannerAdapter.shouldPruneStoredDataEmptyParents("ACT"))
        Assert.assertEquals(
            listOf("/U/0/20260530/ACT", "/U/0/20260530"),
            PolarRuntimePlannerAdapter.storedDataEmptyParentDirectories("/U/0/20260530/ACT/ACTIVITY.BPB", trailingSlash = false)
        )
        PolarRuntimePlannerAdapter.planStoredDataCleanup("activityPrune", "/U/0")
        PolarRuntimePlannerAdapter.planStoredDataCleanup("automaticSamplePrune", "/U/0/AUTOS", cutoffDate = "2026-05-31")
    }
}
