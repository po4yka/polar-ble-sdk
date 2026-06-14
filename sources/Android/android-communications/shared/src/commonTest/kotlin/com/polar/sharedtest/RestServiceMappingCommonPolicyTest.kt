package com.polar.sharedtest

import com.polar.shared.sdk.PolarRestServiceModels
import com.polar.shared.sdk.PolarRestServiceDescription
import com.polar.shared.sdk.PolarRestServiceList
import kotlin.test.Test
import kotlin.test.assertEquals

class RestServiceMappingCommonPolicyTest {
    @Test
    fun sleepRestFacadePathsUseSharedPolicy() {
        val manifest = loadGoldenVectorText("sdk/rest-service/rest-service-mapping-readiness.json")

        assertEquals("/REST/SLEEP.API", PolarRestServiceModels.sleepApiPath())
        assertEquals("/REST/SLEEP.API?cmd=subscribe&event=sleep_recording_state&details=[enabled]", PolarRestServiceModels.sleepRecordingStateSubscribePath())
        assertEquals("/REST/SLEEP.API?cmd=post&endpoint=stop_sleep_recording", PolarRestServiceModels.stopSleepRecordingPath())
        assertEquals(true, manifest.stringArrayContains("requiredBehaviorFamilies", "sleep-rest-action-path-planning"))
        assertEquals(true, manifest.stringArrayContains("coveredBehaviorFamilies", "sleep-rest-action-path-planning"))
    }

