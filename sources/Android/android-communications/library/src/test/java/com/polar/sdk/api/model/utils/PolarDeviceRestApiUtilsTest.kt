package com.polar.sdk.api.model.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.google.protobuf.ByteString
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.model.restapi.PolarDeviceRestApiServiceDescription
import com.polar.sdk.api.model.restapi.PolarDeviceRestApiServices
import com.polar.sdk.api.model.restapi.actionNames
import com.polar.sdk.api.model.restapi.actionPaths
import com.polar.sdk.api.model.restapi.actions
import com.polar.sdk.api.model.restapi.endpoints
import com.polar.sdk.api.model.restapi.eventDetailsFor
import com.polar.sdk.api.model.restapi.eventTriggersFor
import com.polar.sdk.api.model.restapi.events
import com.polar.sdk.impl.utils.receiveRestApiEventData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import protocol.PftpNotification.PbPFtpDevToHostNotification
import protocol.PftpNotification.PbPftpDHRestApiEvent
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.zip.GZIPOutputStream

class PolarDeviceRestApiUtilsTest {

    private lateinit var mockClient: BlePsFtpClient

    @Before
    fun setUp() {
        mockClient = mockk(relaxed = true)
    }

    @Test
    fun receiveRestApiEventData_uncompressedEvent_returnsOriginalPayloads() = runTest {
        val payloads = listOf(
            """{"path":"/v1/users","operation":"created"}""".toByteArray(),
            """{"path":"/v1/users/1","operation":"updated"}""".toByteArray()
        )
        every { mockClient.waitForNotification() } returns flowOf(restApiNotification(payloads, uncompressed = true))

        val result = mockClient.receiveRestApiEventData("device-id").first()

        assertEquals(payloads.size, result.size)
        payloads.forEachIndexed { index, payload ->
            assertArrayEquals(payload, result[index])
        }
    }

    @Test
    fun receiveRestApiEventData_gzipCompressedEvent_decompressesPayloads() = runTest {
        val payloads = listOf(
            """{"path":"/v1/training","operation":"synced"}""".toByteArray(),
            """{"path":"/v1/activity","operation":"deleted"}""".toByteArray()
        )
        every { mockClient.waitForNotification() } returns flowOf(restApiNotification(payloads.map { it.gzip() }, uncompressed = false))

        val result = mockClient.receiveRestApiEventData("device-id").first()

        assertEquals(payloads.size, result.size)
        payloads.forEachIndexed { index, payload ->
            assertArrayEquals(payload, result[index])
        }
    }

    @Test
    fun restServiceGoldenVectors_mapJsonToPublicModels() {
        val vectors = loadRestServiceVectors()
        assertTrue("Expected REST service golden vectors", vectors.isNotEmpty())

        vectors.filter { it.getAsJsonObject("input").get("kind").asString in setOf("serviceList", "serviceDescription") }.forEach { vector ->
            val caseId = vector.get("id").asString
            val input = vector.getAsJsonObject("input")
            val json = input.getAsJsonObject("json").toString()
            val dictionary = parseJsonMap(json)
            when (input.get("kind").asString) {
                "serviceList" -> assertServiceList(caseId, PolarDeviceRestApiServices(dictionary), androidExpected(vector.getAsJsonObject("expected")))
                "serviceDescription" -> assertServiceDescription(caseId, PolarDeviceRestApiServiceDescription(dictionary), vector.getAsJsonObject("expected"))
                else -> error("Unknown REST service vector kind for $caseId")
            }
        }
    }

    @Test
    fun restEventCompressionGoldenVectors_preserveAndroidPolicy() = runTest {
        val vector = loadRestServiceVectors().first { it.getAsJsonObject("input").get("kind").asString == "restEventCompression" }
        vector.getAsJsonObject("input").getAsJsonArray("cases").map { it.asJsonObject }.forEach { testCase ->
            if (testCase.has("expected")) return@forEach
            val payloads = testCase.getAsJsonArray("payloads").map { it.asString.toByteArray() }
            val uncompressed = testCase.get("uncompressed").asBoolean
            every { mockClient.waitForNotification() } returns flowOf(
                restApiNotification(
                    if (uncompressed) payloads else payloads.map { it.gzip() },
                    uncompressed = uncompressed
                )
            )

            val result = mockClient.receiveRestApiEventData("device-id").first()

            assertEquals(testCase.get("id").asString, payloads.size, result.size)
            payloads.forEachIndexed { index, payload ->
                assertArrayEquals(testCase.get("id").asString, payload, result[index])
            }
        }
    }

