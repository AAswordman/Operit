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

data class AntigravityPkceCodes(
    val verifier: String,
    val challenge: String,
)

data class AntigravityOAuthTokenResponse(
    val accessToken: String?,
    val refreshToken: String?,
    val expiresInSeconds: Long?,
)

object AntigravityOAuthProtocol {
    const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    const val USERINFO_URL = "https://www.googleapis.com/oauth2/v1/userinfo?alt=json"
    const val DEFAULT_ENDPOINT = "https://cloudcode-pa.googleapis.com"
    const val SANDBOX_ENDPOINT = "https://daily-cloudcode-pa.sandbox.googleapis.com"
    const val CALLBACK_PATH = "/oauth-callback"
    const val OAUTH_TIMEOUT_MILLIS = 5 * 60 * 1000L
    const val EXPIRY_SKEW_MILLIS = 5 * 60 * 1000L
    const val USER_AGENT = "Operit"

    val apiEndpoints = listOf(DEFAULT_ENDPOINT, SANDBOX_ENDPOINT)

    val scopes = listOf(
        "https://www.googleapis.com/auth/aicode",
        "https://www.googleapis.com/auth/cclog",
        "https://www.googleapis.com/auth/experimentsandconfigs",
        "https://www.googleapis.com/auth/userinfo.email",
        "https://www.googleapis.com/auth/userinfo.profile",
    )

    // 公开模型名到 Cloud Code Assist 运行时模型名。来源是 dsh-antigravity 的静态路由。
    private val runtimeModelIds = mapOf(
        "gemini-3.7-flash" to "gemini-3.7-flash-tiered",
        "gemini-3.6-flash" to "gemini-3.6-flash",
        "gemini-3.5-flash" to "gemini-3.5-flash",
        "gemini-3.1-pro" to "gemini-3.1-pro",
        "gemini-3.1-flash-image" to "gemini-3.1-flash-image",
        "gemini-3-flash" to "gemini-3-flash",
        "gemini-2.5-pro" to "gemini-2.5-pro",
        "gemini-2.5-flash" to "gemini-2.5-flash",
        "claude-opus-4-6" to "claude-opus-4-6-thinking",
        "claude-sonnet-4-6" to "claude-sonnet-4-6-thinking",
        "gpt-oss-120b" to "gpt-oss-120b",
    )

    val defaultModels = listOf(
        "gemini-3.7-flash" to "Gemini 3.7 Flash",
        "gemini-3.6-flash" to "Gemini 3.6 Flash",
        "gemini-3.5-flash" to "Gemini 3.5 Flash",
        "gemini-3.1-pro" to "Gemini 3.1 Pro",
        "gemini-3.1-flash-image" to "Gemini 3.1 Flash Image",
        "gemini-3-flash" to "Gemini 3 Flash",
        "gemini-2.5-pro" to "Gemini 2.5 Pro",
        "gemini-2.5-flash" to "Gemini 2.5 Flash",
        "claude-opus-4-6" to "Claude Opus 4.6",
        "claude-sonnet-4-6" to "Claude Sonnet 4.6",
        "gpt-oss-120b" to "GPT-OSS 120B",
    )

    private val clientId = reversed("moc.tnetnocresuelgoog.sppa.pe304g4hjolotv532ercl12h2nisshmt-1950606001701")

    fun generatePkce(random: SecureRandom = SecureRandom()): AntigravityPkceCodes {
        val verifier = encodeBase64Url(randomBytes(32, random))
        val challenge = encodeBase64Url(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)),
        )
        return AntigravityPkceCodes(verifier = verifier, challenge = challenge)
    }

    fun generateState(random: SecureRandom = SecureRandom()): String {
        return encodeBase64Url(randomBytes(32, random))
    }

    fun buildAuthorizationUrl(
        redirectUri: String,
        pkce: AntigravityPkceCodes,
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

    fun runtimeModelId(publicModelId: String): String {
        val normalized = publicModelId.trim()
        return runtimeModelIds[normalized] ?: normalized
    }

    fun clientMetadata(): String {
        return JSONObject()
            .put("ideType", "ANTIGRAVITY")
            .put("platform", "LINUX")
            .put("pluginType", "GEMINI")
            .toString()
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

class AntigravityOAuthClient(
    private val client: OkHttpClient,
) {
    suspend fun exchangeAuthorizationCode(
        code: String,
        redirectUri: String,
        verifier: String,
    ): AntigravityOAuthTokenResponse {
        return executeTokenRequest(
            AntigravityOAuthProtocol.authorizationCodeBody(
                code = code,
                redirectUri = redirectUri,
                verifier = verifier,
            ),
        ).requireRefreshToken()
    }

    suspend fun refreshAccessToken(refreshToken: String): AntigravityOAuthTokenResponse {
        return executeTokenRequest(AntigravityOAuthProtocol.refreshTokenBody(refreshToken))
    }

    private fun AntigravityOAuthTokenResponse.requireRefreshToken(): AntigravityOAuthTokenResponse {
        if (accessToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
            throw IOException("Antigravity OAuth response has no refresh token")
        }
        return this
    }

    private suspend fun executeTokenRequest(
        fields: List<Pair<String, String>>,
    ): AntigravityOAuthTokenResponse {
        val body = fields.toFormBody()
        val responseBody = executeRequest(
            Request.Builder()
                .url(AntigravityOAuthProtocol.TOKEN_URL)
                .post(body)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build(),
        )
        val json = JSONObject(responseBody)
        return AntigravityOAuthTokenResponse(
            accessToken = json.optString("access_token", "").takeIf { it.isNotBlank() },
            refreshToken = json.optString("refresh_token", "").takeIf { it.isNotBlank() },
            expiresInSeconds = json.optLong("expires_in", -1L).takeIf { it > 0L },
        )
    }

    private suspend fun executeRequest(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(
                    "Antigravity OAuth request failed with HTTP ${response.code}: " +
                        oauthErrorMessage(body),
                )
            }
            body
        }
    }

    private fun oauthErrorMessage(body: String): String {
        if (body.isBlank()) return "empty response"
        return try {
            val json = JSONObject(body)
            json.optString("error_description", "")
                .ifBlank { json.optString("error", "") }
                .ifBlank { "invalid response" }
        } catch (_: Exception) {
            "invalid response"
        }
    }

    private fun List<Pair<String, String>>.toFormBody(): okhttp3.RequestBody {
        val encoded = joinToString("&") { (key, value) ->
            java.net.URLEncoder.encode(key, StandardCharsets.UTF_8.name()) + "=" +
                java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name())
        }
        return encoded.toRequestBody("application/x-www-form-urlencoded".toMediaType())
    }
}
