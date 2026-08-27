package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.Stream
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Kimi K2.5 Provider (Moonshot API).
 * Mirrors DeepseekProvider behavior for reasoning_content handling when thinking is enabled.
 */
open class KimiProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    private val providerType: ApiProviderType = ApiProviderType.MOONSHOT,
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
        fun applyThinkingParams(jsonObject: JSONObject) {
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
        }

        if (!enableThinking) {
            val baseRequestBodyJson =
                super.createRequestBodyInternal(context, chatHistory, modelParameters, stream, availableTools, preserveThinkInHistory)
            val jsonObject = JSONObject(baseRequestBodyJson)
            if (stream) {
                jsonObject.put("stream_options", JSONObject().put("include_usage", true))
            }
            applyThinkingParams(jsonObject)
            return createJsonRequestBody(jsonObject.toString())
        }

        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", stream)
        if (stream) {
            jsonObject.put("stream_options", JSONObject().put("include_usage", true))
        }
        applyThinkingParams(jsonObject)

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
                            AppLogger.w("KimiProvider", "OBJECT参数解析失败: ${param.apiName}", e)
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

        val effectiveEnableToolCall = enableToolCall && availableTools != null && availableTools.isNotEmpty()

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
        val messagesArray =
            buildMessagesWithReasoning(
                context,
                providerReadyHistory,
                effectiveEnableToolCall
            )
        jsonObject.put("messages", messagesArray)

        val logJson = JSONObject(jsonObject.toString())
        if (logJson.has("tools")) {
            val toolsArray = logJson.getJSONArray("tools")
            logJson.put("tools", "[${toolsArray.length()} tools omitted for brevity]")
        }
        val sanitizedLogJson = sanitizeImageDataForLogging(logJson)
        logLargeString("KimiProvider", sanitizedLogJson.toString(4), "Final Kimi K2.5 request body: ")

        return createJsonRequestBody(jsonObject.toString())
    }

    private fun buildMessagesWithReasoning(
        context: Context,
        effectiveHistory: List<PromptTurn>,
        useToolCall: Boolean
    ): JSONArray {
        val messagesArray = JSONArray()

        var queuedAssistantToolText: String? = null
        var queuedAssistantReasoning: String? = null
        var queuedToolCalls = JSONArray()
        val queuedToolCallIds = mutableListOf<String>()
        val openToolCallIds = mutableListOf<String>()
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
                queuedToolCallIds.add(callId)
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

            openToolCallIds.addAll(queuedToolCallIds)
            queuedAssistantToolText = null
            queuedAssistantReasoning = null
            queuedToolCalls = JSONArray()
            queuedToolCallIds.clear()
        }

        fun flushOpenToolCallsAsCancelled(reason: String) {
            emitQueuedToolCallsIfNeeded()
            if (openToolCallIds.isEmpty()) return

            AppLogger.w(
                "KimiProvider",
                "发现未完成的tool_calls，按取消处理: count=${openToolCallIds.size}, reason=$reason"
            )
            for (toolCallId in openToolCallIds) {
                messagesArray.put(
                    JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", toolCallId)
                        put("content", "User cancelled")
                    }
                )
            }
            openToolCallIds.clear()
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
                                if (openToolCallIds.isNotEmpty()) {
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
                                if (openToolCallIds.isNotEmpty()) {
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
                            }
                        }

                        PromptTurnKind.TOOL_RESULT -> {
                            emitQueuedToolCallsIfNeeded()
                            val (textContent, toolResults) = parseXmlToolResults(originalContent)
                            val resultsList = toolResults ?: emptyList()

                            if (resultsList.isNotEmpty() && openToolCallIds.isNotEmpty()) {
                                val validCount = minOf(resultsList.size, openToolCallIds.size)
                                repeat(validCount) { index ->
                                    val (_, resultContent) = resultsList[index]
                                    messagesArray.put(
                                        JSONObject().apply {
                                            put("role", "tool")
                                            put("tool_call_id", openToolCallIds[index])
                                            put("content", resultContent)
                                        }
                                    )
                                }
                                repeat(validCount) {
                                    openToolCallIds.removeAt(0)
                                }

                                if (resultsList.size > validCount) {
                                    AppLogger.w(
                                        "KimiProvider",
                                        "发现多余的tool_result: ${resultsList.size} results vs ${validCount} pending tool_calls"
                                    )
                                }

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
                                val fallbackContent =
                                    when {
                                        textContent.isNotEmpty() -> textContent
                                        originalContent.isNotBlank() -> originalContent
                                        else -> "[Empty]"
                                    }
                                messagesArray.put(
                                    JSONObject().apply {
                                        put("role", "user")
                                        put("content", buildContentField(context, fallbackContent))
                                    }
                                )
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
                        PromptTurnKind.SUMMARY,
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
                        }

                        PromptTurnKind.TOOL_CALL -> {
                            messagesArray.put(
                                JSONObject().apply {
                                    put("role", "assistant")
                                    put("reasoning_content", "")
                                    put("content", buildContentField(context, originalContent.ifBlank { "[Empty]" }, role = "assistant"))
                                }
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
        return super.sendMessage(
            context,
            chatHistory,
            modelParameters,
            enableThinking,
            stream,
            availableTools,
            preserveThinkInHistory,
            onTokensUpdated,
            onUsageReported,
            onNonFatalError,
            enableRetry,
            recordTokenUsage,
            onUsageFinalized,
        )
    }
}
