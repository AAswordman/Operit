package com.ai.assistance.operit.util.logging

import org.json.JSONArray
import org.json.JSONObject

/** 直接读取有界样本，避免为了日志复制、解析和格式化整份模型请求。 */
internal object RequestBodyLogFormatter {
    const val MAX_LOG_CHARACTERS = 12_000
    private const val MAX_STRING_CHARACTERS = 240
    private const val MAX_TOTAL_STRING_CHARACTERS = 6_000
    private const val MAX_VISITED_VALUES = 80
    private const val MAX_OBJECT_FIELDS = 16
    private const val MAX_ARRAY_ENTRIES = 8
    private const val MAX_DEPTH = 5

    fun format(body: JSONObject): String {
        val preview = Budget().copyValue(body, key = "", depth = 0) as JSONObject
        val rendered = preview.toString(2)
        val suffix = "\n[request log truncated]"
        return if (rendered.length <= MAX_LOG_CHARACTERS) {
            rendered
        } else {
            rendered.take(MAX_LOG_CHARACTERS - suffix.length) + suffix
        }
    }

    private class Budget {
        private var remainingValues = MAX_VISITED_VALUES
        private var remainingStringCharacters = MAX_TOTAL_STRING_CHARACTERS

        fun copyValue(value: Any?, key: String, depth: Int): Any {
            if (remainingValues <= 0) return "[more values omitted]"
            remainingValues -= 1
            return when (value) {
                null, JSONObject.NULL -> JSONObject.NULL
                is JSONObject -> if (depth >= MAX_DEPTH) {
                    "[object omitted, fields=${value.length()}]"
                } else {
                    copyObject(value, depth)
                }
                is JSONArray -> if (key == "tools" || depth >= MAX_DEPTH) {
                    "[${value.length()} entries omitted]"
                } else {
                    copyArray(value, depth)
                }
                is String -> previewString(value, key)
                is Number, is Boolean -> value
                else -> "[unsupported log value omitted]"
            }
        }

        private fun copyObject(source: JSONObject, depth: Int): JSONObject {
            val result = JSONObject()
            val keys = source.keys()
            var copied = 0
            while (keys.hasNext() && copied < MAX_OBJECT_FIELDS && remainingValues > 0) {
                val key = keys.next()
                val logKey = key.take(MAX_STRING_CHARACTERS)
                result.put(logKey, copyValue(source.get(key), key, depth + 1))
                copied += 1
            }
            if (copied < source.length()) {
                result.put("_omitted_fields", source.length() - copied)
            }
            return result
        }

        private fun copyArray(source: JSONArray, depth: Int): JSONArray {
            val result = JSONArray()
            val size = source.length()
            val headCount = if (size <= MAX_ARRAY_ENTRIES) size else MAX_ARRAY_ENTRIES / 2
            var copied = 0
            for (index in 0 until headCount) {
                if (remainingValues <= 0) break
                result.put(copyValue(source.get(index), key = "", depth = depth + 1))
                copied += 1
            }
            if (copied < headCount) {
                result.put("[${size - copied} entries omitted]")
                return result
            }
            if (size > MAX_ARRAY_ENTRIES) {
                val tailStart = size - MAX_ARRAY_ENTRIES / 2
                result.put("[${tailStart - headCount} middle entries omitted]")
                for (index in tailStart until size) {
                    if (remainingValues <= 0) break
                    result.put(copyValue(source.get(index), key = "", depth = depth + 1))
                    copied += 1
                }
                val omittedTail = MAX_ARRAY_ENTRIES - copied
                if (omittedTail > 0) result.put("[$omittedTail tail entries omitted]")
            }
            return result
        }

        private fun previewString(value: String, key: String): String {
            // 媒体正文只记录长度；判定本身不遍历 Base64 内容。
            if (value.startsWith("data:") || key == "data" || key == "file_data") {
                return "[media data omitted, length=${value.length}]"
            }
            val limit = minOf(MAX_STRING_CHARACTERS, remainingStringCharacters)
            val preview = value.take(limit)
            remainingStringCharacters -= preview.length
            return if (preview.length == value.length) preview
            else "$preview [text omitted, length=${value.length}]"
        }
    }
}
