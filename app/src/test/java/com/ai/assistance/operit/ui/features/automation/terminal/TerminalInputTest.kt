package com.ai.assistance.operit.ui.features.automation.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalInputTest {
    @Test
    fun configuredShortcutsBecomeTerminalControlSequences() {
        assertEquals("\u001b[A", decodeTerminalShortcut("\\e[A"))
        assertEquals("\t", decodeTerminalShortcut("\\t"))
        assertEquals("\r\n", decodeTerminalShortcut("\\r\\n"))
        assertEquals("\\path\\q", decodeTerminalShortcut("\\\\path\\q"))
    }

    @Test
    fun ctrlCSendsInterruptAndAltFSendsEscapePrefix() {
        assertEquals("\u0003", applyTerminalModifiers("c", ctrl = true, alt = false))
        assertEquals("\u001bf", applyTerminalModifiers("f", ctrl = false, alt = true))
        assertEquals("\u001b\u0003", applyTerminalModifiers("c", ctrl = true, alt = true))
    }

    @Test
    fun unmodifiedNavigationKeepsItsAnsiSequence() {
        assertEquals("\u001b[A", applyTerminalModifiers("\u001b[A", ctrl = false, alt = false))
        assertEquals("\u001b[5~", applyTerminalModifiers("\u001b[5~", ctrl = false, alt = false))
    }

    @Test
    fun modifiedNavigationUsesXtermModifierParameters() {
        assertEquals("\u001b[1;5A", applyTerminalModifiers("\u001b[A", ctrl = true, alt = false))
        assertEquals("\u001b[5;3~", applyTerminalModifiers("\u001b[5~", ctrl = false, alt = true))
        assertEquals("\u001b[1;7D", applyTerminalModifiers("\u001b[D", ctrl = true, alt = true))
    }

    @Test
    fun plainInputAndUnknownEscapeSequencesStayIntact() {
        assertEquals("中文 pwd\r", applyTerminalModifiers("中文 pwd\r", ctrl = false, alt = false))
        assertEquals("\u001b[?25l", applyTerminalModifiers("\u001b[?25l", ctrl = true, alt = false))
        assertEquals("\u001b[", applyTerminalModifiers("\u001b[", ctrl = true, alt = false))
    }
}
