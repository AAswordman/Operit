package com.ai.assistance.operit.ui.features.management.settings.components.speech.workbench

import com.ai.assistance.operit.api.speech.tts.TtsServiceFactory.VoiceServiceType

/** 按当前实现是否消费参数分别显示滑块，不把服务接收参数等同于保证声学效果。 */
internal data class TtsAdjustmentControls(val rate: Boolean, val pitch: Boolean)

internal fun TtsProfileDraft.adjustmentControls(): TtsAdjustmentControls = when (profile.serviceType) {
    VoiceServiceType.SIMPLE_TTS,
    VoiceServiceType.SHERPA_ONNX_TTS,
    VoiceServiceType.DOUBAO_TTS,
    VoiceServiceType.MIMO_TTS -> TtsAdjustmentControls(rate = true, pitch = true)
    VoiceServiceType.OPENAI_TTS,
    VoiceServiceType.OPENAI_WS_TTS,
    VoiceServiceType.SILICONFLOW_TTS,
    VoiceServiceType.MINIMAX_TTS,
    VoiceServiceType.VITS_TTS -> TtsAdjustmentControls(rate = true, pitch = false)
    VoiceServiceType.HTTP_TTS -> {
        val config = profile.httpConfig
        // POST 仅替换正文，其他请求方式走 GET 并替换 URL；自定义请求头也支持占位符。
        val template = if (config.httpMethod.uppercase() == "POST") config.requestBody else config.urlTemplate
        val templates = listOf(template, headers)
        TtsAdjustmentControls(
            rate = templates.any { it.contains("{rate}", ignoreCase = true) },
            pitch = templates.any { it.contains("{pitch}", ignoreCase = true) },
        )
    }
}
