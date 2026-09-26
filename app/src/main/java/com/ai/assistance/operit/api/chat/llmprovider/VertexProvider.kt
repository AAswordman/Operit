package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.api.VertexAuthManager
import com.ai.assistance.operit.data.api.VertexOAuthProtocol
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject

private class VertexAccessTokenProvider(
    private val authManager: VertexAuthManager,
) : ApiKeyProvider {
    override suspend fun getApiKey(): String = authManager.getValidAccessToken()

    override suspend fun getCandidateKeyCount(): Int =
        if (authManager.authState.value == null) 0 else 1
}

class VertexProvider(
    authManager: VertexAuthManager,
    apiEndpoint: String,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    supportsVision: Boolean = true,
    enableToolCall: Boolean = false,
    thinkingConfigurations: String = "",
    thinkingOptionId: String = "",
) : OpenAIProvider(
    apiEndpoint = apiEndpoint,
    apiKeyProvider = VertexAccessTokenProvider(authManager),
    modelName = modelName,
    client = client,
    customHeaders = customHeaders,
    providerType = ApiProviderType.VERTEX_AI,
    supportsVision = supportsVision,
    enableToolCall = enableToolCall,
    thinkingConfigurations = thinkingConfigurations,
    thinkingOptionId = thinkingOptionId,
) {
    override fun applyAuthenticationHeaders(builder: Request.Builder, currentApiKey: String) {
        builder.header("Authorization", "Bearer $currentApiKey")
    }

    override suspend fun getModelsList(context: Context): Result<List<ModelOption>> {
        return Result.success(
            VertexOAuthProtocol.defaultModels.map { (id, name) ->
                ModelOption(id = id, name = name)
            },
        )
    }

    override fun createRequestBody(
        context: Context,
        chatHistory: List<PromptTurn>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean,
    ): RequestBody {
        val body = super.createRequestBody(
            context,
            chatHistory,
            modelParameters,
            enableThinking,
            stream,
            availableTools,
            preserveThinkInHistory,
        )
        val buffer = okio.Buffer()
        body.writeTo(buffer)
        val json = JSONObject(buffer.readUtf8())
        json.put("model", VertexOAuthProtocol.wireModelId(modelName))
        return createJsonRequestBody(json.toString())
    }
}