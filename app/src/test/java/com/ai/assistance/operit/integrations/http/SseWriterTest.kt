package com.ai.assistance.operit.integrations.http

import com.ai.assistance.operit.util.logging.AppLogger
import java.io.ByteArrayOutputStream
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SseWriterTest {
    private var previousSystemLogEnabled = true
    private var previousFileLogEnabled = true
    private val scopes = mutableListOf<CoroutineScope>()
    private val event = "event: assistant_delta\ndata: {\"delta\":\"你好\"}\n\n"

    @Before
    fun disableAndroidLogging() {
        previousSystemLogEnabled = AppLogger.enableSystemLog
        previousFileLogEnabled = AppLogger.enableFileLogging
        AppLogger.enableSystemLog = false
        AppLogger.enableFileLogging = false
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        scopes.clear()
        AppLogger.enableSystemLog = previousSystemLogEnabled
        AppLogger.enableFileLogging = previousFileLogEnabled
    }

    @Test
    fun healthyWriterFlushesUtf8AndClosesItsOutput() {
        val output = RecordingOutputStream()

        withSseWriter(output, "healthy") { it.write(event) }

        assertEquals(event, output.toString(StandardCharsets.UTF_8.name()))
        assertTrue(output.closed)
    }

    @Test
    fun earlyBlockReturnStillFlushesAndCloses() {
        val output = RecordingOutputStream()
        val value = withSseWriter(output, "early-return") { writer ->
            writer.write(event)
            return@withSseWriter 17
        }

        assertEquals(17, value)
        assertEquals(event, output.toString(StandardCharsets.UTF_8.name()))
        assertTrue(output.closed)
    }

    @Test
    fun disconnectedPipeDoesNotLoseTheSuccessfulBodyResultDuringClose() {
        val input = PipedInputStream(64 * 1024)
        val output = PipedOutputStream(input)
        input.close()

        val value = withSseWriter(output, "disconnected") { writer ->
            // The event stays buffered; only close attempts to write to the closed pipe.
            writer.write(event)
            "finished"
        }

        assertEquals("finished", value)
    }

    @Test
    fun writePhaseIoFailureStillReachesTheCaller() {
        val input = PipedInputStream(64 * 1024)
        val output = PipedOutputStream(input)
        input.close()

        assertThrows(IOException::class.java) {
            withSseWriter(output, "write-failure") { writer ->
                writer.write(event)
                writer.flush()
            }
        }
    }

    @Test
    fun closePhaseIoFailureDoesNotMaskCancellation() {
        val input = PipedInputStream(64 * 1024)
        val output = PipedOutputStream(input)
        input.close()
        val cancellation = CancellationException("client cancelled")

        val caught = assertThrows(CancellationException::class.java) {
            withSseWriter(output, "cancelled") { writer ->
                writer.write(event)
                throw cancellation
            }
        }

        assertSame(cancellation, caught)
    }

    @Test
    fun closePhaseIoFailureDoesNotMaskABodyFailure() {
        val input = PipedInputStream(64 * 1024)
        val output = PipedOutputStream(input)
        input.close()
        val failure = IllegalArgumentException("invalid event")

        val caught = assertThrows(IllegalArgumentException::class.java) {
            withSseWriter(output, "body-failure") { writer ->
                writer.write(event)
                throw failure
            }
        }

        assertSame(failure, caught)
    }

    @Test
    fun nonIoCloseFailureIsNotSwallowed() {
        val failure = IllegalStateException("unexpected close failure")
        val output = object : ByteArrayOutputStream() {
            override fun close() { throw failure }
        }

        val caught = assertThrows(IllegalStateException::class.java) {
            withSseWriter(output, "unexpected-close") { it.write(event) }
        }

        assertSame(failure, caught)
    }

    @Test
    fun bodyFailureRetainsANonIoCloseFailureAsSuppressed() {
        val bodyFailure = IllegalArgumentException("body failure")
        val closeFailure = IllegalStateException("close failure")
        val output = object : ByteArrayOutputStream() {
            override fun close() { throw closeFailure }
        }

        val caught = assertThrows(IllegalArgumentException::class.java) {
            withSseWriter(output, "both-fail") { throw bodyFailure }
        }

        assertSame(bodyFailure, caught)
        assertEquals(1, caught.suppressed.size)
        assertSame(closeFailure, caught.suppressed.single())
    }

    @Test
    fun cancellingARealStreamJobKeepsCancellationWithoutAnUncaughtCloseFailure() = runBlocking {
        val uncaught = CopyOnWriteArrayList<Throwable>()
        val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, failure ->
                uncaught.add(failure)
            }
        )
        scopes.add(scope)
        val input = PipedInputStream(64 * 1024)
        val output = PipedOutputStream(input)
        val started = CompletableDeferred<Unit>()
        val job = scope.launch {
            withSseWriter(output, "stream-job") { writer ->
                writer.write(event)
                started.complete(Unit)
                awaitCancellation()
            }
        }

        try {
            withTimeout(5_000) { started.await() }
            input.close()
            job.cancel()
            withTimeout(5_000) { job.join() }

            assertTrue(job.isCancelled)
            assertTrue("close must not escape the cancelled job: $uncaught", uncaught.isEmpty())
        } finally {
            scope.cancel()
            input.close()
        }
    }

    private class RecordingOutputStream : ByteArrayOutputStream() {
        var closed = false
        override fun close() {
            closed = true
            super.close()
        }
    }
}
