package com.ai.assistance.operit.ui.common.a2ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance

/** White light-mode cards with the app's ink and border colors. Blocks are now embedded without a
 * frame, so insets are a translucent ink wash: they stay visible on any chat bubble color.
 * [ink] is the message text color. A custom chat bubble can be dark in a light theme (or the
 * reverse), so light ink means the card sits on a dark backdrop, whatever the app theme says.
 * [backdrop] is the solid bubble color when known; mid-tone bubbles need text tuned to them. */
internal fun cardColorScheme(base: ColorScheme, ink: Color = Color.Unspecified, backdrop: Color = Color.Unspecified): ColorScheme {
    val themeDark = base.surface.luminance() < .5f
    // A known solid bubble decides directly (a custom text color may not match it); otherwise the ink.
    val dark = when {
        backdrop.isSpecified -> getResolvedContrastingTextColor(backdrop) == Color.White
        ink.isSpecified -> ink.luminance() > .5f
        else -> themeDark
    }
    // The theme's own ink and outlines only fit when the backdrop matches the theme.
    val flipped = dark != themeDark
    val surface = if (dark) Color(0xFF202124) else Color.White
    val onSurface = if (dark) Color(0xFFF1F1F1) else if (flipped) Color(0xFF1F1F1F) else base.onSurface
    val inset = if (dark) Color.White.copy(alpha = .08f) else onSurface.copy(alpha = .055f)
    val primary = if (flipped) ensureAccentReadableOn(base.primary, surface) else base.primary
    val scheme = base.copy(
        primary = primary,
        // Buttons and toggles fill with primary, so their label must follow the adjusted fill.
        onPrimary = if (flipped) getResolvedContrastingTextColor(primary) else base.onPrimary,
        error = if (flipped) ensureAccentReadableOn(base.error, surface) else base.error,
        surface = surface,
        onSurface = onSurface,
        onSurfaceVariant = if (dark) Color(0xFFBFC0C4) else if (flipped) Color(0xFF5F6368) else base.onSurfaceVariant,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = inset,
        surfaceContainerHigh = inset,
        surfaceContainerHighest = inset,
        outline = if (dark) Color(0xFF77797E) else if (flipped) Color(0xFF80868B) else base.outline,
        outlineVariant = if (dark) Color(0xFF55575C) else if (flipped) Color(0xFFC4C7C5) else base.outlineVariant,
    )
    // Flipped, the theme's containers would be light-on-light: fills follow the card surface instead,
    // staying close to it because card text is tuned for the bubble and is also drawn on them. Selection
    // shows through borders and labels. The highlight wash is neutral so accent links on it keep contrast.
    fun withContainers(s: ColorScheme) = if (!flipped) s else s.copy(
        primaryContainer = s.primary.copy(alpha = .22f).compositeOver(surface),
        onPrimaryContainer = s.onSurface,
        secondaryContainer = s.onSurface.copy(alpha = .12f).compositeOver(surface),
        onSecondaryContainer = s.onSurface,
    )
    if (!backdrop.isSpecified || !ink.isSpecified) return withContainers(scheme)
    // Colors that already read on the bubble come back untouched. The last resort is black or white,
    // whichever reads best on it: the message ink may be translucent (thinking blocks) or a poor custom pick.
    // Only these accent and text roles are tuned to the bubble; fills and borders follow the card surface.
    val fallback = getResolvedContrastingTextColor(backdrop)
    fun readable(color: Color, target: Double) =
        ensureReadableOn(color, backdrop, target).takeIf { resolvedContrastRatio(it, backdrop) >= target } ?: fallback
    // The default bubble is the theme surface: accents there follow the app-wide contrast setting.
    val customBubble = backdrop != base.surface
    val accent = if (customBubble) readable(scheme.primary, 3.0) else scheme.primary
    return withContainers(scheme.copy(
        primary = accent,
        onPrimary = if (accent == scheme.primary) scheme.onPrimary else getResolvedContrastingTextColor(accent),
        error = if (customBubble) readable(scheme.error, 3.0) else scheme.error,
        onSurface = readable(scheme.onSurface, 4.5),
        onSurfaceVariant = readable(scheme.onSurfaceVariant, 4.5),
    ))
}

internal data class CardGridCell(val index: Int, val span: Int)

/** Pack in source order; never redistribute an incomplete row into wider columns. */
internal fun cardGridRows(count: Int, spans: List<Int>): List<List<CardGridCell>> {
    require(count in 1..4 && spans.all { it in 1..count })
    val rows = mutableListOf<List<CardGridCell>>()
    var row = mutableListOf<CardGridCell>()
    var used = 0
    spans.forEachIndexed { index, span ->
        if (used + span > count) {
            rows += row
            row = mutableListOf()
            used = 0
        }
        row += CardGridCell(index, span)
        used += span
    }
    if (row.isNotEmpty()) rows += row
    return rows
}
