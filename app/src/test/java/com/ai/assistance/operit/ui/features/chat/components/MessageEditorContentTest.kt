package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.util.ThinkingMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageEditorContentTest {
    @Test
    fun addingThinkingTag_generatesMatchingRandomToken() {
        val part = createMessageEditorXmlPart("手动思考内容", "think", "")
        val token = ThinkingMarkup.tokenOf(part.attributes.orEmpty())
        assertNotNull(token)
        assertTrue(token!!.matches(Regex("[A-Za-z0-9]{4,5}")))

        val markup = recomposeMessageFromParts(listOf(part))
        assertEquals(ThinkingMarkup.openTag(token) + part.content + ThinkingMarkup.closeTag(token), markup)
        assertTrue(ThinkingMarkup.isClosed(markup))
        assertEquals(part.content, ThinkingMarkup.body(markup))
    }

    @Test
    fun newThinkingContent_canContainPlainClosingTagAndKeepFollowingAnswer() {
        val thought = "举例：</think> 后面仍是手动思考。"
        val part = createMessageEditorXmlPart(thought, "think", "")
        val content = recomposeMessageFromParts(listOf(part, ParsedMessagePart(PartType.TEXT, "回答正文")))

        assertEquals("回答正文" to listOf(thought), ThinkingMarkup.extract(content))
        val reparsed = parseMessageContentForEditor(content)
        assertEquals(thought, reparsed[0].content)
        assertEquals("回答正文", reparsed[1].content)
    }

    @Test
    fun savingExistingThinkingPart_preservesTokenAndOtherAttributes() {
        val part = createMessageEditorXmlPart("修改后的内容", "think", " id=\"manual\" token=\"aBc4\" ")
        val markup = recomposeMessageFromParts(listOf(part))

        assertEquals("aBc4", ThinkingMarkup.tokenOf(part.attributes.orEmpty()))
        assertEquals("<think id=\"manual\" token=\"aBc4\">修改后的内容</think token=\"aBc4\">", markup)
        assertTrue(ThinkingMarkup.isClosed(markup))
    }

    @Test
    fun tokenCreation_doesNotReadTokenTextFromOtherAttributeValues() {
        val attributes = "data-token=\"fake\" title='token=\"fake\"'"
        val part = createMessageEditorXmlPart("内容", "think", attributes)
        val token = ThinkingMarkup.tokenOf(part.attributes.orEmpty())

        assertNotNull(token)
        assertTrue(token!!.matches(Regex("[A-Za-z0-9]{4,5}")))
        assertTrue(part.attributes!!.startsWith(attributes + " "))
        assertTrue(ThinkingMarkup.isClosed(recomposeMessageFromParts(listOf(part))))
    }

    @Test
    fun parsingTokenThinking_ignoresPlainAndWrongTokenClosers() {
        val thought = "前半。</think>后半。</think token=\"wrong\">尾部。"
        val content = "前文" + ThinkingMarkup.openTag("aBc4") + thought + ThinkingMarkup.closeTag("aBc4") + "回答"
        val parts = parseMessageContentForEditor(content)

        assertEquals(3, parts.size)
        assertEquals(PartType.TEXT, parts[0].type)
        assertEquals("前文", parts[0].content)
        assertEquals(PartType.XML, parts[1].type)
        assertEquals("think", parts[1].tag)
        assertEquals(thought, parts[1].content)
        assertTrue(parts[1].closed)
        assertEquals("回答", parts[2].content)
        assertEquals(content, recomposeMessageFromParts(parts))
    }

    @Test
    fun editingThinkingBody_andRepeatedModeSwitches_keepTheSameToken() {
        val content = ThinkingMarkup.openTag("aBc4") + "原内容" + ThinkingMarkup.closeTag("aBc4") + "\n回答"
        val parts = parseMessageContentForEditor(content).toMutableList()
        parts[0] = parts[0].copy(content = "修改内容。</think>继续。")
        val edited = recomposeMessageFromParts(parts)

        repeat(3) {
            assertEquals(edited, recomposeMessageFromParts(parts))
            assertEquals(edited, recomposeMessageFromParts(parseMessageContentForEditor(edited)))
        }
        assertEquals("\n回答" to listOf("修改内容。</think>继续。"), ThinkingMarkup.extract(edited))
    }

    @Test
    fun parsingLegacyThinking_keepsItUntilExplicitTagSave() {
        val content = "<think>旧版内容</think>回答"
        val parts = parseMessageContentForEditor(content)

        assertEquals(2, parts.size)
        assertEquals("旧版内容", parts[0].content)
        assertNull(ThinkingMarkup.tokenOf(parts[0].attributes.orEmpty()))
        assertEquals(content, recomposeMessageFromParts(parts))

        val saved = createMessageEditorXmlPart(parts[0].content, parts[0].tag!!, parts[0].attributes.orEmpty())
        assertNotNull(ThinkingMarkup.tokenOf(saved.attributes.orEmpty()))
        assertEquals("旧版内容", ThinkingMarkup.body(recomposeMessageFromParts(listOf(saved))))
    }

    @Test
    fun supportedThinkingNames_useTokenPairingWhenSaved() {
        for (name in listOf("think", "thinking")) {
            val part = createMessageEditorXmlPart("内容", name, "")
            val content = recomposeMessageFromParts(listOf(part))
            assertEquals(name, part.tag)
            assertTrue(ThinkingMarkup.isClosed(content))
            assertEquals("内容", parseMessageContentForEditor(content).single().content)
        }
    }

    @Test
    fun unclosedThinking_keepsItsOriginalStateAcrossModeSwitches() {
        val content = ThinkingMarkup.openTag("aBc4") + "尚未闭合。</think>尾部内容"
        val part = parseMessageContentForEditor(content).single()

        assertFalse(part.closed)
        assertEquals("尚未闭合。</think>尾部内容", part.content)
        assertEquals(content, recomposeMessageFromParts(listOf(part)))
    }

    @Test
    fun ordinaryXmlTags_keepNormalClosingSyntaxEvenWithTokenAttribute() {
        val part = createMessageEditorXmlPart("插件内容", "plan", "id=\"1\" token=\"user-value\"")
        val content = recomposeMessageFromParts(listOf(part))

        assertEquals("<plan id=\"1\" token=\"user-value\">插件内容</plan>", content)
        assertEquals("插件内容", parseMessageContentForEditor(content).single().content)
    }

    @Test
    fun whitespaceAndMultipleThinkingParts_surviveRoundTrip() {
        val content = "\n " + ThinkingMarkup.wrap("第一段") + "\n\n" + ThinkingMarkup.wrap("第二段") + "\n"
        val parts = parseMessageContentForEditor(content)

        assertEquals(5, parts.size)
        assertEquals(listOf(PartType.SPACING, PartType.XML, PartType.SPACING, PartType.XML, PartType.SPACING), parts.map { it.type })
        assertEquals("\n ", parts[0].content)
        assertEquals("\n\n", parts[2].content)
        assertEquals("\n", parts[4].content)
        assertEquals(content, recomposeMessageFromParts(parts))
    }

    @Test
    fun selfClosingXmlAndUnmatchedOrdinaryXml_remainLiteralText() {
        val content = "前文<status type=\"completion\"/><think />后文<note>未闭合"
        val parts = parseMessageContentForEditor(content)

        assertEquals(listOf(ParsedMessagePart(PartType.TEXT, content)), parts)
        assertEquals(content, recomposeMessageFromParts(parts))
    }

    @Test
    fun invalidThinkingLikeOpeningIsKeptAsLiteralText() {
        val content = "前文<think.foo>字面内容</think.foo>后文"
        val parts = parseMessageContentForEditor(content)

        assertEquals(listOf(ParsedMessagePart(PartType.TEXT, content)), parts)
        assertEquals(content, recomposeMessageFromParts(parts))
    }

    @Test
    fun otherAttributes_areSpacedWhenTheTagIsCreated() {
        val part = createMessageEditorXmlPart("内容", " think ", "id=\"manual\"")
        val content = recomposeMessageFromParts(listOf(part))

        assertTrue(content.startsWith("<think id=\"manual\" token=\""))
        assertTrue(ThinkingMarkup.isClosed(content))
        assertEquals("内容", parseMessageContentForEditor(content).single().content)
    }

    @Test fun unsupportedThinkingNameDoesNotGenerateToken() {
        val part = createMessageEditorXmlPart("普通内容", "operit_thinking", "")
        assertNull(ThinkingMarkup.tokenOf(part.attributes.orEmpty()))
        val raw = recomposeMessageFromParts(listOf(part))
        assertEquals("<operit_thinking>普通内容</operit_thinking>", raw)
        assertEquals("普通内容", parseMessageContentForEditor(raw).single().content)
    }

    @Test fun unsupportedThinkingNameKeepsOrdinaryXmlClosingWithTokenAttribute() {
        val part = createMessageEditorXmlPart("普通内容", "operit_thinking", "token=\"manual\"")
        val raw = recomposeMessageFromParts(listOf(part))
        assertEquals("<operit_thinking token=\"manual\">普通内容</operit_thinking>", raw)
        assertFalse(ThinkingMarkup.isClosed(raw))
        assertEquals(raw, recomposeMessageFromParts(parseMessageContentForEditor(raw)))
    }

    @Test
    fun thinkingAndProtocolSpacing_doesNotBecomeEditableText() {
        val content = "\r\n  <think token=\"wJCK\">旧思考</think token=\"wJCK\">" +
            "\r\n \t<meta provider=\"openai:responses_reasoning\">{\"id\":\"stored\"}</meta>" +
            "\n\n<think token=\"kxGj\">后续思考</think token=\"kxGj\">\n  回答正文  \n"
        val parts = parseMessageContentForEditor(content)
        val visible = parts.filter { it.type != PartType.SPACING }

        assertEquals(listOf("think", "meta", "think", null), visible.map { it.tag })
        assertEquals(PartType.TEXT, visible.last().type)
        assertEquals("\n  回答正文  \n", visible.last().content)
        assertEquals(content, recomposeMessageFromParts(parts))
    }

    @Test
    fun editingXmlBody_preservesSpacingAndOriginalPartIndexes() {
        val content = "\n<think token=\"aBc4\">原始思考</think token=\"aBc4\"> \r\n\t" +
            "<meta provider=\"openai\">协议正文</meta>\n回答"
        val parts = parseMessageContentForEditor(content).toMutableList()
        assertEquals(PartType.SPACING, parts[0].type)
        parts[1] = parts[1].copy(content = "修改后的思考")
        val edited = content.replace("原始思考", "修改后的思考")

        assertEquals(edited, recomposeMessageFromParts(parts))
        repeat(3) {
            val reparsed = parseMessageContentForEditor(edited)
            assertEquals(parts, reparsed)
            assertEquals(edited, recomposeMessageFromParts(reparsed))
        }
    }

    @Test
    fun whitespaceOnlyMessageAndNewEmptyText_remainEditable() {
        val content = " \n\t\r\n"
        val parts = parseMessageContentForEditor(content)
        assertEquals(listOf(ParsedMessagePart(PartType.TEXT, content)), parts)
        assertEquals(content, recomposeMessageFromParts(parts))

        val newText = ParsedMessagePart(PartType.TEXT, "")
        assertEquals(PartType.TEXT, newText.type)
        assertEquals("", recomposeMessageFromParts(listOf(newText)))
    }

    @Test
    fun blankXmlBody_remainsAnEditableXmlPart() {
        val content = "<meta provider=\"openai\"></meta>\n<think> \t</think>"
        val parts = parseMessageContentForEditor(content)
        assertEquals(listOf(PartType.XML, PartType.SPACING, PartType.XML), parts.map { it.type })
        assertEquals("", parts[0].content)
        assertEquals(" \t", parts[2].content)
        assertEquals(content, recomposeMessageFromParts(parts))
    }

}
