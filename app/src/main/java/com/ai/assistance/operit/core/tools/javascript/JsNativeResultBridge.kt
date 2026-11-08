package com.ai.assistance.operit.core.tools.javascript

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val TOOL_RESULT_CALLBACK_FUNCTION = "__operitDeliverToolResult"
internal const val STRING_RESULT_CALLBACK_FUNCTION = "__operitDeliverStringResult"

internal fun buildNativeResultCallbackRuntime(): String = """
    (function() {
        var root = globalThis;
        var windowRef = typeof window !== 'undefined' ? window : root;
        __operitExpose('$TOOL_RESULT_CALLBACK_FUNCTION', function(callbackId, result, isError) {
            if (typeof windowRef[callbackId] === 'function') {
                windowRef[callbackId](result, isError);
            } else {
                console.error('Callback not found: ' + callbackId);
            }
        });
        __operitExpose('$STRING_RESULT_CALLBACK_FUNCTION', function(callbackId, result, isError) {
            var callback = root[callbackId];
            if (typeof callback === 'function') {
                callback(result, isError);
            } else {
                console.error('Callback not found: ' + callbackId);
            }
        });
    })();
""".trimIndent()

internal fun buildToolResultCallbackArgs(
    callbackId: String,
    result: String,
    isError: Boolean
): String {
    val first = result.indexOfFirst { !it.isWhitespace() }
    val last = result.indexOfLast { !it.isWhitespace() }
    val isJsonLiteral = first >= 0 && when (result[first]) {
        '{' -> result[last] == '}'
        '[' -> result[last] == ']'
        '"' -> result[last] == '"'
        else -> false
    }
    // JSON 结果作为参数原样交付；普通文本只进行一次 JSON 转义，不再插入脚本源码。
    val payload = if (isJsonLiteral) result else Json.encodeToString(result)
    return buildNativeResultCallbackArgs(callbackId, payload, isError)
}

internal fun buildStringResultCallbackArgs(
    callbackId: String,
    result: String,
    isError: Boolean
): String = buildNativeResultCallbackArgs(callbackId.trim(), Json.encodeToString(result), isError)

private fun buildNativeResultCallbackArgs(
    callbackId: String,
    payload: String,
    isError: Boolean
): String {
    val callbackIdJson = Json.encodeToString(callbackId)
    // 预留完整参数容量，避免大结果在 StringBuilder 中反复扩容。
    return buildString(callbackIdJson.length + payload.length + 10) {
        append('[')
        append(callbackIdJson)
        append(',')
        append(payload)
        append(',')
        append(isError)
        append(']')
    }
}
