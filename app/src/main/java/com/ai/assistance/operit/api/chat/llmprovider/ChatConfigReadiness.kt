package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.model.ApiKeyFormatValidator
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getValidModelIndex
import java.net.URI

enum class ChatConfigReadinessIssue {
    PROVIDER_MISSING,
    PROVIDER_UNAVAILABLE,
    ENDPOINT_INVALID,
    MODEL_MISSING,
    API_KEY_MISSING,
    API_KEY_INVALID
}

data class ChatConfigReadinessResult(val issue: ChatConfigReadinessIssue? = null) {
    val isReady: Boolean
        get() = issue == null
}

object ChatConfigReadiness {
    private val credentialOptionalProviders =
        setOf(
            ApiProviderType.OPENAI_RESPONSES_GENERIC,
            ApiProviderType.OPENAI_GENERIC,
            ApiProviderType.ANTHROPIC_GENERIC,
            ApiProviderType.GEMINI_GENERIC,
            ApiProviderType.OTHER
        )

    fun evaluate(
        config: ModelConfigData,
        modelIndex: Int,
        registeredPluginProviderIds: Set<String>
    ): ChatConfigReadinessResult {
        val providerTypeId = config.apiProviderTypeId.trim()
        if (providerTypeId.isEmpty()) {
            return ChatConfigReadinessResult(ChatConfigReadinessIssue.PROVIDER_MISSING)
        }

        val normalizedPluginIds = registeredPluginProviderIds.mapTo(mutableSetOf()) {
            it.trim().lowercase()
        }
        if (providerTypeId.lowercase() in normalizedPluginIds) {
            return ChatConfigReadinessResult()
        }

        val providerType = ApiProviderType.fromProviderTypeId(providerTypeId)
            ?: return ChatConfigReadinessResult(ChatConfigReadinessIssue.PROVIDER_UNAVAILABLE)
        val validModelIndex = getValidModelIndex(config.modelName, modelIndex)
        if (getModelByIndex(config.modelName, validModelIndex).isBlank()) {
            return ChatConfigReadinessResult(ChatConfigReadinessIssue.MODEL_MISSING)
        }

        if (providerType == ApiProviderType.MNN || providerType == ApiProviderType.LLAMA_CPP) {
            return ChatConfigReadinessResult()
        }

        val completedEndpoint = EndpointCompleter.completeEndpoint(config.apiEndpoint, providerType)
        if (!isHttpEndpoint(completedEndpoint)) {
            return ChatConfigReadinessResult(ChatConfigReadinessIssue.ENDPOINT_INVALID)
        }

        val hasConfiguredKey =
            config.apiKey.isNotBlank() ||
                config.apiKeyPool.any { key -> key.isEnabled && key.key.isNotBlank() }
        val keyIsRequired =
            providerType !in credentialOptionalProviders &&
                ApiProviderConfigs.requiresApiKey(providerType, config.apiEndpoint)
        val hasUsableKey = ApiKeyFormatValidator.hasUsableKey(config)

        if (config.useMultipleApiKeys && !hasUsableKey) {
            val issue =
                if (hasConfiguredKey) {
                    ChatConfigReadinessIssue.API_KEY_INVALID
                } else {
                    ChatConfigReadinessIssue.API_KEY_MISSING
                }
            return ChatConfigReadinessResult(issue)
        }
        if (keyIsRequired && !hasConfiguredKey) {
            return ChatConfigReadinessResult(ChatConfigReadinessIssue.API_KEY_MISSING)
        }
        if ((keyIsRequired || hasConfiguredKey) && !hasUsableKey) {
            return ChatConfigReadinessResult(ChatConfigReadinessIssue.API_KEY_INVALID)
        }
        return ChatConfigReadinessResult()
    }

    private fun isHttpEndpoint(endpoint: String): Boolean {
        return try {
            val uri = URI(endpoint.trim())
            uri.host != null &&
                (uri.scheme.equals("http", ignoreCase = true) ||
                    uri.scheme.equals("https", ignoreCase = true))
        } catch (_: Exception) {
            false
        }
    }
}
