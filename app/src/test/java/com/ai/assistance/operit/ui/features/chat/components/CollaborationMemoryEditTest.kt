package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.core.agent.collaboration.AgentMessage
import com.ai.assistance.operit.core.agent.collaboration.AgentMessageKind
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageDisplayMode
import org.junit.Assert.*
import org.junit.Test

class CollaborationMemoryEditTest {
    @Test fun editorInterleavesAssistantPartsAndEventsFromEitherAnchor() {
        val transcript = aTurnWithTwoBatches()
        val finalFragments = collaborationMemoryFragments(transcript, 4, includeAssistant = true)
        val intermediateFragments = collaborationMemoryFragments(transcript, 2, includeAssistant = true)
        assertEquals(listOf(2L, 3L, 4L, 5L), finalFragments.map { it.timestamp })
        assertEquals(finalFragments, intermediateFragments)
        assertEquals(
            listOf(PartType.COLLABORATION, PartType.TEXT, PartType.COLLABORATION, PartType.TEXT),
            memoryEditorParts(finalFragments).map { it.type },
        )
    }

    @Test fun editingAnEarlierTextPartWritesOnlyItsSourceAndPreservesUntouchedWhitespace() {
        val transcript = aTurnWithTwoBatches().toMutableList()
        transcript[2] = transcript[2].copy(content = "<think>thinking</think>\n\nold text")
        transcript[4] = transcript[4].copy(content = "<think>done</think>\n\nfinal text")
        val fragments = collaborationMemoryFragments(transcript, 4, includeAssistant = true)
        val initial = memoryEditorParts(fragments)
        assertEquals(fragments, updatedMemoryEditorFragments(fragments, initial, initial))
        val edited = initial.map {
            if (it.sourceTimestamp == 3L && it.type == PartType.TEXT) it.copy(content = "new text")
            else it
        }
        val result = updatedMemoryEditorFragments(fragments, initial, edited)
        assertEquals(listOf(3L), result.filter { it.changed }.map { it.timestamp })
        assertEquals("<think>thinking</think>new text", result.first { it.timestamp == 3L }.body)
        assertEquals(transcript[4].content, result.last().body)
        assertEquals(listOf(2L, 3L, 4L, 5L), result.map { it.timestamp })
    }

    @Test fun deletingAllPartsClearsOnlyThatAssistantSource() {
        val fragments = collaborationMemoryFragments(aTurnWithTwoBatches(), 4, includeAssistant = true)
        val initial = memoryEditorParts(fragments)
        val result = updatedMemoryEditorFragments(fragments, initial, initial.filter { it.sourceTimestamp != 3L })
        assertEquals("", result.first { it.timestamp == 3L }.body)
        assertEquals("reply", result.last().body)
        assertEquals("halfway", result.first { it.timestamp == 4L }.body)
    }

    private fun eventRow(
        timestamp: Long,
        agentPath: String,
        kind: AgentMessageKind,
        text: String,
        displayMode: ChatMessageDisplayMode = ChatMessageDisplayMode.COLLABORATION_EVENT,
    ) = ChatMessage(
        timestamp = timestamp,
        sender = "user",
        content = AgentMessage(
            id = "11111111-1111-1111-1111-" + timestamp.toString().padStart(12, '0'),
            sender = agentPath,
            recipient = "/root",
            kind = kind,
            text = text,
        ).render(),
        roleName = agentPath,
        displayMode = displayMode,
    )

    private fun assistantRow(
        timestamp: Long,
        sentAt: Long,
        displayMode: ChatMessageDisplayMode,
    ) = ChatMessage(
        timestamp = timestamp,
        sender = "ai",
        content = "reply",
        sentAt = sentAt,
        displayMode = displayMode,
    )

    /** The message that opened a turn carries that turn's request, which is what marks the boundary. */
    private fun turnOpener(timestamp: Long, sentAt: Long) =
        ChatMessage(
            timestamp = timestamp,
            sender = "user",
            content = "做一个功能",
            roleName = "用户",
            sentAt = sentAt,
        )

    /** A message the user slipped in while the reply was being written carries no request of its own. */
    private fun interjection(timestamp: Long) =
        ChatMessage(timestamp = timestamp, sender = "user", content = "顺便说一下", roleName = "用户")

