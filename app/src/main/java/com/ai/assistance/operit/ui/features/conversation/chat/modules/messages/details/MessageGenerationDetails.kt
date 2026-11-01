package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.format.formatCacheHitRate

internal fun hasDisplayableTokenStats(message: ChatMessage): Boolean {
    return message.inputTokens > 0L || message.cachedInputTokens > 0L || message.outputTokens > 0L
}

internal fun hasDisplayableTokenSpeedStats(message: ChatMessage): Boolean {
    return calculateMessageTokenSpeed(message.outputTokens, message.outputDurationMs) != null
}

internal fun hasDisplayableTimingStats(message: ChatMessage): Boolean {
    return message.waitDurationMs > 0L || message.outputDurationMs > 0L
}

internal fun hasDisplayableMessageTimestamp(message: ChatMessage): Boolean {
    return message.completedAt > 0L
}

@Composable
internal fun messageTokenBreakdownText(
    inputTokens: Long,
    outputTokens: Long,
    cachedInputTokens: Long,
): String {
    // 聊天详情和长按信息共用标签与完整数字格式，缓存命中仍属于输入的一部分。
    return buildString {
        append(stringResource(R.string.message_info_token_input_short))
        append(" ")
        append(formatMessageNumber(inputTokens))
        append("  ")
        append(stringResource(R.string.message_info_token_output_short))
        append(" ")
        append(formatMessageNumber(outputTokens))
        append("  ")
        append(stringResource(R.string.message_info_token_cached_short))
        append(" ")
        append(formatMessageNumber(cachedInputTokens))
        append("  ")
        append(stringResource(R.string.message_info_token_hit_rate_short))
        append(" ")
        append(formatCacheHitRate(cachedInputTokens, inputTokens))
    }
}

@Composable
internal fun MessageGenerationDetails(
    message: ChatMessage,
    showMessageTokenStats: Boolean,
    showMessageTimingStats: Boolean,
    showMessageTokenSpeed: Boolean,
    modifier: Modifier = Modifier,
) {
    val showTokens = showMessageTokenStats && hasDisplayableTokenStats(message)
    val showTiming = showMessageTimingStats && hasDisplayableTimingStats(message)
    val tokenSpeed = remember(message.outputTokens, message.outputDurationMs) {
        calculateMessageTokenSpeed(message.outputTokens, message.outputDurationMs)
    }
    val showSpeed = showMessageTokenSpeed && tokenSpeed != null
    if (!showTokens && !showTiming && !showSpeed) return

    // 同一条消息的统计更新不重置展开选择；切换回答版本时恢复默认折叠。
    var expanded by rememberSaveable(message.timestamp, message.selectedVariantIndex) {
        mutableStateOf(false)
    }
    val expansionState = stringResource(if (expanded) R.string.expanded else R.string.collapsed)
    val toggleLabel = stringResource(if (expanded) R.string.collapse else R.string.expand)
    val totalTokens = message.inputTokens + message.outputTokens
    val statsTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button, onClickLabel = toggleLabel) { expanded = !expanded }
                .semantics { stateDescription = expansionState }
                .heightIn(min = 32.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = statsTextColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = if (showTokens) {
                    stringResource(
                        R.string.chat_message_generation_summary,
                        formatCompactMessageTokens(totalTokens),
                    )
                } else {
                    stringResource(R.string.chat_message_generation_details)
                },
                style = MaterialTheme.typography.labelMedium,
                color = statsTextColor,
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showTokens) {
                    Text(
                        text = stringResource(
                            R.string.chat_message_generation_total,
                            formatMessageNumber(totalTokens),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = statsTextColor,
                    )
                    Text(
                        text = messageTokenBreakdownText(
                            message.inputTokens,
                            message.outputTokens,
                            message.cachedInputTokens,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = statsTextColor,
                    )
                }
                if (showTiming) {
                    Text(
                        text = stringResource(
                            R.string.chat_message_generation_timing,
                            formatMessageDuration(message.waitDurationMs),
                            formatMessageDuration(message.outputDurationMs),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = statsTextColor,
                    )
                }
                if (showMessageTokenSpeed && tokenSpeed != null) {
                    Text(
                        text = stringResource(
                            R.string.chat_message_token_speed_compact,
                            formatMessageTokenSpeed(tokenSpeed),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = statsTextColor,
                    )
                }
            }
        }
    }
}
