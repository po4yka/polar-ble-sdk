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
    }

    @Test
    fun `shared backup restore plans select Android protobuf PUT operation and path`() {
        val operation = PolarRuntimePlannerAdapter.planBackupRestoreOperation("/U/0/BACKUP.TXT", "0102")

        Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.PUT, operation?.first)
        Assert.assertEquals("/U/0/BACKUP.TXT", operation?.second)
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
}
