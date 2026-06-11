package com.polar.shared.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

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
    private val json = Json {
        ignoreUnknownKeys = true
    }

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

    fun serviceListJson(jsonPayload: String): PolarRestServiceList {
        val root = parseObject(jsonPayload)
        return serviceList(root["services"].stringMapOrNull())
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

    fun serviceDescriptionJson(jsonPayload: String): PolarRestServiceDescription {
        val root = parseObject(jsonPayload)
        val eventDescriptions = root.mapNotNull { (key, value) ->
            val description = value.stringListMapOrNull()
            if (description == null) null else key to description
        }.toMap()
        return serviceDescription(
            events = root["events"].stringListOrNull(),
            endpoints = root["endpoints"].stringListOrNull(),
            actions = root["cmd"].stringMapOrNull(),
            eventDescriptions = eventDescriptions
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
        return if (uncompressed) {
            payloads
        } else {
            PolarRestEventCompressionCodec.compressedPayloads(payloads)
        }
    }

    private fun parseObject(jsonPayload: String): JsonObject {
        return json.parseToJsonElement(jsonPayload).jsonObject
    }

    private fun JsonElement?.stringListOrNull(): List<String>? {
        val array = this as? JsonArray ?: return null
        return array.mapNotNull { primitiveStringOrNull(it) }
    }

    private fun JsonElement?.stringMapOrNull(): Map<String, String>? {
        val jsonObject = this as? JsonObject ?: return null
        return jsonObject.mapNotNull { (key, value) ->
            val content = primitiveStringOrNull(value)
            if (content == null) null else key to content
        }.toMap()
    }

    private fun JsonElement?.stringListMapOrNull(): Map<String, List<String>>? {
        val jsonObject = this as? JsonObject ?: return null
        val result = jsonObject.mapNotNull { (key, value) ->
            val values = value.stringListOrNull()
            if (values == null) null else key to values
        }.toMap()
        return result.takeIf { it.isNotEmpty() }
    }

    private fun primitiveStringOrNull(element: JsonElement): String? {
        val primitive = element as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        return primitive.content
    }
}
