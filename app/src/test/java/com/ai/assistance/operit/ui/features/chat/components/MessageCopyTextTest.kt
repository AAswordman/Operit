package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.data.model.MessageSectionCodec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageCopyTextTest {

    @Test fun cleanMessageContentForCopy_removesMultipleOpenAiReasoningMetadata() {
        val content =
            """
            <meta provider="openai:responses_reasoning">first-payload</meta>
            <tool name="run"><param name="command">pwd</param></tool>
            <meta provider="openai:responses_reasoning">second-payload</meta>
            final answer
            """.trimIndent()

        assertEquals("final answer", cleanMessageContentForCopy(MessageSectionCodec.parse(content)))
    }

    @Test fun cleanMessageContentForCopy_removesGeminiThoughtSignature() {
        val content = "prefix<meta provider=\"gemini:thought_signature\">signature</meta>suffix"

        assertEquals("prefixsuffix", cleanMessageContentForCopy(MessageSectionCodec.parse(content)))
    }

    @Test fun cleanMessageContentForCopy_preservesTextSectionMarkupAndMarkdown() {
        val content = "<meta provider=\"other\">value</meta>\n**answer**"

        assertEquals(content, cleanMessageContentForCopy(listOf(MessageSection.Text(content))))
    }

    @Test fun cleanMessageContentForCopy_preservesHtmlMetaBeforeInternalMetadata() {
        val content =
            "<meta charset=\"utf-8\">visible" +
                "<meta provider=\"openai:responses_reasoning\">payload</meta>answer"

        val sections = listOf(
            MessageSection.Text("<meta charset=\"utf-8\">visible"),
            MessageSection.Protocol("openai:responses_reasoning", "payload"),
            MessageSection.Text("answer"),
        )
        assertEquals(content, MessageSectionCodec.render(sections))
        assertEquals("<meta charset=\"utf-8\">visibleanswer", cleanMessageContentForCopy(sections))
    }

    @Test fun cleanMessageContentForCopy_removesOpenAiResponsesOutputItemMetadata() {
        val content =
            "answer" +
                "<meta provider=\"openai:responses_output_item\">payload</meta>" +
                "<search provider=\"deepseek\"><query>q</query></search>"

        assertEquals("answer", cleanMessageContentForCopy(MessageSectionCodec.parse(content)))
    }

    @Test fun cleanMessageContentForXmlCopy_preservesToolMarkupWithoutProviderMetadata() {
        val content =
            "<meta provider=\"openai:responses_output_item\">payload</meta>" +
                "<tool name=\"run\"><param name=\"command\">pwd</param></tool>" +
                "<tool_result name=\"run\"><content>ok</content></tool_result>"

        assertEquals(
            "<tool name=\"run\"><param name=\"command\">pwd</param></tool>" +
                "<tool_result name=\"run\"><content>ok</content></tool_result>",
            cleanMessageContentForXmlCopy(MessageSectionCodec.parse(content)),
        )
    }

    @Test fun buildSelectedMessagesPlainText_usesMessageOrderAndPlainTextConversion() = runTest {
        val chatHistory =
            listOf(
                ChatMessage(sender = "user", content = "**first**", timestamp = 30L),
                ChatMessage(sender = "ai", content = "second", timestamp = 10L),
                ChatMessage(sender = "user", content = "third", timestamp = 20L),
            )

        val result =
            buildSelectedMessagesPlainText(chatHistory) { markdown ->
                markdown.replace("**", "")
            }

        assertEquals("first\n\nsecond\n\nthird", result)
    }

    @Test fun buildSelectedMessagesPlainText_cleansMetadataAndKeepsOnlyUserAndAi() = runTest {
        val chatHistory =
            listOf(
                ChatMessage(
                    sender = "user",
                    content = "answer<meta provider=\"gemini:thought_signature\">signature</meta>",
                    timestamp = 1L,
                ),
                ChatMessage(sender = "system", content = "hidden", timestamp = 2L),
                ChatMessage(
                    sender = "user",
                    content = "hidden placeholder",
                    timestamp = 3L,
                    displayMode = ChatMessageDisplayMode.HIDDEN_PLACEHOLDER,
                ),
                ChatMessage(sender = "ai", content = "<think>empty</think>", timestamp = 4L),
                ChatMessage(sender = "ai", content = "reply", timestamp = 5L),
            )

        val result =
            buildSelectedMessagesPlainText(chatHistory) { content -> content }

        assertEquals("answer\n\nreply", result)
    }

    @Test fun buildSelectedMessagesPlainText_convertsEachMessageOnceForLargeSelection() = runTest {
        val messageCount = 100
        val messageContent = "x".repeat(4_096)
        val chatHistory =
            List(messageCount) { index ->
                ChatMessage(sender = if (index % 2 == 0) "user" else "ai", content = messageContent)
            }
        var conversionCount = 0

        val result =
            buildSelectedMessagesPlainText(chatHistory) { content ->
                conversionCount += 1
                content
            }

        assertEquals(messageCount, conversionCount)
        assertEquals(messageCount * messageContent.length + (messageCount - 1) * 2, result.length)
    }

    @Test fun cleanMessageContentForCopy_tokenThoughtNeverLeaksAfterFakeClose() {
        val raw = "前文<think token=\"aBc4\">前半</think>后半</think token=\"aBc4\">回答"
        assertEquals("前文回答", cleanMessageContentForCopy(MessageSectionCodec.parse(raw)))
    }

    @Test fun buildMessageCopyContent_hidesEveryProtocolProviderAndKeepsStoredPayload() {
        for (provider in listOf("openai", "openai:responses_reasoning", "gemini:thought_signature", "custom")) {
            val raw = "VISIBLE_BEGIN<meta provider=\"$provider\">META_HIDDEN</meta>VISIBLE_END"
            val message = ChatMessage(sender = "ai", content = raw)

            assertEquals(
                MessageCopyContent("VISIBLE_BEGINVISIBLE_END", "VISIBLE_BEGINVISIBLE_END"),
                buildMessageCopyContent(message),
            )
            assertEquals(raw, message.content)
            assertEquals(raw, com.ai.assistance.operit.data.model.MessageSectionCodec.render(message.resolvedSections()))
        }
    }

    @Test fun buildSelectedMessagesPlainText_hidesGenericProtocolPayloads() = runTest {
        val message = ChatMessage(
            sender = "ai",
            content = "VISIBLE_BEGIN<meta provider=\"openai\">META_HIDDEN</meta>VISIBLE_END",
        )
        assertEquals(
            "VISIBLE_BEGINVISIBLE_END",
            buildSelectedMessagesPlainText(listOf(message)) { it },
        )
    }

    @Test fun buildMessageCopyContent_preservesLiteralProtocolCodeInXmlSource() {
        val code = "`<meta provider=\"openai\">example</meta>`"
        val message = ChatMessage(sender = "ai", content = code)
        assertEquals(MessageCopyContent(code, code), buildMessageCopyContent(message))
    }

    @Test fun buildMessageCopyContent_toolResultThinkingExamplesNeverLeakOrConsumeAnswer() {
        for (example in listOf("<think>", "<thinking>", "<think token=\"sample\">")) {
            val toolResult =
                "<tool_result_PB3X name=\"github:terminal_exec\" status=\"success\">" +
                    "<content>source contains $example without its closing example</content>" +
                    "</tool_result_PB3X>"
            val raw = "BEFORE" + toolResult + "AFTER"
            val message = ChatMessage(sender = "ai", content = raw)

            assertEquals(1, message.displaySections().filterIsInstance<MessageSection.ToolResult>().size)
            assertEquals(MessageCopyContent("BEFOREAFTER", raw), buildMessageCopyContent(message))
            assertEquals(raw, message.content)
            assertEquals(raw, MessageSectionCodec.render(message.resolvedSections()))
        }
    }

    @Test fun buildMessageCopyContent_toolParametersCannotConsumeLaterSections() {
        val call =
            "<tool_A1b2 name=\"run\"><param name=\"command\">print('<think>')</param></tool_A1b2>"
        val result = "<tool_result_A1b2 name=\"run\"><content>ok</content></tool_result_A1b2>"
        val thought = "<think token=\"Ab12\">private</think token=\"Ab12\">"
        val raw = "BEFORE" + call + "BETWEEN" + result + thought + "AFTER"

        assertEquals(
            MessageCopyContent("BEFOREBETWEENAFTER", raw),
            buildMessageCopyContent(ChatMessage(sender = "ai", content = raw)),
        )
    }

    @Test fun buildMessageCopyContent_preservesInlineAndFencedToolExamples() {
        val example = "<tool_result_EX name=\"run\"><content><think></content></tool_result_EX>"
        for (code in listOf("`$example`", "``$example``", "```xml\n$example\n```", "~~~~xml\n$example\n~~~~")) {
            val raw = "BEFORE\n\n$code\n\nAFTER"
            val message = ChatMessage(sender = "ai", content = raw)

            assertEquals(MessageCopyContent(raw, raw), buildMessageCopyContent(message))
        }
    }

    @Test fun buildMessageCopyContent_preservesExistingTextSectionClassification() {
        val literal = "<tool_result_EX name=\"run\"><content><think></content></tool_result_EX>"
        val container = "<details><summary>Example</summary>$literal</details>"
        val sections = listOf(MessageSection.Text(literal), MessageSection.Text(container))
        val raw = MessageSectionCodec.render(sections)
        val message = ChatMessage(sender = "ai", content = raw, sections = sections)

        assertEquals(MessageCopyContent(raw, raw), buildMessageCopyContent(message))
        assertEquals(sections, message.resolvedSections())
    }

    @Test fun buildMessageCopyContent_filtersAllNonTextSectionsIncludingSelfClosingTools() {
        val raw =
            "BEFORE<tool_R1 name=\"run\"/><tool_result_R1 name=\"run\"/>" +
                "<think token=\"Ab12\">private</think token=\"Ab12\">" +
                "<search>search data</search><status type=\"complete\"/>" +
                "<meta provider=\"custom\">protocol data</meta>AFTER"
        val message = ChatMessage(sender = "ai", content = raw)

        assertEquals("BEFOREAFTER", buildMessageCopyContent(message).markdownSource)
        assertEquals(raw.replace("<meta provider=\"custom\">protocol data</meta>", ""),
            buildMessageCopyContent(message).xmlSource)
        assertEquals(raw, message.content)
    }

    @Test fun buildSelectedMessagesPlainText_neverSendsToolPayloadToMarkdownConversion() = runTest {
        val toolResult =
            "<tool_result_PB3X name=\"run\"><content>source: <think token=\"sample\"></content>" +
                "</tool_result_PB3X>"
        val messages = listOf(
            ChatMessage(sender = "user", content = "question"),
            ChatMessage(sender = "ai", content = "BEFORE" + toolResult + "AFTER"),
        )
        val convertedInputs = mutableListOf<String>()

        val text = buildSelectedMessagesPlainText(messages) { markdown ->
            convertedInputs.add(markdown)
            markdown
        }

        assertEquals(listOf("question", "BEFOREAFTER"), convertedInputs)
        assertEquals("question\n\nBEFOREAFTER", text)
    }
}