    @Test
    fun restEventMalformedCompressionGoldenVectors_preserveAndroidPolicy() = runTest {
        val vector = loadRestServiceVectors().first { it.getAsJsonObject("input").get("kind").asString == "restEventCompression" }
        val testCase = vector.getAsJsonObject("input").getAsJsonArray("cases")
            .map { it.asJsonObject }
            .first { it.get("id").asString == "malformed-compressed-payload" }
        val payloads = testCase.getAsJsonArray("payloads").map { it.asString.toByteArray() }
        every { mockClient.waitForNotification() } returns flowOf(restApiNotification(payloads, uncompressed = false))

        try {
            mockClient.receiveRestApiEventData("device-id").first()
            fail("Expected malformed compressed REST event payload to terminate with IOException")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().isNotEmpty())
        }
    }

    @Test
    fun restServiceGoldenVectors_followNeutralKmpShape() {
        val vectors = loadRestServiceVectors()
        assertTrue("Expected REST service golden vectors", vectors.isNotEmpty())
        vectors.forEach { vector ->
            val id = vector.get("id").asString
            assertTrue(id, vector.has("area"))
            assertTrue(id, vector.has("case"))
            assertTrue(id, vector.has("source"))
            assertTrue(id, vector.has("input"))
            assertTrue(id, vector.has("expected"))
            assertTrue(id, vector.has("platforms"))
            assertTrue(id, vector.getAsJsonObject("input").has("kind"))
            val platforms = vector.getAsJsonObject("platforms")
            assertTrue(id, platforms.get("android").asBoolean)
            assertTrue(id, platforms.get("ios").asBoolean)
            assertTrue(id, platforms.get("common").asBoolean)
        }
    }

    @Test
    fun restRequestTransportPolicyVector_isPinnedBeforeRuntimeMigration() {
        val vector = loadRestServiceVectors().first { it.get("id").asString == "rest-request-transport-policy" }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val commonRuntimePrototype = expected.getAsJsonObject("commonRuntimePrototype")
        val consumerTests = vector.getAsJsonObject("consumerTests")

        assertTrue("rest-request-transport-policy", vector.has("execution"))
        assertEquals(REST_REQUEST_TRANSPORT_SCENARIO_IDS, input.getAsJsonArray("requests").map { it.asJsonObject.get("id").asString })
        assertEquals(REST_REQUEST_TRANSPORT_SCENARIO_IDS, commonRuntimePrototype.getAsJsonArray("cases").map { it.asJsonObject.get("id").asString })
        assertEquals(REST_REQUEST_TRANSPORT_MIGRATION_REQUIREMENT, expected.get("migrationRequirement").asString)
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarDeviceRestApiUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("PolarDeviceRestApiTests"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.RestRequestTransportPolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun restRequestTransportReadinessManifest_isPinnedBeforeRuntimeMigration() {
        val vector = loadRestServiceVectors().first { it.get("id").asString == "rest-request-transport-readiness" }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val consumerTests = vector.getAsJsonObject("consumerTests")

        assertEquals("rest-request-transport-readiness", vector.get("id").asString)
        assertEquals("restRequestTransportReadiness", input.get("kind").asString)
        assertEquals("sdk/rest-service/rest-request-transport-policy.json", input.get("policyVectorPath").asString)
        assertEquals(REST_REQUEST_TRANSPORT_READINESS_FAMILIES, requiredFamilies)
        assertEquals(REST_REQUEST_TRANSPORT_READINESS_FAMILIES, coveredFamilies)
        assertEquals(REST_REQUEST_TRANSPORT_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        val commonRuntimePrototype = expected.getAsJsonObject("commonRuntimePrototype")
        assertEquals("executable shared commonTest runtime planning guard", commonRuntimePrototype.get("status").asString)
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", commonRuntimePrototype.get("reason").asString)
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarDeviceRestApiUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("PolarDeviceRestApiTests"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.RestRequestTransportPolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun restEventCompressionReadinessManifest_isPinnedBeforeCodecMigration() {
        val vector = loadRestServiceVectors().first { it.get("id").asString == "rest-event-compression-readiness" }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }
        val consumerTests = vector.getAsJsonObject("consumerTests")

        assertEquals("rest-event-compression-readiness", vector.get("id").asString)
        assertEquals("restEventCompressionReadiness", input.get("kind").asString)
        assertEquals("sdk/rest-service/rest-event-compression-platform-policy.json", input.get("policyVectorPath").asString)
        assertEquals(REST_EVENT_COMPRESSION_READINESS_FAMILIES, requiredFamilies)
        assertEquals(REST_EVENT_COMPRESSION_READINESS_FAMILIES, coveredFamilies)
        assertEquals(REST_EVENT_COMPRESSION_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarDeviceRestApiUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("PolarDeviceRestApiServiceTests"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.RestEventCompressionPolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun restServiceMappingReadinessManifest_isPinnedBeforeModelMigration() {
        val vector = loadRestServiceVectors().first { it.get("id").asString == "rest-service-mapping-readiness" }
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        assertEquals("restServiceMappingReadiness", input.get("kind").asString)
        assertEquals("compileVerifiedPreMigrationCharacterization", expected.get("migrationReadiness").asString)
        assertEquals(REST_SERVICE_MAPPING_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        assertEquals(REST_SERVICE_MAPPING_READINESS_FAMILIES, requiredFamilies)
        assertEquals(REST_SERVICE_MAPPING_READINESS_FAMILIES, coveredFamilies)
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarDeviceRestApiUtilsTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("PolarDeviceRestApiServiceTests"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.RestServiceMappingCommonPolicyTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun restApiNotification(payloads: List<ByteArray>, uncompressed: Boolean): BlePsFtpUtils.PftpNotificationMessage {
        val event = PbPftpDHRestApiEvent.newBuilder()
            .setUncompressed(uncompressed)
            .addAllEvent(payloads.map { ByteString.copyFrom(it) })
            .build()
        return BlePsFtpUtils.PftpNotificationMessage().apply {
            id = PbPFtpDevToHostNotification.REST_API_EVENT_VALUE
            byteArrayOutputStream = ByteArrayOutputStream().apply {
                write(event.toByteArray())
            }
        }
    }

    private fun ByteArray.gzip(): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(this)
        }
        return output.toByteArray()
    }

    private fun assertServiceList(caseId: String, actual: PolarDeviceRestApiServices, expected: JsonObject) {
        val expectedPaths = expected.getAsJsonObject("pathsForServices").entrySet().associate { it.key to it.value.asString }
        assertEquals("$caseId pathsForServices", expectedPaths, actual.pathsForServices)
        assertEquals("$caseId serviceNames", expected.getAsJsonArray("serviceNames").map { it.asString }.toSet(), actual.serviceNames.toSet())
        assertEquals("$caseId servicePaths", expected.getAsJsonArray("servicePaths").map { it.asString }.toSet(), actual.servicePaths.toSet())
    }

    private fun assertServiceDescription(caseId: String, actual: PolarDeviceRestApiServiceDescription, expected: JsonObject) {
        val expectedActions = expected.getAsJsonObject("actions").entrySet().associate { it.key to it.value.asString }
        assertEquals("$caseId events", expected.getAsJsonArray("events").map { it.asString }, actual.events)
        assertEquals("$caseId endpoints", expected.getAsJsonArray("endpoints").map { it.asString }, actual.endpoints)
        assertEquals("$caseId actions", expectedActions, actual.actions)
        assertEquals("$caseId shared projection events", actual.sharedProjection.events, actual.events)
        assertEquals("$caseId shared projection endpoints", actual.sharedProjection.endpoints, actual.endpoints)
        assertEquals("$caseId shared projection actions", actual.sharedProjection.actions, actual.actions)
        assertEquals("$caseId actionNames", expectedActions.keys, actual.actionNames.toSet())
        assertEquals("$caseId actionPaths", expectedActions.values.toSet(), actual.actionPaths.toSet())
        expected.getAsJsonObject("eventDetails").entrySet().forEach { entry ->
            assertEquals("$caseId details ${entry.key}", entry.value.asJsonArray.map { it.asString }, actual.eventDetailsFor(entry.key))
        }
        expected.getAsJsonObject("eventTriggers").entrySet().forEach { entry ->
            assertEquals("$caseId triggers ${entry.key}", entry.value.asJsonArray.map { it.asString }, actual.eventTriggersFor(entry.key))
        }
    }

    private fun androidExpected(expected: JsonObject): JsonObject {
        return if (expected.has("android")) expected.getAsJsonObject("android") else expected
    }

    private fun parseJsonMap(json: String): Map<String, Any> {
        return Gson().fromJson(json, object : TypeToken<Map<String, Any>>() {}.type)
    }

    private fun loadRestServiceVectors(): List<JsonObject> {
        val vectorDirectory = findRepositoryRoot()
            .resolve("testdata/golden-vectors/sdk/rest-service")
        return vectorDirectory
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { file ->
                FileReader(file).use { reader ->
                    JsonParser().parse(reader).asJsonObject
                }
            }
    }

    private fun findRepositoryRoot(): File {
        val userDirectory = System.getProperty("user.dir") ?: error("user.dir is not set")
        var directory = File(userDirectory).absoluteFile
        while (true) {
            if (directory.resolve("testdata/golden-vectors").isDirectory) {
                return directory
            }
            directory = directory.parentFile ?: error("Could not find repository root from $userDirectory")
        }
    }

    private companion object {
        val REST_SERVICE_MAPPING_READINESS_POLICY_VECTOR_PATHS = listOf(
            "sdk/rest-service/service-list-basic.json",
            "sdk/rest-service/service-list-empty.json",
            "sdk/rest-service/service-description-training.json",
            "sdk/rest-service/service-description-empty.json",
            "sdk/rest-service/service-list-wrong-type-platform-policy.json"
        )

        val REST_REQUEST_TRANSPORT_READINESS_FAMILIES = listOf(
            "service-list-get-path",
            "service-description-get-path",
            "response-error-payload-status",
            "response-error-payload-message",
            "empty-successful-response-policy-gate",
            "fake-pftp-request-harness-gate",
            "facade-error-mapping-deferred",
            "platform-transport-vector-reference-gate",
            "compile-verification-gate"
        )

        val REST_REQUEST_TRANSPORT_SCENARIO_IDS = listOf(
            "service-list-request-error-payload",
            "service-description-request-error-payload",
            "service-list-empty-transport-response",
            "service-description-empty-transport-response"
        )

        const val REST_REQUEST_TRANSPORT_MIGRATION_REQUIREMENT = "Before moving REST request orchestration into common KMP code, implement a fake PFTP request harness that can inject response-error payloads and byte-for-byte empty successful responses for service discovery and service-description reads."

        const val REST_REQUEST_TRANSPORT_READINESS_COMMON_DECISION = "REST request transport migration may proceed only after rest-request-transport-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS REST tests continue to reference the same vectors, service-list and service-description GET paths remain pinned, response-error status and message mapping stay covered, empty successful responses are deliberately normalized or deliberately preserved as platform facade behavior, public facade error mapping remains explicit, and the shared tests are compile-verified."

        val REST_EVENT_COMPRESSION_READINESS_FAMILIES = listOf(
            "uncompressed-batch-payload-preservation",
            "empty-uncompressed-batch-emission",
            "compressed-batch-platform-codec-split",
            "android-gzip-codec-reference-gate",
            "ios-deflate-codec-reference-gate",
            "malformed-compressed-payload-platform-split",
            "notification-payload-order-gate",
            "normalize-or-preserve-codec-decision-gate",
            "platform-event-vector-reference-gate",
            "compile-verification-gate"
        )

        const val REST_EVENT_COMPRESSION_READINESS_COMMON_DECISION = "REST event compression migration may proceed only after rest-event-compression-platform-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS event tests continue to reference the same vectors, uncompressed and empty batches preserve current payload semantics, Android gzip and iOS deflate behavior is deliberately normalized or deliberately preserved, malformed compressed payload handling remains explicit for both platforms, notification payload order is pinned, and the shared tests are compile-verified."

        val REST_SERVICE_MAPPING_READINESS_FAMILIES = listOf(
            "service-list-name-path-mapping",
            "service-list-empty-defaults",
            "service-description-action-event-mapping",
            "service-description-empty-defaults",
            "wrong-type-services-platform-split",
            "unknown-field-ignore-policy",
            "platform-rest-service-vector-references",
            "compile-verification-gate"
        )
    }
}
