package com.ai.assistance.operit.ui.features.chat.components

import com.ai.assistance.operit.data.model.ChatHistory
import java.time.LocalDate
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ChatHistoryCategory {
    ALL,
    RECENT,
    FAVORITES,
}

internal enum class ChatHistoryTimeSection {
    TODAY,
    YESTERDAY,
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
    SEVEN_DAYS_AGO,
}

private const val LOCAL_DATE_RECHECK_INTERVAL_MILLIS = 60_000L

internal fun ChatHistory.timeSection(
    referenceDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
): ChatHistoryTimeSection {
    val activityDate =
        if (lastMessageAt != null) {
            lastMessageAtEpochMillis
                ?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
                ?: lastMessageAt.toLocalDate()
        } else {
            createdAtEpochMillis
                ?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
                ?: createdAt.toLocalDate()
        }
    return when (ChronoUnit.DAYS.between(activityDate, referenceDate)) {
        in Long.MIN_VALUE..0L -> ChatHistoryTimeSection.TODAY
        1L -> ChatHistoryTimeSection.YESTERDAY
        in 2L..6L ->
            when (activityDate.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> ChatHistoryTimeSection.MONDAY
                java.time.DayOfWeek.TUESDAY -> ChatHistoryTimeSection.TUESDAY
                java.time.DayOfWeek.WEDNESDAY -> ChatHistoryTimeSection.WEDNESDAY
                java.time.DayOfWeek.THURSDAY -> ChatHistoryTimeSection.THURSDAY
                java.time.DayOfWeek.FRIDAY -> ChatHistoryTimeSection.FRIDAY
                java.time.DayOfWeek.SATURDAY -> ChatHistoryTimeSection.SATURDAY
                java.time.DayOfWeek.SUNDAY -> ChatHistoryTimeSection.SUNDAY
            }
        else -> ChatHistoryTimeSection.SEVEN_DAYS_AGO
    }
}

internal fun millisUntilNextLocalDate(
    now: Instant,
    zoneId: ZoneId,
): Long {
    val nextDateStart =
        now.atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
    return Duration.between(now, nextDateStart).toMillis().coerceAtLeast(0L) + 1L
}

internal fun localDateRefreshDelayMillis(
    now: Instant,
    zoneId: ZoneId,
): Long =
    minOf(
        millisUntilNextLocalDate(now, zoneId),
        LOCAL_DATE_RECHECK_INTERVAL_MILLIS,
    )

internal fun selectChatHistoriesForCategory(
    histories: List<ChatHistory>,
    category: ChatHistoryCategory,
): List<ChatHistory> {
    val visibleHistories = histories
    return when (category) {
        ChatHistoryCategory.ALL -> visibleHistories
        ChatHistoryCategory.RECENT ->
            visibleHistories.sortedWith(
                compareByDescending<ChatHistory> { it.lastMessageAt ?: it.createdAt }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.id }
            )
        ChatHistoryCategory.FAVORITES -> visibleHistories.filter { it.isFavorite }
    }
}

internal fun canReorderChatHistory(
    category: ChatHistoryCategory,
    searchQuery: String,
): Boolean {
    return category != ChatHistoryCategory.RECENT && searchQuery.isBlank()
}

internal fun canManageChatFolders(category: ChatHistoryCategory): Boolean {
    return category == ChatHistoryCategory.ALL
}

internal fun shouldInheritCurrentFolderForNewChat(category: ChatHistoryCategory): Boolean {
    return category != ChatHistoryCategory.RECENT
}

/**
 * 将过滤视图的新顺序合并回完整列表。
 *
 * 未显示的聊天保留在原槽位，只有可见子集彼此交换位置，避免收藏页拖拽时用过滤列表
 * 覆盖或删除未收藏聊天。
 */
internal fun mergeVisibleChatOrder(
    fullHistories: List<ChatHistory>,
    reorderedVisibleHistories: List<ChatHistory>,
): List<ChatHistory> {
    if (reorderedVisibleHistories.isEmpty()) {
        return fullHistories
    }

    val visibleIds = reorderedVisibleHistories.mapTo(linkedSetOf()) { it.id }
    val fullIds = fullHistories.mapTo(hashSetOf()) { it.id }
    if (
        visibleIds.size != reorderedVisibleHistories.size ||
            !fullIds.containsAll(visibleIds) ||
            fullHistories.count { it.id in visibleIds } != visibleIds.size
    ) {
        return fullHistories
    }

    val reorderedIterator = reorderedVisibleHistories.iterator()
    return fullHistories.map { history ->
        if (history.id in visibleIds) reorderedIterator.next() else history
    }
}
