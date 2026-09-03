package com.ai.assistance.operit.api.chat.llmprovider

import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
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
    fun prefersStructuredErrorCodeOverErrorType() {
        val error = ApiErrorClassifier.classify(
            StatusException(401, "{\"error\":{\"message\":\"Authentication failed\",\"type\":\"authentication_error\",\"param\":null,\"code\":\"invalid_api_key\"}}")
        )
        assertEquals("invalid_api_key", error.providerCode)
        assertEquals("authentication_failed", error.code)
        assertEquals(401, error.httpStatusCode)
    }

    @Test
    fun structuredTypeIsUsedWhenProviderCodeIsAbsent() {
        val error = ApiErrorClassifier.classify(
            StatusException(400, "{\"error\":{\"type\":\"custom_provider_error\",\"message\":\"bad request\"}}")
        )
        assertEquals("custom_provider_error", error.providerCode)
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
    fun classifiesSevenLocalNetworkFailuresWithOperitCodes() {
        val cases = listOf(
            UnknownHostException("api.example.com") to OperitNetworkError.DNS_RESOLUTION_FAILED,
            ConnectException("Connection refused") to OperitNetworkError.CONNECTION_REFUSED,
            NoRouteToHostException("Network is unreachable") to OperitNetworkError.NETWORK_UNREACHABLE,
            SocketException("Connection reset") to OperitNetworkError.CONNECTION_RESET,
            EOFException("unexpected end of stream") to OperitNetworkError.CONNECTION_CLOSED,
            SSLHandshakeException("certificate verify failed") to OperitNetworkError.TLS_HANDSHAKE_FAILED,
            SocketTimeoutException("read timed out") to OperitNetworkError.CONNECTION_TIMEOUT,
        )

        cases.forEach { (throwable, expected) ->
            val error = ApiErrorClassifier.classify(throwable)
            assertEquals(expected.classificationCode, error.code)
            assertEquals(expected.appCode, error.appCode)
            assertTrue(error.recoverable)
            assertEquals(null, error.httpStatusCode)
        }
    }

    @Test
    fun keepsHttpTimeoutSeparateFromLocalNetworkTimeout() {
        val error = ApiErrorClassifier.classify(StatusException(504, "gateway timeout"))

        assertEquals("timeout", error.code)
        assertEquals(null, error.appCode)
        assertEquals(504, error.httpStatusCode)
        assertTrue(error.recoverable)
    }

    @Test
    fun classifiesNetworkFailureFromNestedCause() {
        val error = ApiErrorClassifier.classify(
            IOException("provider request failed", UnknownHostException("api.example.com"))
        )

        assertEquals("dns_resolution_failed", error.code)
        assertEquals(OperitNetworkError.DNS_RESOLUTION_FAILED.appCode, error.appCode)
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
