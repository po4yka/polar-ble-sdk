package com.polar.shared.runtime

data class PolarRuntimePlan(
    val commands: List<String>,
    val terminal: String,
    val resultHex: String? = null
)

data class PolarFacadeCommandOperation(
    val id: String,
    val kind: String,
    val query: String?,
    val parameters: List<String>,
    val notifications: List<String>,
    val sleep: Boolean?,
    val factoryDefaults: Boolean?,
    val otaFirmwareUpdate: Boolean?
)

data class PolarDiskTimeOperation(
    val id: String,
    val kind: String,
    val query: String?,
    val queries: List<String>,
    val parameters: List<String>,
    val expectedFields: List<String>
)

data class PolarRestFacadeOperation(
    val id: String,
    val command: String,
    val path: String,
    val payloadShape: String?,
    val expectedFields: List<String>,
    val transportMode: String?,
    val responseErrorStatus: Int?,
    val responseErrorMessage: String?,
    val expectedPlatformTerminal: String?
)

data class PolarFileFacadeOperation(
    val id: String,
    val command: String,
    val path: String,
    val payloadHex: String?,
    val responseHex: String?,
    val progress: List<Int>,
    val transportMode: String?
)

data class PolarFileRuntimeErrorOperation(
    val id: String,
    val operation: String,
    val path: String,
    val payloadHex: String?,
    val transportMode: String,
    val status: Int?,
    val message: String?,
    val error: String?,
    val responsePayloadHex: String?
)

data class PolarFileRuntimeErrorPlan(
    val command: String,
    val path: String,
    val outcome: String,
    val status: Int? = null,
    val message: String? = null,
    val error: String? = null,
    val capturedPayloadHex: String? = null
)

data class PolarUserDeviceSettingsOperation(
    val id: String,
    val kind: String,
    val path: String,
    val payloadFields: List<String>
)

object PolarRuntimeOrchestration {
    fun planCommand(operation: PolarFacadeCommandOperation): PolarRuntimePlan {
        return when (operation.kind) {
            "query" -> PolarRuntimePlan(operation.queryCommands(), "success")
            "queryFailure" -> PolarRuntimePlan(operation.queryCommands(), "transport-error")
            "resetNotification" -> PolarRuntimePlan(operation.resetCommands(), "success")
            "resetNotificationFailure" -> PolarRuntimePlan(operation.resetCommands(), "transport-error")
            "syncStart" -> PolarRuntimePlan(operation.syncStartCommands(), "success")
            "syncStartFailure" -> PolarRuntimePlan(operation.syncStartCommands(), "platform-split")
            "syncStop" -> PolarRuntimePlan(operation.syncStopCommands(), "success")
            "syncStopFailure" -> PolarRuntimePlan(operation.syncStopCommands(), "platform-split")
            else -> error("Unsupported public facade command operation ${operation.kind}")
        }
    }

    fun planDiskTime(operation: PolarDiskTimeOperation): PolarRuntimePlan {
        return when (operation.kind) {
            "query" -> PolarRuntimePlan(operation.singleQueryCommands(), "success")
            "queryFailure" -> PolarRuntimePlan(operation.singleQueryCommands(), "transport-error")
            "setLocalTimeV2" -> PolarRuntimePlan(operation.setLocalTimeV2Commands(), "success")
            "setLocalTimeH10" -> PolarRuntimePlan(operation.setLocalTimeH10Commands(), "success")
            else -> error("Unsupported disk/time operation ${operation.kind}")
        }
    }

    fun planRestFacade(operation: PolarRestFacadeOperation): PolarRuntimePlan {
        val commands = mutableListOf("${operation.command}:${operation.path}")
        operation.payloadShape?.let { payloadShape -> commands += "payload:$payloadShape" }
        commands += operation.expectedFields.map { field -> "field:$field" }
        val terminal = when (operation.transportMode) {
            "transportError" -> "transport-error"
            "responseError" -> {
                require(operation.responseErrorStatus == 103)
                require(operation.responseErrorMessage == "NO_SUCH_FILE_OR_DIRECTORY")
                require(operation.expectedPlatformTerminal == "pftp-response-error-name")
                commands += "response-error:${operation.responseErrorStatus}:${operation.responseErrorMessage}"
                "response-error"
            }
            "successEmpty" -> {
                commands += "payload:empty-success"
                require(operation.expectedPlatformTerminal == "json-parse-failure")
                "empty-response-parse-failure"
            }
            "successMalformedJson" -> {
                commands += "payload:malformed-json"
                require(operation.expectedPlatformTerminal == "json-parse-failure")
                "malformed-response-parse-failure"
            }
            null -> "success"
            else -> error("Unsupported REST facade transport mode ${operation.transportMode}")
        }
        return PolarRuntimePlan(commands, terminal)
    }

    fun planFileFacade(operation: PolarFileFacadeOperation): PolarRuntimePlan {
        val commands = mutableListOf("${operation.command}:${operation.path}")
        operation.payloadHex?.let { payloadHex -> commands += "payload:$payloadHex" }
        operation.progress.forEach { progress -> commands += "progress:$progress" }
        val terminal = when (operation.transportMode) {
            "transportError" -> "transport-error"
            "pftpResponseError" -> "response-error:103:missing"
            "writeStreamError" -> "write-stream-error-after-payload"
            null -> "success"
            else -> error("Unsupported file facade transport mode ${operation.transportMode}")
        }
        return PolarRuntimePlan(commands, terminal, operation.responseHex)
    }

