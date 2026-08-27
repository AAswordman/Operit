package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.util.ChatMarkupRegex
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

class MistralProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    providerType: ApiProviderType = ApiProviderType.MISTRAL,
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

    override fun parseXmlToolCalls(content: String): Pair<String, JSONArray?> {
        val matches = ChatMarkupRegex.toolCallPattern.findAll(content)

        if (!matches.any()) {
            return Pair(content, null)
        }

        val toolCalls = JSONArray()
        var textContent = content
        var callIndex = 0

        matches.forEach { match ->
            val toolName = match.groupValues[2]
            val toolBody = match.groupValues[3]
            val params = JSONObject()

            ChatMarkupRegex.toolParamPattern.findAll(toolBody).forEach { paramMatch ->
                val paramName = paramMatch.groupValues[1]
                val paramValue = unescapeXml(paramMatch.groupValues[2].trim())
                params.put(paramName, paramValue)
            }

            val callId = generateMistralToolCallId(toolName, params, callIndex)

            toolCalls.put(JSONObject().apply {
                put("id", callId)
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", toolName)
                    put("arguments", params.toString())
                })
            })

            callIndex++
            textContent = textContent.replace(match.value, "")
        }

        return Pair(textContent.trim(), toolCalls)
    }

    private fun generateMistralToolCallId(toolName: String, params: JSONObject, index: Int): String {
        val raw = "$toolName:${params.toString()}:$index"
        val hash = raw.hashCode()
        val positive = if (hash == Int.MIN_VALUE) 0 else kotlin.math.abs(hash)
        var base = positive.toString(36)
        base = base.filter { it.isLetterOrDigit() }.lowercase()
        if (base.isEmpty()) base = "0"
        val padded = base.padStart(9, '0')
        return if (padded.length > 9) padded.takeLast(9) else padded
    }

    private fun unescapeXml(text: String): String {
        return text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}
