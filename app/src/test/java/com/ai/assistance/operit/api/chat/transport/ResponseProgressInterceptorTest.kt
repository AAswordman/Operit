package com.ai.assistance.operit.api.chat.transport

import com.ai.assistance.operit.api.chat.error.RequestFailureTrace
import java.net.SocketException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ResponseProgressInterceptorTest {
    private fun response(request: Request, body: ResponseBody) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()

    private fun intercept(request: Request, response: Response): Pair<Response, Interceptor.Chain> {
        val chain = mock<Interceptor.Chain>()
        whenever(chain.request()).thenReturn(request)
        whenever(chain.proceed(request)).thenReturn(response)
        return ResponseProgressInterceptor.intercept(chain) to chain
    }

    @Test
    fun countsDeliveredBodyBytesWithoutChangingPayloadOrMetadata() {
        val request = RequestFailureTrace.attach(Request.Builder().url("https://example.test").build())
        val content = "data: 中文🙂\n\n"
        val body = content.toResponseBody("text/event-stream; charset=utf-8".toMediaType())
        val original = response(request, body)
        val (tracked, chain) = intercept(request, original)
        assertEquals(body.contentType(), tracked.body!!.contentType())
        assertEquals(body.contentLength(), tracked.body!!.contentLength())
        tracked.use { assertEquals(content, it.body!!.string()) }
        assertEquals(content.toByteArray(Charsets.UTF_8).size.toLong(), request.tag(RequestFailureTrace::class.java)!!.responseBodyBytesRead)
        verify(chain).proceed(request)
    }

    @Test
    fun untracedRequestsKeepTheirOriginalResponse() {
        val request = Request.Builder().url("https://example.test").build()
        val original = response(request, "body".toResponseBody())
        val (tracked, chain) = intercept(request, original)
        assertSame(original, tracked)
        tracked.use { assertEquals("body", it.body!!.string()) }
        verify(chain).proceed(request)
    }

    @Test
    fun partialBodyCountsSurviveTheExactReadFailureAndSourceIsClosed() {
        val request = RequestFailureTrace.attach(Request.Builder().url("https://example.test").build())
        val interrupted = SocketException("Software caused connection abort")
        var read = false
        var closed = false
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (read) throw interrupted
                read = true
                sink.writeUtf8("abc")
                return 3L
            }
            override fun timeout() = Timeout.NONE
            override fun close() { closed = true }
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType() = null
            override fun contentLength() = -1L
            override fun source() = source
        }
        val (tracked, chain) = intercept(request, response(request, body))
        tracked.use {
            val buffer = Buffer()
            assertEquals(3L, it.body!!.source().read(buffer, 3L))
            assertEquals("abc", buffer.readUtf8())
            try {
                it.body!!.source().read(buffer, 1L)
                fail("Expected the original read failure")
            } catch (actual: SocketException) {
                assertSame(interrupted, actual)
            }
        }
        assertTrue(closed)
        assertEquals(3L, request.tag(RequestFailureTrace::class.java)!!.responseBodyBytesRead)
        verify(chain).proceed(request)
    }

    @Test
    fun drainingBufferedBytesDoesNotCountThemTwice() {
        val request = RequestFailureTrace.attach(Request.Builder().url("https://example.test").build())
        val trace = request.tag(RequestFailureTrace::class.java)!!
        val (tracked, _) = intercept(request, response(request, "abcdef".toResponseBody()))
        tracked.use {
            val source = it.body!!.source()
            assertEquals("ab", source.readUtf8(2L))
            assertEquals("cd", source.readUtf8(2L))
            assertEquals("ef", source.readUtf8(2L))
            assertTrue(source.exhausted())
        }
        assertEquals(6L, trace.responseBodyBytesRead)
    }
}
