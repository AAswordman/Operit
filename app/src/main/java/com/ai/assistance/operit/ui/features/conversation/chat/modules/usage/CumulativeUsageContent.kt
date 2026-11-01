package com.ai.assistance.operit.ui.features.conversation.chat.modules.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.context.TokenUsageBreakdown

/** 累计用量来自会话计数，不按当前窗口或可见消息重算。 */
@Composable
internal fun CumulativeUsageContent(usage: TokenUsageBreakdown) {
    val total = usage.totalTokens
    val colors = rememberContextWindowCategoryColors()
    val tokensByCategory = usage.categoryTokens
    val categories = listOf(
        Triple(R.string.context_panel_user_messages, tokensByCategory[0], colors.userMessages),
        Triple(R.string.context_panel_assistant_messages, tokensByCategory[1], colors.assistantMessages),
        Triple(R.string.context_panel_tool_results, tokensByCategory[2], colors.toolResults),
        Triple(R.string.context_panel_system_prompt, tokensByCategory[3], colors.systemPrompt),
        Triple(R.string.context_panel_resident_tools, tokensByCategory[4], colors.residentTools),
        Triple(R.string.context_panel_mcp_tools, tokensByCategory[5], colors.mcpTools),
        Triple(R.string.context_panel_skills, tokensByCategory[6], colors.skills),
        Triple(R.string.context_panel_summary, tokensByCategory[7], colors.summary),
        Triple(R.string.context_panel_other, tokensByCategory[8], colors.other),
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.context_usage_cumulative_scope),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatUsageTokens(total),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                stringResource(R.string.context_usage_cumulative_total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (total > 0L) {
                Row(Modifier.fillMaxSize()) {
                    categories.filter { it.second > 0L }.forEach { (_, tokens, color) ->
                        Box(Modifier.weight(tokens.toFloat()).fillMaxHeight().background(color))
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { (label, tokens, color) ->
                CumulativeUsageRow(stringResource(label), tokens, total, color)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(R.string.context_usage_cumulative_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CumulativeUsageRow(label: String, tokens: Long, total: Long, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatUsageTokens(tokens),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                formatUsagePercent(tokens, total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
