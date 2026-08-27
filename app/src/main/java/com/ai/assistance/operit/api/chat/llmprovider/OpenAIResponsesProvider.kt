package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.util.Base64
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.ChatUtils
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenAIResponsesProvider(
    private val responsesApiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    private val responsesProviderType: ApiProviderType = ApiProviderType.OPENAI_RESPONSES,
    supportsVision: Boolean = false,
    supportsAudio: Boolean = false,
    supportsVideo: Boolean = false,
    enableToolCall: Boolean = false
) : OpenAIProvider(
    apiEndpoint = responsesApiEndpoint,
    apiKeyProvider = apiKeyProvider,
    modelName = modelName,
    client = client,
    customHeaders = customHeaders,
    providerType = responsesProviderType,
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
            if (enableThinking) {
                chatHistory
            } else {
                ChatUtils.stripOpenAiResponsesReasoningMetaTurns(chatHistory)
            }
        val baseRequestBodyJson = super.createRequestBodyInternal(
            context,
            requestChatHistory,
            modelParameters,
            stream,
            availableTools,
            preserveThinkInHistory
        )
        val jsonObject = JSONObject(baseRequestBodyJson)

        applyResponsesReasoningEffort(context, jsonObject, enableThinking)

        val logJson = JSONObject(jsonObject.toString())
        if (logJson.has("tools")) {
            val toolsArray = logJson.getJSONArray("tools")
            logJson.put("tools", "[${toolsArray.length()} tools omitted for brevity]")
        }
        val sanitizedLogJson = sanitizeImageDataForLogging(logJson)
        logLargeString(
            "OpenAIResponsesProvider",
            sanitizedLogJson.toString(4),
            "Final Responses request body: "
        )

        return createJsonRequestBody(jsonObject.toString())
    }

    override fun customizeFinalRequestObject(
        requestObject: JSONObject,
        messagesArray: JSONArray,
        toolsJson: String?
    ) {
        if (!shouldAttachPromptCacheKey()) {
            return
        }

        if (requestObject.has("prompt_cache_key")) {
            return
        }

        val promptCacheKey = buildPromptCacheKey(messagesArray, toolsJson) ?: return
        requestObject.put("prompt_cache_key", promptCacheKey)
        AppLogger.d("AIService", "Responses API自动附加prompt_cache_key: $promptCacheKey")
    }

    private fun applyResponsesReasoningEffort(
        context: Context,
        requestJson: JSONObject,
        enableThinking: Boolean
    ) {
        val reasoningObject = requestJson.optJSONObject("reasoning")

        if (!enableThinking && reasoningObject == null) {
            return
        }

        if (reasoningObject == null && requestJson.has("reasoning") && !requestJson.isNull("reasoning")) {
            AppLogger.w(
                "OpenAIResponsesProvider",
                "Skipping Responses reasoning adaptation because reasoning is not an object"
            )
            return
        }

        val finalReasoningObject = reasoningObject ?: JSONObject()
        val existingEffort =
            finalReasoningObject.optString("effort", "").trim().takeIf { it.isNotEmpty() }
        if (existingEffort == null) {
            val effort = when {
                enableThinking -> resolveResponsesReasoningEffort(context)
                else -> "none"
            }
            if (effort != null) {
                finalReasoningObject.put("effort", effort)
                AppLogger.d(
                    "OpenAIResponsesProvider",
                    "Responses reasoning.effort=$effort"
                )
            }
        } else {
            AppLogger.d(
                "OpenAIResponsesProvider",
                "Preserving caller-supplied Responses reasoning.effort=$existingEffort"
            )
        }

        val existingSummary =
            finalReasoningObject.optString("summary", "").trim().takeIf { it.isNotEmpty() }
        if (enableThinking && existingSummary == null) {
            finalReasoningObject.put("summary", "auto")
            AppLogger.d(
                "OpenAIResponsesProvider",
                "Responses reasoning summary enabled via reasoning.summary=auto"
            )
        }

        requestJson.put("reasoning", finalReasoningObject)
        if (finalReasoningObject.optString("effort", "").trim() != "none") {
            ensureResponsesReasoningEncryptedContentIncluded(requestJson)
        }
    }

    private fun ensureResponsesReasoningEncryptedContentIncluded(requestJson: JSONObject) {
        val includeArray = requestJson.optJSONArray("include") ?: JSONArray().also {
            requestJson.put("include", it)
        }
        for (i in 0 until includeArray.length()) {
            if (includeArray.optString(i, "") == "reasoning.encrypted_content") {
                return
            }
        }
        includeArray.put("reasoning.encrypted_content")
    }

    private fun resolveResponsesReasoningEffort(context: Context): String? {
        val qualityLevel = runCatching {
            runBlocking {
                ApiPreferences.getInstance(context).thinkingQualityLevelFlow.first()
            }
        }.getOrElse {
            AppLogger.w(
                "OpenAIResponsesProvider",
                "Failed to read thinking quality level; reasoning.effort not applied",
                it
            )
            return null
        }

        val effortLevels = listOf("low", "medium", "high", "xhigh", "max")
        val qualityIndex = qualityLevel.coerceIn(
            ApiPreferences.MIN_THINKING_QUALITY_LEVEL,
            ApiPreferences.MAX_THINKING_QUALITY_LEVEL
        ) - 1
        return effortLevels[qualityIndex]
    }

    private fun shouldAttachPromptCacheKey(): Boolean {
        return responsesProviderType == ApiProviderType.OPENAI_RESPONSES
    }

    private fun buildPromptCacheKey(
        messagesArray: JSONArray,
        toolsJson: String?
    ): String? {
        if (messagesArray.length() == 0 && toolsJson.isNullOrBlank()) {
            return null
        }

        val anchorParts = mutableListOf<String>()
        var assistantOrToolSeen = false

        for (i in 0 until messagesArray.length()) {
            val message = messagesArray.optJSONObject(i) ?: continue
            val role = message.optString("role", "")
            if (role.isEmpty()) {
                continue
            }

            if (role == "assistant" || role == "tool") {
                assistantOrToolSeen = true
                break
            }

            if (role == "system" || role == "developer") {
                anchorParts.add("$role:${message.opt("content")}")
                continue
            }

            if (role == "user") {
                anchorParts.add("$role:${message.opt("content")}")
                break
            }
        }

        if (anchorParts.isEmpty() && assistantOrToolSeen) {
            val firstMessage = messagesArray.optJSONObject(0)
            if (firstMessage != null) {
                anchorParts.add(
                    "${firstMessage.optString("role", "unknown")}:${firstMessage.opt("content")}"
                )
            }
        }

        val digestInput =
            buildString {
                append("operit:responses_prompt_cache:v1")
                append("|model=").append(modelName)
                append("|toolCall=").append(enableToolCall)
                if (!toolsJson.isNullOrBlank()) {
                    append("|tools=").append(toolsJson)
                }
                anchorParts.forEach { part ->
                    append("|anchor=").append(part)
                }
            }

        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest(digestInput.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        return "operit_resp_${digest.take(48)}"
    }
}

