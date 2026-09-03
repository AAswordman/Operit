package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.R
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLHandshakeException

internal enum class OperitNetworkError(
    val appCode: Int,
    val classificationCode: String,
    val message: String,
) {
    DNS_RESOLUTION_FAILED(5000, "dns_resolution_failed", "DNS resolution failed"),
    CONNECTION_REFUSED(5001, "connection_refused", "Connection refused"),
    NETWORK_UNREACHABLE(5002, "network_unreachable", "Network unreachable"),
    CONNECTION_RESET(5003, "connection_reset", "Connection reset"),
    CONNECTION_CLOSED(5004, "connection_closed", "Connection closed"),
    TLS_HANDSHAKE_FAILED(5005, "tls_handshake_failed", "TLS handshake failed"),
    CONNECTION_TIMEOUT(5006, "connection_timeout", "Connection timed out");

    fun toErrorJson(): String =
        org.json.JSONObject()
            .put(
                "error",
                org.json.JSONObject()
                    .put("message", message)
                    .put("type", "network_error")
                    .put("param", org.json.JSONObject.NULL)
                    .put("code", appCode),
            )
            .toString()
}

internal data class ApiErrorClassification(
    val code: String,
    val recoverable: Boolean,
    val appCode: Int? = null,
    val httpStatusCode: Int? = null,
    val providerCode: String? = null,
    val retryAfterMs: Long? = null
)

internal object ApiErrorClassifier {
    private val statusPatterns = listOf(
        Regex("""(?i)\bHTTP(?:\s+status|\s+response)?\s*[:=]?\s*(\d{3})\b"""),
        Regex("""(?i)\bstatus(?:\s+code)?\s*[:=]\s*(\d{3})\b"""),
        Regex("""状态码\s*[:：]\s*(\d{3})""")
    )

    private val providerCodePattern = Regex(
        """(?i)[\"'](?:type|code|error_code|errorType)[\"']\s*[:=]\s*[\"']([^\"']+)[\"']"""
    )
    private val structuredErrorCodePattern = Regex(
        """(?is)[\"']error[\"']\s*:\s*\{.*?[\"']code[\"']\s*:\s*[\"']([^\"']+)[\"']"""
    )
    private val structuredErrorTypePattern = Regex(
        """(?is)[\"']error[\"']\s*:\s*\{.*?[\"']type[\"']\s*:\s*[\"']([^\"']+)[\"']"""
    )

    private val providerCodeTokenPattern = Regex(
        """(?i)\b(?:invalid_request_error|authentication_error|billing_error|permission_error|not_found_error|request_too_large|rate_limit_error|rate_limit_exceeded|quota_exceeded|api_error|service_unavailable|overloaded_error|invalidapikey|invalidparameter|model_not_found|insufficient_balance|invalid_request|parameter_unknown|content_filter|malformed_function_call|malformed_tool_call|unexpected_tool_call|too_many_tool_calls)\b"""
    )

