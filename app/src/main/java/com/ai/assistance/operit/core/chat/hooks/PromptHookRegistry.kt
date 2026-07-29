package com.ai.assistance.operit.core.chat.hooks

import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.CopyOnWriteArrayList

private const val TAG = "PromptHookRegistry"

data class PromptHookContext(
    val stage: String,
    val chatId: String? = null,
    val functionType: String? = null,
    val promptFunctionType: String? = null,
    val useEnglish: Boolean? = null,
    val rawInput: String? = null,
    val processedInput: String? = null,
    val chatHistory: List<PromptTurn> = emptyList(),
    val preparedHistory: List<PromptTurn> = emptyList(),
    val systemPrompt: String? = null,
    val dynamicContext: String? = null,  // 动态上下文（时间戳等频繁变化的内容）
    val toolPrompt: String? = null,
    val modelParameters: List<Map<String, Any?>> = emptyList(),
    val availableTools: List<Map<String, Any?>> = emptyList(),
    val metadata: Map<String, Any?> = emptyMap()
)

data class PromptHookMutation(
    val rawInput: String? = null,
    val processedInput: String? = null,
    val chatHistory: List<PromptTurn>? = null,
    val preparedHistory: List<PromptTurn>? = null,
    val systemPrompt: String? = null,
    val dynamicContext: String? = null,  // 动态上下文（时间戳等频繁变化的内容），注入到动态上下文消息而非系统提示词
    val toolPrompt: String? = null,
    val availableTools: List<Map<String, Any?>>? = null,
    val metadata: Map<String, Any?> = emptyMap()
)

interface PromptInputHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

interface PromptHistoryHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

interface PromptEstimateHistoryHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

interface SystemPromptComposeHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

interface ToolPromptComposeHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

interface PromptFinalizeHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

interface PromptEstimateFinalizeHook {
    val id: String

    fun onEvent(context: PromptHookContext): PromptHookMutation? = null
}

object PromptHookRegistry {
    private val promptInputHooks = CopyOnWriteArrayList<PromptInputHook>()
    private val promptHistoryHooks = CopyOnWriteArrayList<PromptHistoryHook>()
    private val promptEstimateHistoryHooks = CopyOnWriteArrayList<PromptEstimateHistoryHook>()
    private val systemPromptComposeHooks = CopyOnWriteArrayList<SystemPromptComposeHook>()
    private val toolPromptComposeHooks = CopyOnWriteArrayList<ToolPromptComposeHook>()
    private val promptFinalizeHooks = CopyOnWriteArrayList<PromptFinalizeHook>()
    private val promptEstimateFinalizeHooks = CopyOnWriteArrayList<PromptEstimateFinalizeHook>()

    @Synchronized
    fun registerPromptInputHook(hook: PromptInputHook) {
        unregisterPromptInputHook(hook.id)
        promptInputHooks.add(hook)
    }

    @Synchronized
    fun unregisterPromptInputHook(hookId: String) {
        promptInputHooks.removeAll { it.id == hookId }
    }

    @Synchronized
    fun registerPromptHistoryHook(hook: PromptHistoryHook) {
        unregisterPromptHistoryHook(hook.id)
        promptHistoryHooks.add(hook)
    }

    @Synchronized
    fun unregisterPromptHistoryHook(hookId: String) {
        promptHistoryHooks.removeAll { it.id == hookId }
    }

    @Synchronized
    fun registerPromptEstimateHistoryHook(hook: PromptEstimateHistoryHook) {
        unregisterPromptEstimateHistoryHook(hook.id)
        promptEstimateHistoryHooks.add(hook)
    }

    @Synchronized
    fun unregisterPromptEstimateHistoryHook(hookId: String) {
        promptEstimateHistoryHooks.removeAll { it.id == hookId }
    }

    @Synchronized
    fun registerSystemPromptComposeHook(hook: SystemPromptComposeHook) {
        unregisterSystemPromptComposeHook(hook.id)
        systemPromptComposeHooks.add(hook)
    }

    @Synchronized
    fun unregisterSystemPromptComposeHook(hookId: String) {
        systemPromptComposeHooks.removeAll { it.id == hookId }
    }

    @Synchronized
    fun registerToolPromptComposeHook(hook: ToolPromptComposeHook) {
        unregisterToolPromptComposeHook(hook.id)
        toolPromptComposeHooks.add(hook)
    }

    @Synchronized
    fun unregisterToolPromptComposeHook(hookId: String) {
        toolPromptComposeHooks.removeAll { it.id == hookId }
    }

