package com.ai.assistance.operit.api.chat.error

import android.system.ErrnoException
import android.system.OsConstants
import java.io.EOFException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.CertPathValidatorException
import java.util.Locale
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLProtocolException
import okhttp3.internal.http2.ConnectionShutdownException
import okhttp3.internal.http2.StreamResetException

internal object NativeRequestFailure {
    fun resolve(chain: List<Throwable>, phase: RequestPhase): Pair<RequestFailureCode, Throwable>? {
        fun first(type: Class<out Throwable>, code: RequestFailureCode): Pair<RequestFailureCode, Throwable>? =
            chain.firstOrNull { type.isInstance(it) }?.let { code to it }

        first(UnknownHostException::class.java, RequestFailureCode.HOST_RESOLUTION_FAILED)?.let { return it }
        chain.filterIsInstance<SocketTimeoutException>().firstOrNull()?.let { timeout ->
            val code = when (phase) {
                RequestPhase.DNS -> RequestFailureCode.DNS_TIMEOUT
                RequestPhase.CONNECT -> RequestFailureCode.CONNECT_TIMEOUT
                RequestPhase.TLS -> RequestFailureCode.TLS_TIMEOUT
                RequestPhase.REQUEST_HEADERS -> RequestFailureCode.REQUEST_HEADERS_WRITE_TIMEOUT
                RequestPhase.REQUEST_BODY -> RequestFailureCode.REQUEST_BODY_WRITE_TIMEOUT
                RequestPhase.RESPONSE_HEADERS -> RequestFailureCode.RESPONSE_HEADERS_TIMEOUT
                RequestPhase.RESPONSE_BODY -> RequestFailureCode.RESPONSE_READ_TIMEOUT
                else -> RequestFailureCode.SOCKET_TIMEOUT_UNLOCATED
            }
            return code to timeout
        }
        first(CertificateExpiredException::class.java, RequestFailureCode.CERTIFICATE_EXPIRED)?.let { return it }
        first(CertificateNotYetValidException::class.java, RequestFailureCode.CERTIFICATE_NOT_YET_VALID)?.let { return it }
        first(CertPathValidatorException::class.java, RequestFailureCode.CERTIFICATE_PATH_INVALID)?.let { return it }
        first(CertificateException::class.java, RequestFailureCode.CERTIFICATE_INVALID)?.let { return it }
        first(SSLPeerUnverifiedException::class.java, RequestFailureCode.TLS_PEER_UNVERIFIED)?.let { return it }
        first(SSLHandshakeException::class.java, RequestFailureCode.TLS_HANDSHAKE_FAILED)?.let { return it }
        first(SSLProtocolException::class.java, RequestFailureCode.TLS_PROTOCOL_ERROR)?.let { return it }
        chain.filterIsInstance<StreamResetException>().firstOrNull()?.let { reset ->
            val code = when (reset.errorCode.httpCode) {
                8 -> RequestFailureCode.HTTP2_CANCEL
                6 -> RequestFailureCode.HTTP2_FRAME_SIZE_ERROR
                7 -> RequestFailureCode.HTTP2_REFUSED_STREAM
                1 -> RequestFailureCode.HTTP2_PROTOCOL_ERROR
                2 -> RequestFailureCode.HTTP2_INTERNAL_ERROR
                3 -> RequestFailureCode.HTTP2_FLOW_CONTROL_ERROR
                11 -> RequestFailureCode.HTTP2_ENHANCE_YOUR_CALM
                12 -> RequestFailureCode.HTTP2_INADEQUATE_SECURITY
                13 -> RequestFailureCode.HTTP2_HTTP_1_1_REQUIRED
                5 -> RequestFailureCode.HTTP2_STREAM_CLOSED
                10 -> RequestFailureCode.HTTP2_CONNECT_ERROR
                4 -> RequestFailureCode.HTTP2_SETTINGS_TIMEOUT
                9 -> RequestFailureCode.HTTP2_COMPRESSION_ERROR
                0 -> RequestFailureCode.HTTP2_NO_ERROR_RESET
                else -> return null
            }
            return code to reset
        }
        first(ConnectionShutdownException::class.java, RequestFailureCode.HTTP2_CONNECTION_SHUTDOWN)?.let { return it }
        first(PortUnreachableException::class.java, RequestFailureCode.PORT_UNREACHABLE)?.let { return it }
        chain.filterIsInstance<ErrnoException>().firstOrNull()?.let { native ->
            val code = when (native.errno) {
                OsConstants.ECONNABORTED -> RequestFailureCode.CONNECTION_ABORTED
                OsConstants.ECONNRESET -> RequestFailureCode.CONNECTION_RESET
                OsConstants.ECONNREFUSED -> RequestFailureCode.CONNECTION_REFUSED
                OsConstants.EHOSTUNREACH -> RequestFailureCode.HOST_UNREACHABLE
                OsConstants.ENETUNREACH -> RequestFailureCode.NETWORK_UNREACHABLE
                OsConstants.EPIPE -> RequestFailureCode.BROKEN_PIPE
                OsConstants.ENOTCONN -> RequestFailureCode.SOCKET_NOT_CONNECTED
                else -> null
            }
            if (code != null) return code to native
        }
        first(NoRouteToHostException::class.java, RequestFailureCode.HOST_UNREACHABLE)?.let { return it }
        // 部分 Android SocketException 没有暴露 errno，只接受系统标准错误的完整文字。
        // 普通 IOException、服务端正文和任意包装消息均不参与这些匹配。
        chain.filterIsInstance<SocketException>().forEach { socket ->
            val code = when (socket.message?.trim()?.lowercase(Locale.ROOT)) {
                "software caused connection abort" -> RequestFailureCode.CONNECTION_ABORTED
                "connection reset", "connection reset by peer" -> RequestFailureCode.CONNECTION_RESET
                "connection refused" -> RequestFailureCode.CONNECTION_REFUSED
                "network is unreachable" -> RequestFailureCode.NETWORK_UNREACHABLE
                "no route to host" -> RequestFailureCode.HOST_UNREACHABLE
                "socket closed", "socket is closed" -> RequestFailureCode.SOCKET_CLOSED
                "broken pipe" -> RequestFailureCode.BROKEN_PIPE
                "socket is not connected" -> RequestFailureCode.SOCKET_NOT_CONNECTED
                else -> null
            }
            if (code != null) return code to socket
        }
        first(ProtocolException::class.java, RequestFailureCode.HTTP_PROTOCOL_ERROR)?.let { return it }
        chain.filterIsInstance<EOFException>().firstOrNull()?.let { eof ->
            return when (phase) {
                RequestPhase.RESPONSE_HEADERS -> RequestFailureCode.RESPONSE_HEADERS_EOF to eof
                RequestPhase.RESPONSE_BODY -> RequestFailureCode.RESPONSE_BODY_EOF to eof
                else -> null
            }
        }
        return null
    }
}
