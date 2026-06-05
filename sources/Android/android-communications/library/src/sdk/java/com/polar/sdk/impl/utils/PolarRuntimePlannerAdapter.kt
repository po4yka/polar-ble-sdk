package com.polar.sdk.impl.utils

import com.polar.shared.runtime.PolarDiskTimeOperation
import com.polar.shared.runtime.PolarFacadeCommandOperation
import com.polar.shared.runtime.PolarFileFacadeOperation
import com.polar.shared.runtime.PolarFileRuntimeErrorOperation
import com.polar.shared.runtime.PolarRestFacadeOperation
import com.polar.shared.runtime.PolarRuntimeOrchestration
import com.polar.shared.runtime.PolarUserDeviceSettingsOperation

internal object PolarRuntimePlannerAdapter {
    fun planCommandQuery(id: String, query: String, parameters: List<String> = emptyList()) {
        PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "query",
                query = query,
                parameters = parameters,
                notifications = emptyList(),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        )
    }

    fun planCommandReset(id: String, sleep: Boolean, factoryDefaults: Boolean, otaFirmwareUpdate: Boolean) {
        PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "resetNotification",
                query = null,
                parameters = emptyList(),
                notifications = emptyList(),
                sleep = sleep,
                factoryDefaults = factoryDefaults,
                otaFirmwareUpdate = otaFirmwareUpdate
            )
        )
    }

    fun planCommandSyncStart(id: String) {
        PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "syncStart",
                query = "REQUEST_SYNCHRONIZATION",
                parameters = emptyList(),
                notifications = listOf("INITIALIZE_SESSION", "START_SYNC"),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        )
    }

    fun planCommandSyncStop(id: String) {
        PolarRuntimeOrchestration.planCommand(
            PolarFacadeCommandOperation(
                id = id,
                kind = "syncStop",
                query = null,
                parameters = emptyList(),
                notifications = listOf("STOP_SYNC:completed=true", "TERMINATE_SESSION"),
                sleep = null,
                factoryDefaults = null,
                otaFirmwareUpdate = null
            )
        )
    }

    fun planDiskTimeQuery(id: String, query: String) {
        PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = id,
                kind = "query",
                query = query,
                queries = emptyList(),
                parameters = emptyList(),
                expectedFields = emptyList()
            )
        )
    }

    fun planSetLocalTimeV2(systemTimeHour: Int, localTimeHour: Int) {
        PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = "set-local-time-v2",
                kind = "setLocalTimeV2",
                query = null,
                queries = listOf("SET_SYSTEM_TIME", "SET_LOCAL_TIME"),
                parameters = emptyList(),
                expectedFields = listOf("systemTimeHour=$systemTimeHour", "localTimeHour=$localTimeHour", "systemTimeTrusted=true")
            )
        )
    }

    fun planSetLocalTimeH10(localTimeHour: Int) {
        PolarRuntimeOrchestration.planDiskTime(
            PolarDiskTimeOperation(
                id = "set-local-time-h10",
                kind = "setLocalTimeH10",
                query = null,
                queries = listOf("SET_LOCAL_TIME"),
                parameters = emptyList(),
                expectedFields = listOf("localTimeHour=$localTimeHour")
            )
        )
    }

    fun planRestFacadeGet(id: String, path: String, payloadShape: String? = null) {
        PolarRuntimeOrchestration.planRestFacade(
            PolarRestFacadeOperation(
                id = id,
                command = "GET",
                path = path,
                payloadShape = payloadShape,
                expectedFields = emptyList(),
                transportMode = null,
                responseErrorStatus = null,
                responseErrorMessage = null,
                expectedPlatformTerminal = null
            )
        )
    }

    fun planFileFacade(id: String, command: String, path: String, payloadHex: String? = null, responseHex: String? = null) {
        PolarRuntimeOrchestration.planFileFacade(
            PolarFileFacadeOperation(
                id = id,
                command = command,
                path = path,
                payloadHex = payloadHex,
                responseHex = responseHex,
                progress = emptyList(),
                transportMode = null
            )
        )
    }

    fun planFileRuntimeError(operation: String, path: String, error: Throwable) {
        PolarRuntimeOrchestration.planFileRuntimeError(
            PolarFileRuntimeErrorOperation(
                id = "platform-file-runtime-error",
                operation = operation,
                path = path,
                payloadHex = null,
                transportMode = "transportError",
                status = null,
                message = null,
                error = error.javaClass.simpleName.ifEmpty { error.toString() },
                responsePayloadHex = null
            )
        )
    }

    fun planUserDeviceSettingsRead(path: String) {
        PolarRuntimeOrchestration.planUserDeviceSettings(
            PolarUserDeviceSettingsOperation(
                id = "get-user-device-settings",
                kind = "read",
                path = path,
                payloadFields = emptyList()
            )
        )
    }

    fun planUserDeviceSettingsReadThenWrite(id: String, path: String, payloadFields: List<String>) {
        PolarRuntimeOrchestration.planUserDeviceSettings(
            PolarUserDeviceSettingsOperation(
                id = id,
                kind = "readThenWrite",
                path = path,
                payloadFields = payloadFields
            )
        )
    }

    fun planUserDeviceSettingsWrite(path: String, payloadFields: List<String>) {
        PolarRuntimeOrchestration.planUserDeviceSettings(
            PolarUserDeviceSettingsOperation(
                id = "set-user-device-settings",
                kind = "write",
                path = path,
                payloadFields = payloadFields
            )
        )
    }
}
