package com.ai.assistance.operit.api.chat.llmprovider
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.json.JSONObject

/**
 * 针对豆包（Doubao）模型的特定API Provider。
 * 继承自OpenAIProvider，以重用大部分兼容逻辑，但特别处理了`thinking`参数。
 */
class DoubaoAIProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    private val providerType: ApiProviderType = ApiProviderType.DOUBAO,
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

    /**
     * 重写创建请求体的方法，以支持豆包的`thinking`参数。
     * 按官方文档建议始终显式传入：enabled/disabled。
     */
    override fun createRequestBody(
        context: android.content.Context,
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

        ThinkingConfigurationApplier.apply(
            context = context,
            requestJson = jsonObject,
            providerTypeId = providerType.name,
            modelName = modelName,
            apiEndpoint = "",
            thinkingConfigurations = thinkingConfigurations,
            enableThinking = enableThinking,
            optionId = thinkingOptionId,
        )

        return createJsonRequestBody(jsonObject.toString())
    }
}