    private fun aTurnWithTwoBatches() = listOf(
        turnOpener(1, sentAt = 100),
        eventRow(2, "/root/worker", AgentMessageKind.NEW_TASK, "do the thing"),
        assistantRow(3, sentAt = 100, displayMode = ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE),
        eventRow(4, "/root/worker", AgentMessageKind.MESSAGE, "halfway"),
        assistantRow(5, sentAt = 100, displayMode = ChatMessageDisplayMode.NORMAL)
            .copy(completedAt = 200),
    )

    @Test fun aReplyCarriesEveryCollaborationRowItsTurnProduced() {
        val transcript = aTurnWithTwoBatches()

        val fragments = collaborationMemoryFragments(transcript, 4)

        // 中途消息夹在两条 AI 消息之间，也仍然属于这一轮。
        assertEquals(listOf(1, 3), fragments.map { it.messageIndex })
        assertEquals(listOf(2L, 4L), fragments.map { it.timestamp })
        assertEquals(listOf("/root/worker", "/root/worker"), fragments.map { it.sender })
        assertEquals(listOf("do the thing", "halfway"), fragments.map { it.body })
    }

    @Test fun rowsFromAnEarlierTurnStayOutOfThisReply() {
        val transcript = listOf(
            eventRow(1, "/root/older", AgentMessageKind.MESSAGE, "old news"),
            assistantRow(2, sentAt = 50, displayMode = ChatMessageDisplayMode.NORMAL),
            turnOpener(3, sentAt = 100),
            eventRow(4, "/root/worker", AgentMessageKind.MESSAGE, "this turn"),
            assistantRow(5, sentAt = 100, displayMode = ChatMessageDisplayMode.NORMAL),
        )

        val fragments = collaborationMemoryFragments(transcript, 4)

        assertEquals(listOf(4L), fragments.map { it.timestamp })
    }

    @Test fun anotherTurnsReplyEndsTheWalk() {
        val transcript = listOf(
            eventRow(1, "/root/older", AgentMessageKind.MESSAGE, "old news"),
            assistantRow(2, sentAt = 50, displayMode = ChatMessageDisplayMode.NORMAL),
            eventRow(3, "/root/worker", AgentMessageKind.MESSAGE, "this turn"),
            assistantRow(4, sentAt = 100, displayMode = ChatMessageDisplayMode.NORMAL),
        )

        val fragments = collaborationMemoryFragments(transcript, 3)

        assertEquals(listOf(3L), fragments.map { it.timestamp })
    }

    @Test fun aMessageTheUserSlippedInDoesNotEndTheTurn() {
        // 转录本身也不把插话当成一轮的边界（见 TranscriptStructureTest），卡片里前后的子代理消息
        // 属于同一轮，编辑器里就必须都能看到。
        val transcript = listOf(
            assistantRow(1, sentAt = 50, displayMode = ChatMessageDisplayMode.NORMAL),
            turnOpener(2, sentAt = 100),
            eventRow(3, "/root/worker", AgentMessageKind.NEW_TASK, "插话之前"),
            assistantRow(4, sentAt = 100, displayMode = ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE),
            interjection(5),
            eventRow(6, "/root/worker", AgentMessageKind.MESSAGE, "插话之后"),
            assistantRow(7, sentAt = 100, displayMode = ChatMessageDisplayMode.NORMAL),
        )

        val fragments = collaborationMemoryFragments(transcript, 6)

        assertEquals(listOf(3L, 6L), fragments.map { it.timestamp })
    }

    @Test fun anIntermediateRowCoversTheWholeTurnToo() {
        // 展开过程后长按中间段也能进编辑器，它看到的应当是整轮，而不是半轮。
        val transcript = aTurnWithTwoBatches()

        val fragments = collaborationMemoryFragments(transcript, 2)

        assertEquals(listOf(1, 3), fragments.map { it.messageIndex })
    }

    @Test fun aReplyThatEndedTheTurnTakesNothingAfterIt() {
        // 最终回复之后的行属于下一轮：哪怕中间隔着一句插话，也不能顺着走查收进来。
        val transcript = listOf(
            turnOpener(1, sentAt = 100),
            eventRow(2, "/root/worker", AgentMessageKind.MESSAGE, "这一轮"),
            assistantRow(3, sentAt = 100, displayMode = ChatMessageDisplayMode.NORMAL)
                .copy(completedAt = 200),
            interjection(4),
            eventRow(5, "/root/next", AgentMessageKind.MESSAGE, "下一轮的"),
            assistantRow(6, sentAt = 300, displayMode = ChatMessageDisplayMode.NORMAL),
        )

        assertEquals(
            listOf(2L),
            collaborationMemoryFragments(transcript, 2).map { it.timestamp },
        )
    }

