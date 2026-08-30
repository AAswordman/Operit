package com.ai.assistance.operit.data.stats

import com.ai.assistance.operit.data.model.ApiProviderType

internal data class ReleasedProviderModelKey(
    val storedProviderModel: String,
    val provider: String,
    val model: String,
)

/** Decodes released `provider:model` keys stored as `provider_model`. */
internal object ReleasedProviderModelKeyDecoder {
    private val builtInProviderAliases = ApiProviderType.entries.associate { it.name to it.name }

    fun decode(
        encoded: String,
        additionalProviderAliases: Map<String, String> = emptyMap(),
    ): ReleasedProviderModelKey {
        val aliases = buildMap {
            putAll(builtInProviderAliases)
            additionalProviderAliases.forEach { (rawAlias, rawIdentity) ->
                val alias = rawAlias.trim()
                val identity = rawIdentity.trim()
                require(alias.isNotEmpty() && identity.isNotEmpty())
                val previous = put(alias, identity)
                require(previous == null || previous == identity)
            }
        }
        val known = aliases.keys.sortedByDescending(String::length)
            .firstOrNull { encoded == it || encoded.startsWith("${it}_") }
        val separator = known?.length ?: encoded.indexOf('_')
        require(separator > 0 && separator < encoded.lastIndex) {
            "released token key does not contain a provider and model: $encoded"
        }
        val providerAlias = encoded.substring(0, separator)
        val model = encoded.substring(separator + 1)
        return ReleasedProviderModelKey(
            storedProviderModel = "$providerAlias:$model",
            provider = if (known == null) providerAlias else aliases.getValue(providerAlias),
            model = model,
        )
    }

    /**
     * Used by migration code for released keys from before provider/model identities existed.
     * Keys that do not start with a known provider alias, such as pre-format function keys
     * like `CHAT` or `FILE_BINDING`, return null so callers can skip them.
     */
    fun decodeOrNull(
        encoded: String,
        additionalProviderAliases: Map<String, String> = emptyMap(),
    ): ReleasedProviderModelKey? {
        val known = (builtInProviderAliases.keys + additionalProviderAliases.keys)
            .filter { encoded == it || encoded.startsWith("${it}_") }
            .maxByOrNull(String::length)
            ?: return null
        return try {
            decode(encoded, additionalProviderAliases)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
