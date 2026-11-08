package com.ai.assistance.operit.core.chat

import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.ChatMessage
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class AIMessageManagerSummaryDispatchTest {
    @Test fun summaryPreprocessingRunsOffCallingThreadAndPropagatesCancellation() = runBlocking {
        val service = Mockito.mock(EnhancedAIService::class.java)
        val callingThread = Thread.currentThread()
        val readingThread = AtomicReference<Thread>()
        val interruption = CancellationException("历史读取探针完成")
        val messages = object : AbstractList<ChatMessage>() {
            override val size = 1
            override fun get(index: Int): ChatMessage {
                readingThread.set(Thread.currentThread())
                // 在第一处历史读取停止，检查真实总结入口的调度，不调用模型或 Android 资源。
                throw interruption
            }
        }

        val failure = runCatching {
            AIMessageManager.summarizeMemory(service, messages)
        }.exceptionOrNull()

        assertNotNull(readingThread.get())
        assertNotSame(callingThread, readingThread.get())
        assertTrue(failure is CancellationException)
        assertEquals(interruption.message, failure?.message)
        Mockito.verifyNoInteractions(service)
    }

    @Test fun cancelledSummaryDoesNotReadHistoryOrStartModelRequest() = runBlocking {
        val service = Mockito.mock(EnhancedAIService::class.java)
        val reads = AtomicInteger()
        var cancellationObserved = false
        val messages = object : AbstractList<ChatMessage>() {
            override val size = 1
            override fun get(index: Int): ChatMessage {
                reads.incrementAndGet()
                throw CancellationException("取消后不应读取历史")
            }
        }

        val summaryJob = launch(start = CoroutineStart.UNDISPATCHED) {
            coroutineContext.cancel()
            try {
                AIMessageManager.summarizeMemory(service, messages)
            } catch (_: CancellationException) {
                cancellationObserved = true
            }
        }
        summaryJob.join()

        assertTrue(cancellationObserved)
        assertEquals(0, reads.get())
        Mockito.verifyNoInteractions(service)
    }
}
