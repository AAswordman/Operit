package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterValueType
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.Stream
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Routes OpenCode Zen/Go models to the protocol-specific provider already used by Operit. */
class OpenCodeProvider private constructor(
    private val delegate: AIService,
    private val baseEndpoint: String,
    private val modelName: String,
    private val protocol: ApiProviderType,
    private val apiKeyProvider: ApiKeyProvider,
    private val thinkingConfigurations: String,
    private val thinkingOptionId: String
) : AIService by delegate {
    // Keep the routed provider identity so shared response handling recognizes Responses/Gemini streams.
    override val providerModel: String = delegate.providerModel

    override suspend fun getModelsList(context: Context): Result<List<ModelOption>> {
        return ModelListFetcher.getModelsList(
            context = context,
            apiKey = apiKeyProvider.getApiKey(),
            apiEndpoint = baseEndpoint,
            apiProviderType = ApiProviderType.OPENCODE
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
        onUsageReported: (suspend (ProviderUsageSnapshot, attempt: Int) -> Unit)?,
        onNonFatalError: suspend (error: String) -> Unit,
        enableRetry: Boolean,
        recordTokenUsage: Boolean,
        onUsageFinalized: (suspend (attempt: Int?) -> Unit)?,
    ): Stream<String> {
        val (thinkingMapping, opencodeParameters) = ThinkingConfigurationApplier.modelParameters(
            providerTypeId = ApiProviderType.OPENCODE.name,
            modelName = modelName,
            apiEndpoint = baseEndpoint,
            thinkingConfigurations = thinkingConfigurations,
            enableThinking = enableThinking,
            optionId = thinkingOptionId,
            protocol = protocol,
        )
        val thinkingEnabled = enableThinking || thinkingMapping.reasoningRequired

        return delegate.sendMessage(
            context = context,
            chatHistory = chatHistory,
            modelParameters = modelParameters + opencodeParameters,
            enableThinking = thinkingEnabled && protocol == ApiProviderType.OPENAI_RESPONSES_GENERIC,
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

    companion object {
        fun create(
            config: ModelConfigData,
            context: Context,
            client: OkHttpClient,
            customHeaders: Map<String, String>,
            apiKeyProvider: ApiKeyProvider,
            supportsVision: Boolean,
            supportsAudio: Boolean,
            supportsVideo: Boolean,
            enableToolCall: Boolean
        ): AIService {
            val model = config.modelName.trim().removePrefix("opencode/").removePrefix("opencode-go/")
            val endpoint = OpenCodeRouting.endpointFor(config.apiEndpoint, model)
            val provider = OpenCodeRouting.protocolFor(config.apiEndpoint, model)
            val routed: AIService = when (provider) {
                ApiProviderType.OPENAI_RESPONSES_GENERIC -> OpenCodeResponsesProvider(
                    endpoint, apiKeyProvider, model, client, customHeaders,
                    supportsVision, supportsAudio, supportsVideo, enableToolCall
                )
                ApiProviderType.ANTHROPIC_GENERIC -> OpenCodeClaudeProvider(
                    endpoint, apiKeyProvider, model, client, customHeaders, enableToolCall
                )
                ApiProviderType.GEMINI_GENERIC -> OpenCodeGeminiProvider(
                    endpoint, apiKeyProvider, model, client, customHeaders, enableToolCall
                )
                else -> OpenCodeChatProvider(
                    endpoint, apiKeyProvider, model, context.applicationContext, client, customHeaders,
                    supportsVision, supportsAudio, supportsVideo, enableToolCall
                )
            }
            return OpenCodeProvider(
                delegate = routed,
                baseEndpoint = config.apiEndpoint,
                modelName = model,
                protocol = provider,
                apiKeyProvider = apiKeyProvider,
                thinkingConfigurations = config.thinkingConfigurations,
                thinkingOptionId = config.thinkingOptionId
            )
        }
    }
}

internal object OpenCodeRouting {
    fun protocolFor(baseEndpoint: String, modelName: String): ApiProviderType {
        val model = modelName.trim().lowercase()
        val provider = model.substringBefore('/').takeIf { it != model }.orEmpty()
        val modelId = model.substringAfterLast('/')
        return when {
            provider == "openai" || provider == "azure" || provider == "xai" ||
                modelId.startsWith("gpt-") || modelId.startsWith("grok-") || modelId.contains("codex") ->
                ApiProviderType.OPENAI_RESPONSES_GENERIC
            provider == "anthropic" || provider == "minimax" || modelId.startsWith("claude-") || modelId.startsWith("minimax-") ->
                ApiProviderType.ANTHROPIC_GENERIC
            provider == "google" || modelId.startsWith("gemini-") -> ApiProviderType.GEMINI_GENERIC
            else -> ApiProviderType.OPENAI_GENERIC
        }
    }

    fun endpointFor(baseEndpoint: String, modelName: String): String {
        val base = normalizedBase(baseEndpoint)
        return when (protocolFor(baseEndpoint, modelName)) {
            ApiProviderType.OPENAI_RESPONSES_GENERIC -> "$base/responses"
            ApiProviderType.ANTHROPIC_GENERIC -> "$base/messages"
            ApiProviderType.GEMINI_GENERIC -> "$base/models/$modelName"
            else -> "$base/chat/completions"
        }
    }

    fun modelsEndpoint(baseEndpoint: String): String = "${normalizedBase(baseEndpoint)}/models"

    fun catalogProviderId(baseEndpoint: String): String =
        if (isGo(baseEndpoint)) "opencode-go" else "opencode"

    private fun normalizedBase(endpoint: String): String {
        val trimmed = endpoint.trim().removeSuffix("/")
        return if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
    }

    fun apiBase(endpoint: String): String =
        normalizedBase(endpoint.substringBefore("/models/"))

    private fun isGo(endpoint: String): Boolean {
        val trimmed = endpoint.trim().removeSuffix("/").lowercase()
        return trimmed.endsWith("/zen/go") || trimmed.endsWith("/zen/go/v1")
    }
}

/** OpenCode's OpenAI-compatible chat route. */
internal class OpenCodeChatProvider(
    endpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    private val appContext: Context,
    client: OkHttpClient,
    customHeaders: Map<String, String>,
    supportsVision: Boolean,
    supportsAudio: Boolean,
    supportsVideo: Boolean,
    enableToolCall: Boolean
) : OpenAIProvider(
    apiEndpoint = endpoint,
    apiKeyProvider = apiKeyProvider,
    modelName = modelName,
    client = client,
    customHeaders = customHeaders,
    providerType = ApiProviderType.OPENAI_GENERIC,
    supportsVision = supportsVision,
    supportsAudio = supportsAudio,
    supportsVideo = supportsVideo,
    enableToolCall = enableToolCall
) {
    // OpenCode 的 OpenAI Chat 路由会向后端透传 DeepSeek 风格 thinking 输出
    // （即使 Operit 未显式开启 thinking，OpenCode 也可能按模型能力自行开启）。
    // 该协议要求历史 assistant 消息必须原样回传 reasoning_content，否则
    // 在模型完成工具调用后的下一轮请求会返回 400：
    //   "The reasoning_content in the thinking mode must be passed back to the API."
    //
    // 以下重写将 reasoning_content 的提取与回传完全收敛在 OpenCodeChatProvider 内部：
    //   1) 强制 preserveThinkInHistory = true，让父类 buildMessagesAndCountTokens
    //      保留历史 assistant 消息中的 <think> 内容；
    //   2) 覆盖 customizeFinalRequestObject，在 messagesArray 后处理阶段对
    //      assistant 消息做 think -> reasoning_content 的拆分与回填。
    // 该实现不动通用 OpenAIProvider，不影响其它 OpenAI 兼容 provider 的行为。
    override fun createRequestBody(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean
    ): RequestBody = createJsonRequestBody(
        createRequestBodyInternal(
            context = context,
            chatHistory = chatHistory,
            modelParameters = modelParameters,
            stream = stream,
            availableTools = availableTools,
            preserveThinkInHistory = true
        )
    )

    override fun customizeFinalRequestObject(
        requestObject: JSONObject,
        messagesArray: JSONArray,
        toolsJson: String?
    ) {
        if (messagesArray.length() == 0) return
        for (i in 0 until messagesArray.length()) {
            val message = messagesArray.optJSONObject(i) ?: continue
            if (message.optString("role") != "assistant") continue
            extractReasoningContentIntoMessage(appContext, message)
        }
    }

    /**
     * 对单个 assistant 消息提取 <think>...</think> 内容，写入 reasoning_content 字段，
     * 并将清理后的内容写回 content 字段。
     *
     * 仅处理纯文本 content（多模态 JSONArray 形态跳过，避免改动既有
     * 多模态构造逻辑；该路径在 OpenCodeChatProvider 实际请求中极少触发）。
     */
    private fun extractReasoningContentIntoMessage(context: Context, message: JSONObject) {
        val textContent = message.opt("content") as? String
        val (cleanContent, reasoning) = textContent?.let(ChatUtils::extractThinkingContent)
            ?: ("" to "")
        // 总是写入 reasoning_content（即使为空，OpenAI Chat 路由上游在历史不含
        // 该字段时也会拒绝；写空字符串与未写含义不同）。
        message.put("reasoning_content", reasoning)
        if (textContent != null) {
            // Assistant history must not re-inject rich media links while the thinking
            // marker is being split into the provider-specific reasoning field.
            message.put("content", buildContentField(context, cleanContent, role = "assistant"))
        }
    }
}

/** OpenCode's OpenAI Responses route. */
internal class OpenCodeResponsesProvider(
    endpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String>,
    supportsVision: Boolean,
    supportsAudio: Boolean,
    supportsVideo: Boolean,
    enableToolCall: Boolean
) : OpenAIProvider(
    apiEndpoint = endpoint,
    apiKeyProvider = apiKeyProvider,
    modelName = modelName,
    client = client,
    customHeaders = customHeaders,
    providerType = ApiProviderType.OPENAI_RESPONSES_GENERIC,
    supportsVision = supportsVision,
    supportsAudio = supportsAudio,
    supportsVideo = supportsVideo,
    enableToolCall = enableToolCall
) {
    override val useResponsesApi: Boolean = true

    override fun createRequestBody(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean
    ): RequestBody {
        val requestChatHistory =
            if (enableThinking) chatHistory
            else ChatUtils.stripOpenAiResponsesReasoningMetaTurns(chatHistory)
        return createJsonRequestBody(
            createRequestBodyInternal(
                context = context,
                chatHistory = requestChatHistory,
                modelParameters = modelParameters,
                stream = stream,
                availableTools = availableTools,
                preserveThinkInHistory = preserveThinkInHistory
            )
        )
    }
}

/** OpenCode's Anthropic-compatible route. */
internal class OpenCodeClaudeProvider(
    endpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String>,
    enableToolCall: Boolean
) : ClaudeProvider(
    apiEndpoint = endpoint,
    apiKeyProvider = apiKeyProvider,
    modelName = modelName,
    client = client,
    customHeaders = customHeaders,
    providerType = ApiProviderType.ANTHROPIC_GENERIC,
    enableToolCall = enableToolCall,
    thinkingConfigurations = "[]"
) {
    override fun addParameters(
        jsonObject: JSONObject,
        modelParameters: List<ModelParameter<*>>
    ) {
        super.addParameters(jsonObject, modelParameters)
        modelParameters
            .filter { it.isEnabled }
            .filter { it.apiName == "thinking" || it.apiName == "budget_tokens" || it.apiName == "output_config" }
            .forEach { parameter -> putJsonParameter(jsonObject, parameter) }
    }

    private fun putJsonParameter(jsonObject: JSONObject, parameter: ModelParameter<*>) {
        when (parameter.valueType) {
            ParameterValueType.OBJECT -> {
                val raw = parameter.currentValue.toString().trim()
                runCatching { JSONObject(raw) }
                    .onSuccess { jsonObject.put(parameter.apiName, it) }
                    .onFailure { error ->
                        AppLogger.w(
                            "OpenCodeClaudeProvider",
                            "Invalid OpenCode JSON parameter: " + parameter.apiName,
                            error
                        )
                    }
            }
            ParameterValueType.STRING -> jsonObject.put(parameter.apiName, parameter.currentValue as String)
            ParameterValueType.INT -> jsonObject.put(parameter.apiName, parameter.currentValue as Int)
            ParameterValueType.FLOAT -> jsonObject.put(parameter.apiName, parameter.currentValue as Float)
            ParameterValueType.BOOLEAN -> jsonObject.put(parameter.apiName, parameter.currentValue as Boolean)
        }
    }
}

/** OpenCode's Google-compatible route, including its API key and SSE conventions. */
internal class OpenCodeGeminiProvider(
    private val endpoint: String,
    private val opencodeApiKeyProvider: ApiKeyProvider,
    private val opencodeModelName: String,
    client: OkHttpClient,
    private val opencodeCustomHeaders: Map<String, String>,
    enableToolCall: Boolean
) : GeminiProvider(
    apiEndpoint = endpoint,
    apiKeyProvider = opencodeApiKeyProvider,
    modelName = opencodeModelName,
    client = client,
    customHeaders = opencodeCustomHeaders,
    providerType = ApiProviderType.GEMINI_GENERIC,
    enableToolCall = enableToolCall,
    thinkingConfigurations = "[]"
) {
    override suspend fun createRequest(
        context: Context,
        requestBody: RequestBody,
        isStreaming: Boolean,
        requestId: String
    ): Request {
        val base = OpenCodeRouting.apiBase(endpoint)
        val method = if (isStreaming) "streamGenerateContent" else "generateContent"
        val suffix = if (isStreaming) "?alt=sse" else ""
        val requestUrl = base + "/models/" + opencodeModelName + ":" + method + suffix
        val builder = Request.Builder()
            .url(requestUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", opencodeApiKeyProvider.getApiKey())
        opencodeCustomHeaders.forEach { (key, value) -> builder.addHeader(key, value) }
        AppLogger.d("OpenCodeGeminiProvider", "OpenCode Gemini request URL: " + requestUrl)
        return builder.build()
    }
}
