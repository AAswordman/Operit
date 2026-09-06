package com.ai.assistance.operit.api.chat.llmprovider

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFetchErrorHelperTest {

    @Test
    fun extractsErrorMessageFromStandardOpenAiJson() {
        val json = """{"error":{"message":"Incorrect API key provided: sk-test***","type":"invalid_request_error"}}"""
        val message = ModelFetchErrorHelper.extractErrorMessage(json)
        assertEquals("Incorrect API key provided: sk-test***", message)
    }

    @Test
    fun extractsErrorMessageFromStringErrorField() {
        val json = """{"error":"Unauthorized access"}"""
        val message = ModelFetchErrorHelper.extractErrorMessage(json)
        assertEquals("Unauthorized access", message)
    }

    @Test
    fun extractsErrorMessageFromRootMessageField() {
        val json = """{"message":"Resource not found"}"""
        val message = ModelFetchErrorHelper.extractErrorMessage(json)
        assertEquals("Resource not found", message)
    }

    @Test
    fun returnsNullForInvalidOrEmptyJson() {
        assertNull(ModelFetchErrorHelper.extractErrorMessage(""))
        assertNull(ModelFetchErrorHelper.extractErrorMessage("<html>502 Bad Gateway</html>"))
        assertNull(ModelFetchErrorHelper.extractErrorMessage("{}"))
    }

    @Test
    fun detectsTimeoutExceptions() {
        assertTrue(ModelFetchErrorHelper.isTimeoutException(SocketTimeoutException("Read timed out")))
        assertTrue(ModelFetchErrorHelper.isTimeoutException(IOException("connection timeout", SocketTimeoutException())))
        assertTrue(ModelFetchErrorHelper.isTimeoutException(Exception("Request timed out")))
        assertFalse(ModelFetchErrorHelper.isTimeoutException(IOException("Connection refused")))
    }

    @Test
    fun detectsDnsResolutionExceptions() {
        assertTrue(ModelFetchErrorHelper.isDnsResolutionException(UnknownHostException("api.deepseek")))
        assertTrue(ModelFetchErrorHelper.isDnsResolutionException(IOException("Unable to resolve host \"api.deepseek\": No address associated with hostname")))
        assertFalse(ModelFetchErrorHelper.isDnsResolutionException(ConnectException("Connection refused")))
        assertFalse(ModelFetchErrorHelper.isDnsResolutionException(SocketTimeoutException("timeout")))
    }

    @Test
    fun detectsNetworkConnectionExceptions() {
        assertTrue(ModelFetchErrorHelper.isNetworkConnectionException(ConnectException("Connection refused")))
        assertTrue(ModelFetchErrorHelper.isNetworkConnectionException(NoRouteToHostException("No route to host")))
        assertTrue(ModelFetchErrorHelper.isNetworkConnectionException(IOException("failed to connect to /192.168.1.1 (port 80)")))
        assertFalse(ModelFetchErrorHelper.isNetworkConnectionException(SocketTimeoutException("timeout")))
    }

    @Test
    fun detectsSslExceptions() {
        assertTrue(ModelFetchErrorHelper.isSslException(SSLException("Handshake failed")))
        assertTrue(ModelFetchErrorHelper.isSslException(IOException("SSL cert validation error")))
        assertFalse(ModelFetchErrorHelper.isSslException(ConnectException("Connection refused")))
    }

    @Test
    fun extractsHttpStatusCodeFromCustomExceptionAndMessage() {
        val httpException = ModelFetchHttpException(401, "{\"error\":{\"message\":\"invalid key\"}}", "API请求失败: 401, 错误: ...")
        assertEquals(401, ModelFetchErrorHelper.extractHttpStatusCode(httpException))

        val wrapped = IOException("wrapped", ModelFetchHttpException(404, "not found", "not found"))
        assertEquals(404, ModelFetchErrorHelper.extractHttpStatusCode(wrapped))

        val stringException = IOException("API请求失败: 403, 错误: Forbidden")
        assertEquals(403, ModelFetchErrorHelper.extractHttpStatusCode(stringException))

        val httpStatusException = Exception("HTTP 502 Bad Gateway")
        assertEquals(502, ModelFetchErrorHelper.extractHttpStatusCode(httpStatusException))

        assertNull(ModelFetchErrorHelper.extractHttpStatusCode(IOException("Network is unreachable")))
    }
}