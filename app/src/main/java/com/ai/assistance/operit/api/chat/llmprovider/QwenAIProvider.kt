package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.stream.Stream
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
    enableToolCall: Boolean = false,
    thinkingConfigurations: String = "",
    thinkingOptionId: String = ""
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
        enableToolCall = enableToolCall,
        thinkingConfigurations = thinkingConfigurations,
        thinkingOptionId = thinkingOptionId
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
        enableThinking: Boolean
    ) {
        if (qwenProviderType != ApiProviderType.SILICONFLOW) {
            return
        }
        ThinkingConfigurationApplier.apply(
            context = context,
            requestJson = requestJson,
            providerTypeId = qwenProviderType.name,
            modelName = modelName,
            apiEndpoint = "",
            thinkingConfigurations = thinkingConfigurations,
            enableThinking = enableThinking,
            optionId = thinkingOptionId,
        )
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
