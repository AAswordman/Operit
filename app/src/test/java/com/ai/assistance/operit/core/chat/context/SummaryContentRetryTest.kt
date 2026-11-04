package com.ai.assistance.operit.core.chat.context

import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SummaryContentRetryTest {
    @Test
    fun nonEmptyFirstResultReturnsWithoutRetryingOrChangingTheContent() = runBlocking {
        var generations = 0
        var retryNotices = 0
        val summary = generateNonEmptySummary(
            emptyResultMessage = "empty summary",
            onEmptyRetry = { retryNotices++ },
        ) {
            generations++
            "  已生成摘要  "
        }
        assertEquals("  已生成摘要  ", summary)
        assertEquals(1, generations)
        assertEquals(0, retryNotices)
    }

    @Test
    fun blankResultPublishesOneNoticeBeforeGeneratingAgain() = runBlocking {
        val events = mutableListOf<String>()
        var generations = 0
        val summary = generateNonEmptySummary(
            emptyResultMessage = "empty summary",
            onEmptyRetry = { events += "empty retry" },
        ) {
            generations++
            events += "generation $generations"
            if (generations == 1) "   " else "重新生成的有效摘要"
        }
        assertEquals("重新生成的有效摘要", summary)
        assertEquals(listOf("generation 1", "empty retry", "generation 2"), events)
    }

    @Test
    fun twoBlankResultsFailWithoutPublishingAnotherNoticeOrSavingASummary() = runBlocking {
        var generations = 0
        var retryNotices = 0
        var saved = false
        try {
            generateNonEmptySummary(
                emptyResultMessage = "本次总结未保存",
                onEmptyRetry = { retryNotices++ },
            ) {
                generations++
                if (generations == 1) "" else "   "
            }
            saved = true
            fail("连续两次空正文必须作为失败返回")
        } catch (error: IllegalStateException) {
            assertEquals("本次总结未保存", error.message)
        }
        assertEquals(2, generations)
        assertEquals(1, retryNotices)
        assertFalse(saved)
    }

    @Test
    fun requestFailureIsNotCaughtOrRetriedByTheEmptyResultPolicy() = runBlocking {
        val failure = IOException("HTTP request retries exhausted")
        var generations = 0
        var retryNotices = 0
        try {
            generateNonEmptySummary(
                emptyResultMessage = "empty summary",
                onEmptyRetry = { retryNotices++ },
            ) {
                generations++
                throw failure
            }
            fail("请求异常必须保留原有失败路径")
        } catch (error: IOException) {
            assertSame(failure, error)
        }
        assertEquals(1, generations)
        assertEquals(0, retryNotices)
    }

    @Test
    fun requestFailureDuringRegenerationDoesNotStartAThirdGeneration() = runBlocking {
        val failure = IOException("regeneration request failed")
        var generations = 0
        var retryNotices = 0
        try {
            generateNonEmptySummary(
                emptyResultMessage = "empty summary",
                onEmptyRetry = { retryNotices++ },
            ) {
                generations++
                if (generations == 1) "" else throw failure
            }
            fail("重新生成时的请求失败必须直接向上传递")
        } catch (error: IOException) {
            assertSame(failure, error)
        }
        assertEquals(2, generations)
        assertEquals(1, retryNotices)
    }

    @Test
    fun cancellationAfterAnEmptyResultDoesNotNotifyOrRegenerate() = runBlocking {
        var generations = 0
        var retryNotices = 0
        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            generateNonEmptySummary(
                emptyResultMessage = "empty summary",
                onEmptyRetry = { retryNotices++ },
            ) {
                generations++
                currentCoroutineContext().cancel()
                ""
            }
        }
        request.join()
        assertTrue(request.isCancelled)
        assertEquals(1, generations)
        assertEquals(0, retryNotices)
    }

    @Test
    fun cancellationDuringTheRetryNoticePreventsAnotherRequest() = runBlocking {
        var generations = 0
        var retryNotices = 0
        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            generateNonEmptySummary(
                emptyResultMessage = "empty summary",
                onEmptyRetry = {
                    retryNotices++
                    currentCoroutineContext().cancel()
                },
            ) {
                generations++
                ""
            }
        }
        request.join()
        assertTrue(request.isCancelled)
        assertEquals(1, generations)
        assertEquals(1, retryNotices)
    }
}
