package com.ai.assistance.operit.integrations.http

import com.ai.assistance.operit.util.AppLogger
import java.io.BufferedWriter
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

private const val CHAT_ID = "chat-under-test"
private const val PIPE_BUFFER_SIZE = 64 * 1024
private const val TIMEOUT_MS = 10_000L
private const val SSE_EVENT = "event: assistant_delta\ndata: {\"delta\":\"hi\"}\n\n"

class WebChatSseWriterTest {
    private var previousSystemLogEnabled = true
    private val scopes = mutableListOf<CoroutineScope>()
    private val threads = mutableListOf<Thread>()

    @Before
    fun disableAndroidSystemLogForJvmTests() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        AppLogger.enableSystemLog = false
    }

    @After
    fun tearDown() {
        AppLogger.enableSystemLog = previousSystemLogEnabled
        scopes.forEach { it.cancel() }
        scopes.clear()
        threads.forEach { it.join(TIMEOUT_MS) }
        threads.clear()
    }

    @Test
    fun closingAWriterWhoseClientIsGoneRaisesPipeClosed() {
        val pipeInput = PipedInputStream(PIPE_BUFFER_SIZE)
        val writer = PipedOutputStream(pipeInput).bufferedWriter(StandardCharsets.UTF_8)
        pipeInput.close()
        writer.write(SSE_EVENT)

        try {
            writer.close()
            fail("expected the close of a writer whose pipe reader is gone to fail")
        } catch (expected: IOException) {
            assertEquals("Pipe closed", expected.message)
        }
    }

    @Test
    fun unguardedWriterCloseEscapesTheStreamCoroutine() = runBlocking {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val scope = recordingScope(uncaught)
        val pipeInput = PipedInputStream(PIPE_BUFFER_SIZE)
        pipeInput.close()

        // The shape that shipped in 1.12.0: use{} owns the writer and nothing guards its close, so
        // the bytes still buffered when close() pushes them into the dead pipe escape the coroutine
        // as an unhandled failure. The service scope installs no CoroutineExceptionHandler, which is
        // what turned this into the crash reported in the issue.
        val streamJob = scope.launch {
            PipedOutputStream(pipeInput).bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write(SSE_EVENT)
            }
        }

        withTimeout(TIMEOUT_MS) { streamJob.join() }

        assertTrue("the escaping close failure should have failed the stream job", streamJob.isCancelled)
        assertEquals(1, uncaught.size)
        assertTrue(uncaught.single() is IOException)
        assertEquals("Pipe closed", uncaught.single().message)
    }

    @Test
    fun closeSseWriterAbsorbsTheClosePhasePipeClosed() {
        val pipeInput = PipedInputStream(PIPE_BUFFER_SIZE)
        val writer = PipedOutputStream(pipeInput).bufferedWriter(StandardCharsets.UTF_8)
        pipeInput.close()
        // Left unflushed so that close() has to push it into the dead pipe.
        writer.write(SSE_EVENT)

        closeSseWriter(writer, CHAT_ID)
    }

    @Test
    fun closeSseWriterStillFlushesAndClosesAHealthyWriter() {
        val pipeInput = PipedInputStream(PIPE_BUFFER_SIZE)
        val writer = PipedOutputStream(pipeInput).bufferedWriter(StandardCharsets.UTF_8)
        val received = CompletableDeferred<String>()
        startClientThread {
            received.complete(String(pipeInput.readBytes(), StandardCharsets.UTF_8))
        }

        writer.write(SSE_EVENT)
        closeSseWriter(writer, CHAT_ID)

        assertEquals(SSE_EVENT, runBlocking { withTimeout(TIMEOUT_MS) { received.await() } })
    }

    @Test
    fun clientDisconnectWhileWritingEndsTheStreamJob() = runBlocking {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val scope = recordingScope(uncaught)
        val pipeInput = PipedInputStream(PIPE_BUFFER_SIZE)
        val pipeOutput = PipedOutputStream(pipeInput)
        val cancelledChats = CopyOnWriteArrayList<String>()

        // The reader plays the browser: it consumes a couple of events and then goes away, which
        // is what closes the response body when the tab is closed mid-generation.
        startClientThread {
            val buffer = ByteArray(4096)
            runCatching { repeat(2) { pipeInput.read(buffer) } }
            runCatching { pipeInput.close() }
        }

        val streamJob = scope.launchStreamJob(
            pipeOutput = pipeOutput,
            onCancelMessage = { cancelledChats.add(CHAT_ID) }
        ) { writer ->
            repeat(500) { index ->
                writer.write("event: assistant_delta\ndata: {\"index\":$index}\n\n")
                writer.flush()
                delay(2)
            }
        }

        withTimeout(TIMEOUT_MS) { streamJob.join() }

        assertEquals(listOf(CHAT_ID), cancelledChats)
        assertTrue("the stream job should have ended", streamJob.isCompleted)
        assertFalse("the stream job should end normally rather than fail", streamJob.isCancelled)
        assertTrue("expected no uncaught exception, got $uncaught", uncaught.isEmpty())
    }

    @Test
    fun cancellingAStreamJobWithBufferedDataDoesNotEscapeACloseFailure() = runBlocking {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val scope = recordingScope(uncaught)
        val pipeInput = PipedInputStream(PIPE_BUFFER_SIZE)
        val pipeOutput = PipedOutputStream(pipeInput)
        val cancelledChats = CopyOnWriteArrayList<String>()
        val reachedStream = CompletableDeferred<Unit>()

        val streamJob = scope.launchStreamJob(
            pipeOutput = pipeOutput,
            onCancelMessage = { cancelledChats.add(CHAT_ID) }
        ) { writer ->
            // A partially written event stays buffered until close() flushes it.
            writer.write("event: assistant_delta\ndata: {\"partial\":")
            reachedStream.complete(Unit)
            delay(TIMEOUT_MS)
        }
        withTimeout(TIMEOUT_MS) { reachedStream.await() }

        // The browser went away: NanoHTTPD closes the response body, which closes the pipe and
        // then cancels the stream job.
        pipeInput.close()
        streamJob.cancel()
        withTimeout(TIMEOUT_MS) { streamJob.join() }

        assertEquals(listOf(CHAT_ID), cancelledChats)
        assertTrue(streamJob.isCancelled)
        assertTrue("expected no uncaught exception, got $uncaught", uncaught.isEmpty())
    }

    private fun recordingScope(uncaught: MutableList<Throwable>): CoroutineScope {
        val scope = CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO +
                CoroutineExceptionHandler { _, throwable -> uncaught.add(throwable) }
        )
        scopes.add(scope)
        return scope
    }

    private fun startClientThread(block: () -> Unit) {
        val thread = Thread(block).apply { isDaemon = true }
        threads.add(thread)
        thread.start()
    }

    /** Mirrors the stream job of WebChatHttpBridge.handleStream. */
    private fun CoroutineScope.launchStreamJob(
        pipeOutput: PipedOutputStream,
        onCancelMessage: () -> Unit,
        body: suspend (BufferedWriter) -> Unit
    ): Job = launch {
        val writer = pipeOutput.bufferedWriter(StandardCharsets.UTF_8)
        try {
            body(writer)
        } catch (e: CancellationException) {
            onCancelMessage()
            throw e
        } catch (e: IOException) {
            onCancelMessage()
        } finally {
            closeSseWriter(writer, CHAT_ID)
        }
    }
}
