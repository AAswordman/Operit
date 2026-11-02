package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.data.api.AntigravityAuthManager
import com.ai.assistance.operit.data.api.AntigravityOAuthProtocol
import com.ai.assistance.operit.data.config.model.ApiProviderType
import com.ai.assistance.operit.data.config.model.ModelOption
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import com.ai.assistance.operit.api.chat.keypool.ApiKeyProvider

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
    // 意图：按 CLIProxyAPI 对齐原生客户端 HTTP/1.1 与专用短时连接池配置。
    client = client.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionPool(ConnectionPool(2, 30, TimeUnit.SECONDS))
        .build(),
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
        val runtimeModel = AntigravityOAuthProtocol.runtimeModelId(modelName)
        val isImageModel = modelName.contains("image", ignoreCase = true)
        val requestType = if (isImageModel) "image_gen" else "agent"

        // 意图：按照 CLIProxyAPI 伪装请求 ID 格式，生图使用 image_gen 格式，对话使用 agent-<uuid>。
        // 不这么做后果：带有 operit- 前缀会被上游日志轻易识别为第三方客户端。
        val reqId = if (isImageModel) {
            "image_gen/${System.currentTimeMillis()}/${UUID.randomUUID()}/12"
        } else {
            "agent-${UUID.randomUUID()}"
        }

        // 意图：移除 safetySettings，避免 Antigravity 网关因未知字段或非预期的安全设置返回校验错误。
        inner.remove("safetySettings")

        // 意图：为非生图对话基于首条用户消息生成稳定的负数会话标识，模拟原生会话追踪指纹。
        if (!isImageModel) {
            val sessionId = deriveSessionId(inner)
            inner.put("sessionId", sessionId)
        }

        // 意图：针对 Claude 系列模型设置 functionCallingConfig.mode 为 VALIDATED，对齐 CLIProxyAPI。
        if (runtimeModel.contains("claude", ignoreCase = true)) {
            val toolConfig = inner.optJSONObject("toolConfig") ?: JSONObject().also { inner.put("toolConfig", it) }
            val funcConfig = toolConfig.optJSONObject("functionCallingConfig") ?: JSONObject().also { toolConfig.put("functionCallingConfig", it) }
            funcConfig.put("mode", "VALIDATED")
        }

        val envelope = JSONObject()
            .put("project", projectId)
            .put("model", runtimeModel)
            .put("request", inner)
            .put("requestType", requestType)
            .put("userAgent", "antigravity")
            .put("requestId", reqId)

        val method = if (isStreaming) "streamGenerateContent?alt=sse" else "generateContent"
        val endpoint = apiEndpoint.ifBlank { AntigravityOAuthProtocol.DEFAULT_ENDPOINT }
        val url = endpoint.trimEnd('/') + "/v1internal:$method"
        val token = apiKeyProvider.getApiKey()

        // 意图：只保留必需的 Header，移除会暴露插件身份的 X-Goog-Api-Client 和 Client-Metadata。
        val builder = Request.Builder()
            .url(url)
            .post(envelope.toString().toRequestBody(JSON))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .header("Accept", if (isStreaming) "text/event-stream" else "application/json")
            .header("User-Agent", AntigravityOAuthProtocol.USER_AGENT)

        customHeaders.forEach { (key, value) -> builder.header(key, value) }
        return builder.build()
    }

    override suspend fun getModelsList(_context: Context): Result<List<ModelOption>> {
        return authManager.fetchModels().map { models ->
            models.ifEmpty {
                AntigravityOAuthProtocol.defaultModels.map { (id, name) ->
                    ModelOption(id = id, name = name)
                }
            }
        }.recover {
            AntigravityOAuthProtocol.defaultModels.map { (id, name) ->
                ModelOption(id = id, name = name)
            }
        }
    }

    override fun unwrapStreamingPayload(json: JSONObject): JSONObject {
        return json.optJSONObject("response") ?: json
    }

    // 意图：从第一条用户消息内容计算稳定的会话 ID，保证同一轮对话拥有连续的上下文会话标识。
    private fun deriveSessionId(request: JSONObject): String {
        val contents = request.optJSONArray("contents") ?: return generateRandomSessionId()
        for (i in 0 until contents.length()) {
            val content = contents.optJSONObject(i) ?: continue
            if (content.optString("role") == "user") {
                val parts = content.optJSONArray("parts") ?: continue
                val firstPart = parts.optJSONObject(0) ?: continue
                val text = firstPart.optString("text")
                if (text.isNotBlank()) {
                    val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))
                    var value = 0L
                    for (b in 0 until 8) {
                        value = (value shl 8) or (hash[b].toLong() and 0xFFL)
                    }
                    value = value and 0x7FFFFFFFFFFFFFFFL
                    return "-$value"
                }
            }
        }
        return generateRandomSessionId()
    }

    private fun generateRandomSessionId(): String {
        val random = SecureRandom()
        val n = (random.nextLong() and 0x7FFFFFFFFFFFFFFFL) % 9_000_000_000_000_000_000L
        return "-$n"
    }

    private fun requestBodyToString(requestBody: RequestBody): String {
        val buffer = okio.Buffer()
        requestBody.writeTo(buffer)
        return buffer.readUtf8()
    }
}
