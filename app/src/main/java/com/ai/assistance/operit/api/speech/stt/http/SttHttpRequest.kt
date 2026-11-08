package com.ai.assistance.operit.api.speech.stt.http

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** 取消转写时同步取消网络请求，响应体在回调内读取并关闭。 */
internal suspend fun Call.awaitSttBody(errorMessage: String): String =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                continuation.resumeWithException(IOException(errorMessage, error))
            }

            override fun onResponse(call: Call, response: Response) {
                val body = try {
                    response.use {
                        val text = it.body?.string().orEmpty()
                        if (!it.isSuccessful) throw IOException("$errorMessage (HTTP ${it.code}): $text")
                        text
                    }
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                    return
                }
                continuation.resume(body)
            }
        })
    }
