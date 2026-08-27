package com.ai.assistance.operit.api.chat.llmprovider

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorClassifierTest {
    @Test
    fun classifiesInvalidRequestAndKeepsProviderCode() {
        val error = ApiErrorClassifier.classify(
            StatusException(
                statusCode = 400,
                message = "API request status: 400 {\"error\":{\"type\":\"invalid_request_error\"}}"
            )
        )

        assertEquals("invalid_request", error.code)
        assertEquals(400, error.httpStatusCode)
        assertEquals("invalid_request_error", error.providerCode)
        assertFalse(error.recoverable)
    }

    @Test
    fun prioritizesAuthenticationStatusOverOuterTimeout() {
        val error = ApiErrorClassifier.classify(
            IOException(
                "连接超时或中断，已重试 5 次: API请求失败，状态码: 401",
                StatusException(
                    statusCode = 401,
                    message = "{\"error\":{\"type\":\"authentication_error\",\"code\":\"invalid_request_error\"}}"
                )
            )
        )

        assertEquals("authentication_failed", error.code)
        assertEquals(401, error.httpStatusCode)
        assertEquals("invalid_request_error", error.providerCode)
        assertFalse(error.recoverable)
    }

    @Test
    fun classifiesRetryMessageFromProviderBeforeNextAttempt() {
        val error = ApiErrorClassifier.classifyMessage(
            "API请求失败，状态码: 401，错误信息: {\"error\":{\"type\":\"authentication_error\",\"code\":\"invalid_request_error\"}}"
        )

        assertEquals("authentication_failed", error.code)
        assertEquals(401, error.httpStatusCode)
        assertEquals("invalid_request_error", error.providerCode)
    }

    @Test
    fun classifiesModelNotFoundAsNonRecoverable() {
        val error = ApiErrorClassifier.classify(
            StatusException(404, "model_not_found: requested model does not exist")
        )

        assertEquals("model_not_found", error.code)
        assertEquals(404, error.httpStatusCode)
        assertEquals("model_not_found", error.providerCode)
        assertFalse(error.recoverable)
    }
    @Test
    fun classifiesBalanceAndQuotaErrorsAsNonRecoverable() {
        val balance = ApiErrorClassifier.classify(StatusException(402, "Insufficient Balance"))
        val quota = ApiErrorClassifier.classify(
            StatusException(429, "You exceeded your current quota")
        )

        assertEquals("insufficient_balance", balance.code)
        assertFalse(balance.recoverable)
        assertEquals("quota_exceeded", quota.code)
        assertFalse(quota.recoverable)
    }

    @Test
    fun classifiesRateLimitAndServerOverloadAsRecoverable() {
        val rateLimit = ApiErrorClassifier.classify(
            StatusException(429, "rate_limit_error: too many requests")
        )
        val overload = ApiErrorClassifier.classify(
            StatusException(503, "service overloaded")
        )

        assertEquals("rate_limited", rateLimit.code)
        assertTrue(rateLimit.recoverable)
        assertEquals("server_overloaded", overload.code)
        assertTrue(overload.recoverable)
    }

    @Test
    fun classifiesServiceUnavailableAndResponsePolicyErrors() {
        val unavailable = ApiErrorClassifier.classify(
            StatusException(503, "service temporarily unavailable")
        )
        val policy = ApiErrorClassifier.classify(
            StatusException(400, "content_filter: safety policy blocked the request")
        )

        assertEquals("service_unavailable", unavailable.code)
        assertTrue(unavailable.recoverable)
        assertEquals("content_policy", policy.code)
        assertFalse(policy.recoverable)
    }

    @Test
    fun classifiesTimeoutAndUnknownHost() {
        val timeout = ApiErrorClassifier.classify(SocketTimeoutException("read timed out"))
        val unknownHost = ApiErrorClassifier.classify(UnknownHostException("api.example.com"))

        assertEquals("timeout", timeout.code)
        assertTrue(timeout.recoverable)
        assertEquals("network_error", unknownHost.code)
        assertTrue(unknownHost.recoverable)
    }

    @Test
    fun keepsUnknownErrorExtensible() {
        val error = ApiErrorClassifier.classify(
            IllegalStateException("an uncategorized host failure")
        )

        assertEquals("unknown", error.code)
        assertEquals(null, error.httpStatusCode)
        assertFalse(error.recoverable)
    }

    private class StatusException(
        override val statusCode: Int,
        message: String
    ) : IOException(message), HttpStatusCodeException
}
