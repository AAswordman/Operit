package com.ai.assistance.operit.util

import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WaifuStreamingTimingTest {
    private fun requireNativeStreamSplitter() {
        try {
            System.loadLibrary("streamnative")
        } catch (error: UnsatisfiedLinkError) {
            assumeNoException(error)
        }
    }

    @Test
    fun firstPreviewArrivesBeforeSentenceAndUpstreamCompletion() = runTest {
        requireNativeStreamSplitter()
        val finish = CompletableDeferred<Unit>()
        val preview = CompletableDeferred<String>()
        var upstreamFinished = false
        val source: Stream<String> = stream {
            emit("你好")
            finish.await()
            emit("，这是后续。")
            upstreamFinished = true
        }
        val segments = mutableListOf<String>()
        val job = launch {
            WaifuMessageProcessor.streamSegmentsWithTypingQueue(
                source, charDelayMs = 240,
                onFirstSegmentPreview = { if (!preview.isCompleted) preview.complete(it) },
            ).collect { segments.add(it) }
        }
        runCurrent()
        assertTrue("无句末标点时也应先显示正文", preview.isCompleted)
        assertFalse("首次显示不能依赖上游完成", upstreamFinished)
        assertTrue(segments.isEmpty())
        finish.complete(Unit)
        advanceUntilIdle()
        job.join()
        assertEquals(listOf("你好，这是后续。"), segments)
    }

    @Test
    fun stableSentenceArrivesBeforeUpstreamCompletion() = runTest {
        requireNativeStreamSplitter()
        val finish = CompletableDeferred<Unit>()
        val segments = mutableListOf<String>()
        val source: Stream<String> = stream {
            emit("第一句。")
            finish.await()
            emit("第二句。")
        }
        val job = launch {
            WaifuMessageProcessor.streamSegmentsWithTypingQueue(source, charDelayMs = 240)
                .collect { segments.add(it) }
        }
        runCurrent()
        assertEquals(listOf("第一句。"), segments)
        finish.complete(Unit)
        runCurrent()
        assertEquals("后续句子应保留打字等待", listOf("第一句。"), segments)
        advanceUntilIdle()
        job.join()
        assertEquals(listOf("第一句。", "第二句。"), segments)
    }

    @Test
    fun cancellingPreviewCancelsUpstreamAndStopsQueuedOutput() = runTest {
        requireNativeStreamSplitter()
        val finish = CompletableDeferred<Unit>()
        var upstreamCancelled = false
        var previewCount = 0
        val source: Stream<String> = stream {
            try {
                emit("正在回复")
                finish.await()
                emit("。后续。")
            } finally {
                upstreamCancelled = true
            }
        }
        val segments = mutableListOf<String>()
        val job = launch {
            WaifuMessageProcessor.streamSegmentsWithTypingQueue(
                source, charDelayMs = 240, onFirstSegmentPreview = { previewCount++ },
            ).collect { segments.add(it) }
        }
        runCurrent()
        assertTrue(previewCount > 0)
        job.cancelAndJoin()
        finish.complete(Unit)
        advanceUntilIdle()
        assertTrue(upstreamCancelled)
        assertTrue(segments.isEmpty())
    }
}
