package com.ai.assistance.operit.api.chat.enhance

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ToolExecutionManagerTest {

    @Test
    fun extractToolInvocations_shouldKeepAllToolBlocksInSameChunk() = runBlocking {
        val response = """
            <tool_A1 name="visit_web"><param name="url">https://www.baidu.com</param></tool_A1>
            <tool_B2 name="visit_web"><param name="url">https://www.bing.com</param></tool_B2>
            <tool_C3 name="visit_web"><param name="url">https://www.github.com</param></tool_C3>
        """.trimIndent()

        val invocations = ToolExecutionManager.extractToolInvocations(response)

        assertEquals(3, invocations.size)
        assertEquals(
            listOf(
                "https://www.baidu.com",
                "https://www.bing.com",
                "https://www.github.com"
            ),
            invocations.map { invocation ->
                invocation.tool.parameters.first { it.name == "url" }.value
            }
        )
    }

    @Test
    fun extractToolInvocations_onlyNative_skipsUnmarkedTags() = runBlocking {
        val response = """
            <tool_A1 name="read_file"><param name="path">/etc/hosts</param></tool_A1>
        """.trimIndent()

        val invocations = ToolExecutionManager.extractToolInvocations(response, onlyNative = true)

        assertEquals(0, invocations.size)
    }

    @Test
    fun extractToolInvocations_onlyNative_keepsMarkedTags() = runBlocking {
        val response = """
            <tool_A1 name="read_file" data-origin="native_tool_call"><param name="path">/etc/hosts</param></tool_A1>
        """.trimIndent()

        val invocations = ToolExecutionManager.extractToolInvocations(response, onlyNative = true)

        assertEquals(1, invocations.size)
        assertEquals("/etc/hosts", invocations[0].tool.parameters.first { it.name == "path" }.value)
    }

    @Test
    fun extractToolInvocations_onlyNative_skipsUnmarkedButKeepsMarked() = runBlocking {
        val response = """
            <tool_A1 name="read_file"><param name="path">/tmp/unmarked</param></tool_A1>
            <tool_B2 name="read_file" data-origin="native_tool_call"><param name="path">/tmp/marked</param></tool_B2>
        """.trimIndent()

        val invocations = ToolExecutionManager.extractToolInvocations(response, onlyNative = true)

        assertEquals(1, invocations.size)
        assertEquals("/tmp/marked", invocations[0].tool.parameters.first { it.name == "path" }.value)
    }
}
