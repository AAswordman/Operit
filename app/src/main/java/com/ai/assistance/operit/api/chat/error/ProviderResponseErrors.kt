package com.ai.assistance.operit.api.chat.error

import org.json.JSONObject

internal object ProviderResponseErrors {
    fun exception(provider: String, error: JSONObject?, message: String): ProviderResponseException =
        ProviderResponseException(
            provider = provider,
            providerCode = error?.opt("code")?.takeUnless { it == JSONObject.NULL }?.toString(),
            providerType = (error?.opt("type") as? String)?.takeIf { it.isNotBlank() }
                ?: (error?.opt("status") as? String)?.takeIf { it.isNotBlank() },
            message = message,
        )

    fun check(provider: String, response: JSONObject): JSONObject {
        val error = response.optJSONObject("error") ?: return response
        throw exception(provider, error, error.optString("message", error.toString()))
    }
}
