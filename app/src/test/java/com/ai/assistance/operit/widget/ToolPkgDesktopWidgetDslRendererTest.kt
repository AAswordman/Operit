package com.ai.assistance.operit.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.preferences.GlobalPresentationSnapshot
import com.ai.assistance.operit.ui.theme.NativeThemeGlanceColor
import com.ai.assistance.operit.ui.theme.NativeThemeGlancePaletteV1
import com.ai.assistance.operit.ui.theme.resolveNativeThemeGlancePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ToolPkgDesktopWidgetDslRendererTest {
    @Test
    fun semanticColorTokensResolveFromTheGlancePalette() {
        val palette =
            resolveNativeThemeGlancePalette(
                presentation = GlobalPresentationSnapshot.default(),
                lightColorScheme =
                    lightColorScheme(
                        primary = Color(0xFF205080),
                        surface = Color(0xFFF4F1ED),
                        onSurface = Color(0xFF1D252C),
                    ),
                darkColorScheme =
                    darkColorScheme(
                        primary = Color(0xFF80B0E0),
                        surface = Color(0xFF1B242D),
                        onSurface = Color(0xFFF1F4F7),
                    ),
            )

        assertEquals(palette.primary, resolveToolPkgDesktopWidgetColorToken("primary", palette))
        assertEquals(palette.onPrimary, resolveToolPkgDesktopWidgetColorToken("onPrimary", palette))
        assertEquals(palette.surface, resolveToolPkgDesktopWidgetColorToken("surface", palette))
        assertEquals(palette.onSurface, resolveToolPkgDesktopWidgetColorToken("onSurface", palette))
        assertEquals(
            palette.onSurfaceVariant,
            resolveToolPkgDesktopWidgetColorToken("onSurfaceVariant", palette),
        )
        assertEquals(palette.error, resolveToolPkgDesktopWidgetColorToken("error", palette))
        assertNull(resolveToolPkgDesktopWidgetColorToken("customLiteral", palette))
    }

    @Test
    fun extendedMaterialColorTokensKeepTheirResolvedDayAndNightColors() {
        val lightScheme =
            lightColorScheme().copy(
                tertiaryContainer = Color(0xFFF8D6E7),
                errorContainer = Color(0xFFFFDAD6),
                inversePrimary = Color(0xFFB9C8FF),
                surfaceContainerHigh = Color(0xFFE8E8EC),
            )
        val darkScheme =
            darkColorScheme().copy(
                tertiaryContainer = Color(0xFF65304E),
                errorContainer = Color(0xFF93000A),
                inversePrimary = Color(0xFF405F90),
                surfaceContainerHigh = Color(0xFF2C2E33),
            )
        val palette =
            resolveNativeThemeGlancePalette(
                presentation = GlobalPresentationSnapshot.default(),
                lightColorScheme = lightScheme,
                darkColorScheme = darkScheme,
            )

        assertTokenColors(
            token = "tertiaryContainer",
            day = lightScheme.tertiaryContainer,
            night = darkScheme.tertiaryContainer,
            palette = palette,
        )
        assertTokenColors(
            token = "errorContainer",
            day = lightScheme.errorContainer,
            night = darkScheme.errorContainer,
            palette = palette,
        )
        assertTokenColors(
            token = "inversePrimary",
            day = lightScheme.inversePrimary,
            night = darkScheme.inversePrimary,
            palette = palette,
        )
        assertTokenColors(
            token = "surfaceContainerHigh",
            day = lightScheme.surfaceContainerHigh,
            night = darkScheme.surfaceContainerHigh,
            palette = palette,
        )
    }

    @Test
    fun everyMaterialColorSchemeColorFieldResolvesAsAGlanceToken() {
        val palette =
            resolveNativeThemeGlancePalette(
                presentation = GlobalPresentationSnapshot.default(),
                lightColorScheme = lightColorScheme(),
                darkColorScheme = darkColorScheme(),
            )

        ColorScheme::class.java.declaredFields
            .filter { field ->
                field.type == java.lang.Long.TYPE ||
                    field.type == java.lang.Long::class.java ||
                    field.type == Color::class.java
            }
            .forEach { field ->
                assertNotNull(
                    field.name,
                    resolveToolPkgDesktopWidgetColorToken(field.name, palette),
                )
            }
    }

    private fun assertTokenColors(
        token: String,
        day: Color,
        night: Color,
        palette: NativeThemeGlancePaletteV1,
    ) {
        assertEquals(
            NativeThemeGlanceColor(day = day, night = night),
            resolveToolPkgDesktopWidgetColorToken(token, palette),
        )
    }
}
