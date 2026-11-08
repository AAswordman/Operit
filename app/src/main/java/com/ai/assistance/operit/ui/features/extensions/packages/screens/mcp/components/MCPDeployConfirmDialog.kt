package com.ai.assistance.operit.ui.features.extensions.packages.screens.mcp.components

import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R

/** MCP插件部署确认对话框 提供直接部署和自定义部署两个选项 */
@Composable
fun MCPDeployConfirmDialog(
        pluginName: String,
        onDismissRequest: () -> Unit,
        onConfirm: () -> Unit,
        onCustomize: () -> Unit
) {
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
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                            text = stringResource(R.string.mcp_deploy_confirm_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 插件名称和提示文本
                Text(
                        text = stringResource(R.string.mcp_deploying_plugin, pluginName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                        text = stringResource(R.string.mcp_deploy_choice_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 底部按钮
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                ) {
                    // 取消按钮
                    TextButton(
                            onClick = onDismissRequest,
                    ) { Text(stringResource(R.string.mcp_cancel)) }

                    // 自定义按钮 - 改为TextButton以节省空间
                    TextButton(
                            onClick = {
                                // 只调用自定义回调，由主程序负责处理确认对话框的关闭
                                onCustomize()
                            },
                            colors =
                                    ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                    )
                    ) { Text(stringResource(R.string.mcp_custom_command)) }

                    // 部署按钮
                    Button(
                            onClick = {
                                onConfirm()
                                onDismissRequest()
                            },
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                    )
                    ) { Text(stringResource(R.string.mcp_direct_deploy)) }
                }
            }
        }
    }
}
