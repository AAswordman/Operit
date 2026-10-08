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

    @Test fun toolResultContentKeepsInternalContentTagsAcrossStorage() {
        val payload = "前半</content><content>示例</content>尾部"
        val raw = "<tool_result_exec name=\"read_file\" status=\"success\"><content>$payload</content></tool_result_exec>"
        val section = MessageSectionCodec.parse(raw).single() as MessageSection.ToolResult
        assertEquals(payload, section.content)
        assertEquals(raw, MessageSectionCodec.render(listOf(section)))
        val restored = MessageSectionStorage.decode(MessageSectionStorage.encode(listOf(section)))
        assertEquals(payload, (restored.single() as MessageSection.ToolResult).content)
        assertEquals(raw, MessageSectionCodec.render(restored))
    }

    @Test fun codeExamplesRemainTextInsteadOfToolCards() {
        val tool = "<tool_exec name=\"run\">参数</tool_exec>"
        for (example in listOf("```xml\n$tool\n```", "~~~xml\n$tool\n~~~", "说明 `$tool` 尾部", "````xml\n```\n$tool\n````")) {
            val sections = MessageSectionCodec.parse(example)
            assertEquals(listOf(MessageSection.Text(example)), sections)
        }
    }

    @Test fun unclosedFenceKeepsToolExampleAsText() {
        val content = "```xml\n<tool_exec name=\"run\">参数</tool_exec>"
        assertEquals(listOf(MessageSection.Text(content)), MessageSectionCodec.parse(content))
    }

    @Test fun xmlContainerKeepsNestedToolExampleAndFollowingToolIsRecognized() {
        val container = "<details><tool_exec name=\"example\">示例</tool_exec></details>"
        val real = "<tool_live name=\"run\">参数</tool_live>"
        val sections = MessageSectionCodec.parse(container + real)
        assertEquals(2, sections.size)
        assertEquals(MessageSection.Text(container), sections[0])
        assertEquals("run", (sections[1] as MessageSection.ToolCall).name)
    }

    @Test fun unclosedCodeInsideResultDoesNotSwallowFollowingSections() {
        val raw = "<tool_result_exec name=\"read_file\"><content>```xml\n代码</content></tool_result_exec>"
        val sections = MessageSectionCodec.parse(raw + "<tool_live name=\"run\">参数</tool_live>")
        assertEquals(2, sections.size)
        assertEquals("```xml\n代码", (sections[0] as MessageSection.ToolResult).content)
        assertEquals("run", (sections[1] as MessageSection.ToolCall).name)
    }

    @Test fun storedRawRestoresPreviouslyTruncatedToolContent() {
        val payload = "前</content>后"
        val raw = "<tool_result_exec name=\"read_file\" status=\"success\"><content>$payload</content></tool_result_exec>"
        val old = MessageSection.ToolResult("read_file", "success", "前", raw)
        val restored = MessageSectionStorage.decode(MessageSectionStorage.encode(listOf(old)))
        assertEquals(payload, (restored.single() as MessageSection.ToolResult).content)
        assertEquals(raw, MessageSectionCodec.render(restored))
    }

    private fun sample(): String = "<think>draft</think><tool_abcd name=\"use_package\"><param name=\"package_name\">extended_file_tools</param></tool_abcd>answer"
    private fun protocolSample(): String = "answer<meta provider=\"openai:responses_reasoning\">cGF5bG9hZA==</meta>"
    private fun toolResultSample(): String = "<tool_result_qgmS name=\"use_package\" status=\"success\"><content>ok</content></tool_result_qgmS>"

    @Test fun parse_tokenBlockKeepsInnerClosingTagsInThinking() {
        val thought = "前半</think>后半</thinking></operit_thinking>尾部"
        val raw = "<think token=\"aBc4\">" + thought + "</think token=\"aBc4\">回答"
        val sections = MessageSectionCodec.parse(raw)
        assertEquals(listOf(MessageSection.Thinking(thought), MessageSection.Text("回答")), sections)
        assertEquals(raw, MessageSectionCodec.render(sections))
    }

    @Test fun parse_unclosedTokenBlockDoesNotCreateTextSection() {
        val thought = "前半</think>后半"
        val raw = "<think token=\"aBc4\">" + thought
        val sections = MessageSectionCodec.parse(raw)
        assertEquals(listOf(MessageSection.Thinking(thought)), sections)
        assertEquals(raw, MessageSectionCodec.render(sections))
    }

    @Test fun render_thinkingMarkupIsStableAcrossRenders() {
        val sections = listOf(MessageSection.Thinking("包含 </think> 的草稿"), MessageSection.Text("回答"))
        val rendered = MessageSectionCodec.render(sections)
        assertEquals(rendered, MessageSectionCodec.render(sections))
        assertEquals(sections, MessageSectionCodec.parse(rendered))
        org.junit.Assert.assertTrue(rendered.startsWith("<think token="))
    }

    @Test fun unsupportedThinkingNameRemainsTextWhenSectionsAreParsed() {
        for (raw in listOf(
            "<operit_thinking>普通内容</operit_thinking>回答",
            "<operit_thinking token=\"aBc4\">普通内容</operit_thinking token=\"aBc4\">回答",
        )) {
            val sections = MessageSectionCodec.parse(raw)
            assertEquals(listOf(MessageSection.Text(raw)), sections)
            assertEquals(raw, MessageSectionCodec.render(sections))
        }
    }

}
