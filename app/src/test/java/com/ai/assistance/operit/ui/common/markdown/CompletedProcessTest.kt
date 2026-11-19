package com.ai.assistance.operit.ui.common.markdown

import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletedProcessTest {
    private fun text(value: String) = MarkdownNodeStable(MarkdownProcessorType.PLAIN_TEXT, value, emptyList())
    private fun xml(value: String) = MarkdownNodeStable(MarkdownProcessorType.XML_BLOCK, value, emptyList())

    @Test fun keepsAllFinalParagraphsAfterMultipleToolRounds() {
        val nodes = listOf(text("Checking"), xml("<tool_a name=\"read\"></tool_a>"),
            text("Investigating"), xml("<tool_result_a><content>result</content></tool_result_a>"),
            text("Final heading"), text("First paragraph"), text("Second paragraph"))
        assertEquals(3, completedProcessEnd(nodes))
    }

    @Test fun ordinaryRepliesAndToolOnlyResponsesStayVisible() {
        assertEquals(-1, completedProcessEnd(listOf(text("One"), text("Two"))))
        assertEquals(-1, completedProcessEnd(listOf(text("Working"), xml("<tool_a name=\"read\"></tool_a>"), text("\n"))))
    }

    @Test fun reasoningIsFoldedOnlyWhenAnAnswerFollows() {
        assertEquals(0, completedProcessEnd(listOf(xml("<think>reasoning</think>"), text("Answer"))))
        assertEquals(-1, completedProcessEnd(listOf(xml("<think>reasoning</think>"))))
    }

    @Test fun includesTrailingWhitespaceAndMetadataInTheFoldedGroup() {
        val nodes = listOf(xml("<tool_a name=\"read\"></tool_a>"),
            xml("<tool_result_a>done</tool_result_a>"), text("\n"),
            xml("<meta>hidden</meta>"), text("\n"), text("Final answer"))
        assertEquals(4, completedProcessEnd(nodes))
    }

    @Test fun visibleXmlIsPartOfTheFinalAnswer() {
        for (tag in listOf("html", "font", "details")) {
            assertEquals(1, completedProcessEnd(listOf(
                xml("<tool_a name=\"read\"></tool_a>"), text("\n"),
                xml("<$tag>Final content</$tag>"), text("More explanation"))))
        }
    }
}
