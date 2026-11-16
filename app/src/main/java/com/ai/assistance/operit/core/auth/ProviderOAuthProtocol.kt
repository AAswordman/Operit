package com.ai.assistance.operit.core.auth

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/** Public-client authorization-code flow. Secrets and implicit/password grants are not supported. */
internal data class ProviderOAuthConfig(
    val clientId: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val scopes: List<String> = emptyList(),
    val authorizationParameters: Map<String, String> = emptyMap(),
    val redirectPort: Int = 0,
    val redirectHost: String = "127.0.0.1",
    val redirectPath: String = "/oauth/callback",
    val issuer: String? = null,
) {
    init {
        require(clientId.isNotBlank() && clientId.length <= 1024 && !clientId.hasControlCharacters()) {
            "OAuth clientId is invalid"
        }
        requireHttpsEndpoint(authorizationEndpoint)
        requireHttpsEndpoint(tokenEndpoint)
        issuer?.let(::requireHttpsEndpoint)
        require(redirectHost == "127.0.0.1" || redirectHost == "localhost") { "OAuth redirectHost must be loopback" }
        require(redirectPort in 0..65535) { "OAuth redirectPort is invalid" }
        require(redirectPath.length <= 256 && redirectPath.matches(Regex("/[A-Za-z0-9/_-]+")) && !redirectPath.contains("//")) {
            "OAuth redirectPath is invalid"
        }
        require(scopes.size <= 64 && scopes.all { scope ->
            scope.isNotEmpty() && scope.length <= 256 && scope.all {
                it.code == 0x21 || it.code in 0x23..0x5b || it.code in 0x5d..0x7e
            }
        }) { "OAuth scopes are invalid" }
        require(authorizationParameters.size <= 32 && authorizationParameters.all { (key, value) ->
            key.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")) &&
                key.lowercase() !in RESERVED_PARAMETERS &&
                value.length <= 2048 && !value.hasControlCharacters()
        }) { "OAuth authorizationParameters cannot override protocol or credential fields" }
    }

    /** All authentication settings participate in the key; changing an issuer requires new consent. */
    fun credentialKey(packageId: String, providerId: String, configId: String, apiEndpoint: String): String {
        require(packageId.isNotBlank() && providerId.isNotBlank() && configId.isNotBlank())
        requireHttpsEndpoint(apiEndpoint, allowQuery = true)
        val fields = listOf(
            packageId, providerId, configId, apiEndpoint,
            clientId, authorizationEndpoint, tokenEndpoint, issuer.orEmpty(),
            redirectPort.toString(), redirectHost, redirectPath,
        )
        // Length-prefix lists as well as entries, so no delimiter/list-boundary collisions are possible.
        val fingerprint = framed(fields) + framed(scopes.distinct().sorted()) +
            framed(authorizationParameters.toSortedMap().flatMap { (key, value) -> listOf(key, value) })
        return sha256Hex(fingerprint)
    }

    companion object {
        private val RESERVED_PARAMETERS = setOf(
            "client_id", "client_secret", "response_type", "response_mode", "redirect_uri", "scope", "state",
            "code_challenge", "code_challenge_method", "code_verifier", "code", "grant_type",
            "access_token", "refresh_token", "id_token", "authorization", "request", "request_uri",
        )
    }
}

internal fun requireHttpsEndpoint(value: String, allowQuery: Boolean = false): URI {
    require(value.length <= 4096 && !value.hasControlCharacters() && !value.contains('\\')) {
        "OAuth endpoint is invalid"
    }
    val uri = try { URI(value) } catch (_: Exception) { throw IllegalArgumentException("OAuth endpoint is invalid") }
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
        uri.rawFragment == null && (allowQuery || uri.rawQuery == null) && (uri.port == -1 || uri.port in 1..65535)
    ) { "OAuth endpoints must use HTTPS without credentials, fragments or protocol query parameters" }
    return uri
}

internal fun String.hasControlCharacters(): Boolean = any { it.code < 0x20 || it.code == 0x7f }
private fun framed(fields: List<String>): String = "${fields.size}:" + fields.joinToString("") { "${it.length}:$it" }
internal fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
internal fun formEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

internal class OAuthLoginRequired : IllegalStateException("Sign in to this provider in model settings")
internal class OAuthAuthorizationDenied : IllegalStateException("OAuth authorization was denied")
internal class OAuthInvalidGrant : IllegalStateException("OAuth grant is no longer valid")
internal class OAuthSessionChanged : IllegalStateException("OAuth session changed; sign in again")

internal class OAuthAuthorizationCode internal constructor(
    val code: String,
    val redirectUri: String,
    val verifier: String,
) {
    override fun toString(): String = "OAuthAuthorizationCode([redacted])"
}

