package com.ai.assistance.operit.api.chat.decorator

import android.content.Context
import com.ai.assistance.operit.api.chat.runtime.FunctionThinkingRequest
import com.ai.assistance.operit.api.chat.AIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.config.model.ModelOption
import com.ai.assistance.operit.data.config.model.ModelParameter
import com.ai.assistance.operit.core.tools.prompt.ToolPrompt
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.mock

class FunctionalThinkingAIServiceTest {
    @Test
    fun readsIndependentFunctionSettingForEachRequest() = runBlocking {
        val delegate = RecordingAIService()
        var setting = FunctionThinkingRequest(true, "high")
        val service = FunctionalThinkingAIService(delegate) { setting }
        val context = mock<Context>()
        service.sendMessage(context, enableThinking = false, thinkingOptionId = "low")
        assertTrue(delegate.receivedThinking)
        assertEquals("high", delegate.receivedOption)
        setting = FunctionThinkingRequest(false, "medium")
        service.sendMessage(context, enableThinking = true, thinkingOptionId = "high")
        assertFalse(delegate.receivedThinking)
        assertEquals("medium", delegate.receivedOption)
    }

    @Test
    fun preservesStreamFlagsAndUsageCallbacks() = runBlocking {
        val delegate = RecordingAIService()
        val service = FunctionalThinkingAIService(delegate) { FunctionThinkingRequest(true, "high") }
        var usage: List<Long> = emptyList()
        val response = service.sendMessage(
            context = mock<Context>(),
            stream = false,
            enableRetry = false,
            recordTokenUsage = false,
            onTokensUpdated = { input, cached, output -> usage = listOf(input, cached, output) },
        )
        val chunks = mutableListOf<String>()
        response.collect { chunks.add(it) }
        assertEquals(listOf("answer"), chunks)
        assertEquals(listOf(3L, 2L, 1L), usage)
        assertFalse(delegate.receivedStreaming)
        assertFalse(delegate.receivedRetry)
        assertFalse(delegate.receivedUsage)
    }

    @Test
    fun cancellationAndReleaseReachOriginalService() = runBlocking {
        val delegate = RecordingAIService()
        val service = FunctionalThinkingAIService(delegate) { FunctionThinkingRequest(false, "") }
        val response = service.sendMessage(context = mock<Context>())
        service.cancelStreaming()
        try {
            response.collect { }
            fail("取消后不得收集原始流")
        } catch (_: CancellationException) {
        }
        assertFalse(delegate.collected)
        service.release()
        assertTrue(delegate.released)
    }

    private class RecordingAIService : AIService {
        var collected = false
        var cancelled = false
        var released = false
        var receivedThinking = false
        var receivedOption: String? = null
        var receivedRetry = true
        var receivedUsage = true
        var receivedStreaming = true

        override val inputTokenCount: Long = 0L
        override val cachedInputTokenCount: Long = 0L
        override val outputTokenCount: Long = 0L
        override val providerModel: String = "OPENAI:test"

        override fun resetTokenCounts() = Unit

        override fun cancelStreaming() { cancelled = true }
        override fun release() { released = true }

        override suspend fun getModelsList(context: Context): Result<List<ModelOption>> =
            Result.success(emptyList())

        override suspend fun sendMessage(
            context: Context,
            chatHistory: List<PromptTurn>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            thinkingOptionId: String?,
            stream: Boolean,
            availableTools: List<ToolPrompt>?,
            preserveThinkInHistory: Boolean,
            onTokensUpdated: suspend (input: Long, cachedInput: Long, output: Long) -> Unit,
            onUsageReported: (suspend (ProviderUsageSnapshot, attempt: Int) -> Unit)?,
            onNonFatalError: suspend (error: String) -> Unit,
            enableRetry: Boolean,
            recordTokenUsage: Boolean,
            onUsageFinalized: (suspend (attempt: Int?) -> Unit)?,
        ): Stream<String> {
            receivedThinking = enableThinking
            receivedOption = thinkingOptionId
            receivedRetry = enableRetry
            receivedUsage = recordTokenUsage
            receivedStreaming = stream
            return com.ai.assistance.operit.util.stream.stream {
                if (cancelled) throw CancellationException("已取消")
                collected = true
                onTokensUpdated(3L, 2L, 1L)
                emit("answer")
            }
        }

        override suspend fun testConnection(context: Context): Result<String> = Result.success("ok")

        override suspend fun calculateInputTokens(
            chatHistory: List<PromptTurn>,
            availableTools: List<ToolPrompt>?,
        ): Long = 0L
    }
}
