package com.polar.shared.sdk

object PolarRestServiceModels {
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
