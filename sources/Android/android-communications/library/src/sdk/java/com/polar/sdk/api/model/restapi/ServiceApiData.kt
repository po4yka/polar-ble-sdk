package com.polar.sdk.api.model.restapi

import com.polar.shared.sdk.PolarRestServiceModels

/**
 * Lists REST API services and corresponding paths
 */
data class PolarDeviceRestApiServices(val dictionary: Map<String, Any>) {
    private val sharedProjection
        get() = PolarRestServiceModels.serviceList(pathsForServices)

    /**
     * Maps available REST API service names to corresponding paths
      */
    val pathsForServices: Map<String,String>
        get() = dictionary["services"].asStringMap()

    /**
     * Lists REST API service names
     */
    val serviceNames: List<String>
        get() = sharedProjection.names

    /**
     * Lists REST API service paths
     */
    val servicePaths: List<String>
        get() = sharedProjection.paths
}

/**
 *  Describes specific service API per SAGRFC95
 */
data class PolarDeviceRestApiServiceDescription (
    val dictionary: Map<String, Any>
) {
    internal val sharedProjection
        get() = PolarRestServiceModels.serviceDescription(
            events = dictionary["events"].asStringList(),
            endpoints = dictionary["endpoints"].asStringList(),
            actions = dictionary["cmd"].asStringMap(),
            eventDescriptions = dictionary.entries.associateNotNull { entry ->
                val description = entry.value.asStringListMap().takeIf { it.isNotEmpty() }
                if (description == null) null else entry.key to description
            }
        )
}

/**
 * Events that can be acted upon using actions. Actions are returned in `actions` and `actionNames`
 * properties.
 */
val PolarDeviceRestApiServiceDescription.events: List<String>
    get() = dictionary["events"].asStringList()

/**
 * Endpoints that can be applied in **endpoint=** parameter in paths from `actions` and `actionPaths`
 */
val PolarDeviceRestApiServiceDescription.endpoints: List<String>
    get() = dictionary["endpoints"].asStringList()

/**
 * Actions/commands that can be sent, using put operation of corresponding path string
 * Path strings can contain following placeholders:
 *
 * **event=**: event name may follow equal to sign in path. Event names are listed using `events`
 * property. If given, the action targets the event.
 *
 * **resend=**: true or false may follow equal sign in path. true means client would like to receive
 * old events passed since last drop of connection
 *
 * **details=[]**: list of detail names may follow equal sign in path, specifying event detailed
 * data. Details are listed using `eventDetails`.
 *
 * **triggers=[]**: list of triggers may follow equal sign in path, specifying triggering related
 * to action. Triggers are listed using `eventTriggers`.
 *
 * **endpoint=**: endpoint, listed by `endpoints`, that is related to the action. This can be used
 * in post action paths.
 *
 */
val PolarDeviceRestApiServiceDescription.actions: Map<String, String>
    get() = dictionary["cmd"].asStringMap()

/**
 * Just the action names from `actions` property
 */
val PolarDeviceRestApiServiceDescription.actionNames: List<String>
    get() = sharedProjection.actionNames

/**
 * Just the action paths from `actions` property
 */
val PolarDeviceRestApiServiceDescription.actionPaths: List<String>
    get() = sharedProjection.actionPaths

/**
 * Lists event details that may be requested as returned event parameter values using action
 * path containing **details=[]** parameter placeholder
 * @param eventName the REST API event to get details for
 * @return detail names
 */
fun PolarDeviceRestApiServiceDescription.eventDetailsFor(eventName: String): List<String> {
    return sharedProjection.details[eventName] ?: emptyList()
}

/**
 * Lists triggers that may be used as trigger parameter list values when action path contains
 * **triggers=[]** parameter placeholder
 * @param eventName  the REST API event to get triggers for
 * @return triggers for the events
 */
fun PolarDeviceRestApiServiceDescription.eventTriggersFor(eventName: String): List<String> {
    return sharedProjection.triggers[eventName] ?: emptyList()
}

private inline fun <K, V> Iterable<Map.Entry<K, V>>.associateNotNull(transform: (Map.Entry<K, V>) -> Pair<K, Map<String, List<String>>>?): Map<K, Map<String, List<String>>> {
    val destination = linkedMapOf<K, Map<String, List<String>>>()
    for (entry in this) {
        val pair = transform(entry)
        if (pair != null) destination[pair.first] = pair.second
    }
    return destination
}

private fun Any?.asStringList(): List<String> {
    return (this as? List<*>)?.filterIsInstance<String>() ?: emptyList()
}

private fun Any?.asStringMap(): Map<String, String> {
    return (this as? Map<*, *>)?.entries?.mapNotNull { entry ->
        val key = entry.key as? String
        val value = entry.value as? String
        if (key != null && value != null) key to value else null
    }?.toMap() ?: emptyMap()
}

private fun Any?.asStringListMap(): Map<String, List<String>> {
    return (this as? Map<*, *>)?.entries?.mapNotNull { entry ->
        val key = entry.key as? String
        val value = entry.value.asStringList()
        if (key != null) key to value else null
    }?.toMap() ?: emptyMap()
}