object OpenAIResponsesPayloadAdapter {

    data class UsageCounts(
        val totalInputTokens: Int,
        val actualInputTokens: Int,
        val cachedInputTokens: Int,
        val outputTokens: Int
    )

    data class ParsedResponseOutput(
        val textChunks: List<String>,
        val reasoningChunks: List<String>,
        val reasoningMetadataTags: List<String>,
        val reasoningObserved: Boolean,
        val toolCalls: JSONArray,
        val usage: UsageCounts?
    )

    fun mapParameterNameForResponses(apiName: String): String {
        return when (apiName) {
            "max_tokens" -> "max_output_tokens"
            else -> apiName
        }
    }

    fun parseUsageCounts(usage: JSONObject?): UsageCounts? {
        usage ?: return null

        // 评审 P1-5：显式全零 payload 也是“已观察到的 usage”——按字段存在判断，
        // 不能按 “>0” 过滤；P2-1：数值全程 Long 解析，只在旧 UI 计数边界饱和 Int。
        val hasInput = usage.has("prompt_tokens") || usage.has("input_tokens")
        val hasOutput = usage.has("completion_tokens") || usage.has("output_tokens")
        val cachedDetails =
            usage.optJSONObject("prompt_tokens_details")
                ?: usage.optJSONObject("input_tokens_details")
        val hasCached = usage.has("cached_tokens") || cachedDetails?.has("cached_tokens") == true
        if (!hasInput && !hasOutput && !hasCached) return null

        val totalInputTokens = usage.optLong("prompt_tokens", usage.optLong("input_tokens", -1))
            .saturateToInt()
        val outputTokens = usage.optLong("completion_tokens", usage.optLong("output_tokens", -1))
            .saturateToInt()
        val cachedInputTokens =
            (cachedDetails?.optLong("cached_tokens", -1)?.takeIf { it >= 0 }
                ?: usage.optLong("cached_tokens", -1))
                .coerceAtLeast(0)
                .saturateToInt()
        val actualInputTokens = (totalInputTokens - cachedInputTokens).coerceAtLeast(0)

        return UsageCounts(totalInputTokens, actualInputTokens, cachedInputTokens, outputTokens)
    }

