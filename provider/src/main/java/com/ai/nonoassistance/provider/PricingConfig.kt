package com.ai.nonoassistance.provider

import kotlinx.serialization.Serializable

/**
 * Remote pricing configuration — fetched from a JSON endpoint.
 *
 * Expected JSON format:
 * ```json
 * {
 *   "lastUpdated": "2026-08-29T00:00:00Z",
 *   "providers": {
 *     "deepseek": {
 *       "models": {
 *         "deepseek-chat": {
 *           "inputPerMillionTokens": 0.14,
 *           "outputPerMillionTokens": 0.28,
 *           "currency": "USD"
 *         },
 *         "deepseek-reasoner": {
 *           "inputPerMillionTokens": 0.55,
 *           "outputPerMillionTokens": 2.19,
 *           "currency": "USD"
 *         }
 *       }
 *     },
 *     "google": {
 *       "models": {
 *         "gemini-2.5-flash": {
 *           "inputPerMillionTokens": 0.15,
 *           "outputPerMillionTokens": 0.60,
 *           "currency": "USD"
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 */
@Serializable
data class PricingRemoteConfig(
    val lastUpdated: String = "",
    val providers: Map<String, PricingProviderEntry> = emptyMap()
)

@Serializable
data class PricingProviderEntry(
    val models: Map<String, PricingModelEntry> = emptyMap()
)

@Serializable
data class PricingModelEntry(
    val inputPerMillionTokens: Double = 0.0,
    val outputPerMillionTokens: Double = 0.0,
    val currency: String = "USD"
)
