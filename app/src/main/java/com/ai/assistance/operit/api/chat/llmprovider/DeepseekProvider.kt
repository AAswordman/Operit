package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.Stream
import java.util.Base64
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
    thinkingOptionId: String = "",
    private val enableWebSearch: Boolean = false
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
                        thinkingOptionId = config.thinkingOptionId,
                        enableWebSearch = config.enableDeepSeekWebSearch
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

        // DeepSeek 服务端联网工具：声明后由服务端执行搜索与读链接，不依赖 enableToolCall；
        // tool_choice 不能强制指定这两个工具，保持 auto 让模型自行决定
        if (enableWebSearch) {
            val tools =
                jsonObject.optJSONArray("tools")
                    ?: JSONArray().also { jsonObject.put("tools", it) }
            tools.put(JSONObject().put("type", "deepseek_web_search"))
            tools.put(JSONObject().put("type", "deepseek_read_url"))
            jsonObject.put("tool_choice", "auto")
            toolsJson = tools.toString()
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
        var queuedServerToolCalls: JSONArray? = null
        var queuedToolCalls = JSONArray()
        val queuedOpenToolCalls = mutableListOf<StructuredToolCallBridge.OpenToolCall>()
        val openToolCalls = mutableListOf<StructuredToolCallBridge.OpenToolCall>()
        var nextToolCallOrdinal = 0

        /**
         * 解码 assistant 历史中持久化的 server_tool_calls meta 载荷，返回 (载荷, 剔除 meta 后的可见内容)。
         * DeepSeek 要求下一轮请求把 server_tool_calls 原样回传，否则读过的网页不再进入上下文。
         */
        fun resolveServerToolCallsForReplay(content: String): Pair<JSONArray?, String> {
            val payloads = ChatMarkupRegex.extractDeepseekServerToolCallsPayloads(content)
            if (payloads.isEmpty()) {
                return null to content
            }
            val merged = JSONArray()
            payloads.forEach { payloadBase64 ->
                runCatching {
                    val decoded =
                        JSONArray(
                            String(Base64.getDecoder().decode(payloadBase64), Charsets.UTF_8)
                        )
                    for (i in 0 until decoded.length()) {
                        val call = decoded.optJSONObject(i) ?: continue
                        merged.put(call)
                    }
                }.onFailure { e ->
                    AppLogger.w("DeepseekProvider", "server_tool_calls 元数据解码失败", e)
                }
            }
            val serverToolCalls = if (merged.length() > 0) merged else null
            return serverToolCalls to ChatMarkupRegex.removeDeepseekServerToolCallsMeta(content)
        }

        fun mergeQueuedServerToolCalls(serverToolCalls: JSONArray?) {
            if (serverToolCalls == null || serverToolCalls.length() == 0) return
            val merged = queuedServerToolCalls ?: JSONArray().also { queuedServerToolCalls = it }
            for (i in 0 until serverToolCalls.length()) {
                val call = serverToolCalls.optJSONObject(i) ?: continue
                merged.put(call)
            }
        }

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

        fun queueToolCalls(
            textContent: String,
            toolCalls: JSONArray,
            reasoningContent: String = "",
            serverToolCalls: JSONArray? = null
        ) {
            appendQueuedAssistantToolText(textContent)
            appendQueuedAssistantReasoning(reasoningContent)
            mergeQueuedServerToolCalls(serverToolCalls)
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
                    queuedServerToolCalls?.let { put("server_tool_calls", it) }
                }
            )

            openToolCalls.addAll(queuedOpenToolCalls)
            queuedAssistantToolText = null
            queuedAssistantReasoning = null
            queuedServerToolCalls = null
            queuedToolCalls = JSONArray()
            queuedOpenToolCalls.clear()
        }

        fun flushOpenToolCallsAsUnmatched(reason: String) {
            emitQueuedToolCallsIfNeeded()
            if (openToolCalls.isEmpty()) return

            AppLogger.w(
                "DeepseekProvider",
                "发现未匹配的tool_calls，按工具结果未匹配处理: count=${openToolCalls.size}, reason=$reason"
            )
            for (openToolCall in openToolCalls) {
                messagesArray.put(
                    JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", openToolCall.id)
                        put(
                            "content",
                            StructuredToolCallBridge.unmatchedToolResultContent(
                                reason,
                                openToolCall.matchingName
                            )
                        )
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
                            flushOpenToolCallsAsUnmatched("system_boundary")
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "system")
                                    put("content", buildContentField(context, originalContent, role = "system"))
                                }
                            )
                        }

                        PromptTurnKind.USER,
                        PromptTurnKind.SUMMARY -> {
                            flushOpenToolCallsAsUnmatched("user_boundary")
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "user")
                                    put("content", buildContentField(context, originalContent))
                                }
                            )
                        }

                        PromptTurnKind.ASSISTANT -> {
                            val (content, reasoningContent) = ChatUtils.extractThinkingContent(originalContent)
                            val (serverToolCalls, visibleContent) = resolveServerToolCallsForReplay(content)
                            val (textContent, parsedToolCalls) = parseXmlToolCalls(visibleContent)
                            val toolCalls =
                                if (parsedToolCalls != null) {
                                    wrapPackageToolCallsWithProxy(parsedToolCalls)
                                } else {
                                    null
                                }

                            if (toolCalls != null && toolCalls.length() > 0) {
                                if (openToolCalls.isNotEmpty()) {
                                    flushOpenToolCallsAsUnmatched("assistant_tool_call_before_result")
                                }
                                queueToolCalls(textContent, toolCalls, reasoningContent, serverToolCalls)
                            } else {
                                flushOpenToolCallsAsUnmatched("assistant_boundary")
                                messagesArray.put(
                                    JSONObject().apply {
                                        put("role", "assistant")
                                        put("reasoning_content", reasoningContent)
                                        put("content", buildContentField(context, visibleContent.ifBlank { "[Empty]" }, role = "assistant"))
                                        serverToolCalls?.let { put("server_tool_calls", it) }
                                    }
                                )
                                appendReadableImageMessageIfNeeded(
                                    messagesArray,
                                    visibleContent,
                                    "assistant message"
                                )
                            }
                        }

                        PromptTurnKind.TOOL_CALL -> {
                            val (serverToolCalls, visibleContent) = resolveServerToolCallsForReplay(originalContent)
                            val (textContent, parsedToolCalls) = parseXmlToolCalls(visibleContent)
                            val toolCalls =
                                if (parsedToolCalls != null) {
                                    wrapPackageToolCallsWithProxy(parsedToolCalls)
                                } else {
                                    null
                                }

                            if (toolCalls != null && toolCalls.length() > 0) {
                                if (openToolCalls.isNotEmpty()) {
                                    flushOpenToolCallsAsUnmatched("typed_tool_call_before_result")
                                }
                                queueToolCalls(textContent, toolCalls, serverToolCalls = serverToolCalls)
                            } else {
                                flushOpenToolCallsAsUnmatched("typed_tool_call_without_payload")
                                messagesArray.put(
                                    JSONObject().apply {
                                        put("role", "assistant")
                                        put("reasoning_content", "")
                                        put("content", buildContentField(context, visibleContent.ifBlank { "[Empty]" }, role = "assistant"))
                                        serverToolCalls?.let { put("server_tool_calls", it) }
                                    }
                                )
                                appendReadableImageMessageIfNeeded(
                                    messagesArray,
                                    visibleContent,
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

                                flushOpenToolCallsAsUnmatched("tool_result_partial_batch")

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
                                flushOpenToolCallsAsUnmatched("tool_result_without_structured_match")
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
                            val (serverToolCalls, visibleContent) = resolveServerToolCallsForReplay(content)
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "assistant")
                                    put("reasoning_content", reasoningContent)
                                    put("content", buildContentField(context, visibleContent.ifBlank { "[Empty]" }, role = "assistant"))
                                    serverToolCalls?.let { put("server_tool_calls", it) }
                                }
                            )
                            appendReadableImageMessageIfNeeded(
                                messagesArray,
                                visibleContent,
                                "assistant message"
                            )
                        }

                        PromptTurnKind.TOOL_CALL -> {
                            val (serverToolCalls, visibleContent) = resolveServerToolCallsForReplay(originalContent)
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "assistant")
                                    put("reasoning_content", "")
                                    put("content", buildContentField(context, visibleContent.ifBlank { "[Empty]" }, role = "assistant"))
                                    serverToolCalls?.let { put("server_tool_calls", it) }
                                }
                            )
                            appendReadableImageMessageIfNeeded(
                                messagesArray,
                                visibleContent,
                                "assistant tool-call message"
                            )
                        }
                    }
                }
            }
        }

        flushOpenToolCallsAsUnmatched("history_end")
        return messagesArray
    }

    override suspend fun onChatCompletionMessageExtras(
        extras: JSONObject,
        emitter: StreamEmitter
    ) {
        if (!enableWebSearch) {
            return
        }

        val serverToolCalls = extras.optJSONArray("server_tool_calls")
        emitDeepseekSearchDisplay(serverToolCalls, extras.optJSONArray("annotations"), emitter)

        // server_tool_calls 须在下一轮原样回传；以隐藏 meta 标签持久化进会话历史，
        // 重建请求时由 resolveServerToolCallsForReplay 还原为 assistant 消息字段
        if (serverToolCalls != null && serverToolCalls.length() > 0) {
            val payloadBase64 =
                Base64.getEncoder()
                    .encodeToString(serverToolCalls.toString().toByteArray(Charsets.UTF_8))
            emitter.emitMetadataTag(ChatMarkupRegex.deepseekServerToolCallsMetaTag(payloadBase64))
        }
    }

    /**
     * 将 server_tool_calls 与 annotations 解析为与 Responses 路径同构的 <search> 展示块。
     * 官方协议：完整 server_tool_calls 与 annotations 只随最后一个 delta chunk / 非流式 message 发送一次。
     */
    private suspend fun emitDeepseekSearchDisplay(
        serverToolCalls: JSONArray?,
        annotations: JSONArray?,
        emitter: StreamEmitter
    ) {
        if (serverToolCalls == null && annotations == null) {
            return
        }

        val queries = linkedSetOf<String>()
        val sources = linkedMapOf<String, ResponsesWebSearchSource>()

        if (serverToolCalls != null) {
            for (stepIndex in 0 until serverToolCalls.length()) {
                val calls =
                    serverToolCalls.optJSONObject(stepIndex)?.optJSONArray("calls") ?: continue
                for (callIndex in 0 until calls.length()) {
                    val call = calls.optJSONObject(callIndex) ?: continue
                    val action = call.optJSONObject("action")
                    val actionType = action?.optString("type", "")?.trim().orEmpty()
                    if (actionType == "search") {
                        val actionQueries = action?.optJSONArray("queries")
                        if (actionQueries != null) {
                            for (queryIndex in 0 until actionQueries.length()) {
                                val query = actionQueries.optString(queryIndex, "").trim()
                                if (query.isNotEmpty()) {
                                    queries.add(query)
                                }
                            }
                        }
                    }

                    val callSources = call.optJSONArray("sources")
                    if (callSources != null) {
                        for (sourceIndex in 0 until callSources.length()) {
                            val source = callSources.optJSONObject(sourceIndex) ?: continue
                            val url = source.optString("url", "").trim()
                            if (url.isEmpty() || sources.containsKey(url)) continue
                            val metadata = linkedMapOf<String, String>()
                            val siteName = source.optString("site_name", "").trim()
                            if (siteName.isNotEmpty()) {
                                metadata["site_name"] = siteName
                            }
                            sources[url] =
                                ResponsesWebSearchSource(
                                    type = source.optString("type", "").trim().ifEmpty { "url" },
                                    title = source.optString("title", "").trim(),
                                    url = url,
                                    metadata = metadata
                                )
                        }
                    }

                    val page = call.optJSONObject("page")
                    val pageUrl = page?.optString("url", "")?.trim().orEmpty()
                    if (pageUrl.isNotEmpty() && !sources.containsKey(pageUrl)) {
                        sources[pageUrl] =
                            ResponsesWebSearchSource(
                                type = "read_link",
                                title = page.optString("title", "").trim(),
                                url = pageUrl
                            )
                    }
                }
            }
        }

        if (annotations != null) {
            for (index in 0 until annotations.length()) {
                val annotation = annotations.optJSONObject(index) ?: continue
                val citation = annotation.optJSONObject("url_citation") ?: annotation
                val url = citation.optString("url", "").trim()
                if (url.isEmpty() || sources.containsKey(url)) continue
                sources[url] =
                    ResponsesWebSearchSource(
                        type = "url_citation",
                        title = citation.optString("title", "").trim(),
                        url = url
                    )
            }
        }

        if (queries.isEmpty() && sources.isEmpty()) {
            return
        }

        val statusSet = linkedSetOf<String>()
        if (serverToolCalls != null) {
            for (stepIndex in 0 until serverToolCalls.length()) {
                val calls =
                    serverToolCalls.optJSONObject(stepIndex)?.optJSONArray("calls") ?: continue
                for (callIndex in 0 until calls.length()) {
                    val status =
                        calls.optJSONObject(callIndex)?.optString("status", "")?.trim().orEmpty()
                    if (status.isNotEmpty()) {
                        statusSet.add(status)
                    }
                }
            }
        }

        val displayXml =
            buildDeepseekSearchDisplayXml(
                actionType =
                    if (queries.isNotEmpty()) "search" else sources.values.firstOrNull()?.type.orEmpty(),
                queries = queries.toList(),
                status = statusSet.joinToString(","),
                sourcesXmlAttributes =
                    sources.values.map { source ->
                        buildString { appendResponsesWebSearchSourceAttributes(this, source) }
                    }
            )

        emitter.emitTag("\n")
        emitter.emitTag(displayXml)
        emitter.emitTag("\n\n")
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

/**
 * DeepSeek 联网搜索展示块的统一装配；sourcesXmlAttributes 由调用方渲染
 * （两条协议路径都经 appendResponsesWebSearchSourceAttributes 产出 type/title/url 属性串）。
 */
private fun buildDeepseekSearchDisplayXml(
    actionType: String,
    queries: List<String>,
    status: String,
    sourcesXmlAttributes: List<String>
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
        sourcesXmlAttributes.forEach { attributes ->
            append("\n  <source")
            append(attributes)
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

object DeepseekResponsesPayloadAdapter {
    fun toResponsesRequest(chatStyleRequest: JSONObject): JSONObject {
        val converted = JSONObject(chatStyleRequest.toString())

        if (converted.has("max_tokens") && !converted.has("max_output_tokens")) {
            converted.put("max_output_tokens", converted.get("max_tokens"))
            converted.remove("max_tokens")
        }

        if (converted.has("response_format")) {
            val responseFormat = converted.get("response_format")
            val textConfig = converted.optJSONObject("text") ?: JSONObject()
            textConfig.put("format", responseFormat)
            converted.put("text", textConfig)
            converted.remove("response_format")
        }

        moveReasoningEffortToReasoningObject(converted)

        if (converted.has("tools")) {
            val originalTools = converted.optJSONArray("tools")
            if (originalTools != null) {
                converted.put("tools", convertToolsToResponsesFormat(originalTools))
            }
        }

        if (converted.has("messages")) {
            val messages = converted.optJSONArray("messages")
            if (messages != null) {
                converted.put("input", convertMessagesToResponsesInput(messages))
                converted.remove("messages")
            }
        }

        return converted
    }

    fun parseNonStreamingResponse(jsonResponse: JSONObject): OpenAIResponsesPayloadAdapter.ParsedResponseOutput {
        val textChunks = mutableListOf<String>()
        val reasoningChunks = mutableListOf<String>()
        val reasoningMetadataTags = mutableListOf<String>()
        val outputItemMetadataTags = mutableListOf<String>()
        val toolCalls = JSONArray()
        var reasoningObserved = false

        val output = jsonResponse.optJSONArray("output")
        if (output != null) {
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                when (item.optString("type", "")) {
                    "message" -> {
                        val isCommentaryMessage =
                            item.optString("phase", "").trim().equals("commentary", ignoreCase = true)
                        if (isCommentaryMessage) {
                            // Commentary is continuation state for the next request, not a second think block.
                            createCommentaryMetadataTag(item)?.let { metadataTag ->
                                outputItemMetadataTags.add(metadataTag)
                                reasoningObserved = true
                            }
                            continue
                        }
                        val contentArray = item.optJSONArray("content")
                        if (contentArray != null) {
                            for (j in 0 until contentArray.length()) {
                                val part = contentArray.optJSONObject(j) ?: continue
                                when (part.optString("type", "")) {
                                    "output_text", "text" -> {
                                        val text = part.optString("text", "")
                                        if (text.isNotEmpty()) {
                                            textChunks.add(text)
                                        }
                                    }

                                    "reasoning_text" -> {
                                        val text = part.optString("text", "")
                                        if (text.isNotEmpty()) {
                                            reasoningObserved = true
                                            reasoningChunks.add(text)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "reasoning" -> {
                        reasoningObserved = true
                        createReasoningMetadataTag(item)?.let { reasoningMetadataTags.add(it) }
                        val contentArray = item.optJSONArray("content")
                        if (contentArray != null) {
                            for (j in 0 until contentArray.length()) {
                                val part = contentArray.optJSONObject(j) ?: continue
                                if (part.optString("type", "") == "reasoning_text") {
                                    val text = part.optString("text", "")
                                    if (text.isNotEmpty()) {
                                        reasoningChunks.add(text)
                                    }
                                }
                            }
                        }
                    }

                    "function_call" -> {
                        val toolCall = convertFunctionCallItemToChatToolCall(item)
                        if (toolCall != null) {
                            toolCalls.put(toolCall)
                        }
                    }

                    "web_search_call" -> {
                        OpenAIResponsesPayloadAdapter.createOutputItemMetadataTag(item)
                            ?.let { outputItemMetadataTags.add(it) }
                    }
                }
            }
        }

        return OpenAIResponsesPayloadAdapter.ParsedResponseOutput(
            textChunks = textChunks,
            reasoningChunks = reasoningChunks,
            reasoningMetadataTags = reasoningMetadataTags,
            outputItemMetadataTags = outputItemMetadataTags,
            reasoningObserved = reasoningObserved,
            toolCalls = toolCalls,
            usage = OpenAIResponsesPayloadAdapter.parseUsageCounts(jsonResponse.optJSONObject("usage"))
        )
    }

    fun createReasoningMetadataTag(item: JSONObject): String? {
        if (item.optString("type", "") != "reasoning") {
            return null
        }

        val id = item.optString("id", "").trim()
        val content = item.optJSONArray("content") ?: return null
        val hasReasoningText = containsReasoningText(content)
        if (id.isEmpty() || !hasReasoningText) {
            return null
        }

        val payload = JSONObject().apply {
            put("reasoning_id", id)
            put("content", JSONArray(content.toString()))
        }
        val payloadBase64 = Base64.getEncoder().encodeToString(payload.toString().toByteArray(Charsets.UTF_8))
        return ChatMarkupRegex.openAiResponsesReasoningMetaTag(payloadBase64)
    }

    fun createCommentaryMetadataTag(item: JSONObject): String? {
        if (item.optString("type", "") != "message" ||
            !item.optString("phase", "").trim().equals("commentary", ignoreCase = true)
        ) {
            return null
        }

        val content = item.optJSONArray("content") ?: return null
        if (!containsCommentaryText(content)) {
            return null
        }

        // DeepSeek emits some thinking as a commentary message instead of a reasoning item.
        // Persist the original item so the continuation can restore it as reasoning_text.
        val payload = JSONObject().apply {
            put("type", "message")
            put("role", "assistant")
            val id = item.optString("id", "").trim()
            if (id.isNotEmpty()) {
                put("id", id)
            }
            put("content", JSONArray(content.toString()))
        }
        val payloadBase64 = Base64.getEncoder().encodeToString(payload.toString().toByteArray(Charsets.UTF_8))
        return ChatMarkupRegex.openAiResponsesOutputItemMetaTag(payloadBase64)
    }

    fun createStreamingCommentaryMetadataTag(item: JSONObject, commentaryText: String): String? {
        if (item.optString("type", "") != "message" ||
            !item.optString("phase", "").trim().equals("commentary", ignoreCase = true) ||
            commentaryText.isEmpty()
        ) {
            return null
        }

        val payload = JSONObject().apply {
            put("type", "message")
            put("role", "assistant")
            val id = item.optString("id", "").trim()
            if (id.isNotEmpty()) {
                put("id", id)
            }
            put(
                "content",
                JSONArray().put(
                    JSONObject()
                        .put("type", "output_text")
                        .put("text", commentaryText)
                )
            )
        }
        val payloadBase64 = Base64.getEncoder().encodeToString(payload.toString().toByteArray(Charsets.UTF_8))
        return ChatMarkupRegex.openAiResponsesOutputItemMetaTag(payloadBase64)
    }

    private fun moveReasoningEffortToReasoningObject(requestJson: JSONObject) {
        if (!requestJson.has("reasoning_effort") || requestJson.isNull("reasoning_effort")) {
            return
        }

        val effort = requestJson.optString("reasoning_effort", "").trim()
        requestJson.remove("reasoning_effort")
        if (effort.isEmpty()) {
            return
        }

        val reasoningObject = requestJson.optJSONObject("reasoning") ?: JSONObject()
        val existingEffort = reasoningObject.optString("effort", "").trim()
        if (existingEffort.isEmpty()) {
            reasoningObject.put("effort", effort)
        }
        requestJson.put("reasoning", reasoningObject)
    }

    private fun convertToolsToResponsesFormat(chatTools: JSONArray): JSONArray {
        val converted = JSONArray()

        for (i in 0 until chatTools.length()) {
            val tool = chatTools.optJSONObject(i) ?: continue
            val toolType = tool.optString("type", "")
            if (toolType != "function") {
                converted.put(tool)
                continue
            }

            val function = tool.optJSONObject("function")
            if (function == null) {
                converted.put(tool)
                continue
            }

            val convertedFunction = JSONObject().apply {
                put("type", "function")
                put("name", function.optString("name", ""))
                if (function.has("description")) {
                    put("description", function.get("description"))
                }
                if (function.has("parameters")) {
                    put("parameters", function.get("parameters"))
                }
                if (function.has("strict")) {
                    put("strict", function.get("strict"))
                }
            }

            converted.put(convertedFunction)
        }

        return converted
    }

    private fun convertMessagesToResponsesInput(messages: JSONArray): JSONArray {
        val input = JSONArray()

        for (i in 0 until messages.length()) {
            val message = messages.optJSONObject(i) ?: continue
            val role = message.optString("role", "")
            if (role.isEmpty()) continue

            if (role == "tool") {
                val callId = message.optString("tool_call_id", "")
                if (callId.isNotEmpty()) {
                    val outputContent = extractToolOutputContent(message.opt("content"))
                    input.put(
                        JSONObject().apply {
                            put("type", "function_call_output")
                            put("call_id", callId)
                            put("output", outputContent)
                        }
                    )
                    continue
                }
            }

            if (role == "assistant") {
                val reasoningItemReplayed = appendReasoningItemsFromAssistantMessage(message, input)
                val commentaryMessageReplayed = appendOutputItemsFromAssistantMessage(message, input)
                val convertedContent = convertMessageContentForResponses(
                    content = message.opt("content"),
                    removeThinkingContent = reasoningItemReplayed || commentaryMessageReplayed
                )
                val hasContent =
                    when (convertedContent) {
                        is String -> convertedContent.isNotBlank()
                        is JSONArray -> convertedContent.length() > 0
                        else -> false
                    }

                if (hasContent) {
                    input.put(
                        JSONObject().apply {
                            put("type", "message")
                            put("role", "assistant")
                            put("content", convertedContent)
                        }
                    )
                }

                val toolCalls = message.optJSONArray("tool_calls")
                if (toolCalls != null && toolCalls.length() > 0) {
                    for (j in 0 until toolCalls.length()) {
                        val call = toolCalls.optJSONObject(j) ?: continue
                        val function = call.optJSONObject("function") ?: continue
                        val name = function.optString("name", "")
                        if (name.isEmpty()) continue

                        val callItem = JSONObject().apply {
                            put("type", "function_call")
                            put("name", name)
                            put("arguments", function.optString("arguments", "{}"))
                        }

                        val callId = call.optString("id", "")
                        if (callId.isNotEmpty()) {
                            callItem.put("call_id", callId)
                        }

                        input.put(callItem)
                    }
                }
                continue
            }

            val convertedContent = convertMessageContentForResponses(message.opt("content"))
            val hasContent =
                when (convertedContent) {
                    is String -> convertedContent.isNotBlank()
                    is JSONArray -> convertedContent.length() > 0
                    else -> false
                }

            if (hasContent) {
                val mappedRole =
                    when (role) {
                        "system" -> "developer"
                        else -> role
                    }

                input.put(
                    JSONObject().apply {
                        put("type", "message")
                        put("role", mappedRole)
                        put("content", convertedContent)
                    }
                )
            }
        }

        return input
    }

    private fun convertMessageContentForResponses(
        content: Any?,
        removeThinkingContent: Boolean = false
    ): Any {
        return when (content) {
            null -> ""
            is String -> sanitizeResponsesMessageText(content, removeThinkingContent)
            is JSONArray -> {
                val convertedParts = JSONArray()

                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i) ?: continue
                    when (part.optString("type", "")) {
                        "text", "output_text", "input_text" -> {
                            val text = sanitizeResponsesMessageText(
                                part.optString("text", ""),
                                removeThinkingContent
                            )
                            if (text.isNotEmpty()) {
                                convertedParts.put(
                                    JSONObject().apply {
                                        put("type", "input_text")
                                        put("text", text)
                                    }
                                )
                            }
                        }

                        "image_url", "input_image" -> {
                            val imageUrl =
                                if (part.optString("type", "") == "input_image") {
                                    part.optString("image_url", "")
                                } else {
                                    part.optJSONObject("image_url")?.optString("url", "")
                                        ?: part.optString("image_url", "")
                                }
                            if (imageUrl.isNotEmpty()) {
                                convertedParts.put(
                                    JSONObject().apply {
                                        put("type", "input_image")
                                        put("image_url", imageUrl)
                                    }
                                )
                            }
                        }

                        "input_audio" -> {
                            val audioObject = part.optJSONObject("input_audio")
                            if (audioObject != null) {
                                convertedParts.put(
                                    JSONObject().apply {
                                        put("type", "input_audio")
                                        put("input_audio", audioObject)
                                    }
                                )
                            }
                        }

                        "input_file" -> {
                            val fileData = part.optString("file_data", "")
                            val fileName = part.optString("filename", "")
                            if (fileData.isNotEmpty() && fileName.isNotEmpty()) {
                                convertedParts.put(
                                    JSONObject().apply {
                                        put("type", "input_file")
                                        put("filename", fileName)
                                        put("file_data", fileData)
                                    }
                                )
                            }
                        }

                        else -> {
                            val text = sanitizeResponsesMessageText(
                                part.optString("text", ""),
                                removeThinkingContent
                            )
                            if (text.isNotEmpty()) {
                                convertedParts.put(
                                    JSONObject().apply {
                                        put("type", "input_text")
                                        put("text", text)
                                    }
                                )
                            }
                        }
                    }
                }

                convertedParts
            }

            else -> content.toString()
        }
    }

    private fun sanitizeResponsesMessageText(
        content: String,
        removeThinkingContent: Boolean
    ): String {
        val visibleContent =
            if (removeThinkingContent) ChatUtils.removeThinkingContent(content) else content
        return ChatUtils.stripOpenAiResponsesProtocolMarkup(visibleContent)
    }

    private fun extractToolOutputContent(content: Any?): Any {
        return when (content) {
            is JSONArray -> {
                val convertedContent = convertMessageContentForResponses(content)
                if (convertedContent is JSONArray && convertedContent.length() > 0) {
                    convertedContent
                } else {
                    extractToolOutputText(content)
                }
            }

            is String -> ChatUtils.stripOpenAiResponsesProtocolMarkup(content)
            else -> extractToolOutputText(content)
        }
    }

    private fun extractToolOutputText(content: Any?): String {
        return when (content) {
            null -> ""
            is String -> content
            is JSONArray -> {
                val parts = mutableListOf<String>()
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i) ?: continue
                    val type = part.optString("type", "")
                    if (type == "text" || type == "output_text" || type == "input_text") {
                        val text = part.optString("text", "")
                        if (text.isNotEmpty()) {
                            parts.add(text)
                        }
                    }
                }
                if (parts.isNotEmpty()) parts.joinToString("\n") else content.toString()
            }

            else -> content.toString()
        }
    }

    private fun appendReasoningItemsFromAssistantMessage(message: JSONObject, input: JSONArray): Boolean {
        val content = message.opt("content")
        val payloads = when (content) {
            is String -> ChatMarkupRegex.extractOpenAiResponsesReasoningPayloads(content)
            is JSONArray -> extractReasoningPayloadsFromContentArray(content)
            else -> emptyList()
        }
        var itemReplayed = false

        payloads.forEach { payloadBase64 ->
            runCatching {
                val decodedPayload = String(Base64.getDecoder().decode(payloadBase64), Charsets.UTF_8)
                if (appendReasoningItemFromMetadata(JSONObject(decodedPayload), input)) {
                    itemReplayed = true
                }
            }.onFailure { e ->
                AppLogger.w("DeepseekProvider", "DeepSeek Responses reasoning metadata decode failed", e)
            }
        }

        return itemReplayed
    }

    private fun appendOutputItemsFromAssistantMessage(message: JSONObject, input: JSONArray): Boolean {
        val content = message.opt("content")
        val payloads = when (content) {
            is String -> ChatMarkupRegex.extractOpenAiResponsesOutputItemPayloads(content)
            is JSONArray -> extractOutputItemPayloadsFromContentArray(content)
            else -> emptyList()
        }
        var commentaryMessageReplayed = false

        payloads.forEach { payloadBase64 ->
            runCatching {
                val decodedPayload = String(Base64.getDecoder().decode(payloadBase64), Charsets.UTF_8)
                if (appendOutputItemFromMetadata(JSONObject(decodedPayload), input)) {
                    commentaryMessageReplayed = true
                }
            }.onFailure { e ->
                AppLogger.w("DeepseekProvider", "DeepSeek Responses output item metadata decode failed", e)
            }
        }

        return commentaryMessageReplayed
    }

    private fun extractReasoningPayloadsFromContentArray(content: JSONArray): List<String> {
        val payloads = mutableListOf<String>()
        for (i in 0 until content.length()) {
            val part = content.optJSONObject(i) ?: continue
            val text = part.optString("text", "")
            if (text.isNotEmpty()) {
                payloads.addAll(ChatMarkupRegex.extractOpenAiResponsesReasoningPayloads(text))
            }
        }
        return payloads
    }

    private fun extractOutputItemPayloadsFromContentArray(content: JSONArray): List<String> {
        val payloads = mutableListOf<String>()
        for (i in 0 until content.length()) {
            val part = content.optJSONObject(i) ?: continue
            val text = part.optString("text", "")
            if (text.isNotEmpty()) {
                payloads.addAll(ChatMarkupRegex.extractOpenAiResponsesOutputItemPayloads(text))
            }
        }
        return payloads
    }

    private fun appendReasoningItemFromMetadata(metadata: JSONObject, input: JSONArray): Boolean {
        val reasoningId = metadata.optString("reasoning_id", "").trim()
        val content = metadata.optJSONArray("content") ?: return false
        if (reasoningId.isEmpty() || !containsReasoningText(content)) {
            return false
        }

        input.put(
            JSONObject().apply {
                put("type", "reasoning")
                put("id", reasoningId)
                put("content", JSONArray(content.toString()))
            }
        )
        return true
    }

    private fun appendOutputItemFromMetadata(metadata: JSONObject, input: JSONArray): Boolean {
        when (metadata.optString("type", "")) {
            "web_search_call" -> {
                if (metadata.optString("id", "").trim().isEmpty()) {
                    return false
                }
                input.put(JSONObject(metadata.toString()))
            }

            "message" -> {
                if (metadata.optString("role", "") != "assistant") {
                    return false
                }
                val content = metadata.optJSONArray("content") ?: return false
                val reasoningContent = convertCommentaryContentToReasoningContent(content)
                if (reasoningContent.length() == 0) {
                    return false
                }
                // Thinking-mode function calls require reasoning_text. Commentary is that thought
                // in a message envelope; replaying it as output_text makes DeepSeek return 400.
                val reasoningItem = JSONObject().apply {
                    put("type", "reasoning")
                    val id = metadata.optString("id", "").trim()
                    if (id.isNotEmpty()) {
                        put("id", id)
                    }
                    put("content", reasoningContent)
                }
                input.put(reasoningItem)
                return true
            }

            else -> return false
        }

        return false
    }

    private fun containsReasoningText(content: JSONArray): Boolean {
        return (0 until content.length()).any { index ->
            val part = content.optJSONObject(index) ?: return@any false
            part.optString("type", "") == "reasoning_text" &&
                part.optString("text", "").isNotEmpty()
        }
    }

    private fun containsCommentaryText(content: JSONArray): Boolean {
        return convertCommentaryContentToReasoningContent(content).length() > 0
    }

    private fun convertCommentaryContentToReasoningContent(content: JSONArray): JSONArray {
        val reasoningContent = JSONArray()
        for (index in 0 until content.length()) {
            val part = content.optJSONObject(index) ?: continue
            if (part.optString("type", "") !in setOf("output_text", "text", "reasoning_text")) {
                continue
            }
            val text = part.optString("text", "")
            if (text.isEmpty()) {
                continue
            }
            reasoningContent.put(
                JSONObject()
                    .put("type", "reasoning_text")
                    .put("text", text)
            )
        }
        return reasoningContent
    }

    private fun convertFunctionCallItemToChatToolCall(item: JSONObject): JSONObject? {
        val name = item.optString("name", "")
        if (name.isEmpty()) return null

        val arguments = item.optString("arguments", "{}").ifBlank { "{}" }
        val callId = item.optString("call_id", item.optString("id", ""))

        return JSONObject().apply {
            if (callId.isNotEmpty()) {
                put("id", callId)
            }
            put("type", "function")
            put(
                "function",
                JSONObject().apply {
                    put("name", name)
                    put("arguments", arguments)
                }
            )
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

    override fun isResponsesCommentaryMessage(item: JSONObject): Boolean {
        return item.optString("phase", "").trim().equals("commentary", ignoreCase = true)
    }

    override fun convertChatRequestToResponsesRequest(requestObject: JSONObject): JSONObject {
        return DeepseekResponsesPayloadAdapter.toResponsesRequest(requestObject)
    }

    override fun createResponsesReasoningMetadataTag(item: JSONObject): String? {
        return DeepseekResponsesPayloadAdapter.createReasoningMetadataTag(item)
    }

    override fun createResponsesMessageMetadataTag(item: JSONObject, bufferedText: String): String? {
        return DeepseekResponsesPayloadAdapter.createStreamingCommentaryMetadataTag(item, bufferedText)
    }

    override fun parseResponsesNonStreamingResponse(
        jsonResponse: JSONObject
    ): OpenAIResponsesPayloadAdapter.ParsedResponseOutput {
        return DeepseekResponsesPayloadAdapter.parseNonStreamingResponse(jsonResponse)
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
            if (tool.optString("type") == "deepseek_web_search") {
                requestObject.put("tool_choice", "auto")
                return
            }
        }
        tools.put(JSONObject().put("type", "deepseek_web_search"))
        tools.put(JSONObject().put("type", "deepseek_read_url"))
        requestObject.put("tool_choice", "auto")
    }

    private fun buildDeepseekSearchXml(
        actionType: String,
        queries: List<String>,
        status: String,
        sources: List<ResponsesWebSearchSource>
    ): String {
        return buildDeepseekSearchDisplayXml(
            actionType = actionType,
            queries = queries,
            status = status,
            sourcesXmlAttributes =
                sources.map { source ->
                    buildString { appendResponsesWebSearchSourceAttributes(this, source) }
                }
        )
    }

}
