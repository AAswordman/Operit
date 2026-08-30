package com.ai.assistance.operit.ui.theme.scene.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeScenePathParserTest {
    @Test
    fun parsesMoveLineQuadCubicAndClose() {
        val commands =
            parseThemeScenePathCommands(
                "M 0 0 L 1 0 Q 0.5 0.5 1 1 C 0.2 0.8 0.1 0.6 0 1 Z",
            )

        assertEquals(5, commands.size)
        assertEquals(ThemeScenePathDataV1.Command.MoveTo(0f, 0f), commands[0])
        assertEquals(ThemeScenePathDataV1.Command.LineTo(1f, 0f), commands[1])
        assertEquals(
            ThemeScenePathDataV1.Command.QuadTo(0.5f, 0.5f, 1f, 1f),
            commands[2],
        )
        assertEquals(
            ThemeScenePathDataV1.Command.CubicTo(0.2f, 0.8f, 0.1f, 0.6f, 0f, 1f),
            commands[3],
        )
        assertEquals(ThemeScenePathDataV1.Command.Close, commands[4])
    }

    @Test
    fun parsesCommaSeparatedCompactForm() {
        val commands = parseThemeScenePathCommands("M 0.1,0.2 L 0.9,0.8")

        assertEquals(
            listOf(
                ThemeScenePathDataV1.Command.MoveTo(0.1f, 0.2f),
                ThemeScenePathDataV1.Command.LineTo(0.9f, 0.8f),
            ),
            commands,
        )
    }

    @Test
    fun coordinateOutsideUnitSquareIsRejected() {
        assertThrows(ThemeScenePathParseException::class.java) {
            parseThemeScenePathCommands("M 0 0 L 1.2 0")
        }
    }

    @Test
    fun unsupportedCommandIsRejected() {
        assertThrows(ThemeScenePathParseException::class.java) {
            parseThemeScenePathCommands("A 0 0")
        }
    }

    @Test
    fun missingCoordinatesAreRejected() {
        assertThrows(ThemeScenePathParseException::class.java) {
            parseThemeScenePathCommands("M 0.5")
        }
    }

    @Test
    fun emptyPathIsRejected() {
        assertThrows(ThemeScenePathParseException::class.java) {
            parseThemeScenePathCommands("   ")
        }
        assertTrue(parseThemeScenePathCommands("M 0 0").size == 1)
    }
}
