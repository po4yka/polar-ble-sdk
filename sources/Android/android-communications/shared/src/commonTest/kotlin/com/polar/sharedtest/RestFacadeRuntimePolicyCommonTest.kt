package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals

class RestFacadeRuntimePolicyCommonTest {
    @Test
    fun restFacadeRuntimePolicyVectorDefinesExecutableCommonRequestPlanning() {
        val vector = loadGoldenVectorText("sdk/rest-service/rest-facade-runtime-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val expectedPrototype = expected.objectValue("commonRuntimePrototype")
        val operations = input.objectArray("operations").map { operation ->
            RestFacadeOperation(
                id = operation.stringValue("id"),
                command = operation.stringValue("command"),
                path = operation.stringValue("path"),
                payloadShape = operation.optionalStringValue("payloadShape"),
                expectedFields = operation.optionalStringArrayValue("expectedFields") ?: emptyList(),
                transportMode = operation.optionalObjectValue("transport")?.stringValue("mode"),
                responseErrorStatus = operation.optionalObjectValue("transport")?.optionalIntValue("status"),
                responseErrorMessage = operation.optionalObjectValue("transport")?.optionalStringValue("message"),
                expectedPlatformTerminal = operation.optionalObjectValue("expectedPlatformTerminal")?.stringValue("android")
            )
        }
        val expectedCases = expectedPrototype.objectArray("cases").associateBy { it.stringValue("id") }
        val runtime = FakeRestFacadeRuntime()

        assertEquals("restFacadeRuntimePolicy", input.stringValue("kind"))
        assertEquals(requiredRestFacadeRuntimeOperationIds, operations.map { it.id })
        assertEquals(requiredRestFacadeRuntimeOperationIds, expectedCases.keys.toList())
        assertEquals(restFacadeRuntimeCommonDecision, expected.stringValue("commonDecision"))
        assertEquals(restFacadeRuntimeTopLevelDecision, vector.stringValue("commonDecision"))
        assertEquals("fake-rest-facade-runtime-policy", vector.objectValue("execution").stringValue("kind"))
        assertEquals("public-facade-psftp-request-capture", vector.objectValue("execution").stringValue("transport"))
        assertEquals(true, vector.objectValue("execution").booleanValue("wallClockSafe"))
        assertRestFacadeOperationFields(operations.associateBy { it.id })

        operations.forEach { operation ->
            val outcome = runtime.run(operation)
            val expected = expectedCases.getValue(operation.id)

            assertEquals(expected.stringArrayValue("commands"), outcome.commands, operation.id)
            assertEquals(expected.stringValue("terminal"), outcome.terminal, operation.id)
        }
        assertRestFacadeCommonRuntimeCases(expectedCases)
    }

    @Test
    fun restFacadeRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/rest-service/rest-facade-runtime-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPath = input.stringValue("policyVectorPath")
        val policy = loadGoldenVectorText(policyVectorPath)
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        assertEquals("rest-facade-runtime-readiness", manifest.stringValue("id"))
        assertEquals("restFacadeRuntimeReadiness", input.stringValue("kind"))
        assertEquals("sdk/rest-service/rest-facade-runtime-policy.json", policyVectorPath)
        assertEquals(requiredRestFacadeRuntimeFamilies, requiredFamilies)
        assertEquals(requiredRestFacadeRuntimeFamilies, coveredFamilies)
        assertRestFacadePolicyVectorShape(policy)
        assertEquals(restFacadeRuntimeReadinessCommonDecision, expected.stringValue("commonDecision"))
        val commonRuntimePrototype = expected.objectValue("commonRuntimePrototype")
        assertEquals("executable shared commonTest runtime planning guard", commonRuntimePrototype.stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", commonRuntimePrototype.stringValue("reason"))
        val consumerTests = manifest.objectValue("consumerTests")
        assertEquals(listOf("com.polar.sdk.impl.BDBleApiImplTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarBleApiImplTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.RestFacadeRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private fun assertRestFacadePolicyVectorShape(policy: String) {
        val policyInput = policy.objectValue("input")
        val policyExpected = policy.objectValue("expected")
        val policyOperations = policyInput.objectArray("operations").associateBy { it.stringValue("id") }
        val policyCases = policyExpected.objectValue("commonRuntimePrototype").objectArray("cases").associateBy { it.stringValue("id") }
        assertEquals("rest-facade-runtime-policy", policy.stringValue("id"))
        assertEquals("rest_facade_runtime_policy", policy.stringValue("case"))
        assertEquals("restFacadeRuntimePolicy", policyInput.stringValue("kind"))
        assertEquals(requiredRestFacadeRuntimeOperationIds, policyOperations.keys.toList())
        assertEquals(requiredRestFacadeRuntimeOperationIds, policyCases.keys.toList())
        assertEquals(restFacadeRuntimeCommonDecision, policyExpected.stringValue("commonDecision"))
        assertEquals(restFacadeRuntimeTopLevelDecision, policy.stringValue("commonDecision"))
        assertEquals("fake-rest-facade-runtime-policy", policy.objectValue("execution").stringValue("kind"))
        assertEquals("public-facade-psftp-request-capture", policy.objectValue("execution").stringValue("transport"))
        assertRestFacadeOperationFields(policyOperations.mapValues { (_, operation) ->
            RestFacadeOperation(
                id = operation.stringValue("id"),
                command = operation.stringValue("command"),
                path = operation.stringValue("path"),
                payloadShape = operation.optionalStringValue("payloadShape"),
                expectedFields = operation.optionalStringArrayValue("expectedFields") ?: emptyList(),
                transportMode = operation.optionalObjectValue("transport")?.stringValue("mode"),
                responseErrorStatus = operation.optionalObjectValue("transport")?.optionalIntValue("status"),
                responseErrorMessage = operation.optionalObjectValue("transport")?.optionalStringValue("message"),
                expectedPlatformTerminal = operation.optionalObjectValue("expectedPlatformTerminal")?.stringValue("android")
            )
        })
        assertRestFacadeCommonRuntimeCases(policyCases)
    }

    private fun assertRestFacadeOperationFields(operationsById: Map<String, RestFacadeOperation>) {
        assertEquals("/REST/SERVICE.API", operationsById.getValue("list-rest-api-services-success").path)
        assertEquals("service-list-json", operationsById.getValue("list-rest-api-services-success").payloadShape)
        assertEquals(listOf("serviceName=sleep", "serviceName=training", "servicePath.sleep=/REST/SLEEP.API", "servicePath.training=/REST/TRAINING.API"), operationsById.getValue("list-rest-api-services-success").expectedFields)
        assertEquals("/REST/SLEEP.API", operationsById.getValue("get-rest-api-description-success").path)
        assertEquals("service-description-json", operationsById.getValue("get-rest-api-description-success").payloadShape)
        assertEquals(listOf("event=sleep", "endpoint=stop", "action.post=/REST/SLEEP.API?cmd=post", "detail.sleep=state", "trigger.sleep=change"), operationsById.getValue("get-rest-api-description-success").expectedFields)
        assertEquals("transportError", operationsById.getValue("list-rest-api-services-request-failure").transportMode)
        assertEquals("transportError", operationsById.getValue("get-rest-api-description-request-failure").transportMode)
        assertEquals("responseError", operationsById.getValue("list-rest-api-services-response-error").transportMode)
        assertEquals(103, operationsById.getValue("list-rest-api-services-response-error").responseErrorStatus)
        assertEquals("NO_SUCH_FILE_OR_DIRECTORY", operationsById.getValue("list-rest-api-services-response-error").responseErrorMessage)
        assertEquals("pftp-response-error-name", operationsById.getValue("list-rest-api-services-response-error").expectedPlatformTerminal)
        assertEquals("responseError", operationsById.getValue("get-rest-api-description-response-error").transportMode)
        assertEquals(103, operationsById.getValue("get-rest-api-description-response-error").responseErrorStatus)
        assertEquals("NO_SUCH_FILE_OR_DIRECTORY", operationsById.getValue("get-rest-api-description-response-error").responseErrorMessage)
        assertEquals("pftp-response-error-name", operationsById.getValue("get-rest-api-description-response-error").expectedPlatformTerminal)
        assertEquals("successEmpty", operationsById.getValue("list-rest-api-services-empty-success").transportMode)
        assertEquals("successMalformedJson", operationsById.getValue("list-rest-api-services-malformed-success").transportMode)
        assertEquals("successEmpty", operationsById.getValue("get-rest-api-description-empty-success").transportMode)
        assertEquals("successMalformedJson", operationsById.getValue("get-rest-api-description-malformed-success").transportMode)
    }

    private fun assertRestFacadeCommonRuntimeCases(casesById: Map<String, String>) {
        assertEquals(listOf("GET:/REST/SERVICE.API", "payload:service-list-json", "field:serviceName=sleep", "field:serviceName=training", "field:servicePath.sleep=/REST/SLEEP.API", "field:servicePath.training=/REST/TRAINING.API"), casesById.getValue("list-rest-api-services-success").stringArrayValue("commands"))
        assertEquals(listOf("GET:/REST/SLEEP.API", "payload:service-description-json", "field:event=sleep", "field:endpoint=stop", "field:action.post=/REST/SLEEP.API?cmd=post", "field:detail.sleep=state", "field:trigger.sleep=change"), casesById.getValue("get-rest-api-description-success").stringArrayValue("commands"))
        assertEquals(listOf("GET:/REST/SERVICE.API", "response-error:103:NO_SUCH_FILE_OR_DIRECTORY"), casesById.getValue("list-rest-api-services-response-error").stringArrayValue("commands"))
        assertEquals(listOf("GET:/REST/SLEEP.API", "response-error:103:NO_SUCH_FILE_OR_DIRECTORY"), casesById.getValue("get-rest-api-description-response-error").stringArrayValue("commands"))
        assertEquals("empty-response-parse-failure", casesById.getValue("list-rest-api-services-empty-success").stringValue("terminal"))
        assertEquals("malformed-response-parse-failure", casesById.getValue("list-rest-api-services-malformed-success").stringValue("terminal"))
        assertEquals("empty-response-parse-failure", casesById.getValue("get-rest-api-description-empty-success").stringValue("terminal"))
        assertEquals("malformed-response-parse-failure", casesById.getValue("get-rest-api-description-malformed-success").stringValue("terminal"))
    }

    private val requiredRestFacadeRuntimeOperationIds = listOf(
        "list-rest-api-services-success",
        "get-rest-api-description-success",
        "list-rest-api-services-request-failure",
        "get-rest-api-description-request-failure",
        "list-rest-api-services-response-error",
        "get-rest-api-description-response-error",
        "list-rest-api-services-empty-success",
        "list-rest-api-services-malformed-success",
        "get-rest-api-description-empty-success",
        "get-rest-api-description-malformed-success"
    )

    private val requiredVectorTerms = listOf(
        "rest-facade-runtime-policy",
        "list-rest-api-services-success",
        "get-rest-api-description-success",
        "list-rest-api-services-request-failure",
        "get-rest-api-description-request-failure",
        "list-rest-api-services-response-error",
        "get-rest-api-description-response-error",
        "list-rest-api-services-empty-success",
        "get-rest-api-description-empty-success",
        "list-rest-api-services-malformed-success",
        "get-rest-api-description-malformed-success",
        "/REST/SERVICE.API",
        "/REST/SLEEP.API",
        "service-list-json",
        "service-description-json",
        "serviceName=sleep",
        "serviceName=training",
        "servicePath.sleep=/REST/SLEEP.API",
        "event=sleep",
        "endpoint=stop",
        "action.post=/REST/SLEEP.API?cmd=post",
        "detail.sleep=state",
        "trigger.sleep=change",
        "transport-error",
        "responseError",
        "response-error",
        "response-error:103:NO_SUCH_FILE_OR_DIRECTORY",
        "pftp-response-error-name",
        "pftp-response-error-code",
        "NO_SUCH_FILE_OR_DIRECTORY",
        "successEmpty",
        "successMalformedJson",
        "empty-response-parse-failure",
        "malformed-response-parse-failure",
        "malformed-json",
        "json-parse-failure",
        "json-decoder-failure",
        "rest-request-transport-policy.json"
    )

    private val requiredRestFacadeRuntimeFamilies = listOf(
        "service-list-request-path",
        "service-list-json-success",
        "service-list-path-field-mapping",
        "service-description-request-path",
        "service-description-json-success",
        "service-description-action-field-mapping",
        "service-description-event-detail-trigger-mapping",
        "service-list-request-failure",
        "service-description-request-failure",
        "service-list-response-error-platform-mapping",
        "service-description-response-error-platform-mapping",
        "service-list-empty-success-parse-failure",
        "service-description-empty-success-parse-failure",
        "service-list-malformed-success-parse-failure",
        "service-description-malformed-success-parse-failure",
        "model-json-mapping-vector-reference-gate",
        "empty-response-transport-policy-gate",
        "response-error-transport-policy-gate",
        "facade-error-mapping-gate",
        "platform-facade-vector-reference-gate",
        "compile-verification-gate"
    )

    private val restFacadeRuntimeCommonDecision = "A shared REST facade runtime may own deterministic service-list and service-description request planning only after platform facades keep public parse behavior, empty-success and malformed-success parse/decode failures, request-failure error mapping, and response-error platform mapping pinned for both service discovery and service-description paths."

    private val restFacadeRuntimeTopLevelDecision = "Promote REST facade request planning only after service-list and description success cases, service-list and service-description request failures, response-error platform mapping, empty-success and malformed-success parse/decode failures, model JSON mapping vectors, and lower-level empty-response/response-error transport policy remain explicitly covered."

    private val restFacadeRuntimeReadinessCommonDecision = "REST facade runtime migration may proceed only after rest-facade-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, model JSON mapping vectors remain linked, empty-response and malformed-response parse/decode failures plus response-error transport policies stay covered, public facade error mapping is pinned for service-list and service-description response errors, and the shared tests are compile-verified."

    private class FakeRestFacadeRuntime {
        fun run(operation: RestFacadeOperation): RestFacadeOutcome {
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
            return RestFacadeOutcome(commands, terminal)
        }
    }

    private data class RestFacadeOperation(
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

    private data class RestFacadeOutcome(
        val commands: List<String>,
        val terminal: String
    )

    private fun String.optionalIntValue(field: String): Int? {
        val valueStart = "\"$field\"".toRegex().find(this)?.range?.last?.plus(1) ?: return null
        val match = Regex("-?\\d+").find(this, valueStart) ?: return null
        return match.value.toInt()
    }
}
