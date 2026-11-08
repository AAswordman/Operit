package com.ai.assistance.operit.api.speech.tts.prefetch

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal data class TtsHttpResponse(
    val code: Int,
    val contentType: MediaType?,
    val bytes: ByteArray,
) {
    val isSuccessful: Boolean get() = code in 200..299
    fun text(): String = bytes.toString(contentType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
}

/** 取消预取时连同下载中的请求一起取消，响应始终在回调内关闭。 */
internal suspend fun OkHttpClient.executeTtsRequest(request: Request): TtsHttpResponse =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        TtsHttpResponse(it.code, it.body?.contentType(), it.body?.bytes() ?: ByteArray(0))
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
