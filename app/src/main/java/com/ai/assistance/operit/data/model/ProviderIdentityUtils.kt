package com.ai.assistance.operit.data.model

/** Keeps legacy display-oriented provider values usable without rewriting stored history. */
internal fun normalizeProviderTypeId(providerTypeId: String): String {
    val normalized = providerTypeId.trim()
    val separator = normalized.indexOf('/')
    if (separator <= 0) return normalized

    val providerPrefix = normalized.substring(0, separator).trim()
    val providerType = ApiProviderType.fromProviderTypeId(providerPrefix)
    return if (providerType == null) normalized else providerType.name
}

/** Normalizes only the provider part of a `provider:model` identity. */
internal fun normalizeProviderModel(providerModel: String): String {
    val separator = providerModel.indexOf(':')
    if (separator <= 0) return providerModel
    return normalizeProviderTypeId(providerModel.substring(0, separator)) +
        providerModel.substring(separator)
}

/** Returns whether missing cache-write usage must remain an unknown billed component. */
internal fun hasIndependentCacheWriteBilling(providerTypeId: String): Boolean =
    when (ApiProviderType.fromProviderTypeId(normalizeProviderTypeId(providerTypeId))) {
        ApiProviderType.ANTHROPIC,
        ApiProviderType.ANTHROPIC_GENERIC,
        ApiProviderType.OTHER,
        null -> true
        else -> false
    }
