package com.ai.assistance.operit.api.speech.tts.prefetch

import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 音频写入失败或轮次已取消时清掉半成品，成功后把文件所有权交给队列或缓存。 */
internal suspend fun File.writeTtsAudio(bytes: ByteArray): File {
    try {
        currentCoroutineContext().ensureActive()
        writeBytes(bytes)
        currentCoroutineContext().ensureActive()
        return this
    } catch (error: Exception) {
        delete()
        throw error
    }
}
