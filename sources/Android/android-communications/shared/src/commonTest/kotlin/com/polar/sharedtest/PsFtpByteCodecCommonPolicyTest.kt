package com.polar.sharedtest

import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import kotlin.test.Test
import kotlin.test.assertEquals

class PsFtpByteCodecCommonPolicyTest {
    @Test
    fun psFtpRfc76FrameGoldenVectorsDecodeHeaderPayloadAndErrorPolicy() {
        RFC76_FRAME_VECTORS.forEachIndexed { index, path ->
            val vector = loadGoldenVectorText(path)
            val frame = decodeRfc76Frame(hexToBytes(vector.objectValue("input").stringValue("frameHex")))
            val expected = vector.objectValue("expected")

            assertEquals(RFC76_FRAME_IDS[index], vector.stringValue("id"), path)
            assertEquals(RFC76_FRAME_CASES[index], vector.stringValue("case"), path)
            assertCommonPsFtpConsumerAndPlatformShape(vector)
            assertEquals(expected.intValue("next"), frame.next, vector.stringValue("id"))
            assertEquals(expected.intValue("status"), frame.status, vector.stringValue("id"))
            assertEquals(expected.intValue("sequenceNumber"), frame.sequenceNumber, vector.stringValue("id"))
            assertEquals(expected.optionalStringValue("payloadHex"), frame.payload.toHexOrNull(), vector.stringValue("id"))

            if (expected.nullValue("error")) {
                assertEquals(null, frame.androidErrorCode, vector.stringValue("id"))
                assertEquals(null, frame.iosErrorCode, vector.stringValue("id"))
            } else {
                val error = expected.objectValue("error")
                assertEquals(error.intValue("android"), frame.androidErrorCode, vector.stringValue("id"))
                assertEquals(error.intValue("ios"), frame.iosErrorCode, vector.stringValue("id"))
                assertEquals("android-currently-masks-shifted-high-byte-while-ios-uses-little-endian-uint16", vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("errorCodePolicy"))
            }
        }
    }

    @Test
    fun psFtpCompleteMessageStreamGoldenVectorDefinesExecutableRfc60EncodingPolicy() {
        val vector = loadGoldenVectorText("sdk/psftp-message-stream/complete-message-streams.json")
        assertEquals("complete-message-streams", vector.stringValue("id"))
        assertEquals("sdk.psftp-message-stream", vector.stringValue("area"))
        assertEquals("complete_message_streams", vector.stringValue("case"))
        assertCommonPsFtpConsumerAndPlatformShape(vector)
        assertEquals("encode-rfc60-complete-message-streams", vector.objectValue("expected").stringValue("policy"))

        val cases = vector.objectValue("input").objectArray("cases")
        assertEquals(COMPLETE_MESSAGE_CASE_IDS, cases.map { testCase -> testCase.stringValue("id") }, vector.stringValue("id"))
        cases.forEach { testCase ->
            val encoded = encodeCompleteMessageStream(
                type = testCase.stringValue("type"),
                header = hexToBytes(testCase.stringValue("headerHex")),
                idValue = testCase.intValue("idValue"),
                data = testCase.optionalStringValue("dataHex")?.let(::hexToBytes) ?: ByteArray(0)
            )

            assertEquals(testCase.stringValue("expectedHex"), encoded.toHex(), testCase.stringValue("id"))
            if (testCase.stringValue("id") == "android-request-with-file-data") {
                assertEquals("Android makeCompleteMessageStream appends file data after the protobuf header while preserving the RFC60 length as header length only; current iOS utility has no file-data parameter.", testCase.stringValue("commonDecision"), testCase.stringValue("id"))
                assertEquals(true, testCase.objectValue("platforms").booleanValue("android"), testCase.stringValue("id"))
                assertEquals(false, testCase.objectValue("platforms").booleanValue("ios"), testCase.stringValue("id"))
            }
        }
    }

    @Test
    fun psFtpRfc76FrameSplittingGoldenVectorDefinesExecutableMtuAndSequencePolicy() {
        val vector = loadGoldenVectorText("sdk/psftp-message-stream/rfc76-frame-splitting.json")
        assertEquals("rfc76-frame-splitting", vector.stringValue("id"))
        assertEquals("sdk.psftp-message-stream", vector.stringValue("area"))
        assertEquals("rfc76_frame_splitting", vector.stringValue("case"))
        assertCommonPsFtpConsumerAndPlatformShape(vector)
        assertEquals("split-rfc76-message-frames", vector.objectValue("expected").stringValue("policy"))

        val cases = vector.objectValue("input").objectArray("cases")
        assertEquals(RFC76_FRAME_SPLITTING_CASE_IDS, cases.map { testCase -> testCase.stringValue("id") }, vector.stringValue("id"))
        cases.forEach { testCase ->
            val frames = splitRfc76Frames(
                payload = hexToBytes(testCase.stringValue("payloadHex")),
                mtu = testCase.intValue("mtu")
            )

            assertEquals(testCase.stringArrayValue("expectedFramesHex"), frames.map { frame -> frame.toHex() }, testCase.stringValue("id"))
        }
    }

