package com.ai.assistance.operit.data.model

object ApiKeyFormatValidator {
    fun normalize(value: String): String = value.trim()

    fun isValid(value: String): Boolean {
        val normalized = normalize(value)
        return normalized.isNotEmpty() && normalized.all { character ->
            character.code in 0x21..0x7E
        }
    }

    fun hasUsableKey(config: ModelConfigData): Boolean {
        if (!config.useMultipleApiKeys) {
            return isValid(config.apiKey)
        }

        val enabledKeys = config.apiKeyPool.filter { it.isEnabled }
        val hasAvailabilityMark =
            enabledKeys.any { it.availabilityStatus != ApiKeyAvailabilityStatus.UNTESTED }
        val candidateKeys =
            if (hasAvailabilityMark) {
                enabledKeys.filter {
                    it.availabilityStatus == ApiKeyAvailabilityStatus.AVAILABLE
                }
            } else {
                enabledKeys
            }

        if (candidateKeys.isNotEmpty()) {
            return candidateKeys.all { isValid(it.key) }
        }
        return !hasAvailabilityMark && isValid(config.apiKey)
    }
}
