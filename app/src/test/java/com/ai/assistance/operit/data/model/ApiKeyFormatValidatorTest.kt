package com.ai.assistance.operit.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyFormatValidatorTest {
    @Test
    fun outerWhitespace_isNormalized() {
        assertTrue(ApiKeyFormatValidator.isValid("  sk-test_123+/=  "))
    }

    @Test
    fun nonAsciiCharacters_areRejected() {
        assertFalse(ApiKeyFormatValidator.isValid("中文密钥"))
        assertFalse(ApiKeyFormatValidator.isValid("sk-fullwidth-Ａ"))
        assertFalse(ApiKeyFormatValidator.isValid("sk-emoji-😀"))
    }

    @Test
    fun whitespaceAndControlCharacters_areRejected() {
        assertFalse(ApiKeyFormatValidator.isValid("sk-internal space"))
        assertFalse(ApiKeyFormatValidator.isValid("sk-tab\tvalue"))
        assertFalse(ApiKeyFormatValidator.isValid("sk-line\nvalue"))
        assertFalse(ApiKeyFormatValidator.isValid("sk-null\u0000value"))
    }

    @Test
    fun availableKeyPool_isAcceptedWithoutSingleKey() {
        val config =
            ModelConfigData(
                id = "pool",
                name = "Pool",
                useMultipleApiKeys = true,
                apiKeyPool =
                    listOf(
                        ApiKeyInfo(
                            id = "key-1",
                            key = "sk-valid",
                            availabilityStatus = ApiKeyAvailabilityStatus.AVAILABLE
                        )
                    )
            )

        assertTrue(ApiKeyFormatValidator.hasUsableKey(config))
    }

    @Test
    fun invalidEnabledPoolEntry_rejectsWholePool() {
        val config =
            ModelConfigData(
                id = "pool",
                name = "Pool",
                useMultipleApiKeys = true,
                apiKeyPool =
                    listOf(
                        ApiKeyInfo(id = "key-1", key = "sk-valid"),
                        ApiKeyInfo(id = "key-2", key = "中文")
                    )
            )

        assertFalse(ApiKeyFormatValidator.hasUsableKey(config))
    }
}
