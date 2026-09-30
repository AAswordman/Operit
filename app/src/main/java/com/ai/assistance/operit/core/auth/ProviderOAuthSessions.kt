package com.ai.assistance.operit.core.auth

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Never use data-class toString/copy for credentials: exception/log messages must not print tokens. */
internal class ProviderOAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long?,
) {
    init {
        require(accessToken.isNotBlank() && !accessToken.hasControlCharacters() && accessToken.length <= 65536)
        require(refreshToken == null || (refreshToken.isNotBlank() && !refreshToken.hasControlCharacters() && refreshToken.length <= 65536))
        require(expiresAtMillis == null || expiresAtMillis > 0)
    }
    override fun toString(): String = "ProviderOAuthTokens([redacted])"
}

internal interface ProviderOAuthCredentialStore {
    fun load(key: String): ProviderOAuthTokens?
    fun save(key: String, tokens: ProviderOAuthTokens)
    fun clear(key: String)
}

internal interface ProviderOAuthTokenEndpoint {
    suspend fun exchange(config: ProviderOAuthConfig, code: OAuthAuthorizationCode): ProviderOAuthTokens
    suspend fun refresh(config: ProviderOAuthConfig, previous: ProviderOAuthTokens): ProviderOAuthTokens
}

internal data class ProviderOAuthStatus(
    val signedIn: Boolean,
    val canRefresh: Boolean,
    val expiresAtMillis: Long?,
)

internal class ProviderOAuthLoginTicket internal constructor(internal val key: String, internal val generation: Long) {
    override fun toString(): String = "ProviderOAuthLoginTicket([redacted])"
}

/** Single-flight refresh per credential, without holding the logout lock during network I/O. */
internal class ProviderOAuthSessions(
    private val store: ProviderOAuthCredentialStore,
    private val endpoint: ProviderOAuthTokenEndpoint,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Slot {
        val refreshMutex = Mutex()
        var generation = 0L
    }
    private val slots = ConcurrentHashMap<String, Slot>()
    private val changeLock = Any()
    private val mutableChanges = MutableStateFlow(0L)
    val changes: StateFlow<Long> = mutableChanges.asStateFlow()

    private fun changed() = synchronized(changeLock) { mutableChanges.value += 1L }
    private fun slot(key: String): Slot = slots.computeIfAbsent(key) { Slot() }

    fun status(key: String): ProviderOAuthStatus = synchronized(slot(key)) {
        val tokens = store.load(key)
        ProviderOAuthStatus(
            signedIn = tokens != null && (tokens.expiresAtMillis == null || tokens.expiresAtMillis > clock() || tokens.refreshToken != null),
            canRefresh = tokens?.refreshToken != null,
            expiresAtMillis = tokens?.expiresAtMillis,
        )
    }

    fun beginLogin(key: String): ProviderOAuthLoginTicket {
        val slot = slot(key)
        return synchronized(slot) { ProviderOAuthLoginTicket(key, ++slot.generation) }
    }

    fun finishLogin(ticket: ProviderOAuthLoginTicket, tokens: ProviderOAuthTokens) {
        val slot = slot(ticket.key)
        synchronized(slot) {
            if (slot.generation != ticket.generation) throw OAuthSessionChanged()
            store.save(ticket.key, tokens)
            slot.generation++
        }
        changed()
    }

    fun cancelLogin(ticket: ProviderOAuthLoginTicket) {
        val slot = slot(ticket.key)
        synchronized(slot) { if (slot.generation == ticket.generation) slot.generation++ }
    }

    fun logout(key: String) {
        val slot = slot(key)
        synchronized(slot) {
            slot.generation++
            store.clear(key)
        }
        changed()
    }

    suspend fun accessToken(key: String, config: ProviderOAuthConfig, forceRefresh: Boolean = false): String {
        val slot = slot(key)
        return slot.refreshMutex.withLock {
            val (generation, previous) = synchronized(slot) {
                slot.generation to (store.load(key) ?: throw OAuthLoginRequired())
            }
            val now = clock()
            val expires = previous.expiresAtMillis
            if (!forceRefresh && (expires == null || expires - now > REFRESH_SKEW_MILLIS ||
                    (previous.refreshToken == null && expires > now))) {
                return@withLock previous.accessToken
            }
            if (previous.refreshToken == null) {
                if (expires != null && expires <= now) {
                    synchronized(slot) {
                        if (slot.generation == generation) {
                            store.clear(key)
                            changed()
                        }
                    }
                }
                throw OAuthLoginRequired()
            }
            val refreshed = try {
                endpoint.refresh(config, previous)
            } catch (error: OAuthInvalidGrant) {
                synchronized(slot) {
                    if (slot.generation == generation) {
                        store.clear(key)
                        changed()
                    }
                }
                throw OAuthLoginRequired()
            }
            currentCoroutineContext().ensureActive()
            synchronized(slot) {
                if (slot.generation != generation) throw OAuthSessionChanged()
                // Refresh rotates credentials within an identity generation. It must not cancel
                // a browser login that started while the old account was still in use.
                store.save(key, refreshed)
            }
            changed()
            refreshed.accessToken
        }
    }

    companion object { const val REFRESH_SKEW_MILLIS = 60_000L }
}
