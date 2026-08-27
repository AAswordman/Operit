package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit
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
    private val client: OkHttpClient,
    private val apiKeyProvider: ApiKeyProvider
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
        onUsageFinalized: (suspend (attempt: Int?) -> Unit)?
    ): Stream<String> {
        val qualityLevel =
            if (enableThinking) ApiPreferences.getInstance(context).thinkingQualityLevelFlow.first()
            else 1
        val capability = OpenCodeModelCatalog.resolve(client, baseEndpoint, modelName)
        val variant = OpenCodeReasoningMapper.select(capability, enableThinking, qualityLevel)
        val opencodeParameters = OpenCodeReasoningParameters.forVariant(
            protocol = protocol,
            modelName = modelName,
            capability = capability,
            variant = variant
        )

        return delegate.sendMessage(
            context = context,
            chatHistory = chatHistory,
            modelParameters = modelParameters + opencodeParameters,
            enableThinking = enableThinking && protocol == ApiProviderType.OPENAI_RESPONSES_GENERIC,
            stream = stream,
            availableTools = availableTools,
            preserveThinkInHistory = preserveThinkInHistory,
            onTokensUpdated = onTokensUpdated,
            onUsageReported = onUsageReported,
            onNonFatalError = onNonFatalError,
            enableRetry = enableRetry,
            recordTokenUsage = recordTokenUsage,
            onUsageFinalized = onUsageFinalized
        )
    }

    companion object {
        fun create(
            config: ModelConfigData,
            modelConfigManager: ModelConfigManager,
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
                    endpoint, apiKeyProvider, model, client, customHeaders,
                    supportsVision, supportsAudio, supportsVideo, enableToolCall
                )
            }
            return OpenCodeProvider(
                delegate = routed,
                baseEndpoint = config.apiEndpoint,
                modelName = model,
                protocol = provider,
                client = client,
                apiKeyProvider = apiKeyProvider
            )
        }
    }
}

internal object OpenCodeRouting {
    fun protocolFor(baseEndpoint: String, modelName: String): ApiProviderType {
        val model = modelName.lowercase()
        return when {
            model.startsWith("gpt-") || model.startsWith("grok-") || model.contains("codex") ->
                ApiProviderType.OPENAI_RESPONSES_GENERIC
            model.startsWith("claude-") || model.startsWith("qwen") || model.startsWith("minimax-") ->
                ApiProviderType.ANTHROPIC_GENERIC
            model.startsWith("gemini-") -> ApiProviderType.GEMINI_GENERIC
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
            preserveThinkInHistory = preserveThinkInHistory
        )
    )
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
    enableToolCall = enableToolCall
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
    enableToolCall = enableToolCall
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
/** The reasoning capabilities published by models.opencode.ai for one model. */
internal data class OpenCodeReasoningCapability(
    val reasoning: Boolean,
    val options: List<OpenCodeReasoningOption>,
    val outputLimit: Int
)

internal sealed class OpenCodeReasoningOption {
    data class Effort(val values: List<String?>) : OpenCodeReasoningOption()
    object Toggle : OpenCodeReasoningOption()

