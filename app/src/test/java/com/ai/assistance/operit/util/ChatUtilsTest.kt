package com.ai.assistance.operit.util

import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUtilsTest {

    @Test fun stripGeminiThoughtSignatureMeta_removesMatchingMeta() {
        assertEquals("hello", ChatUtils.stripGeminiThoughtSignatureMeta("hello<meta provider=\"gemini:thought_signature\">sig</meta>"))
    }

    @Test fun stripGeminiThoughtSignatureMeta_preservesPlainText() {
        assertEquals("hello", ChatUtils.stripGeminiThoughtSignatureMeta("hello"))
    }

    @Test fun stripGeminiThoughtSignatureMeta_pairsOnlyChangeContent() {
        val result = ChatUtils.stripGeminiThoughtSignatureMeta(listOf("assistant" to "<meta provider=\"gemini:thought_signature\">sig</meta>hello"))
        assertEquals(listOf("assistant" to "hello"), result)
    }

    @Test fun stripGeminiThoughtSignatureMeta_turnsOnlyChangeContent() {
        val turns = listOf(PromptTurn(PromptTurnKind.ASSISTANT, "<meta provider=\"gemini:thought_signature\">sig</meta>hello"))
        val result = ChatUtils.stripGeminiThoughtSignatureMetaTurns(turns)
        assertEquals("hello", result.single().content)
    }

    @Test fun geminiProviderModel_recognizesGooglePrefix() {
        assertTrue(ChatUtils.isGeminiProviderModel("GOOGLE:gemini-2.5"))
    }

    @Test fun geminiProviderModel_recognizesGenericPrefix() {
        assertTrue(ChatUtils.isGeminiProviderModel("GEMINI_GENERIC:proxy"))
    }

    @Test fun geminiProviderModel_rejectsOtherPrefix() {
        assertFalse(ChatUtils.isGeminiProviderModel("OPENAI:gpt-4"))
    }

    @Test fun removeThinkingContent_removesThinkBlock() {
        assertEquals("answer", ChatUtils.removeThinkingContent("<think>draft</think>answer"))
    }

    @Test fun removeThinkingContent_removesThinkingBlock() {
        assertEquals("answer", ChatUtils.removeThinkingContent("<thinking>draft</thinking>answer"))
    }

    @Test fun removeThinkingContent_removesSearchBlock() {
        assertEquals("answer", ChatUtils.removeThinkingContent("<search>source</search>answer"))
    }

    @Test fun removeThinkingContent_handlesUnclosedThink() {
        assertEquals("prefix", ChatUtils.removeThinkingContent("prefix<think>draft"))
    }

    @Test fun removeThinkingContent_handlesUnclosedSearch() {
        assertEquals("prefix", ChatUtils.removeThinkingContent("prefix<search>source"))
    }

    @Test fun extractThinkingContent_returnsCleanContent() {
        val result = ChatUtils.extractThinkingContent("<think>draft</think>answer")
        assertEquals("answer", result.first)
        assertEquals("draft", result.second)
    }

    @Test fun extractThinkingContent_mergesMultipleBlocks() {
        val result = ChatUtils.extractThinkingContent("<think>a</think>mid<thinking>b</thinking>end")
        assertEquals("midend", result.first)
        assertEquals("a\nb", result.second)
    }

    @Test fun extractThinkingContent_alsoRemovesSearchBlocks() {
        val result = ChatUtils.extractThinkingContent("<search>s</search><think>a</think>answer")
        assertEquals("answer", result.first)
        assertEquals("a", result.second)
    }

    @Test fun estimateTokenCount_countsEnglishApproximately() {
        assertEquals(1, ChatUtils.estimateTokenCount("abcd"))
    }

    @Test fun estimateTokenCount_countsChineseApproximately() {
        assertEquals(3, ChatUtils.estimateTokenCount("你好"))
    }

    @Test fun estimateTokenCount_countsMixedText() {
        assertEquals(1, ChatUtils.estimateTokenCount("你a"))
    }

    @Test fun extractJson_returnsEmbeddedObject() {
        assertEquals("{\"a\":1}", ChatUtils.extractJson("before {\"a\":1} after"))
    }

    @Test fun extractJson_handlesMarkdownFence() {
        assertEquals("{\"a\":1}", ChatUtils.extractJson("```json\n{\"a\":1}\n```"))
    }

    @Test fun extractJson_returnsOriginalWhenNoObjectFound() {
        assertEquals("plain text", ChatUtils.extractJson("plain text"))
    }

    @Test fun extractJsonArray_returnsEmbeddedArray() {
        assertEquals("[1,2,3]", ChatUtils.extractJsonArray("before [1,2,3] after"))
    }

    @Test fun extractJsonArray_handlesMarkdownFence() {
        assertEquals("[1,2,3]", ChatUtils.extractJsonArray("```json\n[1,2,3]\n```"))
    }

    @Test fun extractJsonArray_returnsOriginalWhenNoArrayFound() {
        assertEquals("plain text", ChatUtils.extractJsonArray("plain text"))
    }

    @Test fun stripGeminiThoughtSignatureMeta_turnObjectIsReusedWhenUnchanged() {
        val turn = PromptTurn(PromptTurnKind.USER, "hello")
        assertTrue(ChatUtils.stripGeminiThoughtSignatureMetaTurns(listOf(turn)).single() === turn)
    }
}
