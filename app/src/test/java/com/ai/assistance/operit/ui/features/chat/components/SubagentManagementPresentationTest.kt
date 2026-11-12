package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.SubagentRunEntity
import com.ai.assistance.operit.data.model.SubagentRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentManagementPresentationTest {
    @Test
    fun allFilter_hidesArchivedAndPrioritizesActiveRuns() {
        val completed = run(id = "completed", status = SubagentRunStatus.COMPLETED, createdAt = 40)
        val running = run(id = "running", status = SubagentRunStatus.RUNNING, createdAt = 10)
        val queued = run(id = "queued", status = SubagentRunStatus.QUEUED, createdAt = 30)
        val archived =
            run(
                id = "archived",
                status = SubagentRunStatus.COMPLETED,
                createdAt = 50,
                archivedAt = 60,
            )

        val visible =
            filterAndSortSubagentRuns(
                listOf(completed, running, queued, archived),
                SubagentListFilter.ALL,
            )

        assertEquals(listOf("running", "queued", "completed"), visible.map { it.id })
    }

    @Test
    fun archivedFilter_ordersByMostRecentArchiveTime() {
        val older =
            run(
                id = "older",
                status = SubagentRunStatus.COMPLETED,
                createdAt = 100,
                archivedAt = 200,
            )
        val newer =
            run(
                id = "newer",
                status = SubagentRunStatus.FAILED,
                createdAt = 50,
                archivedAt = 300,
            )

        val visible =
            filterAndSortSubagentRuns(
                listOf(older, newer),
                SubagentListFilter.ARCHIVED,
            )

        assertEquals(listOf("newer", "older"), visible.map { it.id })
        assertTrue(visible.all { it.archivedAt != null })
    }

    @Test
    fun search_matchesAgentNameOrTaskTitleWithinSelectedFilter() {
        val byAgent =
            run(
                id = "agent-match",
                status = SubagentRunStatus.COMPLETED,
                createdAt = 30,
                agentProfileId = "Explore",
                title = "认证调用链",
            )
        val byTitle =
            run(
                id = "title-match",
                status = SubagentRunStatus.COMPLETED,
                createdAt = 20,
                agentProfileId = "general",
                title = "Explore login flow",
            )
        val running =
            run(
                id = "wrong-filter",
                status = SubagentRunStatus.RUNNING,
                createdAt = 40,
                agentProfileId = "explore",
                title = "running",
            )

        val visible =
            filterAndSortSubagentRuns(
                listOf(byAgent, byTitle, running),
                SubagentListFilter.COMPLETED,
                query = "EXPLORE",
            )

        assertEquals(listOf("agent-match", "title-match"), visible.map { it.id })
    }

    private fun run(
        id: String,
        status: SubagentRunStatus,
        createdAt: Long,
        archivedAt: Long? = null,
        agentProfileId: String = "explore",
        title: String = id,
    ): SubagentRunEntity =
        SubagentRunEntity(
            id = id,
            parentChatId = "parent",
            childChatId = "child-$id",
            agentProfileId = agentProfileId,
            title = title,
            status = status.name,
            createdAt = createdAt,
            archivedAt = archivedAt,
        )
}
