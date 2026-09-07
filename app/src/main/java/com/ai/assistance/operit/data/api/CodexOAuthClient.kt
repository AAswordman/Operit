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
import org.json.JSONArray
import org.json.JSONObject

data class CodexPkceCodes(
    val verifier: String,
    val challenge: String,
)

data class CodexJwtClaims(
    val accountId: String?,
    val residency: String?,
    val email: String?,
    val expiresAtMillis: Long?,
)

data class CodexOAuthTokenResponse(
    val idToken: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val expiresInSeconds: Long?,
) {
    fun requireComplete(): CodexOAuthTokenResponse {
        require(!idToken.isNullOrBlank()) { "Codex OAuth response has no ID token" }
        require(!accessToken.isNullOrBlank()) { "Codex OAuth response has no access token" }
        require(!refreshToken.isNullOrBlank()) { "Codex OAuth response has no refresh token" }
        return this
    }
}

object CodexOAuthProtocol {
    const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    const val ISSUER = "https://auth.openai.com"
    const val CODEX_RESPONSES_ENDPOINT = "https://chatgpt.com/backend-api/codex/responses"
    const val CODEX_USAGE_ENDPOINT = "https://chatgpt.com/backend-api/wham/usage"
    const val MODEL_CATALOG_ENDPOINT = "https://models.opencode.ai/api.json"
    const val PRIMARY_CALLBACK_PORT = 1455
    const val CALLBACK_PATH = "/auth/callback"
    const val OAUTH_TIMEOUT_MILLIS = 5 * 60 * 1000L

    fun generatePkce(random: SecureRandom = SecureRandom()): CodexPkceCodes {
        val verifierBytes = ByteArray(64)
        random.nextBytes(verifierBytes)
        val verifier = encodeBase64Url(verifierBytes)
        val challenge = encodeBase64Url(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        )
        return CodexPkceCodes(verifier = verifier, challenge = challenge)
    }

    fun generateState(random: SecureRandom = SecureRandom()): String {
        val stateBytes = ByteArray(32)
        random.nextBytes(stateBytes)
        return encodeBase64Url(stateBytes)
    }

    fun buildAuthorizationUrl(
        redirectUri: String,
        pkce: CodexPkceCodes,
        state: String,
    ): String {
        return Uri.parse("$ISSUER/oauth/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter(
                "scope",
                "openid profile email offline_access",
            )
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("id_token_add_organizations", "true")
            .appendQueryParameter("codex_cli_simplified_flow", "true")
            .appendQueryParameter("state", state)
            .appendQueryParameter("originator", "operit")
            .build()
            .toString()
    }

    fun parseJwtClaims(token: String): CodexJwtClaims? {
        val parts = token.split('.')
        if (parts.size != 3 || parts[1].isBlank()) {
            return null
        }

        val payload = try {
            Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (_: IllegalArgumentException) {
            return null
        }
        val json = try {
            JSONObject(String(payload, StandardCharsets.UTF_8))
        } catch (_: Exception) {
            return null
        }

        val authClaims = json.optJSONObject("https://api.openai.com/auth")
        val accountId = firstNonBlank(
            json.optString("chatgpt_account_id", ""),
            authClaims?.optString("chatgpt_account_id", "").orEmpty(),
            firstOrganizationId(json.optJSONArray("organizations")),
        )
        val residency = firstNonBlank(
            json.optString("chatgpt_compute_residency", ""),
            authClaims?.optString("chatgpt_compute_residency", "").orEmpty(),
        )?.takeUnless { it == "no_constraint" }
        val email = firstNonBlank(
            json.optString("email", ""),
            json.optJSONObject("https://api.openai.com/profile")?.optString("email", "").orEmpty(),
        )
        val expiresAtMillis = json.optLong("exp", -1L)
            .takeIf { it > 0L }
            ?.times(1000L)

        return CodexJwtClaims(
            accountId = accountId,
            residency = residency,
            email = email,
            expiresAtMillis = expiresAtMillis,
        )
    }

    private fun firstOrganizationId(organizations: JSONArray?): String {
        return organizations?.optJSONObject(0)?.optString("id", "").orEmpty()
    }

    private fun firstNonBlank(vararg values: String): String? {
        return values.firstOrNull { it.isNotBlank() }?.trim()
    }

    private fun encodeBase64Url(bytes: ByteArray): String {
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
    }
}

class CodexOAuthClient(
    private val client: OkHttpClient,
    private val issuer: String = CodexOAuthProtocol.ISSUER,
) {
    suspend fun exchangeAuthorizationCode(
        code: String,
        redirectUri: String,
        verifier: String,
    ): CodexOAuthTokenResponse {
        val body = listOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "client_id" to CodexOAuthProtocol.CLIENT_ID,
            "code_verifier" to verifier,
        ).toFormBody()
        return executeTokenRequest(
            Request.Builder()
                .url(endpoint("/oauth/token"))
                .post(body)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
        ).requireComplete()
    }

    suspend fun refreshAccessToken(refreshToken: String): CodexOAuthTokenResponse {
        val body = listOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to CodexOAuthProtocol.CLIENT_ID,
        ).toFormBody()
        return executeTokenRequest(
            Request.Builder()
                .url(endpoint("/oauth/token"))
                .post(body)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
        )
    }

    suspend fun revokeToken(token: String, tokenTypeHint: String) {
        val body = listOf(
            "token" to token,
            "token_type_hint" to tokenTypeHint,
            "client_id" to CodexOAuthProtocol.CLIENT_ID,
        ).toFormBody()
        executeRequest(
            Request.Builder()
                .url(endpoint("/oauth/revoke"))
                .post(body)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
        )
    }

    private suspend fun executeTokenRequest(request: Request): CodexOAuthTokenResponse {
        val responseBody = executeRequest(request)
        val json = JSONObject(responseBody)
        return CodexOAuthTokenResponse(
            idToken = json.optString("id_token", "").takeIf { it.isNotBlank() },
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
                    "Codex OAuth request failed with HTTP ${response.code}: " +
                        oauthErrorMessage(body),
                )
            }
            body
        }
    }

    private fun endpoint(path: String): String = issuer.trimEnd('/') + path

    private fun oauthErrorMessage(body: String): String {
        if (body.isBlank()) {
            return "empty response"
        }
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
