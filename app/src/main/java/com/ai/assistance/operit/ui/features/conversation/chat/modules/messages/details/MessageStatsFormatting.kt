package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.details

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatMessageNumber(value: Long): String {
    return String.format(Locale.US, "%,d", value)
}

internal fun formatCompactMessageTokens(value: Long): String {
    // 整数缩写在四舍五入后达到下一单位时直接进位，避免显示 1000K 或 1000M。
    val (divisor, suffix) = when {
        value >= 999_500_000L -> 1_000_000_000L to "B"
        value >= 999_500L -> 1_000_000L to "M"
        value >= 1_000L -> 1_000L to "K"
        else -> return formatMessageNumber(value)
    }
    // 先除再处理余数，避免给接近 Long 上限的数值直接加舍入偏移而溢出。
    val rounded = value / divisor + if (value % divisor >= divisor / 2L) 1L else 0L
    return "$rounded$suffix"
}

internal fun formatMessageDuration(durationMs: Long): String {
    return if (durationMs >= 1_000L) {
        String.format(Locale.US, "%,.2f s", durationMs / 1000.0)
    } else {
        "${formatMessageNumber(durationMs)} ms"
    }
}

internal fun calculateMessageTokenSpeed(outputTokens: Long, outputDurationMs: Long): Double? {
    if (outputTokens <= 0L || outputDurationMs <= 0L) return null
    return outputTokens.toDouble() * 1000.0 / outputDurationMs.toDouble()
}

internal fun formatMessageTokenSpeed(tokenSpeed: Double): String {
    return String.format(Locale.US, "%,.1f", tokenSpeed)
}

internal fun formatMessageCompletionTime(completedAt: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(completedAt))
}
