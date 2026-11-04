package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AudioTranscriptionException(
    val statusCode: Int? = null,
    val upstreamMessage: String? = null,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** Executes a dedicated transcription request and returns the transcribed text. */
class AudioTranscriptionClient(
    private val httpClient: OkHttpClient,
    private val endpoint: String,
    private val apiKeyProvider: ApiKeyProvider,
    private val customHeaders: Map<String, String> = emptyMap(),
) {
    companion object {
        /**
         * Builds a transcription client from a model configuration.
         *
         * Dedicated transcription endpoints speak multipart/form-data rather than the
         * chat-completions JSON protocol, so they cannot be driven through
         * [AIServiceFactory.createService].
         */
        fun create(
            config: ModelConfigData,
            modelConfigManager: ModelConfigManager,
        ): AudioTranscriptionClient {
            val apiKeyProvider =
                if (config.useMultipleApiKeys) {
                    MultiApiKeyProvider(config.id, modelConfigManager)
                } else {
                    SingleApiKeyProvider(config.apiKey)
                }
            return AudioTranscriptionClient(
                httpClient = SharedHttpClient.instance,
                endpoint = config.apiEndpoint,
                apiKeyProvider = apiKeyProvider,
                customHeaders = parseCustomHeaders(config.customHeaders),
            )
        }

        private fun parseCustomHeaders(customHeadersJson: String): Map<String, String> {
            if (customHeadersJson.isBlank() || customHeadersJson == "{}") {
                return emptyMap()
            }
            return runCatching {
                val json = JSONObject(customHeadersJson)
                buildMap {
                    json.keys().forEach { key -> put(key, json.getString(key)) }
                }
            }.getOrDefault(emptyMap())
        }
    }

    suspend fun transcribe(file: File, model: String): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider.getApiKey()
        val request =
            Request.Builder()
                .url(endpoint)
                .post(AudioTranscriptionProtocol.buildRequestBody(file, model))
                .apply {
                    if (apiKey.isNotBlank()) {
                        addHeader("Authorization", "Bearer $apiKey")
                    }
                    customHeaders.forEach { (key, value) -> addHeader(key, value) }
                }
                .build()

        val call = httpClient.newCall(request)
        // The shared client uses a very long read timeout for streaming chat responses. Register
        // cancellation so an aborted test does not keep a transcription call alive for minutes.
        val cancellationHandle =
            kotlinx.coroutines.currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
                if (cause != null) {
                    call.cancel()
                }
            }

        try {
            val response =
                try {
                    call.execute()
                } catch (e: IOException) {
                    throw AudioTranscriptionException(
                        message = "Audio transcription request failed: ${e.message}",
                        cause = e,
                    )
                }

            response.use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val upstreamMessage = AudioTranscriptionProtocol.extractErrorMessage(body)
                    val detail = upstreamMessage?.takeIf { it.isNotBlank() } ?: "empty error body"
                    throw AudioTranscriptionException(
                        statusCode = resp.code,
                        upstreamMessage = upstreamMessage,
                        message = "Audio transcription request failed with code ${resp.code}: $detail",
                    )
                }
                val text = AudioTranscriptionProtocol.parseResponseBody(body)
                if (text.isBlank()) {
                    throw AudioTranscriptionException(
                        statusCode = resp.code,
                        message = "Audio transcription response did not contain text",
                    )
                }
                text
            }
        } finally {
            cancellationHandle?.dispose()
        }
    }
}
