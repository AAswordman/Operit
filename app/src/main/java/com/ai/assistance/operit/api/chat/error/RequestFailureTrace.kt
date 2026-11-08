package com.ai.assistance.operit.api.chat.error

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Request

internal enum class RequestPhase {
    UNKNOWN, DNS, CONNECT, TLS, REQUEST_HEADERS, REQUEST_BODY, RESPONSE_HEADERS, RESPONSE_BODY
}

internal data class RequestFailureContext(
    val requestId: String,
    val phase: RequestPhase,
    val elapsedMs: Long,
    val httpStatus: Int?,
    val requestBodySent: Boolean,
    val providerRequestId: String?,
    val responseBodyBytesRead: Long = 0L,
    val clientCancelled: Boolean = false,
    val streamEventsParsed: Long? = null,
    val outputCharsParsed: Long? = null,
    val protocolCompleted: Boolean? = null,
)

/** 每个 Call 独立持有阶段，避免并发聊天共用最后一次请求的状态。 */
internal class RequestFailureTrace(val requestId: String = "http_${UUID.randomUUID()}") {
    private val startedAtNs = System.nanoTime()
    @Volatile var phase = RequestPhase.UNKNOWN
    @Volatile var httpStatus: Int? = null
    @Volatile var requestBodySent = false
    @Volatile var providerRequestId: String? = null
    @Volatile var clientCancelled = false
    @Volatile var streamEventsParsed: Long? = null
    @Volatile var outputCharsParsed: Long? = null
    @Volatile var protocolCompleted: Boolean? = null
    private val responseBytes = AtomicLong()
    val responseBodyBytesRead: Long get() = responseBytes.get()

    fun recordResponseBytes(count: Long) {
        require(count >= 0L)
        responseBytes.addAndGet(count)
    }

    fun snapshot() = RequestFailureContext(
        requestId, phase, (System.nanoTime() - startedAtNs) / 1_000_000,
        httpStatus, requestBodySent, providerRequestId,
        responseBodyBytesRead, clientCancelled, streamEventsParsed, outputCharsParsed, protocolCompleted,
    )

    companion object {
        fun attach(request: Request, requestId: String? = null): Request {
            if (request.tag(RequestFailureTrace::class.java) != null) return request
            val trace = if (requestId == null) RequestFailureTrace() else RequestFailureTrace(requestId)
            return request.newBuilder().tag(RequestFailureTrace::class.java, trace).build()
        }
    }
}
