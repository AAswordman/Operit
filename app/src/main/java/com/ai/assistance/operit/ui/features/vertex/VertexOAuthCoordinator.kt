package com.ai.assistance.operit.ui.features.vertex

import android.content.Context
import android.net.Uri
import com.ai.assistance.operit.data.api.VertexAuthManager
import com.ai.assistance.operit.data.api.VertexOAuthClient
import com.ai.assistance.operit.data.api.VertexOAuthProtocol
import com.ai.assistance.operit.data.api.VertexPkceCodes
import com.ai.assistance.operit.data.preferences.VertexAuthState
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class VertexOAuthLoginSession(
    internal val callbackServer: VertexOAuthLoopbackCallbackServer,
    internal val pkce: VertexPkceCodes,
    internal val state: String,
    val authorizationUrl: String,
    val expiresAt: Long,
) {
    val redirectUri: String get() = callbackServer.redirectUri
}

internal class VertexOAuthCoordinator(context: Context) {
    private val authManager = VertexAuthManager.getInstance(context)
    private val oauthClient = VertexOAuthClient(
        client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build(),
    )

    suspend fun startLogin(): VertexOAuthLoginSession {
        val callbackServer = VertexOAuthLoopbackCallbackServer.open()
        val pkce = VertexOAuthProtocol.generatePkce()
        val state = VertexOAuthProtocol.generateState()
        return VertexOAuthLoginSession(
            callbackServer = callbackServer,
            pkce = pkce,
            state = state,
            authorizationUrl = VertexOAuthProtocol.buildAuthorizationUrl(callbackServer.redirectUri, pkce, state),
            expiresAt = System.currentTimeMillis() + VertexOAuthProtocol.OAUTH_TIMEOUT_MILLIS,
        )
    }

    suspend fun completeLogin(session: VertexOAuthLoginSession, callbackUri: Uri): VertexAuthState {
        if (callbackUri.getQueryParameter("state") != session.state) {
            throw IOException("Vertex OAuth state does not match")
        }
        val error = callbackUri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            throw IOException(callbackUri.getQueryParameter("error_description") ?: error)
        }
        val code = callbackUri.getQueryParameter("code")
            ?: throw IOException("Vertex OAuth callback has no authorization code")
        return authManager.saveLoginTokens(
            oauthClient.exchangeAuthorizationCode(code, session.redirectUri, session.pkce.verifier),
        )
    }
}