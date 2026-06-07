package com.polar.sharedtest

import com.polar.shared.sdk.PolarRestServiceModels
import kotlin.test.Test
import kotlin.test.assertEquals

class RestEventCompressionPolicyCommonTest {
    @Test
    fun restEventCompressionGoldenVectorDefinesExecutableCommonCodecOwnershipPolicy() {
        val vector = loadGoldenVectorText("sdk/rest-service/rest-event-compression-platform-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val caseList = input.objectArray("cases")
        val cases = caseList.associateBy { testCase -> testCase.stringValue("id") }

        assertEquals("rest-event-compression-platform-policy", vector.stringValue("id"))
        assertEquals("restEventCompression", input.stringValue("kind"))
        assertEquals(requiredRestEventCompressionCaseIds, caseList.map { it.stringValue("id") })

        val uncompressed = cases.getValue("uncompressed-batch")
        assertEquals(true, uncompressed.booleanValue("uncompressed"), "uncompressed-batch")
        assertEquals(uncompressed.stringArrayValue("payloads"), decodeRestEventPayloads(uncompressed), "uncompressed-batch")
        val empty = cases.getValue("empty-uncompressed-batch")
        assertEquals(emptyList(), decodeRestEventPayloads(empty), "empty-uncompressed-batch")
        val compressed = cases.getValue("compressed-batch")
        assertEquals(false, compressed.booleanValue("uncompressed"), "compressed-batch")
        assertEquals("gzip", compressed.objectValue("compression").stringValue("android"), "compressed-batch Android codec")
        assertEquals("deflate", compressed.objectValue("compression").stringValue("ios"), "compressed-batch iOS codec")
        assertEquals(compressed.stringArrayValue("payloads"), decodeRestEventPayloads(compressed), "compressed-batch shared JVM codec")
        val malformed = cases.getValue("malformed-compressed-payload")
        assertEquals("throws-ioexception", malformed.objectValue("expected").stringValue("android"), "malformed Android policy")
        assertEquals("emits-original-payload", malformed.objectValue("expected").stringValue("ios"), "malformed iOS policy")

        assertEquals("emit-original-utf8-payloads", expected.stringValue("payloadPolicy"), vector.stringValue("id"))
        assertEquals("preserve-notification-payload-order-and-emit-empty-batches", expected.stringValue("batchPolicy"), vector.stringValue("id"))
        assertEquals(restEventCompressionMalformedAndroidPolicy, expected.objectValue("malformedCompressedPayloadPolicy").stringValue("android"), vector.stringValue("id"))
        assertEquals(restEventCompressionMalformedIosPolicy, expected.objectValue("malformedCompressedPayloadPolicy").stringValue("ios"), vector.stringValue("id"))
        assertEquals(restEventCompressionCommonCodecDecision, vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("compressionPolicy"), vector.stringValue("id"))
    }

    @Test
    fun restEventCompressionReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/rest-service/rest-event-compression-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPath = input.stringValue("policyVectorPath")
        val policy = loadGoldenVectorText(policyVectorPath)
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val consumerTests = manifest.objectValue("consumerTests")
        assertEquals("rest-event-compression-readiness", manifest.stringValue("id"))
        assertEquals("restEventCompressionReadiness", input.stringValue("kind"))
        assertEquals("sdk/rest-service/rest-event-compression-platform-policy.json", policyVectorPath)
        assertEquals(requiredRestEventCompressionFamilies, requiredFamilies)
        assertEquals(requiredRestEventCompressionFamilies, coveredFamilies)
        assertEquals(restEventCompressionReadinessDecision, expected.stringValue("commonDecision"))
        assertEquals("rest-event-compression-platform-policy", policy.stringValue("id"))
        assertEquals(requiredRestEventCompressionCaseIds, policy.objectValue("input").objectArray("cases").map { it.stringValue("id") })
        assertEquals(listOf("com.polar.sdk.api.model.utils.PolarDeviceRestApiUtilsTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("PolarDeviceRestApiServiceTests"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.RestEventCompressionPolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    @Test
    fun restEventCompressionCompressedBatchUsesPolarRestEventCompressionCodec() {
        val vector = loadGoldenVectorText("sdk/rest-service/rest-event-compression-platform-policy.json")
        val compressed = vector.objectValue("input").objectArray("cases").first { testCase -> testCase.stringValue("id") == "compressed-batch" }

        assertEquals(compressed.stringArrayValue("payloads"), decodeRestEventPayloads(compressed))
    }

    private val requiredRestEventCompressionCaseIds = listOf(
        "uncompressed-batch",
        "empty-uncompressed-batch",
        "compressed-batch",
        "malformed-compressed-payload"
    )

    private val requiredRestEventCompressionFamilies = listOf(
        "uncompressed-batch-payload-preservation",
        "empty-uncompressed-batch-emission",
        "compressed-batch-platform-codec-split",
        "android-gzip-codec-reference-gate",
        "ios-deflate-codec-reference-gate",
        "malformed-compressed-payload-platform-split",
        "notification-payload-order-gate",
        "shared-platform-actual-codec-gate",
        "platform-event-vector-reference-gate",
        "compile-verification-gate"
    )

    private val restEventCompressionMalformedAndroidPolicy = "throws IOException from GZIPInputStream and terminates the flow collection"

    private val restEventCompressionMalformedIosPolicy = "logs inflate failure and emits the original payload bytes"

    private val restEventCompressionCommonCodecDecision = "Shared KMP owns REST event compression dispatch while platform actuals deliberately preserve Android GZIPInputStream-compatible gzip decoding and iOS zlib inflate behavior."

    private val restEventCompressionReadinessDecision = "REST event compression migration may proceed only after rest-event-compression-platform-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS event tests continue to reference the same vectors, uncompressed and empty batches preserve current payload semantics, Android gzip and iOS deflate behavior are preserved through shared KMP platform actual codecs, malformed compressed payload handling remains explicit for both platforms, notification payload order is pinned, and the shared tests are compile-verified."

    private fun decodeRestEventPayloads(testCase: String): List<String> {
        return if (testCase.booleanValue("uncompressed")) {
            PolarRestServiceModels.restEventPayloads(
                uncompressed = true,
                payloads = testCase.stringArrayValue("payloads").map { payload -> payload.encodeToByteArray() }
            ).map { payload -> payload.decodeToString() }
        } else {
            PolarRestServiceModels.restEventPayloads(
                uncompressed = false,
                payloads = testCase.objectValue("compressedPayloadsHex").stringArrayValue("android").map(::hexToBytes)
            ).map { payload -> payload.decodeToString() }
        }
    }

    private fun String.booleanValue(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" } ?: error("Missing boolean field $field in $this")
    }
}
