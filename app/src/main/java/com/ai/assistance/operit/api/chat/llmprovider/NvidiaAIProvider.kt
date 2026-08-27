package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.json.JSONObject

/**
 * NVIDIA API Catalog / NIM provider.
 *
 * Official docs expose model-specific reasoning_effort values for GPT-OSS and
 * the current Nemotron 3 Super/Ultra endpoints.
 *
 * The request mapper writes only the control published for the selected model.
 */
class NvidiaAIProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    providerType: ApiProviderType = ApiProviderType.NVIDIA,
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
    providerType = providerType,
    supportsVision = supportsVision,
    supportsAudio = supportsAudio,
    supportsVideo = supportsVideo,
    enableToolCall = enableToolCall,
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
        val baseRequestBodyJson = super.createRequestBodyInternal(
            context,
            chatHistory,
            modelParameters,
            stream,
            availableTools,
            preserveThinkInHistory
        )
        val jsonObject = JSONObject(baseRequestBodyJson)

        ThinkingConfigurationApplier.apply(
            context = context,
            requestJson = jsonObject,
            providerTypeId = ApiProviderType.NVIDIA.name,
            modelName = modelName,
            apiEndpoint = "",
            thinkingConfigurations = thinkingConfigurations,
            enableThinking = enableThinking,
            optionId = thinkingOptionId,
        )

        return createJsonRequestBody(jsonObject.toString())
    }
}
