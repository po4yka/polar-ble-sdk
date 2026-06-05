package com.polar.sharedtest

import com.polar.shared.runtime.PolarPsFtpContinuationTimeout
import com.polar.shared.runtime.PolarPsFtpNotificationPacket
import com.polar.shared.runtime.PolarPsFtpNotification
import com.polar.shared.runtime.PolarPsFtpResponseError
import com.polar.shared.runtime.PolarPsFtpTransportWriteFailure
import com.polar.shared.runtime.PolarPsFtpWriteAckTimeout
import com.polar.shared.runtime.PolarWorkflowPlan
import com.polar.shared.runtime.PolarWorkflowRuntimePlanning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PsFtpRuntimePolicyCommonTest {
    @Test
    fun commonFakeResponseRuntimeReassemblesRequestResponses() {
        val vector = loadGoldenVectorText("sdk/psftp-response/request-response-reassembly.json")
        val cases = vector.objectValue("input").objectArray("cases")
        assertEquals(REQUEST_RESPONSE_REASSEMBLY_CASE_IDS, cases.map { testCase -> testCase.stringValue("id") }, vector.stringValue("id"))

        cases.forEach { testCase ->
            val response = PolarWorkflowRuntimePlanning.reassembleRequestResponse(testCase.stringArrayValue("responseFramesHex").map(::hexToBytes))
            assertEquals(testCase.objectValue("expected").stringValue("payloadHex"), response.toHex(), testCase.stringValue("id"))
        }
    }

    @Test
    fun commonFakeResponseRuntimeMapsResponseErrorFramesToTypedResponseError() {
        val vector = loadGoldenVectorText("sdk/psftp-response/request-response-error-policy.json")
        val cases = vector.objectValue("input").objectArray("cases")
        assertEquals(REQUEST_RESPONSE_ERROR_CASE_IDS, cases.map { testCase -> testCase.stringValue("id") }, vector.stringValue("id"))

        cases.forEach { testCase ->
            val thrown = assertFailsWith<PolarPsFtpResponseError>(testCase.stringValue("id")) {
                PolarWorkflowRuntimePlanning.reassembleRequestResponse(testCase.stringArrayValue("responseFramesHex").map(::hexToBytes))
            }
            assertEquals(testCase.objectValue("ios").intValue("errorCode"), thrown.errorCode, testCase.stringValue("id"))
        }
    }

    @Test
    fun commonFakeNotificationRuntimeReassemblesCompleteNotifications() {
        val vector = loadGoldenVectorText("sdk/psftp-notifications/notification-reassembly.json")
        val cases = vector.objectValue("input").objectArray("cases")
        assertEquals(NOTIFICATION_REASSEMBLY_CASE_IDS, cases.map { testCase -> testCase.stringValue("id") }, vector.stringValue("id"))

        cases.forEach { testCase ->
            val notification = PolarWorkflowRuntimePlanning.reassembleNotifications(testCase.stringArrayValue("framesHex").map { frame ->
                PolarPsFtpNotificationPacket(frame = hexToBytes(frame), transportStatus = 0)
            }).single()
            val expected = testCase.objectValue("expected")
            assertEquals(expected.intValue("id"), notification.id, testCase.stringValue("id"))
            assertEquals(expected.stringValue("parametersHex"), notification.parameters.toHex(), testCase.stringValue("id"))
        }
    }

    @Test
    fun commonFakeNotificationRuntimePreservesCompleteNotificationOrdering() {
        val vector = loadGoldenVectorText("sdk/psftp-notifications/notification-ordering.json")
        val cases = vector.objectValue("input").objectArray("cases")
        assertEquals(NOTIFICATION_ORDERING_CASE_IDS, cases.map { testCase -> testCase.stringValue("id") }, vector.stringValue("id"))

        cases.forEach { testCase ->
            val notifications = PolarWorkflowRuntimePlanning.reassembleNotifications(testCase.objectArray("packets").map { packet ->
                PolarPsFtpNotificationPacket(
                    frame = hexToBytes(packet.stringValue("frameHex")),
                    transportStatus = packet.intValue("status")
                )
            })
            val expectedSequence = testCase.objectValue("expected").objectArray("sequence")
            assertEquals(expectedSequence.size, notifications.size, testCase.stringValue("id"))
            expectedSequence.forEachIndexed { index, expected ->
                assertEquals(expected.intValue("id"), notifications[index].id, testCase.stringValue("id"))
                assertEquals(expected.stringValue("parametersHex"), notifications[index].parameters.toHex(), testCase.stringValue("id"))
            }
        }
    }

    @Test
    fun commonFakeNotificationRuntimePreservesInitialSilenceAsNoEmissionWithoutBuiltInTimeout() {
        val vector = loadGoldenVectorText("sdk/psftp-notifications/notification-timeout-policy.json")
        assertEquals("wait-notification-has-no-built-in-initial-silence-timeout", vector.objectValue("expected").stringValue("policy"))

        val cases = vector.objectValue("input").objectArray("cases")
        assertEquals(NOTIFICATION_TIMEOUT_CASE_IDS, cases.map { testCase -> testCase.stringValue("id") }, vector.stringValue("id"))

        cases.forEach { testCase ->
            val notifications = PolarWorkflowRuntimePlanning.reassembleNotifications(testCase.objectArray("packets").map { packet ->
                PolarPsFtpNotificationPacket(
                    frame = hexToBytes(packet.stringValue("frameHex")),
                    transportStatus = packet.intValue("status")
                )
            })

            assertEquals("noEmission", testCase.objectValue("expected").objectValue("android").stringValue("outcome"), testCase.stringValue("id"))
            assertEquals("noEmission", testCase.objectValue("expected").objectValue("ios").stringValue("outcome"), testCase.stringValue("id"))
            assertTrue(notifications.isEmpty(), testCase.stringValue("id"))
        }
    }

    @Test
    fun commonFakeNotificationRuntimeConsumerTimeoutCleansObserverWithVirtualClock() {
        val vector = loadGoldenVectorText("sdk/psftp-notifications/notification-timeout-policy.json")
        val case = vector.objectValue("input").objectArray("cases").single()
        val expected = vector.objectValue("expected").objectValue("commonFakeClock")

        val outcome = PolarWorkflowRuntimePlanning.planConsumerTimeoutObserverCleanup(
            timeoutMs = case.intValue("observerTimeoutMs"),
            advanceMs = expected.intValue("advanceMs")
        )

        assertEquals(expected.stringValue("outcome"), "consumerTimeout")
        assertEquals("timeout", outcome.terminal)
        assertEquals(listOf("consumer-timeout:${case.intValue("observerTimeoutMs")}", "cleanup-observer"), outcome.commands)
        assertEquals(0, expected.intValue("activeObserverCountAfterTimeout"))
        assertEquals(1, expected.intValue("cleanupCallbackCount"))
    }

    @Test
    fun commonFakePsFtpHarnessPinsInitialSilenceTimeoutCleanupWithoutLeakedOperation() {
        val vector = loadGoldenVectorText("sdk/psftp-notifications/notification-timeout-policy.json")
        val case = vector.objectValue("input").objectArray("cases").single()
        val expected = vector.objectValue("expected").objectValue("commonFakeClock")
        val harness = CommonFakePsFtpRuntimeHarness()

        val terminal = harness.waitForNotificationWithConsumerTimeout(
            timeoutMs = case.intValue("observerTimeoutMs"),
            packets = case.objectArray("packets").map { packet ->
                PolarPsFtpNotificationPacket(
                    frame = hexToBytes(packet.stringValue("frameHex")),
                    transportStatus = packet.intValue("status")
                )
            }
        )

        assertEquals("consumerTimeout", expected.stringValue("outcome"))
        assertEquals("timeout", terminal)
        assertEquals(true, harness.descriptorEnabled)
        assertTrue(harness.independentD2hChannelPackets.isEmpty())
        assertTrue(harness.independentMtuChannelWrites.isEmpty())
        assertEquals(expected.intValue("activeObserverCountAfterTimeout"), harness.activeObserverCount)
        assertEquals(expected.intValue("cleanupCallbackCount"), harness.cleanupCallbackCount)
        assertEquals(0, harness.pendingOperationCount)
        assertEquals(listOf("scanner-pause", "scanner-resume"), harness.operationScopeCleanupEvents)
    }

    @Test
    fun commonFakeNotificationRuntimePinsRfc76ErrorAndTransportStatusPlatformSplit() {
        val vector = loadGoldenVectorText("sdk/psftp-notifications/notification-error-policy.json")
        assertEquals("notification-error-policy", vector.stringValue("id"))
        assertEquals("sdk.psftp-notifications", vector.stringValue("area"))
        assertEquals("notification_error_policy", vector.stringValue("case"))
        assertCommonRuntimePrototype(vector.objectValue("expected"))
        assertPsFtpRuntimeConsumerAndPlatformShape(vector)
        assertEquals("characterize-current-platform-notification-error-semantics", vector.objectValue("expected").stringValue("policy"))
        assertEquals("RFC76 error frames are ignored on both platforms. Nonzero transport status is a current platform split: Android drops the packet before the notification queue, while iOS terminates the wait-notification stream with a response error. KMP runtime migration must intentionally choose the shared policy and preserve or adapt platform facade compatibility.", vector.stringValue("notes"))

        vector.objectValue("input").objectArray("cases").forEach { testCase ->
            val notifications = PolarWorkflowRuntimePlanning.reassembleNotifications(testCase.objectArray("packets").map { packet ->
                PolarPsFtpNotificationPacket(
                    frame = hexToBytes(packet.stringValue("frameHex")),
                    transportStatus = packet.intValue("status")
                )
            })
            val expected = testCase.objectValue("expected")

            when (testCase.stringValue("id")) {
                "rfc76-error-first-frame" -> {
                    assertEquals("noEmission", expected.objectValue("android").stringValue("outcome"), testCase.stringValue("id"))
                    assertEquals("noEmission", expected.objectValue("ios").stringValue("outcome"), testCase.stringValue("id"))
                    assertTrue(notifications.isEmpty(), testCase.stringValue("id"))
                }
                "transport-error-first-packet" -> {
                    assertEquals("noEmission", expected.objectValue("android").stringValue("outcome"), testCase.stringValue("id"))
                    assertEquals("throwsResponseError", expected.objectValue("ios").stringValue("outcome"), testCase.stringValue("id"))
                    assertEquals(1, expected.objectValue("ios").intValue("errorCode"), testCase.stringValue("id"))
                    assertTrue(notifications.isEmpty(), testCase.stringValue("id"))
                }
                else -> error("Unhandled notification error policy case ${testCase.stringValue("id")}")
            }
        }
    }

    @Test
    fun commonFakeNotificationRuntimeMapsMissingLastAfterMoreToTypedContinuationTimeout() {
        val vector = loadGoldenVectorText("sdk/psftp-notifications/notification-continuation-timeout-policy.json")
        assertEquals("typedContinuationTimeout", vector.objectValue("common").stringValue("outcome"))
        val cases = vector.objectValue("input").objectArray("cases")
        assertEquals(NOTIFICATION_CONTINUATION_TIMEOUT_CASE_IDS, cases.map { testCase -> testCase.stringValue("id") }, vector.stringValue("id"))

        cases.forEach { testCase ->
            val thrown = assertFailsWith<PolarPsFtpContinuationTimeout>(testCase.stringValue("id")) {
                PolarWorkflowRuntimePlanning.reassembleNotifications(testCase.objectArray("packets").map { packet ->
                    PolarPsFtpNotificationPacket(
                        frame = hexToBytes(packet.stringValue("frameHex")),
                        transportStatus = packet.intValue("status")
                    )
                })
            }
            assertEquals(testCase.stringValue("id"), thrown.caseId)
        }
    }

    @Test
    fun commonFakePsFtpHarnessPinsContinuationTimeoutCleanupWithoutLeakedOperation() {
        val vector = loadGoldenVectorText("sdk/psftp-notifications/notification-continuation-timeout-policy.json")
        val testCase = vector.objectValue("input").objectArray("cases").single()
        val harness = CommonFakePsFtpRuntimeHarness()

        val thrown = assertFailsWith<PolarPsFtpContinuationTimeout> {
            harness.waitForNotificationContinuation(
                caseId = testCase.stringValue("id"),
                protocolTimeoutMs = 250,
                packets = testCase.objectArray("packets").map { packet ->
                    PolarPsFtpNotificationPacket(
                        frame = hexToBytes(packet.stringValue("frameHex")),
                        transportStatus = packet.intValue("status")
                    )
                }
            )
        }

        assertEquals(testCase.stringValue("id"), thrown.caseId)
        assertEquals(testCase.objectArray("packets").map { packet -> packet.stringValue("frameHex") }, harness.independentD2hChannelPackets.map { packet -> packet.frame.toHex() })
        assertTrue(harness.independentMtuChannelWrites.isEmpty())
        assertEquals(true, harness.descriptorEnabled)
        assertEquals(0, harness.activeObserverCount)
        assertEquals(1, harness.cleanupCallbackCount)
        assertEquals(0, harness.pendingOperationCount)
        assertEquals(listOf("scanner-pause", "scanner-resume"), harness.operationScopeCleanupEvents)
    }

    @Test
    fun commonFakeWriteRuntimeMapsPeerInterruptionToTypedResponseError() {
        val vector = loadGoldenVectorText("sdk/psftp-response/write-interruption-error-policy.json")
        val input = vector.objectValue("input")
        val thrown = assertFailsWith<PolarPsFtpResponseError> {
            PolarWorkflowRuntimePlanning.reassembleRequestResponse(listOf(hexToBytes(input.stringValue("interruptFrameHex"))))
        }

        assertEquals("write-interruption-error-policy", vector.stringValue("id"))
        assertEquals("sdk.psftp-response", vector.stringValue("area"))
        assertEquals("write_interruption_error_policy", vector.stringValue("case"))
        assertEquals("writeInterruptionErrorPolicy", input.stringValue("kind"))
        assertEquals(vector.objectValue("ios").intValue("errorCode"), thrown.errorCode)
        assertEquals("both platforms surface the device interruption as PFTP error code 103, but Android currently exposes BlePsFtpUtils.PftpResponseError while iOS exposes BlePsFtpException.responseError; common runtime should model this as a typed write-interrupted response error", vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("errorPolicy"))
        assertEquals("fake-psftp-runtime-policy", vector.objectValue("execution").stringValue("kind"))
        assertEquals("scripted-rfc76-write-stream", vector.objectValue("execution").stringValue("transport"))
        assertEquals(true, vector.objectValue("execution").booleanValue("wallClockSafe"))
        assertPsFtpRuntimeConsumerAndPlatformShape(vector)
    }

    @Test
    fun commonFakeWriteRuntimeMapsTransportTransmitFailureToTypedTransportError() {
        val vector = loadGoldenVectorText("sdk/psftp-response/write-transport-failure-policy.json")
        val input = vector.objectValue("input")
        val failure = input.objectValue("failure")
        val thrown = assertFailsWith<PolarPsFtpTransportWriteFailure> {
            PolarWorkflowRuntimePlanning.planPsFtpWrite(
                payload = hexToBytes(input.stringValue("payloadHex")),
                transportTransmit = "failure",
                writeAck = "never",
                failureMessage = failure.stringValue("message")
            )
        }

        assertEquals(failure.stringValue("message"), thrown.message)
        assertEquals("write-transport-failure-policy", vector.stringValue("id"))
        assertEquals("sdk.psftp-response", vector.stringValue("area"))
        assertEquals("write_transport_failure_policy", vector.stringValue("case"))
        assertEquals("writeTransportFailurePolicy", input.stringValue("kind"))
        assertEquals("both platforms currently surface first-packet transport write failure as the underlying transport error rather than a PFTP response error", vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("errorPolicy"))
        assertEquals("fake-psftp-runtime-policy", vector.objectValue("execution").stringValue("kind"))
        assertEquals("scripted-rfc76-write-stream", vector.objectValue("execution").stringValue("transport"))
        assertEquals(true, vector.objectValue("execution").booleanValue("wallClockSafe"))
        assertPsFtpRuntimeConsumerAndPlatformShape(vector)
    }

    @Test
    fun commonFakeWriteRuntimePinsPlatformProgressSplitBeforeSharedPolicyChoice() {
        val vector = loadGoldenVectorText("sdk/psftp-response/write-success-progress.json")
        val expected = vector.objectValue("expected")
        val decision = vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("progressPolicy")
        val payloadSize = hexToBytes(vector.objectValue("input").stringValue("payloadHex")).size

        assertEquals(listOf(-14, payloadSize), expected.objectValue("android").signedIntArrayValue("progress"))
        assertEquals(listOf(0, payloadSize - 1, payloadSize), expected.objectValue("ios").signedIntArrayValue("progress"))
        assertEquals(expected.objectValue("android").signedIntArrayValue("progress"), PolarWorkflowRuntimePlanning.planPsFtpWriteProgress(payloadSize, "android"))
        assertEquals(expected.objectValue("ios").signedIntArrayValue("progress"), PolarWorkflowRuntimePlanning.planPsFtpWriteProgress(payloadSize, "ios"))
        assertEquals("success", expected.stringValue("completion"))
        assertEquals("android-currently-emits-negative-header-overhead-progress-before-payload-count-while-ios-emits-initial-zero-header-progress-and-final-payload-count", decision)
        assertCommonRuntimePrototype(expected)
        assertPsFtpRuntimeConsumerAndPlatformShape(vector)
        assertEquals("Covers the PSFTP write stream success path with a device success response. Android currently emits an initial negative progress value while RFC60 header bytes are being consumed, then the payload byte count. iOS emits an initial zero progress value, an intermediate header/payload stream position, and the same final payload byte count. Shared KMP runtime should choose one progress policy before moving write orchestration to common code.", vector.stringValue("notes"))
    }

    @Test
    fun commonFakeWriteRuntimeMapsMissingWriteAckToTypedWriteAckTimeout() {
        val vector = loadGoldenVectorText("sdk/psftp-response/write-ack-timeout-policy.json")
        val input = vector.objectValue("input")
        val failure = input.objectValue("failure")
        val thrown = assertFailsWith<PolarPsFtpWriteAckTimeout> {
            PolarWorkflowRuntimePlanning.planPsFtpWrite(
                payload = hexToBytes(input.stringValue("payloadHex")),
                transportTransmit = failure.stringValue("transportTransmit"),
                writeAck = failure.stringValue("writeAck")
            )
        }

        assertEquals(failure.stringValue("point"), thrown.point)
        assertEquals("write-ack-timeout-policy", vector.stringValue("id"))
        assertEquals("sdk.psftp-response", vector.stringValue("area"))
        assertEquals("write_ack_timeout_policy", vector.stringValue("case"))
        assertEquals("writeAckTimeoutPolicy", input.stringValue("kind"))
        assertEquals("write acknowledgement timeout is a protocol-timeout path distinct from transport transmit failure and peer-side RFC76 response errors", vector.objectValue("platformExpectations").objectValue("commonDecision").stringValue("errorPolicy"))
        assertEquals("planned-fake-clock-or-injectable-timeout-required", vector.objectValue("execution").stringValue("android"))
        assertEquals("planned-fake-clock-or-injectable-timeout-required", vector.objectValue("execution").stringValue("ios"))
        assertEquals("shared-common-test", vector.objectValue("execution").stringValue("common"))
        assertPsFtpRuntimeConsumerAndPlatformShape(vector)
    }

    @Test
    fun commonFakePsFtpHarnessPinsWriteAckTimeoutCleanupWithoutLeakedOperation() {
        val vector = loadGoldenVectorText("sdk/psftp-response/write-ack-timeout-policy.json")
        val input = vector.objectValue("input")
        val failure = input.objectValue("failure")
        val payload = hexToBytes(input.stringValue("payloadHex"))
        val harness = CommonFakePsFtpRuntimeHarness()

        val thrown = assertFailsWith<PolarPsFtpWriteAckTimeout> {
            harness.writeMtuAndWaitForAck(
                payload = payload,
                transportTransmit = failure.stringValue("transportTransmit"),
                writeAck = failure.stringValue("writeAck"),
                ackTimeoutMs = 250
            )
        }

        assertEquals(failure.stringValue("point"), thrown.point)
        assertEquals(listOf(input.stringValue("payloadHex")), harness.independentMtuChannelWrites)
        assertTrue(harness.independentD2hChannelPackets.isEmpty())
        assertEquals(0, harness.writeAckCount)
        assertEquals(true, harness.descriptorEnabled)
        assertEquals(0, harness.activeObserverCount)
        assertEquals(1, harness.cleanupCallbackCount)
        assertEquals(0, harness.pendingOperationCount)
        assertEquals(listOf("scanner-pause", "scanner-resume"), harness.operationScopeCleanupEvents)
    }

    @Test
    fun psFtpRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/psftp-response/psftp-runtime-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val runtimePrototype = expected.objectValue("commonRuntimePrototype")
        val policyVectorPaths = input.stringArrayValue("policyVectorPaths")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        assertEquals("psftp-runtime-readiness", manifest.stringValue("id"))
        assertEquals("psFtpRuntimeReadiness", input.stringValue("kind"))
        assertEquals(requiredPsFtpRuntimePolicyPaths, policyVectorPaths)
        assertEquals(requiredPsFtpRuntimeFamilies, requiredFamilies)
        assertEquals(requiredPsFtpRuntimeFamilies, coveredFamilies)
        requiredPsFtpRuntimePolicyPaths.forEachIndexed { index, path ->
            val policy = loadGoldenVectorText(path)
            assertEquals(requiredPsFtpRuntimePolicyIds[index], policy.stringValue("id"), path)
            assertEquals(requiredPsFtpRuntimePolicyCases[index], policy.stringValue("case"), path)
            assertPsFtpRuntimePolicyExecutionEvidence(policy, path)
        }
        assertEquals(PSFTP_RUNTIME_READINESS_COMMON_DECISION, expected.stringValue("commonDecision"))
        assertEquals("executable shared commonTest runtime planning guard", runtimePrototype.stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", runtimePrototype.stringValue("reason"))
        assertEquals(
            listOf("com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClientTest"),
            consumerTests.stringArrayValue("android")
        )
        assertEquals(listOf("BlePsFtpClientTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.PsFtpRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private val requiredPsFtpRuntimePolicyPaths = listOf(
        "sdk/psftp-response/request-response-reassembly.json",
        "sdk/psftp-response/request-response-error-policy.json",
        "sdk/psftp-notifications/notification-reassembly.json",
        "sdk/psftp-notifications/notification-ordering.json",
        "sdk/psftp-notifications/notification-timeout-policy.json",
        "sdk/psftp-notifications/notification-error-policy.json",
        "sdk/psftp-notifications/notification-continuation-timeout-policy.json",
        "sdk/psftp-response/write-success-progress.json",
        "sdk/psftp-response/write-interruption-error-policy.json",
        "sdk/psftp-response/write-transport-failure-policy.json",
        "sdk/psftp-response/write-ack-timeout-policy.json"
    )

    private val requiredPsFtpRuntimePolicyIds = listOf(
        "request-response-reassembly",
        "request-response-error-policy",
        "notification-reassembly",
        "notification-ordering",
        "notification-timeout-policy",
        "notification-error-policy",
        "notification-continuation-timeout-policy",
        "write-success-progress",
        "write-interruption-error-policy",
        "write-transport-failure-policy",
        "write-ack-timeout-policy"
    )

    private val requiredPsFtpRuntimePolicyCases = listOf(
        "request_response_reassembly",
        "request_response_error_policy",
        "notification_reassembly",
        "notification_ordering",
        "notification_timeout_policy",
        "notification_error_policy",
        "notification_continuation_timeout_policy",
        "write_success_progress",
        "write_interruption_error_policy",
        "write_transport_failure_policy",
        "write_ack_timeout_policy"
    )

    private val requiredPsFtpRuntimeFamilies = listOf(
        "request-response-reassembly",
        "request-response-error-mapping",
        "notification-reassembly",
        "notification-ordering",
        "initial-silence-no-built-in-timeout",
        "consumer-timeout-observer-cleanup",
        "notification-rfc76-error-policy",
        "notification-transport-status-platform-split",
        "notification-continuation-timeout",
        "write-progress-platform-split",
        "write-interruption-response-error",
        "write-transport-failure",
        "write-ack-timeout",
        "fake-clock-timeout-gate",
        "platform-client-vector-reference-gate",
        "compile-verification-gate"
    )

    private fun String.signedIntArrayValue(field: String): List<Int> {
        val match = Regex("\"$field\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL).find(this) ?: error("Missing signed int array field $field")
        val content = match.groupValues[1]
        if (content.trim().isEmpty()) return emptyList()
        return Regex("-?\\d+").findAll(content).map { it.value.toInt() }.toList()
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

    private fun assertCommonRuntimePrototype(container: String) {
        val prototype = container.objectValue("commonRuntimePrototype")
        assertEquals("executable shared commonTest runtime planning guard", prototype.stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", prototype.stringValue("reason"))
    }

    private fun assertPsFtpRuntimeConsumerAndPlatformShape(vector: String) {
        val consumerTests = vector.objectValue("consumerTests")
        val expectedAndroidConsumers = listOf("com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClientTest")
        val expectedCommonConsumers = listOf("com.polar.sharedtest.PsFtpRuntimePolicyCommonTest")
        assertEquals(expectedAndroidConsumers, consumerTests.stringArrayValue("android"), vector.stringValue("id"))
        assertEquals(listOf("BlePsFtpClientTest"), consumerTests.stringArrayValue("ios"), vector.stringValue("id"))
        assertEquals(expectedCommonConsumers, consumerTests.stringArrayValue("commonPrototype"), vector.stringValue("id"))
        val platforms = vector.objectValue("platforms")
        assertEquals(true, platforms.booleanValue("android"), vector.stringValue("id"))
        assertEquals(true, platforms.booleanValue("ios"), vector.stringValue("id"))
        assertEquals(true, platforms.booleanValue("common"), vector.stringValue("id"))
    }

    private fun assertPsFtpRuntimePolicyExecutionEvidence(policy: String, path: String) {
        policy.objectValue("expected").optionalObjectValue("commonRuntimePrototype")?.let { prototype ->
            assertEquals("executable shared commonTest runtime planning guard", prototype.stringValue("status"), path)
            assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", prototype.stringValue("reason"), path)
            return
        }
        policy.optionalObjectValue("execution")?.let { execution ->
            val kind = execution.optionalStringValue("kind")
            val common = execution.optionalStringValue("common")
            if (kind != null) {
                assertEquals("fake-psftp-runtime-policy", kind, path)
                assertEquals(true, execution.booleanValue("wallClockSafe"), path)
                return
            }
            assertEquals("shared-common-test", common, path)
            return
        }
        error("$path must declare exact PSFTP runtime execution evidence before migration")
    }

    private companion object {
        val REQUEST_RESPONSE_REASSEMBLY_CASE_IDS = listOf("single-frame", "multi-frame")
        val REQUEST_RESPONSE_ERROR_CASE_IDS = listOf("known-error-no-such-file", "unknown-error-code")
        val NOTIFICATION_REASSEMBLY_CASE_IDS = listOf("single-frame", "multi-frame")
        val NOTIFICATION_ORDERING_CASE_IDS = listOf("two-single-frame-notifications")
        val NOTIFICATION_TIMEOUT_CASE_IDS = listOf("initial-silence")
        val NOTIFICATION_ERROR_CASE_IDS = listOf("rfc76-error-first-frame", "transport-error-first-packet")
        val NOTIFICATION_CONTINUATION_TIMEOUT_CASE_IDS = listOf("missing-last-frame-after-more")
        const val PSFTP_RUNTIME_READINESS_COMMON_DECISION = "PSFTP runtime migration may proceed only after every policy vector listed in this readiness manifest is executable from shared commonTest, Android and iOS PSFTP client tests continue to reference the same vectors, request response reassembly, response-error mapping, notification reassembly and ordering, initial-silence policy, consumer timeout cleanup, notification error platform split, continuation timeout, write progress split, write interruption, transport failure, write acknowledgement timeout, fake-clock timeout gates, and the shared tests are compile-verified."
    }
}

private class CommonFakePsFtpRuntimeHarness {
    private val clock = CommonFakeVirtualClock()
    private var observerOpen = false

    val independentMtuChannelWrites = mutableListOf<String>()
    val independentD2hChannelPackets = mutableListOf<PolarPsFtpNotificationPacket>()
    val operationScopeCleanupEvents = mutableListOf<String>()
    var descriptorEnabled: Boolean = false
        private set
    var writeAckCount: Int = 0
        private set
    var pendingOperationCount: Int = 0
        private set
    var cleanupCallbackCount: Int = 0
        private set

    val activeObserverCount: Int
        get() = if (observerOpen) 1 else 0

    fun waitForNotificationWithConsumerTimeout(timeoutMs: Int, packets: List<PolarPsFtpNotificationPacket>): String {
        startOperation()
        openD2hObserver()
        return try {
            independentD2hChannelPackets += packets
            if (packets.isEmpty()) {
                clock.advanceBy(timeoutMs.toLong())
                "timeout"
            } else {
                PolarWorkflowRuntimePlanning.reassembleNotifications(packets)
                "success"
            }
        } finally {
            closeD2hObserver()
            finishOperation()
        }
    }

    fun waitForNotificationContinuation(caseId: String, protocolTimeoutMs: Int, packets: List<PolarPsFtpNotificationPacket>): List<PolarPsFtpNotification> {
        startOperation()
        openD2hObserver()
        return try {
            independentD2hChannelPackets += packets
            PolarWorkflowRuntimePlanning.reassembleNotifications(packets)
        } catch (error: PolarPsFtpContinuationTimeout) {
            clock.advanceBy(protocolTimeoutMs.toLong())
            throw PolarPsFtpContinuationTimeout(caseId)
        } finally {
            closeD2hObserver()
            finishOperation()
        }
    }

    fun writeMtuAndWaitForAck(payload: ByteArray, transportTransmit: String, writeAck: String, ackTimeoutMs: Int): PolarWorkflowPlan {
        startOperation()
        enableD2hDescriptor()
        independentMtuChannelWrites += payload.toPsFtpHarnessHex()
        return try {
            val plan = PolarWorkflowRuntimePlanning.planPsFtpWrite(
                payload = payload,
                transportTransmit = transportTransmit,
                writeAck = writeAck
            )
            writeAckCount += 1
            plan
        } catch (error: PolarPsFtpWriteAckTimeout) {
            clock.advanceBy(ackTimeoutMs.toLong())
            throw error
        } finally {
            finishOperation()
        }
    }

    private fun startOperation() {
        pendingOperationCount += 1
        operationScopeCleanupEvents += "scanner-pause"
    }

    private fun finishOperation() {
        pendingOperationCount -= 1
        cleanupCallbackCount += 1
        operationScopeCleanupEvents += "scanner-resume"
    }

    private fun openD2hObserver() {
        enableD2hDescriptor()
        observerOpen = true
    }

    private fun closeD2hObserver() {
        observerOpen = false
    }

    private fun enableD2hDescriptor() {
        descriptorEnabled = true
    }
}

private fun ByteArray.toPsFtpHarnessHex(): String {
    return joinToString(separator = "") { byte ->
        val value = byte.toInt() and 0xFF
        val high = value / 16
        val low = value % 16
        "${high.toPsFtpHarnessHexDigit()}${low.toPsFtpHarnessHexDigit()}"
    }
}

private fun Int.toPsFtpHarnessHexDigit(): Char {
    return if (this < 10) {
        '0' + this
    } else {
        'a' + (this - 10)
    }
}
