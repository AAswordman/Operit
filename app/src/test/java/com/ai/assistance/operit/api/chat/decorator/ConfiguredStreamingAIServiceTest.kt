package com.ai.assistance.operit.api.chat.decorator

import android.content.Context
import com.ai.assistance.operit.api.chat.AIService
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.data.config.model.ModelParameter
import com.ai.assistance.operit.core.tools.prompt.ToolPrompt
import com.ai.assistance.operit.data.stats.ProviderUsageSnapshot
import com.ai.assistance.operit.util.stream.Stream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class ConfiguredStreamingAIServiceTest {
    @Test
    fun configurationAndCallerMustBothAllowStreaming() = runBlocking {
        for (configured in listOf(false, true)) {
            for (requested in listOf(false, true)) {
                val delegate = RecordingService()
                val service = ConfiguredStreamingAIService(delegate, configured)
                service.sendMessage(context = mock<Context>(), stream = requested)
                assertEquals(configured && requested, delegate.receivedStream)
            }
        }
    }

    @Test
    fun twoConfigurationsKeepIndependentOutputModes() = runBlocking {
        val first = RecordingService()
        val second = RecordingService()
        val streamingService = ConfiguredStreamingAIService(first, true)
        val completeService = ConfiguredStreamingAIService(second, false)
        val context = mock<Context>()
        streamingService.sendMessage(context)
        completeService.sendMessage(context)
        streamingService.sendMessage(context)
        assertTrue(first.receivedStream)
        assertFalse(second.receivedStream)
    }

    @Test
    fun preservesResponseCallbacksRetryAndLifecycle() = runBlocking {
        val delegate = RecordingService()
        val service = ConfiguredStreamingAIService(delegate, false)
        var usage = emptyList<Long>()
        val response = service.sendMessage(
            context = mock<Context>(),
            enableRetry = false,
            recordTokenUsage = false,
            onTokensUpdated = { input, cached, output -> usage = listOf(input, cached, output) },
        )
        val chunks = mutableListOf<String>()
        response.collect { chunks.add(it) }
        assertEquals(listOf("answer"), chunks)
        assertEquals(listOf(3L, 2L, 1L), usage)
        assertFalse(delegate.receivedRetry)
        assertFalse(delegate.receivedUsage)
        service.cancelStreaming()
        service.release()
        assertTrue(delegate.cancelled)
        assertTrue(delegate.released)
    }

    private class RecordingService : AIService by mock<AIService>() {
        var receivedStream = true
        var receivedRetry = true
        var receivedUsage = true
        var cancelled = false
        var released = false

        override fun cancelStreaming() { cancelled = true }
        override fun release() { released = true }

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
            receivedStream = stream
            receivedRetry = enableRetry
            receivedUsage = recordTokenUsage
            return com.ai.assistance.operit.util.stream.stream {
                onTokensUpdated(3L, 2L, 1L)
                emit("answer")
            }
        }
    }
}