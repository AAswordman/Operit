package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.json.JSONObject

/**
 * 针对阿里巴巴Qwen（通义千问）模型的特定API Provider。
 * 继承自OpenAIProvider，以重用大部分兼容逻辑，但特别处理了`enable_thinking`参数。
 */
class QwenAIProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    private val qwenProviderType: ApiProviderType = ApiProviderType.ALIYUN,
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
        providerType = qwenProviderType,
        supportsVision = supportsVision,
        supportsAudio = supportsAudio,
        supportsVideo = supportsVideo,
        enableToolCall = enableToolCall
    ) {

    override fun buildInputAudioPayload(link: MediaLink): JSONObject {
        val payload = super.buildInputAudioPayload(link)
        if (qwenProviderType == ApiProviderType.ALIYUN) {
            payload.put("data", "data:${link.mimeType};base64,${link.base64Data}")
        }
        return payload
    }

    /**
     * 重写创建请求体的方法，以支持Qwen的`enable_thinking`参数。
     */
    override fun createRequestBody(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean
    ): RequestBody {
        // 首先，调用父类的实现来获取一个标准的OpenAI格式的请求体JSON对象
        val baseRequestBodyJson = super.createRequestBodyInternal(context, chatHistory, modelParameters, stream, availableTools, preserveThinkInHistory)
        val jsonObject = JSONObject(baseRequestBodyJson)

        applyQwenReasoningSettings(
            context = context,
            requestJson = jsonObject,
            modelParameters = modelParameters,
            enableThinking = enableThinking
        )

        // 记录最终的请求体（省略过长的tools字段）
        val logJson = JSONObject(jsonObject.toString())
        if (logJson.has("tools")) {
            val toolsArray = logJson.getJSONArray("tools")
            logJson.put("tools", "[${toolsArray.length()} tools omitted for brevity]")
        }
        val sanitizedLogJson = sanitizeImageDataForLogging(logJson)
        logLargeString(
            "QwenAIProvider",
            sanitizedLogJson.toString(4),
            "Final Qwen-compatible request body: "
        )

        // 使用更新后的JSONObject创建新的RequestBody
        return createJsonRequestBody(jsonObject.toString())
    }

    private fun applyQwenReasoningSettings(
        context: Context,
        requestJson: JSONObject,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean
    ) {
        if (qwenProviderType != ApiProviderType.SILICONFLOW) {
            if (enableThinking && !requestJson.has("enable_thinking")) {
                requestJson.put("enable_thinking", true)
                AppLogger.d("QwenAIProvider", "已为Qwen模型启用“思考模式”。")
            }
            return
        }

        if (requestJson.has("enable_thinking")) {
            AppLogger.d(
                "QwenAIProvider",
                "Preserving caller-supplied SiliconFlow enable_thinking=${requestJson.opt("enable_thinking")}"
            )
        } else {
            requestJson.put("enable_thinking", enableThinking)
            AppLogger.d(
                "QwenAIProvider",
                "SiliconFlow thinking toggle applied via enable_thinking=$enableThinking"
            )
        }

        if (!enableThinking) {
            return
        }

        if (requestJson.has("thinking_budget")) {
            AppLogger.d(
                "QwenAIProvider",
                "Preserving caller-supplied SiliconFlow thinking_budget=${requestJson.opt("thinking_budget")}"
            )
            return
        }

        val thinkingBudget = resolveSiliconFlowThinkingBudget(context, requestJson, modelParameters) ?: return
        requestJson.put("thinking_budget", thinkingBudget)
        AppLogger.d(
            "QwenAIProvider",
            "SiliconFlow thinking budget applied via thinking_budget=$thinkingBudget"
        )
    }

    private fun resolveSiliconFlowThinkingBudget(
        context: Context,
        requestJson: JSONObject,
        modelParameters: List<ModelParameter<*>>
    ): Int? {
        val qualityLevel = runCatching {
            runBlocking {
                ApiPreferences.getInstance(context).thinkingQualityLevelFlow.first()
            }
        }.getOrElse {
            AppLogger.w(
                "QwenAIProvider",
                "Failed to read thinking quality level for SiliconFlow, falling back to provider default",
                it
            )
            return null
        }

        val thinkingBudgets = listOf(null, 4_096, 8_192, 16_384, 32_768)
        val qualityIndex = qualityLevel.coerceIn(
            ApiPreferences.MIN_THINKING_QUALITY_LEVEL,
            ApiPreferences.MAX_THINKING_QUALITY_LEVEL
        ) - 1
        val requestedBudget = thinkingBudgets[qualityIndex]

        if (requestedBudget == null) {
            return null
        }

        val modelMaxTokens =
            (modelParameters.firstOrNull { it.apiName == "max_tokens" && it.isEnabled }?.currentValue as? Number)
                ?.toInt()
                ?.takeIf { it > 1 }
                ?: requestJson.optInt("max_tokens", 0).takeIf { it > 1 }

        if (modelMaxTokens == null) {
            return requestedBudget
        }

        val cappedBudget = minOf(requestedBudget, modelMaxTokens - 1)
        return if (cappedBudget > 0) cappedBudget else null
    }

    override suspend fun sendMessage(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean,
        onTokensUpdated: suspend (input: Long, cachedInput: Long, output: Long) -> Unit,
        onUsageReported: (suspend (com.ai.assistance.operit.data.stats.ProviderUsageSnapshot, attempt: Int) -> Unit)?,
        onNonFatalError: suspend (error: String) -> Unit,
        enableRetry: Boolean,
        recordTokenUsage: Boolean,
        onUsageFinalized: (suspend (attempt: Int?) -> Unit)?,
    ): Stream<String> {
        // 直接调用父类的sendMessage实现，它已经包含了续写逻辑和stream参数处理
        return super.sendMessage(context, chatHistory, modelParameters, enableThinking, stream, availableTools, preserveThinkInHistory, onTokensUpdated, onUsageReported, onNonFatalError, enableRetry, recordTokenUsage, onUsageFinalized)
    }
}
