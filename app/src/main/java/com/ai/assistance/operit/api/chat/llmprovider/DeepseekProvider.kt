package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.Stream
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * 针对DeepSeek模型的特定API Provider。
 * 继承自OpenAIProvider，以重用大部分兼容逻辑，但特别处理了`reasoning_content`参数。
 * 当启用推理模式时，会将assistant消息中的 thinking标签内容提取出来作为reasoning_content字段。
 */
class DeepseekProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    providerType: ApiProviderType = ApiProviderType.DEEPSEEK,
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
    private val configuredApiEndpoint = apiEndpoint
    /** DeepSeek Files API 图片上传器（与 Responses 协议线共用的抽取实现） */
    private val fileUploader = DeepseekFileUploader(apiEndpoint, apiKeyProvider, client, customHeaders)

    /** 发送前把消息中的图片上传至 DeepSeek Files API 换取 file_id，失败回退 base64 */
    override suspend fun prepareMediaForRequest(context: Context, chatHistory: List<PromptTurn>) {
        fileUploader.prepareMedia(supportsVision, chatHistory, "DeepseekProvider")
    }

    /** 已有 file_id 时用 DeepSeek 文件块引用，否则回退基类的 base64 内嵌 */
    override fun buildImageContentPart(link: ImageLink): JSONObject {
        val fileId = fileUploader.fileIdFor(link.id)
        if (fileId != null) {
            return JSONObject().apply {
                put("type", "file")
                put("file_id", fileId)
            }
        }
        return super.buildImageContentPart(link)
    }
    companion object {
        fun create(
            config: ModelConfigData,
            client: OkHttpClient,
            customHeaders: Map<String, String>,
            apiKeyProvider: ApiKeyProvider,
            supportsVision: Boolean,
            supportsAudio: Boolean,
            supportsVideo: Boolean,
            enableToolCall: Boolean
        ): AIService {
            return when (DeepseekRouting.protocolFor(config.apiEndpoint)) {
                DeepseekApiProtocol.CHAT_COMPLETIONS ->
                    DeepseekProvider(
                        apiEndpoint = config.apiEndpoint,
                        apiKeyProvider = apiKeyProvider,
                        modelName = config.modelName,
                        client = client,
                        customHeaders = customHeaders,
                        providerType = ApiProviderType.DEEPSEEK,
                        supportsVision = supportsVision,
                        supportsAudio = supportsAudio,
                        supportsVideo = supportsVideo,
                        enableToolCall = enableToolCall,
                        thinkingConfigurations = config.thinkingConfigurations,
                        thinkingOptionId = config.thinkingOptionId
                    )

                DeepseekApiProtocol.RESPONSES ->
                    DeepseekResponsesProvider(
                        responsesApiEndpoint = config.apiEndpoint,
                        apiKeyProvider = apiKeyProvider,
                        modelName = config.modelName,
                        client = client,
                        customHeaders = customHeaders,
                        supportsVision = supportsVision,
                        supportsAudio = supportsAudio,
                        supportsVideo = supportsVideo,
                        enableToolCall = enableToolCall,
                        thinkingConfigurations = config.thinkingConfigurations,
                        thinkingOptionId = config.thinkingOptionId,
                        enableWebSearch = config.enableDeepSeekWebSearch
                    )
            }
        }
    }

    /**
     * 重写创建请求体的方法，以支持DeepSeek的`reasoning_content`参数。
     * 当启用推理模式时，需要特殊处理消息格式。
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
        fun applyThinkingParamsIfNeeded(jsonObject: JSONObject) {
            ThinkingConfigurationApplier.apply(
                context = context,
                requestJson = jsonObject,
                providerTypeId = ApiProviderType.DEEPSEEK.name,
                modelName = modelName,
                apiEndpoint = configuredApiEndpoint,
                thinkingConfigurations = thinkingConfigurations,
                enableThinking = enableThinking,
                optionId = thinkingOptionId,
            )
        }

        // 如果未启用推理模式，直接使用父类的实现
        // 推理模式固定开启，需要特殊处理
        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", stream)
        if (stream) {
            jsonObject.put("stream_options", JSONObject().put("include_usage", true))
        }

        // DeepSeek Thinking Mode 默认开启，关闭时也必须显式发送 thinking.type=disabled。
        applyThinkingParamsIfNeeded(jsonObject)

        // 添加已启用的模型参数
        for (param in modelParameters) {
            if (param.isEnabled) {
                when (param.valueType) {
                    com.ai.assistance.operit.data.model.ParameterValueType.INT ->
                        jsonObject.put(param.apiName, param.currentValue as Int)
                    com.ai.assistance.operit.data.model.ParameterValueType.FLOAT ->
                        jsonObject.put(param.apiName, param.currentValue as Float)
                    com.ai.assistance.operit.data.model.ParameterValueType.STRING ->
                        jsonObject.put(param.apiName, param.currentValue as String)
                    com.ai.assistance.operit.data.model.ParameterValueType.BOOLEAN ->
                        jsonObject.put(param.apiName, param.currentValue as Boolean)
                    com.ai.assistance.operit.data.model.ParameterValueType.OBJECT -> {
                        val raw = param.currentValue.toString().trim()
                        val parsed: Any? = try {
                            when {
                                raw.startsWith("{") -> JSONObject(raw)
                                raw.startsWith("[") -> JSONArray(raw)
                                else -> null
                            }
                        } catch (e: Exception) {
                            AppLogger.w("DeepseekProvider", "OBJECT参数解析失败: ${param.apiName}", e)
                            null
                        }
                        if (parsed != null) {
                            jsonObject.put(param.apiName, parsed)
                        } else {
                            jsonObject.put(param.apiName, raw)
                        }
                    }
                }
            }
        }

        // 当工具为空时，将enableToolCall视为false
        val effectiveEnableToolCall = enableToolCall && availableTools != null && availableTools.isNotEmpty()

        // 如果启用Tool Call且传入了工具列表，添加tools定义
        var toolsJson: String? = null
        if (effectiveEnableToolCall) {
            val tools = buildToolDefinitions(availableTools!!)
            if (tools.length() > 0) {
                jsonObject.put("tools", tools)
                jsonObject.put("tool_choice", "auto")
                toolsJson = tools.toString()
            }
        }

        val providerReadyHistory = prepareHistoryForProvider(chatHistory, effectiveEnableToolCall)
        calculateAndStoreInputTokens(
            providerReadyHistory,
            toolsJson,
            preserveThinkInHistory = true
        )

        // 使用特殊的消息构建方法（支持reasoning_content）
        val messagesArray =
            buildMessagesWithReasoning(
                context,
                providerReadyHistory,
                effectiveEnableToolCall
            )
        jsonObject.put("messages", messagesArray)

        // 记录最终的请求体（省略过长的tools字段）
        val logJson = JSONObject(jsonObject.toString())
        if (logJson.has("tools")) {
            val toolsArray = logJson.getJSONArray("tools")
            logJson.put("tools", "[${toolsArray.length()} tools omitted for brevity]")
        }
        val sanitizedLogJson = sanitizeImageDataForLogging(logJson)
        logLargeString("DeepseekProvider", sanitizedLogJson.toString(4), "Final DeepSeek reasoning mode request body: ")

        return createJsonRequestBody(jsonObject.toString())
    }

    /**
     * 构建支持reasoning_content的消息数组
     * 对于assistant角色的消息，提取 thinking标签内容作为reasoning_content
     */
    private fun buildMessagesWithReasoning(
        context: Context,
        effectiveHistory: List<PromptTurn>,
        useToolCall: Boolean
    ): JSONArray {
        val messagesArray = JSONArray()

        var queuedAssistantToolText: String? = null
        var queuedAssistantReasoning: String? = null
        var queuedToolCalls = JSONArray()
        val queuedOpenToolCalls = mutableListOf<StructuredToolCallBridge.OpenToolCall>()
        val openToolCalls = mutableListOf<StructuredToolCallBridge.OpenToolCall>()
        var nextToolCallOrdinal = 0

        fun appendQueuedAssistantToolText(text: String) {
            if (text.isBlank()) return
            queuedAssistantToolText =
                if (queuedAssistantToolText.isNullOrBlank()) {
                    text
                } else {
                    queuedAssistantToolText + "\n" + text
                }
        }

        fun appendQueuedAssistantReasoning(reasoningContent: String) {
            if (reasoningContent.isBlank()) return
            queuedAssistantReasoning =
                if (queuedAssistantReasoning.isNullOrBlank()) {
                    reasoningContent
                } else {
                    queuedAssistantReasoning + "\n" + reasoningContent
                }
        }

        fun queueToolCalls(textContent: String, toolCalls: JSONArray, reasoningContent: String = "") {
            appendQueuedAssistantToolText(textContent)
            appendQueuedAssistantReasoning(reasoningContent)
            for (i in 0 until toolCalls.length()) {
                val sourceToolCall = toolCalls.optJSONObject(i) ?: continue
                val toolCall = JSONObject(sourceToolCall.toString())
                val callId = generatedToolCallId(nextToolCallOrdinal++)
                toolCall.put("id", callId)
                queuedToolCalls.put(toolCall)
                queuedOpenToolCalls.add(
                    StructuredToolCallBridge.OpenToolCall(
                        callId,
                        StructuredToolCallBridge.toolCallName(toolCall)
                    )
                )
            }
        }

        fun emitQueuedToolCallsIfNeeded() {
            if (queuedToolCalls.length() == 0) return

            messagesArray.put(
                JSONObject().apply {
                    put("role", "assistant")
                    put("reasoning_content", queuedAssistantReasoning.orEmpty())
                    if (!queuedAssistantToolText.isNullOrBlank()) {
                        put("content", buildContentField(context, queuedAssistantToolText!!, role = "assistant"))
                    } else {
                        put("content", null)
                    }
                    put("tool_calls", queuedToolCalls)
                }
            )

            openToolCalls.addAll(queuedOpenToolCalls)
            queuedAssistantToolText = null
            queuedAssistantReasoning = null
            queuedToolCalls = JSONArray()
            queuedOpenToolCalls.clear()
        }

        fun flushOpenToolCallsAsCancelled(reason: String) {
            emitQueuedToolCallsIfNeeded()
            if (openToolCalls.isEmpty()) return

            AppLogger.w(
                "DeepseekProvider",
                "发现未完成的tool_calls，按取消处理: count=${openToolCalls.size}, reason=$reason"
            )
            for (openToolCall in openToolCalls) {
                messagesArray.put(
                    JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", openToolCall.id)
                        put("content", "User cancelled")
                    }
                )
            }
            openToolCalls.clear()
        }

        if (effectiveHistory.isNotEmpty()) {
            for (turn in effectiveHistory) {
                val originalContent = comparableContentForTurn(turn, preserveThinkInHistory = true)
                if (useToolCall) {
                    when (turn.kind) {
                        PromptTurnKind.SYSTEM -> {
                            flushOpenToolCallsAsCancelled("system_boundary")
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "system")
                                    put("content", buildContentField(context, originalContent, role = "system"))
                                }
                            )
                        }

                        PromptTurnKind.USER,
                        PromptTurnKind.SUMMARY -> {
                            flushOpenToolCallsAsCancelled("user_boundary")
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "user")
                                    put("content", buildContentField(context, originalContent))
                                }
                            )
                        }

                        PromptTurnKind.ASSISTANT -> {
                            val (content, reasoningContent) = ChatUtils.extractThinkingContent(originalContent)
                            val (textContent, parsedToolCalls) = parseXmlToolCalls(content)
                            val toolCalls =
                                if (parsedToolCalls != null) {
                                    wrapPackageToolCallsWithProxy(parsedToolCalls)
                                } else {
                                    null
                                }

                            if (toolCalls != null && toolCalls.length() > 0) {
                                if (openToolCalls.isNotEmpty()) {
                                    flushOpenToolCallsAsCancelled("assistant_tool_call_before_result")
                                }
                                queueToolCalls(textContent, toolCalls, reasoningContent)
                            } else {
                                flushOpenToolCallsAsCancelled("assistant_boundary")
                                messagesArray.put(
                                    JSONObject().apply {
                                        put("role", "assistant")
                                        put("reasoning_content", reasoningContent)
                                        put("content", buildContentField(context, content.ifBlank { "[Empty]" }, role = "assistant"))
                                    }
                                )
                                appendReadableImageMessageIfNeeded(
                                    messagesArray,
                                    content,
                                    "assistant message"
                                )
                            }
                        }

                        PromptTurnKind.TOOL_CALL -> {
                            val (textContent, parsedToolCalls) = parseXmlToolCalls(originalContent)
                            val toolCalls =
                                if (parsedToolCalls != null) {
                                    wrapPackageToolCallsWithProxy(parsedToolCalls)
                                } else {
                                    null
                                }

                            if (toolCalls != null && toolCalls.length() > 0) {
                                if (openToolCalls.isNotEmpty()) {
                                    flushOpenToolCallsAsCancelled("typed_tool_call_before_result")
                                }
                                queueToolCalls(textContent, toolCalls)
                            } else {
                                flushOpenToolCallsAsCancelled("typed_tool_call_without_payload")
                                messagesArray.put(
                                    JSONObject().apply {
                                        put("role", "assistant")
                                        put("reasoning_content", "")
                                        put("content", buildContentField(context, originalContent.ifBlank { "[Empty]" }, role = "assistant"))
                                    }
                                )
                                appendReadableImageMessageIfNeeded(
                                    messagesArray,
                                    originalContent,
                                    "assistant tool-call message"
                                )
                            }
                        }

                        PromptTurnKind.TOOL_RESULT -> {
                            emitQueuedToolCallsIfNeeded()
                            val (textContent, toolResults) = parseXmlToolResults(originalContent)
                            val resultsList = toolResults ?: emptyList()

                            if (resultsList.isNotEmpty() && openToolCalls.isNotEmpty()) {
                                val readableImageSources = mutableListOf<String>()
                                val matchedCalls =
                                    StructuredToolCallBridge.consumeMatchingToolCalls(
                                        openToolCalls,
                                        resultsList.map { it.first }
                                    )
                                matchedCalls.forEach { matchedCall ->
                                    val resultContent = resultsList[matchedCall.resultIndex].second
                                    readableImageSources.add(resultContent)
                                    messagesArray.put(
                                        JSONObject().apply {
                                            put("role", "tool")
                                            put("tool_call_id", matchedCall.call.id)
                                            put("content", buildContentField(context, resultContent, role = "tool"))
                                        }
                                    )
                                }

                                if (matchedCalls.size < resultsList.size) {
                                    AppLogger.w(
                                        "DeepseekProvider",
                                        "发现未匹配的tool_result: ${resultsList.size - matchedCalls.size}"
                                    )
                                }

                                flushOpenToolCallsAsCancelled("tool_result_partial_batch")

                                appendReadableImageMessageIfNeeded(
                                    messagesArray,
                                    readableImageSources,
                                    "tool result"
                                )

                                if (textContent.isNotEmpty()) {
                                    messagesArray.put(
                                        JSONObject().apply {
                                            put("role", "user")
                                            put("content", buildContentField(context, textContent))
                                        }
                                    )
                                }
                            } else {
                                flushOpenToolCallsAsCancelled("tool_result_without_structured_match")
                                if (textContent.isNotEmpty()) {
                                    messagesArray.put(
                                        JSONObject().apply {
                                            put("role", "user")
                                            put("content", buildContentField(context, textContent))
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    when (turn.kind) {
                        PromptTurnKind.SYSTEM -> {
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "system")
                                    put("content", buildContentField(context, originalContent, role = "system"))
                                }
                            )
                        }

                        PromptTurnKind.USER,
                        PromptTurnKind.SUMMARY -> {
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "user")
                                    put("content", buildContentField(context, originalContent))
                                }
                            )
                        }

                        PromptTurnKind.TOOL_RESULT -> {
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "user")
                                    put("content", buildContentField(context, originalContent))
                                }
                            )
                        }

                        PromptTurnKind.ASSISTANT -> {
                            val (content, reasoningContent) = ChatUtils.extractThinkingContent(originalContent)
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "assistant")
                                    put("reasoning_content", reasoningContent)
                                    put("content", buildContentField(context, content.ifBlank { "[Empty]" }, role = "assistant"))
                                }
                            )
                            appendReadableImageMessageIfNeeded(
                                messagesArray,
                                content,
                                "assistant message"
                            )
                        }

                        PromptTurnKind.TOOL_CALL -> {
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "assistant")
                                    put("reasoning_content", "")
                                    put("content", buildContentField(context, originalContent.ifBlank { "[Empty]" }, role = "assistant"))
                                }
                            )
                            appendReadableImageMessageIfNeeded(
                                messagesArray,
                                originalContent,
                                "assistant tool-call message"
                            )
                        }
                    }
                }
            }
        }

        flushOpenToolCallsAsCancelled("history_end")
        return messagesArray
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
        // 直接调用父类的sendMessage实现
        return super.sendMessage(context, chatHistory, modelParameters, enableThinking, stream, availableTools, preserveThinkInHistory, onTokensUpdated, onUsageReported, onNonFatalError, enableRetry, recordTokenUsage, onUsageFinalized)
    }
}

