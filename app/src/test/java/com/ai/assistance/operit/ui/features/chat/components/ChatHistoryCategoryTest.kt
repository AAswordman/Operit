package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatHistory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistoryCategoryTest {
    private val baseTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    @Test
    fun recent_usesLastMessageThenCreatedTimeAndIgnoresPinnedOrder() {
        val histories =
            listOf(
                history("pinned-old", createdMinutes = 1, lastMessageMinutes = 2, pinned = true),
                history("empty", createdMinutes = 4),
                history("new-message", createdMinutes = 0, lastMessageMinutes = 5),
            )

        assertEquals(
            listOf("new-message", "empty", "pinned-old"),
            selectChatHistoriesForCategory(histories, ChatHistoryCategory.RECENT).map { it.id },
        )
    }

    @Test
    fun timeSection_usesActivityDateAndCollapsesSevenDaysAndOlder() {
        val referenceDate = LocalDate.of(2026, 8, 11)
        val today = LocalDateTime.of(2026, 8, 11, 12, 0)
        val cases =
            listOf(
                today.plusDays(1) to ChatHistoryTimeSection.TODAY,
                today to ChatHistoryTimeSection.TODAY,
                today.minusDays(1) to ChatHistoryTimeSection.YESTERDAY,
                today.minusDays(2) to ChatHistoryTimeSection.SUNDAY,
                today.minusDays(3) to ChatHistoryTimeSection.SATURDAY,
                today.minusDays(4) to ChatHistoryTimeSection.FRIDAY,
                today.minusDays(5) to ChatHistoryTimeSection.THURSDAY,
                today.minusDays(6) to ChatHistoryTimeSection.WEDNESDAY,
                today.minusDays(7) to ChatHistoryTimeSection.SEVEN_DAYS_AGO,
                today.minusDays(30) to ChatHistoryTimeSection.SEVEN_DAYS_AGO,
            )

        cases.forEachIndexed { index, (activityTime, expectedSection) ->
            val candidate =
                history("section-$index", createdMinutes = 0)
                    .copy(createdAt = activityTime, lastMessageAt = activityTime)
            assertEquals(expectedSection, candidate.timeSection(referenceDate))
        }

        val lastMessageWins =
            history("last-message", createdMinutes = 0)
                .copy(
                    createdAt = today.minusDays(30),
                    lastMessageAt = today,
                )
        assertEquals(ChatHistoryTimeSection.TODAY, lastMessageWins.timeSection(referenceDate))
    }

    @Test
    fun nextDateRefresh_waitsUntilTheNextLocalMidnight() {
        val zoneId = ZoneId.of("Asia/Shanghai")
        val beforeMidnight = ZonedDateTime.of(2026, 8, 11, 23, 59, 59, 500_000_000, zoneId)
        val noon = ZonedDateTime.of(2026, 8, 11, 12, 0, 0, 0, zoneId)

        assertEquals(501L, millisUntilNextLocalDate(beforeMidnight.toInstant(), zoneId))
        assertEquals(43_200_001L, millisUntilNextLocalDate(noon.toInstant(), zoneId))
        assertEquals(501L, localDateRefreshDelayMillis(beforeMidnight.toInstant(), zoneId))
        assertEquals(60_000L, localDateRefreshDelayMillis(noon.toInstant(), zoneId))
    }

    @Test
    fun timeSection_reinterpretsRoomEpochWhenTimeZoneChanges() {
        val instant = ZonedDateTime.of(2026, 8, 11, 23, 30, 0, 0, ZoneId.of("UTC")).toInstant()
        val history =
            history("time-zone", createdMinutes = 0)
                .copy(
                    createdAt = LocalDateTime.of(2026, 8, 11, 23, 30),
                    createdAtEpochMillis = instant.toEpochMilli(),
                )

        assertEquals(
            ChatHistoryTimeSection.TODAY,
            history.timeSection(LocalDate.of(2026, 8, 11), ZoneId.of("UTC")),
        )
        assertEquals(
            ChatHistoryTimeSection.TODAY,
            history.timeSection(LocalDate.of(2026, 8, 12), ZoneId.of("Asia/Shanghai")),
        )
    }

    @Test
    fun favorites_filtersWithoutCreatingASecondOrder() {
        val histories =
            listOf(
                history("first", createdMinutes = 1, favorite = true),
                history("hidden", createdMinutes = 3),
                history("second", createdMinutes = 2, favorite = true),
            )

        assertEquals(
            listOf("first", "second"),
            selectChatHistoriesForCategory(histories, ChatHistoryCategory.FAVORITES).map { it.id },
        )
    }

    @Test
    fun reorder_isAvailableOutsideRecentAndSearch() {
        assertEquals(true, canReorderChatHistory(ChatHistoryCategory.ALL, ""))
        assertEquals(false, canReorderChatHistory(ChatHistoryCategory.ALL, "query"))
        assertEquals(false, canReorderChatHistory(ChatHistoryCategory.RECENT, ""))
        assertEquals(true, canReorderChatHistory(ChatHistoryCategory.FAVORITES, ""))
        assertEquals(false, canReorderChatHistory(ChatHistoryCategory.FAVORITES, "query"))
    }

    @Test
    fun folderManagement_isOnlyAvailableInAllCategory() {
        assertEquals(true, canManageChatFolders(ChatHistoryCategory.ALL))
        assertEquals(false, canManageChatFolders(ChatHistoryCategory.RECENT))
        assertEquals(false, canManageChatFolders(ChatHistoryCategory.FAVORITES))
    }

    @Test
    fun newChat_inRecentStartsUngroupedInsteadOfInheritingCurrentFolder() {
        assertEquals(true, shouldInheritCurrentFolderForNewChat(ChatHistoryCategory.ALL))
        assertEquals(false, shouldInheritCurrentFolderForNewChat(ChatHistoryCategory.RECENT))
        assertEquals(true, shouldInheritCurrentFolderForNewChat(ChatHistoryCategory.FAVORITES))
    }

    @Test
    fun favoriteReorder_preservesHiddenChatsAndOnlySwapsVisibleSlots() {
        val firstFavorite = history("favorite-1", createdMinutes = 1, favorite = true)
        val hiddenFirst = history("hidden-1", createdMinutes = 2)
        val secondFavorite = history("favorite-2", createdMinutes = 3, favorite = true)
        val hiddenSecond = history("hidden-2", createdMinutes = 4)

        val merged =
            mergeVisibleChatOrder(
                fullHistories =
                    listOf(firstFavorite, hiddenFirst, secondFavorite, hiddenSecond),
                reorderedVisibleHistories =
                    listOf(
                        secondFavorite.copy(group = "Moved"),
                        firstFavorite,
                    ),
            )

        assertEquals(
            listOf("favorite-2", "hidden-1", "favorite-1", "hidden-2"),
            merged.map { it.id },
        )
        assertEquals("Moved", merged.first().group)
        assertEquals(hiddenFirst, merged[1])
        assertEquals(hiddenSecond, merged[3])
    }

    @Test
    fun favoriteReorder_rejectsInvalidSubsetWithoutChangingFullList() {
        val full = listOf(history("favorite", createdMinutes = 1, favorite = true))
        val unknown = history("unknown", createdMinutes = 2, favorite = true)

        assertEquals(full, mergeVisibleChatOrder(full, listOf(unknown)))
    }

    private fun history(
        id: String,
        createdMinutes: Long,
        lastMessageMinutes: Long? = null,
        favorite: Boolean = false,
        pinned: Boolean = false,
    ): ChatHistory {
        return ChatHistory(
            id = id,
            title = id,
            messages = emptyList(),
            createdAt = baseTime.plusMinutes(createdMinutes),
            updatedAt = baseTime.plusMinutes(100),
            lastMessageAt = lastMessageMinutes?.let(baseTime::plusMinutes),
            isFavorite = favorite,
            pinned = pinned,
        )
    }
}
