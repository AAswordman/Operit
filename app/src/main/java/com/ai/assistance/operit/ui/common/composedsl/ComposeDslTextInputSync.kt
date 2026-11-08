package com.ai.assistance.operit.ui.common.composedsl

import kotlinx.coroutines.CompletableDeferred

/**
 * Reconciles external text updates from the DSL runtime with the in-progress IME edits of a
 * compose_dsl text field.
 *
 * While a field is focused, its text, selection and IME composition are owned by the editor. Tree
 * snapshots pushed back by the DSL runtime are echoes of edits the field has already applied
 * locally, and they routinely lag behind by one or more keystrokes. Applying such a stale echo
 * would resurrect deleted characters, reset the cursor and drop the active composition, so echoes
 * must be recognized and skipped. Only values that cannot be attributed to the field's own edits
 * (template insertion, entry switch, programmatic updates) may be applied while editing.
 */
internal class ComposeDslTextFieldEchoGuard(initialText: String) {

    enum class ExternalUpdate {
        /** The tree caught up with the field text; nothing to apply. */
        CONVERGED,

        /** A stale snapshot of the field's own edits; keep the current field state. */
        OWN_ECHO,

        /** A genuine external change that must be applied to the field. */
        EXTERNAL_CHANGE
    }

    /** Last external text that was applied to the field or confirmed to match it. */
    private var lastSyncedText: String = initialText

    /** Texts the field dispatched upstream that no tree snapshot has confirmed yet. */
    private val pendingEchoTexts = ArrayDeque<String>()

    /** Records a local edit at the moment it is dispatched to the DSL runtime. */
    fun onLocalEditDispatched(text: String) {
        pendingEchoTexts.addLast(text)
    }

    fun reconcile(externalText: String, fieldText: String, isFocused: Boolean): ExternalUpdate {
        if (externalText == fieldText) {
            pendingEchoTexts.clear()
            lastSyncedText = externalText
            return ExternalUpdate.CONVERGED
        }
        if (isFocused) {
            if (externalText == lastSyncedText) {
                return ExternalUpdate.OWN_ECHO
            }
            val echoIndex = pendingEchoTexts.indexOf(externalText)
            if (echoIndex >= 0) {
                // Snapshots are produced in dispatch order, so entries older than the matched
                // echo can no longer arrive and are dropped.
                repeat(echoIndex + 1) { pendingEchoTexts.removeFirstOrNull() }
                return ExternalUpdate.OWN_ECHO
            }
        }
        pendingEchoTexts.clear()
        lastSyncedText = externalText
        return ExternalUpdate.EXTERNAL_CHANGE
    }
}

/**
 * Serializes compose_dsl text-input dispatches so the JS runtime applies edits in keystroke order.
 *
 * Each keystroke used to be dispatched on a fresh thread, so rapid edits could reach the JS engine
 * out of order and leave the runtime state older than what the user sees on screen. With a single
 * in-flight dispatch, runtime state is always a prefix of the edit sequence, and the tree pushed
 * back once the queue drains converges with the field.
 *
 * All methods are expected to be called from the main thread.
 */
internal class ComposeDslTextInputDispatchQueue(
    private val onAllSettled: () -> Unit
) {
    class Entry(val actionId: String, val text: String) {
        val completion = CompletableDeferred<Unit>()
    }

    /** Unsettled entries in dispatch order; the first one may currently be in flight. */
    private val entries = ArrayDeque<Entry>()
    private var dispatchInFlight = false
    private lateinit var startDispatch: (Entry, onSettled: () -> Unit) -> Unit

    fun hasPending(): Boolean = entries.isNotEmpty()

    suspend fun awaitAll() {
        entries.toList().forEach { entry -> runCatching { entry.completion.await() } }
    }

    /** Completes every pending entry without further dispatching, e.g. on re-render or teardown. */
    fun completeAll() {
        entries.forEach { entry ->
            if (!entry.completion.isCompleted) {
                entry.completion.complete(Unit)
            }
        }
        entries.clear()
        dispatchInFlight = false
    }

    fun enqueue(
        actionId: String,
        text: String,
        startDispatch: (Entry, onSettled: () -> Unit) -> Unit
    ) {
        this.startDispatch = startDispatch
        entries.addLast(Entry(actionId, text))
        pump()
    }

    private fun pump() {
        if (dispatchInFlight) {
            return
        }
        val next = entries.firstOrNull() ?: return
        dispatchInFlight = true
        startDispatch(next) { onEntrySettled(next) }
    }

    private fun onEntrySettled(entry: Entry) {
        dispatchInFlight = false
        if (!entries.remove(entry)) {
            // The queue was cleared before this dispatch settled; nothing left to drive.
            return
        }
        if (!entry.completion.isCompleted) {
            entry.completion.complete(Unit)
        }
        if (entries.isEmpty()) {
            onAllSettled()
        } else {
            pump()
        }
    }
}
