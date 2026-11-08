package com.ai.assistance.operit.ui.features.models.tokenstats

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class TokenActivitySeriesPoint(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val tokens: Long,
    val hour: Int? = null,
)

internal enum class TokenActivityAxisMode { HOUR, DAY, WEEK, MONTH_TRANSITIONS }

/** 每个时间桶独立标注，月视图展示真实的周区间，不把跨月边界当作柱子名称。 */
internal fun tokenActivityAxisLabels(
    points: List<TokenActivitySeriesPoint>, mode: TokenActivityAxisMode, locale: Locale,
): List<List<String>> {
    val month = DateTimeFormatter.ofPattern("MMM", locale)
    val day = DateTimeFormatter.ofPattern(when (locale.language) {
        "zh", "ja" -> "d日"
        "ko" -> "d일"
        else -> "d"
    }, locale)
    val shortDate = DateTimeFormatter.ofPattern(when (locale.language) {
        "zh", "ja" -> "M月d日"
        "ko" -> "M월 d일"
        "en" -> "MMM d"
        else -> "d MMM"
    }, locale)
    return points.map { point ->
        when (mode) {
            TokenActivityAxisMode.HOUR -> listOf("${checkNotNull(point.hour)}:00")
            TokenActivityAxisMode.DAY -> listOf(month.format(point.startDate), day.format(point.startDate))
            TokenActivityAxisMode.WEEK -> listOf(
                shortDate.format(point.startDate), "–${shortDate.format(point.endDate)}",
            )
            TokenActivityAxisMode.MONTH_TRANSITIONS -> emptyList()
        }
    }
}
