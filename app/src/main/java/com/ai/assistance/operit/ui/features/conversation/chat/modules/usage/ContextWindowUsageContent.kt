package com.ai.assistance.operit.ui.features.conversation.chat.modules.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.context.ContextWindowBreakdown

@Composable
internal fun ContextWindowUsageContent(
    breakdown: ContextWindowBreakdown,
    lastRequestInputTokens: Long?,
    canSummarize: Boolean,
    isSummarizing: Boolean,
    onSummarizeNow: () -> Unit
) {
    val colors = rememberContextWindowCategoryColors()
    val progressColor = when {
        breakdown.usageRatio >= 0.9f -> MaterialTheme.colorScheme.error
        breakdown.enableSummary && breakdown.autoSummaryThreshold > 0f &&
            breakdown.usageRatio >= breakdown.autoSummaryThreshold -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.context_usage_window_estimate),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                ) {
                    Text(
                        stringResource(R.string.context_panel_estimate_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(formatUsageTokens(breakdown.windowTokens))
                        withStyle(SpanStyle(fontSize = 14.sp, color = secondaryTextColor)) {
                            append(" / ")
                            append(formatUsageTokens(breakdown.maxWindowTokens))
                        }
                    },
                    modifier = Modifier.weight(1f),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatUsagePercent(breakdown.windowTokens, breakdown.maxWindowTokens),
                    fontSize = 21.sp,
                    color = progressColor
                )
            }
            if (lastRequestInputTokens != null) {
                Text(
                    stringResource(
                        R.string.context_panel_last_request,
                        formatUsageTokens(lastRequestInputTokens),
                        formatUsagePercent(lastRequestInputTokens, breakdown.maxWindowTokens)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor
                )
            }
            ContextWindowStackedUsageBar(breakdown, colors)
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 26.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.context_panel_message_history),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    formatUsageTokens(breakdown.messageHistoryTokens),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            listOf(
                Triple(colors.userMessages, R.string.context_panel_user_messages, breakdown.userMessageTokens),
                Triple(colors.assistantMessages, R.string.context_panel_assistant_messages, breakdown.assistantMessageTokens),
                Triple(colors.toolResults, R.string.context_panel_tool_results, breakdown.toolResultTokens)
            ).forEach { (color, label, tokens) ->
                ContextWindowRow(color, stringResource(label), tokens, breakdown.maxWindowTokens, indented = true)
            }
            Spacer(Modifier.height(6.dp))
            listOf(
                Triple(colors.systemPrompt, R.string.context_panel_system_prompt, breakdown.systemPromptTokens),
                Triple(colors.residentTools, R.string.context_panel_resident_tools, breakdown.residentToolTokens),
                Triple(colors.mcpTools, R.string.context_panel_mcp_tools, breakdown.mcpToolTokens),
                Triple(colors.skills, R.string.context_panel_skills, breakdown.skillTokens),
                Triple(colors.summary, R.string.context_panel_summary, breakdown.summaryTokens),
                Triple(colors.other, R.string.context_panel_other, breakdown.otherTokens)
            ).forEach { (color, label, tokens) ->
                ContextWindowRow(color, stringResource(label), tokens, breakdown.maxWindowTokens)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ContextUsageTotalRow(stringResource(R.string.context_panel_remaining_space), breakdown.remainingTokens)
            if (breakdown.enableSummary) {
                ContextUsageTotalRow(stringResource(R.string.context_panel_auto_summary_buffer), breakdown.autoSummaryBufferTokens)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (breakdown.enableSummary) {
                    stringResource(
                        R.string.context_panel_footer_auto_summary,
                        formatUsagePercent((breakdown.autoSummaryThreshold * 10000).toLong(), 10000, decimals = 0)
                    )
                } else {
                    stringResource(R.string.context_panel_footer_summary_disabled)
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor
            )
            Button(onClick = onSummarizeNow, enabled = canSummarize) {
                Text(stringResource(
                    if (isSummarizing) R.string.context_panel_summarizing
                    else R.string.context_panel_summarize_now
                ))
            }
        }
    }
}

