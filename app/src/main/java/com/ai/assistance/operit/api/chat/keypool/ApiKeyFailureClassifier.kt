package com.ai.assistance.operit.api.chat.keypool

import com.ai.assistance.operit.api.chat.llmprovider.HttpStatusCodeException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ApiKeyFailureClassifier {
    fun classify(exception: Throwable): ApiKeyErrorClass {
        var current: Throwable? = exception
        while (current != null) {
            when (current) {
                is FirstTokenTimeoutException -> return ApiKeyErrorClass.FIRST_TOKEN_TIMEOUT
                is SocketTimeoutException -> return ApiKeyErrorClass.NETWORK
                is UnknownHostException -> return ApiKeyErrorClass.NETWORK
                is HttpStatusCodeException -> return classifyHttp(current.statusCode, current.message)
            }
            current = current.cause
        }
        val message = exception.message.orEmpty()
        classifyByMessage(message)?.let { return it }
        return if (exception is IOException) ApiKeyErrorClass.NETWORK else ApiKeyErrorClass.NETWORK
    }

    fun retryAfterMs(exception: Throwable): Long? {
        var current: Throwable? = exception
        while (current != null) {
            val fromInterface = (current as? HttpStatusCodeException)?.retryAfterMs
            if (fromInterface != null && fromInterface > 0L) {
                return fromInterface
            }
            current = current.cause
        }
        return null
    }

    fun httpStatus(exception: Throwable): Int? {
        var current: Throwable? = exception
        while (current != null) {
            val status = (current as? HttpStatusCodeException)?.statusCode
            if (status != null) {
                return status
            }
            current = current.cause
        }
        return null
    }

    private fun classifyHttp(statusCode: Int, message: String?): ApiKeyErrorClass {
        classifyByMessage(message.orEmpty())?.let { fromMessage ->
            if (statusCode == 403 || statusCode == 400 || statusCode == 404) {
                return fromMessage
            }
        }
        return when (statusCode) {
            401 -> ApiKeyErrorClass.AUTH
            402 -> ApiKeyErrorClass.QUOTA
            403 -> classifyByMessage(message.orEmpty()) ?: ApiKeyErrorClass.AUTH
            404 -> classifyByMessage(message.orEmpty()) ?: ApiKeyErrorClass.MODEL
            429 -> ApiKeyErrorClass.RATE_LIMIT
            in 500..599 -> ApiKeyErrorClass.SERVER
            else -> classifyByMessage(message.orEmpty()) ?: ApiKeyErrorClass.NETWORK
        }
    }

    private fun classifyByMessage(message: String): ApiKeyErrorClass? {
        val text = message.lowercase()
        if (text.isBlank()) return null
        if (looksLikeQuota(text)) return ApiKeyErrorClass.QUOTA
        if (looksLikeRateLimit(text)) return ApiKeyErrorClass.RATE_LIMIT
        if (looksLikeAuth(text)) return ApiKeyErrorClass.AUTH
        if (looksLikeModel(text)) return ApiKeyErrorClass.MODEL
        return null
    }

    private fun looksLikeQuota(text: String): Boolean {
        return text.contains("insufficient_quota") ||
            text.contains("insufficient quota") ||
            text.contains("quota exceeded") ||
            text.contains("quota_exceeded") ||
            text.contains("billing") ||
            text.contains("balance") ||
            text.contains("credit") ||
            text.contains("payment required") ||
            text.contains("pre_consume_token") ||
            text.contains("额度") ||
            text.contains("余额") ||
            text.contains("欠费")
    }

    private fun looksLikeRateLimit(text: String): Boolean {
        return text.contains("rate limit") ||
            text.contains("rate_limit") ||
            text.contains("too many requests") ||
            text.contains("tpm") && text.contains("limit") ||
            text.contains("rpm") && text.contains("limit") ||
            text.contains("限流")
    }

    private fun looksLikeAuth(text: String): Boolean {
        return text.contains("invalid api key") ||
            text.contains("incorrect api key") ||
            text.contains("invalid_api_key") ||
            text.contains("authentication") ||
            text.contains("unauthorized") ||
            text.contains("permission denied") ||
            text.contains("not allowed")
    }

    private fun looksLikeModel(text: String): Boolean {
        return (text.contains("model") &&
            (text.contains("not found") ||
                text.contains("does not exist") ||
                text.contains("does not have access") ||
                text.contains("not supported") ||
                text.contains("unknown") ||
                text.contains("invalid"))) ||
            text.contains("model_not_found")
    }
}
