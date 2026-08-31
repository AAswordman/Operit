package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.util.ChatMarkupRegex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for issue #1059: tool round markup was appended to the assistant stream
 * without a line break, so the XML block detector - which only opens a block at a line boundary -
 * treated the tool result as plain model text.
 */
class ToolExecutionManagerMarkupTest {

    private val toolResultMarkup =
        "<tool_result_Xy9Z name=\"zhipu_draw\" status=\"success\">" +
            "<content>{\"filepath\":\"/sdcard/a.png\"}</content></tool_result_Xy9Z>"

    /** Streaming tool call: the provider closes the tool tag without a trailing newline. */
    private val streamedRound = "先画一张\n<tool_aB12 name=\"zhipu_draw\">\n</tool_aB12>"

    /** Non-streaming tool call: the provider emits the tool XML first, then the model text. */
    private val nonStreamedRound =
        "\n<tool_aB12 name=\"zhipu_draw\">\n</tool_aB12>\n画完你自己看"

    @Test
    fun ensureOwnLine_startsToolResultOnItsOwnLineAfterStreamedToolCall() {
        val round = streamedRound + ToolExecutionManager.ensureOwnLine(toolResultMarkup)

        assertToolResultOpensAtLineStart(round)
    }

    @Test
    fun ensureOwnLine_startsToolResultOnItsOwnLineAfterNonStreamedModelText() {
        // The model text ends mid-line, which is what let the raw JSON surface as model output.
        val round = nonStreamedRound + ToolExecutionManager.ensureOwnLine(toolResultMarkup)

        assertToolResultOpensAtLineStart(round)
    }

    @Test
    fun ensureOwnLine_keepsAssistantTextEmittedBeforeTheToolCallIntact() {
        val round = streamedRound + ToolExecutionManager.ensureOwnLine(toolResultMarkup)

        // The text the model produced before calling the tool must survive exactly once.
        assertEquals(1, occurrencesOf(round, "先画一张"))
        assertEquals(1, occurrencesOf(round, "<tool_result_Xy9Z"))
    }

    @Test
    fun ensureOwnLine_separatesConsecutiveToolResultsOfOneBatch() {
        val secondResult = toolResultMarkup.replace("Xy9Z", "Qw7K")
        val round =
            streamedRound +
                ToolExecutionManager.ensureOwnLine(toolResultMarkup) +
                ToolExecutionManager.ensureOwnLine(secondResult)

        assertToolResultOpensAtLineStart(round)
        assertEquals(2, ChatMarkupRegex.toolResultTag.findAll(round).count())
        assertTrue(round.contains("</tool_result_Xy9Z>\n\n<tool_result_Qw7K"))
    }

    @Test
    fun ensureOwnLine_doesNotStackRedundantBlankLines() {
        assertEquals("\ndone\n", ToolExecutionManager.ensureOwnLine("\ndone\n"))
    }

    private fun assertToolResultOpensAtLineStart(round: String) {
        val openingTagIndex = round.indexOf("<tool_result_")
        assertTrue("tool result markup missing", openingTagIndex > 0)
        assertEquals(
            "tool result markup must open a new line so it is parsed as XML, not as model text",
            '\n',
            round[openingTagIndex - 1]
        )
    }

    private fun occurrencesOf(haystack: String, needle: String): Int =
        haystack.split(needle).size - 1
}
