package com.ai.assistance.operit.api.speech.tts

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
    }
}
