package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.error.HttpStatusCodeException
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyRequestFailureTest {
    @Test
    fun wrappedPoolExhaustionStopsWithoutRecordingAnotherKeyFailure() = runBlocking {
        val exhausted = ApiKeyPoolExhaustedException("测试标记不可用", ApiKeyTriedSummary())
        val wrapped = IOException("包装异常", exhausted)
        val session = RecordingSession()
        val caught = withContext(ApiKeyRequestElement(session)) {
            runCatching { reportKeyAwareFailure(wrapped, "model", false, 1) }.exceptionOrNull()
        }
        assertSame(exhausted, caught)
        assertEquals(ApiRequestRetryMode.STOP, ApiRequestRetryPolicy.mode(wrapped))
        assertEquals(ApiKeyErrorClass.NONE, ApiKeyFailureClassifier.classify(wrapped))
        assertTrue(session.reports.isEmpty())
    }

    @Test
    fun routeAndMethodErrorsDoNotPoisonKeyStatistics() = runBlocking {
        val session = RecordingSession()
        withContext(ApiKeyRequestElement(session)) {
            listOf(
                HttpFailure(404, "no Route matched with those values"),
                HttpFailure(405, "method not allowed"),
            ).forEach { failure ->
                assertEquals(ApiKeyErrorClass.NONE, ApiKeyFailureClassifier.classify(failure))
                assertEquals(ApiRequestRetryMode.STOP, ApiRequestRetryPolicy.mode(failure))
                assertTrue(ApiKeyFailureClassifier.isEndpointError(failure))
                reportKeyAwareFailure(failure, "model", false, 1)
            }
        }
        assertTrue(session.reports.isEmpty())
    }

    @Test
    fun explicitModelFailureStillRecordsAKeyFailureForRotation() = runBlocking {
        val session = RecordingSession()
        val failure = HttpFailure(404, "model_not_found")
        withContext(ApiKeyRequestElement(session)) {
            reportKeyAwareFailure(failure, "model", false, 1)
        }
        assertEquals(ApiRequestRetryMode.ROTATE_KEY, ApiRequestRetryPolicy.mode(failure))
        assertEquals(ApiKeyErrorClass.MODEL, session.reports.single().errorClass)
    }

    private class HttpFailure(
        override val statusCode: Int,
        message: String,
    ) : IOException(message), HttpStatusCodeException

    private class RecordingSession : ApiKeyRequestSession {
        val reports = mutableListOf<ApiKeyAttemptReport>()
        override suspend fun reportOutcome(report: ApiKeyAttemptReport) { reports += report }
        override suspend fun nextKey(): ApiKeySelection = error("此用例不应再次选钥")
        override suspend fun maxAttempts(): Int = error("此用例不应计算重试预算")
        override fun triedSummary() = ApiKeyTriedSummary()
        override suspend fun decideRetry(errorClass: ApiKeyErrorClass, retryAfterMs: Long?): ApiKeyRetryDecision =
            error("此用例不应触发重试")
    }
}
