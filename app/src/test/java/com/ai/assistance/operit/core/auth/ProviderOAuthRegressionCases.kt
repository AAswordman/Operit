package com.ai.assistance.operit.core.auth

import java.io.IOException
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** The same real implementation tests run under JUnit and the SDK-free command-line runner. */
internal object ProviderOAuthRegressionCases {
    class Case(val name: String, val run: suspend () -> Unit) {
        override fun toString() = name
    }
    private val config = ProviderOAuthConfig("public-client", "https://identity.example/authorize", "https://identity.example/token")
    private const val now = 1_000_000L
    private const val redirect = "http://127.0.0.1:18765/oauth/callback"
    private val state = "s".repeat(43)
    private val verifier = "v".repeat(43)
    private fun attempt(c: ProviderOAuthConfig = config) = ProviderOAuthAttempt(c, redirect, now, state, verifier)
    private fun callback(code: String = "code", issuer: String? = null) =
        "$redirect?code=${formEncode(code)}&state=$state" + (issuer?.let { "&iss=${formEncode(it)}" } ?: "")
    private fun equal(expected: Any?, actual: Any?) {
        if (expected != actual) throw AssertionError("Expected $expected, got $actual")
    }
    private fun truth(value: Boolean) { if (!value) throw AssertionError("Condition was false") }
    private inline fun <reified T : Throwable> fails(action: () -> Unit): T {
        try { action() } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("Expected ${T::class.simpleName}, got ${error::class.simpleName}", error)
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }
    private suspend inline fun <reified T : Throwable> failsSuspend(crossinline action: suspend () -> Unit): T {
        try { action() } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("Expected ${T::class.simpleName}, got ${error::class.simpleName}", error)
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }
    private class MemoryStore : ProviderOAuthCredentialStore {
        val values = ConcurrentHashMap<String, ProviderOAuthTokens>()
        var saves = 0
        override fun load(key: String) = values[key]
        override fun save(key: String, tokens: ProviderOAuthTokens) { values[key] = tokens; saves++ }
        override fun clear(key: String) { values.remove(key) }
    }
    private class Endpoint(val onRefresh: suspend (ProviderOAuthTokens) -> ProviderOAuthTokens) : ProviderOAuthTokenEndpoint {
        val calls = AtomicInteger()
        override suspend fun exchange(config: ProviderOAuthConfig, code: OAuthAuthorizationCode): ProviderOAuthTokens =
            error("Authorization code exchange is outside session tests")
        override suspend fun refresh(config: ProviderOAuthConfig, previous: ProviderOAuthTokens): ProviderOAuthTokens {
            calls.incrementAndGet()
            return onRefresh(previous)
        }
    }
    private fun expired() = ProviderOAuthTokens("old-access", "old-refresh", now - 1)
    private fun fresh() = ProviderOAuthTokens("new-access", "new-refresh", now + 3_600_000)

