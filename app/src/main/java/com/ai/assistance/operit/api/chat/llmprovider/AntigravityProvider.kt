package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.data.api.AntigravityAuthManager
import com.ai.assistance.operit.data.api.AntigravityOAuthProtocol
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private class AntigravityAccessTokenProvider(
    private val authManager: AntigravityAuthManager,
) : ApiKeyProvider {
    override suspend fun getApiKey(): String = authManager.getValidAccessToken()

    override suspend fun getCandidateKeyCount(): Int =
        if (authManager.authState.value == null) 0 else 1
}

class AntigravityProvider(
    private val authManager: AntigravityAuthManager,
    apiEndpoint: String,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    enableToolCall: Boolean = false,
    thinkingConfigurations: String = "",
    thinkingOptionId: String = "",
) : GeminiProvider(
    apiEndpoint = apiEndpoint.ifBlank { AntigravityOAuthProtocol.DEFAULT_ENDPOINT },
    apiKeyProvider = AntigravityAccessTokenProvider(authManager),
    modelName = modelName,
    client = client,
    customHeaders = customHeaders,
    providerType = ApiProviderType.ANTIGRAVITY,
    enableGoogleSearch = false,
    enableToolCall = enableToolCall,
    thinkingConfigurations = thinkingConfigurations,
    thinkingOptionId = thinkingOptionId,
) {
    override suspend fun createRequest(
        context: Context,
        requestBody: RequestBody,
        isStreaming: Boolean,
        requestId: String,
    ): Request {
        val inner = JSONObject(requestBodyToString(requestBody))
        val projectId = authManager.currentProjectId()
            ?: throw IllegalStateException("Antigravity project id is unavailable")
        val envelope = JSONObject()
            .put("project", projectId)
            .put("model", AntigravityOAuthProtocol.runtimeModelId(modelName))
            .put("request", inner)
            .put("requestType", "agent")
            .put("userAgent", "antigravity")
            .put("requestId", "operit-$requestId-${UUID.randomUUID()}")
        val method = if (isStreaming) "streamGenerateContent?alt=sse" else "generateContent"
        val url = apiEndpoint.trimEnd('/') + "/v1internal:$method"
        val token = apiKeyProvider.getApiKey()
        val builder = Request.Builder()
            .url(url)
            .post(envelope.toString().toRequestBody(JSON))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", if (isStreaming) "text/event-stream" else "application/json")
            .header("User-Agent", AntigravityOAuthProtocol.USER_AGENT)
            .header("X-Goog-Api-Client", "google-cloud-sdk vscode_cloudshelleditor/0.1")
            .header("Client-Metadata", AntigravityOAuthProtocol.clientMetadata())
        if (modelName.startsWith("claude-")) {
            builder.header("anthropic-beta", "interleaved-thinking-2025-05-14")
        }
        customHeaders.forEach { (key, value) -> builder.header(key, value) }
        return builder.build()
    }

    override suspend fun getModelsList(_context: Context): Result<List<ModelOption>> {
        return Result.success(
            AntigravityOAuthProtocol.defaultModels.map { (id, name) ->
                ModelOption(id = id, name = name)
            },
        )
    }

    override fun unwrapStreamingPayload(json: JSONObject): JSONObject {
        return json.optJSONObject("response") ?: json
    }

    private fun requestBodyToString(requestBody: RequestBody): String {
        val buffer = okio.Buffer()
        requestBody.writeTo(buffer)
        return buffer.readUtf8()
    }
}