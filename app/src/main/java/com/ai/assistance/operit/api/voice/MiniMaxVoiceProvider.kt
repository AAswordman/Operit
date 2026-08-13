package com.ai.assistance.operit.api.voice

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import kotlinx.coroutines.flow.Flow

class MiniMaxVoiceProvider(
    private val context: Context,
    private val config: SpeechServicesPreferences.TtsHttpConfig,
    defaultSpeechRate: Float,
    defaultPitch: Float,
) : VoiceService {

    companion object {
        const val DEFAULT_ENDPOINT_URL = "https://api.minimaxi.com/v1/t2a_v2"
        const val DEFAULT_MODEL_NAME = "speech-2.8-hd"
        const val DEFAULT_VOICE_ID = "male-qn-qingse"

        val AVAILABLE_VOICES =
            listOf(
                VoiceService.Voice(
                    id = DEFAULT_VOICE_ID,
                    name = DEFAULT_VOICE_ID,
                    locale = "zh-CN",
                    gender = "MALE"
                )
            )

        private val DEFAULT_RESPONSE_PIPELINE =
            listOf(
                HttpTtsResponsePipelineStep(type = HttpTtsResponsePipelineStep.TYPE_PARSE_JSON),
                HttpTtsResponsePipelineStep(
                    type = HttpTtsResponsePipelineStep.TYPE_PICK,
                    path = "data.audio"
                ),
                HttpTtsResponsePipelineStep(type = HttpTtsResponsePipelineStep.TYPE_HTTP_GET)
            )
    }

    private val delegate = HttpVoiceProvider(context, defaultSpeechRate, defaultPitch)
    private var selectedVoiceId: String = config.voiceId.ifBlank { DEFAULT_VOICE_ID }

    override val isInitialized: Boolean
        get() = delegate.isInitialized

    override val isSpeaking: Boolean
        get() = delegate.isSpeaking

    override val speakingStateFlow: Flow<Boolean>
        get() = delegate.speakingStateFlow

    override suspend fun initialize(): Boolean {
        if (config.apiKey.isBlank()) {
            throw TtsException(context.getString(R.string.minimax_tts_error_api_key_not_set))
        }

        delegate.setConfiguration(buildHttpConfig())
        return delegate.initialize()
    }

    override suspend fun speak(
        text: String,
        interrupt: Boolean,
        rate: Float?,
        pitch: Float?,
        extraParams: Map<String, String>
    ): Boolean {
        if (config.apiKey.isBlank()) {
            throw TtsException(context.getString(R.string.minimax_tts_error_api_key_not_set))
        }

        delegate.setConfiguration(buildHttpConfig())

        val resolvedVoiceId = extraParams["voice"]?.takeIf { it.isNotBlank() } ?: selectedVoiceId
        val resolvedModelName =
            extraParams["model"]?.takeIf { it.isNotBlank() }
                ?: config.modelName.takeIf { it.isNotBlank() }
                ?: DEFAULT_MODEL_NAME

        return delegate.speak(
            text = text,
            interrupt = interrupt,
            rate = rate,
            pitch = pitch,
            extraParams =
                extraParams +
                    mapOf(
                        "model" to resolvedModelName,
                        "voice_id" to resolvedVoiceId
                    )
        )
    }

    override suspend fun stop(): Boolean = delegate.stop()

    override suspend fun pause(): Boolean = delegate.pause()

    override suspend fun resume(): Boolean = delegate.resume()

    override fun shutdown() {
        delegate.shutdown()
    }

    override suspend fun getAvailableVoices(): List<VoiceService.Voice> = AVAILABLE_VOICES

    override suspend fun setVoice(voiceId: String): Boolean {
        selectedVoiceId = voiceId.ifBlank { DEFAULT_VOICE_ID }
        return true
    }

    private fun buildHttpConfig(): SpeechServicesPreferences.TtsHttpConfig {
        return SpeechServicesPreferences.TtsHttpConfig(
            urlTemplate = config.urlTemplate.ifBlank { DEFAULT_ENDPOINT_URL },
            apiKey = config.apiKey.trim(),
            headers = config.headers,
            httpMethod = "POST",
            requestBody =
                """
                {
                  "model": "{model}",
                  "text": "{text}",
                  "stream": false,
                  "output_format": "url",
                  "voice_setting": {
                    "voice_id": "{voice_id}",
                    "speed": {rate},
                    "vol": 1,
                    "pitch": 0
                  },
                  "audio_setting": {
                    "sample_rate": 32000,
                    "bitrate": 128000,
                    "format": "mp3",
                    "channel": 1
                  },
                  "subtitle_enable": false
                }
                """.trimIndent(),
            contentType = "application/json",
            voiceId = selectedVoiceId,
            modelName = config.modelName.ifBlank { DEFAULT_MODEL_NAME },
            responsePipeline = DEFAULT_RESPONSE_PIPELINE
        )
    }
}