    @Test
    fun psFtpRfc76FrameChunkEncoderDefinesSharedProductionHeaderPolicy() {
        assertEquals("060a0b0c", PolarWorkflowRuntimePlanning.encodeRfc76FrameChunk(hexToBytes("0a0b0c"), hasMore = true, next = 0, sequenceNumber = 0).toHex())
        assertEquals("97010203", PolarWorkflowRuntimePlanning.encodeRfc76FrameChunk(hexToBytes("010203"), hasMore = true, next = 1, sequenceNumber = 9).toHex())
        assertEquals("c3", PolarWorkflowRuntimePlanning.encodeRfc76FrameChunk(ByteArray(0), hasMore = false, next = 1, sequenceNumber = 12).toHex())
        assertEquals("03", PolarWorkflowRuntimePlanning.encodeRfc76FrameChunk(ByteArray(0), hasMore = false, next = 1, sequenceNumber = 16).toHex())
    }

    @Test
    fun psFtpRequestWriteFrameSplittingCombinesHeaderAndFilePayloadInSharedProductionPolicy() {
        val frames = PolarWorkflowRuntimePlanning.splitRfc76RequestWriteFrames(
            header = hexToBytes("010203"),
            data = hexToBytes("aabbccdd"),
            mtu = 4
        )

        assertEquals(listOf("06030001", "170203aa", "23bbccdd"), frames.map { frame -> frame.toHex() })
    }

    @Test
    fun psFtpWriteTimeoutPolicySelectsSharedExtendedPathPrefix() {
        assertEquals(900, PolarWorkflowRuntimePlanning.psFtpWriteTimeoutSeconds("/SYNCPART.TGZ"))
        assertEquals(900, PolarWorkflowRuntimePlanning.psFtpWriteTimeoutSeconds("/SYNCPART.TGZ/part0"))
        assertEquals(90, PolarWorkflowRuntimePlanning.psFtpWriteTimeoutSeconds("/U/0/S/UDEVSET.BPB"))
        assertEquals(30, PolarWorkflowRuntimePlanning.psFtpWriteTimeoutSeconds("/SYNCPART.TGZ", defaultTimeoutSeconds = 30, extendedTimeoutSeconds = 120, extendedPathPrefixes = listOf("/FW/")))
        assertEquals(120, PolarWorkflowRuntimePlanning.psFtpWriteTimeoutSeconds("/FW/PACKAGE.BIN", defaultTimeoutSeconds = 30, extendedTimeoutSeconds = 120, extendedPathPrefixes = listOf("/FW/")))
    }

