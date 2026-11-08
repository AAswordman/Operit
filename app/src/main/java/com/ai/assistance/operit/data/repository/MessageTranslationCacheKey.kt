package com.ai.assistance.operit.data.repository

import java.security.MessageDigest

internal object MessageTranslationCacheKey {
    private const val HEX_DIGITS = "0123456789abcdef"

    fun sourceHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0f])
            }
        }
    }
}
