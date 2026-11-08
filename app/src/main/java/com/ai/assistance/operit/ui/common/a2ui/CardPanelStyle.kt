package com.ai.assistance.operit.ui.common.a2ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance

internal data class CardPanelStyle(val fill: Color, val backdrop: Color, val colors: ColorScheme)

/** Scope the palette to the painted panel, not to the chat bubble behind it. */
internal fun cardPanelStyle(background: String?, base: ColorScheme, parentBackdrop: Color): CardPanelStyle {
    val dark = base.surface.luminance() < .5f
    val fill = when (background) {
        "surface" -> base.surface
        "inset" -> base.surfaceContainerHigh
        "accentMuted" -> base.primaryContainer
        "light" -> Color(0xFFF2F3F5)
        "dark" -> Color(0xFF202B3B)
        "blue", "info" -> cardSemanticToneColors("info", dark)!!.second
        "green", "success" -> cardSemanticToneColors("success", dark)!!.second
        "orange", "warning" -> cardSemanticToneColors("warning", dark)!!.second
        "red", "error" -> cardSemanticToneColors("error", dark)!!.second
        else -> Color.Transparent
    }
    // A transparent wrapper must inherit the enclosing panel, including its secondary ink.
    if (fill == Color.Transparent) return CardPanelStyle(fill, parentBackdrop, base)
    val ground = fill.compositeOver(if (parentBackdrop.isSpecified) parentBackdrop else base.surface)
    val ink = getResolvedContrastingTextColor(ground)
    val colors = cardColorScheme(base, ink, ground).copy(
        surface = ground,
        background = ground,
        onBackground = ink,
    )
    return CardPanelStyle(fill, ground, colors)
}
