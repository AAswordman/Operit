package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.core.chat.protocol.MessageSectionCodec
import com.ai.assistance.operit.util.markdown.MarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MessageSectionMarkdownTest {
    @Test fun toolSectionsBecomeCardsWithoutReparsingTheirBodies() = runBlocking {
        val payload = "前</content><think>普通示例</think>尾"
        val raw = "<tool_result_exec name=\"read_file\" status=\"success\"><content>$payload</content></tool_result_exec>"
        val sections = MessageSectionCodec.parse("说明" + raw + "回答")
        val parsedText = mutableListOf<String>()
        val nodes = parseMessageSectionsToNodes(sections) { text ->
            parsedText.add(text)
            listOf(MarkdownNode(MarkdownProcessorType.PLAIN_TEXT, text))
        }
        assertEquals(listOf("说明", "回答"), parsedText)
        assertEquals(listOf(MarkdownProcessorType.PLAIN_TEXT, MarkdownProcessorType.XML_BLOCK, MarkdownProcessorType.PLAIN_TEXT), nodes.map { it.type })
        assertEquals(raw, nodes[1].content.toString())
        assertEquals(payload, (sections[1] as MessageSection.ToolResult).content)
    }

    @Test fun protocolPayloadDoesNotBecomeAVisibleNode() = runBlocking {
        val sections = listOf(MessageSection.Protocol("provider", "hidden"), MessageSection.ToolCall("run"))
        val nodes = parseMessageSectionsToNodes(sections) { error("没有正文需要 Markdown 解析") }
        assertEquals(1, nodes.size)
        assertEquals(MarkdownProcessorType.XML_BLOCK, nodes.single().type)
    }

    @Test fun cacheSeparatesLiteralTextFromToolSectionsWithSameMarkup() {
        val tool = MessageSection.ToolCall("run")
        val content = MessageSectionCodec.render(listOf(tool))
        assertNotEquals(messageSectionCacheKey(content, listOf(tool)), messageSectionCacheKey(content, listOf(MessageSection.Text(content))))
    }

    @Test fun textProjectionHidesMetadataInsideUnfinishedXmlWithoutChangingStoredText() = runBlocking {
        val raw = "<unfinished>正文<meta provider=\"openai:responses_reasoning\">secret</meta>尾"
        val stored = MessageSection.Text(raw)
        val received = mutableListOf<String>()
        parseMessageSectionsToNodes(listOf(stored)) { text ->
            received.add(text)
            emptyList()
        }
        assertEquals(listOf("<unfinished>正文尾"), received)
        assertEquals(raw, stored.content)
    }

    @Test fun closedProtocolCodeExampleRemainsVisibleInStaticText() = runBlocking {
        val raw = "```xml\n<meta provider=\"openai:responses_reasoning\">example</meta>\n```"
        val received = mutableListOf<String>()
        parseMessageSectionsToNodes(listOf(MessageSection.Text(raw))) { text ->
            received.add(text)
            emptyList()
        }
        assertEquals(listOf(raw), received)
    }

    @Test fun unfinishedCodeDoesNotRestoreProtocolWhenStaticRenderingTakesOver() = runBlocking {
        val raw = "```xml\n正文<meta provider=\"gemini:thought_signature\">secret</meta>尾"
        val received = mutableListOf<String>()
        parseMessageSectionsToNodes(listOf(MessageSection.Text(raw))) { text ->
            received.add(text)
            emptyList()
        }
        assertEquals(listOf("```xml\n正文尾"), received)
    }

}