    data class BudgetTokens(val min: Int?, val max: Int?) : OpenCodeReasoningOption()
}

internal sealed class OpenCodeReasoningVariant {
    data class Effort(val value: String) : OpenCodeReasoningVariant()
    data class BudgetTokens(val value: Int) : OpenCodeReasoningVariant()
    data class Toggle(val enabled: Boolean) : OpenCodeReasoningVariant()
}

/**
 * Maps Operit's five global quality positions to the finite variants exposed by
 * OpenCode. The mapping deliberately happens after removing the optional `none`
 * value, so a model's declared capability remains the source of truth.
 */
internal object OpenCodeReasoningMapper {
    fun select(
        capability: OpenCodeReasoningCapability?,
        enableThinking: Boolean,
        qualityLevel: Int
    ): OpenCodeReasoningVariant? {
        if (capability == null || !capability.reasoning || capability.options.isEmpty()) {
            return null
        }

        // OpenCode gives effort options precedence over toggle and budget options.
        val effort = capability.options.filterIsInstance<OpenCodeReasoningOption.Effort>().firstOrNull()
        if (effort != null) {
            if (!enableThinking) {
                return if (effort.values.any { it == null || it.equals("none", ignoreCase = true) }) {
                    OpenCodeReasoningVariant.Effort("none")
                } else {
                    null
                }
            }
            val selectedEffort = effortForQuality(effort.values, qualityLevel) ?: return null
            return OpenCodeReasoningVariant.Effort(selectedEffort)
        }

        val toggle = capability.options.any { it is OpenCodeReasoningOption.Toggle }
        val budget = capability.options.filterIsInstance<OpenCodeReasoningOption.BudgetTokens>().firstOrNull()
        if (budget != null) {
            if (!enableThinking) {
                return if (toggle) OpenCodeReasoningVariant.Toggle(false) else null
            }
            val budgets = budgetVariants(budget, capability.outputLimit)
            if (budgets.isEmpty()) return null
            val selectedBudget = budgets[qualityIndex(budgets.size, qualityLevel)]
            return OpenCodeReasoningVariant.BudgetTokens(selectedBudget)
        }

        // A toggle has no intensity dimension, so every quality position selects
        // the same variant. Unsupported protocol-specific toggles are left empty
        // by toParameters rather than being replaced with an invented effort value.
        return if (toggle) OpenCodeReasoningVariant.Toggle(enableThinking) else null
    }

    internal fun effortForQuality(values: List<String?>, qualityLevel: Int): String? {
        val activeValues = values
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .filterNot { it.equals("none", ignoreCase = true) }
        if (activeValues.isEmpty()) return null
        return activeValues[qualityIndex(activeValues.size, qualityLevel)]
    }

    internal fun qualityIndex(optionCount: Int, qualityLevel: Int): Int {
        require(optionCount > 0) { "optionCount must be positive" }
        val quality = qualityLevel.coerceIn(1, 5)
        return when (optionCount) {
            1 -> 0
            2 -> if (quality <= 2) 0 else 1
            3 -> when {
                quality <= 2 -> 0
                quality <= 4 -> 1
                else -> 2
            }
            4 -> when (quality) {
                1 -> 0
                2, 3 -> 1
                4 -> 2
                else -> 3
            }
            else -> (((quality - 1) * (optionCount - 1)) + 2) / 4
        }.coerceIn(0, optionCount - 1)
    }

    internal fun budgetVariants(
        option: OpenCodeReasoningOption.BudgetTokens,
        outputLimit: Int
    ): List<Int> {
        // This mirrors OpenCode's high/max budget variant construction.
        val maximum = minOf(
            option.max ?: (outputLimit - 1),
            outputLimit - 1,
            1_048_575
        )
        if (maximum <= 0) return emptyList()
        val high = minOf(
            maxOf(option.min ?: 0, (maximum + 1) / 2),
            maximum
        )
        return listOf(high, maximum).distinct().filter { it > 0 }
    }
}

internal object OpenCodeReasoningParameters {
    fun forVariant(
        protocol: ApiProviderType,
        modelName: String,
        capability: OpenCodeReasoningCapability?,
        variant: OpenCodeReasoningVariant?
    ): List<ModelParameter<*>> {
        val result = mutableListOf<ModelParameter<*>>()
        if (variant == null) return result

        when (variant) {
            is OpenCodeReasoningVariant.Effort -> {
                when {
                    protocol.isOpenAiResponses() -> {
                        val reasoning = JSONObject().put("effort", variant.value)
                        if (!variant.value.equals("none", ignoreCase = true)) {
                            reasoning.put("summary", "auto")
                        }
                        result += objectParameter(
                            apiName = "reasoning",
                            value = reasoning
                        )
                        if (!variant.value.equals("none", ignoreCase = true)) {
                            result += objectParameter(
                                apiName = "include",
                                value = JSONArray().put("reasoning.encrypted_content")
                            )
                        }
                    }
                    protocol.isOpenAiChat() -> {
                        result += stringParameter("reasoning_effort", variant.value)
                    }
                    protocol.isAnthropic() -> {
                        anthropicEffortParameters(result, modelName, capability, variant.value)
                    }
                    protocol.isGemini() -> {
                        result += objectParameter(
                            apiName = "thinkingConfig",
                            value = JSONObject()
                                .put("includeThoughts", true)
                                .put("thinkingLevel", variant.value),
                            category = ParameterCategory.GENERATION
                        )
                    }
                }
            }
            is OpenCodeReasoningVariant.BudgetTokens -> {
                when {
                    protocol.isAnthropic() -> {
                        result += objectParameter(
                            apiName = "thinking",
                            value = JSONObject()
                                .put("type", "enabled")
                                .put("budget_tokens", variant.value)
                        )
                    }
                    protocol.isGemini() -> {
                        result += objectParameter(
                            apiName = "thinkingConfig",
                            value = JSONObject()
                                .put("includeThoughts", true)
                                .put("thinkingBudget", variant.value),
                            category = ParameterCategory.GENERATION
                        )
                    }
                }
            }
            is OpenCodeReasoningVariant.Toggle -> {
                // OpenCode's native fallback currently defines a wire-level toggle
                // for MiniMax's Anthropic-compatible route. Other SDKs have no
                // generic toggle lowerer, so they remain at the provider default.
                if (protocol.isAnthropic() && modelName.contains("minimax", ignoreCase = true)) {
                    result += objectParameter(
                        apiName = "thinking",
                        value = JSONObject().put(
                            "type",
                            if (variant.enabled) "adaptive" else "disabled"
                        )
                    )
                }
            }
        }
        return result
    }

