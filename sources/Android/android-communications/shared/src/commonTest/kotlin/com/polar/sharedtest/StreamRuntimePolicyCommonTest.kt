package com.polar.sharedtest

import com.polar.shared.runtime.PolarStreamRuntimePlanning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamRuntimePolicyCommonTest {
    @Test
    fun orderedEmissionsPolicyVectorRunsThroughCommonFakeStreamRuntime() {
        val vector = loadGoldenVectorText("sdk/stream-runtime/ordered-emissions-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val runtime = PolarStreamRuntimePlanning.newState()

        runtime.subscribe(input.stringValue("target"))
        input.stringArrayValue("emissions").forEach { value ->
            runtime.emit(value)
        }
        input.stringArrayValue("completionSignals").forEach { signal ->
            when (signal) {
                "complete" -> runtime.complete()
                else -> error("Unsupported completion signal $signal")
            }
        }

        assertEquals("genericStreamEmissionPolicy", input.stringValue("kind"))
        assertEquals("stream", input.stringValue("target"))
        assertEquals(expected.stringArrayValue("emittedValues"), runtime.emittedValues)
        assertEquals(expected.intValue("completionEventCount"), runtime.completionEventCount)
        assertEquals(expected.intValue("activeObserverCount"), runtime.activeObserverCount)
        assertEquals("preserve-source-order", expected.stringValue("orderingPolicy"))
        assertEquals("Stream values emitted before terminal completion must be delivered in source order.", expected.stringValue("commonDecision"))
    }

    @Test
    fun duplicateCompletionPolicyVectorRunsThroughCommonFakeStreamRuntime() {
        val vector = loadGoldenVectorText("sdk/stream-runtime/duplicate-completion-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val runtime = PolarStreamRuntimePlanning.newState()

        runtime.subscribe(input.stringValue("target"))
        input.stringArrayValue("completionSignals").forEach { signal ->
            when (signal) {
                "complete" -> runtime.complete()
                else -> error("Unsupported completion signal $signal")
            }
        }

        assertEquals("genericStreamCompletionPolicy", input.stringValue("kind"))
        assertEquals("stream", input.stringValue("target"))
        assertEquals(expected.intValue("completionEventCount"), runtime.completionEventCount)
        assertEquals(expected.intValue("activeObserverCount"), runtime.activeObserverCount)
        assertEquals("ignore-after-first-completion", expected.stringValue("duplicateCompletionPolicy"))
        assertEquals("Complete or finish signals after the first terminal completion must be idempotent and must not re-register observers.", expected.stringValue("commonDecision"))
    }

    @Test
    fun lateEmissionAfterCompletionPolicyVectorRunsThroughCommonFakeStreamRuntime() {
        val vector = loadGoldenVectorText("sdk/stream-runtime/late-emission-after-completion-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val runtime = PolarStreamRuntimePlanning.newState()

        runtime.subscribe(input.stringValue("target"))
        input.stringArrayValue("completionSignals").forEach { signal ->
            when (signal) {
                "complete" -> runtime.complete()
                else -> error("Unsupported completion signal $signal")
            }
        }
        input.stringArrayValue("postCompletionEmissions").forEach { value ->
            runtime.emit(value)
        }

        assertEquals("genericStreamCompletionPolicy", input.stringValue("kind"))
        assertEquals("stream", input.stringValue("target"))
        assertEquals(expected.intValue("completionEventCount"), runtime.completionEventCount)
        assertEquals(expected.intValue("activeObserverCount"), runtime.activeObserverCount)
        assertEquals(expected.stringArrayValue("emittedValues"), runtime.emittedValues)
        assertEquals("ignore-after-terminal-completion", expected.stringValue("lateEmissionPolicy"))
        assertEquals("Values emitted after terminal completion must not surface to consumers and must not re-register observers.", expected.stringValue("commonDecision"))
    }

    @Test
    fun consumerCancellationPolicyVectorRunsThroughCommonFakeStreamRuntime() {
        val vector = loadGoldenVectorText("sdk/stream-runtime/consumer-cancellation-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val runtime = PolarStreamRuntimePlanning.newState()

        input.stringArrayValue("actions").forEach { action ->
            when (action) {
                "subscribe" -> runtime.subscribe(input.stringValue("target"))
                "cancel-consumer" -> runtime.cancelConsumer(input.stringValue("target"))
                else -> error("Unsupported stream action $action")
            }
        }

        assertEquals("genericStreamCancellationPolicy", input.stringValue("kind"))
        assertEquals(expected.intValue("activeObserverCount"), runtime.activeObserverCount)
        assertEquals(expected.stringValue("cancellationPolicy"), "idempotent-consumer-cancellation")
        assertEquals(expected.stringArrayValue("cancelledStreams"), runtime.cancelledStreams)
        assertEquals(expected.intValue("cleanupCallbackCount"), runtime.cleanupCallbackCount)
        assertEquals(expected.booleanValue("upstreamCancelled"), runtime.upstreamCancelled)
        assertEquals("Consumer cancellation must remove the observer, cancel upstream work once, and remain idempotent.", expected.stringValue("commonDecision"))
    }

    @Test
    fun disconnectAfterSubscriptionPolicyVectorRunsThroughCommonFakeStreamRuntime() {
        val vector = loadGoldenVectorText("sdk/stream-runtime/disconnect-after-subscription-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val runtime = PolarStreamRuntimePlanning.newState()

        input.stringArrayValue("actions").forEach { action ->
            when (action) {
                "subscribe" -> runtime.subscribe(input.stringValue("target"))
                "disconnect-transport" -> runtime.disconnect(input.stringValue("disconnectError"))
                else -> error("Unsupported stream action $action")
            }
        }

        assertEquals("genericStreamDisconnectPolicy", input.stringValue("kind"))
        assertEquals(expected.intValue("activeObserverCount"), runtime.activeObserverCount)
        assertEquals(expected.stringValue("terminalError"), runtime.terminalError)
        assertEquals(expected.intValue("errorEventCount"), runtime.errorEventCount)
        assertEquals(expected.intValue("cleanupCallbackCount"), runtime.cleanupCallbackCount)
        assertEquals(expected.booleanValue("upstreamCancelled"), runtime.upstreamCancelled)
        assertEquals("disconnect-after-subscription-clears-observer", expected.stringValue("disconnectPolicy"))
        assertEquals("A stream that disconnects after observer registration must terminate consumers, clear observers, and cancel upstream work without leaking an active subscription.", expected.stringValue("commonDecision"))
    }

    @Test
    fun terminalErrorPolicyVectorRunsThroughCommonFakeStreamRuntime() {
        val vector = loadGoldenVectorText("sdk/stream-runtime/terminal-error-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val runtime = PolarStreamRuntimePlanning.newState()

        input.stringArrayValue("actions").forEach { action ->
            when (action) {
                "subscribe" -> runtime.subscribe(input.stringValue("target"))
                "terminal-error" -> runtime.fail(input.stringValue("terminalError"))
                else -> error("Unsupported stream action $action")
            }
        }

        assertEquals("genericStreamTerminalErrorPolicy", input.stringValue("kind"))
        assertEquals(expected.intValue("activeObserverCount"), runtime.activeObserverCount)
        assertEquals(expected.stringValue("terminalError"), runtime.terminalError)
        assertEquals(expected.intValue("completionEventCount"), runtime.completionEventCount)
        assertEquals(expected.intValue("errorEventCount"), runtime.errorEventCount)
        assertEquals("propagate-error-and-clear-observers", expected.stringValue("terminalErrorPolicy"))
        assertEquals("Terminal stream errors must propagate to consumers and clear observers without reporting normal completion.", expected.stringValue("commonDecision"))
    }

    @Test
    fun initialDisconnectedPolicyVectorRunsThroughCommonFakeStreamRuntime() {
        val vector = loadGoldenVectorText("sdk/stream-runtime/initial-disconnected-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val runtime = PolarStreamRuntimePlanning.newState()

        runtime.subscribeWithConnectionGuard(
            target = input.stringValue("target"),
            startConnected = input.booleanValue("startConnected"),
            checkConnection = input.booleanValue("checkConnection")
        )

        assertEquals("genericStreamConnectionGuardPolicy", input.stringValue("kind"))
        assertEquals(expected.intValue("activeObserverCount"), runtime.activeObserverCount)
        assertEquals(expected.stringValue("terminalError"), runtime.terminalError)
        assertEquals(expected.booleanValue("upstreamStarted"), runtime.upstreamStarted)
        assertEquals("fail-before-observer-registration", expected.stringValue("connectionGuardPolicy"))
        assertEquals("A checked stream subscription that starts disconnected must fail before observer registration or upstream work starts.", expected.stringValue("commonDecision"))
    }

    @Test
    fun uncheckedSubscriptionPolicyVectorRunsThroughCommonFakeStreamRuntime() {
        val vector = loadGoldenVectorText("sdk/stream-runtime/unchecked-subscription-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val runtime = PolarStreamRuntimePlanning.newState()

        runtime.subscribeWithConnectionGuard(
            target = input.stringValue("target"),
            startConnected = input.booleanValue("startConnected"),
            checkConnection = input.booleanValue("checkConnection")
        )

        assertEquals("genericStreamConnectionGuardPolicy", input.stringValue("kind"))
        assertEquals(expected.intValue("activeObserverCount"), runtime.activeObserverCount)
        assertEquals(expected.nullableStringValue("terminalError"), runtime.terminalError)
        assertEquals(expected.booleanValue("upstreamStarted"), runtime.upstreamStarted)
        assertEquals(expected.booleanValue("connectionChecked"), runtime.connectionChecked)
        assertEquals("skip-connection-check-and-register-observer", expected.stringValue("connectionGuardPolicy"))
        assertEquals("An unchecked stream subscription must register the observer without querying transport connection state.", expected.stringValue("commonDecision"))
    }

    @Test
    fun streamRuntimeReadinessManifestNamesEveryPreMigrationBehaviorFamily() {
        val manifest = loadGoldenVectorText("sdk/stream-runtime/stream-runtime-readiness.json")
        val input = manifest.objectValue("input")
        val expected = manifest.objectValue("expected")
        val consumerTests = manifest.objectValue("consumerTests")
        val policyVectorPaths = input.stringArrayValue("policyVectorPaths")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")
        assertEquals("stream-runtime-readiness", manifest.stringValue("id"))
        assertEquals("streamRuntimeReadiness", input.stringValue("kind"))
        assertEquals(requiredStreamRuntimePolicyPaths, policyVectorPaths)
        assertEquals(requiredStreamRuntimeFamilies, requiredFamilies)
        assertEquals(requiredStreamRuntimeFamilies, coveredFamilies)
        requiredStreamRuntimePolicyPaths.forEach { path ->
            val policy = loadGoldenVectorText(path)
            assertEquals("fake-stream-runtime-policy", policy.objectValue("execution").stringValue("kind"), "$path must remain executable through fake stream runtime")
            assertEquals(true, policy.objectValue("execution").booleanValue("wallClockSafe"), "$path must remain wall-clock safe")
        }
        assertEquals(streamRuntimeReadinessCommonDecision, expected.stringValue("commonDecision"))
        assertEquals("executable shared commonTest runtime planning guard", expected.objectValue("commonRuntimePrototype").stringValue("status"))
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", expected.objectValue("commonRuntimePrototype").stringValue("reason"))
        assertEquals(listOf("com.polar.androidcommunications.common.ble.ChannelUtilsTests"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("StreamContinuationListTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.StreamRuntimePolicyCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private val requiredStreamRuntimePolicyPaths = listOf(
        "sdk/stream-runtime/ordered-emissions-policy.json",
        "sdk/stream-runtime/terminal-error-policy.json",
        "sdk/stream-runtime/initial-disconnected-policy.json",
        "sdk/stream-runtime/unchecked-subscription-policy.json",
        "sdk/stream-runtime/consumer-cancellation-policy.json",
        "sdk/stream-runtime/disconnect-after-subscription-policy.json",
        "sdk/stream-runtime/duplicate-completion-policy.json",
        "sdk/stream-runtime/late-emission-after-completion-policy.json"
    )

    private val requiredStreamRuntimeFamilies = listOf(
        "ordered-emission-before-completion",
        "terminal-error-propagation",
        "terminal-error-observer-cleanup",
        "checked-disconnected-fails-before-observer",
        "unchecked-subscription-skips-connection-check",
        "consumer-cancellation-observer-cleanup",
        "consumer-cancellation-upstream-cancel",
        "consumer-cancellation-idempotence",
        "disconnect-after-subscription-terminal",
        "disconnect-after-subscription-observer-cleanup",
        "disconnect-after-subscription-upstream-cancel",
        "duplicate-completion-idempotence",
        "post-completion-emission-suppression",
        "active-observer-count-gate",
        "platform-stream-vector-reference-gate",
        "compile-verification-gate"
    )

    private val streamRuntimeReadinessCommonDecision = "Generic stream runtime migration may proceed only after every stream runtime policy vector listed in this readiness manifest is executable from shared commonTest, Android ChannelUtils tests and iOS StreamContinuationList tests continue to reference the same vectors, ordered emissions, terminal errors, connection guards, consumer cancellation, disconnect-after-subscription termination, duplicate completion, post-completion emission suppression, active observer cleanup, and upstream cancellation remain pinned, and the shared tests are compile-verified."

}
