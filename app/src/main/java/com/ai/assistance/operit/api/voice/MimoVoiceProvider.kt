package com.ai.assistance.operit.api.voice

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import kotlinx.coroutines.flow.Flow

class MimoVoiceProvider(
    private val context: Context,
    private val config: SpeechServicesPreferences.TtsHttpConfig,
    defaultSpeechRate: Float,
    defaultPitch: Float,
) : VoiceService {

    companion object {
        const val DEFAULT_ENDPOINT_URL = "https://api.xiaomimimo.com/v1/chat/completions"
        const val DEFAULT_MODEL_NAME = "mimo-v2.5-tts"
        const val DEFAULT_VOICE_ID = "mimo_default"

        val AVAILABLE_VOICES =
            listOf(
                VoiceService.Voice(
                    id = DEFAULT_VOICE_ID,
                    name = "MiMo Default",
                    locale = "multi",
                    gender = "NEUTRAL"
                ),
                VoiceService.Voice(id = "冰糖", name = "冰糖", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "茉莉", name = "茉莉", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "苏打", name = "苏打", locale = "zh-CN", gender = "MALE"),
                VoiceService.Voice(id = "白桦", name = "白桦", locale = "zh-CN", gender = "MALE"),
                VoiceService.Voice(id = "Mia", name = "Mia", locale = "en-US", gender = "FEMALE"),
                VoiceService.Voice(id = "Chloe", name = "Chloe", locale = "en-US", gender = "FEMALE"),
                VoiceService.Voice(id = "Milo", name = "Milo", locale = "en-US", gender = "MALE"),
                VoiceService.Voice(id = "Dean", name = "Dean", locale = "en-US", gender = "MALE")
            )

        private val DEFAULT_RESPONSE_PIPELINE =
            listOf(
                HttpTtsResponsePipelineStep(type = HttpTtsResponsePipelineStep.TYPE_PARSE_JSON),
                HttpTtsResponsePipelineStep(
                    type = HttpTtsResponsePipelineStep.TYPE_PICK,
                    path = "choices[0].message.audio.data"
                ),
                HttpTtsResponsePipelineStep(type = HttpTtsResponsePipelineStep.TYPE_BASE64_DECODE)
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
            throw TtsException(context.getString(R.string.mimo_tts_error_api_key_not_set))
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
            throw TtsException(context.getString(R.string.mimo_tts_error_api_key_not_set))
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
            apiKey = "",
            headers = config.headers + mapOf("api-key" to config.apiKey.trim()),
            httpMethod = "POST",
            requestBody =
                """
                {
                  "model": "{model}",
                  "messages": [
                    {
                      "role": "user",
                      "content": "请自然朗读。语速设置：{rate}x，音高设置：{pitch}x。"
                    },
                    {
                      "role": "assistant",
                      "content": "{text}"
                    }
                  ],
                  "audio": {
                    "format": "wav",
                    "voice": "{voice_id}"
                  },
                  "stream": false
                }
                """.trimIndent(),
            contentType = "application/json",
            voiceId = selectedVoiceId,
            modelName = config.modelName.ifBlank { DEFAULT_MODEL_NAME },
            responsePipeline = DEFAULT_RESPONSE_PIPELINE
        )
    }
}
