package com.ai.assistance.operit.core.chat.hooks

import com.ai.assistance.operit.util.logging.AppLogger
import java.util.concurrent.CopyOnWriteArrayList

private const val SUMMARY_TAG = "SummaryHookRegistry"

data class SummaryHookContext(
    val stage: String,
    val useEnglish: Boolean? = null,
    val previousSummary: String? = null,
    val chatHistory: List<PromptTurn> = emptyList(),
    val preparedHistory: List<PromptTurn> = emptyList(),
    val systemPrompt: String? = null,
    val summaryPrompt: String? = null,
    val summaryResult: String? = null,
    val modelParameters: List<Map<String, Any?>> = emptyList(),
    val metadata: Map<String, Any?> = emptyMap()
)

data class SummaryHookMutation(
    val chatHistory: List<PromptTurn>? = null,
    val preparedHistory: List<PromptTurn>? = null,
    val systemPrompt: String? = null,
    val summaryPrompt: String? = null,
    val summaryResult: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
)

interface SummaryGenerateHook {
    val id: String

    fun onEvent(context: SummaryHookContext): SummaryHookMutation? = null
}

object SummaryHookRegistry {
    private val summaryGenerateHooks = CopyOnWriteArrayList<SummaryGenerateHook>()

    @Synchronized
    fun registerSummaryGenerateHook(hook: SummaryGenerateHook) {
        unregisterSummaryGenerateHook(hook.id)
        summaryGenerateHooks.add(hook)
    }

    @Synchronized
    fun unregisterSummaryGenerateHook(hookId: String) {
        summaryGenerateHooks.removeAll { it.id == hookId }
    }

    fun dispatchSummaryGenerateHooks(initialContext: SummaryHookContext): SummaryHookContext {
        return dispatch(
            initialContext = initialContext,
            hooks = summaryGenerateHooks,
            hookLabel = "SummaryGenerateHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    private fun <THook> dispatch(
        initialContext: SummaryHookContext,
        hooks: List<THook>,
        hookLabel: String,
        invoke: (THook, SummaryHookContext) -> SummaryHookMutation?
    ): SummaryHookContext {
        var current = initialContext
        hooks.forEach { hook ->
            val mutation =
                runCatching { invoke(hook, current) }
                    .onFailure { error ->
                        AppLogger.e(SUMMARY_TAG, "$hookLabel callback failed", error)
                    }
                    .getOrNull()
                    ?: return@forEach
            current = applyMutation(current, mutation)
        }
        return current
    }

    private fun applyMutation(
        current: SummaryHookContext,
        mutation: SummaryHookMutation
    ): SummaryHookContext {
        val mergedMetadata =
            if (mutation.metadata.isEmpty()) {
                current.metadata
            } else {
                current.metadata + mutation.metadata
            }
        return current.copy(
            chatHistory = mutation.chatHistory ?: current.chatHistory,
            preparedHistory = mutation.preparedHistory ?: current.preparedHistory,
            systemPrompt = mutation.systemPrompt ?: current.systemPrompt,
            summaryPrompt = mutation.summaryPrompt ?: current.summaryPrompt,
            summaryResult = mutation.summaryResult ?: current.summaryResult,
            metadata = mergedMetadata
        )
    }
}
