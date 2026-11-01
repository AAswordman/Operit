package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering.tooltiming

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageSection
import com.ai.assistance.operit.data.model.tooltiming.ToolCallExecution
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.markdown.LocalMarkdownRenderMode
import com.ai.assistance.operit.ui.common.markdown.MarkdownRenderMode
import kotlinx.coroutines.delay

internal val LocalToolTimingIndex = staticCompositionLocalOf { ToolTimingIndex.Empty }
internal val LocalInsideToolGroup = staticCompositionLocalOf { false }

/** 正文流和计时状态分别订阅，工具等待输出时仍能刷新标题。 */
@Composable
internal fun rememberMessageToolExecutions(message: ChatMessage): List<ToolCallExecution> {
    val state = message.toolTimingState
    return if (state != null) {
        val records by state.records.collectAsState()
        records
    } else {
        remember(message.content, message.sections) {
            message.resolvedSections().filterIsInstance<MessageSection.ToolCall>().mapNotNull { it.execution }
        }
    }
}

/** 只有仍在执行的可见标题刷新；完成后读取消息中的固定耗时。 */
@Composable
internal fun toolTimingTitle(
    summary: ToolTimingSummary,
    thoughtCount: Int = 0,
    searchCount: Int = 0,
): String {
    val streaming = LocalMarkdownRenderMode.current == MarkdownRenderMode.STREAMING
    val ticking = summary.ticking(streaming)
    val now by produceState(SystemClock.elapsedRealtime(), summary, ticking) {
        value = SystemClock.elapsedRealtime()
        while (ticking) {
            // 亚秒阶段以较短间隔刷新，让毫秒显示在工具尚未返回时也能推进。
            val elapsed = summary.elapsedMillis(value, streaming)
            delay(if (elapsed != null && elapsed < 1_000L) 100L else 1_000L)
            value = SystemClock.elapsedRealtime()
        }
    }
    val millis = summary.elapsedMillis(now, streaming)
    val elapsed = when {
        millis == null -> ""
        millis < 1_000L -> stringResource(R.string.tool_timing_elapsed_millis, millis)
        else -> stringResource(R.string.tool_timing_elapsed, millis / 1_000L)
    }
    val status = when {
        summary.complete -> R.string.tool_timing_completed
        summary.stopped -> R.string.tool_timing_stopped
        thoughtCount > 0 -> R.string.tool_timing_running
        else -> R.string.tool_call_block
    }
    val title = if (thoughtCount > 0) {
        stringResource(R.string.tool_timing_combined, stringResource(status), elapsed, thoughtCount, summary.calls.size)
    } else {
        stringResource(R.string.tool_timing_simple, stringResource(status), summary.calls.size, elapsed)
    }
    return if (searchCount > 0) title + " • " + stringResource(R.string.tool_timing_searches, searchCount) else title
}