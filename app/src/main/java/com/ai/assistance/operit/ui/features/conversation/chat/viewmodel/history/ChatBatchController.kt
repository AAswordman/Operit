package com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.history

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchAction
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchItemResult
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchProgress
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.model.ChatBatchSummary

/** 批量任务随聊天模型存活，逐条等待结果；收起面板不会重复执行或中断已确认的操作。 */
internal class ChatBatchController(
    private val scope: CoroutineScope,
    private val process: suspend (ChatBatchAction, String) -> ChatBatchItemResult,
    private val onFailure: (String, Exception) -> Unit,
    private val onComplete: (ChatBatchSummary) -> Unit,
) {
    private val _progress = MutableStateFlow<ChatBatchProgress?>(null)
    val progress = _progress.asStateFlow()

    fun start(action: ChatBatchAction, ids: List<String>) {
        val targets = ids.distinct()
        if (_progress.value != null || targets.isEmpty()) return
        // 在启动协程前占用任务状态，连续点击也只会提交一次。
        _progress.value = ChatBatchProgress(action, 0, targets.size)
        scope.launch {
            var succeeded = 0
            var skipped = 0
            var failed = 0
            try {
                targets.forEachIndexed { index, id ->
                    val result = try {
                        process(action, id)
                    } catch (cause: CancellationException) {
                        throw cause
                    } catch (cause: Exception) {
                        onFailure(id, cause)
                        ChatBatchItemResult.FAILED
                    }
                    when (result) {
                        ChatBatchItemResult.SUCCEEDED -> succeeded++
                        ChatBatchItemResult.SKIPPED -> skipped++
                        ChatBatchItemResult.FAILED -> failed++
                    }
                    _progress.value = ChatBatchProgress(action, index + 1, targets.size)
                }
                onComplete(ChatBatchSummary(succeeded, skipped, failed))
            } finally {
                _progress.value = null
            }
        }
    }
}
