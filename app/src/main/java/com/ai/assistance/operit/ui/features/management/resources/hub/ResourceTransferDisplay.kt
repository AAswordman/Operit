package com.ai.assistance.operit.ui.features.management.resources.hub

import java.util.Locale

/** 使用十进制单位，列表、详情和速度采用相同换算。 */
internal fun resourceSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
    require(bytes >= 0)
    if (bytes < 1000) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB", "PB", "EB")
    var value = bytes / 1000.0
    var unit = 0
    while (value >= 999.95 && unit < units.lastIndex) { value /= 1000.0; unit++ }
    return String.format(locale, "%.1f %s", value, units[unit])
}

/** 滑动窗口同时接收传输回调和定时心跳，停滞后速度归零；缓存字节不计入网速。 */
internal class DownloadRateMeter {
    private val samples = java.util.ArrayDeque<Pair<Long, Long>>()
    @Synchronized fun reset() { samples.clear() }
    @Synchronized fun sample(now: Long, bytes: Long): Double {
        if (samples.isNotEmpty() && (bytes < samples.last.second || now < samples.last.first)) samples.clear()
        samples.addLast(now to bytes)
        while (samples.size > 1 && samples.elementAt(1).first <= now - 5000) samples.removeFirst()
        val elapsed = now - samples.first.first
        return if (elapsed >= 250) (bytes - samples.first.second) * 1000.0 / elapsed else 0.0
    }
}
