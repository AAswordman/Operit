package com.ai.assistance.operit.api.chat.error

import com.ai.assistance.operit.api.chat.error.HttpStatusCodeException
import com.ai.assistance.operit.api.chat.request.LlmRetryPolicy
import java.util.Locale
import okhttp3.Call
import okhttp3.Request
import org.json.JSONObject

internal data class RequestFailureDescription(
    val reason: RequestFailureCode?,
    val cause: Throwable,
    val detail: String?,
    val requestContext: RequestFailureContext? = null,
    val providerError: ProviderResponseException? = null,
) {
    data class RetryInfo(val nextAttempt: Int, val delayMs: Long)

    val domain: RequestFailureDomain
        get() = if (providerError != null) RequestFailureDomain.PROVIDER
            else reason?.domain ?: RequestFailureDomain.UNCLASSIFIED

    fun json(attempt: Int, retry: RetryInfo? = null, retryable: Boolean = true): String {
        val error = JSONObject()
            .put("schemaVersion", 2)
            .put("code", reason?.code ?: JSONObject.NULL)
            .put("type", reason?.type ?: if (providerError != null) "provider_response_error" else "unclassified_exception")
            .put("message", reason?.explanation ?: cause.message.orEmpty())
            .put("domain", domain.name.lowercase(Locale.ROOT))
            .put("retryable", retryable && reason?.retryable != false)
            .put("attempt", attempt)
            .put("cause", cause.javaClass.simpleName)
            .put("exceptionClass", cause.javaClass.name)
        if (!detail.isNullOrBlank()) {
            error.put("detail", detail.take(2000))
            if (detail.length > 2000) error.put("detailTruncated", true)
        }
        if (reason == RequestFailureCode.FIRST_TOKEN_TIMEOUT) {
            error.put("timeoutMs", LlmRetryPolicy.FIRST_TOKEN_TIMEOUT_MS)
            error.put("cancelledBy", "client_first_token_watchdog")
        }
        requestContext?.let { info ->
            error.put("requestId", info.requestId)
                .put("phase", info.phase.name.lowercase(Locale.ROOT))
                .put("elapsedMs", info.elapsedMs)
                .put("requestBodySent", info.requestBodySent)
                .put("responseBodyBytesRead", info.responseBodyBytesRead)
                .put("clientCancelled", info.clientCancelled)
            info.streamEventsParsed?.let { error.put("streamEventsParsed", it) }
            info.outputCharsParsed?.let { error.put("outputCharsParsed", it) }
            info.protocolCompleted?.let { error.put("protocolCompleted", it) }
            info.httpStatus?.let { error.put("httpStatus", it) }
            info.providerRequestId?.let { error.put("providerRequestId", it) }
        }
        providerError?.let { provider ->
            error.put("provider", provider.provider)
            provider.providerCode?.let { error.put("providerCode", it) }
            provider.providerType?.let { error.put("providerType", it) }
        }
        retry?.let { error.put("retry", JSONObject().put("nextAttempt", it.nextAttempt).put("delayMs", it.delayMs)) }
        return JSONObject().put("error", error).toString()
    }
}

internal object RequestFailure {
    fun causeChain(exception: Throwable): List<Throwable> {
        val chain = mutableListOf<Throwable>()
        var current: Throwable? = exception
        while (chain.size < 32) {
            val item = current ?: break
            if (chain.any { it === item }) break
            chain += item
            current = item.cause
        }
        return chain
    }

    fun describe(exception: Throwable): RequestFailureDescription? {
        val chain = causeChain(exception)
        // 非成功 HTTP 状态继续保留真实 HTTP 错误，不分配本地编号。
        if (chain.any { it is HttpStatusCodeException }) return null
        val context = chain.filterIsInstance<RequestFailureContextException>().firstOrNull()?.requestContext
        chain.filterIsInstance<RequestFailureException>().firstOrNull()?.let { typed ->
            return RequestFailureDescription(typed.failureCode, typed, typed.detail ?: typed.cause?.message, context)
        }
        chain.filterIsInstance<ProviderResponseException>().firstOrNull()?.let { provider ->
            return RequestFailureDescription(null, provider, provider.message, context, provider)
        }
        NativeRequestFailure.resolve(chain, context?.phase ?: RequestPhase.UNKNOWN)?.let { (code, source) ->
            return RequestFailureDescription(code, source, source.message, context)
        }
        val source = chain.last()
        // 没有可靠分类证据时不制造 5006，也不宣称属于网络错误。
        return RequestFailureDescription(null, source, source.message, context)
    }

    fun capture(exception: Exception, call: Call?): Exception {
        val request = call?.request() ?: return exception
        if (call.isCanceled()) request.tag(RequestFailureTrace::class.java)?.clientCancelled = true
        return captureFromRequest(exception, request)
    }

    fun captureFromRequest(exception: Exception, request: Request): Exception {
        if (exception is kotlinx.coroutines.CancellationException ||
            exception is com.ai.assistance.operit.util.exceptions.UserCancellationException
        ) return exception
        val trace = request.tag(RequestFailureTrace::class.java) ?: return exception
        return RequestFailureContextException(exception, trace.snapshot())
    }
}
