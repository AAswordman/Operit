package com.ai.assistance.operit.ui.features.extensions.packages.market.transfer

import java.text.NumberFormat
import java.util.Locale

internal fun formatTransferBytes(bytes: Double, locale: Locale = Locale.getDefault()): String {
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")
    var value = bytes.coerceAtLeast(0.0)
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    val number = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = if (unit == 0) 0 else 1
        minimumFractionDigits = 0
        isGroupingUsed = false
    }.format(value)
    return "$number ${units[unit]}"
}

internal fun formatTransferDuration(seconds: Long): String {
    val duration = seconds.coerceAtLeast(0L)
    return if (duration < 3600L) {
        String.format(Locale.ROOT, "%d:%02d", duration / 60L, duration % 60L)
    } else {
        String.format(Locale.ROOT, "%d:%02d:%02d", duration / 3600L, duration / 60L % 60L, duration % 60L)
    }
}
