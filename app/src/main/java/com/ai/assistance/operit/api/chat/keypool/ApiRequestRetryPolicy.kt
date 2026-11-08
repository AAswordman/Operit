package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.error.RequestFailure
import java.util.Locale

internal enum class ApiRequestRetryMode {
    STOP,
    ROTATE_KEY,
    RETRY,
}

/** 请求参数错误与密钥故障分别处理，避免重发同一份必然失败的请求。 */
internal object ApiRequestRetryPolicy {
    private val invalidParameterMarkers = listOf(
        "invalid_parameter",
        "unsupported_parameter",
        "context_length_exceeded",
        "invalid parameter",
        "invalid value for",
        "invalid type for",
        "unsupported value",
        "unsupported parameter",
        "unsupported content type",
        "unknown parameter",
        "unrecognized request argument",
        "missing required parameter",
        "missing required field",
        "invalid json payload",
        "invalid json in request",
        "malformed json in request",
        "unknown field",
        "maximum context length",
        "参数错误",
        "参数无效",
        "不支持的参数",
    )
    // 通用请求类型也会包装无效密钥或临时故障，不能直接当作参数错误。
    private val invalidRequestMarkers = listOf("invalid_request_error", "invalid_argument")

    fun mode(exception: Throwable): ApiRequestRetryMode {
        if (ApiKeyFailureClassifier.poolExhausted(exception) != null) return ApiRequestRetryMode.STOP
        val description = RequestFailure.describe(exception)
        description?.reason?.let { reason ->
            return if (reason.retryable) ApiRequestRetryMode.RETRY else ApiRequestRetryMode.STOP
        }
        val providerFields = description?.providerError?.let { listOfNotNull(it.providerCode, it.providerType) }
        if (providerFields?.any { it.lowercase() in setOf(
                "invalid_parameter", "unsupported_parameter", "context_length_exceeded",
                "content_policy_violation", "content_filter", "refusal",
            ) } == true
        ) return ApiRequestRetryMode.STOP
        val errorClass = ApiKeyFailureClassifier.classify(exception)
        // 结构化服务端错误只根据业务字段决策，诊断文案不再覆盖其类型。
        if (description?.providerError != null) {
            return when (errorClass) {
                ApiKeyErrorClass.AUTH, ApiKeyErrorClass.MODEL -> ApiRequestRetryMode.ROTATE_KEY
                ApiKeyErrorClass.QUOTA, ApiKeyErrorClass.RATE_LIMIT -> ApiRequestRetryMode.RETRY
                else -> if (providerFields?.any { it.lowercase() in invalidRequestMarkers } == true) {
                    ApiRequestRetryMode.STOP
                } else ApiRequestRetryMode.RETRY
            }
        }
        // 余额可在等待期间补充，保留原有额度重试策略。
        if (errorClass == ApiKeyErrorClass.QUOTA) return ApiRequestRetryMode.RETRY

        // 参数错误即使提及 model 或 not allowed，也不能当作密钥访问失败换钥。
        if (hasErrorMarker(exception, invalidParameterMarkers)) {
            return ApiRequestRetryMode.STOP
        }
        if (errorClass == ApiKeyErrorClass.RATE_LIMIT) return ApiRequestRetryMode.RETRY
        if (errorClass == ApiKeyErrorClass.AUTH || errorClass == ApiKeyErrorClass.MODEL) {
            return ApiRequestRetryMode.ROTATE_KEY
        }

        val status = ApiKeyFailureClassifier.httpStatus(exception)
        // 通用 invalid_request 类型不能覆盖超时、并发冲突、过早请求与服务端临时故障。
        if (status == 408 || status == 409 || status == 425 || status != null && status in 500..599) {
            return ApiRequestRetryMode.RETRY
        }
        if (hasErrorMarker(exception, invalidRequestMarkers) || status != null && status in 400..499) {
            return ApiRequestRetryMode.STOP
        }
        return ApiRequestRetryMode.RETRY
    }

    private fun hasErrorMarker(exception: Throwable, markers: List<String>): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            val message = current.message.orEmpty().lowercase(Locale.ROOT)
            if (markers.any(message::contains)) return true
            current = current.cause
        }
        return false
    }
}
