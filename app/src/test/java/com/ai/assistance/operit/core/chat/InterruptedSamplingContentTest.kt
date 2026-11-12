package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.services.core.finalizeInterruptedStreamingContent
import com.ai.assistance.operit.util.stream.StreamLogger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class InterruptedSamplingContentTest {
    @Test
    fun interruptedToolArgumentsAreNeverRepairedIntoHistory() = runBlocking {
        StreamLogger.setEnabled(false)
        try {
            for (tool in listOf(
                "<tool name=\"write_file\"><param name=\"path\">unfinished",
                "<tool_abcd name=\"write_file\"><param name=\"path\">x</param></tool_abcd>",
            )) {
                assertEquals("Answer\n", retainBeforeUnexecutedTool("Answer\n$tool"))
            }
            val examples = "```xml\n<tool name=\"example\"/>\n```\n`<tool name=\"inline\"/>`\n"
            assertEquals(examples, retainBeforeUnexecutedTool(examples))
            val reasoning = "<think>Example <tool name=\"example\"/></think>\n"
            assertEquals(reasoning, retainBeforeUnexecutedTool(reasoning))
        } finally {
            StreamLogger.setEnabled(true)
        }
    }

    @Test
    fun interruptedReasoningClosesWithoutChangingCodeExamples() {
        assertEquals("<think>reasoning</think>", finalizeInterruptedStreamingContent("<think>reasoning"))
        val example = "```xml\n<think>\n```"
        assertEquals(example, finalizeInterruptedStreamingContent(example))
        val completed = "<think>done</think>answer"
        assertEquals(completed, finalizeInterruptedStreamingContent(completed))
    }
}
