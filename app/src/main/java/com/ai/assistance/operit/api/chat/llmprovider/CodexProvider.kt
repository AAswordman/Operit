package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.BuildConfig
import com.ai.assistance.operit.data.api.CodexAuthManager
import com.ai.assistance.operit.data.api.CodexOAuthProtocol
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

private class CodexAccessTokenProvider(
    private val authManager: CodexAuthManager,
) : ApiKeyProvider {
    override suspend fun getApiKey(): String = authManager.getValidAccessToken()

    override suspend fun getCandidateKeyCount(): Int =
        if (authManager.authState.value == null) 0 else 1
}

class CodexProvider(
    private val authManager: CodexAuthManager,
    modelName: String,
    private val httpClient: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    supportsVision: Boolean = false,
    supportsAudio: Boolean = false,
    supportsVideo: Boolean = false,
    supportsFiles: Boolean = false,
    enableToolCall: Boolean = false,
    private val enableWebSearch: Boolean = false,
    thinkingConfigurations: String = "",
    thinkingOptionId: String = "",
) : OpenAIResponsesProvider(
    responsesApiEndpoint = CodexOAuthProtocol.CODEX_RESPONSES_ENDPOINT,
    apiKeyProvider = CodexAccessTokenProvider(authManager),
    modelName = modelName,
    client = httpClient,
    customHeaders = customHeaders,
    responsesProviderType = ApiProviderType.OPENAI_CODEX,
    supportsVision = supportsVision,
    supportsAudio = supportsAudio,
    supportsVideo = supportsVideo,
    supportsFiles = supportsFiles,
    enableToolCall = enableToolCall,
    thinkingConfigurations = thinkingConfigurations,
    thinkingOptionId = thinkingOptionId,
) {
    override fun applyAuthenticationHeaders(builder: Request.Builder, currentApiKey: String) {
        super.applyAuthenticationHeaders(builder, currentApiKey)
        val accountId = authManager.currentAccountId()
            ?: throw IllegalStateException("Codex account ID is unavailable")
        builder.header("ChatGPT-Account-ID", accountId)
        builder.header("originator", "operit")
        builder.header("User-Agent", "Operit/${BuildConfig.VERSION_NAME}")
        builder.header("session-id", UUID.randomUUID().toString())
        authManager.currentResidency()?.let { residency ->
            builder.header("x-openai-internal-codex-residency", residency)
        }
    }

    override fun customizeFinalRequestObject(
        requestObject: JSONObject,
        messagesArray: JSONArray,
        toolsJson: String?,
    ) {
        super.customizeFinalRequestObject(requestObject, messagesArray, toolsJson)
        if (enableWebSearch) {
            appendWebSearchTool(requestObject)
        }
        CodexModelVariant.applyRequestParameters(requestObject, modelName)
        requestObject.put("store", false)
        requestObject.put("parallel_tool_calls", false)
        val include = requestObject.optJSONArray("include") ?: JSONArray().also {
            requestObject.put("include", it)
        }
        if (!containsString(include, "reasoning.encrypted_content")) {
            include.put("reasoning.encrypted_content")
        }
        if (enableWebSearch && !containsString(include, "web_search_call.action.sources")) {
            include.put("web_search_call.action.sources")
        }
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

        return buildCodexSearchXml(
            actionType = actionType,
            queries = queries,
            status = status,
            sources = sources,
        )
    }

    override suspend fun getModelsList(_context: Context): Result<List<ModelOption>> {
        return CodexModelListFetcher.getModelsList(httpClient)
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

    private fun containsString(array: JSONArray, value: String): Boolean {
        for (index in 0 until array.length()) {
            if (array.optString(index) == value) return true
        }
        return false
    }

    private fun buildCodexSearchXml(
        actionType: String,
        queries: List<String>,
        status: String,
        sources: List<ResponsesWebSearchSource>
    ): String {
        return buildString {
            append("<search")
            appendXmlAttribute("provider", "codex")
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

object CodexModelPolicy {
    private val explicitlyAllowed = setOf(
        "gpt-5.5",
        "gpt-5.3-codex-spark",
        "gpt-5.4",
        "gpt-5.4-mini",
    )
    private val explicitlyDisallowed = setOf("gpt-5.5-pro")
    private val versionPattern = Regex("^gpt-(\\d+\\.\\d+)")

    fun allows(modelId: String, reasoningMode: String? = null): Boolean {
        val normalized = modelId.trim().lowercase()
        if (reasoningMode?.trim()?.equals("pro", ignoreCase = true) == true) return false
        if (normalized in explicitlyDisallowed) return false
        if (normalized in explicitlyAllowed) return true
        if (normalized == "gpt-5.6") return false
        val version = versionPattern.find(normalized)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: return false
        return version > 5.4
    }
}

internal object CodexModelVariant {
    private const val FAST_SUFFIX = "-fast"

    fun applyRequestParameters(requestObject: JSONObject, modelName: String) {
        val apiModelId = apiModelId(modelName)
        if (apiModelId == modelName) return

        requestObject.put("model", apiModelId)
        requestObject.put("service_tier", "priority")
    }

    fun apiModelId(modelName: String): String = modelName.removeSuffix(FAST_SUFFIX)
}

object CodexModelListFetcher {
    suspend fun getModelsList(
        httpClient: OkHttpClient = SharedHttpClient.instance,
    ): Result<List<ModelOption>> {
        return try {
            val request = Request.Builder()
                .url(CodexOAuthProtocol.MODEL_CATALOG_ENDPOINT)
                .get()
                .header("User-Agent", "Operit/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .build()
            val responseBody = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("OpenCode model catalog request failed with HTTP ${response.code}")
                    }
                    body
                }
            }
            Result.success(parseModels(responseBody))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    internal fun parseModels(responseBody: String): List<ModelOption> {
        val root = JSONObject(responseBody)
        val openAiProvider = root.optJSONObject("openai")
            ?: throw IllegalArgumentException("OpenCode model catalog has no openai provider")
        val models = openAiProvider.optJSONObject("models")
            ?: throw IllegalArgumentException("OpenCode model catalog has no openai models")
        val result = mutableListOf<ModelOption>()
        val modelKeys = models.keys()
        while (modelKeys.hasNext()) {
            val modelKey = modelKeys.next()
            val model = models.optJSONObject(modelKey) ?: continue
            val id = model.optString("id").trim()
            val name = model.optString("name").trim()
            if (id.isBlank() || name.isBlank() || !CodexModelPolicy.allows(id)) {
                continue
            }
            result += ModelOption(id = id, name = name)

            val modes = model.optJSONObject("experimental")?.optJSONObject("modes") ?: continue
            val modeKeys = modes.keys()
            while (modeKeys.hasNext()) {
                val mode = modeKeys.next().trim()
                if (mode.isBlank()) continue
                val modeConfig = modes.optJSONObject(mode) ?: continue
                val reasoningMode = modeConfig
                    .optJSONObject("provider")
                    ?.optJSONObject("body")
                    ?.optJSONObject("reasoning")
                    ?.optString("mode")
                    ?.trim()
                if (!CodexModelPolicy.allows(id, reasoningMode)) continue

                result += ModelOption(
                    id = "$id-$mode",
                    name = "$name ${mode.replaceFirstChar { it.uppercaseChar() }}",
                )
            }
        }
        return result
    }
}
