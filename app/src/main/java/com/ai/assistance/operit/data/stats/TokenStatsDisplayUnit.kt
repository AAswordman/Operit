package com.ai.assistance.operit.data.stats

import java.util.Locale

enum class TokenStatsDisplayUnit {
    MILLIONS,
    BILLIONS;

    fun toggled(): TokenStatsDisplayUnit =
        when (this) {
            MILLIONS -> BILLIONS
            BILLIONS -> MILLIONS
        }
}

fun formatTokenCount(value: Long, unit: TokenStatsDisplayUnit): String {
    if (value < 1_000_000L) {
        return when {
            value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
            else -> value.toString()
        }
    }
    return when (unit) {
        TokenStatsDisplayUnit.MILLIONS ->
            String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        TokenStatsDisplayUnit.BILLIONS ->
            String.format(Locale.US, "%.3fB", value / 1_000_000_000.0)
    }
}