/** Ephemeral, single-use authorization attempt; never persisted or sent to plugin JavaScript. */
internal class ProviderOAuthAttempt(
    private val config: ProviderOAuthConfig,
    val redirectUri: String,
    private val startedAtMillis: Long,
    private val state: String = randomOAuthValue(),
    private val verifier: String = randomOAuthValue(),
) {
    private val consumed = AtomicBoolean(false)
    private val destination = URI(redirectUri)

    init {
        require(destination.scheme == "http" && destination.host == config.redirectHost &&
            destination.port in 1..65535 && destination.rawPath == config.redirectPath &&
            destination.rawQuery == null && destination.rawFragment == null && destination.rawUserInfo == null
        ) { "OAuth callback must be the host-owned loopback listener" }
        require(config.redirectPort == 0 || config.redirectPort == destination.port)
        require(verifier.matches(Regex("[A-Za-z0-9._~-]{43,128}")))
        require(state.length >= 32 && !state.hasControlCharacters())
    }

    val authorizationUrl: String
        get() {
            val parameters = linkedMapOf(
                "response_type" to "code",
                "client_id" to config.clientId,
                "redirect_uri" to redirectUri,
                "state" to state,
                "code_challenge" to pkceChallenge(verifier),
                "code_challenge_method" to "S256",
            )
            if (config.scopes.isNotEmpty()) parameters["scope"] = config.scopes.distinct().joinToString(" ")
            parameters.putAll(config.authorizationParameters)
            return config.authorizationEndpoint + "?" + parameters.entries.joinToString("&") {
                "${formEncode(it.key)}=${formEncode(it.value)}"
            }
        }

    /** Invalid requests must not consume an attempt or stop its listener. */
    fun accepts(callbackUrl: String, nowMillis: Long): Boolean = try {
        callbackParameters(callbackUrl, nowMillis)
        !consumed.get()
    } catch (_: IllegalArgumentException) {
        false
    }

    fun consume(callbackUrl: String, nowMillis: Long): OAuthAuthorizationCode {
        val parameters = callbackParameters(callbackUrl, nowMillis)
        check(consumed.compareAndSet(false, true)) { "OAuth callback has already been consumed" }
        if (parameters.containsKey("error")) throw OAuthAuthorizationDenied()
        val code = parameters["code"]
        require(!code.isNullOrBlank() && !code.hasControlCharacters()) { "OAuth callback has no valid code" }
        return OAuthAuthorizationCode(code, redirectUri, verifier)
    }

    private fun callbackParameters(callbackUrl: String, nowMillis: Long): Map<String, String> {
        require(nowMillis >= startedAtMillis && nowMillis - startedAtMillis < TIMEOUT_MILLIS) {
            "OAuth authorization expired"
        }
        require(callbackUrl.length <= 16_384 && !callbackUrl.hasControlCharacters()) { "OAuth callback is invalid" }
        val callback = try { URI(callbackUrl) } catch (_: Exception) { throw IllegalArgumentException("OAuth callback is invalid") }
        require(callback.scheme == destination.scheme && callback.rawAuthority == destination.rawAuthority &&
            callback.rawPath == destination.rawPath && callback.rawFragment == null && callback.rawUserInfo == null
        ) { "OAuth callback destination does not match" }
        val parameters = parseOAuthQuery(callback.rawQuery.orEmpty())
        val returnedState = parameters["state"].orEmpty()
        require(MessageDigest.isEqual(state.toByteArray(UTF_8), returnedState.toByteArray(UTF_8))) {
            "OAuth state does not match"
        }
        if (config.issuer != null) require(parameters["iss"] == config.issuer) { "OAuth issuer does not match" }
        require(parameters.containsKey("code") xor parameters.containsKey("error")) { "Ambiguous OAuth callback" }
        require(parameters[if (parameters.containsKey("code")) "code" else "error"]?.let {
            it.isNotBlank() && !it.hasControlCharacters()
        } == true) { "OAuth callback has no valid result" }
        return parameters
    }

    override fun toString(): String = "ProviderOAuthAttempt([redacted])"

    companion object { const val TIMEOUT_MILLIS: Long = 5 * 60 * 1000L }
}

internal fun parseOAuthQuery(query: String): Map<String, String> {
    if (query.isEmpty()) return emptyMap()
    val result = linkedMapOf<String, String>()
    for (pair in query.split('&')) {
        val parts = pair.split('=', limit = 2)
        val key = URLDecoder.decode(parts[0], "UTF-8")
        val value = URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
        require(!result.containsKey(key)) { "Duplicate OAuth response parameter" }
        result[key] = value
    }
    return result
}

internal fun randomOAuthValue(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }
    .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

internal fun pkceChallenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(UTF_8)))
