package com.ai.assistance.operit.ui.features.chat.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.ContextWindowBreakdown
import com.ai.assistance.operit.core.chat.ContextWindowBreakdownEstimator
import java.util.Locale

@Composable
fun ContextWindowBreakdownSheet(
        breakdown: ContextWindowBreakdown?,
        persistedWindowTokens: Long = 0L,
        isLoading: Boolean,
        isSummarizing: Boolean,
        onSummarizeNow: () -> Unit,
        onDismiss: () -> Unit
) {
    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
                modifier =
                        Modifier.fillMaxWidth(0.92f)
                                .heightIn(max = 640.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
        ) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 18.dp)
                                    .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                            text = stringResource(R.string.context_panel_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = CircleShape
                    ) {
                        Text(
                                text = stringResource(R.string.context_panel_estimate_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                when {
                    isLoading && breakdown == null -> {
                        Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                        text = stringResource(R.string.context_panel_loading),
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    breakdown == null -> {
                        Text(
                                text = stringResource(R.string.context_panel_unavailable),
                                color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    else -> {
                        ContextWindowBreakdownContent(
                                breakdown = breakdown,
                                persistedWindowTokens = persistedWindowTokens,
                                isSummarizing = isSummarizing,
                                onSummarizeNow = onSummarizeNow
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextWindowBreakdownContent(
        breakdown: ContextWindowBreakdown,
        persistedWindowTokens: Long,
        isSummarizing: Boolean,
        onSummarizeNow: () -> Unit
) {
    val colors = rememberContextWindowCategoryColors()
    val percentText =
            remember(breakdown.usageRatio) {
                String.format(Locale.US, "%.1f%%", breakdown.usageRatio * 100f)
            }
    val autoSummaryPercent =
            remember(breakdown.autoSummaryThreshold) {
                String.format(Locale.US, "%.0f%%", breakdown.autoSummaryThreshold * 100f)
            }
    val progressColor =
            when {
                breakdown.usageRatio >= 0.9f -> MaterialTheme.colorScheme.error
                breakdown.usageRatio >= breakdown.autoSummaryThreshold &&
                        breakdown.autoSummaryThreshold > 0f -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
        ) {
            Text(
                    text =
                            stringResource(
                                    R.string.context_panel_usage,
                                    ContextWindowBreakdownEstimator.formatTokenCount(breakdown.windowTokens),
                                    ContextWindowBreakdownEstimator.formatTokenCount(breakdown.maxWindowTokens)
                            ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                    text = percentText,
                    style = MaterialTheme.typography.titleMedium,
                    color = progressColor
            )
        }
        if (persistedWindowTokens > 0L && persistedWindowTokens != breakdown.windowTokens) {
            Text(
                    text =
                            stringResource(
                                    R.string.context_panel_last_window,
                                    ContextWindowBreakdownEstimator.formatTokenCount(
                                            persistedWindowTokens
                                    )
                            ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ContextWindowStackedUsageBar(
                breakdown = breakdown,
                colors = colors
        )

        Text(
                text = stringResource(R.string.context_panel_in_window),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
        )
        ContextWindowRow(
                color = colors.messageHistory,
                label = stringResource(R.string.context_panel_message_history),
                tokens = breakdown.messageHistoryTokens,
                maxTokens = breakdown.maxWindowTokens
        )
        ContextWindowRow(
                color = colors.userMessages,
                label = stringResource(R.string.context_panel_user_messages),
                tokens = breakdown.userMessageTokens,
                maxTokens = breakdown.maxWindowTokens,
                indented = true
        )
        ContextWindowRow(
                color = colors.assistantMessages,
                label = stringResource(R.string.context_panel_assistant_messages),
                tokens = breakdown.assistantMessageTokens,
                maxTokens = breakdown.maxWindowTokens,
                indented = true
        )
        ContextWindowRow(
                color = colors.toolResults,
                label = stringResource(R.string.context_panel_tool_results),
                tokens = breakdown.toolResultTokens,
                maxTokens = breakdown.maxWindowTokens,
                indented = true
        )
        ContextWindowRow(
                color = colors.systemPrompt,
                label = stringResource(R.string.context_panel_system_prompt),
                tokens = breakdown.systemPromptTokens,
                maxTokens = breakdown.maxWindowTokens
        )
        ContextWindowRow(
                color = colors.residentTools,
                label = stringResource(R.string.context_panel_resident_tools),
                tokens = breakdown.residentToolTokens,
                maxTokens = breakdown.maxWindowTokens
        )
        ContextWindowRow(
                color = colors.mcpTools,
                label = stringResource(R.string.context_panel_mcp_tools),
                tokens = breakdown.mcpToolTokens,
                maxTokens = breakdown.maxWindowTokens
        )
        ContextWindowRow(
                color = colors.skills,
                label = stringResource(R.string.context_panel_skills),
                tokens = breakdown.skillTokens,
                maxTokens = breakdown.maxWindowTokens
        )
        ContextWindowRow(
                color = colors.summary,
                label = stringResource(R.string.context_panel_summary),
                tokens = breakdown.summaryTokens,
                maxTokens = breakdown.maxWindowTokens
        )
        if (breakdown.otherTokens > 0L) {
            ContextWindowRow(
                    color = colors.other,
                    label = stringResource(R.string.context_panel_other),
                    tokens = breakdown.otherTokens,
                    maxTokens = breakdown.maxWindowTokens
            )
        }

        Text(
                text = stringResource(R.string.context_panel_remaining_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
        )
        if (breakdown.enableSummary) {
            ContextWindowRow(
                    color = colors.autoSummaryBuffer,
                    label = stringResource(R.string.context_panel_auto_summary_buffer),
                    tokens = breakdown.autoSummaryBufferTokens,
                    maxTokens = breakdown.maxWindowTokens
            )
        }
        ContextWindowRow(
                color = colors.remaining,
                label = stringResource(R.string.context_panel_remaining_space),
                tokens = breakdown.remainingTokens,
                maxTokens = breakdown.maxWindowTokens
        )

        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                    text =
                            if (breakdown.enableSummary) {
                                stringResource(R.string.context_panel_footer_auto_summary, autoSummaryPercent)
                            } else {
                                stringResource(R.string.context_panel_footer_summary_disabled)
                            },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            Button(
                    onClick = onSummarizeNow,
                    enabled = !isSummarizing
            ) {
                Text(stringResource(R.string.context_panel_summarize_now))
            }
        }
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
    val percent =
            if (maxTokens <= 0L) {
                "0.0%"
            } else {
                String.format(Locale.US, "%.1f%%", tokens.toDouble() / maxTokens.toDouble() * 100.0)
            }
    Row(
            modifier = Modifier.fillMaxWidth(),
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 10.dp).weight(1f)
        )
        Text(
                text = ContextWindowBreakdownEstimator.formatTokenCount(tokens),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
        )
        Text(
                text = percent,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private data class ContextWindowCategoryColors(
        val messageHistory: Color,
        val userMessages: Color,
        val assistantMessages: Color,
        val toolResults: Color,
        val systemPrompt: Color,
        val residentTools: Color,
        val mcpTools: Color,
        val skills: Color,
        val summary: Color,
        val other: Color,
        val autoSummaryBuffer: Color,
        val remaining: Color
)

@Composable
private fun rememberContextWindowCategoryColors(): ContextWindowCategoryColors {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val remaining = colorScheme.surfaceVariant
    val autoSummaryBuffer = colorScheme.outline.copy(alpha = 0.72f)
    return remember(isDark, remaining, autoSummaryBuffer) {
        if (isDark) {
            ContextWindowCategoryColors(
                    messageHistory = Color(0xFF5C6BC0),
                    userMessages = Color(0xFF4FC3F7),
                    assistantMessages = Color(0xFF4DB6AC),
                    toolResults = Color(0xFFFFB74D),
                    systemPrompt = Color(0xFFCE93D8),
                    residentTools = Color(0xFF81C784),
                    mcpTools = Color(0xFFF48FB1),
                    skills = Color(0xFF8D6E63),
                    summary = Color(0xFFFFD54F),
                    other = Color(0xFF90A4AE),
                    autoSummaryBuffer = autoSummaryBuffer,
                    remaining = remaining
            )
        } else {
            ContextWindowCategoryColors(
                    messageHistory = Color(0xFF3949AB),
                    userMessages = Color(0xFF0288D1),
                    assistantMessages = Color(0xFF00897B),
                    toolResults = Color(0xFFFB8C00),
                    systemPrompt = Color(0xFF8E24AA),
                    residentTools = Color(0xFF43A047),
                    mcpTools = Color(0xFFD81B60),
                    skills = Color(0xFF6D4C41),
                    summary = Color(0xFFFBC02D),
                    other = Color(0xFF546E7A),
                    autoSummaryBuffer = autoSummaryBuffer,
                    remaining = remaining
            )
        }
    }
}