    fun retryErrorText(context: Context, throwable: Throwable): String {
        val networkError = classifyNetworkError(throwable)
        return networkError?.let {
            context.getString(
                R.string.operit_network_error_format,
                it.appCode,
                it.toErrorJson(),
            )
        } ?: throwable.message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.provider_error_network_interrupted)
    }

    private fun classifyNetworkError(throwable: Throwable): OperitNetworkError? {
        val chain = exceptionChain(throwable)
        val text = chain.asSequence().mapNotNull { it.message }.joinToString("\n").lowercase(Locale.ROOT)
        return classifyNetworkError(chain, text, findStatusCode(chain, text))
    }

    fun classify(throwable: Throwable): ApiErrorClassification {
        val chain = exceptionChain(throwable)
        val rawText = chain
            .asSequence()
            .mapNotNull { it.message }
            .joinToString("\n")
        val text = rawText.lowercase(Locale.ROOT)
        val statusCode = findStatusCode(chain, text)
        val providerCode = extractProviderCode(rawText)
        val networkError = classifyNetworkError(chain, text, statusCode)
        val code = when {
            networkError != null -> networkError.classificationCode
            containsAny(text, "content_filter", "content policy", "safety", "prohibited_content", "blocked by") ->
                "content_policy"

            containsAny(text, "context_length_exceeded", "maximum context", "context window", "token limit") ->
                "context_window_exceeded"

            containsAny(text, "model_not_found", "model not found", "deploymentnotfound", "deployment not found") ->
                "model_not_found"

            containsAny(text, "invalid api key", "incorrect api key", "invalidapikey", "authenticationerror", "authentication_error", "unauthorized") ||
                statusCode == 401 -> "authentication_failed"

            containsAny(text, "insufficient balance", "insufficient_balance", "billing_error", "billing error") ||
                statusCode == 402 -> "insufficient_balance"

            containsAny(text, "insufficient_quota", "quota exceeded", "quota_exceeded", "daily quota", "current quota") ||
                statusCode == 429 && containsAny(text, "quota") ->
                "quota_exceeded"

            containsAny(text, "permission denied", "permission_denied", "permissionerror", "permission_error") ||
                statusCode == 403 -> "permission_denied"

            containsAny(text, "request too large", "request_too_large", "payload too large") ||
                statusCode == 413 -> "request_too_large"

            containsAny(text, "invalid_request_error", "invalid request", "invalid parameters", "invalidparameter", "invalid format", "parameter_unknown") ||
                statusCode == 400 || statusCode == 422 -> "invalid_request"

            containsAny(text, "rate_limit", "rate limit", "rate_limit_error", "throttling", "flowcontrol", "flow control", "too many requests") ||
                statusCode == 429 && !containsAny(text, "overload", "balance", "quota") -> "rate_limited"

            containsAny(text, "overloaded", "overload", "server overloaded", "capacity exceeded") ||
                statusCode == 529 -> "server_overloaded"

            containsAny(text, "service unavailable", "service_unavailable", "temporarily unavailable", "maintenance") ||
                statusCode == 503 -> "service_unavailable"

            containsAny(text, "server error", "internal server error") || statusCode == 500 ->
                "server_error"
            containsAny(text, "gateway error", "bad gateway") || statusCode == 502 ->
                "gateway_error"
            containsAny(text, "invalid endpoint", "endpoint not found") -> "invalid_endpoint"
            statusCode == 404 -> "invalid_endpoint"
            statusCode == 408 || statusCode == 504 -> "timeout"
            statusCode in 500..599 -> "server_error"
            containsAny(text, "api_error", "provider error", "provider_error") ||
                chain.any { it is IOException } -> "provider_error"
            else -> "unknown"
        }

        return ApiErrorClassification(
            code = code,
            recoverable = isRecoverable(code),
            appCode = networkError?.appCode,
            httpStatusCode = statusCode,
            providerCode = providerCode
        )
    }

        private fun classifyNetworkError(
        chain: List<Throwable>,
        text: String,
        statusCode: Int?
    ): OperitNetworkError? {
        if (statusCode != null) return null

        return when {
            chain.any { it is UnknownHostException } ||
                containsAny(text, "unknown host", "unable to resolve", "no address associated with hostname") ->
                OperitNetworkError.DNS_RESOLUTION_FAILED

            chain.any { it is NoRouteToHostException } ||
                containsAny(text, "network is unreachable", "no route to host") ->
                OperitNetworkError.NETWORK_UNREACHABLE

            chain.any { it is SSLHandshakeException } ||
                containsAny(
                    text,
                    "sslhandshakeexception",
                    "tls handshake",
                    "ssl handshake",
                    "certificate verify failed",
                    "certpathvalidatorexception",
                    "trust anchor for certification path not found"
                ) -> OperitNetworkError.TLS_HANDSHAKE_FAILED

            chain.any { it is SocketTimeoutException || it is TimeoutException } ||
                containsAny(text, "timed out", "timeout", "超时") ->
                OperitNetworkError.CONNECTION_TIMEOUT

            containsAny(text, "connection refused", "econnrefused") ||
                chain.any { it is ConnectException && containsAny(it.message.orEmpty().lowercase(Locale.ROOT), "refused") } ->
                OperitNetworkError.CONNECTION_REFUSED

            containsAny(text, "connection reset", "socket reset", "econnreset") ||
                chain.any { it is SocketException && containsAny(it.message.orEmpty().lowercase(Locale.ROOT), "reset") } ->
                OperitNetworkError.CONNECTION_RESET

            chain.any { it is EOFException } ||
                containsAny(text, "unexpected end of stream", "unexpected eof", "premature eof", "connection closed", "socket closed") ->
                OperitNetworkError.CONNECTION_CLOSED

            chain.any { it is ConnectException } ->
                OperitNetworkError.CONNECTION_REFUSED

            else -> null
        }
    }

    private fun exceptionChain(throwable: Throwable): List<Throwable> {
        val result = mutableListOf<Throwable>()
        val seen = mutableSetOf<Throwable>()
        var current: Throwable? = throwable
        while (current != null && seen.add(current)) {
            result += current
            current = current.cause
        }
        return result
    }

    private fun findStatusCode(chain: List<Throwable>, text: String): Int? {
        chain.asSequence()
            .mapNotNull { (it as? HttpStatusCodeException)?.statusCode }
            .firstOrNull()
            ?.let { return it }

        return statusPatterns.asSequence()
            .mapNotNull { pattern -> pattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .firstOrNull()
    }

    private fun extractProviderCode(text: String): String? {
        val structuredCode = structuredErrorCodePattern.find(text)?.groupValues?.getOrNull(1)?.trim()
        if (!structuredCode.isNullOrEmpty()) return structuredCode
        val structuredType = structuredErrorTypePattern.find(text)?.groupValues?.getOrNull(1)?.trim()
        if (!structuredType.isNullOrEmpty()) return structuredType
        return providerCodePattern.findAll(text)
            .map { it.groupValues[1].trim() }
            .lastOrNull { it.isNotEmpty() && !it.equals("error", ignoreCase = true) }
            ?: providerCodeTokenPattern.findAll(text)
                .map { it.value }
                .lastOrNull()
    }

    private fun containsAny(text: String, vararg terms: String): Boolean {
        return terms.any { text.contains(it) }
    }

    private fun isRecoverable(code: String): Boolean {
        return code in setOf(
            "rate_limited",
            "server_overloaded",
            "server_error",
            "gateway_error",
            "service_unavailable",
            "timeout",
            "dns_resolution_failed",
            "connection_refused",
            "network_unreachable",
            "connection_reset",
            "connection_closed",
            "tls_handshake_failed",
            "connection_timeout"
        )
    }
}
