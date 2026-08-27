package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.core.tools.CurrentChatRuntimeStateResultData
import com.ai.assistance.operit.data.model.InputProcessingErrorSource
import com.ai.assistance.operit.data.model.InputProcessingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRuntimeStateStoreTest {
    @Test
    fun mapsConnectingToRequesting() {
        val chatId = uniqueChatId("requesting")

        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Connecting("connecting")
        )

        val snapshot = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("requesting", snapshot.phase.wireName)
        assertTrue(snapshot.phase.isActive)
    }

    @Test
    fun mapsSummarizingToSummarizing() {
        val chatId = uniqueChatId("summarizing")

        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Summarizing("summarizing")
        )

        val snapshot = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("summarizing", snapshot.phase.wireName)
        assertTrue(snapshot.phase.isActive)
    }

    @Test
    fun preservesApiErrorMetadata() {
        val chatId = uniqueChatId("api-error")

        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Error(
                message = "model not found",
                code = "model_not_found",
                errorSource = InputProcessingErrorSource.API,
                recoverable = false,
                providerCode = "model_not_found",
                httpStatusCode = 404
            )
        )

        val snapshot = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("error", snapshot.phase.wireName)
        assertEquals("api", snapshot.error?.source?.wireName)
        assertEquals("model_not_found", snapshot.error?.code)
        assertEquals("model_not_found", snapshot.error?.providerCode)
        assertEquals(404, snapshot.error?.httpStatusCode)
        assertFalse(snapshot.error?.recoverable ?: true)
    }

    @Test
    fun preservesApiErrorMetadataDuringRetry() {
        val chatId = uniqueChatId("api-retry")

        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Retrying(
                message = "invalid API key",
                code = "authentication_failed",
                errorSource = InputProcessingErrorSource.API,
                recoverable = false,
                retryAttempt = 1,
                providerCode = "invalid_request_error",
                httpStatusCode = 401
            )
        )

        val snapshot = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("retrying", snapshot.phase.wireName)
        assertTrue(snapshot.phase.isActive)
        assertEquals("api", snapshot.error?.source?.wireName)
        assertEquals("authentication_failed", snapshot.error?.code)
        assertEquals("invalid_request_error", snapshot.error?.providerCode)
        assertEquals(401, snapshot.error?.httpStatusCode)
        assertEquals(1, snapshot.error?.retryAttempt)
        assertFalse(snapshot.error?.recoverable ?: true)
    }

    @Test
    fun keepsAiAndToolErrorsDistinct() {
        val aiChatId = uniqueChatId("ai-error")
        val toolChatId = uniqueChatId("tool-error")

        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = aiChatId,
            state = InputProcessingState.AiError(
                code = "pure_thinking_only",
                message = "no response body",
                recoverable = true
            )
        )
        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = toolChatId,
            state = InputProcessingState.ToolError(
                toolName = "read_file",
                code = "permission_denied",
                message = "permission denied",
                recoverable = false
            )
        )

        val aiSnapshot = ChatRuntimeStateStore.getSnapshot(aiChatId)
        val toolSnapshot = ChatRuntimeStateStore.getSnapshot(toolChatId)
        assertEquals("ai", aiSnapshot.error?.source?.wireName)
        assertEquals("pure_thinking_only", aiSnapshot.error?.code)
        assertEquals("tool", toolSnapshot.error?.source?.wireName)
        assertEquals("permission_denied", toolSnapshot.error?.code)
        assertEquals("read_file", toolSnapshot.toolName)
    }

    @Test
    fun mapsGenericErrorToSystemSource() {
        val chatId = uniqueChatId("system-error")

        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Error("internal failure")
        )

        val snapshot = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("system", snapshot.error?.source?.wireName)
        assertEquals("unknown", snapshot.error?.code)
    }

    @Test
    fun preservesCancelledUntilTheNextNonTerminalState() {
        val chatId = uniqueChatId("cancelled")

        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Processing("processing")
        )
        ChatRuntimeStateStore.markCancelled(ChatRuntimeSlot.MAIN, chatId)
        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Idle
        )

        val cancelled = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("cancelled", cancelled.phase.wireName)
        assertFalse(cancelled.phase.isActive)
        assertNull(cancelled.userState)
        val cancelledResult = CurrentChatRuntimeStateResultData(
            chatId = chatId,
            aiBehavior = cancelled.phase.wireName
        )
        assertFalse(cancelledResult.isIdle)
        assertFalse(cancelledResult.isActive)
        assertFalse(ChatRuntimeStateStore.globalSnapshot.value.activeChatIds.contains(chatId))
        assertTrue(
            ChatRuntimeStateStore.replayEvents().any { event ->
                event.session?.chatId == chatId && event.session.phase.wireName == "cancelled"
            }
        )

        ChatRuntimeStateStore.updateInputProcessingState(
            runtime = ChatRuntimeSlot.MAIN,
            chatId = chatId,
            state = InputProcessingState.Processing("next request")
        )

        val nextRequest = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("thinking", nextRequest.phase.wireName)
        assertTrue(nextRequest.phase.isActive)
    }

    private fun uniqueChatId(prefix: String): String {
        return "chat-runtime-state-test-$prefix-${System.nanoTime()}"
    }
}
