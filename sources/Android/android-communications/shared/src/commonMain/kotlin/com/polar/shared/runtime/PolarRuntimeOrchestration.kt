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

data class PolarRestRequestTransportOperation(
    val id: String,
    val path: String,
    val transportMode: String,
    val status: Int?,
    val message: String?,
    val payloadHex: String?
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

data class PolarResetNotificationFields(
    val sleep: Boolean,
    val factoryDefaults: Boolean,
    val otaFirmwareUpdate: Boolean
)

data class PolarH10StartRecordingFields(
    val sampleDataIdentifier: String,
    val sampleType: String,
    val recordingIntervalSeconds: Int
)

data class PolarSyncStopNotificationFields(
    val completed: Boolean
)

object PolarRuntimeOrchestration {
    fun userDeviceSettingsPath(
        fileSystemType: String,
        deviceSettingsPath: String = "/U/0/S/UDEVSET.BPB",
        sensorSettingsPath: String = "/UDEVSET.BPB",
        unknownSettingsPath: String? = deviceSettingsPath
    ): String? {
        return when (fileSystemType) {
            "POLAR_FILE_SYSTEM_V2", "polarFileSystemV2" -> deviceSettingsPath
            "H10_FILE_SYSTEM", "h10FileSystem" -> sensorSettingsPath
            else -> unknownSettingsPath
        }
    }

    fun normalizeFileListFolderPath(folderPath: String): String {
        val nonEmpty = folderPath.ifEmpty { "/" }
        val withLeadingSlash = if (nonEmpty.first() == '/') nonEmpty else "/$nonEmpty"
        return if (withLeadingSlash.last() == '/') withLeadingSlash else "$withLeadingSlash/"
    }

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

    fun resetNotificationFields(operation: PolarFacadeCommandOperation): PolarResetNotificationFields {
        require(operation.kind == "resetNotification" || operation.kind == "resetNotificationFailure")
        val commands = operation.resetCommands()
        return PolarResetNotificationFields(
            sleep = commands.flagValue("sleep"),
            factoryDefaults = commands.flagValue("factoryDefaults"),
            otaFirmwareUpdate = commands.flagValue("otaFirmwareUpdate")
        )
    }

    fun h10StartRecordingFields(operation: PolarFacadeCommandOperation): PolarH10StartRecordingFields {
        require(operation.query == "REQUEST_START_RECORDING")
        val commands = operation.queryCommands()
        return PolarH10StartRecordingFields(
            sampleDataIdentifier = commands.parameterValue("sampleDataIdentifier"),
            sampleType = commands.parameterValue("sampleType"),
            recordingIntervalSeconds = commands.parameterValue("recordingIntervalSeconds").toInt()
        )
    }

    fun syncStopNotificationFields(operation: PolarFacadeCommandOperation): PolarSyncStopNotificationFields {
        require(operation.kind == "syncStop" || operation.kind == "syncStopFailure")
        val stopSyncCommand = operation.syncStopCommands().first { command -> command.startsWith("notification:STOP_SYNC") }
        return PolarSyncStopNotificationFields(
            completed = stopSyncCommand.substringAfter("completed=").toBooleanStrict()
        )
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

    fun planRestRequestTransport(operation: PolarRestRequestTransportOperation): PolarRuntimePlan {
        val commands = mutableListOf("GET:${operation.path}")
        return when (operation.transportMode) {
            "pftpResponseError" -> {
                commands += "response-error:${requireNotNull(operation.status)}:${requireNotNull(operation.message)}"
                PolarRuntimePlan(commands, "response-error")
            }
            "success" -> {
                require(operation.payloadHex == "") { "REST request transport policy only covers empty successful responses in this slice" }
                PolarRuntimePlan(commands, "requires-empty-response-policy", resultHex = "")
            }
            else -> error("Unsupported REST request transport mode ${operation.transportMode}")
        }
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
            "writeFailure" -> PolarRuntimePlan(operation.userDeviceSettingsWriteCommands(), "transport-error-after-payload")
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

    private fun List<String>.flagValue(name: String): Boolean {
        return first { command -> command.startsWith("flag:$name=") }
            .substringAfter("=")
            .toBooleanStrict()
    }

    private fun List<String>.parameterValue(name: String): String {
        return first { command -> command.startsWith("parameter:$name=") }
            .substringAfter("=")
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
