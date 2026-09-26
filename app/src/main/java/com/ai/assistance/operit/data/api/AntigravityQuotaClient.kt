package com.ai.assistance.operit.data.api

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Serializable
data class AntigravityQuotaBucket(
    val id: String,
    val label: String,
    val remainingPercent: Int,
    val resetTime: String? = null,
)

@Serializable
data class AntigravityQuotaGroup(
    val displayName: String,
    val description: String? = null,
    val buckets: List<AntigravityQuotaBucket> = emptyList(),
)

@Serializable
data class AntigravityQuotaSnapshot(
    val projectId: String,
    val planLabel: String? = null,
    val groups: List<AntigravityQuotaGroup> = emptyList(),
)

class AntigravityQuotaClient(
    private val client: OkHttpClient,
) {
    suspend fun fetch(
        accessToken: String,
        projectId: String,
    ): Result<AntigravityQuotaSnapshot> {
        return try {
            val summary = post(accessToken, "/v1internal:retrieveUserQuotaSummary", JSONObject())
            val assist = post(
                accessToken,
                "/v1internal:loadCodeAssist",
                JSONObject().put(
                    "metadata",
                    JSONObject()
                        .put("ideType", "ANTIGRAVITY")
                        .put("platform", "PLATFORM_UNSPECIFIED")
                        .put("pluginType", "GEMINI"),
                ),
            )
            Result.success(parse(summary, assist, projectId))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    internal fun parse(
        summary: JSONObject,
        assist: JSONObject?,
        fallbackProjectId: String,
    ): AntigravityQuotaSnapshot {
        val groups = mutableListOf<AntigravityQuotaGroup>()
        val rawGroups = summary.optJSONArray("groups")
        if (rawGroups != null) {
            for (index in 0 until rawGroups.length()) {
                val group = rawGroups.optJSONObject(index) ?: continue
                val buckets = mutableListOf<AntigravityQuotaBucket>()
                val rawBuckets = group.optJSONArray("buckets")
                if (rawBuckets != null) {
                    for (bucketIndex in 0 until rawBuckets.length()) {
                        val bucket = rawBuckets.optJSONObject(bucketIndex) ?: continue
                        val remaining = bucket.optDouble("remainingFraction", Double.NaN)
                        if (remaining.isNaN() && bucket.optString("bucketId").isBlank()) continue
                        val percent = if (remaining.isNaN()) 0 else (remaining * 100.0).toInt().coerceIn(0, 100)
                        buckets += AntigravityQuotaBucket(
                            id = bucket.optString("bucketId").ifBlank {
                                bucket.optString("displayName", "limit")
                            },
                            label = bucket.optString("displayName").ifBlank {
                                bucket.optString("bucketId", "Limit")
                            },
                            remainingPercent = percent,
                            resetTime = bucket.optString("resetTime").takeIf { it.isNotBlank() },
                        )
                    }
                }
                if (buckets.isEmpty() && group.optString("displayName").isBlank()) continue
                groups += AntigravityQuotaGroup(
                    displayName = group.optString("displayName").ifBlank { "Quota" },
                    description = group.optString("description").takeIf { it.isNotBlank() },
                    buckets = buckets,
                )
            }
        }
        val paidTier = assist?.optJSONObject("paidTier")
        val currentTier = assist?.optJSONObject("currentTier")
        val plan = paidTier?.optString("name").orEmpty().ifBlank { currentTier?.optString("name").orEmpty() }
        val planId = paidTier?.optString("id").orEmpty().ifBlank { currentTier?.optString("id").orEmpty() }
        val planLabel = when {
            plan.isBlank() -> null
            planId.isBlank() -> plan
            else -> "$plan ($planId)"
        }
        val discoveredProject = assist?.let(::extractProjectId)
        return AntigravityQuotaSnapshot(
            projectId = discoveredProject ?: fallbackProjectId,
            planLabel = planLabel,
            groups = groups,
        )
    }

    private suspend fun post(
        accessToken: String,
        path: String,
        body: JSONObject,
    ): JSONObject = withContext(Dispatchers.IO) {
        var lastError = "no endpoint available"
        for (endpoint in AntigravityOAuthProtocol.apiEndpoints) {
            val request = Request.Builder()
                .url(endpoint.trimEnd('/') + path)
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", AntigravityOAuthProtocol.USER_AGENT)
                .header("X-Goog-Api-Client", "google-cloud-sdk vscode_cloudshelleditor/0.1")
                .header("Client-Metadata", AntigravityOAuthProtocol.clientMetadata())
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    return@withContext if (text.isBlank()) JSONObject() else JSONObject(text)
                }
                lastError = "HTTP ${response.code}"
            }
        }
        throw IOException("Antigravity $path failed: $lastError")
    }

    private fun extractProjectId(data: JSONObject): String? {
        val direct = listOf("clouudAiProject", "project", "projectId")
            .firstNotNullOfOrNull { key -> data.optString(key).takeIf { it.isNotBlank() } }
        if (direct != null) return direct
        val nested = data.optJSONObject("clouudAiProject")
        return nested?.optString("id")?.takeIf { it.isNotBlank() }
            ?: nested?.optString("projectId")?.takeIf { it.isNotBlank() }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