private enum class DeepseekApiProtocol {
    CHAT_COMPLETIONS,
    RESPONSES
}

private object DeepseekRouting {
    fun protocolFor(endpoint: String): DeepseekApiProtocol {
        val normalizedEndpoint =
            endpoint
                .trim()
                .removeSuffix("#")
                .substringBefore('?')
                .substringBefore('#')
                .removeSuffix("/")
        return if (normalizedEndpoint.endsWith("/responses", ignoreCase = true)) {
            DeepseekApiProtocol.RESPONSES
        } else {
            DeepseekApiProtocol.CHAT_COMPLETIONS
        }
    }
}

private class DeepseekResponsesProvider(
    private val responsesApiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String>,
    supportsVision: Boolean,
    supportsAudio: Boolean,
    supportsVideo: Boolean,
    enableToolCall: Boolean,
    thinkingConfigurations: String,
    thinkingOptionId: String,
    private val enableWebSearch: Boolean
) : OpenAIProvider(
    apiEndpoint = responsesApiEndpoint,
    apiKeyProvider = apiKeyProvider,
    modelName = modelName,
    client = client,
    customHeaders = customHeaders,
    providerType = ApiProviderType.DEEPSEEK,
    supportsVision = supportsVision,
    supportsAudio = supportsAudio,
    supportsVideo = supportsVideo,
    enableToolCall = enableToolCall,
    thinkingConfigurations = thinkingConfigurations,
    thinkingOptionId = thinkingOptionId
) {
    override val useResponsesApi: Boolean = true
    override val bufferResponsesOutputTextUntilItemDone: Boolean = true
    /** DeepSeek Files API 图片上传器（与 Chat Completions 协议线共用的抽取实现） */
    private val fileUploader =
        DeepseekFileUploader(responsesApiEndpoint, apiKeyProvider, client, customHeaders)

    /** 发送前把消息中的图片上传至 DeepSeek Files API 换取 file_id，失败回退 base64 */
    override suspend fun prepareMediaForRequest(context: Context, chatHistory: List<PromptTurn>) {
        fileUploader.prepareMedia(supportsVision, chatHistory, "DeepseekResponsesProvider")
    }

    /**
     * Responses 线同样先输出 DeepSeek 文件块，随后由
     * OpenAIResponsesPayloadAdapter.convertMessageContentForResponses 转换为
     * Responses 协议的 input_image + file_id 引用；未上传成功则回退 base64。
     */
    override fun buildImageContentPart(link: ImageLink): JSONObject {
        val fileId = fileUploader.fileIdFor(link.id)
        if (fileId != null) {
            return JSONObject().apply {
                put("type", "file")
                put("file_id", fileId)
            }
        }
        return super.buildImageContentPart(link)
    }

    override fun isResponsesCommentaryMessage(item: JSONObject): Boolean {
        return item.optString("phase", "").trim().equals("commentary", ignoreCase = true)
    }

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
            if (enableThinking) {
                chatHistory
            } else {
                ChatUtils.stripOpenAiResponsesReasoningMetaTurns(chatHistory)
            }
        val requestJson = JSONObject(
            createRequestBodyInternal(
                context = context,
                chatHistory = requestChatHistory,
                modelParameters = modelParameters,
                stream = stream,
                availableTools = availableTools,
                preserveThinkInHistory = preserveThinkInHistory
            )
        )
        ThinkingConfigurationApplier.apply(
            context = context,
            requestJson = requestJson,
            providerTypeId = ApiProviderType.DEEPSEEK.name,
            modelName = modelName,
            apiEndpoint = responsesApiEndpoint,
            thinkingConfigurations = thinkingConfigurations,
            enableThinking = enableThinking,
            optionId = thinkingOptionId,
        )
        return createJsonRequestBody(requestJson.toString())
    }

    override fun customizeFinalRequestObject(
        requestObject: JSONObject,
        messagesArray: JSONArray,
        toolsJson: String?
    ) {
        if (enableWebSearch) {
            appendWebSearchTool(requestObject)
        }
        super.customizeFinalRequestObject(requestObject, messagesArray, toolsJson)
    }

    override fun formatResponsesWebSearchDisplayXml(
        context: Context,
        item: JSONObject,
        response: JSONObject?
    ): String? {
        if (item.optString("type", "") != "web_search_call") {
            return null
        }

        val action = item.optJSONObject("action")
        val actionType = action?.optString("type", "")?.trim().orEmpty()
        val queries = collectResponsesWebSearchQueries(action, actionType)
        val status = item.optString("status", "").trim()
        val sources = mergeResponsesWebSearchSources(
            primary = collectResponsesWebSearchActionSources(action, actionType) +
                collectResponsesWebSearchSourceArray(action?.optJSONArray("sources")),
            additional = collectResponsesWebSearchSources(response)
        )
        if (queries.isEmpty() && sources.isEmpty()) {
            return null
        }

        return buildDeepseekSearchXml(
            actionType = actionType,
            queries = queries,
            status = status,
            sources = sources,
        )
    }

    private fun appendWebSearchTool(requestObject: JSONObject) {
        val tools = requestObject.optJSONArray("tools") ?: JSONArray().also {
            requestObject.put("tools", it)
        }
        for (index in 0 until tools.length()) {
            val tool = tools.optJSONObject(index) ?: continue
            if (tool.optString("type") == "web_search") {
                requestObject.put("tool_choice", "auto")
                return
            }
        }
        tools.put(JSONObject().put("type", "web_search"))
        requestObject.put("tool_choice", "auto")
    }

    private fun buildDeepseekSearchXml(
        actionType: String,
        queries: List<String>,
        status: String,
        sources: List<ResponsesWebSearchSource>
    ): String {
        return buildString {
            append("<search")
            appendXmlAttribute("provider", "deepseek")
            appendXmlAttribute("action", actionType)
            appendXmlAttribute("status", status)
            append(">")
            queries.forEach { query ->
                append("\n  <query>")
                append(escapeXmlText(query))
                append("</query>")
            }
            sources.forEach { source ->
                append("\n  <source")
                appendResponsesWebSearchSourceAttributes(this, source)
                append(" />")
            }
            append("\n</search>")
        }
    }

    private fun StringBuilder.appendXmlAttribute(name: String, value: String) {
        if (value.isEmpty()) {
            return
        }
        append(" ")
        append(name)
        append("=\"")
        append(escapeXmlAttribute(value))
        append("\"")
    }

    private fun escapeXmlAttribute(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun escapeXmlText(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

}
