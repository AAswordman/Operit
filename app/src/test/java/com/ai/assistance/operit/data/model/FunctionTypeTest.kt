package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionTypeTest {

    @Test fun `prompt function type has CHAT and VOICE`() {
        assertEquals(2, PromptFunctionType.values().size)
        assertTrue(PromptFunctionType.values().contains(PromptFunctionType.CHAT))
        assertTrue(PromptFunctionType.values().contains(PromptFunctionType.VOICE))
    }

    @Test fun `prompt function type name returns enum name`() {
        assertEquals("CHAT", PromptFunctionType.CHAT.name)
        assertEquals("VOICE", PromptFunctionType.VOICE.name)
    }

    @Test fun `function type has all expected values`() {
        val expected = listOf(
            FunctionType.CHAT, FunctionType.SUMMARY, FunctionType.TITLE_GENERATION,
            FunctionType.MEMORY, FunctionType.UI_CONTROLLER, FunctionType.TRANSLATION,
            FunctionType.GREP, FunctionType.ROLE_RESPONSE_PLANNER,
            FunctionType.IMAGE_RECOGNITION, FunctionType.AUDIO_RECOGNITION,
            FunctionType.VIDEO_RECOGNITION,
        )
        assertEquals(11, FunctionType.values().size)
        for (type in expected) {
            assertTrue("Missing $type", FunctionType.values().contains(type))
        }
    }

    @Test fun `function type name returns correct string`() {
        assertEquals("CHAT", FunctionType.CHAT.name)
        assertEquals("SUMMARY", FunctionType.SUMMARY.name)
        assertEquals("TITLE_GENERATION", FunctionType.TITLE_GENERATION.name)
        assertEquals("MEMORY", FunctionType.MEMORY.name)
        assertEquals("UI_CONTROLLER", FunctionType.UI_CONTROLLER.name)
        assertEquals("TRANSLATION", FunctionType.TRANSLATION.name)
        assertEquals("GREP", FunctionType.GREP.name)
        assertEquals("ROLE_RESPONSE_PLANNER", FunctionType.ROLE_RESPONSE_PLANNER.name)
        assertEquals("IMAGE_RECOGNITION", FunctionType.IMAGE_RECOGNITION.name)
        assertEquals("AUDIO_RECOGNITION", FunctionType.AUDIO_RECOGNITION.name)
        assertEquals("VIDEO_RECOGNITION", FunctionType.VIDEO_RECOGNITION.name)
    }
}
