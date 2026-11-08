package com.ai.assistance.operit.core.speech.text

import com.ai.assistance.operit.data.chat.serialization.MessageSectionStorage
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.WaifuMessageProcessor

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end coverage for reading messages aloud with the built-in "Remove XML tags" cleaner
 * (`<[^>]+>`) enabled.
 *
 * The JVM test [TtsCleanerSpeechTest] covers the cleaner itself. These cases add the second half
 * of the pipeline, [WaifuMessageProcessor.cleanContentForWaifu], which parses blocks through the
 * native splitter and therefore needs a device.
 */
@RunWith(AndroidJUnit4::class)
class TtsCleanerSpeechPipelineTest {

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

    private fun speechPipeline(message: String, cleanerRegexs: List<String>): String =
        WaifuMessageProcessor.cleanContentForWaifu(
            TtsCleaner.cleanForSpeech(message, cleanerRegexs)
        )

    @Test
    fun manualRead_readsReplyBodyOnlyUnderBothFilterStates() {
        val withFilter = speechPipeline(messageWithReasoning, listOf(removeXmlTagsPattern))
        val withoutFilter = speechPipeline(messageWithReasoning, emptyList())

        assertEquals(replyBody, withFilter)
        assertEquals(replyBody, withoutFilter)
        assertEquals(
            "Manual read must sound the same regardless of the filter: $withFilter vs $withoutFilter",
            withoutFilter,
            withFilter
        )
    }

    @Test
    fun autoReadStream_neverSpeaksReasoning() = runBlocking {
        // Auto read does not hand the whole message to the cleaner at once. It consumes the reply
        // through the stream TTS projection, buffers characters, and speaks each completed
        // segment; only the tail is flushed at the end. Every one of those segments has to come
        // from the reply body, otherwise the tag filter leaks the reasoning the same way it did
        // for manual read. Chunking the text simulates the model streaming it.
        val chunks = messageWithReasoning.chunked(3)
        val sourceStream: Stream<String> = stream { chunks.forEach { emit(it) } }

        val spoken = StringBuilder()
        WaifuMessageProcessor.streamTtsText(sourceStream).collect { char ->
            spoken.append(char)
        }

        val streamedText = spoken.toString()
        assertFalse(
            "Auto read streamed reasoning into the spoken buffer: $streamedText",
            streamedText.contains("想想用户")
        )

        val withFilter = speechPipeline(streamedText, listOf(removeXmlTagsPattern))
        val withoutFilter = speechPipeline(streamedText, emptyList())
        assertEquals(
            "Auto read must sound the same regardless of the filter: $withFilter vs $withoutFilter",
            withoutFilter,
            withFilter
        )
        assertEquals(replyBody, withFilter)
    }

    @Test
    fun markdownReplyBody_survivesBothFilterStates() {
        val markdownBody = "## 天气\n\n**今天**多云，最高气温 `24` 度。"
        val message = "<think>$reasoning</think>$markdownBody"

        val withFilter = speechPipeline(message, listOf(removeXmlTagsPattern))
        val withoutFilter = speechPipeline(message, emptyList())

        assertEquals("今天多云，最高气温 24 度。", withFilter)
        assertEquals(
            "Markdown flattening must not depend on the filter: $withFilter vs $withoutFilter",
            withoutFilter,
            withFilter
        )
    }

    @Test
    fun tokenPairedReasoning_neverLeaksAfterAnInnerBareClosingTag() {
        val message = "<think token=\"guard\">$reasoning</think>这段仍是思考。" +
            "</think token=\"guard\">$replyBody"

        assertEquals(replyBody, speechPipeline(message, emptyList()))
        assertEquals(replyBody, speechPipeline(message, listOf(removeXmlTagsPattern)))
    }

    @Test
    fun storedSections_feedTheExistingMarkdownSpeechPipeline() {
        val entity = MessageEntity(
            chatId = "speech-sections",
            sender = "ai",
            sections = MessageSectionStorage.encode(listOf(
                MessageSection.Thinking(reasoning),
                MessageSection.Protocol(provider = "custom:protocol", payload = "协议负载"),
                MessageSection.ToolResult(name = "weather", content = "工具内部结果"),
                MessageSection.Text("**今天**多云，最高气温 `24` 度。"),
            )),
            searchText = "不是朗读正文",
            orderIndex = 0,
        )
        val message = entity.toChatMessage()

        assertEquals(
            "今天多云，最高气温 24 度。",
            WaifuMessageProcessor.cleanContentForWaifu(
                TtsCleaner.cleanForSpeech(message.resolvedSections(), listOf(removeXmlTagsPattern))
            ),
        )
    }
}
