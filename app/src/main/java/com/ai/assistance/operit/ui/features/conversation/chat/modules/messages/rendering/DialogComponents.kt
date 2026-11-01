package com.ai.assistance.operit.ui.features.conversation.chat.modules.messages.rendering

import com.ai.assistance.operit.ui.theme.script.ThemeScriptButton as Button
import com.ai.assistance.operit.ui.theme.script.ThemeScriptIconButton as IconButton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.layout.compactDialogHeightWhenShort
import com.ai.assistance.operit.ui.features.conversation.chat.foundation.layout.rememberCompactDialogMetrics

/**
 * 通用的内容详情弹窗
 * @param title 弹窗标题
 * @param content 要显示的完整内容字符串
 * @param icon 标题旁的图标
 * @param onDismiss 请求关闭弹窗的回调
 * @param onCopy 请求复制内容的回调
 * @param isDiffContent 是否为Diff内容，如果是，则使用Diff渲染器
 */
@Composable
fun ContentDetailDialog(
    title: String,
    content: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    isDiffContent: Boolean = false
) {
    var isRawView by remember { mutableStateOf(false) }
    val isXmlContent = remember(content) { content.trim().startsWith("<") }
    val dialogMetrics = rememberCompactDialogMetrics()
    val contentMaxHeight = if (dialogMetrics.isCompactHeight) 160.dp else 400.dp
    val cardModifier =
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .compactDialogHeightWhenShort(dialogMetrics)

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
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    // 视图切换和复制按钮
                    Row {
                        // 只有当内容是XML且不是Diff时才显示切换按钮
                        if (isXmlContent && !isDiffContent) {
                            IconButton(onClick = { isRawView = !isRawView }) {
                                Icon(
                                    imageVector = if (isRawView) Icons.Default.Visibility else Icons.Default.Code,
                                    contentDescription = if (isRawView) "Switch to Visual View" else "Switch to Raw View",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(16.dp))

                // 内容区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp, max = contentMaxHeight)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    val dialogListState = rememberLazyListState()
                    val lines = remember(content) { content.lines() }

                    LaunchedEffect(lines.size) {
                        if (lines.isNotEmpty()) dialogListState.animateScrollToItem(lines.lastIndex)
                    }

                    if (isXmlContent && !isRawView && !isDiffContent) {
                        ParamVisualizer(xmlContent = content)
                    } else if (isDiffContent) {
                        DiffContentLazyColumn(
                            diffLines = lines,
                            listState = dialogListState,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        CodeContentWithLineNumbers(
                            lines = lines,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            listState = dialogListState,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 关闭按钮
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text(stringResource(R.string.common_close)) }
            }
        }
    }
}

/**
 * 用于显示Diff内容的LazyColumn
 * @param diffLines Diff内容行列表
 * @param listState LazyListState
 * @param modifier Modifier
 */
@Composable
fun DiffContentLazyColumn(
    diffLines: List<String>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(diffLines) { line ->
            val (backgroundColor, textColor) = when {
                line.startsWith("+") -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) to MaterialTheme.colorScheme.primary
                line.startsWith("-") -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) to MaterialTheme.colorScheme.error
                line.startsWith("@@") -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f) to MaterialTheme.colorScheme.secondary
                else -> Color.Transparent to MaterialTheme.colorScheme.onSurfaceVariant
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = line.trimEnd(),
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    softWrap = true
                )
            }
        }
    }
}
