package com.ai.assistance.operit.ui.features.conversation.chat.modules.usage

import java.text.NumberFormat
import java.util.Locale

internal fun formatUsageTokens(value: Long): String =
    NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

internal fun formatUsagePercent(value: Long, total: Long, decimals: Int = 1): String {
    if (total <= 0L) return "—"
    return String.format(
        Locale.getDefault(),
        "%.${decimals}f%%",
        value.toDouble() / total.toDouble() * 100.0
    )
}
