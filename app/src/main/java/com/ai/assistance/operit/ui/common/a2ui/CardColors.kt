package com.ai.assistance.operit.ui.common.a2ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** 对比度仅用于卡片本身，保持现有发行版主题和用户选色不变。 */
internal fun resolvedContrastRatio(first: Color, second: Color): Double {
    val a = first.luminance().toDouble()
    val b = second.luminance().toDouble()
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

internal fun getResolvedContrastingTextColor(background: Color): Color =
    if (resolvedContrastRatio(Color.Black, background) >= resolvedContrastRatio(Color.White, background)) {
        Color.Black
    } else {
        Color.White
    }

internal fun ensureAccentReadableOn(accent: Color, surface: Color): Color =
    ensureReadableOn(accent, surface, 3.0)

/** 在原色和可读文字色之间寻找达到指定对比度的最小调整。 */
internal fun ensureReadableOn(color: Color, surface: Color, target: Double): Color {
    if (resolvedContrastRatio(color, surface) >= target) return color
    val destination = getResolvedContrastingTextColor(surface)
    var low = 0f
    var high = 1f
    repeat(24) {
        val middle = (low + high) / 2f
        if (resolvedContrastRatio(lerp(color, destination, middle), surface) >= target + 0.01) {
            high = middle
        } else {
            low = middle
        }
    }
    return lerp(color, destination, high)
}