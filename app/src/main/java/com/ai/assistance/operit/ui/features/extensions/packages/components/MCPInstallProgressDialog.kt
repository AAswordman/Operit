package com.ai.assistance.operit.ui.features.extensions.packages.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mcp.InstallProgress
import com.ai.assistance.operit.data.mcp.InstallResult

/**
 * MCP 安装/卸载进度对话框
 *
 * @param installProgress 当前安装/卸载进度状态
 * @param onDismissRequest 对话框关闭回调
 * @param result 操作结果，可为 null
 * @param serverName 正在操作的服务器名称
 * @param operationType 操作类型，默认为"安装"，可以是"卸载"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPInstallProgressDialog(
        installProgress: InstallProgress?,
        onDismissRequest: () -> Unit,
        result: InstallResult? = null,
        serverName: String = "",
        operationType: String = ""
) {
    if (installProgress == null && result == null) return

    AlertDialog(
            onDismissRequest = {
                // 操作完成或失败时才允许关闭
                if (installProgress is InstallProgress.Finished || result != null) {
                    onDismissRequest()
                }
            },
            properties =
                    DialogProperties(
                            dismissOnBackPress =
                                    result != null || installProgress is InstallProgress.Finished,
                            dismissOnClickOutside =
                                    result != null || installProgress is InstallProgress.Finished
                    ),
            title = { Text("$operationType $serverName") },
            text = {
                Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        // 显示结果
                        result != null -> {
                            when (result) {
                                is InstallResult.Success -> {
                                    if (operationType == stringResource(R.string.mcp_uninstall)) {
                                        Text(
                                                stringResource(R.string.mcp_uninstall_success),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                                stringResource(R.string.mcp_operation_success, operationType),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                        if (result.pluginPath.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(stringResource(R.string.mcp_plugin_installed_to, operationType), style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                    text = result.pluginPath,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                                is InstallResult.Error -> {
                                    Text(
                                            stringResource(R.string.mcp_operation_failed, operationType),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                            text = result.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 显示进度
                        else -> {
                            when (val progress = installProgress) {
                                InstallProgress.Preparing -> {
                                    Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(16.dp)
                                    ) {
                                        CircularProgressIndicator(
                                                modifier = Modifier.size(48.dp),
                                        )
                                    }
                                    Text(
                                            stringResource(R.string.mcp_preparing_operation, operationType),
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center
                                    )
                                }
                                is InstallProgress.Downloading -> {
                                    Text(
                                            stringResource(R.string.mcp_downloading_plugin),
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (progress.progress >= 0) {
                                        LinearProgressIndicator(
                                                progress = { progress.progress / 100f },
                                                modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                                "${progress.progress}%",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(top = 8.dp)
                                        )
                                    } else {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                is InstallProgress.Extracting -> {
                                    val actionText = if (operationType == stringResource(R.string.mcp_uninstall)) stringResource(R.string.mcp_deleting_files) else stringResource(R.string.mcp_extracting_files)
                                    Text(
                                            actionText,
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (progress.progress >= 0) {
                                        LinearProgressIndicator(
                                                progress = { progress.progress / 100f },
                                                modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                                "${progress.progress}%",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(top = 8.dp)
                                        )
                                    } else {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                }
                                InstallProgress.Finished -> {
                                    Text(
                                            stringResource(R.string.mcp_operation_complete, operationType),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center
                                    )
                                }
                                null -> {
                                    // This case should not happen, but we need to handle it for exhaustiveness
                                    Text(
                                            stringResource(R.string.mcp_preparing),
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (result != null || installProgress is InstallProgress.Finished) {
                    Button(onClick = onDismissRequest) { Text(stringResource(R.string.mcp_ok)) }
                }
            },
            dismissButton = {
                if (installProgress !is InstallProgress.Finished && result == null) {
                    TextButton(onClick = onDismissRequest, enabled = false) { Text(stringResource(R.string.mcp_operation_in_progress, operationType)) }
                }
            }
    )
}
