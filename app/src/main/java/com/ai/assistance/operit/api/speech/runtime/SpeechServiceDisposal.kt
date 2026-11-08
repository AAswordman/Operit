package com.ai.assistance.operit.api.speech.runtime

import com.ai.assistance.operit.util.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 页面销毁不能等待原生推理锁；清理独立于页面协程，确保离页后仍归还模型引用。 */
internal object SpeechServiceDisposal {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun submit(tag: String, release: suspend () -> Unit) {
        scope.launch {
            try {
                release()
            } catch (failure: Exception) {
                AppLogger.e(tag, "后台释放语音服务失败", failure)
            }
        }
    }
}