    fun planFileRuntimeError(operation: PolarFileRuntimeErrorOperation): PolarFileRuntimeErrorPlan {
        val command = when (operation.operation) {
            "listFiles", "readFile" -> "GET"
            "writeFile" -> "PUT"
            "removeSingleFile" -> "REMOVE"
            else -> error("Unsupported file runtime operation ${operation.operation}")
        }
        return when (operation.transportMode) {
            "pftpResponseError" -> {
                if (operation.operation == "listFiles" && operation.status == 103) {
                    PolarFileRuntimeErrorPlan(command, operation.path, "directory-missing", status = operation.status)
                } else {
                    PolarFileRuntimeErrorPlan(command, operation.path, "response-error", status = requireNotNull(operation.status), message = requireNotNull(operation.message))
                }
            }
            "transportError" -> PolarFileRuntimeErrorPlan(command, operation.path, "transport-error", error = requireNotNull(operation.error))
            "writeStreamError" -> PolarFileRuntimeErrorPlan(command, operation.path, "write-stream-error", error = requireNotNull(operation.error), capturedPayloadHex = operation.payloadHex)
            "success" -> {
                if (operation.operation == "listFiles") {
                    PolarFileRuntimeErrorPlan(command, operation.path, "directory-parse-failure")
                } else {
                    PolarFileRuntimeErrorPlan(command, operation.path, "requires-empty-response-policy")
                }
            }
            else -> error("Unsupported file runtime transport mode ${operation.transportMode}")
        }
    }

    fun planUserDeviceSettings(operation: PolarUserDeviceSettingsOperation): PolarRuntimePlan {
        return when (operation.kind) {
            "read" -> PolarRuntimePlan(operation.userDeviceSettingsReadCommands(), "success")
            "readFailure" -> PolarRuntimePlan(operation.userDeviceSettingsReadCommands(), "transport-error")
            "write" -> PolarRuntimePlan(operation.userDeviceSettingsWriteCommands(), "success")
            "readThenWrite" -> PolarRuntimePlan(operation.userDeviceSettingsReadWriteCommands(), "success")
            "readThenWriteFailure" -> PolarRuntimePlan(operation.userDeviceSettingsReadWriteCommands(), "transport-error-after-payload")
            else -> error("Unsupported user-device-settings operation ${operation.kind}")
        }
    }

    private fun PolarFacadeCommandOperation.queryCommands(): List<String> {
        val commands = mutableListOf("query:${requireNotNull(query)}")
        if (parameters.isEmpty()) {
            commands += "parameters:none"
        } else {
            commands += parameters.map { parameter -> "parameter:$parameter" }
        }
        return commands
    }

    private fun PolarFacadeCommandOperation.resetCommands(): List<String> {
        return listOf(
            "notification:RESET",
            "flag:sleep=${requireNotNull(sleep)}",
            "flag:factoryDefaults=${requireNotNull(factoryDefaults)}",
            "flag:otaFirmwareUpdate=${requireNotNull(otaFirmwareUpdate)}"
        )
    }

    private fun PolarFacadeCommandOperation.syncStartCommands(): List<String> {
        return queryCommands() + notifications.map { notification -> "notification:$notification" }
    }

    private fun PolarFacadeCommandOperation.syncStopCommands(): List<String> {
        return notifications.map { notification -> "notification:$notification" }
    }

    private fun PolarDiskTimeOperation.singleQueryCommands(): List<String> {
        val commands = mutableListOf("query:${requireNotNull(query)}")
        if (parameters.isEmpty()) {
            commands += "parameters:none"
        } else {
            commands += parameters.map { parameter -> "field:$parameter" }
        }
        return commands
    }

    private fun PolarDiskTimeOperation.setLocalTimeV2Commands(): List<String> {
        require(queries == listOf("SET_SYSTEM_TIME", "SET_LOCAL_TIME"))
        val systemTimeHour = expectedFields.first { it.startsWith("systemTimeHour=") }
        val systemTimeTrusted = expectedFields.first { it == "systemTimeTrusted=true" }
        val localTimeHour = expectedFields.first { it.startsWith("localTimeHour=") }
        return listOf(
            "query:SET_SYSTEM_TIME",
            "field:$systemTimeHour",
            "field:$systemTimeTrusted",
            "query:SET_LOCAL_TIME",
            "field:$localTimeHour"
        )
    }

    private fun PolarDiskTimeOperation.setLocalTimeH10Commands(): List<String> {
        require(queries == listOf("SET_LOCAL_TIME"))
        val localTimeHour = expectedFields.first { it.startsWith("localTimeHour=") }
        return listOf(
            "query:SET_LOCAL_TIME",
            "field:$localTimeHour"
        )
    }

    private fun PolarUserDeviceSettingsOperation.userDeviceSettingsReadCommands(): List<String> {
        return listOf("read:$path")
    }

    private fun PolarUserDeviceSettingsOperation.userDeviceSettingsWriteCommands(): List<String> {
        return listOf("write:$path") + payloadFields.map { field -> "field:$field" }
    }

    private fun PolarUserDeviceSettingsOperation.userDeviceSettingsReadWriteCommands(): List<String> {
        return listOf("read:$path", "write:$path") + payloadFields.map { field -> "field:$field" }
    }
}
