package com.ai.assistance.operit.data.api

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
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
    client: OkHttpClient,
) {
    // 意图：配额查询使用 HTTP/1.1 与独立短时连接，匹配原生调用指纹。
    private val httpClient = client.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionPool(ConnectionPool(2, 30, TimeUnit.SECONDS))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(
        accessToken: String,
        projectId: String,
    ): Result<AntigravityQuotaSnapshot> {
        return try {
            // 意图：对齐 Cli-Proxy-API-Management-Center，向 retrieveUserQuotaSummary 传递带有 project 字段的请求体。
            // 不这么做后果：部分后端实现如果缺少 project 会返回空分组或拒绝提供配额详情。
            val summary = post(accessToken, "/v1internal:retrieveUserQuotaSummary", JSONObject().put("project", projectId))
            val assist = post(
                accessToken,
                "/v1internal:loadCodeAssist",
                JSONObject().put("metadata", JSONObject().put("ideType", "ANTIGRAVITY")),
            )
            Result.success(parse(summary, assist, projectId))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    internal fun parse(
        summary: JSONObject,
        assist: JSONObject?,
        projectId: String,
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
            projectId = discoveredProject ?: projectId,
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
        for (endpoint in AntigravityOAuthProtocol.quotaEndpoints) {
            val request = Request.Builder()
                .url(endpoint.trimEnd('/') + path)
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", AntigravityOAuthProtocol.USER_AGENT)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    return@withContext if (text.isBlank()) JSONObject() else JSONObject(text)
                }
                lastError = "HTTP ${response.code}"
            }
        }
        throw IOException("Antigravity $path failed: $lastError")
    }

    // 意图：修复 cloudaicompanionProject 字段拼写并支持嵌套对象格式。
    // 不这么做后果：由于原代码错拼为 clouudAiProject 导致永远解析不到正确的项目 ID。
    private fun extractProjectId(data: JSONObject): String? {
        for (key in listOf("cloudaicompanionProject", "projectId", "project")) {
            val opt = data.opt(key) ?: continue
            when (opt) {
                is String -> {
                    val trimmed = opt.trim()
                    if (trimmed.isNotBlank()) return trimmed
                }
                is JSONObject -> {
                    val id = opt.optString("id").trim().ifBlank { opt.optString("projectId").trim() }
                    if (id.isNotBlank()) return id
                }
            }
        }
        return null
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
