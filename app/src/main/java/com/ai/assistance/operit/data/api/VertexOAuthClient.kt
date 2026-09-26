package com.ai.assistance.operit.data.api

import android.net.Uri
import android.util.Base64
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class VertexPkceCodes(
    val verifier: String,
    val challenge: String,
)

data class VertexOAuthTokenResponse(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresInSeconds: Long?,
)

object VertexOAuthProtocol {
    const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    const val USERINFO_URL = "https://www.googleapis.com/oauth2/v1/userinfo?alt=json"
    const val CALLBACK_PATH = "/vertex-oauth-callback"
    const val OAUTH_TIMEOUT_MILLIS = 5 * 60 * 1000L
    const val EXPIRY_SKEW_MILLIS = 5 * 60 * 1000L
    const val DEFAULT_LOCATION = "global"
    const val DEFAULT_MODEL = "gemini-3.8-flash"

    val scopes = listOf(
        "https://www.googleapis.com/auth/cloud-platform",
        "https://www.googleapis.com/auth/userinfo.email",
        "https://www.googleapis.com/auth/userinfo.profile",
    )

    val defaultModels = listOf(
        DEFAULT_MODEL to "Gemini 3.8 Flash",
        "gemini-3.5-flash" to "Gemini 3.5 Flash",
        "gemini-3.1-pro-preview" to "Gemini 3.1 Pro Preview",
        "gemini-3-flash-preview" to "Gemini 3 Flash Preview",
        "gemini-2.5-pro" to "Gemini 2.5 Pro",
        "gemini-2.5-flash" to "Gemini 2.5 Flash",
        "gemini-2.5-flash-lite" to "Gemini 2.5 Flash-Lite",
    )

    private val clientId = reversed("moc.stnetnocelgoog.sppa.95504555923")

    fun generatePkce(random: SecureRandom = SecureRandom()): VertexPkceCodes {
        val verifier = encodeBase64Url(randomBytes(32, random))
        val challenge = encodeBase64Url(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)),
        )
        return VertexPkceCodes(verifier = verifier, challenge = challenge)
    }

    fun generateState(random: SecureRandom = SecureRandom()): String {
        return encodeBase64Url(randomBytes(32, random))
    }

    fun buildAuthorizationUrl(
        redirectUri: String,
        pkce: VertexPkceCodes,
        state: String,
    ): String {
        return Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", scopes.joinToString(" "))
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()
            .toString()
    }

    fun openAiRoot(project: String, location: String): String {
        val host = if (location == DEFAULT_LOCATION) {
            "aiplatform.googleapis.com"
        } else {
            "$location-aiplatform.googleapis.com"
        }
        return "https://$host/v1/projects/${Uri.encode(project)}/locations/${Uri.encode(location)}/endpoints/openapi"
    }

    fun wireModelId(modelId: String): String {
        val normalized = modelId.trim()
        return if (normalized.contains('/')) normalized else "google/$normalized"
    }

    fun authorizationCodeBody(
        code: String,
        redirectUri: String,
        verifier: String,
    ): List<Pair<String, String>> {
        return listOf(
            "client_id" to clientId,
            "code" to code,
            "grant_type" to "authorization_code",
            "redirect_uri" to redirectUri,
            "code_verifier" to verifier,
        )
    }

    fun refreshTokenBody(refreshToken: String): List<Pair<String, String>> {
        return listOf(
            "client_id" to clientId,
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token",
        )
    }

    private fun randomBytes(size: Int, random: SecureRandom): ByteArray {
        return ByteArray(size).also(random::nextBytes)
    }

    private fun encodeBase64Url(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun reversed(value: String): String = value.reversed()
}

class VertexOAuthClient(
    private val client: OkHttpClient,
) {
    suspend fun exchangeAuthorizationCode(
        code: String,
        redirectUri: String,
        verifier: String,
    ): VertexOAuthTokenResponse {
        return executeTokenRequest(
            VertexOAuthProtocol.authorizationCodeBody(code, redirectUri, verifier),
        ).requireRefreshToken()
    }

    suspend fun refreshAccessToken(refreshToken: String): VertexOAuthTokenResponse {
        return executeTokenRequest(VertexOAuthProtocol.refreshTokenBody(refreshToken))
    }

    private fun VertexOAuthTokenResponse.requireRefreshToken(): VertexOAuthTokenResponse {
        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
            throw IOException("Vertex OAuth response has no refresh token")
        }
        return this
    }

    private suspend fun executeTokenRequest(
        fields: List<Pair<String, String>>,
    ): VertexOAuthTokenResponse {
        val encoded = fields.joinToString("&") { (key, value) ->
            java.net.URLEncoder.encode(key, StandardCharsets.UTF_8.name()) + "=" +
                java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        }
        val responseBody = withContext(Dispatchers.IO) {
            client.newCall(
                Request.Builder()
                    .url(VertexOAuthProtocol.TOKEN_URL)
                    .post(encoded.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                    .build(),
            ).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Vertex OAuth request failed with HTTP ${response.code}")
                }
                body
            }
        }
        val json = JSONObject(responseBody)
        return VertexOAuthTokenResponse(
            accessToken = json.optString("access_token", "").takeIf { it.isNotBlank() },
            refreshToken = json.optString("refresh_token", "").takeIf { it.isNotBlank() },
            expiresInSeconds = json.optLong("expires_in", -1L).takeIf { it > 0L },
        )
    }
}