package com.polar.testutils

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FakeTransportContractTest {
    @Test
    fun `captures command order targets payloads and scripted outcomes`() {
        val transport = ScriptedFakeTransport(
            listOf(
                FakeTransportOutcome.Bytes(byteArrayOf(0x01, 0x02)),
                FakeTransportOutcome.ResponseError(status = 103, message = "missing"),
                FakeTransportOutcome.TransportError("link lost"),
                FakeTransportOutcome.Complete
            )
        )

        val read = transport.read("/U/0/DEVICE.BPB")
        val write = transport.write("/U/0/SETTINGS.BPB", byteArrayOf(0x0A, 0x0B))
        val subscribe = transport.subscribe("d2h")
        val unsubscribe = transport.unsubscribe("d2h")

        assertArrayEquals(byteArrayOf(0x01, 0x02), (read as FakeTransportOutcome.Bytes).value)
        assertEquals(103, (write as FakeTransportOutcome.ResponseError).status)
        assertEquals("link lost", (subscribe as FakeTransportOutcome.TransportError).message)
        assertTrue(unsubscribe is FakeTransportOutcome.Complete)
        assertEquals(
            listOf(
                FakeTransportCommand(FakeTransportOperation.READ, "/U/0/DEVICE.BPB"),
                FakeTransportCommand(FakeTransportOperation.WRITE, "/U/0/SETTINGS.BPB", "0a0b"),
                FakeTransportCommand(FakeTransportOperation.SUBSCRIBE, "d2h"),
                FakeTransportCommand(FakeTransportOperation.UNSUBSCRIBE, "d2h")
            ),
            transport.commands
        )
    }

    @Test
    fun `returns timeout for unscripted operations`() {
        val transport = ScriptedFakeTransport(emptyList())
        val outcome = transport.read("/missing")

        assertEquals("unscripted-operation", (outcome as FakeTransportOutcome.Timeout).label)
    }

    @Test
    fun `stream cancellation removes observer cancels upstream and is idempotent`() {
        val transport = ScriptedFakeTransport(
            listOf(
                FakeTransportOutcome.Bytes(byteArrayOf(0x03)),
                FakeTransportOutcome.Complete
            )
        )

        val subscription = transport.openStream("d2h")
        val firstCancel = subscription.cancel()
        val secondCancel = subscription.cancel()

        assertArrayEquals(byteArrayOf(0x03), (subscription.subscribeOutcome as FakeTransportOutcome.Bytes).value)
        assertTrue(firstCancel is FakeTransportOutcome.Complete)
        assertTrue(secondCancel is FakeTransportOutcome.Complete)
        assertEquals(0, transport.activeObserverCount)
        assertEquals(listOf("d2h"), transport.cancelledStreams)
        assertTrue(subscription.upstreamCancelled)
        assertEquals(
            listOf(
                FakeTransportCommand(FakeTransportOperation.SUBSCRIBE, "d2h"),
                FakeTransportCommand(FakeTransportOperation.UNSUBSCRIBE, "d2h")
            ),
            transport.commands
        )
    }

    @Test
    fun `failed stream subscription does not register observer`() {
        val transport = ScriptedFakeTransport(
            listOf(FakeTransportOutcome.TransportError("service missing"))
        )

        val subscription = transport.openStream("pmd")
        val cancel = subscription.cancel()

        assertEquals("service missing", (subscription.subscribeOutcome as FakeTransportOutcome.TransportError).message)
        assertTrue(cancel is FakeTransportOutcome.Complete)
        assertEquals(0, transport.activeObserverCount)
        assertTrue(transport.cancelledStreams.isEmpty())
        assertEquals(
            listOf(FakeTransportCommand(FakeTransportOperation.SUBSCRIBE, "pmd")),
            transport.commands
        )
    }

    @Test
    fun `service readiness policy vector is pinned before shared runtime delegation`() {
        val vector = loadFakeTransportVector("service-readiness-policy.json")
        val expected = vector.getAsJsonObject("expected")

        assertFakeTransportPolicyVector(vector, "service-readiness-policy", "serviceReadinessPolicy", "Service-readiness waits in shared runtime must be bounded, observable, deterministic, and free from wall-clock sleeps before feature readiness is delegated to shared.")
        assertEquals("complete", expected.get("readyOutcome").asString)
        assertEquals(listOf("pmd", "pmd", "pmd"), expected.getAsJsonArray("readyChecks").map { it.asString })
        assertEquals("timeout", expected.get("missingOutcome").asString)
        assertEquals("service-readiness", expected.get("missingTimeoutLabel").asString)
        assertEquals(listOf("psftp", "psftp"), expected.getAsJsonArray("missingChecks").map { it.asString })
    }

    @Test
    fun `scripted command outcomes policy vector is pinned before shared runtime delegation`() {
        val vector = loadFakeTransportVector("scripted-command-outcomes-policy.json")
        val expected = vector.getAsJsonObject("expected")

        assertFakeTransportPolicyVector(vector, "scripted-command-outcomes-policy", "scriptedCommandOutcomesPolicy", "Shared fake transport must capture request order, write payload bytes, stream subscription targets, and scripted success/error/complete outcomes deterministically before runtime command planning delegates to shared.")
        assertEquals(listOf("bytes:0102", "response-error:103:missing", "transport-error:link lost", "complete"), expected.getAsJsonArray("outcomes").map { it.asString })
        assertEquals(listOf("READ:/U/0/DEVICE.BPB", "WRITE:/U/0/SETTINGS.BPB:0a0b", "SUBSCRIBE:d2h", "UNSUBSCRIBE:d2h"), expected.getAsJsonArray("commands").map { it.asString })
    }

    @Test
    fun `delayed response policy vector is pinned before shared runtime delegation`() {
        val vector = loadFakeTransportVector("delayed-response-policy.json")
        val expected = vector.getAsJsonObject("expected")

        assertFakeTransportPolicyVector(vector, "delayed-response-policy", "delayedResponsePolicy", "Shared runtime tests must model delayed transport responses with a virtual clock, observable poll attempts, and a distinct pending label before delayed read, write, or notification orchestration delegates to shared.", "executable shared commonTest delayed-response contract")
        assertEquals(listOf("timeout:delayed-response", "bytes:0708"), expected.getAsJsonArray("pollOutcomes").map { it.asString })
        assertEquals(listOf("/U/0/DELAYED.BPB@0", "/U/0/DELAYED.BPB@150"), expected.getAsJsonArray("polls").map { it.asString })
        assertEquals(150, expected.get("finalTimeMillis").asInt)
    }

    @Test
    fun `reconnect after failure policy vector is pinned before shared runtime delegation`() {
        val vector = loadFakeTransportVector("reconnect-after-failure-policy.json")
        val expected = vector.getAsJsonObject("expected")

        assertFakeTransportPolicyVector(vector, "reconnect-after-failure-policy", "reconnectAfterFailurePolicy", "Shared fake transport must make reconnect-after-failure explicit, observable, and deterministic before retry or reconnect-aware runtime planning delegates to shared.", "executable shared commonTest reconnect-after-failure contract")
        assertEquals(listOf("transport-error:disconnected", "complete", "bytes:0506"), expected.getAsJsonArray("outcomes").map { it.asString })
        assertEquals(listOf("READ:/U/0/DEVICE.BPB", "RECONNECT:transport", "READ:/U/0/DEVICE.BPB"), expected.getAsJsonArray("commands").map { it.asString })
        assertTrue(expected.get("connectedAfterReconnect").asBoolean)
    }

    @Test
    fun `retry delay policy vector is pinned before shared runtime delegation`() {
        val vector = loadFakeTransportVector("retry-delay-policy.json")
        val expected = vector.getAsJsonObject("expected")

        assertFakeTransportPolicyVector(vector, "retry-delay-policy", "retryDelayPolicy", "Shared runtime retry tests must schedule retry delays on a virtual clock, assert retry count and elapsed retry times, and avoid wall-clock sleeps before retry policy delegates to shared.", "executable shared commonTest retry-delay scheduler contract")
        assertEquals(3, expected.get("retryCount").asInt)
        assertEquals(listOf(100, 300, 700), expected.getAsJsonArray("retryTimesMillis").map { it.asInt })
        assertEquals(700, expected.get("finalTimeMillis").asInt)
    }

    @Test
    fun `unscripted operation timeout policy vector is pinned before shared runtime delegation`() {
        val vector = loadFakeTransportVector("unscripted-operation-timeout-policy.json")
        val expected = vector.getAsJsonObject("expected")

        assertFakeTransportPolicyVector(vector, "unscripted-operation-timeout-policy", "unscriptedOperationTimeoutPolicy", "Shared fake transport must report unscripted operations as deterministic timeouts while still recording the attempted command before runtime code delegates to shared.")
        assertEquals("timeout:unscripted-operation", expected.get("outcome").asString)
        assertEquals(listOf("READ:/missing"), expected.getAsJsonArray("commands").map { it.asString })
    }

    @Test
    fun `virtual clock timeout policy vector is pinned before shared runtime delegation`() {
        val vector = loadFakeTransportVector("virtual-clock-timeout-policy.json")
        val expected = vector.getAsJsonObject("expected")

        assertFakeTransportPolicyVector(vector, "virtual-clock-timeout-policy", "virtualClockTimeoutPolicy", "Shared runtime timeout tests must advance virtual time deterministically and must not wait for wall-clock protocol constants.")
        assertEquals(false, expected.get("timedOutBeforeFinalAdvance").asBoolean)
        assertEquals(true, expected.get("timedOutAfterFinalAdvance").asBoolean)
        assertEquals(500, expected.get("finalTimeMillis").asInt)
    }

    @Test
    fun `fake transport readiness manifest is pinned before shared runtime delegation`() {
        val vector = JsonParser.parseString(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/fake-transport/fake-transport-readiness.json")
                .readText()
        ).asJsonObject
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val policyVectorPaths = input.getAsJsonArray("policyVectorPaths").map { it.asString }
        val requiredFamilies = input.getAsJsonArray("requiredBehaviorFamilies").map { it.asString }
        val coveredFamilies = expected.getAsJsonArray("coveredBehaviorFamilies").map { it.asString }

        assertEquals("fake-transport-readiness", vector.get("id").asString)
        assertEquals("fakeTransportReadiness", input.get("kind").asString)
        assertEquals(FAKE_TRANSPORT_READINESS_POLICY_VECTOR_PATHS, policyVectorPaths)
        assertEquals(FAKE_TRANSPORT_READINESS_FAMILIES, requiredFamilies)
        assertEquals(FAKE_TRANSPORT_READINESS_FAMILIES, coveredFamilies)
        assertEquals(FAKE_TRANSPORT_READINESS_COMMON_DECISION, expected.get("commonDecision").asString)
        val commonRuntimePrototype = expected.getAsJsonObject("commonRuntimePrototype")
        assertEquals("executable shared commonTest fake-transport readiness guard", commonRuntimePrototype.get("status").asString)
        assertTrue(commonRuntimePrototype.get("wallClockSafe").asBoolean)
        assertEquals(listOf("com.polar.testutils.FakeTransportContractTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("StreamContinuationListTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.FakeTransportContractCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
    }

    private fun loadFakeTransportVector(fileName: String): JsonObject {
        return JsonParser.parseString(
            findRepositoryRoot()
                .resolve("testdata/golden-vectors/sdk/fake-transport/$fileName")
                .readText()
        ).asJsonObject
    }

    private fun assertFakeTransportPolicyVector(
        vector: JsonObject,
        id: String,
        kind: String,
        commonDecision: String,
        commonRuntimePrototypeStatus: String = "executable shared commonTest fake-transport contract"
    ) {
        val input = vector.getAsJsonObject("input")
        val expected = vector.getAsJsonObject("expected")
        val consumerTests = vector.getAsJsonObject("consumerTests")
        val execution = vector.getAsJsonObject("execution").getAsJsonObject("commonRuntimePrototype")

        assertEquals(id, vector.get("id").asString)
        assertEquals(kind, input.get("kind").asString)
        assertEquals(commonDecision, expected.get("commonDecision").asString)
        assertEquals(listOf("com.polar.testutils.FakeTransportContractTest"), consumerTests.getAsJsonArray("android").map { it.asString })
        assertEquals(listOf("StreamContinuationListTest"), consumerTests.getAsJsonArray("ios").map { it.asString })
        assertEquals(listOf("com.polar.sharedtest.FakeTransportContractCommonTest"), consumerTests.getAsJsonArray("commonPrototype").map { it.asString })
        assertEquals(commonRuntimePrototypeStatus, execution.get("status").asString)
        assertTrue(execution.get("wallClockSafe").asBoolean)
    }

    private fun findRepositoryRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (true) {
            if (directory.resolve("testdata/golden-vectors").isDirectory) {
                return directory
            }
            directory = directory.parentFile ?: break
        }
        error("Could not find repository root")
    }

    private companion object {
        val FAKE_TRANSPORT_READINESS_POLICY_VECTOR_PATHS = listOf(
            "sdk/fake-transport/scripted-command-outcomes-policy.json",
            "sdk/fake-transport/delayed-response-policy.json",
            "sdk/fake-transport/reconnect-after-failure-policy.json",
            "sdk/fake-transport/retry-delay-policy.json",
            "sdk/fake-transport/service-readiness-policy.json",
            "sdk/fake-transport/unscripted-operation-timeout-policy.json",
            "sdk/fake-transport/virtual-clock-timeout-policy.json"
        )

        val FAKE_TRANSPORT_READINESS_FAMILIES = listOf(
            "scripted-command-ordering",
            "write-payload-capture",
            "scripted-success-error-complete-outcomes",
            "delayed-response-polling",
            "delayed-response-release",
            "reconnect-after-failure",
            "reconnect-command-recording",
            "retry-delay-scheduling",
            "retry-count-observation",
            "unscripted-operation-timeout",
            "unscripted-command-recording",
            "service-readiness-delayed-success",
            "service-readiness-timeout",
            "readiness-attempt-ordering",
            "virtual-clock-timeout-boundary",
            "wall-clock-free-timeout-tests",
            "platform-facade-compatibility-gate",
            "compile-verification-gate"
        )

        const val FAKE_TRANSPORT_READINESS_COMMON_DECISION = "Fake-transport base runtime shared ownership remains valid while scripted command outcomes, delayed-response polling, reconnect-after-failure controls, retry-delay scheduling, unscripted-operation timeouts, service readiness, and virtual-clock timeout policy vectors remain executable from shared commonTest, Android and iOS guard the same vector paths, platform facade compatibility stays explicit, and the shared tests are compile-verified."
    }
}
