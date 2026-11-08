package com.ai.assistance.operit.core.chat.context

import com.ai.assistance.operit.core.tools.runtime.ToolProgressBus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 每次总结单独维护进度，重试等待期间不让回滚事件覆盖重试标签。 */
internal class SummaryProgressReporter(
    private val preparingMessage: String,
    private val retryingMessage: String,
    private val onProgress: (String) -> Unit,
    private val onRetryError: suspend (String) -> Unit,
    private val publishProgress: (Float, String) -> Unit = { progress, message ->
        ToolProgressBus.update(ToolProgressBus.SUMMARY_PROGRESS_TOOL_NAME, progress, message)
    },
) {
    private var progress = 0.05f
    private var message = preparingMessage
    private var retrying = false
    private var lastMessage: String? = null

    @Synchronized
    fun start() = publish(progress, message)

    @Synchronized
    fun update(progress: Float, message: String) {
        this.progress = progress
        this.message = message
        if (!retrying) publish(progress, message)
    }

    suspend fun retry(error: String) {
        currentCoroutineContext().ensureActive()
        synchronized(this) {
            retrying = true
            publish(0.05f, retryingMessage)
        }
        // 相同错误也逐次交付提示，不能用进度文本的去重规则吞掉下一次失败。
        onRetryError(error)
    }

    fun rollback() {
        update(0.05f, preparingMessage)
    }

    @Synchronized
    fun receivedContent() {
        if (!retrying) return
        retrying = false
        publish(progress, message)
    }

    @Synchronized
    fun complete(message: String) {
        retrying = false
        update(1f, message)
    }

    private fun publish(progress: Float, message: String) {
        publishProgress(progress, message)
        if (lastMessage != message) {
            lastMessage = message
            onProgress(message)
        }
    }
}
