package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageSectionCodecTest {
    @Test fun parse_splitsThinkingToolAndText() {
        val sections = MessageSectionCodec.parse(sample())
        assertEquals(3, sections.size)
        assertEquals("draft", (sections[0] as MessageSection.Thinking).content)
        val call = sections[1] as MessageSection.ToolCall
        assertEquals("use_package", call.name)
        assertEquals("extended_file_tools", call.params["package_name"])
        assertEquals("answer", (sections[2] as MessageSection.Text).content)
    }

    @Test fun parse_unmatchedThinkingCloseRemainsLiteralText() {
        val sections = MessageSectionCodec.parse("<think>before </think> after</think>answer")
        assertEquals(
            listOf(
                MessageSection.Thinking("before "),
                MessageSection.Text(" after</think>answer"),
            ),
            sections,
        )
    }

    @Test fun displaySections_hidesProtocolPayload() {
        val message = ChatMessage(sender = "ai", content = protocolSample())
        assertEquals(1, message.displaySections().size)
        assertEquals("answer", (message.displaySections().single() as MessageSection.Text).content)
        val protocol = message.sections.filterIsInstance<MessageSection.Protocol>().single()
        assertEquals("openai:responses_reasoning", protocol.provider)
    }

    @Test fun render_preservesOriginalToolMarkup() {
        val content = toolResultSample()
        assertEquals(content, MessageSectionCodec.render(MessageSectionCodec.parse(content)))
    }

    private fun sample(): String = "<think>draft</think><tool_abcd name=\"use_package\"><param name=\"package_name\">extended_file_tools</param></tool_abcd>answer"
    private fun protocolSample(): String = "answer<meta provider=\"openai:responses_reasoning\">cGF5bG9hZA==</meta>"
    private fun toolResultSample(): String = "<tool_result_qgmS name=\"use_package\" status=\"success\"><content>ok</content></tool_result_qgmS>"
}
