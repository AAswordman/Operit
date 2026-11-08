package com.ai.assistance.operit.core.chat

import android.os.SystemClock
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.util.logging.AppLogger
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class AIMessageManagerSummaryTest {
    @Test
    fun legacySummaryIsCleanedWithoutChangingStoredMessagesOrAssistantReplay() {
        for (provider in listOf(
            "openai:responses_reasoning",
            "openai:responses_output_item",
            "gemini:thought_signature",
        )) {
            val meta = "<meta provider=\"$provider\">${"A".repeat(16_384)}</meta>"
            val rawSummary = meta + "已完成目录整理" + meta + "，继续处理摘要。"
            val summary = ChatMessage(sender = "summary", content = rawSummary, timestamp = 2L)
            val assistant = ChatMessage(sender = "ai", content = "新的回答" + meta, timestamp = 3L)
            val messages = listOf(
                ChatMessage(sender = "user", content = "摘要之前的消息", timestamp = 1L),
                summary,
                assistant,
            )

            assertEquals(
                listOf(
                    PromptTurn(PromptTurnKind.SUMMARY, "已完成目录整理，继续处理摘要。"),
                    PromptTurn(PromptTurnKind.ASSISTANT, assistant.content),
                ),
                memoryFrom(messages),
            )
            assertEquals(rawSummary, summary.content)
            assertEquals("新的回答" + meta, assistant.content)
        }
    }

    @Test
    fun unfinishedLegacySummaryProtocolDoesNotEnterTheNextRequest() {
        for (suffix in listOf(
            "<meta provider=\"openai:responses_reasoning\">unfinished-payload",
            "<meta provider=\"openai:responses_reasoning\"",
        )) {
            val messages = listOf(
                ChatMessage(sender = "summary", content = "保留摘要正文" + suffix, timestamp = 1L),
                ChatMessage(sender = "user", content = "继续", timestamp = 2L),
            )

            assertEquals(
                listOf(
                    PromptTurn(PromptTurnKind.SUMMARY, "保留摘要正文"),
                    PromptTurn(PromptTurnKind.USER, "继续"),
                ),
                memoryFrom(messages),
            )
        }
    }

    @Test
    fun protocolOnlySummaryDoesNotCreateAnEmptyContextTurn() {
        val messages = listOf(
            ChatMessage(
                sender = "summary",
                content = " \n<meta provider=\"openai:responses_reasoning\">payload</meta>\n",
                timestamp = 1L,
            ),
            ChatMessage(sender = "user", content = "继续", timestamp = 2L),
        )

        assertEquals(listOf(PromptTurn(PromptTurnKind.USER, "继续")), memoryFrom(messages))
    }

    @Test
    fun summaryCodeExamplesAndOrdinaryMetaKeepTheirText() {
        val example = "<meta provider=\"openai:responses_reasoning\">example</meta>"
        val content = "保留 `$example` 和代码块：\n```xml\n$example\n```\n" +
            "<meta charset=\"utf-8\"><meta provider=\"custom\">普通内容</meta>"
        val messages = listOf(ChatMessage(sender = "summary", content = content, timestamp = 1L))

        assertEquals(listOf(PromptTurn(PromptTurnKind.SUMMARY, content)), memoryFrom(messages))
    }

    private fun memoryFrom(messages: List<ChatMessage>): List<PromptTurn> {
        // 请求历史组装不依赖设备，隔离计时和日志的 Android 静态入口。
        Mockito.mockStatic(SystemClock::class.java).use {
            Mockito.mockStatic(AppLogger::class.java).use {
                return AIMessageManager.getMemoryFromMessages(messages)
            }
        }
    }
}
