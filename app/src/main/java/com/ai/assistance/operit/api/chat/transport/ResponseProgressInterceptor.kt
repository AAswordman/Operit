package com.ai.assistance.operit.api.chat.transport

import com.ai.assistance.operit.api.chat.error.RequestFailureTrace
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

/** 只记录交付给应用的响应体字节数，不缓存正文，也不增加任何网络请求。 */
internal object ResponseProgressInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val trace = response.request.tag(RequestFailureTrace::class.java) ?: return response
        val body = response.body ?: return response
        val source = object : ForwardingSource(body.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val count = super.read(sink, byteCount)
                if (count > 0L) trace.recordResponseBytes(count)
                return count
            }
        }.buffer()
        val tracked = object : ResponseBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun source() = source
        }
        return response.newBuilder().body(tracked).build()
    }
}
