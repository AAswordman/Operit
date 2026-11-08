package com.ai.assistance.operit.core.chat.context

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUtilsJsonExtractionTest {

    @Test fun extractJson_keepsNestedObject() {
        assertEquals("{\"a\":{\"b\":1}}", ChatUtils.extractJson("text {\"a\":{\"b\":1}} text"))
    }

    @Test fun extractJson_returnsTrimmedMarkdownBody() {
        assertEquals("{\"a\":1}", ChatUtils.extractJson("```json\n{\"a\":1}\n```   "))
    }

    @Test fun extractJsonArray_keepsNestedArray() {
        assertEquals("[[1],[2]]", ChatUtils.extractJsonArray("text [[1],[2]] text"))
    }

    @Test fun extractJsonArray_returnsTrimmedMarkdownBody() {
        assertEquals("[1]", ChatUtils.extractJsonArray("```json\n[1]\n```   "))
    }
}
