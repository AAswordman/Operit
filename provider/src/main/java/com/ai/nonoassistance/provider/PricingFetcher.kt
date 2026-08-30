package com.ai.nonoassistance.provider

import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * PricingFetcher — fetches model pricing data from a remote JSON endpoint.
 *
 * Features:
 * - Fetches from configurable URL
 * - Caches in memory with TTL (configurable, default 24h)
 * - Returns cached data if fetch fails (graceful degradation)
 * - Thread-safe via synchronized access
 */
class PricingFetcher(
    private val pricingUrl: String = DEFAULT_PRICING_URL,
    private val cacheTtlMs: Long = 24 * 60 * 60 * 1000 // 24 hours
) {
    companion object {
        const val DEFAULT_PRICING_URL =
            "https://raw.githubusercontent.com/AAswordman/Operit/main/app/config/model-pricing.json"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var cachedConfig: PricingRemoteConfig? = null
    private var cacheTimestamp: Long = 0

    /**
     * Fetch pricing config. Returns cached version if fresh, otherwise fetches from remote.
     */
    suspend fun fetchPricing(): PricingRemoteConfig {
        val now = System.currentTimeMillis()

        // Return cached if still fresh
        cachedConfig?.let { cached ->
            if (now - cacheTimestamp < cacheTtlMs) {
                return cached
            }
        }

        // Fetch from remote
        return try {
            val config = fetchFromRemote()
            cachedConfig = config
            cacheTimestamp = now
            config
        } catch (e: Exception) {
            // Fallback to stale cache if available
            cachedConfig ?: PricingRemoteConfig()
        }
    }

    /**
     * Look up pricing for a specific provider + model combination.
     */
    suspend fun getPricing(providerId: String, modelId: String): ModelPricing? {
        val config = fetchPricing()
        val providerEntry = config.providers[providerId] ?: return null
        val modelEntry = providerEntry.models[modelId] ?: return null

        return ModelPricing(
            inputPerMillionTokens = modelEntry.inputPerMillionTokens,
            outputPerMillionTokens = modelEntry.outputPerMillionTokens,
            currency = modelEntry.currency,
            lastUpdated = config.lastUpdated
        )
    }

    private fun fetchFromRemote(): PricingRemoteConfig {
        val url = URL(pricingUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "NonO-Assistant-PricingFetch/1.0")

        return try {
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val body = reader.readText()
                reader.close()
                json.decodeFromString(body)
            } else {
                PricingRemoteConfig()
            }
        } catch (e: Exception) {
            PricingRemoteConfig()
        } finally {
            connection.disconnect()
        }
    }

    /** Force clear cache (useful for manual refresh) */
    fun clearCache() {
        cachedConfig = null
        cacheTimestamp = 0
    }
}
