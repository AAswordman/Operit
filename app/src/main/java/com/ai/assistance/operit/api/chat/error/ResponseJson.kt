package com.ai.assistance.operit.api.chat.error

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** JSON 语法错误在解码位置产生编号，字段缺失另行报告，不能都说成连接失败。 */
internal object ResponseJson {
    fun parseObject(text: String, streaming: Boolean): JSONObject = try {
        JSONObject(text)
    } catch (error: JSONException) {
        throw RequestFailureException(
            if (streaming) RequestFailureCode.STREAM_JSON_INVALID else RequestFailureCode.RESPONSE_JSON_INVALID,
            error.message,
            error,
        )
    }

    fun parseArray(text: String, streaming: Boolean): JSONArray = try {
        JSONArray(text)
    } catch (error: JSONException) {
        throw RequestFailureException(
            if (streaming) RequestFailureCode.STREAM_JSON_INVALID else RequestFailureCode.RESPONSE_JSON_INVALID,
            error.message,
            error,
        )
    }

    fun schemaFailure(error: Exception): Exception =
        if (error is JSONException) RequestFailureException(RequestFailureCode.RESPONSE_SHAPE_INVALID, error.message, error)
        else error
}
