package com.polar.shared.sdk

data class PolarRestServiceList(
    val pathsForServices: Map<String, String>
) {
    val names: List<String>
        get() = PolarRestServiceModels.serviceNames(pathsForServices)

    val paths: List<String>
        get() = PolarRestServiceModels.servicePaths(pathsForServices)
}

data class PolarRestServiceDescription(
    val events: List<String>,
    val endpoints: List<String>,
    val actions: Map<String, String>,
    val details: Map<String, List<String>>,
    val triggers: Map<String, List<String>>
) {
    val actionNames: List<String>
        get() = PolarRestServiceModels.actionNames(actions)

    val actionPaths: List<String>
        get() = PolarRestServiceModels.actionPaths(actions)
}

object PolarRestServiceModels {
    fun sleepApiPath(): String {
        return "/REST/SLEEP.API"
    }

    fun sleepRecordingStateSubscribePath(): String {
        return "${sleepApiPath()}?cmd=subscribe&event=sleep_recording_state&details=[enabled]"
    }

    fun stopSleepRecordingPath(): String {
        return "${sleepApiPath()}?cmd=post&endpoint=stop_sleep_recording"
    }

    fun serviceList(pathsForServices: Map<String, String>?): PolarRestServiceList {
        return PolarRestServiceList(pathsForServices ?: emptyMap())
    }

    fun serviceDescription(events: List<String>?, endpoints: List<String>?, actions: Map<String, String>?, eventDescriptions: Map<String, Map<String, List<String>>>): PolarRestServiceDescription {
        val eventNames = events ?: emptyList()
        val detailKeys = (eventNames + eventDescriptions.keys).distinct()
        return PolarRestServiceDescription(
            events = eventNames,
            endpoints = endpoints ?: emptyList(),
            actions = actions ?: emptyMap(),
            details = detailKeys.associateWith { event -> eventDetails(eventDescriptions[event] ?: emptyMap()) },
            triggers = detailKeys.associateWith { event -> eventTriggers(eventDescriptions[event] ?: emptyMap()) }
        )
    }

    fun serviceNames(pathsForServices: Map<String, String>): List<String> {
        return pathsForServices.keys.toList()
    }

    fun servicePaths(pathsForServices: Map<String, String>): List<String> {
        return pathsForServices.values.toList()
    }

    fun actionNames(actions: Map<String, String>): List<String> {
        return actions.keys.toList()
    }

    fun actionPaths(actions: Map<String, String>): List<String> {
        return actions.values.toList()
    }

    fun eventDetails(eventDescription: Map<String, List<String>>): List<String> {
        return eventDescription["details"] ?: emptyList()
    }

    fun eventTriggers(eventDescription: Map<String, List<String>>): List<String> {
        return eventDescription["triggers"] ?: emptyList()
    }

    fun restEventPayloads(uncompressed: Boolean, payloads: List<ByteArray>): List<ByteArray> {
        require(uncompressed) { "Shared REST event compression codec is intentionally not selected before KMP migration" }
        return payloads
    }
}
