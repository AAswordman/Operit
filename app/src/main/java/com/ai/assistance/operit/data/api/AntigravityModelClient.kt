package com.ai.assistance.operit.data.api

import com.ai.assistance.operit.data.config.model.ModelOption
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** 通过 Antigravity 内部接口获取当前账号实际可用的模型。 */
class AntigravityModelClient(
    private val client: OkHttpClient,
) {
    suspend fun fetch(
        accessToken: String,
        projectId: String,
    ): Result<List<ModelOption>> = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                if (projectId.isNotBlank()) {
                    put("project", projectId)
                }
            }
            var lastError = "no endpoint available"

            for (endpoint in AntigravityOAuthProtocol.apiEndpoints) {
                try {
                    val request = Request.Builder()
                        .url(endpoint.trimEnd('/') + "/v1internal:fetchAvailableModels")
                        .post(requestBody.toString().toRequestBody(JSON_MEDIA))
                        .header("Authorization", "Bearer $accessToken")
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("User-Agent", AntigravityOAuthProtocol.USER_AGENT)
                        .header("X-Goog-Api-Client", "google-cloud-sdk vscode_cloudshelleditor/0.1")
                        .build()

                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            if (responseBody.isBlank()) {
                                throw IOException("Antigravity model list response is empty")
                            }
                            return@withContext Result.success(parseModels(JSONObject(responseBody)))
                        }
                        lastError = "HTTP ${response.code}"
                    }
                } catch (error: IOException) {
                    lastError = error.message ?: error.javaClass.simpleName
                }
            }

            Result.failure(IOException("Antigravity model list request failed: $lastError"))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    internal fun parseModels(payload: JSONObject): List<ModelOption> {
        val optionsById = linkedMapOf<String, ModelOption>()
        when (val rawModels = payload.opt("models")) {
            is JSONObject -> {
                val keys = rawModels.keys()
                while (keys.hasNext()) {
                    val sourceId = keys.next()
                    addModelOption(optionsById, sourceId, rawModels.optJSONObject(sourceId))
                }
            }
            is JSONArray -> {
                for (index in 0 until rawModels.length()) {
                    addModelOption(optionsById, "", rawModels.optJSONObject(index))
                }
            }
        }
        return optionsById.values.sortedBy { it.id }
    }

    private fun addModelOption(
        optionsById: MutableMap<String, ModelOption>,
        sourceId: String,
        entry: JSONObject?,
    ) {
        val id = entry?.optString("modelName").orEmpty()
            .ifBlank { entry?.optString("id").orEmpty() }
            .ifBlank { sourceId }
            .removePrefix("models/")
            .trim()
        if (id.isBlank() || optionsById.containsKey(id)) return

        val name = entry?.optString("displayName").orEmpty()
            .ifBlank { id }
        optionsById[id] = ModelOption(id = id, name = name)
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
