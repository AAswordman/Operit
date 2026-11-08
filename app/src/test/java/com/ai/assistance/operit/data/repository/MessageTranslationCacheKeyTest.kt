package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MessageTranslationCacheKeyTest {
    @Test
    fun sourceHash_matchesSha256Utf8() {
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            MessageTranslationCacheKey.sourceHash("hello"),
        )
    }

    @Test
    fun sourceHash_changesWhenMessageIsEdited() {
        assertNotEquals(
            MessageTranslationCacheKey.sourceHash("original"),
            MessageTranslationCacheKey.sourceHash("edited"),
        )
    }

    @Test
    fun sourceHash_isStableForUnicodeText() {
        assertEquals(
            "49e1e6be89fd25b4a6e01b50ae053e6ff03fcae45fd74fa455aa890cd9ffe9ae",
            MessageTranslationCacheKey.sourceHash("翻译结果"),
        )
    }
}
