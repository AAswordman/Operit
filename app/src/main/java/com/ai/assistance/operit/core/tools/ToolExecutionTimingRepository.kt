package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.data.model.ToolExecutionState
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ToolExecutionTimingKey(
    val scopeId: String,
    val invocationIndex: Int,
)

data class ToolExecutionTimingSnapshot(
    val callId: String,
    val toolName: String,
    val state: ToolExecutionState,
    val startedAtElapsedMs: Long? = null,
    val durationMs: Long? = null,
    val success: Boolean? = null,
    val resultText: String = "",
    val errorText: String = "",
)

object ToolExecutionTimingRepository {
    private const val MAX_RETAINED_CALLS = 512

    private val mutableTimings =
        MutableStateFlow<Map<ToolExecutionTimingKey, ToolExecutionTimingSnapshot>>(emptyMap())

    val timings: StateFlow<Map<ToolExecutionTimingKey, ToolExecutionTimingSnapshot>> =
        mutableTimings.asStateFlow()

    fun get(scopeId: String?, invocationIndex: Int): ToolExecutionTimingSnapshot? {
        if (scopeId.isNullOrBlank() || invocationIndex < 0) return null
        return mutableTimings.value[ToolExecutionTimingKey(scopeId, invocationIndex)]
    }

    fun clearScope(scopeId: String) {
        mutableTimings.update { timings -> timings.filterKeys { it.scopeId != scopeId } }
    }

    fun register(scopeId: String?, invocation: ToolInvocation) {
        val callId = invocation.callId ?: return
        if (scopeId.isNullOrBlank() || invocation.invocationIndex < 0) return

        update(scopeId, invocation.invocationIndex) {
            ToolExecutionTimingSnapshot(
                callId = callId,
                toolName = invocation.tool.name,
                state = ToolExecutionState.WAITING_EXECUTION,
            )
        }
    }

    fun markWaitingAuthorization(scopeId: String?, invocation: ToolInvocation) {
        updateExisting(scopeId, invocation) {
            it.copy(state = ToolExecutionState.WAITING_AUTHORIZATION)
        }
    }

    fun markWaitingExecution(scopeId: String?, invocation: ToolInvocation) {
        updateExisting(scopeId, invocation) {
            it.copy(state = ToolExecutionState.WAITING_EXECUTION)
        }
    }

    fun markRunning(scopeId: String?, invocation: ToolInvocation, startedAtElapsedMs: Long) {
        updateExisting(scopeId, invocation) {
            it.copy(
                state = ToolExecutionState.RUNNING,
                startedAtElapsedMs = startedAtElapsedMs,
                durationMs = null,
            )
        }
    }

    fun markFinished(
        scopeId: String?,
        invocation: ToolInvocation,
        result: ToolResult,
        durationMs: Long?,
        state: ToolExecutionState,
    ) {
        updateExisting(scopeId, invocation) {
            it.copy(
                state = state,
                durationMs = durationMs,
                success = result.success,
                resultText = result.result.toString(),
                errorText = result.error.orEmpty(),
            )
        }
    }

    private fun updateExisting(
        scopeId: String?,
        invocation: ToolInvocation,
        transform: (ToolExecutionTimingSnapshot) -> ToolExecutionTimingSnapshot,
    ) {
        if (scopeId.isNullOrBlank() || invocation.invocationIndex < 0) return
        val key = ToolExecutionTimingKey(scopeId, invocation.invocationIndex)
        mutableTimings.update { current ->
            val existing = current[key] ?: return@update current
            current + (key to transform(existing))
        }
    }

    private fun update(
        scopeId: String,
        invocationIndex: Int,
        create: () -> ToolExecutionTimingSnapshot,
    ) {
        val key = ToolExecutionTimingKey(scopeId, invocationIndex)
        mutableTimings.update { current ->
            val updated = current + (key to create())
            if (updated.size <= MAX_RETAINED_CALLS) {
                updated
            } else {
                updated.entries
                    .drop(updated.size - MAX_RETAINED_CALLS)
                    .associate { it.toPair() }
            }
        }
    }
}
