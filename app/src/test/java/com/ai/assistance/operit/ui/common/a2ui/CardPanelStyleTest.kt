package com.ai.assistance.operit.ui.common.a2ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class CardPanelStyleTest {
    @Test fun coloredPanelsKeepTheirOwnTextAndTonesReadableAcrossBubbleThemes() {
        for (theme in listOf(lightColorScheme(), darkColorScheme())) {
            for (bubble in listOf(Color.White, Color(0xFF202020), Color(0xFFCFD7FF))) {
                val base = cardColorScheme(theme, Color.Black, bubble)
                for (background in listOf("light", "dark", "blue", "green", "orange", "red", "accentMuted", "inset")) {
                    val panel = cardPanelStyle(background, base, bubble)
                    for (ink in listOf(panel.colors.onSurface, panel.colors.onSurfaceVariant)) {
                        assertTrue("$background $bubble text", resolvedContrastRatio(ink, panel.backdrop) >= 4.5)
                    }
                    for (tone in listOf("success", "info", "warning", "error")) {
                        val ink = cardToneInk(tone, panel.colors, panel.backdrop)!!
                        assertTrue("$background $tone", resolvedContrastRatio(ink, panel.backdrop) >= 4.5)
                    }
                    assertTrue("$background accent", resolvedContrastRatio(panel.colors.primary, panel.backdrop) >= 3.0)
                }
            }
        }
    }

    @Test fun transparentPanelsInheritAndOpaqueChildrenCanReverseTheLocalTheme() {
        val base = cardColorScheme(lightColorScheme())
        val dark = cardPanelStyle("dark", base, Color.White)
        val transparent = cardPanelStyle("transparent", dark.colors, dark.backdrop)
        assertSame(dark.colors, transparent.colors)
        assertEquals(dark.backdrop, transparent.backdrop)
        assertEquals(Color.Transparent, transparent.fill)
        val light = cardPanelStyle("light", transparent.colors, transparent.backdrop)
        assertTrue(resolvedContrastRatio(light.colors.onSurface, light.backdrop) >= 4.5)
        assertNotEquals(dark.colors.onSurface, light.colors.onSurface)
        assertEquals(Color(0xFF202B3B), dark.fill)
        assertEquals(Color(0xFFF2F3F5), light.fill)
        assertEquals(cardPanelStyle("blue", base, Color.White).fill, cardPanelStyle("info", base, Color.White).fill)
    }

    @Test fun compilerAcceptsBackgroundPaletteAndRejectsArbitraryStyles() {
        for (background in listOf("transparent", "surface", "inset", "accentMuted",
            "light", "dark", "blue", "green", "orange", "red", "info", "success", "warning", "error")) {
            val report = CardExpress.report("""root=Panel(background="$background",child=Text(text="Hello"))""")
            assertEquals(report.toString(), JsonPrimitive(true), report["valid"])
        }
        for (background in listOf("#123456", "url(image.png)", "linear-gradient(red,blue)")) {
            assertEquals(JsonPrimitive(false),
                CardExpress.report("""root=Panel(background="$background",child=Text(text="Hello"))""")["valid"])
        }
    }
}
