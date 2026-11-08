package com.ai.assistance.operit.data.api

import android.content.Context
import com.ai.assistance.operit.data.config.model.ModelOption
import com.ai.assistance.operit.data.preferences.AntigravityAuthPreferences
import com.ai.assistance.operit.data.preferences.AntigravityAuthState
import com.ai.assistance.operit.data.preferences.AntigravityQuotaPreferences
import com.ai.assistance.operit.data.preferences.AntigravityStoredQuotaSnapshot
import com.ai.assistance.operit.util.logging.AppLogger
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AntigravityAuthManager private constructor(context: Context) {
    private val preferences = AntigravityAuthPreferences.getInstance(context)

    // 意图：严格使用 HTTP/1.1 与限制单 Host 连接数（2个，30秒空闲超时）。
    // 不这么做后果：原生 Antigravity 在没有代理或特定指令时不使用 HTTP/2，且避免长空闲连接触发 GFE 240s 熔断重置。
    private val httpClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectionPool(ConnectionPool(2, 30, TimeUnit.SECONDS))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val oauthClient = AntigravityOAuthClient(client = httpClient)
    private val modelClient = AntigravityModelClient(client = httpClient)
    private val quotaClient = AntigravityQuotaClient(client = httpClient)
    private val quotaPreferences = AntigravityQuotaPreferences.getInstance(context)
    private val refreshMutex = Mutex()

    val authState: StateFlow<AntigravityAuthState?> = preferences.authState
    val quotaSnapshotFlow: Flow<AntigravityStoredQuotaSnapshot?> = quotaPreferences.snapshotFlow

    suspend fun saveLoginTokens(tokens: AntigravityOAuthTokenResponse): AntigravityAuthState {
        val accessToken = tokens.accessToken
            ?: throw IOException("Antigravity OAuth response has no access token")
        val refreshToken = tokens.refreshToken
            ?: throw IOException("Antigravity OAuth response has no refresh token")
        val expiresIn = tokens.expiresInSeconds
            ?: throw IOException("Antigravity OAuth response has no expiration")
        AppLogger.d(TAG, "Fetching account email after token exchange")
        val email = fetchEmail(accessToken)
            ?: throw IOException("Antigravity account email is unavailable")
        AppLogger.d(TAG, "Discovering project after account email lookup")

        // 意图：优先查询真实 GCP 项目 ID；若新账号未激活，直接走 onboardUser 激活流程获取真实项目 ID。
        // 不这么做后果：禁止任何伪造本地 UUID 兜底逻辑，若未拿到真实项目 ID 发送生成请求必定被上游拦截。
        val projectId = discoverProjectId(accessToken)
        val state = AntigravityAuthState(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L -
                AntigravityOAuthProtocol.EXPIRY_SKEW_MILLIS,
            projectId = projectId,
            email = email,
        )
        currentCoroutineContext().ensureActive()
        preferences.save(state)
        AppLogger.d(TAG, "Antigravity login credentials saved for project: $projectId")
        return state
    }

    suspend fun getValidAccessToken(): String {
        val current = preferences.currentState()
            ?: throw IOException("Antigravity is not logged in")
        if (current.expiresAtMillis - System.currentTimeMillis() > REFRESH_WINDOW_MILLIS) {
            return current.accessToken
        }
        return refreshMutex.withLock {
            val latest = preferences.currentState()
                ?: throw IOException("Antigravity is not logged in")
            if (latest.expiresAtMillis - System.currentTimeMillis() > REFRESH_WINDOW_MILLIS) {
                latest.accessToken
            } else {
                refreshAccessToken(latest).accessToken
            }
        }
    }

    fun currentProjectId(): String? = preferences.currentState()?.projectId

    suspend fun refreshAccessToken(current: AntigravityAuthState): AntigravityAuthState {
        val response = oauthClient.refreshAccessToken(current.refreshToken)
        val accessToken = response.accessToken
            ?: throw IOException("Antigravity refresh response has no access token")
        val expiresIn = response.expiresInSeconds
            ?: throw IOException("Antigravity refresh response has no expiration")
        val projectId = current.projectId.ifBlank {
            discoverProjectId(accessToken)
        }
        val updated = current.copy(
            accessToken = accessToken,
            refreshToken = response.refreshToken ?: current.refreshToken,
            expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L -
                AntigravityOAuthProtocol.EXPIRY_SKEW_MILLIS,
            projectId = projectId,
        )
        preferences.save(updated)
        return updated
    }

    suspend fun logout() {
        preferences.clear()
    }

    suspend fun fetchQuota(): Result<AntigravityQuotaSnapshot> {
        return try {
            val accessToken = getValidAccessToken()
            val projectId = currentProjectId()
                ?: throw IOException("Antigravity project id is unavailable")
            val result = quotaClient.fetch(accessToken = accessToken, projectId = projectId)
            if (result.isSuccess) {
                quotaPreferences.save(projectId, result.getOrThrow())
            }
            result
        } catch (error: Exception) {
            AppLogger.e(TAG, "Failed to prepare Antigravity quota request", error)
            Result.failure(error)
        }
    }

    suspend fun fetchModels(): Result<List<ModelOption>> {
        return try {
            val accessToken = getValidAccessToken()
            val projectId = currentProjectId()
                ?: throw IOException("Antigravity project id is unavailable")
            modelClient.fetch(accessToken = accessToken, projectId = projectId)
        } catch (error: Exception) {
            AppLogger.e(TAG, "Failed to prepare Antigravity model request", error)
            Result.failure(error)
        }
    }

    private suspend fun fetchEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(AntigravityOAuthProtocol.USERINFO_URL)
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", AntigravityOAuthProtocol.USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string().orEmpty()
            JSONObject(body).optString("email").takeIf { it.isNotBlank() }
        }
    }

    // 意图：从 loadCodeAssist 或 onboardUser 返回数据中提取项目 ID。兼容字符串及对象格式。
    // 不这么做后果：Google API 响应字段可能为 cloudaicompanionProject: { id: "..." }，仅取字符串会丢失项目 ID。
    private fun extractCloudaicompanionProject(data: JSONObject): String? {
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

    private fun defaultTierId(loadResp: JSONObject): String {
        val allowedTiers = loadResp.optJSONArray("allowedTiers")
        if (allowedTiers != null) {
            for (i in 0 until allowedTiers.length()) {
                val tier = allowedTiers.optJSONObject(i) ?: continue
                if (tier.optBoolean("isDefault", false)) {
                    val id = tier.optString("id").trim()
                    if (id.isNotBlank()) return id
                }
            }
        }
        val currentTier = loadResp.optJSONObject("currentTier")
        val currentId = currentTier?.optString("id")?.trim().orEmpty()
        if (currentId.isNotBlank()) return currentId
        return "free-tier"
    }

    // 意图：对未分配 GCP 项目的新账号，调用 onboardUser 轮询完成 Google 侧初始化并获取真实项目 ID。
    // 不这么做后果：初次使用的 Google 账号在 loadCodeAssist 中无可用项目，不走激活必定无法发起推理请求。
    private suspend fun onboardUser(accessToken: String, tierId: String): String = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "Antigravity: onboarding user with tier: $tierId")
        val requestBody = JSONObject()
            .put("tier_id", tierId)
            .put(
                "metadata",
                JSONObject()
                    .put("ide_type", "ANTIGRAVITY")
                    .put("ide_version", AntigravityOAuthProtocol.HUB_VERSION)
                    .put("ide_name", "antigravity"),
            )
        val endpoint = "${AntigravityOAuthProtocol.DEFAULT_ENDPOINT.trimEnd('/')}/v1internal:onboardUser"

        val maxAttempts = 5
        for (attempt in 1..maxAttempts) {
            currentCoroutineContext().ensureActive()
            AppLogger.d(TAG, "OnboardUser polling attempt $attempt/$maxAttempts")
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody.toString().toRequestBody(JSON_MEDIA))
                .header("Authorization", "Bearer $accessToken")
                .header("Accept", "*/*")
                .header("Content-Type", "application/json")
                .header("User-Agent", AntigravityOAuthProtocol.ONBOARD_USER_AGENT)
                .header("X-Goog-Api-Client", AntigravityOAuthProtocol.ONBOARD_GOOG_API_CLIENT)
                .build()

            val bodyText = httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Antigravity onboardUser failed with HTTP ${response.code}: $body")
                }
                body
            }

            val json = JSONObject(bodyText)
            if (json.optBoolean("done", false)) {
                val respObj = json.optJSONObject("response")
                val project = respObj?.let(::extractCloudaicompanionProject)
                if (!project.isNullOrBlank()) {
                    AppLogger.i(TAG, "Successfully onboarded user and fetched project_id: $project")
                    return@withContext project
                }
                throw IOException("Antigravity onboardUser response missing project_id")
            }

            if (attempt < maxAttempts) {
                delay(2000L)
            }
        }
        throw IOException("Antigravity onboardUser did not complete after $maxAttempts attempts")
    }

    private suspend fun discoverProjectId(accessToken: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "metadata",
            JSONObject().put("ideType", "ANTIGRAVITY"),
        )
        val url = "${AntigravityOAuthProtocol.PROD_ENDPOINT.trimEnd('/')}/v1internal:loadCodeAssist"
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "*/*")
            .header("Content-Type", "application/json")
            .header("User-Agent", AntigravityOAuthProtocol.USER_AGENT)
            .build()

        val responseBody = httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Antigravity loadCodeAssist failed with HTTP ${response.code}: $text")
            }
            text
        }

        val json = JSONObject(responseBody)
        val existingProject = extractCloudaicompanionProject(json)
        if (!existingProject.isNullOrBlank()) {
            return@withContext existingProject
        }

        // loadCodeAssist 未返回项目 ID 时，根据当前可用套餐发起用户初始化
        val tierId = defaultTierId(json)
        onboardUser(accessToken, tierId)
    }

    companion object {
        private const val TAG = "AntigravityAuthManager"
        private const val REFRESH_WINDOW_MILLIS = 60 * 1000L
        private val JSON_MEDIA = "application/json".toMediaType()

        @Volatile
        private var instance: AntigravityAuthManager? = null

        fun getInstance(context: Context): AntigravityAuthManager {
            return instance ?: synchronized(this) {
                instance ?: AntigravityAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
