package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ColdStreamCancellationTest {
    @Test
    fun `token tracking cancellation before collection prevents delegate execution`() = runBlocking {
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        val delegate = RecordingAIService()
        val service = TokenTrackingAIService(delegate, context, "config")

        val response = startRequest(service, context)
        service.cancelStreaming()

        assertCancelled(response)
        assertFalse(delegate.collected)
    }

    @Test
    fun `untracked token request cancellation before collection prevents delegate execution`() = runBlocking {
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        val delegate = RecordingAIService()
        val service = TokenTrackingAIService(delegate, context, "config")

        val response = startRequest(service, context, recordTokenUsage = false)
        service.cancelStreaming()

        assertCancelled(response)
        assertFalse(delegate.collected)
    }

    @Test
    fun `rate limited cancellation before collection prevents delegate execution`() = runBlocking {
        val context = mock<Context>()
        val delegate = RecordingAIService()
        val service = RateLimitedAIService(delegate, rateLimiter = null, concurrencySemaphore = null)

        val response = startRequest(service, context)
        service.cancelStreaming()

        assertCancelled(response)
        assertFalse(delegate.collected)
    }

    private suspend fun startRequest(
        service: AIService,
        context: Context,
        recordTokenUsage: Boolean = true,
    ): Stream<String> =
        service.sendMessage(
            context = context,
            chatHistory = emptyList(),
            modelParameters = emptyList(),
            enableThinking = false,
            stream = true,
            availableTools = null,
            preserveThinkInHistory = false,
            onTokensUpdated = { _, _, _ -> },
            onUsageReported = null,
            onNonFatalError = {},
            onRetryState = {},
            enableRetry = false,
            recordTokenUsage = recordTokenUsage,
        )

    private suspend fun assertCancelled(stream: Stream<String>) {
        try {
            stream.collect { }
            fail("cancelled cold stream must not execute")
        } catch (_: CancellationException) {
        }
    }

    private class RecordingAIService : AIService {
        var collected = false

        override val inputTokenCount: Long = 0L
        override val cachedInputTokenCount: Long = 0L
        override val outputTokenCount: Long = 0L
        override val providerModel: String = "OPENAI:test"

        override fun resetTokenCounts() = Unit

        override fun cancelStreaming() = Unit

        override suspend fun getModelsList(context: Context): Result<List<ModelOption>> =
            Result.success(emptyList())

        override suspend fun sendMessage(
            context: Context,
            chatHistory: List<PromptTurn>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            stream: Boolean,
            availableTools: List<ToolPrompt>?,
            preserveThinkInHistory: Boolean,
            onTokensUpdated: suspend (input: Long, cachedInput: Long, output: Long) -> Unit,
            onUsageReported: (suspend (ProviderUsageSnapshot, attempt: Int) -> Unit)?,
            onNonFatalError: suspend (error: String) -> Unit,
            onRetryState: suspend (retry: RuntimeRetryMetadata) -> Unit,
            enableRetry: Boolean,
            recordTokenUsage: Boolean,
            onUsageFinalized: (suspend (attempt: Int?) -> Unit)?,
        ): Stream<String> = com.ai.assistance.operit.util.stream.stream {
            collected = true
            emit("answer")
        }

        override suspend fun testConnection(context: Context): Result<String> = Result.success("ok")

        override suspend fun calculateInputTokens(
            chatHistory: List<PromptTurn>,
            availableTools: List<ToolPrompt>?,
        ): Long = 0L
    }
}