    private fun anthropicEffortParameters(
        result: MutableList<ModelParameter<*>>,
        modelName: String,
        capability: OpenCodeReasoningCapability?,
        effort: String
    ) {
        val thinking = anthropicThinkingForEffort(modelName, capability?.outputLimit ?: 0)
        if (thinking != null) {
            result += objectParameter("thinking", thinking)
        }
        result += objectParameter(
            apiName = "output_config",
            value = JSONObject().put("effort", effort)
        )
    }

    private fun anthropicThinkingForEffort(modelName: String, outputLimit: Int): JSONObject? {
        val id = modelName.lowercase()
        if (id.contains("opus-4-5") || id.contains("opus-4.5")) {
            val budget = minOf(16_000, (outputLimit / 2 - 1).coerceAtLeast(0))
            return if (budget > 0) {
                JSONObject().put("type", "enabled").put("budget_tokens", budget)
            } else {
                null
            }
        }

        if (id.contains("kimi") || id.contains("k2p")) {
            return JSONObject().put("type", "adaptive").put("display", "summarized")
        }

        val match = CLAUDE_VERSION_REGEX.find(id) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: 0
        return when {
            major > 4 || (major == 4 && minor >= 7) ->
                JSONObject().put("type", "adaptive").put("display", "summarized")
            major == 4 && minor == 6 -> JSONObject().put("type", "adaptive")
            else -> null
        }
    }

    private fun stringParameter(apiName: String, value: String): ModelParameter<String> = ModelParameter(
        id = "opencode-$apiName",
        name = apiName,
        apiName = apiName,
        defaultValue = value,
        currentValue = value,
        isEnabled = true,
        valueType = ParameterValueType.STRING,
        category = ParameterCategory.OTHER,
        isCustom = false
    )

    private fun objectParameter(
        apiName: String,
        value: Any,
        category: ParameterCategory = ParameterCategory.OTHER
    ): ModelParameter<String> {
        val serialized = value.toString()
        return ModelParameter(
            id = "opencode-$apiName",
            name = apiName,
            apiName = apiName,
            defaultValue = serialized,
            currentValue = serialized,
            isEnabled = true,
            valueType = ParameterValueType.OBJECT,
            category = category,
            isCustom = false
        )
    }

    private fun ApiProviderType.isOpenAiResponses(): Boolean =
        this == ApiProviderType.OPENAI_RESPONSES || this == ApiProviderType.OPENAI_RESPONSES_GENERIC

    private fun ApiProviderType.isOpenAiChat(): Boolean =
        this == ApiProviderType.OPENAI || this == ApiProviderType.OPENAI_GENERIC || this == ApiProviderType.OPENAI_LOCAL

    private fun ApiProviderType.isAnthropic(): Boolean =
        this == ApiProviderType.ANTHROPIC || this == ApiProviderType.ANTHROPIC_GENERIC

    private fun ApiProviderType.isGemini(): Boolean =
        this == ApiProviderType.GOOGLE || this == ApiProviderType.GEMINI_GENERIC

