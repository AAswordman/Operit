package com.ai.assistance.operit.core.agent.collaboration

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

enum class CollaborationWaitOutcome { MAILBOX, STEERED, IDLE, TIMED_OUT }

/** Legacy callers without an activity subscription use this polling fallback. */
private const val WAIT_POLL_MS = 100L
private const val WAIT_DELIVER_INTERVAL_MS = 1_000L

/**
 * Storage arrival is not readiness: the next model boundary drains the execution inbox.
 *
 * Reconciliation runs immediately and then at most once per [WAIT_DELIVER_INTERVAL_MS], because
 * senders push their own deliveries and the retry path is only a fallback. Production callers
 * subscribe to inbox/state changes; callers without a subscription use [WAIT_POLL_MS].
 */
internal suspend fun awaitCollaborationInput(
    timeoutMs: Long,
    deliverToCurrentTurn: suspend () -> Unit,
    pendingInput: () -> CollaborationWaitOutcome?,
    hasWaitableWork: () -> Boolean = { true },
    awaitActivity: (suspend () -> Unit)? = null,
): CollaborationWaitOutcome = withTimeoutOrNull(timeoutMs) {
    var nextDeliverAtMs = 0L
    while (true) {
        pendingInput()?.let { return@withTimeoutOrNull it }
        val nowMs = System.currentTimeMillis()
        if (nowMs >= nextDeliverAtMs) {
            deliverToCurrentTurn()
            nextDeliverAtMs = nowMs + WAIT_DELIVER_INTERVAL_MS
            pendingInput()?.let { return@withTimeoutOrNull it }
        }
        // Only after delivery: a queued mailbox entry must reach the inbox before giving up.
        if (!hasWaitableWork()) return@withTimeoutOrNull CollaborationWaitOutcome.IDLE
        if (awaitActivity == null) delay(WAIT_POLL_MS)
        else withTimeoutOrNull(WAIT_DELIVER_INTERVAL_MS) { awaitActivity() }
    }
    @Suppress("UNREACHABLE_CODE")
    CollaborationWaitOutcome.TIMED_OUT
} ?: CollaborationWaitOutcome.TIMED_OUT

/**
 * Work this wait can still observe: mail the caller has not consumed, a live job anywhere in its
 * root conversation, an in-flight spawn reservation, or a streaming peer turn.
 *
 * `finish()` and `send()` enqueue their message before they register or drop a job, so one
 * snapshot of these inputs proves that nothing can arrive for the rest of this wait.
 */
internal fun hasWaitableWork(
    rootChatId: String,
    ownKey: String,
    mailboxEmpty: Boolean,
    jobKeys: Set<String>,
    reservationKeys: Set<String>,
    peerChatIds: Set<String>,
    streamingChatIds: Set<String>,
): Boolean {
    if (!mailboxEmpty) return true
    if (reservationKeys.any { it.startsWith("$rootChatId:") }) return true
    if (jobKeys.any { it != ownKey && it.startsWith("$rootChatId:") }) return true
    return peerChatIds.any { it in streamingChatIds }
}

data class CollaborationWaitResult(val outcome: CollaborationWaitOutcome, val requestedTimeoutMs: Long, val minimumTimeoutMs: Long = 10_000) {
    val timedOut: Boolean get() = outcome == CollaborationWaitOutcome.TIMED_OUT
    val message: String get() {
        val result = when (outcome) {
            CollaborationWaitOutcome.MAILBOX -> "Wait completed."
            CollaborationWaitOutcome.STEERED -> "Wait interrupted by new input."
            CollaborationWaitOutcome.IDLE ->
                "Nothing to wait for right now: no other agent is running and your mailbox is empty. " +
                    "If a child you just created is still being set up, call wait_agent again once it " +
                    "is running; otherwise end the turn: nothing can arrive until you send work."
            CollaborationWaitOutcome.TIMED_OUT -> "Wait timed out."
        }
        return if (requestedTimeoutMs < minimumTimeoutMs) {
            "$result\n\nRequested timeout of ${requestedTimeoutMs}ms was clamped to the minimum of ${minimumTimeoutMs}ms."
        } else result
    }
}
