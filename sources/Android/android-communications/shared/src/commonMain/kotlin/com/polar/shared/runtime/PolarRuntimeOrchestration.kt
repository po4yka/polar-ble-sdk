package com.polar.shared.runtime

data class PolarRuntimePlan(
    val commands: List<String>,
    val terminal: String
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
}
