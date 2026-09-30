package com.ai.assistance.operit.plugins.toolpkg

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ai.assistance.operit.core.auth.AndroidProviderOAuthStore
import com.ai.assistance.operit.core.auth.ProviderOAuthAttempt
import com.ai.assistance.operit.core.auth.ProviderOAuthConfig
import com.ai.assistance.operit.core.auth.ProviderOAuthHttpClient
import com.ai.assistance.operit.core.auth.ProviderOAuthLoopbackServer
import com.ai.assistance.operit.core.auth.ProviderOAuthSessions
import com.ai.assistance.operit.core.auth.ProviderOAuthStatus
import com.ai.assistance.operit.data.model.ModelConfigData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Host-owned OAuth lifecycle; plugins only receive an access token during a request. */
internal class ToolPkgProviderOAuth private constructor(context: Context) {
    private val endpoint = ProviderOAuthHttpClient()
    private val sessions = ProviderOAuthSessions(AndroidProviderOAuthStore(context), endpoint)
    val changes get() = sessions.changes

    private fun activeAuth(provider: ToolPkgAiProviderRegistration): ProviderOAuthConfig {
        val active = ToolPkgAiProviderRegistry.get(provider.providerId)
        check(active != null && active.containerPackageName == provider.containerPackageName && active.auth == provider.auth) {
            "OAuth provider is no longer enabled or its authentication configuration changed"
        }
        return requireNotNull(provider.auth) { "Provider does not use OAuth" }
    }

    fun credentialKey(provider: ToolPkgAiProviderRegistration, config: ModelConfigData): String =
        activeAuth(provider).credentialKey(provider.containerPackageName, provider.providerId, config.id, config.apiEndpoint)

    suspend fun status(provider: ToolPkgAiProviderRegistration, config: ModelConfigData): ProviderOAuthStatus =
        withContext(Dispatchers.IO) { sessions.status(credentialKey(provider, config)) }

    suspend fun accessToken(provider: ToolPkgAiProviderRegistration, config: ModelConfigData): String =
        withContext(Dispatchers.IO) {
            sessions.accessToken(credentialKey(provider, config), activeAuth(provider))
        }

    suspend fun refresh(provider: ToolPkgAiProviderRegistration, config: ModelConfigData) {
        withContext(Dispatchers.IO) {
            sessions.accessToken(credentialKey(provider, config), activeAuth(provider), forceRefresh = true)
        }
    }

    suspend fun logout(provider: ToolPkgAiProviderRegistration, config: ModelConfigData) =
        withContext(Dispatchers.IO) { sessions.logout(credentialKey(provider, config)) }

    suspend fun login(context: Context, provider: ToolPkgAiProviderRegistration, config: ModelConfigData) {
        val auth = activeAuth(provider)
        val key = credentialKey(provider, config)
        withTimeout(ProviderOAuthAttempt.TIMEOUT_MILLIS) {
            withContext(Dispatchers.IO) {
                ProviderOAuthLoopbackServer.open(auth).use { listener ->
                    val ticket = sessions.beginLogin(key)
                    try {
                        val attempt = ProviderOAuthAttempt(auth, listener.redirectUri, System.currentTimeMillis())
                        withContext(Dispatchers.Main) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(attempt.authorizationUrl))
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                        val callback = listener.awaitCallback { attempt.accepts(it, System.currentTimeMillis()) }
                        activeAuth(provider)
                        val code = attempt.consume(callback, System.currentTimeMillis())
                        val tokens = endpoint.exchange(auth, code)
                        currentCoroutineContext().ensureActive()
                        activeAuth(provider)
                        sessions.finishLogin(ticket, tokens)
                    } finally {
                        sessions.cancelLogin(ticket)
                    }
                }
            }
        }
    }

    companion object {
        @Volatile private var instance: ToolPkgProviderOAuth? = null
        fun getInstance(context: Context): ToolPkgProviderOAuth = instance ?: synchronized(this) {
            instance ?: ToolPkgProviderOAuth(context.applicationContext).also { instance = it }
        }
    }
}
