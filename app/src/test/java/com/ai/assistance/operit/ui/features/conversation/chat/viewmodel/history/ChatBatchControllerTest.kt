package com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.history

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchAction
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchItemResult
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchProgress
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchSummary

@OptIn(ExperimentalCoroutinesApi::class)
class ChatBatchControllerTest {
    @Test
    fun waitsForEachItemAndDeduplicatesTargets() = runTest {
        val events = mutableListOf<String>()
        var summary: ChatBatchSummary? = null
        val controller = ChatBatchController(this, process = { _, id ->
            events += "start:$id"
            delay(20)
            events += "end:$id"
            ChatBatchItemResult.SUCCEEDED
        }, onFailure = { _, cause -> throw AssertionError(cause) }, onComplete = { summary = it })

        controller.start(ChatBatchAction.SUMMARIZE_TITLES, listOf("a", "a", "b"))
        assertEquals(ChatBatchProgress(ChatBatchAction.SUMMARIZE_TITLES, 0, 2), controller.progress.value)
        advanceUntilIdle()

        assertEquals(listOf("start:a", "end:a", "start:b", "end:b"), events)
        assertEquals(ChatBatchSummary(2, 0, 0), summary)
        assertNull(controller.progress.value)
    }

    @Test
    fun failureAndSkipDoNotStopRemainingTargets() = runTest {
        val processed = mutableListOf<String>()
        val failures = mutableListOf<String>()
        var summary: ChatBatchSummary? = null
        val controller = ChatBatchController(this, process = { _, id ->
            processed += id
            when (id) {
                "failed" -> throw IllegalStateException("模拟单条存储失败")
                "locked" -> ChatBatchItemResult.SKIPPED
                else -> ChatBatchItemResult.SUCCEEDED
            }
        }, onFailure = { id, _ -> failures += id }, onComplete = { summary = it })

        controller.start(ChatBatchAction.DELETE, listOf("failed", "locked", "ok"))
        advanceUntilIdle()

        assertEquals(listOf("failed", "locked", "ok"), processed)
        assertEquals(listOf("failed"), failures)
        assertEquals(ChatBatchSummary(1, 1, 1), summary)
        assertNull(controller.progress.value)
    }

    @Test
    fun repeatedClickCannotStartAnotherBatchBeforeDispatch() = runTest {
        val actions = mutableListOf<ChatBatchAction>()
        var completions = 0
        val controller = ChatBatchController(this, process = { action, _ ->
            actions += action
            delay(20)
            ChatBatchItemResult.SUCCEEDED
        }, onFailure = { _, cause -> throw AssertionError(cause) }, onComplete = { completions++ })

        controller.start(ChatBatchAction.LOCK, listOf("a"))
        controller.start(ChatBatchAction.DELETE, listOf("b"))
        advanceUntilIdle()

        assertEquals(listOf(ChatBatchAction.LOCK), actions)
        assertEquals(1, completions)
    }

    @Test
    fun cancellationDoesNotBecomeAnItemFailureOrCompletion() = runTest {
        var cancelItem = true
        var failures = 0
        var completions = 0
        val controller = ChatBatchController(this, process = { _, _ ->
            if (cancelItem) throw CancellationException("模拟模型生命周期取消")
            ChatBatchItemResult.SUCCEEDED
        }, onFailure = { _, _ -> failures++ }, onComplete = { completions++ })

        controller.start(ChatBatchAction.ARCHIVE, listOf("a", "b"))
        advanceUntilIdle()
        assertNull(controller.progress.value)
        assertEquals(0, failures)
        assertEquals(0, completions)

        cancelItem = false
        controller.start(ChatBatchAction.UNARCHIVE, listOf("a"))
        advanceUntilIdle()
        assertEquals(1, completions)
    }

    @Test
    fun emptySelectionDoesNotRunOrEmitCompletion() = runTest {
        var calls = 0
        val controller = ChatBatchController(this, process = { _, _ ->
            calls++
            ChatBatchItemResult.SUCCEEDED
        }, onFailure = { _, cause -> throw AssertionError(cause) }, onComplete = { calls++ })

        controller.start(ChatBatchAction.LOCK, emptyList())
        advanceUntilIdle()

        assertEquals(0, calls)
        assertNull(controller.progress.value)
    }

    @Test
    fun explicitFailureResultIsCountedWithoutException() = runTest {
        var summary: ChatBatchSummary? = null
        val controller = ChatBatchController(this,
            process = { _, _ -> ChatBatchItemResult.FAILED },
            onFailure = { _, cause -> throw AssertionError(cause) },
            onComplete = { summary = it })

        controller.start(ChatBatchAction.SUMMARIZE_TITLES, listOf("a", "b"))
        advanceUntilIdle()

        assertEquals(ChatBatchSummary(0, 0, 2), summary)
        assertNull(controller.progress.value)
    }
}
