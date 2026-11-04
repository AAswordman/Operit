package com.ai.assistance.operit.ui.features.extensions.packages.screens.mcp.components

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ai.assistance.operit.ui.theme.script.text.ThemeBasicTextField as BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R

/** MCP插件部署命令编辑对话框 允许用户在部署前编辑自动生成的部署命令 */
@Composable
fun MCPCommandsEditDialog(
        pluginName: String,
        commands: List<String>,
        isLoading: Boolean = false,
        onDismissRequest: () -> Unit,
        onConfirm: (List<String>) -> Unit
) {
    var editedCommands by remember { mutableStateOf(commands.joinToString("\n")) }

    // 当命令更新时，更新编辑文本
    LaunchedEffect(commands) {
        if (commands.isNotEmpty()) {
            editedCommands = commands.joinToString("\n")
        }
    }

    Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                // 标题区域
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                            text = stringResource(R.string.mcp_edit_deploy_commands),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = onDismissRequest, modifier = Modifier.size(32.dp)) {
                        Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.mcp_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 插件名称
                Text(
                        text = pluginName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 提示信息
                Card(
                        shape = RoundedCornerShape(8.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.7f
                                                )
                                ),
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                                text = stringResource(R.string.mcp_command_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 命令编辑区
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                            text = stringResource(R.string.mcp_deploy_commands),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                    )

                    if (isLoading) {
                        Spacer(modifier = Modifier.width(12.dp))
                        CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = stringResource(R.string.mcp_loading_commands),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (commands.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = stringResource(R.string.mcp_lines_suffix, commands.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 代码编辑区域
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .heightIn(min = 160.dp, max = 240.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                )
                                        )
                                        .padding(2.dp)
                ) {
                    if (isLoading) {
                        // 居中显示加载指示器
                        Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    } else {
                        BasicTextField(
                                value = editedCommands,
                                onValueChange = { editedCommands = it },
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(12.dp)
                                                .verticalScroll(rememberScrollState()),
                                textStyle =
                                        TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                        ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 底部按钮
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                            onClick = onDismissRequest,
                            colors =
                                    ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                            modifier = Modifier.padding(end = 12.dp)
                    ) { Text(stringResource(R.string.mcp_cancel)) }

                    Button(
                            onClick = {
                                // 将编辑后的文本拆分为命令列表，并过滤掉空行
                                val finalCommands =
                                        editedCommands.split("\n").map { it.trim() }.filter {
                                            it.isNotEmpty()
                                        }

                                onConfirm(finalCommands)
                            },
                            enabled = !isLoading,
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                    )
                    ) {
                        Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.mcp_confirm_and_deploy))
                    }
                }
            }
        }
    }
}