    /** 旧 UI 计数边界（P2-1）：Long 饱和为 Int，绝不回绕为负。 */
    private fun Long.saturateToInt(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

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

    fun parseNonStreamingResponse(jsonResponse: JSONObject): ParsedResponseOutput {
        val textChunks = mutableListOf<String>()
        val reasoningChunks = mutableListOf<String>()
        val reasoningMetadataTags = mutableListOf<String>()
        val toolCalls = JSONArray()
        var reasoningObserved = false

        val output = jsonResponse.optJSONArray("output")
        if (output != null) {
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                when (item.optString("type", "")) {
                    "message" -> {
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
                        val summaryArray = item.optJSONArray("summary")
                        if (summaryArray != null) {
                            for (j in 0 until summaryArray.length()) {
                                val summaryPart = summaryArray.optJSONObject(j) ?: continue
                                val text = summaryPart.optString("text", "")
                                if (text.isNotEmpty()) {
                                    reasoningChunks.add(text)
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
                }
            }
        }

        return ParsedResponseOutput(
            textChunks = textChunks,
            reasoningChunks = reasoningChunks,
            reasoningMetadataTags = reasoningMetadataTags,
            reasoningObserved = reasoningObserved,
            toolCalls = toolCalls,
            usage = parseUsageCounts(jsonResponse.optJSONObject("usage"))
        )
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
                    val outputText = extractToolOutputText(message.opt("content"))
                    input.put(
                        JSONObject().apply {
                            put("type", "function_call_output")
                            put("call_id", callId)
                            put("output", outputText)
                        }
                    )
                    continue
                }
            }

            if (role == "assistant") {
                appendReasoningItemsFromAssistantMessage(message, input)
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

    private fun convertMessageContentForResponses(content: Any?): Any {
        return when (content) {
            null -> ""
            is String -> ChatMarkupRegex.removeOpenAiResponsesReasoningMeta(content)
            is JSONArray -> {
                val convertedParts = JSONArray()

                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i) ?: continue
                    when (part.optString("type", "")) {
                        "text", "output_text", "input_text" -> {
                            val text = ChatMarkupRegex.removeOpenAiResponsesReasoningMeta(part.optString("text", ""))
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

                        else -> {
                            val fallbackText = part.optString("text", "")
                            if (fallbackText.isNotEmpty()) {
                                convertedParts.put(
                                    JSONObject().apply {
                                        put("type", "input_text")
                                        put("text", fallbackText)
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

    fun createReasoningMetadataTag(item: JSONObject): String? {
        if (item.optString("type", "") != "reasoning") {
            return null
        }

        val id = item.optString("id", "").trim()
        val encryptedContent = item.optString("encrypted_content", "").trim()
        if (id.isEmpty() || encryptedContent.isEmpty()) {
            return null
        }

        val payload = JSONObject().apply {
            put("reasoning_id", id)
            put("encrypted_content", encryptedContent)
            // OpenAI requires summary on every replayed reasoning item; an empty array is valid.
            val summaryArray = item.optJSONArray("summary") ?: JSONArray()
            put("summary", JSONArray(summaryArray.toString()))
        }
        val payloadBase64 = Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return ChatMarkupRegex.openAiResponsesReasoningMetaTag(payloadBase64)
    }

    private fun appendReasoningItemsFromAssistantMessage(message: JSONObject, input: JSONArray) {
        val content = message.opt("content")
        val payloads = when (content) {
            is String -> ChatMarkupRegex.extractOpenAiResponsesReasoningPayloads(content)
            is JSONArray -> extractReasoningPayloadsFromContentArray(content)
            else -> emptyList()
        }

        payloads.forEach { payloadBase64 ->
            runCatching {
                val decodedPayload = String(Base64.decode(payloadBase64, Base64.DEFAULT), Charsets.UTF_8)
                appendReasoningItemFromMetadata(JSONObject(decodedPayload), input)
            }.onFailure { e ->
                AppLogger.w("OpenAIResponsesProvider", "OpenAI Responses reasoning metadata decode failed", e)
            }
        }
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

    private fun appendReasoningItemFromMetadata(metadata: JSONObject, input: JSONArray) {
        val reasoningId = metadata.optString("reasoning_id", "").trim()
        val encryptedContent = metadata.optString("encrypted_content", "").trim()
        if (reasoningId.isEmpty() || encryptedContent.isEmpty()) {
            return
        }

        input.put(
            JSONObject().apply {
                put("type", "reasoning")
                put("id", reasoningId)
                put("encrypted_content", encryptedContent)
                val summary = metadata.optJSONArray("summary") ?: JSONArray()
                put("summary", JSONArray(summary.toString()))
            }
        )
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
