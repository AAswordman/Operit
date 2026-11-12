package com.ai.assistance.operit.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatBranchRepositoryTest {
    @Test
    fun `extracts exact persisted tool call ids from copied markup`() {
        val contents =
            listOf(
                """<tool name="task" call_id="call-parent">...</tool>""",
                """<tool_result name="task" call_id='call-variant'>...</tool_result>""",
                """plain text mentioning call-ignored without an attribute""",
            )

        assertEquals(
            setOf("call-parent", "call-variant"),
            extractPersistedToolCallIds(contents),
        )
    }

    @Test
    fun `remaps task results and continuation parameters without replacing plain text`() {
        val content =
            """<task id="old-task" state="completed">old-task</task>""" +
                """<tool name="task"><param name="task_id">old-task</param></tool>"""

        assertEquals(
            """<task id="new-task" state="completed">old-task</task>""" +
                """<tool name="task"><param name="task_id">new-task</param></tool>""",
            remapPersistedTaskIds(content, mapOf("old-task" to "new-task")),
        )
    }
}