    val cases = listOf(
        Case("PKCE S256 matches RFC 7636 Appendix B") {
            equal("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        },
        Case("state and verifier use independent 256-bit URL-safe random values") {
            val values = (1..100).map { randomOAuthValue() }
            equal(100, values.toSet().size)
            truth(values.all { it.matches(Regex("[A-Za-z0-9_-]{43}")) })
        },
        Case("authorization parameters are encoded, with mandatory code flow and PKCE") {
            val c = config.copy(clientId = "client&redirect_uri=evil", scopes = listOf("profile", "offline_access"),
                authorizationParameters = mapOf("audience" to "https://api.example/?a=1&b=2"))
            val query = parseOAuthQuery(URI(attempt(c).authorizationUrl).rawQuery)
            equal("code", query["response_type"])
            equal("S256", query["code_challenge_method"])
            equal(pkceChallenge(verifier), query["code_challenge"])
            equal(c.clientId, query["client_id"])
            equal(redirect, query["redirect_uri"])
            equal("profile offline_access", query["scope"])
            equal(c.authorizationParameters["audience"], query["audience"])
            truth(!query.containsKey("code_verifier"))
        },
        Case("insecure or ambiguous OAuth endpoints are rejected") {
            listOf("http://identity.example/auth", "https://u:p@identity.example/auth", "https://identity.example/auth#x",
                "https://identity.example/auth?state=x", "https://identity.example:0/auth", "https://identity.example:65536/auth",
                "https://identity.example\\evil/auth", "https://identity.example/\n").forEach { value ->
                fails<IllegalArgumentException> { config.copy(authorizationEndpoint = value) }
                fails<IllegalArgumentException> { config.copy(tokenEndpoint = value) }
            }
        },
        Case("reserved parameters cannot override state, PKCE, grant, redirects or credentials") {
            listOf("state", "STATE", "client_id", "client_secret", "response_type", "response_mode", "redirect_uri", "scope", "code",
                "code_challenge", "code_challenge_method", "code_verifier", "request", "request_uri", "access_token",
                "refresh_token", "id_token", "grant_type", "authorization").forEach { key ->
                fails<IllegalArgumentException> { config.copy(authorizationParameters = mapOf(key to "override")) }
            }
        },
        Case("invalid ports, callback paths, scopes and oversized parameters are rejected") {
            listOf(-1, 65536).forEach { fails<IllegalArgumentException> { config.copy(redirectPort = it) } }
            listOf("callback", "/../callback", "/x?x", "//evil", "/x%2fy", "/" + "x".repeat(256)).forEach {
                fails<IllegalArgumentException> { config.copy(redirectPath = it) }
            }
            listOf("", "has space", "has\nnewline", "has\\slash", "\"quoted\"").forEach {
                fails<IllegalArgumentException> { config.copy(scopes = listOf(it)) }
            }
            fails<IllegalArgumentException> { config.copy(authorizationParameters = mapOf("audience" to "x".repeat(2049))) }
        },
        Case("registration codec preserves valid metadata and defaults") {
            val raw = mapOf<String, Any?>("type" to "oauth2", "clientId" to config.clientId,
                "authorizationEndpoint" to config.authorizationEndpoint, "tokenEndpoint" to config.tokenEndpoint)
            equal(config, parseProviderOAuthConfig(raw))
            val complete = parseProviderOAuthConfig(raw + mapOf("scopes" to listOf("profile"),
                "authorizationParameters" to mapOf("audience" to "api"), "redirectPort" to 1234,
                "redirectPath" to "/callback", "issuer" to "https://identity.example"))
            equal(listOf("profile"), complete.scopes)
            equal(mapOf("audience" to "api"), complete.authorizationParameters)
            equal(1234, complete.redirectPort)
            equal("/callback", complete.redirectPath)
            equal("https://identity.example", complete.issuer)
        },
        Case("registration codec rejects unknown fields including client secrets and custom login handlers") {
            val raw = mapOf<String, Any?>("type" to "oauth2", "clientId" to config.clientId,
                "authorizationEndpoint" to config.authorizationEndpoint, "tokenEndpoint" to config.tokenEndpoint)
            listOf("clientSecret", "client_secret", "login", "refresh", "logout", "typo").forEach {
                fails<IllegalArgumentException> { parseProviderOAuthConfig(raw + (it to "untrusted")) }
            }
        },
        Case("registration codec rejects missing, null or incorrectly typed required metadata") {
            val raw = mapOf<String, Any?>("type" to "oauth2", "clientId" to config.clientId,
                "authorizationEndpoint" to config.authorizationEndpoint, "tokenEndpoint" to config.tokenEndpoint)
            raw.keys.forEach { key ->
                fails<IllegalArgumentException> { parseProviderOAuthConfig(raw - key) }
                fails<IllegalArgumentException> { parseProviderOAuthConfig(raw + (key to null)) }
                fails<IllegalArgumentException> { parseProviderOAuthConfig(raw + (key to 1)) }
            }
            fails<IllegalArgumentException> { parseProviderOAuthConfig(raw + ("type" to "implicit")) }
        },
        Case("registration codec does not coerce scopes, parameters or loopback ports") {
            val raw = mapOf<String, Any?>("type" to "oauth2", "clientId" to config.clientId,
                "authorizationEndpoint" to config.authorizationEndpoint, "tokenEndpoint" to config.tokenEndpoint)
            listOf("profile", listOf(42), listOf(null), null).forEach {
                fails<IllegalArgumentException> { parseProviderOAuthConfig(raw + ("scopes" to it)) }
            }
            listOf(mapOf("x" to 1), mapOf(1 to "x"), listOf("x"), null).forEach {
                fails<IllegalArgumentException> { parseProviderOAuthConfig(raw + ("authorizationParameters" to it)) }
            }
            listOf("1234", 1234.5, true, null).forEach {
                fails<IllegalArgumentException> { parseProviderOAuthConfig(raw + ("redirectPort" to it)) }
            }
        },
        Case("callback listener must be an exact host-owned IPv4 loopback destination") {
            listOf("http://localhost:18765/oauth/callback", "https://127.0.0.1:18765/oauth/callback",
                "http://0.0.0.0:18765/oauth/callback", "http://127.0.0.1:18765/wrong", "$redirect?q=1", "$redirect#x").forEach {
                fails<IllegalArgumentException> { ProviderOAuthAttempt(config, it, now) }
            }
            fails<IllegalArgumentException> { attempt(config.copy(redirectPort = 18766)) }
        },
        Case("explicit localhost compatibility never permits arbitrary redirect hosts") {
            val c = config.copy(redirectHost = "localhost")
            val destination = redirect.replace("127.0.0.1", "localhost")
            val a = ProviderOAuthAttempt(c, destination, now, state, verifier)
            equal("code", a.consume(callback().replace("127.0.0.1", "localhost"), now).code)
            listOf("localhost.evil.example", "0.0.0.0", "192.168.0.1", "127.0.0.2", "::1").forEach {
                fails<IllegalArgumentException> { config.copy(redirectHost = it) }
            }
            truth(config.credentialKey("p", "v", "id", "https://api.example") !=
                c.credentialKey("p", "v", "id", "https://api.example"))
        },
        Case("wrong state and callback destination do not consume the pending attempt") {
            val a = attempt()
            val valid = callback()
            listOf(valid.replace(state, "wrong-state"), valid.replace("127.0.0.1", "evil.example"),
                valid.replace("18765", "18766"), valid.replace("/oauth/callback", "/oauth/%63allback"),
                valid.replace("http://", "https://"), valid + "#fragment", valid.replace("127.0.0.1", "user@127.0.0.1")
            ).forEach {
                truth(!a.accepts(it, now + 1))
                fails<IllegalArgumentException> { a.consume(it, now + 1) }
            }
            equal("code", a.consume(valid, now + 2).code)
        },
        Case("duplicate encoded state, code and issuer parameters are rejected") {
            listOf("&state=$state", "&%73tate=$state", "&code=other", "&iss=a&iss=b").forEach {
                val a = attempt()
                truth(!a.accepts(callback() + it, now))
                fails<IllegalArgumentException> { a.consume(callback() + it, now) }
            }
        },
        Case("missing, blank, ambiguous and control-character callback results are rejected") {
            listOf("state=$state", "code=&state=$state", "code=x&error=access_denied&state=$state",
                "code=x%0Asecret&state=$state", "error=&state=$state", "code=x&state=%ZZ").forEach {
                val a = attempt()
                truth(!a.accepts("$redirect?$it", now))
            }
        },
        Case("issuer identity is verified when declared") {
            val a = attempt(config.copy(issuer = "https://identity.example"))
            truth(!a.accepts(callback(), now))
            truth(!a.accepts(callback(issuer = "https://evil.example"), now))
            equal("code", a.consume(callback(issuer = "https://identity.example"), now).code)
        },
        Case("authorization attempts expire and reject clock rollback") {
            truth(!attempt().accepts(callback(), now - 1))
            truth(!attempt().accepts(callback(), now + ProviderOAuthAttempt.TIMEOUT_MILLIS))
            equal("code", attempt().consume(callback(), now + ProviderOAuthAttempt.TIMEOUT_MILLIS - 1).code)
        },
        Case("a verified callback is single-use even when the code is different") {
            val a = attempt()
            equal("first", a.consume(callback("first"), now).code)
            truth(!a.accepts(callback("second"), now))
            fails<IllegalStateException> { a.consume(callback("second"), now) }
        },
        Case("authorization denial is sanitized and consumes the attempt") {
            val a = attempt()
            val denied = "$redirect?error=access_denied&error_description=secret-from-provider&state=$state"
            truth(a.accepts(denied, now))
            val error = fails<OAuthAuthorizationDenied> { a.consume(denied, now) }
            truth(!error.toString().contains("secret-from-provider"))
            truth(!a.accepts(callback(), now))
        },
        Case("credential namespaces bind every identity and auth setting") {
            fun key(c: ProviderOAuthConfig = config, p: String = "package", v: String = "provider", id: String = "model", ep: String = "https://api.example/v1") = c.credentialKey(p, v, id, ep)
            val base = key()
            val keys = listOf(key(p = "other"), key(v = "other"), key(id = "other"), key(ep = "https://other.example/v1"),
                key(config.copy(clientId = "other")), key(config.copy(authorizationEndpoint = "https://other.example/authorize")),
                key(config.copy(tokenEndpoint = "https://other.example/token")), key(config.copy(scopes = listOf("profile"))),
                key(config.copy(redirectPort = 1234)), key(config.copy(redirectPath = "/other")),
                key(config.copy(issuer = "https://identity.example")), key(config.copy(authorizationParameters = mapOf("audience" to "x"))))
            truth(keys.all { it != base && it.matches(Regex("[a-f0-9]{64}")) })
            equal(keys.size, keys.toSet().size)
            fails<IllegalArgumentException> { key(ep = "http://api.example/v1") }
        },
        Case("namespace framing is unambiguous and metadata ordering is stable") {
            val c1 = config.copy(scopes = listOf("a", "b", "a"), authorizationParameters = linkedMapOf("x" to "1", "y" to "2"))
            val c2 = c1.copy(scopes = listOf("b", "a"), authorizationParameters = linkedMapOf("y" to "2", "x" to "1"))
            equal(c1.credentialKey("ab", "c", "id", "https://api.example"), c2.credentialKey("ab", "c", "id", "https://api.example"))
            truth(c1.credentialKey("ab", "c", "id", "https://api.example") != c1.credentialKey("a", "bc", "id", "https://api.example"))
        },
        Case("token parsing preserves omitted refresh token and accepts token rotation") {
            val old = expired()
            val omitted = parseProviderOAuthTokens(mapOf("access_token" to "new", "token_type" to "bearer", "expires_in" to "3600"), now, old)
            equal("old-refresh", omitted.refreshToken)
            equal(now + 3_600_000L, omitted.expiresAtMillis)
            val rotated = parseProviderOAuthTokens(mapOf("access_token" to "new", "refresh_token" to "rotated", "token_type" to "Bearer"), now, old)
            equal("rotated", rotated.refreshToken)
            equal(null, rotated.expiresAtMillis)
        },
        Case("invalid token type, empty credentials, control characters and expiry overflow fail closed") {
            val valid = mapOf<String, Any?>("access_token" to "a", "token_type" to "Bearer")
            listOf(mapOf("token_type" to "DPoP"), mapOf("token_type" to null), mapOf("access_token" to ""),
                mapOf("access_token" to "a\r\nb"), mapOf("refresh_token" to ""), mapOf("refresh_token" to null),
                mapOf("expires_in" to -1), mapOf("expires_in" to 0), mapOf("expires_in" to 0.5),
                mapOf("expires_in" to "NaN"), mapOf("expires_in" to Long.MAX_VALUE)).forEach {
                fails<IllegalArgumentException> { parseProviderOAuthTokens(valid + it, now) }
            }
        },
        Case("credential and authorization object string representations redact secrets") {
            truth(!ProviderOAuthTokens("SECRET", "SECRET", now).toString().contains("SECRET"))
            truth(!attempt().toString().contains(state))
            truth(!attempt().consume(callback("SECRET"), now).toString().contains("SECRET"))
        },
        Case("twenty concurrent requests share one refresh and persist rotated credentials") {
            val store = MemoryStore().apply { save("key", expired()) }
            val endpoint = Endpoint { delay(50); fresh() }
            val sessions = ProviderOAuthSessions(store, endpoint) { now }
            coroutineScope {
                val tokens = (1..20).map { async(Dispatchers.Default) { sessions.accessToken("key", config) } }.awaitAll()
                truth(tokens.all { it == "new-access" })
            }
            equal(1, endpoint.calls.get())
            equal("new-refresh", store.load("key")?.refreshToken)
        },
        Case("cached valid and unknown-expiry credentials cause no network request") {
            val store = MemoryStore()
            val endpoint = Endpoint { throw AssertionError("Unexpected refresh") }
            val sessions = ProviderOAuthSessions(store, endpoint) { now }
            store.save("key", fresh())
            equal("new-access", sessions.accessToken("key", config))
            store.save("key", ProviderOAuthTokens("unknown-expiry", null, null))
            equal("unknown-expiry", sessions.accessToken("key", config))
            store.save("key", ProviderOAuthTokens("short-but-valid", null, now + 10))
            equal("short-but-valid", sessions.accessToken("key", config))
            equal(0, endpoint.calls.get())
        },
        Case("expired non-refreshable credentials require a fresh login and are cleared") {
            val store = MemoryStore().apply { save("key", ProviderOAuthTokens("expired", null, now - 1)) }
            val endpoint = Endpoint { throw AssertionError("Unexpected refresh") }
            val sessions = ProviderOAuthSessions(store, endpoint) { now }
            failsSuspend<OAuthLoginRequired> { sessions.accessToken("key", config) }
            equal(null, store.load("key"))
            equal(false, sessions.status("key").signedIn)
        },
        Case("a concurrent new login survives cleanup of expired non-refreshable credentials") {
            val store = MemoryStore().apply { save("key", ProviderOAuthTokens("expired", null, now - 1)) }
            lateinit var sessions: ProviderOAuthSessions
            sessions = ProviderOAuthSessions(store, Endpoint { fresh() }) {
                sessions.finishLogin(sessions.beginLogin("key"), fresh())
                now
            }
            failsSuspend<OAuthLoginRequired> { sessions.accessToken("key", config) }
            equal("new-access", store.load("key")?.accessToken)
        },
        Case("logout during refresh prevents late token persistence") {
            supervisorScope {
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val store = MemoryStore().apply { save("key", expired()) }
                val sessions = ProviderOAuthSessions(store, Endpoint { started.complete(Unit); release.await(); fresh() }) { now }
                val request = async { sessions.accessToken("key", config) }
                started.await()
                sessions.logout("key")
                release.complete(Unit)
                failsSuspend<OAuthSessionChanged> { request.await() }
                equal(null, store.load("key"))
            }
        },
        Case("invalid_grant clears the old session but not a newer login") {
            supervisorScope {
                val started = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val store = MemoryStore().apply { save("key", expired()) }
                val sessions = ProviderOAuthSessions(store, Endpoint { started.complete(Unit); release.await(); throw OAuthInvalidGrant() }) { now }
                val request = async { sessions.accessToken("key", config) }
                started.await()
                sessions.finishLogin(sessions.beginLogin("key"), fresh())
                release.complete(Unit)
                failsSuspend<OAuthLoginRequired> { request.await() }
                equal("new-access", store.load("key")?.accessToken)
                store.save("old", expired())
                failsSuspend<OAuthLoginRequired> { sessions.accessToken("old", config) }
                equal(null, store.load("old"))
            }
        },
        Case("transient refresh failure retains credentials without automatic replay") {
            val store = MemoryStore().apply { save("key", expired()) }
            val endpoint = Endpoint { throw IOException("network failure") }
            val sessions = ProviderOAuthSessions(store, endpoint) { now }
            failsSuspend<IOException> { sessions.accessToken("key", config) }
            equal("old-refresh", store.load("key")?.refreshToken)
            equal(1, endpoint.calls.get())
        },
        Case("cancelled refresh releases the single-flight lock and does not persist") {
            coroutineScope {
                val started = CompletableDeferred<Unit>()
                val store = MemoryStore().apply { save("key", expired()) }
                val endpoint = Endpoint { started.complete(Unit); delay(60_000); fresh() }
                val sessions = ProviderOAuthSessions(store, endpoint) { now }
                val request = launch { sessions.accessToken("key", config) }
                started.await()
                request.cancelAndJoin()
                equal("old-access", store.load("key")?.accessToken)
                val ticket = sessions.beginLogin("key")
                sessions.finishLogin(ticket, fresh())
                equal("new-access", withTimeout(1000) { sessions.accessToken("key", config) })
            }
        },
        Case("login completion is single-use, cancellable and invalidated by logout or another login") {
            val store = MemoryStore()
            val sessions = ProviderOAuthSessions(store, Endpoint { fresh() }) { now }
            val cancelled = sessions.beginLogin("key")
            sessions.cancelLogin(cancelled)
            fails<OAuthSessionChanged> { sessions.finishLogin(cancelled, fresh()) }
            val loggedOut = sessions.beginLogin("key")
            sessions.logout("key")
            fails<OAuthSessionChanged> { sessions.finishLogin(loggedOut, fresh()) }
            val old = sessions.beginLogin("key")
            val current = sessions.beginLogin("key")
            fails<OAuthSessionChanged> { sessions.finishLogin(old, fresh()) }
            sessions.cancelLogin(old)
            sessions.finishLogin(current, fresh())
            fails<OAuthSessionChanged> { sessions.finishLogin(current, expired()) }
            equal("new-access", store.load("key")?.accessToken)
        },
        Case("background refresh does not invalidate a pending browser login") {
            val store = MemoryStore().apply { save("key", expired()) }
            val sessions = ProviderOAuthSessions(store, Endpoint { fresh() }) { now }
            val ticket = sessions.beginLogin("key")
            equal("new-access", sessions.accessToken("key", config))
            sessions.finishLogin(ticket, ProviderOAuthTokens("other-account", null, null))
            equal("other-account", store.load("key")?.accessToken)
        },
        Case("old-account invalid_grant does not invalidate a pending browser login") {
            val store = MemoryStore().apply { save("key", expired()) }
            val sessions = ProviderOAuthSessions(store, Endpoint { throw OAuthInvalidGrant() }) { now }
            val ticket = sessions.beginLogin("key")
            failsSuspend<OAuthLoginRequired> { sessions.accessToken("key", config) }
            sessions.finishLogin(ticket, fresh())
            equal("new-access", store.load("key")?.accessToken)
        },
        Case("independent credential namespaces refresh independently") {
            val store = MemoryStore().apply { save("one", expired()); save("two", expired()) }
            val endpoint = Endpoint { fresh() }
            val sessions = ProviderOAuthSessions(store, endpoint) { now }
            sessions.accessToken("one", config)
            sessions.accessToken("two", config)
            equal(2, endpoint.calls.get())
            sessions.logout("one")
            truth(sessions.status("two").signedIn)
            truth(!sessions.status("one").signedIn)
        },
        Case("manual refresh rotates a still-valid token and emits a state change") {
            val store = MemoryStore().apply { save("key", ProviderOAuthTokens("still-valid", "refresh", now + 3600000)) }
            val endpoint = Endpoint { fresh() }
            val sessions = ProviderOAuthSessions(store, endpoint) { now }
            equal("new-access", sessions.accessToken("key", config, forceRefresh = true))
            equal(1, endpoint.calls.get())
            truth(sessions.changes.value > 0)
        },
        Case("real loopback listener ignores bad state, Host and method before accepting one callback") {
            coroutineScope {
                ProviderOAuthLoopbackServer.open(config).use { server ->
                    val a = ProviderOAuthAttempt(config, server.redirectUri, now, state, verifier)
                    val pending = async { withTimeout(5000) { server.awaitCallback { a.accepts(it, now) } } }
                    val uri = URI(server.redirectUri)
                    suspend fun send(target: String, host: String = uri.rawAuthority, method: String = "GET") = withContext(Dispatchers.IO) {
                        Socket("127.0.0.1", uri.port).use { socket ->
                            socket.soTimeout = 3000
                            socket.getOutputStream().write("$method $target HTTP/1.1\r\nHost: $host\r\n\r\n".toByteArray())
                            socket.getInputStream().bufferedReader().readText()
                        }
                    }
                    val target = "${uri.path}?code=real-code&state=$state"
                    truth(send(target.replace(state, "bad")).startsWith("HTTP/1.1 400"))
                    truth(send(target, host = "evil.example").startsWith("HTTP/1.1 400"))
                    truth(send(target, method = "POST").startsWith("HTTP/1.1 400"))
                    val response = send(target)
                    truth(response.startsWith("HTTP/1.1 200"))
                    truth(response.contains("Cache-Control: no-store"))
                    truth(!response.contains("real-code") && !response.contains(state))
                    equal("real-code", a.consume(pending.await(), now).code)
                }
            }
        },
        Case("localhost callback compatibility still binds only the IPv4 loopback socket") {
            coroutineScope {
                val c = config.copy(redirectHost = "localhost")
                ProviderOAuthLoopbackServer.open(c).use { server ->
                    val a = ProviderOAuthAttempt(c, server.redirectUri, now, state, verifier)
                    val pending = async { withTimeout(5000) { server.awaitCallback { a.accepts(it, now) } } }
                    val uri = URI(server.redirectUri)
                    withContext(Dispatchers.IO) {
                        Socket("127.0.0.1", uri.port).use { socket ->
                            socket.soTimeout = 3000
                            socket.getOutputStream().write(("GET ${uri.path}?code=local&state=$state HTTP/1.1\r\n" +
                                "Host: localhost:${uri.port}\r\n\r\n").toByteArray())
                            truth(socket.getInputStream().bufferedReader().readText().startsWith("HTTP/1.1 200"))
                        }
                    }
                    equal("local", a.consume(pending.await(), now).code)
                }
            }
        },
        Case("real loopback listener cancels promptly and releases its bound port") {
            coroutineScope {
                val server = ProviderOAuthLoopbackServer.open(config)
                val port = URI(server.redirectUri).port
                val started = CompletableDeferred<Unit>()
                val pending = launch {
                    server.use { started.complete(Unit); it.awaitCallback { true } }
                }
                started.await()
                delay(20)
                withTimeout(1500) { pending.cancelAndJoin() }
                ProviderOAuthLoopbackServer.open(config.copy(redirectPort = port)).use {
                    equal(port, URI(it.redirectUri).port)
                }
            }
        },
    )
}
