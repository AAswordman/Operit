package com.ai.assistance.operit.core.auth

import org.json.JSONArray
import org.json.JSONObject

internal object ProviderOAuthConfigJson {
    fun parse(raw: Any?): ProviderOAuthConfig? {
        if (raw == null || raw == JSONObject.NULL) return null
        require(raw is JSONObject) { "AI provider auth must be an object" }
        val fields = raw.keys().asSequence().associateWith { key ->
            val value = raw.opt(key)
            when {
                value == JSONObject.NULL -> null
                key == "scopes" && value is JSONArray ->
                    (0 until value.length()).map { value.opt(it) }
                key == "authorizationParameters" && value is JSONObject ->
                    value.keys().asSequence().associateWith { value.opt(it) }
                else -> value
            }
        }
        return parseProviderOAuthConfig(fields)
    }
}
