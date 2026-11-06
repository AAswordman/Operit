package com.ai.assistance.operit.core.auth

/** Strict token validation kept independent of Android/JSON for JVM tests. */
internal fun parseProviderOAuthTokens(
    fields: Map<String, Any?>,
    nowMillis: Long,
    previous: ProviderOAuthTokens? = null,
): ProviderOAuthTokens {
    require((fields["token_type"] as? String).equals("Bearer", ignoreCase = true)) {
        "OAuth token response must use Bearer tokens"
    }
    val accessToken = fields["access_token"] as? String
        ?: throw IllegalArgumentException("OAuth token response has no access token")
    val refreshToken = if (fields.containsKey("refresh_token")) {
        fields["refresh_token"] as? String
            ?: throw IllegalArgumentException("OAuth refresh token is invalid")
    } else previous?.refreshToken
    val expiresAt = if (fields.containsKey("expires_in")) {
        val raw = fields["expires_in"]
        require(raw is Number || raw is String) { "OAuth expiration is invalid" }
        val seconds = raw.toString().toLongOrNull()
        require(seconds != null && seconds > 0 && nowMillis >= 0 && seconds <= (Long.MAX_VALUE - nowMillis) / 1000) {
            "OAuth expiration is invalid"
        }
        nowMillis + seconds * 1000
    } else null
    return ProviderOAuthTokens(accessToken, refreshToken, expiresAt)
}
