package com.ai.nonoassistance.provider

/**
 * ProviderRegistry — manages registered AI providers.
 *
 * Provider registration is config-driven: add a new provider by implementing [AIProvider]
 * and calling [register]. No core code changes needed for new providers.
 */
class ProviderRegistry {

    private val providers = mutableMapOf<String, AIProvider>()

    /** Register a new provider. Overwrites if same id already registered. */
    fun register(provider: AIProvider) {
        providers[provider.id] = provider
    }

    /** Unregister a provider by id */
    fun unregister(providerId: String) {
        providers.remove(providerId)
    }

    /** Get a provider by id, or null if not found */
    fun get(providerId: String): AIProvider? = providers[providerId]

    /** Get all registered providers */
    fun getAll(): List<AIProvider> = providers.values.toList()

    /** Check if a provider is registered */
    fun isRegistered(providerId: String): Boolean = providers.containsKey(providerId)

    /** Get all available models across all registered providers */
    suspend fun getAllModels(): Map<String, List<ModelInfo>> {
        return providers.mapValues { (_, provider) ->
            try {
                provider.listModels()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
