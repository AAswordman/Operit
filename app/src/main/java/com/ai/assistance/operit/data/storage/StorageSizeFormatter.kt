package com.ai.assistance.operit.data.storage

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

internal fun formatStorageSize(
    bytes: Long,
    locale: Locale = Locale.getDefault(),
): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    if (safeBytes < BYTES_PER_UNIT) return "$safeBytes B"

    var value = safeBytes.toDouble()
    var unitIndex = 0
    while (value >= BYTES_PER_UNIT && unitIndex < STORAGE_UNITS.lastIndex) {
        value /= BYTES_PER_UNIT
        unitIndex++
    }

    val fractionDigits =
        when {
            abs(value) >= 100.0 -> 0
            abs(value) >= 10.0 -> 1
            else -> 2
        }
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
        isGroupingUsed = false
    }
    return "${formatter.format(value)} ${STORAGE_UNITS[unitIndex]}"
}

private const val BYTES_PER_UNIT = 1024.0
private val STORAGE_UNITS = listOf("B", "KB", "MB", "GB", "TB", "PB")
