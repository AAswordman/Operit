package com.ai.assistance.operit.api.chat.error

import com.ai.assistance.operit.api.chat.error.HttpStatusCodeException
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLProtocolException
import okhttp3.Request
import okhttp3.internal.http2.ErrorCode
import okhttp3.internal.http2.StreamResetException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestFailureTest {
    @Test
    fun codesAndTypesAreUniqueAndBroadLegacyCodesAreNotReused() {
        val codes = RequestFailureCode.values().toList()
        assertEquals(codes.size, codes.map { it.code }.toSet().size)
        assertEquals(codes.size, codes.map { it.type }.toSet().size)
        assertTrue(codes.none { it.code in setOf(5001, 5002, 5005, 5006) })
        assertEquals(5000, RequestFailureCode.HOST_RESOLUTION_FAILED.code)
        assertEquals(5003, RequestFailureCode.CONNECTION_ABORTED.code)
        assertEquals(5004, RequestFailureCode.FIRST_TOKEN_TIMEOUT.code)
    }

    @Test
    fun arbitraryTextDoesNotBecomeATransportDiagnosis() {
        listOf(
            "timeout", "certificate", "TLS error", "stream was reset: CANCEL",
            "Software caused connection abort", "unknown host", "connection refused",
        ).forEach { text ->
            val description = RequestFailure.describe(IOException(text))!!
            assertNull(text, description.reason)
            assertEquals(RequestFailureDomain.UNCLASSIFIED, description.domain)
            val error = JSONObject(description.json(1)).getJSONObject("error")
            assertTrue(error.isNull("code"))
            assertEquals(text, error.getString("detail"))
        }
    }

    @Test
    fun socketResetAndAbortHaveDifferentCodes() {
        assertEquals(5030, RequestFailure.describe(SocketException("Connection reset"))!!.reason!!.code)
        assertEquals(5003, RequestFailure.describe(SocketException("Software caused connection abort"))!!.reason!!.code)
        assertEquals(5000, RequestFailure.describe(UnknownHostException("api.example.test"))!!.reason!!.code)
    }

    @Test
    fun anApplicationFailureCannotBeReclassifiedByItsTextOrNestedSocket() {
        val failure = RequestFailureException(
            RequestFailureCode.STREAM_JSON_INVALID,
            "Software caused connection abort; timeout; certificate",
            SocketException("Connection reset"),
        )
        assertEquals(5080, RequestFailure.describe(failure)!!.reason!!.code)
    }

    @Test
    fun timeoutCodeUsesObservedPhaseRatherThanTheWordTimeout() {
        val expected = mapOf(
            RequestPhase.DNS to 5017,
            RequestPhase.CONNECT to 5010,
            RequestPhase.REQUEST_HEADERS to 5011,
            RequestPhase.REQUEST_BODY to 5016,
            RequestPhase.RESPONSE_HEADERS to 5012,
            RequestPhase.RESPONSE_BODY to 5013,
            RequestPhase.TLS to 5015,
            RequestPhase.UNKNOWN to 5014,
        )
        expected.forEach { (phase, code) ->
            val exception = contextual(SocketTimeoutException("Read timed out"), phase)
            val error = JSONObject(RequestFailure.describe(exception)!!.json(2)).getJSONObject("error")
            assertEquals(phase.toString(), code, error.getInt("code"))
            if (phase == RequestPhase.RESPONSE_BODY) assertEquals(200, error.getInt("httpStatus"))
            else assertFalse(error.has("httpStatus"))
            assertEquals(phase in setOf(RequestPhase.RESPONSE_HEADERS, RequestPhase.RESPONSE_BODY), error.getBoolean("requestBodySent"))
            assertEquals("local-request", error.getString("requestId"))
            assertEquals("upstream-request", error.getString("providerRequestId"))
        }
    }

    @Test
    fun eofBeforeHeadersAndDuringBodyAreNotMerged() {
        assertEquals(5071, RequestFailure.describe(contextual(EOFException("end"), RequestPhase.RESPONSE_HEADERS))!!.reason!!.code)
        assertEquals(5072, RequestFailure.describe(contextual(EOFException("end"), RequestPhase.RESPONSE_BODY))!!.reason!!.code)
        assertNull(RequestFailure.describe(EOFException("end"))!!.reason)
    }

    @Test
    fun certificateRootCauseIsMoreSpecificThanTheHandshakeWrapper() {
        val handshake = SSLHandshakeException("handshake failed")
        handshake.initCause(CertificateExpiredException("expired"))
        assertEquals(5041, RequestFailure.describe(handshake)!!.reason!!.code)
        assertEquals(5040, RequestFailure.describe(SSLPeerUnverifiedException("peer"))!!.reason!!.code)
        assertEquals(5046, RequestFailure.describe(SSLProtocolException("record"))!!.reason!!.code)
    }

    @Test
    fun supportedHttp2ResetReasonsRemainDistinct() {
        val expected = mapOf(0 to 5062, 1 to 5052, 2 to 5053, 3 to 5054, 4 to 5060, 5 to 5058, 6 to 5064,
            7 to 5051, 8 to 5050, 9 to 5061, 10 to 5059, 11 to 5055, 12 to 5056, 13 to 5057)
        ErrorCode.values().forEach { wire ->
            val description = RequestFailure.describe(StreamResetException(wire))!!
            assertEquals(wire.toString(), expected.getValue(wire.httpCode), description.reason!!.code)
        }
    }

    @Test
    fun watchdogCancellationIsNotReportedAsAnUnattributedHttp2Cancel() {
        val exception = FirstTokenTimeoutException("stream was reset: CANCEL", StreamResetException(ErrorCode.CANCEL))
        val error = JSONObject(RequestFailure.describe(exception)!!.json(3)).getJSONObject("error")
        assertEquals(5004, error.getInt("code"))
        assertEquals(600_000L, error.getLong("timeoutMs"))
        assertEquals("client_first_token_watchdog", error.getString("cancelledBy"))
    }

    @Test
    fun providerBusinessCodesArePreservedWithoutInventingALocalNetworkCode() {
        val exception = ProviderResponseErrors.exception("openai", JSONObject("""{"code":"content_filter","type":"policy_error"}"""), "timeout certificate Software caused connection abort")
        val description = RequestFailure.describe(exception)!!
        val error = JSONObject(description.json(1, retryable = false)).getJSONObject("error")
        assertNull(description.reason)
        assertEquals(RequestFailureDomain.PROVIDER, description.domain)
        assertTrue(error.isNull("code"))
        assertEquals("content_filter", error.getString("providerCode"))
        assertEquals("policy_error", error.getString("providerType"))
        assertFalse(error.getBoolean("retryable"))
    }

    @Test
    fun realHttpStatusRemainsAnHttpStatus() {
        val exception = object : IOException("HTTP 503: timeout"), HttpStatusCodeException {
            override val statusCode = 503
        }
        assertNull(RequestFailure.describe(IOException("wrapper", exception)))
    }

    @Test
    fun retryMetadataDoesNotOverwriteTheFailureIdentity() {
        val description = RequestFailure.describe(SocketException("Connection refused"))!!
        val error = JSONObject(description.json(2, RequestFailureDescription.RetryInfo(3, 2000))).getJSONObject("error")
        assertEquals(5020, error.getInt("code"))
        assertEquals(3, error.getJSONObject("retry").getInt("nextAttempt"))
        assertEquals(2000L, error.getJSONObject("retry").getLong("delayMs"))
    }

    @Test
    fun tracingASecondCallDoesNotModifyTheFirstCall() {
        val original = Request.Builder().url("https://api.example.test/v1/responses").build()
        val first = RequestFailureTrace.attach(original, "first")
        val second = RequestFailureTrace.attach(original, "second")
        val firstTrace = first.tag(RequestFailureTrace::class.java)!!
        firstTrace.phase = RequestPhase.RESPONSE_BODY
        firstTrace.httpStatus = 200
        assertEquals(RequestPhase.UNKNOWN, second.tag(RequestFailureTrace::class.java)!!.phase)
        assertNull(second.tag(RequestFailureTrace::class.java)!!.httpStatus)
        assertSame(first, RequestFailureTrace.attach(first, "replacement"))
    }

    @Test
    fun longDetailsStateThatTheyWereTruncated() {
        val description = RequestFailure.describe(RequestFailureException(RequestFailureCode.STREAM_JSON_INVALID, "x".repeat(3000)))!!
        val error = JSONObject(description.json(1)).getJSONObject("error")
        assertEquals(2000, error.getString("detail").length)
        assertTrue(error.getBoolean("detailTruncated"))
    }

    @Test
    fun standardSocketConditionsDoNotShareAConnectionFailureBucket() {
        mapOf(
            "Connection refused" to 5020, "Network is unreachable" to 5022,
            "Socket closed" to 5031, "Broken pipe" to 5032, "Socket is not connected" to 5033,
        ).forEach { (message, expected) ->
            assertEquals(expected, RequestFailure.describe(SocketException(message))!!.reason!!.code)
        }
    }

    @Test
    fun typedSystemAndCertificateFailuresKeepTheirSpecificConditions() {
        val cases = listOf(
            java.net.NoRouteToHostException("host") to 5021,
            java.net.PortUnreachableException("port") to 5023,
            java.security.cert.CertificateNotYetValidException("future") to 5042,
            java.security.cert.CertPathValidatorException("path") to 5043,
            java.security.cert.CertificateException("certificate") to 5044,
            SSLHandshakeException("handshake") to 5045,
            java.net.ProtocolException("status line") to 5070,
            okhttp3.internal.http2.ConnectionShutdownException() to 5063,
        )
        cases.forEach { (exception, expected) ->
            assertEquals(expected, RequestFailure.describe(exception)!!.reason!!.code)
        }
    }

    @Test
    fun errorEvidenceComesFromTheExplicitAttemptNotAnotherActiveRequest() {
        val request = RequestFailureTrace.attach(Request.Builder().url("https://api.example.test").build(), "first")
        val trace = request.tag(RequestFailureTrace::class.java)!!
        trace.phase = RequestPhase.RESPONSE_BODY
        trace.httpStatus = 200
        val next = RequestFailureTrace.attach(Request.Builder().url("https://api.example.test").build(), "second")
        next.tag(RequestFailureTrace::class.java)!!.phase = RequestPhase.DNS
        val first = RequestFailure.describe(RequestFailure.captureFromRequest(SocketException("Connection reset"), request))!!
        val second = RequestFailure.describe(RequestFailure.captureFromRequest(UnknownHostException("host"), next))!!
        val firstContext = requireNotNull(first.requestContext)
        val secondContext = requireNotNull(second.requestContext)
        assertEquals("first", firstContext.requestId)
        assertEquals(200, firstContext.httpStatus)
        assertEquals("second", secondContext.requestId)
        assertNull(secondContext.httpStatus)
        val beforeCall = IllegalStateException("request construction failed")
        assertSame(beforeCall, RequestFailure.capture(beforeCall, null))
    }

    @Test
    fun abortDescriptionCarriesReceivedProgressWithoutChangingItsIdentity() {
        val request = RequestFailureTrace.attach(Request.Builder().url("https://api.example.test").build(), "progress")
        val trace = request.tag(RequestFailureTrace::class.java)!!
        trace.phase = RequestPhase.RESPONSE_BODY
        trace.httpStatus = 200
        trace.recordResponseBytes(1234L)
        trace.streamEventsParsed = 7L
        trace.outputCharsParsed = 2048L
        trace.protocolCompleted = false
        val description = RequestFailure.describe(RequestFailure.captureFromRequest(
            SocketException("Software caused connection abort"), request))!!
        val error = JSONObject(description.json(2)).getJSONObject("error")
        assertEquals(5003, error.getInt("code"))
        assertEquals("transport", error.getString("domain"))
        assertEquals(1234L, error.getLong("responseBodyBytesRead"))
        assertEquals(7L, error.getLong("streamEventsParsed"))
        assertEquals(2048L, error.getLong("outputCharsParsed"))
        assertFalse(error.getBoolean("protocolCompleted"))
        assertFalse(error.getBoolean("clientCancelled"))
        // 异常持有不可变快照，后续进度不能改写已经记录的失败。
        trace.recordResponseBytes(1000L)
        assertEquals(1234L, description.requestContext!!.responseBodyBytesRead)
    }

    @Test
    fun unobservedStreamProgressIsNotInventedAsZeroOutput() {
        val request = RequestFailureTrace.attach(Request.Builder().url("https://api.example.test").build())
        val description = RequestFailure.describe(RequestFailure.captureFromRequest(
            SocketException("Software caused connection abort"), request))!!
        val error = JSONObject(description.json(1)).getJSONObject("error")
        assertEquals(0L, error.getLong("responseBodyBytesRead"))
        assertFalse(error.has("streamEventsParsed"))
        assertFalse(error.has("outputCharsParsed"))
        assertFalse(error.has("protocolCompleted"))
    }

    @Test
    fun actualCallCancellationIsIncludedWithoutSendingARequest() {
        val request = RequestFailureTrace.attach(Request.Builder().url("https://api.example.test").build())
        val call = okhttp3.OkHttpClient().newCall(request)
        call.cancel()
        val description = RequestFailure.describe(RequestFailure.capture(
            SocketException("Software caused connection abort"), call))!!
        assertTrue(description.requestContext!!.clientCancelled)
        assertEquals(5003, description.reason!!.code)
    }

    private fun contextual(cause: Exception, phase: RequestPhase) = RequestFailureContextException(
        cause, RequestFailureContext(
            "local-request", phase, 1234L, if (phase == RequestPhase.RESPONSE_BODY) 200 else null,
            phase in setOf(RequestPhase.RESPONSE_HEADERS, RequestPhase.RESPONSE_BODY), "upstream-request",
        ),
    )
}
