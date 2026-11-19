package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.R
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.core.agent.collaboration.CollaborationCoordinator
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import com.ai.assistance.operit.data.model.SubagentRunEntity
import com.ai.assistance.operit.data.repository.SubagentInterruption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentAgentCardTest {
    @Test fun badgeIsStableForOneAgentAndSpreadsAcrossAgents() {
        assertSame(
            subagentAgentIdentity("/root/fork_one").icon,
            subagentAgentIdentity("/root/fork_one").icon,
        )
        assertEquals(
            subagentAgentIdentity("/root/fork_one").accent,
            subagentAgentIdentity("/root/fork_one").accent,
        )
        assertSame(subagentAgentIdentity("").icon, subagentAgentIdentity("  ").icon)
        assertEquals(subagentAgentIdentity(""), subagentAgentIdentity("subagent"))

        val identities = (0 until 200).map { subagentAgentIdentity("/root/agent_$it") }
        assertEquals("every badge should be reachable", 8, identities.map { it.icon }.distinct().size)
        assertEquals("every accent should be reachable", 6, identities.map { it.accent }.distinct().size)
    }

    @Test fun lifecycleMapsToTheCardStatus() {
        val started = subagentCardState(runStatus = null)
        assertEquals(SubagentCardStatus.STARTED, started.status)
        assertEquals(SubagentCardStatus.STARTED, subagentCardState(runStatus = "CREATED").status)
        assertEquals(
            SubagentCardStatus.STARTED,
            subagentCardState(runStatus = "SOMETHING_NEW").status,
        )

        val queued = subagentCardState(runStatus = "QUEUED", queuePosition = 3)
        assertEquals(SubagentCardStatus.QUEUED, queued.status)
        assertEquals(3, queued.queuePosition)
        assertEquals(1, subagentCardState(runStatus = "QUEUED", queuePosition = 0).queuePosition)
        // A caller that cannot see the queue says "queued" instead of inventing a position.
        assertNull(subagentCardState(runStatus = "QUEUED").queuePosition)

        assertEquals(
            SubagentCardStatus.STARTED,
            subagentCardState(runStatus = "RUNNING", currentTool = null).status,
        )
        assertEquals(
            SubagentCardStatus.STARTED,
            subagentCardState(runStatus = "RUNNING", currentTool = " ").status,
        )
        val calling = subagentCardState(runStatus = "RUNNING", currentTool = "read_file")
        assertEquals(SubagentCardStatus.CALLING_TOOL, calling.status)
        assertEquals("read_file", calling.toolName)

        assertEquals(
            SubagentCardStatus.COMPLETED,
            subagentCardState(runStatus = "COMPLETED").status,
        )
        assertEquals(SubagentCardStatus.FAILED, subagentCardState(runStatus = "FAILED").status)
        assertEquals(
            SubagentCardStatus.INTERRUPTED,
            subagentCardState(runStatus = "INTERRUPTED").status,
        )
        assertEquals(
            SubagentCardStatus.CANCELLED,
            subagentCardState(runStatus = "CANCELLED").status,
        )
    }

    @Test fun statsOnlyListWhatIsKnown() {
        assertTrue(subagentStatLines(durationText = null, toolCount = 0, roundCount = 0).isEmpty())
        assertTrue(
            subagentStatLines(durationText = "  ", toolCount = 0, roundCount = 0).isEmpty()
        )

        val elapsed = subagentStatLines(durationText = "12 s", toolCount = 0, roundCount = 0)
        assertEquals(1, elapsed.size)
        assertEquals(R.string.subagent_card_elapsed, elapsed.single().labelResId)
        assertEquals(listOf<Any>("12 s"), elapsed.single().args)

        val all = subagentStatLines(durationText = "12 s", toolCount = 3, roundCount = 2)
        assertEquals(3, all.size)
        assertEquals(R.string.subagent_card_tool_count, all[1].labelResId)
        assertEquals(listOf<Any>(3), all[1].args)
        assertEquals(R.string.subagent_card_round_count, all[2].labelResId)
        assertEquals(listOf<Any>(2), all[2].args)
        assertNotEquals(all[0].labelResId, all[1].labelResId)
    }

    @Test fun aBareAgentNameKeepsTheBadgeItsCanonicalPathGets() {
        // A spawn row only knows the bare task name until the run row exists, so the badge must not
        // change when the canonical path arrives.
        assertEquals(subagentAgentIdentity("/root/worker"), subagentAgentIdentity("worker"))
        assertEquals(subagentAgentIdentity("/root/a/worker"), subagentAgentIdentity("worker"))
        assertEquals("worker", agentBadgeSeed(" /root/worker "))
        assertEquals("worker", agentBadgeSeed("/root/worker/"))
        assertEquals("subagent", agentBadgeSeed(""))
        assertEquals("subagent", agentBadgeSeed("/"))
    }

    @Test fun theRunRowKeepsItsOwnBadgeOnlyForANamedV2Agent() {
        assertTrue(subagentRunShowsAgentIdentity(isAutoReview = false, isV2Agent = true))
        assertFalse(subagentRunShowsAgentIdentity(isAutoReview = true, isV2Agent = true))
        assertFalse(subagentRunShowsAgentIdentity(isAutoReview = false, isV2Agent = false))

        val statusColor = Color(0xFF112233)
        val accent = Color(0xFF445566)
        // A finished v2 run must not fall back to the grey status colour the row used to show.
        assertEquals(accent, subagentRunBadgeColor(true, statusColor, accent))
        assertEquals(statusColor, subagentRunBadgeColor(false, statusColor, accent))

        assertEquals("/root/worker", subagentRunTitle(true, "/root/worker", "default"))
        assertEquals("v2 · subagent", subagentRunTitle(true, "  ", ""))
        // A v1 task and the reading companion's numeric owner id both fall back to the version tag.
        assertEquals("v1 · default", subagentRunTitle(false, "/root/worker", "default"))
        assertEquals("v1 · subagent", subagentRunTitle(false, "7", ""))
    }

    @Test fun theCardShowsTheChildsLastFinishedAnswer() {
        val reasoning = "<think data-operit-provider-reasoning=\"html-v1\">weighing options</think>"
        val childMessages =
            listOf(
                ChatMessage(sender = "user", content = "do the thing"),
                ChatMessage(
                    sender = "ai",
                    content = "half a thought",
                    displayMode = ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE,
                ),
                ChatMessage(sender = "ai", content = "   "),
                // A persisted answer still carries the reasoning envelope and the tool calls.
                ChatMessage(
                    sender = "ai",
                    content = "$reasoning<tool name=\"read_file\"></tool>the answer",
                ),
            )

        assertEquals("the answer", subagentReturnedContent(childMessages, null))
        assertEquals("the answer", subagentReturnedContent(childMessages, "   "))
        // A reply row already carries what the agent returned, so the child is not read for it.
        assertEquals("handed over", subagentReturnedContent(childMessages, "handed over"))
        // A child that only produced process so far has nothing to show.
        val onlyProcess =
            listOf(
                ChatMessage(
                    sender = "ai",
                    content = "still thinking",
                    displayMode = ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE,
                )
            )
        assertNull(subagentReturnedContent(onlyProcess, null))
        assertNull(subagentReturnedContent(null, null))
        assertNull(subagentReturnedContent(null, "   "))
        // The task the agent was handed is not something it returned.
        assertNull(subagentReturnedContent(listOf(ChatMessage(sender = "user", content = "task")), null))
    }

    @Test fun theRunLookupPrefersTheLiveRunTheAgentOwns() {
        val archived = collaborationRun(id = "archived", owner = "/root/worker", archivedAt = 1L)
        val live = collaborationRun(id = "live", owner = "/root/worker")
        // The reading companion shares this table and could reuse the same owner string.
        val foreign =
            collaborationRun(
                id = "foreign",
                owner = "/root/worker",
                ownerType = "reading_companion_run",
            )
        val otherAgent = collaborationRun(id = "other", owner = "/root/other")

        assertEquals(
            "live",
            findCollaborationRun(listOf(archived, foreign, live, otherAgent), "/root/worker")?.id,
        )
        assertNull(findCollaborationRun(listOf(archived, otherAgent), "/root/worker"))
        assertNull(findCollaborationRun(listOf(live, otherAgent), null))
        assertNull(findCollaborationRun(listOf(live, otherAgent), "  "))
    }

    @Test fun aReusedNameKeepsEachRowWithTheAgentOfItsTime() {
        val first = collaborationRun(id = "first", owner = "/root/worker", createdAt = 100L)
        val second = collaborationRun(id = "second", owner = "/root/worker", createdAt = 200L)
        val runs = listOf(first, second)
        // Messages: the sender's run already existed when the message arrived.
        assertEquals("first", findCollaborationRun(runs, "/root/worker", 150L)?.id)
        assertEquals("second", findCollaborationRun(runs, "/root/worker", 250L)?.id)
        // A message older than every run of that name never borrows a later agent.
        assertNull(findCollaborationRun(listOf(second), "/root/worker", 150L))
        // Calls: the chat a call reports wins over its name and time.
        assertEquals(
            "first",
            com.ai.assistance.operit.ui.features.chat.components.part.findCallRun(
                runs, "call", "/root/worker", agentId = "child-first", at = 250L,
            )?.id,
        )
        // A reported chat that is gone links nowhere, never to another agent of the name.
        assertNull(
            com.ai.assistance.operit.ui.features.chat.components.part.findCallRun(
                listOf(second), "call", "/root/worker", agentId = "child-first", at = 250L,
            ),
        )
        // An older call that never said its chat does not borrow a later agent of the name.
        assertNull(
            com.ai.assistance.operit.ui.features.chat.components.part.findCallRun(
                listOf(second), "old-call", "/root/worker", at = 150L,
            ),
        )
        // Older calls without that report fall back to the time of their message.
        assertEquals(
            "first",
            com.ai.assistance.operit.ui.features.chat.components.part.findCallRun(runs, "call", "/root/worker", at = 150L)?.id,
        )
        assertEquals(
            "child-second",
            com.ai.assistance.operit.ui.features.chat.components.part.collaborationCallAgentId(
                "{\"accepted\":true,\"agent_id\":\"child-second\"}",
            ),
        )
    }

    @Test fun theOpenCardBelongsToTheConversationThatOpenedIt() {
        SubagentDetailHost.clear()
        openCard(chatId = "a")
        assertNotNull(SubagentDetailHost.requestFor("a"))
        // Another conversation neither shows a card it did not open nor closes one it does not own.
        assertNull(SubagentDetailHost.requestFor("b"))
        SubagentDetailHost.dismiss("b")
        assertNotNull(SubagentDetailHost.requestFor("a"))

        SubagentDetailHost.dismiss("a")
        assertNull(SubagentDetailHost.requestFor("a"))

        // Walking away from the conversation drops the card it left behind.
        openCard(chatId = "a")
        SubagentDetailHost.clear()
        assertNull(SubagentDetailHost.requestFor("a"))
    }

    @Test fun aCardOpenedWithoutAConversationClosesFromAnyConversation() {
        SubagentDetailHost.clear()
        openCard(chatId = null)
        assertNotNull(SubagentDetailHost.requestFor("a"))
        assertNotNull(SubagentDetailHost.requestFor(null))

        SubagentDetailHost.dismiss("a")
        assertNull(SubagentDetailHost.requestFor(null))
    }

    @Test fun onlyAFailedRunExplainsItself() {
        assertEquals("boom", subagentFailureText(SubagentCardStatus.FAILED, "boom"))
        assertNull(subagentFailureText(SubagentCardStatus.FAILED, "   "))
        assertNull(subagentFailureText(SubagentCardStatus.COMPLETED, "boom"))
        // A run the user stopped is not a failure to explain.
        assertNull(subagentFailureText(SubagentCardStatus.CANCELLED, "stopped"))
        // Neither is a run the app stopped for the user.
        assertNull(subagentFailureText(SubagentCardStatus.INTERRUPTED, "stopped by restart"))
    }

    @Test fun onlyAnInterruptedRunExplainsWhyItStopped() {
        assertNull(subagentInterruptionHintRes(SubagentCardStatus.FAILED, "boom"))
        assertNull(subagentInterruptionHintRes(SubagentCardStatus.COMPLETED, "boom"))
        assertNull(subagentInterruptionHintRes(SubagentCardStatus.CANCELLED, "stopped"))

        // Each stored reason keeps its own wording instead of every stop reading as a restart.
        assertEquals(
            R.string.subagent_interrupted_app_restart,
            subagentInterruptionHintRes(
                SubagentCardStatus.INTERRUPTED,
                SubagentInterruption.APP_RESTART,
            ),
        )
        assertEquals(
            R.string.subagent_interrupted_archive_import,
            subagentInterruptionHintRes(
                SubagentCardStatus.INTERRUPTED,
                SubagentInterruption.ARCHIVE_IMPORT,
            ),
        )
        assertEquals(
            R.string.subagent_interrupted_chat_branch,
            subagentInterruptionHintRes(
                SubagentCardStatus.INTERRUPTED,
                SubagentInterruption.CHAT_BRANCH,
            ),
        )
        assertEquals(
            R.string.subagent_interrupted_reading_companion,
            subagentInterruptionHintRes(
                SubagentCardStatus.INTERRUPTED,
                SubagentInterruption.READING_COMPANION,
            ),
        )
        // A reason this build does not know still explains itself rather than showing nothing.
        assertEquals(
            R.string.subagent_interrupted_unknown,
            subagentInterruptionHintRes(SubagentCardStatus.INTERRUPTED, null),
        )
        assertEquals(
            R.string.subagent_interrupted_unknown,
            subagentInterruptionHintRes(SubagentCardStatus.INTERRUPTED, "some other reason"),
        )
    }

    private fun openCard(chatId: String?) {
        SubagentDetailHost.requestDetail(
            chatId = chatId,
            agentPath = "/root/worker",
            avatarUri = null,
            statusText = "completed",
            failureText = null,
            interruptionText = null,
            identity = subagentAgentIdentity("/root/worker"),
            statusColor = Color(0xFF112233),
            body = "the answer",
            childChatId = "child",
            onOpenConversation = null,
        )
    }

    @Test fun aNewerAgentArchivedByHandNeverHandsItsRowsToTheOlderOne() {
        val first = collaborationRun(id = "first", owner = "/root/worker", createdAt = 100L)
        val second = collaborationRun(id = "second", owner = "/root/worker", createdAt = 200L, archivedAt = 300L)
        val runs = listOf(first, second)
        // The row belongs to the second agent; that agent is archived, so the row opens nothing.
        assertNull(findCollaborationRun(runs, "/root/worker", 250L))
        assertNull(
            com.ai.assistance.operit.ui.features.chat.components.part.findCallRun(
                runs, "old-call", "/root/worker", at = 250L,
            ),
        )
        assertNull(
            com.ai.assistance.operit.ui.features.chat.components.part.findCallRun(
                runs, "call", "/root/worker", agentId = "child-second", at = 250L,
            ),
        )
        // The first agent's own rows still open it.
        assertEquals("first", findCollaborationRun(runs, "/root/worker", 150L)?.id)
        // A deleted second run: the envelope's Sender ID keeps the row from falling to the first.
        assertNull(findCollaborationRun(listOf(first), "/root/worker", 250L, senderId = "child-second"))
        assertEquals("first", findCollaborationRun(listOf(first), "/root/worker", 250L, senderId = "child-first")?.id)
    }

    @Test fun anEnvelopeWithASenderIdStillReadsAsAMessage() {
        val content = "Message ID: 123e4567-e89b-12d3-a456-426614174000\nMessage Type: FINAL_ANSWER\n" +
            "Task name: /root\nSender: /root/worker\nSender ID: child-second\nPayload:\ndone"
        val event = collaborationDisplayMessages(content, "fallback").single()
        assertEquals("FINAL_ANSWER", event.kind)
        assertEquals("done", event.body)
        assertEquals("child-second", event.senderId)
    }

    private fun collaborationRun(
        id: String,
        owner: String?,
        ownerType: String? = CollaborationCoordinator.OWNER_TYPE,
        archivedAt: Long? = null,
        createdAt: Long = 0L,
    ) =
        SubagentRunEntity(
            id = id,
            parentChatId = "parent",
            childChatId = "child-$id",
            agentProfileId = "default",
            title = id,
            externalOwnerType = ownerType,
            externalOwnerId = owner,
            archivedAt = archivedAt,
            createdAt = createdAt,
        )
}
