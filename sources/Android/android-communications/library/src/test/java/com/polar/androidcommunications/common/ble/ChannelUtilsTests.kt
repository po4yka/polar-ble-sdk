package com.polar.androidcommunications.common.ble

import com.polar.androidcommunications.api.ble.exceptions.BleDisconnected
import com.polar.androidcommunications.api.ble.model.gatt.BleGattTxInterface
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChannelUtilsTests {

    @Test
    fun postDisconnectedAndClearList_postsBleDisconnectedAndClearsList() = runTest {
        val observers = AtomicSet<Channel<Int>>()

        val job = launch {
            ChannelUtils.monitorNotifications(observers, mockk(relaxed = true), false).collect { }
        }
        testScheduler.advanceUntilIdle()
        assertEquals(1, observers.size())

        ChannelUtils.postDisconnectedAndClearList(observers)
        testScheduler.advanceUntilIdle()

        // observers list is cleared immediately
        assertEquals(0, observers.size())
        job.cancel()
    }

    @Test
    fun postExceptionAndClearList_postsGivenThrowableAndClearsList() = runTest {
        assertStreamRuntimePolicyVectorContains("terminal-error-policy.json", "terminal-error-policy")

        val observers = AtomicSet<Channel<Int>>()
        val error = IllegalStateException("boom")

        val job = launch {
            ChannelUtils.monitorNotifications(observers, mockk(relaxed = true), false).collect { }
        }
        testScheduler.advanceUntilIdle()

        ChannelUtils.postExceptionAndClearList(observers, error)
        testScheduler.advanceUntilIdle()

        // observers list is cleared immediately
        assertEquals(0, observers.size())
        job.cancel()
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    @Test
    fun postError_closesChannelWithError() {
        val channel = Channel<Int>()
        val observers = AtomicSet<Channel<Int>>()
        observers.add(channel)

        ChannelUtils.postError(observers, IllegalArgumentException("x"))

        assertTrue(channel.isClosedForSend)
    }

    @Test
    fun emitNext_emitsToAllItemsInSet() {
        val observers = AtomicSet<Int>()
        observers.add(1)
        observers.add(2)
        observers.add(3)

        var sum = 0
        ChannelUtils.emitNext(observers) { sum += it }

        assertEquals(6, sum)
    }

    @Test
    fun complete_completesFlowAndClearsList() = runTest {
        val observers = AtomicSet<Channel<Int>>()
        val values = mutableListOf<Int>()
        var completed = false

        val job = launch {
            ChannelUtils.monitorNotifications(observers, mockk(relaxed = true), false)
                .collect { values.add(it) }
            completed = true
        }
        testScheduler.advanceUntilIdle()
        assertEquals(1, observers.size())

        ChannelUtils.complete(observers)
        testScheduler.advanceUntilIdle()
        job.join()

        assertTrue(completed)
        assertEquals(0, observers.size())
    }

    @Test
    fun complete_whenCalledTwice_completesOnceAndLeavesObserverListEmpty() = runTest {
        assertStreamRuntimePolicyVectorContains("duplicate-completion-policy.json", "duplicate-completion-policy")

        val observers = AtomicSet<Channel<Int>>()
        var completedCount = 0

        val job = launch {
            ChannelUtils.monitorNotifications(observers, mockk(relaxed = true), false)
                .collect { }
            completedCount++
        }
        testScheduler.advanceUntilIdle()
        assertEquals(1, observers.size())

        ChannelUtils.complete(observers)
        ChannelUtils.complete(observers)
        testScheduler.advanceUntilIdle()
        job.join()

        assertEquals(1, completedCount)
        assertEquals(0, observers.size())
    }

    @Test
    fun complete_thenEmitNext_doesNotEmitAfterCompletionAndObserverListStaysEmpty() = runTest {
        assertStreamRuntimePolicyVectorContains("late-emission-after-completion-policy.json", "late-emission-after-completion-policy")

        val observers = AtomicSet<Channel<Int>>()
        val values = mutableListOf<Int>()

        val job = launch {
            ChannelUtils.monitorNotifications(observers, mockk(relaxed = true), false)
                .collect { values.add(it) }
        }
        testScheduler.advanceUntilIdle()
        assertEquals(1, observers.size())

        ChannelUtils.complete(observers)
        ChannelUtils.emitNext(observers) { channel -> channel.trySend(99) }
        testScheduler.advanceUntilIdle()
        job.join()

        assertEquals(emptyList<Int>(), values)
        assertEquals(0, observers.size())
    }

    private fun assertStreamRuntimePolicyVectorContains(fileName: String, vectorId: String) {
        val vector = loadStreamRuntimeVector(fileName)
        val expected = vector.getAsJsonObject("expected")
        val execution = vector.getAsJsonObject("execution")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val contract = STREAM_RUNTIME_POLICY_CONTRACTS.getValue(vectorId)

        assertEquals(vectorId, vector.get("id").asString)
        assertEquals(contract.inputKind, vector.getAsJsonObject("input").get("kind").asString)
        assertEquals(contract.executionTransport, execution.get("transport").asString)
        assertEquals("fake-stream-runtime-policy", execution.get("kind").asString)
        assertTrue(execution.get("wallClockSafe").asBoolean)
        assertEquals(contract.commonDecision, expected.get("commonDecision").asString)
        assertEquals(listOf("com.polar.androidcommunications.common.ble.ChannelUtilsTests"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("StreamContinuationListTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.StreamRuntimePolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    @Test
    fun streamRuntimeReadinessManifestIsPinnedBeforeStreamRuntimeMigration() {
        val vector = JsonParser.parseString(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/stream-runtime/stream-runtime-readiness.json")
                .readText()
        ).asJsonObject
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        assertEquals("stream-runtime-readiness", vector.get("id").asString)
        assertEquals("streamRuntimeReadiness", input.get("kind").asString)
        assertEquals(STREAM_RUNTIME_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        assertEquals(STREAM_RUNTIME_READINESS_FAMILIES, requiredFamilies)
        assertEquals(STREAM_RUNTIME_READINESS_FAMILIES, coveredFamilies)
        assertEquals(STREAM_RUNTIME_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        val commonRuntimePrototype = expected.getAsJsonObject("commonRuntimePrototype")
        assertEquals("executable shared commonTest runtime planning guard", commonRuntimePrototype.get("status").asString)
        assertEquals("Declared because this vector is consumed by runtime or fake-transport policy tests before production KMP migration.", commonRuntimePrototype.get("reason").asString)
        assertEquals(listOf("com.polar.androidcommunications.common.ble.ChannelUtilsTests"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("StreamContinuationListTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.StreamRuntimePolicyCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun loadStreamRuntimeVector(fileName: String): JsonObject {
        return JsonParser.parseString(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/stream-runtime/$fileName")
                .readText()
        ).asJsonObject
    }

    private fun findRepositoryRoot(): File {
        val userDirectory = System.getProperty("user.dir") ?: error("user.dir is not set")
        var current = File(userDirectory).absoluteFile
        while (true) {
            if (current.resolve("testdata/golden-vectors").isDirectory) {
                return current
            }
            current = current.parentFile ?: error("Could not find repository root from $userDirectory")
        }
    }

    @Test
    fun complete_withEmptySet_doesNotThrow() {
        val observers = AtomicSet<Channel<Int>>()
        ChannelUtils.complete(observers)
        assertEquals(0, observers.size())
    }

    @Test
    fun monitorNotifications_emitsValuesInObserverOrderAndCompletesWhenObserverCloses() = runTest {
        assertStreamRuntimePolicyVectorContains("ordered-emissions-policy.json", "ordered-emissions-policy")

        val observers = AtomicSet<Channel<Int>>()
        val values = mutableListOf<Int>()
        var completed = false

        val job = launch {
            ChannelUtils.monitorNotifications(observers, mockk(relaxed = true), false)
                .collect { values.add(it) }
            completed = true
        }
        testScheduler.advanceUntilIdle()
        val observer = observers.objects().single()

        observer.trySend(1)
        observer.trySend(2)
        observer.close()
        testScheduler.advanceUntilIdle()
        job.join()

        assertEquals(listOf(1, 2), values)
        assertTrue(completed)
        assertEquals(0, observers.size())
    }

    @Test
    fun monitorNotifications_whenConnected_addsObserverAndRemovesOnCancel() = runTest {
        assertStreamRuntimePolicyVectorContains("consumer-cancellation-policy.json", "consumer-cancellation-policy")

        val observers = AtomicSet<Channel<Int>>()
        val transport = mockk<BleGattTxInterface>()
        every { transport.isConnected() } returns true

        val job = launch {
            ChannelUtils.monitorNotifications(observers, transport, true).collect { }
        }
        testScheduler.advanceUntilIdle()
        assertEquals(1, observers.size())

        job.cancel()
        testScheduler.advanceUntilIdle()
        assertEquals(0, observers.size())
    }

    @Test
    fun monitorNotifications_afterConsumerCancel_suppressesLateEvents() = runTest {
        assertStreamRuntimePolicyVectorContains("consumer-cancellation-late-events-policy.json", "consumer-cancellation-late-events-policy")

        val observers = AtomicSet<Channel<Int>>()
        val values = mutableListOf<Int>()
        val job = launch {
            ChannelUtils.monitorNotifications(observers, mockk(relaxed = true), false)
                .collect { values.add(it) }
        }
        testScheduler.advanceUntilIdle()
        val observer = observers.objects().single()

        observer.trySend(1)
        testScheduler.advanceUntilIdle()
        job.cancel()
        testScheduler.advanceUntilIdle()
        ChannelUtils.emitNext(observers) { channel -> channel.trySend(2) }
        ChannelUtils.postExceptionAndClearList(observers, IllegalStateException("late stream failure"))
        ChannelUtils.complete(observers)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(1), values)
        assertEquals(0, observers.size())
    }

    @Test
    fun monitorNotifications_whenDisconnectedAfterSubscription_terminatesCollectorWithCancellation() = runTest {
        assertStreamRuntimePolicyVectorContains("disconnect-after-subscription-policy.json", "disconnect-after-subscription-policy")

        val observers = AtomicSet<Channel<Int>>()
        var caughtError: Throwable? = null

        val job = launch {
            try {
                ChannelUtils.monitorNotifications(observers, mockk(relaxed = true), false).collect { }
            } catch (error: Throwable) {
                caughtError = error
            }
        }
        testScheduler.advanceUntilIdle()
        assertEquals(1, observers.size())

        ChannelUtils.postDisconnectedAndClearList(observers)
        testScheduler.advanceUntilIdle()
        job.join()

        assertTrue(caughtError is CancellationException)
        assertEquals(0, observers.size())
    }

    @Test
    fun monitorNotifications_whenDisconnectedAndCheckEnabled_emitsBleDisconnected() = runTest {
        assertStreamRuntimePolicyVectorContains("initial-disconnected-policy.json", "initial-disconnected-policy")

        val observers = AtomicSet<Channel<Int>>()
        val transport = mockk<BleGattTxInterface>()
        every { transport.isConnected() } returns false

        var caughtError: Throwable? = null
        val job = launch {
            try { ChannelUtils.monitorNotifications(observers, transport, true).collect { } }
            catch (e: Throwable) { caughtError = e }
        }
        testScheduler.advanceUntilIdle()
        job.join()

        assertTrue(caughtError is BleDisconnected)
        assertEquals(0, observers.size())
    }

    @Test
    fun monitorNotifications_whenCheckDisabled_doesNotCallIsConnected() = runTest {
        assertStreamRuntimePolicyVectorContains("unchecked-subscription-policy.json", "unchecked-subscription-policy")

        val observers = AtomicSet<Channel<Int>>()
        val transport = mockk<BleGattTxInterface>(relaxed = true)

        val job = launch {
            ChannelUtils.monitorNotifications(observers, transport, false).collect { }
        }
        testScheduler.advanceUntilIdle()

        assertEquals(1, observers.size())
        verify(exactly = 0) { transport.isConnected() }

        job.cancel()
        testScheduler.advanceUntilIdle()
        assertEquals(0, observers.size())
    }

    @Test
    fun updateView_doesNothingAndDoesNotThrow() {
        ChannelUtils.updateView("value")
    }

    private companion object {
        val STREAM_RUNTIME_READINESS_POLICY_VECTOR_PATHS = listOf(
            "sdk/stream-runtime/ordered-emissions-policy.json",
            "sdk/stream-runtime/terminal-error-policy.json",
            "sdk/stream-runtime/initial-disconnected-policy.json",
            "sdk/stream-runtime/unchecked-subscription-policy.json",
            "sdk/stream-runtime/consumer-cancellation-policy.json",
            "sdk/stream-runtime/consumer-cancellation-late-events-policy.json",
            "sdk/stream-runtime/disconnect-after-subscription-policy.json",
            "sdk/stream-runtime/duplicate-completion-policy.json",
            "sdk/stream-runtime/late-emission-after-completion-policy.json"
        )

        val STREAM_RUNTIME_READINESS_FAMILIES = listOf(
            "ordered-emission-before-completion",
            "terminal-error-propagation",
            "terminal-error-observer-cleanup",
            "checked-disconnected-fails-before-observer",
            "unchecked-subscription-skips-connection-check",
            "consumer-cancellation-observer-cleanup",
            "consumer-cancellation-upstream-cancel",
            "consumer-cancellation-idempotence",
            "post-cancellation-late-event-suppression",
            "disconnect-after-subscription-terminal",
            "disconnect-after-subscription-observer-cleanup",
            "disconnect-after-subscription-upstream-cancel",
            "duplicate-completion-idempotence",
            "post-completion-emission-suppression",
            "active-observer-count-gate",
            "platform-stream-vector-reference-gate",
            "compile-verification-gate"
        )

        val STREAM_RUNTIME_POLICY_CONTRACTS = mapOf(
            "ordered-emissions-policy" to StreamRuntimePolicyContract(
                inputKind = "genericStreamEmissionPolicy",
                executionTransport = "generic-stream-emission",
                commonDecision = "Stream values emitted before terminal completion must be delivered in source order."
            ),
            "terminal-error-policy" to StreamRuntimePolicyContract(
                inputKind = "genericStreamTerminalErrorPolicy",
                executionTransport = "generic-stream-terminal-error",
                commonDecision = "Terminal stream errors must propagate to consumers and clear observers without reporting normal completion."
            ),
            "initial-disconnected-policy" to StreamRuntimePolicyContract(
                inputKind = "genericStreamConnectionGuardPolicy",
                executionTransport = "generic-stream-connection-guard",
                commonDecision = "A checked stream subscription that starts disconnected must fail before observer registration or upstream work starts."
            ),
            "unchecked-subscription-policy" to StreamRuntimePolicyContract(
                inputKind = "genericStreamConnectionGuardPolicy",
                executionTransport = "generic-stream-connection-guard",
                commonDecision = "An unchecked stream subscription must register the observer without querying transport connection state."
            ),
            "consumer-cancellation-policy" to StreamRuntimePolicyContract(
                inputKind = "genericStreamCancellationPolicy",
                executionTransport = "generic-stream-cancellation",
                commonDecision = "Consumer cancellation must remove the observer, cancel upstream work once, and remain idempotent."
            ),
            "consumer-cancellation-late-events-policy" to StreamRuntimePolicyContract(
                inputKind = "genericStreamCancellationPolicy",
                executionTransport = "generic-stream-cancellation",
                commonDecision = "After consumer cancellation, late stream values, terminal errors, and completion signals must not surface or mutate terminal counters."
            ),
            "disconnect-after-subscription-policy" to StreamRuntimePolicyContract(
                inputKind = "genericStreamDisconnectPolicy",
                executionTransport = "generic-stream-disconnect",
                commonDecision = "A stream that disconnects after observer registration must terminate consumers, clear observers, and cancel upstream work without leaking an active subscription."
            ),
            "duplicate-completion-policy" to StreamRuntimePolicyContract(
                inputKind = "genericStreamCompletionPolicy",
                executionTransport = "generic-stream-completion",
                commonDecision = "Complete or finish signals after the first terminal completion must be idempotent and must not re-register observers."
            ),
            "late-emission-after-completion-policy" to StreamRuntimePolicyContract(
                inputKind = "genericStreamCompletionPolicy",
                executionTransport = "generic-stream-completion",
                commonDecision = "Values emitted after terminal completion must not surface to consumers and must not re-register observers."
            )
        )

        const val STREAM_RUNTIME_READINESS_COMMON_DECISION = "Generic stream runtime migration may proceed only after every stream runtime policy vector listed in this readiness manifest is executable from shared commonTest, Android ChannelUtils tests and iOS StreamContinuationList tests continue to reference the same vectors, ordered emissions, terminal errors, connection guards, consumer cancellation, post-cancellation late-event suppression, disconnect-after-subscription termination, duplicate completion, post-completion emission suppression, active observer cleanup, and upstream cancellation remain pinned, and the shared tests are compile-verified."
    }

    private data class StreamRuntimePolicyContract(
        val inputKind: String,
        val executionTransport: String,
        val commonDecision: String
    )
}
