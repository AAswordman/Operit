package com.ai.assistance.operit.data.preferences

import org.junit.Assert.assertThrows
import org.junit.Test

class GlobalPresentationPreferencesTest {
    @Test
    fun themeModeRoundTripsKnownValues() {
        GlobalThemeMode.entries.forEach { mode ->
            org.junit.Assert.assertEquals(mode, GlobalThemeMode.fromValue(mode.value))
        }
    }

    @Test
    fun chatStyleRoundTripsKnownValues() {
        GlobalChatStyle.entries.forEach { style ->
            org.junit.Assert.assertEquals(style, GlobalChatStyle.fromValue(style.value))
        }
    }

    @Test
    fun inputStyleRoundTripsKnownValues() {
        GlobalInputStyle.entries.forEach { style ->
            org.junit.Assert.assertEquals(style, GlobalInputStyle.fromValue(style.value))
        }
    }

    @Test
    fun unknownStoredValueFailsFast() {
        assertThrows(NoSuchElementException::class.java) {
            GlobalThemeMode.fromValue("pink")
        }
        assertThrows(NoSuchElementException::class.java) {
            GlobalChatStyle.fromValue("hologram")
        }
        assertThrows(NoSuchElementException::class.java) {
            GlobalInputStyle.fromValue("voice")
        }
    }
}
