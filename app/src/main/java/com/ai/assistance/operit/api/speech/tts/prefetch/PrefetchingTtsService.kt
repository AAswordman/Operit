package com.ai.assistance.operit.api.speech.tts.prefetch

import com.ai.assistance.operit.api.speech.tts.TtsService

/** 云端实现可接收整段文本，内部并行预取分句并按原文顺序播放。 */
interface PrefetchingTtsService : TtsService
