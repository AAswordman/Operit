package com.ai.assistance.operit.api.voice

import android.content.Context
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences

class DoubaoVoiceProvider(
    context: Context,
    private val config: SpeechServicesPreferences.TtsHttpConfig,
    defaultSpeechRate: Float,
    defaultPitch: Float,
) : HttpVoiceProvider(context, defaultSpeechRate, defaultPitch) {

    companion object {
        const val DEFAULT_ENDPOINT_URL = "https://openspeech.bytedance.com/api/v1/tts"
        const val DEFAULT_CLUSTER = "volcano_tts"
        const val DEFAULT_VOICE_ID = "BV700_V2_streaming"

        val AVAILABLE_VOICES =
            listOf(
                VoiceService.Voice(
                    id = DEFAULT_VOICE_ID,
                    name = DEFAULT_VOICE_ID,
                    locale = "zh-CN",
                    gender = "NEUTRAL",
                )
            )

        private val RESPONSE_PIPELINE =
            listOf(
                HttpTtsResponsePipelineStep(type = HttpTtsResponsePipelineStep.TYPE_PARSE_JSON),
                HttpTtsResponsePipelineStep(
                    type = HttpTtsResponsePipelineStep.TYPE_PICK,
                    path = "data",
                ),
                HttpTtsResponsePipelineStep(type = HttpTtsResponsePipelineStep.TYPE_BASE64_DECODE),
            )
    }

    private var selectedVoiceId: String = config.voiceId.ifBlank { DEFAULT_VOICE_ID }

    override suspend fun initialize(): Boolean {
        setConfiguration(buildHttpConfig())
        return super.initialize()
    }

    override suspend fun speak(
        text: String,
        interrupt: Boolean,
        rate: Float?,
        pitch: Float?,
        extraParams: Map<String, String>,
    ): Boolean {
        setConfiguration(buildHttpConfig())
        val requestVoice = extraParams["voice"]?.takeIf { it.isNotBlank() } ?: selectedVoiceId
        val cluster = extraParams["cluster"]?.takeIf { it.isNotBlank() } ?: DEFAULT_CLUSTER
        return super.speak(
            text = text,
            interrupt = interrupt,
            rate = rate,
            pitch = pitch,
            extraParams = extraParams + mapOf("voice" to requestVoice, "cluster" to cluster),
        )
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
            headers = config.headers + mapOf("Authorization" to "Bearer;{apiKey}"),
            httpMethod = "POST",
            requestBody = """{"app":{"appid":"{model}","token":"{apiKey}","cluster":"{cluster}"},"user":{"uid":"operit"},"audio":{"voice_type":"{voice}","encoding":"mp3","speed_ratio":{rate},"pitch_ratio":{pitch}},"request":{"reqid":"{uuid}","text":"{text}","operation":"query"}}""",
            contentType = "application/json",
            localeTag = config.localeTag.ifBlank { "zh-CN" },
            voiceId = selectedVoiceId,
            modelName = config.modelName,
            responsePipeline = RESPONSE_PIPELINE,
        )
    }
}
