package com.ai.assistance.operit.api.voice

import android.content.Context
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import kotlinx.coroutines.flow.Flow

/**
 * 阶跃星辰 TTS（Step TTS）语音服务实现
 *
 * 官方接口文档：https://platform.stepfun.com/docs/zh/api-reference/audio/create-audio
 * 接口直接返回二进制音频流（默认 mp3），因此响应管道留空，原始响应即音频数据。
 * 语速经由全局语速（{rate}，0.5~2）映射到请求体的 speed 字段，与官方取值范围一致。
 *
 * 专属可选参数保存在 TtsHttpConfig.options 中（键见 OPTION_* 常量），由设置界面写入：
 * - response_format：音频输出格式，官方默认 mp3
 * - volume：音量，官方取值 0.1~2.0，默认 1
 * - voice_label：情绪/语速演绎风格标签（仅 step-tts-2 / step-tts-mini 支持）
 * - instruction：全局自然语言指导（仅 stepaudio-2.5-tts / stepaudio-3-tts 支持）
 */
class StepTtsVoiceProvider(
    private val context: Context,
    private val config: SpeechServicesPreferences.TtsHttpConfig
) : VoiceService {

    companion object {
        const val DEFAULT_ENDPOINT_URL = "https://api.stepfun.com/v1/audio/speech"

        /**
         * 默认模型。
         *
         * 取 `stepaudio-2.5-tts` 而不是文档里更便宜的 `step-tts-mini`：后者是否仍被当前接口
         * 接受无法离线确认（需要有效 API key 打真实请求），而 2.5-tts 是实测可用且官方文档
         * 在列的那个。默认值必须一次就能用——选一个可能已下线的模型，用户首次使用即失败。
         * 想压成本可以在设置里手动改成旧模型。
         */
        const val DEFAULT_MODEL_NAME = "stepaudio-2.5-tts"
        const val DEFAULT_VOICE_ID = "jingdiannvsheng"
        const val DEFAULT_RESPONSE_FORMAT = "mp3"
        const val DEFAULT_VOLUME = "1"

        const val OPTION_RESPONSE_FORMAT = "response_format"
        const val OPTION_VOLUME = "volume"
        const val OPTION_VOICE_LABEL = "voice_label"
        const val OPTION_INSTRUCTION = "instruction"

        /**
         * 请求体模板里承载 voice_label 值的占位符名。
         *
         * 与 [OPTION_VOICE_LABEL] 区分：前者是配置项 key，后者是模板变量名。
         * 用占位符而不是直接拼字符串，才能让 HttpVoiceProvider 统一做 JSON 转义。
         */
        const val OPTION_VOICE_LABEL_VALUE = "voice_label_value"

        val AVAILABLE_MODELS = listOf(
            "step-tts-mini",
            "step-tts-2",
            "stepaudio-2.5-tts",
            "stepaudio-3-tts"
        )

        /**
         * 可选响应格式。刻意不含 `pcm`。
         *
         * 播放链路把响应字节原样写进 `tts_*.bin` 再交给 `MediaPlayer.setDataSource(fd)`，
         * MediaPlayer 靠内容嗅探识别封装格式。裸 PCM 没有容器头（也没有采样率/位深信息），
         * `prepare()` 必然失败——让用户选一个播放不出来的格式没有意义。
         * 若将来要支持 PCM，需要在播放层先封装成 WAV（加 RIFF 头），而不是放行这个选项。
         */
        val RESPONSE_FORMATS = listOf("mp3", "wav", "flac", "opus")

        /** 官方情绪标签，写入请求体 voice_label.emotion */
        val EMOTION_LABELS =
            listOf(
                "高兴",
                "非常高兴",
                "悲伤",
                "生气",
                "非常生气",
                "撒娇",
                "恐惧",
                "惊讶",
                "兴奋",
                "钦佩",
                "困惑"
            )

        /** 官方语速/演绎风格标签，写入请求体 voice_label.style */
        val STYLE_LABELS =
            listOf(
                "慢速",
                "极慢",
                "快速",
                "极快",
                "冷漠",
                "尴尬",
                "沮丧",
                "骄傲",
                "温柔",
                "甜美",
                "豪爽",
                "严肃",
                "傲慢",
                "老年",
                "吼叫",
                "阴阳怪气",
                "磕巴"
            )

        val AVAILABLE_VOICES =
            listOf(
                VoiceService.Voice(id = "vibrant-youth", name = "Vibrant Youth", locale = "multi", gender = "NEUTRAL"),
                VoiceService.Voice(id = "lively-girl", name = "Lively Girl", locale = "multi", gender = "FEMALE"),
                VoiceService.Voice(id = "soft-spoken-gentleman", name = "Soft-spoken Gentleman", locale = "multi", gender = "MALE"),
                VoiceService.Voice(id = "magnetic-voiced-male", name = "Magnetic-voiced Male", locale = "multi", gender = "MALE"),
                VoiceService.Voice(id = "zixinnansheng", name = "自信男声", locale = "zh-CN", gender = "MALE"),
                VoiceService.Voice(id = "elegantgentle-female", name = "气质温婉", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "livelybreezy-female", name = "活力轻快", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "wenrounansheng", name = "温柔男声", locale = "zh-CN", gender = "MALE"),
                VoiceService.Voice(id = "wenrougongzi", name = "温柔公子", locale = "zh-CN", gender = "MALE"),
                VoiceService.Voice(id = "yuanqinansheng", name = "元气男声", locale = "zh-CN", gender = "MALE"),
                VoiceService.Voice(id = "jingdiannvsheng", name = "经典女声", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "wenroushunv", name = "温柔熟女", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "tianmeinvsheng", name = "甜美女声", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "qingchunshaonv", name = "清纯少女", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "cixingnansheng", name = "磁性男声", locale = "zh-CN", gender = "MALE"),
                VoiceService.Voice(id = "yuanqishaonv", name = "元气少女", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "linjiajiejie", name = "邻家姐姐", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "zhengpaiqingnian", name = "正派青年", locale = "zh-CN", gender = "MALE"),
                VoiceService.Voice(id = "qingniandaxuesheng", name = "青年大学生", locale = "zh-CN", gender = "NEUTRAL"),
                VoiceService.Voice(id = "boyinnansheng", name = "播音男声", locale = "zh-CN", gender = "MALE"),
                VoiceService.Voice(id = "ruyananshi", name = "儒雅男士", locale = "zh-CN", gender = "MALE"),
                VoiceService.Voice(id = "shenchennanyin", name = "深沉男音", locale = "zh-CN", gender = "MALE"),
                VoiceService.Voice(id = "qinqienvsheng", name = "亲切女声", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "wenrounvsheng", name = "温柔女声", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "jilingshaonv", name = "机灵少女", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "ruanmengnvsheng", name = "软萌女声", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "youyanvsheng", name = "优雅女声", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "lengyanyujie", name = "冷艳御姐", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "shuangkuaijiejie", name = "爽快姐姐", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "wenjingxuejie", name = "文静学姐", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "linjiameimei", name = "邻家妹妹", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "zhixingjiejie", name = "知性姐姐", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "shuangkuainansheng", name = "爽快男声", locale = "zh-CN", gender = "MALE"),
                VoiceService.Voice(id = "ganliannvsheng", name = "干练女声", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "qinhenvsheng", name = "亲和女声", locale = "zh-CN", gender = "FEMALE"),
                VoiceService.Voice(id = "huolinvsheng", name = "活力女声", locale = "zh-CN", gender = "FEMALE")
            )

        /** 判定标签归属：情绪标签进 voice_label.emotion，其余按演绎风格进 voice_label.style */
        private fun voiceLabelField(label: String): String =
            if (EMOTION_LABELS.contains(label)) "emotion" else "style"
    }

    private val delegate = HttpVoiceProvider(context)
    private var selectedVoiceId: String = config.voiceId.ifBlank { DEFAULT_VOICE_ID }

    /** 读取阶跃专属配置项，空白视为未配置 */
    private fun option(key: String): String = config.options[key]?.trim().orEmpty()

    override val isInitialized: Boolean
        get() = delegate.isInitialized

    override val isSpeaking: Boolean
        get() = delegate.isSpeaking

    override val speakingStateFlow: Flow<Boolean>
        get() = delegate.speakingStateFlow

    override suspend fun initialize(): Boolean {
        if (config.apiKey.isBlank()) {
            throw TtsException(context.getString(R.string.step_tts_error_api_key_not_set))
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
            throw TtsException(context.getString(R.string.step_tts_error_api_key_not_set))
        }

        delegate.setConfiguration(buildHttpConfig())

        val resolvedVoiceId = extraParams["voice"]?.takeIf { it.isNotBlank() } ?: selectedVoiceId
        val resolvedModelName =
            extraParams["model"]?.takeIf { it.isNotBlank() }
                ?: config.modelName.takeIf { it.isNotBlank() }
                ?: DEFAULT_MODEL_NAME

        // instruction 是自由文本，经由 {instruction} 占位符由 HttpVoiceProvider 做 JSON 转义，
        // 因此必须在 extraParams 中携带；未配置时模板不含该占位符，无需注入。
        val instructionParams =
            option(OPTION_INSTRUCTION).takeIf { it.isNotBlank() }
                ?.let { mapOf(OPTION_INSTRUCTION to it) }
                ?: emptyMap()

        // voice_label 同理：它是来自设置界面的标签文本，直接拼进请求体模板会绕过
        // HttpVoiceProvider 的 JSON 转义（目前标签是固定下拉所以没出事，但 options
        // 也可能被其它途径写入）。走 {voice_label_value} 占位符复用同一套转义。
        val voiceLabelParams =
            option(OPTION_VOICE_LABEL).takeIf { it.isNotBlank() }
                ?.let { mapOf(OPTION_VOICE_LABEL_VALUE to it) }
                ?: emptyMap()

        return delegate.speak(
            text = text,
            interrupt = interrupt,
            rate = rate,
            pitch = pitch,
            extraParams =
                extraParams +
                    mapOf(
                        "model" to resolvedModelName,
                        "voice_id" to resolvedVoiceId,
                        OPTION_RESPONSE_FORMAT to
                            option(OPTION_RESPONSE_FORMAT).ifBlank { DEFAULT_RESPONSE_FORMAT },
                        OPTION_VOLUME to
                            option(OPTION_VOLUME).ifBlank { DEFAULT_VOLUME }
                    ) +
                    instructionParams +
                    voiceLabelParams
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
            requestBody = buildRequestBody(),
            contentType = "application/json",
            voiceId = selectedVoiceId,
            modelName = config.modelName.ifBlank { DEFAULT_MODEL_NAME },
            // 官方接口直接返回二进制音频流，无需解析步骤
            responsePipeline = emptyList()
        )
    }

    /**
     * 构建请求体模板。未配置的专属选项（voice_label / instruction）不写入请求体，
     * 由服务端按官方默认值处理；已配置的选项按官方字段名原样下发，
     * 与所选模型不兼容时（如 voice_label 配 stepaudio 系列）由服务端报错提示。
     *
     * 以成员列表拼接而非字符串拼接可选字段：joinToString 在空集合上会返回 prefix 本身，
     * 直接拼接会产出 ",}" 尾逗号，服务端返回 400 invalid request format。
     */
    private fun buildRequestBody(): String {
        val voiceLabel = option(OPTION_VOICE_LABEL)
        val instruction = option(OPTION_INSTRUCTION)

        val members =
            buildList {
                add("\"model\": \"{model}\"")
                add("\"input\": \"{text}\"")
                add("\"voice\": \"{voice_id}\"")
                add("\"response_format\": \"{response_format}\"")
                add("\"speed\": {rate}")
                add("\"volume\": {volume}")
                if (voiceLabel.isNotBlank()) {
                    // 值走 {voice_label_value} 占位符，由 HttpVoiceProvider 做 JSON 转义；
                    // 字段名（emotion / style）是从固定枚举映射出来的，无特殊字符。
                    add("\"voice_label\": {\"${voiceLabelField(voiceLabel)}\": \"{voice_label_value}\"}")
                }
                if (instruction.isNotBlank()) {
                    add("\"instruction\": \"{instruction}\"")
                }
            }

        return "{\n  " + members.joinToString(",\n  ") + "\n}"
    }
}
