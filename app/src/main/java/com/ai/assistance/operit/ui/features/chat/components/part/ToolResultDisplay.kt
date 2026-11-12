package com.ai.assistance.operit.ui.features.chat.components.part

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.components.compactDialogHeightWhenShort
import com.ai.assistance.operit.ui.features.chat.components.rememberCompactDialogMetrics

/** 工具执行结果显示组件 简洁风格，显示工具执行结果，无边框，与CompactToolDisplay风格一致 通过缩进和特殊图标区分工具调用和执行结果 支持点击查看详细内容 */
@Composable
fun ToolResultDisplay(
        toolName: String,
        result: String,
        isSuccess: Boolean = true,
        onCopyResult: () -> Unit = {},
        modifier: Modifier = Modifier,
        summaryPrefix: String? = null,
        enableDialog: Boolean = true  // 新增参数：是否启用弹窗功能，默认启用
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val hasContent = result.isNotBlank()

    // 弹窗状态
    var showDetailDialog by remember { mutableStateOf(false) }

    // 显示详细内容的弹窗 - 仅在启用弹窗时显示
    if (showDetailDialog && enableDialog) {
        ToolResultDetailDialog(
                toolName = toolName,
                result = result,
                isSuccess = isSuccess,
                onDismiss = { showDetailDialog = false },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(result))
                    onCopyResult()
                }
        )
    }

    val resultSummary =
        if (hasContent) {
            result.take(200)
        } else if (isSuccess) {
            context.getString(R.string.execution_success)
        } else {
            context.getString(R.string.execution_failed)
        }
    val summaryText =
        listOfNotNull(summaryPrefix?.takeIf { it.isNotBlank() }, resultSummary)
            .joinToString(" · ")
    val semanticResultText = remember(result, hasContent) {
        if (!hasContent) {
            ""
        } else {
            result
                .replace("\n", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .let { normalized ->
                    if (normalized.length <= 20) normalized else normalized.take(20) + "..."
                }
        }
    }
    val semanticDescription = remember(toolName, summaryText, semanticResultText, isSuccess, hasContent) {
        val resultLabel = context.getString(R.string.tool_execution_result)
        val statusLabel =
            if (isSuccess) context.getString(R.string.success) else context.getString(R.string.failed)
        if (hasContent && semanticResultText.isNotBlank()) {
            val semanticSummary =
                listOfNotNull(summaryPrefix?.takeIf { it.isNotBlank() }, semanticResultText)
                    .joinToString(" · ")
            "$resultLabel: $toolName, $statusLabel, $semanticSummary"
        } else {
            "$resultLabel: $toolName, $statusLabel, $summaryText"
        }
    }

    CanvasToolResultRow(
        summary = summaryText,
        isSuccess = isSuccess,
        semanticDescription = semanticDescription,
        modifier = modifier,
        emphasizeSummary = !hasContent,
        onClick =
            if (hasContent && enableDialog) {
                { showDetailDialog = true }
            } else {
                null
            },
        onCopyClick =
            if (hasContent) {
                {
                    clipboardManager.setText(AnnotatedString(result))
                    onCopyResult()
                }
            } else {
                null
            },
    )
}

/** 工具结果详情弹窗 美观的弹窗显示完整的工具执行结果 */
@Composable
internal fun ToolResultDetailDialog(
        toolName: String,
        result: String,
        isSuccess: Boolean,
        onDismiss: () -> Unit,
        onCopy: () -> Unit,
        titleOverride: String? = null,
        metadata: String? = null,
        primaryActionLabel: String? = null,
        onPrimaryAction: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val dialogMetrics = rememberCompactDialogMetrics()
    val resultMaxHeight = if (dialogMetrics.isCompactHeight) 160.dp else 300.dp
    val cardModifier =
            Modifier.fillMaxWidth().padding(16.dp).compactDialogHeightWhenShort(dialogMetrics)
    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
                modifier = cardModifier,
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 标题栏
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // 状态图标
                    Icon(
                            imageVector =
                                    if (isSuccess) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = if (isSuccess) context.getString(R.string.success) else context.getString(R.string.failed),
                            tint =
                                    if (isSuccess) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // 工具名称
                    Text(
                            text =
                                    titleOverride
                                            ?: "$toolName ${if (isSuccess) context.getString(R.string.execution_success) else context.getString(R.string.execution_failed)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                    )

                    // 复制按钮
                    IconButton(onClick = onCopy) {
                        Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = context.getString(R.string.copy_result),
                                tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                metadata?.takeIf { it.isNotBlank() }?.let { metadataText ->
                    Text(
                            text = metadataText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 分隔线
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(modifier = Modifier.height(16.dp))

                // 结果内容
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .heightIn(min = 50.dp, max = resultMaxHeight)
                                        .verticalScroll(rememberScrollState())
                                        .background(
                                                color =
                                                        if (isSuccess)
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha = 0.5f
                                                                )
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .errorContainer.copy(
                                                                        alpha = 0.2f
                                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(12.dp)
                ) {
                    Text(
                            text = result,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 关闭按钮
                Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onPrimaryAction != null && !primaryActionLabel.isNullOrBlank()) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.close))
                        }
                        Button(
                                onClick = onPrimaryAction,
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                        )
                        ) {
                            Text(primaryActionLabel)
                        }
                    } else {
                        Button(
                                onClick = onDismiss,
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                        )
                        ) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}
