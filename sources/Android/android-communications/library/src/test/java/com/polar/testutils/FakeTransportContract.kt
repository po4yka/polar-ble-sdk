package com.polar.testutils

class ScriptedFakeTransport(
    outcomes: List<FakeTransportOutcome>
) {
    private val pendingOutcomes = ArrayDeque(outcomes)
    private val capturedCommands = mutableListOf<FakeTransportCommand>()
    private val activeStreamTargets = mutableSetOf<String>()
    private val cancelledStreamTargets = mutableListOf<String>()

    val commands: List<FakeTransportCommand>
        get() = capturedCommands.toList()

    val activeObserverCount: Int
        get() = activeStreamTargets.size

    val cancelledStreams: List<String>
        get() = cancelledStreamTargets.toList()

    fun read(target: String): FakeTransportOutcome {
        capturedCommands += FakeTransportCommand(FakeTransportOperation.READ, target)
        return nextOutcome()
    }

    fun write(target: String, payload: ByteArray): FakeTransportOutcome {
        capturedCommands += FakeTransportCommand(FakeTransportOperation.WRITE, target, payload.toHex())
        return nextOutcome()
    }

    fun subscribe(target: String): FakeTransportOutcome {
        capturedCommands += FakeTransportCommand(FakeTransportOperation.SUBSCRIBE, target)
        return nextOutcome()
    }

    fun unsubscribe(target: String): FakeTransportOutcome {
        capturedCommands += FakeTransportCommand(FakeTransportOperation.UNSUBSCRIBE, target)
        return nextOutcome()
    }

    fun openStream(target: String): FakeTransportSubscription {
        val subscribeOutcome = subscribe(target)
        if (subscribeOutcome is FakeTransportOutcome.TransportError || subscribeOutcome is FakeTransportOutcome.ResponseError || subscribeOutcome is FakeTransportOutcome.Timeout) {
            return FakeTransportSubscription(target, this, subscribeOutcome, isActive = false)
        }
        activeStreamTargets += target
        return FakeTransportSubscription(target, this, subscribeOutcome, isActive = true)
    }

    internal fun cancelStream(target: String): FakeTransportOutcome {
        val wasActive = activeStreamTargets.remove(target)
        if (wasActive) {
            cancelledStreamTargets += target
        }
        return unsubscribe(target)
    }

    private fun nextOutcome(): FakeTransportOutcome {
        return pendingOutcomes.removeFirstOrNull() ?: FakeTransportOutcome.Timeout("unscripted-operation")
    }
}

class FakeTransportSubscription internal constructor(
    val target: String,
    private val transport: ScriptedFakeTransport,
    val subscribeOutcome: FakeTransportOutcome,
    private var isActive: Boolean
) {
    val upstreamCancelled: Boolean
        get() = target in transport.cancelledStreams

    fun cancel(): FakeTransportOutcome {
        if (!isActive) {
            return FakeTransportOutcome.Complete
        }
        isActive = false
        return transport.cancelStream(target)
    }
}

data class FakeTransportCommand(
    val operation: FakeTransportOperation,
    val target: String,
    val payloadHex: String? = null
)

enum class FakeTransportOperation {
    READ,
    WRITE,
    SUBSCRIBE,
    UNSUBSCRIBE
}

sealed class FakeTransportOutcome {
    data class Bytes(val value: ByteArray) : FakeTransportOutcome()
    data class TransportError(val message: String) : FakeTransportOutcome()
    data class ResponseError(val status: Int, val message: String) : FakeTransportOutcome()
    data class Timeout(val label: String) : FakeTransportOutcome()
    object Complete : FakeTransportOutcome()
}

private fun ByteArray.toHex(): String {
    return joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}
