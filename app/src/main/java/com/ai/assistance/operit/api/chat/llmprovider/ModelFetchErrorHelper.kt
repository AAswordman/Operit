package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.R
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.json.JSONException
import org.json.JSONObject

/**
 * HTTP 状态码异常包装类，实现 HttpStatusCodeException 接口以保留状态码与原始错误响应体
 */
class ModelFetchHttpException(
    override val statusCode: Int,
    val errorBody: String,
    message: String
) : IOException(message), HttpStatusCodeException

/**
 * 模型列表获取错误友好化解析工具，将底层网络或 HTTP 异常转为用户易懂、可定位原因的提示文案
 */
object ModelFetchErrorHelper {

    fun extractErrorMessage(errorBody: String): String? {
        if (errorBody.isBlank()) return null
        return try {
            val json = JSONObject(errorBody)
            if (json.has("error")) {
                val errorObj = json.optJSONObject("error")
                if (errorObj != null && errorObj.has("message")) {
                    errorObj.optString("message").trim().takeIf { it.isNotBlank() }
                } else {
                    json.optString("error").trim().takeIf { it.isNotBlank() }
                }
            } else if (json.has("message")) {
                json.optString("message").trim().takeIf { it.isNotBlank() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isTimeoutException(throwable: Throwable?): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is SocketTimeoutException) return true
            val msg = current.message?.lowercase() ?: ""
            if (msg.contains("timeout") || msg.contains("timed out")) return true
            current = current.cause
        }
        return false
    }

    fun isNetworkConnectionException(throwable: Throwable?): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is UnknownHostException ||
                current is ConnectException ||
                current is NoRouteToHostException ||
                current is PortUnreachableException
            ) {
                return true
            }
            val msg = current.message?.lowercase() ?: ""
            if (msg.contains("unable to resolve host") ||
                msg.contains("failed to connect") ||
                msg.contains("network is unreachable") ||
                msg.contains("connection refused")
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun isSslException(throwable: Throwable?): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is SSLException) return true
            val msg = current.message?.lowercase() ?: ""
            if (msg.contains("ssl") || msg.contains("cert")) return true
            current = current.cause
        }
        return false
    }

    fun extractHttpStatusCode(throwable: Throwable?): Int? {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is HttpStatusCodeException) {
                return current.statusCode
            }
            val msg = current.message ?: ""
            val regex = Regex("""(?:API请求失败:\s*|HTTP\s*(?:status|code)?\s*|status code:\s*)(\d{3})""", RegexOption.IGNORE_CASE)
            val match = regex.find(msg)
            if (match != null) {
                return match.groupValues[1].toIntOrNull()
            }
            current = current.cause
        }
        return null
    }

    fun formatError(context: Context, throwable: Throwable?): String {
        if (throwable == null) {
            return context.getString(R.string.unknown_error)
        }

        // 1. 超时
        if (isTimeoutException(throwable)) {
            return context.getString(R.string.model_fetch_error_timeout)
        }

        // 2. 网络无法连接 / 域名无法解析（可能需要梯子）
        if (isNetworkConnectionException(throwable)) {
            return context.getString(R.string.model_fetch_error_network)
        }

        // 3. SSL 握手/证书错误
        if (isSslException(throwable)) {
            return context.getString(R.string.model_fetch_error_ssl)
        }

        // 4. HTTP 状态码错误
        val statusCode = extractHttpStatusCode(throwable)
        if (statusCode != null) {
            val rawErrorBody = (throwable as? ModelFetchHttpException)?.errorBody ?: throwable.message.orEmpty()
            val parsedReason = extractErrorMessage(rawErrorBody)

            return when (statusCode) {
                401 -> {
                    if (!parsedReason.isNullOrBlank()) {
                        context.getString(R.string.model_fetch_error_http, statusCode, parsedReason)
                    } else {
                        context.getString(R.string.model_fetch_error_unauthorized)
                    }
                }
                403 -> {
                    if (!parsedReason.isNullOrBlank()) {
                        context.getString(R.string.model_fetch_error_http, statusCode, parsedReason)
                    } else {
                        context.getString(R.string.model_fetch_error_forbidden)
                    }
                }
                404 -> {
                    if (!parsedReason.isNullOrBlank()) {
                        context.getString(R.string.model_fetch_error_http, statusCode, parsedReason)
                    } else {
                        context.getString(R.string.model_fetch_error_not_found)
                    }
                }
                429 -> {
                    if (!parsedReason.isNullOrBlank()) {
                        context.getString(R.string.model_fetch_error_http, statusCode, parsedReason)
                    } else {
                        context.getString(R.string.model_fetch_error_rate_limit)
                    }
                }
                in 500..599 -> {
                    context.getString(R.string.model_fetch_error_server, statusCode)
                }
                else -> {
                    if (!parsedReason.isNullOrBlank()) {
                        context.getString(R.string.model_fetch_error_http, statusCode, parsedReason)
                    } else {
                        context.getString(R.string.model_fetch_api_failed, statusCode, rawErrorBody.take(120))
                    }
                }
            }
        }

        // 5. JSON 解析错误 / 返回非预期格式
        var current: Throwable? = throwable
        while (current != null) {
            if (current is JSONException) {
                return context.getString(R.string.model_fetch_error_format)
            }
            current = current.cause
        }
        val msg = throwable.message.orEmpty()
        if (msg.contains("modellist_error_missing_data") || msg.contains("Unexpected HTML")) {
            return context.getString(R.string.model_fetch_error_format)
        }

        // 6. 兜底
        return context.getString(R.string.get_models_list_failed, msg.ifBlank { context.getString(R.string.unknown_error) })
    }
}
