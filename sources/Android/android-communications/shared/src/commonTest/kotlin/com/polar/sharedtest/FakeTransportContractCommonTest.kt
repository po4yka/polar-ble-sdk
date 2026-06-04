package com.polar.sharedtest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakeTransportContractCommonTest {
    @Test
    fun capturesCommandOrderPayloadsAndScriptedOutcomes() {
        val vector = loadGoldenVectorText("sdk/fake-transport/scripted-command-outcomes-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val transport = ScriptedCommonFakeTransport(input.objectArray("outcomes").map { outcome -> outcome.toFakeTransportOutcome() })
        val actualOutcomes = input.objectArray("operations").map { operation -> transport.runOperation(operation) }

        val writeOperation = input.objectArray("operations").first { operation -> operation.stringValue("operation") == "write" }
        assertEquals("0a0b", writeOperation.stringValue("payloadHex"), "scripted command vector must pin write payload bytes")
        assertEquals("WRITE:/U/0/SETTINGS.BPB:0a0b", expected.stringArrayValue("commands").first { command -> command.startsWith("WRITE:") })
        assertEquals(expected.stringArrayValue("outcomes"), actualOutcomes.map(::describeOutcome))
        assertEquals(expected.stringArrayValue("commands"), transport.commands.map(::describeCommand))
        assertEquals("Shared fake transport must capture request order, write payload bytes, stream subscription targets, and scripted success/error/complete outcomes deterministically before runtime command planning delegates to KMP.", expected.stringValue("commonDecision"))
    }

    @Test
    fun returnsTimeoutForUnscriptedOperations() {
        val vector = loadGoldenVectorText("sdk/fake-transport/unscripted-operation-timeout-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val transport = ScriptedCommonFakeTransport(emptyList())
        val outcome = transport.runOperation(input)

        assertEquals(expected.stringValue("outcome"), describeOutcome(outcome))
        assertEquals(expected.stringArrayValue("commands"), transport.commands.map(::describeCommand))
        assertEquals("Shared fake transport must report unscripted operations as deterministic timeouts while still recording the attempted command before runtime code delegates to KMP.", expected.stringValue("commonDecision"))
    }

    @Test
    fun connectionStateGuardsDisconnectedStartAndDisconnectAfterOperationLimit() {
        val disconnected = ScriptedCommonFakeTransport(
            outcomes = listOf(CommonFakeTransportOutcome.Bytes(byteArrayOf(0x01))),
            startConnected = false
        )
        val disconnectedOutcome = disconnected.read("/U/0/DEVICE.BPB")

        assertEquals("disconnected", assertIs<CommonFakeTransportOutcome.TransportError>(disconnectedOutcome).message)
        assertEquals(false, disconnected.isConnected)
        assertEquals(listOf(CommonFakeTransportCommand(CommonFakeTransportOperation.READ, "/U/0/DEVICE.BPB")), disconnected.commands)

        val disconnecting = ScriptedCommonFakeTransport(
            outcomes = listOf(CommonFakeTransportOutcome.Bytes(byteArrayOf(0x02))),
            disconnectAfterOperations = 1
        )
        val firstOutcome = disconnecting.read("/U/0/DEVICE.BPB")
        val secondOutcome = disconnecting.write("/U/0/SETTINGS.BPB", byteArrayOf(0x0C))

        assertEquals(listOf(0x02.toByte()), assertIs<CommonFakeTransportOutcome.Bytes>(firstOutcome).value.toList())
        assertEquals("disconnected-after-1-operations", assertIs<CommonFakeTransportOutcome.TransportError>(secondOutcome).message)
        assertEquals(false, disconnecting.isConnected)
        assertEquals(
            listOf(
                CommonFakeTransportCommand(CommonFakeTransportOperation.READ, "/U/0/DEVICE.BPB"),
                CommonFakeTransportCommand(CommonFakeTransportOperation.WRITE, "/U/0/SETTINGS.BPB", "0c")
            ),
            disconnecting.commands
        )
    }

    @Test
    fun reconnectAfterFailureIsExplicitObservableAndDeterministic() {
        val vector = loadGoldenVectorText("sdk/fake-transport/reconnect-after-failure-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val transport = ScriptedCommonFakeTransport(
            outcomes = input.objectArray("outcomesAfterReconnect").map { outcome -> outcome.toFakeTransportOutcome() },
            startConnected = input.booleanValue("startConnected")
        )
        val actualOutcomes = input.objectArray("operations").map { operation -> transport.runOperation(operation) }

        assertEquals(expected.stringArrayValue("outcomes"), actualOutcomes.map(::describeOutcome))
        assertEquals(expected.stringArrayValue("commands"), transport.commands.map(::describeCommand))
        assertEquals(expected.booleanValue("connectedAfterReconnect"), transport.isConnected)
        assertEquals("Shared fake transport must make reconnect-after-failure explicit, observable, and deterministic before retry or reconnect-aware runtime planning delegates to KMP.", expected.stringValue("commonDecision"))
    }

    @Test
    fun streamCancellationRemovesObserverCancelsUpstreamAndIsIdempotent() {
        val transport = ScriptedCommonFakeTransport(
            listOf(
                CommonFakeTransportOutcome.Bytes(byteArrayOf(0x03)),
                CommonFakeTransportOutcome.Complete
            )
        )

        val subscription = transport.openStream("d2h")
        val firstCancel = subscription.cancel()
        val secondCancel = subscription.cancel()

        assertEquals(listOf(0x03.toByte()), assertIs<CommonFakeTransportOutcome.Bytes>(subscription.subscribeOutcome).value.toList())
        assertTrue(firstCancel is CommonFakeTransportOutcome.Complete)
        assertTrue(secondCancel is CommonFakeTransportOutcome.Complete)
        assertEquals(0, transport.activeObserverCount)
        assertEquals(listOf("d2h"), transport.cancelledStreams)
        assertEquals(1, transport.cleanupCallbackCount)
        assertTrue(subscription.upstreamCancelled)
        assertEquals(
            listOf(
                CommonFakeTransportCommand(CommonFakeTransportOperation.SUBSCRIBE, "d2h"),
                CommonFakeTransportCommand(CommonFakeTransportOperation.UNSUBSCRIBE, "d2h")
            ),
            transport.commands
        )
    }

    @Test
    fun failedStreamSubscriptionDoesNotRegisterObserver() {
        val transport = ScriptedCommonFakeTransport(
            listOf(CommonFakeTransportOutcome.TransportError("service missing"))
        )

        val subscription = transport.openStream("pmd")
        val cancel = subscription.cancel()

        assertEquals("service missing", assertIs<CommonFakeTransportOutcome.TransportError>(subscription.subscribeOutcome).message)
        assertTrue(cancel is CommonFakeTransportOutcome.Complete)
        assertEquals(0, transport.activeObserverCount)
        assertTrue(transport.cancelledStreams.isEmpty())
        assertEquals(
            listOf(CommonFakeTransportCommand(CommonFakeTransportOperation.SUBSCRIBE, "pmd")),
            transport.commands
        )
    }

    @Test
    fun serviceReadinessGateRecordsAttemptsAndTimesOutDeterministically() {
        val vector = loadGoldenVectorText("sdk/fake-transport/service-readiness-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val delayedReady = CommonFakeServiceReadinessGate(input.booleanArrayValue("readyAttempts"))
        val readyOutcome = delayedReady.awaitReady(
            service = input.stringValue("readyService"),
            maxAttempts = input.intValue("readyMaxAttempts")
        )

        assertTrue(readyOutcome is CommonFakeTransportOutcome.Complete)
        assertEquals(expected.stringArrayValue("readyChecks"), delayedReady.checks)

        val neverReady = CommonFakeServiceReadinessGate(input.booleanArrayValue("missingAttempts"))
        val timeoutOutcome = neverReady.awaitReady(
            service = input.stringValue("missingService"),
            maxAttempts = input.intValue("missingMaxAttempts")
        )

        assertEquals(expected.stringValue("missingTimeoutLabel"), assertIs<CommonFakeTransportOutcome.Timeout>(timeoutOutcome).label)
        assertEquals(expected.stringArrayValue("missingChecks"), neverReady.checks)
        assertEquals("Service-readiness waits in shared runtime must be bounded, observable, deterministic, and free from wall-clock sleeps before feature readiness is delegated to KMP.", expected.stringValue("commonDecision"))
    }

    @Test
    fun virtualClockAdvancesTimeoutsWithoutWallClockSleep() {
        val vector = loadGoldenVectorText("sdk/fake-transport/virtual-clock-timeout-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val clock = CommonFakeVirtualClock()
        val startedAt = input.intValue("startMillis").toLong()

        clock.advanceBy(input.intValue("advanceBeforeTimeoutMillis").toLong())
        assertEquals(expected.booleanValue("timedOutBeforeFinalAdvance"), clock.hasTimedOut(startMillis = startedAt, timeoutMillis = input.intValue("timeoutMillis").toLong()))

        clock.advanceBy(input.intValue("finalAdvanceMillis").toLong())
        assertEquals(expected.booleanValue("timedOutAfterFinalAdvance"), clock.hasTimedOut(startMillis = startedAt, timeoutMillis = input.intValue("timeoutMillis").toLong()))
        assertEquals(expected.intValue("finalTimeMillis").toLong(), clock.currentTimeMillis)
        assertEquals("Shared runtime timeout tests must advance virtual time deterministically and must not wait for wall-clock protocol constants.", expected.stringValue("commonDecision"))
    }

    @Test
    fun retryDelaySchedulerUsesVirtualClockAndReportsRetryCount() {
        val vector = loadGoldenVectorText("sdk/fake-transport/retry-delay-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val clock = CommonFakeVirtualClock()
        val scheduler = CommonFakeRetryScheduler(clock)

        scheduler.runRetryDelays(
            delaysMillis = input.signedIntArrayValue("delaysMillis").map(Int::toLong),
            maxRetries = input.intValue("maxRetries")
        )

        assertEquals(expected.intValue("retryCount"), scheduler.retryTimesMillis.size)
        assertEquals(expected.signedIntArrayValue("retryTimesMillis").map(Int::toLong), scheduler.retryTimesMillis)
        assertEquals(expected.intValue("finalTimeMillis").toLong(), clock.currentTimeMillis)
        assertEquals("Shared runtime retry tests must schedule retry delays on a virtual clock, assert retry count and elapsed retry times, and avoid wall-clock sleeps before retry policy delegates to KMP.", expected.stringValue("commonDecision"))
    }

    @Test
    fun delayedResponseUsesVirtualClockAndRecordsPollAttempts() {
        val vector = loadGoldenVectorText("sdk/fake-transport/delayed-response-policy.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val clock = CommonFakeVirtualClock()
        val delayedResponse = CommonFakeDelayedResponse(
            clock = clock,
            target = input.stringValue("target"),
            delayMillis = input.intValue("delayMillis").toLong(),
            payload = hexToBytes(input.stringValue("payloadHex"))
        )

        val firstPoll = delayedResponse.poll()
        clock.advanceBy(input.intValue("advanceBeforeSecondPollMillis").toLong())
        val secondPoll = delayedResponse.poll()

        assertEquals(expected.stringArrayValue("pollOutcomes"), listOf(firstPoll, secondPoll).map(::describeOutcome))
        assertEquals(expected.stringArrayValue("polls"), delayedResponse.polls)
        assertEquals(expected.intValue("finalTimeMillis").toLong(), clock.currentTimeMillis)
        assertEquals("Shared runtime tests must model delayed transport responses with a virtual clock, observable poll attempts, and a distinct pending label before delayed read, write, or notification orchestration delegates to KMP.", expected.stringValue("commonDecision"))
    }

    @Test
    fun fakeTransportReadinessManifestNamesEveryBaseRuntimeControl() {
        val vector = loadGoldenVectorText("sdk/fake-transport/fake-transport-readiness.json")
        val input = vector.objectValue("input")
        val expected = vector.objectValue("expected")
        val consumerTests = vector.objectValue("consumerTests")
        val policyVectorPaths = input.stringArrayValue("policyVectorPaths")
        val requiredFamilies = input.stringArrayValue("requiredBehaviorFamilies")
        val coveredFamilies = expected.stringArrayValue("coveredBehaviorFamilies")

        assertEquals("fake-transport-readiness", vector.stringValue("id"))
        assertEquals("fakeTransportReadiness", input.stringValue("kind"))
        assertEquals(requiredFakeTransportPolicyPaths, policyVectorPaths)
        assertEquals(requiredFakeTransportFamilies, requiredFamilies)
        assertEquals(requiredFakeTransportFamilies, coveredFamilies)
        requiredFakeTransportPolicyPaths.forEach { path ->
            val prototype = loadGoldenVectorText(path).objectValue("execution").objectValue("commonRuntimePrototype")
            assertEquals(requiredFakeTransportPrototypeStatuses.getValue(path), prototype.stringValue("status"), "$path runtime prototype status")
            assertEquals(true, prototype.booleanValue("wallClockSafe"), "$path runtime prototype must avoid wall-clock sleeps")
        }
        assertEquals(fakeTransportReadinessCommonDecision, expected.stringValue("commonDecision"))
        assertEquals("executable shared commonTest fake-transport readiness guard", expected.objectValue("commonRuntimePrototype").stringValue("status"))
        assertEquals(true, expected.objectValue("commonRuntimePrototype").booleanValue("wallClockSafe"))
        assertEquals(listOf("com.polar.testutils.FakeTransportContractTest"), consumerTests.stringArrayValue("android"))
        assertEquals(listOf("StreamContinuationListTest"), consumerTests.stringArrayValue("ios"))
        assertEquals(listOf("com.polar.sharedtest.FakeTransportContractCommonTest"), consumerTests.stringArrayValue("commonPrototype"))
    }

    private val requiredFakeTransportPolicyPaths = listOf(
        "sdk/fake-transport/scripted-command-outcomes-policy.json",
        "sdk/fake-transport/delayed-response-policy.json",
        "sdk/fake-transport/reconnect-after-failure-policy.json",
        "sdk/fake-transport/retry-delay-policy.json",
        "sdk/fake-transport/service-readiness-policy.json",
        "sdk/fake-transport/unscripted-operation-timeout-policy.json",
        "sdk/fake-transport/virtual-clock-timeout-policy.json"
    )

    private val requiredFakeTransportFamilies = listOf(
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

    private val fakeTransportReadinessCommonDecision = "Fake-transport base runtime migration may proceed only after scripted command outcomes, delayed-response polling, reconnect-after-failure controls, retry-delay scheduling, unscripted-operation timeouts, service readiness, and virtual-clock timeout policy vectors remain executable from shared commonTest, Android and iOS guard the same vector paths, platform facade compatibility stays explicit, and the shared tests are compile-verified."

    private val requiredFakeTransportPrototypeStatuses = mapOf(
        "sdk/fake-transport/scripted-command-outcomes-policy.json" to "executable shared commonTest fake-transport contract",
        "sdk/fake-transport/delayed-response-policy.json" to "executable shared commonTest delayed-response contract",
        "sdk/fake-transport/reconnect-after-failure-policy.json" to "executable shared commonTest reconnect-after-failure contract",
        "sdk/fake-transport/retry-delay-policy.json" to "executable shared commonTest retry-delay scheduler contract",
        "sdk/fake-transport/service-readiness-policy.json" to "executable shared commonTest fake-transport contract",
        "sdk/fake-transport/unscripted-operation-timeout-policy.json" to "executable shared commonTest fake-transport contract",
        "sdk/fake-transport/virtual-clock-timeout-policy.json" to "executable shared commonTest fake-transport contract"
    )

    private fun String.toFakeTransportOutcome(): CommonFakeTransportOutcome {
        return when (stringValue("type")) {
            "bytes" -> CommonFakeTransportOutcome.Bytes(hexToBytes(stringValue("valueHex")))
            "responseError" -> CommonFakeTransportOutcome.ResponseError(status = intValue("status"), message = stringValue("message"))
            "transportError" -> CommonFakeTransportOutcome.TransportError(message = stringValue("message"))
            "complete" -> CommonFakeTransportOutcome.Complete
            else -> error("Unsupported fake transport outcome $this")
        }
    }

    private fun ScriptedCommonFakeTransport.runOperation(operation: String): CommonFakeTransportOutcome {
        return when (operation.stringValue("operation")) {
            "read" -> read(operation.stringValue("target"))
            "write" -> write(operation.stringValue("target"), hexToBytes(operation.stringValue("payloadHex")))
            "subscribe" -> subscribe(operation.stringValue("target"))
            "unsubscribe" -> unsubscribe(operation.stringValue("target"))
            "reconnect" -> reconnect()
            else -> error("Unsupported fake transport operation $operation")
        }
    }

    private fun describeOutcome(outcome: CommonFakeTransportOutcome): String {
        return when (outcome) {
            is CommonFakeTransportOutcome.Bytes -> "bytes:${outcome.value.toHexString()}"
            is CommonFakeTransportOutcome.ResponseError -> "response-error:${outcome.status}:${outcome.message}"
            is CommonFakeTransportOutcome.TransportError -> "transport-error:${outcome.message}"
            is CommonFakeTransportOutcome.Timeout -> "timeout:${outcome.label}"
            CommonFakeTransportOutcome.Complete -> "complete"
        }
    }

    private fun describeCommand(command: CommonFakeTransportCommand): String {
        return listOfNotNull(command.operation.name, command.target, command.payloadHex).joinToString(":")
    }
}
