package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.data.model.InputProcessingErrorSource
import com.ai.assistance.operit.data.model.InputProcessingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRuntimeStateErrorSanitizerTest {
    @After
    fun tearDown() = ChatRuntimeStateStore.resetForTest()

    @Test
    fun sanitize_redactsCredentialsPromptAndThinkingContent() {
        val raw =
            "Authorization: Bearer secret-token-123456 " +
                "api_key=sk-supersecret123456 " +
                "{\"prompt\":\"private user prompt\",\"reasoning_content\":\"private reasoning\"} " +
                "<think>hidden chain of thought</think>"

        val sanitized = ChatRuntimeStateErrorSanitizer.sanitize(raw)

        assertFalse(sanitized.orEmpty().contains("secret-token"))
        assertFalse(sanitized.orEmpty().contains("supersecret"))
        assertFalse(sanitized.orEmpty().contains("private user prompt"))
        assertFalse(sanitized.orEmpty().contains("private reasoning"))
        assertFalse(sanitized.orEmpty().contains("hidden chain of thought"))
        assertTrue(sanitized.orEmpty().contains("[redacted]"))
    }

    @Test
    fun sanitize_limitsPublicMessageToThreeHundredCharacters() {
        val sanitized = ChatRuntimeStateErrorSanitizer.sanitize("x".repeat(600))

        assertEquals(ChatRuntimeStateErrorSanitizer.MAX_PUBLIC_MESSAGE_LENGTH, sanitized?.length)
        assertTrue(sanitized.orEmpty().endsWith("..."))
    }

    @Test
    fun sanitize_preservesNullAndUsefulShortMessage() {
        assertNull(ChatRuntimeStateErrorSanitizer.sanitize(null))
        assertEquals("model not found", ChatRuntimeStateErrorSanitizer.sanitize("model not found"))
    }

    @Test
    fun storePublishesOnlySanitizedMessage() {
        val chatId = "sanitized-error-${System.nanoTime()}"
        ChatRuntimeStateStore.resetForTest()

        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Error(
                message = "Bearer private-token-123456 " + "z".repeat(500),
                code = "provider_error",
                errorSource = InputProcessingErrorSource.API
            )
        )

        val publicMessage = ChatRuntimeStateStore.getSnapshot(chatId).error?.message
        assertFalse(publicMessage.orEmpty().contains("private-token"))
        assertTrue(publicMessage.orEmpty().length <= ChatRuntimeStateErrorSanitizer.MAX_PUBLIC_MESSAGE_LENGTH)
    }
}
