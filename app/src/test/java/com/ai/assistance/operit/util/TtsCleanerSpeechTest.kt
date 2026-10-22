package com.ai.assistance.operit.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * Regression coverage for reading messages aloud when the user enabled the built-in
 * "Remove XML tags" cleaner (`<[^>]+>`).
 *
 * The reasoning block only stays identifiable while its tags exist. Stripping the tags first
 * left the thought text behind as ordinary prose, so speech read it together with the reply.
 */
class TtsCleanerSpeechTest {

    private var previousSystemLogEnabled = true
    private var previousFileLogEnabled = true

    /** The pattern the settings screen inserts for its "Remove XML tags" template. */
    private val removeXmlTagsPattern = "<[^>]+>"

    private val reasoning = "让我先想想用户到底想问什么，然后把答案整理清楚。"
    private val replyBody = "今天重庆多云，最高气温 24 度。"

    private val messageWithReasoning = "<think>$reasoning</think>$replyBody"

    @Before
    fun disableAndroidLogging() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        previousFileLogEnabled = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun restoreAndroidLogging() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = previousFileLogEnabled
    }

    @Test
    fun cleanForSpeech_withXmlTagFilter_readsOnlyReplyBody() {
        val spoken = TtsCleaner.cleanForSpeech(messageWithReasoning, listOf(removeXmlTagsPattern))

        assertEquals(replyBody, spoken)
        assertFalse("Reasoning must not be spoken: $spoken", spoken.contains("想想用户"))
    }

    @Test
    fun cleanForSpeech_withoutXmlTagFilter_readsOnlyReplyBody() {
        val spoken = TtsCleaner.cleanForSpeech(messageWithReasoning, emptyList())

        assertEquals(replyBody, spoken)
        assertFalse("Reasoning must not be spoken: $spoken", spoken.contains("想想用户"))
    }

    @Test
    fun cleanForSpeech_filterStateDoesNotChangeSpokenBody() {
        val withFilter = TtsCleaner.cleanForSpeech(messageWithReasoning, listOf(removeXmlTagsPattern))
        val withoutFilter = TtsCleaner.cleanForSpeech(messageWithReasoning, emptyList())

        assertEquals(
            "Enabling the XML tag filter must not change what gets read aloud",
            withoutFilter,
            withFilter
        )
    }

    @Test
    fun cleanForSpeech_thinkingVariant_alsoSkipped() {
        val message = "<thinking>$reasoning</thinking>$replyBody"

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, emptyList()))
    }

    @Test
    fun cleanForSpeech_unclosedReasoning_isDroppedToEnd() {
        val message = "$replyBody<think>$reasoning"

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, emptyList()))
    }

    @Test
    fun cleanForSpeech_reasoningWithAttributes_isDropped() {
        val message = "<think type=\"reasoning\">$reasoning</think>$replyBody"

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, emptyList()))
    }

    @Test
    fun cleanForSpeech_uppercaseReasoning_isDropped() {
        val message = "<THINK>$reasoning</THINK>$replyBody"

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, emptyList()))
    }

    @Test
    fun cleanForSpeech_searchSources_areSkipped() {
        val message = "<search provider=\"deepseek\"><query>天气</query></search>$replyBody"

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, emptyList()))
    }

    @Test
    fun cleanForSpeech_toolCallsAndResults_areSkipped() {
        val message =
            "<tool name=\"weather\"><param name=\"city\">重庆</param></tool>" +
                "<tool_result name=\"weather\" status=\"success\">晴，24 度</tool_result>" +
                replyBody

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, emptyList()))
    }

    @Test
    fun cleanForSpeech_statusAndEmotionTags_areSkipped() {
        val message =
            "<status type=\"thinking\">正在查询</status>" +
                "<emotion>calm</emotion>" +
                "$replyBody<status type=\"done\"/>"

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, emptyList()))
    }

    // ---- Auto read: the streamed segments and the final flush both flow through the same
    // ---- cleaner, so covering the spoken output here covers both read modes.

    @Test
    fun cleanForSpeech_markdownBody_keepsTextUnderBothFilterStates() {
        val markdownBody = "## 天气\n\n**今天**多云，最高气温 `24` 度。"
        val message = "<think>$reasoning</think>$markdownBody"

        val withoutFilter = TtsCleaner.cleanForSpeech(message, emptyList())
        assertEquals(markdownBody, withoutFilter)

        // The tag filter must not eat the reply: Markdown markers carry no angle brackets.
        val withFilter = TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern))
        assertEquals(markdownBody, withFilter)
    }

    @Test
    fun cleanForSpeech_userPatternStillAppliesToReplyBody() {
        val body = "答案是 42 元（含税）。"
        val message = "<think>$reasoning</think>$body"
        // The settings screen offers this full-width variant for Chinese text.
        val chineseParenthesisPattern = "（[^）]+）"

        val spoken =
            TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern, chineseParenthesisPattern))

        assertEquals("答案是 42 元。", spoken)
        assertFalse(spoken.contains("想想用户"))
    }

    @Test
    fun cleanForSpeech_providerMetadataPayload_isSkipped() {
        // Provider protocol metadata carries base64 payloads. Stripping only the tags would leave
        // that payload to be spoken, so the block itself has to go.
        val message =
            "<meta provider=\"gemini:thought_signature\">Q2Fwc2lnbmF0dXJl</meta>" +
                "<meta provider=\"openai:responses_reasoning\">cmVhc29uaW5n</meta>" +
                replyBody

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, emptyList()))
    }

    @Test
    fun cleanForSpeech_plainReplyWithoutMarkup_isUnchanged() {
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(replyBody, listOf(removeXmlTagsPattern)))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(replyBody, emptyList()))
    }

    @Test
    fun cleanForSpeech_reasoningOnly_yieldsBlankSoPlaybackIsSkipped() {
        val message = "<think>$reasoning</think>"

        assertEquals("", TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)))
        assertEquals("", TtsCleaner.cleanForSpeech(message, emptyList()))
    }
}
