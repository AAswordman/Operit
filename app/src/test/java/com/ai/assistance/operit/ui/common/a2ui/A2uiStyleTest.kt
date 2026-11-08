package com.ai.assistance.operit.ui.common.a2ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class A2uiStyleTest {
    @Test fun cardsUseNeutralSurfacesButKeepUserAccentsInBothModes() {
        val accent = Color(0xFFE85488)
        val light = cardColorScheme(lightColorScheme(primary = accent))
        assertEquals(Color.White, light.surface)
        assertEquals(accent, light.primary)
        assertTrue(light.onSurface.luminance() < .1f)
        val dark = cardColorScheme(darkColorScheme(primary = accent))
        assertEquals(accent, dark.primary)
        assertTrue(dark.surface.luminance() < .1f)
        assertTrue(dark.onSurface.luminance() > .8f)
    }

    @Test fun customBubbleTextColorDecidesTheCardBackdrop() {
        // Light theme with a dark custom AI bubble: the message ink is light.
        val onDarkBubble = cardColorScheme(lightColorScheme(primary = Color(0xFFCA5079)), Color.White)
        assertTrue(onDarkBubble.surface.luminance() < .1f)
        assertTrue(onDarkBubble.onSurface.luminance() > .8f)
        val errorOnDark = onDarkBubble.error.luminance()
        assertTrue((errorOnDark + .05f) / (onDarkBubble.surface.luminance() + .05f) >= 3f)
        // Dark theme with a light custom AI bubble: the message ink is dark.
        val onLightBubble = cardColorScheme(darkColorScheme(primary = Color(0xFFFFB3C6)), Color.Black)
        assertEquals(Color.White, onLightBubble.surface)
        assertTrue(onLightBubble.onSurface.luminance() < .1f)
        val accent = onLightBubble.primary.luminance()
        assertTrue((1.05f) / (accent + .05f) >= 3f)
        val label = onLightBubble.onPrimary.luminance()
        assertTrue((maxOf(label, accent) + .05f) / (minOf(label, accent) + .05f) >= 4.5f)
        // Matching ink keeps the theme scheme untouched.
        val plain = cardColorScheme(lightColorScheme())
        val matched = cardColorScheme(lightColorScheme(), Color.Black)
        assertEquals(listOf(plain.surface, plain.onSurface, plain.primary, plain.outline),
            listOf(matched.surface, matched.onSurface, matched.primary, matched.outline))
    }

    @Test fun gridSpansPackInOrderAndPreserveIncompleteRows() {
        val keypad = cardGridRows(4, List(16) { 1 } + listOf(2, 1, 1))
        assertEquals(5, keypad.size)
        assertEquals(listOf(CardGridCell(16, 2), CardGridCell(17, 1), CardGridCell(18, 1)), keypad.last())
        assertEquals(listOf(listOf(CardGridCell(0, 3)), listOf(CardGridCell(1, 2), CardGridCell(2, 1))),
            cardGridRows(4, listOf(3, 2, 1)))
        assertEquals(1, cardGridRows(2, listOf(1, 1, 1)).last().sumOf { it.span })
        assertTrue(cardGridRows(2, emptyList()).isEmpty())
    }

    @Test fun semanticTextTonesReadOnCustomBubbles() {
        fun ratio(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
        val rainy = lightColorScheme(primary = Color(0xFFE91E63))
        for (bubble in listOf(Color(0xFF808080), Color(0xFFD9E2FF), Color(0xFF6A8F7A), Color(0xFF3A3F58), Color(0xFFFFE4EC))) {
            val ink = if (bubble.luminance() > .18f) Color.Black else Color.White
            val scheme = cardColorScheme(rainy, ink, bubble)
            for (tone in listOf("success", "info", "warning", "error")) {
                val color = cardToneInk(tone, scheme, bubble)!!
                assertTrue("$tone on $bubble", ratio(color, bubble) >= 4.5f)
            }
        }
        // Untoned and unknown tones keep the caller's ink; the plain card surface keeps the fixed hues.
        val plain = cardColorScheme(lightColorScheme())
        assertNull(cardToneInk(null, plain))
        assertNull(cardToneInk("pink", plain))
        assertEquals(cardSemanticToneColors("success", false)!!.first, cardToneInk("success", plain))
    }

    @Test fun secondaryTextAndAccentsReadOnMidToneBubbles() {
        fun ratio(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
        val rainy = lightColorScheme(primary = Color(0xFFE91E63), onSurfaceVariant = Color(0xFF806371))
        for (bubble in listOf(Color(0xFF808080), Color(0xFFD9E2FF), Color(0xFF6A8F7A), Color(0xFF3A3F58))) {
            val ink = if (bubble.luminance() > .18f) Color.Black else Color.White
            val scheme = cardColorScheme(rainy, ink, bubble)
            assertTrue("muted on $bubble", ratio(scheme.onSurfaceVariant, bubble) >= 4.5f)
            assertTrue("text on $bubble", ratio(scheme.onSurface, bubble) >= 4.5f)
            assertTrue("accent on $bubble", ratio(scheme.primary, bubble) >= 3f)
            assertTrue("error on $bubble", ratio(scheme.error, bubble) >= 3f)
            if (scheme.primary != rainy.primary) assertTrue("label on $bubble", ratio(scheme.onPrimary, scheme.primary) >= 4.5f)
        }
        // A bubble the theme already reads on keeps the theme's own colors.
        fun picked(s: androidx.compose.material3.ColorScheme) = listOf(s.primary, s.onPrimary, s.error, s.onSurface, s.onSurfaceVariant)
        assertEquals(picked(cardColorScheme(rainy, Color.Black)), picked(cardColorScheme(rainy, Color.Black, Color.White)))
        // A pale custom accent on the default bubble follows the app's own contrast setting.
        val pale = lightColorScheme(primary = Color(0xFFFFCDE8), surface = Color(0xFFFFF7FA))
        assertEquals(pale.primary, cardColorScheme(pale, Color.Black, pale.surface).primary)
        // Blue where neither black nor white reaches far: translucent thinking ink still gets readable text.
        val blue = Color(0xFF0066FF)
        val thinking = cardColorScheme(rainy, Color.White.copy(alpha = .6f), blue)
        assertTrue(ratio(thinking.onSurface, blue) >= 4.5f)
        assertEquals(1f, thinking.onSurfaceVariant.alpha)
        assertTrue(ratio(thinking.onSurfaceVariant, blue) >= 4.5f)
    }

    @Test fun selectedFillsFollowAFlippedCard() {
        fun ratio(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
        val cases = listOf(lightColorScheme(secondaryContainer = Color(0xFFFFD1DC)) to Color.White,
            darkColorScheme(secondaryContainer = Color(0xFF5A2A3A)) to Color.Black)
        for ((base, ink) in cases) {
            val scheme = cardColorScheme(base, ink)
            assertTrue(ratio(scheme.onSurface, scheme.secondaryContainer) >= 4.5f)
            assertTrue(ratio(scheme.onSecondaryContainer, scheme.secondaryContainer) >= 4.5f)
            assertTrue(ratio(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
        }
        // Card text tuned for a mid-gray bubble must still read on a flipped card's highlight fills.
        val gray = cardColorScheme(lightColorScheme(primary = Color(0xFFE91E63)), Color.White, Color(0xFF6A6A6A))
        for (text in listOf(gray.onSurface, gray.onSurfaceVariant))
            for (fill in listOf(gray.primaryContainer, gray.secondaryContainer)) assertTrue(ratio(text, fill) >= 4.5f)
        for (fill in listOf(gray.primaryContainer, gray.secondaryContainer)) assertTrue(ratio(gray.primary, fill) >= 3f)
        // A custom text color that fights the bubble must not leave the card's own panels unreadable.
        for ((base, badInk, bubble) in listOf(Triple(darkColorScheme(), Color.White, Color(0xFFD9E2FF)),
            Triple(darkColorScheme(), Color.White, Color(0xFF0099BB)), Triple(lightColorScheme(), Color.Black, Color(0xFF2A2A40)))) {
            val s = cardColorScheme(base, badInk, bubble)
            assertTrue(ratio(s.onSurface, s.surface) >= 4.5f)
            assertTrue(ratio(s.onSurface, bubble) >= 4.5f)
        }
    }

    @Test fun statusTextHasReadableContrastInBothModes() {
        for (base in listOf(lightColorScheme(), darkColorScheme())) {
            val scheme = cardColorScheme(base)
            for (tone in listOf("neutral", "success", "info", "warning", "error")) {
                val (ink, rawFill) = cardToneColors(tone, scheme)
                // Neutral insets are translucent washes; measure what is actually drawn.
                val fill = rawFill.compositeOver(scheme.surface)
                val lighter = maxOf(ink.luminance(), fill.luminance())
                val darker = minOf(ink.luminance(), fill.luminance())
                assertTrue(tone, (lighter + .05f) / (darker + .05f) >= 4.5f)
            }
        }
    }
}
