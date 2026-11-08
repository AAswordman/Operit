package com.ai.assistance.operit.api.speech.tts

/**
 * TTS服务相关的自定义异常
 * @param message 异常信息
 * @param httpStatusCode HTTP状态码 (如果适用)
 * @param errorBody 错误响应体 (如果适用)
 * @param cause 原始异常
 */
class TtsException(
    message: String,
    val httpStatusCode: Int? = null,
    val errorBody: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)
