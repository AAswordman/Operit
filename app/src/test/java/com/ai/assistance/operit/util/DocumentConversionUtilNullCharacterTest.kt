package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentConversionUtilNullCharacterTest {

    @Test
    fun stripNullCharactersRemovesEveryNulAndKeepsOtherText() {
        val text = "AB\u0000CD\u0000\u0000EF"

        assertEquals("ABCDEF", DocumentConversionUtil.stripNullCharacters(text))
    }

    @Test
    fun stripNullCharactersKeepsTextWithoutNulUnchanged() {
        val text = "中文 text with emoji 😀 and newlines\nsecond line"

        assertEquals(text, DocumentConversionUtil.stripNullCharacters(text))
    }

    @Test
    fun stripNullCharactersHandlesEmptyText() {
        assertEquals("", DocumentConversionUtil.stripNullCharacters(""))
        assertEquals("", DocumentConversionUtil.stripNullCharacters("\u0000\u0000"))
    }
}
