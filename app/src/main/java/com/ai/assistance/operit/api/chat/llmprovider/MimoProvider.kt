package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Xiaomi MiMo provider.
 * Reuses KimiProvider's reasoning_content-compatible behavior so thinking content
 * can round-trip without duplicating the request/response adaptation logic.
 */
class MimoProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    providerType: ApiProviderType = ApiProviderType.MIMO,
    supportsVision: Boolean = false,
    supportsAudio: Boolean = false,
    supportsVideo: Boolean = false,
    enableToolCall: Boolean = false
) : KimiProvider(
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
    override fun applyAuthenticationHeaders(
        builder: Request.Builder,
        currentApiKey: String
    ) {
        super.applyAuthenticationHeaders(builder, currentApiKey)
        if (currentApiKey.isNotEmpty()) {
            builder.addHeader("api-key", currentApiKey)
        }
    }
}
