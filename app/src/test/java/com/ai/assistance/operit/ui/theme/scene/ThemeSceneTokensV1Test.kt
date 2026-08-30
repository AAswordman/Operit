package com.ai.assistance.operit.ui.theme.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSceneTokensV1Test {
    @Test
    fun colorTokenResolvesLightAndDarkValues() {
        val tokenSet =
            ThemeSceneTokenSetV1(
                tokens =
                    mapOf(
                        "color.primary" to
                            ThemeSceneTokenValueV1.ColorToken(
                                lightArgb = 0xFF3355AA,
                                darkArgb = 0xFFAACCFF,
                            ),
                    ),
            )
        val resolver = ThemeSceneTokenResolverV1(tokenSet)

        assertEquals(
            androidx.compose.ui.graphics.Color(0xFF3355AA),
            resolver.color(ThemeSceneTokenIdV1("color.primary"), darkTheme = false),
        )
        assertEquals(
            androidx.compose.ui.graphics.Color(0xFFAACCFF),
            resolver.color(ThemeSceneTokenIdV1("color.primary"), darkTheme = true),
        )
    }

    @Test
    fun unknownTokenFailsFast() {
        val resolver =
            ThemeSceneTokenResolverV1(
                ThemeSceneTokenSetV1(
                    tokens =
                        mapOf(
                            "color.primary" to
                                ThemeSceneTokenValueV1.ColorToken(0xFF000000, 0xFFFFFFFF),
                        ),
                ),
            )

        assertThrows(IllegalStateException::class.java) {
            resolver.color(ThemeSceneTokenIdV1("color.missing"), darkTheme = false)
        }
    }

    @Test
    fun colorLookupOnNonColorTokenFailsFast() {
        val resolver =
            ThemeSceneTokenResolverV1(
                ThemeSceneTokenSetV1(
                    tokens =
                        mapOf(
                            "dim.corner" to ThemeSceneTokenValueV1.DimensionToken(8f),
                        ),
                ),
            )

        assertThrows(IllegalStateException::class.java) {
            resolver.color(ThemeSceneTokenIdV1("dim.corner"), darkTheme = false)
        }
    }

    @Test
    fun textStyleTokenResolvesReferencedColor() {
        val resolver =
            ThemeSceneTokenResolverV1(
                ThemeSceneTokenSetV1(
                    tokens =
                        mapOf(
                            "color.text" to
                                ThemeSceneTokenValueV1.ColorToken(0xFF111111, 0xFFEEEEEE),
                            "text.body" to
                                ThemeSceneTokenValueV1.TextStyleToken(
                                    fontSizeSp = 15f,
                                    lineHeightSp = 22f,
                                    fontWeight = 500,
                                    letterSpacingEm = 0.02f,
                                    color = ThemeSceneTokenIdV1("color.text"),
                                ),
                        ),
                ),
            )

        val style = resolver.textStyle(ThemeSceneTokenIdV1("text.body"), darkTheme = true)

        assertEquals(15f, style.fontSizeSp)
        assertEquals(22f, style.lineHeightSp)
        assertEquals(500, style.fontWeight)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFEEEEEE), style.color)
    }

    @Test
    fun invalidTokenKeyIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ThemeSceneTokenSetV1(tokens = mapOf("Bad Key" to ThemeSceneTokenValueV1.DimensionToken(1f)))
        }
    }

    @Test
    fun validatingTokenSetReportsUnknownReference() {
        val issues =
            validateThemeSceneTokenSetV1(
                ThemeSceneTokenSetV1(
                    tokens =
                        mapOf(
                            "text.body" to
                                ThemeSceneTokenValueV1.TextStyleToken(
                                    fontSizeSp = 15f,
                                    color = ThemeSceneTokenIdV1("color.missing"),
                                ),
                        ),
                ),
            )

        assertTrue(issues.any { it.code == ThemeSceneIssueCodeV1.UNKNOWN_TOKEN })
    }

    @Test
    fun validatingTokenSetReportsNonColorReference() {
        val issues =
            validateThemeSceneTokenSetV1(
                ThemeSceneTokenSetV1(
                    tokens =
                        mapOf(
                            "dim.gap" to ThemeSceneTokenValueV1.DimensionToken(4f),
                            "text.body" to
                                ThemeSceneTokenValueV1.TextStyleToken(
                                    fontSizeSp = 15f,
                                    color = ThemeSceneTokenIdV1("dim.gap"),
                                ),
                        ),
                ),
            )

        assertTrue(issues.any { it.code == ThemeSceneIssueCodeV1.INVALID_TOKEN_REFERENCE })
    }

    @Test
    fun validTokenSetHasNoIssues() {
        val issues =
            validateThemeSceneTokenSetV1(
                ThemeSceneTokenSetV1(
                    tokens =
                        mapOf(
                            "color.text" to
                                ThemeSceneTokenValueV1.ColorToken(0xFF111111, 0xFFEEEEEE),
                            "text.body" to
                                ThemeSceneTokenValueV1.TextStyleToken(
                                    fontSizeSp = 15f,
                                    color = ThemeSceneTokenIdV1("color.text"),
                                ),
                        ),
                ),
            )

        assertTrue(issues.isEmpty())
    }
}
