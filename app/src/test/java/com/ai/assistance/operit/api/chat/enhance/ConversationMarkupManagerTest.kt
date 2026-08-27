package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.ChatMarkupRegex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationMarkupManagerTest {

    @Test
    fun `tool call id is retained in execution result markup`() {
        val markup =
            ConversationMarkupManager.formatToolResultForMessage(
                ToolResult(
                    toolName = "list_files",
                    success = true,
                    result = StringResultData("files"),
                    toolCallId = "daxkro1vn"
                )
            )

        val callId =
            ChatMarkupRegex.toolCallIdAttr.find(markup)
                ?.groupValues
                ?.getOrNull(1)
        assertEquals("daxkro1vn", callId)
    }

    @Test
    fun `tool results without a call id keep the released markup shape`() {
        val markup =
            ConversationMarkupManager.formatToolResultForMessage(
                ToolResult(
                    toolName = "query_memory",
                    success = true,
                    result = StringResultData("memory")
                )
            )

        assertNull(ChatMarkupRegex.toolCallIdAttr.find(markup))
    }
}
