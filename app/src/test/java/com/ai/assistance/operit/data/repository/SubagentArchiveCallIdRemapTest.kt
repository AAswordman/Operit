package com.ai.assistance.operit.data.repository

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.OperitArchivedChat
import com.ai.assistance.operit.data.model.OperitArchivedMessage
import com.ai.assistance.operit.data.model.OperitArchivedMessageVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class SubagentArchiveCallIdRemapTest {
    @Test
    fun remapsExactCallIdAttributesInBaseAndVariantsOnly() {
        val chat =
            OperitArchivedChat(
                id = "parent",
                title = "Parent",
                messages =
                    listOf(
                        OperitArchivedMessage(
                            baseMessage =
                                ChatMessage(
                                    sender = "ai",
                                    content =
                                        """<tool_result call_id="old-call">old-call</tool_result>""",
                                ),
                            variants =
                                listOf(
                                    OperitArchivedMessageVariant(
                                        variantIndex = 1,
                                        content = """<tool call_id="old-call"/>""",
                                    )
                                ),
                        )
                    ),
            )

        val remapped =
            remapArchivedParentToolCallIds(chat, mapOf("old-call" to "new-call"))

        assertEquals(
            """<tool_result call_id="new-call">old-call</tool_result>""",
            remapped.messages.single().baseMessage.content,
        )
        assertEquals(
            """<tool call_id="new-call"/>""",
            remapped.messages.single().variants.single().content,
        )
    }

    @Test
    fun remapsPersistedTaskIdsInBaseAndVariants() {
        val chat =
            OperitArchivedChat(
                id = "parent",
                title = "Parent",
                messages =
                    listOf(
                        OperitArchivedMessage(
                            baseMessage =
                                ChatMessage(
                                    sender = "ai",
                                    content = """<task id="old-task">old-task</task>""",
                                ),
                            variants =
                                listOf(
                                    OperitArchivedMessageVariant(
                                        variantIndex = 1,
                                        content =
                                            """<param name="task_id">old-task</param>""",
                                    )
                                ),
                        )
                    ),
            )

        val remapped =
            remapArchivedParentToolCallIds(
                chat = chat,
                callIdRemap = emptyMap(),
                taskIdRemap = mapOf("old-task" to "new-task"),
            )

        assertEquals(
            """<task id="new-task">old-task</task>""",
            remapped.messages.single().baseMessage.content,
        )
        assertEquals(
            """<param name="task_id">new-task</param>""",
            remapped.messages.single().variants.single().content,
        )
    }
}
