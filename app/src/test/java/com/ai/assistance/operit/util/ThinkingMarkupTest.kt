package com.ai.assistance.operit.util

import org.junit.Assert.*
import org.junit.Test

class ThinkingMarkupTest {
    private val token = "aBc4"
    private val thought = "前半。</think>后半。</thinking></operit_thinking>\n仍在思考。"

    @Test fun wrap_usesThinkAndMatchingShortToken() {
        val wrapped = ThinkingMarkup.wrap(thought)
        val opening = wrapped.substringBefore('>') + ">"
        val generated = requireNotNull(ThinkingMarkup.tokenOf(opening))
        assertTrue(generated.matches(Regex("[A-Za-z0-9]{4,5}")))
        assertEquals(ThinkingMarkup.openTag(generated) + thought + ThinkingMarkup.closeTag(generated), wrapped)
        assertEquals(thought, ThinkingMarkup.body(wrapped))
    }

    @Test fun remove_doesNotLeakAnyStreamingPrefixAfterFakeClose() {
        val before = "开头。"
        val raw = before + ThinkingMarkup.openTag(token) + thought + ThinkingMarkup.closeTag(token) + "正文。"
        val bodyStart = before.length + ThinkingMarkup.openTag(token).length
        val closeEnd = raw.length - "正文。".length
        for (length in bodyStart until closeEnd) {
            assertEquals("流式前缀长度=$length", before, ThinkingMarkup.remove(raw.take(length)))
        }
        assertEquals("开头。正文。", ThinkingMarkup.remove(raw))
    }

    @Test fun tokenComparison_isExactAndRejectsOtherAttributes() {
        val falseClosers = "</think token=\"Abc4\"></think data-token=\"aBc4\">" +
            "</think title='token=\"aBc4\"'></thinking token=\"aBc4\">"
        val raw = ThinkingMarkup.openTag(token) + falseClosers + "继续思考" + ThinkingMarkup.closeTag(token)
        assertEquals(falseClosers + "继续思考", ThinkingMarkup.body(raw))
        assertEquals("", ThinkingMarkup.remove(raw))
    }

    @Test fun partialClosingTagInThought_doesNotSwallowRealBoundary() {
        val thought = "讨论尚未写完的 </think token= 标记"
        val raw = ThinkingMarkup.openTag(token) + thought + ThinkingMarkup.closeTag(token) + "回答"
        assertEquals(thought, ThinkingMarkup.body(raw))
        assertEquals("回答", ThinkingMarkup.remove(raw))
    }

    @Test fun extract_unclosedTokenBlockKeepsAllTextInThinking() {
        val raw = "前文" + ThinkingMarkup.openTag(token) + thought
        assertFalse(ThinkingMarkup.isClosed(raw.removePrefix("前文")))
        assertEquals("前文" to listOf(thought), ThinkingMarkup.extract(raw))
    }

    @Test fun extract_acceptsSingleQuotesAndLegacyNames() {
        for (name in listOf("think", "thinking")) {
            assertEquals("回答" to listOf("草稿"), ThinkingMarkup.extract("<$name>草稿</$name>回答"))
        }
        val raw = "<think token='aBc4'>草稿</think></think token='aBc4'>回答"
        assertEquals("回答" to listOf("草稿</think>"), ThinkingMarkup.extract(raw))
    }

    @Test fun remove_preservesSelfClosingAndSeparatesMultipleBlocks() {
        val raw = "a<think />" + ThinkingMarkup.openTag(token) + thought + ThinkingMarkup.closeTag(token) +
            "b" + ThinkingMarkup.wrap("第二段") + "c"
        assertEquals("a<think />bc", ThinkingMarkup.remove(raw))
        assertEquals("a<think /> b c", ThinkingMarkup.remove(raw, " "))
    }

    @Test fun unsupportedThinkingNameRemainsOrdinaryContent() {
        assertFalse(ThinkingMarkup.isThinkingTag("operit_thinking"))
        for (raw in listOf(
            "<operit_thinking>普通内容</operit_thinking>回答",
            "<operit_thinking token=\"aBc4\">普通内容</operit_thinking token=\"aBc4\">回答",
        )) {
            assertNull(ThinkingMarkup.blockAt(raw))
            assertEquals(raw to emptyList<String>(), ThinkingMarkup.extract(raw))
            assertEquals(raw, ThinkingMarkup.remove(raw))
        }
    }

}
