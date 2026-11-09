package com.ai.assistance.operit.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SubagentResultExtractorTest {
    @Test
    fun returnsOnlyTextAfterTheLastToolResult() {
        val content =
            """
            I will inspect the project.
            <tool name="read_file"><param name="path">large.kt</param></tool>
            <tool_result name="read_file" status="success"><content>very large output</content></tool_result>
            The implementation is in AuthRepository.kt and is called by LoginViewModel.
            """.trimIndent()

        val result = SubagentResultExtractor.extract(content, "No result")

        assertEquals(
            "The implementation is in AuthRepository.kt and is called by LoginViewModel.",
            result,
        )
        assertFalse(result.contains("very large output"))
        assertFalse(result.contains("<tool"))
    }

    @Test
    fun removesThinkingAndInternalMetadataFromTerminalText() {
        val content =
            """
            <think>private reasoning</think>
            <tool name="search"></tool>
            <tool_result name="search" status="success"><content>raw result</content></tool_result>
            <status type="complete">done</status>
            <meta provider="gemini:thought_signature">signature</meta>
            Final answer.
            """.trimIndent()

        assertEquals("Final answer.", SubagentResultExtractor.extract(content, "No result"))
    }

    @Test
    fun doesNotReturnToolPayloadWhenNoTerminalProseExists() {
        val content =
            """
            I will search first.
            <tool name="search"></tool>
            <tool_result name="search" status="success"><content>raw result</content></tool_result>
            """.trimIndent()

        assertEquals(
            "未返回文本结果",
            SubagentResultExtractor.extract(content, "未返回文本结果"),
        )
    }
}
