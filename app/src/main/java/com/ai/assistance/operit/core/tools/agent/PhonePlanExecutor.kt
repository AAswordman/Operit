package com.ai.assistance.operit.core.tools.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal interface PhoneUiDriver {
    suspend fun read(): PhoneUiSnapshot
    suspend fun tap(node: PhoneUiNode): Boolean
    suspend fun type(node: PhoneUiNode, text: String): Boolean
}

internal data class PhonePlanResult(
    val completed: Int,
    val attempted: Int,
    val lastActionAccepted: Boolean,
    val lastActionMayHaveRun: Boolean,
    val success: Boolean,
    val message: String,
    val conditionWaitMs: Long,
    val verifiedActions: Int = 0,
    val snapshot: PhoneUiSnapshot? = null,
)

/** Executes known selectors and explicit postconditions. Dispatched actions are never resent here. */
internal class PhonePlanExecutor(
    private val driver: PhoneUiDriver,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val onStep: (Int, Int, PhoneLocalAction) -> Unit = { _, _, _ -> },
    private val checkpoint: suspend () -> Unit = {},
) {
    private var conditionWaitMs = 0L

    suspend fun execute(
        template: PhoneLocalSequence,
        arguments: Map<String, String>,
        observed: PhoneUiSnapshot,
        remainingActions: Int,
    ): PhonePlanResult {
        var completed = 0
        var verifiedActions = 0
        var attempted = 0
        var accepted = false
        var mayHaveRun = false
        var lastSnapshot: PhoneUiSnapshot? = null
        conditionWaitMs = 0L
        try {
            // Bind the complete template before the first action, including replayed templates.
            val plan = template.bind(arguments)
            if (plan.steps.size > remainingActions) throw PhoneUiException("Sequence exceeds remaining action budget")
            var state = observed
            for ((index, step) in plan.steps.withIndex()) {
                checkpoint()
                onStep(index + 1, plan.steps.size, step.action)
                lastSnapshot = null
                // Wait sends no input; awaitTarget itself performs the fresh package/condition read.
                // Keep pre-dispatch reads for Tap/Type, including after model planning or takeover.
                if (step.action != PhoneLocalAction.Wait) state = read(plan.packageName)
                if (index == 0 && step.action != PhoneLocalAction.Wait) {
                    if (state.packageName != observed.packageName) throw PhoneUiException("Active app changed during planning")
                    step.target?.let { target ->
                        if (!unique(observed, target).sameTargetAs(unique(state, target))) {
                            throw PhoneUiException("Observed target changed while planning; no action was sent")
                        }
                    }
                }
                accepted = false
                mayHaveRun = false
                when (step.action) {
                    PhoneLocalAction.Tap -> {
                        val node = unique(state, requireNotNull(step.target))
                        if (!node.enabled || node.password || node.bounds?.isUsable != true) {
                            throw PhoneUiException("Tap target is disabled, secret or has no usable bounds")
                        }
                        val expected = requireNotNull(step.expect)
                        if (state.matches(expected).isNotEmpty()) {
                            throw PhoneUiException("Tap expectation already matches before the action; specify an observable change")
                        }
                        attempted++
                        mayHaveRun = true
                        accepted = driver.tap(node)
                        if (!accepted) throw PhoneUiException("Tap delivery was not confirmed; sequence stopped")
                        state = awaitTarget(plan.packageName, expected, step.timeoutMs)
                        lastSnapshot = state
                        verifiedActions++
                    }
                    PhoneLocalAction.Type -> {
                        val target = requireNotNull(step.target)
                        val node = unique(state, target)
                        if (!node.enabled || !node.editable || !node.focused || node.password) {
                            throw PhoneUiException("Type requires a unique enabled, focused, non-password editable target")
                        }
                        if (state.nodes.count { it.editable && it.focused && state.isVisible(it) } != 1) {
                            throw PhoneUiException("Input focus is ambiguous")
                        }
                        attempted++
                        val text = requireNotNull(step.text)
                        mayHaveRun = true
                        accepted = driver.type(node, text)
                        if (!accepted) throw PhoneUiException("Text delivery was not confirmed; sequence stopped")
                        state = awaitTarget(plan.packageName, target.copy(text = text), step.timeoutMs)
                        lastSnapshot = state
                        if (node.text != text) verifiedActions++
                    }
                    PhoneLocalAction.Wait -> {
                        attempted++
                        state = awaitTarget(plan.packageName, requireNotNull(step.expect), step.timeoutMs)
                        lastSnapshot = state
                    }
                }
                completed++
            }
            return PhonePlanResult(completed, attempted, accepted, mayHaveRun, true,
                "All local step conditions satisfied; verify the task outcome before finishing", conditionWaitMs, verifiedActions, lastSnapshot)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detail = if (e is PhoneUiException) e.message.orEmpty() else "Local execution stopped: ${e.javaClass.simpleName}"
            val status = if (mayHaveRun) " The last action may already have taken effect. Inspect before deciding whether to repeat it." else ""
            return PhonePlanResult(completed, attempted, accepted, mayHaveRun, false, detail + status, conditionWaitMs, verifiedActions, lastSnapshot)
        }
    }

    private suspend fun read(packageName: String): PhoneUiSnapshot {
        checkpoint()
        val state = driver.read()
        checkpoint()
        if (state.packageName != packageName) throw PhoneUiException("Active app changed; sequence stopped")
        return state
    }

    private fun unique(state: PhoneUiSnapshot, target: PhoneTarget): PhoneUiNode {
        val matches = state.matches(target)
        if (matches.size != 1) throw PhoneUiException(
            "Target must match exactly one node; matched ${matches.size}. Inspect the current screenshot before choosing another action; do not invent selectors for an unseen page."
        )
        return matches.single()
    }

    private suspend fun awaitTarget(packageName: String, target: PhoneTarget, timeoutMs: Long): PhoneUiSnapshot {
        var elapsedMs = 0L
        while (true) {
            // Paused time is not charged against the target's readiness deadline.
            checkpoint()
            val started = nowMs()
            val state = try {
                driver.read()
            } finally {
                val spent = (nowMs() - started).coerceAtLeast(0L)
                elapsedMs += spent
                conditionWaitMs += spent
            }
            checkpoint()
            if (state.packageName != packageName) throw PhoneUiException("Active app changed while waiting; sequence stopped")
            val matches = state.matches(target)
            if (matches.size > 1) throw PhoneUiException("Expected result is ambiguous; sequence stopped")
            if (matches.size == 1) return state
            if (elapsedMs >= timeoutMs) throw PhoneUiException("Expected result did not appear within ${timeoutMs}ms; sequence stopped")
            val pauseMs = minOf(100L, timeoutMs - elapsedMs)
            val beforeDelay = nowMs()
            try {
                delay(pauseMs)
            } finally {
                val spent = (nowMs() - beforeDelay).coerceAtLeast(0L)
                elapsedMs += spent
                conditionWaitMs += spent
            }
        }
    }
}
