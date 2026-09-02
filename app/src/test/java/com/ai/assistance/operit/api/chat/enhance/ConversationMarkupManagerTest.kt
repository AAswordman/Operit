package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMarkupManagerTest {
    @Test
    fun requestedMarkupLimitBoundsToolResultPayload() {
        val markup =
            ConversationMarkupManager.formatToolResultForMessage(
                result =
                    ToolResult(
                        toolName = "read_file_part",
                        success = true,
                        result = StringResultData("x".repeat(4_096)),
                    ),
                maxMessageChars = 256,
            )

        assertTrue(markup.length <= 256)
        assertTrue(markup.contains("[工具结果过长，已截断]"))
    }

    @Test
    fun requestedMarkupLimitAlsoBoundsExtractedImageLinks() {
        val imageLinks =
            (1..32).joinToString(separator = "") { index ->
                buildString {
                    append(60.toChar())
                    append("link type=\"image\" id=\"image-")
                    append(index)
                    append("\"/")
                    append(62.toChar())
                }
            }
        val markup =
            ConversationMarkupManager.formatToolResultForMessage(
                result =
                    ToolResult(
                        toolName = "visit_web",
                        success = true,
                        result = StringResultData(imageLinks),
                    ),
                maxMessageChars = 256,
            )

        assertTrue(markup.contains("link type=\"image\""))
        assertTrue(markup.length <= 256)
    }
}