    @Synchronized
    fun registerPromptFinalizeHook(hook: PromptFinalizeHook) {
        unregisterPromptFinalizeHook(hook.id)
        promptFinalizeHooks.add(hook)
    }

    @Synchronized
    fun unregisterPromptFinalizeHook(hookId: String) {
        promptFinalizeHooks.removeAll { it.id == hookId }
    }

    @Synchronized
    fun registerPromptEstimateFinalizeHook(hook: PromptEstimateFinalizeHook) {
        unregisterPromptEstimateFinalizeHook(hook.id)
        promptEstimateFinalizeHooks.add(hook)
    }

    @Synchronized
    fun unregisterPromptEstimateFinalizeHook(hookId: String) {
        promptEstimateFinalizeHooks.removeAll { it.id == hookId }
    }

    fun dispatchPromptInputHooks(initialContext: PromptHookContext): PromptHookContext {
        return dispatch(
            initialContext = initialContext,
            hooks = promptInputHooks,
            hookLabel = "PromptInputHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    fun dispatchPromptHistoryHooks(initialContext: PromptHookContext): PromptHookContext {
        return dispatch(
            initialContext = initialContext,
            hooks = promptHistoryHooks,
            hookLabel = "PromptHistoryHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    fun dispatchPromptEstimateHistoryHooks(initialContext: PromptHookContext): PromptHookContext {
        return dispatch(
            initialContext = initialContext,
            hooks = promptEstimateHistoryHooks,
            hookLabel = "PromptEstimateHistoryHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    fun dispatchSystemPromptComposeHooks(initialContext: PromptHookContext): PromptHookContext {
        return dispatch(
            initialContext = initialContext,
            hooks = systemPromptComposeHooks,
            hookLabel = "SystemPromptComposeHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    fun dispatchToolPromptComposeHooks(initialContext: PromptHookContext): PromptHookContext {
        return dispatch(
            initialContext = initialContext,
            hooks = toolPromptComposeHooks,
            hookLabel = "ToolPromptComposeHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    fun dispatchPromptFinalizeHooks(initialContext: PromptHookContext): PromptHookContext {
        return dispatch(
            initialContext = initialContext,
            hooks = promptFinalizeHooks,
            hookLabel = "PromptFinalizeHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    fun dispatchPromptEstimateFinalizeHooks(initialContext: PromptHookContext): PromptHookContext {
        return dispatch(
            initialContext = initialContext,
            hooks = promptEstimateFinalizeHooks,
            hookLabel = "PromptEstimateFinalizeHook"
        ) { hook, context ->
            hook.onEvent(context)
        }
    }

    private fun <THook> dispatch(
        initialContext: PromptHookContext,
        hooks: List<THook>,
        hookLabel: String,
        invoke: (THook, PromptHookContext) -> PromptHookMutation?
    ): PromptHookContext {
        var current = initialContext
        hooks.forEach { hook ->
            val mutation =
                runCatching { invoke(hook, current) }
                    .onFailure { error ->
                        AppLogger.e(TAG, "$hookLabel callback failed", error)
                    }
                    .getOrNull()
                    ?: return@forEach
            current = applyMutation(current, mutation)
        }
        return current
    }

    private fun applyMutation(
        current: PromptHookContext,
        mutation: PromptHookMutation
    ): PromptHookContext {
        val mergedMetadata =
            if (mutation.metadata.isEmpty()) {
                current.metadata
            } else {
                current.metadata + mutation.metadata
            }
        return current.copy(
            rawInput = mutation.rawInput ?: current.rawInput,
            processedInput = mutation.processedInput ?: current.processedInput,
            chatHistory = mutation.chatHistory ?: current.chatHistory,
            preparedHistory = mutation.preparedHistory ?: current.preparedHistory,
            systemPrompt = mutation.systemPrompt ?: current.systemPrompt,
            dynamicContext = mergeDynamicContext(current.dynamicContext, mutation.dynamicContext),
            toolPrompt = mutation.toolPrompt ?: current.toolPrompt,
            availableTools = mutation.availableTools ?: current.availableTools,
            metadata = mergedMetadata
        )
    }

    /**
     * 合并动态上下文：多个 hook 提供的动态上下文追加拼接，而非替换。
     */
    private fun mergeDynamicContext(current: String?, incoming: String?): String? {
        if (incoming.isNullOrBlank()) return current
        if (current.isNullOrBlank()) return incoming
        return "$current\n$incoming"
    }
}