    @Test fun aSegmentThatEndsItsOwnPartTakesOnlyWhatCameBeforeIt() {
        // waifu 模式下每个分段都是一条 NORMAL 行、共享同一个 sentAt：分段自己的编辑器只覆盖到它为止，
        // 分段之间发生的协作行出现在后一个分段的编辑器里。也就是说「整轮」的口径在分段模式下只对
        // 最后一段成立，这里把现状钉住，免得以后有人误以为处处都是整轮。
        val transcript = listOf(
            turnOpener(1, sentAt = 100),
            eventRow(2, "/root/worker", AgentMessageKind.NEW_TASK, "第一批"),
            assistantRow(3, sentAt = 100, displayMode = ChatMessageDisplayMode.NORMAL),
            eventRow(4, "/root/worker", AgentMessageKind.MESSAGE, "第二批"),
            assistantRow(5, sentAt = 100, displayMode = ChatMessageDisplayMode.NORMAL),
        )

        assertEquals(listOf(2L), collaborationMemoryFragments(transcript, 2).map { it.timestamp })
        assertEquals(
            listOf(2L, 4L),
            collaborationMemoryFragments(transcript, 4).map { it.timestamp },
        )
    }

    @Test fun aTaskHandedToAnAgentIsAFragmentToo() {
        val transcript = listOf(
            turnOpener(1, sentAt = 100),
            eventRow(
                2,
                "/root/worker",
                AgentMessageKind.NEW_TASK,
                "做这个",
                displayMode = ChatMessageDisplayMode.COLLABORATION_TASK,
            ),
            assistantRow(3, sentAt = 100, displayMode = ChatMessageDisplayMode.NORMAL),
        )

        val fragments = collaborationMemoryFragments(transcript, 2)

        assertEquals(listOf(2L), fragments.map { it.timestamp })
        assertEquals(listOf("做这个"), fragments.map { it.body })
    }

    @Test fun aReplyOfAnotherRequestIsWhereTheTurnEnds() {
        val transcript = listOf(
            eventRow(1, "/root/older", AgentMessageKind.MESSAGE, "上一个请求"),
            assistantRow(2, sentAt = 50, displayMode = ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE),
            eventRow(3, "/root/worker", AgentMessageKind.MESSAGE, "这个请求"),
            assistantRow(4, sentAt = 100, displayMode = ChatMessageDisplayMode.NORMAL),
        )

        assertEquals(listOf(3L), collaborationMemoryFragments(transcript, 3).map { it.timestamp })
    }

    @Test fun aTranscriptWithoutSentAtKeepsOnlyTheRunBeforeTheReply() {
        val transcript = listOf(
            assistantRow(1, sentAt = 0, displayMode = ChatMessageDisplayMode.ASSISTANT_INTERMEDIATE),
            eventRow(2, "/root/worker", AgentMessageKind.MESSAGE, "halfway"),
            assistantRow(3, sentAt = 0, displayMode = ChatMessageDisplayMode.NORMAL),
        )

        val fragments = collaborationMemoryFragments(transcript, 2)

        assertEquals(listOf(2L), fragments.map { it.timestamp })
    }

    @Test fun onlyAReplyHasFragmentsToEdit() {
        val transcript = aTurnWithTwoBatches()

        assertTrue(collaborationMemoryFragments(transcript, 1).isEmpty())
        assertTrue(collaborationMemoryFragments(transcript, 0).isEmpty())
        assertTrue(collaborationMemoryFragments(transcript, 9).isEmpty())
    }

    @Test fun anUntouchedFragmentWritesNothingBack() {
        val fragment = collaborationMemoryFragments(aTurnWithTwoBatches(), 4).first()

        assertFalse(fragment.changed)
        assertTrue(fragment.copy(body = "rewritten").changed)
        // 删掉的那条按删除写回，不再重复写一次正文。
        assertFalse(fragment.copy(body = "rewritten", deleted = true).changed)
    }
}
