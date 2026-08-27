package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.json.JSONObject

/**
 * OpenRouter provider.
 *
 * OpenRouter chat completions are largely OpenAI-compatible, but reasoning is controlled via
 * the unified `reasoning` object instead of the app's generic `enableThinking` toggle.
 *
 * We align the default reasoning request shape with RikkaHub's OpenRouter handling:
 * - thinking on: `reasoning: {}` or `reasoning.max_tokens = <budget>`
 * - thinking off: `reasoning: { enabled: false, max_tokens: 0 }`
 *
 * This provider keeps the shared OpenAI request/response handling while applying OpenRouter's
 * request-body conventions and default headers.
 */
open class OpenRouterProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    providerType: ApiProviderType = ApiProviderType.OPENROUTER,
    supportsVision: Boolean = false,
    supportsAudio: Boolean = false,
    supportsVideo: Boolean = false,
    enableToolCall: Boolean = false
) : OpenAIProvider(
    apiEndpoint = apiEndpoint,
    apiKeyProvider = apiKeyProvider,
    modelName = modelName,
    client = client,
    customHeaders = mergeOpenRouterHeaders(customHeaders),
    providerType = providerType,
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
    ): RequestBody {
        val baseRequestBodyJson = super.createRequestBodyInternal(
            context,
            chatHistory,
            modelParameters,
            stream,
            availableTools,
            preserveThinkInHistory
        )
        val jsonObject = JSONObject(baseRequestBodyJson)

        applyOpenRouterReasoning(
            context = context,
            requestJson = jsonObject,
            modelParameters = modelParameters,
            enableThinking = enableThinking
        )

        val logJson = JSONObject(jsonObject.toString())
        if (logJson.has("tools")) {
            val toolsArray = logJson.getJSONArray("tools")
            logJson.put("tools", "[${toolsArray.length()} tools omitted for brevity]")
        }
        val sanitizedLogJson = sanitizeImageDataForLogging(logJson)
        logLargeString(
            "OpenRouterProvider",
            sanitizedLogJson.toString(4),
            "Final OpenRouter request body: "
        )

        return createJsonRequestBody(jsonObject.toString())
    }

    private fun applyOpenRouterReasoning(
        context: Context,
        requestJson: JSONObject,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean
    ) {
        val reasoningObject = requestJson.optJSONObject("reasoning")
        val existingHasExplicitReasoningControl =
            reasoningObject?.let {
                it.has("enabled") || it.has("max_tokens") || it.has("effort")
            } == true

        when {
            reasoningObject == null && requestJson.has("reasoning") && !requestJson.isNull("reasoning") -> {
                AppLogger.w(
                    "OpenRouterProvider",
                    "Skipping OpenRouter reasoning adaptation because reasoning is not an object"
                )
            }

            existingHasExplicitReasoningControl -> {
                AppLogger.d(
                    "OpenRouterProvider",
                    "Preserving caller-supplied OpenRouter reasoning object"
                )
            }

            else -> {
                val finalReasoningObject = reasoningObject ?: JSONObject()
                if (enableThinking) {
                    val budgetTokens = resolveReasoningBudget(context, requestJson, modelParameters)
                    if (budgetTokens != null && budgetTokens > 0) {
                        finalReasoningObject.put("max_tokens", budgetTokens)
                    }
                    requestJson.put("reasoning", finalReasoningObject)
                    AppLogger.d(
                        "OpenRouterProvider",
                        if (budgetTokens != null) {
                            "OpenRouter thinking enabled via reasoning.max_tokens=$budgetTokens"
                        } else {
                            "OpenRouter thinking enabled via empty reasoning object"
                        }
                    )
                } else {
                    finalReasoningObject.put("enabled", false)
                    finalReasoningObject.put("max_tokens", 0)
                    requestJson.put("reasoning", finalReasoningObject)
                    AppLogger.d(
                        "OpenRouterProvider",
                        "OpenRouter thinking disabled via reasoning.enabled=false and max_tokens=0"
                    )
                }
            }
        }
    }

    private fun resolveReasoningBudget(
        context: Context,
        requestJson: JSONObject,
        modelParameters: List<ModelParameter<*>>
    ): Int? {
        val qualityLevel = runCatching {
            runBlocking {
                ApiPreferences.getInstance(context).thinkingQualityLevelFlow.first()
            }
        }.getOrElse {
            AppLogger.w(
                "OpenRouterProvider",
                "Failed to read thinking quality level, falling back to auto reasoning",
                it
            )
            return null
        }

        val reasoningBudgets = listOf(null, 1_024, 16_000, 32_000, 64_000)
        val qualityIndex = qualityLevel.coerceIn(
            ApiPreferences.MIN_THINKING_QUALITY_LEVEL,
            ApiPreferences.MAX_THINKING_QUALITY_LEVEL
        ) - 1
        val requestedBudget = reasoningBudgets[qualityIndex]

        if (requestedBudget == null) {
            return null
        }

        val modelMaxTokens =
            (modelParameters.firstOrNull { it.apiName == "max_tokens" && it.isEnabled }?.currentValue as? Number)
                ?.toInt()
                ?.takeIf { it > 1 }
                ?: requestJson.optInt("max_tokens", 0).takeIf { it > 1 }

        if (modelMaxTokens == null) {
            return requestedBudget
        }

        val cappedBudget = minOf(requestedBudget, modelMaxTokens - 1)
        return if (cappedBudget > 0) cappedBudget else null
    }

    companion object {
        private const val DEFAULT_HTTP_REFERER = "ai.assistance.operit"
        private const val DEFAULT_X_TITLE = "Assistance App"

        private fun mergeOpenRouterHeaders(customHeaders: Map<String, String>): Map<String, String> {
            val merged = linkedMapOf<String, String>()

            if (customHeaders.keys.none { it.equals("HTTP-Referer", ignoreCase = true) }) {
                merged["HTTP-Referer"] = DEFAULT_HTTP_REFERER
            }
            if (customHeaders.keys.none { it.equals("X-Title", ignoreCase = true) }) {
                merged["X-Title"] = DEFAULT_X_TITLE
            }

            merged.putAll(customHeaders)
            return merged
        }
    }
}
