package com.ai.assistance.operit.data.model

import com.ai.assistance.operit.data.repository.OPERIT_ARCHIVE_SUBAGENT_RUN_SNAPSHOT_COLUMNS
import java.time.LocalDateTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperitChatArchiveV5SerializationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun snapshotProjectionIncludesAllPersistedSubagentMetadata() {
        assertTrue("toolInvocationCount" in OPERIT_ARCHIVE_SUBAGENT_RUN_SNAPSHOT_COLUMNS)
        assertTrue("archivedAt" in OPERIT_ARCHIVE_SUBAGENT_RUN_SNAPSHOT_COLUMNS)
    }

    @Test
    fun roundTripPreservesChatKindAndSubagentRun() {
        val parent =
            OperitArchivedChat(
                id = "parent",
                title = "Parent",
                messages = emptyList(),
                createdAt = LocalDateTime.of(2026, 7, 30, 1, 2),
                updatedAt = LocalDateTime.of(2026, 7, 30, 1, 3),
                chatKind = ChatKind.NORMAL.name,
            )
        val child =
            OperitArchivedChat(
                id = "child",
                title = "Inspect",
                messages = emptyList(),
                createdAt = LocalDateTime.of(2026, 7, 30, 1, 4),
                updatedAt = LocalDateTime.of(2026, 7, 30, 1, 5),
                parentChatId = parent.id,
                chatKind = ChatKind.SUBAGENT.name,
            )
        val run =
            OperitArchivedSubagentRun(
                id = "task",
                parentChatId = parent.id,
                childChatId = child.id,
                parentToolCallId = "call",
                agentProfileId = "explore",
                title = child.title,
                status = SubagentRunStatus.COMPLETED.name,
                createdAt = 10,
                completedAt = 20,
                agentConfigSnapshot = """{"id":"explore"}""",
                modelConfigIdSnapshot = "model-config",
                modelIndexSnapshot = 1,
                toolInvocationCount = 4,
                archivedAt = 25,
            )
        val archive =
            OperitChatArchive(
                exportedAt = 30,
                chats = listOf(parent, child),
                subagentRuns = listOf(run),
            )

        val encoded = json.encodeToString(archive)
        val decoded = json.decodeFromString<OperitChatArchive>(encoded)

        assertEquals(5, decoded.formatVersion)
        assertEquals(ChatKind.SUBAGENT.name, decoded.chats.single { it.id == "child" }.chatKind)
        assertEquals(run, decoded.subagentRuns.single())
    }
}
