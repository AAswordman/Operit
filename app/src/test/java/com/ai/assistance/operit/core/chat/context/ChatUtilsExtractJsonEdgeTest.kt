package com.ai.assistance.operit.core.chat.context

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUtilsExtractJsonEdgeTest {

    @Test fun extractJson_returnsOriginalWhenBracesOutOfOrder() {
        assertEquals("} {", ChatUtils.extractJson("} {"))
    }

    @Test fun extractJsonArray_returnsOriginalWhenBracketsOutOfOrder() {
        assertEquals("] [", ChatUtils.extractJsonArray("] ["))
    }

    @Test fun extractJson_handlesMarkdownFenceWithoutLanguage() {
        assertEquals("{\"a\":1}", ChatUtils.extractJson("```\n{\"a\":1}\n```"))
    }
}
