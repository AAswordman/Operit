package com.ai.assistance.operit.data.api

import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.util.logging.AppLogger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class GitHubOAuthBrokerStartResponse(
    val ok: Boolean,
    val transactionId: String,
    val deliveryCredential: String,
    val authorizationUrl: String,
    val completionRedirectUri: String,
    val expiresAt: Long
)

@Serializable
private data class GitHubOAuthBrokerClaimResponse(
    val ok: Boolean,
    val status: String,
    val accessToken: String? = null,
    val tokenType: String? = null,
    val scope: String? = null,
    val expiresIn: Long? = null,
    val refreshToken: String? = null,
    val user: GitHubUser? = null
)

data class GitHubOAuthBrokerClaimResult(
    val accessToken: String,
    val tokenType: String,
    val scope: String,
    val expiresIn: Long?,
    val refreshToken: String?,
    val user: GitHubUser
)

class GitHubOAuthBrokerService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun startLogin(completionRedirectUri: String): Result<GitHubOAuthBrokerStartResponse> = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("completionRedirectUri", completionRedirectUri)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$BROKER_BASE_URL/oauth/github/start")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    return@withContext Result.failure(
                        IllegalStateException(httpErrorMessage(response.code, response.message, responseBody))
                    )
                }
                Result.success(json.decodeFromString<GitHubOAuthBrokerStartResponse>(responseBody))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start GitHub OAuth broker transaction", e)
            Result.failure(e)
        }
    }

    suspend fun claimLogin(
        transactionId: String,
        deliveryCredential: String
    ): Result<GitHubOAuthBrokerClaimResult> = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("transactionId", transactionId)
                put("deliveryCredential", deliveryCredential)
            }.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$BROKER_BASE_URL/oauth/github/claim")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (response.code != 200 || responseBody == null) {
                    return@withContext Result.failure(
                        IllegalStateException(httpErrorMessage(response.code, response.message, responseBody))
                    )
                }
                val payload = json.decodeFromString<GitHubOAuthBrokerClaimResponse>(responseBody)
                if (!payload.ok) {
                    return@withContext Result.failure(IllegalStateException("GitHub OAuth broker rejected the transaction"))
                }
                if (payload.status != "complete") {
                    return@withContext Result.failure(
                        IllegalStateException("Unsupported GitHub OAuth broker status: ${payload.status}")
                    )
                }
                Result.success(
                    GitHubOAuthBrokerClaimResult(
                        accessToken = requireValue(payload.accessToken, "accessToken"),
                        tokenType = requireValue(payload.tokenType, "tokenType"),
                        scope = requireValue(payload.scope, "scope"),
                        expiresIn = payload.expiresIn,
                        refreshToken = payload.refreshToken,
                        user = requireValue(payload.user, "user")
                    )
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to claim GitHub OAuth broker transaction", e)
            Result.failure(e)
        }
    }

    private fun httpErrorMessage(code: Int, message: String, body: String?): String {
        return if (body.isNullOrBlank()) {
            "GitHub OAuth broker request failed: HTTP $code $message"
        } else {
            "GitHub OAuth broker request failed: HTTP $code $body"
        }
    }

    private fun <T> requireValue(value: T?, name: String): T {
        return value ?: throw IllegalStateException("GitHub OAuth broker response is missing $name")
    }

    companion object {
        private const val TAG = "GitHubOAuthBroker"
        private const val BROKER_BASE_URL = "https://api.operit.app"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
