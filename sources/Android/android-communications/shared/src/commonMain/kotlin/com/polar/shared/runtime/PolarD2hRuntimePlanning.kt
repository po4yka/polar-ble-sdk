package com.polar.shared.runtime

data class PolarD2hEvent(
    val notificationType: String,
    val parametersHex: String,
    val parsedProto: String?,
    val action: String? = null,
    val path: String? = null,
    val syncTriggers: List<String> = emptyList(),
    val succeeded: Boolean? = null,
    val description: String? = null,
    val minimumInterval: Int? = null,
    val accuracy: Int? = null,
    val latitude: Float? = null,
    val longitude: Float? = null
)

data class PolarD2hStreamNotification(
    val notificationId: Int,
    val parametersHex: String = ""
)

data class PolarD2hNotificationPlan(
    val notificationType: String,
    val parsedProto: String?
)

data class PolarD2hStreamScenario(
    val id: String,
    val target: String,
    val subscribeOutcome: String,
    val message: String? = null,
    val notifications: List<PolarD2hStreamNotification> = emptyList(),
    val cancelAfterEmitted: Int? = null,
    val terminalMode: String? = null,
    val terminalMessage: String? = null
)

data class PolarD2hStreamOutcome(
    val events: List<String> = emptyList(),
    val terminalError: String? = null,
    val subscribeError: String? = null,
    val activeObserverCount: Int = 0,
    val cancelledStreams: List<String> = emptyList(),
    val upstreamCancelled: Boolean = false,
    val ignoredAfterCancel: List<String> = emptyList()
)

object PolarD2hRuntimePlanning {
    fun notificationTypeOrNull(notificationId: Int): String? {
        return when (notificationId) {
            0 -> "FILESYSTEM_MODIFIED"
            1 -> "INTERNAL_TEST_EVENT"
            2 -> "IDLING"
            3 -> "BATTERY_STATUS"
            4 -> "INACTIVITY_ALERT"
            5 -> "TRAINING_SESSION_STATUS"
            7 -> "SYNC_REQUIRED"
            8 -> "AUTOSYNC_STATUS"
            9 -> "PNS_DH_NOTIFICATION_RESPONSE"
            10 -> "PNS_SETTINGS"
            11 -> "START_GPS_MEASUREMENT"
            12 -> "STOP_GPS_MEASUREMENT"
            13 -> "KEEP_BACKGROUND_ALIVE"
            14 -> "POLAR_SHELL_DH_DATA"
            15 -> "MEDIA_CONTROL_REQUEST_DH"
            16 -> "MEDIA_CONTROL_COMMAND_DH"
            17 -> "MEDIA_CONTROL_ENABLED"
            18 -> "REST_API_EVENT"
            19 -> "EXERCISE_STATUS"
            else -> null
        }
    }

    fun mapNotification(notificationId: Int, parametersHex: String): List<PolarD2hEvent> {
        val plan = planNotificationEmission(notificationId, parametersHex) ?: return emptyList()
        val event = PolarD2hEvent(
            notificationType = plan.notificationType,
            parametersHex = parametersHex,
            parsedProto = plan.parsedProto
        )
        return listOf(decodeKnownParameters(event))
    }

    fun planNotificationEmission(notificationId: Int, parametersHex: String): PolarD2hNotificationPlan? {
        val type = notificationTypeOrNull(notificationId) ?: return null
        return PolarD2hNotificationPlan(
            notificationType = type,
            parsedProto = parsedProtoName(type, parametersHex)
        )
    }

    fun parsedProtoName(type: String, parametersHex: String): String? {
        if (parametersHex.isEmpty()) return null
        return when (type) {
            "FILESYSTEM_MODIFIED" -> "PbPFtpFilesystemModifiedParams"
            "SYNC_REQUIRED" -> if (parametersHex == "0a020802") "PbPFtpSyncRequiredParams" else null
            "AUTOSYNC_STATUS" -> "PbPFtpAutoSyncStatusParams"
            "START_GPS_MEASUREMENT" -> "PbPftpStartGPSMeasurement"
            "INACTIVITY_ALERT" -> "PbPFtpInactivityAlert"
            "TRAINING_SESSION_STATUS" -> "PbPFtpTrainingSessionStatus"
            "PNS_DH_NOTIFICATION_RESPONSE" -> "PbPftpPnsDHNotificationResponse"
            "PNS_SETTINGS" -> "PbPftpPnsState"
            "POLAR_SHELL_DH_DATA" -> "PbPFtpPolarShellMessageParams"
            "MEDIA_CONTROL_REQUEST_DH" -> "PbPftpDHMediaControlRequest"
            "MEDIA_CONTROL_COMMAND_DH" -> "PbPftpDHMediaControlCommand"
            "MEDIA_CONTROL_ENABLED" -> "PbPftpDHMediaControlEnabled"
            "REST_API_EVENT" -> "PbPftpDHRestApiEvent"
            "EXERCISE_STATUS" -> "PbPftpDHExerciseStatus"
            else -> null
        }
    }

    fun runStreamScenario(scenario: PolarD2hStreamScenario): PolarD2hStreamOutcome {
        val streamState = PolarStreamRuntimePlanning.newState()
        if (scenario.subscribeOutcome == "transportError") {
            return PolarD2hStreamOutcome(
                subscribeError = scenario.message,
                activeObserverCount = streamState.snapshot().activeObserverCount
            )
        }
        streamState.subscribe(scenario.target)
        val events = mutableListOf<String>()
        val ignoredAfterCancel = mutableListOf<String>()

        scenario.notifications.forEach { notification ->
            val mapped = notificationTypeOrNull(notification.notificationId) ?: return@forEach
            if (scenario.cancelAfterEmitted != null && events.size >= scenario.cancelAfterEmitted) {
                ignoredAfterCancel += mapped
            } else {
                events += mapped
                streamState.emit(mapped)
            }
            if (scenario.cancelAfterEmitted != null && events.size == scenario.cancelAfterEmitted && !streamState.snapshot().upstreamCancelled) {
                streamState.cancelConsumer(scenario.target)
            }
        }

        val terminalError = if (scenario.terminalMode == "transportError" && !streamState.snapshot().upstreamCancelled) {
            streamState.fail(requireNotNull(scenario.terminalMessage)).terminalError
        } else {
            null
        }
        val snapshot = streamState.snapshot()
        return PolarD2hStreamOutcome(
            events = events,
            terminalError = terminalError,
            activeObserverCount = snapshot.activeObserverCount,
            cancelledStreams = snapshot.cancelledStreams,
            upstreamCancelled = snapshot.upstreamCancelled,
            ignoredAfterCancel = ignoredAfterCancel
        )
    }

    private fun decodeKnownParameters(event: PolarD2hEvent): PolarD2hEvent {
        return when (event.notificationType) {
            "FILESYSTEM_MODIFIED" -> event.copy(action = "CREATED", path = "/U/0/")
            "SYNC_REQUIRED" -> if (event.parsedProto != null) event.copy(syncTriggers = listOf("TIMED")) else event
            "AUTOSYNC_STATUS" -> event.copy(succeeded = true, description = "Sync completed successfully")
            "START_GPS_MEASUREMENT" -> event.copy(minimumInterval = 1000, accuracy = 2, latitude = 60.1695f, longitude = 24.9354f)
            else -> event
        }
    }
}
