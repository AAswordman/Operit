package com.ai.assistance.operit.core.tools.defaultTool.standard

import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.MutableSharedStreamImpl
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for #1225: the caller-side wait of send_message_to_ai is decoupled
 * from the sub-session lifecycle. A wait timeout ends only the caller's wait and never
 * cancels the sub-session; real cancellations carry their true reason.
 */
class SendMessageToAiWaitTest {

    private var previousSystemLogEnabled = true
    private var previousFileLogEnabled = true

    @Before
    fun disableAndroidLoggingForJvmTests() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        previousFileLogEnabled = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun restoreAndroidLogging() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = previousFileLogEnabled
    }

    @Test
    fun `wait completes with the full response when the stream finishes within budget`() =
        runBlocking {
            val stream = MutableSharedStreamImpl<String>(replay = Int.MAX_VALUE)
            val chunks = mutableListOf<String>()

            val producer = launch {
                stream.emit("hello ")
                stream.emit("world")
                stream.close()
            }

            val result = awaitAiResponseWithin(stream, timeoutMs = 5_000) { chunk ->
                chunks.add(chunk)
            }
            producer.join()

            assertEquals(AiResponseWaitResult.Completed("hello world"), result)
            assertEquals(listOf("hello ", "world"), chunks)
        }

    @Test
    fun `wait timeout returns partial content and the sub session stream keeps running`() =
        runBlocking {
            val stream = MutableSharedStreamImpl<String>(replay = Int.MAX_VALUE)

            stream.emit("partial-")
            val result = awaitAiResponseWithin(stream, timeoutMs = 150)

            // The caller's wait ends with an accurate timeout result holding partial content.
            assertEquals(AiResponseWaitResult.WaitTimedOut(partialText = "partial-", waitedMs = 150), result)
            // The detaching collector must not tear down the sub-session's stream.
            assertEquals(0, stream.subscriptionCount)

            // The sub-session continues: later emissions are still delivered to new collectors.
            stream.resetReplayCache()
            val lateCollector = StringBuilder()
            val collectJob = launch {
                stream.collect { chunk -> lateCollector.append(chunk) }
            }
            while (stream.subscriptionCount == 0) {
                kotlinx.coroutines.delay(10)
            }
            stream.emit("rest")
            stream.close()
            collectJob.join()

            assertEquals("rest", lateCollector.toString())
        }

    @Test
    fun `session cancel forwards the caller provided reason`() {
        val stream = MutableSharedStreamImpl<String>()
        var capturedReason: String? = null
        val session =
            MessageSendStreamSession(
                chatId = "chat-1",
                message = "msg",
                responseStream = stream,
                responseTimeoutMs = 1_000,
                currentStateProvider = { InputProcessingState.Idle },
                cancelAction = { reason -> capturedReason = reason }
            )

        session.cancel("External chat client disconnected")

        assertEquals("External chat client disconnected", capturedReason)
    }

    @Test
    fun `session cancel defaults to the user cancellation reason`() {
        val stream = MutableSharedStreamImpl<String>()
        var capturedReason: String? = null
        val session =
            MessageSendStreamSession(
                chatId = "chat-1",
                message = "msg",
                responseStream = stream,
                responseTimeoutMs = 1_000,
                currentStateProvider = { InputProcessingState.Idle },
                cancelAction = { reason -> capturedReason = reason }
            )

        session.cancel()

        assertEquals(AIMessageManager.CANCELLATION_REASON_USER, capturedReason)
        assertEquals("User cancelled", capturedReason)
    }

    @Test
    fun `wait result types stay distinguishable for streaming and non-streaming callers`() {
        // Both send_message_to_ai paths branch on these two outcomes; the timeout branch
        // must never be reported as a user cancellation.
        val completed: AiResponseWaitResult = AiResponseWaitResult.Completed("done")
        val timedOut: AiResponseWaitResult = AiResponseWaitResult.WaitTimedOut("part", 180_000)

        assertTrue(completed is AiResponseWaitResult.Completed)
        assertTrue(timedOut is AiResponseWaitResult.WaitTimedOut)
        assertEquals(180_000L, (timedOut as AiResponseWaitResult.WaitTimedOut).waitedMs)
    }
}
