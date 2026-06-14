package com.ai.assistance.operit.api.voice

import android.content.Context
import android.util.Base64
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.HttpLogSanitizer
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * 基于HTTP请求的TTS语音服务实现
 *
 * 此实现通过HTTP请求获取TTS音频数据，支持配置不同的TTS服务端点
 */
open class HttpVoiceProvider(
    private val context: Context
) : VoiceService {

    private var httpConfig: SpeechServicesPreferences.TtsHttpConfig = SpeechServicesPreferences.DEFAULT_HTTP_TTS_PRESET

    companion object {
        private const val TAG = "HttpVoiceProvider"
        private const val DEFAULT_TIMEOUT = 10 // 10秒超时
        private const val SPEECH_PREVIEW_MAX = 48
    }

    private fun speechPreview(text: String): String {
        return text.replace("\n", "\\n").take(SPEECH_PREVIEW_MAX)
    }

    // OkHttpClient实例
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .build()
    }

    // 初始化状态
    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: Boolean
        get() = _isInitialized.value

    private val playbackQueue =
        QueuedTtsPlayback(TAG) { request ->
            fetchAudioFile(request)
        }

    override val isSpeaking: Boolean
        get() = playbackQueue.isSpeaking

    override val speakingStateFlow: Flow<Boolean>
        get() = playbackQueue.speakingStateFlow

    // 缓存的音频文件映射表
    private val audioCache = ConcurrentHashMap<String, File>()

    // 当前语音参数
    private var currentRate: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentVoiceId: String? = null

    // 临时文件目录
    private val cacheDir by lazy { context.cacheDir }

    private data class BinaryPayload(
        val bytes: ByteArray,
        val contentType: String?,
        val charset: Charset
    )

    private sealed interface PipelineValue {
        data class Binary(val payload: BinaryPayload) : PipelineValue
        data class Text(val value: String) : PipelineValue
        data class JsonData(val value: JsonElement) : PipelineValue
    }

    private sealed interface JsonPathToken {
        data class Key(val name: String) : JsonPathToken
        data class Index(val index: Int) : JsonPathToken
    }

    private val jsonParser =
        Json {
            ignoreUnknownKeys = true
        }

    /**
     * 设置HTTP TTS服务的配置
     * @param config TTS HTTP配置
     */
    open fun setConfiguration(config: SpeechServicesPreferences.TtsHttpConfig) {
        this.httpConfig = config
        this.currentVoiceId = config.voiceId.takeIf { it.isNotBlank() }
        this._isInitialized.value = false // 强制重新初始化
        AppLogger.d(
            TAG,
            "setConfiguration method=${config.httpMethod} url=${config.urlTemplate} voice=${config.voiceId} model=${config.modelName} pipelineSteps=${config.responsePipeline.size}"
        )
    }

    /** 初始化TTS引擎 */
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (_isInitialized.value) {
            return@withContext true
        }
        try {
            if (httpConfig.urlTemplate.isBlank()) {
                throw TtsException(context.getString(R.string.http_tts_url_template_not_set))
            }

            val isPost = httpConfig.httpMethod.uppercase() == "POST"
            val hasTextPlaceholder = if (isPost) {
                httpConfig.requestBody.contains("{text}", ignoreCase = true)
            } else {
                httpConfig.urlTemplate.contains("{text}", ignoreCase = true)
            }

            if (!hasTextPlaceholder) {
                val errorMessage = if (isPost) {
                    context.getString(R.string.http_tts_post_body_missing_placeholder)
                } else {
                    context.getString(R.string.http_tts_get_url_missing_placeholder)
                }
                throw TtsException(errorMessage)
            }

            if (!httpConfig.urlTemplate.startsWith("http://") && !httpConfig.urlTemplate.startsWith("https://")) {
                throw TtsException(context.getString(R.string.http_tts_url_invalid_scheme))
            }

            validateResponsePipeline(httpConfig.responsePipeline)

            _isInitialized.value = true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize HTTP TTS provider due to invalid configuration", e)
            _isInitialized.value = false
            // 重新抛出异常，以便UI层可以捕获并显示
            if (e is TtsException) throw e
            else throw TtsException(context.getString(R.string.http_tts_init_failed), cause = e)
        }

        return@withContext _isInitialized.value
    }

    /**
     * 将文本转换为语音并播放
     *
     * @param text 要转换为语音的文本
     * @param interrupt 是否中断当前正在播放的语音
     * @param rate 语速
     * @param pitch 音调
     * @param extraParams 额外的请求参数
     * @return 操作是否成功
     */
    override suspend fun speak(
        text: String,
        interrupt: Boolean,
        rate: Float?,
        pitch: Float?,
        extraParams: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        AppLogger.d(
            TAG,
            "speak request interrupt=$interrupt len=${text.length} preview=\"${speechPreview(text)}\" rate=$rate pitch=$pitch voice=$currentVoiceId initialized=$isInitialized extraKeys=${extraParams.keys}"
        )
        playbackQueue.speak(
            text = text,
            interrupt = interrupt,
            rate = rate,
            pitch = pitch,
            extraParams = extraParams
        )
    }

    private suspend fun fetchAudioFile(request: QueuedTtsPlayback.Request): File? {
        // 检查初始化状态
        if (!isInitialized) {
            val initResult = initialize()
            if (!initResult) {
                return null
            }
        }

        if (!playbackQueue.isCurrent(request)) {
            return null
        }

        val prefs = SpeechServicesPreferences(context.applicationContext)
        val effectiveRate = request.rate ?: prefs.ttsSpeechRateFlow.first()
        val effectivePitch = request.pitch ?: prefs.ttsPitchFlow.first()

        try {
            // 生成缓存键
            val cacheKey = generateCacheKey(
                request.text,
                effectiveRate,
                effectivePitch,
                currentVoiceId,
                request.extraParams
            )
            var audioFile = audioCache[cacheKey]

            // 如果缓存中没有，则请求新的音频
            if (audioFile == null || !audioFile.exists()) {
                audioFile = fetchAudioFromServer(
                    request.text,
                    effectiveRate,
                    effectivePitch,
                    currentVoiceId,
                    request.extraParams
                )
                if (audioFile != null) {
                    audioCache[cacheKey] = audioFile
                } else {
                    return null
                }
            }

            if (!playbackQueue.isCurrent(request)) {
                return null
            }

            return audioFile
        } catch (e: Exception) {
            AppLogger.e(TAG, "HTTP TTS播放失败", e)
            throw e
        }
    }

    /** 停止当前正在播放的语音 */
    override suspend fun stop(): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext false
        playbackQueue.stop()
    }

    /** 暂停当前正在播放的语音 */
    override suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext false
        playbackQueue.pause()
    }

    /** 继续播放暂停的语音 */
    override suspend fun resume(): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext false
        playbackQueue.resume()
    }

    /** 释放TTS引擎资源 */
    override fun shutdown() {
        try {
            playbackQueue.shutdown()
            _isInitialized.value = false
            clearCache()
        } catch (e: Exception) {
            AppLogger.e(TAG, "关闭HTTP TTS引擎失败", e)
        }
    }

    /** 获取可用的语音列表 */
    override suspend fun getAvailableVoices(): List<VoiceService.Voice> {
        AppLogger.w(TAG, "Listing available voices via HTTP is not supported by this generic provider. Returning an empty list. Set voice directly using setVoice().")
        return emptyList()
    }

    /** 设置当前使用的语音 */
    override suspend fun setVoice(voiceId: String): Boolean = withContext(Dispatchers.IO) {
        currentVoiceId = voiceId
        return@withContext true
    }

    /**
     * 从服务器获取音频数据
     *
     * @param text 要转换的文本
     * @param rate 语速
     * @param pitch 音调
     * @param voiceId 语音ID
     * @return 音频文件，如果失败则返回null
     */
    private suspend fun fetchAudioFromServer(
        text: String,
        rate: Float,
        pitch: Float,
        voiceId: String?,
        extraParams: Map<String, String>
    ): File? = withContext(Dispatchers.IO) {
        AppLogger.d(
            TAG,
            "fetchAudioFromServer len=${text.length} preview=\"${speechPreview(text)}\" rate=$rate pitch=$pitch voice=${voiceId?.length ?: 0} chars extraKeys=${extraParams.keys}"
        )
        if (httpConfig.urlTemplate.isBlank()) {
            AppLogger.e(TAG, "HTTP TTS URL template is not configured.")
            return@withContext null
        }

        try {
            // URL-encode parameters before replacing
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val encodedRate = rate.toString()
            val encodedPitch = pitch.toString()
            val encodedVoiceId = voiceId?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
            val requestId = UUID.randomUUID().toString()
            val bodyPlaceholders =
                linkedMapOf(
                    "text" to text,
                    "rate" to encodedRate,
                    "pitch" to encodedPitch,
                    "apiKey" to httpConfig.apiKey,
                    "model" to httpConfig.modelName,
                    "locale" to httpConfig.localeTag,
                    "uuid" to requestId
                ).apply {
                    voiceId?.let { put("voice", it) }
                    extraParams.forEach { (key, value) -> put(key, value) }
                }

            val requestBuilder = Request.Builder()

            if (httpConfig.httpMethod.uppercase() == "POST") {
                // For POST requests, use the URL as base and put parameters in body
                val baseUrl = httpConfig.urlTemplate
                val httpUrl = baseUrl.toHttpUrlOrNull()
                if (httpUrl == null) {
                    AppLogger.e(TAG, "Base URL is invalid: $baseUrl")
                    return@withContext null
                }

                // Replace placeholders in the request body template
                val requestBody =
                    replaceBodyPlaceholders(
                        template = httpConfig.requestBody,
                        replacements = bodyPlaceholders,
                        contentType = httpConfig.contentType
                    )

                val mediaType = httpConfig.contentType.toMediaType()
                val body = requestBody.toRequestBody(mediaType)

                requestBuilder
                    .url(httpUrl)
                    .post(body)
                    .addHeader("Content-Type", httpConfig.contentType)
            } else {
                // For GET requests, use the existing logic with URL parameters
                var finalUrl = httpConfig.urlTemplate
                    .replace("{text}", encodedText, ignoreCase = true)
                    .replace("{rate}", encodedRate, ignoreCase = true)
                    .replace("{pitch}", encodedPitch, ignoreCase = true)

                if (voiceId != null) {
                    finalUrl = finalUrl.replace("{voice}", encodedVoiceId, ignoreCase = true)
                }

                // Replace any extra parameters
                extraParams.forEach { (key, value) ->
                    finalUrl = finalUrl.replace("{$key}", URLEncoder.encode(value, "UTF-8"), ignoreCase = true)
                }

                val httpUrl = finalUrl.toHttpUrlOrNull()
                if (httpUrl == null) {
                    AppLogger.e(TAG, "Constructed URL is invalid: $finalUrl")
                    return@withContext null
                }

                requestBuilder
                    .url(httpUrl)
                    .get()
            }

            val hasConfiguredAuthorization =
                httpConfig.headers.keys.any { it.equals("Authorization", ignoreCase = true) }

            // Add API key if present
            if (httpConfig.apiKey.isNotBlank() && !hasConfiguredAuthorization) {
                requestBuilder.addHeader("Authorization", "Bearer ${httpConfig.apiKey}")
            }

            // Add custom headers
            httpConfig.headers.forEach { (key, value) ->
                requestBuilder.addHeader(
                    key,
                    replacePlainPlaceholders(value, bodyPlaceholders)
                )
            }

            val request = requestBuilder.build()

            AppLogger.i(TAG, "Executing TTS Request: ${request.method} ${HttpLogSanitizer.urlForLog(request.url)}")
            AppLogger.i(TAG, "Request Headers:\n${HttpLogSanitizer.headersForLog(request.headers)}")
            val initialPayload = executeBinaryRequest(request, "HTTP TTS request")
            val finalPayload =
                if (httpConfig.responsePipeline.isEmpty()) {
                    initialPayload
                } else {
                    resolvePipelineAudio(initialPayload, httpConfig.responsePipeline)
                }

            val tempFile = File(cacheDir, "tts_${UUID.randomUUID()}.bin")
            FileOutputStream(tempFile).use { output ->
                output.write(finalPayload.bytes)
            }

            return@withContext tempFile
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取HTTP TTS音频失败", e)
            if (e is TtsException) throw e
            throw TtsException(context.getString(R.string.http_tts_fetch_failed), cause = e)
        }
    }

    private fun replaceBodyPlaceholders(
        template: String,
        replacements: Map<String, String>,
        contentType: String
    ): String {
        return when {
            isJsonContentType(contentType) ->
                replacePlaceholders(template, replacements) { rawValue, matchRange, currentTemplate ->
                    encodeJsonPlaceholderValue(currentTemplate, matchRange, rawValue)
                }

            isFormUrlEncodedContentType(contentType) ->
                replacePlaceholders(template, replacements) { rawValue, _, _ ->
                    URLEncoder.encode(rawValue, "UTF-8")
                }

            else ->
                replacePlaceholders(template, replacements) { rawValue, _, _ -> rawValue }
        }
    }

    private fun replacePlainPlaceholders(
        template: String,
        replacements: Map<String, String>
    ): String {
        return replacePlaceholders(template, replacements) { rawValue, _, _ -> rawValue }
    }

    private fun replacePlaceholders(
        template: String,
        replacements: Map<String, String>,
        transform: (rawValue: String, matchRange: IntRange, currentTemplate: String) -> String
    ): String {
        var result = template
        replacements.forEach { (key, rawValue) ->
            val regex = Regex("\\{${Regex.escape(key)}\\}", RegexOption.IGNORE_CASE)
            val currentTemplate = result
            result =
                regex.replace(currentTemplate) { matchResult ->
                    transform(rawValue, matchResult.range, currentTemplate)
                }
        }
        return result
    }

    private fun encodeJsonPlaceholderValue(
        template: String,
        matchRange: IntRange,
        rawValue: String
    ): String {
        val encodedAsJsonString = Json.encodeToString(rawValue)
        if (isWrappedInJsonQuotes(template, matchRange)) {
            return encodedAsJsonString.removeSurrounding("\"")
        }

        val jsonLiteral = rawValue.toJsonLiteralOrNull()
        return jsonLiteral ?: encodedAsJsonString
    }

    private fun isWrappedInJsonQuotes(template: String, matchRange: IntRange): Boolean {
        val start = matchRange.first
        val endExclusive = matchRange.last + 1
        return start > 0 &&
            endExclusive < template.length &&
            template[start - 1] == '"' &&
            template[endExclusive] == '"'
    }

    private fun String.toJsonLiteralOrNull(): String? {
        val trimmed = trim()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.equals("true", ignoreCase = true) -> "true"
            trimmed.equals("false", ignoreCase = true) -> "false"
            trimmed.equals("null", ignoreCase = true) -> "null"
            trimmed.toLongOrNull() != null -> trimmed
            trimmed.toDoubleOrNull() != null -> trimmed
            else -> null
        }
    }

    private fun isJsonContentType(contentType: String): Boolean {
        val normalized = contentType.substringBefore(';').trim().lowercase()
        return normalized == "application/json" || normalized.endsWith("+json")
    }

    private fun isFormUrlEncodedContentType(contentType: String): Boolean {
        return contentType.substringBefore(';').trim()
            .equals("application/x-www-form-urlencoded", ignoreCase = true)
    }

    private fun validateResponsePipeline(steps: List<HttpTtsResponsePipelineStep>) {
        steps.forEachIndexed { index, step ->
            val type = step.normalizedType
            if (type !in HttpTtsResponsePipelineStep.SUPPORTED_TYPES) {
                throw TtsException(
                    context.getString(R.string.http_tts_response_pipeline_invalid_step, index + 1, step.type)
                )
            }
            if (type == HttpTtsResponsePipelineStep.TYPE_PICK && step.path.isBlank()) {
                throw TtsException(
                    context.getString(R.string.http_tts_response_pipeline_pick_path_required, index + 1)
                )
            }
            if (type == HttpTtsResponsePipelineStep.TYPE_PICK) {
                runCatching { parseJsonPath(step.path) }.getOrElse {
                    throw TtsException(
                        context.getString(
                            R.string.http_tts_response_pipeline_invalid_step,
                            index + 1,
                            "${step.type} path"
                        ),
                        cause = it
                    )
                }
            }
        }
    }

    private suspend fun executeRequest(request: Request): Response =
        suspendCoroutine { continuation ->
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }
            })
        }

    private suspend fun executeBinaryRequest(
        request: Request,
        requestLabel: String
    ): BinaryPayload = withContext(Dispatchers.IO) {
        val response = executeRequest(request)
        response.use { safeResponse ->
            val responseBody = safeResponse.body
                ?: throw TtsException("$requestLabel returned an empty response body")

            if (!safeResponse.isSuccessful) {
                val errorBody = responseBody.string()
                AppLogger.e(TAG, "$requestLabel failed. Code: ${safeResponse.code}, Body: $errorBody")
                throw TtsException(
                    message = "$requestLabel failed with code ${safeResponse.code}",
                    httpStatusCode = safeResponse.code,
                    errorBody = errorBody
                )
            }

            val mediaType = responseBody.contentType()
            return@withContext BinaryPayload(
                bytes = responseBody.bytes(),
                contentType = mediaType?.toString(),
                charset = mediaType?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            )
        }
    }

    private suspend fun resolvePipelineAudio(
        initialPayload: BinaryPayload,
        steps: List<HttpTtsResponsePipelineStep>
    ): BinaryPayload {
        var current: PipelineValue = PipelineValue.Binary(initialPayload)

        steps.forEachIndexed { index, step ->
            current =
                when (step.normalizedType) {
                    HttpTtsResponsePipelineStep.TYPE_PARSE_JSON -> {
                        PipelineValue.JsonData(parseJsonPayload(current, index))
                    }

                    HttpTtsResponsePipelineStep.TYPE_PICK -> {
                        PipelineValue.JsonData(pickJsonValue(current, step.path, index))
                    }

                    HttpTtsResponsePipelineStep.TYPE_PARSE_JSON_STRING -> {
                        PipelineValue.JsonData(parseJsonStringValue(current, index))
                    }

                    HttpTtsResponsePipelineStep.TYPE_HTTP_GET -> {
                        PipelineValue.Binary(followHttpUrl(current, step, index))
                    }

                    HttpTtsResponsePipelineStep.TYPE_HTTP_REQUEST_FROM_OBJECT -> {
                        PipelineValue.Binary(followHttpRequestObject(current, index))
                    }

                    HttpTtsResponsePipelineStep.TYPE_BASE64_DECODE -> {
                        PipelineValue.Binary(decodeBase64Value(current, index))
                    }

                    else -> {
                        throw TtsException(
                            context.getString(
                                R.string.http_tts_response_pipeline_invalid_step,
                                index + 1,
                                step.type
                            )
                        )
                    }
                }
        }

        return when (current) {
            is PipelineValue.Binary -> current.payload
            else -> throw TtsException(context.getString(R.string.http_tts_response_pipeline_final_not_audio))
        }
    }

    private fun parseJsonPayload(current: PipelineValue, stepIndex: Int): JsonElement {
        val text =
            when (current) {
                is PipelineValue.Binary -> current.payload.bytes.toString(current.payload.charset)
                is PipelineValue.Text -> current.value
                is PipelineValue.JsonData -> {
                    throw TtsException(
                        context.getString(
                            R.string.http_tts_response_pipeline_step_type_mismatch,
                            stepIndex + 1,
                            "parse_json",
                            "binary/text"
                        )
                    )
                }
            }

        return try {
            jsonParser.parseToJsonElement(text)
        } catch (e: Exception) {
            throw TtsException(
                context.getString(R.string.http_tts_response_pipeline_parse_json_failed, stepIndex + 1),
                cause = e
            )
        }
    }

    private fun pickJsonValue(current: PipelineValue, path: String, stepIndex: Int): JsonElement {
        val jsonValue =
            when (current) {
                is PipelineValue.JsonData -> current.value
                else -> {
                    throw TtsException(
                        context.getString(
                            R.string.http_tts_response_pipeline_step_type_mismatch,
                            stepIndex + 1,
                            "pick",
                            "json"
                        )
                    )
                }
            }

        return readJsonPath(jsonValue, path, stepIndex)
    }

    private fun parseJsonStringValue(current: PipelineValue, stepIndex: Int): JsonElement {
        val raw =
            current.asScalarString(stepIndex, "parse_json_string")

        return try {
            jsonParser.parseToJsonElement(raw)
        } catch (e: Exception) {
            throw TtsException(
                context.getString(R.string.http_tts_response_pipeline_parse_json_string_failed, stepIndex + 1),
                cause = e
            )
        }
    }

    private suspend fun followHttpUrl(
        current: PipelineValue,
        step: HttpTtsResponsePipelineStep,
        stepIndex: Int
    ): BinaryPayload {
        val rawUrl = current.asScalarString(stepIndex, "http_get").trim()
        val httpUrl = rawUrl.toHttpUrlOrNull()
            ?: throw TtsException(context.getString(R.string.http_tts_response_pipeline_http_url_invalid, stepIndex + 1))

        val request =
            Request.Builder()
                .url(httpUrl)
                .get()
                .apply {
                    step.headers.forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .build()

        return executeBinaryRequest(
            request,
            "HTTP TTS pipeline http_get step ${stepIndex + 1}"
        )
    }

    private suspend fun followHttpRequestObject(
        current: PipelineValue,
        stepIndex: Int
    ): BinaryPayload {
        val requestObject =
            when (current) {
                is PipelineValue.JsonData -> current.value as? JsonObject
                else -> null
            } ?: throw TtsException(
                context.getString(
                    R.string.http_tts_response_pipeline_step_type_mismatch,
                    stepIndex + 1,
                    "http_request_from_object",
                    "json object"
                )
            )

        val url =
            requestObject["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                .ifBlank {
                    throw TtsException(
                        context.getString(R.string.http_tts_response_pipeline_http_object_url_missing, stepIndex + 1)
                    )
                }

        val httpUrl = url.toHttpUrlOrNull()
            ?: throw TtsException(context.getString(R.string.http_tts_response_pipeline_http_url_invalid, stepIndex + 1))

        val method =
            requestObject["method"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()
                ?.takeIf { it.isNotBlank() }
                ?: "GET"

        if (method != "GET" && method != "POST") {
            throw TtsException(
                context.getString(R.string.http_tts_response_pipeline_http_method_invalid, stepIndex + 1, method)
            )
        }

        val requestBuilder = Request.Builder().url(httpUrl)
        val headersObject = requestObject["headers"] as? JsonObject

        headersObject?.forEach { (key, value) ->
            requestBuilder.addHeader(key, value.jsonPrimitive.content)
        }

        if (method == "POST") {
            val bodyElement =
                requestObject["body"]
                    ?: throw TtsException(
                        context.getString(R.string.http_tts_response_pipeline_http_body_missing, stepIndex + 1)
                    )
            val contentType =
                requestObject["content_type"]?.jsonPrimitive?.contentOrNull
                    ?: requestObject["contentType"]?.jsonPrimitive?.contentOrNull
                    ?: throw TtsException(
                        context.getString(R.string.http_tts_response_pipeline_http_content_type_missing, stepIndex + 1)
                    )

            val bodyText =
                if (bodyElement is JsonPrimitive && bodyElement.isString) {
                    bodyElement.content
                } else {
                    jsonParser.encodeToString(JsonElement.serializer(), bodyElement)
                }

            requestBuilder
                .post(bodyText.toRequestBody(contentType.toMediaType()))
                .addHeader("Content-Type", contentType)
        } else {
            requestBuilder.get()
        }

        return executeBinaryRequest(
            requestBuilder.build(),
            "HTTP TTS pipeline http_request_from_object step ${stepIndex + 1}"
        )
    }

    private fun decodeBase64Value(current: PipelineValue, stepIndex: Int): BinaryPayload {
        val raw = current.asScalarString(stepIndex, "base64_decode")
        val decoded =
            try {
                Base64.decode(raw, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                throw TtsException(
                    context.getString(R.string.http_tts_response_pipeline_base64_decode_failed, stepIndex + 1),
                    cause = e
                )
            }

        return BinaryPayload(
            bytes = decoded,
            contentType = null,
            charset = Charsets.UTF_8
        )
    }

    private fun PipelineValue.asScalarString(stepIndex: Int, stepType: String): String {
        return when (this) {
            is PipelineValue.Text -> value
            is PipelineValue.JsonData -> {
                val primitive = value as? JsonPrimitive
                    ?: throw TtsException(
                        context.getString(
                            R.string.http_tts_response_pipeline_step_type_mismatch,
                            stepIndex + 1,
                            stepType,
                            "string"
                        )
                    )
                primitive.contentOrNull
                    ?: throw TtsException(
                        context.getString(
                            R.string.http_tts_response_pipeline_step_type_mismatch,
                            stepIndex + 1,
                            stepType,
                            "string"
                        )
                    )
            }

            is PipelineValue.Binary -> {
                throw TtsException(
                    context.getString(
                        R.string.http_tts_response_pipeline_step_type_mismatch,
                        stepIndex + 1,
                        stepType,
                        "string"
                    )
                )
            }
        }
    }

    private fun readJsonPath(
        root: JsonElement,
        rawPath: String,
        stepIndex: Int
    ): JsonElement {
        val tokens = parseJsonPath(rawPath)
        var current = root
        for (token in tokens) {
            current =
                when (token) {
                    is JsonPathToken.Key -> {
                        val obj = current as? JsonObject
                            ?: throw buildJsonPathReadException(root, rawPath, stepIndex)
                        obj[token.name]
                            ?: throw buildJsonPathReadException(root, rawPath, stepIndex)
                    }

                    is JsonPathToken.Index -> {
                        val arr = current as? kotlinx.serialization.json.JsonArray
                            ?: throw buildJsonPathReadException(root, rawPath, stepIndex)
                        arr.getOrNull(token.index)
                            ?: throw buildJsonPathReadException(root, rawPath, stepIndex)
                    }
                }
        }
        return current
    }

    private fun buildJsonPathReadException(
        root: JsonElement,
        rawPath: String,
        stepIndex: Int
    ): TtsException {
        val rawResponse = root.toString()
        return TtsException(
            message = context.getString(
                R.string.http_tts_response_pipeline_pick_failed,
                stepIndex + 1,
                rawPath,
                rawResponse
            ),
            errorBody = rawResponse
        )
    }

    private fun parseJsonPath(rawPath: String): List<JsonPathToken> {
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
                    tokens += JsonPathToken.Index(index)
                    cursor = end + 1
                }

                else -> {
                    val start = cursor
                    while (cursor < normalized.length && normalized[cursor] != '.' && normalized[cursor] != '[') {
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

    /**
     * 生成缓存键
     *
     * @param text 文本内容
     * @param rate 语速
     * @param pitch 音调
     * @param voiceId 语音ID
     * @return 缓存键
     */
    private fun generateCacheKey(
        text: String,
        rate: Float,
        pitch: Float,
        voiceId: String?,
        extraParams: Map<String, String>
    ): String {
        val paramsString = extraParams.entries.sortedBy { it.key }.joinToString()
        return "${text.hashCode()}_${rate}_${pitch}_${voiceId ?: "default"}_$paramsString"
    }
    
    /**
     * 清除缓存文件
     */
    private fun clearCache() {
        try {
            for (file in audioCache.values) {
                if (file.exists()) {
                    file.delete()
                }
            }
            audioCache.clear()
        } catch (e: Exception) {
            AppLogger.e(TAG, "清除HTTP TTS缓存失败", e)
        }
    }
} 