@Composable
internal fun ContextUsageTotalRow(label: String, tokens: Long) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(formatUsageTokens(tokens), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ContextWindowStackedUsageBar(
        breakdown: ContextWindowBreakdown,
        colors: ContextWindowCategoryColors
) {
    // 只堆叶子分类，避免和「消息历史」父行重复计数
    val segments =
            listOf(
                    breakdown.userMessageTokens to colors.userMessages,
                    breakdown.assistantMessageTokens to colors.assistantMessages,
                    breakdown.toolResultTokens to colors.toolResults,
                    breakdown.systemPromptTokens to colors.systemPrompt,
                    breakdown.residentToolTokens to colors.residentTools,
                    breakdown.mcpToolTokens to colors.mcpTools,
                    breakdown.skillTokens to colors.skills,
                    breakdown.summaryTokens to colors.summary,
                    breakdown.otherTokens to colors.other
            ).filter { it.first > 0L }
    val occupied = segments.sumOf { it.first }
    val remaining = (breakdown.maxWindowTokens - occupied).coerceAtLeast(0L)

    Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        if (occupied > 0L || remaining > 0L) {
            Row(modifier = Modifier.fillMaxSize()) {
                segments.forEach { (tokens, color) ->
                    Box(
                            modifier =
                                    Modifier.fillMaxHeight()
                                            .weight(tokens.toFloat())
                                            .background(color)
                    )
                }
                if (remaining > 0L) {
                    Spacer(modifier = Modifier.weight(remaining.toFloat()).fillMaxHeight())
                }
            }
        }
        // 刻度跟当前自动总结阈值走，而不是写死 80%/90%
        if (breakdown.enableSummary && breakdown.autoSummaryThreshold in 0f..1f) {
            Box(
                    modifier =
                            Modifier.fillMaxWidth(breakdown.autoSummaryThreshold)
                                    .fillMaxHeight()
            ) {
                Box(
                        modifier =
                                Modifier.align(Alignment.CenterEnd)
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.45f
                                                )
                                        )
                )
            }
        }
    }
}

@Composable
private fun ContextWindowRow(
        color: Color,
        label: String,
        tokens: Long,
        maxTokens: Long,
        indented: Boolean = false
) {
    val percent = formatUsagePercent(tokens, maxTokens)
    Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 26.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        if (indented) {
            Spacer(modifier = Modifier.width(16.dp))
        }
        Box(
                modifier =
                        Modifier.size(8.dp)
                                .clip(CircleShape)
                                .background(color)
        )
        Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 10.dp).weight(1f)
        )
        Text(
                text = formatUsageTokens(tokens),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
        )
        Text(
                text = percent,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).widthIn(min = 48.dp),
                textAlign = TextAlign.End
        )
    }
}

internal data class ContextWindowCategoryColors(
        val userMessages: Color,
        val assistantMessages: Color,
        val toolResults: Color,
        val systemPrompt: Color,
        val residentTools: Color,
        val mcpTools: Color,
        val skills: Color,
        val summary: Color,
        val other: Color
)

@Composable
internal fun rememberContextWindowCategoryColors(): ContextWindowCategoryColors {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    return remember(isDark) {
        if (isDark) {
            ContextWindowCategoryColors(
                    userMessages = Color(0xFF4FC3F7),
                    assistantMessages = Color(0xFF4DB6AC),
                    toolResults = Color(0xFFFFB74D),
                    systemPrompt = Color(0xFFCE93D8),
                    residentTools = Color(0xFF81C784),
                    mcpTools = Color(0xFFF48FB1),
                    skills = Color(0xFF8D6E63),
                    summary = Color(0xFFFFD54F),
                    other = Color(0xFF90A4AE)
            )
        } else {
            ContextWindowCategoryColors(
                    userMessages = Color(0xFF0288D1),
                    assistantMessages = Color(0xFF00897B),
                    toolResults = Color(0xFFFB8C00),
                    systemPrompt = Color(0xFF8E24AA),
                    residentTools = Color(0xFF43A047),
                    mcpTools = Color(0xFFD81B60),
                    skills = Color(0xFF6D4C41),
                    summary = Color(0xFFFBC02D),
                    other = Color(0xFF546E7A)
            )
        }
    }
}
