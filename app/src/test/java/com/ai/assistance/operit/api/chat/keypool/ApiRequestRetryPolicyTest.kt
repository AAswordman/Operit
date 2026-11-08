package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.error.FirstTokenTimeoutException
import com.ai.assistance.operit.api.chat.error.HttpStatusCodeException
import com.ai.assistance.operit.api.chat.request.LlmRetryPolicy
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiRequestRetryPolicyTest {
    @Test
    fun invalidParametersStopWithoutRotatingKeys() {
        listOf(
            HttpFailure(400, "invalid_request_error: unsupported parameter temperature"),
            HttpFailure(422, "request validation failed"),
            HttpFailure(400, "invalid parameter temperature: not supported by model"),
            HttpFailure(403, "unsupported parameter: not allowed for this model"),
            HttpFailure(500, "unsupported_parameter: invalid value for model"),
            HttpFailure(400, "invalid_request_error: Unsupported value: temperature does not support 0 with this model"),
            HttpFailure(400, "invalid_request_error: invalid type for max_tokens"),
            IOException("INVALID_ARGUMENT: invalid request body"),
            IOException("missing required field: model"),
            IOException("invalid JSON in request body"),
            IOException("unknown field: response_format"),
            IOException("invalid_argument: 参数错误"),
            IOException("context_length_exceeded: maximum context length is 8192"),
            IOException("request failed", IOException("unsupported_parameter")),
        ).forEach { error ->
            assertEquals(error.message, ApiRequestRetryMode.STOP, ApiRequestRetryPolicy.mode(error))
        }
    }

    @Test
    fun invalidKeysRemainKeyFailuresWhenGenericRequestTypeIsPresent() {
        listOf(
            HttpFailure(401, "invalid_request_error: invalid API key"),
            HttpFailure(400, "invalid_request_error: incorrect API key"),
            HttpFailure(400, "INVALID_ARGUMENT: API key not valid. Please pass a valid API key. API_KEY_INVALID"),
            HttpFailure(403, "permission denied"),
            IOException("request failed", IOException("invalid_api_key")),
            IOException("API key is invalid"),
            IOException("API key is not valid"),
            IOException("API key has expired"),
            IOException("invalid x-api-key"),
        ).forEach { error ->
            assertEquals(error.message, ApiRequestRetryMode.ROTATE_KEY, ApiRequestRetryPolicy.mode(error))
        }
    }

    @Test
    fun unavailableModelsCanUseAnotherCandidateKey() {
        assertEquals(
            ApiRequestRetryMode.ROTATE_KEY,
            ApiRequestRetryPolicy.mode(HttpFailure(404, "invalid_request_error: model_not_found")),
        )
    }

    @Test
    fun quotaAndBalanceFailuresRetainRetries() {
        listOf(
            HttpFailure(400, "invalid_request_error: insufficient_quota"),
            HttpFailure(402, "Payment required"),
            HttpFailure(403, "invalid_request_error: 余额不足"),
            HttpFailure(429, "insufficient_quota"),
            IOException("invalid_request_error", IOException("insufficient quota")),
        ).forEach { error ->
            assertEquals(error.message, ApiRequestRetryMode.RETRY, ApiRequestRetryPolicy.mode(error))
        }
    }

    @Test
    fun transientHttpFailuresKeepRetriesDespiteGenericRequestErrors() {
        listOf("invalid_request_error", "INVALID_ARGUMENT").forEach { type ->
            listOf(408, 409, 425, 429, 500, 503).forEach { status ->
                assertEquals(
                    "$type HTTP $status",
                    ApiRequestRetryMode.RETRY,
                    ApiRequestRetryPolicy.mode(HttpFailure(status, type)),
                )
            }
        }
    }

    @Test
    fun transportAndResponseFailuresKeepRetries() {
        listOf(
            SocketTimeoutException("timeout"),
            FirstTokenTimeoutException(),
            IOException("invalid JSON in response body"),
        ).forEach { error ->
            assertEquals(ApiRequestRetryMode.RETRY, ApiRequestRetryPolicy.mode(error))
        }
    }

    @Test
    fun singleKeyTransientFailuresAllowExactlyFiveRetries() {
        val maxAttempts = ApiKeyRetryPlanner.maxHttpAttempts(1, poolMode = false)
        assertEquals(LlmRetryPolicy.MAX_RETRY_ATTEMPTS + 1, maxAttempts)
        listOf(
            ApiKeyErrorClass.QUOTA,
            ApiKeyErrorClass.RATE_LIMIT,
            ApiKeyErrorClass.SERVER,
            ApiKeyErrorClass.NETWORK,
            ApiKeyErrorClass.FIRST_TOKEN_TIMEOUT,
        ).forEach { errorClass ->
            for (attempt in 1..maxAttempts) {
                val decision = ApiKeyRetryPlanner.decide(
                    errorClass = errorClass,
                    sameKeyAttempts = attempt,
                    remainingCandidatesAfterExclude = 0,
                    httpAttemptsIncludingThis = attempt,
                    maxHttpAttempts = maxAttempts,
                )
                assertEquals("$errorClass attempt=$attempt", attempt == maxAttempts, decision.exhausted)
                assertFalse(decision.switchedKey)
                if (attempt == maxAttempts) assertEquals(0L, decision.delayMs)
            }
        }
    }

    @Test
    fun invalidKeysAndModelsStopAfterFirstFailureWithoutOtherCandidates() {
        listOf(ApiKeyErrorClass.AUTH, ApiKeyErrorClass.MODEL).forEach { errorClass ->
            assertTrue(ApiKeyRetryPlanner.switchesKeyImmediately(errorClass, 0))
            val decision = ApiKeyRetryPlanner.decide(
                errorClass = errorClass,
                sameKeyAttempts = 1,
                remainingCandidatesAfterExclude = 0,
                httpAttemptsIncludingThis = 1,
                maxHttpAttempts = 6,
            )
            assertTrue(decision.exhausted)
            assertFalse(decision.switchedKey)
            assertEquals(0L, decision.delayMs)
        }
    }

    @Test
    fun invalidKeysAndModelsRotateWithoutRepeatingTheFailedKey() {
        listOf(ApiKeyErrorClass.AUTH, ApiKeyErrorClass.MODEL).forEach { errorClass ->
            val decision = ApiKeyRetryPlanner.decide(
                errorClass = errorClass,
                sameKeyAttempts = 1,
                remainingCandidatesAfterExclude = 1,
                httpAttemptsIncludingThis = 1,
                maxHttpAttempts = 2,
            )
            assertFalse(decision.exhausted)
            assertTrue(decision.switchedKey)
            assertEquals(0L, decision.delayMs)
        }
    }

    @Test
    fun typedProtocolAndCertificateFailuresStopWithoutChangingKeys() {
        listOf(
            com.ai.assistance.operit.api.chat.error.RequestFailureCode.STREAM_JSON_INVALID,
            com.ai.assistance.operit.api.chat.error.RequestFailureCode.RESPONSE_JSON_INVALID,
            com.ai.assistance.operit.api.chat.error.RequestFailureCode.RESPONSE_SHAPE_INVALID,
            com.ai.assistance.operit.api.chat.error.RequestFailureCode.CERTIFICATE_EXPIRED,
        ).forEach { code ->
            val error = com.ai.assistance.operit.api.chat.error.RequestFailureException(code, "invalid_api_key timeout")
            assertEquals(ApiRequestRetryMode.STOP, ApiRequestRetryPolicy.mode(error))
        }
    }

    @Test
    fun providerFieldsDetermineBusinessClassWithoutScanningDiagnosticText() {
        val provider = com.ai.assistance.operit.api.chat.error.ProviderResponseException(
            "openai", "rate_limit_exceeded", "rate_limit_error", "certificate credit timeout",
        )
        assertEquals(ApiKeyErrorClass.RATE_LIMIT, ApiKeyFailureClassifier.classify(provider))
        assertEquals(ApiRequestRetryMode.RETRY, ApiRequestRetryPolicy.mode(provider))
        val blocked = com.ai.assistance.operit.api.chat.error.ProviderResponseException(
            "openai", "content_filter", "policy_error", "blocked",
        )
        assertEquals(ApiRequestRetryMode.STOP, ApiRequestRetryPolicy.mode(blocked))
    }

    @Test
    fun structuredServerAndTransportFailuresIgnoreUnrelatedDiagnosticKeywords() {
        val server = com.ai.assistance.operit.api.chat.error.ProviderResponseException(
            "openai", "server_error", "server_error", "unsupported parameter credit invalid_api_key",
        )
        assertEquals(ApiKeyErrorClass.SERVER, ApiKeyFailureClassifier.classify(server))
        assertEquals(ApiRequestRetryMode.RETRY, ApiRequestRetryPolicy.mode(server))
        val timeout = FirstTokenTimeoutException("invalid_api_key unsupported parameter")
        assertEquals(ApiKeyErrorClass.FIRST_TOKEN_TIMEOUT, ApiKeyFailureClassifier.classify(timeout))
        assertEquals(ApiRequestRetryMode.RETRY, ApiRequestRetryPolicy.mode(timeout))
    }

    @Test
    fun unsupportedAudioContentStopsEvenWhenGatewayReports503() {
        val body = """{"error":{"code":"no_healthy_account","message":"{\"code\":11101,\"msg\":\"Parse message failed: unsupported content type at index 0: input_audio\",\"retry_policy\":{\"retry\":false}}"}}"""
        assertEquals(ApiRequestRetryMode.STOP, ApiRequestRetryPolicy.mode(HttpFailure(503, body)))
        assertEquals(ApiRequestRetryMode.STOP, ApiRequestRetryPolicy.mode(IOException("请求失败", HttpFailure(503, body))))
        assertEquals(ApiRequestRetryMode.RETRY, ApiRequestRetryPolicy.mode(HttpFailure(503, "no_healthy_account")))
    }

    private class HttpFailure(
        override val statusCode: Int,
        message: String,
    ) : IOException(message), HttpStatusCodeException
}
