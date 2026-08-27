package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.json.JSONObject

/** xAI's OpenAI-compatible Chat Completions provider for Grok models. */
class XaiProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    supportsVision: Boolean = false,
    supportsAudio: Boolean = false,
    supportsVideo: Boolean = false,
    enableToolCall: Boolean = false,
    thinkingConfigurations: String = "",
    thinkingOptionId: String = ""
) : OpenAIProvider(
    apiEndpoint = apiEndpoint,
    apiKeyProvider = apiKeyProvider,
    modelName = modelName,
    client = client,
    customHeaders = customHeaders,
    providerType = ApiProviderType.XAI,
    supportsVision = supportsVision,
    supportsAudio = supportsAudio,
    supportsVideo = supportsVideo,
    enableToolCall = enableToolCall,
    includeUsageInStream = true,
    thinkingConfigurations = thinkingConfigurations,
        thinkingOptionId = thinkingOptionId
) {
    override fun createRequestBody(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean
    ): RequestBody {
        val requestJson = JSONObject(
            super.createRequestBodyInternal(
                context,
                chatHistory,
                modelParameters,
                stream,
                availableTools,
                preserveThinkInHistory
            )
        )

        ThinkingConfigurationApplier.apply(
            context = context,
            requestJson = requestJson,
            providerTypeId = ApiProviderType.XAI.name,
            modelName = modelName,
            apiEndpoint = "",
            thinkingConfigurations = thinkingConfigurations,
            enableThinking = enableThinking,
            optionId = thinkingOptionId,
        )

        return createJsonRequestBody(requestJson.toString())
    }
}
