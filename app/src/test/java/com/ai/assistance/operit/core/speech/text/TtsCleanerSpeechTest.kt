package com.ai.assistance.operit.core.speech.text

import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.data.chat.serialization.MessageSectionStorage
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

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
    fun regexFirstStepDestroysTheReasoningBoundaryInTheCurrentTemplate() {
        // This calls the existing generic cleaner with the actual built-in template. It shows
        // why later markup-aware processing cannot identify the original non-body block.
        val regexFirst = TtsCleaner.clean(messageWithReasoning, listOf(removeXmlTagsPattern))
        assertEquals(reasoning + replyBody, regexFirst)
        assertFalse(regexFirst.contains("<think"))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(messageWithReasoning, listOf(removeXmlTagsPattern)))
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

    @Test
    fun tokenPairedReasoning_doesNotEndAtABareClosingTag() {
        val message = "<think token=\"guard\">$reasoning</think>这段仍是思考。" +
            "</think token=\"guard\">$replyBody"

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, emptyList()))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)))
    }

    @Test
    fun unclosedTokenReasoning_doesNotExposeTextAfterABareClosingTag() {
        val message = "$replyBody<think token=\"guard\">$reasoning</think>未完成的思考。"

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, emptyList()))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)))
    }

    @Test
    fun tokenPairedReasoning_ignoresAnotherTokensClosingTag() {
        val message = "<think token=\"guard\">$reasoning</think token=\"other\">仍是思考。" +
            "</think token=\"guard\">$replyBody"

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)))
    }

    @Test
    fun structuredSections_selectOnlyTextWithoutRenderingHiddenSections() {
        val sections = listOf(
            MessageSection.Text("**今天**"),
            MessageSection.Thinking(reasoning),
            MessageSection.ToolCall(name = "weather", params = mapOf("city" to "重庆")),
            MessageSection.ToolResult(name = "weather", content = "工具内部结果"),
            MessageSection.Protocol(provider = "custom:protocol", payload = "内部协议负载"),
            MessageSection.Search("<search>搜索来源</search>"),
            MessageSection.Status("<status>执行状态</status>"),
            MessageSection.Text("<emotion>calm</emotion>多云。"),
        )

        assertEquals("**今天**多云。", TtsCleaner.cleanForSpeech(sections, emptyList()))
        assertEquals("多云。", TtsCleaner.cleanForSpeech(sections, listOf("\\*\\*[^*]+\\*\\*")))
    }

    @Test
    fun runtimeProtocolMetadata_doesNotDependOnAProviderAllowlist() {
        val message = "<meta provider=\"operit:tool_timing\">内部计时</meta>" +
            "<meta provider=\"custom:protocol\">协议负载</meta>$replyBody"

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, emptyList()))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)))
    }

    @Test
    fun databaseSectionsRoundTrip_suppliesSpeechWithoutAStoredContentField() {
        val sections = listOf(
            MessageSection.Thinking(reasoning),
            MessageSection.Protocol(provider = "custom:protocol", payload = "协议负载"),
            MessageSection.Text(replyBody),
        )
        val entity = MessageEntity(
            chatId = "speech-sections",
            sender = "ai",
            sections = MessageSectionStorage.encode(sections),
            searchText = "搜索索引不是朗读来源",
            orderIndex = 0,
        )
        val restored = entity.toChatMessage()

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(restored.resolvedSections(), listOf(removeXmlTagsPattern)))
        assertEquals(replyBody, TtsCleaner.cleanForSpeech(restored.content, listOf(removeXmlTagsPattern)))
    }

    @Test
    fun archiveRoundTrip_storesSectionsAndKeepsOnlyReplyTextForSpeech() {
        val sections = listOf(
            MessageSection.Thinking(reasoning),
            MessageSection.ToolResult(name = "weather", content = "内部工具结果"),
            MessageSection.Text(replyBody),
        )
        val message = ChatMessage(sender = "ai", content = MessageSectionCodec.render(sections), sections = sections)
        val json = Json { encodeDefaults = true }
        val archive = json.encodeToString(ChatMessage.serializer(), message)
        val fields = json.parseToJsonElement(archive).jsonObject
        assertFalse(fields.containsKey("content"))
        val restored = json.decodeFromString(ChatMessage.serializer(), archive)

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(restored.resolvedSections(), emptyList()))
    }

    @Test
    fun editedMessage_usesResolvedSectionsInsteadOfTheOldSnapshot() {
        val original = ChatMessage(sender = "ai", content = "<think>$reasoning</think>旧回答")
        val edited = original.copy(content = replyBody)

        assertEquals(replyBody, TtsCleaner.cleanForSpeech(edited.resolvedSections(), listOf(removeXmlTagsPattern)))
    }

    @Test
    fun inlineCodeWithStructuralTags_remainsReplyTextBeforeUserPatterns() {
        val message = "标签示例：`<tool name=\"weather\">示例正文</tool>`。"

        assertEquals(message, TtsCleaner.cleanForSpeech(message, emptyList()))
        assertEquals(
            "标签示例：`示例正文`。",
            TtsCleaner.cleanForSpeech(message, listOf(removeXmlTagsPattern)),
        )
    }
}
