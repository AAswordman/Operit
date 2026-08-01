package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ApiProviderType
import okhttp3.OkHttpClient

/**
 * OrcaRouter provider.
 *
 * OrcaRouter is an OpenAI-compatible multi-model gateway. Its chat completions endpoint also
 * accepts OpenRouter's unified `reasoning` object and answers with `reasoning_content`, so the
 * app's generic thinking toggle keeps working without any extra handling here.
 *
 * Reuses [OpenRouterProvider] for that request/response shape, mirroring how [NousPortalProvider]
 * shares the same compatible wiring.
 */
class OrcaRouterProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    providerType: ApiProviderType = ApiProviderType.ORCAROUTER,
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
