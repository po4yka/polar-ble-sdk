package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class D2hStreamRuntimePolicyCommonTest {
    @Test
    fun d2hStreamRuntimeGoldenVectorDefinesExecutableCommonLateErrorAndCancellationPolicy() {
        val vector = loadGoldenVectorText("sdk/d2h-notifications/stream-runtime-policy.json")
        val input = vector.objectValue("input")
        val expectedCases = vector.objectValue("expected").objectArray("cases").associateBy { testCase -> testCase.stringValue("id") }
        val scenarios = input.objectArray("scenarios")

        scenarios.forEach { scenario ->
            val expected = expectedCases.getValue(scenario.stringValue("id"))
            val outcome = runScenario(input.stringValue("target"), scenario)

            assertEquals(expected.stringArrayValue("events"), outcome.events, scenario.stringValue("id"))
            expected.optionalStringValue("terminalError")?.let { error -> assertEquals(error, outcome.terminalError, scenario.stringValue("id")) }
            expected.optionalStringValue("subscribeError")?.let { error -> assertEquals(error, outcome.subscribeError, scenario.stringValue("id")) }
            expected.optionalIntValue("activeObserverCount")?.let { count -> assertEquals(count, outcome.activeObserverCount, scenario.stringValue("id")) }
            expected.optionalBooleanValue("upstreamCancelled")?.let { cancelled -> assertEquals(cancelled, outcome.upstreamCancelled, scenario.stringValue("id")) }
            assertEquals(expected.stringArrayValue("cancelledStreams"), outcome.cancelledStreams, scenario.stringValue("id"))
            expected.optionalStringArrayValue("ignoredAfterCancel")?.let { ignored -> assertEquals(ignored, outcome.ignoredAfterCancel, scenario.stringValue("id")) }
        }
    }

    @Test
    fun d2hStreamRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/d2h-notifications/stream-runtime-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val policyVectorPath = input.stringValue("policyVectorPath")
        val policy = loadGoldenVectorText(policyVectorPath)
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        val policyInput = policy.objectValue("input")
        val policyExpected = policy.objectValue("expected")
        val policyExecution = policy.objectValue("execution")
        val policyConsumers = policy.objectValue("consumerTests")
        val readinessConsumers = manifest.objectValue("consumerTests")

        assertEquals("d2h-stream-runtime-readiness", manifest.stringValue("id"))
        assertEquals("d2hStreamRuntimeReadiness", input.stringValue("kind"))
        assertEquals("sdk/d2h-notifications/stream-runtime-policy.json", policyVectorPath)
        assertEquals(requiredD2hStreamRuntimeFamilies, requiredFamilies)
        assertEquals(requiredD2hStreamRuntimeFamilies, coveredFamilies)
        assertEquals("stream-runtime-policy", policy.stringValue("id"))
        assertEquals("d2hStreamRuntimePolicy", policyInput.stringValue("kind"))
        assertEquals("d2h", policyInput.stringValue("target"))
        assertEquals(requiredD2hStreamScenarioIds, policyInput.objectArray("scenarios").map { scenario -> scenario.stringValue("id") })
        assertEquals(requiredD2hStreamScenarioIds, policyExpected.objectArray("cases").map { testCase -> testCase.stringValue("id") })
        assertEquals("fake-stream-runtime-policy", policyExecution.stringValue("kind"))
        assertEquals("scripted-d2h-notification-stream", policyExecution.stringValue("transport"))
        assertEquals(true, policyExecution.booleanValue("wallClockSafe"))
        assertEquals(requiredD2hStreamConsumersAndroid, policyConsumers.stringArrayValue("android"))
        assertEquals(requiredD2hStreamConsumersIos, policyConsumers.stringArrayValue("ios"))
        assertEquals(requiredD2hStreamConsumersCommon, policyConsumers.stringArrayValue("commonPrototype"))
        assertEquals(requiredD2hStreamConsumersAndroid, readinessConsumers.stringArrayValue("android"))
        assertEquals(requiredD2hStreamConsumersIos, readinessConsumers.stringArrayValue("ios"))
        assertEquals(requiredD2hStreamConsumersCommon, readinessConsumers.stringArrayValue("commonPrototype"))
        assertEquals(d2hStreamRuntimeReadinessCommonDecision, expected.stringValue("commonDecision"))
        assertEquals("executable shared commonTest runtime planning guard", expected.objectValue("commonRuntimePrototype").stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", expected.objectValue("commonRuntimePrototype").stringValue("reason"))
    }

    private val requiredD2hStreamRuntimeFamilies = listOf(
        "mapped-value-before-late-error",
        "late-upstream-error-propagation",
        "consumer-cancellation-upstream-cancel",
        "suppress-notifications-after-cancel",
        "unknown-notification-filtering",
        "known-values-continue-after-unknown",
        "failed-subscribe-no-observer",
        "active-observer-cleanup-gate",
        "facade-error-mapping-gate",
        "platform-facade-vector-reference-gate",
        "compile-verification-gate"
    )

    private val requiredD2hStreamScenarioIds = listOf(
        "late-error-after-emitted-notification",
        "consumer-cancels-after-first-notification",
        "unknown-notification-between-known-values-is-filtered",
        "failed-subscribe-does-not-register-observer"
    )

    private val requiredD2hStreamConsumersAndroid = listOf("com.polar.sdk.api.model.utils.PolarD2HNotificationsUtilsTest")
    private val requiredD2hStreamConsumersIos = listOf("PolarDeviceToHostNotificationsApiTests")
    private val requiredD2hStreamConsumersCommon = listOf("com.polar.sharedtest.D2hStreamRuntimePolicyCommonTest")

    private val d2hStreamRuntimeReadinessCommonDecision = "D2H stream runtime migration may proceed only after stream-runtime-policy.json and this readiness manifest are executable from shared commonTest, Android and iOS facade tests continue to reference the same vectors, mapped values emitted before late upstream errors are preserved, consumer cancellation cancels upstream work and suppresses later notifications, unknown notifications are filtered without stopping later known values, failed subscribe paths register no observers, public facade error mapping remains pinned, and the shared tests are compile-verified."

    private fun runScenario(target: String, scenario: String): D2hStreamOutcome {
        val transport = ScriptedCommonFakeTransport(listOf(scenario.subscribeTransportOutcome(), CommonFakeTransportOutcome.Complete))
        val subscription = transport.openStream(target)
        val subscribeOutcome = subscription.subscribeOutcome
        if (subscribeOutcome is CommonFakeTransportOutcome.TransportError) {
            subscription.cancel()
            return D2hStreamOutcome(subscribeError = subscribeOutcome.message, activeObserverCount = transport.activeObserverCount, cancelledStreams = transport.cancelledStreams)
        }

        val events = mutableListOf<String>()
        val ignoredAfterCancel = mutableListOf<String>()
        val cancelAfter = scenario.optionalIntValue("cancelAfterEmitted")
        scenario.objectArray("notifications").forEach { notification ->
            val mapped = notification.intValue("notificationId").notificationTypeOrNull() ?: return@forEach
            if (cancelAfter != null && events.size >= cancelAfter) {
                ignoredAfterCancel += mapped
            } else {
                events += mapped
            }
            if (cancelAfter != null && events.size == cancelAfter && !subscription.upstreamCancelled) {
                subscription.cancel()
            }
        }

        val terminal = scenario.optionalObjectValue("terminal")
        val terminalError = if (terminal != null && !subscription.upstreamCancelled) {
            assertIs<CommonFakeTransportOutcome.TransportError>(terminal.toTerminalOutcome()).message
        } else {
            null
        }
        return D2hStreamOutcome(
            events = events,
            terminalError = terminalError,
            activeObserverCount = transport.activeObserverCount,
            cancelledStreams = transport.cancelledStreams,
            upstreamCancelled = subscription.upstreamCancelled,
            ignoredAfterCancel = ignoredAfterCancel
        )
    }

    private fun String.subscribeTransportOutcome(): CommonFakeTransportOutcome {
        return when (stringValue("subscribeOutcome")) {
            "success" -> CommonFakeTransportOutcome.Bytes(byteArrayOf(0x01))
            "transportError" -> CommonFakeTransportOutcome.TransportError(stringValue("message"))
            else -> error("Unsupported subscribe outcome ${stringValue("subscribeOutcome")}")
        }
    }

    private fun String.toTerminalOutcome(): CommonFakeTransportOutcome {
        return when (stringValue("mode")) {
            "transportError" -> CommonFakeTransportOutcome.TransportError(stringValue("message"))
            else -> error("Unsupported terminal mode ${stringValue("mode")}")
        }
    }

    private fun Int.notificationTypeOrNull(): String? {
        return when (this) {
            7 -> "SYNC_REQUIRED"
            12 -> "STOP_GPS_MEASUREMENT"
            else -> null
        }
    }

    private fun String.optionalObjectValue(field: String): String? {
        val fieldIndex = indexOf("\"$field\"")
        if (fieldIndex < 0) return null
        val objectStart = indexOf('{', fieldIndex)
        if (objectStart < 0) return null
        return substring(objectStart, balancedEnd(objectStart, '{', '}') + 1)
    }

    private fun String.optionalIntValue(field: String): Int? {
        return Regex("\"$field\"\\s*:\\s*(\\d+)").find(this)?.groupValues?.get(1)?.toInt()
    }

    private fun String.optionalBooleanValue(field: String): Boolean? {
        return Regex("\"$field\"\\s*:\\s*(true|false)").find(this)?.groupValues?.get(1)?.let { value -> value == "true" }
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

    private data class D2hStreamOutcome(
        val events: List<String> = emptyList(),
        val terminalError: String? = null,
        val subscribeError: String? = null,
        val activeObserverCount: Int = 0,
        val cancelledStreams: List<String> = emptyList(),
        val upstreamCancelled: Boolean = false,
        val ignoredAfterCancel: List<String> = emptyList()
    )
}
