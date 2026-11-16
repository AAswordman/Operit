package com.ai.assistance.operit.core.auth

/** Validate registration data without coercing malformed or unknown security-sensitive fields. */
internal fun parseProviderOAuthConfig(fields: Map<String, Any?>): ProviderOAuthConfig {
    val allowed = setOf("type", "clientId", "authorizationEndpoint", "tokenEndpoint", "scopes",
        "authorizationParameters", "redirectPort", "redirectHost", "redirectPath", "issuer")
    require(fields.keys.all { it in allowed }) { "Unsupported AI provider auth field" }
    require(fields["type"] == "oauth2") { "Only auth.type=oauth2 is supported" }
    fun string(key: String): String = fields[key] as? String
        ?: throw IllegalArgumentException("OAuth $key must be a string")
    val scopes = if (fields.containsKey("scopes")) {
        val value = fields["scopes"]
        require(value is List<*> && value.all { it is String }) { "OAuth scopes must be an array of strings" }
        value.map { it as String }
    } else emptyList()
    val parameters = if (fields.containsKey("authorizationParameters")) {
        val value = fields["authorizationParameters"]
        require(value is Map<*, *> && value.all { (key, item) -> key is String && item is String }) {
            "OAuth authorizationParameters must be an object of strings"
        }
        value.entries.associate { (key, item) -> key as String to item as String }
    } else emptyMap()
    val port = if (fields.containsKey("redirectPort")) {
        val value = fields["redirectPort"]
        require(value is Number) { "OAuth redirectPort must be an integer" }
        value.toString().toIntOrNull() ?: throw IllegalArgumentException("OAuth redirectPort is invalid")
    } else 0
    return ProviderOAuthConfig(
        clientId = string("clientId"),
        authorizationEndpoint = string("authorizationEndpoint"),
        tokenEndpoint = string("tokenEndpoint"),
        scopes = scopes,
        authorizationParameters = parameters,
        redirectPort = port,
        redirectHost = if (fields.containsKey("redirectHost")) string("redirectHost") else "127.0.0.1",
        redirectPath = if (fields.containsKey("redirectPath")) string("redirectPath") else "/oauth/callback",
        issuer = if (fields.containsKey("issuer")) string("issuer") else null,
    )
}
