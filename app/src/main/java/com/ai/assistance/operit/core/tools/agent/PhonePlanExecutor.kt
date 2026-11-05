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
)

/** Executes known selectors and explicit postconditions. Dispatched actions are never resent here. */
internal class PhonePlanExecutor(
    private val driver: PhoneUiDriver,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
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
        var attempted = 0
        var accepted = false
        var mayHaveRun = false
        conditionWaitMs = 0L
        try {
            // Bind the complete template before the first action, including replayed templates.
            val plan = template.bind(arguments)
            if (plan.steps.size > remainingActions) throw PhoneUiException("Sequence exceeds remaining action budget")
            var state = read(plan.packageName)
            if (state.fingerprint != observed.fingerprint || state.packageName != observed.packageName) {
                throw PhoneUiException("Screen changed while the controller was planning; no action was sent")
            }
            for ((index, step) in plan.steps.withIndex()) {
                checkpoint()
                if (index > 0) state = read(plan.packageName)
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
                        awaitTarget(plan.packageName, expected, step.timeoutMs)
                    }
                    PhoneLocalAction.Type -> {
                        val target = requireNotNull(step.target)
                        val node = unique(state, target)
                        if (!node.enabled || !node.editable || !node.focused || node.password) {
                            throw PhoneUiException("Type requires a unique enabled, focused, non-password editable target")
                        }
                        attempted++
                        val text = requireNotNull(step.text)
                        mayHaveRun = true
                        accepted = driver.type(node, text)
                        if (!accepted) throw PhoneUiException("Text delivery was not confirmed; sequence stopped")
                        awaitTarget(plan.packageName, target.copy(text = text), step.timeoutMs)
                    }
                    PhoneLocalAction.Wait -> {
                        attempted++
                        awaitTarget(plan.packageName, requireNotNull(step.expect), step.timeoutMs)
                    }
                }
                completed++
            }
            return PhonePlanResult(completed, attempted, accepted, mayHaveRun, true,
                "All local step conditions satisfied; verify the task outcome before finishing", conditionWaitMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detail = if (e is PhoneUiException) e.message.orEmpty() else "Local execution stopped: ${e.javaClass.simpleName}"
            val status = if (mayHaveRun) " The last action may already have taken effect. Inspect before deciding whether to repeat it." else ""
            return PhonePlanResult(completed, attempted, accepted, mayHaveRun, false, detail + status, conditionWaitMs)
        }
    }

    private suspend fun read(packageName: String): PhoneUiSnapshot {
        checkpoint()
        val state = driver.read()
        if (state.packageName != packageName) throw PhoneUiException("Active app changed; sequence stopped")
        return state
    }

    private fun unique(state: PhoneUiSnapshot, target: PhoneTarget): PhoneUiNode {
        val matches = state.matches(target)
        if (matches.size != 1) throw PhoneUiException("Target must match exactly one node; matched ${matches.size}")
        return matches.single()
    }

    private suspend fun awaitTarget(packageName: String, target: PhoneTarget, timeoutMs: Long) {
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
            if (state.packageName != packageName) throw PhoneUiException("Active app changed while waiting; sequence stopped")
            val matches = state.matches(target)
            if (matches.size > 1) throw PhoneUiException("Expected result is ambiguous; sequence stopped")
            if (matches.size == 1) return
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
