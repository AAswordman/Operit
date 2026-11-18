package com.ai.assistance.operit.core.chat

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*

/** Request-local cancellation: never cancels another request sharing the same provider instance. */
internal class SamplingInterrupt(private val inbox: TurnInputInbox) :
    AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SamplingInterrupt>
    private val lock = Any()
    @Volatile var interrupted = false
        private set
    private var abort: (() -> Unit)? = null
    var replayMetadata: (() -> String?)? = null

    fun bindAbort(action: () -> Unit) = synchronized(lock) {
        if (interrupted) {
            action()
            throw SamplingInterrupted()
        }
        abort = action
    }

    fun interrupt() = synchronized(lock) {
        interrupted = true
        runCatching { abort?.invoke() }
    }

    fun clear() = synchronized(lock) { abort = null }

    fun throwIfInterrupted() {
        if (interrupted) throw SamplingInterrupted()
    }

    /** Called by a provider only after a complete reasoning item, including replay metadata. */
    fun checkpoint() {
        if (inbox.hasPending()) {
            interrupt()
            throw SamplingInterrupted()
        }
    }
}

internal class SamplingInterrupted : CancellationException("Sampling yielded to new turn input")

/** User input preempts sampling; ordinary mailbox input waits for a provider's safe checkpoint. */
internal suspend fun runSteerableSampling(
    inbox: TurnInputInbox?,
    onInterruptedReplay: (String) -> Unit = {},
    block: suspend (SamplingInterrupt?) -> Unit,
): Boolean {
    if (inbox == null) {
        block(null)
        return false
    }
    if (inbox.hasPendingUserInput()) return true
    return coroutineScope {
        val control = SamplingInterrupt(inbox)
        val request = async(Dispatchers.IO + control, start = CoroutineStart.LAZY) { block(control) }
        val watcher = launch {
            inbox.awaitInput(userOnly = true)
            control.interrupt()
            request.cancel(SamplingInterrupted())
        }
        request.start()
        try {
            request.await()
            false
        } catch (e: CancellationException) {
            // A real Stop or parent cancellation must still terminate the entire turn.
            currentCoroutineContext().ensureActive()
            if (!control.interrupted) throw e
            control.replayMetadata?.invoke()?.let(onInterruptedReplay)
            true
        } finally {
            watcher.cancelAndJoin()
            control.clear()
        }
    }
}
