package com.polar.sharedtest

import com.polar.shared.runtime.PolarRestRequestTransportOperation
import com.polar.shared.runtime.PolarRuntimeOrchestration
import com.polar.shared.runtime.PolarRuntimePlan
import kotlin.test.Test
import kotlin.test.assertEquals

class RestRequestTransportPolicyCommonTest {
    @Test
    fun restRequestTransportPolicyVectorRunsThroughProductionCommonPlanner() {
        val vector = loadGoldenVectorText("sdk/rest-service/rest-request-transport-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val expectedPrototype = vector.objectValue("commonRuntimePrototype")
        val consumerTests = vector.objectValue("consumerTests")
        val requests = input.objectArray("requests").map { request ->
            RestRequestCase(
                id = request.stringValue("id"),
                path = request.stringValue("path"),
                transport = request.objectValue("transport").toRestRequestTransportOperation(
                    id = request.stringValue("id"),
                    path = request.stringValue("path")
                )
            )
        }
        val expectedCases = expectedPrototype.objectArray("cases").associateBy { it.stringValue("id") }

        assertEquals("restRequestTransportPolicy", input.stringValue("kind"))
        assertEquals(requiredRequestScenarioIds, requests.map { it.id })
        assertEquals(restRequestTransportSharedOwnershipRequirement, expected.stringValue("sharedOwnershipRequirement"))
        assertEquals("executable shared commonTest", expectedPrototype.stringValue("status"))
        assertEquals(requiredRequestScenarioIds, expectedCases.keys.toList())
        assertEquals(restRequestTransportCommonDecision, expected.stringValue("commonDecision"))
        assertEquals("shared-common-test", vector.objectValue("execution").stringValue("status"))
        assertEquals("executable shared commonTest covers command capture, response errors, and empty-response policy before facade mapping moves", vector.objectValue("platformExpectations").stringValue("common"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarDeviceRestApiUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarDeviceRestApiTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.RestRequestTransportPolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))

        requests.forEach { request ->
            val outcome = PolarRuntimeOrchestration.planRestRequestTransport(request.transport)
            val expected = expectedCases.getValue(request.id)

            assertEquals("${expected.stringValue("command")}:${expected.stringValue("path")}", outcome.commands.first(), request.id)
            assertOutcome(request.id, expected, outcome)
        }
    }

    @Test
    fun restRequestTransportReadinessManifestNamesEverySharedContractBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/rest-service/rest-request-transport-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPath = input.stringValue("policyVectorPath")
        val policy = loadGoldenVectorText(policyVectorPath)
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val consumerTests = manifest.objectValue("consumerTests")
        val platforms = manifest.objectValue("platforms")
        assertEquals("rest-request-transport-readiness", manifest.stringValue("id"))
        assertEquals("restRequestTransportReadiness", input.stringValue("kind"))
        assertEquals("sdk/rest-service/rest-request-transport-policy.json", policyVectorPath)
        assertEquals(requiredRestRequestTransportFamilies, requiredFamilies)
        assertEquals(requiredRestRequestTransportFamilies, coveredFamilies)
        assertRestRequestTransportPolicyVectorShape(policy)
        assertEquals(restRequestTransportReadinessCommonDecision, expected.stringValue("commonDecision"))
        assertEquals("executable shared commonTest runtime planning guard", expected.objectValue("commonRuntimePrototype").stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production shared ownership.", expected.objectValue("commonRuntimePrototype").stringValue("reason"))
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarDeviceRestApiUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarDeviceRestApiTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.RestRequestTransportPolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
        assertEquals(true, platforms.booleanValue("android"))
        assertEquals(true, platforms.booleanValue("ios"))
        assertEquals(true, platforms.booleanValue("common"))
    }

    private fun assertRestRequestTransportPolicyVectorShape(policy: String) {
        val input = policy.objectValue("input")
        val expected = policy.objectValue("expected")
        val commonRuntimePrototype = policy.objectValue("commonRuntimePrototype")
        val requests = input.objectArray("requests").associateBy { it.stringValue("id") }
        val cases = commonRuntimePrototype.objectArray("cases").associateBy { it.stringValue("id") }
        assertEquals("rest-request-transport-policy", policy.stringValue("id"))
        assertEquals("rest_request_transport_policy", policy.stringValue("case"))
        assertEquals("restRequestTransportPolicy", input.stringValue("kind"))
        assertEquals(requiredRequestScenarioIds, requests.keys.toList())
        assertEquals(requiredRequestScenarioIds, cases.keys.toList())
        assertEquals(restRequestTransportSharedOwnershipRequirement, expected.stringValue("sharedOwnershipRequirement"))
        assertEquals(restRequestTransportCommonDecision, expected.stringValue("commonDecision"))
        assertEquals("shared-common-test", policy.objectValue("execution").stringValue("status"))
        assertEquals("executable shared commonTest covers command capture, response errors, and empty-response policy before facade mapping moves", policy.objectValue("platformExpectations").stringValue("common"))
        assertEquals("BDBleApiImplTest and rest-facade-runtime-policy.json pin Android public response-error enum-name mapping and empty-success parse failure through public REST facade tests", policy.objectValue("platformExpectations").stringValue("android"))
        assertEquals("PolarBleApiImplTests and rest-facade-runtime-policy.json pin iOS public response-error code preservation and empty-success decode failure through public REST facade tests", policy.objectValue("platformExpectations").stringValue("ios"))
        assertEquals("This vector records REST request runtime scenarios now covered by executable shared commonTest command-capture and transport-outcome checks plus Android and iOS public facade response-error and empty-success compatibility through rest-facade-runtime-policy.json; additional REST operations still need their own facade compatibility before production REST request orchestration delegates to shared code.", policy.stringValue("notes"))
        assertRestRequestCase(requests.getValue("service-list-request-error-payload"), cases.getValue("service-list-request-error-payload"), "/REST/SERVICES.BPB", "pftpResponseError", 404, "REST services not available", "response-error")
        assertRestRequestCase(requests.getValue("service-description-request-error-payload"), cases.getValue("service-description-request-error-payload"), "/REST/TRAINING.BPB", "pftpResponseError", 500, "REST service description failed", "response-error")
        assertRestRequestCase(requests.getValue("service-list-empty-transport-response"), cases.getValue("service-list-empty-transport-response"), "/REST/SERVICES.BPB", "success", null, null, "requires-empty-response-policy")
        assertRestRequestCase(requests.getValue("service-description-empty-transport-response"), cases.getValue("service-description-empty-transport-response"), "/REST/TRAINING.BPB", "success", null, null, "requires-empty-response-policy")
    }

    private fun assertRestRequestCase(request: String, expectedCase: String, path: String, transportMode: String, status: Int?, message: String?, outcome: String) {
        assertEquals(path, request.stringValue("path"))
        assertEquals(transportMode, request.objectValue("transport").stringValue("mode"))
        status?.let { assertEquals(it, request.objectValue("transport").intValue("status")) }
        message?.let { assertEquals(it, request.objectValue("transport").stringValue("message")) }
        if (transportMode == "success") assertEquals("", request.objectValue("transport").stringValue("payloadHex"))
        assertEquals("GET", expectedCase.stringValue("command"))
        assertEquals(path, expectedCase.stringValue("path"))
        assertEquals(outcome, expectedCase.stringValue("outcome"))
        status?.let { assertEquals(it, expectedCase.intValue("status")) }
        message?.let { assertEquals(it, expectedCase.stringValue("message")) }
    }

    private val requiredRequestScenarioIds = listOf(
        "service-list-request-error-payload",
        "service-description-request-error-payload",
        "service-list-empty-transport-response",
        "service-description-empty-transport-response"
    )

    private val requiredRestRequestTransportFamilies = listOf(
        "service-list-get-path",
        "service-description-get-path",
        "response-error-payload-status",
        "response-error-payload-message",
        "empty-successful-response-policy-gate",
        "fake-pftp-request-harness-gate",
        "facade-error-mapping-pinned",
        "platform-transport-vector-reference-gate",
        "compile-verification-gate"
    )

    private val restRequestTransportSharedOwnershipRequirement = "Shared ownership of REST request orchestration requires a fake PFTP request harness that can inject response-error payloads and byte-for-byte empty successful responses for service discovery and service-description reads."

    private val restRequestTransportCommonDecision = "Characterize current Android and iOS behavior first, then choose whether common code preserves platform-specific empty-response behavior or normalizes it to a typed empty-response parse failure."

    private val restRequestTransportReadinessCommonDecision = "REST request transport shared ownership remains valid while rest-request-transport-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS REST tests continue to reference the same vectors, service-list and service-description GET paths remain pinned, response-error status and message mapping stay covered, empty successful responses are deliberately normalized or deliberately preserved as platform facade behavior, public facade error mapping stays pinned through rest-facade-runtime-policy.json, and the shared tests are compile-verified."

    private fun assertOutcome(caseId: String, expected: String, outcome: PolarRuntimePlan) {
        when (expected.stringValue("outcome")) {
            "response-error" -> {
                assertEquals("response-error", outcome.terminal, caseId)
                assertEquals("response-error:${expected.intValue("status")}:${expected.stringValue("message")}", outcome.commands.last(), caseId)
            }
            "requires-empty-response-policy" -> {
                assertEquals("requires-empty-response-policy", outcome.terminal, caseId)
                assertEquals("", outcome.resultHex, caseId)
            }
            else -> error("Unsupported expected REST runtime outcome ${expected.stringValue("outcome")} for $caseId")
        }
    }

    private fun String.toRestRequestTransportOperation(id: String, path: String): PolarRestRequestTransportOperation {
        return PolarRestRequestTransportOperation(
            id = id,
            path = path,
            transportMode = stringValue("mode"),
            status = optionalIntValue("status"),
            message = optionalStringValue("message"),
            payloadHex = optionalStringValue("payloadHex")
        )
    }

    private fun String.optionalIntValue(field: String): Int? {
        val valueStart = "\"$field\"".toRegex().find(this)?.range?.last?.plus(1) ?: return null
        val match = Regex("-?\\d+").find(this, valueStart) ?: return null
        return match.value.toInt()
    }

    private data class RestRequestCase(
        val id: String,
        val path: String,
        val transport: PolarRestRequestTransportOperation
    )
}