    private val CLAUDE_VERSION_REGEX =
        Regex("claude-(?:[a-z]+-)?(\\d+)(?:[.-](\\d{1,2}))?(?:[.@-]|$)")
}

/** Fetches and caches the same model capability catalog used by OpenCode. */
internal object OpenCodeModelCatalog {
    private const val CATALOG_URL = "https://models.opencode.ai/api.json"
    private const val CACHE_TTL_MS = 5 * 60 * 1000L
    private const val TAG = "OpenCodeModelCatalog"

    private data class Snapshot(
        val fetchedAt: Long,
        val providers: Map<String, Map<String, OpenCodeReasoningCapability>>
    )

    @Volatile private var snapshot: Snapshot? = null
    private val refreshMutex = Mutex()

    suspend fun resolve(
        client: OkHttpClient,
        baseEndpoint: String,
        modelName: String
    ): OpenCodeReasoningCapability? {
        val providerId = OpenCodeRouting.catalogProviderId(baseEndpoint)
        val now = System.currentTimeMillis()
        snapshot?.takeIf { now - it.fetchedAt < CACHE_TTL_MS }?.let {
            return it.providers[providerId]?.get(modelName)
        }

        return refreshMutex.withLock {
            val current = snapshot
            val refreshedNow = System.currentTimeMillis()
            if (current != null && refreshedNow - current.fetchedAt < CACHE_TTL_MS) {
                return@withLock current.providers[providerId]?.get(modelName)
            }

            val fresh = try {
                fetch(client)
            } catch (error: Exception) {
                AppLogger.w(TAG, "刷新OpenCode模型能力目录失败", error)
                null
            }
            if (fresh != null) {
                snapshot = fresh
                fresh.providers[providerId]?.get(modelName)
            } else {
                current?.providers?.get(providerId)?.get(modelName)
            }
        }
    }

    private suspend fun fetch(client: OkHttpClient): Snapshot = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(CATALOG_URL)
            .header("Accept", "application/json")
            .build()
        val catalogClient = client.newBuilder()
            .callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        val body = catalogClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("OpenCode model catalog HTTP ${response.code}")
            }
            response.body?.string() ?: throw IOException("OpenCode model catalog response is empty")
        }
        val root = JSONObject(body)
        val providers = listOf("opencode", "opencode-go").associateWith { providerId ->
            parseProvider(root.optJSONObject(providerId))
        }
        Snapshot(System.currentTimeMillis(), providers)
    }

    private fun parseProvider(provider: JSONObject?): Map<String, OpenCodeReasoningCapability> {
        if (provider == null) return emptyMap()
        val models = provider.optJSONObject("models") ?: return emptyMap()
        val result = mutableMapOf<String, OpenCodeReasoningCapability>()
        val keys = models.keys()
        while (keys.hasNext()) {
            val modelId = keys.next()
            val model = models.optJSONObject(modelId) ?: continue
            result[modelId] = OpenCodeReasoningCapability(
                reasoning = model.optBoolean("reasoning", false),
                options = parseOptions(model),
                outputLimit = model.optJSONObject("limit")?.optInt("output", 0) ?: 0
            )
        }
        return result
    }

    private fun parseOptions(model: JSONObject): List<OpenCodeReasoningOption> {
        if (!model.has("reasoning_options") || model.isNull("reasoning_options")) {
            return emptyList()
        }
        val array = model.optJSONArray("reasoning_options") ?: return emptyList()
        val result = mutableListOf<OpenCodeReasoningOption>()
        for (index in 0 until array.length()) {
            val option = array.optJSONObject(index) ?: continue
            when (option.optString("type")) {
                "effort" -> {
                    val values = option.optJSONArray("values") ?: JSONArray()
                    val parsed = buildList {
                        for (valueIndex in 0 until values.length()) {
                            add(if (values.isNull(valueIndex)) null else values.optString(valueIndex))
                        }
                    }
                    result += OpenCodeReasoningOption.Effort(parsed)
                }
                "toggle" -> result += OpenCodeReasoningOption.Toggle
                "budget_tokens" -> result += OpenCodeReasoningOption.BudgetTokens(
                    min = optionalInt(option, "min"),
                    max = optionalInt(option, "max")
                )
            }
        }
        return result
    }

    private fun optionalInt(objectValue: JSONObject, key: String): Int? =
        (objectValue.opt(key) as? Number)?.toInt()
}
