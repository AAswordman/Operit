package com.ai.assistance.operit.data.api

import android.content.Context
import com.ai.assistance.operit.data.preferences.VertexAuthPreferences
import com.ai.assistance.operit.data.preferences.VertexAuthState
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class VertexAuthManager private constructor(context: Context) {
    private val preferences = VertexAuthPreferences.getInstance(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val oauthClient = VertexOAuthClient(client = httpClient)
    private val refreshMutex = Mutex()

    val authState: StateFlow<VertexAuthState?> = preferences.authState

    suspend fun saveLoginTokens(tokens: VertexOAuthTokenResponse): VertexAuthState {
        val accessToken = tokens.accessToken ?: throw IOException("Vertex OAuth response has no access token")
        val refreshToken = tokens.refreshToken ?: throw IOException("Vertex OAuth response has no refresh token")
        val expiresIn = tokens.expiresInSeconds ?: throw IOException("Vertex OAuth response has no expiration")
        val state = VertexAuthState(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L -
                VertexOAuthProtocol.EXPIRY_SKEW_MILLIS,
            email = fetchEmail(accessToken),
        )
        preferences.save(state)
        return state
    }

    suspend fun getValidAccessToken(): String {
        val current = preferences.currentState() ?: throw IOException("Vertex AI is not logged in")
        if (current.expiresAtMillis - System.currentTimeMillis() > REFRESH_WINDOW_MILLIS) {
            return current.accessToken
        }
        return refreshMutex.withLock {
            val latest = preferences.currentState() ?: throw IOException("Vertex AI is not logged in")
            if (latest.expiresAtMillis - System.currentTimeMillis() > REFRESH_WINDOW_MILLIS) {
                latest.accessToken
            } else {
                refreshAccessToken(latest).accessToken
            }
        }
    }

    suspend fun logout() {
        preferences.clear()
    }

    private suspend fun refreshAccessToken(current: VertexAuthState): VertexAuthState {
        val response = oauthClient.refreshAccessToken(current.refreshToken)
        val accessToken = response.accessToken ?: throw IOException("Vertex refresh response has no access token")
        val expiresIn = response.expiresInSeconds ?: throw IOException("Vertex refresh response has no expiration")
        val updated = current.copy(
            accessToken = accessToken,
            refreshToken = response.refreshToken ?: current.refreshToken,
            expiresAtMillis = System.currentTimeMillis() + expiresIn * 1000L -
                VertexOAuthProtocol.EXPIRY_SKEW_MILLIS,
        )
        preferences.save(updated)
        return updated
    }

    private suspend fun fetchEmail(accessToken: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(VertexOAuthProtocol.USERINFO_URL)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            JSONObject(response.body?.string().orEmpty()).optString("email").takeIf { it.isNotBlank() }
        }
    }

    companion object {
        private const val REFRESH_WINDOW_MILLIS = 60 * 1000L

        @Volatile private var instance: VertexAuthManager? = null

        fun getInstance(context: Context): VertexAuthManager {
            return instance ?: synchronized(this) {
                instance ?: VertexAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }
}