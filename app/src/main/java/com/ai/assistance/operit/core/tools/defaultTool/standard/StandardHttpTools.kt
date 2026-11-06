package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.HttpStreamEventData
import com.ai.assistance.operit.core.tools.HttpResponseData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import java.io.File
import java.io.StringReader
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject

/** HTTP网络请求工具 提供直接访问网页和发送HTTP请求的能力 */
class StandardHttpTools(private val context: Context) {
    private data class PreparedHttpRequest(
            val toolName: String,
            val url: String,
            val method: String,
            val bodyParam: String,
            val bodyType: String,
            val useCookies: Boolean,
            val client: OkHttpClient,
            val request: Request
    )

    companion object {
        private const val TAG = "HttpTools"
        private const val USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

        internal fun clearSharedCookies() {
            cookieStore.clear()
        }
    }

    // 自定义CookieJar实现
    private val cookieJar =
            object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookieStore[url.host] = cookies
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookieStore[url.host] ?: emptyList()
                }
            }

    // 默认OkHttpClient实例，配置基本超时和Cookie支持
    private val defaultClient =
            OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .cookieJar(cookieJar)
                    .build()

    // 创建可配置的OkHttpClient
    private fun buildConfigurableClient(
            connectTimeout: Long = 15,
            readTimeout: Long = 20,
            writeTimeout: Long = 15,
            followRedirects: Boolean = true,
            followSslRedirects: Boolean = true,
            useCookies: Boolean = true,
            proxyHost: String? = null,
            proxyPort: Int = 0,
            ignoreSsl: Boolean = false,
            callTimeout: Long? = null
    ): OkHttpClient {
        val builder =
                OkHttpClient.Builder()
                        .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                        .readTimeout(readTimeout, TimeUnit.SECONDS)
                        .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                        .followRedirects(followRedirects)
                        .followSslRedirects(followSslRedirects)
        if (callTimeout != null) {
            builder.callTimeout(callTimeout, TimeUnit.SECONDS)
        }

        // 配置Cookie支持
        if (useCookies) {
            builder.cookieJar(cookieJar)
        }

        // 配置代理
        if (!proxyHost.isNullOrBlank() && proxyPort > 0) {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort))
            builder.proxy(proxy)
        }

        if (ignoreSsl) {
            applyUnsafeSsl(builder)
        }

        return builder.build()
    }

    private fun applyUnsafeSsl(builder: OkHttpClient.Builder) {
        val trustManager =
                object : X509TrustManager {
                    override fun checkClientTrusted(
                            chain: Array<out X509Certificate>?,
                            authType: String?
                    ) {}

                    override fun checkServerTrusted(
                            chain: Array<out X509Certificate>?,
                            authType: String?
                    ) {}

                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

        builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        builder.hostnameVerifier { _, _ -> true }
    }

    /** 读取响应体内容，处理编码问题 */
    private fun readResponseBody(responseBody: ResponseBody, contentType: String): String {
        return try {
            AppLogger.d(TAG, "使用OkHttp内置string()方法读取响应内容")
            responseBody.string()
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取响应体时发生错误", e)
            ""
        }
    }

    /** 读取响应体为Base64 */
    private fun readResponseBodyAsBase64(responseBody: ResponseBody): String {
        return try {
            val bytes = responseBody.bytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取响应体为Base64时发生错误", e)
            ""
        }
    }

    private fun errorResult(toolName: String, message: String): ToolResult {
        return ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = message
        )
    }

    private fun buildHttpResponseData(
            url: String,
            response: okhttp3.Response,
            content: String,
            contentBase64: String,
            size: Int
    ): HttpResponseData {
        val contentType = response.header("Content-Type") ?: ""
        val responseHeadersMap =
                response.headers.names().associateWith { name ->
                    response.headers.get(name) ?: ""
                }
        val responseCookieUrl =
                url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL: $url")
        val responseCookies = cookieJar.loadForRequest(responseCookieUrl)
        val cookiesMap = responseCookies.associate { it.name to it.value }
        return HttpResponseData(
                url = url,
                statusCode = response.code,
                statusMessage = response.message,
                headers = responseHeadersMap,
                contentType = contentType,
                content = content,
                contentBase64 = contentBase64,
                size = size,
                cookies = cookiesMap
        )
    }

    private fun prepareHttpRequest(tool: AITool): PreparedHttpRequest {
        val url = tool.parameters.find { it.name == "url" }?.value ?: ""
        val methodParam = tool.parameters.find { it.name == "method" }?.value
        val method = methodParam?.uppercase() ?: "GET"
        val headersParam = tool.parameters.find { it.name == "headers" }?.value ?: "{}"
        val bodyParam = tool.parameters.find { it.name == "body" }?.value ?: ""
        val bodyTypeParam = tool.parameters.find { it.name == "body_type" }?.value
        val bodyType = bodyTypeParam?.lowercase() ?: "json"

        val timeouts = resolveHttpTimeouts(tool)
        val followRedirectsParam = tool.parameters.find { it.name == "follow_redirects" }?.value
        val useCookiesParam = tool.parameters.find { it.name == "use_cookies" }?.value
        val proxyHostParam = tool.parameters.find { it.name == "proxy_host" }?.value
        val proxyPortParam = tool.parameters.find { it.name == "proxy_port" }?.value
        val customCookiesParam = tool.parameters.find { it.name == "custom_cookies" }?.value
        val ignoreSslParam = tool.parameters.find { it.name == "ignore_ssl" }?.value

        require(url.isNotBlank()) { "URL parameter cannot be empty" }
        require(isValidUrl(url)) { "Invalid URL format: $url" }
        require(method in listOf("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "PATCH", "TRACE")) {
            "Unsupported HTTP method: $method"
        }

        val headers = parseHeaders(headersParam)
        val customCookies =
                if (!customCookiesParam.isNullOrBlank()) {
                    parseCookies(customCookiesParam, url)
                } else null

        val useCookies = useCookiesParam?.lowercase() != "false"
        val client =
                buildConfigurableClient(
                        connectTimeout = timeouts.connectSeconds,
                        readTimeout = timeouts.readSeconds,
                        writeTimeout = timeouts.writeSeconds,
                        callTimeout = timeouts.overallSeconds,
                        followRedirects = followRedirectsParam?.lowercase() != "false",
                        followSslRedirects = followRedirectsParam?.lowercase() != "false",
                        useCookies = useCookies,
                        proxyHost = proxyHostParam,
                        proxyPort = proxyPortParam?.toIntOrNull() ?: 0,
                        ignoreSsl = ignoreSslParam?.lowercase() == "true"
                )

        if (customCookies != null) {
            val requestCookieUrl = url.toHttpUrlOrNull()
            if (requestCookieUrl != null && useCookies) {
                cookieStore[requestCookieUrl.host] = customCookies
            }
        }

        val requestBuilder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }

        if (method != "GET" && method != "HEAD" && bodyParam.isNotBlank()) {
            val requestBody =
                    when (bodyType) {
                        "json" -> {
                            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                            bodyParam.toRequestBody(mediaType)
                        }
                        "form" -> {
                            val formBodyBuilder = FormBody.Builder()
                            val jsonObj = JSONObject(bodyParam)
                            val keys = jsonObj.keys()
                            while (keys.hasNext()) {
                                val key = keys.next()
                                formBodyBuilder.add(key, jsonObj.getString(key))
                            }
                            formBodyBuilder.build()
                        }
                        "text" -> {
                            val mediaType = "text/plain; charset=utf-8".toMediaTypeOrNull()
                            bodyParam.toRequestBody(mediaType)
                        }
                        "xml" -> {
                            val mediaType = "application/xml; charset=utf-8".toMediaTypeOrNull()
                            bodyParam.toRequestBody(mediaType)
                        }
                        "multipart" -> {
                            throw IllegalArgumentException(
                                    "multipart request body type requires dedicated multipart_request tool"
                            )
                        }
                        else -> {
                            throw IllegalArgumentException("Unsupported request body type: $bodyType")
                        }
                    }

            requestBuilder.method(method, requestBody)
        } else {
            requestBuilder.method(method, null)
        }

        return PreparedHttpRequest(
                toolName = tool.name,
                url = url,
                method = method,
                bodyParam = bodyParam,
                bodyType = bodyType,
                useCookies = useCookies,
                client = client,
                request = requestBuilder.build()
        )
    }

    /** 发送HTTP请求 支持GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS方法，并可自定义请求头、请求体、超时、代理和Cookie设置 */
    suspend fun httpRequest(tool: AITool): ToolResult {
        return try {
            val spec = prepareHttpRequest(tool)
            val response = spec.client.newCall(spec.request).execute()
            val responseBody = response.body ?: return errorResult(tool.name, "Response body is empty")
            val bodyBytes = responseBody.bytes()
            val contentType = response.header("Content-Type") ?: ""
            val responseBodyString =
                    try {
                        val charset = response.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                        String(bodyBytes, charset)
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Failed to decode response body as text for content-type $contentType", e)
                        "[Binary Content, decoding failed]"
                    }
            val httpResponseData =
                    buildHttpResponseData(
                            url = spec.url,
                            response = response,
                            content = responseBodyString,
                            contentBase64 = android.util.Base64.encodeToString(bodyBytes, android.util.Base64.NO_WRAP),
                            size = bodyBytes.size
                    )
            ToolResult(toolName = tool.name, success = true, result = httpResponseData, error = "")
        } catch (e: Exception) {
            errorResult(tool.name, "Error executing HTTP request: ${e.message}")
        }
    }

    suspend fun httpRequestStream(tool: AITool): Flow<ToolResult> = flow {
        try {
            val spec = prepareHttpRequest(tool)
            val response = spec.client.newCall(spec.request).execute()
            val responseBody = response.body
                    ?: run {
                        emit(errorResult(tool.name, "Response body is empty"))
                        return@flow
                    }

            val responseHeadersMap =
                    response.headers.names().associateWith { name ->
                        response.headers.get(name) ?: ""
                    }
            val contentType = response.header("Content-Type") ?: ""
            emit(
                    ToolResult(
                            toolName = tool.name,
                            success = true,
                            result =
                                    HttpStreamEventData(
                                            type = "response_started",
                                            url = spec.url,
                                            statusCode = response.code,
                                            statusMessage = response.message,
                                            headers = responseHeadersMap,
                                            contentType = contentType,
                                            receivedBytes = 0
                                    )
                    )
            )

            val charset = response.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            val reader = responseBody.charStream()
            val buffer = CharArray(1024)
            val aggregatedText = StringBuilder()
            var chunkIndex = 0

            while (true) {
                val readCount = reader.read(buffer)
                if (readCount <= 0) {
                    break
                }
                val chunk = String(buffer, 0, readCount)
                aggregatedText.append(chunk)
                emit(
                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        HttpStreamEventData(
                                                type = "chunk",
                                                url = spec.url,
                                                contentType = contentType,
                                                chunk = chunk,
                                                chunkIndex = chunkIndex,
                                                receivedBytes = aggregatedText.toString().toByteArray(charset).size.toLong()
                                        )
                        )
                )
                chunkIndex += 1
            }
            reader.close()

            val finalText = aggregatedText.toString()
            val finalBytes = finalText.toByteArray(charset)
            val finalResponseData =
                    buildHttpResponseData(
                            url = spec.url,
                            response = response,
                            content = finalText,
                            contentBase64 = android.util.Base64.encodeToString(finalBytes, android.util.Base64.NO_WRAP),
                            size = finalBytes.size
                    )
            emit(ToolResult(toolName = tool.name, success = true, result = finalResponseData, error = ""))
        } catch (e: Exception) {
            emit(errorResult(tool.name, "Error executing streaming HTTP request: ${e.message}"))
        }
    }

    /** 验证URL格式 */
    private fun isValidUrl(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            val protocol = url.protocol.lowercase()
            protocol == "http" || protocol == "https"
        } catch (e: Exception) {
            false
        }
    }

    /** 解析请求头 */
    private fun parseHeaders(headersJson: String): Map<String, String> {
        return try {
            val result = mutableMapOf<String, String>()
            val jsonObj = JSONObject(headersJson)
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = jsonObj.getString(key)
            }
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析请求头时出错", e)
            emptyMap()
        }
    }

    /** 解析Cookie字符串 */
    private fun parseCookies(cookiesJson: String, urlString: String): List<Cookie>? {
        return try {
            val cookieList = mutableListOf<Cookie>()
            val jsonObj = JSONObject(cookiesJson)
            val keys = jsonObj.keys()

            val requestCookieUrl =
                    urlString.toHttpUrlOrNull()
                            ?: throw IllegalArgumentException("Invalid URL: $urlString")

            while (keys.hasNext()) {
                val name = keys.next()
                val value = jsonObj.get(name)

                // 支持简单的name=value格式
                if (value is String) {
                    val cookie =
                            Cookie.Builder()
                                    .name(name)
                                    .value(value)
                                    .domain(requestCookieUrl.host)
                                    .build()
                    cookieList.add(cookie)
                }
                // 支持复杂的Cookie对象格式
                else if (value is JSONObject) {
                    val cookieBuilder =
                            Cookie.Builder().name(name).value(value.optString("value", ""))

                    // 如果有设置domain，使用指定值，否则使用URL的host
                    val domain = value.optString("domain", "")
                    if (domain.isNotBlank()) {
                        cookieBuilder.domain(domain)
                    } else {
                        cookieBuilder.domain(requestCookieUrl.host)
                    }

                    // 其他可选属性
                    val path = value.optString("path", "")
                    if (path.isNotBlank()) cookieBuilder.path(path)

                    val expiresAt = value.optLong("expiresAt", 0)
                    if (expiresAt > 0) cookieBuilder.expiresAt(expiresAt)

                    if (value.optBoolean("secure", false)) cookieBuilder.secure()
                    if (value.optBoolean("httpOnly", false)) cookieBuilder.httpOnly()

                    cookieList.add(cookieBuilder.build())
                }
            }

            cookieList
        } catch (e: Exception) {
            AppLogger.e(TAG, "解析Cookie时出错", e)
            null
        }
    }

    /** 管理Cookie的方法 */
    suspend fun manageCookies(tool: AITool): ToolResult {
        val action = tool.parameters.find { it.name == "action" }?.value?.lowercase() ?: "get"
        val domain = tool.parameters.find { it.name == "domain" }?.value ?: ""
        val cookiesJson = tool.parameters.find { it.name == "cookies" }?.value ?: "{}"

        return try {
            when (action) {
                "get" -> {
                    // 获取指定域名的Cookie
                    val cookies =
                            if (domain.isNotBlank()) {
                                cookieStore[domain] ?: emptyList()
                            } else {
                                // 获取所有域名的Cookie
                                cookieStore.values.flatten()
                            }

                    // 转换为可读格式
                    val cookiesMap =
                            cookies.associate {
                                it.name to
                                        mapOf(
                                                "value" to it.value,
                                                "domain" to it.domain,
                                                "path" to it.path,
                                                "expires" to
                                                        (if (it.expiresAt != 0L) it.expiresAt
                                                        else null),
                                                "secure" to it.secure,
                                                "httpOnly" to it.httpOnly
                                        )
                            }

                    val jsonResult = JSONObject(cookiesMap as Map<*, *>).toString(2)
                    ToolResult(
                            toolName = tool.name,
                            success = true,
                            result = StringResultData("Current cookie status:\n$jsonResult")
                    )
                }
                "set" -> {
                    if (domain.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "setCookie requires domain parameter"
                        )
                    }

                    // 解析Cookie数据
                    val cookies = parseCookies(cookiesJson, "https://$domain")
                    if (cookies != null) {
                        cookieStore[domain] = cookies
                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = StringResultData("Successfully set ${cookies.size} cookies to domain $domain")
                        )
                    } else {
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Cookie format error, cannot parse"
                        )
                    }
                }
                "clear" -> {
                    if (domain.isBlank()) {
                        // 清除所有Cookie
                        CookiePrivacyManager.clearAllCookies()
                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = StringResultData("Cleared all cookies")
                        )
                    } else {
                        // 清除指定域名的Cookie
                        cookieStore.remove(domain)
                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = StringResultData("Cleared cookies for domain $domain")
                        )
                    }
                }
                else -> {
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Unsupported action: $action, supported actions are: get, set, clear"
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "管理Cookie时出错", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error managing cookies: ${e.message}"
            )
        }
    }

    /** 发送包含文件的多部分表单请求 */
    suspend fun multipartRequest(tool: AITool): ToolResult {
        val url = tool.parameters.find { it.name == "url" }?.value ?: ""
        val methodParam = tool.parameters.find { it.name == "method" }?.value
        val method = methodParam?.uppercase() ?: "POST"
        val headersParam = tool.parameters.find { it.name == "headers" }?.value ?: "{}"
        val formDataParam = tool.parameters.find { it.name == "form_data" }?.value ?: "{}"
        val filesParam = tool.parameters.find { it.name == "files" }?.value ?: "[]"

        val timeouts = resolveHttpTimeouts(tool)
        val followRedirectsParam = tool.parameters.find { it.name == "follow_redirects" }?.value
        val useCookiesParam = tool.parameters.find { it.name == "use_cookies" }?.value
        val proxyHostParam = tool.parameters.find { it.name == "proxy_host" }?.value
        val proxyPortParam = tool.parameters.find { it.name == "proxy_port" }?.value
        val customCookiesParam = tool.parameters.find { it.name == "custom_cookies" }?.value
        val ignoreSslParam = tool.parameters.find { it.name == "ignore_ssl" }?.value

        if (url.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "URL parameter cannot be empty"
            )
        }

        // 验证URL格式
        if (!isValidUrl(url)) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid URL format: $url"
            )
        }

        // 验证HTTP方法 (多部分表单主要用于POST和PUT)
        if (method !in listOf("POST", "PUT")) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Multipart form requests only support POST and PUT methods, not supported: $method"
            )
        }

        return try {
            // 解析请求头
            val headers = parseHeaders(headersParam)

            // 解析自定义Cookie
            val customCookies =
                    if (!customCookiesParam.isNullOrBlank()) {
                        parseCookies(customCookiesParam, url)
                    } else null

            // 配置客户端
            val client =
                    buildConfigurableClient(
                            connectTimeout = timeouts.connectSeconds,
                            readTimeout = timeouts.readSeconds,
                            writeTimeout = timeouts.writeSeconds,
                            callTimeout = timeouts.overallSeconds,
                            followRedirects = followRedirectsParam?.lowercase() != "false",
                            followSslRedirects = followRedirectsParam?.lowercase() != "false",
                            useCookies = useCookiesParam?.lowercase() != "false",
                            proxyHost = proxyHostParam,
                            proxyPort = proxyPortParam?.toIntOrNull() ?: 0,
                            ignoreSsl = ignoreSslParam?.lowercase() == "true"
                    )

            // 如果有自定义Cookie，添加到cookieStore
            if (customCookies != null) {
                val requestCookieUrl = url.toHttpUrlOrNull()
                if (requestCookieUrl != null && useCookiesParam?.lowercase() != "false") {
                    cookieStore[requestCookieUrl.host] = customCookies
                }
            }

            // 构建多部分请求体
            val multipartBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

            // 解析并添加表单数据
            try {
                val formData = JSONObject(formDataParam)
                val formKeys = formData.keys()
                while (formKeys.hasNext()) {
                    val key = formKeys.next()
                    val value = formData.getString(key)
                    multipartBodyBuilder.addFormDataPart(key, value)
                }
            } catch (e: Exception) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Error parsing form data: ${e.message}"
                )
            }

            // 解析并添加文件
            try {
                val filesArray = JSONArray(filesParam)
                for (i in 0 until filesArray.length()) {
                    val fileObj = filesArray.getJSONObject(i)
                    val fieldName = fileObj.getString("field_name")
                    val filePath = fileObj.getString("file_path")
                    val contentType = fileObj.optString("content_type", "application/octet-stream")
                    val fileName = fileObj.optString("file_name", File(filePath).name)

                    val file = File(filePath)
                    if (!file.exists() || !file.canRead()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "File does not exist or cannot be read: $filePath"
                        )
                    }

                    // 添加文件到多部分表单中
                    val fileBody = file.asRequestBody(contentType.toMediaType())
                    multipartBodyBuilder.addFormDataPart(fieldName, fileName, fileBody)
                }
            } catch (e: Exception) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Error parsing file data: ${e.message}"
                )
            }

            // 构建请求
            val requestBuilder = Request.Builder().url(url).header("User-Agent", USER_AGENT)

            // 添加自定义请求头
            headers.forEach { (name, value) -> requestBuilder.header(name, value) }

            // 构建multipart请求体
            val requestBody = multipartBodyBuilder.build()
            requestBuilder.method(method, requestBody)

            // 执行请求
            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            // 检查响应类型
            val contentType = response.header("Content-Type") ?: ""

            // 处理响应
            val responseHeadersMap =
                    response.headers.names().associateWith { name ->
                        response.headers.get(name) ?: ""
                    }

            // 提取响应的Cookie
            val responseCookieUrl =
                    url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL: $url")
            val responseCookies = cookieJar.loadForRequest(responseCookieUrl)
            val cookiesMap = responseCookies.associate { it.name to it.value }

            val responseBody =
                    response.body
                            ?: return ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result = StringResultData(""),
                                    error = "Response body is empty"
                            )

            var responseBodyString: String? = null
            var responseBodyBase64: String?

            val bodyBytes = responseBody.bytes()
            responseBodyBase64 = android.util.Base64.encodeToString(bodyBytes, android.util.Base64.NO_WRAP)
            try {
                val charset = response.body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                responseBodyString = String(bodyBytes, charset)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to decode response body as text for content-type $contentType", e)
                responseBodyString = "[Binary Content, decoding failed]"
            }
            
            // 返回原始内容
            val httpResponseData =
                    HttpResponseData(
                            url = url,
                            statusCode = response.code,
                            statusMessage = response.message,
                            headers = responseHeadersMap,
                            contentType = contentType,
                            content = responseBodyString ?: "[Binary Content]",
                            contentBase64 = responseBodyBase64,
                            size = bodyBytes.size,
                            cookies = cookiesMap
                    )

            ToolResult(toolName = tool.name, success = true, result = httpResponseData, error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "执行多部分表单请求时出错", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error executing multipart form request: ${e.message}"
            )
        }
    }

    private data class HttpTimeouts(
        val connectSeconds: Long,
        val readSeconds: Long,
        val writeSeconds: Long,
        val overallSeconds: Long?,
    )

    private fun resolveHttpTimeouts(tool: AITool): HttpTimeouts {
        val connectTimeoutParam = tool.parameters.find { it.name == "connect_timeout" }?.value
        val readTimeoutParam = tool.parameters.find { it.name == "read_timeout" }?.value
        val writeTimeoutParam = tool.parameters.find { it.name == "write_timeout" }?.value
        val overallSeconds =
            tool.parameters.find { it.name == "timeout" }?.value?.let(::parseTimeoutSeconds)
                ?: tool.parameters.find { it.name == "timeout_ms" }?.value?.let(::parseTimeoutMilliseconds)

        val connectSeconds = parseTimeoutSeconds(connectTimeoutParam) ?: overallSeconds ?: 15L
        val readSeconds = parseTimeoutSeconds(readTimeoutParam) ?: overallSeconds ?: 20L
        val writeSeconds = parseTimeoutSeconds(writeTimeoutParam) ?: overallSeconds ?: 15L
        return HttpTimeouts(
            connectSeconds = connectSeconds.coerceIn(1L, 600L),
            readSeconds = readSeconds.coerceIn(1L, 600L),
            writeSeconds = writeSeconds.coerceIn(1L, 600L),
            overallSeconds = overallSeconds?.coerceIn(1L, 600L),
        )
    }

    private fun parseTimeoutSeconds(raw: String?): Long? {
        val value = raw?.trim()?.toLongOrNull() ?: return null
        if (value <= 0L) return null
        return if (value >= 1000L) {
            parseTimeoutMilliseconds(raw)
        } else {
            value
        }
    }

    private fun parseTimeoutMilliseconds(raw: String?): Long? {
        val value = raw?.trim()?.toLongOrNull() ?: return null
        if (value <= 0L) return null
        return (value / 1000L + if (value % 1000L == 0L) 0L else 1L).coerceAtLeast(1L)
    }
}