    @Test
    fun restServiceListGoldenVectorsDefineExecutableCommonMappingPolicy() {
        listOf(
            "sdk/rest-service/service-list-basic.json",
            "sdk/rest-service/service-list-empty.json"
        ).forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val serviceList = parseServiceList(vector.objectValue("input").objectValue("json"))
            val expected = vector.objectValue("expected")

            assertEquals(expected.stringArrayValue("serviceNames"), serviceList.names, vector.stringValue("id"))
            assertEquals(expected.stringArrayValue("servicePaths"), serviceList.paths, vector.stringValue("id"))
            expected.optionalObjectValue("pathsForServices")?.let { expectedPaths ->
                serviceList.names.forEach { serviceName ->
                    assertEquals(expectedPaths.stringValue(serviceName), serviceList.pathsForServices.getValue(serviceName), "${vector.stringValue("id")} $serviceName")
                }
            }
            vector.optionalObjectValue("platformExpectations")?.optionalObjectValue("commonDecision")?.let { decision ->
                assertEquals("ignore-unknown-fields", decision.stringValue("unknownTopLevelPolicy"), vector.stringValue("id"))
                assertEquals("return-empty-collections", decision.stringValue("missingServicesPolicy"), vector.stringValue("id"))
            }
        }
    }

    @Test
    fun restServiceDescriptionGoldenVectorsDefineExecutableCommonMappingPolicy() {
        listOf(
            "sdk/rest-service/service-description-training.json",
            "sdk/rest-service/service-description-empty.json"
        ).forEach { relativePath ->
            val vector = loadGoldenVectorText(relativePath)
            val description = parseServiceDescription(vector.objectValue("input").objectValue("json"))
            val expected = vector.objectValue("expected")

            assertEquals(expected.stringArrayValue("events"), description.events, vector.stringValue("id"))
            assertEquals(expected.stringArrayValue("endpoints"), description.endpoints, vector.stringValue("id"))
            expected.optionalObjectValue("actions")?.let { actions ->
                actions.objectEntries().forEach { action ->
                    assertEquals(action.value, description.actions[action.key], "${vector.stringValue("id")} action ${action.key}")
                }
                assertEquals(actions.objectEntries().map { action -> action.key }, description.actionNames, "${vector.stringValue("id")} action names")
                assertEquals(actions.objectEntries().map { action -> action.value }, description.actionPaths, "${vector.stringValue("id")} action paths")
            }
            expected.objectValue("eventDetails").objectEntries().forEach { event ->
                assertEquals(event.value.stringArrayItems(), description.details[event.key] ?: emptyList(), "${vector.stringValue("id")} details ${event.key}")
            }
            expected.objectValue("eventTriggers").objectEntries().forEach { event ->
                assertEquals(event.value.stringArrayItems(), description.triggers[event.key] ?: emptyList(), "${vector.stringValue("id")} triggers ${event.key}")
            }
            vector.optionalObjectValue("platformExpectations")?.optionalObjectValue("commonDecision")?.let { decision ->
                assertEquals("ignore-unknown-fields", decision.stringValue("unknownTopLevelPolicy"), vector.stringValue("id"))
                assertEquals("return-empty-collections", decision.stringValue("missingCollectionsPolicy"), vector.stringValue("id"))
            }
        }
    }

    @Test
    fun restServiceListWrongTypeGoldenVectorPinsPlatformSplitForCommonDecoderOwnership() {
        val vector = loadGoldenVectorText("sdk/rest-service/service-list-wrong-type-platform-policy.json")
        val expected = vector.objectValue("expected")
        val androidExpected = expected.objectValue("android")
        val iosExpected = expected.objectValue("ios")
        val decision = vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("wrongTypePolicy")
        val consumerTests = vector.objectValue("consumerTests")
        val platforms = vector.objectValue("platforms")

        assertEquals("service-list-wrong-type-platform-policy", vector.stringValue("id"))
        assertEquals("sdk.rest-service", vector.stringValue("area"))
        assertEquals("service_list_wrong_type_platform_policy", vector.stringValue("case"))
        assertEquals("serviceList", vector.objectValue("input").stringValue("kind"))
        assertEquals(emptyList(), androidExpected.stringArrayValue("serviceNames"))
        assertEquals(emptyList(), androidExpected.stringArrayValue("servicePaths"))
        assertEquals("{}", androidExpected.objectValue("pathsForServices"))
        assertEquals(true, iosExpected.booleanValue("decodeError"))
        assertEquals(REST_WRONG_TYPE_POLICY_DECISION, decision)
        assertEquals("Malformed service discovery payload where services is present but not an object, preserving current Android/iOS policy before shared normalization.", vector.stringValue("notes"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarDeviceRestApiUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarDeviceRestApiServiceTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.RestServiceMappingCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"), vector.stringValue("id"))
        assertEquals(true, platforms.booleanValue("ios"), vector.stringValue("id"))
        assertEquals(true, platforms.booleanValue("common"), vector.stringValue("id"))
    }

    @Test
    fun restServiceMappingReadinessManifestNamesEverySharedContractBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/rest-service/rest-service-mapping-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val policyVectorPaths = input.stringArrayValue("policyVectorPaths")
        val requiredBehaviorFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredBehaviorFamilies = expected.stringArrayValue("coveredBehaviorFamilies")

        assertEquals("rest-service-mapping-readiness", manifest.stringValue("id"))
        assertEquals("restServiceMappingReadiness", input.stringValue("kind"))
        assertEquals("compileVerifiedSharedContractCharacterization", expected.stringValue("sharedOwnershipStatus"))
        assertEquals(REQUIRED_REST_SERVICE_MAPPING_FAMILIES, requiredBehaviorFamilies)
        assertEquals(REQUIRED_REST_SERVICE_MAPPING_FAMILIES, coveredBehaviorFamilies)
        assertEquals(REST_SERVICE_MAPPING_POLICY_VECTORS, policyVectorPaths)
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarDeviceRestApiUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarDeviceRestApiServiceTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.RestServiceMappingCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun parseServiceList(json: String): PolarRestServiceList {
        return PolarRestServiceModels.serviceListJson(json)
    }

    private fun parseServiceDescription(json: String): PolarRestServiceDescription {
        return PolarRestServiceModels.serviceDescriptionJson(json)
    }

    private fun String.optionalObjectValue(field: String): String? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val objectStart = indexOf('{', fieldIndex)
        if (objectStart < 0) return null
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.objectEntries(): List<JsonEntry> {
        val entries = mutableListOf<JsonEntry>()
        var index = 1
        while (index < length - 1) {
            val keyMatch = Regex("\"([^\"]+)\"\\s*:").find(this, index) ?: break
            val key = keyMatch.groupValues[1]
            var valueStart = keyMatch.range.last + 1
            while (valueStart < length && this[valueStart].isWhitespace()) valueStart += 1
            val value = when (this[valueStart]) {
                '"' -> {
                    val valueEnd = indexOf('"', valueStart + 1)
                    substring(valueStart + 1, valueEnd).also { index = valueEnd + 1 }
                }
                '[' -> {
                    val valueEnd = balancedEnd(valueStart, '[', ']')
                    substring(valueStart, valueEnd + 1).also { index = valueEnd + 1 }
                }
                '{' -> {
                    val valueEnd = balancedEnd(valueStart, '{', '}')
                    substring(valueStart, valueEnd + 1).also { index = valueEnd + 1 }
                }
                else -> error("Unsupported JSON entry value for key $key")
            }
            entries += JsonEntry(key, value)
        }
        return entries
    }

    private fun String.stringArrayItems(): List<String> {
        return Regex("\"([^\"]*)\"").findAll(this).map { it.groupValues[1] }.toList()
    }

    private fun String.stringArrayMapValue(): Map<String, List<String>> {
        return objectEntries().associate { entry -> entry.key to entry.value.stringArrayItems() }
    }

    private fun String.booleanValue(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" } ?: error("Missing boolean field $field in $this")
    }

    private fun String.balancedEnd(start: Int, open: Char, close: Char): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until length) {
            val char = this[index]
            if (escaped) {
                escaped = false
                continue
            }
            if (char == '\\') {
                escaped = inString
                continue
            }
            if (char == '"') {
                inString = !inString
                continue
            }
            if (!inString && char == open) depth += 1
            if (!inString && char == close) {
                depth -= 1
                if (depth == 0) return index
            }
        }
        error("Unbalanced $open$close block")
    }

    private data class JsonEntry(
        val key: String,
        val value: String
    )

    private companion object {
        val REST_SERVICE_MAPPING_POLICY_VECTORS = listOf(
            "sdk/rest-service/service-list-basic.json",
            "sdk/rest-service/service-list-empty.json",
            "sdk/rest-service/service-description-training.json",
            "sdk/rest-service/service-description-empty.json",
            "sdk/rest-service/service-list-wrong-type-platform-policy.json"
        )
        val REQUIRED_REST_SERVICE_MAPPING_FAMILIES = listOf(
            "service-list-name-path-mapping",
            "service-list-empty-defaults",
            "service-description-action-event-mapping",
            "sleep-rest-action-path-planning",
            "service-description-empty-defaults",
            "wrong-type-services-platform-split",
            "unknown-field-ignore-policy",
            "platform-rest-service-vector-references",
            "compile-verification-gate"
        )
        const val REST_WRONG_TYPE_POLICY_DECISION = "Android currently treats a non-object services field as empty while iOS JSONDecoder throws a type mismatch; choose an explicit shared policy before moving service discovery decoding to shared."
    }
}
