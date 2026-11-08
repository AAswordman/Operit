package com.ai.assistance.operit.api.chat.decorator

import android.content.Context
import com.ai.assistance.operit.api.chat.runtime.FunctionThinkingRequest
import com.ai.assistance.operit.api.chat.AIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.config.model.ModelParameter
import com.ai.assistance.operit.core.tools.prompt.ToolPrompt
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import com.ai.assistance.operit.util.stream.Stream

/** 在功能请求发出时读取思考配置，避免配置只保存在界面而没有进入实际请求。 */
internal class FunctionalThinkingAIService(
    private val delegate: AIService,
    private val thinkingRequest: suspend () -> FunctionThinkingRequest,
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
    ): Stream<String> {
        val request = thinkingRequest()
        return delegate.sendMessage(
            context = context,
            chatHistory = chatHistory,
            modelParameters = modelParameters,
            enableThinking = request.enableThinking,
            thinkingOptionId = request.thinkingOptionId,
            stream = stream,
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
}