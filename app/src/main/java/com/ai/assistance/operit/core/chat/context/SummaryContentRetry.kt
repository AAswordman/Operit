package com.ai.assistance.operit.core.chat.context

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 仅在完整生成并清理后的正文为空时重新生成一次，请求异常继续交由原有请求层处理。 */
internal suspend fun generateNonEmptySummary(
    emptyResultMessage: String,
    onEmptyRetry: suspend () -> Unit,
    generate: suspend () -> String,
): String {
    // 两次完整生成共用同一批历史；不在这里捕获或重试网络异常。
    repeat(2) { attempt ->
        currentCoroutineContext().ensureActive()
        val content = generate()
        currentCoroutineContext().ensureActive()
        if (content.isNotBlank()) return content
        if (attempt == 0) onEmptyRetry()
    }
    throw IllegalStateException(emptyResultMessage)
}