    @Test
    fun psFtpByteCodecReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/psftp-message-stream/byte-codec-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val policyVectorPaths = input.stringArrayValue("policyVectorPaths")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        assertEquals("psftp-byte-codec-readiness", manifest.stringValue("id"))
        assertEquals("psFtpByteCodecReadiness", input.stringValue("kind"))
        assertEquals(requiredByteCodecPolicyPaths, policyVectorPaths)
        assertEquals(requiredByteCodecFamilies, requiredFamilies)
        assertEquals(requiredByteCodecFamilies, coveredFamilies)
        requiredByteCodecPolicyPaths.forEachIndexed { index, path ->
            val policy = loadGoldenVectorText(path)
            assertEquals(requiredByteCodecPolicyIds[index], policy.stringValue("id"), path)
            assertEquals(requiredByteCodecPolicyCases[index], policy.stringValue("case"), path)
            assertCommonPsFtpConsumerAndPlatformShape(policy)
        }
        assertEquals(PSFTP_BYTE_CODEC_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals(
            listOf("com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtilsTest"),
            consumerTests.stringArrayValue("android")
        )
        assertEquals(listOf("BlePsFtpUtilityTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.PsFtpByteCodecCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private val requiredByteCodecPolicyPaths = RFC76_FRAME_VECTORS + listOf(
        "sdk/psftp-message-stream/complete-message-streams.json",
        "sdk/psftp-message-stream/rfc76-frame-splitting.json"
    )

    private val requiredByteCodecFamilies = listOf(
        "rfc76-header-next-bit",
        "rfc76-status-decoding",
        "rfc76-sequence-number-decoding",
        "rfc76-payload-slicing",
        "rfc76-error-frame-platform-split",
        "rfc60-request-stream-encoding",
        "rfc60-query-stream-encoding",
        "rfc60-notification-stream-encoding",
        "android-request-file-data-append-policy",
        "ios-request-write-frame-splitting",
        "rfc76-mtu-frame-splitting",
        "rfc76-sequence-wrap",
        "platform-codec-vector-reference-gate",
        "compile-verification-gate"
    )

    private fun decodeRfc76Frame(frame: ByteArray): DecodedRfc76Frame {
        val frame = PolarWorkflowRuntimePlanning.decodeRfc76Frame(frame)
        return DecodedRfc76Frame(
            next = frame.next,
            status = frame.status,
            sequenceNumber = frame.sequenceNumber,
            payload = frame.payload,
            androidErrorCode = frame.androidErrorCode,
            iosErrorCode = frame.iosErrorCode
        )
    }

    private fun encodeCompleteMessageStream(type: String, header: ByteArray, idValue: Int, data: ByteArray): ByteArray {
        return PolarWorkflowRuntimePlanning.encodeCompleteMessageStream(type, header, idValue, data)
    }

    private fun splitRfc76Frames(payload: ByteArray, mtu: Int): List<ByteArray> {
        return PolarWorkflowRuntimePlanning.splitRfc76Frames(payload, mtu)
    }

    private data class DecodedRfc76Frame(
        val next: Int,
        val status: Int,
        val sequenceNumber: Int,
        val payload: ByteArray?,
        val androidErrorCode: Int?,
        val iosErrorCode: Int?
    )

    private fun ByteArray?.toHexOrNull(): String? {
        return this?.toHex()
    }

    private fun assertCommonPsFtpConsumerAndPlatformShape(vector: String) {
        val consumerTests = vector.objectValue("consumerTests")
        assertEquals(listOf("com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtilsTest"), consumerTests.stringArrayValue("android"), vector.stringValue("id"))
        assertEquals(listOf("BlePsFtpUtilityTest"), consumerTests.stringArrayValue("ios"), vector.stringValue("id"))
        assertEquals(listOf("com.polar.sharedtest.PsFtpByteCodecCommonPolicyTest"), consumerTests.stringArrayValue("commonPrototype"), vector.stringValue("id"))
        val platforms = vector.objectValue("platforms")
        assertEquals(true, platforms.booleanValue("android"), vector.stringValue("id"))
        assertEquals(true, platforms.booleanValue("ios"), vector.stringValue("id"))
        assertEquals(true, platforms.booleanValue("common"), vector.stringValue("id"))
    }

    private fun String.nullValue(field: String): Boolean {
        return Regex("\"$field\"\\s*:\\s*null").containsMatchIn(this)
    }

    private fun ByteArray.toHex(): String {
        return joinToString(separator = "") { byte ->
            val value = byte.toInt() and 0xFF
            val high = value / 16
            val low = value % 16
            "${high.toHexDigit()}${low.toHexDigit()}"
        }
    }

    private fun Int.toHexDigit(): Char {
        return if (this < 10) {
            '0' + this
        } else {
            'a' + (this - 10)
        }
    }

    private companion object {
        val RFC76_FRAME_VECTORS = listOf(
            "sdk/psftp-rfc76/error-frame-ffff.json",
            "sdk/psftp-rfc76/final-last-frame.json",
            "sdk/psftp-rfc76/first-more-frame.json",
            "sdk/psftp-rfc76/header-only-last-frame.json",
            "sdk/psftp-rfc76/header-only-more-frame.json",
            "sdk/psftp-rfc76/middle-more-frame.json",
            "sdk/psftp-rfc76/single-last-frame.json"
        )
        val RFC76_FRAME_IDS = listOf(
            "error-frame-ffff",
            "final-last-frame",
            "first-more-frame",
            "header-only-last-frame",
            "header-only-more-frame",
            "middle-more-frame",
            "single-last-frame"
        )
        val RFC76_FRAME_CASES = listOf(
            "error_frame_ffff",
            "final_last_frame",
            "first_more_frame",
            "header_only_last_frame",
            "header_only_more_frame",
            "middle_more_frame",
            "single_last_frame"
        )
        val COMPLETE_MESSAGE_CASE_IDS = listOf(
            "request-header-only",
            "android-request-with-file-data",
            "query-with-header",
            "notification-with-header",
            "notification-empty-header"
        )
        val RFC76_FRAME_SPLITTING_CASE_IDS = listOf(
            "empty-payload",
            "exactly-one-frame",
            "two-frames",
            "sequence-wraps-after-fifteen"
        )
        val requiredByteCodecPolicyIds = RFC76_FRAME_IDS + listOf(
            "complete-message-streams",
            "rfc76-frame-splitting"
        )
        val requiredByteCodecPolicyCases = RFC76_FRAME_CASES + listOf(
            "complete_message_streams",
            "rfc76_frame_splitting"
        )
        const val PSFTP_BYTE_CODEC_READINESS_COMMON_DECISION = "PSFTP byte-codec migration may proceed only after every RFC76 and RFC60 vector listed in this readiness manifest is executable from shared commonTest, Android and iOS codec tests continue to reference the same vectors, header next/status/sequence/payload decoding, RFC76 error-frame platform split, complete-message stream encoding, Android file-data append behavior, iOS request write frame splitting, MTU frame splitting, sequence wrap, and the shared tests are compile-verified."
    }
}
