package com.ai.assistance.operit.ui.features.automation.terminal

import com.ai.assistance.operit.terminal.view.domain.ansi.AnsiTerminalEmulator
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalResizeCursorTest {
    private fun text(emulator: AnsiTerminalEmulator): String = emulator.getFullContent()
        .joinToString("") { row -> row.joinToString("") { it.char.toString() }.trimEnd() }

    @Test
    fun heightChangePreservesUnsubmittedCommandAndInsertionPosition() {
        val emulator = AnsiTerminalEmulator(12, 4)
        emulator.parse("echo abc")
        emulator.resize(12, 8)
        assertEquals(8, emulator.getCursorX())
        assertEquals(0, emulator.getCursorY())
        emulator.parse("d")
        assertEquals("echo abcd", text(emulator))
    }

    @Test
    fun widthChangeMapsCursorThroughWrappedLogicalLine() {
        val emulator = AnsiTerminalEmulator(6, 4)
        emulator.parse("abcdefghi")
        emulator.resize(4, 6)
        assertEquals(1, emulator.getCursorX())
        assertEquals(2, emulator.getCursorY())
        emulator.parse("j")
        assertEquals("abcdefghij", text(emulator))
    }

    @Test
    fun blankPromptLineIsNotTrimmedDuringResize() {
        val emulator = AnsiTerminalEmulator(8, 4)
        emulator.parse("one\r\n\r\n")
        emulator.resize(8, 8)
        assertEquals(0, emulator.getCursorX())
        assertEquals(2, emulator.getCursorY())
        emulator.parse("next")
        assertEquals('n', emulator.getFullContent()[2][0].char)
    }

    @Test
    fun pendingWrapStillWrapsAfterResize() {
        val emulator = AnsiTerminalEmulator(4, 4)
        emulator.parse("abcd")
        emulator.resize(2, 6)
        assertEquals(2, emulator.getCursorX())
        assertEquals(1, emulator.getCursorY())
        emulator.parse("e")
        assertEquals("abcde", text(emulator))
    }

    @Test
    fun savedCursorIsRemappedWithTheCurrentCursor() {
        val emulator = AnsiTerminalEmulator(8, 4)
        emulator.parse("abc\u001b7def")
        emulator.resize(4, 6)
        emulator.parse("\u001b8X")
        assertEquals("abcXef", text(emulator))
    }
}
