package com.ai.assistance.operit.core.chat.context

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryProgressReporterTest {
    @Test
    fun everyRetryPublishesItsErrorAfterTheRetryLabelEvenWhenErrorsRepeat() = runBlocking {
        val events = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val reporter = SummaryProgressReporter(
            preparingMessage = "preparing", retryingMessage = "retrying",
            onProgress = {},
            onRetryError = { errors += it; events += "error:$it" },
            publishProgress = { _, message -> events += "progress:$message" },
        )
        reporter.start()
        repeat(5) { reporter.retry("HTTP 429: rate limited") }
        assertEquals(List(5) { "HTTP 429: rate limited" }, errors)
        assertEquals(
            listOf("progress:preparing") + List(5) {
                listOf("progress:retrying", "error:HTTP 429: rate limited")
            }.flatten(),
            events,
        )
    }

    @Test
    fun rollbackKeepsRetryLabelUntilNewContentAndRestartsTheStageProgress() = runBlocking {
        val progress = mutableListOf<Pair<Float, String>>()
        val labels = mutableListOf<String>()
        val reporter = SummaryProgressReporter(
            preparingMessage = "preparing", retryingMessage = "retrying",
            onProgress = { labels += it }, onRetryError = {},
            publishProgress = { value, message -> progress += value to message },
        )
        reporter.start()
        reporter.update(0.85f, "key info")
        reporter.retry("HTTP 503")
        reporter.rollback()
        assertEquals(0.05f to "retrying", progress.last())
        assertEquals("retrying", labels.last())
        reporter.receivedContent()
        assertEquals(0.05f to "preparing", progress.last())
        reporter.update(0.20f, "title")
        reporter.complete("completed")
        assertEquals(listOf("preparing", "key info", "retrying", "preparing", "title", "completed"), labels)
        assertEquals(1f to "completed", progress.last())
    }

    @Test
    fun cancelledAttemptDoesNotPublishALateRetryOrError() = runBlocking {
        val events = mutableListOf<String>()
        val reporter = SummaryProgressReporter(
            preparingMessage = "preparing", retryingMessage = "retrying",
            onProgress = {}, onRetryError = { events += "error:$it" },
            publishProgress = { _, message -> events += message },
        )
        reporter.start()
        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            reporter.retry("HTTP 503")
        }
        request.join()
        assertEquals(listOf("preparing"), events)
    }
}