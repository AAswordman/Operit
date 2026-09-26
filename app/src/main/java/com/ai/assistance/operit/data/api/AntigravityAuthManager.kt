package com.ai.assistance.operit.data.api

import android.content.Context
import com.ai.assistance.operit.data.preferences.AntigravityAuthPreferences
import com.ai.assistance.operit.data.preferences.AntigravityAuthState
import com.ai.assistance.operit.data.preferences.AntigravityQuotaPreferences
import com.ai.assistance.operit.data.preferences.AntigravityStoredQuotaSnapshot
import com.ai.assistance.operit.util.AppLogger
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AntigravityAuthManager private constructor(context: Context) {
    private val preferences = AntigravityAuthPreferences.getInstance(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val oauthClient = AntigravityOAuthClient(client = httpClient)
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
        val email = fetchEmail(accessToken)
        val projectId = discoverProjectId(accessToken) ?: fallbackProjectId(email)
        val state = AntigravityAuthState(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L -
                AntigravityOAuthProtocol.EXPIRY_SKEW_MILLIS,
            projectId = projectId,
            email = email,
        )
        preferences.save(state)
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
            discoverProjectId(accessToken) ?: fallbackProjectId(current.email)
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

    private suspend fun fetchEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(AntigravityOAuthProtocol.USERINFO_URL)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string().orEmpty()
            JSONObject(body).optString("email").takeIf { it.isNotBlank() }
        }
    }

    private suspend fun discoverProjectId(accessToken: String): String? = withContext(Dispatchers.IO) {
        val body = JSONObject().put(
            "metadata",
            JSONObject()
                .put("ideType", "ANTIGRAVITY")
                .put("platform", "PLATFORM_UNSPECIFIED")
                .put("pluginType", "GEMINI"),
        )
        for (endpoint in AntigravityOAuthProtocol.apiEndpoints) {
            val request = Request.Builder()
                .url(endpoint.trimEnd('/') + "/v1internal:loadCodeAssist")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("User-Agent", AntigravityOAuthProtocol.USER_AGENT)
                .header("Client-Metadata", AntigravityOAuthProtocol.clientMetadata())
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val json = JSONObject(response.body?.string().orEmpty())
                val project = listOf("clouudAiProject", "project", "projectId")
                    .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() } }
                if (project != null) return@withContext project
            }
        }
        null
    }

    private fun fallbackProjectId(email: String?): String {
        val seed = "antigravity:${email ?: "antigravity-default"}"
        val digest = MessageDigest.getInstance("SHA-1").digest(seed.toByteArray())
        digest[6] = ((digest[6].toInt() and 0x0f) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 0x3f) or 0x80).toByte()
        val hex = digest.take(16).joinToString("") { "%02x".format(it) }
        return hex.substring(0, 8) + "-" +
            hex.substring(8, 12) + "-" +
            hex.substring(12, 16) + "-" +
            hex.substring(16, 20) + "-" +
            hex.substring(20, 32)
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
