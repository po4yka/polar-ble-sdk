package com.polar.sharedtest

class ScriptedCommonFakeTransport(
    outcomes: List<CommonFakeTransportOutcome>,
    startConnected: Boolean = true,
    private val disconnectAfterOperations: Int? = null
) {
    private val pendingOutcomes = ArrayDeque(outcomes)
    private val capturedCommands = mutableListOf<CommonFakeTransportCommand>()
    private val activeStreamTargets = mutableSetOf<String>()
    private val cancelledStreamTargets = mutableListOf<String>()
    private var completedOperationCount = 0
    private var connected = startConnected
    private var cleanupCallbacks = 0

    val commands: List<CommonFakeTransportCommand>
        get() = capturedCommands.toList()

    val activeObserverCount: Int
        get() = activeStreamTargets.size

    val cancelledStreams: List<String>
        get() = cancelledStreamTargets.toList()

    val isConnected: Boolean
        get() = connected

    val cleanupCallbackCount: Int
        get() = cleanupCallbacks

    fun read(target: String): CommonFakeTransportOutcome {
        capturedCommands += CommonFakeTransportCommand(CommonFakeTransportOperation.READ, target)
        return nextOutcome()
    }

    fun write(target: String, payload: ByteArray): CommonFakeTransportOutcome {
        capturedCommands += CommonFakeTransportCommand(CommonFakeTransportOperation.WRITE, target, payload.toHex())
        return nextOutcome()
    }

    fun remove(target: String): CommonFakeTransportOutcome {
        capturedCommands += CommonFakeTransportCommand(CommonFakeTransportOperation.REMOVE, target)
        return nextOutcome()
    }

    fun subscribe(target: String): CommonFakeTransportOutcome {
        capturedCommands += CommonFakeTransportCommand(CommonFakeTransportOperation.SUBSCRIBE, target)
        return nextOutcome()
    }

    fun unsubscribe(target: String): CommonFakeTransportOutcome {
        capturedCommands += CommonFakeTransportCommand(CommonFakeTransportOperation.UNSUBSCRIBE, target)
        return nextOutcome()
    }

    fun reconnect(): CommonFakeTransportOutcome {
        capturedCommands += CommonFakeTransportCommand(CommonFakeTransportOperation.RECONNECT, "transport")
        connected = true
        return CommonFakeTransportOutcome.Complete
    }

    fun openStream(target: String): CommonFakeTransportSubscription {
        val subscribeOutcome = subscribe(target)
        if (subscribeOutcome is CommonFakeTransportOutcome.TransportError || subscribeOutcome is CommonFakeTransportOutcome.ResponseError || subscribeOutcome is CommonFakeTransportOutcome.Timeout) {
            return CommonFakeTransportSubscription(target, this, subscribeOutcome, isActive = false)
        }
        activeStreamTargets += target
        return CommonFakeTransportSubscription(target, this, subscribeOutcome, isActive = true)
    }

    internal fun cancelStream(target: String): CommonFakeTransportOutcome {
        val wasActive = activeStreamTargets.remove(target)
        if (wasActive) {
            cancelledStreamTargets += target
            cleanupCallbacks += 1
        }
        return unsubscribe(target)
    }

    private fun nextOutcome(): CommonFakeTransportOutcome {
        if (!connected) {
            return CommonFakeTransportOutcome.TransportError("disconnected")
        }
        val disconnectLimit = disconnectAfterOperations
        if (disconnectLimit != null && completedOperationCount >= disconnectLimit) {
            connected = false
            return CommonFakeTransportOutcome.TransportError("disconnected-after-$disconnectLimit-operations")
        }
        completedOperationCount += 1
        return pendingOutcomes.removeFirstOrNull() ?: CommonFakeTransportOutcome.Timeout("unscripted-operation")
    }
}

class CommonFakeTransportSubscription internal constructor(
    val target: String,
    private val transport: ScriptedCommonFakeTransport,
    val subscribeOutcome: CommonFakeTransportOutcome,
    private var isActive: Boolean
) {
    val upstreamCancelled: Boolean
        get() = target in transport.cancelledStreams

    fun cancel(): CommonFakeTransportOutcome {
        if (!isActive) {
            return CommonFakeTransportOutcome.Complete
        }
        isActive = false
        return transport.cancelStream(target)
    }
}

class CommonFakeServiceReadinessGate(
    private val readinessByAttempt: List<Boolean>
) {
    private var attemptIndex = 0
    private val readinessChecks = mutableListOf<String>()

    val checks: List<String>
        get() = readinessChecks.toList()

    fun awaitReady(service: String, maxAttempts: Int): CommonFakeTransportOutcome {
        repeat(maxAttempts) {
            readinessChecks += service
            val ready = readinessByAttempt.getOrElse(attemptIndex) { false }
            attemptIndex += 1
            if (ready) {
                return CommonFakeTransportOutcome.Complete
            }
        }
        return CommonFakeTransportOutcome.Timeout("service-readiness")
    }
}

class CommonFakeVirtualClock {
    private var nowMillis = 0L

    val currentTimeMillis: Long
        get() = nowMillis

    fun advanceBy(millis: Long) {
        require(millis >= 0) { "Virtual time cannot move backwards" }
        nowMillis += millis
    }

    fun hasTimedOut(startMillis: Long, timeoutMillis: Long): Boolean {
        return nowMillis - startMillis >= timeoutMillis
    }
}

class CommonFakeRetryScheduler(
    private val clock: CommonFakeVirtualClock
) {
    private val scheduledRetryTimes = mutableListOf<Long>()

    val retryTimesMillis: List<Long>
        get() = scheduledRetryTimes.toList()

    fun runRetryDelays(delaysMillis: List<Long>, maxRetries: Int) {
        require(maxRetries >= 0) { "Retry count cannot be negative" }
        delaysMillis.take(maxRetries).forEach { delayMillis ->
            clock.advanceBy(delayMillis)
            scheduledRetryTimes += clock.currentTimeMillis
        }
    }
}

class CommonFakeDelayedResponse(
    private val clock: CommonFakeVirtualClock,
    private val target: String,
    private val delayMillis: Long,
    private val payload: ByteArray
) {
    private val pollAttempts = mutableListOf<String>()

    val polls: List<String>
        get() = pollAttempts.toList()

    fun poll(): CommonFakeTransportOutcome {
        pollAttempts += "$target@${clock.currentTimeMillis}"
        return if (clock.currentTimeMillis >= delayMillis) {
            CommonFakeTransportOutcome.Bytes(payload)
        } else {
            CommonFakeTransportOutcome.Timeout("delayed-response")
        }
    }
}

data class CommonFakeTransportCommand(
    val operation: CommonFakeTransportOperation,
    val target: String,
    val payloadHex: String? = null
)

enum class CommonFakeTransportOperation {
    READ,
    WRITE,
    REMOVE,
    SUBSCRIBE,
    UNSUBSCRIBE,
    RECONNECT
}

sealed class CommonFakeTransportOutcome {
    data class Bytes(val value: ByteArray) : CommonFakeTransportOutcome()
    data class TransportError(val message: String) : CommonFakeTransportOutcome()
    data class ResponseError(val status: Int, val message: String) : CommonFakeTransportOutcome()
    data class Timeout(val label: String) : CommonFakeTransportOutcome()
    object Complete : CommonFakeTransportOutcome()
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
