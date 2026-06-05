package com.polar.shared.runtime

data class PolarStreamRuntimeSnapshot(
    val emittedValues: List<String> = emptyList(),
    val completionEventCount: Int = 0,
    val activeObserverCount: Int = 0,
    val cancelledStreams: List<String> = emptyList(),
    val cleanupCallbackCount: Int = 0,
    val errorEventCount: Int = 0,
    val upstreamCancelled: Boolean = false,
    val upstreamStarted: Boolean = false,
    val terminalError: String? = null,
    val connectionChecked: Boolean = false
)

class PolarStreamRuntimeState {
    private val emittedValuesState = mutableListOf<String>()
    private val cancelledStreamsState = mutableListOf<String>()
    private var completed = false
    private var completionEventCountState = 0
    private var activeObserverCountState = 0
    private var cleanupCallbackCountState = 0
    private var errorEventCountState = 0
    private var upstreamCancelledState = false
    private var upstreamStartedState = false
    private var terminalErrorState: String? = null
    private var connectionCheckedState = false

    val emittedValues: List<String> get() = emittedValuesState.toList()
    val cancelledStreams: List<String> get() = cancelledStreamsState.toList()
    val completionEventCount: Int get() = completionEventCountState
    val activeObserverCount: Int get() = activeObserverCountState
    val cleanupCallbackCount: Int get() = cleanupCallbackCountState
    val errorEventCount: Int get() = errorEventCountState
    val upstreamCancelled: Boolean get() = upstreamCancelledState
    val upstreamStarted: Boolean get() = upstreamStartedState
    val terminalError: String? get() = terminalErrorState
    val connectionChecked: Boolean get() = connectionCheckedState

    fun subscribe(target: String): PolarStreamRuntimeSnapshot {
        completed = false
        activeObserverCountState = 1
        upstreamCancelledState = false
        upstreamStartedState = true
        terminalErrorState = null
        errorEventCountState = 0
        connectionCheckedState = false
        cancelledStreamsState.remove(target)
        return snapshot()
    }

    fun subscribeWithConnectionGuard(target: String, startConnected: Boolean, checkConnection: Boolean): PolarStreamRuntimeSnapshot {
        connectionCheckedState = checkConnection
        if (checkConnection && !startConnected) {
            activeObserverCountState = 0
            upstreamStartedState = false
            terminalErrorState = "gattDisconnected"
            cancelledStreamsState.remove(target)
            return snapshot()
        }
        return subscribe(target)
    }

    fun complete(): PolarStreamRuntimeSnapshot {
        if (completed) return snapshot()
        completed = true
        completionEventCountState += 1
        activeObserverCountState = 0
        return snapshot()
    }

    fun fail(error: String): PolarStreamRuntimeSnapshot {
        if (completed) return snapshot()
        completed = true
        terminalErrorState = error
        errorEventCountState += 1
        activeObserverCountState = 0
        return snapshot()
    }

    fun emit(value: String): PolarStreamRuntimeSnapshot {
        if (!completed) emittedValuesState += value
        return snapshot()
    }

    fun cancelConsumer(target: String): PolarStreamRuntimeSnapshot {
        if (activeObserverCountState == 0) return snapshot()
        completed = true
        activeObserverCountState = 0
        upstreamCancelledState = true
        cleanupCallbackCountState += 1
        cancelledStreamsState += target
        return snapshot()
    }

    fun disconnect(error: String): PolarStreamRuntimeSnapshot {
        if (completed) return snapshot()
        completed = true
        terminalErrorState = error
        errorEventCountState += 1
        activeObserverCountState = 0
        upstreamCancelledState = true
        cleanupCallbackCountState += 1
        return snapshot()
    }

    fun snapshot(): PolarStreamRuntimeSnapshot {
        return PolarStreamRuntimeSnapshot(
            emittedValues = emittedValuesState.toList(),
            completionEventCount = completionEventCountState,
            activeObserverCount = activeObserverCountState,
            cancelledStreams = cancelledStreamsState.toList(),
            cleanupCallbackCount = cleanupCallbackCountState,
            errorEventCount = errorEventCountState,
            upstreamCancelled = upstreamCancelledState,
            upstreamStarted = upstreamStartedState,
            terminalError = terminalErrorState,
            connectionChecked = connectionCheckedState
        )
    }
}

object PolarStreamRuntimePlanning {
    fun newState(): PolarStreamRuntimeState = PolarStreamRuntimeState()

    fun planCheckedSubscription(target: String, startConnected: Boolean, checkConnection: Boolean): PolarStreamRuntimeSnapshot {
        return newState().subscribeWithConnectionGuard(target, startConnected, checkConnection)
    }

    fun planConsumerCancellation(target: String): PolarStreamRuntimeSnapshot {
        val state = newState()
        state.subscribe(target)
        return state.cancelConsumer(target)
    }

    fun planConsumerCancellationLateEvents(target: String, preCancelValue: String, postCancelValue: String, terminalError: String): PolarStreamRuntimeSnapshot {
        val state = newState()
        state.subscribe(target)
        state.emit(preCancelValue)
        state.cancelConsumer(target)
        state.emit(postCancelValue)
        state.fail(terminalError)
        return state.complete()
    }

    fun planDisconnectAfterSubscription(target: String, error: String): PolarStreamRuntimeSnapshot {
        val state = newState()
        state.subscribe(target)
        return state.disconnect(error)
    }

    fun planDuplicateCompletion(target: String): PolarStreamRuntimeSnapshot {
        val state = newState()
        state.subscribe(target)
        state.complete()
        return state.complete()
    }

    fun planPostCompletionEmissionSuppression(target: String, value: String): PolarStreamRuntimeSnapshot {
        val state = newState()
        state.subscribe(target)
        state.complete()
        return state.emit(value)
    }
}
