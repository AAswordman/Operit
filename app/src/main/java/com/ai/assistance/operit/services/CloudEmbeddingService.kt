package com.ai.assistance.operit.services

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.config.model.ModelConfigData
import com.ai.assistance.operit.data.embedding.isEmbeddingReady
import com.ai.assistance.operit.data.embedding.memoryEmbeddingEndpoint
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.api.chat.keypool.MultiApiKeyProvider
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import com.ai.assistance.operit.data.model.Embedding
import com.ai.assistance.operit.util.logging.AppLogger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class CloudEmbeddingService(
    private val context: Context
) {

    companion object {
        private const val TAG = "CloudEmbeddingService"
    }

    class CloudEmbeddingException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

    private val models = ModelConfigManager(context)
    private val keyProviders = ConcurrentHashMap<String, MultiApiKeyProvider>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    suspend fun generateEmbedding(config: ModelConfigData?, text: String): Embedding? = withContext(Dispatchers.IO) {
        val normalized = config?.copy(apiEndpoint = config.apiEndpoint.trim(), modelName = config.modelName.trim())
        if (!normalized.isEmbeddingReady() || text.isBlank()) {
            return@withContext null
        }

        try {
            requestEmbedding(requireNotNull(normalized), text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "记忆向量请求失败", e)
            null
        }
    }

    suspend fun generateEmbeddingOrThrow(config: ModelConfigData?, text: String): Embedding = withContext(Dispatchers.IO) {
        val normalized = config?.copy(apiEndpoint = config.apiEndpoint.trim(), modelName = config.modelName.trim())
        if (!normalized.isEmbeddingReady()) {
            throw CloudEmbeddingException(context.getString(R.string.memory_embedding_error_config_incomplete))
        }
        if (text.isBlank()) {
            throw CloudEmbeddingException(context.getString(R.string.memory_embedding_error_input_empty))
        }

        requestEmbedding(requireNotNull(normalized), text)
    }

    private suspend fun requestEmbedding(config: ModelConfigData, text: String): Embedding {
        val apiKey = if (config.useMultipleApiKeys) {
            keyProviders.getOrPut(config.id) { MultiApiKeyProvider(config.id, models) }.getApiKey()
        } else config.apiKey.trim()
        val requestBodyJson = JSONObject()
            .put("model", config.modelName)
            .put("input", text)
            .toString()

        val builder = Request.Builder()
            .url(memoryEmbeddingEndpoint(config.apiEndpoint))
            .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
        if (config.customHeaders.isNotBlank()) {
            val headers = JSONObject(config.customHeaders)
            headers.keys().forEach { name -> builder.header(name, headers.getString(name)) }
        }
        val request = builder.build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = extractErrorMessage(responseBody).ifBlank { response.message.ifBlank { "请求失败" } }
                val message = context.getString(R.string.memory_embedding_error_http, response.code, detail)
                AppLogger.w(TAG, "$message, body=$responseBody")
                throw CloudEmbeddingException(message)
            }

            return parseEmbedding(responseBody)
        }
    }

    private fun parseEmbedding(responseBody: String): Embedding {
        if (responseBody.isBlank()) {
            throw CloudEmbeddingException(context.getString(R.string.memory_embedding_error_empty_response))
        }

        return try {
            val root = JSONObject(responseBody)
            val data = root.optJSONArray("data")
                ?: throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_missing_data, truncate(responseBody))
                )
            if (data.length() <= 0) {
                throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_empty_data, truncate(responseBody))
                )
            }

            val first = data.optJSONObject(0)
                ?: throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_invalid_first_item, truncate(responseBody))
                )
            val embeddingJson = first.optJSONArray("embedding")
                ?: throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_missing_embedding, truncate(responseBody))
                )
            if (embeddingJson.length() <= 0) {
                throw CloudEmbeddingException(
                    context.getString(R.string.memory_embedding_error_empty_embedding, truncate(responseBody))
                )
            }

            // 无效维度必须报告解析失败，不能把错误响应当作有效向量写入索引。
            val vector = FloatArray(embeddingJson.length()) { index ->
                embeddingJson.getDouble(index).toFloat().also { value ->
                    require(value.isFinite()) { "向量第 $index 维不是有限数值" }
                }
            }
            Embedding(vector)
        } catch (e: CloudEmbeddingException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse embedding response", e)
            throw CloudEmbeddingException(
                context.getString(R.string.memory_embedding_error_parse_failed, truncate(responseBody)),
                e
            )
        }
    }

    private fun extractErrorMessage(responseBody: String): String {
        if (responseBody.isBlank()) return ""

        return try {
            val root = JSONObject(responseBody)
            when {
                root.has("error") -> parseErrorNode(root.opt("error"))
                root.has("message") -> root.optString("message")
                root.has("detail") -> root.optString("detail")
                else -> truncate(responseBody)
            }.trim()
        } catch (_: Exception) {
            truncate(responseBody)
        }
    }

    private fun parseErrorNode(errorNode: Any?): String {
        return when (errorNode) {
            is JSONObject -> listOf(
                errorNode.optString("message"),
                errorNode.optString("type"),
                errorNode.optString("code")
            ).firstOrNull { it.isNotBlank() } ?: truncate(errorNode.toString())
            is JSONArray -> truncate(errorNode.toString())
            is String -> errorNode
            null -> ""
            else -> truncate(errorNode.toString())
        }
    }

    private fun truncate(text: String, maxLength: Int = 200): String {
        val singleLine = text.replace('\n', ' ').replace('\r', ' ').trim()
        return if (singleLine.length <= maxLength) singleLine else singleLine.take(maxLength) + "..."
    }
}
