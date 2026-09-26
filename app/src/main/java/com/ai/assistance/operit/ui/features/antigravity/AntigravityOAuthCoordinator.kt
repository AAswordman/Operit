package com.ai.assistance.operit.ui.features.antigravity

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.data.api.AntigravityAuthManager
import com.ai.assistance.operit.data.api.AntigravityOAuthClient
import com.ai.assistance.operit.data.api.AntigravityOAuthProtocol
import com.ai.assistance.operit.data.api.AntigravityPkceCodes
import com.ai.assistance.operit.data.preferences.AntigravityAuthState
import java.io.IOException

internal data class AntigravityOAuthLoginSession(
    internal val callbackServer: AntigravityOAuthLoopbackCallbackServer,
    internal val pkce: AntigravityPkceCodes,
    internal val state: String,
    val authorizationUrl: String,
    val expiresAt: Long,
) {
    val redirectUri: String
        get() = callbackServer.redirectUri
}

internal class AntigravityOAuthCoordinator(context: Context) {
    private val authManager = AntigravityAuthManager.getInstance(context)
    private val oauthClient = AntigravityOAuthClient(
        client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build(),
    )

    suspend fun startLogin(): AntigravityOAuthLoginSession {
        val callbackServer = AntigravityOAuthLoopbackCallbackServer.open()
        val pkce = AntigravityOAuthProtocol.generatePkce()
        val state = AntigravityOAuthProtocol.generateState()
        val expiresAt = System.currentTimeMillis() + AntigravityOAuthProtocol.OAUTH_TIMEOUT_MILLIS
        return AntigravityOAuthLoginSession(
            callbackServer = callbackServer,
            pkce = pkce,
            state = state,
            authorizationUrl = AntigravityOAuthProtocol.buildAuthorizationUrl(
                redirectUri = callbackServer.redirectUri,
                pkce = pkce,
                state = state,
            ),
            expiresAt = expiresAt,
        )
    }

    suspend fun completeLogin(
        session: AntigravityOAuthLoginSession,
        callbackUri: Uri,
    ): AntigravityAuthState {
        val callbackState = callbackUri.getQueryParameter("state")
        if (callbackState != session.state) {
            throw IOException("Antigravity OAuth state does not match")
        }
        val error = callbackUri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val description = callbackUri.getQueryParameter("error_description")
            throw IOException(description ?: error)
        }
        val code = callbackUri.getQueryParameter("code")
            ?: throw IOException("Antigravity OAuth callback has no authorization code")
        val tokens = oauthClient.exchangeAuthorizationCode(
            code = code,
            redirectUri = session.redirectUri,
            verifier = session.pkce.verifier,
        )
        return authManager.saveLoginTokens(tokens)
    }
}