package com.ai.assistance.operit.api.voice

import android.content.Context
import com.ai.assistance.operit.data.preferences.SpeechServiceProfilesPreferences
import kotlinx.coroutines.runBlocking

/** 语音服务工厂，用于创建不同类型的语音服务实例 */
object VoiceServiceFactory {
    /** 语音服务类型枚举 */
    enum class VoiceServiceType {
        /** 基于Android系统TTS的简单语音实现 */
        SIMPLE_TTS,
        /** 基于HTTP请求的TTS实现 */
        HTTP_TTS,
        /** 基于 OpenAI Realtime WebSocket 的 TTS 实现 */
        OPENAI_WS_TTS,
        /** 硅基流动TTS服务 */
        SILICONFLOW_TTS,
        /** MiniMax TTS 服务 */
        MINIMAX_TTS,
        /** MiMo TTS 服务 */
        MIMO_TTS,
        /** 豆包 TTS 服务 */
        DOUBAO_TTS,
        OPENAI_TTS,
        /** 基于 VITS/Piper ONNX Runtime 推理形态的本地 TTS 服务 */
        VITS_TTS,
    }

    /** Creates a voice service from one complete active-profile snapshot. */
    fun createVoiceService(context: Context): VoiceService {
        val profiles = SpeechServiceProfilesPreferences(context)
        val profile = runBlocking { profiles.getCurrentTtsProfile() }
        return createVoiceService(context, profile)
    }

    private fun createVoiceService(
        context: Context,
        profile: SpeechServiceProfilesPreferences.TtsProfile,
    ): VoiceService =
        when (profile.serviceType) {
            VoiceServiceType.SIMPLE_TTS -> {
                SimpleVoiceProvider(
                    context = context,
                    initialLocaleTag = profile.httpConfig.localeTag,
                    initialVoiceId = profile.httpConfig.voiceId,
                    defaultSpeechRate = profile.speechRate,
                    defaultPitch = profile.pitch,
                )
            }
            VoiceServiceType.HTTP_TTS -> {
                HttpVoiceProvider(
                    context = context,
                    defaultSpeechRate = profile.speechRate,
                    defaultPitch = profile.pitch,
                ).apply {
                    setConfiguration(profile.httpConfig)
                }
            }
            VoiceServiceType.OPENAI_WS_TTS -> {
                OpenAIRealtimeVoiceProvider(
                    context = context,
                    endpointUrl = profile.httpConfig.urlTemplate,
                    apiKey = profile.httpConfig.apiKey,
                    model = profile.httpConfig.modelName,
                    initialVoiceId = profile.httpConfig.voiceId,
                    defaultSpeechRate = profile.speechRate,
                )
            }
            VoiceServiceType.SILICONFLOW_TTS -> {
                SiliconFlowVoiceProvider(
                    context = context,
                    apiKey = profile.httpConfig.apiKey,
                    initialVoiceId = profile.httpConfig.voiceId,
                    initialModelName = profile.httpConfig.modelName,
                    defaultSpeechRate = profile.speechRate,
                )
            }
            VoiceServiceType.MINIMAX_TTS -> {
                MiniMaxVoiceProvider(
                    context = context,
                    config = profile.httpConfig,
                    defaultSpeechRate = profile.speechRate,
                    defaultPitch = profile.pitch,
                )
            }
            VoiceServiceType.MIMO_TTS -> {
                MimoVoiceProvider(
                    context = context,
                    config = profile.httpConfig,
                    defaultSpeechRate = profile.speechRate,
                    defaultPitch = profile.pitch,
                )
            }
            VoiceServiceType.DOUBAO_TTS -> {
                DoubaoVoiceProvider(
                    context = context,
                    config = profile.httpConfig,
                    defaultSpeechRate = profile.speechRate,
                    defaultPitch = profile.pitch,
                )
            }
            VoiceServiceType.OPENAI_TTS -> {
                OpenAIVoiceProvider(
                    context = context,
                    endpointUrl = profile.httpConfig.urlTemplate,
                    apiKey = profile.httpConfig.apiKey,
                    model = profile.httpConfig.modelName,
                    initialVoiceId = profile.httpConfig.voiceId,
                    defaultSpeechRate = profile.speechRate,
                )
            }
            VoiceServiceType.VITS_TTS -> {
                VitsVoiceProvider(
                    context = context,
                    config = profile.vitsConfig,
                    defaultSpeechRate = profile.speechRate,
                )
            }
        }

    // 单例实例缓存
    private var instance: VoiceService? = null
    private var currentProfileSnapshot: SpeechServiceProfilesPreferences.TtsProfile? = null

    /**
     * 获取语音服务单例实例
     *
     * @param context 应用上下文
     * @return VoiceService实例
     */
    @Synchronized
    fun getInstance(context: Context): VoiceService {
        val profiles = SpeechServiceProfilesPreferences(context)
        val profile = runBlocking { profiles.getCurrentTtsProfile() }

        if (instance == null || profile != currentProfileSnapshot) {
            instance?.shutdown()
            instance = createVoiceService(context, profile)
            currentProfileSnapshot = profile
        }
        return instance!!
    }

    /** 重置单例实例 在需要更改语音服务类型或释放资源时调用 */
    @Synchronized
    fun resetInstance() {
        instance?.shutdown()
        instance = null
        currentProfileSnapshot = null
    }
}
