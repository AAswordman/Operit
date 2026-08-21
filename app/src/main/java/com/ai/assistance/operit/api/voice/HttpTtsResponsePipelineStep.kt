package com.ai.assistance.operit.api.voice

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class HttpTtsResponsePipelineStep(
    val type: String,
    val path: String = "",
    val headers: Map<String, String> = emptyMap()
) {
    val normalizedType: String
        get() = type.trim().lowercase()

    companion object {
        const val TYPE_PARSE_JSON = "parse_json"
        const val TYPE_PICK = "pick"
        const val TYPE_PARSE_JSON_STRING = "parse_json_string"
        const val TYPE_HTTP_GET = "http_get"
        const val TYPE_HTTP_REQUEST_FROM_OBJECT = "http_request_from_object"
        const val TYPE_BASE64_DECODE = "base64_decode"

        val SUPPORTED_TYPES: Set<String> =
            setOf(
                TYPE_PARSE_JSON,
                TYPE_PICK,
                TYPE_PARSE_JSON_STRING,
                TYPE_HTTP_GET,
                TYPE_HTTP_REQUEST_FROM_OBJECT,
                TYPE_BASE64_DECODE
            )

        private val editableJson =
            Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            }

        fun parseList(raw: String): List<HttpTtsResponsePipelineStep> {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return emptyList()
            return editableJson.decodeFromString(trimmed)
        }

        fun encodeList(steps: List<HttpTtsResponsePipelineStep>): String {
            return editableJson.encodeToString(steps)
        }

        /** Validates the path grammar shared by persisted config repair and HTTP execution. */
        fun requireValidPickPath(rawPath: String) {
            parseJsonPath(rawPath)
        }

        internal sealed interface JsonPathToken {
            data class Key(val name: String) : JsonPathToken
            data class Index(val index: Int) : JsonPathToken
        }

        internal fun parseJsonPath(rawPath: String): List<JsonPathToken> {
            val trimmed = rawPath.trim()
            if (trimmed.isBlank() || trimmed == "$") return emptyList()

            val normalized =
                trimmed.removePrefix("$").let {
                    if (it.startsWith(".")) it.removePrefix(".") else it
                }
            val tokens = mutableListOf<JsonPathToken>()
            var cursor = 0
            while (cursor < normalized.length) {
                when (normalized[cursor]) {
                    '.' -> cursor++
                    '[' -> {
                        val end = normalized.indexOf(']', cursor + 1)
                        require(end > cursor) { "Invalid json path: $rawPath" }
                        val indexText = normalized.substring(cursor + 1, end).trim()
                        val index = indexText.toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid json path index: $rawPath")
                        require(index >= 0) { "Invalid json path index: $rawPath" }
                        tokens += JsonPathToken.Index(index)
                        cursor = end + 1
                    }
                    else -> {
                        val start = cursor
                        while (
                            cursor < normalized.length &&
                                normalized[cursor] != '.' &&
                                normalized[cursor] != '['
                        ) {
                            cursor++
                        }
                        val key = normalized.substring(start, cursor)
                        require(key.isNotBlank()) { "Invalid json path: $rawPath" }
                        tokens += JsonPathToken.Key(key)
                    }
                }
            }
            return tokens
        }
    }
}
