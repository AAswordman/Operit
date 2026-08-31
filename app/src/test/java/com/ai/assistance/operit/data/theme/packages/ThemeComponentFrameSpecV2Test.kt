package com.ai.assistance.operit.data.theme.packages

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeComponentFrameSpecV2Test {
    @Test
    fun hudNotchedFrameDecodesItsExplicitGeometry() {
        val frame =
            Json {
                ignoreUnknownKeys = false
                explicitNulls = false
            }.decodeFromString<ThemeComponentFrameSpecV2>(
                """
                {
                  "type": "hud_notched",
                  "cutSizeDp": 12.0,
                  "notchWidthFraction": 0.32,
                  "notchDepthDp": 7.0,
                  "border": { "token": "cyber.cyan", "widthDp": 1.5 },
                  "accent": { "token": "cyber.magenta", "widthDp": 1.0 }
                }
                """.trimIndent(),
            )

        assertTrue(frame is ThemeComponentFrameSpecV2.HudNotched)
        val hud = frame as ThemeComponentFrameSpecV2.HudNotched
        assertEquals(12f, hud.cutSizeDp)
        assertEquals(0.32f, hud.notchWidthFraction)
        assertEquals(listOf("cyber.cyan", "cyber.magenta"), hud.strokes().map { stroke -> stroke.token })
    }

    @Test
    fun invalidHudNotchWidthIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ThemeComponentFrameSpecV2.HudNotched(
                cutSizeDp = 12f,
                notchWidthFraction = 0.75f,
                notchDepthDp = 7f,
                border = ThemeComponentFrameStrokeV2(token = "cyber.cyan", widthDp = 1f),
            )
        }
    }
}
