package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.core.chat.hooks.PromptTurn
import com.ai.assistance.operit.core.chat.hooks.PromptTurnKind
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.TextStreamEventCarrier
import com.ai.assistance.operit.util.stream.TextStreamEventType
import com.ai.assistance.operit.util.stream.TextStreamRevisionTracker
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression coverage for issue #1375.
 *
 * A retried request must discard the whole aborted attempt, and every retry of the same attempt id
 * must report the raw offset where that retry started. Deriving the offset from the visible buffer
 * left a prefix such as `<tool_L6Gi` in the final message.
 */
class ProviderRetryRollbackTest {

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
    fun `single retry rollback discards the whole aborted attempt`() = runBlocking {
        val attempts = AtomicInteger(0)
        val provider =
            providerFor(attempts) { attempt ->
                if (attempt == 1) abortedStream(ABORTED_FIRST_TEXT) else SUCCESSFUL_STREAM
            }

        Mockito.mockStatic(AppLogger::class.java).use {
            val response = send(provider)
            val tracker = collect(response)
            val events = (response as TextStreamEventCarrier).eventChannel.replayCache

            assertEquals(
                listOf(TextStreamEventType.SAVEPOINT, TextStreamEventType.ROLLBACK),
                events.map { it.eventType }
            )
            assertTrue(
                "a request-start savepoint must report offset 0",
                events.all { it.contentLength == 0 }
            )

            val finalContent = tracker.currentContent().toString()
            assertEquals(SUCCESSFUL_TEXT, finalContent)
            assertTrue(
                "aborted attempt must not leave a tool-tag prefix behind",
                !finalContent.startsWith(ABORTED_FIRST_TEXT)
            )
            assertEquals(2, attempts.get())
        }
    }

    @Test
    fun `repeated retry rollback discards every aborted attempt`() = runBlocking {
        val attempts = AtomicInteger(0)
        val provider =
            providerFor(attempts) { attempt ->
                when (attempt) {
                    1 -> abortedStream(ABORTED_FIRST_TEXT)
                    2 -> abortedStream(ABORTED_SECOND_TEXT)
                    else -> SUCCESSFUL_STREAM
                }
            }

        Mockito.mockStatic(AppLogger::class.java).use {
            val response = send(provider)
            val tracker = collect(response)
            val events = (response as TextStreamEventCarrier).eventChannel.replayCache

            assertEquals(
                listOf(
                    TextStreamEventType.SAVEPOINT,
                    TextStreamEventType.ROLLBACK,
                    TextStreamEventType.ROLLBACK
                ),
                events.map { it.eventType }
            )
            val rollbacks = events.filter { it.eventType == TextStreamEventType.ROLLBACK }
            assertEquals(
                "each rollback must report the raw offset where its own attempt started",
                listOf(0, ABORTED_FIRST_TEXT.length),
                rollbacks.map { it.contentLength }
            )
            assertEquals(
                listOf(ABORTED_FIRST_TEXT.length, ABORTED_SECOND_TEXT.length),
                rollbacks.map { it.discardedLength }
            )

            val finalContent = tracker.currentContent().toString()
            assertEquals(SUCCESSFUL_TEXT, finalContent)
            assertTrue(
                "neither aborted attempt may leave text behind",
                !finalContent.contains(ABORTED_FIRST_TEXT) &&
                    !finalContent.contains(ABORTED_SECOND_TEXT)
            )
            assertEquals(3, attempts.get())
        }
    }

    private fun providerFor(
        attempts: AtomicInteger,
        streamForAttempt: (Int) -> String
    ): OpenAIProvider {
        val client =
            OkHttpClient.Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        val attempt = attempts.incrementAndGet()
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "text/event-stream")
                            .body(
                                streamForAttempt(attempt)
                                    .toResponseBody("text/event-stream".toMediaType())
                            )
                            .build()
                    }
                )
                .build()
        return OpenAIProvider(
            apiEndpoint = "https://example.test/v1/chat/completions",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "gpt-test",
            client = client,
            providerType = ApiProviderType.OPENAI
        )
    }

    private suspend fun send(provider: OpenAIProvider): Stream<String> {
        val context = mock<Context>()
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.getString(any<Int>())).thenReturn("status")
        whenever(context.getString(any<Int>(), any())).thenReturn("status")
        return provider.sendMessage(
            context = context,
            chatHistory = listOf(PromptTurn(PromptTurnKind.USER, "Hi")),
            modelParameters = emptyList(),
            enableThinking = false,
            stream = true,
            availableTools = null,
            preserveThinkInHistory = false,
            onTokensUpdated = { _, _, _ -> },
            onNonFatalError = {},
            enableRetry = true
        )
    }

    private suspend fun collect(response: Stream<String>): TextStreamRevisionTracker {
        val tracker = TextStreamRevisionTracker()
        response.collect { chunk -> tracker.append(chunk) }
        (response as TextStreamEventCarrier).eventChannel.replayCache.forEach { event ->
            when (event.eventType) {
                TextStreamEventType.SAVEPOINT -> tracker.savepoint(event.id, event.contentLength)
                TextStreamEventType.ROLLBACK ->
                    tracker.rollback(event.id, event.contentLength, event.discardedLength)
            }
        }
        return tracker
    }

    companion object {
        private const val ABORTED_FIRST_TEXT = "<tool_L6Gi"
        private const val ABORTED_SECOND_TEXT = "<tool_Z9q"

        private const val SUCCESSFUL_TEXT =
            "<tool_AB12 name=\"get_notifications\"><param name=\"filter\">qq</param></tool_AB12>"

        private val SUCCESSFUL_STREAM =
            """
            data: {"choices":[{"delta":{"content":"<tool_AB12 name=\"get_notifications\">"},"finish_reason":null}]}

            data: {"choices":[{"delta":{"content":"<param name=\"filter\">qq</param>"},"finish_reason":null}]}

            data: {"choices":[{"delta":{"content":"</tool_AB12>"},"finish_reason":null}]}

            data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

            data: [DONE]

            """.trimIndent()

        private fun abortedStream(text: String): String =
            """
            data: {"choices":[{"delta":{"content":"$text"},"finish_reason":null}]}

            """.trimIndent()
    }
}
