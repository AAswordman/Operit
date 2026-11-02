package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.config.model.ApiProviderType
import com.ai.assistance.operit.util.logging.AppLogger
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamEventType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import com.ai.assistance.operit.api.chat.AIService
import com.ai.assistance.operit.api.chat.keypool.SingleApiKeyProvider

class ProviderUsageCancellationTest {

    private var previousSystemLogEnabled = true

    @Before
    fun disableAndroidSystemLogForJvmTests() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After
    fun restoreAndroidSystemLog() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
    }

    @Test
    fun `OpenAI streaming usage callback cancellation propagates unchanged`() {
        assertUsageCancellationPropagates(
            provider = openAiProvider(OPENAI_STREAM_RESPONSE, "text/event-stream"),
            stream = true,
        )
    }

    @Test
    fun `OpenAI non streaming usage callback cancellation propagates unchanged`() {
        assertUsageCancellationPropagates(
            provider = openAiProvider(OPENAI_RESPONSE, "application/json"),
            stream = false,
        )
    }

    @Test
    fun `Gemini streaming usage callback cancellation propagates unchanged`() {
        assertUsageCancellationPropagates(
            provider = geminiProvider(GEMINI_STREAM_RESPONSE, "text/event-stream"),
            stream = true,
        )
    }

    @Test
    fun `Gemini non streaming usage callback cancellation propagates unchanged`() {
        assertUsageCancellationPropagates(
            provider = geminiProvider(GEMINI_RESPONSE, "application/json"),
            stream = false,
        )
    }

    @Test
    fun `OpenAI terminal stream failure preserves emitted content without rollback`() {
        assertTerminalFailureDoesNotRollback(
            provider = openAiProvider(OPENAI_STREAM_RESPONSE, "text/event-stream"),
        )
    }

    @Test
    fun `Gemini terminal stream failure preserves emitted content without rollback`() {
        assertTerminalFailureDoesNotRollback(
            provider = geminiProvider(GEMINI_STREAM_RESPONSE, "text/event-stream"),
        )
    }

    @Test
    fun `OpenAI EOF without a terminal signal does not finalize usage`() {
        assertIncompleteStreamDoesNotFinalize(
            provider = openAiProvider(OPENAI_INCOMPLETE_STREAM_RESPONSE, "text/event-stream"),
        )
    }

    @Test
    fun `OpenAI finish reason confirms stream completion`() {
        assertStreamFinalizes(
            provider = openAiProvider(OPENAI_FINISHED_STREAM_RESPONSE, "text/event-stream"),
        )
    }

    @Test
    fun `Gemini EOF without a terminal signal does not finalize usage`() {
        assertIncompleteStreamDoesNotFinalize(
            provider = geminiProvider(GEMINI_INCOMPLETE_STREAM_RESPONSE, "text/event-stream"),
        )
    }

    @Test
    fun `Gemini unspecified finish reason does not finalize usage`() {
        assertIncompleteStreamDoesNotFinalize(
            provider = geminiProvider(GEMINI_UNSPECIFIED_STREAM_RESPONSE, "text/event-stream"),
        )
    }

    @Test
    fun `Gemini prompt feedback does not finalize streamed usage`() {
        assertIncompleteStreamDoesNotFinalize(
            provider = geminiProvider(GEMINI_PROMPT_BLOCKED_STREAM_RESPONSE, "text/event-stream"),
        )
    }

    @Test
    fun `Gemini prompt feedback does not finalize non streaming usage`() {
        assertIncompleteStreamDoesNotFinalize(
            provider = geminiProvider(GEMINI_PROMPT_BLOCKED_RESPONSE, "application/json"),
            stream = false,
        )
    }

    @Test
    fun `Gemini finish reason confirms stream completion`() {
        assertStreamFinalizes(
            provider = geminiProvider(GEMINI_FINISHED_STREAM_RESPONSE, "text/event-stream"),
        )
    }

    @Test
    fun `Claude message stop confirms stream completion`() {
        assertStreamFinalizes(
            provider = claudeProvider(CLAUDE_FINISHED_STREAM_RESPONSE, "text/event-stream"),
        )
    }

    @Test
    fun `Claude EOF without a terminal signal does not finalize usage`() {
        assertIncompleteStreamDoesNotFinalize(
            provider = claudeProvider(CLAUDE_INCOMPLETE_STREAM_RESPONSE, "text/event-stream"),
        )
    }

    @Test
    fun `Claude compatible finish reason confirms stream completion`() {
        val provider = claudeProvider(OPENAI_FINISHED_STREAM_RESPONSE, "text/event-stream")
        assertStreamFinalizes(provider)
        assertEquals(2L, provider.outputTokenCount)
    }

    @Test
    fun `Claude compatible JSONL finish reason confirms stream completion`() {
        val provider = claudeProvider(CLAUDE_COMPATIBLE_JSONL_RESPONSE, "application/json")
        assertStreamFinalizes(provider)
        assertEquals(2L, provider.outputTokenCount)
    }

    private fun assertUsageCancellationPropagates(provider: AIService, stream: Boolean) {
        val expected = CancellationException("usage observer cancelled")
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getString(any<Int>())).thenReturn("status")
        whenever(context.getString(any<Int>(), any())).thenReturn("status")

        Mockito.mockStatic(AppLogger::class.java).use {
            runBlocking {
                val response =
                    provider.sendMessage(
                        context = context,
                        chatHistory = listOf(PromptTurn(PromptTurnKind.USER, "Hi")),
                        modelParameters = emptyList(),
                        enableThinking = false,
                        stream = stream,
                        availableTools = null,
                        preserveThinkInHistory = false,
                        onTokensUpdated = { _, _, _ -> },
                        onUsageReported = { _, _ -> throw expected },
                        onNonFatalError = {},
                        enableRetry = false,
                    )
                try {
                    response.collect { }
                    fail("usage callback cancellation must propagate")
                } catch (actual: CancellationException) {
                    // Dispatching through the provider's IO context may recreate the cancellation instance;
                    // the callback contract is cancellation propagation, not object identity.
                    assertEquals(expected.message, actual.message)
                }
            }
        }
    }

    private fun assertTerminalFailureDoesNotRollback(provider: AIService) {
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getString(any<Int>())).thenReturn("status")
        whenever(context.getString(any<Int>(), any())).thenReturn("status")

        Mockito.mockStatic(AppLogger::class.java).use {
            runBlocking {
                val response =
                    provider.sendMessage(
                        context = context,
                        chatHistory = listOf(PromptTurn(PromptTurnKind.USER, "Hi")),
                        modelParameters = emptyList(),
                        enableThinking = false,
                        stream = true,
                        availableTools = null,
                        preserveThinkInHistory = false,
                        onTokensUpdated = { _, _, _ -> },
                        onUsageReported = { _, _ -> throw IOException("network interrupted") },
                        onNonFatalError = {},
                        enableRetry = false,
                    )
                val received = StringBuilder()
                try {
                    response.collect { chunk -> received.append(chunk) }
                    fail("terminal stream failure must propagate")
                } catch (_: IOException) {
                    // The generated response is retained for interrupted-message finalization.
                }

                val events = (response as TextStreamEventCarrier).eventChannel.replayCache
                assertEquals("answer", received.toString())
                assertEquals(listOf(TextStreamEventType.SAVEPOINT), events.map { it.eventType })
            }
        }
    }

    private fun assertIncompleteStreamDoesNotFinalize(provider: AIService, stream: Boolean = true) {
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getString(any<Int>())).thenReturn("status")
        whenever(context.getString(any<Int>(), any())).thenReturn("status")
        val finalizedAttempts = mutableListOf<Int?>()

        Mockito.mockStatic(AppLogger::class.java).use {
            runBlocking {
                val response =
                    provider.sendMessage(
                        context = context,
                        chatHistory = listOf(PromptTurn(PromptTurnKind.USER, "Hi")),
                        modelParameters = emptyList(),
                        enableThinking = false,
                        stream = stream,
                        availableTools = null,
                        preserveThinkInHistory = false,
                        onTokensUpdated = { _, _, _ -> },
                        onUsageReported = null,
                        onNonFatalError = {},
                        enableRetry = false,
                        onUsageFinalized = { attempt -> finalizedAttempts += attempt },
                    )
                try {
                    response.collect { }
                    fail("incomplete stream must fail")
                } catch (_: IOException) {
                    // A bare EOF must reach the retry/error path instead of finalizing usage.
                }
            }
        }

        assertEquals(emptyList<Int?>(), finalizedAttempts)
    }

    private fun assertStreamFinalizes(provider: AIService) {
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getString(any<Int>())).thenReturn("status")
        whenever(context.getString(any<Int>(), any())).thenReturn("status")
        val finalizedAttempts = mutableListOf<Int?>()

        Mockito.mockStatic(AppLogger::class.java).use {
            runBlocking {
                val response =
                    provider.sendMessage(
                        context = context,
                        chatHistory = listOf(PromptTurn(PromptTurnKind.USER, "Hi")),
                        modelParameters = emptyList(),
                        enableThinking = false,
                        stream = true,
                        availableTools = null,
                        preserveThinkInHistory = false,
                        onTokensUpdated = { _, _, _ -> },
                        onUsageReported = null,
                        onNonFatalError = {},
                        enableRetry = false,
                        onUsageFinalized = { attempt -> finalizedAttempts += attempt },
                    )
                response.collect { }
            }
        }

        assertEquals(listOf(1), finalizedAttempts)
    }

    private fun openAiProvider(body: String, contentType: String): OpenAIProvider =
        OpenAIProvider(
            apiEndpoint = "https://example.test/v1/chat/completions",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "gpt-test",
            client = respondingClient(body, contentType),
            providerType = ApiProviderType.OPENAI,
        )

    private fun geminiProvider(body: String, contentType: String): GeminiProvider =
        GeminiProvider(
            apiEndpoint = "https://example.test",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "gemini-test",
            client = respondingClient(body, contentType),
        )

    private fun claudeProvider(body: String, contentType: String): ClaudeProvider =
        ClaudeProvider(
            apiEndpoint = "https://example.test/v1/messages",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "claude-test",
            client = respondingClient(body, contentType),
            providerType = ApiProviderType.ANTHROPIC,
        )

    private fun respondingClient(body: String, contentType: String): OkHttpClient {
        val mediaType = contentType.toMediaType()
        return OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", contentType)
                        .body(body.toResponseBody(mediaType))
                        .build()
                },
            )
            .build()
    }

    companion object {
        private val OPENAI_RESPONSE =
            """
            {
              "choices": [{"message": {"content": "answer"}, "finish_reason": "stop"}],
              "usage": {"prompt_tokens": 3, "completion_tokens": 2, "total_tokens": 5}
            }
            """.trimIndent()

        private val OPENAI_STREAM_RESPONSE =
            """
            data: {"choices":[{"delta":{"content":"answer"},"finish_reason":null}]}

            data: {"choices":[],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}

            data: [DONE]

            """.trimIndent()

        private val OPENAI_FINISHED_STREAM_RESPONSE =
            """
            data: {"choices":[{"delta":{"content":"answer"},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}
            """.trimIndent()

        private val OPENAI_INCOMPLETE_STREAM_RESPONSE =
            """
            data: {"choices":[{"delta":{"content":"answer"},"finish_reason":null}],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}
            """.trimIndent()

        private val GEMINI_RESPONSE =
            """
            {
              "usageMetadata": {
                "promptTokenCount": 3,
                "cachedContentTokenCount": 0,
                "candidatesTokenCount": 2
              },
              "candidates": [{
                "finishReason": "STOP",
                "content": {"parts": [{"text": "answer"}]}
              }]
            }
            """.trimIndent()

        private val GEMINI_STREAM_RESPONSE =
            "data: ${GEMINI_RESPONSE.replace("\n", "")}" + "\n\n" + "data: [DONE]\n\n"

        private val GEMINI_FINISHED_STREAM_RESPONSE = "data: ".plus(GEMINI_RESPONSE.replace("\n", ""))

        private val GEMINI_INCOMPLETE_STREAM_RESPONSE =
            """
            data: {
              "usageMetadata": {
                "promptTokenCount": 3,
                "cachedContentTokenCount": 0,
                "candidatesTokenCount": 2
              },
              "candidates": [{
                "content": {"parts": [{"text": "answer"}]}
              }]
            }
            """.trimIndent().replace("\n", "")

        private val GEMINI_UNSPECIFIED_STREAM_RESPONSE =
            """
            data: {
              "usageMetadata": {
                "promptTokenCount": 3,
                "cachedContentTokenCount": 0,
                "candidatesTokenCount": 2
              },
              "candidates": [{
                "finishReason": "FINISH_REASON_UNSPECIFIED",
                "content": {"parts": [{"text": "answer"}]}
              }]
            }
            """.trimIndent().replace("\n", "")

        private val GEMINI_PROMPT_BLOCKED_RESPONSE =
            """
            {
              "promptFeedback": {
                "blockReason": "SAFETY",
                "blockReasonMessage": "blocked"
              },
              "usageMetadata": {
                "promptTokenCount": 3,
                "cachedContentTokenCount": 0,
                "candidatesTokenCount": 0
              }
            }
            """.trimIndent()

        private val GEMINI_PROMPT_BLOCKED_STREAM_RESPONSE =
            "data: ".plus(GEMINI_PROMPT_BLOCKED_RESPONSE.replace("\n", ""))

        private val CLAUDE_FINISHED_STREAM_RESPONSE =
            """
            data: {"type":"message_start","message":{"usage":{"input_tokens":3,"cache_read_input_tokens":0}}}

            data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"answer"}}

            data: {"type":"message_delta","usage":{"output_tokens":2}}

            data: {"type":"message_stop"}
            """.trimIndent()

        private val CLAUDE_INCOMPLETE_STREAM_RESPONSE =
            """
            data: {"type":"message_start","message":{"usage":{"input_tokens":3,"cache_read_input_tokens":0}}}

            data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"answer"}}

            data: {"type":"message_delta","usage":{"output_tokens":2}}
            """.trimIndent()

        private val CLAUDE_COMPATIBLE_JSONL_RESPONSE =
            """
            {"choices":[{"delta":{"content":"answer"},"finish_reason":null}]}
            {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}
            """.trimIndent()
    }
}
