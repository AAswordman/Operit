package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import okhttp3.OkHttpClient

class NousPortalProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    providerType: ApiProviderType = ApiProviderType.NOUS_PORTAL,
    supportsVision: Boolean = false,
    supportsAudio: Boolean = false,
    supportsVideo: Boolean = false,
    enableToolCall: Boolean = false
) : OpenRouterProvider(
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
)
