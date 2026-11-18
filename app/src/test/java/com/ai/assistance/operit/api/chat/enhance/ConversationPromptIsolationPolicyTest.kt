package com.ai.assistance.operit.api.chat.enhance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPromptIsolationPolicyTest {
    @Test
    fun subtaskUsesRequestScopedTemplateAndRejectsPersonalContext() {
        assertEquals(
            "request persona",
            ConversationPromptIsolationPolicy.resolveSystemTemplate(
                isSubTask = true,
                requestTemplate = "request persona",
                globalTemplate = "global persona",
            ),
        )
        assertFalse(ConversationPromptIsolationPolicy.allowPersonalContext(isSubTask = true))
    }

    @Test
    fun ordinaryChatKeepsConfiguredTemplateAndPersonalContext() {
        assertEquals(
            "request persona",
            ConversationPromptIsolationPolicy.resolveSystemTemplate(
                isSubTask = false,
                requestTemplate = "request persona",
                globalTemplate = "global persona",
            ),
        )
        assertTrue(ConversationPromptIsolationPolicy.allowPersonalContext(isSubTask = false))
    }
}
