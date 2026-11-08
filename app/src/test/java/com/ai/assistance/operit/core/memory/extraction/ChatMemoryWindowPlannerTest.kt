package com.ai.assistance.operit.core.memory.extraction

import com.ai.assistance.operit.data.model.ChatMessage
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMemoryWindowPlannerTest {

    @Test
    fun `plan keeps each normal user turn with its assistant response`() {
        val windows =
            plan(
                messages =
                    listOf(
                        message("user", "first", 1),
                        message("ai", "first reply", 2),
                        message("user", "second", 3),
                        message("ai", "second reply", 4),
                        message("user", "third", 5),
                        message("ai", "third reply", 6),
                        message("user", "fourth", 7),
                        message("ai", "fourth reply", 8),
                        message("user", "fifth", 9),
                        message("ai", "fifth reply", 10),
                    ),
                windowMessageCount = 8
            )

        assertEquals(listOf(8, 2), windows.map { it.sourceMessageCount })
        assertEquals(
            listOf(
                "first",
                "first reply",
                "second",
                "second reply",
                "third",
                "third reply",
                "fourth",
                "fourth reply",
            ),
            windows[0].messages.map { it.content }
        )
        assertEquals(listOf("fifth", "fifth reply"), windows[1].messages.map { it.content })
    }

    @Test
    fun `plan keeps consecutive assistant replies with their user turn at a boundary`() {
        val windows =
            plan(
                messages =
                    listOf(
                        message("user", "first", 1),
                        message("ai", "first reply", 2),
                        message("user", "second", 3),
                        message("ai", "second reply", 4),
                        message("user", "group prompt", 5),
                        message("ai", "group reply one", 6),
                        message("ai", "group reply two", 7),
                        message("ai", "group reply three", 8),
                        message("user", "next prompt", 9),
                        message("ai", "next reply", 10),
                    ),
                windowMessageCount = 8
            )

        assertEquals(listOf(8, 2), windows.map { it.sourceMessageCount })
        assertEquals(
            listOf(
                "first",
                "first reply",
                "second",
                "second reply",
                "group prompt",
                "group reply one",
                "group reply two",
                "group reply three",
            ),
            windows.first().messages.map { it.content }
        )
    }

    @Test
    fun `plan excludes summary messages from every memory window`() {
        val postSummaryTurns =
            (1L..8L).flatMap { turn ->
                listOf(
                    message("user", "after summary user $turn", turn * 2 + 10),
                    message("ai", "after summary reply $turn", turn * 2 + 11),
                )
            }
        val windows =
            plan(
                messages =
                    listOf(
                        message("user", "before summary user 1", 1),
                        message("ai", "before summary reply 1", 2),
                        message("user", "before summary user 2", 3),
                        message("ai", "before summary reply 2", 4),
                        message("user", "before summary user 3", 5),
                        message("ai", "before summary reply 3", 6),
                        message("user", "before summary user 4", 7),
                        message("ai", "before summary reply 4", 8),
                        message("summary", "earlier conversation summary", 9),
                    ) + postSummaryTurns,
                windowMessageCount = 8
            )

        assertEquals(3, windows.size)
        assertEquals(8, windows[0].sourceMessageCount)
        assertEquals(
            listOf(
                "after summary user 1",
                "after summary reply 1",
                "after summary user 2",
                "after summary reply 2",
                "after summary user 3",
                "after summary reply 3",
                "after summary user 4",
                "after summary reply 4",
            ),
            windows[1].messages.map { it.content }
        )
        assertEquals(
            listOf(
                "after summary user 5",
                "after summary reply 5",
                "after summary user 6",
                "after summary reply 6",
                "after summary user 7",
                "after summary reply 7",
                "after summary user 8",
                "after summary reply 8",
            ),
            windows[2].messages.map { it.content }
        )
        assertEquals(8, windows[1].sourceMessageCount)
        assertEquals(8, windows[2].sourceMessageCount)
    }

    @Test
    fun `plan does not create a request for every summary`() {
        val messages =
            buildList {
                (1L..12L).forEach { turn ->
                    add(message("user", "user $turn", turn * 3))
                    add(message("ai", "assistant $turn", turn * 3 + 1))
                    add(message("summary", "summary $turn", turn * 3 + 2))
                }
            }

        val windows = plan(messages, 8)

        assertEquals(listOf(8, 8, 8), windows.map { it.sourceMessageCount })
        assertEquals(24, windows.sumOf { it.sourceMessageCount })
        assertEquals("user 5", windows[1].messages.first().content)
        assertEquals("user 9", windows[2].messages.first().content)
    }

    @Test
    fun `plan keeps a multi-thousand-message chat bounded despite per-turn summaries`() {
        val messages =
            buildList {
                (1L..2_000L).forEach { turn ->
                    add(message("user", "user $turn", turn * 3))
                    add(message("ai", "assistant $turn", turn * 3 + 1))
                    add(message("summary", "summary $turn", turn * 3 + 2))
                }
            }

        val windows = plan(messages, 48)

        assertEquals(84, windows.size)
        assertEquals(4_000, windows.sumOf { it.sourceMessageCount })
        assertTrue(windows.all { it.sourceMessageCount in 1..48 })
        assertEquals("user 25", windows[1].messages.first().content)
    }

    @Test
    fun `plan ignores blank messages and assistant-only fragments`() {
        val windows =
            plan(
                messages =
                    listOf(
                        message("ai", "orphan reply", 1),
                        message("user", "", 2),
                        message("summary", "summary", 3),
                        message("ai", "another orphan reply", 4),
                    ),
                windowMessageCount = 16
            )

        assertTrue(windows.isEmpty())
    }

    @Test
    fun `plan retains assistant sender used by imported web chats`() {
        val windows =
            plan(
                messages =
                    listOf(
                        message("user", "question", 1),
                        message("assistant", "reply", 2),
                    ),
                windowMessageCount = 16
            )

        assertEquals(1, windows.size)
        assertEquals(listOf("question", "reply"), windows.single().messages.map { it.content })
    }

    @Test
    fun `plan ignores blank and nonblank summary messages`() {
        val windows =
            plan(
                messages =
                    listOf(
                        message("summary", "useful summary", 1),
                        message("summary", "", 2),
                        message("user", "question", 3),
                        message("ai", "reply", 4),
                    ),
                windowMessageCount = 16
            )

        assertEquals(
            listOf("question", "reply"),
            windows.single().messages.map { it.content }
        )
    }

    @Test
    fun `plan constrains caller window size to supported bounds`() {
        val messages =
            (1L..50L).flatMap { turn ->
                listOf(
                    message("user", "user $turn", turn * 2),
                    message("ai", "assistant $turn", turn * 2 + 1),
                )
            }

        val windows = plan(messages, Int.MAX_VALUE)

        assertTrue(windows.all { it.sourceMessageCount <= ChatMemoryWindowPlanner.MAX_WINDOW_MESSAGE_COUNT })
        assertEquals(100, windows.sumOf { it.sourceMessageCount })
    }

    @Test
    fun `plan splits an oversized group reply turn without exceeding the window limit`() {
        val messages =
            buildList {
                add(message("user", "group prompt", 1))
                (1L..20L).forEach { reply ->
                    add(message("ai", "group reply $reply", reply + 1))
                }
            }

        val windows = plan(messages, 8)

        assertEquals(listOf(8, 8, 5), windows.map { it.sourceMessageCount })
        assertTrue(windows.all { it.sourceMessageCount <= 8 })
        assertEquals(
            (1L..20L).map { "group reply $it" },
            windows.flatMap { window ->
                window.messages
                    .filter { it.sender == "ai" }
                    .map { it.content }
            }
        )
        assertEquals("group prompt", windows[1].messages.first().content)
        assertEquals("group prompt", windows[2].messages.first().content)
    }

    @Test
    fun `plan reserves space for an assistant reply at the window boundary`() {
        val messages =
            (1L..24L).flatMap { turn ->
                listOf(
                    message("user", "user $turn", turn * 2),
                    message("ai", "assistant $turn", turn * 2 + 1),
                )
            } + listOf(
                message("user", "boundary user", 100),
                message("ai", "boundary assistant", 101),
            )

        val windows = plan(messages, 48)

        assertEquals(listOf(48, 2), windows.map { it.sourceMessageCount })
        assertEquals(
            listOf("boundary user", "boundary assistant"),
            windows.last().messages.map { it.content }
        )
    }

    @Test
    fun `plan with inclusive range keeps only messages inside the range`() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val range =
            ChatMemoryRebuildTimeScope.inclusiveDates(
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 2),
                shanghai
            )
        val windows =
            plan(
                messages =
                    listOf(
                        message("user", "before", range.startInclusiveMs - 1),
                        message("ai", "before reply", range.startInclusiveMs - 1),
                        message("user", "on day", range.startInclusiveMs),
                        message("ai", "on day reply", range.endExclusiveMs - 1),
                        message("user", "after", range.endExclusiveMs),
                        message("ai", "after reply", range.endExclusiveMs + 1),
                    ),
                windowMessageCount = 16,
                timeScope = range
            )

        assertEquals(listOf("on day", "on day reply"), windows.single().messages.map { it.content })
    }

    @Test
    fun `plan with inclusive range excludes an assistant reply after the exclusive end`() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val range =
            ChatMemoryRebuildTimeScope.inclusiveDates(
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 2),
                shanghai
            )
        val windows =
            plan(
                messages =
                    listOf(
                        message("user", "on day", range.startInclusiveMs),
                        message("ai", "late reply", range.endExclusiveMs),
                    ),
                windowMessageCount = 16,
                timeScope = range
            )

        assertEquals(listOf("on day"), windows.single().messages.map { it.content })
    }

    @Test
    fun `plan with inclusive range ignores an assistant reply whose user turn is outside`() {
        val shanghai = ZoneId.of("Asia/Shanghai")
        val range =
            ChatMemoryRebuildTimeScope.inclusiveDates(
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 2),
                shanghai
            )
        val windows =
            plan(
                messages =
                    listOf(
                        message("user", "yesterday", range.startInclusiveMs - 1),
                        message("ai", "today reply", range.startInclusiveMs),
                    ),
                windowMessageCount = 16,
                timeScope = range
            )

        assertTrue(windows.isEmpty())
    }

    private fun plan(
        messages: List<ChatMessage>,
        windowMessageCount: Int,
        timeScope: ChatMemoryRebuildTimeScope = ChatMemoryRebuildTimeScope.EntireChat
    ): List<ChatMemoryWindowPlanner.Window> =
        ChatMemoryWindowPlanner.plan(messages, windowMessageCount, timeScope)

    private fun message(sender: String, content: String, timestamp: Long): ChatMessage =
        ChatMessage(sender = sender, content = content, timestamp = timestamp)
}
