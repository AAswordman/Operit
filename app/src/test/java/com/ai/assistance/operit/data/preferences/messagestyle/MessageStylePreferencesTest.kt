package com.ai.assistance.operit.data.preferences.messagestyle

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageStylePreferencesTest {
    @Test
    fun legacyBubblePreservesBothSides() {
        val expanded = MessageStylePreferences.expandLegacy(mapOf("chat_style" to "bubble"))
        assertEquals("bubble", expanded[MessageStylePreferences.USER])
        assertEquals("bubble", expanded[MessageStylePreferences.AI])
    }

    @Test
    fun legacyCommandAndFreshThemeKeepCommandDefaults() {
        for (stored in listOf(emptyMap(), mapOf("chat_style" to "cursor"))) {
            val expanded = MessageStylePreferences.expandLegacy(stored)
            assertEquals("cursor", expanded[MessageStylePreferences.USER])
            assertEquals("cursor", expanded[MessageStylePreferences.AI])
        }
    }

    @Test
    fun explicitMixedStylesAreNotOverwrittenByLegacyValue() {
        val expanded = MessageStylePreferences.expandLegacy(mapOf(
            "chat_style" to "cursor",
            "user_chat_style" to "bubble",
            "ai_chat_style" to "cursor",
            "custom_chat_title" to "Echo",
        ))
        assertEquals("bubble", expanded[MessageStylePreferences.USER])
        assertEquals("cursor", expanded[MessageStylePreferences.AI])
        assertEquals("Echo", expanded["custom_chat_title"])
    }

    @Test
    fun onlyMissingSideIsExpanded() {
        val expanded = MessageStylePreferences.expandLegacy(mapOf(
            "chat_style" to "bubble",
            "user_chat_style" to "cursor",
        ))
        assertEquals("cursor", expanded[MessageStylePreferences.USER])
        assertEquals("bubble", expanded[MessageStylePreferences.AI])
    }
}