package com.ai.assistance.operit.ui.features.chat.components

import java.util.Locale
import kotlin.math.floor

/**
 * 计算缓存命中率：缓存输入 / 输入，保留两位小数并向下截断（不做四舍五入）。
 * 例如 99.999% 显示为 99.99%，不会进位到 100.00%。
 * 输入为 0 时命中率无意义，返回 "-"。
 */
internal fun formatCacheHitRate(cachedInput: Long, input: Long): String {
    if (input <= 0L) return "-"
    val rate = cachedInput.coerceAtLeast(0L) * 100.0 / input
    // +1e-9 仅用于抵消浮点表示误差，保证 95.88 不会被截成 95.87
    val truncated = floor(rate * 100.0 + 1e-9) / 100.0
    return String.format(Locale.US, "%.2f%%", truncated)
}
