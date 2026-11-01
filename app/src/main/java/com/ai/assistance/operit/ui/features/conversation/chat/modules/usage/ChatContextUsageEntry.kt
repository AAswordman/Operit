package com.ai.assistance.operit.ui.features.conversation.chat.modules.usage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.context.ContextWindowBreakdownEstimator
import com.ai.assistance.operit.ui.features.conversation.chat.viewmodel.ChatViewModel

/** 输入设置里的统计入口，整行打开明细。 */
@Composable
fun ChatContextUsageEntry(
    actualViewModel: ChatViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val windowTokens by actualViewModel.currentWindowSize.collectAsState()
    val maxWindowSizeInK by actualViewModel.maxWindowSizeInK.collectAsState()
    val inputTokens by actualViewModel.inputTokenCount.collectAsState()
    val outputTokens by actualViewModel.outputTokenCount.collectAsState()
    val enableSummary by actualViewModel.enableSummary.collectAsState()
    val summaryThreshold by actualViewModel.summaryTokenThreshold.collectAsState()
    val maxTokens = (maxWindowSizeInK * 1024f).toLong()
    val ratio = if (maxTokens > 0L) windowTokens.toFloat() / maxTokens.toFloat() else 0f
    val indicatorColor = when {
        ratio >= 0.9f -> MaterialTheme.colorScheme.error
        enableSummary && summaryThreshold > 0f && ratio >= summaryThreshold ->
            MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier.size(18.dp),
            color = indicatorColor,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeWidth = 2.dp
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.context_usage_entry_title),
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    // 显式使用主题文字色，避免弹窗继承默认黑色而破坏深色主题对比。
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatUsagePercent(windowTokens, maxTokens, decimals = 0),
                    fontSize = 13.sp,
                    color = indicatorColor
                )
            }
            Text(
                text = stringResource(
                    R.string.context_usage_entry_detail,
                    ContextWindowBreakdownEstimator.formatTokenCount(windowTokens),
                    ContextWindowBreakdownEstimator.formatTokenCount(maxTokens),
                    ContextWindowBreakdownEstimator.formatTokenCount(inputTokens + outputTokens)
                ),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}