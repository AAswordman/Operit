package com.ai.assistance.operit.core.chat.tools

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
}
