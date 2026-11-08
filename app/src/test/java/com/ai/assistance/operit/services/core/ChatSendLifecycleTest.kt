package com.ai.assistance.operit.services.core

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChatSendLifecycleTest {
    @Test
    fun successFinishesOnceWithoutReportingFailure() = runBlocking {
        val events = mutableListOf<String>()
        runChatSendLifecycle(
            onCancelled = { fail("正常发送不应取消") },
            onFailure = { fail("正常发送不应报错") },
            onFinally = { events += "finish" },
        ) { events += "send" }
        assertEquals(listOf("send", "finish"), events)
    }

    @Test
    fun cancellingSuspendedSendCompletesSuspendingCleanupAndKeepsParentAlive() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var completed = false
        val job = launch {
            runChatSendLifecycle(
                onCancelled = { events += "cancel" },
                onFailure = { fail("取消不应被报告成请求失败") },
                onFinally = {
                    delay(1)
                    events += "finish"
                },
            ) {
                started.complete(Unit)
                awaitCancellation()
            }
            completed = true
        }
        started.await()
        job.cancelAndJoin()
        assertEquals(listOf("cancel", "finish"), events)
        assertTrue(job.isCancelled)
        assertFalse(completed)
        assertTrue(isActive)
    }

    @Test
    fun providerCancellationPropagatesTheOriginalException() = runBlocking {
        val expected = CancellationException("用户停止请求")
        var cancellationCount = 0
        var cleanupCount = 0
        try {
            runChatSendLifecycle(
                onCancelled = {
                    assertSame(expected, it)
                    cancellationCount++
                },
                onFailure = { fail("流主动取消不应显示错误") },
                onFinally = { cleanupCount++ },
            ) { throw expected }
            fail("取消必须继续传播")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
        assertEquals(1, cancellationCount)
        assertEquals(1, cleanupCount)
    }

    @Test
    fun ordinaryFailureReportsOnceThenFinishes() = runBlocking {
        val expected = IOException("连接已断开")
        val events = mutableListOf<String>()
        runChatSendLifecycle(
            onCancelled = { fail("普通失败不应取消") },
            onFailure = {
                assertSame(expected, it)
                events += "error"
            },
            onFinally = { events += "finish" },
        ) { throw expected }
        assertEquals(listOf("error", "finish"), events)
    }

    @Test
    fun earlyReturnStillFinishesOnce() = runBlocking {
        var cleanupCount = 0
        runChatSendLifecycle(
            onCancelled = { fail("提前结束不应取消") },
            onFailure = { fail("提前结束不应报错") },
            onFinally = { cleanupCount++ },
        ) request@{ return@request }
        assertEquals(1, cleanupCount)
    }
}
