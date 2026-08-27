package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * NVIDIA API Catalog / NIM provider.
 *
 * Official docs indicate two reasoning control styles:
 * 1) chat_template_kwargs.enable_thinking (Nemotron and many template-based models)
 * 2) reasoning_effort (GPT-OSS deployments)
 *
 * We always write chat_template_kwargs.enable_thinking for an explicit toggle and
 * add a default reasoning_effort=medium for GPT-OSS models when thinking is enabled
 * and user has not set reasoning_effort manually.
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
    enableToolCall: Boolean = false
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
    enableToolCall = enableToolCall
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

        // Explicit thinking toggle for NVIDIA template-based reasoning models.
        val chatTemplateKwargs = jsonObject.optJSONObject("chat_template_kwargs") ?: JSONObject()
        chatTemplateKwargs.put("enable_thinking", enableThinking)
        jsonObject.put("chat_template_kwargs", chatTemplateKwargs)

        // GPT-OSS models on NVIDIA use reasoning_effort to control reasoning depth.
        val modelNameLower = modelName.lowercase()
        val isGptOss = modelNameLower.contains("gpt-oss")
        val gptOssEffort = if (enableThinking && isGptOss && !jsonObject.has("reasoning_effort")) {
            resolveGptOssReasoningEffort(context)
        } else {
            null
        }
        if (gptOssEffort != null) {
            jsonObject.put("reasoning_effort", gptOssEffort)
        }

        AppLogger.d(
            "NvidiaAIProvider",
            "NVIDIA thinking params applied: enable_thinking=$enableThinking, gpt_oss_reasoning_effort=$gptOssEffort"
        )

        return createJsonRequestBody(jsonObject.toString())
    }

    private fun resolveGptOssReasoningEffort(context: Context): String? {
        val qualityLevel = runCatching {
            runBlocking {
                ApiPreferences.getInstance(context).thinkingQualityLevelFlow.first()
            }
        }.getOrElse {
            AppLogger.w(
                "NvidiaAIProvider",
                "Failed to read thinking quality level for NVIDIA GPT-OSS; reasoning_effort not applied",
                it
            )
            return null
        }

        val efforts = listOf("low", "medium", "high", "max", "max")
        val qualityIndex = qualityLevel.coerceIn(
            ApiPreferences.MIN_THINKING_QUALITY_LEVEL,
            ApiPreferences.MAX_THINKING_QUALITY_LEVEL
        ) - 1
        return efforts[qualityIndex]
    }
}
