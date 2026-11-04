package com.ai.assistance.operit.api.chat.llmprovider

import java.io.File
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject

/**
 * Wire contract for dedicated speech-to-text endpoints such as
 * `POST /v1/audio/transcriptions`.
 *
 * These endpoints accept `multipart/form-data` and are not chat-completions endpoints; sending a
 * chat JSON body to them produces an opaque HTTP 400. Keeping the contract in one pure object lets
 * model tests, the audio-recognition path and the STT provider share the same request shape.
 */
object AudioTranscriptionProtocol {
    private const val TRANSCRIPTION_PATH = "/audio/transcriptions"

    /** True when [endpoint] addresses a dedicated transcription endpoint. */
    fun isTranscriptionEndpoint(endpoint: String): Boolean {
        val withoutFragment = endpoint.trim().substringBefore('#')
        val path = withoutFragment.substringBefore('?')
        return path.trimEnd('/').endsWith(TRANSCRIPTION_PATH, ignoreCase = true)
    }

    /** True when [endpoint] belongs to SiliconFlow's official audio API host. */
    fun isSiliconFlowEndpoint(endpoint: String): Boolean {
        val host =
            runCatching { java.net.URI(endpoint.trim()).host }.getOrNull() ?: return false
        return host.equals("api.siliconflow.cn", ignoreCase = true) ||
            host.equals("api.siliconflow.com", ignoreCase = true)
    }

    fun mediaTypeFor(file: File): MediaType {
        return when (file.extension.lowercase()) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg", "opus" -> "audio/ogg"
            "webm" -> "audio/webm"
            else -> "application/octet-stream"
        }.toMediaType()
    }

    /**
     * Builds the multipart body for a transcription request.
     *
     * SiliconFlow's published schema only accepts `file` and `model`; callers targeting it must
     * leave [language] and [responseFormat] null.
     */
    fun buildRequestBody(
        file: File,
        model: String,
        language: String? = null,
        responseFormat: String? = null,
    ): RequestBody {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart("file", file.name, file.asRequestBody(mediaTypeFor(file)))
        builder.addFormDataPart("model", model)
        if (!language.isNullOrBlank()) {
            builder.addFormDataPart("language", language)
        }
        if (!responseFormat.isNullOrBlank()) {
            builder.addFormDataPart("response_format", responseFormat)
        }
        return builder.build()
    }

    /** Reads the transcribed text from a `{"text": "..."}` response, or returns plain text. */
    fun parseResponseBody(body: String): String {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            return JSONObject(trimmed).optString("text", "")
        }
        return trimmed
    }

    /**
     * Normalizes upstream error bodies from SiliconFlow (`{"code":...,"message":"..."}`) and
     * OpenAI-compatible services (`{"error":{"message":"..."}}`).
     */
    fun extractErrorMessage(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            val json = JSONObject(trimmed)
            json.optString("message").takeIf { it.isNotBlank() }
                ?: json.optJSONObject("error")
                    ?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                ?: trimmed
        }.getOrDefault(trimmed)
    }
}
