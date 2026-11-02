package com.ai.assistance.operit.api.chat.decorator

import android.content.Context
import com.ai.assistance.operit.api.chat.AIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.config.model.ModelParameter
import com.ai.assistance.operit.core.tools.prompt.ToolPrompt
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import com.ai.assistance.operit.util.stream.Stream

/** 在统一服务入口应用配置的流式选择，覆盖聊天、功能调用及后续工具轮次。 */
class ConfiguredStreamingAIService(
    private val delegate: AIService,
    private val enableStreamOutput: Boolean,
) : AIService by delegate {
    override suspend fun sendMessage(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        thinkingOptionId: String?,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean,
        onTokensUpdated: suspend (input: Long, cachedInput: Long, output: Long) -> Unit,
        onUsageReported: (suspend (ProviderUsageSnapshot, attempt: Int) -> Unit)?,
        onNonFatalError: suspend (error: String) -> Unit,
        enableRetry: Boolean,
        recordTokenUsage: Boolean,
        onUsageFinalized: (suspend (attempt: Int?) -> Unit)?,
    ): Stream<String> = delegate.sendMessage(
        context = context,
        chatHistory = chatHistory,
        modelParameters = modelParameters,
        enableThinking = enableThinking,
        thinkingOptionId = thinkingOptionId,
        // 配置允许流式时，仍尊重调用方主动使用非流式的要求。
        stream = stream && enableStreamOutput,
        availableTools = availableTools,
        preserveThinkInHistory = preserveThinkInHistory,
        onTokensUpdated = onTokensUpdated,
        onUsageReported = onUsageReported,
        onNonFatalError = onNonFatalError,
        enableRetry = enableRetry,
        recordTokenUsage = recordTokenUsage,
        onUsageFinalized = onUsageFinalized,
    )
}
