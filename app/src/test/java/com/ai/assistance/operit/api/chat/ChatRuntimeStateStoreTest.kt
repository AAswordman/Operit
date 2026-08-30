package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.core.tools.ChatRuntimeErrorResultData
import com.ai.assistance.operit.core.tools.ChatRuntimeRetryResultData
import com.ai.assistance.operit.core.tools.CurrentChatRuntimeStateResultData
import com.ai.assistance.operit.data.model.InputProcessingErrorSource
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatRuntimeStateStoreTest {
    private var previousSystemLogEnabled = true

    @Before
    fun setUp() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
        ChatRuntimeStateStore.resetForTest()
    }

    @After
    fun tearDown() {
        ChatRuntimeStateStore.resetForTest()
        AppLogger.enableSystemLog = previousSystemLogEnabled
    }

    @Test
    fun mapsConnectingToRequesting() {
        val chatId = uniqueChatId("requesting")
        ChatRuntimeStateStore.updateInputProcessingState(ChatRuntimeSlot.MAIN, chatId, InputProcessingState.Connecting("connecting"))
        val snapshot = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("requesting", snapshot.phase.wireName)
        assertTrue(snapshot.phase.isActive)
    }
    @Test
    fun mapsProcessingToThinkingOnly() {
        val chatId = uniqueChatId("processing")
        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN, chatId, InputProcessingState.Processing("processing")
        )
        assertEquals(ChatRuntimeStatePhase.THINKING, ChatRuntimeStateStore.getSnapshot(chatId).phase)
        assertEquals("thinking", ChatRuntimeStateStore.getSnapshot(chatId).phase.wireName)
    }

    @Test
    fun mapsProcessingToolResultToDedicatedPhase() {
        val chatId = uniqueChatId("processing-tool-result")
        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN, chatId, InputProcessingState.ProcessingToolResult("read_file")
        )
        val snapshot = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals(ChatRuntimeStatePhase.PROCESSING_TOOL_RESULT, snapshot.phase)
        assertEquals("processing_tool_result", snapshot.phase.wireName)
        assertEquals("read_file", snapshot.toolName)
    }

    @Test
    fun mapsExecutingPlanToDedicatedPhase() {
        val chatId = uniqueChatId("executing-plan")
        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN, chatId, InputProcessingState.ExecutingPlan("planning")
        )
        val snapshot = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals(ChatRuntimeStatePhase.EXECUTING_PLAN, snapshot.phase)
        assertEquals("executing_plan", snapshot.phase.wireName)
    }

    @Test
    fun mapsSummarizingToSummarizing() {
        val chatId = uniqueChatId("summarizing")
        ChatRuntimeStateStore.updateInputProcessingState(ChatRuntimeSlot.MAIN, chatId, InputProcessingState.Summarizing("summarizing"))
        assertEquals("summarizing", ChatRuntimeStateStore.getSnapshot(chatId).phase.wireName)
    }


    @Test
    fun preservesApiErrorMetadata() {
        val chatId = uniqueChatId("api-error")
        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN,
            chatId,
            InputProcessingState.Error(
                message = "model not found",
                code = "model_not_found",
                errorSource = InputProcessingErrorSource.API,
                recoverable = false,
                providerCode = "model_not_found",
                httpStatusCode = 404
            )
        )
        val snapshot = ChatRuntimeStateStore.getSnapshot(chatId)
        val error = snapshot.error
        assertEquals(ChatRuntimeStatePhase.ERROR, snapshot.phase)
        assertEquals("api", error?.source?.wireName)
        assertEquals("model_not_found", error?.code)
        assertEquals("model_not_found", error?.providerCode)
        assertEquals(404, error?.httpStatusCode)
        assertFalse(error?.recoverable ?: true)
        assertNull(snapshot.retry)
    }

    @Test
    fun keepsAiAndToolErrorsDistinct() {
        val aiChatId = uniqueChatId("ai-error")
        val toolChatId = uniqueChatId("tool-error")
        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN, aiChatId,
            InputProcessingState.AiError("pure_thinking_only", "no response body", true)
        )
        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN, toolChatId,
            InputProcessingState.ToolError("read_file", "permission_denied", "permission denied", false)
        )
        assertEquals("ai", ChatRuntimeStateStore.getSnapshot(aiChatId).error?.source?.wireName)
        assertEquals("tool", ChatRuntimeStateStore.getSnapshot(toolChatId).error?.source?.wireName)
        assertEquals("read_file", ChatRuntimeStateStore.getSnapshot(toolChatId).toolName)
    }

    @Test
    fun mapsGenericErrorToSystemSource() {
        val chatId = uniqueChatId("system-error")
        ChatRuntimeStateStore.updateInputProcessingState(ChatRuntimeSlot.MAIN, chatId, InputProcessingState.Error("internal failure"))
        assertEquals("system", ChatRuntimeStateStore.getSnapshot(chatId).error?.source?.wireName)
    }

    @Test
    fun retryingPublishesSeparateErrorAndRetryObjects() {
        val chatId = uniqueChatId("retrying-error")
        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN, chatId,
            InputProcessingState.Error(
                message = "rate limited",
                code = "rate_limited",
                errorSource = InputProcessingErrorSource.API,
                recoverable = true,
                providerCode = "rate_limit_error",
                httpStatusCode = 429
            )
        )
        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN,
            chatId,
            InputProcessingState.Retrying(
                message = "retrying",
                retryAttempt = 1,
                maxRetryAttempts = 5,
                retryAfterMs = 1000L,
                errorCode = "rate_limited",
                providerCode = "rate_limit_error",
                httpStatusCode = 429
            )
        )

        val retrying = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals(ChatRuntimeStatePhase.RETRYING, retrying.phase)
        assertEquals(1, retrying.retry?.attempt)
        assertEquals(5, retrying.retry?.maxAttempts)
        assertEquals(1000L, retrying.retry?.retryAfterMs)
        assertEquals("rate_limited", retrying.error?.code)
        assertEquals("rate_limit_error", retrying.error?.providerCode)
        assertEquals(429, retrying.error?.httpStatusCode)

        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN,
            chatId,
            InputProcessingState.Error(
                message = "retry exhausted",
                code = "rate_limited",
                errorSource = InputProcessingErrorSource.API
            )
        )
        val terminalError = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals(ChatRuntimeStatePhase.ERROR, terminalError.phase)
        assertEquals(1, terminalError.retry?.attempt)
        assertEquals(5, terminalError.retry?.maxAttempts)

        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN,
            chatId,
            InputProcessingState.Receiving("recovered")
        )
        val recovered = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals(ChatRuntimeStatePhase.GENERATING_RESPONSE, recovered.phase)
        assertNull(recovered.error)
        assertNull(recovered.retry)
    }

    @Test
    fun queryResultSerializesNestedErrorAndRetryObjects() {
        val encoded = Json.encodeToString(
            CurrentChatRuntimeStateResultData(
                chatId = "chat-1",
                aiBehavior = "retrying",
                error = ChatRuntimeErrorResultData(
                    source = "api",
                    code = "rate_limited",
                    recoverable = true,
                    providerCode = "rate_limit_error",
                    httpStatusCode = 429
                ),
                retry = ChatRuntimeRetryResultData(
                    attempt = 1,
                    maxAttempts = 5,
                    retryAfterMs = 1000L
                )
            )
        )
        val payload = Json.parseToJsonElement(encoded).jsonObject
        val error = payload.getValue("error").jsonObject
        val retry = payload.getValue("retry").jsonObject

        assertEquals("rate_limited", error.getValue("code").jsonPrimitive.content)
        assertEquals("rate_limit_error", error.getValue("providerCode").jsonPrimitive.content)
        assertEquals("1", retry.getValue("attempt").jsonPrimitive.content)
        assertEquals("5", retry.getValue("maxAttempts").jsonPrimitive.content)
        assertEquals("1000", retry.getValue("retryAfterMs").jsonPrimitive.content)
        assertFalse(payload.containsKey("errorCode"))
        assertFalse(payload.containsKey("retryAttempt"))
    }

    @Test
    fun cancelledIsClearedByTerminalIdle() {
        val chatId = uniqueChatId("cancelled")
        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN,
            chatId,
            InputProcessingState.Retrying(
                message = "retrying",
                retryAttempt = 1,
                maxRetryAttempts = 5,
                retryAfterMs = 1000L,
                errorCode = "connection_timeout"
            )
        )
        ChatRuntimeStateStore.markCancelled(ChatRuntimeSlot.MAIN, chatId)
        val cancelled = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("cancelled", cancelled.phase.wireName)
        assertNull(cancelled.error)
        assertNull(cancelled.retry)
        val cancelledResult = CurrentChatRuntimeStateResultData(chatId, cancelled.phase.wireName)
        assertFalse(cancelledResult.isIdle)
        assertFalse(cancelledResult.isActive)

        ChatRuntimeStateStore.updateInputProcessingState(ChatRuntimeSlot.MAIN, chatId, InputProcessingState.Idle)
        val idle = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("idle", idle.phase.wireName)
        assertNull(idle.userState)
        assertFalse(ChatRuntimeStateStore.replayEvents().any { it.session?.chatId == chatId })
    }

    @Test
    fun toolConfirmationIsExplicitAndRestoresPreviousPhase() {
        val chatId = uniqueChatId("confirmation")
        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN, chatId, InputProcessingState.WaitingToolResult("read_file")
        )
        ChatRuntimeStateStore.updateToolConfirmation(chatId, "write_file")
        val waiting = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("waiting_tool_confirmation", waiting.phase.wireName)
        assertEquals("write_file", waiting.toolName)

        ChatRuntimeStateStore.clearToolConfirmations()
        val restored = ChatRuntimeStateStore.getSnapshot(chatId)
        assertEquals("waiting_tool_result", restored.phase.wireName)
        assertEquals("read_file", restored.toolName)
    }

    @Test
    fun removeChatClearsRecordAndPublishesIdleView() {
        val chatId = uniqueChatId("remove")
        ChatRuntimeStateStore.updateUserDraft(ChatRuntimeSlot.MAIN, chatId, true)
        assertEquals(1, ChatRuntimeStateStore.recordCountForTest())
        ChatRuntimeStateStore.removeChat(chatId)
        assertEquals(0, ChatRuntimeStateStore.recordCountForTest())
        assertEquals("idle", ChatRuntimeStateStore.getSnapshot(chatId).phase.wireName)
        assertFalse(ChatRuntimeStateStore.snapshots.value.containsKey(chatId))
    }

    @Test
    fun inactiveRecordsAreBounded() {
        repeat(ChatRuntimeStateStore.maxRecordCountForTest() + 32) { index ->
            ChatRuntimeStateStore.updateInputProcessingState(
                ChatRuntimeSlot.MAIN,
                uniqueChatId("bounded-$index"),
                InputProcessingState.Idle
            )
        }

        assertEquals(
            ChatRuntimeStateStore.maxRecordCountForTest(),
            ChatRuntimeStateStore.recordCountForTest()
        )
    }

    @Test
    fun staleInactiveRecordsArePrunedButActiveRecordsRemain() {
        val idleChatId = uniqueChatId("stale-idle")
        val activeChatId = uniqueChatId("stale-active")
        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN,
            idleChatId,
            InputProcessingState.Idle
        )
        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN,
            activeChatId,
            InputProcessingState.Processing("active")
        )
        ChatRuntimeStateStore.updateApplicationState(ChatRuntimeStateApplicationState.FOREGROUND)

        ChatRuntimeStateStore.pruneInactiveRecordsForTest(Long.MAX_VALUE)

        assertFalse(ChatRuntimeStateStore.snapshots.value.containsKey(idleChatId))
        assertTrue(ChatRuntimeStateStore.snapshots.value.containsKey(activeChatId))
    }

    @Test
    fun slowCollectorRecordsDropsWhileSnapshotRemainsCurrent() = runBlocking {
        val firstEventReceived = CompletableDeferred<Unit>()
        val releaseCollector = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            ChatRuntimeStateStore.events.collect {
                firstEventReceived.complete(Unit)
                releaseCollector.await()
            }
        }
        val chatId = uniqueChatId("backpressure")
        ChatRuntimeStateStore.updateInputProcessingState(
            ChatRuntimeSlot.MAIN,
            chatId,
            InputProcessingState.Processing("first")
        )
        firstEventReceived.await()

        repeat(ChatRuntimeStateStore.eventBufferCapacityForTest() + 32) { index ->
            val state = if (index % 2 == 0) {
                InputProcessingState.Connecting("connecting")
            } else {
                InputProcessingState.Receiving("receiving")
            }
            ChatRuntimeStateStore.updateInputProcessingState(ChatRuntimeSlot.MAIN, chatId, state)
        }

        assertTrue(ChatRuntimeStateStore.droppedEventCountForTest() > 0L)
        assertEquals(
            ChatRuntimeStatePhase.GENERATING_RESPONSE,
            ChatRuntimeStateStore.getSnapshot(chatId).phase
        )
        releaseCollector.complete(Unit)
        collector.cancelAndJoin()
    }

    private fun uniqueChatId(prefix: String): String =
        "chat-runtime-state-test-$prefix-${System.nanoTime()}"
}
